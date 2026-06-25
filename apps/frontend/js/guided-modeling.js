import { api } from "./api.js";
import { state } from "./state.js";
import { el } from "./dom.js";
import { escapeHtml } from "./utils.js";
import { setStatus } from "./status.js";
import {
  isModelingLevel,
  modelingElementDefinition,
  modelingRelationshipKindLabel,
} from "./modeling-config-data.js";
import { applyDefinitionAccent, renderPalette, syncPaletteCollapsedUi } from "./canvas.js";
import { levelIntro, phaseNarrative } from "./methodology-narratives.mjs";
import { openWorkbenchView } from "./view-explorer.js";

const STORAGE_KEY = "modless.guidedModeling.progress";
const PHASE_MENU_ID = "methodologyPhaseMenu";
const PHASE_MENU_MAX_HEIGHT = 320;
const PHASE_MENU_MIN_HEIGHT = 100;
const PHASE_MENU_GAP = 6;
const VIEWPORT_PAD = 8;

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
      checkedCriteria: parsed?.checkedCriteria || {},
    };
  } catch {
    return { completedTaskIds: [], selectedPhaseId: null, checkedCriteria: {} };
  }
}

function saveProgress(progress) {
  state.guidedModeling.progress = progress;
  window.localStorage.setItem(storageKey(), JSON.stringify(progress));
  renderGuidedModelingPanel();
}

function processForActiveLevel() {
  if (!isModelingLevel(state.activeType)) return null;
  return state.guidedModeling.definitions[state.activeType] || null;
}

function phaseTaskIds(phase) {
  return (phase?.tasks || []).map((t) => t.id).filter(Boolean);
}

function isPhaseComplete(phase, progress) {
  const ids = phaseTaskIds(phase);
  return ids.length > 0 && ids.every((id) => progress.completedTaskIds.includes(id));
}

function selectedPhase(process, progress) {
  if (!process?.phases?.length) return null;
  if (progress.selectedPhaseId) {
    const found = process.phases.find((p) => p.id === progress.selectedPhaseId);
    if (found) return found;
  }
  const firstIncomplete = process.phases.find((phase) => !isPhaseComplete(phase, progress));
  return firstIncomplete || process.phases[0];
}

function suggestedPhase(process, progress) {
  return process?.phases?.find((phase) => !isPhaseComplete(phase, progress)) || null;
}

function phaseProgress(process, progress) {
  const phases = process?.phases || [];
  const complete = phases.filter((p) => isPhaseComplete(p, progress)).length;
  return { complete, total: phases.length, ratio: phases.length ? complete / phases.length : 0 };
}

export function guidedPaletteFocusTypes() {
  if (state.leftPaneMode !== "methodology" && !state.guidedModeling?.paletteFocusActive) {
    return new Set();
  }
  const process = processForActiveLevel();
  if (!process) return new Set();
  const progress = state.guidedModeling.progress || loadProgress();
  const phase = selectedPhase(process, progress);
  const mainTask = (phase?.tasks || []).find((t) => t.id?.endsWith(".main")) || phase?.tasks?.[0];
  return new Set(mainTask?.paletteFocus || []);
}

export function guidedActiveTask() {
  const process = processForActiveLevel();
  if (!process) return null;
  const progress = state.guidedModeling.progress || loadProgress();
  const phase = selectedPhase(process, progress);
  return (phase?.tasks || []).find((t) => !progress.completedTaskIds.includes(t.id)) || null;
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
  await openWorkbenchView(viewId);
  setStatus("Opened recommended canvas view for this phase.");
  return true;
}

export function selectGuidedPhase(phaseId) {
  const progress = loadProgress();
  progress.selectedPhaseId = phaseId;
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
  saveProgress({ completedTaskIds: [], selectedPhaseId: null, checkedCriteria: {} });
  renderPalette();
}

export function switchLeftPaneMode(mode) {
  if (!el.workspace) return;
  const next = mode === "methodology" ? "methodology" : "palette";
  state.leftPaneMode = next;
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
  renderPalette();
}

export function syncMethodologyRailState() {
  const hidden = el.workspace?.classList.contains("palette-hidden");
  const mode = state.leftPaneMode || "palette";
  el.paletteRailToggleBtn?.classList.toggle("active", !hidden && mode === "palette");
  el.methodologyRailBtn?.classList.toggle("active", !hidden && mode === "methodology");
}

export async function loadGuidedModelingDefinitions() {
  if (state.guidedModeling.loading) return;
  state.guidedModeling.loading = true;
  try {
    const levels = ["cim", "pim", "psm"];
    const results = await Promise.all(
      levels.map(async (level) => {
        const def = await api(`/modeling/process/${level}`);
        return [level, def];
      }),
    );
    state.guidedModeling.definitions = Object.fromEntries(results);
    state.guidedModeling.endToEnd = await api("/modeling/process/end-to-end");
    state.guidedModeling.progress = loadProgress();
  } catch (error) {
    console.warn("Guided modeling definitions unavailable", error);
  } finally {
    state.guidedModeling.loading = false;
    if (state.leftPaneMode === "methodology") {
      renderGuidedModelingPanel();
    }
  }
}

function buildTaskPrompt(phase, task, narrative) {
  const concepts = narrative.concepts.slice(0, 12).join(", ");
  const steps = (task?.steps || narrative.steps || []).join(" ");
  return [
    `Help me complete modeling phase "${phase.name}" (${phase.id}) for ${state.activeType.toUpperCase()}.`,
    narrative.summary,
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
  const phase = selectedPhase(process, progress);
  if (!phase) return;
  const narrative = phaseNarrative(phase, state.activeType);
  const task =
    (phase.tasks || []).find((t) => !progress.completedTaskIds.includes(t.id)) || phase.tasks?.[0];
  const prompt = buildTaskPrompt(phase, task, narrative);
  window.dispatchEvent(
    new CustomEvent("modless:guided-task-prompt", { detail: { taskId: task?.id, prompt } }),
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

function positionPhaseMenu(anchor, menu) {
  if (!anchor || !menu || menu.classList.contains("hidden")) {
    return;
  }

  menu.id = PHASE_MENU_ID;
  if (menu.parentElement !== document.body) {
    document.body.appendChild(menu);
  }

  const rect = anchor.getBoundingClientRect();
  const spaceBelow = Math.max(0, window.innerHeight - rect.bottom - PHASE_MENU_GAP - VIEWPORT_PAD);
  const spaceAbove = Math.max(0, rect.top - PHASE_MENU_GAP - VIEWPORT_PAD);
  const openUp = spaceBelow < spaceAbove;
  const available = openUp ? spaceAbove : spaceBelow;
  const maxHeight = Math.min(PHASE_MENU_MAX_HEIGHT, Math.max(PHASE_MENU_MIN_HEIGHT, available));

  let left = rect.left;
  const width = rect.width;
  if (left + width > window.innerWidth - VIEWPORT_PAD) {
    left = Math.max(VIEWPORT_PAD, window.innerWidth - VIEWPORT_PAD - width);
  }
  left = Math.max(VIEWPORT_PAD, left);

  menu.style.position = "fixed";
  menu.style.left = `${Math.round(left)}px`;
  menu.style.width = `${Math.round(width)}px`;
  menu.style.right = "auto";
  menu.style.maxHeight = `${Math.round(maxHeight)}px`;
  menu.style.zIndex = "1200";

  if (openUp) {
    menu.style.top = "auto";
    menu.style.bottom = `${Math.round(window.innerHeight - rect.top + PHASE_MENU_GAP)}px`;
  } else {
    menu.style.top = `${Math.round(rect.bottom + PHASE_MENU_GAP)}px`;
    menu.style.bottom = "auto";
  }

  menu.classList.remove("is-positioning");
}

function renderPhaseDetail(host, phase, process, progress) {
  const narrative = phaseNarrative(phase, state.activeType);
  const detail = document.createElement("article");
  detail.className = "methodology-detail";

  appendSection(detail, "What this phase means", (sec) => {
    const p = document.createElement("p");
    p.textContent = narrative.summary;
    sec.appendChild(p);
  });

  appendSection(detail, "Why it comes now", (sec) => {
    const p = document.createElement("p");
    p.textContent = narrative.why;
    sec.appendChild(p);
  });

  if (narrative.concepts.length) {
    appendSection(detail, "Elements & concepts to model", (sec) => {
      const grid = document.createElement("div");
      grid.className = "methodology-concept-grid";
      narrative.concepts.forEach((type) => grid.appendChild(renderConceptChip(type)));
      sec.appendChild(grid);
    });
  }

  if (narrative.relationships?.length) {
    appendSection(detail, "Relationships you'll use", (sec) => {
      const ul = document.createElement("ul");
      narrative.relationships.forEach((kind) => {
        const li = document.createElement("li");
        li.textContent = modelingRelationshipKindLabel(state.activeType, kind);
        ul.appendChild(li);
      });
      sec.appendChild(ul);
    });
  }

  if (narrative.entryCriteria.length) {
    appendSection(detail, "Before you start", (sec) => {
      sec.appendChild(renderCriteriaList(narrative.entryCriteria, phase.id, "entry", progress));
    });
  }

  if (narrative.exitCriteria.length) {
    appendSection(detail, "You're done when", (sec) => {
      sec.appendChild(renderCriteriaList(narrative.exitCriteria, phase.id, "exit", progress));
    });
  }

  if (narrative.validationRules.length) {
    appendSection(detail, "Validation rules", (sec) => {
      const ul = document.createElement("ul");
      narrative.validationRules.forEach((rule) => {
        const li = document.createElement("li");
        li.textContent = rule;
        ul.appendChild(li);
      });
      sec.appendChild(ul);
    });
  }

  (visibleTasks(phase) || []).forEach((task) => {
    const done = progress.completedTaskIds.includes(task.id);
    const card = document.createElement("div");
    card.className = `methodology-task-card${done ? " is-done" : ""}`;
    const title = document.createElement("h4");
    title.textContent = task.name;
    card.appendChild(title);
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
    const markBtn = document.createElement("button");
    markBtn.type = "button";
    markBtn.className = "methodology-btn";
    markBtn.textContent = done ? "Mark incomplete" : "Mark task complete";
    markBtn.addEventListener("click", () => toggleGuidedTaskComplete(task.id));
    card.appendChild(markBtn);
    detail.appendChild(card);
  });

  const actions = document.createElement("div");
  actions.className = "methodology-actions";
  const primary = document.createElement("button");
  primary.type = "button";
  primary.className = "methodology-btn methodology-btn-primary";
  primary.textContent = `Open ${narrative.viewpointLabel}`;
  primary.addEventListener("click", () => activatePhaseViewpoint(phase.viewpoint));
  actions.appendChild(primary);

  const row = document.createElement("div");
  row.className = "methodology-actions-row";
  const paletteBtn = document.createElement("button");
  paletteBtn.type = "button";
  paletteBtn.className = "methodology-btn";
  paletteBtn.textContent = "Show palette elements";
  paletteBtn.addEventListener("click", openPaletteForCurrentPhase);
  const aiBtn = document.createElement("button");
  aiBtn.type = "button";
  aiBtn.className = "methodology-btn";
  aiBtn.textContent = "Ask AI";
  aiBtn.addEventListener("click", openAssistantForGuidedPhase);
  row.appendChild(paletteBtn);
  row.appendChild(aiBtn);
  actions.appendChild(row);

  if (isPhaseComplete(phase, progress)) {
    const next = process.phases[process.phases.indexOf(phase) + 1];
    if (next) {
      const nextBtn = document.createElement("button");
      nextBtn.type = "button";
      nextBtn.className = "methodology-btn methodology-btn-primary";
      nextBtn.textContent = `Continue to ${next.name}`;
      nextBtn.addEventListener("click", () => selectGuidedPhase(next.id));
      actions.appendChild(nextBtn);
    }
  }

  detail.appendChild(actions);
  host.appendChild(detail);
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

function visibleTasks(phase) {
  return (phase?.tasks || []).filter(
    (task) => !task.id?.endsWith(".enums") && !/^Configure .+ enumerations$/i.test(task.name || ""),
  );
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
    const complete = isPhaseComplete(p, progress);
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
  const narrative = phaseNarrative(phase, state.activeType);
  const complete = isPhaseComplete(phase, progress);
  const nav = document.createElement("div");
  nav.className = "methodology-nav";

  nav.appendChild(
    renderProgressTrack(phases, phase, progress, (phaseId) => {
      phaseMenuOpen = false;
      selectGuidedPhase(phaseId);
    }),
  );

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
    if (index > 0) selectGuidedPhase(phases[index - 1].id);
  });

  const heading = document.createElement("div");
  heading.className = "methodology-phase-heading";
  heading.innerHTML = `
    <div class="methodology-phase-step-label">Step ${index + 1} of ${phases.length}</div>
    <h3 class="methodology-phase-title">${escapeHtml(phase.name)}</h3>`;

  const metaParts = [
    narrative.duration || null,
    narrative.viewpointLabel || null,
    narrative.role || null,
    complete ? "Complete" : "In progress",
  ].filter(Boolean);
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
    if (index < phases.length - 1) selectGuidedPhase(phases[index + 1].id);
  });

  header.appendChild(prev);
  header.appendChild(heading);
  header.appendChild(next);
  nav.appendChild(header);

  const selectWrap = document.createElement("div");
  selectWrap.className = `methodology-phase-select-wrap${phaseMenuOpen ? " is-open" : ""}`;

  const selectBtn = document.createElement("button");
  selectBtn.type = "button";
  selectBtn.className = "sidebar-select methodology-phase-jump-btn";
  selectBtn.setAttribute("aria-haspopup", "listbox");
  selectBtn.setAttribute("aria-expanded", phaseMenuOpen ? "true" : "false");
  selectBtn.innerHTML = `
    <span class="methodology-phase-jump-label">Jump to phase</span>
    <span class="methodology-phase-jump-caret" aria-hidden="true"></span>`;
  selectBtn.addEventListener("click", (event) => {
    event.stopPropagation();
    phaseMenuOpen = !phaseMenuOpen;
    renderGuidedModelingPanel();
  });

  const menu = document.createElement("div");
  menu.className = `methodology-phase-menu${phaseMenuOpen ? " is-positioning" : " hidden"}`;
  menu.setAttribute("role", "listbox");
  menu.setAttribute("aria-label", "Modeling phases");
  phases.forEach((p, i) => {
    const done = isPhaseComplete(p, progress);
    const option = document.createElement("button");
    option.type = "button";
    option.className = `methodology-phase-option${p.id === phase.id ? " is-active" : ""}`;
    option.setAttribute("role", "option");
    option.setAttribute("aria-selected", String(p.id === phase.id));
    option.innerHTML = `
      <span class="methodology-phase-option-index${done ? " is-complete" : ""}" aria-hidden="true">${done ? "✓" : i}</span>
      <span class="methodology-phase-option-text">
        <span class="methodology-phase-option-label">${escapeHtml(p.name)}</span>
        ${p.id === phase.id ? '<span class="methodology-phase-option-current">Current</span>' : ""}
      </span>`;
    option.addEventListener("click", (event) => {
      event.stopPropagation();
      phaseMenuOpen = false;
      selectGuidedPhase(p.id);
    });
    menu.appendChild(option);
  });

  selectWrap.appendChild(selectBtn);
  nav.appendChild(selectWrap);
  host.appendChild(nav);

  if (phaseMenuOpen) {
    positionPhaseMenu(selectBtn, menu);
  }
}

export function renderGuidedModelingPanel() {
  const host = el.methodologyPanelHost;
  if (!host) return;

  if (!isModelingLevel(state.activeType)) {
    phaseMenuOpen = false;
    removeFloatedPhaseMenu();
    host.innerHTML = `<div class="methodology-empty">Open a CIM, PIM, or PSM model to use the methodology guide.</div>`;
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
  const suggested = suggestedPhase(process, progress);

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
        <p>${escapeHtml(levelIntro(state.activeType))}</p>
      </div>
    </div>`;
  if (suggested && suggested.id !== phase?.id) {
    const hint = document.createElement("p");
    hint.className = "methodology-hero-hint";
    hint.textContent = `Suggested next: ${suggested.name}`;
    hero.appendChild(hint);
  }
  host.appendChild(hero);

  if (phase) {
    renderPhaseNavigator(host, process, progress);
    renderPhaseDetail(host, phase, process, progress);
  }

  const footer = document.createElement("div");
  footer.className = "methodology-footer";
  const reset = document.createElement("button");
  reset.type = "button";
  reset.className = "methodology-btn";
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

  el.methodologyPanelHost?.addEventListener(
    "scroll",
    () => {
      if (!phaseMenuOpen) {
        return;
      }
      phaseMenuOpen = false;
      renderGuidedModelingPanel();
    },
    { passive: true },
  );

  window.addEventListener("resize", () => {
    if (!phaseMenuOpen) {
      return;
    }
    const anchor = el.methodologyPanelHost?.querySelector(".methodology-phase-jump-btn");
    const menu = document.getElementById(PHASE_MENU_ID);
    positionPhaseMenu(anchor, menu);
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
}

export function onGuidedModelingContextChanged() {
  state.guidedModeling.progress = loadProgress();
  if (state.leftPaneMode === "methodology") {
    renderGuidedModelingPanel();
  }
  renderPalette();
}

export function showMethodologyPane() {
  switchLeftPaneMode("methodology");
}

export function showPalettePane() {
  switchLeftPaneMode("palette");
}
