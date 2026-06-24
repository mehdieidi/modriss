import "reflect-metadata";
import {
  DiagramLoader,
  EnableDefaultToolsAction,
  GEdge,
  RepositionAction,
  ShowGridAction,
  STANDALONE_MODULE_CONFIG,
  TYPES,
  ZoomAction,
  baseViewModule,
  configureActionHandler,
  createDiagramOptionsModule,
  initializeDiagramContainer,
  overrideModelElement,
} from "@eclipse-glsp/client";
import { gridModule } from "@eclipse-glsp/client/lib/features/grid/grid-module.js";
import {
  DefaultTypes,
  LayoutOperation,
  RedoAction,
  RejectAction,
  SelectAction,
  SetContextActions,
  UndoAction,
} from "@eclipse-glsp/protocol";
import {
  Action,
  BaseJsonrpcGLSPClient,
  FeatureModule,
  FitToScreenAction,
  GNode,
  GLabel,
  ICommand,
  IActionHandler,
  IContextMenuService,
  RequestAction,
  RequestModelAction,
  SetModelAction,
  SetViewportAction,
  UpdateModelAction,
} from "@eclipse-glsp/sprotty";
import { GLSPWebSocketProvider } from "@eclipse-glsp/protocol/lib/client-server-protocol/jsonrpc/ws-connection-provider.js";
import { Container, inject, injectable } from "inversify";
import { ModlessEdgeView } from "./views/modless-edge-view.js";
import { ModlessLabelView } from "./views/modless-label-view.js";
import { ModlessNodeView } from "./views/modless-node-view.js";
import { resolveBackendUrl } from "./bridge/session-options.js";
import { installInteractionBridge } from "./bridge/interaction-bridge.js";
import { scheduleCanvasBoundsSync, syncCanvasBounds } from "./bridge/canvas-bounds.js";

export const MODLESS_DIAGRAM_TYPE = "modless-diagram";

const DIAGRAM_LOAD_TIMEOUT_MS = 45_000;

function collectDiagramNodeIds(diagramRoot: HTMLElement | null, clientId = ""): string[] {
  if (!diagramRoot) {
    return [];
  }
  const prefix = clientId ? `${clientId}_` : "";
  return [...diagramRoot.querySelectorAll<SVGElement>(".modless-node, g.sprotty-node")]
    .map((node) => {
      const rawId = node.id || "";
      if (prefix && rawId.startsWith(prefix)) {
        return rawId.slice(prefix.length);
      }
      return rawId;
    })
    .filter((id) => Boolean(id) && !id.endsWith("-port-n") && !id.endsWith("-port-s"));
}

function extractNodesFromModelRoot(root: unknown): DiagramNodeLike[] {
  const nodes: DiagramNodeLike[] = [];
  const visit = (element: unknown): void => {
    if (!element || typeof element !== "object") {
      return;
    }
    const model = element as {
      type?: string;
      children?: unknown[];
      position?: { x?: number; y?: number };
      size?: { width?: number; height?: number };
    };
    if (model.type === "node") {
      nodes.push({
        x: model.position?.x,
        y: model.position?.y,
        width: model.size?.width,
        height: model.size?.height,
      });
    }
    for (const child of model.children || []) {
      visit(child);
    }
  };
  visit(root);
  return nodes;
}

function fitViewportFromDiagram(
  diagramRoot: HTMLElement,
  nodes: DiagramNodeLike[],
  actionDispatcher: { dispatch: (action: unknown) => void | Promise<void> },
): boolean {
  if (!nodes.length) {
    return false;
  }
  let minX = Infinity;
  let minY = Infinity;
  let maxX = -Infinity;
  let maxY = -Infinity;
  for (const node of nodes) {
    const width = Number(node.width) || 228;
    const height = Number(node.height) || 112;
    const x = Number(node.x) || 0;
    const y = Number(node.y) || 0;
    minX = Math.min(minX, x);
    minY = Math.min(minY, y);
    maxX = Math.max(maxX, x + width);
    maxY = Math.max(maxY, y + height);
  }
  if (!Number.isFinite(minX) || !Number.isFinite(minY)) {
    return false;
  }
  const boundsWidth = Math.max(1, maxX - minX);
  const boundsHeight = Math.max(1, maxY - minY);
  const centerX = minX + boundsWidth / 2;
  const centerY = minY + boundsHeight / 2;
  const rect = diagramRoot.getBoundingClientRect();
  const padding = 32;
  const zoom = Math.min(
    1.25,
    Math.max(
      0.05,
      Math.min(
        (Math.max(rect.width, 1) - padding * 2) / boundsWidth,
        (Math.max(rect.height, 1) - padding * 2) / boundsHeight,
      ),
    ),
  );
  actionDispatcher.dispatch(
    SetViewportAction.create(
      "root",
      {
        scroll: {
          x: centerX - (Math.max(rect.width, 1) / 2) / zoom,
          y: centerY - (Math.max(rect.height, 1) / 2) / zoom,
        },
        zoom,
      },
      { animate: false },
    ),
  );
  return true;
}

function fitDiagramToScreen(
  actionDispatcher: { dispatch: (action: unknown) => void | Promise<void> },
  diagramRoot: HTMLElement | null = null,
  clientId = "",
  nodes: DiagramNodeLike[] = [],
): void {
  if (nodes.length && diagramRoot && fitViewportFromDiagram(diagramRoot, nodes, actionDispatcher)) {
    return;
  }
  const nodeIds = collectDiagramNodeIds(diagramRoot, clientId);
  if (!nodeIds.length) {
    return;
  }
  actionDispatcher.dispatch(
    FitToScreenAction.create(nodeIds, { padding: 32, animate: false, maxZoom: 1.25 }),
  );
}

function hideDiagramGrid(
  actionDispatcher: { dispatch: (action: unknown) => void | Promise<void> },
): void {
  actionDispatcher.dispatch(ShowGridAction.create({ show: false }));
}

function scheduleDiagramFit(
  boundsElement: HTMLElement,
  actionDispatcher: { dispatch: (action: unknown) => void | Promise<void> },
): () => void {
  const timers: number[] = [];
  const run = () => syncCanvasBounds(boundsElement, actionDispatcher);
  for (const delay of [120, 600, 1500]) {
    timers.push(window.setTimeout(run, delay));
  }
  return () => {
    for (const timer of timers) {
      window.clearTimeout(timer);
    }
  };
}

function withTimeout<T>(promise: Promise<T>, ms: number, label: string): Promise<T> {
  return new Promise<T>((resolve, reject) => {
    const timer = window.setTimeout(() => {
      reject(new Error(`${label} timed out after ${Math.round(ms / 1000)}s`));
    }, ms);
    promise
      .then((value) => {
        window.clearTimeout(timer);
        resolve(value);
      })
      .catch((error) => {
        window.clearTimeout(timer);
        reject(error);
      });
  });
}

type ModlessCallbacks = {
  onNodeClick?: (nodeId: string) => void;
  onNodeDoubleClick?: (nodeId: string) => void;
  onEdgeClick?: (edgeId: string) => void;
  onCanvasClick?: () => void;
  onViewportChange?: () => void;
  resolveSelectionKind?: (elementId: string) => "node" | "edge";
};

const bridge = {
  callbacks: {} as ModlessCallbacks,
  setCallbacks(callbacks: ModlessCallbacks = {}): void {
    bridge.callbacks = { ...bridge.callbacks, ...callbacks };
  },
};

let sharedClient: BaseJsonrpcGLSPClient | null = null;
let sharedClientUrl = "";

function resetSharedGlspClient(): void {
  sharedClient = null;
  sharedClientUrl = "";
}

function glspSessionReady(options: Record<string, unknown>): boolean {
  return Boolean(
    String(options.level || "").trim() &&
      String(options.authToken || "").trim() &&
      String(options.modelId || "").trim(),
  );
}

function setHostStatus(host: HTMLElement, message: string): void {
  let status = host.querySelector<HTMLElement>(".glsp-canvas-status");
  if (!status) {
    status = document.createElement("div");
    status.className = "glsp-canvas-status";
    host.appendChild(status);
  }
  status.textContent = message;
  host.classList.remove("is-connected");
}

function modlessViewModule(): FeatureModule {
  return new FeatureModule(
    (bind, unbind, isBound, rebind) => {
      const context = { bind, unbind, isBound, rebind };
      if (!isBound(ModlessNodeView)) {
        bind(ModlessNodeView).toSelf().inSingletonScope();
      }
      if (!isBound(ModlessEdgeView)) {
        bind(ModlessEdgeView).toSelf().inSingletonScope();
      }
      if (!isBound(ModlessLabelView)) {
        bind(ModlessLabelView).toSelf().inSingletonScope();
      }
      overrideModelElement(context, DefaultTypes.NODE, GNode, ModlessNodeView);
      overrideModelElement(context, DefaultTypes.EDGE, GEdge, ModlessEdgeView);
      overrideModelElement(context, DefaultTypes.LABEL, GLabel, ModlessLabelView);
    },
    { featureId: Symbol("modlessView"), requires: [baseViewModule] },
  );
}

@injectable()
class ModlessModelFitHandler implements IActionHandler {
  #timer: number | null = null;
  #boundsElement: HTMLElement | null = null;
  #clientId = "";

  setBoundsElement(element: HTMLElement | null, clientId = ""): void {
    this.#boundsElement = element;
    this.#clientId = clientId;
  }

  latestNodes(): DiagramNodeLike[] {
    return this.#diagramNodes;
  }

  #diagramNodes: DiagramNodeLike[] = [];

  handle(action: Action): void | Action | ICommand {
    if (!SetModelAction.is(action) && !UpdateModelAction.is(action)) {
      return undefined;
    }
    if (!this.#boundsElement) {
      return undefined;
    }
    const modelNodes = extractNodesFromModelRoot(
      (action as { newRoot?: unknown }).newRoot,
    );
    if (modelNodes.length) {
      this.#diagramNodes = modelNodes;
    }
    if (this.#timer !== null) {
      window.clearTimeout(this.#timer);
    }
    const boundsElement = this.#boundsElement;
    const nodes = this.#diagramNodes;
    this.#timer = window.setTimeout(() => {
      this.#timer = null;
      syncCanvasBounds(boundsElement, this.actionDispatcher);
      fitDiagramToScreen(this.actionDispatcher, boundsElement, this.#clientId, nodes);
    }, 200);
    return undefined;
  }

  constructor(
    @inject(TYPES.IActionDispatcher)
    private readonly actionDispatcher: { dispatch: (action: unknown) => void | Promise<void> },
  ) {}
}

@injectable()
class NoOpSetContextActionsHandler implements IActionHandler {
  handle(_action: Action): void | Action | ICommand {
    return undefined;
  }
}

@injectable()
class OrphanRejectActionHandler implements IActionHandler {
  handle(action: Action): void {
    if (!RejectAction.is(action)) {
      return;
    }
    console.warn("[GLSP-Client] Diagram request rejected:", action.message, action.detail || "");
  }
}

@injectable()
class ModlessSelectionBridgeHandler implements IActionHandler {
  handle(action: Action): void | Action | ICommand {
    if (!SelectAction.is(action)) {
      return undefined;
    }
    const selected = (action.selectedElementsIDs || []).map(String);
    if (!selected.length) {
      return undefined;
    }
    const first = selected[0];
    if (
      first.endsWith("-port-n") ||
      first.endsWith("-port-s") ||
      first.endsWith("-port-e") ||
      first.endsWith("-port-w")
    ) {
      return undefined;
    }
    const kind = bridge.callbacks.resolveSelectionKind?.(first) || "node";
    if (kind === "edge") {
      bridge.callbacks.onEdgeClick?.(first);
    } else {
      bridge.callbacks.onNodeClick?.(first);
    }
    return undefined;
  }
}

function modlessEmbedModule(): FeatureModule {
  const noopContextMenu: IContextMenuService = { show: () => {} };
  return new FeatureModule(
    (bind, unbind, isBound, rebind) => {
      const context = { bind, unbind, isBound, rebind };
      bind(NoOpSetContextActionsHandler).toSelf().inSingletonScope();
      bind(ModlessModelFitHandler).toSelf().inSingletonScope();
      bind(OrphanRejectActionHandler).toSelf().inSingletonScope();
      bind(ModlessSelectionBridgeHandler).toSelf().inSingletonScope();
      configureActionHandler(context, SetContextActions.KIND, NoOpSetContextActionsHandler);
      configureActionHandler(context, SetModelAction.KIND, ModlessModelFitHandler);
      configureActionHandler(context, UpdateModelAction.KIND, ModlessModelFitHandler);
      configureActionHandler(context, RejectAction.KIND, OrphanRejectActionHandler);
      configureActionHandler(context, SelectAction.KIND, ModlessSelectionBridgeHandler);
      if (!isBound(TYPES.IContextMenuService)) {
        bind(TYPES.IContextMenuService).toConstantValue(noopContextMenu);
      }
    },
    { featureId: Symbol("modlessEmbed") },
  );
}

async function createGlspClient(serverUrl: string): Promise<BaseJsonrpcGLSPClient> {
  if (sharedClient && sharedClientUrl === serverUrl) {
    return sharedClient;
  }

  const provider = new GLSPWebSocketProvider(serverUrl, { reconnecting: true });
  const client = new BaseJsonrpcGLSPClient({
    id: "modless-glsp-client",
    connectionProvider: () =>
      provider.listen({
        onConnection: (connection) => connection,
        onReconnect: (options) => {
          console.warn("[GLSP-Client] Reconnecting to", serverUrl, options);
        },
      }),
  });

  sharedClient = client;
  sharedClientUrl = serverUrl;
  return client;
}

export class ModlessGlspRenderer {
  #host: HTMLElement | null = null;
  #diagramRoot: HTMLElement | null = null;
  #mounted = false;
  #connected = false;
  #options: Record<string, unknown> = {};
  #container: Container | null = null;
  #clientId = `modless-diagram-${Math.random().toString(36).slice(2)}`;
  #actionDispatcher: { dispatch: (action: unknown) => void | Promise<void> } | null = null;
  #disposeInteractionBridge: (() => void) | null = null;
  #disposeCanvasBoundsSync: (() => void) | null = null;
  #disposeFitSchedule: (() => void) | null = null;
  #sessionKey = "";
  #mountTask: Promise<boolean> = Promise.resolve(false);
  #reloadTask: Promise<void> = Promise.resolve();

  isAvailable(): boolean {
    return true;
  }

  getEditor(): this | null {
    return this.#mounted && this.#connected ? this : null;
  }

  #sessionKeyFor(options: Record<string, unknown>): string {
    return [
      options.level || "",
      options.modelId || "",
      options.viewId || "",
      options.glspServerUrl || "",
    ].join(":");
  }

  async unmount(): Promise<void> {
    this.#disposeInteractionBridge?.();
    this.#disposeInteractionBridge = null;
    this.#disposeCanvasBoundsSync?.();
    this.#disposeCanvasBoundsSync = null;
    this.#disposeFitSchedule?.();
    this.#disposeFitSchedule = null;
    this.#container = null;
    this.#actionDispatcher = null;
    this.#diagramRoot = null;
    this.#connected = false;
    this.#mounted = false;
    this.#sessionKey = "";
    if (this.#host) {
      this.#host.replaceChildren();
      this.#host.id = "glspEditorHost";
      this.#host.classList.remove("is-mounted", "is-connected", "glsp-idle", "glsp-diagram-host");
    }
    this.#host = null;
    resetSharedGlspClient();
  }

  async mount(host: HTMLElement, options: Record<string, unknown> = {}): Promise<boolean> {
    this.#mountTask = this.#mountTask.then(() => this.#mountNow(host, options)).catch((error) => {
      console.error("[GLSP-Client] Diagram mount failed", error);
      return false;
    });
    return this.#mountTask;
  }

  updateMountOptions(options: Record<string, unknown> = {}): void {
    this.#options = { ...this.#options, ...options };
    const callbacks = (options.callbacks || this.#options.callbacks) as ModlessCallbacks | undefined;
    if (callbacks) {
      bridge.setCallbacks(callbacks);
    }
  }

  async #mountNow(host: HTMLElement, options: Record<string, unknown> = {}): Promise<boolean> {
    const nextSessionKey = this.#sessionKeyFor(options);
    bridge.setCallbacks((options.callbacks || {}) as ModlessCallbacks);

    if (!glspSessionReady(options)) {
      this.#host = host;
      this.#options = { ...options };
      this.#mounted = false;
      this.#connected = false;
      this.#sessionKey = "";
      host.replaceChildren();
      host.id = "glspEditorHost";
      host.classList.add("is-mounted", "glsp-idle");
      host.classList.remove("is-connected", "glsp-diagram-host");
      setHostStatus(host, "Open a project model to start the GLSP diagram.");
      return false;
    }

    if (this.#mounted && this.#connected && this.#sessionKey === nextSessionKey) {
      this.updateMountOptions(options);
      return true;
    }

    if (this.#mounted) {
      await this.unmount();
    }

    this.#host = host;
    this.#options = { ...options };
    this.#mounted = true;
    host.classList.add("is-mounted", "glsp-diagram-host");
    host.classList.remove("glsp-idle");
    host.id = "glspEditorHost";
    setHostStatus(host, "Connecting to GLSP diagram server…");

    try {
      host.replaceChildren();

      const diagramRoot = document.createElement("div");
      diagramRoot.id = this.#clientId;
      diagramRoot.className = "modless-glsp-diagram-root sprotty";
      diagramRoot.style.width = "100%";
      diagramRoot.style.height = "100%";
      diagramRoot.style.position = "absolute";
      diagramRoot.style.inset = "0";
      host.appendChild(diagramRoot);

      const hidden = document.createElement("div");
      hidden.id = `${this.#clientId}_hidden`;
      hidden.className = "sprotty-hidden";
      host.appendChild(hidden);

      const serverUrl = String(options.glspServerUrl || "ws://127.0.0.1:8081/modless");
      console.info("[GLSP-Client] Connecting diagram session", {
        serverUrl,
        level: options.level,
        modelId: options.modelId,
        viewId: options.viewId,
      });

      const glspClient = await createGlspClient(serverUrl);
      const glspClientProvider = () => Promise.resolve(glspClient);
      const container = new Container();
      initializeDiagramContainer(
        container,
        createDiagramOptionsModule({
          clientId: this.#clientId,
          diagramType: MODLESS_DIAGRAM_TYPE,
          glspClientProvider,
          sourceUri: this.#sourceUri(options),
          editMode: "editable",
        }),
        baseViewModule,
        gridModule,
        modlessViewModule(),
        modlessEmbedModule(),
        STANDALONE_MODULE_CONFIG,
      );

      this.#container = container;
      const loader = await container.getAsync(DiagramLoader);
      setHostStatus(host, "Loading diagram model…");
      await withTimeout(
        loader.load({
          requestModelOptions: {
            sourceUri: this.#sourceUri(options),
            level: options.level || "cim",
            modelId: options.modelId || "",
            viewId: options.viewId || "",
            authToken: options.authToken || "",
            backendUrl: resolveBackendUrl(options),
          },
        }),
        DIAGRAM_LOAD_TIMEOUT_MS,
        "GLSP diagram load",
      );

      this.#actionDispatcher = await container.getAsync(TYPES.IActionDispatcher);
      this.#diagramRoot = diagramRoot;
      const fitHandler = await container.getAsync(ModlessModelFitHandler);
      fitHandler.setBoundsElement(diagramRoot, this.#clientId);
      await this.#finalizeDiagramSession(diagramRoot, host);
      host.classList.add("is-connected");
      host.querySelector(".glsp-canvas-status")?.remove();
      this.#connected = true;
      this.#sessionKey = nextSessionKey;
      console.info("[GLSP-Client] Diagram session ready");
      return true;
    } catch (error) {
      host.replaceChildren();
      setHostStatus(
        host,
        `GLSP diagram failed: ${error instanceof Error ? error.message : String(error)}`,
      );
      this.#connected = false;
      this.#mounted = false;
      this.#sessionKey = "";
      this.#container = null;
      this.#actionDispatcher = null;
      return false;
    }
  }

  #sourceUri(options: Record<string, unknown>): string {
    const level = String(options.level || "cim");
    const modelId = String(options.modelId || "draft");
    return `modless://${level}/${modelId}`;
  }

  #fitViewport(nodes: DiagramNodeLike[] = []): void {
    if (!this.#actionDispatcher || !this.#diagramRoot) {
      return;
    }
    syncCanvasBounds(this.#diagramRoot, this.#actionDispatcher);
    fitDiagramToScreen(this.#actionDispatcher, this.#diagramRoot, this.#clientId, nodes);
  }

  async #finalizeDiagramSession(diagramRoot: HTMLElement, host: HTMLElement): Promise<void> {
    if (!this.#actionDispatcher) {
      return;
    }
    this.#disposeInteractionBridge?.();
    this.#disposeFitSchedule?.();
    const callbacks = this.#options.callbacks as ModlessCallbacks | undefined;
    this.#disposeInteractionBridge = installInteractionBridge({
      host,
      diagramRoot,
      actionDispatcher: this.#actionDispatcher,
      onViewportChange: () => {
        callbacks?.onViewportChange?.();
      },
    });
    this.#disposeCanvasBoundsSync?.();
    this.#disposeCanvasBoundsSync = scheduleCanvasBoundsSync(diagramRoot, this.#actionDispatcher);
    this.#actionDispatcher.dispatch(EnableDefaultToolsAction.create());
    hideDiagramGrid(this.#actionDispatcher);
    this.#actionDispatcher.dispatch(LayoutOperation.create());
    this.#disposeFitSchedule = scheduleDiagramFit(diagramRoot, this.#actionDispatcher);
    diagramRoot.setAttribute("tabindex", "0");
    diagramRoot.style.outline = "none";
    const focusDiagram = () => {
      const focusTarget =
        diagramRoot.querySelector<HTMLElement>(".sprotty-graph") ||
        diagramRoot.querySelector<HTMLElement>("svg") ||
        diagramRoot;
      focusTarget?.focus?.({ preventScroll: true });
    };
    window.requestAnimationFrame(() => {
      focusDiagram();
      hideDiagramGrid(this.#actionDispatcher!);
    });
    window.setTimeout(() => {
      this.#actionDispatcher?.dispatch(LayoutOperation.create());
      focusDiagram();
      void this.#container?.getAsync(ModlessModelFitHandler).then((handler) => {
        this.#fitViewport(handler.latestNodes());
      });
    }, 450);
    window.setTimeout(() => {
      void this.#container?.getAsync(ModlessModelFitHandler).then((handler) => {
        this.#fitViewport(handler.latestNodes());
      });
    }, 1400);
  }

  async syncFromState(options: Record<string, unknown> = {}): Promise<void> {
    this.#options = { ...this.#options, ...options };
    if (options.callbacks) {
      bridge.setCallbacks(options.callbacks as ModlessCallbacks);
    }
    const nextSessionKey = this.#sessionKeyFor(this.#options);
    if (!this.#connected || nextSessionKey !== this.#sessionKey) {
      if (this.#host) {
        await this.mount(this.#host, this.#options);
      }
      return;
    }
    if (options.refresh === true) {
      this.#reloadTask = this.#reloadTask
        .then(() => this.#requestModelRefresh())
        .catch((error) => {
          console.error("[GLSP-Client] Diagram refresh failed", error);
        });
      await this.#reloadTask;
    }
  }

  async #requestModelRefresh(): Promise<void> {
    if (!this.#actionDispatcher) {
      return;
    }
    await this.#actionDispatcher.dispatch(
      RequestModelAction.create({
        options: {
          sourceUri: this.#sourceUri(this.#options),
          level: this.#options.level || "cim",
          modelId: this.#options.modelId || "",
          viewId: this.#options.viewId || "",
          authToken: this.#options.authToken || "",
          backendUrl: resolveBackendUrl(this.#options),
        },
        requestId: RequestAction.generateRequestId(),
      }),
    );
  }

  updateSelection(): void {}
  updateNode(): void {
    void this.syncFromState({ refresh: true });
  }
  updateEdge(): void {
    void this.syncFromState({ refresh: true });
  }
  updateViewport(): void {}
  updateConnectionState(): void {}
  updateContextBoxes(): void {}
  updateImpactState(): void {}
  updateNodeIcons(): void {}
  refreshEdges(): void {}
  renderDiagram(): void {}
  resetCanvasView(): void {
    if (!this.#container) {
      this.#fitViewport();
      this.onViewportChanged();
      return;
    }
    void this.#container.getAsync(ModlessModelFitHandler).then((handler) => {
      this.#fitViewport(handler.latestNodes());
      this.onViewportChanged();
    });
  }

  zoomCanvasBy(multiplier = 1): void {
    const factor = multiplier > 1 ? 1.1 : 0.9;
    this.#actionDispatcher?.dispatch(ZoomAction.create({ zoomFactor: factor }));
    this.onViewportChanged();
  }
  fitCanvasToDiagram(): void {
    this.resetCanvasView();
  }
  focusNode(nodeId: string): void {
    this.#actionDispatcher?.dispatch(RepositionAction.create([nodeId]));
  }
  focusCanvasPoint(): void {
    this.resetCanvasView();
  }
  onViewportChanged(): void {
    const callbacks = this.#options.callbacks as ModlessCallbacks | undefined;
    callbacks?.onViewportChange?.();
  }
  beginInlineLabelEdit(): void {}
  addNode(node: { type?: string; x?: number; y?: number }): void {
    this.#actionDispatcher?.dispatch({
      kind: "createNode",
      isOperation: true,
      elementTypeId: node.type || "Unknown",
      location: { x: node.x || 120, y: node.y || 120 },
    });
  }
  addEdge(edge: { sourceId?: string; targetId?: string }): void {
    if (!edge.sourceId || !edge.targetId) {
      return;
    }
    this.#actionDispatcher?.dispatch({
      kind: "createEdge",
      isOperation: true,
      elementTypeId: DefaultTypes.EDGE,
      sourceElementId: edge.sourceId,
      targetElementId: edge.targetId,
    });
  }
  setHoverNode(): void {}
  setHoverEdge(): void {}
  applyElkLayout(): void {
    this.#actionDispatcher?.dispatch(LayoutOperation.create());
  }
  undo(): void {
    this.#actionDispatcher?.dispatch(UndoAction.create());
  }
  redo(): void {
    this.#actionDispatcher?.dispatch(RedoAction.create());
  }
  canUndo(): boolean {
    return Boolean(this.#connected);
  }
  canRedo(): boolean {
    return Boolean(this.#connected);
  }
}

export function createGlspRenderer(): ModlessGlspRenderer {
  return new ModlessGlspRenderer();
}

export function createRenderer(kind?: string): ModlessGlspRenderer | null {
  if (kind === "glsp-sprotty") {
    return createGlspRenderer();
  }
  return null;
}
