import assert from "node:assert/strict";
import { beforeEach, test } from "node:test";

function domElement() {
  const classes = new Set();
  const children = [];
  return {
    children,
    dataset: {},
    style: {},
    classList: {
      add(...names) { names.forEach((name) => classes.add(name)); },
      remove(...names) { names.forEach((name) => classes.delete(name)); },
      toggle(name, force) {
        if (force === undefined ? !classes.has(name) : force) classes.add(name);
        else classes.delete(name);
      },
      contains(name) { return classes.has(name); },
    },
    setAttribute() {}, addEventListener() {},
    append(...items) { items.forEach((item) => this.appendChild(item)); },
    appendChild(item) { children.push(item); return item; },
    querySelectorAll() { return []; }, querySelector() { return null; },
    set innerHTML(value) { this._innerHTML = value; if (value === "") children.length = 0; },
    get innerHTML() { return this._innerHTML || ""; },
    remove() {},
  };
}

globalThis.window = {
  location: { origin: "http://localhost" },
  addEventListener() {}, dispatchEvent() {}, setTimeout: () => 0,
  matchMedia: () => ({ matches: false }),
};
globalThis.document = {
  getElementById: () => null,
  addEventListener() {},
  querySelector: () => null,
  createElement: domElement,
  body: domElement(),
  documentElement: domElement(),
};

const { state } = await import("../js/state.js");
const { el } = await import("../js/dom.js");
const { applyAttributePanel, openAttributePanel } = await import("../js/attr-panel.js");
const { initializeModelingRuntimeState } = await import("../js/modeling-config-data.js");

beforeEach(() => {
  state.activeType = "pim";
  state.modelingConfig.config = {
    levelOrder: ["pim"],
    levels: {
      pim: {
        modelNameTemplate: "Test model",
        labelField: "name",
        relationshipSemantics: { containmentKind: "CONTAINS", containmentKinds: ["CONTAINS"] },
        rootTemplate: { eClass: "Model", name: "Test model" },
        elements: [{ type: "Node" }],
      },
    },
  };
  initializeModelingRuntimeState(state.modelingConfig.config);
  state.tabs = { pim: { modelName: "Test model" } };
  state.baseModel = { eClass: "Model", name: "Test model" };
  state.diagram = { nodes: [], connections: [] };
  state.graph = {
    elementsById: new Map(), relationshipsById: new Map(),
    relationshipsBySource: new Map(), relationshipsByTarget: new Map(),
    relationshipsByKind: new Map(), parentByChild: new Map(),
    containmentByParent: new Map(), traceLinksById: new Map(),
    assumptionsById: new Map(), validationIssues: [], manualBacklog: [],
  };
  state.views = { byId: new Map(), activeViewId: null };
  state.fragments = { byId: new Map() };
  state.nodesById = new Map();
  state.connectionsById = new Map();
  state.selectedRootModel = false;
  state.selectedNodeId = null;
  state.selectedConnectionId = null;
  el.attrPanelTitle = domElement();
});

function input(key, type, value, required = false) {
  return { dataset: { attrKey: key, attrType: type }, value, required, tagName: "INPUT" };
}

for (const target of ["element", "root", "connection"]) {
  for (const [scenario, date, required, expected] of [
    ["untouched optional date", "", false, null],
    ["cleared optional date", "", false, null],
    ["selected date", "2026-10-17", false, "2026-10-17"],
    ["empty required date", "", true, "1970-01-01"],
  ]) {
    test(`${target}: saves another attribute with ${scenario}`, () => {
      const model = {
        id: "item", eClass: "Node", name: "Item",
        dueDate: scenario.startsWith("cleared") ? "2026-01-01" : null,
      };
      let saved = model;
      if (target === "root") {
        Object.assign(state.baseModel, model, { eClass: "Model" });
        state.selectedRootModel = true;
        saved = state.baseModel;
      } else if (target === "connection") {
        state.selectedConnectionId = model.id;
        state.connectionsById.set(model.id, { id: model.id, kind: "LINK" });
        state.graph.relationshipsById.set(model.id, model);
      } else {
        const node = { id: model.id, type: "Node", label: model.name, meta: model, x: 0, y: 0 };
        state.selectedNodeId = model.id;
        state.nodesById.set(model.id, node);
        state.graph.elementsById.set(model.id, model);
        state.diagram.nodes.push(node);
      }
      el.attrPanelBody = {
        querySelectorAll: () => [
          input("description", "text", "Updated description"),
          input("dueDate", "date", date, required),
        ],
      };

      assert.equal(applyAttributePanel(), true);
      const payload = JSON.parse(JSON.stringify(saved));
      assert.equal(payload.description, "Updated description");
      assert.equal(payload.dueDate, expected);
    });
  }
}

test("renders semantic PSM status in the attribute inspector", () => {
  const config = {
    levelOrder: ["psm"],
    levels: {
      psm: {
        apiType: "PSM",
        rootTemplate: { eClass: "AwsPsmModel" },
        elements: [
          { type: "AwsPsmModel" },
          {
            type: "S3LifecycleRule",
            attributes: [
              { name: "status", fieldType: "text", required: true },
              { name: "ruleId", fieldType: "text", required: true },
            ],
          },
        ],
      },
    },
  };
  state.activeType = "psm";
  state.modelingConfig.config = config;
  initializeModelingRuntimeState(config);
  const element = { eClass: "S3LifecycleRule", id: "rule-1", status: "Enabled", ruleId: "expire" };
  const node = { id: element.id, type: element.eClass, label: "expire", meta: element, x: 0, y: 0 };
  state.nodesById.set(node.id, node);
  state.graph.elementsById.set(node.id, element);
  state.diagram.nodes = [node];

  Object.assign(el, {
    attrPanelBody: domElement(),
    attrPanelType: domElement(),
    attrPanelTitle: domElement(),
    attrPanelApplyBtn: domElement(),
    attrPanelDeleteBtn: domElement(),
    attributePanel: domElement(),
    workspace: domElement(),
  });

  openAttributePanel(node.id);

  const attrKeys = [];
  const visit = (elementNode) => {
    if (elementNode?.dataset?.attrKey) attrKeys.push(elementNode.dataset.attrKey);
    (elementNode?.children || []).forEach(visit);
  };
  visit(el.attrPanelBody);
  assert.ok(attrKeys.includes("status"), "the Ecore status attribute should be editable");
});
