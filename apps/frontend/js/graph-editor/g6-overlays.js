import {state} from '../state.js';
import {el} from '../dom.js';
import {nodeSizeForDiagram} from './g6-style.js';

let overlayRoot = null;
let labelInput = null;
let previewSvg = null;
let contextLayer = null;
let nodeIconLayer = null;
let contextBoxItems = new Map();

function ensureOverlayRoot() {
  if (overlayRoot?.isConnected) {
    return overlayRoot;
  }
  overlayRoot = document.createElement("div");
  overlayRoot.className = "g6-overlay-root";
  el.canvasViewport?.appendChild(overlayRoot);
  return overlayRoot;
}

function viewportPointFromClient(clientX, clientY) {
  const rect = el.canvasViewport?.getBoundingClientRect();
  return {
    x: clientX - (rect?.left || 0),
    y: clientY - (rect?.top || 0)
  };
}

function graphClientPoint(graph, x, y) {
  let converted = null;
  try {
    converted = graph?.getClientByCanvas?.([x, y]);
  } catch {
    try {
      converted = graph?.getClientByCanvas?.({x, y});
    } catch {
      converted = null;
    }
  }
  if (Array.isArray(converted)) {
    return {x: converted[0], y: converted[1]};
  }
  if (converted && Number.isFinite(converted.x)
      && Number.isFinite(converted.y)) {
    return converted;
  }
  const rect = el.canvasViewport?.getBoundingClientRect();
  return {
    x: (rect?.left || 0) + x * state.viewport.scale + state.viewport.x,
    y: (rect?.top || 0) + y * state.viewport.scale + state.viewport.y
  };
}

export function canvasToViewportPoint(graph, x, y) {
  const point = graphClientPoint(graph, x, y);
  return viewportPointFromClient(point.x, point.y);
}

export function clearInlineLabelEditor() {
  labelInput?.remove();
  labelInput = null;
}

export function showInlineLabelEditor(graph, node, {
  onCommit = () => {
  },
  onCancel = () => {
  }
} = {}) {
  if (!node) {
    return;
  }
  clearInlineLabelEditor();
  const root = ensureOverlayRoot();
  const size = nodeSizeForDiagram(state.activeType);
  const topLeft = graphClientPoint(graph, node.x, node.y);
  const viewport = viewportPointFromClient(topLeft.x, topLeft.y);
  labelInput = document.createElement("input");
  labelInput.className = "g6-inline-label-editor";
  labelInput.value = node.label || "";
  labelInput.style.left = `${Math.round(viewport.x + 12)}px`;
  labelInput.style.top = `${Math.round(viewport.y + size.height / 2 - 15)}px`;
  labelInput.style.width = `${Math.max(80, size.width - 24)}px`;
  root.appendChild(labelInput);
  labelInput.focus();
  labelInput.select();

  const cleanup = () => {
    labelInput?.removeEventListener("keydown", onKeyDown);
    labelInput?.removeEventListener("blur", onBlur);
    clearInlineLabelEditor();
  };
  const commit = () => {
    const next = labelInput?.value || "";
    cleanup();
    onCommit(next);
  };
  const cancel = () => {
    cleanup();
    onCancel();
  };

  function onKeyDown(event) {
    if (event.key === "Enter") {
      event.preventDefault();
      commit();
    } else if (event.key === "Escape") {
      event.preventDefault();
      cancel();
    }
  }

  function onBlur() {
    commit();
  }

  labelInput.addEventListener("keydown", onKeyDown);
  labelInput.addEventListener("blur", onBlur);
}

function ensurePreviewSvg() {
  if (previewSvg?.isConnected) {
    return previewSvg;
  }
  previewSvg = document.createElementNS("http://www.w3.org/2000/svg", "svg");
  previewSvg.classList.add("g6-connection-preview");
  ensureOverlayRoot().appendChild(previewSvg);
  return previewSvg;
}

export function updateConnectionPreview(graph, sourceNode, clientX, clientY) {
  if (!sourceNode) {
    clearConnectionPreview();
    return;
  }
  const size = nodeSizeForDiagram(state.activeType);
  const startClient = graphClientPoint(graph, sourceNode.x + size.width,
      sourceNode.y + size.height / 2);
  const start = viewportPointFromClient(startClient.x, startClient.y);
  const end = viewportPointFromClient(clientX, clientY);
  const svg = ensurePreviewSvg();
  svg.innerHTML = `<path d="M ${start.x} ${start.y} L ${end.x} ${end.y}" />`;
}

export function clearConnectionPreview() {
  previewSvg?.remove();
  previewSvg = null;
}

function ensureContextLayer() {
  if (contextLayer?.isConnected) {
    return contextLayer;
  }
  contextLayer = document.createElement("div");
  contextLayer.className = "g6-context-layer";
  ensureOverlayRoot().appendChild(contextLayer);
  return contextLayer;
}

export function renderNodeIcons(graph, nodes = [], {
  visibleNode = () => true
} = {}) {
  nodeIconLayer?.remove();
  nodeIconLayer = null;
}

export function renderContextBoxes(graph, boxes = [], {
  selectedContextName = "",
  onSelect = () => {
  }
} = {}) {
  if (!boxes.length) {
    contextLayer?.remove();
    contextLayer = null;
    contextBoxItems.clear();
    return;
  }
  const layer = ensureContextLayer();
  const keep = new Set();
  boxes.forEach((box) => {
    const key = String(box.name || "");
    if (!key) {
      return;
    }
    keep.add(key);
    const min = graphClientPoint(graph, box.minX, box.minY);
    const max = graphClientPoint(graph, box.maxX, box.maxY);
    const a = viewportPointFromClient(min.x, min.y);
    const b = viewportPointFromClient(max.x, max.y);
    let record = contextBoxItems.get(key);
    if (!record) {
      const item = document.createElement("div");
      item.className = "g6-bounded-context-box";
      const label = document.createElement("div");
      label.className = "g6-bounded-context-label";
      const name = document.createElement("span");
      label.append(name);
      item.appendChild(label);
      record = {item, name, contextName: key, onSelect};
      item.addEventListener("click", (event) => {
        record.onSelect(record.contextName);
      });
      contextBoxItems.set(key, record);
      layer.appendChild(item);
    }
    record.contextName = key;
    record.onSelect = onSelect;
    record.name.textContent = key;
    record.item.classList.toggle("selected", selectedContextName === key);
    record.item.dataset.contextName = key;
    record.item.style.left = `${Math.round(Math.min(a.x, b.x))}px`;
    record.item.style.top = `${Math.round(Math.min(a.y, b.y))}px`;
    record.item.style.width = `${Math.max(1,
        Math.round(Math.abs(b.x - a.x)))}px`;
    record.item.style.height = `${Math.max(1,
        Math.round(Math.abs(b.y - a.y)))}px`;
  });
  contextBoxItems.forEach((record, key) => {
    if (keep.has(key)) {
      return;
    }
    record.item.remove();
    contextBoxItems.delete(key);
  });
}

export function clearG6Overlays() {
  clearInlineLabelEditor();
  clearConnectionPreview();
  nodeIconLayer?.remove();
  nodeIconLayer = null;
  contextLayer?.remove();
  contextLayer = null;
  contextBoxItems.clear();
}
