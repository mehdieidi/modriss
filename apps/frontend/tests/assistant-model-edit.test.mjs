import assert from "node:assert/strict";
import test from "node:test";

globalThis.window = { location: { origin: "http://localhost" }, addEventListener() {}, dispatchEvent() {} };
globalThis.document = { getElementById: () => null, addEventListener() {} };

const { state } = await import("../js/state.js");
const { applyModelingRuntimeConfig } = await import("../js/config.js");
const { initializeModelingRuntimeState } = await import("../js/modeling-config-data.js");
const { installGraphAndViews } = await import("../js/graph-store.js");
const { materializeActiveView } = await import("../js/view-materializer.js");

function renderCheckpoint(model) {
  const config = {
    version: 1,
    levelOrder: ["cim"],
    levels: {
      cim: {
        rootTemplate: { eClass: "CIMModel", modelLevel: "CIM" },
        relationshipSemantics: { containmentKind: "CONTAINS", containmentKinds: ["CONTAINS"] },
        workbench: { defaultViewDefinitionId: "main" },
        viewDefinitions: [{ id: "main", viewType: "main", canvas: ["InformationItem"] }],
        elements: [
          { type: "CIMModel", references: [{ name: "informationItems", targetType: "InformationItem", containment: true, many: true }] },
          { type: "InformationItem", visualRole: "node" },
        ],
      },
    },
  };
  state.modelingConfig.config = config;
  initializeModelingRuntimeState(config);
  applyModelingRuntimeConfig(config);
  state.activeType = "cim";
  state.tabs = { cim: {} };
  state.canvasFocusStack = [];
  installGraphAndViews("cim", model, "Lending", { skipFragments: true, skipClientLayout: true });
  return materializeActiveView();
}

test("assistant semantic rename appears on canvas when the stored graph still has the old label", () => {
  const model = {
    id: "root", eClass: "CIMModel", modelLevel: "CIM",
    informationItems: [
      { id: "item", eClass: "InformationItem", name: "load identity" },
      { id: "other", eClass: "InformationItem", name: "Borrower" },
    ],
    graph: { elements: [
      { id: "item", eClass: "InformationItem", name: "Load ID", x: 140, y: 80 },
      { id: "other", eClass: "InformationItem", name: "Borrower", x: 340, y: 80 },
    ], relationships: [] },
  };
  const diagram = renderCheckpoint(model);
  assert.equal(diagram.nodes.find(node => node.id === "item").label, "load identity");
  assert.equal(diagram.nodes.find(node => node.id === "other").label, "Borrower");
});

test("assistant deletion disappears from canvas after semantic and visual removal", () => {
  const model = {
    id: "root", eClass: "CIMModel", modelLevel: "CIM",
    informationItems: [{ id: "other", eClass: "InformationItem", name: "Borrower" }],
    graph: { elements: [{ id: "other", eClass: "InformationItem", name: "Borrower" }], relationships: [] },
  };
  const diagram = renderCheckpoint(model);
  assert.deepEqual(diagram.nodes.map(node => node.id), ["other"]);
});
