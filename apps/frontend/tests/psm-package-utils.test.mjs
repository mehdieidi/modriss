import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { zipEntryNames, preferredEntry, branchIsStale } from "../js/psm-package-utils.js";

test("entry selector preserves nested ETL and EGL paths in real Azure ZIPs", async () => {
  const root = new URL("../../../methodology/modeling-framework/samples/custom-psm-azure/", import.meta.url);
  for (const [kind, expected] of [["transformation", "rules/functions.etl"], ["generation", "templates/function.egl"]]) {
    const bytes = await readFile(new URL(`${kind}.zip`, root));
    const names = zipEntryNames(bytes.buffer.slice(bytes.byteOffset, bytes.byteOffset + bytes.byteLength));
    assert.ok(names.includes(expected));
    assert.equal(preferredEntry(names, kind === "transformation" ? ".etl" : ".egx"), kind === "transformation" ? "main.etl" : "main.egx");
  }
});

test("malformed ZIPs cannot produce script choices", () => {
  assert.throws(() => zipEntryNames(new ArrayBuffer(0)), /valid ZIP/);
  assert.throws(() => zipEntryNames(new ArrayBuffer(40)), /valid ZIP/);
});

test("branches retain provenance and show changes to or removal of their source PIM", () => {
  const branch = { sourcePimId: "pim", sourcePimRevision: 2 };
  assert.equal(branchIsStale(branch, [{ id: "pim", revision: 2 }]), false);
  assert.equal(branchIsStale(branch, [{ id: "pim", revision: 3 }]), true);
  assert.equal(branchIsStale(branch, []), true);
});
