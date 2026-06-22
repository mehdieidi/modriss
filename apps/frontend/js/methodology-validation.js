import { el } from "./dom.js";
import { state } from "./state.js";
import { syncMobileDockState } from "./mobile-ui.js";

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

function isManualIssue(issue) {
  return String(issue?.issueClass || "").startsWith("MANUAL_");
}

function issueTargetId(issue) {
  return String(
    issue?.elementId ||
      issue?.relationshipId ||
      issue?.targetElementId ||
      issue?.sourceElementId ||
      "",
  ).trim();
}

function manualRequirementLabel(issue) {
  const rawClass = String(issue?.issueClass || issue?.code || "").toUpperCase();
  if (rawClass.includes("MANUAL_REQUIRED")) {
    return "Mandatory";
  }
  if (rawClass.includes("MANUAL_OPTIONAL")) {
    return "Optional";
  }
  return severityOf(issue) === "ERROR" ? "Mandatory" : "Optional";
}

function preferredIssueView({ errors, warnings, openManual, closedManual }) {
  const current = state.validation.issueView || "errors";
  const counts = {
    errors: errors.length,
    warnings: warnings.length,
    "manual-open": openManual.length,
    "manual-closed": closedManual.length,
  };
  if (counts[current] > 0 || Object.prototype.hasOwnProperty.call(counts, current)) {
    return current;
  }
  return Object.entries(counts).find(([, count]) => count > 0)?.[0] || "errors";
}

function defaultIssueViewForIssues(issues) {
  const manualIssues = issues.filter(isManualIssue);
  const nonManual = issues.filter((issue) => !isManualIssue(issue));
  if (nonManual.some((issue) => severityOf(issue) === "ERROR")) {
    return "errors";
  }
  if (nonManual.some((issue) => severityOf(issue) === "WARNING")) {
    return "warnings";
  }
  if (manualIssues.some((issue) => !issue?.resolved)) {
    return "manual-open";
  }
  if (manualIssues.some((issue) => issue?.resolved)) {
    return "manual-closed";
  }
  return "errors";
}

function applyIssueView(issues) {
  state.validation.issueView = defaultIssueViewForIssues(issues);
}

function statusFromIssues(issues) {
  if (!issues.length) {
    return {
      stateClass: "validation-state-ok",
      label: "OK",
      meta: "No issues found.",
    };
  }
  const manualIssues = issues.filter(isManualIssue);
  const openManual = manualIssues.filter((issue) => !issue?.resolved);
  const nonManual = issues.filter((issue) => !isManualIssue(issue));
  const errorCount = nonManual.filter((issue) => severityOf(issue) === "ERROR").length;
  const warningCount = nonManual.filter((issue) => severityOf(issue) === "WARNING").length;
  if (errorCount) {
    return {
      stateClass: "validation-state-error",
      label: `Errors (${errorCount})`,
      meta: `${errorCount} error issue(s): fix required before generation.`,
    };
  }
  if (warningCount) {
    return {
      stateClass: "validation-state-warning",
      label: `Warnings (${warningCount})`,
      meta: `${warningCount} warning(s): review recommended.`,
    };
  }
  if (openManual.length) {
    return {
      stateClass: "validation-state-warning",
      label: `Manual Tasks (${openManual.length})`,
      meta: `${openManual.length} manual task(s): review before promotion.`,
    };
  }
  if (manualIssues.length) {
    return {
      stateClass: "validation-state-ok",
      label: `Manual Tasks (${manualIssues.length})`,
      meta: `${manualIssues.length} manual task(s) resolved.`,
    };
  }
  return {
    stateClass: "validation-state-ok",
    label: `Issues (${nonManual.length})`,
    meta: `${nonManual.length} informational issue(s).`,
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
  const manualIssues = issues.filter(isManualIssue);
  const openManual = manualIssues.filter((issue) => !issue?.resolved);
  const resolvedManual = manualIssues.filter((issue) => Boolean(issue?.resolved));
  const nonManual = issues.filter((issue) => !isManualIssue(issue));
  const openErrors = nonManual.filter((issue) => severityOf(issue) === "ERROR");
  const openWarnings = nonManual.filter((issue) => severityOf(issue) === "WARNING");
  const openOther = nonManual.filter((issue) => !["ERROR", "WARNING"].includes(severityOf(issue)));

  const renderIssue = (issue, sectionType = "validation") => {
    const rawClass = String(issue.issueClass || issue.code || "").toUpperCase();
    const manual = isManualIssue(issue);
    const issueClass = manual
      ? "Manual Task"
      : rawClass.includes("GENERATION") || rawClass.includes("SYSTEM")
        ? "System Error"
        : "Validation";
    const severity = severityOf(issue);
    const title = issue.constraint || issue.code || "Constraint";
    const message = issue.message || "Invalid model state.";
    const guide = issue.guidance || issue.suggestedFix || "Review and fix this element.";
    const targetId = issueTargetId(issue);
    const element = issue.elementName || targetId || "";
    const toggle = manual
      ? `<label class="validation-manual-toggle"><input data-manual-task-id="${escapeHtml(
          issue.manualTaskId || "",
        )}" type="checkbox" ${issue.resolved ? "checked" : ""}/> Resolve</label>`
      : "";
    const tags = [manual ? manualRequirementLabel(issue) : severity, issueClass];
    const itemClass = `validation-issue-item validation-issue-${escapeHtml(sectionType)}${
      issue.resolved ? " validation-issue-item-resolved" : ""
    }`;
    return `<li class="${itemClass}">
      <div class="validation-issue-head-row">
        <div class="validation-issue-head">${escapeHtml(title)}</div>
        <div class="validation-issue-tags">
          ${tags
            .map((tag) => `<span class="validation-issue-kind">${escapeHtml(tag)}</span>`)
            .join("")}
        </div>
        ${toggle}
        <button class="validation-issue-locate" ${targetId ? "" : "disabled"}
            data-issue-locate="${escapeHtml(targetId)}"
            data-issue-element-name="${escapeHtml(issue.elementName || "")}"
            data-issue-element-type="${escapeHtml(issue.elementType || "")}"
            type="button">Locate</button>
      </div>
      ${element ? `<div class="validation-issue-element">${escapeHtml(element)}</div>` : ""}
      <div class="validation-issue-message">${escapeHtml(message)}</div>
      <div class="validation-issue-guide">${escapeHtml(guide)}</div>
    </li>`;
  };
  const renderSection = (title, sectionIssues, sectionType) => {
    if (!sectionIssues.length) {
      return `<div class="validation-issue-message">No ${escapeHtml(title.toLowerCase())}.</div>`;
    }
    return `<section class="validation-issue-section validation-issue-section-${escapeHtml(
      sectionType,
    )}">
      <div class="validation-issue-section-head">
        <span>${escapeHtml(title)}</span>
        <span>${sectionIssues.length}</span>
      </div>
      <ul class="validation-issue-list">${sectionIssues
        .map((issue) => renderIssue(issue, sectionType))
        .join("")}</ul>
    </section>`;
  };
  const issueView = preferredIssueView({
    errors: openErrors,
    warnings: openWarnings,
    openManual,
    closedManual: resolvedManual,
  });
  state.validation.issueView = issueView;
  const tabs = [
    ["errors", "Errors", openErrors.length],
    ["warnings", "Warnings", openWarnings.length],
    ["manual-open", "Manual Tasks", openManual.length],
    ["manual-closed", "Closed Manual", resolvedManual.length],
  ]
    .map(
      ([id, label, count]) =>
        `<button class="validation-manual-tab${issueView === id ? " active" : ""}"
        data-issue-view="${escapeHtml(id)}" type="button">${escapeHtml(label)} (${count})</button>`,
    )
    .join("");
  const content =
    issueView === "warnings"
      ? renderSection("Warnings", openWarnings, "warning")
      : issueView === "manual-open"
        ? renderSection("Open Manual Tasks", openManual, "manual")
        : issueView === "manual-closed"
          ? renderSection("Closed Manual Tasks", resolvedManual, "manual")
          : [
              renderSection("Errors", openErrors, "error"),
              openOther.length ? renderSection("Other", openOther, "other") : "",
            ].join("");
  el.validationDrawerIssues.innerHTML = `<div class="validation-manual-tabs">${tabs}</div>${content}`;
}

function renderValidationCenter() {
  if (!el.validationFab) {
    return;
  }
  const validationVisible = state.activeType !== "artifact";
  el.validationFab.classList.toggle("hidden", !validationVisible);
  el.mobileDockValidationBtn?.classList.toggle("hidden", !validationVisible);
  if (!validationVisible) {
    el.validationDrawer?.classList.add("hidden");
    el.validationFabProgress?.classList.add("hidden");
    el.validationDrawerProgress?.classList.add("hidden");
    syncMobileDockState();
    return;
  }
  const issues = state.validation.issues || [];
  const status = statusFromIssues(issues);
  el.validationFab.classList.remove(
    "validation-state-ok",
    "validation-state-warning",
    "validation-state-error",
  );
  el.validationFab.classList.add(status.stateClass);
  el.mobileDockValidationBtn?.classList.remove(
    "validation-state-ok",
    "validation-state-warning",
    "validation-state-error",
  );
  el.mobileDockValidationBtn?.classList.add(status.stateClass);
  if (el.validationFabText) {
    el.validationFabText.textContent = status.label;
  }
  if (el.mobileDockValidationLabel) {
    let dockLabel = status.label;
    if (dockLabel.startsWith("Errors")) {
      dockLabel = dockLabel.replace("Errors", "Err");
    } else if (dockLabel.startsWith("Warnings")) {
      dockLabel = dockLabel.replace("Warnings", "Warn");
    } else if (dockLabel.startsWith("Manual Tasks")) {
      dockLabel = dockLabel.replace("Manual Tasks", "Tasks");
    }
    el.mobileDockValidationLabel.textContent = dockLabel;
  }
  el.validationFab.classList.toggle("validation-is-running", !!state.validation.inProgress);
  el.mobileDockValidationBtn?.classList.toggle(
    "validation-is-running",
    !!state.validation.inProgress,
  );
  el.validationFabProgress?.classList.toggle("hidden", !state.validation.inProgress);
  if (el.validationDrawerMeta) {
    const ts = state.validation.lastValidatedAt
      ? new Date(state.validation.lastValidatedAt).toLocaleTimeString()
      : "Not validated";
    el.validationDrawerMeta.textContent = `${status.meta} Last check: ${ts}`;
  }
  el.validationDrawer?.classList.toggle("validation-is-running", !!state.validation.inProgress);
  el.validationDrawerProgress?.classList.toggle("hidden", !state.validation.inProgress);
  renderIssues(issues);
  el.validationDrawer?.classList.toggle("hidden", !state.validation.panelOpen);
  syncMobileDockState();
}

export function isMethodologyValidationError(error) {
  return Boolean(
    error && error.status === 400 && Array.isArray(error.issues) && error.issues.length > 0,
  );
}

export function applyValidationIssues(issues, { openOnFirst = false } = {}) {
  state.validation.issues = Array.isArray(issues) ? issues : [];
  applyIssueView(state.validation.issues);
  state.validation.lastValidatedAt = Date.now();
  if (state.validation.issues.length && openOnFirst && !state.validation.firstIssueShown) {
    state.validation.panelOpen = true;
    state.validation.firstIssueShown = true;
  }
  if (!state.validation.issues.length) {
    state.validation.firstIssueShown = false;
  }
  renderValidationCenter();
}

export function clearValidationIssues({ keepPanelState = true } = {}) {
  state.validation.issues = [];
  state.validation.lastValidatedAt = Date.now();
  state.validation.issueView = "errors";
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
  el.validationDrawerCloseBtn.addEventListener("click", () => toggleValidationDrawer(false));
  el.validationDrawerIssues?.addEventListener("click", (event) => {
    const target = event.target instanceof HTMLElement ? event.target : null;
    const tab = target?.closest("[data-issue-view]");
    if (tab) {
      state.validation.issueView = tab.getAttribute("data-issue-view") || "errors";
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
    window.dispatchEvent(
      new CustomEvent("modless:locate-issue-target", {
        detail: {
          id,
          elementName: button.getAttribute("data-issue-element-name") || "",
          elementType: button.getAttribute("data-issue-element-type") || "",
        },
      }),
    );
  });
  el.validationDrawerIssues?.addEventListener("change", (event) => {
    const input = event.target instanceof HTMLInputElement ? event.target : null;
    if (!input || !input.matches("[data-manual-task-id]")) {
      return;
    }
    const manualTaskId = input.getAttribute("data-manual-task-id") || "";
    window.dispatchEvent(
      new CustomEvent("modless:manual-task-toggle", {
        detail: { manualTaskId, resolved: input.checked },
      }),
    );
  });
  renderValidationCenter();
}
