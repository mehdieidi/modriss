import {state} from './state.js';
import {saveCurrentModel} from './model-ops.js';
import {updateProjectActiveModel} from './project.js';
import {publishDiagramUpdate} from './collaboration.js';
import {flushCurrentModelPatch} from './model-patch.js';

const DEFAULT_AUTOSAVE_DELAY_MS = 700;
let autoSaveInFlight = null;
let autoSaveQueued = false;

export function scheduleAutoSave({delayMs = DEFAULT_AUTOSAVE_DELAY_MS} = {}) {
  if (!state.project || state.activeType === "artifact") {
    return;
  }
  clearTimeout(state.autoSaveTimer);
  const nextDelay = Number.isFinite(delayMs) ? Math.max(0, delayMs)
      : DEFAULT_AUTOSAVE_DELAY_MS;
  state.autoSaveTimer = setTimeout(() => autoSave(), nextDelay);
}

export async function flushAutoSave() {
  if (!state.project || state.activeType === "artifact") {
    return;
  }
  if (state.autoSaveTimer) {
    clearTimeout(state.autoSaveTimer);
    state.autoSaveTimer = null;
  }
  await autoSave();
}

async function autoSave() {
  if (autoSaveInFlight) {
    autoSaveQueued = true;
    return autoSaveInFlight;
  }
  autoSaveInFlight = performAutoSave().finally(() => {
    autoSaveInFlight = null;
    if (autoSaveQueued) {
      autoSaveQueued = false;
      scheduleAutoSave({delayMs: 250});
    }
  });
  return autoSaveInFlight;
}

async function performAutoSave() {
  if (!state.project || state.activeType === "artifact") {
    return;
  }
  if (state.inlineLabelEditNodeId) {
    const activeElement = document.activeElement;
    const isStillEditing = activeElement instanceof HTMLElement
        && activeElement.classList.contains("node-label")
        && activeElement.closest(
            `[data-node-id="${state.inlineLabelEditNodeId}"]`);
    if (!isStillEditing) {
      state.inlineLabelEditNodeId = null;
    }
  }
  if (state.inlineLabelEditNodeId) {
    clearTimeout(state.autoSaveTimer);
    state.autoSaveTimer = setTimeout(() => autoSave(), 350);
    return;
  }
  if (!state.diagram?.nodes?.length && !state.modelId) {
    return;
  }
  try {
    const previousModelId = state.modelId;
    if (state.modelId) {
      const patched = await flushCurrentModelPatch({
        name: state.tabs[state.activeType]?.modelName
            || `${state.activeType}-model`
      });
      if (!patched) {
        await saveCurrentModel({quiet: true});
      }
    } else {
      await saveCurrentModel({quiet: true});
    }
    await updateProjectActiveModel(state.activeType, state.modelId);
    if (state.modelId && state.modelId !== previousModelId) {
      publishDiagramUpdate({immediate: true});
    }
  } catch (_) {
    // silent auto-save failure
  }
}
