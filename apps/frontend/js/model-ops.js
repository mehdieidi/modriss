import { apiUrl, MODEL_TYPES } from "./config.js";
import { state } from "./state.js";
import { el } from "./dom.js";
import { api, apiAuthHeaders } from "./api.js";
import { flushCurrentModelPatch, prepareModelForSave } from "./model-patch.js";
import { setBusy, setError, setStatus } from "./status.js";
import { formatUserError } from "./errors.js";
import { emptyDiagram, genId, scheduleIdleTask, stringifyJsonAsync, yieldToMain } from "./utils.js";
import { serializeModel } from "./diagram.js";
import {
  activeView,
  ensureViewContent,
  ensureViewContentAsync,
  installGraphAndViews,
  installGraphAndViewsAsync,
  restoreTabGraphState,
  saveCurrentTabGraphState,
  setActiveViewId,
  syncActiveViewFromVisibleGraph,
} from "./graph-store.js";
import { materializeActiveView } from "./view-materializer.js";
import {
  iconAnchorBoundsFromNodeRect,
  measureIconNodeSize,
  routePointOnIconAnchor,
} from "./graph-editor/icon-node-metrics.js";
import {
  fitViewportToDiagram,
  renderDiagram,
  renderDiagramAsync,
  renderPalette,
  scrollToConnectionAndHighlight,
  scrollToNodeAndHighlight,
} from "./canvas.js";
import { closeAttributePanel } from "./attr-panel.js";
import { closeImpactPanel } from "./impact.js";
import { loadArtifactById, loadArtifactRecord, loadCurrentProjectArtifact } from "./artifact.js";
import { confirmAction } from "./confirm-action.js";
import { requireActiveProject } from "./project-guards.js";
import {
  completeGenerationProgress,
  hideGenerationProgress,
  setGenerationProgressPhase,
  showGenerationProgress,
} from "./generation-progress.js";
import {
  applyValidationIssues,
  bindValidationCenterUi,
  clearValidationIssues,
  isMethodologyValidationError,
  setValidationInProgress,
  toggleValidationDrawer,
} from "./methodology-validation.js";
import { renderViewWorkbench } from "./view-explorer.js";
import {
  applyDiagramUndoSnapshot,
  clearDiagramUndoHistory,
  hasDiagramUndoHistory,
  popDiagramUndoSnapshot,
} from "./undo.js";
import {
  beginModelSave,
  completeModelSave,
  failModelSave,
  hasUnsavedModelChanges,
  markModelDirty,
  resetModelSaveState,
  updateModelSaveUi,
} from "./model-save-ui.js";
import {
  isModelingLevel,
  modelingDefaultLayoutStrategy,
  modelingLevelConfig,
  modelingLevelListLabel,
  transformationForLevel,
} from "./modeling-config-data.js";

let autoLayoutPromise = null;
let saveInFlight = null;

export function isModelSaveInFlight() {
  return Boolean(saveInFlight);
}

// ── Model list (sidebar select) ───────────────────────────────────────────────

function defaultModelName(typeKey = state.activeType) {
  return (
    state.modelingConfig.config?.levels?.[typeKey]?.modelNameTemplate ||
    `${typeKey || "model"}-model`
  );
}

function isModelingType(typeKey = state.activeType) {
  return isModelingLevel(typeKey);
}

async function centerCurrentDiagram({ fit = true } = {}) {
  await fitViewportToDiagram({ fit });
}

function captureModelReplacementSnapshot(typeKey = state.activeType) {
  const tabState = state.tabs[typeKey];
  const serializedModel =
    typeKey === state.activeType
      ? serializeModel({ syncView: true, reconcileRelationships: true })
      : structuredClone(tabState?.baseModel || {});
  return {
    typeKey,
    modelId: state.modelId,
    modelRevision: state.modelRevision || 0,
    modelName: tabState?.modelName || defaultModelName(typeKey),
    baseModel: serializedModel,
    diagram: structuredClone(state.diagram || emptyDiagram(typeKey)),
    graph: state.graph
      ? structuredClone({
          elements: [...state.graph.elementsById.values()],
          relationships: [...state.graph.relationshipsById.values()],
          traceLinks: [...state.graph.traceLinksById.values()],
          assumptions: [...state.graph.assumptionsById.values()],
          validationIssues: state.graph.validationIssues || [],
          manualBacklog: state.graph.manualBacklog || [],
        })
      : null,
    views: state.views ? structuredClone([...state.views.byId.values()]) : null,
    fragments: state.fragments ? structuredClone([...state.fragments.byId.values()]) : null,
    activeViewId: state.views?.activeViewId || null,
    validationIssues: structuredClone(state.validation.issues || []),
  };
}

function pushModelReplacementSnapshot(typeKey = state.activeType) {
  if (!isModelingType(typeKey) || !state.baseModel) {
    return;
  }
  state.undo.modelReplacements.push(captureModelReplacementSnapshot(typeKey));
  while (state.undo.modelReplacements.length > 20) {
    state.undo.modelReplacements.shift();
  }
}

async function applyModelReplacementSnapshot(
  snapshot,
  { statusMessage = "", persist = true } = {},
) {
  if (!snapshot || !isModelingType(snapshot.typeKey)) {
    return;
  }
  state.modelId = snapshot.modelId;
  state.modelRevision = snapshot.modelRevision || 0;
  state.baseModel = structuredClone(snapshot.baseModel);
  state.diagram = structuredClone(snapshot.diagram);
  if (state.tabs[snapshot.typeKey]) {
    state.tabs[snapshot.typeKey].modelId = snapshot.modelId;
    state.tabs[snapshot.typeKey].modelRevision = snapshot.modelRevision || 0;
    state.tabs[snapshot.typeKey].baseModel = structuredClone(snapshot.baseModel);
    state.tabs[snapshot.typeKey].diagram = structuredClone(snapshot.diagram);
    state.tabs[snapshot.typeKey].graph = structuredClone(snapshot.graph);
    state.tabs[snapshot.typeKey].views = structuredClone(snapshot.views);
    state.tabs[snapshot.typeKey].fragments = structuredClone(snapshot.fragments);
    state.tabs[snapshot.typeKey].activeViewId = snapshot.activeViewId;
    state.tabs[snapshot.typeKey].modelName =
      snapshot.modelName || defaultModelName(snapshot.typeKey);
    state.tabs[snapshot.typeKey].dirty = !persist;
  }
  restoreTabGraphState(snapshot.typeKey);
  materializeActiveView();
  setActiveModelName(snapshot.modelName || defaultModelName(snapshot.typeKey));
  clearDiagramUndoHistory(snapshot.typeKey);
  renderDiagram();
  renderViewWorkbench();
  await centerCurrentDiagram();
  if (persist) {
    await saveCurrentModel({ quiet: true, rethrow: true });
  } else {
    resetModelSaveState({ dirty: true });
  }
  if (Array.isArray(snapshot.validationIssues) && snapshot.validationIssues.length) {
    applyValidationIssues(snapshot.validationIssues, { openOnFirst: true });
    toggleValidationDrawer(true);
  } else {
    clearValidationIssues({ keepPanelState: false });
  }
  if (statusMessage) {
    setStatus(statusMessage);
  }
}

async function downloadBlobFromResponse(response, fallbackFilename) {
  const blob = await response.blob();
  const contentDisposition = response.headers.get("content-disposition") || "";
  const utf8Filename = contentDisposition.match(/filename\*=UTF-8''([^;]+)/i);
  const basicFilename = contentDisposition.match(/filename="?([^";]+)"?/i);
  const filename = utf8Filename
    ? decodeURIComponent(utf8Filename[1])
    : basicFilename?.[1] || fallbackFilename;
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement("a");
  anchor.href = url;
  anchor.download = filename;
  anchor.click();
  URL.revokeObjectURL(url);
}

function manualGuidanceIssuesFromModel(modelJson) {
  const backlog = mergedManualBacklogFromModel(modelJson);
  return backlog.map((task, index) => {
    const manualTaskId = manualTaskIdentity(task, index);
    const resolved = String(task?.status || "").toUpperCase() === "DONE";
    const required =
      Boolean(task?.required) || String(task?.category || "").toUpperCase() === "MANUAL_MODELING";
    const enforcement = String(task?.enforcement || "").trim();
    const mandatory = required || enforcement.startsWith("MANDATORY");
    const taskTitle = String(task?.name || task?.title || `Manual task ${index + 1}`);
    const elementId =
      firstReferenceId(
        task?.elementId,
        task?.relatedElementId,
        task?.targetElementId,
        task?.sourceElementId,
        task?.affectedElements,
        task?.relatedElements,
      ) || null;
    return {
      code: mandatory ? "MANUAL_REQUIRED" : "MANUAL_OPTIONAL",
      severity: resolved ? "INFO" : mandatory ? "ERROR" : "WARNING",
      constraint: mandatory ? "ManualTaskRequired" : "ManualTaskOptional",
      issueClass: resolved ? "MANUAL_RESOLVED" : mandatory ? "MANUAL_REQUIRED" : "MANUAL_OPTIONAL",
      manualTaskId,
      resolved,
      elementId,
      elementType: String(task?.elementType || task?.relatedElementType || ""),
      elementName: taskTitle,
      affectedElements: task?.affectedElements || task?.relatedElements || [],
      message: `Complete the manual task: ${taskTitle}.`,
      guidance: String(
        task?.rationale ||
          task?.description ||
          "Review the affected element, record the decision, and mark this task complete.",
      ),
    };
  });
}

function manualBacklogIdentity(task, _index) {
  const explicitId = String(task?.id || "").trim();
  if (explicitId) {
    return `id:${explicitId}`;
  }
  const title = String(task?.name || task?.title || "")
    .trim()
    .toLowerCase();
  const elementId = String(
    task?.elementId ||
      task?.relatedElementId ||
      task?.targetElementId ||
      task?.sourceElementId ||
      "",
  )
    .trim()
    .toLowerCase();
  const category = String(task?.category || "")
    .trim()
    .toLowerCase();
  return `fallback:${category}:${title}:${elementId}`;
}

function mergedManualBacklogFromModel(modelJson) {
  const merged = [];
  const seen = new Set();
  const decisions = Array.isArray(modelJson?.readiness?.manualDecisions)
    ? modelJson.readiness.manualDecisions
    : [];
  [
    ...(Array.isArray(modelJson?.manualBacklog) ? modelJson.manualBacklog : []),
    ...(Array.isArray(modelJson?.graph?.manualBacklog) ? modelJson.graph.manualBacklog : []),
  ].forEach((task, index) => {
    const key = manualBacklogIdentity(task, index);
    if (seen.has(key)) {
      return;
    }
    seen.add(key);
    const matchingDecision = decisions.find(
      (decision) =>
        String(decision?.name || "").trim() === String(task?.name || task?.title || "").trim(),
    );
    if (
      matchingDecision?.affectedElements &&
      !task?.affectedElements &&
      !task?.elementId &&
      !task?.relatedElementId
    ) {
      merged.push({ ...task, affectedElements: matchingDecision.affectedElements });
      return;
    }
    merged.push(task);
  });
  return merged;
}

function manualTaskIdentity(task, index) {
  const explicitId = String(task?.id || "").trim();
  if (explicitId) {
    return explicitId;
  }
  const title = String(task?.name || task?.title || "")
    .trim()
    .toLowerCase();
  const elementId = String(task?.elementId || task?.relatedElementId || "")
    .trim()
    .toLowerCase();
  return `manual-task-${index}-${sanitizeManualTaskKey(title)}-${sanitizeManualTaskKey(elementId)}`;
}

function firstReferenceId(...values) {
  for (const value of values) {
    const id = referenceId(value);
    if (id) {
      return id;
    }
  }
  return "";
}

function referenceId(value) {
  if (value == null) {
    return "";
  }
  if (typeof value === "string" || typeof value === "number" || typeof value === "boolean") {
    return String(value).trim();
  }
  if (Array.isArray(value)) {
    return firstReferenceId(...value);
  }
  if (typeof value === "object") {
    return firstReferenceId(
      value.$ref,
      value.id,
      value.elementId,
      value.targetElementId,
      value.sourceElementId,
    );
  }
  return "";
}

function sanitizeManualTaskKey(value) {
  return (
    String(value || "")
      .replaceAll(/[^a-z0-9_-]+/g, "-")
      .replaceAll(/^-+|-+$/g, "") || "na"
  );
}

function ensureManualBacklogIdentity(model) {
  if (!model) {
    return;
  }
  const backlog = mergedManualBacklogFromModel(model);
  model.manualBacklog = backlog;
  model.graph ??= {};
  model.graph.manualBacklog = backlog.map((task) => structuredClone(task));
  model.manualBacklog.forEach((task, _index) => {
    if (!task || typeof task !== "object") {
      return;
    }
    if (!String(task.id || "").trim()) {
      task.id = genId();
    }
    if (!String(task.status || "").trim()) {
      task.status = "OPEN";
    }
  });
}

function stripServerTransportFields(model) {
  if (!model || typeof model !== "object") {
    return model;
  }
  delete model._sourceXmiBase64;
  delete model._sourceXmiToken;
  return model;
}

function adoptSavedBaseModel(nextModel) {
  state.baseModel = stripServerTransportFields(nextModel);
}

function updateActiveTabAfterSave() {
  const tab = state.tabs[state.activeType];
  if (!tab) {
    return;
  }
  tab.modelId = state.modelId;
  tab.modelRevision = state.modelRevision;
  tab.baseModel = state.baseModel;
  tab.diagram = state.diagram;
}

function schedulePostSaveHousekeeping(typeKey, modelId, summary) {
  rememberModelSummary(typeKey, summary);
  void scheduleIdleTask(async () => {
    if (state.activeType === typeKey && state.tabs[typeKey]) {
      saveCurrentTabGraphState(typeKey);
    }
    await syncProjectActiveModel(typeKey, modelId);
    await reloadModels();
  });
}

function mergePersistedViewIntoBaseModel(view) {
  if (!view?.id) {
    return state.baseModel;
  }
  const base =
    state.baseModel && typeof state.baseModel === "object"
      ? state.baseModel
      : (state.baseModel = {});
  if (!Array.isArray(base.views)) {
    base.views = [];
  }
  const copy = structuredClone(view);
  delete copy._lazyContent;
  const index = base.views.findIndex((candidate) => candidate?.id === copy.id);
  if (index >= 0) {
    base.views[index] = copy;
  } else {
    base.views.push(copy);
  }
  return base;
}

function persistedRelationshipIdsForView() {
  const fromBase = Array.isArray(state.baseModel?.graph?.relationships)
    ? state.baseModel.graph.relationships
    : [];
  const ids = fromBase.map((relationship) => String(relationship?.id || "").trim()).filter(Boolean);
  if (ids.length) {
    return new Set(ids);
  }
  return new Set(
    [...state.graph.relationshipsById.entries()]
      .filter(([, relationship]) => !relationship?.visualOnly)
      .map(([relationshipId]) => relationshipId),
  );
}

function isPersistableViewEdge(relationshipId, persistedIds) {
  const id = String(relationshipId || "").trim();
  if (!id || id.startsWith("containment-")) {
    return false;
  }
  const relationship = state.graph.relationshipsById.get(id);
  if (relationship?.visualOnly) {
    return false;
  }
  return persistedIds.has(id);
}

function serializeViewForPersistence(view) {
  const copy = structuredClone(view);
  delete copy._lazyContent;
  const persistedIds = persistedRelationshipIdsForView();
  if (Array.isArray(copy.edges)) {
    copy.edges = copy.edges.filter((edge) =>
      isPersistableViewEdge(edge.relationshipId || edge.id, persistedIds),
    );
  }
  return copy;
}

function viewPatchOperations(payload) {
  const operations = [];
  if (!Array.isArray(state.baseModel?.views)) {
    operations.push({ op: "add", path: "/views", value: [] });
  }
  operations.push({ op: "add", path: "/views/-", value: payload });
  return operations;
}

async function persistViewForBackendLayout(view) {
  if (!state.modelId || !view?.id) {
    return false;
  }
  if (storedModelHasView(view.id)) {
    return true;
  }
  const payload = serializeViewForPersistence(view);
  const patchUrl = `/${MODEL_TYPES[state.activeType].apiType}/${state.modelId}`;
  const patchBody = (operations) =>
    JSON.stringify({
      operations,
      expectedRevision: state.modelRevision || 1,
    });

  const updated = await api(patchUrl, {
    method: "PATCH",
    body: patchBody(viewPatchOperations(payload)),
  });
  mergePersistedViewIntoBaseModel(payload);
  if (updated && typeof updated === "object") {
    state.modelRevision = Number(updated.revision) || state.modelRevision;
  }
  return true;
}

function manualGuidanceIssuesFromCurrentModel() {
  ensureManualBacklogIdentity(state.baseModel);
  return manualGuidanceIssuesFromModel(state.baseModel || {});
}

function storedValidationIssuesFromCurrentModel() {
  const model = state.baseModel || {};
  if (Array.isArray(model.validationIssues)) {
    return structuredClone(model.validationIssues);
  }
  if (Array.isArray(model.graph?.validationIssues)) {
    return structuredClone(model.graph.validationIssues);
  }
  return [];
}

function mergeIssuesWithManualGuidance(issues) {
  const baseIssues = Array.isArray(issues) ? issues : [];
  const manualIssues = manualGuidanceIssuesFromCurrentModel();
  if (!manualIssues.length) {
    return baseIssues;
  }
  const seen = new Set();
  const merged = [];
  [...baseIssues, ...manualIssues].forEach((issue) => {
    const manualTaskId = String(issue?.manualTaskId || "").trim();
    const issueClass = String(issue?.issueClass || "");
    const key =
      manualTaskId && issueClass.startsWith("MANUAL_")
        ? `manual::${manualTaskId}`
        : [
            String(issue?.constraint || issue?.code || ""),
            String(issue?.elementId || ""),
            String(issue?.message || ""),
          ].join("::");
    if (seen.has(key)) {
      return;
    }
    seen.add(key);
    merged.push(issue);
  });
  return merged;
}

function isManualGuidanceIssue(issue) {
  return String(issue?.issueClass || "").startsWith("MANUAL_");
}

async function setManualTaskResolved(manualTaskId, resolved) {
  const model = state.baseModel;
  if (!model || !Array.isArray(model.manualBacklog)) {
    return;
  }
  ensureManualBacklogIdentity(model);
  const id = String(manualTaskId || "").trim();
  if (!id) {
    return;
  }
  const task = model.manualBacklog.find((item, index) => manualTaskIdentity(item, index) === id);
  if (!task) {
    return;
  }
  task.id = id;
  task.status = resolved ? "DONE" : "OPEN";
  if (Array.isArray(state.graph?.manualBacklog)) {
    const graphTask = state.graph.manualBacklog.find(
      (item, index) => manualTaskIdentity(item, index) === id,
    );
    if (graphTask) {
      graphTask.id = id;
      graphTask.status = task.status;
    }
  }
  await saveCurrentModel({ quiet: true, rethrow: true });
  const merged = mergeIssuesWithManualGuidance(
    state.validation.issues.filter(
      (issue) => !String(issue?.issueClass || "").startsWith("MANUAL_"),
    ),
  );
  applyValidationIssues(merged, { openOnFirst: false });
}

async function applyManualGuidanceFromLoadedModel() {
  const modelJson = state.baseModel;
  const backendIssues = storedValidationIssuesFromCurrentModel();
  const allGuidanceIssues = manualGuidanceIssuesFromModel(modelJson);
  const mergedIssues = mergeIssuesWithManualGuidance(backendIssues);
  if (!mergedIssues.length) {
    return;
  }
  const openGuidanceIssues = allGuidanceIssues.filter((item) => !item.resolved);
  applyValidationIssues(mergedIssues, { openOnFirst: true });
  toggleValidationDrawer(true);
  await fitViewportToDiagram({ fit: true, frames: 3 });
  const warningCount = mergedIssues.filter(
    (item) =>
      !isManualGuidanceIssue(item) && String(item?.severity || "").toUpperCase() === "WARNING",
  ).length;
  const requiredCount = openGuidanceIssues.filter((item) => item.severity === "ERROR").length;
  const optionalCount = openGuidanceIssues.length - requiredCount;
  const parts = [];
  if (warningCount) {
    parts.push(`${warningCount} warning(s)`);
  }
  if (requiredCount || optionalCount) {
    parts.push(`${requiredCount} required and ${optionalCount} optional manual task(s)`);
  }
  if (!parts.length) {
    parts.push(`${mergedIssues.length} informational issue(s)`);
  }
  setStatus(`Generated model includes ${parts.join(", ")}.`);
}

export function getActiveModelName() {
  const tabState = state.tabs[state.activeType];
  const currentName = tabState?.modelName;
  return (currentName || defaultModelName()).trim();
}

export function setActiveModelName(name) {
  const tabState = state.tabs[state.activeType];
  if (!tabState) {
    return;
  }
  tabState.modelName = (name || defaultModelName()).trim();
}

async function syncProjectActiveModel(type, modelId) {
  if (!state.project || !modelId) {
    return;
  }
  if (String(state.project.activeModelIds?.[type] || "") === String(modelId)) {
    return;
  }
  try {
    const activeModelIds = {
      ...(state.project.activeModelIds || {}),
      [type]: String(modelId),
    };
    const updated = await api(`/projects/${state.project.id}`, {
      method: "PUT",
      body: JSON.stringify({
        name: state.project.name,
        description: state.project.description || "",
        activeModelIds,
      }),
    });
    state.project = updated;
  } catch (_error) {
    console.warn("Failed to sync active model.");
  }
}

export async function reloadModels() {
  try {
    const projectParam = state.project ? `?projectId=${state.project.id}` : "";
    const records = await api(`/${MODEL_TYPES[state.activeType].apiType}${projectParam}`);
    state.modelsCache[state.activeType] = records;
  } catch (error) {
    setError(error, { prefix: "Load failed." });
  }
}

async function updateExistingModelWithPayload(payload) {
  const body = await stringifyJsonAsync(payload);
  const updated = await api(`/${MODEL_TYPES[state.activeType].apiType}/${state.modelId}`, {
    method: "PUT",
    body,
  });
  state.modelRevision = Number(updated?.revision) || state.modelRevision;
  return updated;
}

function waitForSaveIndicatorPaint() {
  if (typeof window === "undefined" || typeof window.requestAnimationFrame !== "function") {
    return Promise.resolve();
  }
  return new Promise((resolve) => {
    window.requestAnimationFrame(() => window.setTimeout(resolve, 0));
  });
}

async function waitForCanvasPaint(frames = 2) {
  if (typeof window === "undefined" || typeof window.requestAnimationFrame !== "function") {
    return;
  }
  for (let index = 0; index < frames; index += 1) {
    await new Promise((resolve) => window.requestAnimationFrame(resolve));
  }
}

async function buildSavePayloadFromPrepared(selectedName, nextModel) {
  requireActiveProject("save a model");
  return {
    name: selectedName,
    model: nextModel,
    projectId: state.project.id,
    expectedRevision: state.modelRevision || 1,
  };
}

// ── Save / Load model ─────────────────────────────────────────────────────────

export async function saveCurrentModel({
  rethrow = false,
  quiet = false,
  skipBeginSave = false,
  force = false,
} = {}) {
  if (saveInFlight) {
    return saveInFlight;
  }
  if (!force && !quiet && state.modelId && !hasUnsavedModelChanges()) {
    if (!quiet) {
      setStatus("Model is up to date.");
    }
    return;
  }
  saveInFlight = performSaveCurrentModel({ rethrow, quiet, skipBeginSave }).finally(() => {
    saveInFlight = null;
  });
  return saveInFlight;
}

async function performSaveCurrentModel({
  rethrow = false,
  quiet = false,
  skipBeginSave = false,
} = {}) {
  if (!isModelingType()) {
    if (!quiet) {
      setStatus(`Switch to ${modelingLevelListLabel()} to save a model.`);
    }
    return;
  }
  const selectedName = getActiveModelName();
  setActiveModelName(selectedName);
  const typeKey = state.activeType;

  const doBusy = !quiet;
  try {
    if (!quiet && !skipBeginSave) {
      beginModelSave();
    }
    if (doBusy) {
      setBusy("Saving…");
    }
    await yieldToMain();
    let prepared = await prepareModelForSave();
    const positionOnlySave = prepared.incremental === "positions";
    if (doBusy && !positionOnlySave) {
      await waitForSaveIndicatorPaint();
    }
    if (state.modelId) {
      let updated = await flushCurrentModelPatch({
        name: selectedName,
        rethrow: true,
        prepared,
      });
      if (!updated) {
        if (!prepared.nextModel) {
          prepared = await prepareModelForSave({ forceFull: true });
        }
        const payload = await buildSavePayloadFromPrepared(selectedName, prepared.nextModel);
        await yieldToMain();
        updated = await updateExistingModelWithPayload(payload);
        adoptSavedBaseModel(prepared.nextModel);
      }
      if (updated && typeof updated === "object") {
        state.modelRevision = Number(updated.revision) || state.modelRevision;
      }
      setActiveModelName(updated?.name || selectedName);
    } else {
      const payload = await buildSavePayloadFromPrepared(selectedName, prepared.nextModel);
      await yieldToMain();
      const body = await stringifyJsonAsync(payload);
      const created = await api(`/${MODEL_TYPES[state.activeType].apiType}`, {
        method: "POST",
        body,
      });
      state.modelId = created.id;
      state.modelRevision = Number(created.revision) || 1;
      await yieldToMain();
      adoptSavedBaseModel(payload.model);
      setActiveModelName(created.name || payload.name);
    }
    updateActiveTabAfterSave();
    completeModelSave();
    if (!quiet) {
      setStatus("Model saved");
      schedulePostSaveHousekeeping(typeKey, state.modelId, {
        id: state.modelId,
        projectId: state.project?.id,
        level: typeKey,
        name: getActiveModelName(),
        revision: state.modelRevision,
        updatedAt: new Date().toISOString(),
      });
    }
  } catch (error) {
    if (isMethodologyValidationError(error)) {
      applyValidationIssues(error.issues, { openOnFirst: !quiet });
    }
    if (!quiet) {
      failModelSave(formatUserError(error, { prefix: "Save failed." }));
      setError(error, { prefix: "Save failed." });
    } else {
      markModelDirty();
    }
    if (rethrow) {
      throw error;
    }
  }
}

export async function loadModelById(
  typeKey,
  id,
  {
    showManualGuidance = false,
    autoLayout = true,
    deferRender = false,
    includeViews = true,
    skipFragments = false,
    skipClientLayout = false,
    deferTabSnapshot = false,
  } = {},
) {
  if (state.activeType !== typeKey) {
    await switchTab(typeKey);
  }
  const record = await api(
    `/${MODEL_TYPES[typeKey].apiType}/${id}${includeViews === false ? "?includeViews=false" : ""}`,
  );
  await yieldToMain();
  state.modelId = record.id;
  state.modelRevision = Number(record.revision) || 1;
  state.baseModel = record.modelJson;
  await installGraphAndViewsAsync(
    typeKey,
    record.modelJson,
    record.name || defaultModelName(typeKey),
    {
      skipFragments,
      skipClientLayout,
    },
  );
  await yieldToMain();
  state.diagram = materializeActiveView();
  if (state.tabs[typeKey]) {
    state.tabs[typeKey].modelId = record.id;
    state.tabs[typeKey].modelRevision = state.modelRevision;
    state.tabs[typeKey].baseModel = state.baseModel;
    state.tabs[typeKey].diagram = state.diagram;
    state.tabs[typeKey].modelName = record.name || defaultModelName(typeKey);
    state.tabs[typeKey].dirty = false;
    if (!deferTabSnapshot) {
      saveCurrentTabGraphState(typeKey);
    }
  }
  clearDiagramUndoHistory(typeKey);
  clearValidationIssues();
  setActiveModelName(record.name || defaultModelName(typeKey));
  if (!deferRender) {
    renderPalette();
    await renderDiagramAsync();
    renderViewWorkbench();
    const { onGuidedModelingContextChanged } = await import("./guided-modeling.js");
    onGuidedModelingContextChanged();
    await centerCurrentDiagram();
    resetModelSaveState();
    if (autoLayout && !activeView()?.autoLayoutApplied && state.diagram.nodes.length) {
      await autoLayoutCurrentDiagram({
        progress: true,
        status: false,
        force: false,
      });
    }
    if (showManualGuidance) {
      void scheduleIdleTask(() => applyManualGuidanceFromLoadedModel());
    }
    return;
  }
  resetModelSaveState();
  if (showManualGuidance) {
    void scheduleIdleTask(() => applyManualGuidanceFromLoadedModel());
  }
}

async function loadModelRecord(
  typeKey,
  record,
  {
    showManualGuidance = false,
    autoLayout = true,
    deferRender = false,
    skipFragments = false,
    skipClientLayout = false,
    deferTabSnapshot = false,
  } = {},
) {
  if (!record?.id || !record?.modelJson) {
    await loadModelById(typeKey, record?.id, {
      showManualGuidance,
      autoLayout,
      deferRender,
      skipFragments,
      skipClientLayout,
      deferTabSnapshot,
    });
    return;
  }
  if (state.activeType !== typeKey) {
    await switchTab(typeKey);
  }
  state.modelId = record.id;
  state.modelRevision = Number(record.revision) || 1;
  state.baseModel = record.modelJson;
  await installGraphAndViewsAsync(
    typeKey,
    record.modelJson,
    record.name || defaultModelName(typeKey),
    {
      skipFragments,
      skipClientLayout,
    },
  );
  await yieldToMain();
  state.diagram = materializeActiveView();
  if (state.tabs[typeKey]) {
    state.tabs[typeKey].modelId = record.id;
    state.tabs[typeKey].modelRevision = state.modelRevision;
    state.tabs[typeKey].baseModel = state.baseModel;
    state.tabs[typeKey].diagram = state.diagram;
    state.tabs[typeKey].modelName = record.name || defaultModelName(typeKey);
    state.tabs[typeKey].dirty = false;
    if (!deferTabSnapshot) {
      saveCurrentTabGraphState(typeKey);
    }
  }
  rememberModelSummary(typeKey, record);
  clearDiagramUndoHistory(typeKey);
  clearValidationIssues();
  setActiveModelName(record.name || defaultModelName(typeKey));
  if (!deferRender) {
    renderPalette();
    await renderDiagramAsync();
    renderViewWorkbench();
    await centerCurrentDiagram();
    resetModelSaveState();
    if (autoLayout && !activeView()?.autoLayoutApplied && state.diagram.nodes.length) {
      await autoLayoutCurrentDiagram({
        progress: true,
        status: false,
        force: false,
      });
    }
    if (showManualGuidance) {
      void scheduleIdleTask(() => applyManualGuidanceFromLoadedModel());
    }
    return;
  }
  resetModelSaveState();
  if (showManualGuidance) {
    void scheduleIdleTask(() => applyManualGuidanceFromLoadedModel());
  }
}

async function autoLayoutGeneratedModel(typeLabel) {
  async function finalizePresentation({ center = true, skipRender = false } = {}) {
    if (!skipRender) {
      await yieldToMain();
      await renderDiagramAsync();
    }
    renderPalette();
    renderViewWorkbench();
    const { onGuidedModelingContextChanged } = await import("./guided-modeling.js");
    onGuidedModelingContextChanged();
    if (center) {
      await centerCurrentDiagram();
    }
  }
  const view = activeView();
  if (!view?.id) {
    await finalizePresentation();
    return;
  }
  if (!state.diagram.nodes.length) {
    await ensureViewContentAsync(view, state.activeType, { skipClientLayout: true });
    state.diagram = materializeActiveView();
  }
  if (!state.diagram.nodes.length) {
    await finalizePresentation();
    return;
  }
  if (view.autoLayoutApplied) {
    await finalizePresentation();
    return;
  }
  setGenerationProgressPhase(`Auto-layouting the generated ${typeLabel} model…`, 86);
  await waitForCanvasPaint(1);
  await autoLayoutCurrentDiagram({
    progress: false,
    status: false,
    busy: false,
    rethrow: true,
    force: false,
    skipClientLayout: true,
  });
  setGenerationProgressPhase(`Rendering the arranged ${typeLabel} model…`, 96);
  await waitForCanvasPaint(1);
  await finalizePresentation({ center: false, skipRender: true });
}

// ── Transformation / generation ───────────────────────────────────────────────

async function runTransformation(path, sourceModelId) {
  const result = await api(`/transformations/${path}`, {
    method: "POST",
    body: JSON.stringify({
      sourceModelId,
      expectedRevision: state.modelRevision || 1,
    }),
  });
  const status = String(result?.status || "").toUpperCase();
  if (status === "SUCCEEDED" || result?.success === true) {
    return result;
  }
  if (result?.id) {
    return waitForTransformationJob(result.id);
  }
  return result;
}

function storedModelHasView(viewId) {
  const normalizedId = String(viewId || "").trim();
  return (
    !normalizedId ||
    (Array.isArray(state.baseModel?.views) &&
      state.baseModel.views.some((view) => String(view?.id || "") === normalizedId))
  );
}

async function ensureStoredModelForBackendOperation(operationLabel, { requiredViewId = "" } = {}) {
  const viewId = String(requiredViewId || "").trim();
  if (state.modelId && viewId && !storedModelHasView(viewId)) {
    const view = state.views.byId.get(viewId);
    if (view) {
      syncActiveViewFromVisibleGraph();
      await ensureViewContentAsync(view, state.activeType, { skipClientLayout: true });
      await persistViewForBackendLayout(view);
    }
  }
  const hasUnsavedChanges = hasUnsavedModelChanges();
  if (state.modelId && !hasUnsavedChanges && (!viewId || storedModelHasView(viewId))) {
    return true;
  }
  if (state.modelId && !hasUnsavedChanges && viewId) {
    await saveCurrentModel({ quiet: true, rethrow: true });
    if (storedModelHasView(viewId)) {
      return true;
    }
    throw new Error(`Unable to persist active view: ${viewId}`);
  }
  const level = String(state.activeType || "model").toUpperCase();
  const confirmed = await confirmAction({
    title: "Save Model First",
    message: `${operationLabel} uses the stored backend ${level} model. Save the current model first, then continue?`,
    confirmLabel: "Save and Continue",
  });
  if (!confirmed) {
    setStatus(`${operationLabel} canceled`);
    return false;
  }
  await saveCurrentModel({ quiet: true, rethrow: true });
  return Boolean(state.modelId);
}

async function waitForTransformationJob(jobId) {
  if (!jobId) {
    throw new Error("Transformation did not return a job id.");
  }
  const startedAt = Date.now();
  while (Date.now() - startedAt < 10 * 60 * 1000) {
    const job = await api(`/transformations/jobs/${jobId}`);
    const status = String(job?.status || "").toUpperCase();
    if (status === "SUCCEEDED") {
      return job;
    }
    if (status === "FAILED" || status === "CANCELLED") {
      const diagnostics = Array.isArray(job?.diagnostics) ? job.diagnostics.join("; ") : "";
      throw new Error(diagnostics || `Transformation job ${status.toLowerCase()}.`);
    }
    await new Promise((resolve) => setTimeout(resolve, 1000));
  }
  throw new Error("Transformation job timed out.");
}

function isTransformationApiError(error, path) {
  const expected = `/transformations/${path}`;
  return typeof error?.path === "string" && error.path === expected;
}

function rememberModelSummary(typeKey, record) {
  if (!record?.id) {
    return;
  }
  state.modelsCache[typeKey] ??= [];
  const summary = {
    id: record.id,
    projectId: record.projectId,
    level: record.level,
    name: record.name,
    createdAt: record.createdAt,
    updatedAt: record.updatedAt,
    revision: Number(record.revision) || 1,
  };
  state.modelsCache[typeKey] = [
    summary,
    ...state.modelsCache[typeKey].filter((item) => item.id !== record.id),
  ];
}

function nodeRect(node, nodeSize) {
  return {
    minX: node.x,
    minY: node.y,
    maxX: node.x + nodeSize.width,
    maxY: node.y + nodeSize.height,
  };
}

function groupRect(nodes, nodeSize, { padX = 0, padY = 0 } = {}) {
  return {
    minX: Math.min(...nodes.map((node) => node.x)) - padX,
    minY: Math.min(...nodes.map((node) => node.y)) - padY,
    maxX: Math.max(...nodes.map((node) => node.x + nodeSize.width)) + padX,
    maxY: Math.max(...nodes.map((node) => node.y + nodeSize.height)) + padY,
  };
}

function rectsOverlap(a, b) {
  return a.minX < b.maxX && a.maxX > b.minX && a.minY < b.maxY && a.maxY > b.minY;
}

function moveLayoutNodes(nodes, dx, dy, movedNodeIds) {
  nodes.forEach((node) => {
    node.x = Math.round(node.x + dx);
    node.y = Math.round(node.y + dy);
    node.meta = node.meta && typeof node.meta === "object" ? node.meta : {};
    node.meta.x = node.x;
    node.meta.y = node.y;
    movedNodeIds.add(node.id);
  });
}

function spreadEdgeAnchorsForNode(node, edges, nodeSize) {
  const labelText = node.label || node.id || "";
  const measured = measureIconNodeSize(labelText, { width: nodeSize.width, low: false });
  const size = { width: nodeSize.width, height: measured.height };
  const anchor = iconAnchorBoundsFromNodeRect(0, 0, size.width, size.height, false, labelText);
  const byEndpoint = [
    {
      items: edges.filter((edge) => edge.sourceId === node.id && edge.sourceAnchor),
      anchorKey: "sourceAnchor",
      pinIndex: 0,
    },
    {
      items: edges.filter((edge) => edge.targetId === node.id && edge.targetAnchor),
      anchorKey: "targetAnchor",
      pinIndex: -1,
    },
  ];
  byEndpoint.forEach(({ items, anchorKey, pinIndex }) => {
    const bySide = new Map();
    items.forEach((edge) => {
      const side = edge[anchorKey]?.side;
      if (!side) {
        return;
      }
      if (!bySide.has(side)) {
        bySide.set(side, []);
      }
      bySide.get(side).push(edge);
    });
    bySide.forEach((sideEdges) => {
      if (sideEdges.length < 2) {
        return;
      }
      sideEdges.sort((left, right) => {
        const leftOther = left.sourceId === node.id ? left.targetId : left.sourceId;
        const rightOther = right.sourceId === node.id ? right.targetId : right.sourceId;
        return (
          String(leftOther || "").localeCompare(String(rightOther || "")) ||
          String(left.id || "").localeCompare(String(right.id || ""))
        );
      });
      const step = Math.max(8, Math.min(18, anchor.height / Math.max(1, sideEdges.length - 1)));
      const start = anchor.top + Math.max(2, (anchor.height - step * (sideEdges.length - 1)) / 2);
      sideEdges.forEach((edge, index) => {
        const offsetY = Math.round(start + index * step);
        edge[anchorKey].offsetY = offsetY;
        if (!Array.isArray(edge.pinPoints) || !edge.pinPoints.length) {
          return;
        }
        const targetIndex = pinIndex < 0 ? edge.pinPoints.length - 1 : pinIndex;
        const pin = edge.pinPoints[targetIndex];
        if (pin && typeof pin === "object") {
          pin.y = Math.round(node.y + offsetY);
        }
      });
    });
  });
}

function _spreadEdgeAnchors(nodes, edges, nodeSize) {
  const visibleEdges = edges.filter((edge) => !edge.bundle);
  const edgesByNodeId = new Map(nodes.map((node) => [node.id, []]));
  visibleEdges.forEach((edge) => {
    edgesByNodeId.get(edge.sourceId)?.push(edge);
    if (edge.targetId !== edge.sourceId) {
      edgesByNodeId.get(edge.targetId)?.push(edge);
    }
  });
  nodes.forEach((node) =>
    spreadEdgeAnchorsForNode(node, edgesByNodeId.get(node.id) || [], nodeSize),
  );
}

function routePointForAnchor(node, anchor, nodeSize) {
  if (!node || !anchor) {
    return null;
  }
  const side = anchor.side === "left" ? "left" : anchor.side === "right" ? "right" : null;
  const offsetY = Number(anchor.offsetY);
  if (!side) {
    return null;
  }
  const labelText = node.label || node.id || "";
  const measured = measureIconNodeSize(labelText, { width: nodeSize.width, low: false });
  const size = { width: nodeSize.width, height: measured.height };
  return routePointOnIconAnchor(
    node.x,
    node.y,
    size.width,
    size.height,
    side,
    Number.isFinite(offsetY) ? offsetY : undefined,
    false,
    labelText,
  );
}

function samePoint(left, right) {
  return (
    Math.round(Number(left?.x)) === Math.round(Number(right?.x)) &&
    Math.round(Number(left?.y)) === Math.round(Number(right?.y))
  );
}

function pushRoutePoint(points, point) {
  if (!point || !Number.isFinite(Number(point.x)) || !Number.isFinite(Number(point.y))) {
    return;
  }
  const normalized = { x: Math.round(point.x), y: Math.round(point.y) };
  if (!points.length || !samePoint(points[points.length - 1], normalized)) {
    points.push(normalized);
  }
}

function orthogonalizeEdgePinPoints(edge, sourceNode, targetNode, nodeSize) {
  const start = routePointForAnchor(sourceNode, edge.sourceAnchor, nodeSize);
  const end = routePointForAnchor(targetNode, edge.targetAnchor, nodeSize);
  if (!start || !end) {
    return;
  }
  const rawPins = (Array.isArray(edge.pinPoints) ? edge.pinPoints : [])
    .map((point) => ({
      x: Math.round(Number(point?.x)),
      y: Math.round(Number(point?.y)),
    }))
    .filter((point) => Number.isFinite(point.x) && Number.isFinite(point.y));
  const sourceSide = edge.sourceAnchor?.side;
  const path = [start, ...rawPins, end];
  const orthogonal = [start];
  for (let index = 1; index < path.length; index += 1) {
    const previous = orthogonal[orthogonal.length - 1];
    const next = path[index];
    if (samePoint(previous, next)) {
      continue;
    }
    const diagonal = previous.x !== next.x && previous.y !== next.y;
    if (diagonal) {
      const horizontalFirst =
        index === 1
          ? sourceSide !== "left" && sourceSide !== "right"
            ? Math.abs(next.x - previous.x) >= Math.abs(next.y - previous.y)
            : true
          : Math.abs(next.x - previous.x) >= Math.abs(next.y - previous.y);
      pushRoutePoint(
        orthogonal,
        horizontalFirst ? { x: next.x, y: previous.y } : { x: previous.x, y: next.y },
      );
    }
    pushRoutePoint(orthogonal, next);
  }
  edge.pinPoints = orthogonal.slice(1, -1);
}

function _orthogonalizeEdgeRoutes(nodes, edges, nodeSize) {
  const nodesById = new Map(nodes.map((node) => [node.id, node]));
  edges
    .filter((edge) => !edge.bundle)
    .forEach((edge) =>
      orthogonalizeEdgePinPoints(
        edge,
        nodesById.get(edge.sourceId),
        nodesById.get(edge.targetId),
        nodeSize,
      ),
    );
}

function _fallbackEdgePresentation(sourceNode, targetNode, nodeSize, laneOffset = 0) {
  if (!sourceNode || !targetNode) {
    return { pinPoints: [], sourceAnchor: null, targetAnchor: null };
  }
  const sourceCenterX = sourceNode.x + nodeSize.width / 2;
  const targetCenterX = targetNode.x + nodeSize.width / 2;
  const sourceSide = targetCenterX >= sourceCenterX ? "right" : "left";
  const targetSide = sourceSide === "right" ? "left" : "right";
  const sourcePoint = {
    x: Math.round(sourceNode.x + (sourceSide === "right" ? nodeSize.width : 0)),
    y: Math.round(sourceNode.y + nodeSize.height / 2),
  };
  const targetPoint = {
    x: Math.round(targetNode.x + (targetSide === "right" ? nodeSize.width : 0)),
    y: Math.round(targetNode.y + nodeSize.height / 2),
  };
  const midX = Math.round((sourcePoint.x + targetPoint.x) / 2 + laneOffset);
  return {
    pinPoints: [
      { x: midX, y: sourcePoint.y },
      { x: midX, y: targetPoint.y },
    ],
    sourceAnchor: { side: sourceSide, offsetY: Math.round(nodeSize.height / 2) },
    targetAnchor: { side: targetSide, offsetY: Math.round(nodeSize.height / 2) },
  };
}

function _fallbackLaneOffset(edge, laneIndex = 0) {
  if (laneIndex <= 0) {
    return 0;
  }
  const direction = laneIndex % 2 === 0 ? -1 : 1;
  const distance = Math.ceil(laneIndex / 2);
  let hash = 0;
  String(edge?.id || "")
    .split("")
    .forEach((char) => {
      hash = (hash * 31 + char.charCodeAt(0)) % 997;
    });
  return direction * (distance * 18 + (hash % 7));
}

export function updateGenerateButtonState() {
  if (!el.generateContextBtn) {
    return;
  }
  const transformation = transformationForLevel(state.activeType);
  const artifactAction = state.modelingConfig.config?.artifactAction || {};
  const buttonConfig =
    state.activeType === "artifact"
      ? {
          label: artifactAction.buttonLabel || "Download Project",
          title: artifactAction.buttonTitle || "Download the generated project",
        }
      : transformation
        ? {
            label: transformation.buttonLabel || transformation.label || "Generate",
            title: transformation.buttonTitle || transformation.title || "Run generation",
          }
        : null;
  const isVisible = Boolean(buttonConfig);
  el.generateContextBtn.classList.toggle("hidden", !isVisible);
  el.generateContextBtn.disabled = !isVisible;
  el.generateContextBtn.classList.toggle("topbar-download-btn", state.activeType === "artifact");
  if (!buttonConfig) {
    return;
  }
  const label = el.generateContextBtn.querySelector(".topbar-btn-label");
  if (label) {
    label.textContent = buttonConfig.label;
  } else {
    el.generateContextBtn.textContent = buttonConfig.label;
  }
  el.generateContextBtn.title = buttonConfig.title;
}

async function executeConfiguredTransformation(transformation) {
  const sourceLevel = transformation?.sourceLevel || state.activeType;
  const targetLevel = transformation?.targetLevel || "";
  const operation = transformation?.operation || "";
  if (!operation) {
    setStatus("No backend transformation operation is configured for this context.");
    return;
  }
  if (state.activeType !== sourceLevel || !state.modelId) {
    if (state.activeType !== sourceLevel) {
      setStatus(transformation.switchStatus || `Switch to ${sourceLevel.toUpperCase()} tab first`);
      return;
    }
  }
  try {
    if (
      !(await ensureStoredModelForBackendOperation(transformation.ensureStoredLabel || "Generate"))
    ) {
      return;
    }
    showGenerationProgress({
      title: transformation.progressTitle || "Generating",
      subtitle: transformation.progressSubtitle || "",
      label: transformation.startLabel || "Starting backend transformation...",
    });
    setBusy(transformation.busyLabel || "Generating...");
    setGenerationProgressPhase(transformation.runningLabel || "Running backend generation...", 68);
    const result = await runTransformation(operation, state.modelId);
    if (targetLevel === "artifact") {
      if (transformation.loadingStatus) {
        setStatus(transformation.loadingStatus);
      }
      setGenerationProgressPhase(
        transformation.preparingLabel || "Opening generated artifact...",
        94,
      );
      if (result.artifact) {
        await loadArtifactRecord(result.artifact, { collapseTree: true });
      } else {
        await loadArtifactById(result.resultArtifactId, { collapseTree: true });
      }
      await switchTab("artifact");
      await completeGenerationProgress(transformation.completeLabel || "Artifacts ready.");
      setStatus(transformation.successStatus || "Artifact ready");
      return;
    }
    setGenerationProgressPhase(transformation.preparingLabel || "Preparing generated model...", 76);
    await waitForCanvasPaint(1);
    if (result.model) {
      await loadModelRecord(targetLevel, result.model, {
        showManualGuidance: false,
        autoLayout: false,
        deferRender: true,
        skipFragments: true,
        skipClientLayout: true,
        deferTabSnapshot: true,
      });
    } else {
      await loadModelById(targetLevel, result.resultModelId, {
        showManualGuidance: false,
        autoLayout: false,
        deferRender: true,
        includeViews: false,
        skipFragments: true,
        skipClientLayout: true,
        deferTabSnapshot: true,
      });
    }
    await yieldToMain();
    await autoLayoutGeneratedModel(
      (
        state.modelingConfig.config?.levels?.[targetLevel]?.displayName || targetLevel
      ).toUpperCase(),
    );
    await completeGenerationProgress(transformation.completeLabel || "Model ready.");
    if (!state.validation.issues.length) {
      setStatus(transformation.successStatus || "Model generated and loaded");
    }
    void scheduleIdleTask(async () => {
      saveCurrentTabGraphState(targetLevel);
      applyManualGuidanceFromLoadedModel();
    });
  } catch (error) {
    if (isMethodologyValidationError(error)) {
      if (targetLevel && targetLevel !== "artifact" && isTransformationApiError(error, operation)) {
        await switchTab(targetLevel);
      }
      applyValidationIssues(error.issues, { openOnFirst: true });
      toggleValidationDrawer(true);
      await fitViewportToDiagram({ fit: true });
    } else {
      applyValidationIssues(
        [
          {
            severity: "ERROR",
            constraint: "GenerationError",
            issueClass: "SYSTEM_ERROR",
            message:
              transformation.errorMessage ||
              "Generation could not be completed. The technical details have been kept out of the issue board.",
            guidance:
              "Automatic generation was interrupted. Review highlighted items and continue with manual refinement.",
          },
        ],
        { openOnFirst: true },
      );
      toggleValidationDrawer(true);
      await fitViewportToDiagram({ fit: true });
    }
    setError(error, { prefix: "Generation failed." });
  } finally {
    hideGenerationProgress();
  }
}

export async function generateFirstConfiguredTransformation() {
  await executeConfiguredTransformation(
    Object.values(state.modelingConfig.config?.transformations || {})[0],
  );
}

export async function generateNextConfiguredTransformation() {
  await executeConfiguredTransformation(
    Object.values(state.modelingConfig.config?.transformations || {})[1],
  );
}

export async function generateArtifactConfiguredTransformation() {
  await executeConfiguredTransformation(
    Object.values(state.modelingConfig.config?.transformations || {}).find(
      (transformation) => transformation?.targetLevel === "artifact",
    ),
  );
}

export async function generateForCurrentContext() {
  const transformation = transformationForLevel(state.activeType);
  if (transformation) {
    await executeConfiguredTransformation(transformation);
    return;
  }
  setStatus(`Switch to ${modelingLevelListLabel()} tab first`);
}

// ── Tab switching ─────────────────────────────────────────────────────────────

export async function switchTab(type) {
  if (type !== state.activeType) {
    const { closeChatWindow } = await import("./chat.js?v=chat-stop-20260704a");
    closeChatWindow();
  }
  closeAttributePanel();
  el.modelTreePanel?.classList.add("hidden");
  el.workspace?.classList.remove("views-open");
  if (state.impactMode) {
    closeImpactPanel();
  }

  // Save current diagram tab state before switching
  if (state.activeType !== "artifact" && state.tabs[state.activeType]) {
    syncActiveViewFromVisibleGraph();
    state.tabs[state.activeType].modelId = state.modelId;
    state.tabs[state.activeType].modelRevision = state.modelRevision || 0;
    state.tabs[state.activeType].baseModel = state.baseModel;
    state.tabs[state.activeType].diagram = state.diagram;
    saveCurrentTabGraphState(state.activeType);
  }

  state.activeType = type;
  Array.from(el.modelTabs.querySelectorAll(".tab")).forEach((t) =>
    t.classList.toggle("active", t.dataset.type === type),
  );
  updateGenerateButtonState();

  const isArtifact = type === "artifact";
  const isReadonlyEditor = isArtifact;
  const topbar = document.querySelector(".topbar");
  el.workspace?.classList.toggle("artifact-mode", isArtifact);
  topbar?.classList.toggle("artifact-mode", isArtifact);
  if (isArtifact) {
    el.workspace?.classList.remove("palette-collapsed");
  } else {
    el.workspace?.classList.toggle("palette-collapsed", !!state.paletteCollapsed);
  }
  el.modelingPanel.classList.toggle("hidden", isReadonlyEditor);
  el.artifactPanel.classList.toggle("hidden", !isArtifact);
  el.canvasViewport.classList.toggle("hidden", isReadonlyEditor);
  el.artifactEditor.classList.toggle("hidden", !isArtifact);
  el.impactToggleBtn.disabled = isReadonlyEditor;
  el.validateModelBtn?.classList.toggle("hidden", isArtifact);
  if (el.validateModelBtn) {
    el.validateModelBtn.disabled = isArtifact;
  }

  if (isArtifact) {
    state.validation.panelOpen = false;
    el.validationFab?.classList.add("hidden");
    el.validationDrawer?.classList.add("hidden");
    el.modelWorkbenchPanel?.classList.add("hidden");
    renderViewWorkbench();
    updateModelSaveUi();
    setStatus("Artifact Explorer");
    await loadCurrentProjectArtifact({ collapseTree: true });
    return;
  }

  // Restore tab state
  const tabState = state.tabs[type];
  state.modelId = tabState.modelId;
  state.modelRevision = tabState.modelRevision || 0;
  state.baseModel = tabState.baseModel;
  state.diagram = tabState.diagram || emptyDiagram(type);
  restoreTabGraphState(type);
  materializeActiveView();
  clearValidationIssues({ keepPanelState: false });
  tabState.modelName = tabState.modelName || defaultModelName(type);
  state.modelSave.dirty = Boolean(tabState.dirty);
  state.modelSave.saving = false;
  state.modelSave.error = "";
  updateModelSaveUi();

  renderPalette();
  await renderDiagramAsync();
  renderViewWorkbench();
  const { onGuidedModelingContextChanged } = await import("./guided-modeling.js");
  onGuidedModelingContextChanged();
  await centerCurrentDiagram();
  setStatus(`Switched to ${type.toUpperCase()}`);
}

export async function validateCurrentModel() {
  if (state.activeType === "artifact") {
    setStatus(`Validation is available for ${modelingLevelListLabel()}.`);
    return;
  }
  try {
    if (!(await ensureStoredModelForBackendOperation("Validate Model"))) {
      return;
    }
    setBusy("Validating…");
    setValidationInProgress(true);
    const result = await api(
      `/${MODEL_TYPES[state.activeType].apiType}/${state.modelId}/validate`,
      { method: "POST" },
    );
    const backendIssues = Array.isArray(result?.issues) ? result.issues : [];
    if (backendIssues.length) {
      const mergedIssues = mergeIssuesWithManualGuidance(backendIssues);
      applyValidationIssues(mergedIssues, { openOnFirst: true });
      toggleValidationDrawer(true);
      setStatus(
        result?.valid === false
          ? "Validation completed with errors."
          : "Validation completed with issues.",
      );
      return;
    }
    const manualGuidance = manualGuidanceIssuesFromCurrentModel();
    if (manualGuidance.length) {
      applyValidationIssues(manualGuidance, { openOnFirst: true });
      toggleValidationDrawer(true);
      const requiredCount = manualGuidance.filter(
        (issue) => String(issue?.severity || "").toUpperCase() === "ERROR",
      ).length;
      const optionalCount = manualGuidance.length - requiredCount;
      setStatus(
        `Model is valid. Pending manual tasks: ${requiredCount} required, ${optionalCount} optional.`,
      );
    } else {
      clearValidationIssues();
      toggleValidationDrawer(true);
      setStatus("Validation passed. No issues found.");
    }
  } catch (error) {
    if (isMethodologyValidationError(error)) {
      const merged = mergeIssuesWithManualGuidance(error.issues);
      applyValidationIssues(merged, { openOnFirst: true });
      toggleValidationDrawer(true);
      setStatus("Validation completed with issues.");
      return;
    }
    setError(error, { prefix: "Validation failed." });
  } finally {
    setValidationInProgress(false);
  }
}

export async function autoLayoutCurrentDiagram(options = {}) {
  if (autoLayoutPromise) {
    if (options.status !== false) {
      setStatus("Auto layout is already running.");
    }
    return autoLayoutPromise;
  }
  const promise = runAutoLayoutCurrentDiagram(options);
  autoLayoutPromise = promise;
  try {
    return await promise;
  } finally {
    if (autoLayoutPromise === promise) {
      autoLayoutPromise = null;
    }
  }
}

async function runAutoLayoutCurrentDiagram({
  progress = true,
  status = true,
  busy = true,
  rethrow = false,
  force = true,
  strategy = "",
  skipClientLayout = true,
} = {}) {
  if (!isModelingType()) {
    if (status) {
      setStatus(`Auto layout is available for ${modelingLevelListLabel()}.`);
    }
    return;
  }
  if (!state.diagram.nodes.length) {
    if (status) {
      setStatus("Add elements to the diagram first.");
    }
    return;
  }

  const view = activeView();
  if (!view?.id) {
    if (status) {
      setStatus("No active view to arrange.");
    }
    return;
  }

  syncActiveViewFromVisibleGraph();
  await ensureViewContentAsync(view, state.activeType, { skipClientLayout });
  await yieldToMain();
  state.diagram = materializeActiveView();

  try {
    if (
      !(await ensureStoredModelForBackendOperation("Auto Layout", {
        requiredViewId: view.id,
      }))
    ) {
      return;
    }
    if (progress) {
      showGenerationProgress({
        kicker: "Auto Layout in Progress",
        title: "Arranging current view",
        subtitle: "The backend is arranging the persisted view.",
        label: "Loading persisted view…",
      });
    }
    if (busy) {
      setBusy("Auto layout…");
    }
    if (progress || busy) {
      await waitForCanvasPaint(1);
    }
    if (progress) {
      setGenerationProgressPhase("Analyzing diagram topology…", 32);
      setGenerationProgressPhase("Computing backend ELK layout…", 72);
      await waitForCanvasPaint(1);
    }
    const level = MODEL_TYPES[state.activeType].apiType;
    const selectedStrategy = String(
      strategy || view.layoutStrategy || modelingDefaultLayoutStrategy(),
    );
    const response = await api(
      `/${level}/${state.modelId}/views/${encodeURIComponent(
        view.id,
      )}/layout?force=${force}&strategy=${encodeURIComponent(selectedStrategy)}`,
      { method: "POST" },
    );
    await yieldToMain();
    if (!response?.view) {
      throw new Error("Backend layout did not return the persisted view.");
    }
    state.modelRevision = Number(response.revision) || state.modelRevision;
    state.views.byId.set(view.id, structuredClone(response.view));
    materializeActiveView();
    mergePersistedViewIntoBaseModel(response.view);
    if (state.tabs[state.activeType]) {
      state.tabs[state.activeType].modelRevision = state.modelRevision;
      state.tabs[state.activeType].baseModel = state.baseModel;
      state.tabs[state.activeType].diagram = state.diagram;
      saveCurrentTabGraphState(state.activeType);
    }
    window.varkaLayoutAudit = {
      modelId: state.modelId,
      viewId: view.id,
      expectedNodes: response.nodeCount,
      positionedNodes: response.nodeCount,
      expectedEdges: response.edgeCount,
      routedEdges: response.edgeCount,
      backend: true,
      layoutApplied: response.layoutApplied,
    };
    if (progress) {
      setGenerationProgressPhase("Rendering layout…", 88);
    }
    await yieldToMain();
    await renderDiagramAsync({ full: true });
    await waitForCanvasPaint(1);
    await fitViewportToDiagram({ fit: true });
    resetModelSaveState();
    if (progress) {
      setGenerationProgressPhase("Layout applied.", 100);
    }
    const warnings = Array.isArray(response.warnings) ? response.warnings : [];
    if (warnings.length) {
      if (status) {
        setStatus(`Auto layout applied with ${warnings.length} warning(s).`);
      }
      return;
    }
    if (status) {
      setStatus(
        response.layoutApplied
          ? "Auto layout applied and persisted."
          : "Persisted layout restored.",
      );
    }
  } catch (error) {
    if (rethrow) {
      throw error;
    }
    if (status) {
      setError(error, { prefix: "Auto layout failed." });
    }
  } finally {
    if (progress) {
      hideGenerationProgress();
    }
  }
}

export async function exportActiveModel(format = "json") {
  if (!isModelingType()) {
    setStatus(`Switch to ${modelingLevelListLabel()} to export model ${format.toUpperCase()}.`);
    return;
  }
  const normalizedFormat = String(format || "json").toLowerCase();
  if (state.modelId) {
    const patched = await flushCurrentModelPatch({
      name: getActiveModelName(),
      rethrow: true,
    });
    if (!patched) {
      await saveCurrentModel({ quiet: true, rethrow: true });
    } else {
      completeModelSave();
    }
  }
  const exportPath = state.modelId
    ? `/${MODEL_TYPES[state.activeType].apiType}/${state.modelId}/export`
    : `/${MODEL_TYPES[state.activeType].apiType}/export`;
  const response = await fetch(apiUrl(exportPath), {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      ...apiAuthHeaders(),
    },
    body: JSON.stringify({
      name: getActiveModelName(),
      model: state.modelId
        ? null
        : serializeModel({ syncView: true, reconcileRelationships: true }),
      format: normalizedFormat,
    }),
  });
  if (!response.ok) {
    let message = `Export failed (${response.status})`;
    try {
      const contentType = response.headers.get("content-type") || "";
      if (contentType.includes("application/json")) {
        const body = await response.json();
        message = body.message || message;
      } else {
        message = (await response.text()).trim() || message;
      }
    } catch {
      // keep fallback message
    }
    throw new Error(message);
  }
  const fallbackFilename = `${
    state.project?.name || "project"
  }-${state.activeType}.${normalizedFormat}`;
  await downloadBlobFromResponse(response, fallbackFilename);
  setStatus(`Exported ${state.activeType.toUpperCase()} model ${normalizedFormat.toUpperCase()}`);
}

export async function importActiveModel(file, format = "json", typeKey = state.activeType) {
  if (!isModelingType(typeKey)) {
    setStatus(`Switch to ${modelingLevelListLabel()} to import model ${format.toUpperCase()}.`);
    return;
  }
  if (!file) {
    return;
  }
  try {
    requireActiveProject("import a model");
  } catch (error) {
    setError(error, { prefix: "Import unavailable." });
    return;
  }
  if (state.activeType !== typeKey) {
    await switchTab(typeKey);
  }
  const confirmed = await confirmAction({
    title: "Replace Current Model?",
    message: "This model will replace the current model on the canvas. Are you sure?",
    confirmLabel: "Replace",
    danger: false,
  });
  if (!confirmed) {
    return;
  }

  const normalizedFormat = String(format || "json").toLowerCase();
  const shouldAutoLayoutImportedModel = normalizedFormat === "xmi";
  const formData = new FormData();
  formData.append("file", file);
  try {
    showGenerationProgress({
      kicker: "Import in Progress",
      title: "Importing model",
      subtitle: "Loading the file and preparing the canvas.",
      label: "Uploading model file…",
    });
    setBusy("Importing model…");
    const response = await fetch(
      apiUrl(
        `/${MODEL_TYPES[state.activeType].apiType}/import?format=${encodeURIComponent(
          normalizedFormat,
        )}&projectId=${encodeURIComponent(state.project?.id || "")}`,
      ),
      {
        method: "POST",
        headers: apiAuthHeaders(),
        body: formData,
      },
    );
    setGenerationProgressPhase("Reading imported model…", 34);
    const contentType = response.headers.get("content-type") || "";
    const body = contentType.includes("application/json") ? await response.json() : null;
    if (!response.ok) {
      const issues = Array.isArray(body?.issues) ? body.issues : [];
      if (issues.length) {
        applyValidationIssues(issues, { openOnFirst: true });
        toggleValidationDrawer(true);
      }
      throw new Error(body?.message || `Import failed (${response.status})`);
    }

    setGenerationProgressPhase("Materializing imported graph…", 52);
    pushModelReplacementSnapshot();
    clearDiagramUndoHistory(state.activeType);
    state.baseModel = structuredClone(body.modelJson);
    await installGraphAndViewsAsync(
      state.activeType,
      body.modelJson,
      body.name || defaultModelName(),
    );
    state.diagram = materializeActiveView();
    if (state.tabs[state.activeType]) {
      state.tabs[state.activeType].baseModel = structuredClone(state.baseModel);
      state.tabs[state.activeType].diagram = structuredClone(state.diagram);
      state.tabs[state.activeType].modelName = String(body.name || defaultModelName()).trim();
      state.tabs[state.activeType].dirty = true;
      saveCurrentTabGraphState(state.activeType);
    }
    setActiveModelName(body.name || defaultModelName());

    const backendIssues = Array.isArray(body?.issues) ? body.issues : [];
    const hasErrorIssue = backendIssues.some(
      (issue) => String(issue?.severity || "").toUpperCase() === "ERROR",
    );
    if (!hasErrorIssue) {
      setGenerationProgressPhase("Saving imported model…", 68);
      await saveCurrentModel({ rethrow: true, quiet: true });
    } else {
      resetModelSaveState({ dirty: true });
    }

    setGenerationProgressPhase("Preparing imported model…", 72);
    let importLayoutWarning = "";
    if (shouldAutoLayoutImportedModel && state.diagram.nodes.length) {
      try {
        setGenerationProgressPhase("Auto-layouting imported model…", 80);
        await autoLayoutCurrentDiagram({
          progress: false,
          status: false,
          busy: false,
          rethrow: true,
          force: true,
        });
      } catch (layoutError) {
        importLayoutWarning = layoutError.message || "Auto layout failed for the imported model.";
        await renderDiagramAsync();
        renderViewWorkbench();
        await centerCurrentDiagram();
      }
    } else {
      await renderDiagramAsync();
      renderViewWorkbench();
      await centerCurrentDiagram();
    }
    if (state.tabs[state.activeType]) {
      state.tabs[state.activeType].diagram = structuredClone(state.diagram);
      state.tabs[state.activeType].dirty = hasUnsavedModelChanges();
      saveCurrentTabGraphState(state.activeType);
    }

    if (!hasErrorIssue && hasUnsavedModelChanges()) {
      setGenerationProgressPhase("Saving imported model…", 92);
      await saveCurrentModel({ rethrow: true, quiet: true });
    }
    await completeGenerationProgress("Imported model ready.");
    if (backendIssues.length) {
      const mergedIssues = mergeIssuesWithManualGuidance(backendIssues);
      applyValidationIssues(mergedIssues, { openOnFirst: true });
      toggleValidationDrawer(true);
      const issueStatus = `Imported ${state.activeType.toUpperCase()} model ${normalizedFormat.toUpperCase()} with ${backendIssues.length} issue(s).`;
      setStatus(
        importLayoutWarning
          ? `${issueStatus} Auto layout failed: ${importLayoutWarning}`
          : issueStatus,
      );
      return;
    }
    clearValidationIssues({ keepPanelState: false });
    const successStatus = `Imported ${state.activeType.toUpperCase()} model ${normalizedFormat.toUpperCase()}`;
    setStatus(
      importLayoutWarning
        ? `${successStatus}. Auto layout failed: ${importLayoutWarning}`
        : successStatus,
    );
  } finally {
    hideGenerationProgress();
  }
}

export async function undoLastModelReplacement() {
  if (!isModelingType()) {
    setStatus(`Switch to ${modelingLevelListLabel()} to undo model replacement.`);
    return;
  }
  const snapshot = state.undo.modelReplacements.pop();
  if (!snapshot) {
    setStatus("Nothing to undo.");
    return;
  }
  closeAttributePanel();
  await applyModelReplacementSnapshot(snapshot, {
    statusMessage: "Restored previous model.",
    persist: true,
  });
}

export async function undoLastEdit() {
  if (!isModelingType()) {
    setStatus(`Switch to ${modelingLevelListLabel()} to undo.`);
    return;
  }
  if (hasDiagramUndoHistory()) {
    const snapshot = popDiagramUndoSnapshot();
    if (!snapshot || !applyDiagramUndoSnapshot(snapshot)) {
      setStatus("Nothing to undo.");
      return;
    }
    closeAttributePanel();
    materializeActiveView();
    setActiveModelName(snapshot.modelName || defaultModelName(snapshot.typeKey));
    renderDiagram();
    renderViewWorkbench();
    await saveCurrentModel({ quiet: true, rethrow: true });
    setStatus("Undid last canvas edit.");
    return;
  }
  await undoLastModelReplacement();
}

bindValidationCenterUi();

function issueTargetCandidates(id) {
  const candidates = [];
  let current = String(id || "").trim();
  const seen = new Set();
  while (current && !seen.has(current)) {
    candidates.push(current);
    seen.add(current);
    current =
      state.graph?.parentByChild instanceof Map
        ? String(state.graph.parentByChild.get(current) || "").trim()
        : "";
  }
  return candidates;
}

function viewContainingIssueTarget(ids) {
  const candidates = Array.isArray(ids) ? ids : [ids];
  if (!candidates.length || !(state.views?.byId instanceof Map)) {
    return null;
  }
  for (const view of state.views.byId.values()) {
    const hasNode =
      Array.isArray(view?.nodes) &&
      view.nodes.some((node) => candidates.includes(String(node?.elementId || node?.id || "")));
    if (hasNode) {
      return view;
    }
    const viewRelationships = Array.isArray(view?.edges) ? view.edges : view?.relationships;
    const hasRelationship =
      Array.isArray(viewRelationships) &&
      viewRelationships.some((relationship) =>
        candidates.includes(String(relationship?.relationshipId || relationship?.id || "")),
      );
    if (hasRelationship) {
      return view;
    }
  }
  return null;
}

function activateViewForIssueTarget(ids) {
  const view = viewContainingIssueTarget(ids);
  if (!view || view.id === state.views.activeViewId) {
    return false;
  }
  if (!setActiveViewId(view.id)) {
    return false;
  }
  state.diagram = materializeActiveView();
  renderDiagram();
  renderViewWorkbench();
  return true;
}

function locateIssueTarget(detail) {
  const ids = Array.isArray(detail.targetIds) ? detail.targetIds : [];
  const id = String(detail.id || ids[0] || "").trim();
  const issueName = String(detail.elementName || "")
    .trim()
    .toLowerCase();
  const issueType = String(detail.elementType || "")
    .trim()
    .toLowerCase();
  const candidates = [...new Set(ids.flatMap((candidate) => issueTargetCandidates(candidate)))];
  if (id && !candidates.length) {
    candidates.push(...issueTargetCandidates(id));
  }
  activateViewForIssueTarget(candidates);

  for (const candidate of candidates) {
    if (state.diagram.nodes.some((node) => node.id === candidate)) {
      scrollToNodeAndHighlight(candidate);
      setStatus(
        candidate === id
          ? "Located issue element on canvas."
          : "Located containing element on canvas.",
      );
      return;
    }
  }
  for (const candidate of candidates) {
    if (state.diagram.connections.some((edge) => edge.id === candidate)) {
      scrollToConnectionAndHighlight(candidate);
      setStatus(
        candidate === id
          ? "Located issue connection on canvas."
          : "Located containing connection on canvas.",
      );
      return;
    }
  }

  const byName = state.diagram.nodes.find((node) => {
    const nodeLabel = String(node.label || "")
      .trim()
      .toLowerCase();
    const nodeType = String(node.type || "")
      .trim()
      .toLowerCase();
    return issueName && nodeLabel === issueName && (!issueType || nodeType === issueType);
  });
  if (byName) {
    scrollToNodeAndHighlight(byName.id);
    setStatus("Located related element on canvas.");
    return;
  }

  setStatus("This issue has no model element to locate. Review its details in the issue board.");
}

let locateIssueTargetBound = false;
if (!locateIssueTargetBound) {
  locateIssueTargetBound = true;
  window.addEventListener("varka:locate-issue-target", (event) => {
    locateIssueTarget(event?.detail || {});
  });
  window.addEventListener("varka:manual-task-toggle", async (event) => {
    const detail = event?.detail || {};
    const manualTaskId = String(detail.manualTaskId || "").trim();
    const resolved = Boolean(detail.resolved);
    if (!manualTaskId) {
      return;
    }
    try {
      await setManualTaskResolved(manualTaskId, resolved);
      setStatus(resolved ? "Manual task marked resolved." : "Manual task moved back to open.");
    } catch (error) {
      setError(error, { prefix: "Failed to update manual task." });
    }
  });
}
