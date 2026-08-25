import { state } from "./state.js";
import { el } from "./dom.js";
import { api, isPlannedFeatureError } from "./api.js";
import { setStatus } from "./status.js";
import { escapeHtml } from "./utils.js";
import { highlightImpactedNodes, scrollToNodeAndHighlight } from "./canvas.js";
import { loadModelById, switchTab } from "./model-ops.js";
import { closeAttributePanel } from "./attr-panel.js";
import { loadArtifactById, openArtifactFile } from "./artifact.js";
import { isMobileViewport } from "./responsive.js";
import { syncMobileDockState } from "./mobile-ui.js";
import {
  modelingImpactConfig,
  modelingLevelConfig,
  modelingLevelKeys,
  isArtifactLevel,
  modelingArtifactKey,
} from "./modeling-config-data.js";

function setImpactButtonState(active) {
  if (!el.impactToggleBtn) {
    return;
  }
  el.impactToggleBtn.classList.toggle("active", !!active);
  const label = el.impactToggleBtn.querySelector(".topbar-btn-label");
  if (label) {
    label.textContent = "Impact";
  } else {
    el.impactToggleBtn.textContent = "Impact";
  }
  el.impactToggleBtn.title = active
    ? "Impact mode ON - click any element to analyze its impact"
    : "Toggle change impact analysis mode";
}

// ── Toggle impact mode ────────────────────────────────────────────────────────

export function toggleImpactMode() {
  state.impactMode = !state.impactMode;
  setImpactButtonState(state.impactMode); /*
      ? 'Impact mode ON — click any element to analyse its impact'
      : 'Toggle Change Impact Analysis mode'; */

  if (!state.impactMode) {
    closeImpactPanel();
  } else {
    closeAttributePanel();
    openImpactPanel();
    setStatus("Impact mode ON — click any element to see its change impact");
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
  state.impactMode = false;
  state.impactData = null;
  setImpactButtonState(false);
  el.impactPanel.classList.add("hidden");
  el.workspace.classList.remove("impact-open");
  el.workspace.classList.remove("mobile-right-open");
  syncMobileDockState();
  highlightImpactedNodes();
  renderImpactPanel(null);
}

// ── Fetch impact data ─────────────────────────────────────────────────────────

export async function fetchImpact(elementId) {
  if (!state.modelId) {
    setStatus("Save or load a model first");
    return;
  }
  setStatus("Analysing impact…");
  try {
    const typeKey = modelingLevelConfig(state.activeType).apiType || state.activeType;
    const endpoint = expandImpactEndpoint(
      modelingImpactConfig().elementImpactEndpoint,
      {
        level: typeKey,
        modelId: state.modelId,
        elementId,
      },
      "/impact/{level}/{modelId}/element/{elementId}",
    );
    const data = await api(endpoint);
    const enriched = await enrichImpactWithArtifactFiles(data);
    state.impactData = enriched;
    renderImpactPanel(enriched);
    highlightImpactedNodes();
    setStatus(`Impact analysis complete for ${enriched.focalElement?.elementName || elementId}`);
  } catch (error) {
    if (isPlannedFeatureError(error)) {
      setStatus("Impact analysis is not available in this backend build.");
      renderImpactPanel(null);
      return;
    }
    setStatus(error, { prefix: "Impact analysis failed.", error: true });
    renderImpactPanel(null);
  }
}

async function enrichImpactWithArtifactFiles(data) {
  const downstream = Array.isArray(data?.downstream) ? data.downstream : [];
  const focalElementId = String(data?.focalElement?.elementId || "").trim();
  const artifacts = downstream.filter((item) => {
    const modelId = item?.modelId;
    return isArtifactLevel(item?.modelType) && modelId != null && String(modelId).trim() !== "";
  });

  const pairs = await Promise.all(
    artifacts.map(async (artifact) => {
      const id = String(artifact.modelId);
      try {
        const record = await api(`/artifact/${id}`);
        const filesMap = record?.files || record?.modelJson?.files || {};
        const allFiles = Object.keys(filesMap)
          .map((path) => String(path || "").trim())
          .filter(Boolean);
        const traceability = record?.modelJson?.traceability || {};
        const traceElementId = String(artifact.elementId || focalElementId || "").trim();
        const tracedFiles = traceElementId ? extractTracedFiles(traceability, traceElementId) : [];

        const sorted = (tracedFiles.length > 0 ? tracedFiles : allFiles).sort((a, b) =>
          a.localeCompare(b),
        );
        return [id, sorted];
      } catch {
        return [id, []];
      }
    }),
  );

  return { ...data, artifactFilesById: Object.fromEntries(pairs) };
}

function extractTracedFiles(traceability, elementId) {
  if (!traceability || typeof traceability !== "object" || !elementId) {
    return [];
  }
  const direct = Array.isArray(traceability[elementId]) ? traceability[elementId] : [];
  return direct.map((path) => String(path || "").trim()).filter(Boolean);
}

function expandImpactEndpoint(template, values, fallback) {
  const source = String(template || fallback || "");
  return source.replace(/\{([A-Za-z0-9_]+)\}/g, (_match, key) =>
    encodeURIComponent(String(values?.[key] ?? "")),
  );
}

function levelConfigSafe(levelKey) {
  try {
    return modelingLevelConfig(levelKey);
  } catch {
    return null;
  }
}

function ancestorForLevel(ancestors, levelKey) {
  const level = levelConfigSafe(levelKey);
  const selectors = [levelKey, level?.apiType, level?.chatType, level?.displayName]
    .map((value) => String(value || "").toLowerCase())
    .filter(Boolean);
  return (ancestors || []).find((ancestor) =>
    selectors.includes(String(ancestor?.type || "").toLowerCase()),
  );
}

function configuredLevelForAncestor(ancestor) {
  const type = String(ancestor?.type || "").toLowerCase();
  if (!type) {
    return "";
  }
  return (
    modelingLevelKeys().find((levelKey) => {
      const level = levelConfigSafe(levelKey);
      return [levelKey, level?.apiType, level?.chatType, level?.displayName]
        .map((value) => String(value || "").toLowerCase())
        .includes(type);
    }) || ""
  );
}

function tierBadgeClass(modelType) {
  return isArtifactLevel(modelType) ? "tier-badge-artifact" : "tier-badge-model";
}

// ── Render impact panel ───────────────────────────────────────────────────────

function renderImpactPanel(data) {
  if (!data) {
    el.impactPanelBody.innerHTML =
      '<div class="impact-placeholder"><span>Click an element on the canvas while Impact mode is active to see its change impact.</span></div>';
    return;
  }

  const focal = data.focalElement || {};
  const upstreamPath = buildUpstreamPath(data);
  const downstreamTree = buildDownstreamTree(data);
  const connected = data.connectedElements || [];

  const html = [];

  html.push(`<div class="impact-focal-card">
    <div class="impact-focal-label">Selected Element</div>
    <div class="impact-focal-name">${escapeHtml(focal.elementName || focal.elementId || "")}</div>
    <div class="impact-focal-meta">
      <span class="tier-badge ${tierBadgeClass(focal.modelType)}">${escapeHtml(
        focal.modelType || "",
      )}</span>
      &nbsp;${escapeHtml(focal.elementType || "")}
      <br><span style="opacity:0.7">${escapeHtml(focal.modelName || "")}</span>
    </div>
  </div>`);

  html.push(buildUpstreamTreeHtml(upstreamPath, focal));
  html.push(buildDownstreamTreeHtml(downstreamTree, data.artifactFilesById || {}));
  html.push(buildConnectedSectionHtml(connected));

  el.impactPanelBody.innerHTML = html.join("");

  wireActionButtons();
  wireSectionToggles();
}

function buildUpstreamPath(data) {
  const upstream = Array.isArray(data?.upstream) ? data.upstream : [];
  const modelIds = upstream
    .map((item) => item?.modelId)
    .filter(Boolean)
    .map(String);
  const hasMultipleElementsPerModel = new Set(modelIds).size !== modelIds.length;
  if (hasMultipleElementsPerModel) {
    return [...upstream].reverse();
  }
  const byModelId = new Map(upstream.filter((i) => i.modelId).map((i) => [String(i.modelId), i]));

  const chain = [];
  const visited = new Set();
  let cursor = data?.focalElement?.sourceModelId ? String(data.focalElement.sourceModelId) : null;

  while (cursor && !visited.has(cursor)) {
    visited.add(cursor);
    const item = byModelId.get(cursor);
    if (!item) {
      break;
    }
    chain.push(item);
    cursor = item.sourceModelId ? String(item.sourceModelId) : null;
  }

  if (chain.length > 0) {
    return chain.reverse();
  }
  return [...upstream].reverse();
}

function buildDownstreamTree(data) {
  const focalModelId = data?.focalElement?.modelId ? String(data.focalElement.modelId) : null;
  if (!focalModelId) {
    return [];
  }

  const downstream = Array.isArray(data?.downstream) ? data.downstream : [];
  const byParent = new Map();
  downstream.forEach((item) => {
    if (!item?.sourceModelId) {
      return;
    }
    const parentId = String(item.sourceModelId);
    if (!byParent.has(parentId)) {
      byParent.set(parentId, []);
    }
    byParent.get(parentId).push(item);
  });

  function buildChildren(parentId, visited) {
    const children = byParent.get(parentId) || [];
    return children
      .sort(
        (a, b) =>
          String(a.modelType || "").localeCompare(String(b.modelType || "")) ||
          String(a.modelName || "").localeCompare(String(b.modelName || "")),
      )
      .map((item, index) => {
        const fallback = item.elementName || item.elementId || `unknown-${index}`;
        const id = item.modelId ? String(item.modelId) : `${parentId}:${fallback}`;
        const nextVisited = new Set(visited);
        if (item.modelId) {
          nextVisited.add(id);
        }
        return {
          item,
          children: item.modelId && !visited.has(id) ? buildChildren(id, nextVisited) : [],
        };
      });
  }

  return buildChildren(focalModelId, new Set([focalModelId]));
}

function buildUpstreamTreeHtml(path, focal) {
  const count = path.length;
  const isOpen = true;

  const nodes = [
    ...path.map((item) => buildTreeNodeRow(item, { direction: "upstream" })),
    buildTreeNodeRow(focal, { direction: "focal", isFocal: true }),
  ];

  return `<div class="impact-section ${isOpen ? "open" : ""}">
    <div class="impact-section-header" title="All upstream chain nodes that lead to the selected element">
      <span class="impact-section-icon">▶</span>
      <span class="impact-section-title">⬆ Upstream Chain</span>
      <span class="impact-section-count">${count}</span>
    </div>
    <div class="impact-section-body">
      <div class="impact-tree">${nodes.join('<div class="impact-tree-link"></div>')}</div>
    </div>
  </div>`;
}

function buildDownstreamTreeHtml(tree, artifactFilesById) {
  const count = countTreeNodes(tree);
  const isOpen = true;

  if (count === 0) {
    return `<div class="impact-section ${isOpen ? "open" : ""}">
      <div class="impact-section-header" title="All downstream impacted nodes and generated files">
        <span class="impact-section-icon">▶</span>
        <span class="impact-section-title">⬇ Downstream Impact Tree</span>
        <span class="impact-section-count">0</span>
      </div>
      <div class="impact-section-body">
        <div class="impact-empty">No downstream elements found in the transformation chain.</div>
      </div>
    </div>`;
  }

  return `<div class="impact-section ${isOpen ? "open" : ""}">
    <div class="impact-section-header" title="All downstream impacted nodes and generated files">
      <span class="impact-section-icon">▶</span>
      <span class="impact-section-title">⬇ Downstream Impact Tree</span>
      <span class="impact-section-count">${count}</span>
    </div>
    <div class="impact-section-body">
      <div class="impact-tree impact-tree-branching">${buildDownstreamBranchHtml(
        tree,
        artifactFilesById,
      )}</div>
    </div>
  </div>`;
}

function buildDownstreamBranchHtml(nodes, artifactFilesById) {
  return nodes
    .map((node) => {
      const item = node.item;
      const modelType = String(item.modelType || "").toLowerCase();
      const files = isArtifactLevel(modelType) ? artifactFilesById[String(item.modelId)] || [] : [];

      const row = buildTreeNodeRow(item, { direction: "downstream" });

      const fileRows = files
        .map((path) => {
          const artifactId = escapeHtml(String(item.modelId || ""));
          const safePath = escapeHtml(path);
          return `<div class="impact-tree-file-row">
      <span class="impact-tree-file-dot"></span>
      <button
        class="impact-tree-file-btn"
        data-impact-action="open-artifact-file"
        data-artifact-id="${artifactId}"
        data-file-path="${safePath}"
        title="Open ${safePath} and show file impact chain"
      >📄 ${safePath}</button>
    </div>`;
        })
        .join("");

      const children = node.children?.length
        ? `<div class="impact-tree-children">${buildDownstreamBranchHtml(
            node.children,
            artifactFilesById,
          )}</div>`
        : "";
      const fileTree = fileRows
        ? `<div class="impact-tree-children impact-tree-files">${fileRows}</div>`
        : "";

      return `<div class="impact-tree-branch-node">${row}${fileTree}${children}</div>`;
    })
    .join("");
}

function buildConnectedSectionHtml(items) {
  const isOpen = items.length > 0;
  const body = items.length
    ? items.map((item) => buildTreeNodeRow(item, { direction: "connected" })).join("")
    : '<div class="impact-empty">No same-model connected peers found.</div>';

  return `<div class="impact-section ${isOpen ? "open" : ""}">
    <div class="impact-section-header" title="Same-model neighbors connected by relations/connectors">
      <span class="impact-section-icon">▶</span>
      <span class="impact-section-title">↔ Connected Peers</span>
      <span class="impact-section-count">${items.length}</span>
    </div>
    <div class="impact-section-body" style="${isOpen ? "" : "display:none"}">${body}</div>
  </div>`;
}

function buildTreeNodeRow(item, { direction, isFocal = false } = {}) {
  const modelType = String(item?.modelType || "").toLowerCase();
  const elementName = item?.elementName || item?.elementId || item?.modelName || "Unnamed";
  const elementType =
    item?.elementType || (isArtifactLevel(modelType) ? "Generated Artifact" : "Model Element");
  const canNavigateModel = !!item?.modelId && !isArtifactLevel(modelType);
  const canOpenArtifact = !!item?.modelId && isArtifactLevel(modelType);

  const action = canNavigateModel
    ? `<button class="impact-tree-action" data-impact-action="open-model" data-model-id="${escapeHtml(
        String(item.modelId),
      )}" data-model-type="${escapeHtml(modelType)}" data-element-id="${escapeHtml(
        item?.elementId || "",
      )}" title="Load model and locate element">→ navigate</button>`
    : canOpenArtifact
      ? `<button class="impact-tree-action" data-impact-action="open-artifact" data-artifact-id="${escapeHtml(
          String(item.modelId),
        )}" title="Open artifact explorer">→ open</button>`
      : "";

  return `<div class="impact-tree-node ${
    isFocal ? "impact-tree-node-focal" : ""
  } impact-tree-node-${escapeHtml(direction || "node")}">
    <div class="impact-tree-node-main">
      <span class="tier-badge ${tierBadgeClass(modelType)}">${escapeHtml(
        item?.modelType || "",
      )}</span>
      <div class="impact-tree-node-text">
        <div class="impact-tree-node-title" title="${escapeHtml(
          elementName,
        )}">${escapeHtml(elementName)}</div>
        <div class="impact-tree-node-meta" title="${escapeHtml(
          item?.modelName || "",
        )}">${escapeHtml(elementType)} · ${escapeHtml(item?.modelName || "")}</div>
      </div>
    </div>
    ${action}
  </div>`;
}

function countTreeNodes(nodes) {
  return nodes.reduce((acc, node) => acc + 1 + countTreeNodes(node.children || []), 0);
}

function wireSectionToggles() {
  el.impactPanelBody.querySelectorAll(".impact-section-header").forEach((header) => {
    header.addEventListener("click", () => {
      const section = header.closest(".impact-section");
      const body = section.querySelector(".impact-section-body");
      const isOpen = section.classList.toggle("open");
      body.style.display = isOpen ? "" : "none";
    });
  });
}

function wireActionButtons() {
  el.impactPanelBody.querySelectorAll("[data-impact-action]").forEach((btn) => {
    btn.addEventListener("click", async (e) => {
      e.stopPropagation();
      const action = btn.dataset.impactAction;

      if (action === "open-model") {
        const modelId = btn.dataset.modelId;
        const modelType = btn.dataset.modelType;
        const elementId = btn.dataset.elementId;
        if (!modelId || !modelType) {
          return;
        }
        try {
          await loadModelById(modelType.toLowerCase(), modelId);
          if (elementId) {
            await new Promise((resolve) => setTimeout(resolve, 120));
            scrollToNodeAndHighlight(elementId);
          }
          setStatus(`Navigated to ${modelType.toUpperCase()} model`);
        } catch (error) {
          setStatus(error, { prefix: "Navigation failed.", error: true });
        }
        return;
      }

      if (action === "open-artifact") {
        const artifactId = btn.dataset.artifactId;
        if (!artifactId) {
          return;
        }
        try {
          const artifactImpact = await buildArtifactImpact(artifactId);
          await switchTab("artifact");
          await loadArtifactById(artifactId);
          state.impactData = artifactImpact;
          state.impactMode = true;
          setImpactButtonState(true);
          openImpactPanel();
          renderImpactPanel(artifactImpact);
          setStatus("Artifact opened with upstream impact lineage");
        } catch (error) {
          setStatus(error, { prefix: "Artifact navigation failed.", error: true });
        }
        return;
      }

      if (action === "open-artifact-file") {
        const artifactId = btn.dataset.artifactId;
        const filePath = btn.dataset.filePath;
        if (!artifactId || !filePath) {
          return;
        }
        try {
          const fileImpact = await buildArtifactImpact(artifactId, filePath);
          await switchTab("artifact");
          await loadArtifactById(artifactId);
          await openArtifactFile(filePath);
          state.impactData = fileImpact;
          state.impactMode = true;
          setImpactButtonState(true);
          openImpactPanel();
          renderImpactPanel(fileImpact);
          setStatus(`Opened ${filePath} with impact lineage`);
        } catch (error) {
          setStatus(error, { prefix: "File impact navigation failed.", error: true });
        }
      }
    });
  });
}

async function buildArtifactImpact(artifactId, filePath = null) {
  let chain;
  try {
    chain = await api(
      expandImpactEndpoint(
        modelingImpactConfig().artifactImpactEndpoint,
        { artifactId },
        "/impact/artifact/{artifactId}",
      ),
    );
  } catch (error) {
    if (isPlannedFeatureError(error)) {
      throw new Error("Impact analysis is not available in this backend build.");
    }
    throw new Error(`Failed to load artifact impact data: ${error.message}`);
  }

  const ancestors = Array.isArray(chain?.ancestors) ? chain.ancestors : [];
  const lineageLevel = modelingImpactConfig().artifactLineage?.upstreamLevel || "";
  const lineageAncestor =
    ancestorForLevel(ancestors, lineageLevel) ||
    [...ancestors].reverse().find((ancestor) => configuredLevelForAncestor(ancestor));
  const lineage =
    filePath && lineageAncestor
      ? await resolveArtifactFileLineage(
          artifactId,
          filePath,
          lineageAncestor.id,
          configuredLevelForAncestor(lineageAncestor),
        )
      : null;

  const focal = {
    modelId: chain.modelId,
    modelType: chain.modelType || "ARTIFACT",
    modelName: chain.modelName || "Artifact",
    sourceModelId: ancestors[0]?.id || null,
    elementId: filePath || String(chain.modelId || artifactId),
    elementType: filePath ? "Artifact File" : "Artifact",
    elementName: filePath || chain.modelName || "Artifact",
    relationship: filePath ? "FOCAL_ARTIFACT_FILE" : "FOCAL_ARTIFACT",
  };

  const upstream =
    Array.isArray(lineage?.upstream) && lineage.upstream.length > 0
      ? lineage.upstream
      : ancestors.map((ancestor, index) => ({
          modelId: ancestor.id,
          modelType: ancestor.type,
          modelName: ancestor.name,
          sourceModelId: ancestors[index + 1]?.id || null,
          elementId: null,
          elementType: "Model",
          elementName: ancestor.name,
          relationship: "UPSTREAM_MODEL",
        }));

  let connectedElements = Array.isArray(lineage?.connectedElements)
    ? lineage.connectedElements
    : [];
  for (const rule of modelingImpactConfig().connectedElementRules || []) {
    const levelKey = String(rule?.level || "");
    const ancestor = ancestorForLevel(ancestors, levelKey);
    if (!ancestor) {
      continue;
    }
    try {
      const related = await fetchConfiguredConnectedElements(rule, ancestor, levelKey);
      connectedElements = [...connectedElements, ...related];
    } catch (error) {
      const levelLabel = levelConfigSafe(levelKey)?.displayName || levelKey || "upstream";
      throw new Error(`Failed to fetch ${levelLabel} connected elements: ${error.message}`);
    }
  }

  return {
    focalElement: focal,
    upstream,
    downstream: [],
    connectedElements,
    artifactFilesById: {},
  };
}

async function resolveArtifactFileLineage(artifactId, filePath, upstreamModelId, upstreamLevelKey) {
  const artifactRecord = await api(`/artifact/${artifactId}`);
  const traceability = artifactRecord?.modelJson?.traceability || {};
  const normalizedPath = String(filePath || "").trim();
  if (!normalizedPath) {
    return null;
  }

  const impactingElementId = Object.keys(traceability).find((elementId) => {
    const files = Array.isArray(traceability[elementId]) ? traceability[elementId] : [];
    return files.some((path) => String(path || "").trim() === normalizedPath);
  });

  if (!impactingElementId) {
    return null;
  }

  let exactImpact;
  try {
    const levelApiType = levelConfigSafe(upstreamLevelKey)?.apiType || upstreamLevelKey;
    exactImpact = await api(
      expandImpactEndpoint(
        modelingImpactConfig().elementImpactEndpoint,
        {
          level: levelApiType,
          modelId: upstreamModelId,
          elementId: impactingElementId,
        },
        "/impact/{level}/{modelId}/element/{elementId}",
      ),
    );
  } catch (error) {
    if (isPlannedFeatureError(error)) {
      throw new Error("Impact analysis is not available in this backend build.");
    }
    throw new Error(
      `Failed to resolve element ${impactingElementId} lineage for file ${normalizedPath}: ${error.message}`,
    );
  }
  const upstream = [
    ...(Array.isArray(exactImpact?.upstream) ? exactImpact.upstream : []),
    ...(exactImpact?.focalElement ? [exactImpact.focalElement] : []),
  ];

  return {
    upstream,
    connectedElements: Array.isArray(exactImpact?.connectedElements)
      ? exactImpact.connectedElements
      : [],
  };
}

async function fetchConfiguredConnectedElements(rule, ancestor, levelKey) {
  const level = levelConfigSafe(levelKey);
  const levelApiType = level?.apiType || levelKey;
  const record = await api(
    expandImpactEndpoint(
      rule?.modelEndpoint,
      { level: levelApiType, modelId: ancestor.id },
      "/{level}/{modelId}",
    ),
  );
  const elements = Array.isArray(record?.modelJson?.diagram?.elements)
    ? record.modelJson.diagram.elements
    : [];
  const includes = Array.isArray(rule?.typeIncludes)
    ? rule.typeIncludes.map((value) => String(value || "").toLowerCase()).filter(Boolean)
    : [];
  const modelType = String(rule?.modelType || level?.chatType || level?.displayName || levelKey);
  const fallbackElementType = String(rule?.fallbackElementType || "Element");
  const fallbackElementName = String(rule?.fallbackElementName || fallbackElementType);
  const relationship = String(rule?.relationship || "UPSTREAM_ELEMENT");

  return elements
    .filter((element) => {
      if (!includes.length) {
        return true;
      }
      const type = String(element?.eClass || element?.type || "").toLowerCase();
      return includes.some((token) => type.includes(token));
    })
    .map((element) => ({
      modelId: record.id,
      modelType,
      modelName: ancestor.name || record.name || modelType,
      sourceModelId: null,
      elementId: String(element?.id || ""),
      elementType: String(element?.eClass || element?.type || fallbackElementType),
      elementName: String(element?.label || element?.name || element?.id || fallbackElementName),
      relationship,
    }));
}
