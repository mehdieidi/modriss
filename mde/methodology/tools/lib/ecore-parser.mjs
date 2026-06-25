import { readFileSync } from "node:fs";

/** @returns {{ classes: string[], enums: string[], dataTypes: string[] }} */
export function parseEcore(file) {
  const xml = readFileSync(file, "utf8");
  const classes = [...xml.matchAll(/xsi:type="ecore:EClass" name="([^"]+)"/g)].map((m) => m[1]);
  const enums = [...xml.matchAll(/xsi:type="ecore:EEnum" name="([^"]+)"/g)].map((m) => m[1]);
  const dataTypes = [...xml.matchAll(/xsi:type="ecore:EDataType" name="([^"]+)"/g)].map(
    (m) => m[1],
  );
  return { classes, enums, dataTypes };
}

export function allConcepts(parsed) {
  return [...parsed.classes, ...parsed.enums, ...parsed.dataTypes];
}
