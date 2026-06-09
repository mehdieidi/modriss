import {api, isPlannedFeatureError} from './api.js';
import {el} from './dom.js';
import {state} from './state.js';
import {escapeHtml} from './utils.js';
import {setError, setStatus} from './status.js';

function lineNumberText(lineCount) {
  return Array.from({length: lineCount}, (_, i) => String(i + 1)).join("\n");
}

function joinPath(basePath, childName) {
  if (!basePath) {
    return childName;
  }
  return `${basePath}/${childName}`;
}

function parentPath(path) {
  if (!path) {
    return "";
  }
  const parts = path.split("/").filter(Boolean);
  if (!parts.length) {
    return "";
  }
  parts.pop();
  return parts.join("/");
}

function adminScope() {
  return state.admin.scope;
}

export function syncAdminEditorLineNumberScroll() {
  if (!el.adminEditorLineNumbers || !el.adminEditorContent) {
    return;
  }
  el.adminEditorLineNumbers.scrollTop = el.adminEditorContent.scrollTop;
}

export function refreshAdminEditorLineNumbers() {
  if (!el.adminEditorLineNumbers || !el.adminEditorContent) {
    return;
  }
  const lineCount = Math.max(1, el.adminEditorContent.value.split("\n").length);
  el.adminEditorLineNumbers.textContent = lineNumberText(lineCount);
  syncAdminEditorLineNumberScroll();
}

function resetAdminEditor() {
  state.admin.activeFile = null;
  state.admin.dirty = false;
  el.adminEditorFileName.textContent = "No file selected";
  el.adminEditorContent.value = "";
  refreshAdminEditorLineNumbers();
  el.adminSaveFileBtn.classList.add("hidden");
  el.adminSaveFileBtn.textContent = "Save File";
}

function setAdminPathLabel() {
  el.adminPathLabel.textContent = state.admin.currentPath
      ? `/${state.admin.currentPath}` : "/";
}

function renderAdminTree(entries) {
  el.adminTree.innerHTML = "";
  if (!entries.length) {
    el.adminTree.innerHTML = `<div class="project-list-empty">No files in this folder.</div>`;
    return;
  }

  entries.forEach((entry) => {
    const item = document.createElement("button");
    item.type = "button";
    item.className = "file-tree-item";
    item.dataset.path = entry.path;
    item.dataset.directory = String(entry.directory);
    item.textContent = entry.directory ? `${entry.name}/` : entry.name;
    item.title = entry.path;
    if (state.admin.selectedEntry?.path === entry.path) {
      item.classList.add("active");
    }
    item.addEventListener("click", () => selectAdminEntry(entry));
    item.addEventListener("dblclick", () => openAdminEntry(entry));
    el.adminTree.appendChild(item);
  });
}

async function loadEntries() {
  if (state.admin.unavailable) {
    return;
  }
  const scope = adminScope();
  if (!scope) {
    return;
  }
  const query = state.admin.currentPath
      ? `?scope=${encodeURIComponent(scope)}&path=${encodeURIComponent(
          state.admin.currentPath)}`
      : `?scope=${encodeURIComponent(scope)}`;
  const entries = await api(`/admin/workspace/entries${query}`);
  state.admin.entries = entries;
  state.admin.selectedEntry = null;
  renderAdminTree(entries);
  setAdminPathLabel();
}

function selectAdminEntry(entry) {
  state.admin.selectedEntry = entry;
  renderAdminTree(state.admin.entries);
}

async function openAdminEntry(entry) {
  if (entry.directory) {
    state.admin.currentPath = entry.path;
    resetAdminEditor();
    await reloadAdminEntries();
    return;
  }
  await openAdminFile(entry.path);
}

export async function openAdminFile(path) {
  if (state.admin.unavailable) {
    setStatus("Admin workspace is not available in this backend build.");
    return;
  }
  try {
    const scope = adminScope();
    if (!scope) {
      return;
    }
    const response = await api(
        `/admin/workspace/file?scope=${encodeURIComponent(
            scope)}&path=${encodeURIComponent(path)}`);
    state.admin.activeFile = response.path;
    state.admin.dirty = false;
    el.adminEditorFileName.textContent = response.path;
    el.adminEditorContent.value = response.content || "";
    refreshAdminEditorLineNumbers();
    el.adminSaveFileBtn.classList.remove("hidden");
    el.adminSaveFileBtn.textContent = "Save File";
    setStatus(`Opened: ${response.path}`);
  } catch (error) {
    setError(`Open failed: ${error.message}`);
  }
}

export async function saveAdminFile() {
  if (state.admin.unavailable) {
    setStatus("Admin workspace is not available in this backend build.");
    return;
  }
  if (!state.admin.activeFile || !adminScope()) {
    setStatus("No file selected");
    return;
  }
  try {
    await api("/admin/workspace/file", {
      method: "PUT",
      body: JSON.stringify({
        scope: adminScope(),
        path: state.admin.activeFile,
        content: el.adminEditorContent.value
      })
    });
    state.admin.dirty = false;
    el.adminSaveFileBtn.textContent = "Save File";
    setStatus(`Saved: ${state.admin.activeFile}`);
  } catch (error) {
    setError(`Save failed: ${error.message}`);
  }
}

export async function reloadAdminEntries() {
  if (state.admin.unavailable) {
    return;
  }
  try {
    await loadEntries();
    setStatus("Admin workspace refreshed");
  } catch (error) {
    setError(`Reload failed: ${error.message}`);
  }
}

export async function initAdminWorkspace() {
  state.admin.unavailable = false;
  try {
    if (!state.admin.scopes.length) {
      state.admin.scopes = await api("/admin/workspace/scopes");
      el.adminScopeSelect.innerHTML = state.admin.scopes
      .map((scope) => `<option value="${escapeHtml(scope.key)}">${escapeHtml(
          scope.label)}</option>`)
      .join("");
      state.admin.scope = state.admin.scopes[0]?.key || null;
      if (state.admin.scope) {
        el.adminScopeSelect.value = state.admin.scope;
      }
    }
    resetAdminEditor();
    state.admin.currentPath = "";
    await loadEntries();
  } catch (error) {
    if (isPlannedFeatureError(error)) {
      state.admin.unavailable = true;
      state.admin.scopes = [];
      state.admin.scope = null;
      resetAdminEditor();
      el.adminScopeSelect.innerHTML = "";
      el.adminTree.innerHTML = `<div class="project-list-empty">Admin workspace is not available in this backend build.</div>`;
      setStatus("Admin workspace is not available in this backend build.");
      return;
    }
    setError(`Admin init failed: ${error.message}`);
  }
}

export async function changeAdminScope() {
  if (state.admin.unavailable) {
    setStatus("Admin workspace is not available in this backend build.");
    return;
  }
  state.admin.scope = el.adminScopeSelect.value || null;
  state.admin.currentPath = "";
  resetAdminEditor();
  await reloadAdminEntries();
}

export async function navigateAdminUp() {
  if (state.admin.unavailable) {
    return;
  }
  state.admin.currentPath = parentPath(state.admin.currentPath);
  resetAdminEditor();
  await reloadAdminEntries();
}

export async function createAdminFile() {
  if (state.admin.unavailable) {
    setStatus("Admin workspace is not available in this backend build.");
    return;
  }
  const fileName = window.prompt("New file name (relative to current folder):",
      "new-file");
  if (!fileName) {
    return;
  }
  const path = joinPath(state.admin.currentPath, fileName.trim());
  if (!path) {
    return;
  }
  try {
    await api("/admin/workspace/file", {
      method: "POST",
      body: JSON.stringify({scope: adminScope(), path, content: ""})
    });
    await reloadAdminEntries();
    await openAdminFile(path);
  } catch (error) {
    setError(`Create file failed: ${error.message}`);
  }
}

export async function createAdminDirectory() {
  if (state.admin.unavailable) {
    setStatus("Admin workspace is not available in this backend build.");
    return;
  }
  const folderName = window.prompt(
      "New folder name (relative to current folder):", "new-folder");
  if (!folderName) {
    return;
  }
  const path = joinPath(state.admin.currentPath, folderName.trim());
  if (!path) {
    return;
  }
  try {
    await api("/admin/workspace/directory", {
      method: "POST",
      body: JSON.stringify({scope: adminScope(), path})
    });
    await reloadAdminEntries();
  } catch (error) {
    setError(`Create folder failed: ${error.message}`);
  }
}

export async function renameAdminEntry() {
  if (state.admin.unavailable) {
    setStatus("Admin workspace is not available in this backend build.");
    return;
  }
  if (!state.admin.selectedEntry) {
    setStatus("Select a file or folder first");
    return;
  }
  const currentPath = state.admin.selectedEntry.path;
  const suggestion = state.admin.selectedEntry.path.split("/").pop();
  const newName = window.prompt("New name:", suggestion);
  if (!newName) {
    return;
  }
  const destination = joinPath(parentPath(currentPath), newName.trim());
  if (!destination || destination === currentPath) {
    return;
  }
  try {
    await api("/admin/workspace/rename", {
      method: "POST",
      body: JSON.stringify(
          {scope: adminScope(), path: currentPath, newPath: destination})
    });
    if (state.admin.activeFile === currentPath) {
      state.admin.activeFile = destination;
      el.adminEditorFileName.textContent = destination;
    }
    await reloadAdminEntries();
  } catch (error) {
    setError(`Rename failed: ${error.message}`);
  }
}

export async function deleteAdminEntry() {
  if (state.admin.unavailable) {
    setStatus("Admin workspace is not available in this backend build.");
    return;
  }
  if (!state.admin.selectedEntry) {
    setStatus("Select a file or folder first");
    return;
  }
  const target = state.admin.selectedEntry.path;
  if (!window.confirm(`Delete "${target}"?`)) {
    return;
  }
  try {
    await api(`/admin/workspace/path?scope=${encodeURIComponent(
        adminScope())}&path=${encodeURIComponent(target)}`, {
      method: "DELETE"
    });
    if (state.admin.activeFile === target) {
      resetAdminEditor();
    }
    await reloadAdminEntries();
  } catch (error) {
    setError(`Delete failed: ${error.message}`);
  }
}
