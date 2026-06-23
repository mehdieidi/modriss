import "reflect-metadata";
import {
  DiagramLoader,
  GEdge,
  RepositionAction,
  STANDALONE_MODULE_CONFIG,
  TYPES,
  ZoomAction,
  baseViewModule,
  configureActionHandler,
  createDiagramOptionsModule,
  initializeDiagramContainer,
  overrideModelElement,
} from "@eclipse-glsp/client";
import { DefaultTypes, RejectAction, SetContextActions } from "@eclipse-glsp/protocol";
import {
  Action,
  BaseJsonrpcGLSPClient,
  FeatureModule,
  GNode,
  ICommand,
  IActionHandler,
  IContextMenuService,
  RequestAction,
  RequestModelAction,
} from "@eclipse-glsp/sprotty";
import { GLSPWebSocketProvider } from "@eclipse-glsp/protocol/lib/client-server-protocol/jsonrpc/ws-connection-provider.js";
import { Container, injectable } from "inversify";
import { ModlessEdgeView } from "./views/modless-edge-view.js";
import { ModlessNodeView } from "./views/modless-node-view.js";
import { resolveBackendUrl } from "./bridge/session-options.js";

export const MODLESS_DIAGRAM_TYPE = "modless-diagram";

let sharedClient: BaseJsonrpcGLSPClient | null = null;
let sharedClientUrl = "";

function resetSharedGlspClient(): void {
  sharedClient = null;
  sharedClientUrl = "";
}

function glspSessionReady(options: Record<string, unknown>): boolean {
  return Boolean(String(options.level || "").trim() && String(options.authToken || "").trim());
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
      overrideModelElement(context, DefaultTypes.NODE, GNode, ModlessNodeView);
      overrideModelElement(context, DefaultTypes.EDGE, GEdge, ModlessEdgeView);
    },
    { featureId: Symbol("modlessView"), requires: [baseViewModule] },
  );
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

function modlessEmbedModule(): FeatureModule {
  const noopContextMenu: IContextMenuService = { show: () => {} };
  return new FeatureModule(
    (bind, unbind, isBound, rebind) => {
      const context = { bind, unbind, isBound, rebind };
      bind(NoOpSetContextActionsHandler).toSelf().inSingletonScope();
      bind(OrphanRejectActionHandler).toSelf().inSingletonScope();
      configureActionHandler(context, SetContextActions.KIND, NoOpSetContextActionsHandler);
      configureActionHandler(context, RejectAction.KIND, OrphanRejectActionHandler);
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
  #mounted = false;
  #connected = false;
  #options: Record<string, unknown> = {};
  #container: Container | null = null;
  #clientId = `modless-diagram-${Math.random().toString(36).slice(2)}`;
  #actionDispatcher: { dispatch: (action: unknown) => void } | null = null;
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
      options.viewId || "",
      options.glspServerUrl || "",
    ].join(":");
  }

  async unmount(): Promise<void> {
    this.#container = null;
    this.#actionDispatcher = null;
    this.#connected = false;
    this.#mounted = false;
    this.#sessionKey = "";
    if (this.#host) {
      this.#host.replaceChildren();
      this.#host.classList.remove("is-mounted", "is-connected", "glsp-idle");
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

  async #mountNow(host: HTMLElement, options: Record<string, unknown> = {}): Promise<boolean> {
    const nextSessionKey = this.#sessionKeyFor(options);

    if (!glspSessionReady(options)) {
      this.#host = host;
      this.#options = { ...options };
      this.#mounted = true;
      this.#connected = false;
      this.#sessionKey = "";
      host.id = this.#clientId;
      host.classList.add("is-mounted");
      host.classList.remove("is-connected", "glsp-idle");
      setHostStatus(host, "Sign in and open a project to start the GLSP diagram.");
      return true;
    }

    if (this.#mounted && this.#connected && this.#sessionKey === nextSessionKey) {
      return true;
    }

    if (this.#mounted) {
      await this.unmount();
    }

    this.#host = host;
    this.#options = { ...options };
    this.#mounted = true;
    host.classList.add("is-mounted");
    host.classList.remove("glsp-idle");
    host.id = this.#clientId;
    setHostStatus(host, "Connecting to GLSP diagram server…");

    try {
      const hidden = document.createElement("div");
      hidden.id = `${this.#clientId}_hidden`;
      hidden.style.display = "none";
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
        modlessViewModule(),
        modlessEmbedModule(),
        STANDALONE_MODULE_CONFIG,
      );

      this.#container = container;

      const loader = await container.getAsync(DiagramLoader);
      setHostStatus(host, "Loading diagram model…");
      await loader.load({
        requestModelOptions: {
          sourceUri: this.#sourceUri(options),
          level: options.level || "cim",
          modelId: options.modelId || "",
          viewId: options.viewId || "",
          authToken: options.authToken || "",
          backendUrl: resolveBackendUrl(options),
        },
      });

      this.#actionDispatcher = await container.getAsync(TYPES.IActionDispatcher);

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

  async syncFromState(options: Record<string, unknown> = {}): Promise<void> {
    this.#options = { ...this.#options, ...options };
    if (!options.full) {
      return;
    }
    const nextSessionKey = this.#sessionKeyFor(this.#options);
    if (!this.#connected || nextSessionKey !== this.#sessionKey) {
      if (this.#host) {
        await this.mount(this.#host, this.#options);
      }
      return;
    }
    this.#reloadTask = this.#reloadTask
      .then(() => this.#requestModelRefresh())
      .catch((error) => {
        console.error("[GLSP-Client] Diagram refresh failed", error);
      });
    await this.#reloadTask;
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
    void this.syncFromState({ full: true });
  }
  updateEdge(): void {
    void this.syncFromState({ full: true });
  }
  updateViewport(): void {}
  updateConnectionState(): void {}
  updateContextBoxes(): void {
    void this.syncFromState({ full: true });
  }
  updateImpactState(): void {
    void this.syncFromState({ full: true });
  }
  updateNodeIcons(): void {
    void this.syncFromState({ full: true });
  }
  refreshEdges(): void {
    void this.syncFromState({ full: true });
  }
  renderDiagram(): void {
    void this.syncFromState({ full: true });
  }
  resetCanvasView(): void {
    this.#actionDispatcher?.dispatch(ZoomAction.create({ zoomFactor: 1, fitToScreen: true }));
  }
  zoomCanvasBy(multiplier = 1): void {
    const factor = multiplier > 1 ? 1.1 : 0.9;
    this.#actionDispatcher?.dispatch(ZoomAction.create({ zoomFactor: factor, fitToScreen: false }));
  }
  fitCanvasToDiagram(): void {
    this.#actionDispatcher?.dispatch(ZoomAction.create({ zoomFactor: 1, fitToScreen: true }));
  }
  focusNode(nodeId: string): void {
    this.#actionDispatcher?.dispatch(RepositionAction.create([nodeId]));
  }
  focusCanvasPoint(): void {
    this.resetCanvasView();
  }
  onViewportChanged(): void {
    const callbacks = this.#options.callbacks as { onViewportChange?: () => void } | undefined;
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
    this.#actionDispatcher?.dispatch({ kind: "layout", isOperation: true });
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
