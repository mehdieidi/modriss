import { METAMODEL } from "./constants.js";
import { normalizeThemeColor } from "./theme-colors.js";

/** @type {{ doc: object|null, fileName: string, dirty: boolean, selection: object, filters: object }} */
export const appState = {
  doc: null,
  fileName: "",
  dirty: false,
  selection: { section: "overview", index: -1, subIndex: -1 },
  filters: { elementQuery: "", category: "all" },
};

export function levelFromDoc(doc) {
  return String(doc?.metamodelRef?.level || "cim").toLowerCase();
}

export function createEmptyDoc(level = "cim") {
  const meta = METAMODEL[level] || METAMODEL.cim;
  return {
    cvsVersion: 2,
    displayName: meta.displayName,
    metamodelRef: {
      level,
      ecore: meta.ecore,
      nsUri: meta.nsUri,
    },
    primitives: {
      "concept-card": {
        geometry: "rounded-rectangle",
        cornerRadius: 12,
        description: "Default concept card",
      },
    },
    notationPrimitives: {},
    elementVisualDefaults: {
      icon: "category",
      color: { light: "#475569", dark: "#94a3b8" },
      category: meta.displayName,
      notation: {
        tag: meta.displayName,
        shape: "concept-card",
        lineFields: ["name", "summary"],
      },
    },
    elementVisualRules: [],
    elementOverrides: [],
    referenceMappings: [],
    relationshipMappings: [],
    relationshipRules: [],
    relationshipKinds: ["CONTAINS", "DEPENDS_ON", "TRACE"],
    relationshipKindLabels: {},
    relationshipVisualRules: [],
    semanticReferenceRules: [],
    semanticEdgeObjectRules: [],
    badgeRules: [],
    viewpoints: [
      {
        id: "main",
        displayName: "Main Canvas",
        viewType: "MAIN",
        viewpoint: "main",
        elementTypes: [],
        palette: [],
        relationshipKinds: [],
        layoutHint: "DEFAULT_LAYERED",
      },
    ],
    canvasPolicy: {
      roleSizes: {
        node: { width: 120, height: 118 },
        container: { width: 316, height: 168 },
        detail: { width: 228, height: 84 },
      },
      lowDetailBelow: 0.42,
      highDetailAtOrAbove: 1.35,
      edgeLabelsAtOrAbove: 0.8,
      denseEdgeThreshold: 700,
      denseEdgeLabelsAtOrAbove: 1.3,
      veryDenseEdgeThreshold: 1600,
      veryDenseEdgeLabelsAtOrAbove: 1.7,
    },
    boundedContext: { enabled: false },
    complexityManagement: [],
    workbench: {},
    scaffoldRecipes: [],
    constraints: [],
    strictnessModes: ["exploration", "methodology", "production"],
    rootTemplate: {
      eClass: meta.rootEClass,
      modelLevel: meta.modelLevel,
      name: "",
      diagram: { elements: [], relationships: [] },
      graph: {
        elements: [],
        relationships: [],
        traceLinks: [],
        assumptions: [],
        validationIssues: [],
        manualBacklog: [],
      },
      views: [],
      fragments: [],
    },
  };
}

export function normalizeLoadedDoc(raw) {
  const doc = structuredClone(raw);
  if (!doc.cvsVersion) {
    doc.cvsVersion = 2;
  }
  if (!doc.notationPrimitives || !Object.keys(doc.notationPrimitives).length) {
    doc.notationPrimitives = { ...(doc.primitives || {}) };
  }
  doc.elementOverrides ??= [];
  doc.elementVisualRules ??= [];
  doc.viewpoints ??= [];
  doc.primitives ??= {};
  doc.canvasPolicy ??= createEmptyDoc(levelFromDoc(doc)).canvasPolicy;
  return doc;
}

export function setDoc(doc, fileName = "") {
  appState.doc = normalizeLoadedDoc(doc);
  appState.fileName = fileName;
  appState.dirty = false;
  syncNotationPrimitives();
}

export function syncNotationPrimitives() {
  const doc = appState.doc;
  if (!doc) {
    return;
  }
  doc.notationPrimitives = { ...(doc.primitives || {}) };
}

export function markDirty() {
  appState.dirty = true;
  document.getElementById("dirtyBadge")?.classList.toggle("hidden", false);
}

export function clearDirty() {
  appState.dirty = false;
  document.getElementById("dirtyBadge")?.classList.toggle("hidden", true);
}

export function mutate(mutator) {
  mutator(appState.doc);
  syncNotationPrimitives();
  markDirty();
}

export function exportFileName() {
  if (appState.fileName) {
    return appState.fileName.replace(/\.json$/i, "") + ".cvs.json";
  }
  const level = levelFromDoc(appState.doc);
  return `${level}.cvs.json`;
}

export function elementCategories() {
  const doc = appState.doc;
  if (!doc) {
    return [];
  }
  const cats = new Set();
  (doc.elementOverrides || []).forEach((el) => {
    if (el?.category) {
      cats.add(el.category);
    }
  });
  return [...cats].sort();
}

export function resolveElementVisual(element) {
  const doc = appState.doc;
  const defaults = doc?.elementVisualDefaults || {};
  const defaultColor = normalizeThemeColor(defaults.color);
  const merged = {
    icon: defaults.icon || "category",
    color: defaultColor,
    category: defaults.category || "",
    visualRole: element?.visualRole || "node",
    primitive: element?.primitive || defaults.notation?.shape || "concept-card",
    tag: element?.card?.tag || defaults.notation?.tag || "",
    lineFields: element?.card?.lineFields || defaults.notation?.lineFields || [],
    label: element?.displayName || element?.label || element?.type || "Element",
  };
  if (element?.icon) merged.icon = element.icon;
  if (element?.color) merged.color = normalizeThemeColor(element.color);
  if (element?.category) merged.category = element.category;
  if (element?.primitive) merged.primitive = element.primitive;
  if (element?.card?.tag) merged.tag = element.card.tag;
  return merged;
}
