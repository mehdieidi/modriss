import assert from "node:assert/strict";
import test from "node:test";
import { impactCounts, impactEvidence, impactGroups } from "../js/impact-data.js";

const node = (modelId, elementId, modelType = "PIM") => ({
  modelId, elementId, modelType, modelName: `${modelType} model`,
  elementName: elementId, elementType: "Function",
});
const edge = (source, target, transformationRule = "TR-070") => ({
  sourceModelId: source.modelId, sourceElementId: source.elementId,
  targetModelId: target.modelId, targetElementId: target.elementId, transformationRule,
});

test("counts unique model-scoped elements and files without multiplying shared branches", () => {
  const focalElement = node("cim", "command", "CIM");
  const handler = node("pim", "handler");
  const schema = node("pim", "schema");
  const lambda = node("psm", "handler", "PSM");
  const request = node("psm", "request", "PSM");
  const file = node("artifact", "template.yaml", "ARTIFACT");
  const data = { focalElement, upstream: [],
    downstream: [handler, schema, lambda, request, file, file],
    traceEdges: [edge(focalElement, handler), edge(focalElement, schema),
      edge(handler, lambda), edge(schema, request), edge(lambda, file), edge(request, file)],
  };
  assert.deepEqual(impactCounts(data), { upstream: 0, downstream: 4, files: 1 });
  const groups = impactGroups(data, "downstream");
  assert.deepEqual(groups.map((group) => group.items.length), [2, 2, 1]);
  assert.deepEqual(impactEvidence(data, request, "downstream").map(({ peer }) => peer), [schema]);
  assert.deepEqual(impactEvidence(data, file, "downstream").map(({ peer }) => peer), [lambda, request]);
  assert.deepEqual(impactEvidence(data, handler, "upstream").map(({ peer }) => peer), [lambda]);
  assert.equal(impactGroups(data, "downstream", "template.yaml")[0].items.length, 1);
  assert.equal(impactGroups(data, "downstream", "no such file").length, 0);
  assert.equal(impactGroups(data, "downstream", "tr-070").length, 3);
});

test("missing trace data stays empty without fabricating lineage", () => {
  assert.deepEqual(impactGroups({}, "upstream"), []);
  assert.deepEqual(impactCounts({}), { upstream: 0, downstream: 0, files: 0 });
});
