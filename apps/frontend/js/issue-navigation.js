import { state } from "./state.js";
import { genId } from "./utils.js";
import {
  activeView,
  ensureViewContent,
  prepareViewNodeIndex,
  reconcileGraphRelationshipsIfDirty,
  setActiveViewId,
} from "./graph-store.js";
import { CURRENT_LAYOUT_GEOMETRY_VERSION, visibleIdsForView } from "./view-materializer.js";

function resolveIssueTarget(detail) {
  const ids = [
    ...new Set(
      [detail.id, ...(Array.isArray(detail.targetIds) ? detail.targetIds : [])]
        .map((id) => String(id || "").trim())
        .filter(Boolean),
    ),
  ];
  for (const id of ids) {
    if (state.graph.elementsById.has(id)) return { id, kind: "node" };
    if (state.graph.relationshipsById.has(id)) return { id, kind: "connection" };
    // A many-target semantic relationship can have several canvas edge IDs.
    const relationship = [...state.graph.relationshipsById.values()].find(
      (entry) => entry.semanticObjectId === id,
    );
    if (relationship) return { id: relationship.id, kind: "connection" };
  }
  const name = String(detail.elementName || "")
    .trim()
    .toLowerCase();
  const type = String(detail.elementType || "")
    .trim()
    .toLowerCase();
  const matches = name
    ? [...state.graph.elementsById.values()].filter(
        (element) =>
          String(element.name || element.label || "")
            .trim()
            .toLowerCase() === name &&
          (!type || String(element.eClass || element.type || "").toLowerCase() === type),
      )
    : [];
  return matches.length === 1 ? { id: matches[0].id, kind: "node" } : null;
}

function viewShowsTarget(view, target) {
  const { visibleElementIds, relationshipIds } = visibleIdsForView(view);
  return target.kind === "node"
    ? visibleElementIds.includes(target.id)
    : relationshipIds.includes(target.id);
}

function createTemporaryIssueView(target) {
  const element = state.graph.elementsById.get(target.id);
  const relationship = state.graph.relationshipsById.get(target.id);
  const elementIds =
    target.kind === "node"
      ? [target.id]
      : [...new Set([relationship.sourceElementId, relationship.targetElementId])];
  if (elementIds.some((id) => !state.graph.elementsById.has(id))) return null;
  const label = element?.name || element?.label || relationship?.name || target.id;
  const view = {
    id: `${state.activeType}-issue-${genId()}`,
    name: `${label} (Temporary)`,
    kind: "FOCUS",
    _temporaryIssueView: true,
    _returnViewId: state.views.activeViewId,
    filters: { elementTypes: [], relationshipKinds: [] },
    hidden: { elementIds: [], relationshipIds: [] },
    autoLayoutApplied: true,
    layoutGeometryVersion: CURRENT_LAYOUT_GEOMETRY_VERSION,
    nodes: elementIds.map((elementId, index) => ({ elementId, x: 80 + index * 360, y: 80 })),
    edges: target.kind === "connection" ? [{ relationshipId: target.id, visible: true }] : [],
  };
  prepareViewNodeIndex(view);
  return view;
}

// Resolve the primary target before choosing a view. Parent containers and related
// elements must never outrank a target that exists but is absent from every canvas.
export function activateIssueTarget(detail = {}) {
  reconcileGraphRelationshipsIfDirty(state.activeType);
  const target = resolveIssueTarget(detail);
  if (!target) return null;
  let view = activeView();
  if (!view || !viewShowsTarget(view, target)) {
    view = [...state.views.byId.values()].find((candidate) => viewShowsTarget(candidate, target));
  }
  let temporary = false;
  if (!view) {
    view = createTemporaryIssueView(target);
    if (!view) return null;
    temporary = true;
    state.views.byId.set(view.id, view);
  }
  const changedView = view.id !== state.views.activeViewId;
  if (changedView) {
    ensureViewContent(view, state.activeType, { skipClientLayout: true });
    // Temporary issue navigation retains the route back through an existing focus.
    // Selecting a permanent view abandons the stack just like the view picker.
    if (!temporary) state.canvasFocusStack = [];
    if (!setActiveViewId(view.id)) return null;
  }
  if (temporary) {
    state.canvasFocusStack.push({
      typeKey: state.activeType,
      elementId: target.id,
      label: view.name,
      previousViewId: view._returnViewId,
      focusViewId: view.id,
    });
  }
  return { ...target, viewId: view.id, changedView, temporary: Boolean(view._temporaryIssueView) };
}
