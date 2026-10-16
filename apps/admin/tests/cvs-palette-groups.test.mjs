import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";
import { transform } from "esbuild";

const source = await readFile(new URL("../src/cvs-palette-groups.ts", import.meta.url), "utf8");
const { code } = await transform(source, { loader: "ts", format: "esm", target: "es2022" });
const { paletteGroups, renamePaletteGroup } = await import(
  `data:text/javascript;base64,${Buffer.from(code).toString("base64")}`
);

test("renaming a group updates defaults, explicit elements, and inherited rules together", () => {
  const doc = {
    elementVisualDefaults: { category: "Shared", color: "#123456" },
    elements: [
      { type: "Actor", category: "Shared", icon: "person" },
      { type: "Policy", category: "Policies" },
      { type: "Condition" },
    ],
    elementVisualRules: [
      { match: { packages: ["kernel"] }, metadata: { category: "Shared", creatable: false } },
      { match: { types: ["Expression"] }, metadata: { category: "Shared", creatable: true } },
      { match: { types: ["Policy"] }, metadata: { category: "Policies" } },
      { match: { abstract: true }, metadata: { creatable: false } },
    ],
  };
  const original = structuredClone(doc);
  const group = paletteGroups(doc).find((item) => item.name === "Shared");
  assert.equal(group.isDefault, true);
  assert.deepEqual(group.elementTypes, ["Actor"]);
  assert.deepEqual(group.sharedRules, ["Packages: kernel", "Types: Expression"]);
  renamePaletteGroup(doc, "Shared", "  Common Values  ");
  assert.equal(doc.elementVisualDefaults.category, "Common Values");
  assert.equal(doc.elements[0].category, "Common Values");
  assert.equal(doc.elementVisualRules[0].metadata.category, "Common Values");
  assert.equal(doc.elementVisualRules[1].metadata.category, "Common Values");
  assert.equal(doc.elements[1].category, "Policies");
  assert.equal(doc.elementVisualRules[2].metadata.category, "Policies");
  assert.equal(Object.hasOwn(doc.elements[2], "category"), false);
  renamePaletteGroup(doc, "Common Values", "Shared");
  assert.deepEqual(doc, original, "Renaming preserves unrelated visual settings and rule selectors.");
});

test("renaming to an existing group combines all sources and survives JSON roundtrip", () => {
  const doc = {
    elements: [{ type: "Actor", category: "People" }, { type: "Stakeholder", category: "Stakeholders" }],
  };
  renamePaletteGroup(doc, "People", "Stakeholders");
  const groups = paletteGroups(JSON.parse(JSON.stringify(doc)));
  assert.deepEqual(groups.find((group) => group.name === "Stakeholders").elementTypes, ["Actor", "Stakeholder"]);
  assert.equal(groups.some((group) => group.name === "People"), false);
});

test("rejects empty names without changing any sources", () => {
  const doc = { elementVisualDefaults: { category: "Example" } };
  assert.throws(() => renamePaletteGroup(doc, "Example", " \n "), /required/);
  assert.deepEqual(doc, { elementVisualDefaults: { category: "Example" } });
});

test("the fallback group can be named even when a document has no default category", () => {
  const doc = { elements: [{ type: "Actor" }] };
  renamePaletteGroup(doc, "Model Elements", "Business Elements");
  assert.deepEqual(doc.elementVisualDefaults, { category: "Business Elements" });
});

for (const level of ["cim", "pim", "psm"]) {
  test(`${level}: discovers and renames the inherited expression group`, async () => {
    const doc = JSON.parse(await readFile(new URL(
      `../../../methodology/modeling-framework/notation/${level}.cvs.json`, import.meta.url,
    ), "utf8"));
    const groups = paletteGroups(doc);
    assert.equal(groups.some((group) => group.name === "Kernel / Trace"), false);
    assert.ok(groups.some((group) => group.name === "Traceability & Metadata"));
    const expressions = groups.find((group) => group.name === "Expressions & Values");
    assert.ok(expressions.sharedRules.some((rule) => rule.includes("Expression")));
    renamePaletteGroup(doc, "Expressions & Values", "Business Expressions");
    const saved = JSON.parse(JSON.stringify(doc));
    const rule = saved.elementVisualRules.find((item) => item.match?.types?.includes("Expression"));
    assert.equal(rule.metadata.category, "Business Expressions");
    assert.equal(rule.metadata.creatable, true);
    assert.equal(rule.metadata.containedOnly, true);
  });
}
