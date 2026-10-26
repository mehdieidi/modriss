import { state } from "./state.js";

export const ASSISTANT_MODEL_LOCK_EVENT = "modriss-assistant-model-lock-change";
export const ASSISTANT_MODEL_LOCK_MESSAGE =
  "The assistant is updating the model. Editing and saving will resume when its results are on the canvas.";

// Keep the conversation and Stop button usable while the model workspace is frozen.
const LOCKED_REGIONS = [
  "canvasViewport",
  "palettePane",
  "attributePanel",
  "modelTreePanel",
  "modelWorkbenchPanel",
  "modelTabs",
  "mobileModelLevelTabs",
  "projectRailMenu",
  "importModelSubmenu",
  "generateContextBtn",
  "undoModelReplaceBtn",
  "importModelBtn",
  "psmPlatformsBtn",
  "chatNewBtn",
  "chatHistoryBtn",
  "chatHistoryPanel",
  "fileRailMenu",
  "validateModelBtn",
];
const previousInert = new Map();
const owners = new Set();
let guardsInstalled = false;

export function isAssistantModelLocked() {
  return Boolean(state.assistantModelLocked);
}

export function setAssistantModelLocked(locked, owner = "chat") {
  if (locked) owners.add(owner);
  else owners.delete(owner);
  const next = owners.size > 0;
  if (next === isAssistantModelLocked()) return;
  state.assistantModelLocked = next;
  for (const id of LOCKED_REGIONS) {
    const region = document.getElementById(id);
    if (!region) continue;
    if (next) {
      previousInert.set(region, region.inert);
      region.inert = true;
    }
  }
  if (!next) {
    for (const [region, inert] of previousInert) region.inert = inert;
    previousInert.clear();
  }
  let notice = document.getElementById("assistantModelLockNotice");
  if (next && !notice) {
    notice = document.createElement("div");
    notice.id = "assistantModelLockNotice";
    notice.className = "assistant-model-lock-notice";
    notice.setAttribute("role", "status");
    notice.textContent = ASSISTANT_MODEL_LOCK_MESSAGE;
    document.getElementById("canvasViewport")?.parentElement?.appendChild(notice);
  }
  if (notice) notice.hidden = !next;
  installGuards();
  window.dispatchEvent(new CustomEvent(ASSISTANT_MODEL_LOCK_EVENT, { detail: { locked: next } }));
}

export function hasAssistantModelLockOwner(owner) {
  return owners.has(owner);
}

function installGuards() {
  if (guardsInstalled) return;
  guardsInstalled = true;
  const selector = LOCKED_REGIONS.map((id) => `#${id}`).join(",");
  const block = (event) => {
    if (!isAssistantModelLocked()) return;
    const target = event.target;
    const inChat = target?.closest?.("#chatWindow");
    const modelShortcut =
      event.type === "keydown" &&
      !inChat &&
      (((event.ctrlKey || event.metaKey) && ["s", "z", "y"].includes(event.key?.toLowerCase())) ||
        ["Delete", "Backspace"].includes(event.key));
    if (modelShortcut || target?.closest?.(selector)) {
      event.preventDefault();
      event.stopImmediatePropagation();
    }
  };
  for (const type of [
    "pointerdown",
    "click",
    "dblclick",
    "dragstart",
    "drop",
    "beforeinput",
    "input",
    "change",
    "keydown",
  ]) {
    window.addEventListener(type, block, true);
  }
}
