import { state } from "./state.js";
import {
  TUTORIAL_STEPS,
  readTutorialProgress,
  writeTutorialProgress,
  tutorialStorageKey,
  tutorialElements,
  tutorialTaskComplete,
} from "./tutorial-state.js";

let activeTutorial = null;

export function initEditorTutorial({ showPalette } = {}) {
  if (activeTutorial) return;
  const key = tutorialStorageKey(state.auth.user);
  // Accessing localStorage itself can throw when storage is disabled.
  let storage;
  try {
    storage = window.localStorage;
  } catch {
    storage = null;
  }
  const progress = readTutorialProgress(storage, key);
  if (progress.completed) return;
  let index = Math.max(
    0,
    TUTORIAL_STEPS.findIndex((step) => step.id === progress.step),
  );
  let exercise =
    progress.exercise && typeof progress.exercise === "object" ? progress.exercise : {};
  if (exercise.projectId && exercise.projectId !== state.project?.id) index = 0;
  let stopped = false;
  let minimized = false;
  let previousTarget = null;
  const previousFocus = document.activeElement;
  const root = document.createElement("div");
  root.className = "editor-tutorial";
  root.innerHTML = `
    <div class="editor-tutorial-spotlight" aria-hidden="true"></div>
    <section class="editor-tutorial-card" role="dialog" aria-modal="false"
             aria-labelledby="editorTutorialTitle" aria-describedby="editorTutorialBody">
      <div class="editor-tutorial-heading">
        <span class="editor-tutorial-progress"></span>
        <button type="button" class="editor-tutorial-minimize" aria-expanded="true">Minimize</button>
      </div>
      <progress class="editor-tutorial-meter" max="${TUTORIAL_STEPS.length}" value="1" aria-label="Tutorial progress"></progress>
      <h2 id="editorTutorialTitle" tabindex="-1"></h2>
      <div class="editor-tutorial-instructions">
        <p id="editorTutorialBody"></p>
        <p class="editor-tutorial-task" role="status" aria-live="polite"></p>
      </div>
      <div class="editor-tutorial-actions">
        <button type="button" class="btn btn-secondary editor-tutorial-finish">Finish tutorial</button>
        <div class="editor-tutorial-navigation">
          <button type="button" class="btn btn-secondary editor-tutorial-back">Back</button>
          <button type="button" class="btn btn-primary editor-tutorial-next">Next</button>
        </div>
      </div>
    </section>`;
  document.body.appendChild(root);
  activeTutorial = root;
  const card = root.querySelector(".editor-tutorial-card");
  const spotlight = root.querySelector(".editor-tutorial-spotlight");
  const title = root.querySelector("h2");
  const body = root.querySelector("#editorTutorialBody");
  const task = root.querySelector(".editor-tutorial-task");
  const next = root.querySelector(".editor-tutorial-next");
  const back = root.querySelector(".editor-tutorial-back");
  const minimize = root.querySelector(".editor-tutorial-minimize");
  let lastWritten = "";

  function persist(completed = false) {
    const value = { completed, step: TUTORIAL_STEPS[index].id, exercise };
    const serialized = JSON.stringify(value);
    if (serialized !== lastWritten) {
      writeTutorialProgress(storage, key, value);
      lastWritten = serialized;
    }
  }

  function finish() {
    persist(true);
    stopped = true;
    window.clearInterval(timer);
    window.removeEventListener("resize", position);
    document.removeEventListener("scroll", position, true);
    document.removeEventListener("keydown", onKeyDown);
    window.removeEventListener("storage", onStorage);
    root.remove();
    activeTutorial = null;
    if (previousFocus?.isConnected && previousFocus !== document.body) previousFocus.focus();
  }

  function onStorage(event) {
    if (event.key === key && readTutorialProgress(storage, key).completed) finish();
  }

  function onKeyDown(event) {
    if (event.key === "Escape" && root.contains(document.activeElement)) {
      event.preventDefault();
      finish();
    }
  }

  function visibleTarget() {
    for (const selector of TUTORIAL_STEPS[index].target.split(",")) {
      const target = document.querySelector(selector.trim());
      if (!target || target.closest(".hidden")) continue;
      const rect = target.getBoundingClientRect();
      if (
        rect.width &&
        rect.height &&
        rect.bottom > 0 &&
        rect.top < window.innerHeight &&
        rect.right > 0 &&
        rect.left < window.innerWidth
      )
        return target;
    }
    return null;
  }

  function position() {
    if (stopped) return;
    const target = visibleTarget();
    if (target !== previousTarget) {
      previousTarget = target;
      target?.scrollIntoView({ block: "nearest", inline: "nearest", behavior: "instant" });
    }
    const width = window.innerWidth;
    const height = window.innerHeight;
    const rect = target?.getBoundingClientRect();
    spotlight.hidden = !rect || minimized;
    if (rect) {
      const left = Math.max(4, rect.left - 5);
      const top = Math.max(4, rect.top - 5);
      Object.assign(spotlight.style, {
        left: `${left}px`,
        top: `${top}px`,
        width: `${Math.max(0, Math.min(width - 4, rect.right + 5) - left)}px`,
        height: `${Math.max(0, Math.min(height - 4, rect.bottom + 5) - top)}px`,
      });
    }
    const cardWidth = card.offsetWidth;
    const cardHeight = card.offsetHeight;
    const gap = 18;
    let x = (width - cardWidth) / 2;
    let y = (height - cardHeight) / 2;
    if (minimized || width <= 640) {
      y = height - cardHeight - 76;
    } else if (rect) {
      const candidates = [
        [rect.right + gap, rect.top],
        [rect.left - cardWidth - gap, rect.top],
        [rect.left, rect.bottom + gap],
        [rect.left, rect.top - cardHeight - gap],
      ];
      const fits = candidates.find(
        ([cx, cy]) =>
          cx >= 12 && cy >= 12 && cx + cardWidth <= width - 12 && cy + cardHeight <= height - 12,
      );
      [x, y] = fits || [width - cardWidth - 24, height - cardHeight - 32];
    }
    card.style.left = `${Math.max(12, Math.min(x, width - cardWidth - 12))}px`;
    card.style.top = `${Math.max(12, Math.min(y, height - cardHeight - 12))}px`;
  }

  function updateTask() {
    const step = TUTORIAL_STEPS[index];
    const complete =
      (!step.task || tutorialTaskComplete(step.id, state, exercise)) &&
      (step.id !== "project" ||
        document.getElementById("projectOverlay")?.classList.contains("hidden"));
    next.disabled = !complete;
    const message = step.task ? (complete ? "Step completed. Select Next to continue." : step.task) : "";
    if (task.textContent !== message) task.textContent = message;
    task.hidden = !message;
    persist();
  }

  function render() {
    const step = TUTORIAL_STEPS[index];
    if (["goal", "requirement", "palette"].includes(step.id)) showPalette?.();
    if (["cim-view", "goal"].includes(step.id) && !exercise.projectId) {
      exercise = {
        projectId: state.project?.id,
        baselineIds: tutorialElements(state).map((element) => element.id),
      };
    }
    if (step.id === "save") exercise.savesBefore = state.modelSave?.successfulSaves || 0;
    root.querySelector(".editor-tutorial-progress").textContent =
      `${step.phase} · ${index + 1} / ${TUTORIAL_STEPS.length}`;
    root.querySelector("progress").value = index + 1;
    title.textContent = step.title;
    body.textContent = step.body;
    next.textContent = step.next || "Next";
    root.querySelector(".editor-tutorial-finish").classList.toggle("hidden", index === TUTORIAL_STEPS.length - 1);
    back.disabled = index === 0;
    previousTarget = null;
    updateTask();
    position();
    title.focus({ preventScroll: true });
  }

  next.addEventListener("click", () => {
    updateTask();
    if (next.disabled) return;
    if (index === TUTORIAL_STEPS.length - 1) return finish();
    index += 1;
    render();
  });
  back.addEventListener("click", () => {
    index = Math.max(0, index - 1);
    render();
  });
  root.querySelector(".editor-tutorial-finish").addEventListener("click", finish);
  minimize.addEventListener("click", () => {
    minimized = !minimized;
    root.classList.toggle("is-minimized", minimized);
    minimize.textContent = minimized ? "Show instructions" : "Minimize";
    minimize.setAttribute("aria-expanded", String(!minimized));
    position();
  });
  document.addEventListener("keydown", onKeyDown);
  window.addEventListener("storage", onStorage);
  window.addEventListener("resize", position);
  document.addEventListener("scroll", position, true);
  const timer = window.setInterval(() => {
    // Project changes invalidate the exercise, including references captured before a reload.
    if (exercise.projectId && exercise.projectId !== state.project?.id) {
      exercise = {};
      index = 0;
      render();
    }
    // Unsaved elements can disappear after a refresh, or the learner may delete them.
    const elements = tutorialElements(state);
    const goalIndex = TUTORIAL_STEPS.findIndex((step) => step.id === "goal");
    const requirementIndex = TUTORIAL_STEPS.findIndex((step) => step.id === "requirement");
    if (
      state.activeType === "cim" &&
      index > goalIndex &&
      exercise.goalId &&
      !elements.some((element) => element.id === exercise.goalId)
    ) {
      delete exercise.goalId;
      delete exercise.requirementId;
      index = goalIndex;
      render();
    } else if (
      state.activeType === "cim" &&
      index > requirementIndex &&
      exercise.requirementId &&
      !elements.some((element) => element.id === exercise.requirementId)
    ) {
      delete exercise.requirementId;
      index = requirementIndex;
      render();
    }
    // The chooser can close asynchronously after a project is fully loaded.
    if (
      TUTORIAL_STEPS[index].id === "project" &&
      state.project?.id &&
      document.getElementById("projectOverlay")?.classList.contains("hidden")
    ) {
      index = 1;
      render();
    }
    updateTask();
    position();
  }, 350);
  render();
}
