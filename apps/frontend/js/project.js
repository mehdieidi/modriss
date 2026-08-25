import { state } from "./state.js";
import { el } from "./dom.js";
import { api, apiAuthHeaders } from "./api.js";
import { setBusy, setError, setStatus } from "./status.js";
import { formatUserError } from "./errors.js";
import { emptyDiagram, escapeHtml } from "./utils.js";
import { toDiagram } from "./diagram.js";
import {
  fitViewportToDiagram,
  renderDiagramAsync,
  renderPalette,
  resetCanvasView,
} from "./canvas.js";
import { updateGenerateButtonState } from "./model-ops.js";
import { renderViewWorkbench } from "./view-explorer.js";
import { restoreTabGraphState } from "./graph-store.js";
import { materializeActiveView } from "./view-materializer.js";
import { clearArtifactState } from "./artifact.js";
import { resetChatForProjectChange } from "./chat.js?v=chat-provenance-ui-20260718a";
import { apiUrl, MODEL_TYPES } from "./config.js";
import { confirmAction } from "./confirm-action.js";
import { resetModelSaveState, updateModelSaveUi } from "./model-save-ui.js";
import { defaultModelingLevel, modelingLevelKeys } from "./modeling-config-data.js";

export { requireActiveProject } from "./project-guards.js";

function resolveProjectId(project) {
  return project?.id || project?.projectId || project?.uuid || null;
}
const getElementTarget = (event) => (event.target instanceof Element ? event.target : null);
const LAST_PROJECT_STORAGE_PREFIX = "varka.lastProjectId";

function defaultModelName(typeKey) {
  const configured = state.modelingConfig.config?.levels?.[typeKey]?.modelNameTemplate;
  if (!configured) throw new Error(`Modeling config is missing a model name template for '${typeKey}'.`);
  return configured;
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
    el.projectMembersCloseBtn &&
    el.projectMembersCloseBtn.dataset.boundProjectMembersClose !== "true"
  ) {
    el.projectMembersCloseBtn.dataset.boundProjectMembersClose = "true";
    el.projectMembersCloseBtn.addEventListener("click", hideProjectMembersDialog);
  }

  if (
    el.projectMembersOverlay &&
    el.projectMembersOverlay.dataset.boundProjectMembersOverlay !== "true"
  ) {
    el.projectMembersOverlay.dataset.boundProjectMembersOverlay = "true";
    el.projectMembersOverlay.addEventListener("click", (event) => {
      if (event.target === el.projectMembersOverlay) {
        hideProjectMembersDialog();
      }
    });
  }

  if (
    el.projectMemberInviteBtn &&
    el.projectMemberInviteBtn.dataset.boundProjectMemberInvite !== "true"
  ) {
    el.projectMemberInviteBtn.dataset.boundProjectMemberInvite = "true";
    el.projectMemberInviteBtn.addEventListener("click", inviteProjectMember);
  }

  if (
    el.projectMemberRoleInput &&
    el.projectMemberRoleInput.dataset.boundProjectMemberRoleEnter !== "true"
  ) {
    el.projectMemberRoleInput.dataset.boundProjectMemberRoleEnter = "true";
    el.projectMemberRoleInput.addEventListener("keydown", (event) => {
      if (event.key === "Enter") {
        event.preventDefault();
        inviteProjectMember();
      }
    });
  }

  if (
    el.projectMemberEmailInput &&
    el.projectMemberEmailInput.dataset.boundProjectMemberEmailEnter !== "true"
  ) {
    el.projectMemberEmailInput.dataset.boundProjectMemberEmailEnter = "true";
    el.projectMemberEmailInput.addEventListener("keydown", (event) => {
      if (event.key === "Enter") {
        event.preventDefault();
        inviteProjectMember();
      }
    });
  }

  if (el.projectMembersList && el.projectMembersList.dataset.boundProjectMemberActions !== "true") {
    el.projectMembersList.dataset.boundProjectMemberActions = "true";
    el.projectMembersList.addEventListener("click", handleProjectMemberListClick);
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

function currentUserId() {
  return state.auth?.user?.id || "";
}

function isCurrentProjectOwner() {
  return Boolean(state.project?.ownerUserId && state.project.ownerUserId === currentUserId());
}

function clearProjectMembersMessages() {
  for (const node of [el.projectMembersError, el.projectMembersSuccess]) {
    if (!node) {
      continue;
    }
    node.textContent = "";
    node.classList.add("hidden");
  }
}

function showProjectMembersError(error) {
  if (!el.projectMembersError) {
    setError(error, { prefix: "Project access failed." });
    return;
  }
  el.projectMembersError.textContent = formatUserError(error);
  el.projectMembersError.classList.remove("hidden");
  el.projectMembersSuccess?.classList.add("hidden");
}

function showProjectMembersSuccess(message) {
  if (!el.projectMembersSuccess) {
    setStatus(message);
    return;
  }
  el.projectMembersSuccess.textContent = message;
  el.projectMembersSuccess.classList.remove("hidden");
  el.projectMembersError?.classList.add("hidden");
}

function setProjectMembersBusy(busy) {
  if (el.projectMemberInviteBtn) {
    el.projectMemberInviteBtn.disabled = busy || !isCurrentProjectOwner();
  }
  if (el.projectMemberEmailInput) {
    el.projectMemberEmailInput.disabled = busy || !isCurrentProjectOwner();
  }
  if (el.projectMemberRoleInput) {
    el.projectMemberRoleInput.disabled = busy || !isCurrentProjectOwner();
  }
  el.projectMembersList?.querySelectorAll("button, input").forEach((node) => {
    node.disabled = busy || node.dataset.locked === "true";
  });
}

async function refreshProjectMembers() {
  if (!state.project?.id || !el.projectMembersList) {
    return;
  }
  const members = await api(`/projects/${state.project.id}/members`);
  state.project = {
    ...state.project,
    members,
  };
  renderProjectMembers(members);
}

function renderProjectMembers(members = []) {
  if (!el.projectMembersList) {
    return;
  }
  const owner = isCurrentProjectOwner();
  if (el.projectMembersSubtitle) {
    el.projectMembersSubtitle.textContent = owner
      ? state.project?.name || ""
      : "Only the project owner can manage access.";
  }
  if (el.projectMemberInviteForm) {
    el.projectMemberInviteForm.classList.toggle("hidden", !owner);
  }
  if (!members.length) {
    el.projectMembersList.innerHTML = `<div class="project-member-empty">No members found.</div>`;
    return;
  }
  el.projectMembersList.innerHTML = members
    .map((member) => {
      const userId = String(member.userId || "");
      const isProjectOwner = state.project?.ownerUserId === userId;
      const canManageMember = owner && !isProjectOwner;
      const displayName = member.displayName || member.email || "User";
      const role = member.role || (isProjectOwner ? "OWNER" : "");
      const roleControl = isProjectOwner
        ? `<span class="project-member-owner-role">${escapeHtml(role)}</span>`
        : `<input class="project-input project-member-role" data-member-role-input="${escapeHtml(
            userId,
          )}" ${canManageMember ? "" : 'data-locked="true" disabled'} maxlength="48" value="${escapeHtml(
            role,
          )}" type="text" />`;
      const saveButton = canManageMember
        ? `<button class="btn btn-secondary" data-member-action="save-role" data-user-id="${escapeHtml(
            userId,
          )}" type="button">Save</button>`
        : "";
      const removeButton = canManageMember
        ? `<button class="btn btn-danger" data-member-action="remove" data-user-id="${escapeHtml(
            userId,
          )}" type="button">Remove</button>`
        : "";
      return `
        <div class="project-member-row" data-project-member-id="${escapeHtml(userId)}">
          <div class="project-member-identity">
            <div class="project-member-name">${escapeHtml(displayName)}</div>
            <div class="project-member-email">${escapeHtml(member.email || "")}</div>
          </div>
          ${roleControl}
          ${saveButton}
          ${removeButton}
        </div>`;
    })
    .join("");
}

export async function showProjectMembersDialog() {
  if (!state.project?.id) {
    setStatus("Load a project first.");
    return;
  }
  if (!el.projectMembersOverlay || !el.projectMembersList) {
    setError("Project access editor is unavailable");
    return;
  }
  clearProjectMembersMessages();
  el.projectMembersOverlay.classList.remove("hidden");
  el.projectMembersOverlay.setAttribute("aria-hidden", "false");
  document.body.classList.add("modal-open");
  el.projectMembersList.innerHTML = `<div class="project-member-empty">Loading...</div>`;
  try {
    await refreshProjectMembers();
    if (isCurrentProjectOwner()) {
      el.projectMemberEmailInput?.focus();
    }
  } catch (error) {
    showProjectMembersError(error);
  }
}

export function hideProjectMembersDialog() {
  if (!el.projectMembersOverlay) {
    return;
  }
  el.projectMembersOverlay.classList.add("hidden");
  el.projectMembersOverlay.setAttribute("aria-hidden", "true");
  document.body.classList.remove("modal-open");
}

async function inviteProjectMember() {
  if (!state.project?.id || !isCurrentProjectOwner()) {
    return;
  }
  const email = el.projectMemberEmailInput?.value.trim() || "";
  const role = el.projectMemberRoleInput?.value.trim() || "";
  if (!email) {
    el.projectMemberEmailInput?.focus();
    return;
  }
  if (!role) {
    el.projectMemberRoleInput?.focus();
    return;
  }
  try {
    setProjectMembersBusy(true);
    const member = await api(`/projects/${state.project.id}/invite`, {
      method: "POST",
      body: JSON.stringify({ email, role }),
    });
    if (el.projectMemberEmailInput) {
      el.projectMemberEmailInput.value = "";
    }
    if (el.projectMemberRoleInput) {
      el.projectMemberRoleInput.value = "";
    }
    await refreshProjectMembers();
    showProjectMembersSuccess(`${member.email || email} was added to the project.`);
  } catch (error) {
    showProjectMembersError(error);
  } finally {
    setProjectMembersBusy(false);
  }
}

async function handleProjectMemberListClick(event) {
  const target = getElementTarget(event);
  const button = target?.closest("button[data-member-action]");
  if (!button || !state.project?.id || !isCurrentProjectOwner()) {
    return;
  }
  const userId = button.getAttribute("data-user-id") || "";
  if (!userId) {
    return;
  }
  const action = button.getAttribute("data-member-action");
  if (action === "save-role") {
    await updateProjectMemberRole(userId);
  } else if (action === "remove") {
    await removeProjectMember(userId);
  }
}

async function updateProjectMemberRole(userId) {
  const input = el.projectMembersList?.querySelector(
    `[data-member-role-input="${CSS.escape(userId)}"]`,
  );
  const role = input?.value.trim() || "";
  if (!role) {
    input?.focus();
    return;
  }
  try {
    setProjectMembersBusy(true);
    const member = await api(
      `/projects/${state.project.id}/members/${encodeURIComponent(userId)}`,
      {
        method: "PUT",
        body: JSON.stringify({ role }),
      },
    );
    await refreshProjectMembers();
    showProjectMembersSuccess(`${member.email || "Member"} role updated.`);
  } catch (error) {
    showProjectMembersError(error);
  } finally {
    setProjectMembersBusy(false);
  }
}

async function removeProjectMember(userId) {
  const member = state.project?.members?.find((candidate) => candidate.userId === userId);
  const label = member?.email || member?.displayName || "this member";
  const confirmed = await confirmAction({
    title: "Remove Access",
    message: `Remove ${label} from "${state.project?.name || "this project"}"?`,
    confirmLabel: "Remove",
    danger: true,
  });
  if (!confirmed) {
    return;
  }
  try {
    setProjectMembersBusy(true);
    await api(`/projects/${state.project.id}/members/${encodeURIComponent(userId)}`, {
      method: "DELETE",
    });
    await refreshProjectMembers();
    showProjectMembersSuccess(`${label} was removed from the project.`);
  } catch (error) {
    showProjectMembersError(error);
  } finally {
    setProjectMembersBusy(false);
  }
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
    if (el.deleteProjectBtn) {
      el.deleteProjectBtn.disabled = !isCurrentProjectOwner();
      el.deleteProjectBtn.title = isCurrentProjectOwner()
        ? "Delete current project"
        : "Only the project owner can delete this project";
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
      const modelIdToLoad = activeModelId;
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
    let diagramWarning = false;
    try {
      await renderDiagramAsync();
      await fitViewportToDiagram({ fit: true });
    } catch (error) {
      diagramWarning = true;
      console.warn("Diagram renderer failed after project load.", error);
    }
    renderViewWorkbench();
    resetModelSaveState();
    hideProjectDialog();
    setStatus(
      diagramWarning
        ? `Project "${projectName}" loaded (diagram renderer unavailable).`
        : `Project "${projectName}" loaded`,
    );
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
  if (!isCurrentProjectOwner()) {
    setStatus("Only the project owner can delete this project.");
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
    restoreTabGraphState(defaultLevel);
    materializeActiveView();
    if (el.projectLabel) {
      el.projectLabel.textContent = "No project";
    }
    if (String(readLastProjectId()) === String(projectId)) {
      clearLastProjectId();
    }
    renderPalette();
    await renderDiagramAsync();
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
