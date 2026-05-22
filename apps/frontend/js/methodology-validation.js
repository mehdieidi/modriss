import {el} from './dom.js';
import {state} from './state.js';

function escapeHtml(value) {
  return String(value ?? "")
  .replace(/&/g, "&amp;")
  .replace(/</g, "&lt;")
  .replace(/>/g, "&gt;")
  .replace(/"/g, "&quot;")
  .replace(/'/g, "&#39;");
}

function severityOf(issue) {
  return String(issue?.severity || "ERROR").toUpperCase();
}

function statusFromIssues(issues) {
  if (!issues.length) {
    return {
      stateClass: "validation-state-ok",
      label: "OK",
      meta: "No issues found."
    };
  }
  const hasError = issues.some((issue) => severityOf(issue) === "ERROR");
  const hasWarning = issues.some((issue) => severityOf(issue) === "WARNING");
  if (hasError) {
    return {
      stateClass: "validation-state-error",
      label: `Errors (${issues.length})`,
      meta: `${issues.length} issue(s): fix required before generation.`
    };
  }
  if (hasWarning) {
    return {
      stateClass: "validation-state-warning",
      label: `Warnings (${issues.length})`,
      meta: `${issues.length} warning(s): review recommended.`
    };
  }
  return {
    stateClass: "validation-state-ok",
    label: `Issues (${issues.length})`,
    meta: `${issues.length} informational issue(s).`
  };
}

function renderIssues(issues) {
  if (!el.validationDrawerIssues) {
    return;
  }
  if (!issues.length) {
    el.validationDrawerIssues.innerHTML = `<div class="validation-issue-message">Model is valid against current metamodel and constraints.</div>`;
    return;
  }
  const manualIssues = issues.filter((issue) =>
      String(issue?.issueClass || "").startsWith("MANUAL_"));
  const openManual = manualIssues.filter((issue) => !issue?.resolved);
  const resolvedManual = manualIssues.filter(
      (issue) => Boolean(issue?.resolved));
  const nonManual = issues.filter((issue) =>
      !String(issue?.issueClass || "").startsWith("MANUAL_"));
  const showResolved = state.validation.manualView === "resolved";
  const visibleIssues = showResolved ? resolvedManual : [...nonManual,
    ...openManual];
  const rendered = visibleIssues.map((issue) => {
    const rawClass = String(issue.issueClass || issue.code || "").toUpperCase();
    const issueClass = rawClass.includes("MANUAL_REQUIRED")
        ? "Manual Required"
        : rawClass.includes("MANUAL_OPTIONAL")
            ? "Manual Optional"
            : rawClass.includes("GENERATION") || rawClass.includes("SYSTEM")
                ? "System Error"
                : "Validation";
    const title = issue.constraint || issue.code || "Constraint";
    const message = issue.message || "Invalid model state.";
    const guide = issue.guidance || issue.suggestedFix
        || "Review and fix this element.";
    const element = issue.elementName || issue.elementId || "";
    const hasTarget = Boolean(issue.elementId);
    const isManual = rawClass.startsWith("MANUAL_");
    const toggle = isManual
        ? `<label class="validation-manual-toggle"><input data-manual-task-id="${escapeHtml(
            issue.manualTaskId || "")}" type="checkbox" ${issue.resolved
            ? "checked" : ""}/> Resolve</label>`
        : "";
    const itemClass = `validation-issue-item${issue.resolved
        ? " validation-issue-item-resolved" : ""}`;
    return `<li class="${itemClass}">
      <div class="validation-issue-head-row">
        <div class="validation-issue-head">${escapeHtml(title)}</div>
        <span class="validation-issue-kind">${escapeHtml(issueClass)}</span>
        ${toggle}
        ${hasTarget
        ? `<button class="validation-issue-locate" data-issue-locate="${escapeHtml(
            issue.elementId)}" data-issue-element-name="${escapeHtml(
            issue.elementName || "")}" data-issue-element-type="${escapeHtml(
            issue.elementType || "")}" type="button">Locate</button>` : ""}
      </div>
      ${element ? `<div class="validation-issue-element">${escapeHtml(
        element)}</div>` : ""}
      <div class="validation-issue-message">${escapeHtml(message)}</div>
      <div class="validation-issue-guide">${escapeHtml(guide)}</div>
    </li>`;
  }).join("");
  const tabs = manualIssues.length
      ? `<div class="validation-manual-tabs">
          <button class="validation-manual-tab${showResolved ? ""
          : " active"}" data-manual-view="open" type="button">Open (${openManual.length})</button>
          <button class="validation-manual-tab${showResolved ? " active"
          : ""}" data-manual-view="resolved" type="button">Resolved (${resolvedManual.length})</button>
        </div>`
      : "";
  const empty = !visibleIssues.length
      ? `<div class="validation-issue-message">${showResolved
          ? "No resolved manual tasks." : "No open issues."}</div>`
      : `<ul class="validation-issue-list">${rendered}</ul>`;
  el.validationDrawerIssues.innerHTML = `${tabs}${empty}`;
}

function renderValidationCenter() {
  if (!el.validationFab) {
    return;
  }
  const issues = state.validation.issues || [];
  const status = statusFromIssues(issues);
  el.validationFab.classList.remove("validation-state-ok",
      "validation-state-warning", "validation-state-error");
  el.validationFab.classList.add(status.stateClass);
  if (el.validationFabText) {
    el.validationFabText.textContent = status.label;
  }
  el.validationFab.classList.toggle("validation-is-running",
      !!state.validation.inProgress);
  el.validationFabProgress?.classList.toggle("hidden",
      !state.validation.inProgress);
  if (el.validationDrawerMeta) {
    const ts = state.validation.lastValidatedAt
        ? new Date(state.validation.lastValidatedAt).toLocaleTimeString()
        : "Not validated";
    el.validationDrawerMeta.textContent = `${status.meta} Last check: ${ts}`;
  }
  el.validationDrawer?.classList.toggle("validation-is-running",
      !!state.validation.inProgress);
  el.validationDrawerProgress?.classList.toggle("hidden",
      !state.validation.inProgress);
  renderIssues(issues);
  el.validationDrawer?.classList.toggle("hidden", !state.validation.panelOpen);
}

export function isMethodologyValidationError(error) {
  return Boolean(
      error
      && error.status === 400
      && Array.isArray(error.issues)
      && error.issues.length > 0
  );
}

export function applyValidationIssues(issues, {openOnFirst = false} = {}) {
  state.validation.issues = Array.isArray(issues) ? issues : [];
  state.validation.lastValidatedAt = Date.now();
  if (state.validation.issues.length && openOnFirst
      && !state.validation.firstIssueShown) {
    state.validation.panelOpen = true;
    state.validation.firstIssueShown = true;
  }
  if (!state.validation.issues.length) {
    state.validation.firstIssueShown = false;
  }
  renderValidationCenter();
}

export function clearValidationIssues({keepPanelState = true} = {}) {
  state.validation.issues = [];
  state.validation.lastValidatedAt = Date.now();
  state.validation.manualView = "open";
  if (!keepPanelState) {
    state.validation.panelOpen = false;
  }
  state.validation.firstIssueShown = false;
  renderValidationCenter();
}

export function setValidationInProgress(inProgress) {
  state.validation.inProgress = Boolean(inProgress);
  renderValidationCenter();
}

export function toggleValidationDrawer(forceOpen) {
  if (!el.validationDrawer) {
    return;
  }
  if (typeof forceOpen === "boolean") {
    state.validation.panelOpen = forceOpen;
  } else {
    state.validation.panelOpen = !state.validation.panelOpen;
  }
  renderValidationCenter();
}

export function bindValidationCenterUi() {
  if (!el.validationFab || !el.validationDrawerCloseBtn) {
    return;
  }
  el.validationFab.addEventListener("click", () => toggleValidationDrawer());
  el.validationDrawerCloseBtn.addEventListener("click",
      () => toggleValidationDrawer(false));
  el.validationDrawerIssues?.addEventListener("click", (event) => {
    const target = event.target instanceof HTMLElement ? event.target : null;
    const tab = target?.closest("[data-manual-view]");
    if (tab) {
      state.validation.manualView = tab.getAttribute("data-manual-view")
          || "open";
      renderValidationCenter();
      return;
    }
    const button = target?.closest("[data-issue-locate]");
    if (!button) {
      return;
    }
    const id = button.getAttribute("data-issue-locate") || "";
    if (!id) {
      return;
    }
    window.dispatchEvent(new CustomEvent("func2:locate-issue-target", {
      detail: {
        id,
        elementName: button.getAttribute("data-issue-element-name") || "",
        elementType: button.getAttribute("data-issue-element-type") || ""
      }
    }));
  });
  el.validationDrawerIssues?.addEventListener("change", (event) => {
    const input = event.target instanceof HTMLInputElement ? event.target
        : null;
    if (!input || !input.matches("[data-manual-task-id]")) {
      return;
    }
    const manualTaskId = input.getAttribute("data-manual-task-id") || "";
    window.dispatchEvent(new CustomEvent("func2:manual-task-toggle", {
      detail: {manualTaskId, resolved: input.checked}
    }));
  });
  renderValidationCenter();
}
