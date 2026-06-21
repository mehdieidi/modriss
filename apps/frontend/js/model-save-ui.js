import { state } from "./state.js";
import { isModelingLevel } from "./modeling-config-data.js";

function isModelingType(typeKey = state.activeType) {
  return isModelingLevel(typeKey);
}

function saveState() {
  state.modelSave ??= {
    dirty: false,
    saving: false,
    lastSavedAt: null,
    error: "",
  };
  return state.modelSave;
}

function activeTab() {
  return isModelingType() ? state.tabs[state.activeType] : null;
}

export function hasUnsavedModelChanges() {
  if (!isModelingType()) {
    return false;
  }
  const tab = activeTab();
  return Boolean(tab?.dirty || saveState().dirty);
}

export function updateModelSaveUi() {
  const current = saveState();
  const tab = activeTab();
  if (tab) {
    current.dirty = Boolean(tab.dirty);
  }
  const isModeling = isModelingType();
  const label = current.saving
    ? "Saving model"
    : current.dirty
      ? "Save model, unsaved changes"
      : "Save model";
  const saveButton = document.getElementById("saveModelBtn");
  const saveLabel = document.getElementById("saveModelBtnLabel");

  if (saveButton) {
    saveButton.disabled = !isModeling || current.saving;
    saveButton.classList.toggle("is-saving", current.saving);
    saveButton.classList.toggle("is-dirty", current.dirty && !current.saving);
    saveButton.classList.toggle("is-error", Boolean(current.error));
    saveButton.classList.toggle("hidden", !isModeling);
    saveButton.setAttribute("aria-label", label);
    saveButton.title = current.error || `${label} (Ctrl+S)`;
  }
  if (saveLabel) {
    saveLabel.textContent = "Save";
  }
}

export function markModelDirty() {
  if (!isModelingType()) {
    return;
  }
  const current = saveState();
  const tab = activeTab();
  current.dirty = true;
  if (tab) {
    tab.dirty = true;
  }
  current.error = "";
  updateModelSaveUi();
}

export function beginModelSave() {
  const current = saveState();
  current.saving = true;
  current.error = "";
  updateModelSaveUi();
}

export function completeModelSave() {
  const current = saveState();
  const tab = activeTab();
  current.dirty = false;
  if (tab) {
    tab.dirty = false;
  }
  current.saving = false;
  current.error = "";
  current.lastSavedAt = Date.now();
  updateModelSaveUi();
}

export function failModelSave(message = "Save failed") {
  const current = saveState();
  const tab = activeTab();
  current.dirty = true;
  if (tab) {
    tab.dirty = true;
  }
  current.saving = false;
  current.error = message;
  updateModelSaveUi();
}

export function resetModelSaveState({ dirty = false } = {}) {
  const current = saveState();
  const tab = activeTab();
  current.dirty = dirty;
  if (tab) {
    tab.dirty = dirty;
  }
  current.saving = false;
  current.error = "";
  current.lastSavedAt = dirty ? null : Date.now();
  updateModelSaveUi();
}
