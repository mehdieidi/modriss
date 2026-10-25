import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";
import { transform } from "esbuild";

const source = await readFile(new URL("../src/cvs-containers.ts", import.meta.url), "utf8");
const { code } = await transform(source, { loader: "ts", format: "esm", target: "es2022" });
const { containerEditingCatalog } = await import(`data:text/javascript;base64,${Buffer.from(code).toString("base64")}`);

test("relationship owners and inherited children come from the Ecore catalog", () => {
  const doc = {
    elements: [{ type: "DomainEntity" }, { type: "DomainRelationship", relationshipElement: true }],
    metamodelContainmentFeatures: {
      DomainEntity: [{ feature: "invariants", types: ["BusinessInvariant"] }],
      DomainRelationship: [
        { feature: "sourceMultiplicity", types: ["Multiplicity"] },
        { feature: "targetMultiplicity", types: ["Multiplicity"] },
      ],
      WorkflowTransition: [{ feature: "condition", types: ["Expression"] }],
      LeafRelationship: [],
    },
    metamodelSemanticEdgeObjectRules: [{ eClass: "WorkflowTransition" }],
  };
  const catalog = containerEditingCatalog(doc, "DomainRelationship");
  assert.deepEqual(catalog.ownerTypes, ["DomainEntity", "DomainRelationship", "WorkflowTransition"]);
  assert.deepEqual(catalog.childTypes, ["Multiplicity"]);
  assert.deepEqual([...catalog.edgeTypes].sort(), ["DomainRelationship", "WorkflowTransition"]);
  assert.deepEqual(containerEditingCatalog(doc, "WorkflowTransition").childTypes, ["Expression"]);
});

test("an empty catalog offers no invented owners or child types", () => {
  const catalog = containerEditingCatalog({ elements: [{ type: "Unknown" }] });
  assert.deepEqual(catalog.ownerTypes, []);
  assert.deepEqual(catalog.childTypes, []);
});
