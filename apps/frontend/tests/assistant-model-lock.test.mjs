import assert from "node:assert/strict";
import { test } from "node:test";

const listeners = new Map();
const elements = new Map();
function element() {
  return {
    inert: false, disabled: false, hidden: false,
    classList: { add() {}, remove() {}, toggle() {} },
    setAttribute() {},
    parentElement: { appendChild(node) { elements.set(node.id, node); } },
  };
}
globalThis.CustomEvent ??= class { constructor(type, options) { this.type = type; this.detail = options.detail; } };
globalThis.window = {
  location: { origin: "http://localhost" },
  addEventListener(type, listener) {
    if (!listeners.has(type)) listeners.set(type, []);
    listeners.get(type).push(listener);
  },
  dispatchEvent(event) { for (const listener of listeners.get(event.type) || []) listener(event); },
};
globalThis.document = {
  getElementById: (id) => elements.get(id) || null,
  addEventListener() {}, createElement: element,
};
const { state } = await import("../js/state.js");
const { applyModelingRuntimeConfig } = await import("../js/config.js");
const { initializeModelingRuntimeState } = await import("../js/modeling-config-data.js");
const { setAssistantModelLocked, isAssistantModelLocked } = await import("../js/assistant-model-lock.js");
const { markModelDirty, updateModelSaveUi, resetModelSaveState } = await import("../js/model-save-ui.js");
const { saveCurrentModel, undoLastEdit, importActiveModel, switchTab } = await import("../js/model-ops.js");
const { applyAttributePanel, deleteSelection, deleteSelectedConnection } = await import("../js/attr-panel.js");
const { addConnection } = await import("../js/canvas.js");

test("assistant ownership blocks saves, mutations, shortcuts and workspace input until released", async () => {
  const config = { levelOrder: ["cim"], levels: { cim: { apiType: "cim", elements: [] } } };
  initializeModelingRuntimeState(config);
  state.modelingConfig.config = config;
  applyModelingRuntimeConfig(config);
  state.activeType = "cim";
  state.modelId = "model";
  state.tabs = { cim: {} };
  for (const id of ["saveModelBtn", "mobileDockSaveBtn", "canvasViewport", "attributePanel", "palettePane", "modelTabs"]) {
    elements.set(id, element());
  }
  markModelDirty();
  let requests = 0;
  globalThis.fetch = async () => { requests++; throw new Error("No writes are allowed"); };
  setAssistantModelLocked(true);
  updateModelSaveUi();
  assert.equal(isAssistantModelLocked(), true);
  assert.equal(elements.get("saveModelBtn").disabled, true);
  assert.equal(elements.get("mobileDockSaveBtn").disabled, true);
  assert.equal(elements.get("canvasViewport").inert, true);
  assert.equal(elements.get("attributePanel").inert, true);

  await assert.rejects(saveCurrentModel({ rethrow: true, force: true }), /assistant is updating/i);
  await assert.rejects(saveCurrentModel({ rethrow: true, quiet: true }), /assistant is updating/i);
  const before = structuredClone(state.diagram);
  applyAttributePanel();
  await deleteSelection();
  await deleteSelectedConnection();
  await undoLastEdit();
  await importActiveModel({});
  await switchTab("pim");
  assert.equal(addConnection("a", "b"), false);
  assert.deepEqual(state.diagram, before);
  assert.equal(state.activeType, "cim");
  assert.equal(requests, 0);

  for (const event of [
    { type: "keydown", ctrlKey: true, key: "s", target: { closest: () => null } },
    { type: "drop", target: { closest: () => elements.get("canvasViewport") } },
    { type: "beforeinput", target: { closest: () => elements.get("attributePanel") } },
  ]) {
    let blocked = false;
    window.dispatchEvent({ ...event, preventDefault() { blocked = true; }, stopImmediatePropagation() {} });
    assert.equal(blocked, true);
  }

  // Reconnecting to a local chat must not release a lock observed in another tab.
  setAssistantModelLocked(true, "remote");
  setAssistantModelLocked(false);
  assert.equal(isAssistantModelLocked(), true);
  resetModelSaveState();
  assert.equal(elements.get("saveModelBtn").disabled, true);
  setAssistantModelLocked(false, "remote");
  assert.equal(isAssistantModelLocked(), false);
  assert.equal(elements.get("canvasViewport").inert, false);
  assert.equal(elements.get("attributePanel").inert, false);
  assert.equal(elements.get("saveModelBtn").disabled, false);
  assert.equal(elements.get("mobileDockSaveBtn").disabled, false);
});
