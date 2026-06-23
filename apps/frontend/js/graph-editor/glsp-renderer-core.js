import { escapeXml, finiteNumber, safeSvgValue } from "./glsp-shapes.js";
import { createModlessNodeElement, setNodeTransform } from "./glsp-node-view.js";

const DEFAULT_SIZE = { width: 228, height: 112 };

export function graphToDiagram(graph) {
  const nodes = [];
  const connections = [];
  for (const child of graph?.children || []) {
    if (child.type === "node") {
      nodes.push({
        id: child.id,
        type: child.args?.elementType || "Unknown",
        x: child.position?.x || 0,
        y: child.position?.y || 0,
        width: child.size?.width || DEFAULT_SIZE.width,
        height: child.size?.height || DEFAULT_SIZE.height,
        label: child.children?.find((item) => item.id?.endsWith("-label"))?.text || child.id,
        lines: child.children?.find((item) => item.id?.endsWith("-lines"))?.text || "",
        args: child.args || {},
      });
    }
    if (child.type === "edge") {
      connections.push({
        id: child.id,
        sourceId: child.sourceId,
        targetId: child.targetId,
        kind: child.args?.kind,
        args: child.args || {},
        shortcut: child.args?.shortcut,
      });
    }
  }
  return {
    nodes,
    connections,
    overlays: graph.overlays || {},
  };
}

export function clientToGraphPoint(host, clientX, clientY) {
  const surface = host?.querySelector?.(".glsp-diagram-surface");
  const rect = surface?.getBoundingClientRect?.() || host?.getBoundingClientRect?.();
  const viewport = readViewportFromHost(host);
  const localX = clientX - (rect?.left || 0);
  const localY = clientY - (rect?.top || 0);
  return {
    x: (localX - viewport.x) / viewport.scale,
    y: (localY - viewport.y) / viewport.scale,
  };
}

export function readViewportFromHost(host) {
  const layer = host?.querySelector?.(".glsp-viewport");
  if (!layer) {
    return { x: 0, y: 0, scale: 1 };
  }
  const transform = layer.getAttribute("transform") || "";
  const match = transform.match(/translate\(([-\d.]+)\s+([-\d.]+)\)\s+scale\(([-\d.]+)\)/);
  if (!match) {
    return { x: 0, y: 0, scale: 1 };
  }
  return {
    x: Number(match[1]) || 0,
    y: Number(match[2]) || 0,
    scale: Number(match[3]) || 1,
  };
}

export function applyViewportTransform(host, viewport = { x: 0, y: 0, scale: 1 }) {
  const layer = host?.querySelector?.(".glsp-viewport");
  if (!layer) {
    return;
  }
  const x = Number(viewport.x) || 0;
  const y = Number(viewport.y) || 0;
  const scale = Number(viewport.scale) || 1;
  layer.setAttribute("transform", `translate(${x} ${y}) scale(${scale})`);
}

export function renderGlspScene(host, state) {
  const {
    diagram = { nodes: [], connections: [] },
    levelConfig = {},
    viewport = { x: 0, y: 0, scale: 1 },
    selection = {},
    hovered = {},
    connectionDrag = null,
    overlays = {},
    detailLevel = "normal",
    selectedContextName = "",
  } = state;

  const mergedOverlays = { ...(diagram.overlays || {}), ...overlays };
  const width = Math.max(1200, host.clientWidth || 1200);
  const height = Math.max(800, host.clientHeight || 800);

  host.innerHTML = `
    <div class="glsp-diagram-surface" data-testid="glsp-surface">
      <svg class="glsp-diagram-svg" width="100%" height="100%" viewBox="0 0 ${width} ${height}">
        <defs>
          <marker id="modless-arrow" markerWidth="10" markerHeight="8" refX="9" refY="4" orient="auto">
            <path d="M0,0 L10,4 L0,8 Z" fill="var(--edge-stroke, #64748b)" />
          </marker>
          <marker id="modless-arrow-selected" markerWidth="10" markerHeight="8" refX="9" refY="4" orient="auto">
            <path d="M0,0 L10,4 L0,8 Z" fill="var(--accent, #00a6e0)" />
          </marker>
        </defs>
        <g class="glsp-viewport" transform="translate(${viewport.x || 0} ${viewport.y || 0}) scale(${viewport.scale || 1})">
          <g class="glsp-context-layer"></g>
          <g class="glsp-edge-layer"></g>
          <g class="glsp-node-layer"></g>
          <g class="glsp-connection-preview-layer"></g>
        </g>
      </svg>
      <div class="glsp-overlay-root"></div>
    </div>`;

  const contextLayer = host.querySelector(".glsp-context-layer");
  const edgeLayer = host.querySelector(".glsp-edge-layer");
  const nodeLayer = host.querySelector(".glsp-node-layer");
  const previewLayer = host.querySelector(".glsp-connection-preview-layer");

  for (const raw of mergedOverlays.contextBoxes || []) {
    const box = normalizeBoundedContextBox(raw, { selectedContextName });
    if (box) {
      contextLayer.appendChild(renderContextBox(box));
    }
  }

  const nodeById = new Map(diagram.nodes.map((node) => [node.id, node]));
  for (const edge of diagram.connections || []) {
    edgeLayer.appendChild(
      renderEdge(edge, nodeById, {
        selected: selection.edgeId === edge.id,
        hovered: hovered.edgeId === edge.id,
        validation: mergedOverlays.validationByElement?.[edge.id],
        impact: mergedOverlays.impactByElement?.[edge.id],
      }),
    );
  }

  for (const node of diagram.nodes || []) {
    const validation = mergedOverlays.validationByElement?.[node.id];
    const impact = mergedOverlays.impactByElement?.[node.id];
    nodeLayer.appendChild(
      createModlessNodeElement(node, {
        selected: selection.nodeId === node.id,
        hovered: hovered.nodeId === node.id,
        validation,
        impact,
        detailLevel: detailLevel,
      }),
    );
  }

  if (connectionDrag?.sourceId) {
    const source = nodeById.get(connectionDrag.sourceId);
    if (source) {
      previewLayer.appendChild(renderConnectionPreview(source, connectionDrag));
    }
  }

  return { overlayRoot: host.querySelector(".glsp-overlay-root") };
}

export function normalizeBoundedContextBox(box, { selectedContextName = "" } = {}) {
  if (!box || typeof box !== "object") {
    return null;
  }
  const name = String(box.name || box.label || "").trim();
  if (!name) {
    return null;
  }
  let x;
  let y;
  let width;
  let height;
  if (Number.isFinite(box.minX) && Number.isFinite(box.maxX)) {
    x = finiteNumber(box.minX);
    y = finiteNumber(box.minY);
    width = Math.max(1, finiteNumber(box.maxX) - x);
    height = Math.max(1, finiteNumber(box.maxY) - y);
  } else if (Number.isFinite(box.x) && Number.isFinite(box.width)) {
    x = finiteNumber(box.x);
    y = finiteNumber(box.y);
    width = Math.max(1, finiteNumber(box.width));
    height = Math.max(1, finiteNumber(box.height));
  } else {
    return null;
  }
  return {
    id: box.id || name,
    name,
    label: name,
    x,
    y,
    width,
    height,
    selected: Boolean(selectedContextName && selectedContextName === name),
  };
}

function renderContextBox(box) {
  const label = box.label || box.name;
  const g = svgEl("g", {
    class: `glsp-context-box${box.selected ? " is-selected" : ""}`,
    "data-id": box.id,
    "data-context-name": box.name,
  });
  g.appendChild(
    svgEl("rect", {
      class: "glsp-context-box-body",
      x: box.x,
      y: box.y,
      width: box.width,
      height: box.height,
      rx: 6,
      fill: "color-mix(in srgb, var(--accent-glow, #00a6e0) 8%, transparent)",
      stroke: "color-mix(in srgb, var(--outline, #64748b) 62%, transparent)",
      "stroke-dasharray": "8 4",
      "stroke-width": 1.5,
    }),
  );
  const labelY = box.y - 4;
  const labelText = escapeXml(label);
  const labelWidth = Math.min(220, Math.max(48, labelText.length * 6.5 + 14));
  g.appendChild(
    svgEl("rect", {
      class: "glsp-context-box-label-bg",
      x: box.x + 10,
      y: labelY - 11,
      width: labelWidth,
      height: 14,
      rx: 3,
      fill: "color-mix(in srgb, var(--surface, #0f172a) 88%, transparent)",
      stroke: "color-mix(in srgb, var(--border, #334155) 54%, transparent)",
      "stroke-width": 1,
    }),
  );
  g.appendChild(
    svgEl("text", {
      class: "glsp-context-box-label",
      x: box.x + 16,
      y: labelY,
      fill: "color-mix(in srgb, var(--text, #f8fafc) 88%, var(--muted, #94a3b8) 12%)",
      "font-size": 9,
      "font-weight": 700,
    }),
  ).textContent = labelText;
  return g;
}

function edgePathD(source, target) {
  const sw = finiteNumber(source.width, DEFAULT_SIZE.width);
  const sh = finiteNumber(source.height, DEFAULT_SIZE.height);
  const tw = finiteNumber(target.width, DEFAULT_SIZE.width);
  const th = finiteNumber(target.height, DEFAULT_SIZE.height);
  const sx = finiteNumber(source.x) + sw / 2;
  const sy = finiteNumber(source.y) + sh;
  const tx = finiteNumber(target.x) + tw / 2;
  const ty = finiteNumber(target.y);
  return { d: `M ${sx} ${sy} C ${sx} ${sy + 40}, ${tx} ${ty - 40}, ${tx} ${ty}`, sx, sy, tx, ty };
}

export function updateNodePosition(host, nodeId, x, y, connections = [], nodesById = null) {
  const nodeEl = host?.querySelector?.(`.glsp-node[data-id="${CSS.escape(nodeId)}"]`);
  setNodeTransform(nodeEl, x, y);
  if (!connections.length) {
    return;
  }
  const geom = (id) => {
    const node = nodesById?.get?.(id);
    if (!node) {
      return null;
    }
    const nx = id === nodeId ? finiteNumber(x) : finiteNumber(node.x);
    const ny = id === nodeId ? finiteNumber(y) : finiteNumber(node.y);
    return {
      x: nx,
      y: ny,
      width: finiteNumber(node.width, DEFAULT_SIZE.width),
      height: finiteNumber(node.height, DEFAULT_SIZE.height),
    };
  };
  for (const edge of connections) {
    if (edge.sourceId !== nodeId && edge.targetId !== nodeId) {
      continue;
    }
    const source = geom(edge.sourceId);
    const target = geom(edge.targetId);
    if (!source || !target) {
      continue;
    }
    const pathInfo = edgePathD(source, target);
    const edgeEl = host.querySelector(`.glsp-edge[data-id="${CSS.escape(edge.id)}"] path`);
    edgeEl?.setAttribute("d", pathInfo.d);
  }
}

function renderEdge(edge, nodeById, style) {
  const source = nodeById.get(edge.sourceId);
  const target = nodeById.get(edge.targetId);
  if (!source || !target) {
    return svgEl("g");
  }
  const { d, sx, sy, tx, ty } = edgePathD(source, target);
  const g = svgEl("g", {
    class: `modless-edge glsp-edge${style.selected ? " is-selected" : ""}${edge.shortcut ? " is-shortcut" : ""}`,
    "data-id": edge.id,
  });
  const path = svgEl("path", {
    d,
    fill: "none",
    stroke: style.selected
      ? "var(--accent, #00a6e0)"
      : edge.args?.stroke || "var(--edge-stroke, #64748b)",
    "stroke-width": style.selected ? 2.2 : 1.5,
    "stroke-dasharray": edge.args?.lineDash?.join(" ") || (edge.shortcut ? "6 4" : ""),
    "marker-end": style.selected ? "url(#modless-arrow-selected)" : "url(#modless-arrow)",
  });
  g.appendChild(path);
  const label = edge.args?.label || edge.kind;
  if (label && edge.args?.showLabel !== false) {
    g.appendChild(
      textEl((sx + tx) / 2, (sy + ty) / 2 - 6, label, {
        fill: "var(--text-secondary, #94a3b8)",
        fontSize: 10,
        textAnchor: "middle",
      }),
    );
  }
  return g;
}

function renderConnectionPreview(source, drag) {
  const sw = finiteNumber(source.width, DEFAULT_SIZE.width);
  const sh = finiteNumber(source.height, DEFAULT_SIZE.height);
  const sx = finiteNumber(source.x) + sw / 2;
  const sy = finiteNumber(source.y) + sh;
  const tx = finiteNumber(drag.x, sx);
  const ty = finiteNumber(drag.y, sy);
  return svgEl("path", {
    d: `M ${sx} ${sy} L ${tx} ${ty}`,
    fill: "none",
    stroke: "var(--accent, #00a6e0)",
    "stroke-width": 1.5,
    "stroke-dasharray": "4 4",
    opacity: 0.8,
  });
}

function svgEl(tag, attrs = {}) {
  const el = document.createElementNS("http://www.w3.org/2000/svg", tag);
  for (const [key, value] of Object.entries(attrs)) {
    const safe = safeSvgValue(key, value);
    if (safe !== null) {
      el.setAttribute(key, String(safe));
    }
  }
  return el;
}

function textEl(x, y, value, { fill, fontSize = 12, fontWeight = 400, textAnchor = "start" } = {}) {
  const text = String(value ?? "");
  if (!text.trim()) {
    return svgEl("text");
  }
  const el = svgEl("text", {
    x: finiteNumber(x),
    y: finiteNumber(y),
    fill,
    "font-size": fontSize,
    "font-weight": fontWeight,
    "text-anchor": textAnchor,
  });
  el.textContent = escapeXml(text);
  return el;
}

export function showInlineLabelEditor(host, node, { onCommit, onCancel }) {
  const overlay = host.querySelector(".glsp-overlay-root");
  if (!overlay || !node) {
    return;
  }
  overlay.innerHTML = "";
  const input = document.createElement("input");
  input.className = "glsp-inline-label-editor";
  const viewport = readViewportFromHost(host);
  input.value = node.label || "";
  input.style.left = `${(node.x || 0) * viewport.scale + viewport.x + 8}px`;
  input.style.top = `${(node.y || 0) * viewport.scale + viewport.y + 34}px`;
  const commit = () => {
    onCommit?.(input.value.trim());
    overlay.innerHTML = "";
  };
  input.addEventListener("keydown", (event) => {
    if (event.key === "Enter") {
      commit();
    }
    if (event.key === "Escape") {
      onCancel?.();
      overlay.innerHTML = "";
    }
  });
  input.addEventListener("blur", commit);
  overlay.appendChild(input);
  input.focus();
  input.select();
}
