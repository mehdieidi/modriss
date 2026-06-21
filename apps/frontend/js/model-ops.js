import { apiUrl, MODEL_TYPES } from "./config.js";
import { state } from "./state.js";
import { el } from "./dom.js";
import { api, apiAuthHeaders } from "./api.js";
import { flushCurrentModelPatch } from "./model-patch.js";
import { setBusy, setError, setStatus } from "./status.js";
import { emptyDiagram, genId } from "./utils.js";
import { serializeModel } from "./diagram.js";
import {
  activeView,
  installGraphAndViews,
  restoreTabGraphState,
  saveCurrentTabGraphState,
  setActiveViewId,
  syncActiveViewFromVisibleGraph,
} from "./graph-store.js";
import { materializeActiveView } from "./view-materializer.js";
import {
  centerViewportOnDiagram,
  contextNameFromNode,
  renderDiagram,
  renderPalette,
  resetCanvasView,
  scrollToConnectionAndHighlight,
  scrollToNodeAndHighlight,
} from "./canvas.js";
import { closeAttributePanel } from "./attr-panel.js";
import { closeImpactPanel } from "./impact.js";
import { refreshGithubConnection } from "./github.js";
import { loadArtifactById, loadArtifactRecord, loadCurrentProjectArtifact } from "./artifact.js";
import { confirmAction } from "./confirm-action.js";
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
  modelingLevelConfig,
  modelingLevelListLabel,
  transformationForLevel,
} from "./modeling-config-data.js";

let autoLayoutPromise = null;

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

function supportsBoundedContext(typeKey = state.activeType) {
  try {
    return Boolean(modelingLevelConfig(typeKey).boundedContext?.enabled);
  } catch {
    return false;
  }
}

function resetBoundedContextState() {
  state.boundedContextCreateMode = false;
  state.boundedContextDraftNodeIds = new Set();
  state.boundedContextDraftName = "";
  state.boundedContextViewMode = "normal";
  state.activeBoundedContextName = "";
  state.selectedBoundedContextName = null;
}

function centerCurrentDiagram({ fit = true } = {}) {
  if (Array.isArray(state.diagram?.nodes) && state.diagram.nodes.length) {
    centerViewportOnDiagram({ fit });
    return;
  }
  resetCanvasView();
}

function captureModelReplacementSnapshot(typeKey = state.activeType) {
  const tabState = state.tabs[typeKey];
  const serializedModel =
    typeKey === state.activeType ? serializeModel() : structuredClone(tabState?.baseModel || {});
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
  centerCurrentDiagram();
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
  const basicFilename = contentDisposition.match(/filename=\"?([^\";]+)\"?/i);
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
      message: taskTitle,
      guidance: String(
        task?.rationale ||
          task?.description ||
          "Review and complete this manual methodology step before promotion.",
      ),
    };
  });
}

function manualBacklogIdentity(task, index) {
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
  [
    ...(Array.isArray(modelJson?.manualBacklog) ? modelJson.manualBacklog : []),
    ...(Array.isArray(modelJson?.graph?.manualBacklog) ? modelJson.graph.manualBacklog : []),
  ].forEach((task, index) => {
    const key = manualBacklogIdentity(task, index);
    if (seen.has(key)) {
      return;
    }
    seen.add(key);
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
  model.manualBacklog.forEach((task, index) => {
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

function mergePersistedViewIntoBaseModel(view) {
  const base =
    state.baseModel && typeof state.baseModel === "object" ? structuredClone(state.baseModel) : {};
  const views = Array.isArray(base.views) ? base.views : [];
  const index = views.findIndex((candidate) => candidate?.id === view?.id);
  if (index >= 0) {
    views[index] = structuredClone(view);
  } else {
    views.push(structuredClone(view));
  }
  base.views = views;
  return base;
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

function applyManualGuidanceFromLoadedModel() {
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
  } catch (error) {
    console.warn("Failed to sync active model.");
  }
}

export async function reloadModels() {
  try {
    const projectParam = state.project ? `?projectId=${state.project.id}` : "";
    const records = await api(`/${MODEL_TYPES[state.activeType].apiType}${projectParam}`);
    state.modelsCache[state.activeType] = records;
  } catch (error) {
    setError(`Load failed: ${error.message}`);
  }
}

async function updateExistingModelWithPayload(payload) {
  const updated = await api(`/${MODEL_TYPES[state.activeType].apiType}/${state.modelId}`, {
    method: "PUT",
    body: JSON.stringify(payload),
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

function buildSavePayload(selectedName) {
  syncActiveViewFromVisibleGraph();
  return {
    name: selectedName,
    model: serializeModel(),
    projectId: state.project?.id || null,
    expectedRevision: state.modelRevision || 1,
  };
}

// ── Save / Load model ─────────────────────────────────────────────────────────

export async function saveCurrentModel({ rethrow = false, quiet = false } = {}) {
  if (!isModelingType()) {
    if (!quiet) {
      setStatus(`Switch to ${modelingLevelListLabel()} to save a model.`);
    }
    return;
  }
  const selectedName = getActiveModelName();
  setActiveModelName(selectedName);

  const doBusy = !quiet;
  try {
    if (!quiet) {
      beginModelSave();
    }
    if (doBusy) {
      setBusy("Saving…");
      await waitForSaveIndicatorPaint();
    }
    if (state.modelId) {
      let updated = await flushCurrentModelPatch({
        name: selectedName,
        rethrow: true,
      });
      let savedModel = null;
      if (!updated) {
        const payload = buildSavePayload(selectedName);
        savedModel = payload.model;
        updated = await updateExistingModelWithPayload(payload);
      }
      if (updated && typeof updated === "object") {
        state.modelRevision = Number(updated.revision) || state.modelRevision;
      }
      if (savedModel) {
        state.baseModel = stripServerTransportFields(structuredClone(savedModel));
      }
      setActiveModelName(updated?.name || selectedName);
      if (!quiet) {
        setStatus(`Model saved`);
      }
    } else {
      const payload = buildSavePayload(selectedName);
      const created = await api(`/${MODEL_TYPES[state.activeType].apiType}`, {
        method: "POST",
        body: JSON.stringify(payload),
      });
      state.modelId = created.id;
      state.modelRevision = Number(created.revision) || 1;
      state.baseModel = stripServerTransportFields(structuredClone(payload.model));
      setActiveModelName(created.name || payload.name);
      if (!quiet) {
        setStatus(`Model saved (${created.id.slice(0, 8)}…)`);
      }
    }
    if (state.tabs[state.activeType]) {
      state.tabs[state.activeType].modelId = state.modelId;
      state.tabs[state.activeType].modelRevision = state.modelRevision;
      state.tabs[state.activeType].baseModel = state.baseModel;
      state.tabs[state.activeType].diagram = state.diagram;
      saveCurrentTabGraphState(state.activeType);
    }
    await syncProjectActiveModel(state.activeType, state.modelId);
    completeModelSave();
    if (!quiet) {
      await reloadModels();
    }
  } catch (error) {
    if (isMethodologyValidationError(error)) {
      applyValidationIssues(error.issues, { openOnFirst: !quiet });
    }
    if (!quiet) {
      failModelSave(`Save failed: ${error.message}`);
      setError(`Save failed: ${error.message}`);
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
  { showManualGuidance = false, autoLayout = true } = {},
) {
  if (state.activeType !== typeKey) {
    await switchTab(typeKey);
  }
  const record = await api(`/${MODEL_TYPES[typeKey].apiType}/${id}`);
  state.modelId = record.id;
  state.modelRevision = Number(record.revision) || 1;
  state.baseModel = structuredClone(record.modelJson);
  installGraphAndViews(typeKey, record.modelJson, record.name || defaultModelName(typeKey));
  state.diagram = materializeActiveView();
  if (supportsBoundedContext(typeKey)) {
    resetBoundedContextState();
  }
  if (state.tabs[typeKey]) {
    state.tabs[typeKey].modelId = record.id;
    state.tabs[typeKey].modelRevision = state.modelRevision;
    state.tabs[typeKey].baseModel = state.baseModel;
    state.tabs[typeKey].diagram = state.diagram;
    state.tabs[typeKey].modelName = record.name || defaultModelName(typeKey);
    state.tabs[typeKey].dirty = false;
    saveCurrentTabGraphState(typeKey);
  }
  clearDiagramUndoHistory(typeKey);
  clearValidationIssues();
  setActiveModelName(record.name || defaultModelName(typeKey));
  renderPalette();
  renderDiagram();
  renderViewWorkbench();
  centerCurrentDiagram();
  resetModelSaveState();
  if (autoLayout && !activeView()?.autoLayoutApplied && state.diagram.nodes.length) {
    await autoLayoutCurrentDiagram({
      progress: true,
      status: false,
      force: false,
    });
  }
  if (showManualGuidance) {
    applyManualGuidanceFromLoadedModel();
  }
}

async function loadModelRecord(
  typeKey,
  record,
  { showManualGuidance = false, autoLayout = true } = {},
) {
  if (!record?.id || !record?.modelJson) {
    await loadModelById(typeKey, record?.id, { showManualGuidance, autoLayout });
    return;
  }
  if (state.activeType !== typeKey) {
    await switchTab(typeKey);
  }
  state.modelId = record.id;
  state.modelRevision = Number(record.revision) || 1;
  state.baseModel = structuredClone(record.modelJson);
  installGraphAndViews(typeKey, record.modelJson, record.name || defaultModelName(typeKey));
  state.diagram = materializeActiveView();
  if (state.tabs[typeKey]) {
    state.tabs[typeKey].modelId = record.id;
    state.tabs[typeKey].modelRevision = state.modelRevision;
    state.tabs[typeKey].baseModel = state.baseModel;
    state.tabs[typeKey].diagram = state.diagram;
    state.tabs[typeKey].modelName = record.name || defaultModelName(typeKey);
    state.tabs[typeKey].dirty = false;
    saveCurrentTabGraphState(typeKey);
  }
  rememberModelSummary(typeKey, record);
  clearDiagramUndoHistory(typeKey);
  clearValidationIssues();
  setActiveModelName(record.name || defaultModelName(typeKey));
  renderPalette();
  renderDiagram();
  renderViewWorkbench();
  centerCurrentDiagram();
  resetModelSaveState();
  if (autoLayout && !activeView()?.autoLayoutApplied && state.diagram.nodes.length) {
    await autoLayoutCurrentDiagram({
      progress: true,
      status: false,
      force: false,
    });
  }
  if (showManualGuidance) {
    applyManualGuidanceFromLoadedModel();
  }
}

async function autoLayoutGeneratedModel(typeLabel) {
  if (activeView()?.autoLayoutApplied || !state.diagram.nodes.length) {
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
  });
  setGenerationProgressPhase(`Rendering the arranged ${typeLabel} model…`, 96);
  await waitForCanvasPaint(1);
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
  const hasUnsavedChanges = hasUnsavedModelChanges();
  if (state.modelId && !hasUnsavedChanges && storedModelHasView(requiredViewId)) {
    return true;
  }
  if (state.modelId && !hasUnsavedChanges && requiredViewId) {
    await saveCurrentModel({ quiet: true, rethrow: true });
    if (storedModelHasView(requiredViewId)) {
      return true;
    }
    throw new Error(`Unable to persist active view: ${requiredViewId}`);
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
  if (!record?.id || !state.modelsCache?.[typeKey]) {
    return;
  }
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

function separateBoundedContextOverlaps(nodeSize) {
  const movedNodeIds = new Set();
  if (!supportsBoundedContext() || state.diagram.nodes.length < 2) {
    return movedNodeIds;
  }
  const contextGroups = new Map();
  state.diagram.nodes.forEach((node) => {
    const contextName = contextNameFromNode(node);
    if (!contextName) {
      return;
    }
    if (!contextGroups.has(contextName)) {
      contextGroups.set(contextName, []);
    }
    contextGroups.get(contextName).push(node);
  });
  if (!contextGroups.size) {
    return movedNodeIds;
  }

  const gap = 88;
  for (let pass = 0; pass < 6; pass += 1) {
    let changed = false;
    for (const [contextName, contextNodes] of contextGroups.entries()) {
      const shiftedTargets = new Set();
      const contextBounds = groupRect(contextNodes, nodeSize, {
        padX: 22,
        padY: 26,
      });
      for (const node of state.diagram.nodes) {
        const nodeContext = contextNameFromNode(node);
        if (nodeContext === contextName) {
          continue;
        }
        const targetKey = nodeContext || node.id;
        if (shiftedTargets.has(targetKey)) {
          continue;
        }
        const targetNodes =
          nodeContext && contextGroups.has(nodeContext) ? contextGroups.get(nodeContext) : [node];
        const targetBounds =
          targetNodes.length > 1
            ? groupRect(targetNodes, nodeSize, { padX: 22, padY: 26 })
            : nodeRect(node, nodeSize);
        if (!rectsOverlap(contextBounds, targetBounds)) {
          continue;
        }
        const dx = contextBounds.maxX - targetBounds.minX + gap;
        moveLayoutNodes(targetNodes, dx, 0, movedNodeIds);
        shiftedTargets.add(targetKey);
        changed = true;
      }
    }
    if (!changed) {
      break;
    }
  }
  return movedNodeIds;
}

function spreadEdgeAnchorsForNode(node, edges, nodeSize) {
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
      const step = Math.max(
        10,
        Math.min(24, (nodeSize.height - 20) / Math.max(1, sideEdges.length - 1)),
      );
      const start = Math.max(10, (nodeSize.height - step * (sideEdges.length - 1)) / 2);
      sideEdges.forEach((edge, index) => {
        const offsetY = Math.round(Math.min(nodeSize.height - 10, start + index * step));
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

function spreadEdgeAnchors(nodes, edges, nodeSize) {
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
  if (!side || !Number.isFinite(offsetY)) {
    return null;
  }
  return {
    x: Math.round(node.x + (side === "right" ? nodeSize.width : 0)),
    y: Math.round(node.y + Math.max(8, Math.min(nodeSize.height - 8, offsetY))),
  };
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

function orthogonalizeEdgeRoutes(nodes, edges, nodeSize) {
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

function fallbackEdgePresentation(sourceNode, targetNode, nodeSize, laneOffset = 0) {
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

function fallbackLaneOffset(edge, laneIndex = 0) {
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
  if (el.deployGithubBtn) {
    el.deployGithubBtn.classList.toggle("hidden", state.activeType !== "artifact");
    el.deployGithubBtn.disabled = state.activeType !== "artifact";
  }
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
        showManualGuidance: true,
        autoLayout: false,
      });
    } else {
      await loadModelById(targetLevel, result.resultModelId, {
        showManualGuidance: true,
        autoLayout: false,
      });
    }
    await autoLayoutGeneratedModel(
      (
        state.modelingConfig.config?.levels?.[targetLevel]?.displayName || targetLevel
      ).toUpperCase(),
    );
    await completeGenerationProgress(transformation.completeLabel || "Model ready.");
    if (!state.validation.issues.length) {
      setStatus(transformation.successStatus || "Model generated and loaded");
    }
  } catch (error) {
    if (isMethodologyValidationError(error)) {
      if (targetLevel && targetLevel !== "artifact" && isTransformationApiError(error, operation)) {
        await switchTab(targetLevel);
      }
      applyValidationIssues(error.issues, { openOnFirst: true });
      toggleValidationDrawer(true);
    } else {
      applyValidationIssues(
        [
          {
            severity: "ERROR",
            constraint: "GenerationError",
            issueClass: "SYSTEM_ERROR",
            message: error.message || transformation.errorMessage || "Generation failed.",
            guidance:
              "Automatic generation was interrupted. Review highlighted items and continue with manual refinement.",
          },
        ],
        { openOnFirst: true },
      );
      toggleValidationDrawer(true);
    }
    setError(`Generation failed: ${error.message}`);
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
    const { closeChatWindow } = await import("./chat.js");
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
  if (!supportsBoundedContext(type)) {
    resetBoundedContextState();
  }
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
    await Promise.all([
      loadCurrentProjectArtifact({ collapseTree: true }),
      refreshGithubConnection(),
    ]);
    return;
  }

  // Restore tab state
  const tabState = state.tabs[type];
  state.modelId = tabState.modelId;
  state.modelRevision = tabState.modelRevision || 0;
  state.baseModel = tabState.baseModel;
  state.diagram = tabState.diagram || emptyDiagram(type);
  if (supportsBoundedContext(type)) {
    resetBoundedContextState();
  }
  restoreTabGraphState(type);
  materializeActiveView();
  clearValidationIssues({ keepPanelState: false });
  tabState.modelName = tabState.modelName || defaultModelName(type);
  state.modelSave.dirty = Boolean(tabState.dirty);
  state.modelSave.saving = false;
  state.modelSave.error = "";
  updateModelSaveUi();

  renderPalette();
  renderDiagram();
  renderViewWorkbench();
  resetCanvasView();
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
    setError(`Validation failed: ${error.message}`);
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
    const selectedStrategy = String(strategy || view.layoutStrategy || "SPACIOUS_LAYERED");
    const response = await api(
      `/${level}/${state.modelId}/views/${encodeURIComponent(
        view.id,
      )}/layout?force=${force}&strategy=${encodeURIComponent(selectedStrategy)}`,
      { method: "POST" },
    );
    if (!response?.view) {
      throw new Error("Backend layout did not return the persisted view.");
    }
    state.modelRevision = Number(response.revision) || state.modelRevision;
    state.views.byId.set(view.id, structuredClone(response.view));
    materializeActiveView();
    state.baseModel = mergePersistedViewIntoBaseModel(response.view);
    if (state.tabs[state.activeType]) {
      state.tabs[state.activeType].modelRevision = state.modelRevision;
      state.tabs[state.activeType].baseModel = state.baseModel;
      state.tabs[state.activeType].diagram = state.diagram;
      saveCurrentTabGraphState(state.activeType);
    }
    window.modlessLayoutAudit = {
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
    renderDiagram();
    await waitForCanvasPaint(1);
    centerViewportOnDiagram({ fit: true });
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
      setError(`Auto layout failed: ${error.message}`);
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
      model: state.modelId ? null : serializeModel(),
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
    installGraphAndViews(state.activeType, body.modelJson, body.name || defaultModelName());
    state.diagram = materializeActiveView();
    if (supportsBoundedContext()) {
      resetBoundedContextState();
    }
    if (state.tabs[state.activeType]) {
      state.tabs[state.activeType].baseModel = structuredClone(state.baseModel);
      state.tabs[state.activeType].diagram = structuredClone(state.diagram);
      state.tabs[state.activeType].modelName = String(body.name || defaultModelName()).trim();
      state.tabs[state.activeType].dirty = true;
      saveCurrentTabGraphState(state.activeType);
    }
    setActiveModelName(body.name || defaultModelName());

    setGenerationProgressPhase("Preparing imported model…", 68);
    let importLayoutWarning = "";
    if (shouldAutoLayoutImportedModel && state.diagram.nodes.length) {
      try {
        setGenerationProgressPhase("Auto-layouting imported model…", 76);
        await autoLayoutCurrentDiagram({
          progress: false,
          save: false,
          publish: false,
          status: false,
          busy: false,
          rethrow: true,
          preserveExistingPositions: false,
        });
      } catch (layoutError) {
        importLayoutWarning = layoutError.message || "Auto layout failed for the imported model.";
        renderDiagram();
        renderViewWorkbench();
        centerCurrentDiagram();
      }
    } else {
      renderDiagram();
      renderViewWorkbench();
      centerCurrentDiagram();
    }
    if (state.tabs[state.activeType]) {
      state.tabs[state.activeType].diagram = structuredClone(state.diagram);
      state.tabs[state.activeType].dirty = true;
      saveCurrentTabGraphState(state.activeType);
    }

    const backendIssues = Array.isArray(body?.issues) ? body.issues : [];
    const hasErrorIssue = backendIssues.some(
      (issue) => String(issue?.severity || "").toUpperCase() === "ERROR",
    );
    if (!hasErrorIssue) {
      setGenerationProgressPhase("Saving imported model…", 88);
      await saveCurrentModel({ rethrow: true, quiet: true });
    } else {
      resetModelSaveState({ dirty: true });
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
  const id = String(detail.id || "").trim();
  const issueName = String(detail.elementName || "")
    .trim()
    .toLowerCase();
  const issueType = String(detail.elementType || "")
    .trim()
    .toLowerCase();
  if (!id) {
    setStatus("Unable to locate issue target.");
    return;
  }

  const candidates = issueTargetCandidates(id);
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
    return (issueName && nodeLabel === issueName) || (issueType && nodeType === issueType);
  });
  if (byName) {
    scrollToNodeAndHighlight(byName.id);
    setStatus("Located related element on canvas.");
    return;
  }

  setStatus("Issue target is not present on current canvas.");
}

let locateIssueTargetBound = false;
if (!locateIssueTargetBound) {
  locateIssueTargetBound = true;
  window.addEventListener("modless:locate-issue-target", (event) => {
    locateIssueTarget(event?.detail || {});
  });
  window.addEventListener("modless:manual-task-toggle", async (event) => {
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
      setError(`Failed to update manual task: ${error.message}`);
    }
  });
}
