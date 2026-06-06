import {
  artifactTree,
  CANVAS_SIZE,
  codeLines,
  models,
  transformationBatches,
} from "./case-study.js";

const SVG_NS = "http://www.w3.org/2000/svg";

function createElement(tagName, className) {
  const element = document.createElement(tagName);
  if (className) {
    element.className = className;
  }
  return element;
}

function createSvgElement(tagName, attributes = {}) {
  const element = document.createElementNS(SVG_NS, tagName);
  Object.entries(attributes).forEach(
      ([name, value]) => element.setAttribute(name, value));
  return element;
}

function edgePath(source, target) {
  const horizontalDistance = Math.abs(target.x - source.x);
  if (horizontalDistance < 70) {
    const middleY = (source.y + target.y) / 2;
    return `M ${source.x} ${source.y} V ${middleY} H ${target.x} V ${target.y}`;
  }

  const middleX = (source.x + target.x) / 2;
  return `M ${source.x} ${source.y} H ${middleX} V ${target.y} H ${target.x}`;
}

function edgeLabelPosition(source, target) {
  if (Math.abs(target.x - source.x) < 70) {
    return {
      x: source.x + 12,
      y: (source.y + target.y) / 2 - 8,
    };
  }

  return {
    x: (source.x + target.x) / 2,
    y: (source.y + target.y) / 2 - 8,
  };
}

function renderNode(node) {
  const anchor = createElement("div", "model-node-anchor");
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
  card.append(head, body, badge);
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
    const source = nodesById.get(edge.source);
    const target = nodesById.get(edge.target);
    const path = createSvgElement("path", {
      d: edgePath(source, target),
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

    const labelPosition = edgeLabelPosition(source, target);
    const label = createSvgElement("text", {
      x: labelPosition.x,
      y: labelPosition.y,
      class: [
        "edge-label",
        edge.aiAddition ? "ai-addition-edge" : "",
      ]
      .filter(Boolean)
      .join(" "),
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
    const row = createElement("span",
        `code-line ${line.className || ""}`.trim());
    row.dataset.line = String(index + 1).padStart(2, "0");
    row.innerHTML = line.html || " ";
    mount.append(row);
  });
}

export function renderCaseStudy() {
  Object.entries(models).forEach(([modelId, model]) => {
    const mount = document.querySelector(`#model-${modelId}`);
    renderModel(mount, model, modelId);
  });
  renderMappingRules();
  renderFlowTokens();
  renderArtifacts();
  renderCode();
}
