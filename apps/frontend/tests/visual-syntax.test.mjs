import assert from "node:assert/strict";
import { beforeEach, test } from "node:test";

globalThis.window = { location: { origin: "http://localhost" }, addEventListener() {} };
globalThis.document = { getElementById: () => null, addEventListener() {} };

const { state } = await import("../js/state.js");
const { ensureViewContent, installGraphAndViews, refreshViewContent, selectElementIdsForView, selectRelationshipIdsForView } = await import("../js/graph-store.js");
const { initializeModelingRuntimeState, modelingViewDefinition } = await import("../js/modeling-config-data.js");

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
  initializeModelingRuntimeState(state.modelingConfig.config);
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

test("service ownership surfaces service roots and only resources linked to them", () => {
  state.activeType = "psm";
  state.modelingConfig.config = {
    levelOrder: ["psm"],
    levels: {
      psm: {
        rootTemplate: { eClass: "AwsPsmModel", modelLevel: "AWS_PSM" },
        workbench: { defaultViewDefinitionId: "psm-service-ownership" },
        relationshipSemantics: { containmentKind: "CONTAINS", containmentKinds: ["CONTAINS"] },
        elements: [
          { type: "AwsPsmModel" },
          { type: "AwsService" },
          { type: "AwsResource", abstract: true },
          { type: "AwsLambdaFunction", supertypes: ["AwsResource"] },
        ],
        views: [
          {
            id: "psm-service-ownership",
            displayName: "Service Ownership",
            palette: ["AwsService"],
            canvas: ["AwsService"],
            viewEntryTypes: ["AwsService", "AwsResource"],
            includeSubtypes: true,
            relationshipKinds: ["SERVICE_OWNERSHIP"],
            restrictPaletteToView: true,
          },
        ],
      },
    },
  };
  initializeModelingRuntimeState(state.modelingConfig.config);

  const { graph, views } = installGraphAndViews(
    "psm",
    {
      name: "Example",
      eClass: "AwsPsmModel",
      graph: {
        elements: [
          { id: "service-a", eClass: "AwsService" },
          { id: "owned-function", eClass: "AwsLambdaFunction" },
          { id: "unowned-function", eClass: "AwsLambdaFunction" },
        ],
        relationships: [
          {
            id: "ownership-link",
            kind: "SERVICE_OWNERSHIP",
            sourceElementId: "owned-function",
            targetElementId: "service-a",
          },
        ],
      },
      views: [
        {
          id: "view-psm-psm-service-ownership-global",
          name: "Service Ownership",
          level: "AWS_PSM",
          kind: "SERVICE_OWNERSHIP",
          definitionId: "psm-service-ownership",
          scope: { scopeKind: "MODEL" },
          filters: { elementTypes: ["AwsService", "AwsResource"], relationshipKinds: ["SERVICE_OWNERSHIP"] },
          palette: ["AwsService"],
          canvas: ["AwsService", "AwsResource"],
          nodes: [
            { elementId: "service-a", x: 100, y: 100 },
            { elementId: "owned-function", x: 400, y: 100 },
            { elementId: "unowned-function", x: 700, y: 100 },
          ],
          edges: [{ relationshipId: "ownership-link", visible: true }],
          autoLayoutApplied: true,
          layoutGeometryVersion: 3,
        },
      ],
    },
    "Example",
    { skipFragments: true, skipClientLayout: true },
  );
  const view = views.byId.get("view-psm-psm-service-ownership-global");
  ensureViewContent(view, "psm", { skipClientLayout: true });

  assert.deepEqual(
    selectElementIdsForView(graph, view, "psm").sort(),
    ["owned-function", "service-a"],
  );
  assert.deepEqual(
    view.nodes.map((node) => node.elementId).sort(),
    ["owned-function", "service-a"],
  );
  assert.equal(view.autoLayoutApplied, false);
  assert.deepEqual(
    selectRelationshipIdsForView(
      graph,
      view,
      selectElementIdsForView(graph, view, "psm"),
      "psm",
    ),
    ["ownership-link"],
  );
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
