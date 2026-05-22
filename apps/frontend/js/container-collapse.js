import {setStatus} from './status.js';
import {activeView, syncActiveViewFromVisibleGraph} from './graph-store.js';
import {materializeActiveView} from './view-materializer.js';

let renderDiagramCallback = null;
let renderWorkbenchCallback = null;

function safeArray(value) {
  return Array.isArray(value) ? value : [];
}

export function configureContainerCollapse({
  renderDiagram,
  renderWorkbench
} = {}) {
  renderDiagramCallback = renderDiagram || renderDiagramCallback;
  renderWorkbenchCallback = renderWorkbench || renderWorkbenchCallback;
}

export function isContainerCollapsed(elementId) {
  const view = activeView();
  return safeArray(view?.collapsedElementIds).includes(elementId)
      || safeArray(view?.nodes).some((node) => node.elementId === elementId
          && node.collapsed);
}

export function setContainerCollapsed(elementId, collapsed) {
  const view = activeView();
  if (!view || !elementId) {
    return false;
  }
  syncActiveViewFromVisibleGraph();
  const collapsedIds = new Set(safeArray(view.collapsedElementIds));
  if (collapsed) {
    collapsedIds.add(elementId);
  } else {
    collapsedIds.delete(elementId);
  }
  view.collapsedElementIds = [...collapsedIds];
  view.nodes = safeArray(view.nodes).map((node) => node.elementId === elementId
      ? {...node, collapsed: Boolean(collapsed)}
      : node);
  materializeActiveView();
  renderDiagramCallback?.();
  renderWorkbenchCallback?.();
  setStatus(collapsed ? "Container collapsed" : "Container expanded");
  return true;
}

export function toggleContainerCollapsed(elementId) {
  return setContainerCollapsed(elementId, !isContainerCollapsed(elementId));
}
