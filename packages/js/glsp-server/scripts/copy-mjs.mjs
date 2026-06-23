import { cpSync, mkdirSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const root = dirname(fileURLToPath(import.meta.url));
const pkgRoot = join(root, "..");
const dist = join(pkgRoot, "dist");
const mjsFiles = [
  "cvs-mapper.mjs",
  "operation-validator.mjs",
  "elk-layout.mjs",
  "shortcut-edges.mjs",
  "complexity.mjs",
  "overlays.mjs",
];

mkdirSync(dist, { recursive: true });
for (const file of mjsFiles) {
  cpSync(join(pkgRoot, "src", file), join(dist, file));
}
