#!/usr/bin/env node
import { readFileSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const ROOT = join(dirname(fileURLToPath(import.meta.url)), "../..");
const LEVELS = ["cim", "pim", "psm"];

const KNOWN_THEME_PAIRS = {
  "#334155": { light: "#334155", dark: "#94a3b8" },
  "#475569": { light: "#475569", dark: "#94a3b8" },
  "#64748b": { light: "#64748b", dark: "#cbd5e1" },
  "#2563eb": { light: "#2563eb", dark: "#60a5fa" },
  "#7c3aed": { light: "#7c3aed", dark: "#a78bfa" },
  "#0891b2": { light: "#0891b2", dark: "#22d3ee" },
  "#0f766e": { light: "#0f766e", dark: "#2dd4bf" },
  "#db2777": { light: "#db2777", dark: "#f472b6" },
  "#4f46e5": { light: "#4f46e5", dark: "#818cf8" },
  "#ca8a04": { light: "#ca8a04", dark: "#fbbf24" },
  "#16a34a": { light: "#16a34a", dark: "#4ade80" },
  "#ea580c": { light: "#ea580c", dark: "#fb923c" },
  "#dc2626": { light: "#dc2626", dark: "#f87171" },
  "#0f172a": { light: "#334155", dark: "#94a3b8" },
  "#1d4ed8": { light: "#2563eb", dark: "#60a5fa" },
  "#9333ea": { light: "#7c3aed", dark: "#c4b5fd" },
  "#be123c": { light: "#e11d48", dark: "#fb7185" },
  "#b45309": { light: "#d97706", dark: "#fbbf24" },
  "#e11d48": { light: "#e11d48", dark: "#fb7185" },
  "#d97706": { light: "#d97706", dark: "#fbbf24" },
  "#059669": { light: "#059669", dark: "#34d399" },
  "#0284c7": { light: "#0284c7", dark: "#38bdf8" },
  "#c026d3": { light: "#c026d3", dark: "#e879f9" },
  "#65a30d": { light: "#65a30d", dark: "#a3e635" },
};

function parseHex(hex) {
  const match = /^#?([a-f\d]{2})([a-f\d]{2})([a-f\d]{2})$/i.exec(String(hex || "").trim());
  if (!match) {
    return null;
  }
  return {
    r: Number.parseInt(match[1], 16),
    g: Number.parseInt(match[2], 16),
    b: Number.parseInt(match[3], 16),
  };
}

function toHex({ r, g, b }) {
  const channel = (value) =>
    Math.max(0, Math.min(255, Math.round(value)))
      .toString(16)
      .padStart(2, "0");
  return `#${channel(r)}${channel(g)}${channel(b)}`;
}

function mixTowardWhite(hex, amount) {
  const rgb = parseHex(hex);
  if (!rgb) {
    return hex;
  }
  const ratio = Math.max(0, Math.min(1, amount));
  return toHex({
    r: rgb.r + (255 - rgb.r) * ratio,
    g: rgb.g + (255 - rgb.g) * ratio,
    b: rgb.b + (255 - rgb.b) * ratio,
  });
}

function relativeLuminance(rgb) {
  const channel = (value) => {
    const normalized = value / 255;
    return normalized <= 0.03928
      ? normalized / 12.92
      : ((normalized + 0.055) / 1.055) ** 2.4;
  };
  return (
    0.2126 * channel(rgb.r) +
    0.7152 * channel(rgb.g) +
    0.0722 * channel(rgb.b)
  );
}

function themeColorPairFromHex(hex) {
  const normalized = String(hex || "").trim();
  if (!normalized) {
    return { light: "#475569", dark: "#94a3b8" };
  }
  const known = KNOWN_THEME_PAIRS[normalized.toLowerCase()];
  if (known) {
    return known;
  }
  const rgb = parseHex(normalized);
  if (!rgb) {
    return { light: normalized, dark: normalized };
  }
  const lum = relativeLuminance(rgb);
  if (lum < 0.12) {
    return { light: mixTowardWhite(normalized, 0.35), dark: normalized };
  }
  if (lum > 0.62) {
    return { light: normalized, dark: mixTowardWhite(normalized, 0.15) };
  }
  return { light: normalized, dark: mixTowardWhite(normalized, 0.42) };
}

function migrateColorValue(value) {
  if (!value) {
    return value;
  }
  if (typeof value === "object" && (value.light || value.dark)) {
    return {
      light: String(value.light || value.dark).trim(),
      dark: String(value.dark || value.light).trim(),
    };
  }
  if (typeof value === "string") {
    return themeColorPairFromHex(value);
  }
  return value;
}

function walkObject(node, visitor) {
  if (!node || typeof node !== "object") {
    return;
  }
  if (Array.isArray(node)) {
    node.forEach((item) => walkObject(item, visitor));
    return;
  }
  visitor(node);
  Object.values(node).forEach((value) => walkObject(value, visitor));
}

function migrateDoc(doc) {
  if (doc.elementVisualDefaults?.color) {
    doc.elementVisualDefaults.color = migrateColorValue(doc.elementVisualDefaults.color);
  }
  (doc.elementVisualRules || []).forEach((rule) => {
    if (rule.metadata?.color) {
      rule.metadata.color = migrateColorValue(rule.metadata.color);
    }
  });
  (doc.elementOverrides || []).forEach((entry) => {
    if (entry.color) {
      entry.color = migrateColorValue(entry.color);
    }
  });
  (doc.relationshipVisualRules || []).forEach((rule) => {
    if (rule.stroke) {
      rule.stroke = migrateColorValue(rule.stroke);
    }
    (rule.variants || []).forEach((variant) => {
      if (variant.stroke) {
        variant.stroke = migrateColorValue(variant.stroke);
      }
    });
  });
  walkObject(doc.elementMappings, (node) => {
    if (Object.hasOwn(node, "color")) {
      node.color = migrateColorValue(node.color);
    }
  });
}

for (const level of LEVELS) {
  const path = join(ROOT, "mde/notation", `${level}.cvs.json`);
  const doc = JSON.parse(readFileSync(path, "utf8"));
  migrateDoc(doc);
  writeFileSync(path, `${JSON.stringify(doc, null, 2)}\n`, "utf8");
  console.log(`Updated theme colors in ${path}`);
}
