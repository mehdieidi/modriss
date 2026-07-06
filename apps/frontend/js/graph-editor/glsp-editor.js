import { getCanvasFitArea } from "../canvas-viewport-fit.js";
import { state } from "../state.js";
import { el } from "../dom.js";
import { modelingLevelConfig } from "../modeling-config-data.js";
import { detailLevelForZoom, shouldShowEdgeLabels } from "./g6-performance.js";
import { bindGlspInteractions } from "./glsp-interactions.js";
import { mapDiagramToGlsp } from "./glsp-mapper.js";
import {
  applyViewportTransform,
  clientToGraphPoint,
  renderGlspScene,
  showInlineLabelEditor,
  updateNodePosition,
} from "./glsp-renderer-core.js";

let editor = null;

function updateDebugState(patch = {}) {
  window.modlessGlspState = {
    ...(window.modlessGlspState || {}),
    ...patch,
    mounted: Boolean(editor?.host),
    renderer: "glsp-sprotty",
  };
}

function installDebugProbe() {
  window.modlessGlspDebug = () => {
    const host = el.glspEditorHost;
    const hostRect = host?.getBoundingClientRect?.();
    return {
      ...(window.modlessGlspState || {}),
      activeType: state.activeType,
      stateNodes: state.diagram.nodes.length,
      stateEdges: state.diagram.connections.length,
      hostRect: hostRect
        ? { width: Math.round(hostRect.width), height: Math.round(hostRect.height) }
        : null,
      hostChildren: host?.children?.length || 0,
      hasSvg: Boolean(host?.querySelector("svg")),
      viewport: { ...state.viewport },
    };
  };
}

function setCanvasZoomIndicator() {
  if (el.canvasZoomValue) {
    el.canvasZoomValue.textContent = `${Math.round((state.viewport.scale || 1) * 100)}%`;
  }
}

function mapperOptions() {
  const zoom = state.viewport.scale || 1;
  const edgeCount = state.diagram.connections.length;
  return {
    typeKey: state.activeType,
    detailLevel: detailLevelForZoom(zoom),
    showLabels: shouldShowEdgeLabels(zoom, edgeCount),
    selectedEdgeId: state.selectedConnectionId,
    hoveredEdgeId: editor?.hoveredEdgeId || "",
    isContainer: editor?.mapperOptions?.isContainer || (() => false),
    visibleNode: editor?.mapperOptions?.visibleNode || (() => true),
    viewProfile: editor?.mapperOptions?.viewProfile || "",
  };
}

function buildOverlays() {
  const overlays = {
    contextBoxes: editor?.callbacks?.contextBoxes?.() || [],
    validationByElement: {},
    impactByElement: {},
  };

  for (const issue of state.validation?.issues || []) {
    const id = issue.elementId || issue.relationshipId;
    if (!id) {
      continue;
    }
    if (!overlays.validationByElement[id]) {
      overlays.validationByElement[id] = [];
    }
    overlays.validationByElement[id].push(issue);
  }

  if (state.impactMode && state.impactData) {
    const mark = (id) => {
      if (id) {
        overlays.impactByElement[id] = true;
      }
    };
    mark(state.impactData.focalElement?.elementId);
    for (const bucket of ["upstream", "downstream", "connectedElements"]) {
      for (const item of state.impactData[bucket] || []) {
        mark(item?.elementId);
      }
    }
  }

  return overlays;
}

function buildRenderState() {
  const options = mapperOptions();
  const visibleNodes = state.diagram.nodes.filter((node) => options.visibleNode?.(node) !== false);
  const visibleNodeIds = new Set(visibleNodes.map((node) => node.id));
  const visibleConnections = state.diagram.connections.filter(
    (edge) => visibleNodeIds.has(edge.sourceId) && visibleNodeIds.has(edge.targetId),
  );
  const diagram = mapDiagramToGlsp(
    { nodes: visibleNodes, connections: visibleConnections },
    options,
  );
  return {
    diagram: { ...diagram, overlays: buildOverlays() },
    levelConfig: modelingLevelConfig(state.activeType),
    viewport: state.viewport,
    detailLevel: options.detailLevel,
    selection: {
      nodeId: state.selectedNodeId,
      edgeId: state.selectedConnectionId,
    },
    hovered: {
      nodeId: editor?.hoveredNodeId || null,
      edgeId: editor?.hoveredEdgeId || null,
    },
    selectedContextName: "",
    connectionDrag: state.linkDrag,
    overlays: buildOverlays(),
  };
}

function paintSceneNow() {
  if (!editor?.host || editor.dragging) {
    return;
  }
  renderGlspScene(editor.host, buildRenderState());
  syncSelectionCacheFromState();
  updateDebugState({
    lastPaint: {
      nodes: state.diagram.nodes.length,
      edges: state.diagram.connections.length,
    },
  });
}

let paintSceneFrame = 0;

function schedulePaintScene() {
  if (!editor?.host) {
    return;
  }
  if (editor.dragging) {
    editor.needsPaint = true;
    return;
  }
  if (paintSceneFrame) {
    return;
  }
  paintSceneFrame = window.requestAnimationFrame(() => {
    paintSceneFrame = 0;
    paintSceneNow();
  });
}

function paintScene() {
  schedulePaintScene();
}

function flushPaintAfterDrag() {
  if (!editor?.needsPaint) {
    return;
  }
  editor.needsPaint = false;
  schedulePaintScene();
}

export function isGlspAvailable() {
  return true;
}

export function mountGlspEditor(container, { callbacks = {}, mapper = {} } = {}) {
  installDebugProbe();
  const host = container;
  if (!host) {
    throw new Error("GLSP editor host is missing");
  }

  if (editor?.host === host) {
    editor.callbacks = callbacks;
    editor.mapperOptions = mapper;
    paintSceneNow();
    return editor;
  }

  editor?.disposeInteractions?.();
  host.classList.add("is-mounted", "is-connected");
  host.classList.remove("glsp-idle");
  host.replaceChildren();

  editor = {
    host,
    callbacks,
    mapperOptions: mapper,
    hoveredNodeId: null,
    hoveredEdgeId: null,
    contextBoxesCache: [],
    contextBoxesDirty: true,
    dragging: false,
    draggedNodeId: null,
    selectionNodeIds: new Set(),
    selectionEdgeId: null,
    needsPaint: false,
    moveNodeVisual: (nodeId, x, y) => updateGlspNodePosition(nodeId, x, y),
    beginNodeDrag: (nodeId) => beginGlspNodeDrag(nodeId),
    endNodeDrag: () => endGlspNodeDrag(),
  };

  bindGlspInteractions(editor, callbacks);
  paintSceneNow();
  setCanvasZoomIndicator();
  updateDebugState({ lastMount: "mounted" });
  return editor;
}

export function getGlspEditor() {
  return editor;
}

export function renderGlspDiagram() {
  paintScene();
}

export function syncGlspFromState({ full = false } = {}) {
  if (!editor?.host || editor.dragging) {
    if (!editor?.host) {
      updateDebugState({ lastError: "syncGlspFromState before mount" });
    }
    return;
  }
  void full;
  paintScene();
}

function syncSelectionCacheFromState() {
  if (!editor) {
    return;
  }
  const nextNodeIds = new Set(state.selectedNodeIds || []);
  if (state.selectedNodeId) {
    nextNodeIds.add(state.selectedNodeId);
  }
  editor.selectionNodeIds = nextNodeIds;
  editor.selectionEdgeId = state.selectedConnectionId || null;
}

export function updateGlspSelection() {
  if (!editor?.host || editor.dragging) {
    if (editor?.dragging) {
      editor.needsPaint = true;
    }
    return;
  }
  const host = editor.host;
  const prevNodeIds = editor.selectionNodeIds || new Set();
  const prevEdgeId = editor.selectionEdgeId || null;
  const nextNodeIds = new Set(state.selectedNodeIds || []);
  if (state.selectedNodeId) {
    nextNodeIds.add(state.selectedNodeId);
  }
  const nextEdgeId = state.selectedConnectionId || null;

  for (const id of prevNodeIds) {
    if (!nextNodeIds.has(id)) {
      host
        .querySelector(`.glsp-node[data-id="${CSS.escape(id)}"]`)
        ?.classList.remove("is-selected");
    }
  }
  for (const id of nextNodeIds) {
    if (!prevNodeIds.has(id)) {
      host.querySelector(`.glsp-node[data-id="${CSS.escape(id)}"]`)?.classList.add("is-selected");
    }
  }
  if (prevEdgeId !== nextEdgeId) {
    if (prevEdgeId) {
      host
        .querySelector(`.glsp-edge[data-id="${CSS.escape(prevEdgeId)}"]`)
        ?.classList.remove("is-selected");
    }
    if (nextEdgeId) {
      host
        .querySelector(`.glsp-edge[data-id="${CSS.escape(nextEdgeId)}"]`)
        ?.classList.add("is-selected");
    }
  }
  editor.selectionNodeIds = nextNodeIds;
  editor.selectionEdgeId = nextEdgeId;
}

export function updateGlspConnectionState() {
  paintScene();
}

export function setGlspHoverNode(nodeId) {
  updateGlspHoverVisual(nodeId, editor?.hoveredEdgeId || null);
}

export function setGlspHoverEdge(edgeId) {
  updateGlspHoverVisual(editor?.hoveredNodeId || null, edgeId);
}

export function updateGlspHoverVisual(nodeId, edgeId) {
  if (!editor?.host) {
    return;
  }
  const nextNode = nodeId || null;
  const nextEdge = edgeId || null;
  if (editor.hoveredNodeId === nextNode && editor.hoveredEdgeId === nextEdge) {
    return;
  }
  const host = editor.host;
  if (editor.hoveredNodeId) {
    host
      .querySelector(`.glsp-node[data-id="${CSS.escape(editor.hoveredNodeId)}"]`)
      ?.classList.remove("is-hovered");
  }
  if (editor.hoveredEdgeId) {
    host
      .querySelector(`.glsp-edge[data-id="${CSS.escape(editor.hoveredEdgeId)}"]`)
      ?.classList.remove("is-hovered");
  }
  editor.hoveredNodeId = nextNode;
  editor.hoveredEdgeId = nextEdge;
  if (nextNode) {
    host
      .querySelector(`.glsp-node[data-id="${CSS.escape(nextNode)}"]`)
      ?.classList.add("is-hovered");
  }
  if (nextEdge) {
    host
      .querySelector(`.glsp-edge[data-id="${CSS.escape(nextEdge)}"]`)
      ?.classList.add("is-hovered");
  }
}

export function updateGlspImpactState() {
  paintScene();
}

export function updateGlspViewport() {
  if (!editor?.host) {
    return;
  }
  applyViewportTransform(editor.host, state.viewport);
  setCanvasZoomIndicator();
}

export function zoomGlspCanvasBy(multiplier = 1) {
  if (!editor?.host) {
    return false;
  }
  const prev = state.viewport.scale || 1;
  const next = Math.max(0.01, Math.min(2.5, prev * multiplier));
  state.viewport.scale = next;
  applyViewportTransform(editor.host, state.viewport);
  setCanvasZoomIndicator();
  editor.callbacks?.onViewportChange?.();
  paintScene();
  return true;
}

export function resetGlspCanvasView() {
  if (!editor?.host) {
    return false;
  }
  if (state.diagram?.nodes?.length) {
    return fitGlspCanvasToDiagram(null, { fit: true });
  }
  state.viewport.x = 0;
  state.viewport.y = 0;
  state.viewport.scale = 1;
  applyViewportTransform(editor.host, state.viewport);
  setCanvasZoomIndicator();
  editor.callbacks?.onViewportChange?.();
  paintScene();
  return true;
}

export function fitGlspCanvasToDiagram(bounds = null, { fit = false, fitArea = null } = {}) {
  if (!editor?.host || !state.diagram.nodes.length) {
    return false;
  }
  const rect = el.canvasViewport?.getBoundingClientRect?.();
  const area =
    fitArea ||
    getCanvasFitArea(rect) ||
    (rect?.width && rect?.height
      ? {
          width: Math.max(1, rect.width - 96),
          height: Math.max(1, rect.height - 96),
          centerX: rect.width / 2,
          centerY: rect.height / 2,
        }
      : null);
  const resolved =
    bounds ||
    (() => {
      let minX = Infinity;
      let minY = Infinity;
      let maxX = -Infinity;
      let maxY = -Infinity;
      for (const node of state.diagram.nodes) {
        const w = Number(node.width) || 228;
        const h = Number(node.height) || 112;
        minX = Math.min(minX, node.x);
        minY = Math.min(minY, node.y);
        maxX = Math.max(maxX, node.x + w);
        maxY = Math.max(maxY, node.y + h);
      }
      return {
        minX,
        minY,
        width: Math.max(1, maxX - minX),
        height: Math.max(1, maxY - minY),
      };
    })();

  const scale =
    fit && area?.width && area?.height
      ? Math.max(
          0.01,
          Math.min(2.5, Math.min(area.width / resolved.width, area.height / resolved.height) || 1),
        )
      : state.viewport.scale || 1;

  if (fit) {
    state.viewport.scale = scale;
  }
  if (area?.width && area?.height) {
    const centerX = resolved.minX + resolved.width / 2;
    const centerY = resolved.minY + resolved.height / 2;
    state.viewport.x = Math.round(area.centerX - centerX * scale);
    state.viewport.y = Math.round(area.centerY - centerY * scale);
  }
  applyViewportTransform(editor.host, state.viewport);
  setCanvasZoomIndicator();
  editor.callbacks?.onViewportChange?.();
  paintScene();
  return true;
}

export function toGraphCoordinates(clientX, clientY) {
  if (editor?.host) {
    return clientToGraphPoint(editor.host, clientX, clientY);
  }
  const rect = el.canvasViewport?.getBoundingClientRect?.();
  const px = clientX - (rect?.left || 0);
  const py = clientY - (rect?.top || 0);
  return {
    x: (px - state.viewport.x) / state.viewport.scale,
    y: (py - state.viewport.y) / state.viewport.scale,
  };
}

export function focusGlspNode(nodeId) {
  const node = state.nodesById.get(nodeId);
  if (!node || !editor?.host) {
    return false;
  }
  const rect = el.canvasViewport?.getBoundingClientRect?.();
  const scale = state.viewport.scale || 1;
  const w = Number(node.width) || 228;
  const h = Number(node.height) || 112;
  state.viewport.x = Math.round((rect?.width || 0) / 2 - (node.x + w / 2) * scale);
  state.viewport.y = Math.round((rect?.height || 0) / 2 - (node.y + h / 2) * scale);
  applyViewportTransform(editor.host, state.viewport);
  setCanvasZoomIndicator();
  editor.callbacks?.onViewportChange?.();
  return true;
}

export function focusGlspCanvasPoint() {
  return resetGlspCanvasView();
}

export function beginGlspInlineLabelEdit(node, handlers = {}) {
  if (!editor?.host || !node) {
    return;
  }
  showInlineLabelEditor(editor.host, node, handlers);
}

export function updateGlspContextBoxes(boxes = null, { useCache = false } = {}) {
  if (!editor) {
    return;
  }
  if (boxes) {
    editor.contextBoxesCache = boxes;
    editor.contextBoxesDirty = false;
  } else if (useCache && !editor.contextBoxesDirty) {
    // keep cache
  } else {
    editor.contextBoxesCache = editor.callbacks?.contextBoxes?.() || [];
    editor.contextBoxesDirty = false;
  }
  paintScene();
}

export function updateGlspNodePosition(nodeId, x, y) {
  if (!editor?.host || !nodeId) {
    return;
  }
  const connections = state.diagram.connections.filter(
    (edge) => edge.sourceId === nodeId || edge.targetId === nodeId,
  );
  updateNodePosition(editor.host, nodeId, x, y, connections, state.nodesById);
}

export function beginGlspNodeDrag(nodeId) {
  if (!editor) {
    return;
  }
  editor.dragging = true;
  editor.draggedNodeId = nodeId;
}

export function endGlspNodeDrag() {
  if (!editor) {
    return;
  }
  editor.dragging = false;
  editor.draggedNodeId = null;
  flushPaintAfterDrag();
}

export function updateGlspNode() {
  paintScene();
}

export function updateGlspEdge() {
  paintScene();
}

export function updateGlspNodeIcons() {
  paintScene();
}

export function refreshGlspEdges() {
  paintScene();
}

export function addGlspNode(node) {
  if (!node) {
    return;
  }
  paintScene();
}

export function addGlspEdge(edge) {
  if (!edge) {
    return;
  }
  paintScene();
}

export function updateGlspMountOptions({ callbacks = {}, mapper = {} } = {}) {
  if (!editor) {
    return;
  }
  if (callbacks && Object.keys(callbacks).length) {
    editor.callbacks = callbacks;
  }
  if (mapper && Object.keys(mapper).length) {
    editor.mapperOptions = mapper;
  }
}

export function onGlspViewportChanged() {
  setCanvasZoomIndicator();
}

export class ModlessGlspRenderer {
  isAvailable() {
    return isGlspAvailable();
  }

  getEditor() {
    return editor ? this : null;
  }

  async mount(host, options = {}) {
    mountGlspEditor(host, {
      callbacks: options.callbacks || {},
      mapper: options.mapper || {},
    });
    return true;
  }

  async syncFromState(options = {}) {
    if (!editor?.host) {
      return;
    }
    if (options.callbacks) {
      editor.callbacks = { ...editor.callbacks, ...options.callbacks };
    }
    if (options.mapper) {
      editor.mapperOptions = { ...editor.mapperOptions, ...options.mapper };
    }
    syncGlspFromState(options);
  }

  updateMountOptions(options = {}) {
    if (!editor) {
      return;
    }
    updateGlspMountOptions({
      callbacks: options.callbacks || {},
      mapper: options.mapper || {},
    });
  }

  renderDiagram() {
    renderGlspDiagram();
  }
  updateSelection() {
    updateGlspSelection();
  }
  updateViewport() {
    updateGlspViewport();
  }
  updateConnectionState() {
    updateGlspConnectionState();
  }
  updateContextBoxes(...args) {
    updateGlspContextBoxes(...args);
  }
  updateImpactState() {
    updateGlspImpactState();
  }
  updateNode() {
    updateGlspNode();
  }
  updateEdge() {
    updateGlspEdge();
  }
  updateNodeIcons() {
    updateGlspNodeIcons();
  }
  refreshEdges() {
    refreshGlspEdges();
  }
  resetCanvasView() {
    resetGlspCanvasView();
  }
  zoomCanvasBy(multiplier) {
    zoomGlspCanvasBy(multiplier);
  }
  fitCanvasToDiagram(bounds, options) {
    fitGlspCanvasToDiagram(bounds, options);
  }
  focusNode(nodeId) {
    focusGlspNode(nodeId);
  }
  focusCanvasPoint() {
    focusGlspCanvasPoint();
  }
  onViewportChanged() {
    onGlspViewportChanged();
  }
  beginInlineLabelEdit(node, handlers) {
    beginGlspInlineLabelEdit(node, handlers);
  }
  addNode(node) {
    addGlspNode(node);
  }
  addEdge(edge) {
    addGlspEdge(edge);
  }
  setHoverNode(nodeId) {
    setGlspHoverNode(nodeId);
  }
  setHoverEdge(edgeId) {
    setGlspHoverEdge(edgeId);
  }
  applyElkLayout() {}
  undo() {}
  redo() {}
  canUndo() {
    return false;
  }
  canRedo() {
    return false;
  }
  async unmount() {
    editor?.disposeInteractions?.();
    if (editor?.host) {
      editor.host.replaceChildren();
      editor.host.classList.remove("is-mounted", "is-connected", "glsp-idle");
    }
    editor = null;
  }
}

export function createGlspRenderer() {
  return new ModlessGlspRenderer();
}

export function createRenderer(kind) {
  if (kind === "glsp-sprotty") {
    return createGlspRenderer();
  }
  return null;
}

export const MODLESS_DIAGRAM_TYPE = "modless-diagram";
