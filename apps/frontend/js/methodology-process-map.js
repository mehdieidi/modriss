/**
 * SPEM process map with semantic zoom: process → phase → stage → sub-stage → task.
 */
import { state } from "./state.js";
import { el } from "./dom.js";
import { escapeHtml } from "./utils.js";
import { isModelingLevel, modelingElementDefinition } from "./modeling-config-data.js";
import {
  guidanceForProcess,
  processDisplayTitle,
  renderableLoopEdges,
  roleForTask,
  tasksForStage,
} from "./methodology-process-utils.js";
import { renderMethodLibrary } from "./methodology-library.js";

const ICON_BASE = "/assets/icons/process-map";
const NODE_W = 148;
const NODE_H = 88;
const NODE_GAP = 36;
const PAD = 56;
const ICON_SIZE = 26;
const TASK_NODE_H = 76;
const TASK_NODE_W = 140;

const LIFECYCLE_GATES = [
  ["G0", "Pursue, explore, redirect, or stop"],
  ["G1", "Method and organization ready"],
  ["G2", "CIM accepted for this slice"],
  ["G3", "PIM accepted and platform-mappable"],
  ["G4", "PSM accepted for generation"],
  ["G5", "Increment accepted, rework, or defer"],
  ["G6", "Release authorized, rejected, or exception accepted"],
  ["G7", "Release transitioned to operations"],
  ["G8", "Lifecycle closed after retirement"],
];

const PHASE_ICONS = {
  establishment: "charter.svg",
  posture: "charter.svg",
  foundation: "cloud.svg",
  discovery: "discover.svg",
  context: "discover.svg",
  exploration: "explore.svg",
  contracts: "data.svg",
  data: "data.svg",
  compute: "code.svg",
  exposure: "code.svg",
  integration: "integration.svg",
  orchestration: "integration.svg",
  assurance: "gate.svg",
  synthesis: "synthesize.svg",
  convergence: "gate.svg",
  readiness: "gate.svg",
  network: "integration.svg",
  storage: "data.svg",
  messaging: "integration.svg",
  identity: "discover.svg",
  deployment: "cloud.svg",
};

let mapZoom = 1;
let mapPan = { x: 0, y: 0 };
let mapProcessKind = null;
let mapHistory = [];
let mapHistoryIndex = -1;
let activeMapScaler = null;

function iconForName(name, id = "") {
  const text = `${name} ${id}`.toLowerCase();
  for (const [key, file] of Object.entries(PHASE_ICONS)) {
    if (text.includes(key)) return `${ICON_BASE}/${file}`;
  }
  return `${ICON_BASE}/milestone.svg`;
}

function leafStages(stage) {
  if (stage?.subStages?.length) return stage.subStages.flatMap(leafStages);
  return stage ? [stage] : [];
}

function findStageById(stages, stageId) {
  for (const stage of stages || []) {
    if (stage.id === stageId) return stage;
    const nested = findStageById(stage.subStages, stageId);
    if (nested) return nested;
  }
  return null;
}

function getMapStack() {
  if (!state.guidedModeling.mapStack?.length) {
    state.guidedModeling.mapStack = [{ type: "process" }];
  }
  return state.guidedModeling.mapStack;
}

function resetMapStack() {
  state.guidedModeling.mapStack = [{ type: "process" }];
}

function pushMapContext(context) {
  getMapStack().push(context);
}

function mapSnapshot() {
  return {
    processKind: mapProcessKind,
    stack: getMapStack().map((context) => ({ ...context })),
    selectedId: state.guidedModeling.mapSelectedId || null,
    libraryBrowser: state.guidedModeling.libraryBrowser
      ? { ...state.guidedModeling.libraryBrowser }
      : null,
  };
}

function recordMapNavigation() {
  mapHistory = mapHistory.slice(0, mapHistoryIndex + 1);
  mapHistory.push(mapSnapshot());
  mapHistoryIndex = mapHistory.length - 1;
}

function resetMapHistory() {
  mapHistory = [mapSnapshot()];
  mapHistoryIndex = 0;
}

function restoreMapHistory(index) {
  if (index < 0 || index >= mapHistory.length) return;
  mapHistoryIndex = index;
  const snapshot = mapHistory[index];
  mapProcessKind = snapshot.processKind;
  state.guidedModeling.mapStack = snapshot.stack.map((context) => ({ ...context }));
  state.guidedModeling.mapSelectedId = snapshot.selectedId;
  state.guidedModeling.libraryBrowser = snapshot.libraryBrowser
    ? { ...snapshot.libraryBrowser }
    : null;
  renderProcessMap();
}

function goBackInMap() {
  restoreMapHistory(mapHistoryIndex - 1);
}

function goForwardInMap() {
  restoreMapHistory(mapHistoryIndex + 1);
}

function navigateToStackIndex(index) {
  const stack = getMapStack();
  state.guidedModeling.mapStack = stack.slice(0, index + 1);
  if (mapProcessKind === "full" && index === 0) {
    state.guidedModeling.libraryBrowser = null;
  }
  const context = state.guidedModeling.mapStack.at(-1);
  state.guidedModeling.mapSelectedId = context?.phaseId || context?.stageId || null;
  recordMapNavigation();
}

function processForMap() {
  if (mapProcessKind === "full") return state.guidedModeling?.endToEnd;
  return (
    state.guidedModeling?.definitions?.[mapProcessKind || state.activeType] ||
    state.guidedModeling?.definitions?.cim
  );
}

function switchMapProcess(processKind) {
  if (processKind !== "full" && !state.guidedModeling?.definitions?.[processKind]) return;
  mapProcessKind = processKind;
  state.guidedModeling.libraryBrowser = null;
  resetMapStack();
  state.guidedModeling.mapSelectedId = null;
  recordMapNavigation();
  renderProcessMap();
}

/** @returns {{ nodes: object[], edges: object[], width: number, height: number, title: string }} */
function buildViewLayout(process, stack) {
  const ctx = stack[stack.length - 1];
  if (ctx.type === "process") return buildProcessView(process);
  if (ctx.type === "phase") return buildPhaseView(process, ctx.phaseId);
  if (ctx.type === "stage") return buildStageView(process, ctx.phaseId, ctx.stageId);
  return buildProcessView(process);
}

function buildProcessView(process) {
  const phases = process.phases || [];
  const enginePhaseIds = new Set(
    (process.processEngine?.cycle || []).flatMap((step) => step.phaseIds || []),
  );
  const items = phases.map((phase) => ({
    id: phase.id,
    label: phase.name,
    sublabel:
      process.processId === "modriss.end-to-end.modeling"
        ? phase.id === "e2e.ph1"
          ? "Release activities repeat inside this phase"
          : phase.id === "e2e.ph2"
            ? "Retirement evidence contributes to shared G8 closure"
            : `${phase.stages?.length || 0} stages · click to explore`
        : enginePhaseIds.has(phase.id)
          ? "Engine phase"
          : "Click to open stages",
    icon: iconForName(phase.name, phase.id),
    kind: "phase",
    drillable: true,
    drill: { type: "phase", phaseId: phase.id },
    phase,
  }));

  const layout = layoutHorizontalRow(items, { rowY: 108 });
  const cycle = process.processEngine?.cycle || [];
  const loop = process.processEngine?.loop;
  const fromPhaseId = phaseIdForEngineStep(cycle, loop?.fromStepId);
  const toPhaseId = phaseIdForEngineStep(cycle, loop?.toStepId);
  if (fromPhaseId && toPhaseId) {
    layout.edges.push({
      from: fromPhaseId,
      to: toPhaseId,
      kind: "loop",
      label: "Iterative-incremental cycle",
    });
  }

  if (process.processId === "modriss.end-to-end.modeling") {
    layout.subtitle =
      "Three sequential phases occur once; increment and release activities repeat only inside Phase 1, while Operations runs as a distinct concurrent process.";
  }

  layout.title = processDisplayTitle(process);
  return layout;
}

function phaseIdForEngineStep(cycle, stepId) {
  return cycle.find((step) => step.id === stepId)?.phaseIds?.[0] || null;
}

function buildPhaseView(process, phaseId) {
  const phase = process.phases?.find((p) => p.id === phaseId);
  if (!phase) return buildProcessView(process);

  const stages = phase.stages || [];
  const items = stages.map((stage) => {
    const hasSub = stage.subStages?.length > 0;
    const tasks = tasksForStage(process, stage);
    const hasTasks = tasks.length > 0;
    const drillable = hasSub || hasTasks;
    const childProcess =
      process.processId === "modriss.end-to-end.modeling" ? childProcessLevel(stage) : null;
    return {
      id: stage.id,
      label: stage.name,
      sublabel: childProcess
        ? `${childProcess.toUpperCase()} process · click to open`
        : hasSub
          ? `${stage.subStages.length} sub-stage(s). Click to open`
          : hasTasks
            ? `${tasks.length} task(s). Click to open`
            : stage.objective?.slice(0, 40),
      icon: iconForName(stage.name, stage.id),
      kind: "stage",
      drillable,
      drill: hasSub
        ? { type: "stage", phaseId, stageId: stage.id }
        : hasTasks
          ? { type: "stage", phaseId, stageId: stage.id }
          : null,
      phase,
      stage,
    };
  });

  const layout = layoutHorizontalRow(items, { rowY: 130 });
  if (process.processId === "modriss.end-to-end.modeling") {
    const visibleStageIds = new Set(layout.nodes.map((node) => node.id));
    const loops = (process.workSequences || [])
      .filter(
        (sequence) =>
          ["iteration-loop", "rework"].includes(sequence.relation) &&
          visibleStageIds.has(sequence.predecessorRef) &&
          visibleStageIds.has(sequence.successorRef),
      )
      .map((sequence, index) => ({
        from: sequence.predecessorRef,
        to: sequence.successorRef,
        kind: sequence.relation === "rework" ? "rework" : "loop",
        label: sequence.condition || sequence.guidance || "Repeat or route work",
        lane: index,
      }));
    layout.edges.push(...loops);
  }
  layout.title = phase.name;
  layout.subtitle = phase.objective;
  return layout;
}

function buildStageView(process, phaseId, stageId) {
  const phase = process.phases?.find((p) => p.id === phaseId);
  const stage = findStageById(phase?.stages, stageId);
  if (!stage) return buildPhaseView(process, phaseId);

  if (stage.subStages?.length) {
    const items = stage.subStages.map((sub) => ({
      id: sub.id,
      label: sub.name,
      sublabel: tasksForStage(process, sub).length
        ? `${tasksForStage(process, sub).length} task(s)`
        : sub.objective?.slice(0, 36),
      icon: iconForName(sub.name, sub.id),
      kind: "substage",
      drillable: tasksForStage(process, sub).length > 0,
      drill: { type: "stage", phaseId, stageId: sub.id },
      phase,
      stage: sub,
    }));
    const layout = layoutHorizontalRow(items, { rowY: 130 });
    layout.title = `${stage.name}: Sub-stages`;
    layout.subtitle = stage.objective;
    return layout;
  }

  const tasks = tasksForStage(process, stage);
  const items = tasks.map((task) => ({
    id: task.id,
    label: task.name,
    sublabel: task.durationEstimate || task.viewpoint || "",
    icon: `${ICON_BASE}/code.svg`,
    kind: "task",
    drillable: false,
    phase,
    stage,
    task,
    width: TASK_NODE_W,
    height: TASK_NODE_H,
  }));

  const layout = layoutHorizontalRow(items, {
    rowY: 130,
    nodeW: TASK_NODE_W,
    nodeH: TASK_NODE_H,
    gap: 28,
  });
  layout.title = `${stage.name}: Tasks`;
  layout.subtitle = stage.objective;
  return layout;
}

function layoutHorizontalRow(items, opts = {}) {
  const nodeW = opts.nodeW || NODE_W;
  const nodeH = opts.nodeH || NODE_H;
  const gap = opts.gap || NODE_GAP;
  const rowY = opts.rowY || 120;
  const startX = PAD;

  const nodes = items.map((item, i) => ({
    ...item,
    x: startX + i * (nodeW + gap),
    y: rowY,
    width: item.width || nodeW,
    height: item.height || nodeH,
  }));

  const edges = [];
  for (let i = 1; i < nodes.length; i++) {
    edges.push({ from: nodes[i - 1].id, to: nodes[i].id, kind: "flow" });
  }

  const width = Math.max(startX + nodes.length * (nodeW + gap) + PAD, 720);
  const height = rowY + nodeH + PAD + 40;

  return { nodes, edges, width, height };
}

function nodeSize(node) {
  return { w: node.width || NODE_W, h: node.height || NODE_H };
}

function anchor(node, side) {
  const { w, h } = nodeSize(node);
  switch (side) {
    case "right":
      return { x: node.x + w, y: node.y + h / 2 };
    case "left":
      return { x: node.x, y: node.y + h / 2 };
    case "bottom":
      return { x: node.x + w / 2, y: node.y + h };
    case "top":
      return { x: node.x + w / 2, y: node.y };
    default:
      return { x: node.x + w / 2, y: node.y + h / 2 };
  }
}

function edgePath(edge, nodeById, loopRoutes) {
  const a = nodeById.get(edge.from);
  const b = nodeById.get(edge.to);
  if (!a || !b) return "";

  if (edge.kind === "loop" || edge.kind === "rework") {
    const route = loopRoutes.get(edge);
    const p1 = route?.start || anchor(a, "bottom");
    const p2 = route?.end || anchor(b, "bottom");
    const loopY = route?.controlY ?? (p1.y + p2.y) / 2 + 44;
    const targetY = p2.y + 5;
    return `M ${p1.x} ${p1.y} C ${p1.x} ${loopY}, ${p2.x} ${loopY}, ${p2.x} ${targetY}`;
  }

  const p1 = anchor(a, "right");
  const p2 = anchor(b, "left");
  p2.x -= 5;
  if (Math.abs(p1.y - p2.y) < 4) {
    return `M ${p1.x} ${p1.y} H ${p2.x}`;
  }
  const midX = (p1.x + p2.x) / 2;
  return `M ${p1.x} ${p1.y} H ${midX} V ${p2.y} H ${p2.x}`;
}

function computeBounds(layout, loopRoutes) {
  let maxX = layout.width;
  let maxY = layout.height;
  for (const node of layout.nodes) {
    const { w, h } = nodeSize(node);
    maxX = Math.max(maxX, node.x + w + PAD);
    maxY = Math.max(maxY, node.y + h + PAD);
  }
  for (const route of loopRoutes.values()) {
    const targetY = route.end.y + 5;
    const curveLowPoint = (route.start.y + targetY) / 8 + route.controlY * 0.75;
    maxY = Math.max(maxY, curveLowPoint + 32);
  }
  return { width: maxX, height: maxY };
}

function buildLoopEdgeRoutes(edges, nodeById) {
  // A layout can show only part of a process. Ignore relationships whose
  // endpoints are outside that view instead of trying to anchor a missing node.
  const loopEdges = renderableLoopEdges(edges, nodeById);
  const routes = new Map(
    loopEdges.map((edge) => [
      edge,
      {
        start: anchor(nodeById.get(edge.from), "bottom"),
        end: anchor(nodeById.get(edge.to), "bottom"),
      },
    ]),
  );

  for (const node of nodeById.values()) {
    const { w } = nodeSize(node);
    for (const direction of ["outgoing", "incoming"]) {
      // Match attachment order to the other endpoint's x position to avoid crossed starts and tips.
      const connected = loopEdges
        .filter((edge) => (direction === "outgoing" ? edge.from : edge.to) === node.id)
        .sort((a, b) => {
          const otherNodeId = direction === "outgoing" ? "to" : "from";
          const aNode = nodeById.get(a[otherNodeId]);
          const bNode = nodeById.get(b[otherNodeId]);
          const aX = aNode ? aNode.x + nodeSize(aNode).w / 2 : 0;
          const bX = bNode ? bNode.x + nodeSize(bNode).w / 2 : 0;
          return aX - bX || (a.lane || 0) - (b.lane || 0);
        });
      if (!connected.length) continue;

      const firstFraction = direction === "outgoing" ? 0.18 : 0.58;
      const lastFraction = direction === "outgoing" ? 0.42 : 0.82;
      connected.forEach((edge, index) => {
        const ratio = connected.length === 1 ? 0.5 : index / (connected.length - 1);
        const x = node.x + w * (firstFraction + (lastFraction - firstFraction) * ratio);
        const route = routes.get(edge);
        route[direction === "outgoing" ? "start" : "end"].x = x;
      });
    }
  }

  const routeOrder = loopEdges
    .map((edge) => {
      const route = routes.get(edge);
      return { edge, route, span: Math.abs(route.end.x - route.start.x) };
    })
    .sort((a, b) => a.span - b.span || (a.edge.lane || 0) - (b.edge.lane || 0));

  routeOrder.forEach(({ route, span }, index) => {
    const averageBottom = (route.start.y + route.end.y) / 2;
    route.controlY = averageBottom + 44 + span * 0.12 + index * 20;
  });

  return routes;
}

function renderBreadcrumb(process, stack) {
  const host = el.methodologyMapBreadcrumb;
  if (!host) return;

  const parts = [];
  stack.forEach((ctx, i) => {
    let label =
      i === 0
        ? mapProcessKind === "full"
          ? "Full Process"
          : processDisplayTitle(process)
        : "Process";
    if (ctx.type === "phase") {
      label = process.phases?.find((p) => p.id === ctx.phaseId)?.name || "Phase";
    } else if (ctx.type === "stage") {
      const phase = process.phases?.find((p) => p.id === ctx.phaseId);
      label = findStageById(phase?.stages, ctx.stageId)?.name || "Stage";
    }
    const isLast = i === stack.length - 1;
    parts.push(
      `<button type="button" class="map-crumb${isLast ? " is-current" : ""}" data-crumb-index="${i}"${isLast ? ' aria-current="location"' : ""}>${escapeHtml(label)}</button>`,
    );
    if (!isLast) parts.push('<span class="map-crumb-sep" aria-hidden="true">›</span>');
  });

  host.innerHTML = parts.join("");
  host.querySelectorAll(".map-crumb").forEach((btn) => {
    btn.addEventListener("click", () => {
      navigateToStackIndex(Number(btn.dataset.crumbIndex));
      state.guidedModeling.mapSelectedId = null;
      renderProcessMap();
    });
  });
}

function renderSvg(layout, selectedId, onNodeClick) {
  const nodeById = new Map(layout.nodes.map((n) => [n.id, n]));
  const loopRoutes = buildLoopEdgeRoutes(layout.edges, nodeById);
  const bounds = computeBounds(layout, loopRoutes);
  const svgNs = "http://www.w3.org/2000/svg";

  const svg = document.createElementNS(svgNs, "svg");
  svg.setAttribute("class", "methodology-map-svg");
  svg.setAttribute("viewBox", `0 0 ${bounds.width} ${bounds.height}`);
  svg.setAttribute("preserveAspectRatio", "xMidYMid meet");
  svg.setAttribute("role", "img");

  const defs = document.createElementNS(svgNs, "defs");
  defs.innerHTML = `
    <marker id="mapArrowFlow" viewBox="0 0 12 12" markerWidth="13" markerHeight="13" refX="10.8" refY="6" orient="auto" markerUnits="userSpaceOnUse">
      <path d="M 1 1 Q 5 5 10.8 6 M 1 11 Q 5 7 10.8 6" class="methodology-map-edge-marker"/>
    </marker>
    <marker id="mapArrowLoop" viewBox="0 0 12 12" markerWidth="13" markerHeight="13" refX="10.8" refY="6" orient="auto" markerUnits="userSpaceOnUse">
      <path d="M 1 1 Q 5 5 10.8 6 M 1 11 Q 5 7 10.8 6" class="methodology-map-edge-marker is-loop"/>
    </marker>
    <marker id="mapArrowRework" viewBox="0 0 12 12" markerWidth="13" markerHeight="13" refX="10.8" refY="6" orient="auto" markerUnits="userSpaceOnUse">
      <path d="M 1 1 Q 5 5 10.8 6 M 1 11 Q 5 7 10.8 6" class="methodology-map-edge-marker is-rework"/>
    </marker>`;
  svg.appendChild(defs);

  const edgesG = document.createElementNS(svgNs, "g");
  edgesG.setAttribute("class", "methodology-map-edges");
  const edgeLabelsG = document.createElementNS(svgNs, "g");
  edgeLabelsG.setAttribute("class", "methodology-map-edge-labels");
  for (const edge of layout.edges) {
    const d = edgePath(edge, nodeById, loopRoutes);
    if (!d) continue;
    const path = document.createElementNS(svgNs, "path");
    path.setAttribute("d", d);
    path.setAttribute(
      "class",
      `methodology-map-edge${edge.kind === "loop" ? " is-loop" : ""}${edge.kind === "rework" ? " is-rework" : ""}`,
    );
    const markerId =
      edge.kind === "loop"
        ? "mapArrowLoop"
        : edge.kind === "rework"
          ? "mapArrowRework"
          : "mapArrowFlow";
    path.setAttribute("marker-end", `url(#${markerId})`);
    edgesG.appendChild(path);

    if ((edge.kind === "loop" || edge.kind === "rework") && edge.label) {
      const a = nodeById.get(edge.from);
      const b = nodeById.get(edge.to);
      if (a && b) {
        const { start, end, controlY } = loopRoutes.get(edge);
        const curveLowPoint = (start.y + end.y + 5) / 8 + controlY * 0.75;
        const labelCopy = truncate(edge.label, 46);
        const label = document.createElementNS(svgNs, "g");
        label.setAttribute(
          "class",
          `map-edge-label${edge.kind === "rework" ? " is-rework" : " is-loop"}`,
        );
        label.setAttribute(
          "transform",
          `translate(${(start.x + end.x) / 2}, ${curveLowPoint + 17})`,
        );

        const text = document.createElementNS(svgNs, "text");
        text.setAttribute("class", "map-edge-label-copy");
        text.setAttribute("text-anchor", "middle");
        text.setAttribute("y", "0");
        const title = document.createElementNS(svgNs, "title");
        title.textContent = `${a.label} → ${b.label}: ${edge.label}`;
        text.appendChild(title);
        const kind = document.createElementNS(svgNs, "tspan");
        kind.setAttribute("class", "map-edge-label-kind");
        kind.textContent = edge.kind === "rework" ? "REWORK · " : "ITERATION · ";
        text.appendChild(kind);
        const copy = document.createElementNS(svgNs, "tspan");
        copy.textContent = labelCopy;
        text.appendChild(copy);
        label.appendChild(text);
        edgeLabelsG.appendChild(label);
      }
    }
  }
  svg.appendChild(edgesG);
  svg.appendChild(edgeLabelsG);

  const nodesG = document.createElementNS(svgNs, "g");
  nodesG.setAttribute("class", "methodology-map-nodes");
  for (const node of layout.nodes) {
    const { w, h } = nodeSize(node);
    const g = document.createElementNS(svgNs, "g");
    g.setAttribute(
      "class",
      [
        "methodology-map-node",
        `is-${node.kind}`,
        node.id === selectedId ? "is-selected" : "",
        node.drillable ? "is-drillable" : "",
      ]
        .filter(Boolean)
        .join(" "),
    );
    g.setAttribute("tabindex", "0");
    g.setAttribute("role", "button");
    g.setAttribute("transform", `translate(${node.x}, ${node.y})`);

    const bg = document.createElementNS(svgNs, "rect");
    bg.setAttribute("class", "map-node-bg");
    bg.setAttribute("width", String(w));
    bg.setAttribute("height", String(h));
    bg.setAttribute("rx", "12");
    g.appendChild(bg);

    const iconBg = document.createElementNS(svgNs, "rect");
    iconBg.setAttribute("class", "map-node-icon-wrap");
    iconBg.setAttribute("x", String(w / 2 - 24));
    iconBg.setAttribute("y", "10");
    iconBg.setAttribute("width", "48");
    iconBg.setAttribute("height", "36");
    iconBg.setAttribute("rx", "10");
    g.appendChild(iconBg);

    const icon = document.createElementNS(svgNs, "image");
    icon.setAttribute("href", node.icon);
    icon.setAttribute("class", "map-node-icon");
    icon.setAttribute("x", String(w / 2 - ICON_SIZE / 2));
    icon.setAttribute("y", String(18 - ICON_SIZE / 2 + 8));
    icon.setAttribute("width", String(ICON_SIZE));
    icon.setAttribute("height", String(ICON_SIZE));
    g.appendChild(icon);

    if (node.drillable) {
      const chevron = document.createElementNS(svgNs, "text");
      chevron.setAttribute("class", "map-node-chevron");
      chevron.setAttribute("x", String(w - 14));
      chevron.setAttribute("y", "18");
      chevron.textContent = "›";
      g.appendChild(chevron);
    }

    const label = document.createElementNS(svgNs, "text");
    label.setAttribute("class", "map-node-label");
    label.setAttribute("x", String(w / 2));
    label.setAttribute("y", String(h - 26));
    label.textContent = truncate(node.label, 20);
    g.appendChild(label);

    if (node.sublabel) {
      const sub = document.createElementNS(svgNs, "text");
      sub.setAttribute("class", "map-node-sublabel");
      sub.setAttribute("x", String(w / 2));
      sub.setAttribute("y", String(h - 10));
      sub.textContent = truncate(node.sublabel, 24);
      g.appendChild(sub);
    }

    const activate = () => onNodeClick(node);
    g.addEventListener("click", activate);
    g.addEventListener("keydown", (e) => {
      if (e.key === "Enter" || e.key === " ") {
        e.preventDefault();
        activate();
      }
    });
    nodesG.appendChild(g);
  }
  svg.appendChild(nodesG);

  return { svg, bounds };
}

function truncate(text, max) {
  if (!text || text.length <= max) return text || "";
  return `${text.slice(0, max - 1)}…`;
}

function renderDetail(host, node, process, level) {
  if (!node) {
    if (level === "full") {
      const libraryBrowser = state.guidedModeling.libraryBrowser;
      if (libraryBrowser) {
        renderMethodLibrary(host, state.guidedModeling.methodLibrary, libraryBrowser, {
          onCategory(category) {
            state.guidedModeling.libraryBrowser = { category, query: "", itemId: null };
            recordMapNavigation();
            renderProcessMap();
          },
          onItem(itemId) {
            state.guidedModeling.libraryBrowser = {
              ...state.guidedModeling.libraryBrowser,
              itemId,
            };
            recordMapNavigation();
            renderProcessMap();
          },
          onBack() {
            state.guidedModeling.libraryBrowser = {
              ...state.guidedModeling.libraryBrowser,
              itemId: null,
            };
            recordMapNavigation();
            renderProcessMap();
          },
          onClose() {
            state.guidedModeling.libraryBrowser = null;
            recordMapNavigation();
            renderProcessMap();
          },
          onQuery(query) {
            state.guidedModeling.libraryBrowser = { ...libraryBrowser, query };
            mapHistory[mapHistoryIndex] = mapSnapshot();
          },
        });
      } else {
        host.innerHTML = renderFullMethodSummary(process);
        bindFullMethodSummary(host);
        host
          .querySelector('[data-action="browse-method-library"]')
          ?.addEventListener("click", () => {
            state.guidedModeling.libraryBrowser = { category: null, query: "", itemId: null };
            recordMapNavigation();
            renderProcessMap();
          });
      }
      return;
    }
    host.innerHTML = `<div class="methodology-map-detail-empty">
      <p><strong>Semantic zoom</strong></p>
      <p>Click a <strong>phase Activity</strong> to open nested Activities and TaskUses. Use Back or the breadcrumb to zoom out.</p>
    </div>`;
    return;
  }

  const parts = [];
  const phase = node.phase || (node.task ? findPhaseForTask(process, node.task.id) : null);
  const stage =
    node.stage || (node.task && phase ? findStageForTask(process, phase, node.task.id) : null);
  const task = node.task || null;
  const role = roleForTask(process, task, stage, phase);
  const roles = rolesForNode(process, task, stage, phase);
  const artifacts = artifactsForNode(process, phase, stage, task);
  const guidelines = guidelinesForNode(process, phase, stage, task);
  const paletteFocus = paletteFocusForNode(process, phase, stage, task);

  if (node.task) {
    parts.push(`<span class="map-detail-kind">TaskUse</span>`);
    parts.push(`<h3>${escapeHtml(task.name)}</h3>`);
    if (task.purpose) parts.push(`<p>${escapeHtml(task.purpose)}</p>`);
    parts.push(`<p>${escapeHtml(stage?.objective || phase?.objective || "")}</p>`);
    if (roles.length) parts.push(renderRoles(roles));
    else if (role) parts.push(renderRole(role));
    if (task.inputArtifacts?.length) parts.push(renderArtifacts(task.inputArtifacts, "Inputs"));
    if (artifacts.length) parts.push(renderArtifacts(artifacts));
    if (task.steps?.length) {
      parts.push(renderSectionTitle("Steps"));
      parts.push("<ol>");
      task.steps.forEach((s) => parts.push(`<li>${escapeHtml(s)}</li>`));
      parts.push("</ol>");
    }
    if (task.entryCriteria?.length)
      parts.push(renderListSection("Entry criteria", task.entryCriteria));
    if (task.exitCriteria?.length)
      parts.push(renderListSection("Exit criteria", task.exitCriteria));
    if (task.validationRules?.length)
      parts.push(renderListSection("Validation rules", task.validationRules));
    const bindings = (task.metamodelBindings || []).map((binding) => binding.classifier);
    if (bindings.length) {
      parts.push(renderChipSection("Metamodel bindings", bindings.slice(0, 18), level));
    }
    if (paletteFocus.length)
      parts.push(renderChipSection("Related palette elements", paletteFocus, level));
    if (guidelines.length) parts.push(renderGuidelines(guidelines));
  } else if (node.stage) {
    parts.push(`<span class="map-detail-kind">Activity · MODRISS::Stage</span>`);
    parts.push(`<h3>${escapeHtml(node.stage.name)}</h3>`);
    parts.push(`<p>${escapeHtml(node.stage.objective || "")}</p>`);
    if (roles.length) parts.push(renderRoles(roles));
    else if (role) parts.push(renderRole(role));
    if (artifacts.length) parts.push(renderArtifacts(artifacts));
    if (paletteFocus.length)
      parts.push(renderChipSection("Related palette elements", paletteFocus, level));
    if (guidelines.length) parts.push(renderGuidelines(guidelines));
    const stageTasks = tasksForStage(process, node.stage);
    if (stageTasks.length) {
      parts.push(renderSectionTitle("Tasks"));
      parts.push("<ul>");
      stageTasks.forEach((t) => parts.push(`<li>${escapeHtml(t.name)}</li>`));
      parts.push("</ul>");
    }
  } else if (node.phase) {
    const primaryTask = (phase.stages || [])
      .flatMap((stage) => leafStages(stage).flatMap((leaf) => tasksForStage(process, leaf)))
      .find((task) => task);
    const narrative = {
      summary: phase.summary || phase.objective || primaryTask?.steps?.[0] || phase.name || "",
    };
    parts.push(`<span class="map-detail-kind">Activity · Phase</span>`);
    parts.push(`<h3>${escapeHtml(phase.name)}</h3>`);
    parts.push(`<p>${escapeHtml(phase.objective || narrative.summary)}</p>`);
    if (roles.length) parts.push(renderRoles(roles));
    else if (role) parts.push(renderRole(role));
    if (artifacts.length) parts.push(renderArtifacts(artifacts));
    if (paletteFocus.length)
      parts.push(renderChipSection("Related palette elements", paletteFocus, level));
    if (guidelines.length) parts.push(renderGuidelines(guidelines));
    if (phase.entryCriteria?.length) parts.push(renderListSection("Entry", phase.entryCriteria));
    if (phase.exitCriteria?.length) parts.push(renderListSection("Exit", phase.exitCriteria));
  }

  const canGuide = level === state.activeType && (node.phase || node.stage || node.task);
  if (canGuide) {
    const taskActions = node.task
      ? `<div class="methodology-map-detail-action-row">
           <button type="button" class="btn btn-secondary btn-sm" data-action="palette">Palette elements</button>
           <button type="button" class="btn btn-secondary btn-sm" data-action="ask-ai">Ask AI</button>
         </div>`
      : node.stage && paletteFocus.length
        ? `<button type="button" class="btn btn-secondary btn-full" data-action="palette">Show related palette elements</button>`
        : "";
    parts.push(`<div class="methodology-map-detail-actions">
      ${taskActions}
      <button type="button" class="methodology-map-detail-link" data-action="goto-guide">Show in process guide</button>
    </div>`);
  }

  host.innerHTML = parts.join("");
  host.querySelector('[data-action="goto-guide"]')?.addEventListener("click", async () => {
    const { selectGuidedPhase, selectGuidedStage } = await import("./guided-modeling.js");
    const process = state.guidedModeling?.definitions?.[state.activeType];
    const phase = node.phase || (node.task ? findPhaseForTask(process, node.task.id) : null);
    if (phase) selectGuidedPhase(phase.id);
    const targetStage =
      node.stage || (node.task && phase ? findStageForTask(process, phase, node.task.id) : null);
    if (targetStage) selectGuidedStage(targetStage.id);
    closeMethodologyMap();
  });
  host.querySelector('[data-action="palette"]')?.addEventListener("click", async () => {
    const { openPaletteForCurrentPhase } = await import("./guided-modeling.js");
    closeMethodologyMap();
    await openPaletteForCurrentPhase();
  });
  host.querySelector('[data-action="ask-ai"]')?.addEventListener("click", async () => {
    const { openAssistantForGuidedPhase } = await import("./guided-modeling.js");
    closeMethodologyMap();
    openAssistantForGuidedPhase();
  });
}

function rolesForNode(process, task, stage, phase) {
  const useIds = task?.performerRoleUseRefs || stage?.roleUseRefs || phase?.roleUseRefs || [];
  const roleIds = new Set([
    ...(task?.performerRoleRefs || []),
    ...useIds
      .map((useId) => (process.roleUses || []).find((use) => use.id === useId)?.roleDefinitionRef)
      .filter(Boolean),
  ]);
  return [...roleIds]
    .map((roleId) =>
      (process.methodContent?.roleDefinitions || []).find((role) => role.id === roleId),
    )
    .filter(Boolean);
}

function renderRoles(roles) {
  return `${renderSectionTitle(roles.length > 1 ? "Responsible roles" : "Role")}${roles
    .map((role) => {
      const responsibilities = (role.responsibilities || []).join("; ");
      return `<p><strong>${escapeHtml(role.name)}</strong>${responsibilities ? `: ${escapeHtml(responsibilities)}` : ""}</p>`;
    })
    .join("")}`;
}

function childProcessLevel(stage) {
  return (
    {
      "e2e.p1.cim-modeling": "cim",
      "e2e.p3.pim-refinement": "pim",
      "e2e.p5.psm-refinement": "psm",
      "e2e.p7.artifact-completion": "artifact",
    }[stage?.id] || null
  );
}

function renderFullMethodSummary(process) {
  const engine = process.processEngine || {};
  const roles = process.methodContent?.roleDefinitions || [];
  const products = process.methodContent?.workProductDefinitions || [];
  const milestones = process.milestones || [];
  const changeWorkflows = process.changeManagement?.workflows || [];
  const guidance = guidanceForProcess(process);
  const library = state.guidedModeling?.methodLibrary;
  const releaseCycle = engine.releaseCycle;
  const disciplines = [
    "Product and project management",
    "Risk and opportunity management",
    "Quality assurance",
    "Security and privacy",
    "Configuration, change, and traceability",
    "FinOps and cost management",
    "Documentation and knowledge retention",
    "Measurement and method improvement",
  ];
  const phases = process.phases || [];
  const entryEvidence = process.governance?.entryEvidence || [];
  const exitEvidence = process.governance?.exitEvidence || [];
  const allTasks = process.methodContent?.taskDefinitions || [];
  const stat = (value, label) =>
    `<div class="process-stat"><strong>${value}</strong><span>${label}</span></div>`;
  return `<div class="full-process-summary">
    <div class="full-process-hero">
      <span class="map-detail-kind">Full Process</span>
      <h3>From product intent to lasting operation</h3>
      <p>Explore the complete method without losing your place. Select a view, inspect a gate, or expand any card for its full definition.</p>
      <div class="process-stat-grid">
        ${stat(phases.length, "phases")}${stat(milestones.length, "gates")}${stat(roles.length, "roles")}${stat(products.length, "work products")}
      </div>
    </div>
    <div class="process-view-tabs" role="tablist" aria-label="Full process views">
      ${[
        ["overview", "Overview"],
        ["gates", "Gates"],
        ["roles", "Roles"],
        ["outputs", "Outputs"],
        ["guidance", "Guidance"],
      ]
        .map(
          ([id, label], index) =>
            `<button type="button" role="tab" data-process-tab="${id}" aria-selected="${index === 0}" class="${index === 0 ? "is-active" : ""}">${label}</button>`,
        )
        .join("")}
    </div>
    <section class="process-view is-active" data-process-view="overview" role="tabpanel">
      <div class="process-cadence" aria-label="Lifecycle cadence diagram">
        <div class="process-cadence-node"><span>01</span><strong>Intent</strong><small>Enter with evidence</small></div>
        <div class="process-cadence-arrow" aria-hidden="true">→</div>
        <div class="process-cadence-node is-cycle"><span>∞</span><strong>CIM → PIM → AWS PSM → artifact</strong><small>Repeatable Phase 1 increment</small></div>
        <div class="process-cadence-arrow" aria-hidden="true">→</div>
        <div class="process-cadence-node"><span>03</span><strong>Operate</strong><small>Release to retirement</small></div>
      </div>
      <div class="process-callout"><span aria-hidden="true">↻</span><div><strong>Lifecycle cadence</strong><p>${escapeHtml(releaseCycle?.guidance || engine.description || "")}</p></div></div>
      <div class="process-outcome"><span>Increment outcome</span><strong>${escapeHtml(engine.deliverable?.description || "")}</strong></div>
      <div class="process-evidence-grid">
        <div><h4><span aria-hidden="true">↓</span> Entry evidence</h4>${entryEvidence.map((item) => `<span>${escapeHtml(item)}</span>`).join("") || "<span>None specified</span>"}</div>
        <div><h4><span aria-hidden="true">↑</span> Exit evidence</h4>${exitEvidence.map((item) => `<span>${escapeHtml(item)}</span>`).join("") || "<span>None specified</span>"}</div>
      </div>
      <div class="process-gate-banner"><span>Process gate</span><strong>${escapeHtml(process.governance?.gate || "Not specified")}</strong></div>
      ${renderSectionTitle("Continuous disciplines")}
      <div class="process-discipline-grid">${disciplines.map((item, index) => `<div><span>${index + 1}</span>${escapeHtml(item)}</div>`).join("")}</div>
    </section>
    <section class="process-view" data-process-view="gates" role="tabpanel" hidden>
      <div class="process-view-intro"><strong>Decision path</strong><span>Select a gate to reveal its milestone and lifecycle phase.</span></div>
      <div class="process-gate-rail">${LIFECYCLE_GATES.map(([id, name], index) => {
        const milestone = milestones.find((item) => item.gateId === id || item.name === name);
        const phase = process.phases?.find((candidate) => candidate.id === milestone?.phaseId);
        return `<button type="button" class="process-gate-node${index === 0 ? " is-active" : ""}" data-gate-index="${index}" aria-pressed="${index === 0}"><span>${escapeHtml(id)}</span><strong>${escapeHtml(name)}</strong><small>${escapeHtml(phase?.name || "Lifecycle decision")}</small></button>`;
      }).join("")}</div>
      <div class="process-gate-detail process-explorer-detail" aria-live="polite"></div>
    </section>
    <section class="process-view" data-process-view="roles" role="tabpanel" hidden>
      <div class="process-view-intro"><strong>Accountability map</strong><span>Select a role to see where it acts across the lifecycle.</span></div>
      <div class="process-role-selector" role="listbox" aria-label="Process roles">${roles
        .map((role, index) => {
          const taskCount = allTasks.filter((task) =>
            (task.performerRoleRefs || []).includes(role.id),
          ).length;
          const gateCount = milestones.filter((gate) =>
            (gate.authorityRoleRefs || []).includes(role.id),
          ).length;
          return `<button type="button" role="option" class="process-role-option${index === 0 ? " is-active" : ""}" data-role-index="${index}" aria-selected="${index === 0}"><span>${String(index + 1).padStart(2, "0")}</span><strong>${escapeHtml(role.name)}</strong><small>${taskCount} tasks · ${gateCount} gates</small></button>`;
        })
        .join("")}</div>
      <div class="process-role-detail" aria-live="polite">${roles[0] ? renderRoleExplorerDetail(roles[0], process) : ""}</div>
    </section>
    <section class="process-view" data-process-view="outputs" role="tabpanel" hidden>
      <div class="process-view-intro"><strong>Lifecycle work products</strong><span>Select an output to trace who creates it, who uses it, and where.</span></div>
      <div class="process-role-selector" role="listbox" aria-label="Lifecycle work products">${products
        .map((product, index) => {
          const producers = allTasks.filter((task) =>
            (task.outputWorkProductRefs || []).includes(product.id),
          ).length;
          const consumers = allTasks.filter((task) =>
            (task.inputWorkProductRefs || []).includes(product.id),
          ).length;
          return `<button type="button" role="option" class="process-role-option${index === 0 ? " is-active" : ""}" data-output-index="${index}" aria-selected="${index === 0}"><span>${String(index + 1).padStart(2, "0")}</span><strong>${escapeHtml(product.name)}</strong><small>${producers} producers · ${consumers} consumers</small></button>`;
        })
        .join("")}</div>
      <div class="process-role-detail process-output-detail" aria-live="polite">${products[0] ? renderOutputExplorerDetail(products[0], process) : ""}</div>
    </section>
    <section class="process-view" data-process-view="guidance" role="tabpanel" hidden>
      <div class="process-view-intro"><strong>Method guidance</strong><span>Select guidance to see its scope and full recommendation.</span></div>
      <div class="process-role-selector" role="listbox" aria-label="Method guidance">${guidance.map((item, index) => `<button type="button" role="option" class="process-role-option${index === 0 ? " is-active" : ""}" data-guidance-index="${index}" aria-selected="${index === 0}"><span>${String(index + 1).padStart(2, "0")}</span><strong>${escapeHtml(item.name)}</strong><small>${escapeHtml(item.appliesTo || "Process-wide")}</small></button>`).join("")}</div>
      <div class="process-role-detail process-guidance-detail" aria-live="polite">${guidance[0] ? renderGuidanceExplorerDetail(guidance[0], process) : ""}</div>
      ${changeWorkflows.length ? `${renderSectionTitle("Change and feedback workflows")}<div class="process-workflows">${changeWorkflows.map((workflow) => `<details><summary><strong>${escapeHtml(workflow.name)}</strong><span>${escapeHtml(workflow.trigger)}</span></summary><ol>${(workflow.steps || []).map((step) => `<li>${escapeHtml(step)}</li>`).join("")}</ol></details>`).join("")}</div>` : ""}
    </section>
    <div class="method-library-summary">
      <strong>Complete process content library</strong>
      <p>${library ? `${library.roleDefinitions?.length || 0} roles · ${library.taskDefinitions?.length || 0} tasks · ${library.workProductDefinitions?.length || 0} work products · ${library.guidance?.length || 0} guidance items` : "Loading process content"}</p>
      <button type="button" class="btn btn-secondary btn-full" data-action="browse-method-library"${library ? "" : " disabled"}>Browse process content</button>
    </div>
  </div>`;
}

function bindFullMethodSummary(host) {
  const showGate = (index) => {
    const [id, name] = LIFECYCLE_GATES[index] || LIFECYCLE_GATES[0];
    const process = processForMap();
    const milestone = (process?.milestones || []).find(
      (item) => item.gateId === id || item.name === name,
    );
    const detail = host.querySelector(".process-gate-detail");
    host.querySelectorAll("[data-gate-index]").forEach((button) => {
      const active = Number(button.dataset.gateIndex) === index;
      button.classList.toggle("is-active", active);
      button.setAttribute("aria-pressed", String(active));
    });
    if (detail)
      detail.innerHTML = milestone
        ? renderGateExplorerDetail(milestone, process)
        : `<div class="process-role-accountability"><span>${escapeHtml(id)}</span><div><strong>${escapeHtml(name)}</strong><p>No milestone definition is available.</p></div></div>`;
  };

  host.querySelectorAll("[data-process-tab]").forEach((tab) =>
    tab.addEventListener("click", () => {
      const target = tab.dataset.processTab;
      host.querySelectorAll("[data-process-tab]").forEach((item) => {
        const active = item === tab;
        item.classList.toggle("is-active", active);
        item.setAttribute("aria-selected", String(active));
      });
      host.querySelectorAll("[data-process-view]").forEach((view) => {
        const active = view.dataset.processView === target;
        view.classList.toggle("is-active", active);
        view.hidden = !active;
      });
    }),
  );
  host.querySelectorAll("[data-role-index]").forEach((option) =>
    option.addEventListener("click", () => {
      const index = Number(option.dataset.roleIndex);
      const process = processForMap();
      const role = process?.methodContent?.roleDefinitions?.[index];
      if (!role) return;
      host.querySelectorAll("[data-role-index]").forEach((item) => {
        const active = item === option;
        item.classList.toggle("is-active", active);
        item.setAttribute("aria-selected", String(active));
      });
      const detail = host.querySelector(".process-role-detail");
      if (detail) detail.innerHTML = renderRoleExplorerDetail(role, process);
    }),
  );
  host.querySelectorAll("[data-output-index]").forEach((option) =>
    option.addEventListener("click", () => {
      const process = processForMap();
      const product =
        process?.methodContent?.workProductDefinitions?.[Number(option.dataset.outputIndex)];
      if (!product) return;
      selectExplorerOption(host, "data-output-index", option);
      const detail = host.querySelector(".process-output-detail");
      if (detail) detail.innerHTML = renderOutputExplorerDetail(product, process);
    }),
  );
  host.querySelectorAll("[data-guidance-index]").forEach((option) =>
    option.addEventListener("click", () => {
      const process = processForMap();
      const guidance = guidanceForProcess(process);
      const item = guidance[Number(option.dataset.guidanceIndex)];
      if (!item) return;
      selectExplorerOption(host, "data-guidance-index", option);
      const detail = host.querySelector(".process-guidance-detail");
      if (detail) detail.innerHTML = renderGuidanceExplorerDetail(item, process);
    }),
  );
  host
    .querySelectorAll("[data-gate-index]")
    .forEach((gate) =>
      gate.addEventListener("click", () => showGate(Number(gate.dataset.gateIndex))),
    );
  showGate(0);
}

function selectExplorerOption(host, attribute, selected) {
  host.querySelectorAll(`[${attribute}]`).forEach((item) => {
    const active = item === selected;
    item.classList.toggle("is-active", active);
    item.setAttribute("aria-selected", String(active));
  });
}

function phasesForTaskDefinitions(process, tasks) {
  const taskDefinitionIds = new Set(tasks.map((task) => task.id));
  return (process.phases || []).filter((phase) =>
    (phase.stages || [])
      .flatMap(leafStages)
      .some((stage) =>
        tasksForStage(process, stage).some((task) =>
          taskDefinitionIds.has(task.taskDefinitionRef || task.id),
        ),
      ),
  );
}

function renderExplorerChips(items, label) {
  return items.length
    ? items
        .map((item) => `<span class="process-role-chip">${escapeHtml(label(item))}</span>`)
        .join("")
    : `<span class="process-role-empty">No direct assignments</span>`;
}

function renderGateExplorerDetail(gate, process) {
  const phase = (process.phases || []).find((item) => item.id === gate.phaseId);
  const roles = (process.methodContent?.roleDefinitions || []).filter((role) =>
    (gate.authorityRoleRefs || []).includes(role.id),
  );
  const evidence = (process.methodContent?.workProductDefinitions || []).filter((product) =>
    (gate.requiredEvidenceRefs || []).includes(product.id),
  );
  return `<div class="process-role-detail-header"><div><span>Decision gate</span><h4>${escapeHtml(gate.gateId || "Gate")} · ${escapeHtml(gate.name)}</h4></div><div class="process-role-metrics"><span><strong>${roles.length}</strong> authorities</span><span><strong>${evidence.length}</strong> evidence</span></div></div>
    <div class="process-role-accountability"><span aria-hidden="true">✓</span><div><strong>Acceptance condition</strong><p>${escapeHtml(gate.acceptanceCondition || "No acceptance condition specified")}</p></div></div>
    <div class="process-explorer-columns"><div><strong>Lifecycle position</strong><div>${renderExplorerChips(phase ? [phase] : [], (item) => item.name)}</div></div><div><strong>Decision authorities</strong><div>${renderExplorerChips(roles, (item) => item.name)}</div></div></div>
    <div class="process-role-task-list"><strong>Required evidence</strong>${evidence.length ? `<ol>${evidence.map((item) => `<li><span>${escapeHtml(item.name)}</span><small>Required</small></li>`).join("")}</ol>` : `<span class="process-role-empty">No evidence specified</span>`}</div>`;
}

function renderOutputExplorerDetail(product, process) {
  const tasks = process.methodContent?.taskDefinitions || [];
  const producers = tasks.filter((task) => (task.outputWorkProductRefs || []).includes(product.id));
  const consumers = tasks.filter((task) => (task.inputWorkProductRefs || []).includes(product.id));
  const relatedTasks = [
    ...new Map([...producers, ...consumers].map((task) => [task.id, task])).values(),
  ];
  const phases = phasesForTaskDefinitions(process, relatedTasks);
  const roleIds = new Set(relatedTasks.flatMap((task) => task.performerRoleRefs || []));
  const roles = (process.methodContent?.roleDefinitions || []).filter((role) =>
    roleIds.has(role.id),
  );
  return `<div class="process-role-detail-header"><div><span>Work product</span><h4>${escapeHtml(product.name)}</h4></div><div class="process-role-metrics"><span><strong>${producers.length}</strong> producers</span><span><strong>${consumers.length}</strong> consumers</span></div></div>
    <div class="process-role-accountability"><span aria-hidden="true">↗</span><div><strong>Purpose</strong><p>${escapeHtml(product.description || "No description provided")}</p></div></div>
    <div class="process-explorer-columns"><div><strong>Lifecycle phases</strong><div>${renderExplorerChips(phases, (item) => item.name)}</div></div><div><strong>Responsible roles</strong><div>${renderExplorerChips(roles, (item) => item.name)}</div></div></div>
    <div class="process-output-flow"><div><strong>Created by</strong>${renderExplorerChips(producers, (item) => item.name)}</div><span aria-hidden="true">→</span><div><strong>Used by</strong>${renderExplorerChips(consumers, (item) => item.name)}</div></div>`;
}

function renderGuidanceExplorerDetail(item, process) {
  const appliesTo = item.appliesTo || "process";
  const phase = (process.phases || []).find((candidate) => candidate.id === appliesTo);
  const stage = (process.phases || [])
    .flatMap((candidate) => candidate.stages || [])
    .flatMap(leafStages)
    .find((candidate) => candidate.id === appliesTo);
  const task = (process.methodContent?.taskDefinitions || []).find(
    (candidate) => candidate.id === appliesTo,
  );
  const scopeName =
    phase?.name ||
    stage?.name ||
    task?.name ||
    (appliesTo === "process" ? "Full process" : appliesTo);
  return `<div class="process-role-detail-header"><div><span>Guidance item</span><h4>${escapeHtml(item.name)}</h4></div><div class="process-guidance-scope">${escapeHtml(scopeName)}</div></div>
    <div class="process-role-accountability"><span aria-hidden="true">i</span><div><strong>Recommendation</strong><p>${escapeHtml(item.text || "No guidance text provided")}</p></div></div>
    <div class="process-guidance-route"><span>Applies to</span><strong>${escapeHtml(scopeName)}</strong><small>${escapeHtml(appliesTo)}</small></div>`;
}

function renderRoleExplorerDetail(role, process) {
  const allTasks = process.methodContent?.taskDefinitions || [];
  const allProducts = process.methodContent?.workProductDefinitions || [];
  const tasks = allTasks.filter((task) => (task.performerRoleRefs || []).includes(role.id));
  const gates = (process.milestones || []).filter((gate) =>
    (gate.authorityRoleRefs || []).includes(role.id),
  );
  const phases = phasesForTaskDefinitions(process, tasks);
  const productIds = new Set(
    tasks.flatMap((task) => [
      ...(task.inputWorkProductRefs || []),
      ...(task.outputWorkProductRefs || []),
    ]),
  );
  const products = allProducts.filter((product) => productIds.has(product.id));
  const empty = `<span class="process-role-empty">No direct assignments</span>`;
  const chips = renderExplorerChips;

  return `<div class="process-role-detail-header">
    <div><span>Selected role</span><h4>${escapeHtml(role.name)}</h4></div>
    <div class="process-role-metrics"><span><strong>${tasks.length}</strong> tasks</span><span><strong>${gates.length}</strong> gates</span><span><strong>${products.length}</strong> products</span></div>
  </div>
  <div class="process-role-accountability"><span aria-hidden="true">✓</span><div><strong>Key accountability</strong>${(role.responsibilities || []).map((item) => `<p>${escapeHtml(item)}</p>`).join("")}</div></div>
  <div class="process-role-flow">
    <div><strong>Acts in</strong><div>${chips(phases, (phase) => phase.name)}</div></div>
    <span aria-hidden="true">→</span>
    <div><strong>Decides at</strong><div>${chips(gates, (gate) => gate.gateId || gate.name)}</div></div>
    <span aria-hidden="true">→</span>
    <div><strong>Shapes</strong><div>${chips(products, (product) => product.name)}</div></div>
  </div>
  <div class="process-role-task-list"><strong>Assigned work</strong>${tasks.length ? `<ol>${tasks.map((task) => `<li><span>${escapeHtml(task.name)}</span>${task.primaryPerformerRoleRef === role.id ? "<small>Lead</small>" : "<small>Contributor</small>"}</li>`).join("")}</ol>` : empty}</div>`;
}

function renderSectionTitle(title) {
  return `<div class="map-detail-section-title">${escapeHtml(title)}</div>`;
}

function renderListSection(title, items) {
  return `${renderSectionTitle(title)}<ul>${items
    .map((item) => `<li>${escapeHtml(item)}</li>`)
    .join("")}</ul>`;
}

function renderChipSection(title, items, level = state.activeType) {
  return `${renderSectionTitle(title)}<div class="map-detail-chip-list">${items
    .map((item) => {
      const label = modelingElementDefinition(level, item)?.displayName || item;
      return `<span class="map-detail-chip">${escapeHtml(label)}</span>`;
    })
    .join("")}</div>`;
}

function renderRole(role) {
  const responsibilities = (role.responsibilities || []).join("; ");
  return `<div class="map-detail-section">
    ${renderSectionTitle("Role")}
    <p><strong>${escapeHtml(role.name)}</strong>${responsibilities ? `: ${escapeHtml(responsibilities)}` : ""}</p>
  </div>`;
}

function renderArtifacts(artifacts, title = "Artifacts and deliverables") {
  const items = artifacts
    .map((artifact) => {
      const desc = artifact.description ? `: ${artifact.description}` : "";
      return `<li><strong>${escapeHtml(artifact.name)}</strong>${escapeHtml(desc)}</li>`;
    })
    .join("");
  return `${renderSectionTitle(title)}<ul>${items}</ul>`;
}

function renderGuidelines(guidelines) {
  const items = guidelines
    .map((g) => `<li><strong>${escapeHtml(g.name)}</strong>: ${escapeHtml(g.text)}</li>`)
    .join("");
  return `${renderSectionTitle("Guidelines")}<ul>${items}</ul>`;
}

function guidelinesForNode(process, phase, stage, task) {
  const applies = new Set(["process", phase?.id, stage?.id, task?.id].filter(Boolean));
  return guidanceForProcess(process).filter((g) => applies.has(g.appliesTo));
}

function artifactsForNode(process, phase, stage, task) {
  const artifacts = new Map();
  const addTask = (item) =>
    (item?.artifacts || []).forEach((artifact) => artifacts.set(artifact.id, artifact));
  if (task) {
    addTask(task);
  } else if (stage) {
    leafStages(stage).forEach((leaf) => tasksForStage(process, leaf).forEach(addTask));
  } else if (phase) {
    (phase.stages || [])
      .flatMap(leafStages)
      .forEach((leaf) => tasksForStage(process, leaf).forEach(addTask));
  }
  return [...artifacts.values()];
}

function paletteFocusForNode(process, phase, stage, task) {
  if (task) return task.paletteFocus || [];
  const stages = stage ? [stage] : phase?.stages || [];
  return [
    ...new Set(
      stages
        .flatMap(leafStages)
        .flatMap((leaf) => tasksForStage(process, leaf).flatMap((item) => item.paletteFocus || [])),
    ),
  ];
}

function findPhaseForTask(process, taskId) {
  for (const phase of process?.phases || []) {
    for (const st of phase.stages || []) {
      for (const leaf of leafStages(st)) {
        if (tasksForStage(process, leaf).some((t) => t.id === taskId)) return phase;
      }
    }
  }
  return null;
}

function findStageForTask(process, phase, taskId) {
  for (const st of phase?.stages || []) {
    for (const leaf of leafStages(st)) {
      if (tasksForStage(process, leaf).some((t) => t.id === taskId)) return leaf;
    }
  }
  return null;
}

function renderLegend(host) {
  host.innerHTML = `
    <span class="methodology-map-legend-title">Legend</span>
    <span class="methodology-map-legend-item"><span class="methodology-map-legend-line"></span> Sequential flow</span>
    <span class="methodology-map-legend-item"><span class="methodology-map-legend-line is-loop"></span> Iterative cycle</span>
    <span class="methodology-map-legend-item"><span class="methodology-map-legend-line is-rework"></span> Rework / feedback</span>
    <span class="methodology-map-legend-item">Click a phase to explore; CIM, PIM, and PSM child-process stages open their DSML modeling processes · drag to pan · wheel or controls to zoom · Back / Forward to revisit views</span>`;
}

function handleNodeClick(node) {
  const childLevel = mapProcessKind === "full" ? childProcessLevel(node.stage) : null;
  if (childLevel) {
    const stage = node.stage;
    const phase = node.phase || findPhaseForStage(processForMap(), stage.id);
    const stack = getMapStack();
    state.guidedModeling.libraryBrowser = null;
    state.guidedModeling.mapSelectedId = stage.id;
    const top = stack.at(-1);
    if (top?.type !== "stage" || top.stageId !== stage.id) {
      stack.push({ type: "stage", phaseId: phase?.id, stageId: stage.id });
    }
    recordMapNavigation();
    switchMapProcess(childLevel);
    return;
  }

  state.guidedModeling.mapSelectedId = node.id;
  state.guidedModeling.libraryBrowser = null;

  if (node.drillable && node.drill) {
    const stack = getMapStack();
    const top = stack.at(-1);
    const alreadyAtNode =
      (node.drill.type === "phase" && top.type === "phase" && top.phaseId === node.drill.phaseId) ||
      (node.drill.type === "stage" && top.type === "stage" && top.stageId === node.drill.stageId);
    if (!alreadyAtNode) {
      stack.push(node.drill);
    }
  }

  recordMapNavigation();
  renderProcessMap();
}

function findPhaseForStage(process, stageId) {
  return (process?.phases || []).find((phase) => findStageById(phase.stages, stageId)) || null;
}

function renderProcessMap() {
  const canvasHost = el.methodologyMapCanvas;
  const detailHost = el.methodologyMapDetail;
  const legendHost = el.methodologyMapLegend;
  const titleHost = el.methodologyMapTitle;
  const subtitleHost = el.methodologyMapSubtitle;
  if (!canvasHost) return;

  const process = processForMap();
  const stack = getMapStack();

  const displayedLevel = mapProcessKind || state.activeType;
  const hasSupportedLevel = isModelingLevel(displayedLevel) || displayedLevel === "artifact";
  if (!process || (mapProcessKind !== "full" && !hasSupportedLevel)) {
    canvasHost.innerHTML = `<div class="methodology-map-detail-empty">${mapProcessKind === "full" ? "The Full Process definition is still loading." : "Open a CIM, PIM, PSM, or generated artifacts to view its process."}</div>`;
    return;
  }

  renderBreadcrumb(process, stack);
  if (el.methodologyMapBackBtn) {
    el.methodologyMapBackBtn.disabled = mapHistoryIndex <= 0;
  }
  if (el.methodologyMapForwardBtn) {
    el.methodologyMapForwardBtn.disabled = mapHistoryIndex >= mapHistory.length - 1;
  }

  const layout = buildViewLayout(process, stack);
  if (titleHost) titleHost.textContent = layout.title || "Process";
  if (subtitleHost) {
    subtitleHost.textContent =
      layout.subtitle ||
      (mapProcessKind === "full"
        ? "Full product lifecycle with repeatable release and increment cycles"
        : process.processEngine?.description || "SPEM Activities → TaskUses");
  }

  const selectedId = state.guidedModeling.mapSelectedId;
  canvasHost.innerHTML = "";

  const scaler = document.createElement("div");
  scaler.className = "methodology-map-scaler";
  scaler.style.transform = `translate(${mapPan.x}px, ${mapPan.y}px) scale(${mapZoom})`;
  scaler.style.transformOrigin = "top left";
  activeMapScaler = scaler;

  const { svg, bounds } = renderSvg(layout, selectedId, handleNodeClick);
  scaler.style.width = `${bounds.width}px`;
  scaler.style.minHeight = `${bounds.height}px`;
  scaler.appendChild(svg);
  canvasHost.appendChild(scaler);

  const selectedNode =
    layout.nodes.find((n) => n.id === selectedId) ||
    mapContextDetailNode(process, stack, selectedId);
  renderDetail(detailHost, selectedNode, process, mapProcessKind || state.activeType);
  if (legendHost) renderLegend(legendHost);
}

function applyMapTransform() {
  if (activeMapScaler) {
    activeMapScaler.style.transform = `translate(${mapPan.x}px, ${mapPan.y}px) scale(${mapZoom})`;
  }
}

function zoomMap(nextZoom, origin = null) {
  const boundedZoom = Math.max(0.55, Math.min(1.8, nextZoom));
  if (origin) {
    const factor = boundedZoom / mapZoom;
    mapPan = {
      x: origin.x - (origin.x - mapPan.x) * factor,
      y: origin.y - (origin.y - mapPan.y) * factor,
    };
  }
  mapZoom = boundedZoom;
  applyMapTransform();
}

function beginMapPan(event) {
  if (event.button !== 0 || event.target.closest?.(".methodology-map-node, button")) return;
  const host = el.methodologyMapCanvas;
  if (!host) return;
  event.preventDefault();
  host.classList.add("is-panning");
  host.setPointerCapture(event.pointerId);
  const startX = event.clientX;
  const startY = event.clientY;
  const initialPan = { ...mapPan };
  const move = (moveEvent) => {
    mapPan = {
      x: initialPan.x + moveEvent.clientX - startX,
      y: initialPan.y + moveEvent.clientY - startY,
    };
    applyMapTransform();
  };
  const finish = () => {
    host.classList.remove("is-panning");
    host.removeEventListener("pointermove", move);
    host.removeEventListener("pointerup", finish);
    host.removeEventListener("pointercancel", finish);
  };
  host.addEventListener("pointermove", move);
  host.addEventListener("pointerup", finish);
  host.addEventListener("pointercancel", finish);
}

function handleMapWheel(event) {
  if (!state.guidedModeling?.mapOpen) return;
  event.preventDefault();
  const bounds = el.methodologyMapCanvas.getBoundingClientRect();
  zoomMap(mapZoom * (event.deltaY < 0 ? 1.08 : 0.92), {
    x: event.clientX - bounds.left,
    y: event.clientY - bounds.top,
  });
}

function mapContextDetailNode(process, stack, selectedId) {
  const ctx = stack[stack.length - 1];
  if (ctx?.type === "stage") {
    const phase = process.phases?.find((p) => p.id === ctx.phaseId);
    const stage = findStageById(phase?.stages, ctx.stageId);
    if (stage && (!selectedId || selectedId === stage.id)) {
      return { id: stage.id, phase, stage };
    }
  }
  if (ctx?.type === "phase") {
    const phase = process.phases?.find((p) => p.id === ctx.phaseId);
    if (phase && (!selectedId || selectedId === phase.id)) {
      return { id: phase.id, phase };
    }
  }
  return null;
}

export function openMethodologyMap() {
  // Artifact generation is the final methodology level.  It does not have a
  // canvas model, but it does have a process definition and must be able to
  // open the same map from the artifact explorer.
  if (!isModelingLevel(state.activeType) && state.activeType !== "artifact") return;
  mapProcessKind = state.activeType;
  state.guidedModeling.libraryBrowser = null;
  state.guidedModeling.mapOpen = true;
  resetMapStack();
  state.guidedModeling.mapSelectedId = null;
  mapZoom = 1;
  mapPan = { x: 0, y: 0 };
  resetMapHistory();
  el.workspace?.classList.add("methodology-map-open");
  el.methodologyMapOverlay?.classList.remove("hidden");
  el.methodologyMapOverlay?.setAttribute("aria-hidden", "false");
  renderProcessMap();
}

/** Open the map at a phase or stage selected from the compact methodology navigator. */
export function openMethodologyMapAt({ phaseId = null, stageId = null } = {}) {
  if (!isModelingLevel(state.activeType) && state.activeType !== "artifact") return;

  mapProcessKind = state.activeType;
  state.guidedModeling.libraryBrowser = null;
  state.guidedModeling.mapOpen = true;
  resetMapStack();
  state.guidedModeling.mapSelectedId = null;
  resetMapHistory();
  if (phaseId) {
    pushMapContext({ type: "phase", phaseId });
    state.guidedModeling.mapSelectedId = phaseId;
    recordMapNavigation();
  }
  if (phaseId && stageId) {
    pushMapContext({ type: "stage", phaseId, stageId });
    state.guidedModeling.mapSelectedId = stageId;
    recordMapNavigation();
  }
  mapZoom = 1;
  mapPan = { x: 0, y: 0 };
  el.workspace?.classList.add("methodology-map-open");
  el.methodologyMapOverlay?.classList.remove("hidden");
  el.methodologyMapOverlay?.setAttribute("aria-hidden", "false");
  renderProcessMap();
}

export function openFullMethodologyMap() {
  state.guidedModeling ??= {};
  mapProcessKind = "full";
  state.guidedModeling.libraryBrowser = null;
  state.guidedModeling.mapOpen = true;
  resetMapStack();
  state.guidedModeling.mapSelectedId = null;
  mapZoom = 1;
  mapPan = { x: 0, y: 0 };
  resetMapHistory();
  el.workspace?.classList.add("methodology-map-open");
  el.methodologyMapOverlay?.classList.remove("hidden");
  el.methodologyMapOverlay?.setAttribute("aria-hidden", "false");
  renderProcessMap();
}

export function closeMethodologyMap() {
  state.guidedModeling.mapOpen = false;
  el.workspace?.classList.remove("methodology-map-open");
  el.methodologyMapOverlay?.classList.add("hidden");
  el.methodologyMapOverlay?.setAttribute("aria-hidden", "true");
}

export function toggleMethodologyMap() {
  if (state.guidedModeling?.mapOpen) closeMethodologyMap();
  else openMethodologyMap();
}

export function initMethodologyProcessMap() {
  state.guidedModeling ??= {};
  state.guidedModeling.mapOpen = false;
  state.guidedModeling.mapSelectedId = null;
  resetMapStack();
  mapZoom = 1;
  mapPan = { x: 0, y: 0 };
  mapProcessKind = state.activeType;
  resetMapHistory();

  el.methodologyMapBackBtn?.addEventListener("click", goBackInMap);
  el.methodologyMapForwardBtn?.addEventListener("click", goForwardInMap);
  el.methodologyMapCloseBtn?.addEventListener("click", closeMethodologyMap);
  el.methodologyMapZoomInBtn?.addEventListener("click", () => {
    zoomMap(mapZoom + 0.1);
  });
  el.methodologyMapZoomOutBtn?.addEventListener("click", () => {
    zoomMap(mapZoom - 0.1);
  });
  el.methodologyMapZoomResetBtn?.addEventListener("click", () => {
    mapZoom = 1;
    mapPan = { x: 0, y: 0 };
    applyMapTransform();
  });

  el.methodologyMapCanvas?.addEventListener("pointerdown", beginMapPan);
  el.methodologyMapCanvas?.addEventListener("wheel", handleMapWheel, { passive: false });

  document.addEventListener("keydown", (e) => {
    if (!state.guidedModeling?.mapOpen) return;
    if (e.key === "Escape") {
      const stack = getMapStack();
      if (stack.length > 1) {
        goBackInMap();
      } else {
        closeMethodologyMap();
      }
    }
  });
}

export function refreshMethodologyProcessMap() {
  if (state.guidedModeling?.mapOpen) renderProcessMap();
}

export function createMethodologyMapOpenButton() {
  const btn = document.createElement("button");
  btn.type = "button";
  btn.className = "methodology-map-open-btn";
  const title = processDisplayTitle(state.guidedModeling?.definitions?.[state.activeType]);
  btn.title = `Explore ${title}`;
  btn.innerHTML = `<span class="icon-svg icon-mask" aria-hidden="true"></span> ${title}`;
  btn.addEventListener("click", openMethodologyMap);
  return btn;
}

export function createFullMethodologyOpenButton() {
  const btn = document.createElement("button");
  btn.type = "button";
  btn.className = "methodology-map-open-btn methodology-map-open-btn-secondary";
  btn.title = "Explore the full software development process";
  btn.innerHTML = `<span class="icon-svg icon-mask" aria-hidden="true"></span> Full Process`;
  btn.addEventListener("click", openFullMethodologyMap);
  return btn;
}
