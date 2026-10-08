import assert from "node:assert/strict";
import test from "node:test";
import { declutterEdgeLabels } from "../../apps/frontend/js/graph-editor/edge-label-layout.js";

function edge(id, y, emphasized = {}) {
  return {
    id,
    style: { labelText: "contains command", labelBackground: true, ...emphasized },
    data: {
      sourceAnchor: { side: "right" },
      targetAnchor: { side: "left" },
      routeStart: { x: 0, y },
      routeEnd: { x: 300, y },
      pinPoints: [],
    },
  };
}

test("keeps separated labels while decluttering parallel routes deterministically", () => {
  const edges = [edge("b", 8), edge("c", 60), edge("a", 0)];
  const geometry = structuredClone(edges.map((item) => item.data));
  declutterEdgeLabels([], edges);
  assert.deepEqual(edges.map((item) => item.style.labelText !== ""), [false, true, true]);
  assert.deepEqual(edges.map((item) => item.data), geometry);
});

test("prioritizes hovered labels and keeps labels off node boxes", () => {
  const edges = [edge("a", 0), edge("b", 8, { hovered: true }), edge("c", 60)];
  const nodes = [{ style: { x: 150, y: 60, width: 100, height: 40 } }];
  declutterEdgeLabels(nodes, edges);
  assert.deepEqual(edges.map((item) => item.style.labelText !== ""), [false, true, false]);
});
