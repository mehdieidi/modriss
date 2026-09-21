#!/usr/bin/env node
/**
 * Migrates *-ui-metadata.json to CVS v2 notation files under mde/notation/.
 *
 * Usage: node tools/notation-migrate/migrate-ui-to-cvs.mjs [cim|pim|psm]
 */
import { readFileSync, writeFileSync, mkdirSync } from "node:fs";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const __dirname = dirname(fileURLToPath(import.meta.url));
const repoRoot = resolve(__dirname, "../..");

const METAMODEL = {
  cim: {
    ecore: "mde/metamodels/cim/cim-combined.ecore",
    nsUri: "https://modriss.org/cim/1.0",
  },
  pim: {
    ecore: "mde/metamodels/pim/pim-combined.ecore",
    nsUri: "https://modriss.org/pim/1.0",
  },
  psm: {
    ecore: "mde/metamodels/psm/psm-combined.ecore",
    nsUri: "https://modriss.org/psm/aws/1.0",
  },
};

function referenceMappingsFromRules(semanticReferenceRules = []) {
  return semanticReferenceRules.map((rule) => ({
    sourceType: rule.sourceType,
    targetType: rule.targetType,
    eReference: rule.feature,
    edgeKind: rule.kind,
    directed: rule.reverse !== true,
  }));
}

function relationshipMappingsFromRules(semanticEdgeObjectRules = []) {
  return semanticEdgeObjectRules.map((rule) => ({
    eClass: rule.eClass,
    visualRole: "relationship",
    edgeKind: Array.isArray(rule.matchKinds) ? rule.matchKinds[0] : rule.kind,
    sourceType: rule.sourceType,
    targetType: rule.targetType,
  }));
}

function viewsFromViewDefinitions(viewDefinitions = []) {
  return viewDefinitions.map((view) => {
    const visibleTypes = view.palette?.length ? view.palette : view.elementTypes || [];
    return {
      id: view.id,
      displayName: view.displayName,
      viewType: view.viewType,
      palette: visibleTypes,
      canvas: visibleTypes,
      relationshipKinds: view.relationshipKinds || [],
      layoutHint: view.layoutHint,
    };
  });
}

function elementsFromUiElements(elements = []) {
  return elements
    .filter((item) => item && item.type)
    .map((item) => {
      const notation = { ...(item.notation || {}) };
      if (item.primitive && !notation.shape) notation.shape = item.primitive;
      if (item.card && typeof item.card === "object") {
        notation.tag ??= item.card.tag;
        notation.lineFields ??= item.card.lineFields;
        notation.detailFields ??= item.card.detailFields;
      }
      return {
        type: item.type,
        label: item.label,
        displayName: item.displayName,
        icon: item.icon,
        color: item.color,
        category: item.category,
        visualRole: item.visualRole,
        creatable: item.creatable,
        supportOnly: item.supportOnly,
        containedOnly: item.containedOnly,
        relationshipElement: item.relationshipElement,
        visibleFields: item.visibleFields || notation.lineFields || [],
        ...(Object.keys(notation).length ? { notation } : {}),
      };
    });
}

function elementVisualRulesFromUiRules(rules = []) {
  return rules.map((rule) => {
    const metadata = { ...(rule.metadata || {}) };
    return { ...rule, metadata };
  });
}

function containersFromUiElements(elements = []) {
  return elements
    .filter((item) => item && item.type && (item.visualRole === "container" || item.containmentPaletteExtras))
    .map((item) => ({
      elementType: item.type,
      palette: item.containmentPaletteExtras || [],
      canvas: item.containmentPaletteExtras || [],
      relationshipKinds: [],
      layoutHint: "CONTAINER",
    }));
}

function migrateLevel(level) {
  const uiPath = join(
    repoRoot,
    "packages/java/platform-modeling/src/main/resources/modeling",
    `${level}-ui-metadata.json`,
  );
  const ui = JSON.parse(readFileSync(uiPath, "utf8"));
  const meta = METAMODEL[level];
  if (!meta) {
    throw new Error(`Unknown level: ${level}`);
  }

  const cvs = {
    cvsVersion: 2,
    displayName: ui.displayName || level.toUpperCase(),
    metamodelRef: {
      level,
      ecore: meta.ecore,
      nsUri: meta.nsUri,
    },
    elementVisualDefaults: (() => {
      return { ...(ui.elementVisualDefaults || {}) };
    })(),
    elements: elementsFromUiElements(ui.elements || []),
    containers: containersFromUiElements(ui.elements || []),
    badgeRules: ui.badgeRules || [],
    referenceMappings: referenceMappingsFromRules(ui.semanticReferenceRules || []),
    relationshipMappings: relationshipMappingsFromRules(ui.semanticEdgeObjectRules || []),
    relationshipRules: ui.relationshipRules || [],
    relationshipKinds: ui.relationshipKinds || [],
    relationshipSemantics: ui.relationshipSemantics || {},
    relationshipLabelFields: ui.relationshipLabelFields || ["name"],
    relationshipKindLabels: ui.relationshipKindLabels || {},
    relationshipVisualRules: ui.relationshipVisualRules || [],
    semanticReferenceRules: ui.semanticReferenceRules || [],
    semanticEdgeObjectRules: ui.semanticEdgeObjectRules || [],
    semanticReferenceKindMappings: ui.semanticReferenceKindMappings || {},
    semanticReferenceExclusions: ui.semanticReferenceExclusions || [],
    shortcutConnectorRules: ui.shortcutConnectorRules || [],
    views: viewsFromViewDefinitions(ui.viewDefinitions || []),
    elementVisualRules: elementVisualRulesFromUiRules(ui.elementVisualRules || []),
    canvasPolicy: ui.canvasPolicy || {},
    boundedContext: ui.boundedContext || {},
    complexityManagement: ui.complexityManagement || [],
    workbench: ui.workbench || {},
    scaffoldRecipes: ui.scaffoldRecipes || [],
    constraints: ui.constraints || [],
    strictnessModes: ui.strictnessModes || ["exploration", "methodology", "production"],
    rootTemplate: ui.rootTemplate,
    starterTemplate: ui.starterTemplate,
  };

  const outDir = join(repoRoot, "mde/notation");
  mkdirSync(outDir, { recursive: true });
  const outPath = join(outDir, `${level}.cvs.json`);
  writeFileSync(outPath, `${JSON.stringify(cvs, null, 2)}\n`, "utf8");
  console.log(`Wrote ${outPath}`);
}

const level = (process.argv[2] || "cim").toLowerCase();
migrateLevel(level);
