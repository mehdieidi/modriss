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
    nsUri: "https://varka.org/cim/1.0",
  },
  pim: {
    ecore: "mde/metamodels/pim/pim-combined.ecore",
    nsUri: "https://varka.org/pim/1.0",
  },
  psm: {
    ecore: "mde/metamodels/psm/psm-combined.ecore",
    nsUri: "https://varka.org/psm/aws/1.0",
  },
};

function enrichPrimitives(notationPrimitives = {}) {
  const primitives = {};
  for (const [key, value] of Object.entries(notationPrimitives)) {
    primitives[key] = { ...value };
  }
  return primitives;
}

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

function viewpointsFromViewDefinitions(viewDefinitions = []) {
  return viewDefinitions.map((view) => ({
    id: view.id,
    displayName: view.displayName,
    viewType: view.viewType,
    viewpoint: view.viewpoint,
    elementTypes: view.elementTypes || [],
    palette: view.palette || [],
    relationshipKinds: view.relationshipKinds || [],
    layoutHint: view.layoutHint,
  }));
}

function elementOverridesFromUiElements(elements = []) {
  return elements
    .filter((item) => item && item.type)
    .map((item) => {
      const notation = item.notation || {};
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
        primitive: notation.shape,
        card: {
          tag: notation.tag,
          lineFields: notation.lineFields || [],
          detailFields: notation.detailFields || notation.lineFields || [],
        },
      };
    });
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

  const primitives = enrichPrimitives(ui.notationPrimitives || {});

  const cvs = {
    cvsVersion: 2,
    displayName: ui.displayName || level.toUpperCase(),
    metamodelRef: {
      level,
      ecore: meta.ecore,
      nsUri: meta.nsUri,
    },
    primitives,
    notationPrimitives: ui.notationPrimitives || {},
    elementVisualDefaults: ui.elementVisualDefaults || {},
    elementVisualRules: ui.elementVisualRules || [],
    elementOverrides: elementOverridesFromUiElements(ui.elements || []),
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
    relationshipSemantics: ui.relationshipSemantics || {},
    relationshipLabelFields: ui.relationshipLabelFields || ["name"],
    viewpoints: viewpointsFromViewDefinitions(ui.viewDefinitions || []),
    canvasPolicy: ui.canvasPolicy || {},
    boundedContext: ui.boundedContext || {},
    complexityManagement: ui.complexityManagement || [],
    workbench: ui.workbench || {},
    scaffoldRecipes: ui.scaffoldRecipes || [],
    constraints: ui.constraints || [],
    strictnessModes: ui.strictnessModes || ["exploration", "methodology", "production"],
    universalSyntax: ui.universalSyntax || [],
    kernelSyntax: ui.kernelSyntax || [],
    kernelNotation: ui.kernelNotation || ui.kernelSyntax || [],
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
