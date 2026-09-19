/**
 * SPEM process map with semantic zoom: process → phase → stage → sub-stage → task.
 */
import { state } from "./state.js";
import { el } from "./dom.js";
import { escapeHtml } from "./utils.js";
import { isModelingLevel, modelingElementDefinition } from "./modeling-config-data.js";
import { guidanceForProcess, roleForTask, tasksForStage } from "./methodology-process-utils.js";

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

function stageTaskIds(process, stage) {
  return leafStages(stage)
    .flatMap((s) => tasksForStage(process, s))
    .map((t) => t.id)
    .filter(Boolean);
}

function taskStatus(taskId, progress) {
  if (!taskId) return "pending";
  return progress.completedTaskIds?.includes(taskId) ? "complete" : "pending";
}

function stageStatus(process, stage, progress) {
  const ids = stageTaskIds(process, stage);
  if (!ids.length) return "pending";
  if (ids.every((id) => progress.completedTaskIds.includes(id))) return "complete";
  if (ids.some((id) => progress.completedTaskIds.includes(id))) return "progress";
  return "pending";
}

function phaseStatus(process, phase, progress) {
  const ids = (phase?.stages || [])
    .flatMap((st) => leafStages(st))
    .flatMap((s) => tasksForStage(process, s))
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

function goBackInMap() {
  popMapContext();
  state.guidedModeling.mapSelectedId = null;
  renderProcessMap();
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
  const enginePhaseIds = new Set(
    (process.processEngine?.cycle || []).flatMap((step) => step.phaseIds || []),
  );
  const items = phases.map((phase) => ({
    id: phase.id,
    label: phase.name,
    sublabel: enginePhaseIds.has(phase.id) ? "Engine phase" : "Click to open stages",
    icon: iconForName(phase.name, phase.id),
    kind: "phase",
    status: phaseStatus(process, phase, progress),
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

  layout.title = `${process.displayName || process.level?.toUpperCase()}: Phases`;
  return layout;
}

function phaseIdForEngineStep(cycle, stepId) {
  return cycle.find((step) => step.id === stepId)?.phaseIds?.[0] || null;
}

function buildPhaseView(process, progress, phaseId) {
  const phase = process.phases?.find((p) => p.id === phaseId);
  if (!phase) return buildProcessView(process, progress);

  const stages = phase.stages || [];
  const items = stages.map((stage) => {
    const hasSub = stage.subStages?.length > 0;
    const tasks = tasksForStage(process, stage);
    const hasTasks = tasks.length > 0;
    const drillable = hasSub || hasTasks;
    return {
      id: stage.id,
      label: stage.name,
      sublabel: hasSub
        ? `${stage.subStages.length} sub-stage(s). Click to open`
        : hasTasks
          ? `${tasks.length} task(s). Click to open`
          : stage.objective?.slice(0, 40),
      icon: iconForName(stage.name, stage.id),
      kind: "stage",
      status: stageStatus(process, stage, progress),
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
      sublabel: tasksForStage(process, sub).length
        ? `${tasksForStage(process, sub).length} task(s)`
        : sub.objective?.slice(0, 36),
      icon: iconForName(sub.name, sub.id),
      kind: "substage",
      status: stageStatus(process, sub, progress),
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

function renderDetail(host, node, process, level) {
  if (!node) {
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
  const artifacts = artifactsForNode(process, phase, stage, task);
  const guidelines = guidelinesForNode(process, phase, stage, task);
  const paletteFocus = paletteFocusForNode(process, phase, stage, task);

  if (node.task) {
    parts.push(`<span class="map-detail-kind">TaskUse</span>`);
    parts.push(`<h3>${escapeHtml(task.name)}</h3>`);
    parts.push(`<p>${escapeHtml(stage?.objective || phase?.objective || "")}</p>`);
    if (role) parts.push(renderRole(role));
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
      parts.push(renderChipSection("Validation", task.validationRules));
    const bindings = (task.metamodelBindings || []).map((binding) => binding.classifier);
    if (bindings.length) {
      parts.push(renderChipSection("Metamodel bindings", bindings.slice(0, 18)));
    }
    if (paletteFocus.length)
      parts.push(renderChipSection("Related palette elements", paletteFocus));
    if (guidelines.length) parts.push(renderGuidelines(guidelines));
  } else if (node.stage) {
    parts.push(`<span class="map-detail-kind">Activity · MODRISS::Stage</span>`);
    parts.push(`<h3>${escapeHtml(node.stage.name)}</h3>`);
    parts.push(`<p>${escapeHtml(node.stage.objective || "")}</p>`);
    if (role) parts.push(renderRole(role));
    if (artifacts.length) parts.push(renderArtifacts(artifacts));
    if (paletteFocus.length)
      parts.push(renderChipSection("Related palette elements", paletteFocus));
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
    if (role) parts.push(renderRole(role));
    if (artifacts.length) parts.push(renderArtifacts(artifacts));
    if (paletteFocus.length)
      parts.push(renderChipSection("Related palette elements", paletteFocus));
    if (guidelines.length) parts.push(renderGuidelines(guidelines));
    if (phase.entryCriteria?.length) parts.push(renderListSection("Entry", phase.entryCriteria));
    if (phase.exitCriteria?.length) parts.push(renderListSection("Exit", phase.exitCriteria));
  }

  const canGuide = node.phase || node.stage || node.task;
  if (canGuide) {
    const taskActions = node.task
      ? `<button type="button" class="btn btn-primary btn-full" data-action="toggle-task">${
          state.guidedModeling?.progress?.completedTaskIds?.includes(task.id)
            ? "Mark incomplete"
            : "Mark task complete"
        }</button>
         <div class="methodology-map-detail-action-row">
           <button type="button" class="btn btn-secondary btn-sm" data-action="palette">Palette elements</button>
           <button type="button" class="btn btn-secondary btn-sm" data-action="ask-ai">Ask AI</button>
         </div>`
      : node.stage && paletteFocus.length
        ? `<button type="button" class="btn btn-secondary btn-full" data-action="palette">Show related palette elements</button>`
        : "";
    parts.push(`<div class="methodology-map-detail-actions">
      ${taskActions}
      <button type="button" class="methodology-map-detail-link" data-action="goto-guide">Show in navigator</button>
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
  host.querySelector('[data-action="toggle-task"]')?.addEventListener("click", async () => {
    const { toggleGuidedTaskComplete } = await import("./guided-modeling.js");
    toggleGuidedTaskComplete(task.id);
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

function renderSectionTitle(title) {
  return `<div class="map-detail-section-title">${escapeHtml(title)}</div>`;
}

function renderListSection(title, items) {
  return `${renderSectionTitle(title)}<ul>${items
    .map((item) => `<li>${escapeHtml(item)}</li>`)
    .join("")}</ul>`;
}

function renderChipSection(title, items) {
  return `${renderSectionTitle(title)}<div class="map-detail-chip-list">${items
    .map((item) => {
      const label = modelingElementDefinition(state.activeType, item)?.displayName || item;
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

function renderArtifacts(artifacts) {
  const items = artifacts
    .map((artifact) => {
      const desc = artifact.description ? `: ${artifact.description}` : "";
      return `<li><strong>${escapeHtml(artifact.name)}</strong>${escapeHtml(desc)}</li>`;
    })
    .join("");
  return `${renderSectionTitle("Artifacts and deliverables")}<ul>${items}</ul>`;
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

function renderLegend(host, stack) {
  const inTask = stack.some((c) => c.type === "stage");
  host.innerHTML = `
    <span class="methodology-map-legend-title">Legend</span>
    <span class="methodology-map-legend-item"><span class="methodology-map-legend-swatch is-current"></span> In progress</span>
    <span class="methodology-map-legend-item"><span class="methodology-map-legend-swatch is-complete"></span> Complete</span>
    <span class="methodology-map-legend-item"><span class="methodology-map-legend-line"></span> Sequential flow</span>
    <span class="methodology-map-legend-item"><span class="methodology-map-legend-line is-loop"></span> Iterative cycle</span>
    ${inTask ? '<span class="methodology-map-legend-item">Use Back to zoom out</span>' : '<span class="methodology-map-legend-item">Click node to zoom in</span>'}`;
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

  if (!process || (!isModelingLevel(state.activeType) && state.activeType !== "artifact")) {
    canvasHost.innerHTML = `<div class="methodology-map-detail-empty">Open a CIM, PIM, PSM, or generated artifacts to view the process map.</div>`;
    return;
  }

  renderBreadcrumb(process, stack);
  if (el.methodologyMapBackBtn) {
    el.methodologyMapBackBtn.disabled = stack.length <= 1;
  }

  const layout = buildViewLayout(process, progress, stack);
  if (titleHost) titleHost.textContent = layout.title || "Process map";
  if (subtitleHost) {
    subtitleHost.textContent =
      layout.subtitle || process.processEngine?.description || "SPEM Activities → TaskUses";
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

  const selectedNode =
    layout.nodes.find((n) => n.id === selectedId) ||
    mapContextDetailNode(process, stack, selectedId);
  renderDetail(detailHost, selectedNode, process, state.activeType);
  if (legendHost) renderLegend(legendHost, stack);
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
  state.guidedModeling.mapOpen = true;
  resetMapStack();
  state.guidedModeling.mapSelectedId = state.guidedModeling.progress?.selectedPhaseId || null;
  el.workspace?.classList.add("methodology-map-open");
  el.methodologyMapOverlay?.classList.remove("hidden");
  el.methodologyMapOverlay?.setAttribute("aria-hidden", "false");
  renderProcessMap();
}

/** Open the map at a phase or stage selected from the compact methodology navigator. */
export function openMethodologyMapAt({ phaseId = null, stageId = null } = {}) {
  if (!isModelingLevel(state.activeType) && state.activeType !== "artifact") return;

  state.guidedModeling.mapOpen = true;
  resetMapStack();
  if (phaseId) {
    pushMapContext({ type: "phase", phaseId });
  }
  if (phaseId && stageId) {
    pushMapContext({ type: "stage", phaseId, stageId });
  }
  state.guidedModeling.mapSelectedId = stageId || phaseId || null;
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

  el.methodologyMapBackBtn?.addEventListener("click", goBackInMap);
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
