var __decorate =
  (this && this.__decorate) ||
  function (decorators, target, key, desc) {
    var c = arguments.length,
      r =
        c < 3
          ? target
          : desc === null
            ? (desc = Object.getOwnPropertyDescriptor(target, key))
            : desc,
      d;
    if (typeof Reflect === "object" && typeof Reflect.decorate === "function")
      r = Reflect.decorate(decorators, target, key, desc);
    else
      for (var i = decorators.length - 1; i >= 0; i--)
        if ((d = decorators[i]))
          r = (c < 3 ? d(r) : c > 3 ? d(target, key, r) : d(target, key)) || r;
    return c > 3 && r && Object.defineProperty(target, key, r), r;
  };
var __metadata =
  (this && this.__metadata) ||
  function (k, v) {
    if (typeof Reflect === "object" && typeof Reflect.metadata === "function")
      return Reflect.metadata(k, v);
  };
import { GModelSerializer, ModelState } from "@eclipse-glsp/server";
import { inject, injectable } from "inversify";
import { BackendClient, resolveBackendUrl } from "./backend-client.js";
import { graphToDiagram, materializeDiagram, toSprottyGraph } from "./cvs-mapper.js";
let ModlessSourceModelStorage = class ModlessSourceModelStorage {
  serializer;
  modelState;
  async loadSourceModel(action) {
    const options = action.options || {};
    const level = String(options.level || "cim");
    const modelId = String(options.modelId || "");
    const viewId = String(options.viewId || "");
    const authToken = String(options.authToken || "");
    // Always use the server-side backend URL (e.g. http://backend:8080 in Docker).
    // The browser sends a host-reachable URL (127.0.0.1) which is not valid inside the GLSP container.
    const backendUrl = resolveBackendUrl();
    if (!modelId) {
      const levelConfig =
        (await new BackendClient({ backendUrl, authToken }).loadModelingConfig()) || {};
      const levels = levelConfig.levels || {};
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
    const record = (await client.loadModel(level, modelId)) || {};
    const model =
      record.modelJson && typeof record.modelJson === "object" ? record.modelJson : record;
    const config = (await client.loadModelingConfig()) || {};
    const levels = config.levels || {};
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
  async saveSourceModel(_action) {
    await this.persistModel();
  }
  async persistModel() {
    const level = String(this.modelState.get("level") || "cim");
    const modelId = String(this.modelState.get("modelId") || "");
    const authToken = String(this.modelState.get("authToken") || "");
    const backendUrl = String(this.modelState.get("backendUrl") || resolveBackendUrl());
    const domainModel = this.modelState.get("domainModel") || {};
    if (!modelId) {
      return;
    }
    const schema = this.serializer.createSchema(this.modelState.root);
    const diagram = graphToDiagram(schema);
    const graph = {
      nodes: diagram.nodes.map((node) => ({
        id: node.id,
        type: node.type,
        x: node.x,
        y: node.y,
        width: node.width,
        height: node.height,
        data: node.data,
      })),
      edges: diagram.connections
        .filter((edge) => !edge.shortcut)
        .map((edge) => ({
          id: edge.id,
          sourceId: edge.sourceId,
          targetId: edge.targetId,
          kind: edge.kind,
          data: edge.data,
        })),
    };
    const client = new BackendClient({ backendUrl, authToken });
    const updated = await client.patchModel(level, modelId, [
      { op: "replace", path: "/graph", value: graph },
    ]);
    if (updated) {
      this.modelState.set("domainModel", { ...domainModel, ...updated, graph });
    } else {
      this.modelState.set("domainModel", { ...domainModel, graph });
    }
  }
};
__decorate(
  [inject(GModelSerializer), __metadata("design:type", Object)],
  ModlessSourceModelStorage.prototype,
  "serializer",
  void 0,
);
__decorate(
  [inject(ModelState), __metadata("design:type", Object)],
  ModlessSourceModelStorage.prototype,
  "modelState",
  void 0,
);
ModlessSourceModelStorage = __decorate([injectable()], ModlessSourceModelStorage);
export { ModlessSourceModelStorage };
export function readSessionOptions(options = {}) {
  return {
    level: String(options.level || "cim"),
    modelId: String(options.modelId || ""),
    viewId: String(options.viewId || ""),
  };
}
