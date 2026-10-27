import { state } from "./state.js";
import { el } from "./dom.js";
import { api } from "./api.js";
import { setStatus } from "./status.js";
import { escapeHtml } from "./utils.js";
import { highlightImpactedNodes, locateCanvasTarget } from "./canvas.js";
import { loadModelById, switchTab } from "./model-ops.js";
import { closeAttributePanel } from "./attr-panel.js";
import { loadArtifactById, openArtifactFile } from "./artifact.js";
import { isMobileViewport } from "./responsive.js";
import { syncMobileDockState } from "./mobile-ui.js";
import { modelingImpactConfig, modelingLevelConfig } from "./modeling-config-data.js";
import { impactCounts, impactGroups, isImpactFile } from "./impact-data.js";

let requestVersion = 0;
let pendingRequest;
const PAGE_SIZE = 6;

function setImpactButtonState(active) {
  el.impactToggleBtn?.classList.toggle("active", active);
  el.impactToggleBtn?.setAttribute("aria-pressed", String(active));
  if (el.impactToggleBtn) {
    el.impactToggleBtn.title = active
      ? "Impact mode ON — select an element or file to follow its traces"
      : "Analyze transformation and generated-file traces";
  }
}

export function toggleImpactMode() {
  if (state.impactMode) {
    closeImpactPanel();
    return;
  }
  state.impactMode = true;
  setImpactButtonState(true);
  closeAttributePanel();
  openImpactPanel();
  renderImpactPanel(null);
  setStatus(
    "Impact mode ON. Select an element or file to follow its transformation and generated-file traces.",
  );
  if (state.activeType === "artifact" && state.artifact.activeFile) {
    return fetchImpact(state.artifact.activeFile);
  }
}

export function openImpactPanel() {
  el.modelTreePanel?.classList.add("hidden");
  el.attributePanel?.classList.add("hidden");
  el.impactPanel.classList.remove("hidden");
  el.workspace.classList.remove("views-open", "attr-open");
  el.workspace.classList.add("impact-open");
  if (isMobileViewport()) {
    el.workspace.classList.remove("mobile-left-open");
    el.workspace.classList.add("mobile-right-open");
    syncMobileDockState();
  }
}

export function closeImpactPanel() {
  requestVersion += 1;
  pendingRequest?.abort();
  state.impactMode = false;
  state.impactData = null;
  setImpactButtonState(false);
  el.impactPanel.classList.add("hidden");
  el.workspace.classList.remove("impact-open", "mobile-right-open");
  syncMobileDockState();
  highlightImpactedNodes();
  renderImpactPanel(null);
}

function expandEndpoint(template, values, fallback) {
  return String(template || fallback).replace(/\{([A-Za-z0-9_]+)\}/g, (_match, key) =>
    encodeURIComponent(String(values[key] ?? "")),
  );
}

function artifactImpactData(chain, artifactId, filePath) {
  return {
    focalElement: {
      modelId: artifactId,
      modelType: "ARTIFACT",
      modelName: chain.modelName,
      elementId: filePath,
      elementName: filePath || chain.modelName,
      elementType: filePath ? "Generated File" : "Artifact",
    },
    upstream: chain.upstream || [],
    downstream: [],
    traceEdges: chain.traceEdges || [],
    notices: chain.notices || [],
  };
}

export async function fetchImpact(elementId) {
  if (!state.impactMode) return;
  const isArtifact = state.activeType === "artifact";
  const modelId = isArtifact ? state.artifact.id : state.modelId;
  if (!modelId) {
    setStatus(isArtifact ? "Load generated artifacts first" : "Save or load a model first");
    return;
  }
  pendingRequest?.abort();
  const controller = new AbortController();
  pendingRequest = controller;
  const version = ++requestVersion;
  const activeType = state.activeType;
  const projectId = state.project?.id;
  const providerId = state.cloudProvider?.id;
  const current = () =>
    version === requestVersion &&
    state.impactMode &&
    (isArtifact ? state.artifact.id : state.modelId) === modelId &&
    state.activeType === activeType &&
    state.project?.id === projectId &&
    state.cloudProvider?.id === providerId;
  state.impactData = null;
  highlightImpactedNodes();
  openImpactPanel();
  el.impactPanelBody.setAttribute("aria-busy", "true");
  el.impactPanelBody.innerHTML =
    '<div class="impact-placeholder" role="status">Following transformation and generated-file traces…</div>';
  setStatus("Analysing impact…");
  try {
    const endpoint = isArtifact
      ? expandEndpoint(
          modelingImpactConfig().artifactImpactEndpoint,
          { artifactId: modelId },
          "/impact/artifact/{artifactId}",
        ) + `?filePath=${encodeURIComponent(elementId)}`
      : expandEndpoint(
          modelingImpactConfig().elementImpactEndpoint,
          { level: modelingLevelConfig(activeType).apiType || activeType, modelId, elementId },
          "/impact/{level}/{modelId}/element/{elementId}",
        );
    const response = await api(endpoint, { signal: controller.signal });
    if (!current()) return;
    const data = isArtifact ? artifactImpactData(response, modelId, elementId) : response;
    state.impactData = data;
    renderImpactPanel(data);
    highlightImpactedNodes();
    setStatus(`Impact analysis complete for ${data.focalElement?.elementName || elementId}`);
  } catch (error) {
    if (!current() || controller.signal.aborted) return;
    el.impactPanelBody.innerHTML = `<div class="impact-placeholder" role="alert">${escapeHtml(error.message || String(error))}<br><button class="impact-retry">Try again</button></div>`;
    el.impactPanelBody
      .querySelector(".impact-retry")
      ?.addEventListener("click", () => fetchImpact(elementId));
    setStatus(error, { prefix: "Impact analysis failed.", error: true });
  } finally {
    if (version === requestVersion) el.impactPanelBody.removeAttribute("aria-busy");
  }
}

function renderImpactPanel(data) {
  el.impactPanelBody.removeAttribute("aria-busy");
  if (!data) {
    el.impactPanelBody.innerHTML =
      state.activeType === "artifact"
        ? '<div class="impact-placeholder"><strong>Follow a file’s trace</strong><p>Select a file in the explorer to see the model elements and transformation rules that contributed to it.</p></div>'
        : '<div class="impact-placeholder"><strong>Follow an element’s trace</strong><p>Select an element on the canvas to see what it was derived from, what it generated, and which files it contributed to.</p></div>';
    return;
  }
  const focal = data.focalElement || {};
  const counts = impactCounts(data);
  el.impactPanelBody.innerHTML = `
    <div class="impact-focal-card">
      <div class="impact-focal-label">${isImpactFile(focal) ? "Selected artifact" : "Selected element"}</div>
      <div class="impact-focal-name">${escapeHtml(focal.elementName || focal.elementId || "")}</div>
      <div class="impact-focal-meta"><span class="tier-badge ${isImpactFile(focal) ? "tier-badge-artifact" : "tier-badge-model"}">${escapeHtml(focal.modelType || "")}</span> ${escapeHtml(focal.elementType || "")}<br>${escapeHtml(focal.modelName || "")}</div>
    </div>
    <div class="impact-stats" aria-label="Unique traced results">
      <button data-impact-jump="upstream" ${counts.upstream ? "" : "disabled"} aria-label="Show ${counts.upstream} upstream elements"><b>${counts.upstream}</b> upstream</button>
      <button data-impact-jump="downstream" ${counts.downstream ? "" : "disabled"} aria-label="Show ${counts.downstream} downstream elements"><b>${counts.downstream}</b> downstream</button>
      <button data-impact-jump="files" ${counts.files ? "" : "disabled"} aria-label="Show ${counts.files} generated files"><b>${counts.files}</b> files</button>
    </div>
    <p class="impact-explanation">Traces from saved models and generated files. Each result appears once; expand its trace links to see the source and rule.</p>
    ${(data.notices || []).map((notice) => `<p class="impact-notice" role="status">${escapeHtml(notice)}</p>`).join("")}
    <label class="impact-search-label">Filter results<input class="impact-search" type="search" placeholder="Name, file path, type or rule…" aria-label="Filter impact results"></label>
    <div class="impact-results"></div>`;
  const results = el.impactPanelBody.querySelector(".impact-results");
  const renderResults = (query = "") => {
    results.innerHTML =
      renderDirection(data, "upstream", query) + renderDirection(data, "downstream", query);
    wireActions(results, data);
    results.querySelectorAll("[data-impact-more]").forEach((button) => {
      button.addEventListener("click", () => {
        const list = button.previousElementSibling;
        const hidden = [...list.querySelectorAll(":scope > [hidden]")];
        hidden.slice(0, PAGE_SIZE).forEach((row) => {
          row.hidden = false;
        });
        const remaining = Math.max(0, hidden.length - PAGE_SIZE);
        button.hidden = remaining === 0;
        button.textContent = `Show more (${remaining} remaining)`;
      });
    });
  };
  renderResults();
  el.impactPanelBody
    .querySelector(".impact-search")
    .addEventListener("input", (event) => renderResults(event.target.value));
  el.impactPanelBody.querySelectorAll("[data-impact-jump]").forEach((button) => {
    button.addEventListener("click", () => {
      el.impactPanelBody.querySelector(".impact-search").value = "";
      renderResults();
      const selector =
        button.dataset.impactJump === "files"
          ? '[data-impact-files="true"]'
          : `[data-impact-direction="${button.dataset.impactJump}"]`;
      results.querySelector(selector)?.scrollIntoView({ block: "start", behavior: "smooth" });
    });
  });
}

function renderDirection(data, direction, query) {
  const groups = impactGroups(data, direction, query);
  const count = groups.reduce((total, group) => total + group.items.length, 0);
  const title = direction === "upstream" ? "↑ Derived from" : "↓ Contributes to";
  const empty = query.trim()
    ? "No matching traces."
    : direction === "upstream"
      ? "No recorded upstream traces for this selection."
      : "No recorded downstream traces or generated files for this selection.";
  return `<section class="impact-direction" data-impact-direction="${direction}" aria-label="${direction} traces">
    <h3>${title}<span class="impact-section-count">${count}</span></h3>
    ${count ? groups.map((group) => renderGroup(group, direction)).join("") : `<p class="impact-empty">${empty}</p>`}
  </section>`;
}

function renderGroup(group, direction) {
  const count = group.items.length;
  const unit = group.files
    ? count === 1
      ? "file"
      : "files"
    : count === 1
      ? "element"
      : "elements";
  return `<details class="impact-group" data-impact-files="${group.files}" open>
    <summary><span class="tier-badge ${group.files ? "tier-badge-artifact" : "tier-badge-model"}">${escapeHtml(group.modelType)}</span><span class="impact-group-name">${escapeHtml(group.modelName)}</span><span class="impact-section-count">${count} ${unit}</span></summary>
    <div class="impact-group-list">${group.items.map(({ item, evidence }, index) => renderItem(item, evidence, direction, index >= PAGE_SIZE)).join("")}</div>
    ${count > PAGE_SIZE ? `<button class="impact-more" data-impact-more>Show more (${count - PAGE_SIZE} remaining)</button>` : ""}
  </details>`;
}

function actionAttributes(item) {
  return `data-impact-action="${isImpactFile(item) ? "file" : "model"}" data-model-id="${escapeHtml(item.modelId)}" data-model-type="${escapeHtml(String(item.modelType).toLowerCase())}" data-element-id="${escapeHtml(item.elementId || "")}"`;
}

function renderItem(item, evidence, direction, hidden) {
  const name = item.elementName || item.elementId;
  const sourceLabel = direction === "upstream" ? "Leads to" : "From";
  return `<article class="impact-result-card" ${hidden ? "hidden" : ""}>
    <div class="impact-result-heading"><span class="impact-result-type">${escapeHtml(item.elementType)}</span><button class="impact-tree-action" ${actionAttributes(item)} aria-label="${isImpactFile(item) ? "Open file" : "Locate"} ${escapeHtml(name)}">${isImpactFile(item) ? "Open file ↗" : "Locate ↗"}</button></div>
    <div class="impact-result-name ${isImpactFile(item) ? "impact-file-path" : ""}">${escapeHtml(name)}</div>
    ${evidence.length ? `<details class="impact-evidence"><summary>${evidence.length === 1 ? `${sourceLabel} ${escapeHtml(evidence[0].peer?.elementName || "recorded source")}` : `${evidence.length} trace links`}</summary><ul>${evidence.map(({ edge, peer }) => `<li><span>${sourceLabel} ${peer ? `<button class="impact-source-link" ${actionAttributes(peer)}>${escapeHtml(peer.elementName)}</button>` : escapeHtml(direction === "upstream" ? edge.targetElementId : edge.sourceElementId)}</span><code>${escapeHtml(edge.transformationRule || (edge.relationship === "GENERATED_ARTIFACT" ? "Code generation" : edge.relationship))}</code></li>`).join("")}</ul></details>` : ""}
  </article>`;
}

function restoreImpact(data) {
  state.impactData = data;
  state.impactMode = true;
  closeAttributePanel();
  setImpactButtonState(true);
  openImpactPanel();
  renderImpactPanel(data);
  highlightImpactedNodes();
}

function wireActions(root, data) {
  let navigating = false;
  root.querySelectorAll("[data-impact-action]").forEach((button) => {
    button.addEventListener("click", async () => {
      if (navigating) return;
      navigating = true;
      const actions = [...root.querySelectorAll("[data-impact-action]")];
      actions.forEach((action) => {
        action.disabled = true;
      });
      const originalLabel = button.textContent;
      button.textContent = "Opening…";
      const modelId = button.dataset.modelId;
      const elementId = button.dataset.elementId;
      const projectId = state.project?.id;
      const version = ++requestVersion;
      pendingRequest?.abort();
      const current = () =>
        version === requestVersion && state.impactMode && state.project?.id === projectId;
      try {
        if (button.dataset.impactAction === "model") {
          await loadModelById(button.dataset.modelType, modelId, { preserveImpact: true });
          if (!current()) return;
          const target = await locateCanvasTarget({ id: elementId });
          if (!current()) return;
          restoreImpact(data);
          setStatus(
            target
              ? `Located ${button.dataset.modelType.toUpperCase()} element. Trace analysis remains on the original selection.`
              : "Model opened. This element has no canvas representation; its trace remains available in the panel.",
          );
        } else {
          const chain = await api(
            expandEndpoint(
              modelingImpactConfig().artifactImpactEndpoint,
              { artifactId: modelId },
              "/impact/artifact/{artifactId}",
            ) + (elementId ? `?filePath=${encodeURIComponent(elementId)}` : ""),
          );
          if (!current()) return;
          await switchTab("artifact", { preserveImpact: true });
          if (!current()) return;
          await loadArtifactById(modelId);
          if (!current()) return;
          if (elementId) await openArtifactFile(elementId, { preserveImpact: true });
          if (!current()) return;
          restoreImpact(artifactImpactData(chain, modelId, elementId));
          setStatus(`Opened ${elementId || chain.modelName} with its recorded source traces`);
        }
      } catch (error) {
        setStatus(error, { prefix: "Impact navigation failed.", error: true });
      } finally {
        actions.forEach((action) => {
          action.disabled = false;
        });
        button.textContent = originalLabel;
        navigating = false;
      }
    });
  });
}
