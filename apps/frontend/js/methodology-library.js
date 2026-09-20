import { escapeHtml } from "./utils.js";

const CATEGORIES = [
  { id: "roleDefinitions", label: "Roles" },
  { id: "taskDefinitions", label: "Tasks" },
  { id: "workProductDefinitions", label: "Work products" },
  { id: "guidance", label: "Guidance" },
  { id: "processComponents", label: "Process components" },
];

function itemDescription(category, item) {
  if (category === "roleDefinitions") return item.responsibilities?.join("; ") || "Role definition";
  if (category === "taskDefinitions") return item.purpose || item.description || "Task definition";
  if (category === "workProductDefinitions")
    return item.description || item.purpose || "Work product";
  if (category === "guidance") return item.text || item.description || "Process guidance";
  return `${item.phaseCount || 0} phases · ${item.workSequenceCount || 0} work sequences`;
}

function renderCategoryTabs(category) {
  return `<div class="method-library-tabs" role="tablist" aria-label="Process content category">
    ${CATEGORIES.map(
      (item) =>
        `<button type="button" role="tab" aria-selected="${category === item.id}" class="method-library-tab${category === item.id ? " is-active" : ""}" data-category="${item.id}">${item.label}</button>`,
    ).join("")}
  </div>`;
}

function renderResults(host, library, category, query) {
  const items = library?.[category] || [];
  const normalizedQuery = String(query || "")
    .trim()
    .toLowerCase();
  const filtered = normalizedQuery
    ? items.filter((item) => JSON.stringify(item).toLowerCase().includes(normalizedQuery))
    : items;
  host.innerHTML = filtered.length
    ? filtered
        .map(
          (
            item,
          ) => `<button type="button" class="method-library-result" data-item-id="${escapeHtml(item.id)}">
            <strong>${escapeHtml(item.name || item.displayName || item.id)}</strong>
            <span>${escapeHtml(itemDescription(category, item))}</span>
            <small>${escapeHtml((item.sourceProcesses || [item.sourceProcess]).filter(Boolean).join(" · "))}</small>
          </button>`,
        )
        .join("")
    : `<p class="method-library-empty">No matching content. Try another search.</p>`;
}

function renderValueList(title, values, definitions = null) {
  if (!values?.length) return "";
  const items = values.map((value) => {
    const definition = definitions?.find((item) => item.id === value);
    return `<li>${escapeHtml(definition?.name || value)}${definition?.description ? `: ${escapeHtml(definition.description)}` : ""}</li>`;
  });
  return `<div class="method-library-detail-section"><h4>${escapeHtml(title)}</h4><ul>${items.join("")}</ul></div>`;
}

function renderMethodContentItem(library, category, item) {
  const parts = [
    `<span class="map-detail-kind">${escapeHtml(CATEGORIES.find((entry) => entry.id === category)?.label || "Process content")}</span>`,
    `<h3>${escapeHtml(item.name || item.displayName || item.id)}</h3>`,
    `<p>${escapeHtml(item.purpose || item.description || item.text || "")}</p>`,
  ];

  if (category === "roleDefinitions") {
    parts.push(renderValueList("Responsibilities", item.responsibilities));
    parts.push(renderValueList("Conceptual role mappings", item.methodRoleIds));
  } else if (category === "taskDefinitions") {
    const roleNames = (item.performerRoleRefs || []).map(
      (id) => library.roleDefinitions?.find((role) => role.id === id)?.name || id,
    );
    if (roleNames.length) parts.push(renderValueList("Performing roles", roleNames));
    parts.push(
      renderValueList("Inputs", item.inputWorkProductRefs, library.workProductDefinitions),
    );
    parts.push(
      renderValueList("Outputs", item.outputWorkProductRefs, library.workProductDefinitions),
    );
    parts.push(renderValueList("Entry criteria", item.entryCriteria));
    parts.push(renderValueList("Work steps", item.steps));
    parts.push(renderValueList("Exit criteria", item.exitCriteria));
    parts.push(renderValueList("Validation rules", item.validationRules));
    parts.push(renderValueList("Coverage groups", item.coverageGroups));
    parts.push(
      renderValueList(
        "Metamodel bindings",
        (item.metamodelBindings || []).map((binding) =>
          `${binding.kind || ""} ${binding.classifier || ""}`.trim(),
        ),
      ),
    );
    parts.push(renderValueList("Related palette elements", item.paletteFocus));
    if (item.inputSource)
      parts.push(`<p><strong>Input source:</strong> ${escapeHtml(item.inputSource)}</p>`);
    if (item.durationEstimate)
      parts.push(
        `<p><strong>Indicative duration:</strong> ${escapeHtml(item.durationEstimate)}</p>`,
      );
  } else if (category === "workProductDefinitions") {
    if (item.workProductKind)
      parts.push(`<p><strong>Kind:</strong> ${escapeHtml(item.workProductKind)}</p>`);
  } else if (category === "guidance") {
    if (item.guidanceKind)
      parts.push(`<p><strong>Guidance kind:</strong> ${escapeHtml(item.guidanceKind)}</p>`);
    if (item.appliesTo)
      parts.push(`<p><strong>Applies to:</strong> ${escapeHtml(item.appliesTo)}</p>`);
    parts.push(renderValueList("Roles", item.roles));
    parts.push(renderValueList("Work products", item.workProducts));
    parts.push(renderValueList("Requirements", item.requirements));
    parts.push(renderValueList("References", item.references));
  } else {
    parts.push(
      renderValueList(
        "Repeatable activities",
        item.repeatableActivities?.map(
          (activity) =>
            `${activity.name}${activity.repeatCondition ? ` — ${activity.repeatCondition}` : ""}`,
        ),
      ),
    );
  }

  const sources = item.sourceProcesses || (item.sourceProcess ? [item.sourceProcess] : []);
  if (sources.length) parts.push(renderValueList("Provenance", sources));
  if (item.initialContext)
    parts.push(`<p><strong>Initial context:</strong> ${escapeHtml(item.initialContext)}</p>`);
  if (item.resultContext)
    parts.push(`<p><strong>Result context:</strong> ${escapeHtml(item.resultContext)}</p>`);
  return parts.filter(Boolean).join("");
}

export function renderMethodLibrary(host, library, browserState, handlers) {
  if (!library) {
    host.innerHTML = `<div class="method-library-empty">The process content library is unavailable.</div>`;
    return;
  }

  const category = browserState?.category || null;
  const item =
    category && browserState?.itemId
      ? (library[category] || []).find((entry) => entry.id === browserState.itemId)
      : null;

  if (item) {
    host.innerHTML = `<div class="method-library-header">
      <button type="button" class="method-library-back" data-action="back-to-results">‹ Back to results</button>
      ${renderCategoryTabs(category)}
    </div>${renderMethodContentItem(library, category, item)}
      <button type="button" class="method-library-back" data-action="return-to-lifecycle">Return to Full Process</button>`;
  } else if (category) {
    const activeCategory = CATEGORIES.find((entry) => entry.id === category);
    host.innerHTML = `<div class="method-library-header">
      <button type="button" class="method-library-back" data-action="return-to-lifecycle">‹ Full Process overview</button>
      <h3>Process content library</h3>
      ${renderCategoryTabs(category)}
      <label class="method-library-search-label" for="methodLibrarySearch">Search ${escapeHtml(activeCategory?.label.toLowerCase() || "process content")}</label>
      <input class="method-library-search" id="methodLibrarySearch" type="search" placeholder="Search by name, step, role, or identifier" value="${escapeHtml(browserState?.query || "")}" />
    </div>
      <div class="method-library-results-count">${(library[category] || []).length} entries</div>
      <div class="method-library-results" id="methodLibraryResults"></div>`;
    const search = host.querySelector("#methodLibrarySearch");
    const results = host.querySelector("#methodLibraryResults");
    renderResults(results, library, category, browserState?.query);
    results?.addEventListener("click", (event) => {
      const button = event.target.closest?.("[data-item-id]");
      if (button) handlers.onItem(button.dataset.itemId);
    });
    search?.addEventListener("input", () => {
      handlers.onQuery(search.value);
      renderResults(results, library, category, search.value);
    });
  } else {
    host.innerHTML = `<div class="method-library-header">
      <button type="button" class="method-library-back" data-action="return-to-lifecycle">‹ Full Process overview</button>
      <h3>Process content library</h3>
      <p>Browse the complete SPEM process content package. Each item includes its definition and source components.</p>
    </div><div class="method-library-category-list">
      ${CATEGORIES.map(
        (
          entry,
        ) => `<button type="button" class="method-library-category" data-category="${entry.id}">
        <strong>${entry.label}</strong><span>${(library[entry.id] || []).length} definitions</span>
      </button>`,
      ).join("")}
    </div>`;
  }

  host.querySelectorAll("[data-category]").forEach((button) => {
    button.addEventListener("click", () => handlers.onCategory(button.dataset.category));
  });
  host.querySelector('[data-action="back-to-results"]')?.addEventListener("click", handlers.onBack);
  host
    .querySelector('[data-action="return-to-lifecycle"]')
    ?.addEventListener("click", handlers.onClose);
}
