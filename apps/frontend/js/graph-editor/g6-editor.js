import {state} from '../state.js';
import {el} from '../dom.js';
import {mapDiagramToG6, mapEdgeToG6, mapNodeToG6} from './g6-mapper.js';
import {
  canvasBackgroundColor,
  cssVar,
  G6_BASE_EDGE_TYPE,
  G6_BASE_NODE_TYPE,
  MODLESS_EDGE_TYPE,
  MODLESS_NODE_TYPE,
  nodeSizeForDiagram
} from './g6-style.js';
import {
  cancelScheduledDraw,
  createAdjacencyIndex,
  createSpatialIndex,
  detailLevelForZoom,
  diffGraphData,
  fingerprintElement,
  scheduleGraphDraw,
  scheduleGraphRender,
  shouldShowEdgeLabels
} from './g6-performance.js';
import {bindG6Interactions} from './g6-interactions.js';
import {
  clearG6Overlays,
  renderContextBoxes,
  renderNodeIcons,
  showInlineLabelEditor
} from './g6-overlays.js';

let editor = null;
let extensionsRegistered = false;
let pendingViewportFrame = 0;
let pendingViewportSyncFrame = 0;
let pendingLodFrame = 0;
let pendingLodTimer = 0;
let pendingLodState = null;
const CONNECT_GLOBAL_TARGET_NODE_LIMIT = 240;
const CONNECT_ILLEGAL_STATE_NODE_LIMIT = 800;
const LOD_UPDATE_IDLE_DELAY_MS = 140;

function g6() {
  return window.G6 || null;
}

export function isG6Available() {
  const api = g6();
  return Boolean(api?.Graph);
}

function updateDebugState(patch = {}) {
  window.modlessG6State = {
    ...(window.modlessG6State || {}),
    ...patch,
    available: isG6Available(),
    mounted: Boolean(editor?.graph),
    renderer: "antv-g6"
  };
}

function installDebugProbe() {
  window.modlessG6Debug = () => {
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
      ...(window.modlessG6State || {}),
      activeType: state.activeType,
      stateNodes: state.diagram.nodes.length,
      stateEdges: state.diagram.connections.length,
      mappedNodes: editor?.dataSnapshot?.nodesById?.size || 0,
      mappedEdges: editor?.dataSnapshot?.edgesById?.size || 0,
      graphSize,
      hostRect: hostRect ? {
        width: Math.round(hostRect.width),
        height: Math.round(hostRect.height)
      } : null,
      hostChildren: [...(host?.children || [])].map((child) =>
          child.tagName),
      hasCanvasDescendant: Boolean(host?.querySelector?.("canvas")),
      canvasGridRenderer: el.canvasGrid?.dataset?.renderer || "",
      g6Version: g6()?.version || "",
      g6Keys: Object.keys(g6() || {}).slice(0, 40),
      unavailable: el.canvasGrid?.classList.contains(
          "g6-renderer-unavailable")
    };
  };
}

function truncate(text, max = 44) {
  const value = String(text || "");
  return value.length > max ? `${value.slice(0, Math.max(1, max - 1))}...`
      : value;
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
  return lines.slice(0, maxLines).map((item, index) => {
    if (index === maxLines - 1 && lines.length > maxLines) {
      return truncate(item, maxLine - 1);
    }
    return truncate(item, maxLine);
  }).join("\n");
}

function lineCount(text) {
  const value = String(text || "");
  return value ? value.split("\n").length : 0;
}

function boundedText(text, maxLine, maxLines) {
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
    segments.push({start, end, length});
    total += length;
  }
  if (!segments.length) {
    return points[0] || {x: 0, y: 0};
  }
  let remaining = total / 2;
  for (const segment of segments) {
    if (remaining <= segment.length) {
      const ratio = remaining / segment.length;
      return {
        x: segment.start.x + (segment.end.x - segment.start.x) * ratio,
        y: segment.start.y + (segment.end.y - segment.start.y) * ratio
      };
    }
    remaining -= segment.length;
  }
  return segments[segments.length - 1].end;
}

function badgeFill(text, diagramType) {
  const value = String(text || "").toLowerCase();
  if (value.includes("block") || value.includes("critical")
      || value.includes("error") || value.includes("high")) {
    return diagramType === "cim" ? "rgba(220, 38, 38, 0.16)"
        : "rgba(248, 113, 113, 0.17)";
  }
  if (value.includes("generated") || value.includes("trace")) {
    return diagramType === "cim" ? "rgba(79, 70, 229, 0.14)"
        : "rgba(129, 140, 248, 0.16)";
  }
  if (value.includes("encrypt") || value.includes("auth")
      || value.includes("security")) {
    return diagramType === "cim" ? "rgba(22, 163, 74, 0.14)"
        : "rgba(74, 222, 128, 0.15)";
  }
  return diagramType === "cim" ? "rgba(15, 23, 42, 0.09)"
      : "rgba(148, 163, 184, 0.14)";
}

function badgeTextFill(diagramType) {
  return diagramType === "cim" ? "rgba(31, 41, 55, 0.82)"
      : "rgba(226, 232, 240, 0.82)";
}

function renderPlaceholderIcon(shape, container, {
  left,
  top,
  low = false
} = {}) {
  const size = low ? 18 : 20;
  shape.upsert("placeholderIcon", "image", {
    x: left + 10,
    y: top + (low ? 9 : 6),
    width: size,
    height: size,
    src: "/assets/icons/placeholder.svg",
    opacity: 0.9,
    pointerEvents: "none"
  }, container);
  shape.upsert("iconTile", "rect", false, container);
  shape.upsert("iconSky", "circle", false, container);
  shape.upsert("iconMark", "path", false, container);
}

function renderOpenControl(shape, container, {
  left,
  top,
  width,
  height,
  diagramType,
  selected = false,
  openControlHover = false,
  low = false
}) {
  const controlWidth = low ? 30 : 38;
  const controlHeight = low ? 14 : 16;
  const x = left + width - controlWidth - 9;
  const y = top + (low ? 8 : 7);
  const isCim = diagramType === "cim";
  const fill = openControlHover
      ? (isCim ? "rgba(24, 20, 14, 0.92)"
          : "rgba(94, 203, 255, 0.26)")
      : (isCim ? "rgba(24, 20, 14, 0.76)"
          : "rgba(226, 232, 240, 0.14)");
  const stroke = openControlHover ? cssVar("--accent-select", "#5ecbff")
      : selected ? cssVar("--accent-select", "#5ecbff")
          : isCim ? "rgba(24, 20, 14, 0.22)"
              : "rgba(226, 232, 240, 0.22)";
  shape.upsert("openControl", "rect", {
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
    cursor: "pointer"
  }, container);
  shape.upsert("openControlText", "text", {
    x: x + controlWidth / 2,
    y: y + controlHeight / 2 + 0.5,
    text: "OPEN",
    fontFamily: cssVar("--font-display", "sans-serif"),
    fontSize: low ? 6.5 : 7.5,
    fontWeight: 800,
    fill: isCim ? "rgba(255, 255, 255, 0.94)"
        : cssVar("--text-strong", "#e3e8f2"),
    textAlign: "center",
    textBaseline: "middle",
    cursor: "pointer"
  }, container);
}

function renderNodeTags(shape, container, {
  badges = [],
  diagramType,
  left,
  top,
  width,
  height,
  low = false
}) {
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
    shape.upsert(`badge${index}`, "rect", visible ? {
      x,
      y,
      width: tagWidth,
      height: rowHeight,
      radius: 3,
      fill: badgeFill(value, diagramType),
      stroke: diagramType === "cim" ? "rgba(15, 23, 42, 0.1)"
          : "rgba(148, 163, 184, 0.14)",
      pointerEvents: "none"
    } : false, container);
    shape.upsert(`badgeText${index}`, "text", visible ? {
      x: x + tagWidth / 2,
      y: y + rowHeight / 2 + 0.5,
      text: label,
      fontFamily: cssVar("--font-ui", "sans-serif"),
      fontSize: 6.8,
      fontWeight: 800,
      fill: badgeTextFill(diagramType),
      textAlign: "center",
      textBaseline: "middle",
      pointerEvents: "none"
    } : false, container);
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

function registerModlessG6Extensions() {
  if (extensionsRegistered) {
    return;
  }
  const api = g6();
  if (!api?.register || !api?.ExtensionCategory || !api?.Rect
      || !api?.BaseEdge) {
    updateDebugState({
      customExtensions: "skipped",
      lastExtensionError: "G6 custom extension API unavailable"
    });
    return;
  }
  const {register, ExtensionCategory, Rect, BaseEdge} = api;

  class ModlessNode extends Rect {
    render(attributes = this.parsedAttributes, container) {
      const size = attributes.size || [attributes.width || 228,
        attributes.height || 112];
      const width = Number(size[0]) || Number(attributes.width) || 228;
      const height = Number(size[1]) || Number(attributes.height) || 112;
      const left = -width / 2;
      const top = -height / 2;
      const diagramType = attributes.diagramType || "cim";
      const accent = attributes.accent || cssVar("--accent", "#00a6e0");
      const stateSet = states(attributes);
      const selected = attributes.selected || stateSet.has("selected");
      const hovered = attributes.hover || stateSet.has("hover");
      const connectSource = attributes.connectSource
          || stateSet.has("connect-source");
      const connectLegal = attributes.connectLegal
          || stateSet.has("connect-legal");
      const connectIllegal = attributes.connectIllegal
          || stateSet.has("connect-illegal");
      const impact = attributes.impactFocal || stateSet.has("impact-focal")
          || attributes.impactUpstream || stateSet.has("impact-upstream")
          || attributes.impactDownstream || stateSet.has("impact-downstream")
          || attributes.impactConnected || stateSet.has("impact-connected");
      const focused = attributes.focused || stateSet.has("focus");
      const dimmed = !focused && (attributes.dimmed || stateSet.has("dimmed"));
      const draft = attributes.contextDraft || stateSet.has("context-draft");
      const openControlHover = attributes.openControlHover
          || stateSet.has("open-control-hover");
      const low = attributes.detailLevel === "low";
      const containerNode = Boolean(attributes.isContainer);
      const handleVisible = Boolean(attributes.showHandles) || connectSource;
      const badges = Array.isArray(attributes.badges) ? attributes.badges
      .slice(0, 4) : [];
      const headerHeight = low ? 0 : 32;
      const tagStripHeight = low ? 0 : 24;

      if (diagramType === "cim") {
        const fill = attributes.sticky || "#fde68a";
        super.render({
          ...attributes,
          fill,
          stroke: selected ? cssVar("--accent-select", "#5ecbff")
              : "rgba(21, 28, 40, 0.24)",
          lineWidth: selected ? 2.4 : 1,
          radius: 2,
          labelText: ""
        }, container);
        this.upsert("key", "rect", {
          x: left,
          y: top,
          width,
          height,
          radius: 2,
          fill,
          stroke: connectLegal ? "#16a34a"
              : connectSource ? cssVar("--accent-select", "#5ecbff")
                  : connectIllegal ? "rgba(15, 23, 42, 0.22)"
                      : selected ? cssVar("--accent-select", "#5ecbff")
                          : impact ? "#f97316"
                              : "rgba(21, 28, 40, 0.24)",
          lineWidth: selected || connectLegal || connectSource || draft ? 2.4
              : 1,
          lineDash: draft ? [6, 4] : undefined,
          shadowColor: selected || hovered || connectLegal
              ? "rgba(8, 14, 24, 0.36)" : "rgba(12, 18, 28, 0.28)",
          shadowBlur: selected || hovered || connectLegal ? 18 : 10,
          opacity: dimmed || connectIllegal ? 0.48 : 1,
          cursor: "pointer"
        }, container);
        this.upsert("header", "rect", low ? false : {
          x: left,
          y: top,
          width,
          height: headerHeight,
          radius: [2, 2, 0, 0],
          fill: "rgba(255,255,255,0.34)",
          stroke: "transparent",
          pointerEvents: "none"
        }, container);
        this.upsert("semanticShape", "path", false, container);
        this.upsert("corner", "path", false, container);
        this.upsert("icon", "rect", false, container);
        this.upsert("dot", "circle", false, container);
        renderPlaceholderIcon(this, container, {
          left,
          top,
          accent,
          diagramType,
          low
        });
        this.upsert("type", "text", low ? false : {
          x: left + 38,
          y: top + 7,
          text: boundedText(attributes.kindText || attributes.typeText
              || "Element", containerNode ? 13 : 18, 2),
          fontFamily: cssVar("--font-display", "sans-serif"),
          fontSize: 8,
          lineHeight: 8.5,
          fontWeight: 800,
          fill: "rgba(35, 28, 18, 0.86)",
          textBaseline: "top",
          pointerEvents: "none"
        }, container);
        const labelText = lineBreak(attributes.labelText || "", low ? 20 : 24,
            low ? 1 : 2);
        const labelLineHeight = low ? 12 : 14;
        const labelY = top + (low ? 24 : 39);
        const idY = Math.min(top + height - tagStripHeight - 10,
            labelY + lineCount(labelText) * labelLineHeight + 8);
        this.upsert("label", "text", {
          x: left + 10,
          y: labelY,
          text: labelText,
          fontFamily: cssVar("--font-ui", "sans-serif"),
          fontSize: low ? 10 : 12,
          lineHeight: labelLineHeight,
          fontWeight: 700,
          fill: "rgba(24, 20, 14, 0.92)",
          textBaseline: "top",
          pointerEvents: "none"
        }, container);
        this.upsert("notation", "text", low ? false : {
          x: left + 10,
          y: idY,
          text: truncate(attributes.elementId || attributes.id || "", 30),
          fontFamily: cssVar("--font-ui", "sans-serif"),
          fontSize: 7.4,
          fontWeight: 700,
          fill: "rgba(45, 38, 26, 0.58)",
          textBaseline: "middle",
          pointerEvents: "none"
        }, container);
        renderNodeTags(this, container, {
          badges,
          diagramType,
          left,
          top,
          width,
          height,
          low
        });
        if (containerNode) {
          renderOpenControl(this, container, {
            left,
            top,
            width,
            height,
            diagramType,
            selected,
            openControlHover,
            low
          });
        } else {
          this.upsert("openControl", "rect", false, container);
          this.upsert("openControlText", "text", false, container);
        }
      } else {
        const fill = cssVar("--node-bg", "#131923");
        const border = cssVar("--node-border", "#3d495f");
        super.render({
          ...attributes,
          fill,
          stroke: selected ? cssVar("--accent-select", "#5ecbff") : border,
          lineWidth: selected ? 2.4 : 1,
          radius: 2,
          labelText: ""
        }, container);
        this.upsert("key", "rect", {
          x: left,
          y: top,
          width,
          height,
          radius: 2,
          fill,
          stroke: connectLegal ? "#16a34a"
              : connectSource ? cssVar("--accent-select", "#5ecbff")
                  : selected ? cssVar("--accent-select", "#5ecbff")
                      : impact ? "#f97316" : border,
          lineWidth: selected || connectLegal || connectSource || draft ? 2.4
              : 1,
          lineDash: draft ? [6, 4] : undefined,
          shadowColor: selected || hovered || connectLegal
              ? "rgba(8, 14, 24, 0.44)" : "rgba(6, 11, 20, 0.32)",
          shadowBlur: selected || hovered || connectLegal ? 18 : 8,
          opacity: dimmed || connectIllegal ? 0.48 : 1,
          cursor: "pointer"
        }, container);
        this.upsert("header", "rect", low ? false : {
          x: left,
          y: top,
          width,
          height: headerHeight,
          radius: [2, 2, 0, 0],
          fill: "rgba(35,42,55,0.58)",
          stroke: "transparent",
          pointerEvents: "none"
        }, container);
        this.upsert("icon", "rect", false, container);
        renderPlaceholderIcon(this, container, {
          left,
          top,
          accent,
          diagramType,
          low
        });
        this.upsert("type", "text", low ? false : {
          x: left + 40,
          y: top + 7,
          text: boundedText(attributes.kindText || attributes.typeText
              || "Element", containerNode ? 18 : 24, 2),
          fontFamily: cssVar("--font-display", "sans-serif"),
          fontSize: 8.2,
          lineHeight: 8.8,
          fontWeight: 800,
          fill: accent,
          textBaseline: "top",
          pointerEvents: "none"
        }, container);
        this.upsert("dot", "circle", false, container);
        this.upsert("semanticShape", "path", false, container);
        const labelText = lineBreak(attributes.labelText || "", low ? 28 : 30,
            low ? 1 : 2);
        const labelLineHeight = low ? 12.5 : 14;
        const labelY = top + (low ? 18 : 42);
        const idY = Math.min(top + height - tagStripHeight - 10,
            labelY + lineCount(labelText) * labelLineHeight + 8);
        this.upsert("label", "text", {
          x: left + 11,
          y: labelY,
          text: labelText,
          fontFamily: cssVar("--font-ui", "sans-serif"),
          fontSize: low ? 10.5 : 12,
          lineHeight: labelLineHeight,
          fontWeight: 700,
          fill: cssVar("--text", "#e3e8f2"),
          textBaseline: "top",
          pointerEvents: "none"
        }, container);
        this.upsert("notation", "text", low ? false : {
              x: left + 11,
              y: idY,
              text: truncate(attributes.elementId || attributes.id || "", 36),
              fontFamily: cssVar("--font-ui", "sans-serif"),
              fontSize: 7.5,
              fontWeight: 700,
              fill: "rgba(152, 168, 192, 0.72)",
              textBaseline: "middle",
              pointerEvents: "none"
            }, container);
        renderNodeTags(this, container, {
          badges,
          diagramType,
          left,
          top,
          width,
          height,
          low
        });
        if (containerNode) {
          renderOpenControl(this, container, {
            left,
            top,
            width,
            height,
            diagramType,
            selected,
            openControlHover,
            low
          });
        } else {
          this.upsert("openControl", "rect", false, container);
          this.upsert("openControlText", "text", false, container);
        }
      }

      this.upsert("leftHandle", "circle", handleVisible ? {
        cx: left,
        cy: 0,
        r: 7,
        fill: cssVar("--node-bg", "#131923"),
        stroke: cssVar("--accent-select", "#5ecbff"),
        lineWidth: 2,
        opacity: low ? 0.75 : 1,
        cursor: "crosshair"
      } : false, container);
      this.upsert("rightHandle", "circle", handleVisible ? {
        cx: left + width,
        cy: 0,
        r: 7,
        fill: cssVar("--node-bg", "#131923"),
        stroke: cssVar("--accent-select", "#5ecbff"),
        lineWidth: 2,
        opacity: low ? 0.75 : 1,
        cursor: "crosshair"
      } : false, container);
      this.upsert("legalBadge", "text", connectLegal ? {
        x: left + width - 8,
        y: top + height - 10,
        text: "LEGAL",
        fontFamily: cssVar("--font-display", "sans-serif"),
        fontSize: 8,
        fontWeight: 800,
        fill: "#052e16",
        textAlign: "right",
        textBaseline: "middle",
        pointerEvents: "none"
      } : false, container);
    }
  }

  class ModlessEdge extends BaseEdge {
    getKeyPath(attributes) {
      const [sourcePoint, targetPoint] = this.getEndpoints(attributes);
      const routeStart = attributes.routeStart;
      const routeEnd = attributes.routeEnd;
      const source = Number.isFinite(Number(routeStart?.x))
      && Number.isFinite(Number(routeStart?.y))
          ? [Number(routeStart.x), Number(routeStart.y)]
          : (sourcePoint || [0, 0]);
      const target = Number.isFinite(Number(routeEnd?.x))
      && Number.isFinite(Number(routeEnd?.y))
          ? [Number(routeEnd.x), Number(routeEnd.y)]
          : (targetPoint || [0, 0]);
      const pins = Array.isArray(attributes.pinPoints)
          ? attributes.pinPoints : [];
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
        const dx = target[0] - source[0];
        const dy = target[1] - source[1];
        if (Math.abs(dx) > 80 && Math.abs(dy) > 30) {
          const midX = Math.round(source[0] + dx / 2);
          path.push(["L", midX, source[1]]);
          path.push(["L", midX, target[1]]);
        }
      }
      path.push(["L", target[0], target[1]]);
      return path;
    }

    getKeyStyle(attributes) {
      const stateSet = states(attributes);
      const selected = attributes.selected || stateSet.has("selected");
      const hovered = attributes.hovered || attributes.hover
          || stateSet.has("hover");
      const focused = attributes.focused || stateSet.has("focus");
      const dimmed = !focused && (attributes.dimmed || stateSet.has("dimmed"));
      return {
        ...super.getKeyStyle(attributes),
        stroke: attributes.stroke || cssVar("--accent", "#00a6e0"),
        lineWidth: selected ? 2.6 : (hovered ? 2.45
            : Number(attributes.lineWidth) || 1.7),
        lineDash: attributes.lineDash,
        opacity: dimmed ? 0.3 : (Number(attributes.opacity) || 0.9),
        shadowColor: selected || hovered ? cssVar("--accent-glow",
            "rgba(0,166,224,0.28)") : undefined,
        shadowBlur: selected || hovered ? 6 : 0,
        cursor: "pointer",
        endArrow: attributes.endArrow === false ? false : true,
        startArrow: attributes.startArrow ? true : false
      };
    }

    render(attributes = this.parsedAttributes, container) {
      super.render(attributes, container);
      const labelText = String(attributes.labelText || "");
      const pinPoints = Array.isArray(attributes.pinPoints)
          ? attributes.pinPoints : [];
      const showPins = Boolean(attributes.showPins && pinPoints.length);
      for (let index = 0; index < 8; index += 1) {
        const pin = showPins ? pinPoints[index] : null;
        const x = Number(pin?.x);
        const y = Number(pin?.y);
        this.upsert(`pin-${index}`, "circle",
            pin && Number.isFinite(x) && Number.isFinite(y) ? {
              cx: x,
              cy: y,
              r: attributes.selected ? 5.5 : 4.5,
              fill: canvasBackgroundColor(),
              stroke: attributes.selected
                  ? cssVar("--accent-select", "#5ecbff")
                  : cssVar("--accent", "#00a6e0"),
              lineWidth: attributes.selected ? 2 : 1.5,
              pointerEvents: "none"
            } : false, container);
      }
      if (!labelText) {
        this.upsert("label", "text", false, container);
        return;
      }
      const path = this.getKeyPath(attributes);
      const points = path.filter((entry) => entry[0] === "M" || entry[0]
          === "L").map((entry) => ({x: Number(entry[1]), y: Number(entry[2])}));
      const midpoint = pathMidpoint(points);
      this.upsert("label", "text", {
        x: midpoint.x,
        y: midpoint.y - 8,
        text: truncate(labelText, 32),
        fontFamily: cssVar("--font-ui", "sans-serif"),
        fontSize: attributes.selected ? 11 : 10,
        fontWeight: attributes.selected ? 800 : 700,
        fill: attributes.selected ? cssVar("--accent-light", "#7bd0ff")
            : cssVar("--muted", "#98a8c0"),
        stroke: canvasBackgroundColor(),
        lineWidth: 4,
        paintOrder: "stroke",
        textAlign: "center",
        textBaseline: "middle",
        cursor: "pointer"
      }, container);
    }
  }

  register(ExtensionCategory.NODE, MODLESS_NODE_TYPE, ModlessNode);
  register(ExtensionCategory.EDGE, MODLESS_EDGE_TYPE, ModlessEdge);
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
  const height = Math.max(1,
      Math.round(rect?.height || parentRect?.height || 1));
  return {width, height};
}

function resizeGraphToHost() {
  if (!editor?.graph || !editor.host) {
    return;
  }
  const {width, height} = hostSize(editor.host);
  editor.graph.setSize?.(width, height);
  editor.graph.resize?.(width, height);
}

function readGraphZoom(fallback = state.viewport.scale || 1) {
  try {
    const zoom = editor?.graph?.getZoom?.();
    return Number.isFinite(zoom) ? zoom : fallback;
  } catch (error) {
    updateDebugState({lastViewportReadError: error.message || String(error)});
    return fallback;
  }
}

function readGraphPosition() {
  try {
    return editor?.graph?.getPosition?.() || null;
  } catch (error) {
    updateDebugState({lastViewportReadError: error.message || String(error)});
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
    ...(editor?.mapperOptions || {})
  };
}

function spatialIndexFallbackSize() {
  return nodeSizeForDiagram(state.activeType);
}

function graphDataFromState(options = mapperOptions()) {
  return mapDiagramToG6({
    nodes: state.diagram.nodes,
    edges: state.diagram.connections,
    ...options
  });
}

function rememberDataSnapshot(data) {
  const nodeFingerprints = new Map();
  const edgeFingerprints = new Map();
  const nodesById = new Map(data.nodes.map((node) => {
    nodeFingerprints.set(node.id, fingerprintElement(node));
    return [node.id, node];
  }));
  const edgesById = new Map(data.edges.map((edge) => {
    edgeFingerprints.set(edge.id, fingerprintElement(edge));
    return [edge.id, edge];
  }));
  editor.dataSnapshot = {
    nodesById, edgesById, nodeFingerprints,
    edgeFingerprints
  };
  rebuildNodeTypeIndex(data.nodes);
}

function rebuildSpatialIndex(nodes = null) {
  if (!editor) {
    return;
  }
  if (!editor.spatialIndex) {
    editor.spatialIndex = createSpatialIndex([], {
      fallbackSize: spatialIndexFallbackSize()
    });
  }
  const source = nodes || [...(editor.dataSnapshot?.nodesById?.values?.()
      || [])];
  editor.spatialIndex.rebuild(source, {
    fallbackSize: spatialIndexFallbackSize()
  });
}

function ensureDataSnapshot() {
  if (!editor.dataSnapshot) {
    rememberDataSnapshot({nodes: [], edges: []});
  }
  return editor.dataSnapshot;
}

function nodeTypeFromData(nodeData) {
  return String(nodeData?.style?.nodeType || nodeData?.data?.nodeType
      || nodeData?.data?.source?.type || nodeData?.data?.source?.eClass || "")
  .trim();
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
  return hasKnownElement(id, editor?.dataSnapshot?.nodesById)
      && isElementMounted(id);
}

function hasKnownEdge(id) {
  return hasKnownElement(id, editor?.dataSnapshot?.edgesById)
      && isElementMounted(id);
}

function setCanvasZoomIndicator() {
  const label = document.getElementById("canvasZoomValue");
  if (label) {
    label.textContent = `${Math.round((state.viewport.scale || 1) * 100)}%`;
  }
}

function updateViewportChrome() {
  el.canvasGrid?.style.setProperty("--viewport-scale",
      String(state.viewport.scale || 1));
  el.canvasGrid?.classList.toggle("lod-low", state.viewport.scale < 0.35);
  el.canvasGrid?.classList.toggle("lod-medium", state.viewport.scale >= 0.35
      && state.viewport.scale < 0.75);
  el.canvasGrid?.classList.toggle("lod-high", state.viewport.scale >= 1.5);
}

function runViewportSync({syncSelection = false} = {}) {
  syncViewportStateFromGraph();
  setCanvasZoomIndicator();
  updateViewportChrome();
  updateG6Lod({defer: true});
  updateG6ContextBoxes(null, {useCache: true});
  if (syncSelection) {
    updateG6Selection();
  }
  editor?.callbacks?.onViewportSynced?.();
}

function settleNativeViewport() {
  runViewportSync({syncSelection: true});
}

function scheduleViewportSync() {
  if (pendingViewportSyncFrame) {
    return;
  }
  pendingViewportSyncFrame = window.requestAnimationFrame(() => {
    pendingViewportSyncFrame = 0;
    runViewportSync();
  });
}

function afterGraphViewport(result) {
  result?.then?.(settleNativeViewport)?.catch?.((error) => updateDebugState({
    lastViewportError: error.message || String(error)
  }));
  if (!result?.then) {
    window.requestAnimationFrame(settleNativeViewport);
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
    fallbackSize: spatialIndexFallbackSize()
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

function applyDiff(data) {
  const diff = diffGraphData(editor.dataSnapshot, data);
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
  if (diff.addEdges.length) {
    editor.graph.addEdgeData?.(diff.addEdges);
  }
  if (diff.updateNodes.length) {
    editor.graph.updateNodeData?.(diff.updateNodes);
  }
  if (diff.updateEdges.length) {
    try {
      const result = editor.graph.updateEdgeData?.(diff.updateEdges);
      result?.catch?.((error) => updateDebugState({
        lastEdgeUpdateError: error.message || String(error)
      }));
    } catch (error) {
      updateDebugState({lastEdgeUpdateError: error.message || String(error)});
    }
  }
  if (diff.addNodes.length || diff.updateNodes.length
      || diff.removeNodeIds.length) {
    editor.connectStateKey = "";
    editor.contextBoxesDirty = true;
  }
  [...diff.addNodes, ...diff.updateNodes].forEach((node) =>
      editor.spatialIndex?.update?.(node, {
        fallbackSize: spatialIndexFallbackSize()
      }));
  editor.dataSnapshot = diff.snapshot;
  rebuildNodeTypeIndex([...diff.snapshot.nodesById.values()]);
  editor.adjacency = createAdjacencyIndex(state.diagram.connections);
  if (diff.addNodes.length || diff.addEdges.length || diff.removeNodeIds.length
      || diff.removeEdgeIds.length) {
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
      result?.catch?.((error) => updateDebugState({
        lastStateError: error.message || String(error)
      }));
    } catch (error) {
      updateDebugState({lastStateError: error.message || String(error)});
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
    return {nodeIds, edgeIds};
  }
  nodeIds.add(nodeId);
  (editor.adjacency?.byNode?.get(nodeId) || new Set()).forEach((edgeId) => {
    edgeIds.add(edgeId);
    const edge = editor.adjacency?.byId?.get(edgeId)
        || state.diagram.connections.find((item) => item.id === edgeId);
    if (edge?.sourceId) {
      nodeIds.add(edge.sourceId);
    }
    if (edge?.targetId) {
      nodeIds.add(edge.targetId);
    }
  });
  return {nodeIds, edgeIds};
}

function setGraphDimmed(enabled, changedNodes, changedEdges) {
  if (editor.hoverDimmedActive === enabled) {
    return;
  }
  editor.hoverDimmedActive = enabled;
  (editor.dataSnapshot?.nodesById?.keys?.() || []).forEach((id) => {
    if (setFlag(editor.nodeStateFlags, id, "dimmed", enabled)) {
      changedNodes.add(id);
    }
  });
  (editor.dataSnapshot?.edgesById?.keys?.() || []).forEach((id) => {
    if (setFlag(editor.edgeStateFlags, id, "dimmed", enabled)) {
      changedEdges.add(id);
    }
  });
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
  const nodeIds = new Set();
  const edgeIds = new Set();
  if (previous) {
    nodeIds.add(previous);
    setG6NodeHandleVisibility(previous, false);
  }
  (editor.hoverFocusNodeIds || new Set()).forEach((id) => nodeIds.add(id));
  (editor.hoverFocusEdgeIds || new Set()).forEach((id) => edgeIds.add(id));
  editor.nodeStateFlags.forEach((flags, id) => {
    if (flags.has("hover") || flags.has("focus") || flags.has("dimmed")) {
      nodeIds.add(id);
    }
  });
  editor.edgeStateFlags.forEach((flags, id) => {
    if (flags.has("hover") || flags.has("focus") || flags.has("dimmed")) {
      edgeIds.add(id);
    }
  });
  (editor.dataSnapshot?.nodesById?.keys?.() || []).forEach((id) => {
    if (editor.hoverDimmedActive) {
      nodeIds.add(id);
    }
  });
  (editor.dataSnapshot?.edgesById?.keys?.() || []).forEach((id) => {
    if (editor.hoverDimmedActive) {
      edgeIds.add(id);
    }
  });
  nodeIds.forEach((id) => {
    if (clearHoverFlagSet(editor.nodeStateFlags, id,
        ["hover", "focus", "dimmed"])) {
      changedNodes.add(id);
    }
  });
  edgeIds.forEach((id) => {
    if (clearHoverFlagSet(editor.edgeStateFlags, id,
        ["hover", "focus", "dimmed"])) {
      changedEdges.add(id);
    }
  });
  editor.hoveredNodeId = null;
  editor.hoverFocusNodeIds = new Set();
  editor.hoverFocusEdgeIds = new Set();
  editor.hoverDimmedActive = false;
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

export function mountG6Editor(container, {
  callbacks = {},
  mapper = {}
} = {}) {
  installDebugProbe();
  if (editor?.graph) {
    editor.callbacks = callbacks;
    editor.mapperOptions = mapper;
    updateDebugState({lastMount: "reused"});
    return editor;
  }
  if (!isG6Available()) {
    updateDebugState({lastError: "AntV G6 is not available"});
    throw new Error("AntV G6 is not available");
  }
  try {
    registerModlessG6Extensions();
  } catch (error) {
    console.warn(
        "Modless G6 custom extensions unavailable; using built-in G6 shapes",
        error);
    updateDebugState({
      lastExtensionError: error.message || String(error)
    });
  }
  const api = g6();
  const host = createHost(container);
  host.classList.add("is-mounted");
  const {width, height} = hostSize(host);
  const graph = new api.Graph({
    container: host,
    width,
    height,
    autoResize: true,
    animation: false,
    theme: "dark",
    zoomRange: [0.01, 2.5],
    cursor: "grab",
    data: {nodes: [], edges: []},
    behaviors: ["zoom-canvas", "optimize-viewport-transform"],
    node: {
      type: G6_BASE_NODE_TYPE,
      state: {
        normal: {
          selected: false,
          hover: false,
          focused: false,
          dimmed: false,
          openControlHover: false
        },
        selected: {selected: true},
        hover: {hover: true},
        focus: {focused: true},
        dimmed: {dimmed: true},
        "connect-source": {connectSource: true},
        "connect-legal": {connectLegal: true},
        "connect-illegal": {connectIllegal: true},
        "open-control-hover": {openControlHover: true},
        "context-draft": {contextDraft: true},
        "impact-focal": {impactFocal: true},
        "impact-upstream": {impactUpstream: true},
        "impact-downstream": {impactDownstream: true},
        "impact-connected": {impactConnected: true}
      }
    },
    edge: {
      type: G6_BASE_EDGE_TYPE,
      state: {
        normal: {
          selected: false,
          hovered: false,
          focused: false,
          dimmed: false
        },
        selected: {selected: true},
        hover: {hovered: true},
        focus: {focused: true},
        dimmed: {dimmed: true}
      }
    }
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
    hoverDimmedActive: false,
    adjacency: createAdjacencyIndex(state.diagram.connections),
    spatialIndex: createSpatialIndex([], {
      fallbackSize: spatialIndexFallbackSize()
    }),
    contextBoxesCache: [],
    contextBoxesDirty: true,
    lastLod: detailLevelForZoom(state.viewport.scale),
    lastShowLabels: true,
    viewportReady: false,
    viewportRetryCount: 0
  };
  editor.setOpenControlHover = (nodeId) => {
    const previous = editor.openControlHoverNodeId || null;
    const next = nodeId || null;
    if (previous === next) {
      return;
    }
    const changed = new Set();
    if (previous && setFlag(editor.nodeStateFlags, previous,
        "open-control-hover", false)) {
      changed.add(previous);
    }
    editor.openControlHoverNodeId = next;
    if (next && setFlag(editor.nodeStateFlags, next, "open-control-hover",
        true)) {
      changed.add(next);
    }
    flushElementStates(changed, editor.nodeStateFlags);
    if (changed.size) {
      scheduleGraphDraw(editor.graph);
    }
  };
  bindG6Interactions(editor, callbacks);
  resizeGraphToHost();
  updateDebugState(
      {lastError: "", lastMount: "mounted", hostSize: {width, height}});
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
  cancelPendingLodUpdate();
  clearG6Overlays();
  editor?.disposeInteractions?.();
  editor?.graph?.destroy?.();
  editor?.host?.classList.remove("is-mounted");
  editor = null;
}

export function getG6Editor() {
  return editor;
}

export function renderG6Diagram() {
  return syncG6FromState({full: !editor?.dataSnapshot});
}

export function setG6Data(nodes, edges) {
  if (!editor?.graph) {
    updateDebugState({lastError: "setG6Data before mount"});
    return;
  }
  const data = {nodes, edges};
  updateDebugState({lastSetData: {nodes: nodes.length, edges: edges.length}});
  resizeGraphToHost();
  editor.graph.setData?.(data);
  rememberDataSnapshot(data);
  rebuildSpatialIndex(data.nodes);
  editor.contextBoxesDirty = true;
  scheduleGraphRender(editor.graph);
  updateG6NodeIcons();
}

export function syncG6FromState({full = false} = {}) {
  if (!editor?.graph) {
    updateDebugState({lastError: "syncG6FromState before mount"});
    return;
  }
  const options = mapperOptions();
  const data = graphDataFromState(options);
  cancelPendingLodUpdate();
  editor.lastLod = options.detailLevel;
  editor.lastShowLabels = options.showLabels;
  updateDebugState(
      {lastSync: {full, nodes: data.nodes.length, edges: data.edges.length}});
  resizeGraphToHost();
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
}

export function addG6Node(node) {
  if (!editor?.graph || !node) {
    return;
  }
  const mapped = mapNodeToG6(node, mapperOptions());
  editor.graph.addNodeData?.([mapped]);
  rememberNodeData(mapped);
  editor.contextBoxesDirty = true;
  scheduleGraphRender(editor.graph);
  updateG6NodeIcons();
}

export function updateG6Node(nodeId, patch = {}) {
  if (!editor?.graph || !nodeId) {
    return;
  }
  const node = state.nodesById.get(nodeId) || state.diagram.nodes.find(
      (candidate) => candidate.id === nodeId);
  if (!node) {
    return;
  }
  Object.assign(node, patch);
  const mapped = mapNodeToG6(node, mapperOptions());
  editor.graph.updateNodeData?.([mapped]);
  rememberNodeData(mapped);
  editor.contextBoxesDirty = true;
  scheduleGraphDraw(editor.graph);
  updateG6NodeIcons();
}

export function updateG6NodePosition(nodeId, x, y) {
  if (!editor?.graph || !nodeId) {
    return;
  }
  const node = state.nodesById.get(nodeId) || state.diagram.nodes.find(
      (candidate) => candidate.id === nodeId);
  if (!node) {
    return;
  }
  node.x = Math.round(x);
  node.y = Math.round(y);
  const size = nodeSizeForDiagram(state.activeType);
  editor.graph.updateNodeData?.([{
    id: nodeId,
    style: {
      x: node.x + size.width / 2,
      y: node.y + size.height / 2
    }
  }]);
  rememberNodeData(mapNodeToG6(node, mapperOptions()));
  editor.spatialIndex?.update?.({
    id: nodeId,
    x: node.x,
    y: node.y,
    width: size.width,
    height: size.height
  }, {fallbackSize: size});
  editor.contextBoxesDirty = true;
  scheduleGraphDraw(editor.graph);
  updateG6NodeIcons();
}

function setG6NodeHandleVisibility(nodeId, visible) {
  if (!editor?.graph || !nodeId) {
    return;
  }
  const node = state.nodesById.get(nodeId) || state.diagram.nodes.find(
      (candidate) => candidate.id === nodeId);
  if (!node) {
    return;
  }
  const mapped = mapNodeToG6({...node, showHandles: Boolean(visible)},
      mapperOptions());
  editor.graph.updateNodeData?.([mapped]);
  rememberNodeData(mapped);
  scheduleGraphDraw(editor.graph);
}

export function removeG6Node(nodeId) {
  if (!editor?.graph || !nodeId) {
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
  editor.graph.addEdgeData?.([mapped]);
  editor.adjacency = createAdjacencyIndex(state.diagram.connections);
  rememberEdgeData(mapped);
  scheduleGraphRender(editor.graph);
}

export function updateG6Edge(edgeId, patch = {}) {
  if (!editor?.graph || !edgeId) {
    return;
  }
  const edge = editor.adjacency?.byId?.get(edgeId)
      || state.diagram.connections.find((item) => item.id === edgeId);
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
  if (!editor?.graph || !edgeId) {
    return;
  }
  editor.graph.removeEdgeData?.([edgeId]);
  editor.dataSnapshot?.edgesById?.delete(edgeId);
  editor.dataSnapshot?.edgeFingerprints?.delete(edgeId);
  editor.edgeStateFlags.delete(edgeId);
  editor.adjacency = createAdjacencyIndex(state.diagram.connections);
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
      result?.catch?.((error) => updateDebugState({
        lastEdgeUpdateError: error.message || String(error)
      }));
    } catch (error) {
      updateDebugState({lastEdgeUpdateError: error.message || String(error)});
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
  const changedNodes = replaceFlagSet(editor.nodeStateFlags, "selected",
      state.selectedNodeIds || []);
  const changedEdges = replaceFlagSet(editor.edgeStateFlags, "selected",
      state.selectedConnectionId ? [state.selectedConnectionId] : []);
  flushElementStates(changedNodes, editor.nodeStateFlags);
  flushElementStates(changedEdges, editor.edgeStateFlags);
  if (changedEdges.size) {
    refreshG6Edges([...changedEdges]);
  }
  if (changedNodes.size) {
    scheduleGraphDraw(editor.graph);
  }
}

function idsByTypes(types = [], {
  excludeId = "",
  limit = Number.POSITIVE_INFINITY
} = {}) {
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
        return {ids, overflow};
      }
      ids.push(id);
    }
  }
  return {ids, overflow};
}

function connectionStateForNode(source, node) {
  if (!source || !node) {
    return "";
  }
  return editor.callbacks?.connectionTargetState?.(source, node) === "legal"
      ? "connect-legal" : "connect-illegal";
}

function legalConnectionTargetTypes(source) {
  const availableTypes = [...(editor?.nodeIdsByType?.keys?.() || [])];
  const result = editor.callbacks?.connectionTargetTypes?.(source,
      {availableTypes});
  return Array.isArray(result) ? result.filter(Boolean) : null;
}

export function updateG6ConnectionState() {
  if (!editor?.graph) {
    return;
  }
  const sourceId = state.connectSourceId || state.linkDrag?.sourceId || "";
  const hoverTargetId = state.linkDrag?.hoveredTargetId
      || (state.connectSourceId ? state.hoveredNodeId || "" : "");
  const nextKey = sourceId ? [
    sourceId,
    hoverTargetId,
    state.preferredConnectionKind || "",
    state.activeType,
    state.nodesById.size
  ].join("|") : "";
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
      const {ids, overflow} = idsByTypes(targetTypes, {
        excludeId: sourceId,
        limit: CONNECT_GLOBAL_TARGET_NODE_LIMIT + 1
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
    const showIllegalStates = state.nodesById.size
        <= CONNECT_ILLEGAL_STATE_NODE_LIMIT;
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
    if (hoverTargetId && hoverTargetId !== sourceId
        && !globallyMarkedTargetIds.has(hoverTargetId)) {
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
  if (editor.hoveredNodeId === normalizedNodeId
      && (normalizedNodeId || (!editor.hoverDimmedActive
          && !editor.hoverFocusNodeIds?.size
          && !editor.hoverFocusEdgeIds?.size))) {
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
  if (normalizedNodeId && !editor.hoverDimmedActive) {
    setGraphDimmed(true, changedNodes, changedEdges);
  } else if (!normalizedNodeId) {
    setGraphDimmed(false, changedNodes, changedEdges);
  }
  symmetricDifference(previousFocusNodeIds, nextFocus.nodeIds).forEach((id) => {
    if (setFlag(editor.nodeStateFlags, id, "focus",
        nextFocus.nodeIds.has(id))) {
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
  if (editor.hoveredEdgeId && setFlag(editor.edgeStateFlags,
      editor.hoveredEdgeId, "hover", false)) {
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
  const upstream = new Set();
  const downstream = new Set();
  const connected = new Set();
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
    ["context-draft", state.boundedContextDraftNodeIds || new Set()]
  ].forEach(([flag, ids]) => {
    replaceFlagSet(editor.nodeStateFlags, flag, ids).forEach((id) =>
        changed.add(id));
  });
  flushElementStates(changed, editor.nodeStateFlags);
}

export function updateG6Viewport() {
  if (!editor?.graph) {
    return;
  }
  const retryViewport = (error) => {
    updateDebugState({lastViewportError: error.message || String(error)});
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
          [state.viewport.x, state.viewport.y], false);
      translateResult?.catch?.(retryViewport);
    } catch (error) {
      retryViewport(error);
      return;
    }
    editor.viewportReady = true;
  };
  applyTransform();
  runViewportSync({syncSelection: true});
}

export function getG6Viewport() {
  syncViewportStateFromGraph();
  return {...state.viewport};
}

export function zoomG6CanvasBy(multiplier = 1) {
  if (!editor?.graph) {
    return false;
  }
  const prev = readGraphZoom();
  const next = Math.max(0.01, Math.min(2.5, prev * multiplier));
  state.viewport.scale = next;
  setCanvasZoomIndicator();
  try {
    afterGraphViewport(editor.graph.zoomTo?.(next, false));
  } catch (error) {
    updateDebugState({lastViewportError: error.message || String(error)});
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
    updateDebugState({lastViewportError: error.message || String(error)});
  }
  return true;
}

export function fitG6CanvasToDiagram(bounds = null, {fit = false} = {}) {
  if (!editor?.graph) {
    return false;
  }
  const nodes = [...(editor.dataSnapshot?.nodesById?.keys?.() || [])];
  if (!nodes.length) {
    return false;
  }
  try {
    const rect = el.canvasViewport?.getBoundingClientRect?.();
    const scale = fit && bounds
        ? Math.max(0.01, Math.min(1, Math.min(
            ((rect?.width || 0) - 96) / bounds.width,
            ((rect?.height || 0) - 96) / bounds.height) || 1))
        : (state.viewport.scale || readGraphZoom() || 1);
    if (fit && bounds) {
      state.viewport.scale = scale;
      setCanvasZoomIndicator();
    }
    if (bounds && rect?.width && rect?.height) {
      const centerX = bounds.minX + bounds.width / 2;
      const centerY = bounds.minY + bounds.height / 2;
      state.viewport.x = Math.round(rect.width / 2 - centerX * scale);
      state.viewport.y = Math.round(rect.height / 2 - centerY * scale);
      const zoomResult = fit ? editor.graph.zoomTo?.(scale, false) : null;
      const translateResult = editor.graph.translateTo?.(
          [state.viewport.x, state.viewport.y], false);
      const results = [zoomResult, translateResult].filter(Boolean);
      afterGraphViewport(results.some((result) => result?.then)
          ? Promise.all(results) : translateResult || zoomResult);
    } else {
      const focusResult = editor.graph.focusElement?.(nodes[0], {duration: 0});
      afterGraphViewport(focusResult);
    }
  } catch (error) {
    updateDebugState({lastViewportError: error.message || String(error)});
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
      y: (py - state.viewport.y) / state.viewport.scale
    };
  }
  let converted = null;
  try {
    converted = editor.graph.getCanvasByClient?.([clientX, clientY]);
  } catch {
    try {
      converted = editor.graph.getCanvasByClient?.({x: clientX, y: clientY});
    } catch {
      converted = null;
    }
  }
  if (Array.isArray(converted)) {
    return {x: converted[0], y: converted[1]};
  }
  return converted || {x: 0, y: 0};
}

export function toClientCoordinates(x, y) {
  if (!editor?.graph) {
    const rect = el.canvasViewport.getBoundingClientRect();
    return {
      x: rect.left + x * state.viewport.scale + state.viewport.x,
      y: rect.top + y * state.viewport.scale + state.viewport.y
    };
  }
  let converted = null;
  try {
    converted = editor.graph.getClientByCanvas?.([x, y]);
  } catch {
    try {
      converted = editor.graph.getClientByCanvas?.({x, y});
    } catch {
      converted = null;
    }
  }
  if (Array.isArray(converted)) {
    return {x: converted[0], y: converted[1]};
  }
  return converted || {x: 0, y: 0};
}

export function focusG6Node(nodeId) {
  if (!editor?.graph || !nodeId) {
    return;
  }
  editor.graph.focusElement?.(nodeId, {duration: 280});
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
  syncG6FromState({full: false});
}

function scheduleG6LodUpdate(nextLod, showLabels) {
  pendingLodState = {nextLod, showLabels};
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

export function updateG6Lod({defer = false} = {}) {
  if (!editor?.graph) {
    return;
  }
  const zoom = currentZoom();
  const nextLod = detailLevelForZoom(zoom);
  const showLabels = shouldShowEdgeLabels(zoom,
      state.diagram.connections.length);
  if (nextLod === editor.lastLod && showLabels === editor.lastShowLabels) {
    return;
  }
  if (defer) {
    scheduleG6LodUpdate(nextLod, showLabels);
  } else {
    applyG6Lod(nextLod, showLabels);
  }
}

export function updateG6ContextBoxes(boxes = null, {useCache = false} = {}) {
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
    selectedContextName: state.selectedBoundedContextName,
    onSelect: editor.callbacks?.onContextSelect,
    onOpen: editor.callbacks?.onContextOpen
  });
}

export function updateG6NodeIcons() {
  if (!editor?.graph) {
    return;
  }
  renderNodeIcons(editor.graph, state.diagram.nodes, {
    visibleNode: editor.mapperOptions?.visibleNode || (() => true)
  });
}

export function onG6ViewportChanged() {
  if (!editor?.graph) {
    return;
  }
  scheduleViewportSync();
}
