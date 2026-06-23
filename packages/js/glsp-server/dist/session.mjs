import { materializeDiagram, toSprottyGraph } from "./cvs-mapper.mjs";
import { validateCreateEdge, validateCreateNode } from "./operation-validator.mjs";
import { applyElkLayout } from "./elk-layout.mjs";

export class ModlessSession {
  constructor({ level, modelId, authToken, viewId, backendUrl }) {
    this.level = level;
    this.modelId = modelId;
    this.authToken = authToken;
    this.viewId = viewId;
    this.backendUrl = backendUrl.replace(/\/$/, "");
    this.model = null;
    this.levelConfig = {};
    this.diagram = { nodes: [], connections: [] };
    this.sessionOptions = {
      zoom: 1,
      selection: { nodeId: null, edgeId: null },
      validationIssues: [],
      impactState: {},
    };
  }

  async load() {
    this.model = await this.#fetchJson(`/${this.level}/${this.modelId}`);
    const config = await this.#fetchJson("/modeling/config");
    this.levelConfig = config.levels?.[this.level] || {};
    this.diagram = materializeDiagram(this.model, this.viewId);
    return this.#graphResponse();
  }

  async applyOperation(operation) {
    switch (operation.kind) {
      case "create-node": {
        const validation = validateCreateNode(this.levelConfig, operation.elementType);
        if (!validation.ok) {
          throw new Error(validation.message);
        }
        const id = `node-${Date.now()}`;
        this.diagram.nodes.push({
          id,
          type: operation.elementType,
          x: operation.x || 120,
          y: operation.y || 120,
          width: 228,
          height: 112,
          data: { id, type: operation.elementType, name: operation.elementType },
        });
        break;
      }
      case "move-node": {
        const node = this.diagram.nodes.find((item) => item.id === operation.elementId);
        if (node) {
          node.x = operation.x;
          node.y = operation.y;
          if (operation.width) {
            node.width = operation.width;
          }
          if (operation.height) {
            node.height = operation.height;
          }
        }
        break;
      }
      case "create-edge": {
        const source = this.diagram.nodes.find((node) => node.id === operation.sourceId);
        const target = this.diagram.nodes.find((node) => node.id === operation.targetId);
        if (!source || !target) {
          throw new Error("Edge source or target not found");
        }
        const validation = validateCreateEdge(
          this.levelConfig,
          source.type,
          target.type,
          operation.edgeKind,
        );
        if (!validation.ok) {
          throw new Error(validation.message);
        }
        const id = `edge-${Date.now()}`;
        this.diagram.connections.push({
          id,
          sourceId: operation.sourceId,
          targetId: operation.targetId,
          kind: validation.edgeKind,
          data: { id, kind: validation.edgeKind },
        });
        break;
      }
      case "delete-elements": {
        const ids = new Set(operation.elementIds || []);
        this.diagram.nodes = this.diagram.nodes.filter((node) => !ids.has(node.id));
        this.diagram.connections = this.diagram.connections.filter(
          (edge) => !ids.has(edge.id) && !ids.has(edge.sourceId) && !ids.has(edge.targetId),
        );
        break;
      }
      case "layout-elk":
        this.diagram = await applyElkLayout(this.diagram, this.levelConfig);
        break;
      case "set-viewport":
        this.sessionOptions.zoom = Number(operation.zoom || this.sessionOptions.zoom || 1);
        break;
      case "set-overlays":
        this.sessionOptions = {
          ...this.sessionOptions,
          ...operation.payload,
        };
        break;
      default:
        break;
    }
    if (operation.kind !== "set-viewport" && operation.kind !== "set-overlays") {
      this.#syncGraph();
      await this.persist();
    }
    return this.#graphResponse();
  }

  #graphResponse() {
    return {
      graph: toSprottyGraph(this.diagram, this.levelConfig, this.sessionOptions),
      revision: this.model?.revision || 0,
      level: this.level,
    };
  }

  async persist() {
    const operations = [{ op: "replace", path: "/graph", value: this.model.graph }];
    const updated = await this.#fetchJson(`/${this.level}/${this.modelId}`, {
      method: "PATCH",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ operations }),
    });
    if (updated) {
      this.model = updated;
    }
  }

  #syncGraph() {
    this.model.graph = {
      nodes: this.diagram.nodes.map((node) => ({
        id: node.id,
        type: node.type,
        x: node.x,
        y: node.y,
        width: node.width,
        height: node.height,
        data: node.data,
      })),
      edges: this.diagram.connections
        .filter((edge) => !edge.shortcut)
        .map((edge) => ({
          id: edge.id,
          sourceId: edge.sourceId,
          targetId: edge.targetId,
          kind: edge.kind,
          data: edge.data,
        })),
    };
  }

  async #fetchJson(path, init = {}) {
    const headers = new Headers(init.headers || {});
    if (this.authToken) {
      headers.set("X-Auth-Token", this.authToken);
    }
    const response = await fetch(`${this.backendUrl}/api${path}`, { ...init, headers });
    if (!response.ok) {
      throw new Error(`Backend ${path} failed: ${response.status}`);
    }
    if (response.status === 204) {
      return null;
    }
    return response.json();
  }
}
