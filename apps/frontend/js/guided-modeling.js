import { api } from "./api.js";
import { state } from "./state.js";
import { el } from "./dom.js";
import { escapeHtml } from "./utils.js";
import { setStatus } from "./status.js";
import {
  isModelingLevel,
  isArtifactLevel,
  modelingElementDefinition,
  modelingLevelKeys,
  modelingArtifactKey,
  modelingMethodologyConfig,
} from "./modeling-config-data.js";
import { applyDefinitionAccent, renderPalette, syncPaletteCollapsedUi } from "./canvas.js";
import { guidanceForProcess, roleForTask, tasksForStage } from "./methodology-process-utils.js";
import {
  createMethodologyMapOpenButton,
  closeMethodologyMap,
  initMethodologyProcessMap,
  openMethodologyMap,
  openMethodologyMapAt,
  refreshMethodologyProcessMap,
} from "./methodology-process-map.js";

const STORAGE_KEY = "modriss.guidedModeling.progress";
const PHASE_MENU_ID = "methodologyPhaseMenu";

let phaseMenuOpen = false;
let phaseMenuListenersBound = false;

function storageKey() {
  const projectId = state.project?.id || "default";
  const level = state.activeType || "cim";
  const modelId = state.modelId || "draft";
  return `${STORAGE_KEY}:${projectId}:${level}:${modelId}`;
}

function loadProgress() {
  try {
    const raw = window.localStorage.getItem(storageKey());
    const parsed = raw ? JSON.parse(raw) : null;
    return {
      completedTaskIds: parsed?.completedTaskIds || [],
      selectedPhaseId: parsed?.selectedPhaseId || null,
      selectedStageId: parsed?.selectedStageId || null,
      checkedCriteria: parsed?.checkedCriteria || {},
    };
  } catch {
    return {
      completedTaskIds: [],
      selectedPhaseId: null,
      selectedStageId: null,
      checkedCriteria: {},
    };
  }
}

function leafStages(stage) {
  if (stage?.subStages?.length) {
    return stage.subStages.flatMap(leafStages);
  }
  return stage ? [stage] : [];
}

function flattenAllStages(stages) {
  const out = [];
  for (const stage of stages || []) {
    out.push(stage);
    if (stage.subStages?.length) {
      out.push(...flattenAllStages(stage.subStages));
    }
  }
  return out;
}

function stageTasks(process, stage) {
  return leafStages(stage).flatMap((s) => tasksForStage(process, s));
}

function phaseTasks(process, phase) {
  return (phase?.stages || []).flatMap((st) => stageTasks(process, st));
}

function phaseTaskIds(process, phase) {
  return phaseTasks(process, phase)
    .map((t) => t.id)
    .filter(Boolean);
}

function isStageComplete(process, stage, progress) {
  const ids = stageTasks(process, stage)
    .map((t) => t.id)
    .filter(Boolean);
  return ids.length > 0 && ids.every((id) => progress.completedTaskIds.includes(id));
}

function outboundLoops(stage) {
  return (stage?.iterationLoops || []).filter((loop) => loop.direction === "outbound");
}

function isPhaseComplete(process, phase, progress) {
  const ids = phaseTaskIds(process, phase);
  return ids.length > 0 && ids.every((id) => progress.completedTaskIds.includes(id));
}

function selectedPhase(process, progress) {
  if (!process?.phases?.length) return null;
  if (progress.selectedPhaseId) {
    const found = process.phases.find((p) => p.id === progress.selectedPhaseId);
    if (found) return found;
  }
  const firstIncomplete = process.phases.find(
    (phase) => !isPhaseComplete(process, phase, progress),
  );
  return firstIncomplete || process.phases[0];
}

function selectedStage(process, phase, progress) {
  if (!phase?.stages?.length) return null;
  if (progress.selectedStageId) {
    const found = flattenAllStages(phase.stages).find((s) => s.id === progress.selectedStageId);
    if (found && tasksForStage(process, found).length) return found;
  }
  const leaves = phase.stages.flatMap(leafStages);
  const firstIncomplete = leaves.find((stage) => !isStageComplete(process, stage, progress));
  return firstIncomplete || leaves[0] || null;
}

function selectedTask(process, stage, progress) {
  const tasks = tasksForStage(process, stage);
  if (!tasks.length) return null;
  return tasks.find((t) => !progress.completedTaskIds.includes(t.id)) || tasks[0];
}

function selectedContext(process, progress) {
  const phase = selectedPhase(process, progress);
  if (!phase) return { phase: null, stage: null, task: null };
  const stage = selectedStage(process, phase, progress);
  const task = stage ? selectedTask(process, stage, progress) : null;
  return { phase, stage, task };
}

function _suggestedPhase(process, progress) {
  return process?.phases?.find((phase) => !isPhaseComplete(process, phase, progress)) || null;
}

function saveProgress(progress) {
  state.guidedModeling.progress = progress;
  window.localStorage.setItem(storageKey(), JSON.stringify(progress));
  refreshMethodologyProcessMap();
  renderGuidedModelingPanel();
}

function processForActiveLevel() {
  if (!isMethodologyLevel(state.activeType)) return null;
  return state.guidedModeling.definitions[state.activeType] || null;
}

function isMethodologyLevel(level) {
  return isModelingLevel(level) || isArtifactLevel(level);
}

function phaseProgress(process, progress) {
  const tasks = (process?.phases || []).flatMap((p) => phaseTasks(process, p));
  const ids = tasks.map((t) => t.id).filter(Boolean);
  const complete = ids.filter((id) => progress.completedTaskIds.includes(id)).length;
  return { complete, total: ids.length, ratio: ids.length ? complete / ids.length : 0 };
}

function renderProgressContract(process) {
  const contract = process?.progressModel;
  if (!contract) return null;
  const metrics = (contract.metrics || [])
    .slice(0, 4)
    .map(
      (metric) => `
    <li><strong>${escapeHtml(metric.name)}</strong><span>${escapeHtml(metric.unit)} · ${escapeHtml(metric.cadence)}</span></li>
  `,
    )
    .join("");
  const section = document.createElement("section");
  section.className = "methodology-progress-contract";
  section.innerHTML = `
    <div class="methodology-progress-contract-heading">
      <span>Progress contract</span>
      <small>${escapeHtml(contract.trackingUnit || "versioned-process-run")}</small>
    </div>
    <p>Checklist progress is local to this model. Team progress is authoritative only when the process-run record links evidence, blockers, decisions, and gate outcomes.</p>
    <ul>${metrics}</ul>`;
  return section;
}

export function guidedPaletteFocusTypes() {
  if (state.leftPaneMode !== "methodology" && !state.guidedModeling?.paletteFocusActive) {
    return new Set();
  }
  const process = processForActiveLevel();
  if (!process) return new Set();
  const progress = state.guidedModeling.progress || loadProgress();
  const { task } = selectedContext(process, progress);
  return new Set(task?.paletteFocus || []);
}

export function guidedActiveTask() {
  const process = processForActiveLevel();
  if (!process) return null;
  const progress = state.guidedModeling.progress || loadProgress();
  const { task } = selectedContext(process, progress);
  return task;
}

function findViewIdByViewpoint(viewpoint) {
  if (!viewpoint) return null;
  const target = String(viewpoint).toLowerCase();
  for (const [id, view] of state.views.byId) {
    if (String(view.viewpoint || "").toLowerCase() === target) {
      return id;
    }
  }
  return null;
}

export async function activatePhaseViewpoint(viewpoint) {
  const viewId = findViewIdByViewpoint(viewpoint);
  if (!viewId) {
    setStatus(`No canvas view found for "${viewpoint}". Save the model and try again.`, {
      error: true,
    });
    return false;
  }
  const { openWorkbenchView } = await import("./view-explorer.js");
  await openWorkbenchView(viewId);
  setStatus("Opened recommended canvas view for this phase.");
  return true;
}

export function selectGuidedPhase(phaseId) {
  const progress = loadProgress();
  progress.selectedPhaseId = phaseId;
  progress.selectedStageId = null;
  saveProgress(progress);
}

export function selectGuidedStage(stageId) {
  const progress = loadProgress();
  progress.selectedStageId = stageId;
  saveProgress(progress);
}

export function markGuidedTaskComplete(taskId) {
  const progress = loadProgress();
  if (!progress.completedTaskIds.includes(taskId)) {
    progress.completedTaskIds.push(taskId);
  }
  saveProgress(progress);
  renderPalette();
}

export function toggleGuidedTaskComplete(taskId) {
  const progress = loadProgress();
  if (progress.completedTaskIds.includes(taskId)) {
    progress.completedTaskIds = progress.completedTaskIds.filter((id) => id !== taskId);
  } else {
    progress.completedTaskIds.push(taskId);
  }
  saveProgress(progress);
  renderPalette();
}

export function resetGuidedProgress() {
  saveProgress({
    completedTaskIds: [],
    selectedPhaseId: null,
    selectedStageId: null,
    checkedCriteria: {},
  });
  renderPalette();
}

export function switchLeftPaneMode(mode) {
  if (!el.workspace) return;
  const next = mode === "methodology" ? "methodology" : "palette";
  state.leftPaneMode = next;
  const isArtifact = isArtifactLevel(state.activeType);
  if (isArtifact) {
    el.modelingPanel?.classList.toggle("hidden", next !== "methodology");
    el.artifactPanel?.classList.toggle("hidden", next === "methodology");
    if (next === "palette") {
      closeMethodologyMap();
    }
  }
  if (next === "methodology") {
    state.guidedModeling.paletteFocusActive = false;
    state.paletteCollapsed = false;
    syncPaletteCollapsedUi();
  }
  el.workspace.classList.toggle("left-pane-methodology", next === "methodology");
  el.workspace.classList.toggle("left-pane-palette", next === "palette");
  el.workspace.classList.remove("palette-hidden");
  syncMethodologyRailState();
  if (next === "methodology") {
    renderGuidedModelingPanel();
  }
  if (!isArtifact) {
    renderPalette();
  }
}

export function syncMethodologyRailState() {
  const hidden = el.workspace?.classList.contains("palette-hidden");
  const mode = state.leftPaneMode || "palette";
  el.paletteRailToggleBtn?.classList.toggle("active", !hidden && mode === "palette");
  el.methodologyRailBtn?.classList.toggle("active", !hidden && mode === "methodology");
  if (el.paletteRailToggleBtn) {
    const isArtifact = isArtifactLevel(state.activeType);
    el.paletteRailToggleBtn.title = isArtifact ? "File explorer" : "Toggle Palette";
    el.paletteRailToggleBtn.setAttribute(
      "aria-label",
      isArtifact ? "File explorer" : "Toggle Palette",
    );
  }
}

export async function loadGuidedModelingDefinitions() {
  if (state.guidedModeling.loading) return;
  state.guidedModeling.loading = true;
  try {
    const levels = [...modelingLevelKeys(), modelingArtifactKey()];
    const results = await Promise.all(
      levels.map(async (level) => {
        const def = await api(`/modeling/process/${level}`);
        return [level, def];
      }),
    );
    state.guidedModeling.definitions = Object.fromEntries(results);
    state.guidedModeling.endToEnd = await api("/modeling/process/end-to-end");
    state.guidedModeling.progress = loadProgress();
    refreshMethodologyProcessMap();
  } catch (error) {
    console.warn("Guided modeling definitions unavailable", error);
  } finally {
    state.guidedModeling.loading = false;
    if (state.leftPaneMode === "methodology") {
      renderGuidedModelingPanel();
    }
  }
}

function buildTaskPrompt(phase, stage, task) {
  const concepts = (task?.paletteFocus || []).slice(0, 12).join(", ");
  const steps = (task?.steps || []).join(" ");
  return [
    `Help me complete task "${task?.name}" in phase "${phase.name}" / stage "${stage?.name}" for ${state.activeType.toUpperCase()}.`,
    phase.objective || "",
    steps ? `Steps: ${steps}` : "",
    concepts ? `Focus elements: ${concepts}` : "",
    "Suggest concrete model elements and relationships to add on the canvas.",
  ]
    .filter(Boolean)
    .join(" ");
}

export function openAssistantForGuidedPhase() {
  const process = processForActiveLevel();
  if (!process) return;
  const progress = state.guidedModeling.progress || loadProgress();
  const { phase, stage, task } = selectedContext(process, progress);
  if (!phase || !task) return;
  const prompt = buildTaskPrompt(phase, stage, task);
  window.dispatchEvent(
    new CustomEvent("modriss:guided-task-prompt", { detail: { taskId: task?.id, prompt } }),
  );
  const chatInput = document.getElementById("chatInput");
  if (chatInput) {
    chatInput.value = prompt;
    chatInput.dispatchEvent(new Event("input", { bubbles: true }));
    chatInput.focus();
  }
  const chatToggle = document.getElementById("chatToggle");
  if (chatToggle && document.getElementById("chatWindow")?.classList.contains("hidden")) {
    chatToggle.click();
  }
}

export async function openPaletteForCurrentPhase() {
  switchLeftPaneMode("palette");
  state.guidedModeling.paletteFocusActive = true;
  renderPalette();
  setStatus("Palette highlights elements for the selected methodology phase.");
}

function renderConceptChip(type) {
  const def = modelingElementDefinition(state.activeType, type);
  const label = def?.displayName || type;
  const chip = document.createElement("button");
  chip.type = "button";
  chip.className = "methodology-concept-chip";
  chip.title = `Highlight ${type} in palette`;
  chip.textContent = label;
  applyDefinitionAccent(chip, def);
  chip.addEventListener("click", async () => {
    await openPaletteForCurrentPhase();
  });
  return chip;
}

function renderPhaseMetaBadges(items, complete) {
  const wrap = document.createElement("div");
  wrap.className = "methodology-phase-meta-badges";
  items.forEach((text) => {
    const badge = document.createElement("span");
    const isStatus = text === "Complete" || text === "In progress";
    badge.className = [
      "methodology-meta-badge",
      isStatus && complete ? "is-complete" : "",
      isStatus && !complete ? "is-progress" : "",
    ]
      .filter(Boolean)
      .join(" ");
    badge.textContent = text;
    wrap.appendChild(badge);
  });
  return wrap;
}

function removeFloatedPhaseMenu() {
  document.getElementById(PHASE_MENU_ID)?.remove();
}

function renderIterationLoops(host, stage, process) {
  const loops = outboundLoops(stage);
  if (!loops.length) return;

  appendSection(host, "Engine rework loops", (sec) => {
    const intro = document.createElement("p");
    intro.textContent =
      "Inside each engine cycle, revisit an earlier stage when exit criteria are not met.";
    intro.style.fontSize = "0.72rem";
    intro.style.color = "var(--muted)";
    sec.appendChild(intro);

    loops.forEach((loop) => {
      const card = document.createElement("div");
      card.className = "methodology-loop-card";
      const title = document.createElement("h4");
      title.innerHTML = `${loop.twinPeaks ? '<span class="methodology-loop-badge">Twin Peaks</span>' : ""}${escapeHtml(loop.name)}`;
      card.appendChild(title);
      const trigger = document.createElement("p");
      trigger.innerHTML = `<strong>Trigger:</strong> ${escapeHtml(loop.trigger)}`;
      card.appendChild(trigger);
      const guidance = document.createElement("p");
      guidance.textContent = loop.guidance;
      card.appendChild(guidance);
      const peer = flattenAllStages((process?.phases || []).flatMap((p) => p.stages || [])).find(
        (s) => s.id === loop.peerStageId,
      );
      if (peer) {
        const btn = document.createElement("button");
        btn.type = "button";
        btn.className = "methodology-btn";
        btn.textContent = `Go to ${peer.name}`;
        btn.addEventListener("click", () => selectGuidedStage(peer.id));
        card.appendChild(btn);
      }
      sec.appendChild(card);
    });
  });
}

function openPhaseInMap(phaseId) {
  selectGuidedPhase(phaseId);
  openMethodologyMapAt({ phaseId });
}

function openStageInMap(phaseId, stageId) {
  selectGuidedPhase(phaseId);
  selectGuidedStage(stageId);
  openMethodologyMapAt({ phaseId, stageId });
}

function renderStageTrack(host, phase, progress) {
  const stages = phase?.stages || [];
  if (!stages.length) return;

  const ctx = { stage: selectedStage(processForActiveLevel(), phase, progress) };

  const label = document.createElement("div");
  label.className = "methodology-stage-label";
  label.textContent = "Activities in this phase";
  host.appendChild(label);

  const track = document.createElement("div");
  track.className = "methodology-stage-track";
  stages.forEach((stage) => {
    const complete = isStageComplete(processForActiveLevel(), stage, progress);
    const current = stage.id === ctx.stage?.id;
    const chip = document.createElement("button");
    chip.type = "button";
    chip.className = [
      "methodology-stage-chip",
      complete ? "is-complete" : "",
      current ? "is-current" : "",
    ]
      .filter(Boolean)
      .join(" ");
    chip.title = `Open ${stage.name} in the process map`;
    chip.textContent = stage.name;
    chip.addEventListener("click", () => openStageInMap(phase.id, stage.id));
    track.appendChild(chip);
  });
  host.appendChild(track);
}

function renderTaskCard(task, progress) {
  const done = progress.completedTaskIds.includes(task.id);
  const card = document.createElement("div");
  card.className = `methodology-task-card${done ? " is-done" : ""}`;
  const title = document.createElement("h4");
  title.textContent = task.name;
  card.appendChild(title);
  if (task.artifacts?.length) {
    const arts = document.createElement("p");
    arts.style.fontSize = "0.68rem";
    arts.style.color = "var(--muted)";
    arts.textContent = `Artifacts: ${task.artifacts.map((a) => a.name).join(", ")}`;
    card.appendChild(arts);
  }
  if (task.steps?.length) {
    const steps = document.createElement("ol");
    steps.className = "methodology-task-steps";
    task.steps.forEach((step) => {
      const li = document.createElement("li");
      li.textContent = step;
      steps.appendChild(li);
    });
    card.appendChild(steps);
  }
  if (task.paletteFocus?.length) {
    const grid = document.createElement("div");
    grid.className = "methodology-concept-grid";
    task.paletteFocus.forEach((type) => grid.appendChild(renderConceptChip(type)));
    card.appendChild(grid);
  }
  const markBtn = document.createElement("button");
  markBtn.type = "button";
  markBtn.className = "methodology-btn";
  markBtn.textContent = done ? "Mark incomplete" : "Mark task complete";
  markBtn.addEventListener("click", () => toggleGuidedTaskComplete(task.id));
  card.appendChild(markBtn);
  return card;
}

function _renderPhaseDetail(host, process, progress) {
  const { phase, stage, task } = selectedContext(process, progress);
  if (!phase || !stage) return;

  const detail = document.createElement("article");
  detail.className = "methodology-detail";

  appendSection(detail, "Phase objective", (sec) => {
    const p = document.createElement("p");
    p.textContent = phase.objective;
    sec.appendChild(p);
  });

  appendSection(detail, `Stage: ${stage.name}`, (sec) => {
    const p = document.createElement("p");
    p.textContent = stage.objective;
    sec.appendChild(p);
  });

  if (task?.entryCriteria?.length) {
    appendSection(detail, "Before you start", (sec) => {
      sec.appendChild(renderCriteriaList(task.entryCriteria, task.id, "entry", progress));
    });
  }

  if (task?.exitCriteria?.length) {
    appendSection(detail, "You're done when", (sec) => {
      sec.appendChild(renderCriteriaList(task.exitCriteria, task.id, "exit", progress));
    });
  }

  if (task?.validationRules?.length) {
    appendSection(detail, "Validation rules", (sec) => {
      const ul = document.createElement("ul");
      task.validationRules.forEach((rule) => {
        const li = document.createElement("li");
        li.textContent = rule;
        ul.appendChild(li);
      });
      sec.appendChild(ul);
    });
  }

  renderIterationLoops(detail, stage, process);

  const guidelines = guidanceForProcess(process).filter(
    (g) =>
      g.appliesTo === "process" ||
      g.appliesTo === phase.id ||
      g.appliesTo === stage.id ||
      g.appliesTo === task?.id,
  );
  if (guidelines.length) {
    appendSection(detail, "Guidelines", (sec) => {
      guidelines.forEach((g) => {
        const card = document.createElement("div");
        card.className = "methodology-guideline-card";
        const title = document.createElement("h4");
        title.textContent = g.name;
        card.appendChild(title);
        const text = document.createElement("p");
        text.textContent = g.text;
        card.appendChild(text);
        sec.appendChild(card);
      });
    });
  }

  const role = roleForTask(process, task, stage, phase);
  if (role) {
    appendSection(detail, "Performing role", (sec) => {
      const p = document.createElement("p");
      p.innerHTML = `<strong>${escapeHtml(role.name)}</strong>, ${escapeHtml((role.responsibilities || []).join("; "))}`;
      sec.appendChild(p);
    });
  }

  appendSection(detail, "TaskUses", (sec) => {
    tasksForStage(process, stage).forEach((t) => sec.appendChild(renderTaskCard(t, progress)));
  });

  renderChangeManagement(detail, process);

  const actions = document.createElement("div");
  actions.className = "methodology-actions";
  if (task?.viewpoint) {
    const primary = document.createElement("button");
    primary.type = "button";
    primary.className = "methodology-btn methodology-btn-primary";
    primary.textContent = `Open ${task.viewpoint} view`;
    primary.addEventListener("click", () => activatePhaseViewpoint(task.viewpoint));
    actions.appendChild(primary);
  }

  const row = document.createElement("div");
  row.className = "methodology-actions-row";
  if (isModelingLevel(state.activeType)) {
    const paletteBtn = document.createElement("button");
    paletteBtn.type = "button";
    paletteBtn.className = "methodology-btn";
    paletteBtn.textContent = "Show palette elements";
    paletteBtn.addEventListener("click", openPaletteForCurrentPhase);
    row.appendChild(paletteBtn);
  }
  const aiBtn = document.createElement("button");
  aiBtn.type = "button";
  aiBtn.className = "methodology-btn";
  aiBtn.textContent = "Ask AI";
  aiBtn.addEventListener("click", openAssistantForGuidedPhase);
  row.appendChild(aiBtn);
  actions.appendChild(row);

  const leaves = phase.stages.flatMap(leafStages);
  const stageIndex = leaves.findIndex((s) => s.id === stage.id);
  if (
    stageIndex >= 0 &&
    stageIndex < leaves.length - 1 &&
    isStageComplete(process, stage, progress)
  ) {
    const nextStage = leaves[stageIndex + 1];
    const nextBtn = document.createElement("button");
    nextBtn.type = "button";
    nextBtn.className = "methodology-btn methodology-btn-primary";
    nextBtn.textContent = `Continue to ${nextStage.name}`;
    nextBtn.addEventListener("click", () => selectGuidedStage(nextStage.id));
    actions.appendChild(nextBtn);
  } else if (isPhaseComplete(process, phase, progress)) {
    const phaseIndex = process.phases.indexOf(phase);
    const nextPhase = process.phases[phaseIndex + 1];
    if (nextPhase) {
      const nextBtn = document.createElement("button");
      nextBtn.type = "button";
      nextBtn.className = "methodology-btn methodology-btn-primary";
      nextBtn.textContent = `Continue to phase ${nextPhase.name}`;
      nextBtn.addEventListener("click", () => selectGuidedPhase(nextPhase.id));
      actions.appendChild(nextBtn);
    }
  }

  detail.appendChild(actions);
  host.appendChild(detail);
}

function renderChangeManagement(host, process) {
  const workflows = process?.changeManagement?.workflows || [];
  if (!workflows.length) return;

  appendSection(host, "Changing the model", (sec) => {
    const intro = document.createElement("p");
    intro.textContent =
      "After marking phases complete, use these workflows when scope changes or elements need add/modify/remove.";
    intro.style.fontSize = "0.72rem";
    intro.style.color = "var(--muted)";
    sec.appendChild(intro);

    workflows.slice(0, 5).forEach((workflow) => {
      const card = document.createElement("details");
      card.className = "methodology-change-card";
      const summary = document.createElement("summary");
      summary.textContent = workflow.name;
      card.appendChild(summary);
      if (workflow.trigger) {
        const trig = document.createElement("p");
        trig.style.fontSize = "0.7rem";
        trig.style.margin = "6px 0 4px";
        trig.innerHTML = `<strong>When:</strong> ${escapeHtml(workflow.trigger)}`;
        card.appendChild(trig);
      }
      if (workflow.steps?.length) {
        const ol = document.createElement("ol");
        workflow.steps.forEach((step) => {
          const li = document.createElement("li");
          li.textContent = step;
          ol.appendChild(li);
        });
        card.appendChild(ol);
      }
      sec.appendChild(card);
    });
  });
}

function _renderProcessEngine(host, process) {
  const engine = process?.processEngine;
  if (!engine?.cycle?.length) return;

  const label = document.createElement("div");
  label.className = "methodology-stage-label";
  label.textContent = engine.displayName || "Process engine";
  host.appendChild(label);

  const desc = document.createElement("p");
  desc.style.fontSize = "0.7rem";
  desc.style.color = "var(--muted)";
  desc.style.margin = "0 0 6px";
  desc.style.lineHeight = "1.35";
  desc.textContent = engine.description;
  host.appendChild(desc);

  if (engine.deliverable?.name) {
    const del = document.createElement("p");
    del.style.fontSize = "0.68rem";
    del.style.color = "var(--muted)";
    del.style.margin = "0 0 6px";
    del.textContent = `Accepted outcome per cycle: ${engine.deliverable.name}`;
    host.appendChild(del);
  }

  const bar = document.createElement("div");
  bar.className = "methodology-engine-bar methodology-e2e-bar";
  bar.setAttribute("aria-label", "Process engine cycle");
  engine.cycle.forEach((step) => {
    const chip = document.createElement("span");
    chip.className = "methodology-e2e-step";
    chip.title = step.steps?.join(" ") || step.name;
    chip.textContent = step.name.replace(/ \(.*\)/, "").slice(0, 18);
    bar.appendChild(chip);
  });
  host.appendChild(bar);

  if (engine.loop?.guidance) {
    const loopHint = document.createElement("p");
    loopHint.className = "methodology-hero-hint";
    loopHint.textContent = `↻ ${engine.loop.guidance}`;
    host.appendChild(loopHint);
  }

  if (engine.onboarding?.name) {
    const onboard = document.createElement("p");
    onboard.style.fontSize = "0.68rem";
    onboard.style.color = "var(--muted)";
    onboard.style.marginTop = "4px";
    onboard.textContent = `Before first cycle (once): ${engine.onboarding.name}`;
    host.appendChild(onboard);
  }
}

function _renderEndToEndBar(host) {
  const e2e = state.guidedModeling.endToEnd;
  const cycle = e2e?.processEngine?.cycle || e2e?.phases;
  if (!cycle?.length) return;

  const activeE2eId = modelingMethodologyConfig().endToEndPhaseByLevel?.[state.activeType];

  const label = document.createElement("div");
  label.className = "methodology-stage-label";
  label.textContent = "End-to-end engine";
  host.appendChild(label);

  const wrap = document.createElement("div");
  wrap.className = "methodology-e2e-bar";
  wrap.setAttribute("aria-label", "End-to-end engine cycle");
  cycle.forEach((step) => {
    const chip = document.createElement("span");
    chip.className = `methodology-e2e-step${step.id === activeE2eId ? " is-active" : ""}`;
    chip.title = step.objective || step.name;
    chip.textContent = step.name
      .replace(/ \(.*\)/, "")
      .replace(/Transformation.*/, "ETL")
      .slice(0, 18);
    wrap.appendChild(chip);
  });
  host.appendChild(wrap);
}

function appendSection(parent, title, build) {
  const section = document.createElement("section");
  section.className = "methodology-section";
  const heading = document.createElement("div");
  heading.className = "methodology-section-title";
  heading.textContent = title;
  section.appendChild(heading);
  build(section);
  parent.appendChild(section);
}

function renderCriteriaList(items, phaseId, kind, progress) {
  const wrap = document.createElement("div");
  wrap.className = "methodology-criteria";
  const key = `${phaseId}:${kind}`;
  const checked = new Set(progress.checkedCriteria[key] || []);
  items.forEach((text, index) => {
    const id = `${key}:${index}`;
    const row = document.createElement("label");
    row.className = "methodology-criterion";
    const input = document.createElement("input");
    input.type = "checkbox";
    input.checked = checked.has(id);
    input.addEventListener("change", () => {
      const p = loadProgress();
      const set = new Set(p.checkedCriteria[key] || []);
      if (input.checked) set.add(id);
      else set.delete(id);
      p.checkedCriteria[key] = [...set];
      saveProgress(p);
    });
    const span = document.createElement("span");
    span.textContent = text;
    row.appendChild(input);
    row.appendChild(span);
    wrap.appendChild(row);
  });
  return wrap;
}

function bindPhaseMenuListeners() {
  if (phaseMenuListenersBound) {
    return;
  }
  phaseMenuListenersBound = true;
  document.addEventListener("click", (event) => {
    if (!phaseMenuOpen) {
      return;
    }
    if (
      event.target?.closest?.(".methodology-phase-select-wrap") ||
      event.target?.closest?.(`#${PHASE_MENU_ID}`)
    ) {
      return;
    }
    phaseMenuOpen = false;
    renderGuidedModelingPanel();
  });
  document.addEventListener("keydown", (event) => {
    if (event.key === "Escape" && phaseMenuOpen) {
      phaseMenuOpen = false;
      renderGuidedModelingPanel();
    }
  });
}

function renderProgressTrack(phases, phase, progress, onSelect) {
  const track = document.createElement("div");
  track.className = "methodology-progress-track";
  track.setAttribute("role", "tablist");
  track.setAttribute("aria-label", "Modeling phases");
  phases.forEach((p, i) => {
    const complete = isPhaseComplete(processForActiveLevel(), p, progress);
    const selected = p.id === phase.id;
    const seg = document.createElement("button");
    seg.type = "button";
    seg.className = [
      "methodology-progress-segment",
      complete ? "is-complete" : "",
      selected ? "is-current" : "",
    ]
      .filter(Boolean)
      .join(" ");
    seg.title = `${i}. ${p.name}`;
    seg.setAttribute("role", "tab");
    seg.setAttribute("aria-selected", String(selected));
    seg.setAttribute("aria-label", `${i}. ${p.name}`);
    seg.addEventListener("click", () => onSelect(p.id));
    track.appendChild(seg);
  });
  return track;
}

function renderPhaseNavigator(host, process, progress) {
  const phases = process.phases || [];
  const phase = selectedPhase(process, progress);
  if (!phase) return;

  const index = phases.findIndex((p) => p.id === phase.id);
  const complete = isPhaseComplete(process, phase, progress);
  const nav = document.createElement("div");
  nav.className = "methodology-nav";

  const header = document.createElement("div");
  header.className = "methodology-phase-header";

  const prev = document.createElement("button");
  prev.type = "button";
  prev.className = "methodology-nav-btn";
  prev.title = "Previous phase";
  prev.setAttribute("aria-label", "Previous phase");
  prev.textContent = "‹";
  prev.disabled = index <= 0;
  prev.addEventListener("click", () => {
    phaseMenuOpen = false;
    if (index > 0) openPhaseInMap(phases[index - 1].id);
  });

  const heading = document.createElement("div");
  heading.className = "methodology-phase-heading";
  heading.innerHTML = `
    <div class="methodology-phase-step-label">Phase ${index + 1} of ${phases.length}</div>
    <h3 class="methodology-phase-title">${escapeHtml(phase.name)}</h3>`;

  const metaParts = [complete ? "Complete" : "In progress"];
  heading.appendChild(renderPhaseMetaBadges(metaParts, complete));

  const next = document.createElement("button");
  next.type = "button";
  next.className = "methodology-nav-btn";
  next.title = "Next phase";
  next.setAttribute("aria-label", "Next phase");
  next.textContent = "›";
  next.disabled = index < 0 || index >= phases.length - 1;
  next.addEventListener("click", () => {
    phaseMenuOpen = false;
    if (index < phases.length - 1) openPhaseInMap(phases[index + 1].id);
  });

  header.appendChild(prev);
  header.appendChild(heading);
  header.appendChild(next);
  nav.appendChild(header);
  const phaseLabel = document.createElement("div");
  phaseLabel.className = "methodology-navigator-label";
  phaseLabel.textContent = "All phases";
  nav.appendChild(phaseLabel);
  nav.appendChild(
    renderProgressTrack(phases, phase, progress, (phaseId) => {
      phaseMenuOpen = false;
      openPhaseInMap(phaseId);
    }),
  );
  renderStageTrack(nav, phase, progress);
  host.appendChild(nav);
}

export function renderGuidedModelingPanel() {
  const host = el.methodologyPanelHost;
  if (!host) return;

  if (!isMethodologyLevel(state.activeType)) {
    phaseMenuOpen = false;
    removeFloatedPhaseMenu();
    host.innerHTML = `<div class="methodology-empty">Open a CIM, PIM, PSM, or generated artifacts to use the methodology guide.</div>`;
    return;
  }

  const process = processForActiveLevel();
  if (!process) {
    phaseMenuOpen = false;
    removeFloatedPhaseMenu();
    host.innerHTML = `<div class="methodology-empty">Loading methodology…</div>`;
    return;
  }

  const progress = state.guidedModeling.progress || loadProgress();
  const { complete, total, ratio } = phaseProgress(process, progress);
  const phase = selectedPhase(process, progress);

  removeFloatedPhaseMenu();
  host.innerHTML = "";

  const hero = document.createElement("div");
  hero.className = "methodology-hero";
  hero.innerHTML = `
    <div class="methodology-hero-top">
      <div class="methodology-progress-ring" style="--pct: ${Math.round(ratio * 100)}">
        <span>${complete}/${total}</span>
      </div>
      <div class="methodology-hero-text">
        <h2>${escapeHtml(process.displayName || state.activeType.toUpperCase())} methodology</h2>
        <p>${complete} of ${total} tasks complete</p>
      </div>
    </div>`;
  hero.appendChild(createMethodologyMapOpenButton());
  host.appendChild(hero);

  const progressContract = renderProgressContract(process);
  if (progressContract) host.appendChild(progressContract);

  if (phase) {
    renderPhaseNavigator(host, process, progress);
  }

  const footer = document.createElement("div");
  footer.className = "methodology-footer";
  const reset = document.createElement("button");
  reset.type = "button";
  reset.className = "methodology-btn methodology-btn-danger";
  reset.textContent = "Reset all progress";
  reset.addEventListener("click", () => {
    if (window.confirm("Reset methodology progress for this model?")) {
      resetGuidedProgress();
    }
  });
  footer.appendChild(reset);
  host.appendChild(footer);
}

export function initGuidedModeling() {
  state.guidedModeling ??= {
    definitions: {},
    endToEnd: null,
    progress: loadProgress(),
    loading: false,
    searchQuery: "",
    paletteFocusActive: false,
  };
  state.leftPaneMode ??= "palette";

  bindPhaseMenuListeners();

  window.addEventListener("resize", () => {
    if (!phaseMenuOpen) {
      return;
    }
    renderGuidedModelingPanel();
  });

  el.methodologySearchInput?.addEventListener("input", () => {
    state.guidedModeling.searchQuery = el.methodologySearchInput?.value || "";
    const q = state.guidedModeling.searchQuery.trim().toLowerCase();
    if (q) {
      const process = processForActiveLevel();
      const match = (process?.phases || []).find(
        (p) => p.name.toLowerCase().includes(q) || String(p.id).toLowerCase().includes(q),
      );
      if (match) {
        const progress = loadProgress();
        if (progress.selectedPhaseId !== match.id) {
          progress.selectedPhaseId = match.id;
          state.guidedModeling.progress = progress;
          window.localStorage.setItem(storageKey(), JSON.stringify(progress));
        }
      }
    }
    renderGuidedModelingPanel();
  });

  loadGuidedModelingDefinitions();
  initMethodologyProcessMap();
}

export function onGuidedModelingContextChanged() {
  state.guidedModeling.progress = loadProgress();
  if (state.leftPaneMode === "methodology") {
    renderGuidedModelingPanel();
  }
  refreshMethodologyProcessMap();
  renderPalette();
}

export function showMethodologyPane() {
  switchLeftPaneMode("methodology");
  if (isArtifactLevel(state.activeType)) {
    openMethodologyMap();
  }
}

export function showPalettePane() {
  if (isArtifactLevel(state.activeType)) {
    closeMethodologyMap();
  }
  switchLeftPaneMode("palette");
}
