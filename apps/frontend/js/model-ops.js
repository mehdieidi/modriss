import {apiUrl, MODEL_TYPES} from './config.js';
import {state} from './state.js';
import {el} from './dom.js';
import {api, apiAuthHeaders} from './api.js';
import {flushCurrentModelPatch} from './model-patch.js';
import {setBusy, setError, setStatus} from './status.js';
import {emptyDiagram} from './utils.js';
import {saveStoredEdgeLayout, serializeModel, toDiagram} from './diagram.js';
import {
  activeView,
  installGraphAndViews,
  restoreTabGraphState,
  saveCurrentTabGraphState,
  setActiveViewId,
  syncActiveViewFromVisibleGraph
} from './graph-store.js';
import {materializeActiveView} from './view-materializer.js';
import {
  centerViewportOnDiagram,
  contextNameFromNode,
  edgePresentationFromLayout,
  getCurrentDiagramNodeSize,
  renderDiagram,
  renderPalette,
  resetCanvasView,
  scrollToConnectionAndHighlight,
  scrollToNodeAndHighlight
} from './canvas.js';
import {resolveNodeOverlaps} from './layout-engine.js';
import {closeAttributePanel} from './attr-panel.js';
import {closeImpactPanel} from './impact.js';
import {refreshGithubConnection} from './github.js';
import {
  loadArtifactById,
  loadArtifactRecord,
  loadCurrentProjectArtifact
} from './artifact.js';
import {confirmAction} from './confirm-action.js';
import {
  completeGenerationProgress,
  hideGenerationProgress,
  setGenerationProgressPhase,
  showGenerationProgress
} from './generation-progress.js';
import {
  applyValidationIssues,
  bindValidationCenterUi,
  clearValidationIssues,
  isMethodologyValidationError,
  setValidationInProgress,
  toggleValidationDrawer
} from './methodology-validation.js';
import {renderViewWorkbench} from './view-explorer.js';
import {
  applyDiagramUndoSnapshot,
  clearDiagramUndoHistory,
  hasDiagramUndoHistory,
  popDiagramUndoSnapshot
} from './undo.js';
import {
  beginModelSave,
  completeModelSave,
  failModelSave,
  hasUnsavedModelChanges,
  markModelDirty,
  resetModelSaveState,
  updateModelSaveUi
} from './model-save-ui.js';

// ── Model list (sidebar select) ───────────────────────────────────────────────

function defaultModelName(typeKey = state.activeType) {
  return `${typeKey}-model`;
}

function isModelingType(typeKey = state.activeType) {
  return ["cim", "pim", "psm"].includes(typeKey);
}

function centerCurrentDiagram({fit = true} = {}) {
  if (Array.isArray(state.diagram?.nodes) && state.diagram.nodes.length) {
    centerViewportOnDiagram({fit});
    return;
  }
  resetCanvasView();
}

function captureModelReplacementSnapshot(typeKey = state.activeType) {
  const tabState = state.tabs[typeKey];
  const serializedModel = typeKey === state.activeType
      ? serializeModel()
      : structuredClone(tabState?.baseModel || {});
  return {
    typeKey,
    modelId: state.modelId,
    modelRevision: state.modelRevision || 0,
    modelName: tabState?.modelName || defaultModelName(typeKey),
    baseModel: serializedModel,
    diagram: structuredClone(state.diagram || emptyDiagram(typeKey)),
    graph: state.graph ? structuredClone({
      elements: [...state.graph.elementsById.values()],
      relationships: [...state.graph.relationshipsById.values()],
      traceLinks: [...state.graph.traceLinksById.values()],
      assumptions: [...state.graph.assumptionsById.values()],
      validationIssues: state.graph.validationIssues || [],
      manualBacklog: state.graph.manualBacklog || []
    }) : null,
    views: state.views ? structuredClone([...state.views.byId.values()])
        : null,
    fragments: state.fragments ? structuredClone(
            [...state.fragments.byId.values()])
        : null,
    activeViewId: state.views?.activeViewId || null,
    validationIssues: structuredClone(state.validation.issues || [])
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
    snapshot, {statusMessage = "", persist = true} = {}) {
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
    state.tabs[snapshot.typeKey].baseModel = structuredClone(
        snapshot.baseModel);
    state.tabs[snapshot.typeKey].diagram = structuredClone(snapshot.diagram);
    state.tabs[snapshot.typeKey].graph = structuredClone(snapshot.graph);
    state.tabs[snapshot.typeKey].views = structuredClone(snapshot.views);
    state.tabs[snapshot.typeKey].fragments = structuredClone(
        snapshot.fragments);
    state.tabs[snapshot.typeKey].activeViewId = snapshot.activeViewId;
    state.tabs[snapshot.typeKey].modelName = snapshot.modelName
        || defaultModelName(snapshot.typeKey);
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
    await saveCurrentModel({quiet: true, rethrow: true});
  } else {
    resetModelSaveState({dirty: true});
  }
  if (Array.isArray(snapshot.validationIssues)
      && snapshot.validationIssues.length) {
    applyValidationIssues(snapshot.validationIssues, {openOnFirst: true});
    toggleValidationDrawer(true);
  } else {
    clearValidationIssues({keepPanelState: false});
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
  const filename = utf8Filename ? decodeURIComponent(utf8Filename[1])
      : (basicFilename?.[1] || fallbackFilename);
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement("a");
  anchor.href = url;
  anchor.download = filename;
  anchor.click();
  URL.revokeObjectURL(url);
}

function manualGuidanceIssuesFromModel(modelJson) {
  const backlog = Array.isArray(modelJson?.manualBacklog)
      ? modelJson.manualBacklog : [];
  return backlog.map((task, index) => {
    const manualTaskId = manualTaskIdentity(task, index);
    const resolved = String(task?.status || "").toUpperCase() === "DONE";
    const required = Boolean(task?.required) || String(
            task?.category || "").toUpperCase()
        === "MANUAL_MODELING";
    const enforcement = String(task?.enforcement || "").trim();
    const mandatory = required || enforcement.startsWith("MANDATORY");
    const taskTitle = String(task?.name || task?.title
        || `Manual task ${index + 1}`);
    const elementId = firstReferenceId(
        task?.elementId,
        task?.relatedElementId,
        task?.targetElementId,
        task?.sourceElementId,
        task?.affectedElements,
        task?.relatedElements
    ) || null;
    return {
      code: mandatory ? "MANUAL_REQUIRED" : "MANUAL_OPTIONAL",
      severity: resolved ? "INFO" : (mandatory ? "ERROR" : "WARNING"),
      constraint: mandatory ? "ManualTaskRequired" : "ManualTaskOptional",
      issueClass: resolved
          ? "MANUAL_RESOLVED"
          : (mandatory ? "MANUAL_REQUIRED" : "MANUAL_OPTIONAL"),
      manualTaskId,
      resolved,
      elementId,
      elementType: String(task?.elementType || task?.relatedElementType || ""),
      elementName: taskTitle,
      message: taskTitle,
      guidance: String(task?.rationale || task?.description
          || "Review and complete this manual methodology step before promotion.")
    };
  });
}

function manualTaskIdentity(task, index) {
  const explicitId = String(task?.id || "").trim();
  if (explicitId) {
    return explicitId;
  }
  const title = String(task?.name || task?.title || "").trim().toLowerCase();
  const elementId = String(task?.elementId || task?.relatedElementId || "")
  .trim().toLowerCase();
  return `manual-task-${index}-${sanitizeManualTaskKey(
      title)}-${sanitizeManualTaskKey(
      elementId)}`;
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
  if (typeof value === "string" || typeof value === "number"
      || typeof value === "boolean") {
    return String(value).trim();
  }
  if (Array.isArray(value)) {
    return firstReferenceId(...value);
  }
  if (typeof value === "object") {
    return firstReferenceId(value.$ref, value.id, value.elementId,
        value.targetElementId, value.sourceElementId);
  }
  return "";
}

function sanitizeManualTaskKey(value) {
  return String(value || "").replaceAll(/[^a-z0-9_-]+/g, "-").replaceAll(
      /^-+|-+$/g, "") || "na";
}

function ensureManualBacklogIdentity(model) {
  if (!model || !Array.isArray(model.manualBacklog)) {
    return;
  }
  model.manualBacklog.forEach((task, index) => {
    if (!task || typeof task !== "object") {
      return;
    }
    if (!String(task.id || "").trim()) {
      task.id = manualTaskIdentity(task, index);
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

function manualGuidanceIssuesFromCurrentModel() {
  ensureManualBacklogIdentity(state.baseModel);
  return manualGuidanceIssuesFromModel(state.baseModel || {});
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
    const key = [
      String(issue?.constraint || issue?.code || ""),
      String(issue?.elementId || ""),
      String(issue?.message || "")
    ].join("::");
    if (seen.has(key)) {
      return;
    }
    seen.add(key);
    merged.push(issue);
  });
  return merged;
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
  const task = model.manualBacklog.find(
      (item, index) => manualTaskIdentity(item, index) === id);
  if (!task) {
    return;
  }
  task.id = id;
  task.status = resolved ? "DONE" : "OPEN";
  if (Array.isArray(state.graph?.manualBacklog)) {
    const graphTask = state.graph.manualBacklog.find(
        (item, index) => manualTaskIdentity(item, index) === id);
    if (graphTask) {
      graphTask.id = id;
      graphTask.status = task.status;
    }
  }
  await saveCurrentModel({quiet: true, rethrow: true});
  const merged = mergeIssuesWithManualGuidance(
      state.validation.issues.filter(
          (issue) => !String(issue?.issueClass || "").startsWith("MANUAL_")));
  applyValidationIssues(merged, {openOnFirst: false});
}

function applyManualGuidanceFromLoadedModel() {
  const modelJson = state.baseModel;
  const allGuidanceIssues = manualGuidanceIssuesFromModel(modelJson);
  if (!allGuidanceIssues.length) {
    return;
  }
  const openGuidanceIssues = allGuidanceIssues.filter((item) => !item.resolved);
  applyValidationIssues([...openGuidanceIssues,
    ...allGuidanceIssues.filter((item) => item.resolved)], {openOnFirst: true});
  toggleValidationDrawer(true);
  const requiredCount = openGuidanceIssues.filter(
      (item) => item.severity === "ERROR").length;
  const optionalCount = openGuidanceIssues.length - requiredCount;
  setStatus(
      `Generated model includes ${requiredCount} required and ${optionalCount} optional manual task(s).`);
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
      [type]: String(modelId)
    };
    const updated = await api(`/projects/${state.project.id}`, {
      method: "PUT",
      body: JSON.stringify({
        name: state.project.name,
        description: state.project.description || "",
        activeModelIds
      })
    });
    state.project = updated;
  } catch (error) {
    console.warn("Failed to sync active model.");
  }
}

export async function reloadModels() {
  try {
    const projectParam = state.project ? `?projectId=${state.project.id}` : "";
    const records = await api(
        `/${MODEL_TYPES[state.activeType].apiType}${projectParam}`);
    state.modelsCache[state.activeType] = records;
  } catch (error) {
    setError(`Load failed: ${error.message}`);
  }
}

// ── Save / Load model ─────────────────────────────────────────────────────────

export async function saveCurrentModel({rethrow = false, quiet = false} = {}) {
  if (!isModelingType()) {
    if (!quiet) {
      setStatus("Switch to CIM, PIM, or PSM to save a model.");
    }
    return;
  }
  const selectedName = getActiveModelName();
  setActiveModelName(selectedName);
  syncActiveViewFromVisibleGraph();
  const payload = {
    name: selectedName,
    model: serializeModel(),
    projectId: state.project?.id || null,
    expectedRevision: state.modelRevision || 1
  };

  const doBusy = !quiet;
  try {
    if (!quiet) {
      beginModelSave();
    }
    if (doBusy) {
      setBusy("Saving…");
    }
    if (state.modelId) {
      const updated = await flushCurrentModelPatch({
        name: payload.name,
        rethrow: true
      });
      if (!updated) {
        throw new Error(
            "Model changes could not be represented as patch operations.");
      }
      if (updated && typeof updated === "object") {
        state.modelRevision = Number(updated.revision) || state.modelRevision;
      }
      state.baseModel = stripServerTransportFields(structuredClone(
          payload.model));
      setActiveModelName(updated?.name || payload.name);
      if (!quiet) {
        setStatus(`Model saved`);
      }
    } else {
      const created = await api(`/${MODEL_TYPES[state.activeType].apiType}`, {
        method: "POST",
        body: JSON.stringify(payload)
      });
      state.modelId = created.id;
      state.modelRevision = Number(created.revision) || 1;
      state.baseModel = stripServerTransportFields(structuredClone(
          payload.model));
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
      applyValidationIssues(error.issues, {openOnFirst: !quiet});
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

export async function loadModelById(typeKey, id,
    {showManualGuidance = false} = {}) {
  if (state.activeType !== typeKey) {
    await switchTab(typeKey);
  }
  const record = await api(`/${MODEL_TYPES[typeKey].apiType}/${id}`);
  state.modelId = record.id;
  state.modelRevision = Number(record.revision) || 1;
  state.baseModel = structuredClone(record.modelJson);
  state.diagram = toDiagram(typeKey, record.modelJson, record.name);
  installGraphAndViews(typeKey, record.modelJson, record.name
      || defaultModelName(typeKey));
  materializeActiveView();
  if (typeKey === "cim") {
    state.boundedContextCreateMode = false;
    state.boundedContextDraftNodeIds = new Set();
    state.boundedContextDraftName = "";
    state.boundedContextViewMode = "normal";
    state.activeBoundedContextName = "";
  }
  if (state.tabs[typeKey]) {
    state.tabs[typeKey].modelId = record.id;
    state.tabs[typeKey].modelRevision = state.modelRevision;
    state.tabs[typeKey].baseModel = structuredClone(record.modelJson);
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
  if (showManualGuidance) {
    applyManualGuidanceFromLoadedModel();
  }
}

async function loadModelRecord(typeKey, record,
    {showManualGuidance = false} = {}) {
  if (!record?.id || !record?.modelJson) {
    await loadModelById(typeKey, record?.id, {showManualGuidance});
    return;
  }
  if (state.activeType !== typeKey) {
    await switchTab(typeKey);
  }
  state.modelId = record.id;
  state.modelRevision = Number(record.revision) || 1;
  state.baseModel = structuredClone(record.modelJson);
  state.diagram = toDiagram(typeKey, record.modelJson, record.name);
  installGraphAndViews(typeKey, record.modelJson, record.name
      || defaultModelName(typeKey));
  materializeActiveView();
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
  if (showManualGuidance) {
    applyManualGuidanceFromLoadedModel();
  }
}

// ── Transformation / generation ───────────────────────────────────────────────

async function runTransformation(path, sourceModelId) {
  const result = await api(`/transformations/${path}`, {
    method: "POST",
    body: JSON.stringify({
      sourceModelId,
      expectedRevision: state.modelRevision || 1
    })
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

async function ensureStoredModelForBackendOperation(operationLabel) {
  if (state.modelId && !hasUnsavedModelChanges()) {
    return true;
  }
  const level = String(state.activeType || "model").toUpperCase();
  const confirmed = await confirmAction({
    title: "Save Model First",
    message: `${operationLabel} uses the stored backend ${level} model. Save the current model first, then continue?`,
    confirmLabel: "Save and Continue"
  });
  if (!confirmed) {
    setStatus(`${operationLabel} canceled`);
    return false;
  }
  await saveCurrentModel({quiet: true, rethrow: true});
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
      const diagnostics = Array.isArray(job?.diagnostics)
          ? job.diagnostics.join("; ")
          : "";
      throw new Error(
          diagnostics || `Transformation job ${status.toLowerCase()}.`);
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
    revision: Number(record.revision) || 1
  };
  state.modelsCache[typeKey] = [
    summary,
    ...state.modelsCache[typeKey].filter((item) => item.id !== record.id)
  ];
}

function semanticPortsForNode(node) {
  const type = String(node?.type || "");
  const commonPorts = [
    {id: "flow-in", label: "in", width: 10, height: 10},
    {id: "flow-out", label: "out", width: 10, height: 10}
  ];
  if (["Function", "AwsLambdaFunction", "Command", "BusinessEvent"].includes(
      type)) {
    return [
      ...commonPorts,
      {id: "data", label: "data", width: 10, height: 10},
      {id: "security", label: "sec", width: 10, height: 10}
    ];
  }
  if (["DataStore", "ObjectStore", "DynamoDbTable", "S3Bucket",
    "SecretsManagerSecret", "SsmParameter", "KmsKey", "IamRole",
    "IamPolicy"].includes(type)) {
    return [
      {id: "resource-in", label: "in", width: 10, height: 10},
      {id: "resource-out", label: "out", width: 10, height: 10}
    ];
  }
  return commonPorts;
}

function semanticPortForEdge(edge, endpoint) {
  const kind = String(edge?.kind || "").toUpperCase();
  if (["READS", "WRITES", "READS_FROM", "WRITES_TO", "HAS_ENV"].includes(
      kind)) {
    return endpoint === "source" ? "data" : "resource-in";
  }
  if (["USES_SECRET", "GRANTS", "USES_ROLE", "AUTHORIZED_BY",
    "ENCRYPTED_BY"].includes(kind)) {
    return endpoint === "source" ? "security" : "resource-in";
  }
  if (["CAUSES", "EMITS", "PUBLISHES", "TRIGGERS", "INVOKES", "TARGETS",
    "ROUTES_TO", "INTEGRATES_WITH", "PRECEDES", "ORCHESTRATES"].includes(
      kind)) {
    return endpoint === "source" ? "flow-out" : "flow-in";
  }
  return endpoint === "source" ? "flow-out" : "flow-in";
}

function nodeRect(node, nodeSize) {
  return {
    minX: node.x,
    minY: node.y,
    maxX: node.x + nodeSize.width,
    maxY: node.y + nodeSize.height
  };
}

function groupRect(nodes, nodeSize, {padX = 0, padY = 0} = {}) {
  return {
    minX: Math.min(...nodes.map((node) => node.x)) - padX,
    minY: Math.min(...nodes.map((node) => node.y)) - padY,
    maxX: Math.max(...nodes.map((node) => node.x + nodeSize.width)) + padX,
    maxY: Math.max(...nodes.map((node) => node.y + nodeSize.height)) + padY
  };
}

function rectsOverlap(a, b) {
  return a.minX < b.maxX && a.maxX > b.minX
      && a.minY < b.maxY && a.maxY > b.minY;
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
  if (state.activeType !== "cim" || state.diagram.nodes.length < 2) {
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
        padY: 26
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
        const targetNodes = nodeContext && contextGroups.has(nodeContext)
            ? contextGroups.get(nodeContext)
            : [node];
        const targetBounds = targetNodes.length > 1
            ? groupRect(targetNodes, nodeSize, {padX: 22, padY: 26})
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

export function updateGenerateButtonState() {
  if (!el.generateContextBtn) {
    return;
  }
  const buttonByType = {
    cim: {label: "Generate PIM", title: "Transform active CIM model to PIM"},
    pim: {label: "Generate PSM", title: "Transform active PIM model to PSM"},
    psm: {
      label: "Generate Artifacts",
      title: "Generate artifacts from active PSM model"
    },
    artifact: {
      label: "Download Project",
      title: "Download the generated project"
    }
  };
  const buttonConfig = buttonByType[state.activeType];
  const isVisible = Boolean(buttonConfig);
  el.generateContextBtn.classList.toggle("hidden", !isVisible);
  el.generateContextBtn.disabled = !isVisible;
  el.generateContextBtn.classList.toggle("topbar-download-btn",
      state.activeType === "artifact");
  if (el.deployGithubBtn) {
    el.deployGithubBtn.classList.toggle("hidden",
        state.activeType !== "artifact");
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

export async function generateCimToPim() {
  if (state.activeType !== "cim" || !state.modelId) {
    if (state.activeType !== "cim") {
      setStatus("Switch to CIM tab first");
      return;
    }
  }
  try {
    if (!await ensureStoredModelForBackendOperation("Generate PIM")) {
      return;
    }
    showGenerationProgress({
      title: "Generating PIM",
      subtitle: "Turning the current CIM into a platform-independent model.",
      label: "Starting backend transformation…"
    });
    setBusy("Generating PIM…");
    setGenerationProgressPhase(
        "Translating the CIM into a draft PIM model…", 68);
    const result = await runTransformation("cim-to-pim", state.modelId);
    if (result.model) {
      await loadModelRecord("pim", result.model, {showManualGuidance: true});
    } else {
      await loadModelById("pim", result.resultModelId,
          {showManualGuidance: true});
    }
    setGenerationProgressPhase("Opening the generated PIM model…", 92);
    await completeGenerationProgress("PIM ready.");
    if (!state.validation.issues.length) {
      setStatus("PIM generated and loaded");
    }
  } catch (error) {
    if (isMethodologyValidationError(error)) {
      if (isTransformationApiError(error, "cim-to-pim")) {
        await switchTab("pim");
      }
      applyValidationIssues(error.issues, {openOnFirst: true});
      toggleValidationDrawer(true);
    } else {
      applyValidationIssues([{
        severity: "ERROR",
        constraint: "GenerationError",
        issueClass: "SYSTEM_ERROR",
        message: error.message || "CIM to PIM generation failed.",
        guidance:
            "Automatic generation was interrupted. Review highlighted items and continue with manual refinement."
      }], {openOnFirst: true});
      toggleValidationDrawer(true);
    }
    setError(`Generation failed: ${error.message}`);
  } finally {
    hideGenerationProgress();
  }
}

export async function generatePimToPsm() {
  if (state.activeType !== "pim" || !state.modelId) {
    if (state.activeType !== "pim") {
      setStatus("Switch to PIM tab first");
      return;
    }
  }
  try {
    if (!await ensureStoredModelForBackendOperation("Generate PSM")) {
      return;
    }
    showGenerationProgress({
      title: "Generating PSM",
      subtitle: "Converting the current PIM into a platform-specific model.",
      label: "Starting backend transformation…"
    });
    setBusy("Generating PSM…");
    setGenerationProgressPhase(
        "Transforming the PIM into a platform-specific design…", 68);
    const result = await runTransformation("pim-to-psm", state.modelId);
    if (result.model) {
      await loadModelRecord("psm", result.model, {showManualGuidance: true});
    } else {
      await loadModelById("psm", result.resultModelId,
          {showManualGuidance: true});
    }
    setGenerationProgressPhase("Opening the generated PSM model…", 92);
    await completeGenerationProgress("PSM ready.");
    if (!state.validation.issues.length) {
      setStatus("PSM generated and loaded");
    }
  } catch (error) {
    if (isMethodologyValidationError(error)) {
      if (isTransformationApiError(error, "pim-to-psm")) {
        await switchTab("psm");
      }
      applyValidationIssues(error.issues, {openOnFirst: true});
      toggleValidationDrawer(true);
    } else {
      applyValidationIssues([{
        severity: "ERROR",
        constraint: "GenerationError",
        issueClass: "SYSTEM_ERROR",
        message: error.message || "PIM to PSM generation failed.",
        guidance:
            "Automatic generation was interrupted. Review highlighted items and continue with manual refinement."
      }], {openOnFirst: true});
      toggleValidationDrawer(true);
    }
    setError(`Generation failed: ${error.message}`);
  } finally {
    hideGenerationProgress();
  }
}

export async function generatePsmToArtifact() {
  if (state.activeType !== "psm" || !state.modelId) {
    if (state.activeType !== "psm") {
      setStatus("Switch to PSM tab first");
      return;
    }
  }
  try {
    if (!await ensureStoredModelForBackendOperation("Generate Artifacts")) {
      return;
    }
    showGenerationProgress({
      title: "Generating Artifacts",
      subtitle: "Building deployable project files from the current PSM.",
      label: "Starting backend generation…"
    });
    setBusy("Generating Artifact…");
    setGenerationProgressPhase(
        "Generating deployment-ready artifacts from the PSM…", 76);
    const result = await runTransformation("psm-to-artifact", state.modelId);
    setStatus("Artifact generated — loading…");
    setGenerationProgressPhase(
        "Opening the generated project in the artifact explorer…", 94);
    if (result.artifact) {
      await loadArtifactRecord(result.artifact, {collapseTree: true});
    } else {
      await loadArtifactById(result.resultArtifactId, {collapseTree: true});
    }
    await switchTab("artifact");
    await completeGenerationProgress("Artifacts ready.");
    setStatus("Artifact ready");
  } catch (error) {
    if (isMethodologyValidationError(error)) {
      applyValidationIssues(error.issues, {openOnFirst: true});
      toggleValidationDrawer(true);
    } else {
      applyValidationIssues([{
        severity: "ERROR",
        constraint: "GenerationError",
        issueClass: "SYSTEM_ERROR",
        message: error.message || "PSM to Artifact generation failed.",
        guidance:
            "Automatic generation was interrupted. Review highlighted items and continue with manual refinement."
      }], {openOnFirst: true});
      toggleValidationDrawer(true);
    }
    setError(`Generation failed: ${error.message}`);
  } finally {
    hideGenerationProgress();
  }
}

export async function generateForCurrentContext() {
  if (state.activeType === "cim") {
    await generateCimToPim();
    return;
  }
  if (state.activeType === "pim") {
    await generatePimToPsm();
    return;
  }
  if (state.activeType === "psm") {
    await generatePsmToArtifact();
    return;
  }
  setStatus("Switch to CIM, PIM, or PSM tab first");
}

// ── Tab switching ─────────────────────────────────────────────────────────────

export async function switchTab(type) {
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
  if (type !== "cim") {
    state.boundedContextCreateMode = false;
    state.boundedContextDraftNodeIds = new Set();
    state.boundedContextDraftName = "";
    state.boundedContextViewMode = "normal";
    state.activeBoundedContextName = "";
    state.selectedBoundedContextName = null;
  }
  Array.from(el.modelTabs.querySelectorAll(".tab")).forEach(
      (t) => t.classList.toggle("active", t.dataset.type === type));
  updateGenerateButtonState();

  const isArtifact = type === "artifact";
  const isReadonlyEditor = isArtifact;
  const topbar = document.querySelector(".topbar");
  el.workspace?.classList.toggle("artifact-mode", isArtifact);
  topbar?.classList.toggle("artifact-mode", isArtifact);
  if (isArtifact) {
    el.workspace?.classList.remove("palette-collapsed");
  } else {
    el.workspace?.classList.toggle("palette-collapsed",
        !!state.paletteCollapsed);
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
    el.modelWorkbenchPanel?.classList.add("hidden");
    updateModelSaveUi();
    setStatus("Artifact Explorer");
    await Promise.all([
      loadCurrentProjectArtifact({collapseTree: true}),
      refreshGithubConnection()
    ]);
    return;
  }

  // Restore tab state
  const tabState = state.tabs[type];
  state.modelId = tabState.modelId;
  state.modelRevision = tabState.modelRevision || 0;
  state.baseModel = tabState.baseModel;
  state.diagram = tabState.diagram || emptyDiagram(type);
  if (type === "cim") {
    state.boundedContextCreateMode = false;
    state.boundedContextDraftNodeIds = new Set();
    state.boundedContextDraftName = "";
    state.boundedContextViewMode = "normal";
    state.activeBoundedContextName = "";
  }
  restoreTabGraphState(type);
  materializeActiveView();
  clearValidationIssues({keepPanelState: false});
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
    setStatus("Validation is available for CIM, PIM, and PSM.");
    return;
  }
  try {
    if (!await ensureStoredModelForBackendOperation("Validate Model")) {
      return;
    }
    setBusy("Validating…");
    setValidationInProgress(true);
    const result = await api(
        `/${MODEL_TYPES[state.activeType].apiType}/${state.modelId}/validate`,
        {method: "POST"});
    const backendIssues = Array.isArray(result?.issues) ? result.issues : [];
    if (backendIssues.length) {
      const mergedIssues = mergeIssuesWithManualGuidance(backendIssues);
      applyValidationIssues(mergedIssues, {openOnFirst: true});
      toggleValidationDrawer(true);
      setStatus(result?.valid === false
          ? "Validation completed with errors."
          : "Validation completed with issues.");
      return;
    }
    const manualGuidance = manualGuidanceIssuesFromCurrentModel();
    if (manualGuidance.length) {
      applyValidationIssues(manualGuidance, {openOnFirst: true});
      toggleValidationDrawer(true);
      const requiredCount = manualGuidance.filter(
          (issue) => String(issue?.severity || "").toUpperCase()
              === "ERROR").length;
      const optionalCount = manualGuidance.length - requiredCount;
      setStatus(
          `Model is valid. Pending manual tasks: ${requiredCount} required, ${optionalCount} optional.`);
    } else {
      clearValidationIssues();
      toggleValidationDrawer(true);
      setStatus("Validation passed. No issues found.");
    }
  } catch (error) {
    if (isMethodologyValidationError(error)) {
      const merged = mergeIssuesWithManualGuidance(error.issues);
      applyValidationIssues(merged, {openOnFirst: true});
      toggleValidationDrawer(true);
      setStatus("Validation completed with issues.");
      return;
    }
    setError(`Validation failed: ${error.message}`);
  } finally {
    setValidationInProgress(false);
  }
}

export async function autoLayoutCurrentDiagram({
  progress = true,
  save = true,
  publish = true,
  status = true,
  busy = true,
  rethrow = false,
  preserveExistingPositions = true
} = {}) {
  if (!isModelingType()) {
    if (status) {
      setStatus("Auto layout is available for CIM, PIM, and PSM.");
    }
    return;
  }
  if (!state.diagram.nodes.length) {
    if (status) {
      setStatus("Add elements to the diagram first.");
    }
    return;
  }

  const nodeSize = getCurrentDiagramNodeSize();
  const view = activeView();
  const payload = {
    viewId: state.views.activeViewId,
    profile: view?.layoutProfile || "DEFAULT_LAYERED",
    preserveExistingPositions,
    options: {
      nodeSpacing: state.activeType === "cim" ? 96 : 112,
      layerSpacing: state.activeType === "cim" ? 164 : 188,
      nodePlacementStrategy: "NETWORK_SIMPLEX"
    },
    nodes: state.diagram.nodes.map((node) => ({
      id: node.id,
      label: node.label,
      width: nodeSize.width,
      height: nodeSize.height,
      x: node.x,
      y: node.y,
      ports: semanticPortsForNode(node)
    })),
    edges: state.diagram.connections.map((edge) => ({
      id: edge.id,
      label: edge.bundle ? (edge.label || "bundle") : edge.kind,
      sourceNodeId: edge.sourceId,
      targetNodeId: edge.targetId,
      sourcePortId: semanticPortForEdge(edge, "source"),
      targetPortId: semanticPortForEdge(edge, "target")
    })).filter((edge) => !String(edge.id || "").startsWith("bundle-"))
  };

  try {
    if (progress) {
      showGenerationProgress({
        kicker: "Auto Layout in Progress",
        title: "Arranging current view",
        subtitle: "Computing positions for the elements visible in this view.",
        label: "Preparing diagram elements…"
      });
    }
    if (busy) {
      setBusy("Auto layout…");
    }
    if (progress) {
      setGenerationProgressPhase("Computing layout…", 46);
    }
    const response = await api("/layout", {
      method: "POST",
      body: JSON.stringify(payload)
    });
    const nodesById = new Map(
        (response.nodes || []).map((node) => [node.id, node]));
    const edgesById = new Map(
        (response.edges || []).map((edge) => [edge.id, edge]));

    state.diagram.nodes.forEach((node) => {
      const positioned = nodesById.get(node.id);
      if (!positioned) {
        return;
      }
      node.x = Math.round(Number(positioned.x) || 0);
      node.y = Math.round(Number(positioned.y) || 0);
      node.meta = node.meta && typeof node.meta === "object" ? node.meta : {};
      node.meta.x = node.x;
      node.meta.y = node.y;
    });
    const movedNodeIds = new Set(resolveNodeOverlaps(state.diagram.nodes,
        nodeSize));
    const movedByContext = separateBoundedContextOverlaps(nodeSize);
    movedByContext.forEach((nodeId) => movedNodeIds.add(nodeId));
    resolveNodeOverlaps(state.diagram.nodes, nodeSize).forEach((nodeId) =>
        movedNodeIds.add(nodeId));
    const layoutNodesById = new Map(
        state.diagram.nodes.map((node) => [node.id, node]));
    state.diagram.connections.filter((edge) => !edge.bundle).forEach((edge) => {
      if (movedNodeIds.has(edge.sourceId) || movedNodeIds.has(
          edge.targetId)) {
        edge.pinPoints = [];
        delete edge.sourceAnchor;
        delete edge.targetAnchor;
        delete edge.layout;
        saveStoredEdgeLayout(state.activeType, edge.id, {pinPoints: []});
        return;
      }
      const layoutData = edgesById.get(edge.id);
      const sourceNode = layoutNodesById.get(edge.sourceId);
      const targetNode = layoutNodesById.get(edge.targetId);
      const presentation = edgePresentationFromLayout(layoutData, sourceNode,
          targetNode);
      edge.pinPoints = [];
      edge.sourceAnchor = presentation.sourceAnchor;
      edge.targetAnchor = presentation.targetAnchor;
      delete edge.layout;
      saveStoredEdgeLayout(state.activeType, edge.id, {
        pinPoints: [],
        sourceAnchor: presentation.sourceAnchor,
        targetAnchor: presentation.targetAnchor
      });
    });
    if (progress) {
      setGenerationProgressPhase("Refreshing the canvas…", 76);
    }
    syncActiveViewFromVisibleGraph();

    renderDiagram();
    renderViewWorkbench();
    centerViewportOnDiagram({fit: true});
    if (save) {
      if (progress) {
        setGenerationProgressPhase("Saving layout…", 92);
      }
      await saveCurrentModel({quiet: true, rethrow: true});
    }
    if (publish) {
    }
    if (progress) {
      await completeGenerationProgress("Layout applied.");
    }
    const warnings = Array.isArray(response.warnings) ? response.warnings : [];
    if (warnings.length) {
      if (status) {
        setStatus(`Auto layout applied with ${warnings.length} warning(s).`);
      }
      return;
    }
    if (status) {
      setStatus("Auto layout applied.");
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
    setStatus(
        `Switch to CIM, PIM, or PSM to export model ${format.toUpperCase()}.`);
    return;
  }
  const normalizedFormat = String(format || "json").toLowerCase();
  if (state.modelId) {
    const patched = await flushCurrentModelPatch({
      name: getActiveModelName(),
      rethrow: true
    });
    if (!patched) {
      await saveCurrentModel({quiet: true, rethrow: true});
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
      ...apiAuthHeaders()
    },
    body: JSON.stringify({
      name: getActiveModelName(),
      model: state.modelId ? null : serializeModel(),
      format: normalizedFormat
    })
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
  const fallbackFilename = `${state.project?.name
  || "project"}-${state.activeType}.${normalizedFormat}`;
  await downloadBlobFromResponse(response, fallbackFilename);
  setStatus(
      `Exported ${state.activeType.toUpperCase()} model ${normalizedFormat.toUpperCase()}`);
}

export async function importActiveModel(file, format = "json",
    typeKey = state.activeType) {
  if (!isModelingType(typeKey)) {
    setStatus(
        `Switch to CIM, PIM, or PSM to import model ${format.toUpperCase()}.`);
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
    danger: false
  });
  if (!confirmed) {
    return;
  }

  const normalizedFormat = String(format || "json").toLowerCase();
  const formData = new FormData();
  formData.append("file", file);
  let layoutWarning = "";
  try {
    showGenerationProgress({
      kicker: "Import in Progress",
      title: "Importing model",
      subtitle: "Loading the file, arranging the diagram, and preparing the canvas.",
      label: "Uploading model file…"
    });
    setBusy("Importing model…");
    const response = await fetch(
        apiUrl(
            `/${MODEL_TYPES[state.activeType].apiType}/import?format=${encodeURIComponent(
                normalizedFormat)}&projectId=${encodeURIComponent(
                state.project?.id || "")}`), {
          method: "POST",
          headers: apiAuthHeaders(),
          body: formData
        });
    setGenerationProgressPhase("Reading imported model…", 34);
    const contentType = response.headers.get("content-type") || "";
    const body = contentType.includes("application/json")
        ? await response.json()
        : null;
    if (!response.ok) {
      const issues = Array.isArray(body?.issues) ? body.issues : [];
      if (issues.length) {
        applyValidationIssues(issues, {openOnFirst: true});
        toggleValidationDrawer(true);
      }
      throw new Error(body?.message || `Import failed (${response.status})`);
    }

    setGenerationProgressPhase("Materializing imported graph…", 52);
    pushModelReplacementSnapshot();
    clearDiagramUndoHistory(state.activeType);
    state.baseModel = structuredClone(body.modelJson);
    installGraphAndViews(state.activeType, body.modelJson,
        body.name || defaultModelName());
    state.diagram = materializeActiveView();
    if (state.activeType === "cim") {
      state.boundedContextCreateMode = false;
      state.boundedContextDraftNodeIds = new Set();
      state.boundedContextDraftName = "";
      state.boundedContextViewMode = "normal";
      state.activeBoundedContextName = "";
    }
    if (state.tabs[state.activeType]) {
      state.tabs[state.activeType].baseModel = structuredClone(
          state.baseModel);
      state.tabs[state.activeType].diagram = structuredClone(state.diagram);
      state.tabs[state.activeType].modelName = String(
          body.name || defaultModelName()).trim();
      state.tabs[state.activeType].dirty = true;
      saveCurrentTabGraphState(state.activeType);
    }
    setActiveModelName(body.name || defaultModelName());

    setGenerationProgressPhase("Arranging imported model…", 68);
    try {
      await autoLayoutCurrentDiagram({
        progress: false,
        save: false,
        publish: false,
        status: false,
        busy: false,
        rethrow: true,
        preserveExistingPositions: false
      });
    } catch (error) {
      layoutWarning = error.message;
    }
    renderDiagram();
    renderViewWorkbench();
    centerCurrentDiagram();
    if (state.tabs[state.activeType]) {
      state.tabs[state.activeType].diagram = structuredClone(state.diagram);
      state.tabs[state.activeType].dirty = true;
      saveCurrentTabGraphState(state.activeType);
    }

    const backendIssues = Array.isArray(body?.issues) ? body.issues : [];
    const hasErrorIssue = backendIssues.some(
        (issue) => String(issue?.severity || "").toUpperCase() === "ERROR");
    if (!hasErrorIssue) {
      setGenerationProgressPhase("Saving imported model…", 88);
      await saveCurrentModel({rethrow: true, quiet: true});
    } else {
      resetModelSaveState({dirty: true});
    }
    await completeGenerationProgress("Imported model ready.");
    if (backendIssues.length) {
      const mergedIssues = mergeIssuesWithManualGuidance(backendIssues);
      applyValidationIssues(mergedIssues, {openOnFirst: true});
      toggleValidationDrawer(true);
      setStatus(
          `Imported ${state.activeType.toUpperCase()} model ${normalizedFormat.toUpperCase()} with ${backendIssues.length} issue(s).`);
      return;
    }
    clearValidationIssues({keepPanelState: false});
    setStatus(layoutWarning
        ? `Imported ${state.activeType.toUpperCase()} model ${normalizedFormat.toUpperCase()}, but auto layout failed: ${layoutWarning}`
        : `Imported ${state.activeType.toUpperCase()} model ${normalizedFormat.toUpperCase()}`);
  } finally {
    hideGenerationProgress();
  }
}

export async function undoLastModelReplacement() {
  if (!isModelingType()) {
    setStatus("Switch to CIM, PIM, or PSM to undo model replacement.");
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
    persist: true
  });
}

export async function undoLastEdit() {
  if (!isModelingType()) {
    setStatus("Switch to CIM, PIM, or PSM to undo.");
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
    setActiveModelName(
        snapshot.modelName || defaultModelName(snapshot.typeKey));
    renderDiagram();
    renderViewWorkbench();
    await saveCurrentModel({quiet: true, rethrow: true});
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
    current = state.graph?.parentByChild instanceof Map
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
    const hasNode = Array.isArray(view?.nodes) && view.nodes.some((node) =>
        candidates.includes(String(node?.elementId || node?.id || "")));
    if (hasNode) {
      return view;
    }
    const viewRelationships = Array.isArray(view?.edges) ? view.edges
        : view?.relationships;
    const hasRelationship = Array.isArray(viewRelationships)
        && viewRelationships.some((relationship) =>
            candidates.includes(String(relationship?.relationshipId
                || relationship?.id || "")));
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
  const issueName = String(detail.elementName || "").trim().toLowerCase();
  const issueType = String(detail.elementType || "").trim().toLowerCase();
  if (!id) {
    setStatus("Unable to locate issue target.");
    return;
  }

  const candidates = issueTargetCandidates(id);
  activateViewForIssueTarget(candidates);

  for (const candidate of candidates) {
    if (state.diagram.nodes.some((node) => node.id === candidate)) {
      scrollToNodeAndHighlight(candidate);
      setStatus(candidate === id ? "Located issue element on canvas."
          : "Located containing element on canvas.");
      return;
    }
  }
  for (const candidate of candidates) {
    if (state.diagram.connections.some((edge) => edge.id === candidate)) {
      scrollToConnectionAndHighlight(candidate);
      setStatus(candidate === id ? "Located issue connection on canvas."
          : "Located containing connection on canvas.");
      return;
    }
  }

  const byName = state.diagram.nodes.find((node) => {
    const nodeLabel = String(node.label || "").trim().toLowerCase();
    const nodeType = String(node.type || "").trim().toLowerCase();
    return (issueName && nodeLabel === issueName)
        || (issueType && nodeType === issueType);
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
      setStatus(resolved ? "Manual task marked resolved."
          : "Manual task moved back to open.");
    } catch (error) {
      setError(`Failed to update manual task: ${error.message}`);
    }
  });
}
