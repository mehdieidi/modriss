import { state } from "./state.js";
import { el } from "./dom.js";
import { api, apiAuthHeaders } from "./api.js";
import { setBusy, setError, setStatus } from "./status.js";
import { formatUserError } from "./errors.js";
import { emptyDiagram, escapeHtml } from "./utils.js";
import { toDiagram } from "./diagram.js";
import { renderDiagram, renderPalette, resetCanvasView } from "./canvas.js";
import { updateGenerateButtonState } from "./model-ops.js";
import { renderViewWorkbench } from "./view-explorer.js";
import { restoreTabGraphState } from "./graph-store.js";
import { materializeActiveView } from "./view-materializer.js";
import { clearArtifactState } from "./artifact.js";
import { resetChatForProjectChange } from "./chat.js";
import { apiUrl, MODEL_TYPES } from "./config.js";
import { confirmAction } from "./confirm-action.js";
import { resetModelSaveState, updateModelSaveUi } from "./model-save-ui.js";
import { defaultModelingLevel, modelingLevelKeys } from "./modeling-config-data.js";

function resolveProjectId(project) {
  return project?.id || project?.projectId || project?.uuid || null;
}

const getElementTarget = (event) => (event.target instanceof Element ? event.target : null);
const LAST_PROJECT_STORAGE_PREFIX = "modless.lastProjectId";

function defaultModelName(typeKey) {
  return state.modelingConfig.config?.levels?.[typeKey]?.modelNameTemplate || `${typeKey}-model`;
}

function resetModelingTab(typeKey) {
  state.tabs[typeKey] = {
    modelId: null,
    modelRevision: 0,
    baseModel: null,
    diagram: emptyDiagram(typeKey),
    modelName: defaultModelName(typeKey),
    graph: null,
    views: null,
    fragments: null,
    activeViewId: null,
    dirty: false,
  };
}

function lastProjectStorageKey() {
  const userId = state.auth?.user?.id || state.auth?.user?.email || "anonymous";
  return `${LAST_PROJECT_STORAGE_PREFIX}:${String(userId).toLowerCase()}`;
}

function saveLastProjectId(projectId) {
  if (!projectId) {
    return;
  }
  window.localStorage.setItem(lastProjectStorageKey(), String(projectId));
}

function readLastProjectId() {
  return window.localStorage.getItem(lastProjectStorageKey()) || "";
}

function clearLastProjectId() {
  window.localStorage.removeItem(lastProjectStorageKey());
}

export function bindProjectDialogActions() {
  if (el.createProjectBtn && el.createProjectBtn.dataset.boundProjectCreate !== "true") {
    el.createProjectBtn.dataset.boundProjectCreate = "true";
    el.createProjectBtn.addEventListener("click", createProject);
  }

  if (el.newProjectName && el.newProjectName.dataset.boundProjectEnter !== "true") {
    el.newProjectName.dataset.boundProjectEnter = "true";
    el.newProjectName.addEventListener("keydown", (event) => {
      if (event.key === "Enter") {
        createProject();
      }
    });
  }

  if (el.projectList && el.projectList.dataset.boundProjectListClick !== "true") {
    el.projectList.dataset.boundProjectListClick = "true";
    el.projectList.addEventListener("click", async (event) => {
      const target = getElementTarget(event);
      const item = target?.closest(".project-item[data-project-id]");
      if (!item) {
        return;
      }
      const projectId = item.getAttribute("data-project-id");
      if (!projectId) {
        return;
      }
      try {
        const fullProject = await api(`/projects/${projectId}`);
        await loadProject(fullProject);
      } catch (error) {
        setError(error, { prefix: "Failed to load project." });
      }
    });
  }

  if (el.editProjectNameBtn && el.editProjectNameBtn.dataset.boundEditProjectName !== "true") {
    el.editProjectNameBtn.dataset.boundEditProjectName = "true";
    el.editProjectNameBtn.addEventListener("click", (event) => {
      event.preventDefault();
      event.stopPropagation();
      showProjectNameDialog();
    });
  }

  if (
    el.projectNameCancelBtn &&
    el.projectNameCancelBtn.dataset.boundProjectNameCancel !== "true"
  ) {
    el.projectNameCancelBtn.dataset.boundProjectNameCancel = "true";
    el.projectNameCancelBtn.addEventListener("click", hideProjectNameDialog);
  }

  if (el.projectNameSaveBtn && el.projectNameSaveBtn.dataset.boundProjectNameSave !== "true") {
    el.projectNameSaveBtn.dataset.boundProjectNameSave = "true";
    el.projectNameSaveBtn.addEventListener("click", saveProjectName);
  }

  if (el.projectNameInput && el.projectNameInput.dataset.boundProjectNameInput !== "true") {
    el.projectNameInput.dataset.boundProjectNameInput = "true";
    el.projectNameInput.addEventListener("keydown", (event) => {
      if (event.key === "Enter") {
        event.preventDefault();
        saveProjectName();
      } else if (event.key === "Escape") {
        event.preventDefault();
        hideProjectNameDialog();
      }
    });
  }

  if (el.projectNameOverlay && el.projectNameOverlay.dataset.boundProjectNameOverlay !== "true") {
    el.projectNameOverlay.dataset.boundProjectNameOverlay = "true";
    el.projectNameOverlay.addEventListener("click", (event) => {
      if (event.target === el.projectNameOverlay) {
        hideProjectNameDialog();
      }
    });
  }
}

// ── Show / hide project dialog ────────────────────────────────────────────────

export async function showProjectDialog() {
  el.projectOverlay.classList.remove("hidden");
  await refreshProjectList();
}

export function hideProjectDialog() {
  el.projectOverlay.classList.add("hidden");
}

export function showProjectNameDialog() {
  if (!state.project?.id) {
    setStatus("Load a project first.");
    return;
  }
  if (!el.projectNameOverlay || !el.projectNameInput) {
    setError("Project name editor is unavailable");
    return;
  }
  if (el.projectNameError) {
    el.projectNameError.classList.add("hidden");
    el.projectNameError.textContent = "";
  }
  el.projectNameInput.value = state.project?.name || "";
  el.projectNameOverlay.classList.remove("hidden");
  el.projectNameOverlay.setAttribute("aria-hidden", "false");
  document.body.classList.add("modal-open");
  el.projectNameInput.focus();
  el.projectNameInput.select();
}

export function hideProjectNameDialog() {
  if (!el.projectNameOverlay) {
    return;
  }
  el.projectNameOverlay.classList.add("hidden");
  el.projectNameOverlay.setAttribute("aria-hidden", "true");
  document.body.classList.remove("modal-open");
}

async function saveProjectName() {
  if (!state.project?.id || !el.projectNameInput) {
    return;
  }
  const name = el.projectNameInput.value.trim();
  if (!name) {
    if (el.projectNameError) {
      el.projectNameError.textContent = "Project name cannot be empty";
      el.projectNameError.classList.remove("hidden");
    }
    return;
  }
  const setSaving = (saving) => {
    if (el.projectNameSaveBtn) {
      el.projectNameSaveBtn.disabled = saving;
    }
    if (el.projectNameCancelBtn) {
      el.projectNameCancelBtn.disabled = saving;
    }
    el.projectNameInput.disabled = saving;
  };
  try {
    setSaving(true);
    const updated = await api(`/projects/${state.project.id}`, {
      method: "PUT",
      body: JSON.stringify({
        name,
        description: state.project.description || "",
        activeModelIds: state.project.activeModelIds || {},
      }),
    });
    state.project = {
      ...state.project,
      ...updated,
    };
    if (el.projectLabel) {
      el.projectLabel.textContent = updated?.name || name;
    }
    hideProjectNameDialog();
    setStatus(`Project renamed to "${updated?.name || name}"`);
  } catch (error) {
    if (el.projectNameError) {
      el.projectNameError.textContent = formatUserError(error);
      el.projectNameError.classList.remove("hidden");
    }
  } finally {
    setSaving(false);
  }
}

// ── Project list ──────────────────────────────────────────────────────────────

export async function refreshProjectList() {
  try {
    const projects = await api("/projects");
    el.projectList.innerHTML = "";
    if (!projects.length) {
      el.projectList.innerHTML = `<div class="project-list-empty">No projects yet. Create one →</div>`;
      return;
    }
    projects.forEach((p) => {
      const projectId = resolveProjectId(p);
      const item = document.createElement("div");
      item.className = "project-item";
      if (projectId) {
        item.setAttribute("data-project-id", String(projectId));
      }
      const createdDate = p.createdAt ? new Date(p.createdAt) : null;
      const date =
        createdDate && !Number.isNaN(createdDate.getTime()) ? createdDate.toLocaleDateString() : "";
      item.innerHTML = `
        <span class="project-item-icon">📁</span>
        <div class="project-item-info">
          <div class="project-item-name">${escapeHtml(p.name)}</div>
          <div class="project-item-desc">${escapeHtml(p.description || "")}</div>
        </div>
        <span class="project-item-date">${escapeHtml(date)}</span>`;
      el.projectList.appendChild(item);
    });
  } catch (err) {
    el.projectList.innerHTML = `<div class="project-list-empty">Could not load projects: ${escapeHtml(
      err.message,
    )}</div>`;
  }
}

// ── Create project ────────────────────────────────────────────────────────────

export async function createProject() {
  const name = el.newProjectName.value.trim();
  if (!name) {
    el.newProjectName.focus();
    return;
  }
  try {
    setBusy("Creating project…");
    const project = await api("/projects", {
      method: "POST",
      body: JSON.stringify({ name, description: el.newProjectDesc.value.trim() }),
    });
    el.newProjectName.value = "";
    el.newProjectDesc.value = "";
    await loadProject(project);
  } catch (err) {
    setError(`Failed to create project: ${err.message}`);
  }
}

// ── Load project ──────────────────────────────────────────────────────────────

export async function loadProject(project) {
  const projectId = resolveProjectId(project);
  if (!projectId) {
    setError("Failed to load project: invalid project data");
    return;
  }
  const normalizedProject = {
    ...project,
    id: projectId,
    ownerUserId: project?.ownerUserId || project?.ownerId || null,
    activeModelIds: project?.activeModelIds || {},
  };
  const projectName = project?.name || "Unnamed project";
  setBusy(`Loading project "${projectName}"…`);
  try {
    state.project = normalizedProject;
    saveLastProjectId(projectId);
    clearArtifactState();
    resetChatForProjectChange();

    if (el.projectLabel) {
      el.projectLabel.textContent = projectName;
    }

    const typeKeys = modelingLevelKeys();
    const recordsByType = {};
    await Promise.all(
      typeKeys.map(async (type) => {
        try {
          const records = await api(`/${MODEL_TYPES[type].apiType}?projectId=${projectId}`);
          state.modelsCache[type] = records;
          recordsByType[type] = records;
        } catch (error) {
          state.modelsCache[type] = [];
          recordsByType[type] = [];
          console.warn(
            `Failed to load ${type.toUpperCase()} models for project ${projectId}.`,
            error,
          );
        }
      }),
    );

    // Reset all tab states
    for (const type of typeKeys) {
      resetModelingTab(type);
    }

    // Load active models for each tab
    for (const type of typeKeys) {
      // Support legacy projects that stored active-model keys as uppercase tab types.
      const activeModelId =
        normalizedProject.activeModelIds?.[type] ||
        normalizedProject.activeModelIds?.[type.toUpperCase()];
      const fallbackModelId = state.modelingConfig.config?.levels?.[type]?.fallbackActiveModel
        ? recordsByType[type]?.[0]?.id || null
        : null;
      const modelIdToLoad = activeModelId || fallbackModelId;
      if (modelIdToLoad) {
        try {
          const record = await api(`/${MODEL_TYPES[type].apiType}/${modelIdToLoad}`);
          state.tabs[type] = {
            modelId: record.id,
            modelRevision: Number(record.revision) || 1,
            baseModel: structuredClone(record.modelJson),
            diagram: toDiagram(type, record.modelJson, record.name),
            modelName: record.name || `${type}-model`,
            graph: null,
            views: null,
            fragments: null,
            activeViewId: null,
            dirty: false,
          };
        } catch (_) {
          // model not found or deleted – leave blank
        }
      }
    }

    // Apply default modeling tab state
    const defaultLevel = defaultModelingLevel();
    state.activeType = defaultLevel;
    state.modelId = state.tabs[defaultLevel]?.modelId || null;
    state.modelRevision = state.tabs[defaultLevel]?.modelRevision || 0;
    state.baseModel = state.tabs[defaultLevel]?.baseModel || null;
    state.diagram = state.tabs[defaultLevel]?.diagram || emptyDiagram(defaultLevel);
    state.boundedContextCreateMode = false;
    state.boundedContextDraftNodeIds = new Set();
    state.boundedContextDraftName = "";
    state.boundedContextViewMode = "normal";
    state.activeBoundedContextName = "";
    restoreTabGraphState(defaultLevel);
    materializeActiveView();

    Array.from(el.modelTabs.querySelectorAll(".tab")).forEach((t) =>
      t.classList.toggle("active", t.dataset.type === defaultLevel),
    );
    const topbar = document.querySelector(".topbar");
    topbar?.classList.remove("artifact-mode");
    el.workspace?.classList.remove("artifact-mode");
    el.modelingPanel.classList.remove("hidden");
    el.artifactPanel.classList.add("hidden");
    el.canvasViewport.classList.remove("hidden");
    el.artifactEditor.classList.add("hidden");
    el.impactToggleBtn.disabled = false;
    updateGenerateButtonState();

    renderPalette();
    renderDiagram();
    renderViewWorkbench();
    resetModelSaveState();
    resetCanvasView();
    hideProjectDialog();
    setStatus(`Project "${projectName}" loaded`);
    updateModelSaveUi();
  } catch (error) {
    setError(error, { prefix: "Failed to load project." });
  }
}

export async function restoreLastProjectIfPossible() {
  const lastProjectId = readLastProjectId();
  if (!lastProjectId) {
    return false;
  }
  try {
    const project = await api(`/projects/${lastProjectId}`);
    await loadProject(project);
    return true;
  } catch (error) {
    clearLastProjectId();
    console.warn(`Could not restore last project ${lastProjectId}.`, error);
    return false;
  }
}

export async function deleteCurrentProject() {
  if (!state.project?.id) {
    setStatus("No active project to delete.");
    return;
  }
  const projectId = state.project.id;
  const projectName = state.project.name || "this project";
  const confirmed = await confirmAction({
    title: "Delete Project",
    message: `Delete project "${projectName}"? This cannot be undone.`,
    confirmLabel: "Delete Project",
    danger: true,
  });
  if (!confirmed) {
    return;
  }

  try {
    await api(`/projects/${projectId}`, { method: "DELETE" });
    state.project = null;
    clearArtifactState();
    state.modelId = null;
    state.modelRevision = 0;
    state.baseModel = null;
    for (const type of modelingLevelKeys()) {
      resetModelingTab(type);
    }
    const defaultLevel = defaultModelingLevel();
    state.activeType = defaultLevel;
    state.diagram = state.tabs[defaultLevel]?.diagram || emptyDiagram(defaultLevel);
    state.selectedNodeId = null;
    state.selectedConnectionId = null;
    state.boundedContextCreateMode = false;
    state.boundedContextDraftNodeIds = new Set();
    state.boundedContextDraftName = "";
    state.boundedContextViewMode = "normal";
    state.activeBoundedContextName = "";
    restoreTabGraphState(defaultLevel);
    materializeActiveView();
    if (el.projectLabel) {
      el.projectLabel.textContent = "No project";
    }
    if (String(readLastProjectId()) === String(projectId)) {
      clearLastProjectId();
    }
    renderPalette();
    renderDiagram();
    renderViewWorkbench();
    resetModelSaveState();
    resetCanvasView();
    await showProjectDialog();
    setStatus(`Deleted project "${projectName}"`);
  } catch (error) {
    setError(error, { prefix: "Failed to delete project." });
  }
}

function filenameFromContentDisposition(contentDisposition, fallbackFilename) {
  const utf8Filename = contentDisposition.match(/filename\*=UTF-8''([^;]+)/i);
  if (utf8Filename) {
    return decodeURIComponent(utf8Filename[1]);
  }
  const basicFilename = contentDisposition.match(/filename="?([^";]+)"?/i);
  return basicFilename?.[1] || fallbackFilename;
}

export async function downloadCurrentProject() {
  if (!state.project?.id) {
    setStatus("Load a project first.");
    return;
  }
  const projectName = state.project.name || "project";
  const fallbackFilename = `${
    projectName.replaceAll(/[^A-Za-z0-9._-]+/g, "-").replaceAll(/^-+|-+$/g, "") || "project"
  }.zip`;
  try {
    const response = await fetch(apiUrl(`/projects/${state.project.id}/download`), {
      headers: apiAuthHeaders(),
    });
    if (!response.ok) {
      let message = `Download failed (${response.status})`;
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
    const blob = await response.blob();
    const filename = filenameFromContentDisposition(
      response.headers.get("content-disposition") || "",
      fallbackFilename,
    );
    const objectUrl = URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.href = objectUrl;
    link.download = filename;
    link.click();
    URL.revokeObjectURL(objectUrl);
    setStatus(`Downloaded project: ${filename}`);
  } catch (error) {
    setError(error, { prefix: "Project download failed." });
  }
}

// ── Update active model IDs on project ───────────────────────────────────────

export async function updateProjectActiveModel(type, modelId) {
  if (!state.project || !modelId) {
    return;
  }
  try {
    const activeModelIds = Object.assign({}, state.project.activeModelIds, {
      [type]: String(modelId),
    });
    const updated = await api(`/projects/${state.project.id}`, {
      method: "PUT",
      body: JSON.stringify({
        name: state.project.name,
        description: state.project.description || "",
        activeModelIds,
      }),
    });
    state.project = updated;
  } catch (_) {
    // non-critical
  }
}
