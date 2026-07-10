import {
  artifactTree,
  CANVAS_SIZE,
  codeLines,
  models,
  transformationBatches,
} from "./case-study.js";

const SVG_NS = "http://www.w3.org/2000/svg";
const renderedModels = [];
const PORT_VECTORS = {
  bottom: { x: 0, y: 1 },
  left: { x: -1, y: 0 },
  right: { x: 1, y: 0 },
  top: { x: 0, y: -1 },
};

function createElement(tagName, className) {
  const element = document.createElement(tagName);
  if (className) {
    element.className = className;
  }
  return element;
}

function createSvgElement(tagName, attributes = {}) {
  const element = document.createElementNS(SVG_NS, tagName);
  Object.entries(attributes).forEach(([name, value]) => element.setAttribute(name, value));
  return element;
}

function edgeRoute(source, target) {
  const isHorizontal = source.vector.x !== 0;
  const midpoint = isHorizontal
    ? Math.round((source.x + target.x) / 2)
    : Math.round((source.y + target.y) / 2);

  if (isHorizontal) {
    return {
      label: { x: midpoint, y: Math.round((source.y + target.y) / 2) - 8 },
      path: `M ${source.x.toFixed(2)} ${source.y.toFixed(2)} H ${midpoint} V ${target.y.toFixed(2)} H ${target.x.toFixed(2)}`,
    };
  }

  return {
    label: { x: Math.round((source.x + target.x) / 2), y: midpoint - 8 },
    path: `M ${source.x.toFixed(2)} ${source.y.toFixed(2)} V ${midpoint} H ${target.x.toFixed(2)} V ${target.y.toFixed(2)}`,
  };
}

function canvasPointFromPort(port, mount, side) {
  const portRect = port.getBoundingClientRect();
  const mountRect = mount.getBoundingClientRect();
  const x = portRect.left - mountRect.left + portRect.width / 2;
  const y = portRect.top - mountRect.top + portRect.height / 2;

  return {
    vector: PORT_VECTORS[side],
    x: (x / mountRect.width) * CANVAS_SIZE.width,
    y: (y / mountRect.height) * CANVAS_SIZE.height,
  };
}

function nodePort(mount, node, side) {
  const anchor = mount.querySelector(`.model-node-anchor[data-node-id="${node.id}"]`);
  const port = anchor.querySelector(`.node-port-${side}`);
  return canvasPointFromPort(port, mount, side);
}

function updateModelEdges(renderedModel) {
  const { edges, model, mount, nodesById } = renderedModel;

  model.edges.forEach((edge) => {
    const sourceNode = nodesById.get(edge.source);
    const targetNode = nodesById.get(edge.target);
    const deltaX = targetNode.x - sourceNode.x;
    const deltaY = targetNode.y - sourceNode.y;
    const usesHorizontalPorts = Math.abs(deltaX) >= Math.abs(deltaY) * 0.72;
    const sourceSide = usesHorizontalPorts
      ? deltaX >= 0
        ? "right"
        : "left"
      : deltaY >= 0
        ? "bottom"
        : "top";
    const targetSide = usesHorizontalPorts
      ? deltaX >= 0
        ? "left"
        : "right"
      : deltaY >= 0
        ? "top"
        : "bottom";
    const source = nodePort(mount, sourceNode, sourceSide);
    const target = nodePort(mount, targetNode, targetSide);

    const route = edgeRoute(source, target);
    const path = edges.querySelector(`path[data-edge-id="${edge.id}"]`);
    path.setAttribute("d", route.path);

    const label = edges.querySelector(`text[data-edge-id="${edge.id}"]`);
    label.setAttribute("x", route.label.x);
    label.setAttribute("y", route.label.y);
  });
}

export function updateRenderedModelEdges() {
  renderedModels.forEach(updateModelEdges);
}

function renderNode(node) {
  const anchor = createElement("div", "model-node-anchor");
  anchor.dataset.nodeId = node.id;
  anchor.style.left = `${(node.x / CANVAS_SIZE.width) * 100}%`;
  anchor.style.top = `${(node.y / CANVAS_SIZE.height) * 100}%`;

  const card = createElement("article", "model-node");
  card.dataset.nodeId = node.id;
  card.dataset.nodeType = node.type;
  card.style.setProperty("--node-color", node.color);

  if (node.aiAddition) {
    card.classList.add("ai-addition");
  }
  if (node.manualTarget) {
    card.classList.add("manual-target");
  }

  const leftPort = createElement("span", "node-port node-port-left");
  const rightPort = createElement("span", "node-port node-port-right");
  const topPort = createElement("span", "node-port node-port-top");
  const bottomPort = createElement("span", "node-port node-port-bottom");

  const head = createElement("div", "model-node-head");
  const icon = createElement("span", "model-node-icon");
  icon.textContent = node.icon;

  const title = createElement("div", "model-node-title");
  const tag = createElement("span");
  tag.textContent = node.tag;
  const name = createElement("strong");
  name.textContent = node.name;
  title.append(tag, name);

  const menu = createElement("span", "model-node-menu");
  menu.textContent = "...";
  head.append(icon, title, menu);

  const body = createElement("div", "model-node-body");
  node.properties.forEach(([propertyName, propertyValue]) => {
    const row = createElement("span", "node-property");
    const label = createElement("span");
    label.textContent = propertyName;
    const value = createElement("strong");
    value.textContent = propertyValue;
    row.append(label, value);
    body.append(row);
  });

  const badge = createElement("span", "node-badge");
  badge.textContent = "refined";
  card.append(leftPort, rightPort, topPort, bottomPort, head, body, badge);
  anchor.append(card);
  return anchor;
}

function renderModel(mount, model, modelId) {
  const nodesById = new Map(model.nodes.map((node) => [node.id, node]));
  const edges = createSvgElement("svg", {
    class: "model-edges",
    viewBox: `0 0 ${CANVAS_SIZE.width} ${CANVAS_SIZE.height}`,
    preserveAspectRatio: "none",
    "aria-hidden": "true",
  });

  const definitions = createSvgElement("defs");
  const marker = createSvgElement("marker", {
    id: `arrow-${modelId}`,
    viewBox: "0 0 10 10",
    refX: "9",
    refY: "5",
    markerWidth: "5",
    markerHeight: "5",
    orient: "auto-start-reverse",
  });
  marker.append(
    createSvgElement("path", {
      d: "M 0 0 L 10 5 L 0 10 z",
      fill: "context-stroke",
    }),
  );
  definitions.append(marker);
  edges.append(definitions);

  model.edges.forEach((edge) => {
    const path = createSvgElement("path", {
      class: [
        "model-edge",
        edge.dashed ? "edge-dashed" : "",
        edge.kind ? `edge-${edge.kind}` : "",
        edge.aiAddition ? "ai-addition-edge" : "",
      ]
        .filter(Boolean)
        .join(" "),
      "data-edge-id": edge.id,
      "data-source": edge.source,
      "data-target": edge.target,
      "marker-end": `url(#arrow-${modelId})`,
    });
    edges.append(path);

    const label = createSvgElement("text", {
      class: ["edge-label", edge.aiAddition ? "ai-addition-edge" : ""].filter(Boolean).join(" "),
      "data-edge-id": edge.id,
      "data-source": edge.source,
      "data-target": edge.target,
      "text-anchor": "middle",
    });
    label.textContent = edge.label;
    edges.append(label);
  });

  const nodes = createElement("div", "model-nodes");
  model.nodes.forEach((node) => nodes.append(renderNode(node)));
  mount.append(edges, nodes);

  const renderedModel = {
    edges,
    model,
    mount,
    nodesById,
  };
  renderedModels.push(renderedModel);
  updateModelEdges(renderedModel);
}

function renderMappingRules() {
  const mount = document.querySelector("#mapping-rules");
  transformationBatches.forEach((batch) => {
    batch.rules.forEach((rule) => {
      const token = createElement("span", "mapping-token");
      token.dataset.batch = batch.id;
      token.style.left = `${rule.x}%`;
      token.style.top = `${rule.y}%`;
      token.style.setProperty("--mapping-color", rule.color);
      const dot = createElement("i");
      const label = createElement("span");
      label.textContent = rule.label;
      token.append(dot, label);
      mount.append(token);
    });
  });
}

function renderFlowTokens() {
  const mount = document.querySelector("#flow-tokens");
  transformationBatches.forEach((batch) => {
    batch.inputs.forEach(([label, color], index) => {
      const token = createElement("span", "flow-token flow-token-input");
      token.dataset.batch = batch.id;
      token.dataset.index = String(index);
      token.textContent = label;
      token.style.setProperty("--token-color", color);
      mount.append(token);
    });
    batch.outputs.forEach(([label, color], index) => {
      const token = createElement("span", "flow-token flow-token-output");
      token.dataset.batch = batch.id;
      token.dataset.index = String(index);
      token.textContent = label;
      token.style.setProperty("--token-color", color);
      mount.append(token);
    });
  });
}

function renderArtifacts() {
  const mount = document.querySelector("#artifact-tree");
  artifactTree.forEach((entry) => {
    const row = createElement("span", "tree-row");
    row.style.setProperty("--tree-depth", String(entry.depth));
    row.style.setProperty("--tree-color", entry.color);
    if (entry.active) {
      row.classList.add("active");
    }

    const icon = createElement("i", `tree-icon ${entry.type}`);
    const name = createElement("span");
    name.textContent = entry.name;
    row.append(icon, name);
    mount.append(row);
  });
}

function renderCode() {
  const mount = document.querySelector("#code-editor");
  codeLines.forEach((line, index) => {
    const row = createElement("span", `code-line ${line.className || ""}`.trim());
    row.dataset.line = String(index + 1).padStart(2, "0");
    row.innerHTML = line.html || " ";
    mount.append(row);
  });
}

export function renderCaseStudy() {
  renderedModels.length = 0;
  Object.entries(models).forEach(([modelId, model]) => {
    const mount = document.querySelector(`#model-${modelId}`);
    renderModel(mount, model, modelId);
  });
  renderMappingRules();
  renderFlowTokens();
  renderArtifacts();
  renderCode();

  window.addEventListener(
    "resize",
    () => {
      window.requestAnimationFrame(updateRenderedModelEdges);
    },
    { passive: true },
  );
}
