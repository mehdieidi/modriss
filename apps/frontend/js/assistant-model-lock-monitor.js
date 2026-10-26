import { api } from "./api.js";
import { state } from "./state.js";
import { hasAssistantModelLockOwner, setAssistantModelLocked } from "./assistant-model-lock.js";
import { isModelingLevel } from "./modeling-config-data.js";
import { MODEL_TYPES } from "./config.js";
import { customPsmActive } from "./cloud-providers.js";

let started = false;
let observedModel = null;

export async function refreshAssistantModelLock() {
  if (hasAssistantModelLockOwner("chat")) return;
  const typeKey = state.activeType;
  const modelId = state.modelId;
  if (!modelId || !isModelingLevel(typeKey) || !state.project?.id || customPsmActive()) {
    observedModel = null;
    setAssistantModelLocked(false, "remote");
    return;
  }
  const key = `${typeKey}:${modelId}`;
  const result = await api(`/${MODEL_TYPES[typeKey].apiType}/${modelId}/editing-lock`);
  if (
    state.activeType !== typeKey ||
    state.modelId !== modelId ||
    hasAssistantModelLockOwner("chat")
  )
    return;
  if (result?.locked) {
    observedModel = key;
    setAssistantModelLocked(true, "remote");
  } else if (observedModel === key) {
    // Discard drafts from another tab and show the persisted assistant result before unlocking.
    const { isModelLoadInFlight, loadModelById } = await import("./model-ops.js");
    if (isModelLoadInFlight()) return;
    await loadModelById(typeKey, modelId, {
      preserveActiveView: true,
      preserveViewport: true,
      autoLayout: false,
      assistantResult: true,
    });
    observedModel = null;
    setAssistantModelLocked(false, "remote");
  }
}

export function startAssistantModelLockMonitor() {
  if (started) return;
  started = true;
  const poll = async () => {
    try {
      await refreshAssistantModelLock();
    } catch {
      // A disconnect must not release an observed lock or preserve drafts over the final result.
    } finally {
      window.setTimeout(poll, 1000);
    }
  };
  void poll();
}
