import test from "node:test";
import assert from "node:assert/strict";
import { materializeDiagramFromModel } from "../src/model-materializer.mjs";

test("materializeDiagramFromModel reads graph.elements and view filters", () => {
  const model = {
    graph: {
      elements: [
        { id: "n1", eClass: "Capability", name: "Billing", x: 120, y: 80 },
        { id: "n2", eClass: "ApplicationComponent", name: "Portal", x: 420, y: 180 },
      ],
      relationships: [
        {
          id: "e1",
          kind: "ASSIGNMENT",
          source: "n1",
          target: "n2",
        },
      ],
    },
    views: [
      {
        id: "view-cim-dashboard-global",
        filters: { elementTypes: ["Capability", "ApplicationComponent"] },
        nodes: [
          { elementId: "n1", x: 100, y: 50 },
          { elementId: "n2", x: 400, y: 150 },
        ],
      },
    ],
  };

  const diagram = materializeDiagramFromModel(model, "view-cim-dashboard-global");
  assert.equal(diagram.nodes.length, 2);
  assert.equal(diagram.connections.length, 1);
  assert.equal(diagram.nodes[0].type, "Capability");
  assert.equal(diagram.nodes[0].x, 100);
  assert.equal(diagram.nodes[0].data.name, "Billing");
});

test("materializeDiagramFromModel does not treat empty legacy nodeIds as filter", () => {
  const model = {
    graph: {
      elements: [{ id: "n1", eClass: "Capability", name: "Only", x: 10, y: 20 }],
      relationships: [],
    },
    views: [
      {
        id: "view-main",
        nodeIds: [],
        filters: { elementTypes: ["Capability"] },
      },
    ],
  };

  const diagram = materializeDiagramFromModel(model, "view-main");
  assert.equal(diagram.nodes.length, 1);
});
