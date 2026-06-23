import { GModelSerializer, ModelState, SourceModelStorage } from "@eclipse-glsp/server";
import { Args, RequestModelAction, SaveModelAction } from "@eclipse-glsp/protocol";
import { inject, injectable } from "inversify";
import { BackendClient, resolveBackendUrl } from "./backend-client.js";
import { graphToDiagram, materializeDiagram, toSprottyGraph } from "./cvs-mapper.js";

type LevelConfig = Record<string, unknown>;

@injectable()
export class ModlessSourceModelStorage implements SourceModelStorage {
  @inject(GModelSerializer)
  protected serializer!: GModelSerializer;

  @inject(ModelState)
  protected modelState!: ModelState;

  async loadSourceModel(action: RequestModelAction): Promise<void> {
    const options = action.options || {};
    const level = String(options.level || "cim");
    const modelId = String(options.modelId || "");
    const viewId = String(options.viewId || "");
    const authToken = String(options.authToken || "");
    // Always use the server-side backend URL (e.g. http://backend:8080 in Docker).
    // The browser sends a host-reachable URL (127.0.0.1) which is not valid inside the GLSP container.
    const backendUrl = resolveBackendUrl();

    if (!modelId) {
      const levelConfig = (await new BackendClient({ backendUrl, authToken }).loadModelingConfig()) || {};
      const levels = (levelConfig.levels || {}) as Record<string, LevelConfig>;
      this.modelState.set("level", level);
      this.modelState.set("modelId", "");
      this.modelState.set("viewId", viewId);
      this.modelState.set("authToken", authToken);
      this.modelState.set("backendUrl", backendUrl);
      this.modelState.set("levelConfig", levels[level] || {});
      this.modelState.set("domainModel", {});
      const emptyRoot = this.serializer.createRoot({ type: "graph", id: "root", children: [] });
      this.modelState.updateRoot(emptyRoot);
      console.info("[GLSP-Server] Loaded empty draft diagram (no modelId yet)");
      return;
    }

    console.info(
      `[GLSP-Server] Loading model level=${level} modelId=${modelId} viewId=${viewId || "(default)"}`,
    );

    const client = new BackendClient({ backendUrl, authToken });
    const record = ((await client.loadModel(level, modelId)) || {}) as Record<string, unknown>;
    const model =
      record.modelJson && typeof record.modelJson === "object"
        ? (record.modelJson as Record<string, unknown>)
        : record;
    const config = (await client.loadModelingConfig()) || {};
    const levels = (config.levels || {}) as Record<string, LevelConfig>;
    const levelConfig = levels[level] || {};

    this.modelState.set("level", level);
    this.modelState.set("modelId", modelId);
    this.modelState.set("viewId", viewId);
    this.modelState.set("authToken", authToken);
    this.modelState.set("backendUrl", backendUrl);
    this.modelState.set("domainModel", model);
    this.modelState.set("levelConfig", levelConfig);

    const diagram = materializeDiagram(model, viewId);
    const graphSchema = toSprottyGraph(diagram, levelConfig, {});
    const root = this.serializer.createRoot(graphSchema);
    this.modelState.updateRoot(root);

    const childCount = Array.isArray(graphSchema.children) ? graphSchema.children.length : 0;
    console.info(
      `[GLSP-Server] Model loaded: ${diagram.nodes.length} node(s), ${diagram.connections.length} edge(s), graph children=${childCount}`,
    );
  }

  async saveSourceModel(_action: SaveModelAction): Promise<void> {
    await this.persistModel();
  }

  async persistModel(): Promise<void> {
    const level = String(this.modelState.get("level") || "cim");
    const modelId = String(this.modelState.get("modelId") || "");
    const authToken = String(this.modelState.get("authToken") || "");
    const backendUrl = String(this.modelState.get("backendUrl") || resolveBackendUrl());
    const domainModel = (this.modelState.get<Record<string, unknown>>("domainModel") || {}) as Record<
      string,
      unknown
    >;

    if (!modelId) {
      return;
    }

    const schema = this.serializer.createSchema(this.modelState.root);
    const diagram = graphToDiagram(schema);
    const graph = {
      nodes: diagram.nodes.map((node: Record<string, unknown>) => ({
        id: node.id,
        type: node.type,
        x: node.x,
        y: node.y,
        width: node.width,
        height: node.height,
        data: node.data,
      })),
      edges: diagram.connections
        .filter((edge: Record<string, unknown>) => !edge.shortcut)
        .map((edge: Record<string, unknown>) => ({
          id: edge.id,
          sourceId: edge.sourceId,
          targetId: edge.targetId,
          kind: edge.kind,
          data: edge.data,
        })),
    };

    const client = new BackendClient({ backendUrl, authToken });
    const updated = await client.patchModel(level, modelId, [{ op: "replace", path: "/graph", value: graph }]);
    if (updated) {
      this.modelState.set("domainModel", { ...domainModel, ...updated, graph });
    } else {
      this.modelState.set("domainModel", { ...domainModel, graph });
    }
  }
}

export function readSessionOptions(options: Args = {}): {
  level: string;
  modelId: string;
  viewId: string;
} {
  return {
    level: String(options.level || "cim"),
    modelId: String(options.modelId || ""),
    viewId: String(options.viewId || ""),
  };
}
