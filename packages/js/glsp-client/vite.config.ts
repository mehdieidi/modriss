import { copyFileSync, mkdirSync } from "node:fs";
import { createRequire } from "node:module";
import { defineConfig, type Plugin } from "vite";
import { resolve } from "node:path";

const require = createRequire(import.meta.url);
const vendorDir = resolve(__dirname, "../../../apps/frontend/vendor/glsp");

function stubCssImports(): Plugin {
  return {
    name: "stub-css-imports",
    enforce: "pre",
    resolveId(id) {
      if (id.endsWith(".css")) {
        return `\0css-stub:${id}`;
      }
      return undefined;
    },
    load(id) {
      if (id.startsWith("\0css-stub:")) {
        return "/* stub */";
      }
      return undefined;
    },
  };
}

function copyGlspVendorCss(): Plugin {
  return {
    name: "copy-glsp-vendor-css",
    closeBundle() {
      const glspCss = require.resolve("@eclipse-glsp/client/css/glsp-sprotty.css");
      mkdirSync(vendorDir, { recursive: true });
      copyFileSync(glspCss, resolve(vendorDir, "glsp-client.css"));
    },
  };
}

function dropBundledCss(): Plugin {
  return {
    name: "drop-bundled-css",
    generateBundle(_options, bundle) {
      for (const fileName of Object.keys(bundle)) {
        if (fileName.endsWith(".css")) {
          delete bundle[fileName];
        }
      }
    },
  };
}

export default defineConfig({
  plugins: [stubCssImports(), dropBundledCss(), copyGlspVendorCss()],
  resolve: {
    dedupe: ["sprotty", "sprotty-protocol", "inversify", "reflect-metadata", "snabbdom"],
  },
  build: {
    lib: {
      entry: resolve(__dirname, "src/entry.ts"),
      name: "ModlessGlsp",
      formats: ["es"],
      fileName: () => "modless-glsp.js",
    },
    outDir: resolve(__dirname, "../../../apps/frontend/vendor/glsp"),
    emptyOutDir: true,
    sourcemap: true,
    cssCodeSplit: false,
    rollupOptions: {
      output: {
        inlineDynamicImports: true,
      },
    },
  },
});
