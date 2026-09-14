import { emptyDiagram } from "./utils.js";

const LAST_MODELING_TYPE_STORAGE_PREFIX = "modriss.lastModelingType";

function modelingTypeStorageKey(projectId) {
  const userId = state.auth?.user?.id || state.auth?.user?.email || "anonymous";
  return `${LAST_MODELING_TYPE_STORAGE_PREFIX}:${String(userId).toLowerCase()}:${String(
    projectId || "",
  )}`;
}

export function saveLastModelingType(projectId, type) {
  if (!projectId || !type || typeof window === "undefined" || !window.localStorage) {
    return;
  }
  window.localStorage.setItem(modelingTypeStorageKey(projectId), String(type));
}

export function readLastModelingType(projectId) {
  if (!projectId || typeof window === "undefined" || !window.localStorage) {
    return "";
  }
  return window.localStorage.getItem(modelingTypeStorageKey(projectId)) || "";
}

// ── Mutable application state (singleton) ─────────────────────────────────────
export const state = {
  auth: {
    token: null,
    user: null,
  },
  project: null, // active Project object from backend
  activeType: "",
  modelId: null,
  modelRevision: 0,
  baseModel: null,
  modelSave: {
    dirty: false,
    saving: false,
    lastSavedAt: null,
    error: "",
  },
  diagram: emptyDiagram(""),
  nodesById: new Map(),
  connectionsById: new Map(),
  graph: {
    elementsById: new Map(),
    relationshipsById: new Map(),
    traceLinksById: new Map(),
    assumptionsById: new Map(),
    relationshipsBySource: new Map(),
    relationshipsByTarget: new Map(),
    relationshipsByKind: new Map(),
    containmentByParent: new Map(),
    parentByChild: new Map(),
  },
  views: {
    byId: new Map(),
    activeViewId: null,
    visibleNodeIds: new Set(),
    visibleRelationshipIds: new Set(),
    expandedContainers: new Set(),
  },
  fragments: {
    byId: new Map(),
    rootIds: [],
  },
  visibleGraph: emptyDiagram(""),
  modelingStrictness: "methodology",
  // Per-tab persisted state (save/restore on tab switch)
  tabs: {},
  modelsCache: {},
  modelingConfig: {
    config: null,
    draft: null,
  },
  viewport: { x: 0, y: 0, scale: 1 },
  connectMode: false,
  connectSourceId: null,
  preferredConnectionKind: null,
  linkDrag: null,
  dragNode: null,
  panDrag: null,
  touchTap: null,
  chat: {
    available: true,
    sessions: new Map(),
    channels: new Map(),
    attachments: [],
    historyOpen: false,
  },
  assistantPreview: null,
  artifact: {
    id: null,
    name: "",
    files: [],
    activeFile: null,
    dirty: false,
    treeCollapsed: false,
  },
  admin: {
    unavailable: false,
    scopes: [],
    scope: null,
    currentPath: "",
    entries: [],
    selectedEntry: null,
    activeFile: null,
    dirty: false,
  },
  selectedNodeId: null,
  selectedNodeIds: new Set(),
  selectedRootModel: false,
  hoveredNodeId: null,
  inlineLabelEditNodeId: null,
  selectedConnectionId: null,
  canvasFocusStack: [],
  impactMode: false,
  impactData: null,
  issueLocateTargetId: null,
  paletteCollapsed: false,
  paletteDragType: "",
  paletteSearch: {},
  paletteGroupCollapsed: {},
  leftPaneMode: "palette",
  guidedModeling: {
    definitions: {},
    endToEnd: null,
    progress: { completedTaskIds: [], activePhaseId: null },
    loading: false,
  },
  validation: {
    issues: [],
    inProgress: false,
    panelOpen: false,
    firstIssueShown: false,
    lastValidatedAt: null,
    issueView: "errors",
  },
  edgeKindPicker: {
    open: false,
    menuOpen: false,
    edgeId: null,
    drawnFromId: null,
    drawnToId: null,
    x: 0,
    y: 0,
    options: [],
  },
  undo: {
    modelReplacements: [],
    diagramHistory: {},
  },
};
