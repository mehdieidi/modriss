import {state} from './state.js';
import {el} from './dom.js';
import {MODEL_TYPES} from './config.js';
import {api} from './api.js';
import {setStatus} from './status.js';
import {
  deleteBoundedContext,
  renameBoundedContext,
  renderDiagram
} from './canvas.js';
import {scheduleAutoSave} from './autosave.js';
import {isMobileViewport} from './responsive.js';
import {publishDiagramUpdate} from './collaboration.js';
import {relationshipIdsFromModel, toDiagram} from './diagram.js';
import {confirmAction} from './confirm-action.js';
import {
  removeElementFromGraph,
  removeRelationshipFromGraph
} from './graph-store.js';
import {captureDiagramUndoSnapshot, pushDiagramUndoSnapshot} from './undo.js';

// Fields managed by canvas – shown read-only
const READONLY_ATTR_KEYS = new Set(["id", "eClass", "x", "y"]);
// Fields skipped entirely (rendered via canvas label editing)
const SKIP_ATTR_KEYS = new Set(["label", "name", "tags", "status"]);

// ── Open / close ──────────────────────────────────────────────────────────────

export function openAttributePanel(nodeId) {
  const node = state.nodesById.get(nodeId);
  if (!node) {
    return;
  }

  state.selectedNodeId = nodeId;
  state.selectedNodeIds = new Set([nodeId]);
  state.selectedBoundedContextName = null;
  state.selectedConnectionId = null;

  el.nodeLayer.querySelectorAll(".node").forEach((n) => {
    n.classList.toggle("selected", n.dataset.nodeId === nodeId);
  });
  el.edgeLayer.querySelectorAll(".edge-path, .edge-label").forEach(
      (edge) => edge.classList.remove("selected"));

  el.attrPanelType.textContent = node.type;
  el.attrPanelTitle.textContent = node.label;
  if (el.attrPanelApplyBtn) {
    el.attrPanelApplyBtn.hidden = false;
    el.attrPanelApplyBtn.textContent = "✓ Apply Changes";
  }
  if (el.attrPanelDeleteBtn) {
    el.attrPanelDeleteBtn.hidden = false;
    el.attrPanelDeleteBtn.textContent = "🗑 Delete Element";
  }

  renderAttributeFields(node);

  el.modelTreePanel?.classList.add("hidden");
  el.attributePanel.classList.remove("hidden");
  el.workspace.classList.remove("views-open", "impact-open");
  el.workspace.classList.add("attr-open");
  if (isMobileViewport()) {
    el.workspace.classList.remove("mobile-left-open");
    el.workspace.classList.add("mobile-right-open");
    if (el.mobileBackdrop) {
      el.mobileBackdrop.classList.remove("hidden");
    }
  }
}

export function closeAttributePanel() {
  state.selectedNodeId = null;
  state.selectedNodeIds = new Set();
  state.selectedBoundedContextName = null;
  state.selectedConnectionId = null;
  el.nodeLayer.querySelectorAll(".node").forEach(
      (n) => n.classList.remove("selected"));
  el.edgeLayer.querySelectorAll(".edge-path, .edge-label").forEach(
      (edge) => edge.classList.remove("selected"));
  el.attributePanel.classList.add("hidden");
  el.workspace.classList.remove("attr-open");
  el.workspace.classList.remove("mobile-right-open");
  if (el.mobileBackdrop) {
    el.mobileBackdrop.classList.add("hidden");
  }
}

export function openConnectionPanel(connectionId) {
  const connection = state.diagram.connections.find(
      (edge) => edge.id === connectionId);
  if (!connection) {
    return;
  }
  state.selectedNodeId = null;
  state.selectedNodeIds = new Set();
  state.selectedBoundedContextName = null;
  state.selectedConnectionId = connectionId;
  el.nodeLayer.querySelectorAll(".node").forEach(
      (n) => n.classList.remove("selected"));
  el.edgeLayer.querySelectorAll(".edge-path, .edge-label").forEach((edge) => {
    edge.classList.toggle("selected", edge.dataset.edgeId === connectionId);
  });

  const source = state.nodesById.get(connection.sourceId);
  const target = state.nodesById.get(connection.targetId);
  el.attrPanelType.textContent = "Connection";
  el.attrPanelTitle.textContent = `${source?.label
  || connection.sourceId} → ${target?.label || connection.targetId}`;
  if (el.attrPanelApplyBtn) {
    el.attrPanelApplyBtn.hidden = true;
  }
  if (el.attrPanelDeleteBtn) {
    el.attrPanelDeleteBtn.hidden = false;
    el.attrPanelDeleteBtn.textContent = "🗑 Delete Connection";
  }

  el.attrPanelBody.innerHTML = "";
  el.attrPanelBody.appendChild(
      buildAttrField("kind", connection.kind, "text", true));
  el.attrPanelBody.appendChild(
      buildAttrField("source", source?.label || connection.sourceId, "text",
          true));
  el.attrPanelBody.appendChild(
      buildAttrField("target", target?.label || connection.targetId, "text",
          true));
  el.attrPanelBody.appendChild(
      buildAttrField("id", connection.id, "text", true));

  el.modelTreePanel?.classList.add("hidden");
  el.attributePanel.classList.remove("hidden");
  el.workspace.classList.remove("views-open", "impact-open");
  el.workspace.classList.add("attr-open");
  if (isMobileViewport()) {
    el.workspace.classList.remove("mobile-left-open");
    el.workspace.classList.add("mobile-right-open");
    if (el.mobileBackdrop) {
      el.mobileBackdrop.classList.remove("hidden");
    }
  }
}

export function openBoundedContextPanel(contextName) {
  if (!contextName) {
    return;
  }
  state.selectedNodeId = null;
  state.selectedNodeIds = new Set();
  state.selectedConnectionId = null;
  state.selectedBoundedContextName = contextName;
  el.nodeLayer.querySelectorAll(".node").forEach(
      (n) => n.classList.remove("selected"));
  el.edgeLayer.querySelectorAll(".edge-path, .edge-label").forEach(
      (edge) => edge.classList.remove("selected"));
  el.attrPanelType.textContent = "BoundedContext";
  el.attrPanelTitle.textContent = contextName;
  if (el.attrPanelApplyBtn) {
    el.attrPanelApplyBtn.hidden = false;
    el.attrPanelApplyBtn.textContent = "✓ Rename Context";
  }
  if (el.attrPanelDeleteBtn) {
    el.attrPanelDeleteBtn.hidden = false;
    el.attrPanelDeleteBtn.textContent = "🗑 Delete Context";
  }
  el.attrPanelBody.innerHTML = "";
  el.attrPanelBody.appendChild(
      buildAttrField("contextName", contextName, "text", false));
  el.modelTreePanel?.classList.add("hidden");
  el.attributePanel.classList.remove("hidden");
  el.workspace.classList.remove("views-open", "impact-open");
  el.workspace.classList.add("attr-open");
  if (isMobileViewport()) {
    el.workspace.classList.remove("mobile-left-open");
    el.workspace.classList.add("mobile-right-open");
    if (el.mobileBackdrop) {
      el.mobileBackdrop.classList.remove("hidden");
    }
  }
}

// ── Render attribute form fields ──────────────────────────────────────────────

function renderAttributeFields(node) {
  el.attrPanelBody.innerHTML = "";
  const meta = node.meta || {};

  const labelKey = state.activeType === "cim" ? "label" : "name";
  el.attrPanelBody.appendChild(
      buildAttrField(labelKey, node.label, "text", false));

  Object.entries(meta).forEach(([key, value]) => {
    if (key === labelKey || key === "label" || key === "name") {
      return;
    }
    if (SKIP_ATTR_KEYS.has(key)) {
      return;
    }
    const readonly = READONLY_ATTR_KEYS.has(key);
    el.attrPanelBody.appendChild(
        buildAttrField(key, value, inferFieldType(value), readonly));
  });
}

function inferFieldType(value) {
  if (Array.isArray(value) || (value && typeof value === "object")) {
    return "json";
  }
  if (typeof value === "boolean") {
    return "boolean";
  }
  if (typeof value === "number") {
    return "number";
  }
  if (typeof value === "string" && value.length > 80) {
    return "textarea";
  }
  return "text";
}

function buildAttrField(key, value, fieldType, readonly) {
  const wrapper = document.createElement("div");

  if (readonly) {
    wrapper.className = "attr-field";
    const lbl = document.createElement("label");
    lbl.textContent = key;
    const val = document.createElement("div");
    val.className = "attr-field-readonly";
    val.textContent = String(value);
    wrapper.appendChild(lbl);
    wrapper.appendChild(val);
    return wrapper;
  }

  if (fieldType === "boolean") {
    wrapper.className = "attr-field attr-field-checkbox";
    const input = document.createElement("input");
    input.type = "checkbox";
    input.id = `attr-${key}`;
    input.checked = Boolean(value);
    input.dataset.attrKey = key;
    input.dataset.attrType = "boolean";
    const lbl = document.createElement("label");
    lbl.htmlFor = `attr-${key}`;
    lbl.textContent = key;
    wrapper.appendChild(input);
    wrapper.appendChild(lbl);
    return wrapper;
  }

  wrapper.className = "attr-field";
  const lbl = document.createElement("label");
  lbl.htmlFor = `attr-${key}`;
  lbl.textContent = key;
  wrapper.appendChild(lbl);

  let input;
  if (fieldType === "textarea") {
    input = document.createElement("textarea");
    input.textContent = String(value ?? "");
  } else if (fieldType === "json") {
    input = document.createElement("textarea");
    input.textContent = JSON.stringify(value ?? null, null, 2);
  } else if (fieldType === "number") {
    input = document.createElement("input");
    input.type = "number";
    input.value = String(value ?? 0);
  } else {
    input = document.createElement("input");
    input.type = "text";
    input.value = String(value ?? "");
  }
  input.id = `attr-${key}`;
  input.dataset.attrKey = key;
  input.dataset.attrType = fieldType;
  wrapper.appendChild(input);
  return wrapper;
}

// ── Apply / delete ────────────────────────────────────────────────────────────

export function applyAttributePanel() {
  if (state.selectedBoundedContextName) {
    const currentName = state.selectedBoundedContextName;
    const input = el.attrPanelBody.querySelector(
        '[data-attr-key="contextName"]');
    const nextName = String(input?.value ?? "").trim();
    if (!nextName) {
      setStatus("Bounded context name cannot be empty");
      return;
    }
    if (!renameBoundedContext(currentName, nextName)) {
      return;
    }
    state.selectedBoundedContextName = nextName;
    el.attrPanelTitle.textContent = nextName;
    scheduleAutoSave();
    publishDiagramUpdate();
    setStatus(`Renamed bounded context to "${nextName}"`);
    return;
  }
  if (state.selectedConnectionId) {
    return;
  }
  const node = state.nodesById.get(state.selectedNodeId);
  if (!node) {
    return;
  }

  const labelKey = state.activeType === "cim" ? "label" : "name";
  const undoSnapshot = captureDiagramUndoSnapshot();

  try {
    el.attrPanelBody.querySelectorAll("[data-attr-key]").forEach((input) => {
      const key = input.dataset.attrKey;
      const attrType = input.dataset.attrType;
      let value;

      if (attrType === "boolean") {
        value = input.checked;
      } else if (attrType === "number") {
        value = Number(input.value);
      } else if (attrType === "json") {
        value = JSON.parse(input.value || "null");
      } else if (input.tagName === "TEXTAREA") {
        value = input.value;
      } else {
        value = input.value;
      }

      if (key === labelKey || key === "label" || key === "name") {
        node.label = String(value) || node.label;
        node.meta[labelKey] = node.label;
        if (node.meta.label !== undefined) {
          node.meta.label = node.label;
        }
        if (node.meta.name !== undefined) {
          node.meta.name = node.label;
        }
      } else {
        node.meta[key] = value;
      }
    });
  } catch {
    setStatus(
        "One property contains invalid JSON. Fix it before applying changes.");
    return;
  }

  if (undoSnapshot?.signature !== JSON.stringify(state.diagram || {})) {
    pushDiagramUndoSnapshot(undoSnapshot);
  }

  renderDiagram();
  const nodeEl = el.nodeLayer.querySelector(
      `[data-node-id="${state.selectedNodeId}"]`);
  if (nodeEl) {
    nodeEl.classList.add("selected");
  }

  el.attrPanelTitle.textContent = node.label;
  scheduleAutoSave();
  publishDiagramUpdate();
  setStatus(`Attributes updated for ${node.id}`);
}

export async function deleteSelection() {
  if (state.selectedBoundedContextName) {
    const contextName = state.selectedBoundedContextName;
    const confirmed = await confirmAction({
      title: "Delete Bounded Context",
      message: `Delete bounded context "${contextName}"?`,
      confirmLabel: "Delete",
      danger: true
    });
    if (!confirmed) {
      return;
    }
    if (!deleteBoundedContext(contextName)) {
      return;
    }
    closeAttributePanel();
    scheduleAutoSave();
    publishDiagramUpdate();
    setStatus(`Deleted bounded context "${contextName}"`);
    return;
  }
  if (state.selectedConnectionId) {
    await deleteSelectedConnection();
    return;
  }
  const nodeId = state.selectedNodeId;
  if (!nodeId) {
    return;
  }
  const node = state.nodesById.get(nodeId);
  if (!node) {
    return;
  }

  const confirmed = await confirmAction({
    title: "Delete Element",
    message: `Delete "${node.label}" (${node.type})?`,
    confirmLabel: "Delete",
    danger: true
  });
  if (!confirmed) {
    return;
  }

  pushDiagramUndoSnapshot();
  state.diagram.nodes = state.diagram.nodes.filter((n) => n.id !== nodeId);
  state.diagram.connections = state.diagram.connections.filter(
      (c) => c.sourceId !== nodeId && c.targetId !== nodeId
  );
  removeElementFromGraph(nodeId);

  closeAttributePanel();
  renderDiagram();
  scheduleAutoSave();
  publishDiagramUpdate();
  setStatus(`Deleted ${node.type}: ${nodeId}`);
}

export const deleteSelectedNode = deleteSelection;

export async function deleteSelectedConnection() {
  const connectionId = state.selectedConnectionId;
  if (!connectionId) {
    return;
  }
  const connection = state.diagram.connections.find(
      (edge) => edge.id === connectionId);
  if (!connection) {
    return;
  }
  const confirmed = await confirmAction({
    title: "Delete Connection",
    message: `Delete connection "${connection.kind}"?`,
    confirmLabel: "Delete",
    danger: true
  });
  if (!confirmed) {
    return;
  }

  const undoSnapshot = captureDiagramUndoSnapshot();
  const persistedConnectionIds = new Set(
      relationshipIdsFromModel(state.activeType, state.baseModel || {}));
  const shouldDeletePersistedRelationship = Boolean(
      state.modelId && persistedConnectionIds.has(connectionId));

  if (shouldDeletePersistedRelationship) {
    const updated = await api(
        `/${MODEL_TYPES[state.activeType].apiType}/${state.modelId}/relationships/${encodeURIComponent(
            connectionId)}`, {
          method: "DELETE"
        });
    state.baseModel = structuredClone(updated.modelJson);
    state.diagram = toDiagram(state.activeType, updated.modelJson,
        updated.name);
  } else {
    state.diagram.connections = state.diagram.connections.filter(
        (edge) => edge.id !== connectionId);
  }
  removeRelationshipFromGraph(connectionId);
  pushDiagramUndoSnapshot(undoSnapshot);

  closeAttributePanel();
  renderDiagram();
  scheduleAutoSave();
  publishDiagramUpdate();
  setStatus(`Deleted connection: ${connection.kind}`);
}
