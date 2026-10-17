import assert from "node:assert/strict";
import { test } from "node:test";
import { defaultPsmNotation, inferPsmLevel, fieldKey } from "../js/psm-notation.js";

globalThis.window = { location: { origin: "http://localhost" }, addEventListener() {} };
globalThis.document = { getElementById: () => null, addEventListener() {} };
const { state } = await import("../js/state.js");
const { initializeModelingRuntimeState, modelingPalette, modelingLegalKinds } = await import("../js/modeling-config-data.js");
const { installGraphAndViews, serializeGraphAndViewsInto } = await import("../js/graph-store.js");
const schema = [
  { name: "Application", namespace: "urn:azure", features: [{ name: "name", kind: "attribute", type: "EString" },
    { name: "functions", type: "Function", typeNamespace: "urn:azure", kind: "reference", containment: true, many: true }] },
  { name: "Function", namespace: "urn:azure", features: [{ name: "name", kind: "attribute", type: "EString" },
    { name: "runtime", type: "Runtime", kind: "attribute", literals: ["node", "python"], defaultValue: "node" },
    { name: "enabled", type: "EBoolean", kind: "attribute", defaultValue: "true" },
    { name: "calls", type: "Function", typeNamespace: "urn:azure", kind: "reference", many: true }] },
];

test("Ecore-only packages infer a complete workbench with one view, one group and default icons", () => {
  const notation = defaultPsmNotation(schema);
  assert.equal(notation.rootType, "Application");
  assert.equal(notation.views.length, 1);
  assert.equal(notation.paletteGroups.length, 1);
  assert.ok(notation.elements.every((element) => element.icon === "category"));
  const level = inferPsmLevel({ name: "Azure" }, schema, notation);
  assert.equal(level.elements[1].attributes.find((field) => field.name === "runtime").fieldType, "select");
  assert.equal(level.elements[1].attributes.find((field) => field.name === "enabled").defaultValue, true);
  state.activeType = "psm";
  state.modelingConfig.config = { levels: { psm: level }, levelOrder: ["psm"], diagramEditor: {}, artifact: { key: "artifact" } };
  initializeModelingRuntimeState(state.modelingConfig.config);
  assert.deepEqual(modelingPalette("psm"), ["Function"]);
  assert.deepEqual(modelingLegalKinds("psm", "Function", "Function"), ["REF_CALLS"]);
  assert.deepEqual(modelingLegalKinds("psm", "Function", "Application"), []);
  const root = { eClass: "Application", id: "root", name: "Example", functions: [
    { eClass: "Function", id: "a", name: "First", runtime: "node", calls: [{ $ref: "b" }] },
    { eClass: "Function", id: "b", name: "Second", runtime: "python" },
  ] };
  installGraphAndViews("psm", root, "Azure model");
  assert.equal(state.views.byId.size, 1);
  assert.equal(state.graph.elementsById.size, 2);
  assert.ok([...state.graph.relationshipsById.values()].some((edge) => edge.kind === "REF_CALLS"));
  state.graph.elementsById.get("a").name = "Edited";
  const saved = serializeGraphAndViewsInto(structuredClone(root), { syncView: false });
  assert.equal(saved.functions.find((value) => value.id === "a").name, "Edited");
  assert.equal(saved.functions.find((value) => value.id === "a").calls[0].$ref, "b");
});

test("namespace collisions and transport-field collisions preserve language identity", () => {
  const schemaWithCollision = [{ name: "Resource", namespace: "urn:a", features: [] }, { name: "Resource", namespace: "urn:b", features: [] }];
  const level = inferPsmLevel({ name: "Cloud" }, schemaWithCollision);
  assert.deepEqual(level.elements.map((element) => element.type), ["urn:a#Resource", "urn:b#Resource"]);
  assert.equal(fieldKey({ name: "id" }), "_semantic_id");
  assert.equal(fieldKey({ name: "views" }), "_semantic_views");
  assert.equal(fieldKey({ name: "x" }), "_semantic_x");
});

test("custom views, groups and icons change presentation while retaining Ecore fields", () => {
  const notation = defaultPsmNotation(schema);
  notation.paletteGroups.push({ id: "compute", name: "Compute" });
  notation.elements[1].group = "compute";
  notation.elements[1].icon = "functions";
  notation.views.push({ id: "functions", name: "Functions", types: ["Function"] });
  const level = inferPsmLevel({ name: "Azure" }, schema, notation);
  assert.equal(level.views.length, 2);
  assert.equal(level.elements[1].category, "Compute");
  assert.equal(level.elements[1].icon, "functions");
  assert.equal(level.elements[1].attributes.length, 3);
  assert.deepEqual(level.views[1].palette, ["Function"]);
});
