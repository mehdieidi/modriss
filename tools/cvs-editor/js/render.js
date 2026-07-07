import {
  COMMON_ICONS,
  GEOMETRIES,
  LAYOUT_HINTS,
  MARKER_ENDS,
  METAMODEL,
  SHAPE_PRESETS,
  VISUAL_ROLES,
} from "./constants.js";
import { appState, elementCategories, levelFromDoc, resolveElementVisual } from "./state.js";
import {
  chipList,
  edgePreviewSvg,
  escapeHtml,
  iconUrl,
  nodePreviewSvg,
  primitivePreviewSvg,
  roleSizePreview,
} from "./preview.js";
import { normalizeThemeColor } from "./theme-colors.js";

function doc() {
  return appState.doc;
}

function sel() {
  return appState.selection;
}

function field(
  label,
  id,
  value,
  { type = "text", list = "", placeholder = "", min, max, step } = {},
) {
  const attrs = [
    `id="${id}"`,
    `name="${id}"`,
    type !== "textarea" ? `type="${type}"` : "",
    list ? `list="${list}"` : "",
    placeholder ? `placeholder="${escapeHtml(placeholder)}"` : "",
    min !== undefined ? `min="${min}"` : "",
    max !== undefined ? `max="${max}"` : "",
    step !== undefined ? `step="${step}"` : "",
  ]
    .filter(Boolean)
    .join(" ");
  const val = escapeHtml(value ?? "");
  if (type === "textarea") {
    return `<label class="field"><span>${escapeHtml(label)}</span><textarea ${attrs}>${val}</textarea></label>`;
  }
  if (type === "color") {
    return `<label class="field field-color"><span>${escapeHtml(label)}</span><input ${attrs} value="${val}"/><span class="color-swatch" style="background:${val || "#475569"}"></span></label>`;
  }
  return `<label class="field"><span>${escapeHtml(label)}</span><input ${attrs} value="${val}"/></label>`;
}

function themeColorFields(label, idPrefix, value) {
  const pair = normalizeThemeColor(value);
  return `
    ${field(`${label} (light theme)`, `${idPrefix}-light`, pair.light, { type: "color" })}
    ${field(`${label} (dark theme)`, `${idPrefix}-dark`, pair.dark, { type: "color" })}
  `;
}

function previewColor(value) {
  return normalizeThemeColor(value).dark;
}

function selectField(label, id, value, options) {
  const opts = options
    .map((o) => {
      const v = typeof o === "string" ? o : o.value;
      const t = typeof o === "string" ? o : o.label;
      return `<option value="${escapeHtml(v)}"${String(value) === String(v) ? " selected" : ""}>${escapeHtml(t)}</option>`;
    })
    .join("");
  return `<label class="field"><span>${escapeHtml(label)}</span><select id="${id}" name="${id}">${opts}</select></label>`;
}

function checkboxField(label, id, checked) {
  return `<label class="field field-check"><input type="checkbox" id="${id}" name="${id}"${checked ? " checked" : ""}/><span>${escapeHtml(label)}</span></label>`;
}

export function renderOverview() {
  const d = doc();
  if (!d) {
    return `<div class="empty-state"><h2>No document loaded</h2><p>Load a CVS file or create a new one.</p></div>`;
  }
  const level = levelFromDoc(d);
  const stats = [
    ["Elements", (d.elementOverrides || []).length],
    ["Primitives", Object.keys(d.primitives || {}).length],
    ["Viewpoints", (d.viewpoints || []).length],
    ["Package rules", (d.elementVisualRules || []).length],
    ["Edge styles", (d.relationshipVisualRules || []).length],
    ["Badge rules", (d.badgeRules || []).length],
  ];
  return `
    <header class="section-head">
      <div>
        <h2>Overview</h2>
        <p>Document metadata and coverage summary for ${escapeHtml(d.displayName || level.toUpperCase())}.</p>
      </div>
    </header>
    <div class="overview-grid">
      <article class="panel stat-panel">
        <h3>Metamodel</h3>
        ${field("Display name", "ov-displayName", d.displayName)}
        ${field("CVS version", "ov-cvsVersion", d.cvsVersion, { type: "number" })}
        ${selectField("Level", "ov-level", level, Object.keys(METAMODEL))}
        ${field("Ecore path", "ov-ecore", d.metamodelRef?.ecore)}
        ${field("Namespace URI", "ov-nsUri", d.metamodelRef?.nsUri)}
      </article>
      <article class="panel stat-panel">
        <h3>Coverage</h3>
        <div class="stat-cards">
          ${stats
            .map(
              ([name, count]) =>
                `<div class="stat-card"><strong>${count}</strong><span>${escapeHtml(name)}</span></div>`,
            )
            .join("")}
        </div>
      </article>
      <article class="panel stat-panel wide">
        <h3>Defaults preview</h3>
        <div class="defaults-preview-row">
          ${nodePreviewSvg(
            {
              label: "Sample element",
              color: d.elementVisualDefaults?.color,
              icon: d.elementVisualDefaults?.icon,
              tag: d.elementVisualDefaults?.notation?.tag,
              lineFields: d.elementVisualDefaults?.notation?.lineFields,
              visualRole: "node",
            },
            { width: 240, height: 120 },
          )}
          <div class="defaults-meta">
            <p>Global defaults applied before package rules and per-type overrides.</p>
            <button type="button" class="btn ghost" data-nav="advanced">Edit defaults →</button>
          </div>
        </div>
      </article>
    </div>`;
}

export function renderElements() {
  const d = doc();
  const query = appState.filters.elementQuery.toLowerCase();
  const category = appState.filters.category;
  const items = (d?.elementOverrides || [])
    .map((el, index) => ({ el, index, visual: resolveElementVisual(el) }))
    .filter(({ el, visual }) => {
      if (category !== "all" && visual.category !== category) return false;
      if (!query) return true;
      const hay = `${el.type} ${visual.label} ${visual.category} ${visual.tag}`.toLowerCase();
      return hay.includes(query);
    });

  const categories = ["all", ...elementCategories()];
  const selectedIndex = sel().section === "elements" ? sel().index : -1;

  const gallery = items
    .map(({ el, index, visual }) => {
      const active = index === selectedIndex ? " is-selected" : "";
      return `<button type="button" class="element-card${active}" data-element-index="${index}">
        ${nodePreviewSvg(visual, { selected: index === selectedIndex })}
        <div class="element-card-meta">
          <strong>${escapeHtml(el.type)}</strong>
          <span>${escapeHtml(visual.category || "—")}</span>
        </div>
      </button>`;
    })
    .join("");

  const inspector = renderElementInspector(selectedIndex);

  return `
    <header class="section-head">
      <div>
        <h2>Elements</h2>
        <p>Per-type visual overrides — icons, colors, shapes, and card layout.</p>
      </div>
      <div class="section-actions">
        <button type="button" class="btn primary" id="addElementBtn">+ Add element</button>
      </div>
    </header>
    <div class="split-layout">
      <div class="split-main">
        <div class="toolbar">
          <input class="search-input" id="elementSearch" placeholder="Search types…" value="${escapeHtml(appState.filters.elementQuery)}"/>
          <div class="chip-filters">
            ${categories
              .map(
                (c) =>
                  `<button type="button" class="filter-chip${category === c ? " active" : ""}" data-category="${escapeHtml(c)}">${escapeHtml(c === "all" ? "All" : c)}</button>`,
              )
              .join("")}
          </div>
        </div>
        <div class="element-gallery">${gallery || `<div class="empty-inline">No elements match.</div>`}</div>
      </div>
      <aside class="inspector panel">${inspector}</aside>
    </div>`;
}

function renderElementInspector(index) {
  const d = doc();
  if (!d || index < 0 || !d.elementOverrides?.[index]) {
    return `<div class="inspector-empty"><p>Select an element card to edit its visual syntax.</p></div>`;
  }
  const el = d.elementOverrides[index];
  const visual = resolveElementVisual(el);
  const lineFields = el.card?.lineFields || [];
  const detailFields = el.card?.detailFields || [];

  const iconGrid = COMMON_ICONS.map(
    (name) =>
      `<button type="button" class="icon-pick${el.icon === name ? " active" : ""}" data-icon="${name}" title="${name}">
        <img src="${iconUrl(name)}" alt="" loading="lazy"/>
      </button>`,
  ).join("");

  return `
    <div class="inspector-head">
      ${nodePreviewSvg(visual, { width: 260, height: 130, selected: true })}
      <h3>${escapeHtml(el.type)}</h3>
    </div>
    <div class="inspector-body">
      ${field("Type name", "el-type", el.type)}
      ${field("Label", "el-label", el.label || el.displayName)}
      ${field("Category", "el-category", el.category)}
      ${selectField("Visual role", "el-visualRole", el.visualRole || "node", VISUAL_ROLES)}
      ${themeColorFields("Color", "el-color", el.color || visual.color)}
      <label class="field"><span>Primitive / shape</span><input id="el-primitive" list="shape-presets" value="${escapeHtml(el.primitive || "")}"/><datalist id="shape-presets">${SHAPE_PRESETS.map((s) => `<option value="${s}">`).join("")}</datalist></label>
      ${field("Card tag", "el-tag", el.card?.tag)}
      <div class="field"><span>Line fields</span><div class="chip-editor" id="el-lineFields">${chipList(lineFields, { removable: true })}</div><input class="chip-input" id="el-lineFieldInput" placeholder="Add field + Enter"/></div>
      <div class="field"><span>Detail fields</span><div class="chip-editor" id="el-detailFields">${chipList(detailFields, { removable: true })}</div><input class="chip-input" id="el-detailFieldInput" placeholder="Add field + Enter"/></div>
      ${checkboxField("Contained only", "el-containedOnly", el.containedOnly)}
      ${checkboxField("Support only", "el-supportOnly", el.supportOnly)}
      ${checkboxField("Creatable", "el-creatable", el.creatable !== false)}
      <details class="icon-picker-wrap"><summary>Icon library</summary><div class="icon-grid">${iconGrid}</div></details>
      <button type="button" class="btn danger ghost" id="deleteElementBtn">Delete element</button>
    </div>`;
}

export function renderPrimitives() {
  const d = doc();
  const primitives = Object.entries(d?.primitives || {});
  const selectedKey = sel().section === "primitives" ? sel().id : "";
  const cards = primitives
    .map(([key, value]) => {
      const active = key === selectedKey ? " is-selected" : "";
      return `<button type="button" class="primitive-card${active}" data-primitive-key="${escapeHtml(key)}">
        ${primitivePreviewSvg(value)}
        <strong>${escapeHtml(key)}</strong>
        <span>${escapeHtml(value?.geometry || "rectangle")}</span>
      </button>`;
    })
    .join("");

  const selected = selectedKey ? d.primitives[selectedKey] : null;
  const inspector = selected
    ? `<div class="inspector-body">
        ${field("Key", "prim-key", selectedKey)}
        ${selectField("Geometry", "prim-geometry", selected.geometry, GEOMETRIES)}
        ${field("Corner radius", "prim-cornerRadius", selected.cornerRadius ?? 0, { type: "number", min: 0, step: 1 })}
        ${field("Description", "prim-description", selected.description || "", { type: "textarea" })}
        <button type="button" class="btn danger ghost" id="deletePrimitiveBtn">Delete primitive</button>
      </div>`
    : `<div class="inspector-empty"><p>Select a primitive shape.</p></div>`;

  return `
    <header class="section-head">
      <div><h2>Primitives</h2><p>Reusable geometry and shape building blocks.</p></div>
      <button type="button" class="btn primary" id="addPrimitiveBtn">+ Add primitive</button>
    </header>
    <div class="split-layout">
      <div class="split-main"><div class="primitive-gallery">${cards || `<div class="empty-inline">No primitives yet.</div>`}</div></div>
      <aside class="inspector panel">${inspector}</aside>
    </div>`;
}

export function renderPackageRules() {
  const d = doc();
  const rules = d?.elementVisualRules || [];
  const idx = sel().section === "packages" ? sel().index : -1;
  const cards = rules
    .map((rule, index) => {
      const meta = rule.metadata || {};
      const pkgs = (rule.match?.packages || []).join(", ") || "—";
      const active = index === idx ? " is-selected" : "";
      return `<button type="button" class="package-rule-card${active}" data-package-index="${index}">
        <div class="swatch" style="background:${escapeHtml(previewColor(meta.color))}"></div>
        <div><strong>${escapeHtml(pkgs)}</strong><span>${escapeHtml(meta.category || "")}</span></div>
      </button>`;
    })
    .join("");

  const inspector = renderPackageInspector(idx);
  return `
    <header class="section-head">
      <div><h2>Package rules</h2><p>Visual defaults by metamodel package.</p></div>
      <button type="button" class="btn primary" id="addPackageRuleBtn">+ Add rule</button>
    </header>
    <div class="split-layout">
      <div class="split-main"><div class="package-list">${cards || `<div class="empty-inline">No package rules.</div>`}</div></div>
      <aside class="inspector panel">${inspector}</aside>
    </div>`;
}

function renderPackageInspector(index) {
  const d = doc();
  const rule = d?.elementVisualRules?.[index];
  if (!rule) {
    return `<div class="inspector-empty"><p>Select a package rule.</p></div>`;
  }
  const meta = rule.metadata || {};
  const notation = meta.notation || {};
  return `
    <div class="inspector-body">
      ${field("Packages (comma-separated)", "pkg-packages", (rule.match?.packages || []).join(", "))}
      ${field("Category", "pkg-category", meta.category)}
      ${field("Icon", "pkg-icon", meta.icon)}
      ${themeColorFields("Color", "pkg-color", meta.color)}
      ${field("Shape", "pkg-shape", notation.shape)}
      ${field("Tag", "pkg-tag", notation.tag)}
      ${field("Line fields", "pkg-lineFields", (notation.lineFields || []).join(", "))}
      <button type="button" class="btn danger ghost" id="deletePackageRuleBtn">Delete rule</button>
    </div>`;
}

export function renderViewpoints() {
  const d = doc();
  const views = d?.viewpoints || [];
  const idx = sel().section === "viewpoints" ? sel().index : -1;
  const allTypes = (d?.elementOverrides || []).map((e) => e.type).filter(Boolean);

  const strip = views
    .map((view, index) => {
      const active = index === idx ? " is-selected" : "";
      const paletteCount = (view.palette || []).length;
      return `<button type="button" class="view-strip-card${active}" data-view-index="${index}">
        <span class="view-id">${escapeHtml(view.id)}</span>
        <strong>${escapeHtml(view.displayName)}</strong>
        <small>${paletteCount} palette · ${(view.elementTypes || []).length} scoped</small>
      </button>`;
    })
    .join("");

  const view = idx >= 0 ? views[idx] : null;
  const detail = view
    ? `<div class="view-editor panel">
        <div class="view-editor-grid">
          ${field("ID", "view-id", view.id)}
          ${field("Display name", "view-displayName", view.displayName)}
          ${field("View type", "view-viewType", view.viewType)}
          ${field("Viewpoint", "view-viewpoint", view.viewpoint)}
          ${selectField("Layout hint", "view-layoutHint", view.layoutHint || "DEFAULT_LAYERED", LAYOUT_HINTS)}
        </div>
        <div class="dual-list">
          <div class="dual-col">
            <h4>Palette <small>(canvas drag sources)</small></h4>
            <div class="type-pool" id="view-palette">${paletteTypeButtons(view.palette || [], allTypes, "palette")}</div>
          </div>
          <div class="dual-col">
            <h4>Scoped element types</h4>
            <div class="type-pool" id="view-elementTypes">${paletteTypeButtons(view.elementTypes || [], allTypes, "scope")}</div>
          </div>
        </div>
        <div class="field"><span>Relationship kinds</span>
          <div class="chip-editor" id="view-relKinds">${chipList(view.relationshipKinds || [], { removable: true })}</div>
          <input class="chip-input" id="view-relKindInput" placeholder="Add kind + Enter"/>
        </div>
        <button type="button" class="btn danger ghost" id="deleteViewBtn">Delete viewpoint</button>
      </div>`
    : `<div class="empty-inline panel">Select a viewpoint above.</div>`;

  return `
    <header class="section-head">
      <div><h2>Viewpoints</h2><p>Named views, palettes, and scoped diagrams.</p></div>
      <button type="button" class="btn primary" id="addViewBtn">+ Add viewpoint</button>
    </header>
    <div class="view-strip">${strip || ""}</div>
    ${detail}`;
}

function paletteTypeButtons(activeList, allTypes, pool) {
  const active = new Set(activeList || []);
  return allTypes
    .map((type) => {
      const on = active.has(type);
      return `<button type="button" class="type-toggle${on ? " on" : ""}" data-pool="${pool}" data-type="${escapeHtml(type)}">${escapeHtml(type)}</button>`;
    })
    .join("");
}

export function renderCanvas() {
  const d = doc();
  const policy = d?.canvasPolicy || {};
  const roles = policy.roleSizes || {};
  return `
    <header class="section-head"><div><h2>Canvas policy</h2><p>Node sizes and semantic zoom thresholds.</p></div></header>
    <div class="canvas-grid">
      <article class="panel">
        <h3>Role sizes</h3>
        <div class="role-size-row">
          ${Object.entries(roles)
            .map(([role, size]) => roleSizePreview(role, size))
            .join("")}
        </div>
        ${Object.entries(roles)
          .map(
            ([role, size]) => `
          <div class="role-fields">
            <h4>${escapeHtml(role)}</h4>
            ${field(`${role} width`, `canvas-${role}-w`, size?.width, { type: "number", min: 40 })}
            ${field(`${role} height`, `canvas-${role}-h`, size?.height, { type: "number", min: 40 })}
          </div>`,
          )
          .join("")}
      </article>
      <article class="panel">
        <h3>Semantic zoom</h3>
        ${field("Low detail below", "canvas-lowDetailBelow", policy.lowDetailBelow, { type: "number", step: 0.01, min: 0 })}
        ${field("High detail at/above", "canvas-highDetailAtOrAbove", policy.highDetailAtOrAbove, { type: "number", step: 0.01 })}
        ${field("Edge labels at/above", "canvas-edgeLabelsAtOrAbove", policy.edgeLabelsAtOrAbove, { type: "number", step: 0.01 })}
        ${field("Dense edge threshold", "canvas-denseEdgeThreshold", policy.denseEdgeThreshold, { type: "number" })}
        ${field("Dense labels at/above", "canvas-denseEdgeLabelsAtOrAbove", policy.denseEdgeLabelsAtOrAbove, { type: "number", step: 0.01 })}
        ${field("Very dense threshold", "canvas-veryDenseEdgeThreshold", policy.veryDenseEdgeThreshold, { type: "number" })}
        ${field("Very dense labels at/above", "canvas-veryDenseEdgeLabelsAtOrAbove", policy.veryDenseEdgeLabelsAtOrAbove, { type: "number", step: 0.01 })}
      </article>
    </div>`;
}

export function renderRelationships() {
  const d = doc();
  const rules = d?.relationshipVisualRules || [];
  const idx = sel().section === "relationships" ? sel().index : -1;
  const cards = rules
    .map((rule, index) => {
      const active = index === idx ? " is-selected" : "";
      return `<button type="button" class="edge-rule-card${active}" data-edge-index="${index}">
        ${edgePreviewSvg(rule)}
        <span>${escapeHtml((rule.matchKinds || []).slice(0, 3).join(", "))}</span>
      </button>`;
    })
    .join("");

  const rule = idx >= 0 ? rules[idx] : null;
  const inspector = rule
    ? `<div class="inspector-body">
        ${field("Match kinds", "edge-kinds", (rule.matchKinds || []).join(", "))}
        ${field("Match EClasses", "edge-eclasses", (rule.matchEClasses || []).join(", "))}
        ${field("CSS class", "edge-className", rule.className)}
        ${themeColorFields("Stroke", "edge-stroke", rule.stroke)}
        ${field("Line width", "edge-lineWidth", rule.lineWidth ?? 2, { type: "number", step: 0.1 })}
        ${field("Line dash", "edge-lineDash", (rule.lineDash || []).join(", "))}
        ${selectField("Marker end", "edge-markerEnd", rule.markerEnd || "arrow", MARKER_ENDS)}
        ${selectField("Marker start", "edge-markerStart", rule.markerStart || "", MARKER_ENDS)}
        <button type="button" class="btn danger ghost" id="deleteEdgeRuleBtn">Delete rule</button>
      </div>`
    : `<div class="inspector-empty"><p>Select an edge style rule.</p></div>`;

  const kindLabels = Object.entries(d?.relationshipKindLabels || {})
    .map(([k, v]) => `${k} → ${v}`)
    .join("\n");

  return `
    <header class="section-head">
      <div><h2>Relationships</h2><p>Kinds, labels, and edge presentation.</p></div>
      <button type="button" class="btn primary" id="addEdgeRuleBtn">+ Add visual rule</button>
    </header>
    <div class="relationships-grid">
      <article class="panel">
        <h3>Relationship kinds</h3>
        ${field("Kinds (comma-separated)", "rel-kinds", (d?.relationshipKinds || []).join(", "))}
        ${field("Kind labels", "rel-kindLabels", kindLabels, { type: "textarea", placeholder: "KIND → label per line" })}
      </article>
    </div>
    <div class="split-layout">
      <div class="split-main"><div class="edge-rule-gallery">${cards || `<div class="empty-inline">No visual rules.</div>`}</div></div>
      <aside class="inspector panel">${inspector}</aside>
    </div>`;
}

export function renderBadges() {
  const d = doc();
  const rules = d?.badgeRules || [];
  const idx = sel().section === "badges" ? sel().index : -1;
  const list = rules
    .map((rule, index) => {
      const active = index === idx ? " is-selected" : "";
      const label = rule.label || rule.field;
      return `<button type="button" class="badge-rule-card${active}" data-badge-index="${index}">
        <span class="badge-preview">${escapeHtml(label)}</span>
        <div><strong>${escapeHtml(rule.field)}</strong><span>${rule.useValue ? "uses value" : rule.when === true ? "when true" : ""}</span></div>
      </button>`;
    })
    .join("");

  const rule = idx >= 0 ? rules[idx] : null;
  const inspector = rule
    ? `<div class="inspector-body">
        ${field("Field", "badge-field", rule.field)}
        ${field("Label", "badge-label", rule.label || "")}
        ${checkboxField("Use field value as label", "badge-useValue", rule.useValue)}
        ${checkboxField("Show when true", "badge-when", rule.when === true)}
        <button type="button" class="btn danger ghost" id="deleteBadgeRuleBtn">Delete rule</button>
      </div>`
    : `<div class="inspector-empty"><p>Select a badge rule.</p></div>`;

  return `
    <header class="section-head">
      <div><h2>Badges</h2><p>Attribute-driven node badges.</p></div>
      <button type="button" class="btn primary" id="addBadgeRuleBtn">+ Add badge rule</button>
    </header>
    <div class="split-layout">
      <div class="split-main"><div class="badge-list">${list || `<div class="empty-inline">No badge rules.</div>`}</div></div>
      <aside class="inspector panel">${inspector}</aside>
    </div>`;
}

export function renderAdvanced() {
  const d = doc();
  const defaults = d?.elementVisualDefaults || {};
  const notation = defaults.notation || {};
  return `
    <header class="section-head"><div><h2>Advanced</h2><p>Defaults, templates, and raw JSON.</p></div></header>
    <div class="advanced-grid">
      <article class="panel">
        <h3>Element visual defaults</h3>
        ${field("Icon", "def-icon", defaults.icon)}
        ${themeColorFields("Color", "def-color", defaults.color)}
        ${field("Category", "def-category", defaults.category)}
        ${field("Shape", "def-shape", notation.shape)}
        ${field("Tag", "def-tag", notation.tag)}
        ${field("Line fields", "def-lineFields", (notation.lineFields || []).join(", "))}
      </article>
      <article class="panel wide">
        <h3>Raw document JSON</h3>
        <p class="hint">Power-user escape hatch. Changes sync when you click Apply JSON.</p>
        <textarea id="rawJsonEditor" class="raw-json" spellcheck="false">${escapeHtml(JSON.stringify(d, null, 2))}</textarea>
        <button type="button" class="btn primary" id="applyRawJsonBtn">Apply JSON</button>
      </article>
    </div>`;
}

export function renderActiveSection() {
  const section = sel().section || "overview";
  switch (section) {
    case "elements":
      return renderElements();
    case "primitives":
      return renderPrimitives();
    case "packages":
      return renderPackageRules();
    case "viewpoints":
      return renderViewpoints();
    case "canvas":
      return renderCanvas();
    case "relationships":
      return renderRelationships();
    case "badges":
      return renderBadges();
    case "advanced":
      return renderAdvanced();
    default:
      return renderOverview();
  }
}
