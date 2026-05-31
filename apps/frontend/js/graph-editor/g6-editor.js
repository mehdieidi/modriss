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
  hideNodeTools,
  renderContextBoxes,
  showInlineLabelEditor,
  showNodeTools
} from './g6-overlays.js';

let editor = null;
let extensionsRegistered = false;
let pendingViewportFrame = 0;

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
      const dimmed = attributes.dimmed || stateSet.has("dimmed");
      const draft = attributes.contextDraft || stateSet.has("context-draft");
      const low = attributes.detailLevel === "low";
      const high = attributes.detailLevel === "high";

      if (diagramType === "cim") {
        const fill = attributes.sticky || "#fde68a";
        super.render({
          ...attributes,
          fill,
          stroke: selected ? cssVar("--accent-select", "#5ecbff")
              : "rgba(21, 28, 40, 0.24)",
          lineWidth: selected ? 2.4 : 1,
          radius: attributes.notation === "context" ? 8 : 6,
          labelText: ""
        }, container);
        this.upsert("key", "rect", {
          x: left,
          y: top,
          width,
          height,
          radius: attributes.notation === "context" ? 8 : 6,
          fill,
          stroke: connectLegal ? "#16a34a"
              : connectSource ? cssVar("--accent-select", "#5ecbff")
                  : connectIllegal ? "rgba(15, 23, 42, 0.22)"
                      : selected ? cssVar("--accent-select", "#5ecbff")
                          : impact ? "#f97316"
                              : "rgba(21, 28, 40, 0.24)",
          lineWidth: selected || connectLegal || connectSource || draft ? 2.4
              : (attributes.notation === "aggregate"
              || attributes.notation === "context" ? 2 : 1),
          lineDash: attributes.notation === "external"
          || attributes.notation === "context" || draft ? [6, 4] : undefined,
          shadowColor: selected || hovered || connectLegal
              ? "rgba(8, 14, 24, 0.36)" : "rgba(12, 18, 28, 0.28)",
          shadowBlur: selected || hovered || connectLegal ? 18 : 10,
          opacity: dimmed || connectIllegal ? 0.48 : 1,
          cursor: "pointer"
        }, container);
        this.upsert("corner", "path", {
          path: [
            ["M", left + width - 18, top],
            ["L", left + width, top],
            ["L", left + width, top + 18],
            ["Z"]
          ],
          fill: "rgba(255,255,255,0.58)",
          opacity: 0.62,
          pointerEvents: "none"
        }, container);
        this.upsert("type", "text", low ? false : {
          x: left + 10,
          y: top + 15,
          text: truncate(attributes.typeText || "Element", 26).toUpperCase(),
          fontFamily: cssVar("--font-display", "sans-serif"),
          fontSize: 9,
          fontWeight: 800,
          fill: "rgba(35, 28, 18, 0.86)",
          textBaseline: "middle",
          pointerEvents: "none"
        }, container);
        this.upsert("label", "text", {
          x: left + 10,
          y: top + (low ? 24 : 38),
          text: lineBreak(attributes.labelText || "", low ? 18 : 21,
              low ? 1 : 2),
          fontFamily: cssVar("--font-ui", "sans-serif"),
          fontSize: low ? 10 : 12,
          fontWeight: 700,
          fill: "rgba(24, 20, 14, 0.92)",
          textBaseline: "top",
          pointerEvents: "none"
        }, container);
        this.upsert("notation", "text", low ? false : {
          x: left + 10,
          y: top + height - (high ? 22 : 16),
          text: truncate(attributes.notationText || "", 29),
          fontFamily: cssVar("--font-ui", "sans-serif"),
          fontSize: 9.5,
          fontWeight: 600,
          fill: "rgba(45, 38, 26, 0.72)",
          textBaseline: "middle",
          pointerEvents: "none"
        }, container);
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
          lineDash: attributes.isCollapsed || draft ? [6, 4] : undefined,
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
          height: 40,
          radius: [2, 2, 0, 0],
          fill: "rgba(35,42,55,0.58)",
          stroke: "transparent",
          pointerEvents: "none"
        }, container);
        this.upsert("icon", "rect", low ? false : {
          x: left + 11,
          y: top + 9,
          width: 22,
          height: 22,
          radius: 5,
          fill: accent,
          opacity: 0.78,
          pointerEvents: "none"
        }, container);
        this.upsert("type", "text", low ? false : {
          x: left + 42,
          y: top + 21,
          text: truncate(attributes.typeText || "Element", 27).toUpperCase(),
          fontFamily: cssVar("--font-display", "sans-serif"),
          fontSize: 10,
          fontWeight: 800,
          fill: accent,
          textBaseline: "middle",
          pointerEvents: "none"
        }, container);
        this.upsert("dot", "circle", low ? false : {
          cx: left + width - 15,
          cy: top + 20,
          r: 4,
          fill: accent,
          opacity: 0.9,
          pointerEvents: "none"
        }, container);
        this.upsert("label", "text", {
          x: left + 11,
          y: top + (low ? 18 : 53),
          text: lineBreak(attributes.labelText || "", low ? 28 : 30,
              low ? 1 : 2),
          fontFamily: cssVar("--font-ui", "sans-serif"),
          fontSize: low ? 10.5 : 12,
          fontWeight: 700,
          fill: cssVar("--text", "#e3e8f2"),
          textBaseline: "top",
          pointerEvents: "none"
        }, container);
        this.upsert("notation", "text", !high || !attributes.notationText
            ? false : {
              x: left + 11,
              y: top + height - 20,
              text: truncate(attributes.notationText || "", 34),
              fontFamily: cssVar("--font-ui", "sans-serif"),
              fontSize: 10,
              fontWeight: 600,
              fill: cssVar("--muted", "#98a8c0"),
              textBaseline: "middle",
              pointerEvents: "none"
            }, container);
      }

      this.upsert("leftHandle", "circle", {
        cx: left,
        cy: 0,
        r: 7,
        fill: cssVar("--node-bg", "#131923"),
        stroke: cssVar("--accent-select", "#5ecbff"),
        lineWidth: 2,
        opacity: low ? 0.75 : 1,
        cursor: "crosshair"
      }, container);
      this.upsert("rightHandle", "circle", {
        cx: left + width,
        cy: 0,
        r: 7,
        fill: cssVar("--node-bg", "#131923"),
        stroke: cssVar("--accent-select", "#5ecbff"),
        lineWidth: 2,
        opacity: low ? 0.75 : 1,
        cursor: "crosshair"
      }, container);
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
      const source = sourcePoint || [0, 0];
      const target = targetPoint || [0, 0];
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
      const dimmed = attributes.dimmed || stateSet.has("dimmed");
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
      const midpoint = points[Math.floor(points.length / 2)] || points[0]
          || {x: 0, y: 0};
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

function graphDataFromState() {
  return mapDiagramToG6({
    nodes: state.diagram.nodes,
    edges: state.diagram.connections,
    ...mapperOptions()
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
}

function ensureDataSnapshot() {
  if (!editor.dataSnapshot) {
    rememberDataSnapshot({nodes: [], edges: []});
  }
  return editor.dataSnapshot;
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

function settleNativeViewport() {
  syncViewportStateFromGraph();
  updateG6Lod();
  updateG6ContextBoxes();
  updateG6Selection();
  editor?.callbacks?.onViewportSynced?.();
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
  }
  editor.dataSnapshot = diff.snapshot;
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
    batch[id] = [...(map.get(id) || [])];
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
    behaviors: [
      "zoom-canvas",
      {
        type: "drag-element",
        key: "modless-drag-element",
        enable: (event) => event?.targetType === "node"
      }
    ],
    node: {
      type: G6_BASE_NODE_TYPE,
      state: {
        selected: {selected: true},
        hover: {hover: true},
        dimmed: {dimmed: true},
        "connect-source": {connectSource: true},
        "connect-legal": {connectLegal: true},
        "connect-illegal": {connectIllegal: true},
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
        selected: {selected: true},
        hover: {hovered: true},
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
    connectStateIds: new Set(),
    connectStateKey: "",
    hoveredNodeId: null,
    hoveredEdgeId: null,
    adjacency: createAdjacencyIndex(state.diagram.connections),
    lastLod: detailLevelForZoom(state.viewport.scale),
    lastShowLabels: true,
    viewportReady: false,
    viewportRetryCount: 0
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
  scheduleGraphRender(editor.graph);
}

export function syncG6FromState({full = false} = {}) {
  if (!editor?.graph) {
    updateDebugState({lastError: "syncG6FromState before mount"});
    return;
  }
  const data = graphDataFromState();
  updateDebugState(
      {lastSync: {full, nodes: data.nodes.length, edges: data.edges.length}});
  resizeGraphToHost();
  if (full || !editor.dataSnapshot) {
    editor.graph.setData?.(data);
    rememberDataSnapshot(data);
    editor.adjacency = createAdjacencyIndex(state.diagram.connections);
    editor.connectStateKey = "";
    scheduleGraphRender(editor.graph);
  } else {
    applyDiff(data);
  }
  updateG6Selection();
  updateG6ConnectionState();
  updateG6ImpactState();
  updateG6ContextBoxes();
}

export function addG6Node(node) {
  if (!editor?.graph || !node) {
    return;
  }
  const mapped = mapNodeToG6(node, mapperOptions());
  editor.graph.addNodeData?.([mapped]);
  rememberNodeData(mapped);
  scheduleGraphRender(editor.graph);
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
  scheduleGraphDraw(editor.graph);
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
  scheduleGraphRender(editor.graph);
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
  const edge = state.diagram.connections.find((item) => item.id === edgeId);
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
  const ids = edgeIds?.length ? new Set(edgeIds) : null;
  const edges = state.diagram.connections.filter((edge) =>
      !ids || ids.has(edge.id)).map((edge) => mapEdgeToG6(edge,
      mapperOptions()));
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
  if (state.selectedNodeId && state.selectedNodeIds?.size === 1) {
    const node = state.nodesById.get(state.selectedNodeId);
    const isContainer = editor.mapperOptions?.isContainer?.(node);
    const isBoundedContext = node?.type === "BoundedContextCandidate"
        && state.activeType === "cim";
    showNodeTools(editor.graph, node, {
      isContainer,
      isBoundedContext,
      collapsed: Boolean(node?.meta?.__collapsed),
      onOpen: editor.callbacks?.onOpenContainer,
      onCollapseToggle: editor.callbacks?.onToggleContainerCollapsed
    });
  } else {
    hideNodeTools();
  }
}

export function updateG6ConnectionState() {
  if (!editor?.graph) {
    return;
  }
  const sourceId = state.connectSourceId || state.linkDrag?.sourceId || "";
  const nextKey = sourceId ? [
    sourceId,
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
    state.nodesById.forEach((node, id) => {
      let flag = "";
      if (id === sourceId) {
        flag = "connect-source";
      } else if (source) {
        flag = editor.callbacks?.connectionTargetState?.(source, node)
        === "legal"
            ? "connect-legal" : "connect-illegal";
      }
      if (flag) {
        editor.connectStateIds.add(id);
        if (setFlag(editor.nodeStateFlags, id, flag, true)) {
          changed.add(id);
        }
      }
    });
  }
  flushElementStates(changed, editor.nodeStateFlags);
}

export function setG6HoverNode(nodeId) {
  if (!editor?.graph || editor.hoveredNodeId === nodeId) {
    return;
  }
  const changedNodes = new Set();
  const changedEdges = new Set();
  const previous = editor.hoveredNodeId;
  editor.hoveredNodeId = nodeId || null;
  if (previous) {
    if (setFlag(editor.nodeStateFlags, previous, "hover", false)) {
      changedNodes.add(previous);
    }
    const activeEdges = editor.adjacency?.byNode?.get(previous) || new Set();
    activeEdges.forEach((edgeId) => {
      if (setFlag(editor.edgeStateFlags, edgeId, "hover", false)) {
        changedEdges.add(edgeId);
      }
    });
  }
  if (nodeId) {
    if (setFlag(editor.nodeStateFlags, nodeId, "hover", true)) {
      changedNodes.add(nodeId);
    }
    const activeEdges = editor.adjacency?.byNode?.get(nodeId) || new Set();
    activeEdges.forEach((edgeId) => {
      if (setFlag(editor.edgeStateFlags, edgeId, "hover", true)) {
        changedEdges.add(edgeId);
      }
    });
  }
  flushElementStates(changedNodes, editor.nodeStateFlags);
  flushElementStates(changedEdges, editor.edgeStateFlags);
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
  syncViewportStateFromGraph();
  updateG6Lod();
  updateG6ContextBoxes();
  updateG6Selection();
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
  let result = null;
  try {
    if (fit && bounds) {
      const rect = el.canvasViewport?.getBoundingClientRect?.();
      const padding = 96;
      const fitScale = Math.min(
          ((rect?.width || 0) - padding) / bounds.width,
          ((rect?.height || 0) - padding) / bounds.height);
      const next = Math.max(0.01, Math.min(1, fitScale || 1));
      state.viewport.scale = next;
      setCanvasZoomIndicator();
      result = editor.graph.zoomTo?.(next, false);
    }
    const centerX = bounds ? bounds.minX + bounds.width / 2 : null;
    const centerY = bounds ? bounds.minY + bounds.height / 2 : null;
    const focusNodeId = bounds ? nodes.reduce((bestId, nodeId) => {
      const node = state.nodesById.get(nodeId)
          || state.diagram.nodes.find((candidate) => candidate.id === nodeId);
      const best = state.nodesById.get(bestId)
          || state.diagram.nodes.find((candidate) => candidate.id === bestId);
      const nodeDistance = node ? Math.hypot(node.x - centerX, node.y - centerY)
          : Number.POSITIVE_INFINITY;
      const bestDistance = best ? Math.hypot(best.x - centerX, best.y - centerY)
          : Number.POSITIVE_INFINITY;
      return nodeDistance < bestDistance ? nodeId : bestId;
    }, nodes[0]) : nodes[0];
    const focusResult = editor.graph.focusElement?.(focusNodeId, {duration: 0});
    afterGraphViewport(focusResult || result);
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
    syncViewportStateFromGraph();
    updateG6ContextBoxes();
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

export function updateG6Lod() {
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
  editor.lastLod = nextLod;
  editor.lastShowLabels = showLabels;
  syncG6FromState({full: false});
}

export function updateG6ContextBoxes(boxes = null) {
  if (!editor?.graph) {
    return;
  }
  const resolved = boxes || editor.callbacks?.contextBoxes?.() || [];
  renderContextBoxes(editor.graph, resolved, {
    selectedContextName: state.selectedBoundedContextName,
    onSelect: editor.callbacks?.onContextSelect,
    onOpen: editor.callbacks?.onContextOpen
  });
}

export function onG6ViewportChanged() {
  syncViewportStateFromGraph();
  setCanvasZoomIndicator();
  el.canvasGrid?.style.setProperty("--viewport-scale",
      String(state.viewport.scale || 1));
  el.canvasGrid?.classList.toggle("lod-low", state.viewport.scale < 0.35);
  el.canvasGrid?.classList.toggle("lod-medium", state.viewport.scale >= 0.35
      && state.viewport.scale < 0.75);
  el.canvasGrid?.classList.toggle("lod-high", state.viewport.scale >= 1.5);
  updateG6Lod();
  updateG6ContextBoxes();
  updateG6Selection();
  editor.callbacks?.onViewportSynced?.();
}
