import assert from "node:assert/strict";
import { beforeEach, test } from "node:test";

globalThis.window = { location: { origin: "http://localhost" } };
globalThis.document = { getElementById: () => null };

const { state } = await import("../js/state.js");
const { initializeModelingRuntimeState } = await import("../js/modeling-config-data.js");
const {
  addConnectionToGraphAndActiveView,
  installGraphAndViews,
  serializeGraphAndViewsInto,
} = await import("../js/graph-store.js");
const { edgeLabel } = await import("../js/graph-editor/g6-mapper.js");

const id = "01M4ZJ4E3J9H37REYDBK4X8Z63";
const kinds = ["DEPENDS_ON", "CONFLICTS_WITH", "REFINES", "DUPLICATES", "DERIVES_FROM"];

beforeEach(() => {
  const config = {
    levelOrder: ["cim"],
    levels: {
      cim: {
        rootTemplate: { eClass: "CIMModel" },
        relationshipSemantics: { containmentKind: "CONTAINS", containmentKinds: ["CONTAINS"] },
        relationshipLabelFields: ["name", "label"],
        relationshipKindLabels: { DEPENDS_ON: "depends on" },
        workbench: { defaultViewDefinitionId: "requirements" },
        viewDefinitions: [{ id: "requirements", viewType: "REQUIREMENTS", palette: ["Requirement"] }],
        elements: [
          {
            type: "CIMModel",
            references: [
              { name: "requirements", targetType: "Requirement", containment: true, many: true },
              { name: "requirementRelationships", targetType: "RequirementRelationship", containment: true, many: true },
            ],
          },
          { type: "Requirement" },
          {
            type: "RequirementRelationship",
            relationshipElement: true,
            attributes: [{ name: "name" }, { name: "kind", options: kinds }],
            references: [
              { name: "source", targetType: "Requirement" },
              { name: "target", targetType: "Requirement" },
            ],
          },
        ],
        semanticEdgeObjectRules: [{
          eClass: "RequirementRelationship",
          kindField: "kind",
          matchKinds: kinds,
          sourceType: "Requirement",
          targetType: "Requirement",
          rootFeature: "requirementRelationships",
          sourceFeature: "source",
          targetFeature: "target",
        }],
      },
    },
  };
  state.activeType = "cim";
  state.modelingConfig.config = config;
  initializeModelingRuntimeState(config);
  installGraphAndViews("cim", {
    eClass: "CIMModel",
    requirements: [
      { eClass: "Requirement", id: "a", name: "First requirement" },
      { eClass: "Requirement", id: "b", name: "Second requirement" },
    ],
    requirementRelationships: [{
      eClass: "RequirementRelationship", id,
      name: `requirementRelationships ${id}`,
      source: "a", target: "b", kind: "DEPENDS_ON",
    }],
  }, "Requirements", { skipClientLayout: true });
});

test("a loaded requirement relationship displays its kind instead of its generated name", () => {
  assert.equal(edgeLabel({ id, kind: "DEPENDS_ON" }), "depends on");
  assert.equal(state.graph.relationshipsById.get(id).name, `requirementRelationships ${id}`);
});

test("changing a requirement relationship kind updates its label and survives save and reload", () => {
  for (const kind of kinds) {
    const edge = { id, sourceId: "a", targetId: "b", kind };
    addConnectionToGraphAndActiveView(edge);
    assert.equal(edgeLabel(edge), kind.toLowerCase().replaceAll("_", " "));
    const saved = { eClass: "CIMModel" };
    serializeGraphAndViewsInto(saved, { syncView: false });
    installGraphAndViews("cim", saved, "Requirements", { skipClientLayout: true });
    assert.equal(edgeLabel(state.graph.relationshipsById.get(id)), kind.toLowerCase().replaceAll("_", " "));
  }
});

test("a newly drawn relationship displays its kind despite the generated class name", () => {
  const edge = { id: "new-relationship", sourceId: "a", targetId: "b", kind: "DEPENDS_ON" };
  addConnectionToGraphAndActiveView(edge);
  assert.equal(edgeLabel(edge), "depends on");
});

test("generated labels also fall back to the kind when copied onto a visible edge", () => {
  assert.equal(edgeLabel({ id, kind: "DEPENDS_ON", label: `requirementRelationships ${id}` }), "depends on");
});

test("custom relationship names, explicit edge labels, and bundle labels are preserved", () => {
  state.graph.relationshipsById.get(id).name = "Needs authentication";
  assert.equal(edgeLabel({ id, kind: "DEPENDS_ON" }), "Needs authentication");
  assert.equal(edgeLabel({ id, kind: "DEPENDS_ON", label: "Custom label" }), "Custom label");
  assert.equal(edgeLabel({ id: "bundle", bundle: true, label: "3 dependencies" }), "3 dependencies");
  assert.equal(edgeLabel({ id: "bundle", bundle: true }), "Bundled relations");
});
