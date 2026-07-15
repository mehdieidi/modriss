import { state } from "../state.js";
import { el } from "../dom.js";
import { modelingPlaceholderIcon } from "../modeling-config-data.js";
import { getCanvasFitArea } from "../canvas-viewport-fit.js";
import { mapDiagramToG6, mapEdgeToG6, mapNodeToG6 } from "./g6-mapper.js";
import {
  canvasBackgroundColor,
  cssVar,
  G6_BASE_EDGE_TYPE,
  G6_BASE_NODE_TYPE,
  VARKA_EDGE_TYPE,
  VARKA_NODE_TYPE,
  nodeSizeForDiagram,
} from "./g6-style.js";
import {
  cancelScheduledDraw,
  createAdjacencyIndex,
  createSpatialIndex,
  detailLevelForZoom,
  diffGraphData,
  fingerprintElement,
  scheduleGraphDraw,
  scheduleGraphRender,
  scheduleIncrementalCanvasMutation,
  shouldShowEdgeLabels,
} from "./g6-performance.js";
import { bindG6Interactions } from "./g6-interactions.js";
import { renderIconCentricNodeG6, iconAnchorBoundsLocal } from "./icon-node-layout.js";
import { applyTintedIconsToNodes, onIconTintsUpdated, primeIconTints } from "./icon-tint.js";
import {
  clearG6Overlays,
  renderContextBoxes,
  renderNodeIcons,
  showInlineLabelEditor,
} from "./g6-overlays.js";

let editor = null;
let iconTintListenerInstalled = false;

function ensureIconTintListener() {
  if (iconTintListenerInstalled) {
    return;
  }
  iconTintListenerInstalled = true;
  onIconTintsUpdated(() => {
    if (editor?.graph) {
      scheduleGraphDraw(editor.graph);
    }
  });
}
let extensionsRegistered = false;
let pendingViewportFrame = 0;
let pendingViewportSyncFrame = 0;
let pendingLodFrame = 0;
let pendingLodTimer = 0;
let pendingLodState = null;
const CONNECT_GLOBAL_TARGET_NODE_LIMIT = 240;
const CONNECT_ILLEGAL_STATE_NODE_LIMIT = 800;
const HOVER_FOCUS_EDGE_LIMIT = 64;
const LOD_UPDATE_IDLE_DELAY_MS = 220;
const VIEWPORT_TRANSFORM_IDLE_MS = 180;
const FIT_VIEW_PADDING = 72;
const FIT_VIEW_MIN_SCALE = 0.01;
const FIT_VIEW_MAX_SCALE = 2.5;
const FIT_VIEW_SINGLE_NODE_SCALE = 1.25;
const FIT_VIEW_SPARSE_NODE_MAX_SCALE = 1.35;
const FIT_VIEW_SPARSE_NODE_MIN_SCALE = 0.5;
const MINIMAP_PADDING = 10;
const MINIMAP_MIN_SPAN = 240;
const MINIMAP_NODE_RADIUS = 3.5;
let viewportTransformEndTimer = 0;
let viewportTransforming = false;

function g6() {
  return window.G6 || null;
}

export function isG6Available() {
  const api = g6();
  return Boolean(api?.Graph);
}

function updateDebugState(patch = {}) {
  window.varkaG6State = {
    ...(window.varkaG6State || {}),
    ...patch,
    available: isG6Available(),
    mounted: Boolean(editor?.graph),
    renderer: "antv-g6",
  };
}

function installDebugProbe() {
  window.varkaG6Debug = () => {
    const host = el.g6EditorHost;
    const graph = editor?.graph || null;
    const hostRect = host?.getBoundingClientRect?.();
    let graphSize = null;
    try {
      graphSize = graph?.getSize?.() || null;
    } catch (error) {
      graphSize = `getSize failed: ${error.message}`;
    }
    return {
      ...(window.varkaG6State || {}),
      activeType: state.activeType,
      stateNodes: state.diagram.nodes.length,
      stateEdges: state.diagram.connections.length,
      mappedNodes: editor?.dataSnapshot?.nodesById?.size || 0,
      mappedEdges: editor?.dataSnapshot?.edgesById?.size || 0,
      graphSize,
      hostRect: hostRect
        ? {
            width: Math.round(hostRect.width),
            height: Math.round(hostRect.height),
          }
        : null,
      hostChildren: [...(host?.children || [])].map((child) => child.tagName),
      hasCanvasDescendant: Boolean(host?.querySelector?.("canvas")),
      canvasGridRenderer: el.canvasGrid?.dataset?.renderer || "",
      g6Version: g6()?.version || "",
      g6Keys: Object.keys(g6() || {}).slice(0, 40),
      unavailable: el.canvasGrid?.classList.contains("g6-renderer-unavailable"),
    };
  };
}

function truncate(text, max = 44) {
  const value = String(text || "");
  return value.length > max ? `${value.slice(0, Math.max(1, max - 1))}...` : value;
}

function lineBreak(text, maxLine = 23, maxLines = 2) {
  const value = String(text || "").trim();
  if (!value) {
    return "";
  }
  const words = value.split(/\s+/);
  const lines = [];
  let line = "";
  words.forEach((word) => {
    const next = line ? `${line} ${word}` : word;
    if (next.length > maxLine && line) {
      lines.push(line);
      line = word;
    } else {
      line = next;
    }
  });
  if (line) {
    lines.push(line);
  }
  return lines
    .slice(0, maxLines)
    .map((item, index) => {
      if (index === maxLines - 1 && lines.length > maxLines) {
        return truncate(item, maxLine - 1);
      }
      return truncate(item, maxLine);
    })
    .join("\n");
}

function _lineCount(text) {
  const value = String(text || "");
  return value ? value.split("\n").length : 0;
}

function _boundedText(text, maxLine, maxLines) {
  return lineBreak(text, maxLine, maxLines).toUpperCase();
}

function pathMidpoint(points) {
  const segments = [];
  let total = 0;
  for (let index = 1; index < points.length; index += 1) {
    const start = points[index - 1];
    const end = points[index];
    const length = Math.hypot(end.x - start.x, end.y - start.y);
    if (!Number.isFinite(length) || length <= 0) {
      continue;
    }
    segments.push({ start, end, length });
    total += length;
  }
  if (!segments.length) {
    return points[0] || { x: 0, y: 0 };
  }
  let remaining = total / 2;
  for (const segment of segments) {
    if (remaining <= segment.length) {
      const ratio = remaining / segment.length;
      return {
        x: segment.start.x + (segment.end.x - segment.start.x) * ratio,
        y: segment.start.y + (segment.end.y - segment.start.y) * ratio,
      };
    }
    remaining -= segment.length;
  }
  return segments[segments.length - 1].end;
}

function badgeFill(text, _diagramType) {
  const value = String(text || "").toLowerCase();
  if (
    value.includes("block") ||
    value.includes("critical") ||
    value.includes("error") ||
    value.includes("high")
  ) {
    return "rgba(248, 113, 113, 0.17)";
  }
  if (value.includes("generated") || value.includes("trace")) {
    return "rgba(129, 140, 248, 0.16)";
  }
  if (value.includes("encrypt") || value.includes("auth") || value.includes("security")) {
    return "rgba(74, 222, 128, 0.15)";
  }
  return "rgba(148, 163, 184, 0.14)";
}

function badgeTextFill(_diagramType) {
  return "rgba(226, 232, 240, 0.82)";
}

function _renderNodeIcon(shape, container, { left, top, low = false, iconSrc = "" } = {}) {
  const size = low ? 18 : 20;
  shape.upsert(
    "placeholderIcon",
    "image",
    {
      x: left + 10,
      y: top + (low ? 9 : 6),
      width: size,
      height: size,
      src: iconSrc || modelingPlaceholderIcon(),
      opacity: 0.9,
      pointerEvents: "none",
    },
    container,
  );
  shape.upsert("iconTile", "rect", false, container);
  shape.upsert("iconSky", "circle", false, container);
  shape.upsert("iconMark", "path", false, container);
}

function _clearPlaceholderIcon(shape, container) {
  shape.upsert("placeholderIcon", "image", false, container);
  shape.upsert("iconTile", "rect", false, container);
  shape.upsert("iconSky", "circle", false, container);
  shape.upsert("iconMark", "path", false, container);
}

function _renderOpenControl(
  shape,
  container,
  {
    left,
    top,
    width,
    height: _height,
    diagramType: _diagramType,
    selected = false,
    openControlHover = false,
    low = false,
  },
) {
  const controlWidth = low ? 30 : 38;
  const controlHeight = low ? 14 : 16;
  const x = left + width - controlWidth - 9;
  const y = top + (low ? 8 : 7);
  const fill = openControlHover ? "rgba(94, 203, 255, 0.26)" : "rgba(226, 232, 240, 0.14)";
  const stroke = openControlHover
    ? cssVar("--accent-select", "#5ecbff")
    : selected
      ? cssVar("--accent-select", "#5ecbff")
      : "rgba(226, 232, 240, 0.22)";
  shape.upsert(
    "openControl",
    "rect",
    {
      x,
      y: openControlHover ? y - 1 : y,
      width: controlWidth,
      height: openControlHover ? controlHeight + 2 : controlHeight,
      radius: 2,
      fill,
      stroke,
      lineWidth: openControlHover || selected ? 1.4 : 1,
      shadowColor: openControlHover ? "rgba(94, 203, 255, 0.38)" : "transparent",
      shadowBlur: openControlHover ? 8 : 0,
      cursor: "pointer",
    },
    container,
  );
  shape.upsert(
    "openControlText",
    "text",
    {
      x: x + controlWidth / 2,
      y: y + controlHeight / 2 + 0.5,
      text: "OPEN",
      fontFamily: cssVar("--font-display", "sans-serif"),
      fontSize: low ? 6.5 : 7.5,
      fontWeight: 800,
      fill: cssVar("--text-strong", "#e3e8f2"),
      textAlign: "center",
      textBaseline: "middle",
      cursor: "pointer",
    },
    container,
  );
}

function clearPortGlyphs(shape, container) {
  [
    "leftPortRail",
    "rightPortRail",
    "leftPortWell",
    "rightPortWell",
    "leftPortCore",
    "rightPortCore",
    "leftPortArrow",
    "rightPortArrow",
  ].forEach((key) => {
    shape.upsert(key, "path", false, container);
    shape.upsert(key, "rect", false, container);
    shape.upsert(key, "circle", false, container);
  });
}

function _renderNodeTags(
  shape,
  container,
  { badges = [], diagramType, left, top, width, height, low = false },
) {
  const tags = Array.isArray(badges) ? badges.slice(0, 4) : [];
  let x = left + 10;
  let y = top + height - 21;
  const maxX = left + width - 10;
  const rowHeight = 13;
  tags.forEach((text, index) => {
    const value = String(text || "").trim();
    const label = truncate(value, 12).toUpperCase();
    const tagWidth = Math.min(72, Math.max(28, 15 + label.length * 4.7));
    if (x + tagWidth > maxX && x > left + 10) {
      x = left + 10;
      y += rowHeight + 2;
    }
    const visible = !low && value && y + rowHeight <= top + height - 5;
    shape.upsert(
      `badge${index}`,
      "rect",
      visible
        ? {
            x,
            y,
            width: tagWidth,
            height: rowHeight,
            radius: 3,
            fill: badgeFill(value, diagramType),
            stroke: "rgba(148, 163, 184, 0.14)",
            pointerEvents: "none",
          }
        : false,
      container,
    );
    shape.upsert(
      `badgeText${index}`,
      "text",
      visible
        ? {
            x: x + tagWidth / 2,
            y: y + rowHeight / 2 + 0.5,
            text: label,
            fontFamily: cssVar("--font-ui", "sans-serif"),
            fontSize: 6.8,
            fontWeight: 800,
            fill: badgeTextFill(diagramType),
            textAlign: "center",
            textBaseline: "middle",
            pointerEvents: "none",
          }
        : false,
      container,
    );
    x += tagWidth + 4;
  });
  for (let index = tags.length; index < 4; index += 1) {
    shape.upsert(`badge${index}`, "rect", false, container);
    shape.upsert(`badgeText${index}`, "text", false, container);
  }
}

function states(attributes) {
  return new Set(Array.isArray(attributes?.states) ? attributes.states : []);
}

function _notationGlyphPath(geometry, left, top, width, height) {
  const name = String(geometry || "rectangle").toLowerCase();
  const right = left + width;
  const bottom = top + height;
  const midX = left + width / 2;
  const midY = top + height / 2;
  const close = (points) => [
    ["M", points[0][0], points[0][1]],
    ...points.slice(1).map(([x, y]) => ["L", x, y]),
    ["Z"],
  ];
  if (name === "diamond") {
    return close([
      [midX, top],
      [right, midY],
      [midX, bottom],
      [left, midY],
    ]);
  }
  if (name === "hexagon") {
    const inset = width * 0.24;
    return close([
      [left + inset, top],
      [right - inset, top],
      [right, midY],
      [right - inset, bottom],
      [left + inset, bottom],
      [left, midY],
    ]);
  }
  if (name === "octagon") {
    const insetX = width * 0.2;
    const insetY = height * 0.2;
    return close([
      [left + insetX, top],
      [right - insetX, top],
      [right, top + insetY],
      [right, bottom - insetY],
      [right - insetX, bottom],
      [left + insetX, bottom],
      [left, bottom - insetY],
      [left, top + insetY],
    ]);
  }
  if (name === "trapezoid") {
    const inset = width * 0.18;
    return close([
      [left + inset, top],
      [right, top],
      [right - inset, bottom],
      [left, bottom],
    ]);
  }
  if (name === "ellipse") {
    return [
      ["M", midX, top],
      ["A", width / 2, height / 2, 0, 1, 1, midX, bottom],
      ["A", width / 2, height / 2, 0, 1, 1, midX, top],
      ["Z"],
    ];
  }
  return null;
}

function registerVarkaG6Extensions() {
  if (extensionsRegistered) {
    return;
  }
  const api = g6();
  if (!api?.register || !api?.ExtensionCategory || !api?.Rect || !api?.BaseEdge) {
    updateDebugState({
      customExtensions: "skipped",
      lastExtensionError: "G6 custom extension API unavailable",
    });
    return;
  }
  const { register, ExtensionCategory, Rect, BaseEdge } = api;

  class VarkaNode extends Rect {
    render(attributes = this.parsedAttributes, container) {
      const size = attributes.size || [attributes.width || 120, attributes.height || 118];
      const width = Number(size[0]) || Number(attributes.width) || 120;
      const height = Number(size[1]) || Number(attributes.height) || 118;
      const left = -width / 2;
      const top = -height / 2;
      const labelText = attributes.labelText || attributes.id || "";
      const accent = attributes.accent || cssVar("--accent", "#00a6e0");
      const stateSet = states(attributes);
      const selected = attributes.selected || stateSet.has("selected");
      const hovered = attributes.hover || stateSet.has("hover");
      const connectSource = attributes.connectSource || stateSet.has("connect-source");
      const connectLegal = attributes.connectLegal || stateSet.has("connect-legal");
      const connectIllegal = attributes.connectIllegal || stateSet.has("connect-illegal");
      const impact =
        attributes.impactFocal ||
        stateSet.has("impact-focal") ||
        attributes.impactUpstream ||
        stateSet.has("impact-upstream") ||
        attributes.impactDownstream ||
        stateSet.has("impact-downstream") ||
        attributes.impactConnected ||
        stateSet.has("impact-connected");
      const focused = attributes.focused || stateSet.has("focus");
      const dimmed = !focused && (attributes.dimmed || stateSet.has("dimmed"));
      const draft = attributes.contextDraft || stateSet.has("context-draft");
      const openControlHover = attributes.openControlHover || stateSet.has("open-control-hover");
      const low = attributes.detailLevel === "low";
      const containerNode = Boolean(attributes.isContainer);
      const handleVisible =
        Boolean(attributes.showHandles) || connectSource || connectLegal || selected || hovered;
      const anchor = iconAnchorBoundsLocal(width, height, low, labelText);
      const handleStroke = connectLegal
        ? "#16a34a"
        : connectSource || selected
          ? cssVar("--accent-select", "#5ecbff")
          : "rgba(148, 163, 184, 0.72)";
      const handleFill = connectLegal ? "#16a34a" : cssVar("--node-bg", "#131923");

      super.render(
        {
          ...attributes,
          fill: "transparent",
          stroke: "transparent",
          lineWidth: 0,
          labelText: "",
          pointerEvents: "none",
        },
        container,
      );

      renderIconCentricNodeG6(this, container, {
        width,
        height,
        low,
        selected,
        iconSrc: attributes.iconSrc,
        labelText: attributes.labelText || attributes.id || "",
        kindText: attributes.kindText || attributes.displayName || "",
        warm: Boolean(attributes.sticky),
        warmFill: attributes.sticky || "#fde68a",
        container: containerNode,
        accent,
        openControlHover,
        draft,
        dimmed,
        connectIllegal,
        flags: {
          selected,
          hovered,
          impact,
          connectLegal,
          connectSource,
          connectIllegal,
        },
      });

      this.upsert("corner", "path", false, container);
      this.upsert(
        "leftHandle",
        "circle",
        handleVisible
          ? {
              cx: anchor.left,
              cy: anchor.centerY,
              r: 5,
              fill: handleFill,
              stroke: handleStroke,
              lineWidth: 1.5,
              opacity: connectIllegal ? 0.45 : 1,
              cursor: "crosshair",
            }
          : false,
        container,
      );
      this.upsert(
        "rightHandle",
        "circle",
        handleVisible
          ? {
              cx: anchor.right,
              cy: anchor.centerY,
              r: 5,
              fill: handleFill,
              stroke: handleStroke,
              lineWidth: 1.5,
              opacity: connectIllegal ? 0.45 : 1,
              cursor: "crosshair",
            }
          : false,
        container,
      );
      this.upsert(
        "legalBadge",
        "text",
        connectLegal
          ? {
              x: left + width - 8,
              y: top + height - 10,
              text: "LEGAL",
              fontFamily: cssVar("--font-display", "sans-serif"),
              fontSize: 8,
              fontWeight: 800,
              fill: "#052e16",
              textAlign: "right",
              textBaseline: "middle",
              pointerEvents: "none",
            }
          : false,
        container,
      );
      clearPortGlyphs(this, container);
    }
  }

  class VarkaEdge extends BaseEdge {
    getKeyPath(attributes) {
      const routeStart = attributes.routeStart;
      const routeEnd = attributes.routeEnd;
      let sourcePoint;
      let targetPoint;
      try {
        [sourcePoint, targetPoint] = this.getEndpoints(attributes);
      } catch {
        sourcePoint = null;
        targetPoint = null;
      }
      const source =
        Number.isFinite(Number(routeStart?.x)) && Number.isFinite(Number(routeStart?.y))
          ? [Number(routeStart.x), Number(routeStart.y)]
          : sourcePoint || [0, 0];
      const target =
        Number.isFinite(Number(routeEnd?.x)) && Number.isFinite(Number(routeEnd?.y))
          ? [Number(routeEnd.x), Number(routeEnd.y)]
          : targetPoint || [0, 0];
      const pins = Array.isArray(attributes.pinPoints) ? attributes.pinPoints : [];
      const path = [["M", source[0], source[1]]];
      if (pins.length) {
        pins.forEach((pin) => {
          const x = Number(pin?.x);
          const y = Number(pin?.y);
          if (Number.isFinite(x) && Number.isFinite(y)) {
            path.push(["L", x, y]);
          }
        });
      } else {
        const exitX = source[0] + 42;
        const entryX = target[0] - 42;
        const midX =
          entryX > exitX
            ? Math.round((exitX + entryX) / 2)
            : Math.round(Math.max(source[0], target[0]) + 128);
        path.push(["L", midX, source[1]]);
        path.push(["L", midX, target[1]]);
      }
      path.push(["L", target[0], target[1]]);
      return path;
    }

    getKeyStyle(attributes) {
      const stateSet = states(attributes);
      const selected = attributes.selected || stateSet.has("selected");
      const hovered = attributes.hovered || attributes.hover || stateSet.has("hover");
      const impactFocal = attributes.impactFocal || stateSet.has("impact-focal");
      const focused = attributes.focused || stateSet.has("focus");
      const dimmed = !focused && (attributes.dimmed || stateSet.has("dimmed"));
      return {
        ...super.getKeyStyle(attributes),
        stroke: attributes.stroke || cssVar("--accent", "#00a6e0"),
        lineWidth: impactFocal
          ? 3.4
          : selected
            ? 2.6
            : hovered
              ? 2.45
              : Number(attributes.lineWidth) || 1.7,
        lineDash: attributes.lineDash,
        opacity: dimmed ? 0.3 : Number(attributes.opacity) || 0.9,
        shadowColor:
          impactFocal || selected || hovered
            ? cssVar("--accent-glow", "rgba(0,166,224,0.28)")
            : undefined,
        shadowBlur: impactFocal ? 12 : selected || hovered ? 6 : 0,
        cursor: "pointer",
        endArrow: attributes.endArrow === false ? false : true,
        startArrow: attributes.startArrow ? true : false,
      };
    }

    render(attributes = this.parsedAttributes, container) {
      super.render(attributes, container);
      const stateSet = states(attributes);
      const impactFocal = attributes.impactFocal || stateSet.has("impact-focal");
      const selected = attributes.selected || stateSet.has("selected");
      const hovered = attributes.hovered || attributes.hover || stateSet.has("hover");
      const emphasized = impactFocal || selected || hovered;
      const labelText = String(attributes.labelText || "");
      const pinPoints = Array.isArray(attributes.pinPoints) ? attributes.pinPoints : [];
      const showPins = Boolean(attributes.showPins && pinPoints.length);
      const path = this.getKeyPath(attributes);
      this.upsert(
        "edgeHalo",
        "path",
        emphasized
          ? {
              d: path,
              stroke: impactFocal
                ? cssVar("--accent-glow", "rgba(0,166,224,0.28)")
                : "rgba(15, 23, 42, 0.22)",
              lineWidth: impactFocal ? 11 : selected ? 8 : 6,
              opacity: 0.48,
              fill: "none",
              pointerEvents: "none",
            }
          : false,
        container,
      );
      const routeStart = attributes.routeStart;
      const routeEnd = attributes.routeEnd;
      [
        ["sourcePortCap", routeStart, attributes.stroke || cssVar("--accent", "#00a6e0")],
        ["targetPortCap", routeEnd, attributes.stroke || cssVar("--accent", "#00a6e0")],
      ].forEach(([name, point, stroke]) => {
        const x = Number(point?.x);
        const y = Number(point?.y);
        this.upsert(
          name,
          "circle",
          emphasized && Number.isFinite(x) && Number.isFinite(y)
            ? {
                cx: x,
                cy: y,
                r: selected ? 3.8 : 3,
                fill: canvasBackgroundColor(),
                stroke,
                lineWidth: selected ? 1.8 : 1.2,
                opacity: 1,
                pointerEvents: "none",
              }
            : false,
          container,
        );
      });
      for (let index = 0; index < 8; index += 1) {
        const pin = showPins ? pinPoints[index] : null;
        const x = Number(pin?.x);
        const y = Number(pin?.y);
        this.upsert(
          `pin-${index}`,
          "circle",
          pin && Number.isFinite(x) && Number.isFinite(y)
            ? {
                cx: x,
                cy: y,
                r: selected ? 5.5 : 4.5,
                fill: canvasBackgroundColor(),
                stroke: selected
                  ? cssVar("--accent-select", "#5ecbff")
                  : cssVar("--accent", "#00a6e0"),
                lineWidth: selected ? 2 : 1.5,
                pointerEvents: "none",
              }
            : false,
          container,
        );
      }
      if (!labelText) {
        this.upsert("label", "text", false, container);
        return;
      }
      const points = path
        .filter((entry) => entry[0] === "M" || entry[0] === "L")
        .map((entry) => ({ x: Number(entry[1]), y: Number(entry[2]) }));
      const midpoint = pathMidpoint(points);
      this.upsert(
        "label",
        "text",
        {
          x: midpoint.x,
          y: midpoint.y - 8,
          text: truncate(labelText, 32),
          fontFamily: cssVar("--font-ui", "sans-serif"),
          fontSize: attributes.selected ? 11 : 10,
          fontWeight: attributes.selected ? 800 : 700,
          fill: attributes.selected
            ? cssVar("--accent-light", "#7bd0ff")
            : cssVar("--muted", "#98a8c0"),
          stroke: canvasBackgroundColor(),
          lineWidth: 4,
          paintOrder: "stroke",
          textAlign: "center",
          textBaseline: "middle",
          cursor: "pointer",
        },
        container,
      );
    }
  }

  register(ExtensionCategory.NODE, VARKA_NODE_TYPE, VarkaNode);
  register(ExtensionCategory.EDGE, VARKA_EDGE_TYPE, VarkaEdge);
  extensionsRegistered = true;
}

function createHost(container) {
  const host = container || el.g6EditorHost;
  if (host) {
    return host;
  }
  const next = document.createElement("div");
  next.id = "g6EditorHost";
  next.className = "g6-editor-host";
  el.canvasGrid?.prepend(next);
  el.g6EditorHost = next;
  return next;
}

function hostSize(host) {
  const rect = host?.getBoundingClientRect?.();
  const parentRect = host?.parentElement?.getBoundingClientRect?.();
  const width = Math.max(1, Math.round(rect?.width || parentRect?.width || 1));
  const height = Math.max(1, Math.round(rect?.height || parentRect?.height || 1));
  return { width, height };
}

function resizeGraphToHost() {
  if (!editor?.graph || !editor.host) {
    return;
  }
  const { width, height } = hostSize(editor.host);
  editor.graph.setSize?.(width, height);
  editor.graph.resize?.(width, height);
}

function readGraphZoom(fallback = state.viewport.scale || 1) {
  try {
    const reader = editor?.graph?.getZoom;
    if (typeof reader !== "function") {
      return fallback;
    }
    const zoom = reader.call(editor.graph);
    return Number.isFinite(zoom) ? zoom : fallback;
  } catch {
    return fallback;
  }
}

function readGraphPosition() {
  try {
    const reader = editor?.graph?.getPosition;
    if (typeof reader !== "function") {
      return null;
    }
    return reader.call(editor.graph) || null;
  } catch {
    return null;
  }
}

function currentZoom() {
  return readGraphZoom();
}

function mapperOptions() {
  const zoom = currentZoom();
  const edgeCount = state.diagram.connections.length;
  return {
    typeKey: state.activeType,
    detailLevel: detailLevelForZoom(zoom),
    showLabels: shouldShowEdgeLabels(zoom, edgeCount),
    selectedEdgeId: state.selectedConnectionId,
    hoveredEdgeId: editor?.hoveredEdgeId || "",
    ...(editor?.mapperOptions || {}),
  };
}

function spatialIndexFallbackSize() {
  return nodeSizeForDiagram(state.activeType);
}

function graphDataFromState(options = mapperOptions()) {
  return mapDiagramToG6({
    nodes: state.diagram.nodes,
    edges: state.diagram.connections,
    ...options,
  });
}

async function ensureTintedNode(node) {
  if (!node?.style?.iconSrc || !node?.style?.accent) {
    return node;
  }
  await primeIconTints([
    {
      src: node.style.iconSrc,
      color: node.style.accent,
    },
  ]);
  applyTintedIconsToNodes([node]);
  return node;
}

async function prepareGraphData(options = mapperOptions()) {
  const data = graphDataFromState(options);
  await primeIconTints(
    data.nodes.map((node) => ({
      src: node.style?.iconSrc,
      color: node.style?.accent,
    })),
  );
  applyTintedIconsToNodes(data.nodes);
  return data;
}

function rememberDataSnapshot(data) {
  const nodeFingerprints = new Map();
  const edgeFingerprints = new Map();
  const nodesById = new Map(
    data.nodes.map((node) => {
      nodeFingerprints.set(node.id, fingerprintElement(node));
      return [node.id, node];
    }),
  );
  const edgesById = new Map(
    data.edges.map((edge) => {
      edgeFingerprints.set(edge.id, fingerprintElement(edge));
      return [edge.id, edge];
    }),
  );
  editor.dataSnapshot = {
    nodesById,
    edgesById,
    nodeFingerprints,
    edgeFingerprints,
  };
  rebuildNodeTypeIndex(data.nodes);
}

function rebuildSpatialIndex(nodes = null) {
  if (!editor) {
    return;
  }
  if (!editor.spatialIndex) {
    editor.spatialIndex = createSpatialIndex([], {
      fallbackSize: spatialIndexFallbackSize(),
    });
  }
  const source = nodes || [...(editor.dataSnapshot?.nodesById?.values?.() || [])];
  editor.spatialIndex.rebuild(source, {
    fallbackSize: spatialIndexFallbackSize(),
  });
}

function ensureDataSnapshot() {
  if (!editor.dataSnapshot) {
    rememberDataSnapshot({ nodes: [], edges: [] });
  }
  return editor.dataSnapshot;
}

function nodeTypeFromData(nodeData) {
  return String(
    nodeData?.style?.nodeType ||
      nodeData?.data?.nodeType ||
      nodeData?.data?.source?.type ||
      nodeData?.data?.source?.eClass ||
      "",
  ).trim();
}

function forgetNodeType(nodeId) {
  if (!editor || !nodeId) {
    return;
  }
  const previousType = editor.nodeTypeById?.get?.(nodeId) || "";
  if (!previousType) {
    return;
  }
  const ids = editor.nodeIdsByType?.get?.(previousType);
  ids?.delete?.(nodeId);
  if (ids && !ids.size) {
    editor.nodeIdsByType.delete(previousType);
  }
  editor.nodeTypeById.delete(nodeId);
}

function rememberNodeType(nodeData) {
  if (!editor || !nodeData?.id) {
    return;
  }
  const nextType = nodeTypeFromData(nodeData);
  const previousType = editor.nodeTypeById?.get?.(nodeData.id) || "";
  if (previousType && previousType !== nextType) {
    forgetNodeType(nodeData.id);
    editor.connectStateKey = "";
  }
  if (!nextType) {
    return;
  }
  if (!editor.nodeIdsByType.has(nextType)) {
    editor.nodeIdsByType.set(nextType, new Set());
  }
  editor.nodeIdsByType.get(nextType).add(nodeData.id);
  editor.nodeTypeById.set(nodeData.id, nextType);
}

function rebuildNodeTypeIndex(nodes = []) {
  if (!editor) {
    return;
  }
  editor.nodeIdsByType.clear();
  editor.nodeTypeById.clear();
  nodes.forEach(rememberNodeType);
}

function hasKnownElement(id, map) {
  return Boolean(id && map?.has?.(id));
}

function isElementMounted(id) {
  if (!id || !editor?.graph?.getElementType) {
    return true;
  }
  try {
    return Boolean(editor.graph.getElementType(id));
  } catch {
    return false;
  }
}

function hasKnownNode(id) {
  return hasKnownElement(id, editor?.dataSnapshot?.nodesById) && isElementMounted(id);
}

function hasKnownEdge(id) {
  return hasKnownElement(id, editor?.dataSnapshot?.edgesById) && isElementMounted(id);
}

function setCanvasZoomIndicator() {
  const label = document.getElementById("canvasZoomValue");
  if (label) {
    label.textContent = `${Math.round((state.viewport.scale || 1) * 100)}%`;
  }
}

function updateViewportChrome() {
  el.canvasGrid?.style.setProperty("--viewport-scale", String(state.viewport.scale || 1));
  el.canvasGrid?.classList.toggle("lod-low", state.viewport.scale < 0.35);
  el.canvasGrid?.classList.toggle(
    "lod-medium",
    state.viewport.scale >= 0.35 && state.viewport.scale < 0.75,
  );
  el.canvasGrid?.classList.toggle("lod-high", state.viewport.scale >= 1.5);
}

function markViewportTransforming() {
  viewportTransforming = true;
  if (viewportTransformEndTimer) {
    window.clearTimeout(viewportTransformEndTimer);
  }
  viewportTransformEndTimer = window.setTimeout(() => {
    viewportTransformEndTimer = 0;
    viewportTransforming = false;
    runViewportSync();
  }, VIEWPORT_TRANSFORM_IDLE_MS);
}

function runViewportSync({ syncSelection = false, lightweight = false } = {}) {
  syncViewportStateFromGraph();
  setCanvasZoomIndicator();
  updateViewportChrome();
  scheduleMinimapRender();
  if (lightweight || viewportTransforming) {
    if (syncSelection) {
      updateG6Selection();
    }
    editor?.callbacks?.onViewportSynced?.();
    return;
  }
  // Remap only after viewport activity settles. Node detail and edge labels are
  // encoded in graph data, so CSS LOD classes alone cannot reveal them.
  updateG6Lod({ defer: true });
  updateG6ContextBoxes(null, { useCache: true });
  if (syncSelection) {
    updateG6Selection();
  }
  editor?.callbacks?.onViewportSynced?.();
}

function settleNativeViewport() {
  runViewportSync({ syncSelection: true });
}

function scheduleViewportSync({ lightweight = false } = {}) {
  if (pendingViewportSyncFrame) {
    return;
  }
  pendingViewportSyncFrame = window.requestAnimationFrame(() => {
    pendingViewportSyncFrame = 0;
    runViewportSync({ lightweight });
  });
}

function afterGraphViewport(result) {
  result?.then?.(settleNativeViewport)?.catch?.((error) =>
    updateDebugState({
      lastViewportError: error.message || String(error),
    }),
  );
  if (!result?.then) {
    window.requestAnimationFrame(settleNativeViewport);
  }
}

function numberFromPath(value, path) {
  let current = value;
  for (const key of path) {
    current = current?.[key];
  }
  const number = Number(current);
  return Number.isFinite(number) ? number : null;
}

function normalizeRenderBounds(raw) {
  if (!raw) {
    return null;
  }
  const minX =
    numberFromPath(raw, ["min", 0]) ??
    numberFromPath(raw, ["min", "x"]) ??
    numberFromPath(raw, ["minX"]) ??
    numberFromPath(raw, ["left"]) ??
    numberFromPath(raw, ["x"]);
  const minY =
    numberFromPath(raw, ["min", 1]) ??
    numberFromPath(raw, ["min", "y"]) ??
    numberFromPath(raw, ["minY"]) ??
    numberFromPath(raw, ["top"]) ??
    numberFromPath(raw, ["y"]);
  const maxX =
    numberFromPath(raw, ["max", 0]) ??
    numberFromPath(raw, ["max", "x"]) ??
    numberFromPath(raw, ["maxX"]) ??
    numberFromPath(raw, ["right"]);
  const maxY =
    numberFromPath(raw, ["max", 1]) ??
    numberFromPath(raw, ["max", "y"]) ??
    numberFromPath(raw, ["maxY"]) ??
    numberFromPath(raw, ["bottom"]);
  const width = numberFromPath(raw, ["width"]);
  const height = numberFromPath(raw, ["height"]);
  const resolvedMaxX = maxX ?? (minX !== null && width !== null ? minX + width : null);
  const resolvedMaxY = maxY ?? (minY !== null && height !== null ? minY + height : null);
  if (
    minX === null ||
    minY === null ||
    resolvedMaxX === null ||
    resolvedMaxY === null ||
    resolvedMaxX <= minX ||
    resolvedMaxY <= minY
  ) {
    return null;
  }
  return { minX, minY, maxX: resolvedMaxX, maxY: resolvedMaxY };
}

function renderedNodeBounds(nodeIds) {
  if (!editor?.graph || !Array.isArray(nodeIds) || !nodeIds.length) {
    return null;
  }
  let minX = Infinity;
  let minY = Infinity;
  let maxX = -Infinity;
  let maxY = -Infinity;
  let count = 0;
  nodeIds.forEach((nodeId) => {
    let raw = null;
    try {
      raw = editor.graph.getElementRenderBounds?.(nodeId);
    } catch {
      raw = null;
    }
    const bounds = normalizeRenderBounds(raw);
    if (!bounds) {
      return;
    }
    minX = Math.min(minX, bounds.minX);
    minY = Math.min(minY, bounds.minY);
    maxX = Math.max(maxX, bounds.maxX);
    maxY = Math.max(maxY, bounds.maxY);
    count += 1;
  });
  if (!count) {
    return null;
  }
  return {
    minX,
    minY,
    maxX,
    maxY,
    width: Math.max(1, maxX - minX),
    height: Math.max(1, maxY - minY),
  };
}

function minimapNodeCenter(node) {
  const style = node?.style || {};
  const x = Number(style.x);
  const y = Number(style.y);
  if (Number.isFinite(x) && Number.isFinite(y)) {
    return { x, y };
  }
  const source = node?.data?.source || {};
  const size = nodeSizeForDiagram(state.activeType, source);
  return {
    x: Number(source.x || 0) + size.width / 2,
    y: Number(source.y || 0) + size.height / 2,
  };
}

function minimapNodeSize(node) {
  const size = node?.style?.size;
  if (Array.isArray(size)) {
    return {
      width: Math.max(1, Number(size[0]) || 1),
      height: Math.max(1, Number(size[1]) || 1),
    };
  }
  return {
    width: Math.max(1, Number(node?.style?.width) || 1),
    height: Math.max(1, Number(node?.style?.height) || 1),
  };
}

function createMinimapPointMapper(bounds, rect) {
  const width = Math.max(1, rect?.width || 1);
  const height = Math.max(1, rect?.height || 1);
  const availableWidth = Math.max(1, width - MINIMAP_PADDING * 2);
  const availableHeight = Math.max(1, height - MINIMAP_PADDING * 2);
  const scale = Math.min(availableWidth / bounds.width, availableHeight / bounds.height);
  const contentWidth = bounds.width * scale;
  const contentHeight = bounds.height * scale;
  const offsetX = (width - contentWidth) / 2;
  const offsetY = (height - contentHeight) / 2;
  return {
    toMinimap(point) {
      return {
        x: offsetX + (point.x - bounds.minX) * scale,
        y: offsetY + (point.y - bounds.minY) * scale,
      };
    },
    toGraph(point) {
      return {
        x: bounds.minX + (point.x - offsetX) / scale,
        y: bounds.minY + (point.y - offsetY) / scale,
      };
    },
  };
}

function minimapGraphBounds(nodes) {
  if (!nodes.length) {
    return null;
  }
  let minX = Infinity;
  let minY = Infinity;
  let maxX = -Infinity;
  let maxY = -Infinity;
  nodes.forEach((node) => {
    const center = minimapNodeCenter(node);
    const size = minimapNodeSize(node);
    minX = Math.min(minX, center.x - size.width / 2);
    minY = Math.min(minY, center.y - size.height / 2);
    maxX = Math.max(maxX, center.x + size.width / 2);
    maxY = Math.max(maxY, center.y + size.height / 2);
  });
  const centerX = (minX + maxX) / 2;
  const centerY = (minY + maxY) / 2;
  const width = Math.max(MINIMAP_MIN_SPAN, maxX - minX);
  const height = Math.max(MINIMAP_MIN_SPAN, maxY - minY);
  return {
    minX: centerX - width / 2,
    minY: centerY - height / 2,
    maxX: centerX + width / 2,
    maxY: centerY + height / 2,
    width,
    height,
  };
}

function setMinimapVisible(visible) {
  el.canvasMinimap?.classList.toggle("hidden", !visible);
}

function renderMinimapWindow(mapper, minimapRect) {
  const viewportRect = el.canvasViewport?.getBoundingClientRect?.();
  const windowEl = el.canvasMinimapWindow;
  if (!viewportRect || !windowEl) {
    return;
  }
  const topLeft = toGraphCoordinates(viewportRect.left, viewportRect.top);
  const bottomRight = toGraphCoordinates(viewportRect.right, viewportRect.bottom);
  const miniA = mapper.toMinimap(topLeft);
  const miniB = mapper.toMinimap(bottomRight);
  const left = Math.max(0, Math.min(minimapRect.width, Math.min(miniA.x, miniB.x)));
  const top = Math.max(0, Math.min(minimapRect.height, Math.min(miniA.y, miniB.y)));
  const right = Math.max(0, Math.min(minimapRect.width, Math.max(miniA.x, miniB.x)));
  const bottom = Math.max(0, Math.min(minimapRect.height, Math.max(miniA.y, miniB.y)));
  windowEl.style.left = `${Math.round(left)}px`;
  windowEl.style.top = `${Math.round(top)}px`;
  windowEl.style.width = `${Math.max(8, Math.round(right - left))}px`;
  windowEl.style.height = `${Math.max(8, Math.round(bottom - top))}px`;
}

function renderCanvasMinimap() {
  const minimap = el.canvasMinimap;
  const svg = el.canvasMinimapSvg;
  if (!minimap || !svg || !editor?.dataSnapshot?.nodesById) {
    return;
  }
  const nodes = [...editor.dataSnapshot.nodesById.values()];
  const edges = [...(editor.dataSnapshot.edgesById?.values?.() || [])];
  if (!nodes.length) {
    setMinimapVisible(false);
    svg.replaceChildren();
    return;
  }
  setMinimapVisible(true);
  const rect = minimap.getBoundingClientRect();
  const width = Math.max(1, Math.round(rect.width || 1));
  const height = Math.max(1, Math.round(rect.height || 1));
  const bounds = minimapGraphBounds(nodes);
  const mapper = createMinimapPointMapper(bounds, { width, height });
  editor.minimapMapper = mapper;
  svg.setAttribute("viewBox", `0 0 ${width} ${height}`);
  const fragment = document.createDocumentFragment();
  const nodeCenters = new Map(nodes.map((node) => [node.id, minimapNodeCenter(node)]));
  edges.forEach((edge) => {
    const source = nodeCenters.get(edge.source);
    const target = nodeCenters.get(edge.target);
    if (!source || !target) {
      return;
    }
    const a = mapper.toMinimap(source);
    const b = mapper.toMinimap(target);
    const line = document.createElementNS("http://www.w3.org/2000/svg", "line");
    line.setAttribute("class", "canvas-minimap-edge");
    line.setAttribute("x1", String(a.x));
    line.setAttribute("y1", String(a.y));
    line.setAttribute("x2", String(b.x));
    line.setAttribute("y2", String(b.y));
    fragment.appendChild(line);
  });
  nodes.forEach((node) => {
    const point = mapper.toMinimap(minimapNodeCenter(node));
    const circle = document.createElementNS("http://www.w3.org/2000/svg", "circle");
    circle.setAttribute("class", "canvas-minimap-node");
    circle.setAttribute("cx", String(point.x));
    circle.setAttribute("cy", String(point.y));
    circle.setAttribute("r", String(MINIMAP_NODE_RADIUS));
    fragment.appendChild(circle);
  });
  svg.replaceChildren(fragment);
  renderMinimapWindow(mapper, { width, height });
}

function scheduleMinimapRender() {
  if (!editor || editor.minimapFrame) {
    return;
  }
  editor.minimapFrame = window.requestAnimationFrame(() => {
    if (!editor) {
      return;
    }
    editor.minimapFrame = 0;
    renderCanvasMinimap();
  });
}

function panFromMinimapPointer(event) {
  if (!editor?.minimapMapper || !el.canvasMinimap) {
    return;
  }
  const rect = el.canvasMinimap.getBoundingClientRect();
  const point = editor.minimapMapper.toGraph({
    x: Math.max(0, Math.min(rect.width, event.clientX - rect.left)),
    y: Math.max(0, Math.min(rect.height, event.clientY - rect.top)),
  });
  focusG6CanvasPoint(point.x, point.y);
  scheduleMinimapRender();
}

function bindCanvasMinimap() {
  const minimap = el.canvasMinimap;
  if (!editor || !minimap || editor.minimapCleanup) {
    return;
  }
  const stopMinimapPointer = (event) => {
    event.stopPropagation();
  };
  const onPointerDown = (event) => {
    event.preventDefault();
    event.stopPropagation();
    editor.minimapDragging = true;
    minimap.classList.add("is-dragging");
    minimap.setPointerCapture?.(event.pointerId);
    panFromMinimapPointer(event);
  };
  const onPointerMove = (event) => {
    event.stopPropagation();
    if (!editor?.minimapDragging) {
      return;
    }
    event.preventDefault();
    panFromMinimapPointer(event);
  };
  const finishDrag = (event) => {
    if (!editor) {
      return;
    }
    editor.minimapDragging = false;
    minimap.classList.remove("is-dragging");
    if (event?.pointerId !== undefined) {
      minimap.releasePointerCapture?.(event.pointerId);
    }
  };
  minimap.addEventListener("pointerdown", onPointerDown);
  minimap.addEventListener("pointerover", stopMinimapPointer);
  minimap.addEventListener("pointerenter", stopMinimapPointer);
  minimap.addEventListener("pointermove", onPointerMove);
  minimap.addEventListener("pointerleave", stopMinimapPointer);
  minimap.addEventListener("pointerup", finishDrag);
  minimap.addEventListener("pointercancel", finishDrag);
  minimap.addEventListener("lostpointercapture", finishDrag);
  editor.minimapCleanup = () => {
    minimap.removeEventListener("pointerdown", onPointerDown);
    minimap.removeEventListener("pointerover", stopMinimapPointer);
    minimap.removeEventListener("pointerenter", stopMinimapPointer);
    minimap.removeEventListener("pointermove", onPointerMove);
    minimap.removeEventListener("pointerleave", stopMinimapPointer);
    minimap.removeEventListener("pointerup", finishDrag);
    minimap.removeEventListener("pointercancel", finishDrag);
    minimap.removeEventListener("lostpointercapture", finishDrag);
  };
}

function defaultCanvasFitArea(rect) {
  if (!rect?.width || !rect?.height) {
    return null;
  }
  return {
    width: Math.max(1, rect.width - FIT_VIEW_PADDING * 2),
    height: Math.max(1, rect.height - FIT_VIEW_PADDING * 2),
    centerX: rect.width / 2,
    centerY: rect.height / 2,
  };
}

async function panGraphBy(dx, dy) {
  if (!editor?.graph) {
    return;
  }
  if (typeof editor.graph.translateBy === "function") {
    await editor.graph.translateBy([dx, dy], false);
    return;
  }
  const position = readGraphPosition();
  const x = Array.isArray(position) ? position[0] : position?.x;
  const y = Array.isArray(position) ? position[1] : position?.y;
  if (Number.isFinite(x) && Number.isFinite(y)) {
    await editor.graph.translateTo?.([x + dx, y + dy], false);
  }
}

function waitForViewportFrame() {
  if (typeof window === "undefined" || typeof window.requestAnimationFrame !== "function") {
    return Promise.resolve();
  }
  return new Promise((resolve) => window.requestAnimationFrame(resolve));
}

function canvasPointToViewport(x, y) {
  if (!editor?.graph) {
    return null;
  }
  let converted = null;
  try {
    converted = editor.graph.getViewportByCanvas?.([x, y]);
  } catch {
    try {
      converted = editor.graph.getViewportByCanvas?.({ x, y });
    } catch {
      converted = null;
    }
  }
  if (Array.isArray(converted)) {
    return { x: converted[0], y: converted[1] };
  }
  if (converted && Number.isFinite(Number(converted.x)) && Number.isFinite(Number(converted.y))) {
    return { x: Number(converted.x), y: Number(converted.y) };
  }
  const rect = el.canvasViewport?.getBoundingClientRect?.();
  const client = toClientCoordinates(x, y);
  if (!rect || !client) {
    return null;
  }
  return {
    x: client.x - rect.left,
    y: client.y - rect.top,
  };
}

async function panCanvasPointToViewportTarget(canvasX, canvasY, targetX, targetY, fitToken) {
  for (let index = 0; index < 6; index += 1) {
    if (fitToken !== editor?.viewportFitToken) {
      return;
    }
    const current = canvasPointToViewport(canvasX, canvasY);
    if (!current) {
      return;
    }
    const dx = Math.round(targetX - current.x);
    const dy = Math.round(targetY - current.y);
    if (Math.abs(dx) <= 1 && Math.abs(dy) <= 1) {
      return;
    }
    const beforeDistance = Math.hypot(dx, dy);
    await panGraphBy(dx, dy);
    await waitForViewportFrame();
    if (fitToken !== editor?.viewportFitToken) {
      return;
    }
    const after = canvasPointToViewport(canvasX, canvasY);
    if (!after) {
      return;
    }
    const afterDistance = Math.hypot(targetX - after.x, targetY - after.y);
    if (afterDistance > beforeDistance + 1) {
      await panGraphBy(-2 * dx, -2 * dy);
      await waitForViewportFrame();
    }
  }
}

function rememberNodeData(nodeData) {
  if (!nodeData?.id) {
    return;
  }
  const snapshot = ensureDataSnapshot();
  snapshot.nodesById.set(nodeData.id, nodeData);
  snapshot.nodeFingerprints.set(nodeData.id, fingerprintElement(nodeData));
  rememberNodeType(nodeData);
  editor.spatialIndex?.update?.(nodeData, {
    fallbackSize: spatialIndexFallbackSize(),
  });
}

function rememberEdgeData(edgeData) {
  if (!edgeData?.id) {
    return;
  }
  const snapshot = ensureDataSnapshot();
  snapshot.edgesById.set(edgeData.id, edgeData);
  snapshot.edgeFingerprints.set(edgeData.id, fingerprintElement(edgeData));
}

function edgeEndpointsReady(edge, nodesById) {
  const sourceId = String(edge?.source || edge?.sourceId || "");
  const targetId = String(edge?.target || edge?.targetId || "");
  return Boolean(sourceId && targetId && nodesById?.has?.(sourceId) && nodesById?.has?.(targetId));
}

function applyDiff(data) {
  const diff = diffGraphData(editor.dataSnapshot, data);
  const nextNodes = diff.snapshot?.nodesById || new Map();
  if (diff.removeEdgeIds.length) {
    editor.graph.removeEdgeData?.(diff.removeEdgeIds);
    diff.removeEdgeIds.forEach((id) => editor.edgeStateFlags.delete(id));
  }
  if (diff.removeNodeIds.length) {
    editor.graph.removeNodeData?.(diff.removeNodeIds);
    diff.removeNodeIds.forEach((id) => {
      editor.nodeStateFlags.delete(id);
      editor.connectStateIds.delete(id);
      forgetNodeType(id);
      editor.spatialIndex?.remove?.(id);
    });
  }
  if (diff.addNodes.length) {
    editor.graph.addNodeData?.(diff.addNodes);
  }
  const addEdges = diff.addEdges.filter((edge) => edgeEndpointsReady(edge, nextNodes));
  if (addEdges.length) {
    editor.graph.addEdgeData?.(addEdges);
  }
  if (diff.updateNodes.length) {
    editor.graph.updateNodeData?.(diff.updateNodes);
  }
  const updateEdges = diff.updateEdges.filter((edge) => edgeEndpointsReady(edge, nextNodes));
  if (updateEdges.length) {
    try {
      const result = editor.graph.updateEdgeData?.(updateEdges);
      result?.catch?.((error) =>
        updateDebugState({
          lastEdgeUpdateError: error.message || String(error),
        }),
      );
    } catch (error) {
      updateDebugState({ lastEdgeUpdateError: error.message || String(error) });
    }
  }
  if (diff.addNodes.length || diff.updateNodes.length || diff.removeNodeIds.length) {
    editor.connectStateKey = "";
    editor.contextBoxesDirty = true;
  }
  [...diff.addNodes, ...diff.updateNodes].forEach((node) =>
    editor.spatialIndex?.update?.(node, {
      fallbackSize: spatialIndexFallbackSize(),
    }),
  );
  editor.dataSnapshot = diff.snapshot;
  const topologyChanged =
    diff.addNodes.length ||
    diff.addEdges.length ||
    diff.removeNodeIds.length ||
    diff.removeEdgeIds.length;
  if (topologyChanged) {
    rebuildNodeTypeIndex([...diff.snapshot.nodesById.values()]);
    editor.adjacency = createAdjacencyIndex(state.diagram.connections);
  } else {
    diff.updateNodes.forEach((node) => rememberNodeData(node));
    diff.updateEdges.forEach((edge) => rememberEdgeData(edge));
  }
  if (
    diff.addNodes.length ||
    diff.addEdges.length ||
    diff.removeNodeIds.length ||
    diff.removeEdgeIds.length
  ) {
    scheduleGraphRender(editor.graph);
  } else {
    scheduleGraphDraw(editor.graph);
  }
}

function setFlag(map, id, flag, enabled) {
  if (!id) {
    return false;
  }
  const flags = new Set(map.get(id) || []);
  const had = flags.has(flag);
  if (enabled) {
    flags.add(flag);
  } else {
    flags.delete(flag);
  }
  if (!flags.size) {
    map.delete(id);
  } else {
    map.set(id, flags);
  }
  return had !== enabled;
}

function flushElementStates(ids, map) {
  const batch = {};
  ids.forEach((id) => {
    const isNodeState = map === editor.nodeStateFlags;
    const exists = isNodeState ? hasKnownNode(id) : hasKnownEdge(id);
    if (!exists) {
      map.delete(id);
      return;
    }
    batch[id] = ["normal", ...(map.get(id) || [])];
  });
  if (Object.keys(batch).length) {
    try {
      const result = editor.graph.setElementState?.(batch, false);
      result?.catch?.((error) =>
        updateDebugState({
          lastStateError: error.message || String(error),
        }),
      );
    } catch (error) {
      updateDebugState({ lastStateError: error.message || String(error) });
    }
  }
}

function replaceFlagSet(map, flag, ids) {
  const next = new Set(ids || []);
  const changed = new Set();
  map.forEach((flags, id) => {
    if (flags.has(flag) && !next.has(id)) {
      if (setFlag(map, id, flag, false)) {
        changed.add(id);
      }
    }
  });
  next.forEach((id) => {
    if (setFlag(map, id, flag, true)) {
      changed.add(id);
    }
  });
  return changed;
}

function symmetricDifference(left = new Set(), right = new Set()) {
  const changed = new Set();
  left.forEach((value) => {
    if (!right.has(value)) {
      changed.add(value);
    }
  });
  right.forEach((value) => {
    if (!left.has(value)) {
      changed.add(value);
    }
  });
  return changed;
}

function hoverFocusSets(nodeId) {
  const nodeIds = new Set();
  const edgeIds = new Set();
  if (!nodeId) {
    return { nodeIds, edgeIds };
  }
  nodeIds.add(nodeId);
  const adjacentEdgeIds = editor.adjacency?.byNode?.get(nodeId) || new Set();
  if (adjacentEdgeIds.size > HOVER_FOCUS_EDGE_LIMIT) {
    return { nodeIds, edgeIds };
  }
  adjacentEdgeIds.forEach((edgeId) => {
    edgeIds.add(edgeId);
    const edge =
      editor.adjacency?.byId?.get(edgeId) ||
      state.diagram.connections.find((item) => item.id === edgeId);
    if (edge?.sourceId) {
      nodeIds.add(edge.sourceId);
    }
    if (edge?.targetId) {
      nodeIds.add(edge.targetId);
    }
  });
  return { nodeIds, edgeIds };
}

function clearHoverFlagSet(map, id, flags) {
  let changed = false;
  flags.forEach((flag) => {
    if (setFlag(map, id, flag, false)) {
      changed = true;
    }
  });
  return changed;
}

function clearG6HoverFocusState() {
  if (!editor?.graph) {
    return;
  }
  const changedNodes = new Set();
  const changedEdges = new Set();
  const previous = editor.hoveredNodeId;
  const nodeIds = new Set(editor.hoverFocusNodeIds || []);
  const edgeIds = new Set(editor.hoverFocusEdgeIds || []);
  if (previous) {
    nodeIds.add(previous);
    setG6NodeHandleVisibility(previous, false);
  }
  nodeIds.forEach((id) => {
    if (clearHoverFlagSet(editor.nodeStateFlags, id, ["hover", "focus"])) {
      changedNodes.add(id);
    }
  });
  edgeIds.forEach((id) => {
    if (clearHoverFlagSet(editor.edgeStateFlags, id, ["hover", "focus"])) {
      changedEdges.add(id);
    }
  });
  editor.hoveredNodeId = null;
  editor.hoverFocusNodeIds = new Set();
  editor.hoverFocusEdgeIds = new Set();
  flushElementStates(changedNodes, editor.nodeStateFlags);
  flushElementStates(changedEdges, editor.edgeStateFlags);
  if (changedEdges.size) {
    refreshG6Edges([...changedEdges]);
  }
  if (changedNodes.size || changedEdges.size) {
    scheduleGraphDraw(editor.graph);
  }
}

function syncViewportStateFromGraph() {
  if (!editor?.graph) {
    return;
  }
  const position = readGraphPosition();
  const zoom = readGraphZoom();
  if (position) {
    const x = Array.isArray(position) ? position[0] : position.x;
    const y = Array.isArray(position) ? position[1] : position.y;
    if (Number.isFinite(x) && Number.isFinite(y)) {
      state.viewport.x = x;
      state.viewport.y = y;
    }
  }
  if (Number.isFinite(zoom)) {
    state.viewport.scale = zoom;
  }
  setCanvasZoomIndicator();
}

export function mountG6Editor(container, { callbacks = {}, mapper = {} } = {}) {
  installDebugProbe();
  if (editor?.graph) {
    editor.callbacks = callbacks;
    editor.mapperOptions = mapper;
    updateDebugState({ lastMount: "reused" });
    return editor;
  }
  if (!isG6Available()) {
    updateDebugState({ lastError: "AntV G6 is not available" });
    throw new Error("AntV G6 is not available");
  }
  try {
    registerVarkaG6Extensions();
  } catch (error) {
    console.warn("Varka G6 custom extensions unavailable; using built-in G6 shapes", error);
    updateDebugState({
      lastExtensionError: error.message || String(error),
    });
  }
  const api = g6();
  const host = createHost(container);
  host.classList.add("is-mounted");
  const { width, height } = hostSize(host);
  const graph = new api.Graph({
    container: host,
    width,
    height,
    autoResize: true,
    animation: false,
    theme: "dark",
    zoomRange: [0.01, 2.5],
    cursor: "grab",
    // At close zoom levels, only a small part of a large model is visible.
    // Culling prevents offscreen nodes and edges from being repainted on pan.
    canvas: {
      enableCulling: true,
    },
    data: { nodes: [], edges: [] },
    behaviors: ["zoom-canvas"],
    node: {
      type: G6_BASE_NODE_TYPE,
      state: {
        normal: {
          selected: false,
          hover: false,
          focused: false,
          dimmed: false,
          connectSource: false,
          connectLegal: false,
          connectIllegal: false,
          openControlHover: false,
        },
        selected: { selected: true },
        hover: { hover: true },
        focus: { focused: true },
        dimmed: { dimmed: true },
        "connect-source": { connectSource: true },
        "connect-legal": { connectLegal: true },
        "connect-illegal": { connectIllegal: true },
        "open-control-hover": { openControlHover: true },
        "context-draft": { contextDraft: true },
        "impact-focal": { impactFocal: true },
        "impact-upstream": { impactUpstream: true },
        "impact-downstream": { impactDownstream: true },
        "impact-connected": { impactConnected: true },
      },
    },
    edge: {
      type: G6_BASE_EDGE_TYPE,
      state: {
        normal: {
          selected: false,
          hovered: false,
          focused: false,
          dimmed: false,
          impactFocal: false,
        },
        selected: { selected: true },
        hover: { hovered: true },
        focus: { focused: true },
        dimmed: { dimmed: true },
        "impact-focal": { impactFocal: true },
      },
    },
  });
  editor = {
    graph,
    host,
    callbacks,
    mapperOptions: mapper,
    dataSnapshot: null,
    nodeStateFlags: new Map(),
    edgeStateFlags: new Map(),
    nodeIdsByType: new Map(),
    nodeTypeById: new Map(),
    connectStateIds: new Set(),
    connectStateKey: "",
    hoveredNodeId: null,
    hoveredEdgeId: null,
    hoverFocusNodeIds: new Set(),
    hoverFocusEdgeIds: new Set(),
    selectedNodeStateIds: new Set(),
    selectedEdgeStateIds: new Set(),
    adjacency: createAdjacencyIndex(state.diagram.connections),
    spatialIndex: createSpatialIndex([], {
      fallbackSize: spatialIndexFallbackSize(),
    }),
    contextBoxesCache: [],
    contextBoxesDirty: true,
    lastLod: detailLevelForZoom(state.viewport.scale),
    lastShowLabels: true,
    viewportReady: false,
    viewportRetryCount: 0,
    minimapFrame: 0,
    minimapMapper: null,
    minimapDragging: false,
    minimapCleanup: null,
  };
  editor.setOpenControlHover = (nodeId) => {
    const previous = editor.openControlHoverNodeId || null;
    const next = nodeId || null;
    if (previous === next) {
      return;
    }
    const changed = new Set();
    if (previous && setFlag(editor.nodeStateFlags, previous, "open-control-hover", false)) {
      changed.add(previous);
    }
    editor.openControlHoverNodeId = next;
    if (next && setFlag(editor.nodeStateFlags, next, "open-control-hover", true)) {
      changed.add(next);
    }
    flushElementStates(changed, editor.nodeStateFlags);
    if (changed.size) {
      scheduleGraphDraw(editor.graph);
    }
  };
  bindG6Interactions(editor, callbacks);
  bindCanvasMinimap();
  ensureIconTintListener();
  resizeGraphToHost();
  scheduleMinimapRender();
  updateDebugState({ lastError: "", lastMount: "mounted", hostSize: { width, height } });
  return editor;
}

export function destroyG6Editor() {
  cancelScheduledDraw();
  if (pendingViewportFrame) {
    window.cancelAnimationFrame(pendingViewportFrame);
    pendingViewportFrame = 0;
  }
  if (pendingViewportSyncFrame) {
    window.cancelAnimationFrame(pendingViewportSyncFrame);
    pendingViewportSyncFrame = 0;
  }
  if (viewportTransformEndTimer) {
    window.clearTimeout(viewportTransformEndTimer);
    viewportTransformEndTimer = 0;
  }
  viewportTransforming = false;
  cancelPendingLodUpdate();
  clearG6Overlays();
  if (editor?.minimapFrame) {
    window.cancelAnimationFrame(editor.minimapFrame);
  }
  editor?.minimapCleanup?.();
  editor?.disposeInteractions?.();
  editor?.graph?.destroy?.();
  editor?.host?.classList.remove("is-mounted");
  editor = null;
}

export function getG6Editor() {
  return editor;
}

export function renderG6Diagram() {
  return syncG6FromState({ full: !editor?.dataSnapshot });
}

export function setG6Data(nodes, edges) {
  if (!editor?.graph) {
    updateDebugState({ lastError: "setG6Data before mount" });
    return;
  }
  updateDebugState({ lastSetData: { nodes: nodes.length, edges: edges.length } });
  resizeGraphToHost();
  void (async () => {
    await primeIconTints(
      nodes.map((node) => ({
        src: node.style?.iconSrc,
        color: node.style?.accent,
      })),
    );
    applyTintedIconsToNodes(nodes);
    if (!editor?.graph) {
      return;
    }
    const data = { nodes, edges };
    editor.graph.setData?.(data);
    rememberDataSnapshot(data);
    rebuildSpatialIndex(data.nodes);
    editor.contextBoxesDirty = true;
    scheduleGraphRender(editor.graph);
    scheduleMinimapRender();
    updateG6NodeIcons();
  })();
}

export function syncG6FromState({ full = false } = {}) {
  if (!editor?.graph) {
    updateDebugState({ lastError: "syncG6FromState before mount" });
    return;
  }
  const options = mapperOptions();
  cancelPendingLodUpdate();
  editor.lastLod = options.detailLevel;
  editor.lastShowLabels = options.showLabels;
  resizeGraphToHost();
  void prepareGraphData(options).then((data) => {
    if (!editor?.graph) {
      return;
    }
    updateDebugState({ lastSync: { full, nodes: data.nodes.length, edges: data.edges.length } });
    if (full || !editor.dataSnapshot) {
      editor.graph.setData?.(data);
      rememberDataSnapshot(data);
      rebuildSpatialIndex(data.nodes);
      editor.adjacency = createAdjacencyIndex(state.diagram.connections);
      editor.connectStateKey = "";
      editor.contextBoxesDirty = true;
      scheduleGraphRender(editor.graph);
    } else {
      applyDiff(data);
    }
    updateG6Selection();
    updateG6ConnectionState();
    updateG6ImpactState();
    updateG6NodeIcons();
    updateG6ContextBoxes();
    scheduleMinimapRender();
  });
}

export function addG6Node(node) {
  if (!editor?.graph || !node) {
    return;
  }
  const mapped = mapNodeToG6(node, mapperOptions());
  scheduleIncrementalCanvasMutation(() => {
    void ensureTintedNode(mapped).then((tinted) => {
      if (!editor?.graph) {
        return;
      }
      editor.graph.addNodeData?.([tinted]);
      rememberNodeData(tinted);
      editor.contextBoxesDirty = true;
      scheduleGraphDraw(editor.graph);
      updateG6NodeIcons();
    });
  });
}

export function updateG6Node(nodeId, patch = {}) {
  if (!editor?.graph || !nodeId) {
    return;
  }
  const node =
    state.nodesById.get(nodeId) || state.diagram.nodes.find((candidate) => candidate.id === nodeId);
  if (!node) {
    return;
  }
  Object.assign(node, patch);
  const mapped = mapNodeToG6(node, mapperOptions());
  void ensureTintedNode(mapped).then((tinted) => {
    if (!editor?.graph) {
      return;
    }
    editor.graph.updateNodeData?.([tinted]);
    rememberNodeData(tinted);
    editor.contextBoxesDirty = true;
    scheduleGraphDraw(editor.graph);
    updateG6NodeIcons();
  });
}

export function updateG6NodePosition(nodeId, x, y) {
  if (!editor?.graph || !nodeId) {
    return;
  }
  const node =
    state.nodesById.get(nodeId) || state.diagram.nodes.find((candidate) => candidate.id === nodeId);
  if (!node) {
    return;
  }
  node.x = Math.round(x);
  node.y = Math.round(y);
  const size = nodeSizeForDiagram(state.activeType, node);
  editor.graph.updateNodeData?.([
    {
      id: nodeId,
      style: {
        x: node.x + size.width / 2,
        y: node.y + size.height / 2,
      },
    },
  ]);
  rememberNodeData(mapNodeToG6(node, mapperOptions()));
  editor.spatialIndex?.update?.(
    {
      id: nodeId,
      x: node.x,
      y: node.y,
      width: size.width,
      height: size.height,
    },
    { fallbackSize: size },
  );
  editor.contextBoxesDirty = true;
  scheduleGraphDraw(editor.graph);
  updateG6NodeIcons();
}

function setG6NodeHandleVisibility(nodeId, visible) {
  if (!editor?.graph || !nodeId) {
    return;
  }
  const node =
    state.nodesById.get(nodeId) || state.diagram.nodes.find((candidate) => candidate.id === nodeId);
  if (!node) {
    return;
  }
  const mapped = mapNodeToG6({ ...node, showHandles: Boolean(visible) }, mapperOptions());
  editor.graph.updateNodeData?.([mapped]);
  rememberNodeData(mapped);
  scheduleGraphDraw(editor.graph);
}

export function removeG6Node(nodeId) {
  if (!editor?.graph || !nodeId || !hasKnownNode(nodeId)) {
    return;
  }
  editor.graph.removeNodeData?.([nodeId]);
  editor.dataSnapshot?.nodesById?.delete(nodeId);
  editor.dataSnapshot?.nodeFingerprints?.delete(nodeId);
  editor.nodeStateFlags.delete(nodeId);
  editor.connectStateIds.delete(nodeId);
  forgetNodeType(nodeId);
  editor.spatialIndex?.remove?.(nodeId);
  editor.contextBoxesDirty = true;
  scheduleGraphRender(editor.graph);
  updateG6NodeIcons();
}

export function addG6Edge(edge) {
  if (!editor?.graph || !edge) {
    return;
  }
  const mapped = mapEdgeToG6(edge, mapperOptions());
  scheduleIncrementalCanvasMutation(() => {
    if (!editor?.graph) {
      return;
    }
    const sourceId = String(mapped.source || edge.sourceId || "");
    const targetId = String(mapped.target || edge.targetId || "");
    if (!sourceId || !targetId) {
      return;
    }
    const endpointReady = (nodeId) =>
      hasKnownNode(nodeId) ||
      (typeof editor.graph.getElementDataById === "function" &&
        Boolean(editor.graph.getElementDataById(nodeId)));
    if (!endpointReady(sourceId) || !endpointReady(targetId)) {
      return;
    }
    editor.graph.addEdgeData?.([mapped]);
    editor.adjacency.add(edge);
    rememberEdgeData(mapped);
    scheduleGraphDraw(editor.graph);
  });
}

export function updateG6Edge(edgeId, patch = {}) {
  if (!editor?.graph || !edgeId) {
    return;
  }
  const edge =
    editor.adjacency?.byId?.get(edgeId) ||
    state.diagram.connections.find((item) => item.id === edgeId);
  if (!edge) {
    return;
  }
  Object.assign(edge, patch);
  const mapped = mapEdgeToG6(edge, mapperOptions());
  editor.graph.updateEdgeData?.([mapped]);
  rememberEdgeData(mapped);
  scheduleGraphDraw(editor.graph);
}

export function removeG6Edge(edgeId) {
  if (!editor?.graph || !edgeId || !hasKnownEdge(edgeId)) {
    return;
  }
  editor.graph.removeEdgeData?.([edgeId]);
  editor.dataSnapshot?.edgesById?.delete(edgeId);
  editor.dataSnapshot?.edgeFingerprints?.delete(edgeId);
  editor.edgeStateFlags.delete(edgeId);
  editor.adjacency.remove(edgeId);
  scheduleGraphRender(editor.graph);
}

export function refreshG6Edges(edgeIds = []) {
  if (!editor?.graph) {
    return;
  }
  const sourceEdges = edgeIds?.length
    ? edgeIds.map((id) => editor.adjacency?.byId?.get(id)).filter(Boolean)
    : state.diagram.connections;
  const edges = sourceEdges.map((edge) => mapEdgeToG6(edge, mapperOptions()));
  if (edges.length) {
    const existingEdges = edges.filter((edge) => hasKnownEdge(edge.id));
    if (!existingEdges.length) {
      return;
    }
    try {
      const result = editor.graph.updateEdgeData?.(existingEdges);
      result?.catch?.((error) =>
        updateDebugState({
          lastEdgeUpdateError: error.message || String(error),
        }),
      );
    } catch (error) {
      updateDebugState({ lastEdgeUpdateError: error.message || String(error) });
      return;
    }
    existingEdges.forEach(rememberEdgeData);
    scheduleGraphDraw(editor.graph);
  }
}

export function updateG6Selection() {
  if (!editor?.graph) {
    return;
  }
  const nextNodeIds = new Set(state.selectedNodeIds || []);
  const nextEdgeIds = new Set(state.selectedConnectionId ? [state.selectedConnectionId] : []);
  const changedNodes = symmetricDifference(editor.selectedNodeStateIds, nextNodeIds);
  const changedEdges = symmetricDifference(editor.selectedEdgeStateIds, nextEdgeIds);
  changedNodes.forEach((id) => setFlag(editor.nodeStateFlags, id, "selected", nextNodeIds.has(id)));
  changedEdges.forEach((id) => setFlag(editor.edgeStateFlags, id, "selected", nextEdgeIds.has(id)));
  editor.selectedNodeStateIds = nextNodeIds;
  editor.selectedEdgeStateIds = nextEdgeIds;
  flushElementStates(changedNodes, editor.nodeStateFlags);
  flushElementStates(changedEdges, editor.edgeStateFlags);
  if (changedEdges.size) {
    refreshG6Edges([...changedEdges]);
  }
  if (changedNodes.size) {
    scheduleGraphDraw(editor.graph);
  }
}

function idsByTypes(types = [], { excludeId = "", limit = Number.POSITIVE_INFINITY } = {}) {
  const ids = [];
  let overflow = false;
  for (const type of types) {
    const bucket = editor?.nodeIdsByType?.get?.(type);
    if (!bucket?.size) {
      continue;
    }
    for (const id of bucket) {
      if (id === excludeId) {
        continue;
      }
      if (ids.length >= limit) {
        overflow = true;
        return { ids, overflow };
      }
      ids.push(id);
    }
  }
  return { ids, overflow };
}

function connectionStateForNode(source, node) {
  if (!source || !node) {
    return "";
  }
  return editor.callbacks?.connectionTargetState?.(source, node) === "legal"
    ? "connect-legal"
    : "connect-illegal";
}

function legalConnectionTargetTypes(source) {
  const availableTypes = [...(editor?.nodeIdsByType?.keys?.() || [])];
  const result = editor.callbacks?.connectionTargetTypes?.(source, { availableTypes });
  return Array.isArray(result) ? result.filter(Boolean) : null;
}

function clearG6ConnectionFlags() {
  if (!editor?.graph) {
    return;
  }
  const changed = new Set(editor.connectStateIds);
  editor.nodeStateFlags.forEach((flags, id) => {
    if (flags.has("connect-source") || flags.has("connect-legal") || flags.has("connect-illegal")) {
      changed.add(id);
    }
  });
  changed.forEach((id) => {
    ["connect-source", "connect-legal", "connect-illegal"].forEach((flag) => {
      setFlag(editor.nodeStateFlags, id, flag, false);
    });
  });
  editor.connectStateIds.clear();
  editor.connectStateKey = "";
  flushElementStates(changed, editor.nodeStateFlags);
}

export function updateG6ConnectionState() {
  if (!editor?.graph) {
    return;
  }
  const sourceId = state.connectSourceId || state.linkDrag?.sourceId || "";
  const hoverTargetId =
    state.linkDrag?.hoveredTargetId || (state.connectSourceId ? state.hoveredNodeId || "" : "");
  const nextKey = sourceId
    ? [
        sourceId,
        hoverTargetId,
        state.preferredConnectionKind || "",
        state.activeType,
        state.nodesById.size,
      ].join("|")
    : "";
  if (!sourceId) {
    clearG6ConnectionFlags();
    return;
  }
  if (nextKey === editor.connectStateKey) {
    return;
  }
  editor.connectStateKey = nextKey;
  const changed = new Set();
  editor.connectStateIds.forEach((id) => {
    ["connect-source", "connect-legal", "connect-illegal"].forEach((flag) => {
      if (setFlag(editor.nodeStateFlags, id, flag, false)) {
        changed.add(id);
      }
    });
  });
  editor.connectStateIds.clear();
  if (sourceId) {
    const source = state.nodesById.get(sourceId);
    editor.connectStateIds.add(sourceId);
    if (setFlag(editor.nodeStateFlags, sourceId, "connect-source", true)) {
      changed.add(sourceId);
    }
    const targetTypes = source ? legalConnectionTargetTypes(source) : null;
    let globallyMarkedTargetIds = new Set();
    if (targetTypes) {
      const { ids, overflow } = idsByTypes(targetTypes, {
        excludeId: sourceId,
        limit: CONNECT_GLOBAL_TARGET_NODE_LIMIT + 1,
      });
      if (!overflow && ids.length <= CONNECT_GLOBAL_TARGET_NODE_LIMIT) {
        globallyMarkedTargetIds = new Set(ids);
        ids.forEach((id) => {
          editor.connectStateIds.add(id);
          if (setFlag(editor.nodeStateFlags, id, "connect-legal", true)) {
            changed.add(id);
          }
        });
      }
    }
    const showIllegalStates = state.nodesById.size <= CONNECT_ILLEGAL_STATE_NODE_LIMIT;
    if (!targetTypes && showIllegalStates) {
      state.nodesById.forEach((node, id) => {
        let flag = "";
        if (id === sourceId) {
          flag = "connect-source";
        } else if (source) {
          flag = connectionStateForNode(source, node);
        }
        if (flag) {
          editor.connectStateIds.add(id);
          if (setFlag(editor.nodeStateFlags, id, flag, true)) {
            changed.add(id);
          }
        }
      });
    }
    if (
      hoverTargetId &&
      hoverTargetId !== sourceId &&
      !globallyMarkedTargetIds.has(hoverTargetId)
    ) {
      const hoverNode = state.nodesById.get(hoverTargetId);
      const hoverFlag = connectionStateForNode(source, hoverNode);
      if (hoverFlag) {
        editor.connectStateIds.add(hoverTargetId);
        if (setFlag(editor.nodeStateFlags, hoverTargetId, hoverFlag, true)) {
          changed.add(hoverTargetId);
        }
      }
    }
  }
  flushElementStates(changed, editor.nodeStateFlags);
}

export function setG6HoverNode(nodeId) {
  const normalizedNodeId = nodeId || null;
  if (!editor?.graph) {
    return;
  }
  if (!normalizedNodeId) {
    clearG6HoverFocusState();
    return;
  }
  if (
    editor.hoveredNodeId === normalizedNodeId &&
    (normalizedNodeId || (!editor.hoverFocusNodeIds?.size && !editor.hoverFocusEdgeIds?.size))
  ) {
    return;
  }
  const changedNodes = new Set();
  const changedEdges = new Set();
  const previous = editor.hoveredNodeId;
  const previousFocusNodeIds = editor.hoverFocusNodeIds || new Set();
  const previousFocusEdgeIds = editor.hoverFocusEdgeIds || new Set();
  editor.hoveredNodeId = normalizedNodeId;
  if (previous) {
    if (setFlag(editor.nodeStateFlags, previous, "hover", false)) {
      changedNodes.add(previous);
    }
    setG6NodeHandleVisibility(previous, false);
  }
  const nextFocus = hoverFocusSets(normalizedNodeId);
  symmetricDifference(previousFocusNodeIds, nextFocus.nodeIds).forEach((id) => {
    if (setFlag(editor.nodeStateFlags, id, "focus", nextFocus.nodeIds.has(id))) {
      changedNodes.add(id);
    }
  });
  symmetricDifference(previousFocusEdgeIds, nextFocus.edgeIds).forEach((id) => {
    const focused = nextFocus.edgeIds.has(id);
    if (setFlag(editor.edgeStateFlags, id, "focus", focused)) {
      changedEdges.add(id);
    }
    if (setFlag(editor.edgeStateFlags, id, "hover", focused)) {
      changedEdges.add(id);
    }
  });
  editor.hoverFocusNodeIds = nextFocus.nodeIds;
  editor.hoverFocusEdgeIds = nextFocus.edgeIds;
  if (normalizedNodeId) {
    if (setFlag(editor.nodeStateFlags, normalizedNodeId, "hover", true)) {
      changedNodes.add(normalizedNodeId);
    }
    setG6NodeHandleVisibility(normalizedNodeId, true);
  }
  flushElementStates(changedNodes, editor.nodeStateFlags);
  flushElementStates(changedEdges, editor.edgeStateFlags);
  if (changedEdges.size) {
    refreshG6Edges([...changedEdges]);
  }
}

export function setG6HoverEdge(edgeId) {
  if (!editor?.graph || editor.hoveredEdgeId === edgeId) {
    return;
  }
  const changed = new Set();
  if (
    editor.hoveredEdgeId &&
    setFlag(editor.edgeStateFlags, editor.hoveredEdgeId, "hover", false)
  ) {
    changed.add(editor.hoveredEdgeId);
  }
  editor.hoveredEdgeId = edgeId || null;
  if (edgeId && setFlag(editor.edgeStateFlags, edgeId, "hover", true)) {
    changed.add(edgeId);
  }
  flushElementStates(changed, editor.edgeStateFlags);
  if (changed.size) {
    refreshG6Edges([...changed]);
  }
}

export function updateG6ImpactState() {
  if (!editor?.graph) {
    return;
  }
  const focal = new Set();
  const focalEdges = new Set();
  const upstream = new Set();
  const downstream = new Set();
  const connected = new Set();
  if (state.issueLocateTargetId) {
    const targetId = state.issueLocateTargetId;
    if (state.diagram?.connections?.some?.((edge) => edge.id === targetId)) {
      focalEdges.add(targetId);
    } else {
      focal.add(targetId);
    }
  }
  if (state.impactMode && state.impactData) {
    if (state.impactData.focalElement?.elementId) {
      focal.add(state.impactData.focalElement.elementId);
    }
    (state.impactData.upstream || []).forEach((item) => {
      if (item.elementId) {
        upstream.add(item.elementId);
      }
    });
    (state.impactData.downstream || []).forEach((item) => {
      if (item.elementId) {
        downstream.add(item.elementId);
      }
    });
    (state.impactData.connectedElements || []).forEach((item) => {
      if (item.elementId) {
        connected.add(item.elementId);
      }
    });
  }
  const changed = new Set();
  [
    ["impact-focal", focal],
    ["impact-upstream", upstream],
    ["impact-downstream", downstream],
    ["impact-connected", connected],
  ].forEach(([flag, ids]) => {
    replaceFlagSet(editor.nodeStateFlags, flag, ids).forEach((id) => changed.add(id));
  });
  flushElementStates(changed, editor.nodeStateFlags);
  const changedEdges = replaceFlagSet(editor.edgeStateFlags, "impact-focal", focalEdges);
  flushElementStates(changedEdges, editor.edgeStateFlags);
  if (changedEdges.size) {
    refreshG6Edges([...changedEdges]);
  }
}

export function updateG6Viewport() {
  if (!editor?.graph) {
    return;
  }
  const retryViewport = (error) => {
    updateDebugState({ lastViewportError: error.message || String(error) });
    if (editor.viewportRetryCount < 3 && !pendingViewportFrame) {
      editor.viewportRetryCount += 1;
      pendingViewportFrame = window.requestAnimationFrame(() => {
        pendingViewportFrame = 0;
        updateG6Viewport();
      });
    }
  };
  const applyTransform = () => {
    if (!editor?.graph) {
      return;
    }
    try {
      const zoomResult = editor.graph.zoomTo?.(state.viewport.scale, false);
      zoomResult?.catch?.(retryViewport);
      const translateResult = editor.graph.translateTo?.(
        [state.viewport.x, state.viewport.y],
        false,
      );
      translateResult?.catch?.(retryViewport);
    } catch (error) {
      retryViewport(error);
      return;
    }
    editor.viewportReady = true;
  };
  applyTransform();
  runViewportSync({ syncSelection: true });
}

export function getG6Viewport() {
  syncViewportStateFromGraph();
  return { ...state.viewport };
}

export function zoomG6CanvasBy(multiplier = 1) {
  if (!editor?.graph) {
    return false;
  }
  const prev = readGraphZoom();
  const next = Math.max(FIT_VIEW_MIN_SCALE, Math.min(FIT_VIEW_MAX_SCALE, prev * multiplier));
  state.viewport.scale = next;
  setCanvasZoomIndicator();
  try {
    afterGraphViewport(editor.graph.zoomTo?.(next, false));
  } catch (error) {
    updateDebugState({ lastViewportError: error.message || String(error) });
  }
  return true;
}

export function resetG6CanvasView() {
  if (!editor?.graph) {
    return false;
  }
  state.viewport.scale = 1;
  setCanvasZoomIndicator();
  try {
    afterGraphViewport(editor.graph.zoomTo?.(1, false));
  } catch (error) {
    updateDebugState({ lastViewportError: error.message || String(error) });
  }
  return true;
}

export function fitG6CanvasToDiagram(bounds = null, { fit = false, fitArea = null } = {}) {
  if (!editor?.graph) {
    return false;
  }
  const diagramNodes = Array.isArray(state.diagram?.nodes) ? state.diagram.nodes : [];
  const graphNodes = [...(editor.dataSnapshot?.nodesById?.keys?.() || [])];
  if (!diagramNodes.length && !graphNodes.length) {
    return false;
  }
  const nodes = graphNodes.length
    ? graphNodes
    : diagramNodes.map((node) => node.id).filter(Boolean);
  const rect = el.canvasViewport?.getBoundingClientRect?.();

  try {
    const area = defaultCanvasFitArea(rect) || fitArea || getCanvasFitArea(rect);
    const fitBounds = bounds || (fit ? renderedNodeBounds(nodes) : null);
    const finiteBounds =
      fitBounds &&
      Number.isFinite(Number(fitBounds.minX)) &&
      Number.isFinite(Number(fitBounds.minY)) &&
      Number.isFinite(Number(fitBounds.width)) &&
      Number.isFinite(Number(fitBounds.height));
    if (!finiteBounds) {
      return false;
    }
    const rawScale =
      fit && area
        ? Math.min(area.width / fitBounds.width, area.height / fitBounds.height) || 1
        : state.viewport.scale || readGraphZoom() || 1;
    const nodeCount = diagramNodes.length || graphNodes.length;
    const maxScale =
      nodeCount <= 1
        ? FIT_VIEW_SINGLE_NODE_SCALE
        : nodeCount <= 8
          ? FIT_VIEW_SPARSE_NODE_MAX_SCALE
          : FIT_VIEW_MAX_SCALE;
    const minScale =
      nodeCount > 0 && nodeCount <= 8 ? FIT_VIEW_SPARSE_NODE_MIN_SCALE : FIT_VIEW_MIN_SCALE;
    const scale =
      fit && area
        ? Math.max(minScale, Math.min(maxScale, rawScale))
        : state.viewport.scale || readGraphZoom() || 1;
    window.varkaLastFitAudit = {
      activeType: state.activeType,
      nodeCount,
      graphNodeCount: graphNodes.length,
      diagramNodeCount: diagramNodes.length,
      fit,
      area,
      bounds: fitBounds,
      rawScale,
      scale,
    };
    if (fit && fitBounds) {
      state.viewport.scale = scale;
      setCanvasZoomIndicator();
    }
    if (fitBounds && area?.width && area?.height) {
      const centerX = fitBounds.minX + fitBounds.width / 2;
      const centerY = fitBounds.minY + fitBounds.height / 2;
      editor.viewportFitToken = (editor.viewportFitToken || 0) + 1;
      const fitToken = editor.viewportFitToken;
      const applyFit = async () => {
        if (fit && typeof editor.graph.fitView === "function") {
          const previousZoomRange = editor.graph.getZoomRange?.();
          try {
            editor.graph.setZoomRange?.([minScale, maxScale]);
            await editor.graph.fitView({ when: "always", direction: "both" }, false);
            if (
              fitToken === editor.viewportFitToken &&
              typeof editor.graph.fitCenter === "function"
            ) {
              await editor.graph.fitCenter(false);
            }
          } finally {
            const restoreRange = Array.isArray(previousZoomRange)
              ? previousZoomRange
              : [FIT_VIEW_MIN_SCALE, FIT_VIEW_MAX_SCALE];
            editor.graph.setZoomRange?.(restoreRange);
          }
          if (fitToken !== editor.viewportFitToken) {
            return;
          }
        } else {
          if (fit) {
            await editor.graph.zoomTo?.(scale, false);
          }
          if (fitToken !== editor.viewportFitToken) {
            return;
          }
          state.viewport.scale = scale;
          await waitForViewportFrame();
          await panCanvasPointToViewportTarget(
            centerX,
            centerY,
            area.centerX,
            area.centerY,
            fitToken,
          );
        }
        if (fitToken !== editor.viewportFitToken) {
          return;
        }
        settleNativeViewport();
      };
      void applyFit().catch((error) => {
        updateDebugState({ lastViewportError: error.message || String(error) });
      });
    } else {
      const focusResult = editor.graph.focusElement?.(nodes[0], { duration: 0 });
      afterGraphViewport(focusResult);
    }
  } catch (error) {
    updateDebugState({ lastViewportError: error.message || String(error) });
  }
  return true;
}

export function toGraphCoordinates(clientX, clientY) {
  if (!editor?.graph) {
    const rect = el.canvasViewport.getBoundingClientRect();
    const px = clientX - rect.left;
    const py = clientY - rect.top;
    return {
      x: (px - state.viewport.x) / state.viewport.scale,
      y: (py - state.viewport.y) / state.viewport.scale,
    };
  }
  let converted = null;
  try {
    converted = editor.graph.getCanvasByClient?.([clientX, clientY]);
  } catch {
    try {
      converted = editor.graph.getCanvasByClient?.({ x: clientX, y: clientY });
    } catch {
      converted = null;
    }
  }
  if (Array.isArray(converted)) {
    return { x: converted[0], y: converted[1] };
  }
  return converted || { x: 0, y: 0 };
}

export function toClientCoordinates(x, y) {
  if (!editor?.graph) {
    const rect = el.canvasViewport.getBoundingClientRect();
    return {
      x: rect.left + x * state.viewport.scale + state.viewport.x,
      y: rect.top + y * state.viewport.scale + state.viewport.y,
    };
  }
  let converted = null;
  try {
    converted = editor.graph.getClientByCanvas?.([x, y]);
  } catch {
    try {
      converted = editor.graph.getClientByCanvas?.({ x, y });
    } catch {
      converted = null;
    }
  }
  if (Array.isArray(converted)) {
    return { x: converted[0], y: converted[1] };
  }
  return converted || { x: 0, y: 0 };
}

export function focusG6Node(nodeId) {
  if (!editor?.graph || !nodeId) {
    return;
  }
  const targetScale = Math.max(readGraphZoom(), 1.2);
  state.viewport.scale = targetScale;
  setCanvasZoomIndicator();
  try {
    editor.graph.zoomTo?.(targetScale, false);
    editor.graph.focusElement?.(nodeId, { duration: 280 });
  } catch (error) {
    updateDebugState({ lastViewportError: error.message || String(error) });
  }
  window.setTimeout(() => {
    runViewportSync();
  }, 320);
}

export function focusG6CanvasPoint(x, y) {
  if (!editor?.graph) {
    return;
  }
  const rect = el.canvasViewport?.getBoundingClientRect();
  const scale = state.viewport.scale || readGraphZoom();
  state.viewport.x = Math.round((rect?.width || 0) / 2 - x * scale);
  state.viewport.y = Math.round((rect?.height || 0) / 2 - y * scale);
  state.viewport.scale = scale;
  updateG6Viewport();
}

export function beginG6InlineLabelEdit(node, handlers) {
  if (!editor?.graph || !node) {
    return;
  }
  showInlineLabelEditor(editor.graph, node, handlers);
}

function cancelPendingLodUpdate() {
  if (pendingLodTimer) {
    window.clearTimeout(pendingLodTimer);
    pendingLodTimer = 0;
  }
  if (pendingLodFrame) {
    window.cancelAnimationFrame(pendingLodFrame);
    pendingLodFrame = 0;
  }
  pendingLodState = null;
}

function applyG6Lod(nextLod, showLabels) {
  if (!editor?.graph) {
    return;
  }
  cancelPendingLodUpdate();
  editor.lastLod = nextLod;
  editor.lastShowLabels = showLabels;
  syncG6FromState({ full: false });
}

function scheduleG6LodUpdate(nextLod, showLabels) {
  pendingLodState = { nextLod, showLabels };
  if (pendingLodTimer) {
    window.clearTimeout(pendingLodTimer);
  }
  pendingLodTimer = window.setTimeout(() => {
    pendingLodTimer = 0;
    if (pendingLodFrame) {
      return;
    }
    pendingLodFrame = window.requestAnimationFrame(() => {
      pendingLodFrame = 0;
      const pending = pendingLodState;
      pendingLodState = null;
      if (pending) {
        applyG6Lod(pending.nextLod, pending.showLabels);
      }
    });
  }, LOD_UPDATE_IDLE_DELAY_MS);
}

export function updateG6Lod({ defer = false } = {}) {
  if (!editor?.graph) {
    return;
  }
  const zoom = currentZoom();
  const nextLod = detailLevelForZoom(zoom);
  const showLabels = shouldShowEdgeLabels(zoom, state.diagram.connections.length);
  if (nextLod === editor.lastLod && showLabels === editor.lastShowLabels) {
    return;
  }
  if (defer) {
    scheduleG6LodUpdate(nextLod, showLabels);
  } else {
    applyG6Lod(nextLod, showLabels);
  }
}

export function updateG6ContextBoxes(boxes = null, { useCache = false } = {}) {
  if (!editor?.graph) {
    return;
  }
  let resolved = boxes;
  if (resolved) {
    editor.contextBoxesCache = resolved;
    editor.contextBoxesDirty = false;
  } else if (useCache && !editor.contextBoxesDirty) {
    resolved = editor.contextBoxesCache || [];
  } else {
    resolved = editor.callbacks?.contextBoxes?.() || [];
    editor.contextBoxesCache = resolved;
    editor.contextBoxesDirty = false;
  }
  renderContextBoxes(editor.graph, resolved, {
    selectedContextName: "",
    onSelect: editor.callbacks?.onContextSelect,
    onOpen: editor.callbacks?.onContextOpen,
  });
}

export function updateG6NodeIcons() {
  if (!editor?.graph) {
    return;
  }
  renderNodeIcons(editor.graph, state.diagram.nodes, {
    visibleNode: editor.mapperOptions?.visibleNode || (() => true),
  });
}

export function onG6ViewportChanged() {
  if (!editor?.graph) {
    return;
  }
  markViewportTransforming();
  scheduleViewportSync({ lightweight: true });
}
