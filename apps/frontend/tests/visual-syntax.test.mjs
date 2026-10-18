import assert from "node:assert/strict";
import { beforeEach, test } from "node:test";

globalThis.window = { location: { origin: "http://localhost" }, addEventListener() {} };
globalThis.document = { getElementById: () => null, addEventListener() {} };

const { state } = await import("../js/state.js");
const { ensureViewContent, installGraphAndViews, refreshViewContent, selectElementIdsForView } = await import("../js/graph-store.js");
const { modelingViewDefinition } = await import("../js/modeling-config-data.js");

beforeEach(() => {
  state.activeType = "cim";
  state.modelingConfig.config = {
    levelOrder: ["cim"],
    levels: {
      cim: {
        rootTemplate: { eClass: "Model", modelLevel: "CIM" },
        relationshipSemantics: { containmentKind: "CONTAINS", containmentKinds: ["CONTAINS"] },
        elements: [{ type: "Actor" }, { type: "Requirement" }],
        views: [
          { id: "overview", displayName: "Overview", viewpoint: "shared", palette: [], canvas: [], relationshipKinds: [] },
          { id: "stakeholder-actor-map", displayName: "Actors", viewType: "STAKEHOLDER_ACTOR_MAP", viewpoint: "shared", palette: ["Actor"], canvas: ["Actor"], elementTypes: ["Actor"], relationshipKinds: ["OWNS_GOALS"], layoutHint: "RADIAL" },
        ],
      },
    },
  };
  state.tabs = { cim: {} };
});

function savedView(extra = {}) {
  return {
    id: "view-cim-stakeholder-actor-map-global", name: "Stakeholder and Actor", level: "CIM",
    kind: "STAKEHOLDER_ACTOR_MAP", definitionId: "stakeholder-actor-map", viewpoint: "shared",
    palette: ["Actor", "Requirement"], canvas: ["Actor", "Requirement"],
    filters: { elementTypes: ["Actor", "Requirement"], relationshipKinds: ["PROVIDES_REQUIREMENTS"] },
    nodes: [{ elementId: "actor", x: 340, y: 260 }, { elementId: "requirement", x: 500, y: 100 }],
    edges: [], camera: { x: 10, y: 20, scale: 0.75 },
    hidden: { elementIds: [], relationshipIds: [] }, autoLayoutApplied: true,
    ...extra,
  };
}

function install(view) {
  return installGraphAndViews("cim", {
    name: "Example", eClass: "Model",
    graph: { elements: [{ id: "actor", eClass: "Actor" }, { id: "requirement", eClass: "Requirement" }], relationships: [] },
    views: [view],
  }, "Example", { skipFragments: true, skipClientLayout: true, preserveActiveViewId: "view-cim-overview-global", preserveActiveView: true });
}

test("persisted inactive views use active CVS settings while preserving geometry and camera", () => {
  const saved = savedView();
  const { graph, views } = install(saved);
  const updated = views.byId.get(saved.id);
  assert.equal(updated.name, "Actors");
  assert.deepEqual(updated.palette, ["Actor"]);
  assert.deepEqual(updated.canvas, ["Actor"]);
  assert.deepEqual(updated.filters.elementTypes, ["Actor"]);
  assert.deepEqual(updated.filters.relationshipKinds, ["OWNS_GOALS"]);
  assert.equal(updated.layoutProfile, "RADIAL");
  assert.deepEqual(updated.camera, saved.camera);
  const actorNode = updated.nodes.find((node) => node.elementId === "actor");
  assert.equal(actorNode.x, 340);
  assert.equal(actorNode.y, 260);
  assert.deepEqual(selectElementIdsForView(graph, updated, "cim"), ["actor"]);
});

test("clearing CVS lists removes old palette, canvas, and connector filters", () => {
  const definition = state.modelingConfig.config.levels.cim.views[1];
  definition.palette = [];
  definition.canvas = [];
  definition.relationshipKinds = [];
  const { graph, views } = install(savedView());
  const updated = views.byId.get("view-cim-stakeholder-actor-map-global");
  assert.deepEqual(updated.palette, []);
  assert.deepEqual(updated.canvas, []);
  assert.deepEqual(updated.filters.relationshipKinds, []);
  assert.deepEqual(selectElementIdsForView(graph, updated, "cim"), []);
});

test("the active configured view also applies CVS settings and retains its saved position", () => {
  const saved = savedView();
  const { views } = installGraphAndViews("cim", {
    name: "Example", eClass: "Model", activeViewId: saved.id,
    graph: { elements: [{ id: "actor", eClass: "Actor" }, { id: "requirement", eClass: "Requirement" }], relationships: [] },
    views: [saved],
  }, "Example", { skipFragments: true, skipClientLayout: true, preserveActiveView: true });
  const updated = views.byId.get(saved.id);
  assert.equal(views.activeViewId, saved.id);
  assert.equal(updated.name, "Actors");
  assert.deepEqual(updated.palette, ["Actor"]);
  assert.deepEqual(updated.nodes.map((node) => node.elementId), ["actor"]);
  assert.equal(updated.nodes[0].x, 340);
  assert.equal(updated.nodes[0].y, 260);
  assert.deepEqual(updated.camera, saved.camera);
});

test("view identity wins over a shared viewpoint and supports legacy kind matching", () => {
  assert.equal(modelingViewDefinition("cim", savedView()).id, "stakeholder-actor-map");
  assert.equal(modelingViewDefinition("cim", savedView({ definitionId: "" })).id, "stakeholder-actor-map");
});

test("refreshing the active configured view preserves hidden elements and explicit pins", () => {
  const saved = savedView({
    pinnedElementIds: ["requirement"],
    hidden: { elementIds: ["actor"], relationshipIds: [] },
  });
  const { graph, views } = installGraphAndViews("cim", {
    name: "Example", eClass: "Model", activeViewId: saved.id,
    graph: { elements: [{ id: "actor", eClass: "Actor" }, { id: "requirement", eClass: "Requirement" }], relationships: [] },
    views: [saved],
  }, "Example", { skipFragments: true, skipClientLayout: true, preserveActiveView: true });
  const updated = views.byId.get(saved.id);
  assert.deepEqual(updated.hidden, saved.hidden);
  assert.deepEqual(updated.pinnedElementIds, ["requirement"]);
  assert.deepEqual(selectElementIdsForView(graph, updated, "cim"), ["requirement"]);
  assert.equal(updated.nodes[0].elementId, "requirement");
  assert.equal(updated.nodes[0].x, 500);
  assert.equal(updated.nodes[0].y, 100);
});

test("saved user viewpoints retain their own name and filters", () => {
  const saved = savedView({ id: "my-view", name: "My review", kind: "SAVED_VIEWPOINT" });
  const { views } = install(saved);
  const updated = views.byId.get(saved.id);
  assert.equal(updated.name, "My review");
  assert.deepEqual(updated.palette, saved.palette);
  assert.deepEqual(updated.canvas, saved.canvas);
  assert.deepEqual(updated.filters, saved.filters);
});

test("materializing and refreshing the main view preserves its name", () => {
  const { views } = installGraphAndViews("cim", {
    name: "Example", eClass: "Model",
    graph: { elements: [{ id: "actor", eClass: "Actor" }], relationships: [] },
  }, "Example", { skipFragments: true, skipClientLayout: true, preserveActiveViewId: "view-cim-overview-global", preserveActiveView: true });
  const main = views.byId.get("view-cim-main");
  assert.equal(main.name, "Example Main View");
  assert.equal(main._lazyContent, true);
  ensureViewContent(main, "cim", { skipClientLayout: true });
  assert.equal(main.name, "Example Main View");
  refreshViewContent(main, "cim", { skipClientLayout: true });
  refreshViewContent(main, "cim", { skipClientLayout: true });
  assert.equal(main.name, "Example Main View");
});
