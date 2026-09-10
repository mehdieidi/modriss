import { el } from "./dom.js";

const CHOICES = [
  {
    value: "KEEP_USER",
    title: "Keep my version",
    detail: "Preserve the manual value currently in your model.",
  },
  {
    value: "TAKE_GENERATED",
    title: "Keep generated version",
    detail: "Apply the value from the newly generated model.",
  },
];

export function resolveSynchronizationConflicts(conflicts) {
  if (!Array.isArray(conflicts) || conflicts.length === 0) return Promise.resolve(new Map());
  if (!el.synchronizationConflictOverlay) return Promise.resolve(null);

  const decisions = new Map();
  let index = 0;
  let finish;
  const close = (result) => {
    el.synchronizationConflictOverlay.classList.add("hidden");
    document.body.classList.remove("modal-open");
    document.removeEventListener("keydown", onKeyDown);
    el.synchronizationConflictCloseBtn.removeEventListener("click", cancel);
    el.synchronizationConflictOverlay.removeEventListener("click", onOverlayClick);
    el.synchronizationConflictPreviousBtn.removeEventListener("click", previous);
    el.synchronizationConflictNextBtn.removeEventListener("click", next);
    el.synchronizationConflictApplyBtn.removeEventListener("click", apply);
    finish(result);
  };
  const cancel = () => close(null);
  const onOverlayClick = (event) => {
    if (event.target === el.synchronizationConflictOverlay) cancel();
  };
  const previous = () => {
    if (index > 0) {
      index -= 1;
      render();
    }
  };
  const next = () => {
    if (index < conflicts.length - 1) {
      index += 1;
      render();
    }
  };
  const apply = () => close(decisions);
  const chooseAll = (choice) => {
    conflicts.forEach((conflict) => decisions.set(conflict.conflictId, choice));
    render();
  };
  const onKeyDown = (event) => {
    if (event.key === "Escape") {
      event.preventDefault();
      cancel();
    }
    if (event.key === "ArrowLeft") {
      event.preventDefault();
      previous();
    }
    if (event.key === "ArrowRight") {
      event.preventDefault();
      next();
    }
  };

  const choose = (choice) => {
    decisions.set(conflicts[index].conflictId, choice);
    render();
  };
  const render = () => {
    const conflict = conflicts[index];
    const element = conflict.elementName || conflict.elementEClass || "Model element";
    el.synchronizationConflictProgress.textContent = `Conflict ${index + 1} of ${conflicts.length} · ${decisions.size} selected`;
    el.synchronizationConflictTitle.textContent = `Resolve ${element}`;
    el.synchronizationConflictSummary.textContent =
      conflict.description ||
      "Both your model and the newly generated model changed the same value.";
    replaceChildren(el.synchronizationConflictMeta, [
      meta("Element", conflict.elementEClass || "Model element"),
      meta("Property", conflict.featureName || "Unknown"),
      meta("Change", humanize(conflict.differenceKind || "changed")),
    ]);
    replaceChildren(el.synchronizationConflictDiff, [
      valueCard("Previous generated baseline", conflict.baseValue, "baseline"),
      valueCard("Your manual version", conflict.workingValue, "manual"),
      valueCard("Fresh generated version", conflict.generatedValue, "generated"),
    ]);
    const selected = decisions.get(conflict.conflictId);
    replaceChildren(
      el.synchronizationConflictChoices,
      CHOICES.map((choice) => choiceButton(choice, selected, choose)),
    );
    replaceChildren(
      el.synchronizationConflictBulkAction,
      selected ? [applyAllButton(selected, chooseAll, conflicts.length)] : [],
    );
    el.synchronizationConflictPreviousBtn.disabled = index === 0;
    el.synchronizationConflictNextBtn.disabled = index === conflicts.length - 1;
    el.synchronizationConflictNextBtn.textContent =
      index === conflicts.length - 1 ? "Last conflict" : "Next";
    el.synchronizationConflictApplyBtn.disabled = decisions.size !== conflicts.length;
    el.synchronizationConflictApplyBtn.textContent =
      decisions.size === conflicts.length
        ? "Apply all decisions"
        : `Choose ${conflicts.length - decisions.size} more`;
  };

  el.synchronizationConflictCloseBtn.addEventListener("click", cancel);
  el.synchronizationConflictOverlay.addEventListener("click", onOverlayClick);
  el.synchronizationConflictPreviousBtn.addEventListener("click", previous);
  el.synchronizationConflictNextBtn.addEventListener("click", next);
  el.synchronizationConflictApplyBtn.addEventListener("click", apply);
  document.addEventListener("keydown", onKeyDown);
  el.synchronizationConflictOverlay.classList.remove("hidden");
  document.body.classList.add("modal-open");
  render();
  return new Promise((resolve) => {
    finish = resolve;
  });
}

function meta(label, value) {
  const item = document.createElement("span");
  item.className = "synchronization-conflict-meta-item";
  item.textContent = `${label}: ${value}`;
  return item;
}

function valueCard(label, value, kind) {
  const card = document.createElement("section");
  card.className = `synchronization-conflict-value synchronization-conflict-value-${kind}`;
  const heading = document.createElement("h4");
  heading.textContent = label;
  const code = document.createElement("pre");
  code.textContent = formatValue(value);
  card.append(heading, code);
  return card;
}

function choiceButton(choice, selected, choose) {
  const button = document.createElement("button");
  button.type = "button";
  button.className = `synchronization-conflict-option${selected === choice.value ? " selected" : ""}`;
  button.setAttribute("aria-pressed", String(selected === choice.value));
  const title = document.createElement("strong");
  title.textContent = choice.title;
  const detail = document.createElement("span");
  detail.textContent = choice.detail;
  button.append(title, detail);
  button.addEventListener("click", () => choose(choice.value));
  return button;
}

function applyAllButton(choice, chooseAll, total) {
  const button = document.createElement("button");
  button.type = "button";
  button.className = "btn btn-secondary";
  const title = CHOICES.find((item) => item.value === choice)?.title || "selected value";
  button.textContent = `Apply “${title}” to all ${total} conflicts`;
  button.addEventListener("click", () => chooseAll(choice));
  return button;
}

function replaceChildren(element, children) {
  element.replaceChildren(...children);
}

function formatValue(value) {
  if (value === null || value === undefined) return "Absent, this version deletes the value.";
  if (typeof value === "string") return value || "(empty string)";
  return JSON.stringify(value, null, 2);
}

function humanize(value) {
  return String(value).replaceAll("_", " ").toLowerCase();
}
