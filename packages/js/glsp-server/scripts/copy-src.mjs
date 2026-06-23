import { cpSync, mkdirSync, rmSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const root = dirname(fileURLToPath(import.meta.url));
const pkgRoot = join(root, "..");
const dist = join(pkgRoot, "dist");
const files = [
  "main.mjs",
  "session.mjs",
  "cvs-mapper.mjs",
  "operation-validator.mjs",
  "elk-layout.mjs",
  "shortcut-edges.mjs",
  "complexity.mjs",
  "overlays.mjs",
];

rmSync(dist, { recursive: true, force: true });
mkdirSync(dist, { recursive: true });
for (const file of files) {
  cpSync(join(pkgRoot, "src", file), join(dist, file));
}
