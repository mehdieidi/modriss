/**
 * SPEM process map with semantic zoom: process → phase → stage → sub-stage → task.
 */
import { state } from "./state.js";
import { el } from "./dom.js";
import { escapeHtml } from "./utils.js";
import { phaseNarrative } from "./methodology-narratives.mjs";
import { isModelingLevel } from "./modeling-config-data.js";

const ICON_BASE = "/assets/icons/process-map";
const NODE_W = 148;
const NODE_H = 88;
const NODE_GAP = 36;
const PAD = 56;
const ICON_SIZE = 26;
const TASK_NODE_H = 76;
const TASK_NODE_W = 140;

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

function iconForName(name, id = "") {
  const text = `${name} ${id}`.toLowerCase();
  for (const [key, file] of Object.entries(PHASE_ICONS)) {
    if (text.includes(key)) return `${ICON_BASE}/${file}`;
  }
  return `${ICON_BASE}/milestone.svg`;
}

function iconForStep(step) {
  const name = (step?.name || "").toLowerCase();
  const id = step?.id || "";
  if (id.includes("plan") || name.includes("plan")) return `${ICON_BASE}/plan.svg`;
  if (step?.type === "deliverable" || name.includes("deliver")) return `${ICON_BASE}/deliver.svg`;
  if (name.includes("retrospect")) return `${ICON_BASE}/retrospect.svg`;
  return `${ICON_BASE}/engine.svg`;
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

function stageTaskIds(stage) {
  return leafStages(stage)
    .flatMap((s) => s.tasks || [])
    .map((t) => t.id)
    .filter(Boolean);
}

function taskStatus(taskId, progress) {
  if (!taskId) return "pending";
  return progress.completedTaskIds?.includes(taskId) ? "complete" : "pending";
}

function stageStatus(stage, progress) {
  const ids = stageTaskIds(stage);
  if (!ids.length) return "pending";
  if (ids.every((id) => progress.completedTaskIds.includes(id))) return "complete";
  if (ids.some((id) => progress.completedTaskIds.includes(id))) return "progress";
  return "pending";
}

function phaseStatus(phase, progress) {
  const ids = (phase?.stages || [])
    .flatMap((st) => leafStages(st))
    .flatMap((s) => s.tasks || [])
    .map((t) => t.id);
  if (!ids.length) return "pending";
  if (ids.every((id) => progress.completedTaskIds.includes(id))) return "complete";
  if (progress.selectedPhaseId === phase.id) return "current";
  if (ids.some((id) => progress.completedTaskIds.includes(id))) return "progress";
  return "pending";
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

function pushMapContext(ctx) {
  getMapStack().push(ctx);
}

function popMapContext() {
  const stack = getMapStack();
  if (stack.length > 1) stack.pop();
}

function navigateToStackIndex(index) {
  const stack = getMapStack();
  state.guidedModeling.mapStack = stack.slice(0, index + 1);
}

/** @returns {{ nodes: object[], edges: object[], width: number, height: number, title: string }} */
function buildViewLayout(process, progress, stack) {
  const ctx = stack[stack.length - 1];
  if (ctx.type === "process") return buildProcessView(process, progress);
  if (ctx.type === "phase") return buildPhaseView(process, progress, ctx.phaseId);
  if (ctx.type === "stage") return buildStageView(process, progress, ctx.phaseId, ctx.stageId);
  return buildProcessView(process, progress);
}

function buildProcessView(process, progress) {
  const phases = process.phases || [];
  const items = phases.map((phase) => ({
    id: phase.id,
    label: phase.name,
    sublabel: phase.inEngine === false ? "Once per program" : "Click to open stages",
    icon: iconForName(phase.name, phase.id),
    kind: phase.inEngine === false ? "onboarding" : "phase",
    status: phaseStatus(phase, progress),
    drillable: true,
    drill: { type: "phase", phaseId: phase.id },
    phase,
  }));

  const layout = layoutHorizontalRow(items, { rowY: 120 });

  const engine = process.processEngine;
  if (engine?.cycle?.length) {
    const metaSteps = engine.cycle.filter((s) => s.type !== "phase");
    const phaseNodes = layout.nodes.filter((n) => n.kind !== "engine");
    const phaseSpan =
      phaseNodes.length > 0 ? phaseNodes[phaseNodes.length - 1].x + NODE_W - phaseNodes[0].x : 0;
    const metaW = NODE_W - 12 + 28;
    const engineTotalW = metaSteps.length * metaW - 28;
    const engineStartX =
      PAD + Math.max(0, phaseSpan / 2 - engineTotalW / 2 + phaseNodes[0]?.x - PAD || 0);
    const engineY = layout.height - 88;

    const metaNodes = metaSteps.map((step, i) => ({
      id: step.id,
      label: step.name,
      sublabel: step.type,
      icon: iconForStep(step),
      kind: "engine",
      status: "meta",
      drillable: false,
      step,
      x: engineStartX + i * metaW,
      y: engineY,
      width: NODE_W - 12,
      height: NODE_H - 16,
    }));

    layout.nodes.push(...metaNodes);
    for (let i = 1; i < metaNodes.length; i++) {
      layout.edges.push({ from: metaNodes[i - 1].id, to: metaNodes[i].id, kind: "flow" });
    }
    const loop = engine.loop;
    if (loop?.fromStepId && loop.toStepId) {
      layout.edges.push({
        from: loop.fromStepId,
        to: loop.toStepId,
        kind: "loop",
        label: "Engine cycle",
      });
    }
    layout.height = engineY + NODE_H + PAD + 24;
  }

  layout.title = `${process.displayName || process.level?.toUpperCase()} — Phases`;
  return layout;
}

function buildPhaseView(process, progress, phaseId) {
  const phase = process.phases?.find((p) => p.id === phaseId);
  if (!phase) return buildProcessView(process, progress);

  const stages = phase.stages || [];
  const items = stages.map((stage) => {
    const hasSub = stage.subStages?.length > 0;
    const hasTasks = stage.tasks?.length > 0;
    const drillable = hasSub || hasTasks;
    return {
      id: stage.id,
      label: stage.name,
      sublabel: hasSub
        ? `${stage.subStages.length} sub-stage(s) — click to open`
        : hasTasks
          ? `${stage.tasks.length} task(s) — click to open`
          : stage.objective?.slice(0, 40),
      icon: iconForName(stage.name, stage.id),
      kind: "stage",
      status: stageStatus(stage, progress),
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
  layout.title = phase.name;
  layout.subtitle = phase.objective;
  return layout;
}

function buildStageView(process, progress, phaseId, stageId) {
  const phase = process.phases?.find((p) => p.id === phaseId);
  const stage = findStageById(phase?.stages, stageId);
  if (!stage) return buildPhaseView(process, progress, phaseId);

  if (stage.subStages?.length) {
    const items = stage.subStages.map((sub) => ({
      id: sub.id,
      label: sub.name,
      sublabel: sub.tasks?.length ? `${sub.tasks.length} task(s)` : sub.objective?.slice(0, 36),
      icon: iconForName(sub.name, sub.id),
      kind: "substage",
      status: stageStatus(sub, progress),
      drillable: (sub.tasks?.length || 0) > 0,
      drill: { type: "stage", phaseId, stageId: sub.id },
      phase,
      stage: sub,
    }));
    const layout = layoutHorizontalRow(items, { rowY: 130 });
    layout.title = `${stage.name} — Sub-stages`;
    layout.subtitle = stage.objective;
    return layout;
  }

  const tasks = stage.tasks || [];
  const items = tasks.map((task) => ({
    id: task.id,
    label: task.name,
    sublabel: task.durationEstimate || task.viewpoint || "",
    icon: `${ICON_BASE}/code.svg`,
    kind: "task",
    status: taskStatus(task.id, progress),
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
  layout.title = `${stage.name} — Tasks`;
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

function edgePath(edge, nodeById, layoutHeight) {
  const a = nodeById.get(edge.from);
  const b = nodeById.get(edge.to);
  if (!a || !b) return "";

  if (edge.kind === "loop") {
    const p1 = anchor(a, "bottom");
    const p2 = anchor(b, "bottom");
    const loopY = layoutHeight - 28;
    return `M ${p1.x} ${p1.y} C ${p1.x} ${loopY}, ${p2.x} ${loopY}, ${p2.x} ${p2.y}`;
  }

  const p1 = anchor(a, "right");
  const p2 = anchor(b, "left");
  if (Math.abs(p1.y - p2.y) < 4) {
    const mid = (p1.x + p2.x) / 2;
    return `M ${p1.x} ${p1.y} H ${p2.x}`;
  }
  const midX = (p1.x + p2.x) / 2;
  return `M ${p1.x} ${p1.y} H ${midX} V ${p2.y} H ${p2.x}`;
}

function computeBounds(layout) {
  let maxX = layout.width;
  let maxY = layout.height;
  for (const node of layout.nodes) {
    const { w, h } = nodeSize(node);
    maxX = Math.max(maxX, node.x + w + PAD);
    maxY = Math.max(maxY, node.y + h + PAD);
  }
  if (layout.edges.some((e) => e.kind === "loop")) {
    maxY += 48;
  }
  return { width: maxX, height: maxY };
}

function renderBreadcrumb(process, stack) {
  const host = el.methodologyMapBreadcrumb;
  if (!host) return;

  const parts = [];
  stack.forEach((ctx, i) => {
    let label = "Process";
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
  const bounds = computeBounds(layout);
  const nodeById = new Map(layout.nodes.map((n) => [n.id, n]));
  const svgNs = "http://www.w3.org/2000/svg";

  const svg = document.createElementNS(svgNs, "svg");
  svg.setAttribute("class", "methodology-map-svg");
  svg.setAttribute("viewBox", `0 0 ${bounds.width} ${bounds.height}`);
  svg.setAttribute("preserveAspectRatio", "xMidYMid meet");
  svg.setAttribute("role", "img");

  const defs = document.createElementNS(svgNs, "defs");
  defs.innerHTML = `
    <marker id="mapArrowFlow" markerWidth="10" markerHeight="10" refX="8" refY="5" orient="auto" markerUnits="strokeWidth">
      <path d="M0,0 L10,5 L0,10 z" class="methodology-map-edge-marker"/>
    </marker>
    <marker id="mapArrowLoop" markerWidth="10" markerHeight="10" refX="8" refY="5" orient="auto" markerUnits="strokeWidth">
      <path d="M0,0 L10,5 L0,10 z" class="methodology-map-edge-marker is-loop"/>
    </marker>`;
  svg.appendChild(defs);

  const edgesG = document.createElementNS(svgNs, "g");
  edgesG.setAttribute("class", "methodology-map-edges");
  for (const edge of layout.edges) {
    const d = edgePath(edge, nodeById, bounds.height);
    if (!d) continue;
    const path = document.createElementNS(svgNs, "path");
    path.setAttribute("d", d);
    path.setAttribute(
      "class",
      `methodology-map-edge${edge.kind === "loop" ? " is-loop" : ""}${edge.kind === "rework" ? " is-rework" : ""}`,
    );
    path.setAttribute(
      "marker-end",
      edge.kind === "loop" ? "url(#mapArrowLoop)" : "url(#mapArrowFlow)",
    );
    edgesG.appendChild(path);

    if (edge.kind === "loop" && edge.label) {
      const a = nodeById.get(edge.from);
      const b = nodeById.get(edge.to);
      if (a && b) {
        const text = document.createElementNS(svgNs, "text");
        text.setAttribute("class", "map-loop-label");
        text.setAttribute("x", (anchor(a, "bottom").x + anchor(b, "bottom").x) / 2);
        text.setAttribute("y", bounds.height - 10);
        text.textContent = `↻ ${edge.label}`;
        edgesG.appendChild(text);
      }
    }
  }
  svg.appendChild(edgesG);

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
        node.status ? `is-${node.status}` : "",
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

    const dot = document.createElementNS(svgNs, "circle");
    dot.setAttribute("class", "map-status-dot");
    dot.setAttribute("cx", String(12));
    dot.setAttribute("cy", "12");
    dot.setAttribute("r", "5");
    g.appendChild(dot);

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

function renderDetail(host, node, level) {
  if (!node) {
    host.innerHTML = `<div class="methodology-map-detail-empty">
      <p><strong>Semantic zoom</strong></p>
      <p>Click a <strong>phase</strong> to open its stages, then sub-stages and atomic tasks. Use the breadcrumb to navigate back.</p>
    </div>`;
    return;
  }

  const parts = [];

  if (node.task) {
    const task = node.task;
    parts.push(`<span class="map-detail-kind">Atomic task</span>`);
    parts.push(`<h3>${escapeHtml(task.name)}</h3>`);
    if (task.steps?.length) {
      parts.push("<p><strong>Steps</strong></p><ol>");
      task.steps.forEach((s) => parts.push(`<li>${escapeHtml(s)}</li>`));
      parts.push("</ol>");
    }
    if (task.artifacts?.length) {
      parts.push(
        `<p><strong>Artifacts:</strong> ${task.artifacts.map((a) => escapeHtml(a.name)).join(", ")}</p>`,
      );
    }
    if (task.paletteFocus?.length) {
      parts.push(
        `<p><strong>Palette:</strong> ${task.paletteFocus.map((t) => `<code>${escapeHtml(t)}</code>`).join(", ")}</p>`,
      );
    }
  } else if (node.stage) {
    parts.push(`<span class="map-detail-kind">Stage</span>`);
    parts.push(`<h3>${escapeHtml(node.stage.name)}</h3>`);
    parts.push(`<p>${escapeHtml(node.stage.objective || "")}</p>`);
    if (node.stage.tasks?.length) {
      parts.push("<p><strong>Tasks</strong></p><ul>");
      node.stage.tasks.forEach((t) => parts.push(`<li>${escapeHtml(t.name)}</li>`));
      parts.push("</ul>");
    }
  } else if (node.phase) {
    const phase = node.phase;
    const narrative = phaseNarrative(phase, level);
    parts.push(`<span class="map-detail-kind">Phase</span>`);
    parts.push(`<h3>${escapeHtml(phase.name)}</h3>`);
    parts.push(`<p>${escapeHtml(phase.objective || narrative.summary)}</p>`);
    if (phase.entryCriteria?.length) {
      parts.push("<p><strong>Entry</strong></p><ul>");
      phase.entryCriteria.forEach((c) => parts.push(`<li>${escapeHtml(c)}</li>`));
      parts.push("</ul>");
    }
    if (phase.exitCriteria?.length) {
      parts.push("<p><strong>Exit</strong></p><ul>");
      phase.exitCriteria.forEach((c) => parts.push(`<li>${escapeHtml(c)}</li>`));
      parts.push("</ul>");
    }
  } else if (node.step) {
    parts.push(`<span class="map-detail-kind">Engine step</span>`);
    parts.push(`<h3>${escapeHtml(node.step.name)}</h3>`);
    if (node.step.steps?.length) {
      parts.push("<ul>");
      node.step.steps.forEach((s) => parts.push(`<li>${escapeHtml(s)}</li>`));
      parts.push("</ul>");
    }
  }

  const canGuide = node.phase || node.task;
  if (canGuide) {
    parts.push(`<div class="methodology-map-detail-actions">
      <button type="button" class="methodology-btn methodology-btn-primary" data-action="goto-guide">Open in methodology guide</button>
    </div>`);
  }

  host.innerHTML = parts.join("");
  host.querySelector('[data-action="goto-guide"]')?.addEventListener("click", async () => {
    const { selectGuidedPhase, selectGuidedStage } = await import("./guided-modeling.js");
    const process = state.guidedModeling?.definitions?.[state.activeType];
    const phase = node.phase || (node.task ? findPhaseForTask(process, node.task.id) : null);
    if (phase) selectGuidedPhase(phase.id);
    const stage = node.stage || (node.task && phase ? findStageForTask(phase, node.task.id) : null);
    if (stage) selectGuidedStage(stage.id);
    closeMethodologyMap();
  });
}

function findPhaseForTask(process, taskId) {
  for (const phase of process?.phases || []) {
    for (const st of phase.stages || []) {
      for (const leaf of leafStages(st)) {
        if ((leaf.tasks || []).some((t) => t.id === taskId)) return phase;
      }
    }
  }
  return null;
}

function findStageForTask(phase, taskId) {
  for (const st of phase?.stages || []) {
    for (const leaf of leafStages(st)) {
      if ((leaf.tasks || []).some((t) => t.id === taskId)) return leaf;
    }
  }
  return null;
}

function renderLegend(host, stack) {
  const inTask = stack.some((c) => c.type === "stage");
  host.innerHTML = `
    <span class="methodology-map-legend-title">Legend</span>
    <span class="methodology-map-legend-item"><span class="methodology-map-legend-swatch is-onboarding"></span> Onboarding</span>
    <span class="methodology-map-legend-item"><span class="methodology-map-legend-swatch is-current"></span> In progress</span>
    <span class="methodology-map-legend-item"><span class="methodology-map-legend-swatch is-complete"></span> Complete</span>
    <span class="methodology-map-legend-item"><span class="methodology-map-legend-line"></span> Sequential flow</span>
    <span class="methodology-map-legend-item"><span class="methodology-map-legend-line is-loop"></span> Engine cycle</span>
    ${inTask ? '<span class="methodology-map-legend-item">› Click breadcrumb to zoom out</span>' : '<span class="methodology-map-legend-item">› Click node to zoom in</span>'}`;
}

function handleNodeClick(node) {
  state.guidedModeling.mapSelectedId = node.id;

  if (node.drillable && node.drill) {
    const stack = getMapStack();
    const top = stack[stack.length - 1];
    const samePhase =
      node.drill.type === "phase" && top.type === "phase" && top.phaseId === node.drill.phaseId;
    const sameStage =
      node.drill.type === "stage" && top.type === "stage" && top.stageId === node.drill.stageId;
    if (!samePhase && !sameStage) {
      pushMapContext(node.drill);
    }
  }

  renderProcessMap();
}

function renderProcessMap() {
  const canvasHost = el.methodologyMapCanvas;
  const detailHost = el.methodologyMapDetail;
  const legendHost = el.methodologyMapLegend;
  const titleHost = el.methodologyMapTitle;
  const subtitleHost = el.methodologyMapSubtitle;
  if (!canvasHost) return;

  const process =
    state.guidedModeling?.definitions?.[state.activeType] || state.guidedModeling?.definitions?.cim;
  const progress = state.guidedModeling?.progress || {
    completedTaskIds: [],
    selectedPhaseId: null,
  };
  const stack = getMapStack();

  if (!process || !isModelingLevel(state.activeType)) {
    canvasHost.innerHTML = `<div class="methodology-map-detail-empty">Open a CIM, PIM, or PSM model to view the process map.</div>`;
    return;
  }

  renderBreadcrumb(process, stack);

  const layout = buildViewLayout(process, progress, stack);
  if (titleHost) titleHost.textContent = layout.title || "Process map";
  if (subtitleHost) {
    subtitleHost.textContent =
      layout.subtitle || process.processEngine?.description || "SPEM phases → stages → tasks";
  }

  const selectedId = state.guidedModeling.mapSelectedId;
  canvasHost.innerHTML = "";

  const scaler = document.createElement("div");
  scaler.className = "methodology-map-scaler";
  scaler.style.transform = `scale(${mapZoom})`;
  scaler.style.transformOrigin = "top left";

  const { svg, bounds } = renderSvg(layout, selectedId, handleNodeClick);
  scaler.style.width = `${bounds.width}px`;
  scaler.style.minHeight = `${bounds.height}px`;
  scaler.appendChild(svg);
  canvasHost.appendChild(scaler);

  const selectedNode = layout.nodes.find((n) => n.id === selectedId) || null;
  renderDetail(detailHost, selectedNode, state.activeType);
  if (legendHost) renderLegend(legendHost, stack);
}

export function openMethodologyMap() {
  if (!isModelingLevel(state.activeType)) return;
  state.guidedModeling.mapOpen = true;
  resetMapStack();
  state.guidedModeling.mapSelectedId = state.guidedModeling.progress?.selectedPhaseId || null;
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

  el.methodologyMapCloseBtn?.addEventListener("click", closeMethodologyMap);
  el.methodologyMapZoomInBtn?.addEventListener("click", () => {
    mapZoom = Math.min(1.5, mapZoom + 0.1);
    renderProcessMap();
  });
  el.methodologyMapZoomOutBtn?.addEventListener("click", () => {
    mapZoom = Math.max(0.55, mapZoom - 0.1);
    renderProcessMap();
  });
  el.methodologyMapZoomResetBtn?.addEventListener("click", () => {
    mapZoom = 1;
    renderProcessMap();
  });

  document.addEventListener("keydown", (e) => {
    if (!state.guidedModeling?.mapOpen) return;
    if (e.key === "Escape") {
      const stack = getMapStack();
      if (stack.length > 1) {
        popMapContext();
        state.guidedModeling.mapSelectedId = null;
        renderProcessMap();
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
  btn.title = "Open interactive process map";
  btn.innerHTML = `<span class="icon-svg icon-mask" aria-hidden="true"></span> Process map`;
  btn.addEventListener("click", openMethodologyMap);
  return btn;
}

export function createMethodologyMapHeaderButton() {
  const btn = document.createElement("button");
  btn.type = "button";
  btn.className = "methodology-map-header-btn";
  btn.title = "Open process map";
  btn.setAttribute("aria-label", "Open process map");
  btn.innerHTML = `<span class="icon-svg icon-mask" aria-hidden="true"></span>`;
  btn.addEventListener("click", openMethodologyMap);
  return btn;
}
