import assert from "node:assert/strict";
import { beforeEach, test } from "node:test";
import { readFileSync } from "node:fs";

globalThis.window = { location: { origin: "http://localhost" }, addEventListener() {} };
globalThis.document = { getElementById: () => null, addEventListener() {} };

const { state } = await import("../js/state.js");
const {
  initializeModelingRuntimeState,
  modelingContainmentPalette,
  modelingContainmentEntryForChildType,
} = await import("../js/modeling-config-data.js");
const {
  installGraphAndViews,
  addNodeToGraphAndActiveView,
  serializeGraphAndViewsInto,
  selectElementIdsForView,
} = await import("../js/graph-store.js");
const cvs = JSON.parse(readFileSync(new URL(
  "../../../methodology/modeling-framework/notation/cim.cvs.json", import.meta.url,
)));

beforeEach(() => {
  const config = {
    version: 1,
    levelOrder: ["cim"],
    levels: {
      cim: {
        rootTemplate: { eClass: "CIMModel", modelLevel: "CIM" },
        relationshipSemantics: { containmentKind: "CONTAINS", containmentKinds: ["CONTAINS"] },
        workbench: { defaultViewDefinitionId: "main" },
        viewDefinitions: [{ id: "main", viewType: "main" }],
        containers: cvs.containers,
        elements: [
          { type: "CIMModel", references: [
            { name: "concepts", targetType: "Entity", containment: true, many: true },
          ] },
          { type: "Entity", references: [
            { name: "invariants", targetType: "BusinessInvariant", containment: true, many: true },
          ] },
          { type: "BusinessInvariant", containedOnly: true, references: [
            { name: "expressionModel", targetType: "Expression", containment: true, many: false },
          ] },
          { type: "Expression", containedOnly: true, creatable: true },
          { type: "TemporalConstraint", references: ["deadline", "duration", "ordering"].map(
            (name) => ({ name, targetType: "Expression", containment: true, many: false }),
          ) },
        ],
      },
    },
  };
  state.modelingConfig.config = config;
  initializeModelingRuntimeState(config);
  state.activeType = "cim";
});

test("an invariant exposes an expression slot and refuses a second owned expression", () => {
  assert.deepEqual(modelingContainmentPalette("cim", "BusinessInvariant"), ["Expression"]);
  assert.equal(modelingContainmentEntryForChildType(
    "cim", "BusinessInvariant", "Expression", {},
  ).feature, "expressionModel");
  for (const expression of ["expression-1", { id: "expression-1", eClass: "Expression" }]) {
    assert.equal(modelingContainmentEntryForChildType(
      "cim", "BusinessInvariant", "Expression", { expressionModel: expression },
    ), null);
  }
});

test("repeated expression drops select the next available singleton feature", () => {
  const features = ["deadline", "duration", "ordering"].map(
    (feature) => ({ feature, types: ["Expression"], many: false }),
  );
  // Exercise both generated feature metadata and the Ecore-reference fallback.
  for (const containmentPalettes of [{}, { TemporalConstraint: { features } }]) {
    state.modelingConfig.config.levels.cim.containmentPalettes = containmentPalettes;
    const owner = {};
    for (const expected of ["deadline", "duration", "ordering"]) {
      const entry = modelingContainmentEntryForChildType("cim", "TemporalConstraint", "Expression", owner);
      assert.equal(entry.feature, expected);
      owner[entry.feature] = `expression-${expected}`;
    }
    assert.equal(modelingContainmentEntryForChildType("cim", "TemporalConstraint", "Expression", owner), null);
  }
});

test("a newly added expression is visible inside its invariant and stays owned after saving", () => {
  const model = {
    eClass: "CIMModel", id: "root", concepts: [{
      eClass: "Entity", id: "entity-1", invariants: [{ eClass: "BusinessInvariant", id: "invariant-1" }],
    }],
  };
  installGraphAndViews("cim", model, "Example", { skipFragments: true, skipClientLayout: true });
  const owner = state.graph.elementsById.get("invariant-1");
  const entry = modelingContainmentEntryForChildType("cim", "BusinessInvariant", "Expression", owner);
  owner[entry.feature] = "expression-1";
  addNodeToGraphAndActiveView({
    id: "expression-1", type: "Expression", x: 20, y: 30,
    meta: { body: "balance >= 0", language: "OCL", __ownerId: owner.id, __containmentFeature: entry.feature },
  });
  const focusView = {
    id: "invariant-focus", scope: { scopeKind: "CONTAINER", rootElementId: owner.id },
    nodes: [], edges: [],
  };
  assert.deepEqual(selectElementIdsForView(state.graph, focusView, "cim"), ["expression-1"]);
  const saved = structuredClone(model);
  serializeGraphAndViewsInto(saved, { syncView: false });
  const expression = saved.concepts[0].invariants[0].expressionModel;
  assert.equal(Array.isArray(expression), false);
  assert.equal(expression.id, "expression-1");
  assert.equal(expression.body, "balance >= 0");
  installGraphAndViews("cim", saved, "Example", { skipFragments: true, skipClientLayout: true });
  assert.equal(state.graph.parentByChild.get("expression-1"), "invariant-1");
});
