import {emptyDiagram} from './utils.js';

// ── Mutable application state (singleton) ─────────────────────────────────────
export const state = {
  auth: {
    token: null,
    user: null
  },
  project: null,   // active Project object from backend
  activeType: "cim",
  modelId: null,
  baseModel: null,
  diagram: emptyDiagram("cim"),
  nodesById: new Map(),
  graph: {
    elementsById: new Map(),
    relationshipsById: new Map(),
    traceLinksById: new Map(),
    assumptionsById: new Map(),
    relationshipsBySource: new Map(),
    relationshipsByTarget: new Map(),
    relationshipsByKind: new Map(),
    containmentByParent: new Map(),
    parentByChild: new Map()
  },
  views: {
    byId: new Map(),
    activeViewId: null,
    visibleNodeIds: new Set(),
    visibleRelationshipIds: new Set(),
    expandedContainers: new Set(),
    collapsedContainers: new Set()
  },
  fragments: {
    byId: new Map(),
    rootIds: []
  },
  visibleGraph: emptyDiagram("cim"),
  modelingStrictness: "methodology",
  modelingToolsMinimized: false,
  // Per-tab persisted state (save/restore on tab switch)
  tabs: {
    cim: {
      modelId: null,
      baseModel: null,
      diagram: emptyDiagram("cim"),
      modelName: "cim-model",
      graph: null,
      views: null,
      fragments: null,
      activeViewId: null
    },
    pim: {
      modelId: null,
      baseModel: null,
      diagram: emptyDiagram("pim"),
      modelName: "pim-model",
      graph: null,
      views: null,
      fragments: null,
      activeViewId: null
    },
    psm: {
      modelId: null,
      baseModel: null,
      diagram: emptyDiagram("psm"),
      modelName: "psm-model",
      graph: null,
      views: null,
      fragments: null,
      activeViewId: null
    }
  },
  modelsCache: {cim: [], pim: [], psm: []},
  modelingConfig: {
    config: null,
    draft: null
  },
  viewport: {x: 0, y: 0, scale: 1},
  connectMode: false,
  connectSourceId: null,
  preferredConnectionKind: null,
  linkDrag: null,
  dragNode: null,
  panDrag: null,
  touchTap: null,
  chat: {
    sessions: new Map(),
    channels: new Map(),
    attachment: null
  },
  artifact: {
    id: null,
    name: "",
    files: [],
    activeFile: null,
    dirty: false,
    treeCollapsed: false
  },
  github: {
    connected: false,
    githubLogin: "",
    selectedRepository: "",
    selectedBranch: "",
    lastDeploymentStatus: "",
    lastDeploymentMessage: "",
    lastDeploymentAt: "",
    repositoryUrl: "",
    commitUrl: "",
    loading: false
  },
  admin: {
    scopes: [],
    scope: null,
    currentPath: "",
    entries: [],
    selectedEntry: null,
    activeFile: null,
    dirty: false
  },
  selectedNodeId: null,
  selectedNodeIds: new Set(),
  hoveredNodeId: null,
  inlineLabelEditNodeId: null,
  selectedBoundedContextName: null,
  selectedConnectionId: null,
  boundedContextCreateMode: false,
  boundedContextDraftNodeIds: new Set(),
  boundedContextDraftName: "",
  boundedContextViewMode: "normal",
  activeBoundedContextName: "",
  dragBoundedContext: null,
  impactMode: false,
  impactData: null,
  paletteCollapsed: false,
  paletteSearch: {
    cim: "",
    pim: "",
    psm: ""
  },
  cimWorkbench: {
    representationByViewId: {},
    registerByViewId: {},
    search: "",
    missingOnly: false,
    sliceKind: "",
    sliceValue: "",
    activeLens: "all",
    edgeMode: "both",
    sortKey: "name",
    hidPalette: false
  },
  pimWorkbench: {
    representationByViewId: {},
    registerByViewId: {},
    search: "",
    missingOnly: false,
    sliceKind: "",
    sliceValue: "",
    sortKey: "name",
    hidPalette: false
  },
  psmWorkbench: {
    representationByViewId: {},
    registerByViewId: {},
    search: "",
    missingOnly: false,
    sliceKind: "",
    sliceValue: "",
    activeLens: "all",
    edgeMode: "both",
    sortKey: "name",
    hidPalette: false
  },
  paletteGroupCollapsed: {
    cim: {},
    pim: {},
    psm: {}
  },
  autoSaveTimer: null,
  collaboration: {
    ws: null,
    projectId: null,
    sessionId: null,
    isReady: false,
    pendingDiagramUpdate: false,
    reconnectTimer: null,
    reconnectAttempts: 0,
    intentionalDisconnect: false,
    revision: 0,
    remoteApplying: false,
    lastDiagramPublishAt: 0,
    cursorThrottleAt: 0,
    diagramThrottleTimer: null,
    participants: []
  },
  validation: {
    issues: [],
    inProgress: false,
    panelOpen: false,
    firstIssueShown: false,
    lastValidatedAt: null,
    manualView: "open"
  },
  edgeKindPicker: {
    open: false,
    edgeId: null,
    x: 0,
    y: 0,
    options: []
  },
  undo: {
    modelReplacements: [],
    diagramHistory: {
      cim: [],
      pim: [],
      psm: []
    }
  }
};
