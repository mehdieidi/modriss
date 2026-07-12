export const LEVELS = ["cim", "pim", "psm"];

export const METAMODEL = {
  cim: {
    displayName: "CIM",
    ecore: "mde/metamodels/cim/cim-combined.ecore",
    nsUri: "https://varka.org/cim/1.0",
    rootEClass: "CIMModel",
    modelLevel: "CIM",
  },
  pim: {
    displayName: "PIM",
    ecore: "mde/metamodels/pim/pim-combined.ecore",
    nsUri: "https://varka.org/pim/1.0",
    rootEClass: "PIMModel",
    modelLevel: "PIM",
  },
  psm: {
    displayName: "PSM",
    ecore: "mde/metamodels/psm/psm-combined.ecore",
    nsUri: "https://varka.org/psm/aws/1.0",
    rootEClass: "PSMModel",
    modelLevel: "PSM",
  },
};

export const VISUAL_ROLES = ["node", "container", "detail", "support", "relationship"];

export const GEOMETRIES = [
  "rectangle",
  "rounded-rectangle",
  "diamond",
  "hexagon",
  "octagon",
  "trapezoid",
  "ellipse",
];

export const SHAPE_PRESETS = [
  "concept-card",
  "workspace-container",
  "participant-card",
  "class-card",
  "behavior-node",
  "process-node",
  "constraint-badge-card",
  "dashboard-row",
  "goal-card",
  "metric-card",
  "api-card",
  "function-card",
  "container-card",
];

export const ICON_PATH = "../../../apps/frontend/assets/icons";

export const COMMON_ICONS = [
  "dashboard",
  "category",
  "flag",
  "analytics",
  "database",
  "bolt",
  "schema",
  "shield",
  "groups",
  "person",
  "api",
  "functions",
  "event",
  "route",
  "memory",
  "cloud",
  "settings",
  "security",
  "link",
  "track_changes",
  "fact_check",
  "tag",
  "article",
  "folder",
  "map",
  "timeline",
  "gavel",
  "warning",
  "check_circle",
  "deploy",
  "lan",
  "rss_feed",
  "inventory",
  "monitoring",
  "key",
  "public",
  "extension",
  "placeholder",
];

export const LAYOUT_HINTS = [
  "DEFAULT_LAYERED",
  "CONTAINER",
  "PROCESS",
  "GOVERNANCE",
  "SPACIOUS_LAYERED",
  "TREE",
  "RADIAL",
];

export const MARKER_ENDS = [
  "",
  "arrow",
  "triangle-hollow",
  "triangle-filled",
  "diamond-filled",
  "diamond-hollow",
];
