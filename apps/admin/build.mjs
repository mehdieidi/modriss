import esbuild from "esbuild";
import { cp, mkdir, rm, writeFile } from "node:fs/promises";
import { join } from "node:path";

const outdir = "dist";
await rm(outdir, { recursive: true, force: true });
await mkdir(join(outdir, "assets"), { recursive: true });

await esbuild.build({
  entryPoints: ["src/main.tsx"],
  bundle: true,
  minify: true,
  sourcemap: false,
  splitting: false,
  format: "esm",
  target: ["es2022"],
  outfile: join(outdir, "assets", "index.js"),
  loader: {
    ".tsx": "tsx",
    ".ts": "ts",
    ".css": "css",
  },
});

await cp("public", outdir, { recursive: true });
await writeFile(
  join(outdir, "index.html"),
  `<!doctype html>
<html lang="en">
  <head>
    <meta charset="UTF-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
    <meta name="robots" content="noindex,nofollow" />
    <title>MODRISS Admin</title>
    <link rel="icon" type="image/svg+xml" href="/icons/favicon.svg" />
    <link rel="stylesheet" href="/assets/index.css" />
  </head>
  <body>
    <div id="root"></div>
    <script src="/admin-config.js"></script>
    <script type="module" src="/assets/index.js"></script>
  </body>
</html>
`,
);
