import { escapeHtml } from "./utils.js";
import { defaultPsmNotation, typeKey } from "./psm-notation.js";
import { enhanceSelects } from "./themed-select.js";
import { modelingIconAliases, resolveModelingIconSource } from "./modeling-config-data.js";

const h = escapeHtml;
const drafts = new Map();
const options = (entries, value) =>
  entries
    .map(
      ([key, label]) =>
        `<option value="${h(key)}" ${key === value ? "selected" : ""}>${h(label)}</option>`,
    )
    .join("");

/** Presentation-only editor. Language features remain inferred from the Ecore schema. */
export function renderPsmVisualEditor(container, { platform, schema, syntax, save }) {
  const draftKey = `${platform.projectId || ""}:${platform.id}`;
  if (!drafts.has(draftKey))
    drafts.set(draftKey, structuredClone(syntax || defaultPsmNotation(schema)));
  let draft = drafts.get(draftKey);
  const icons = [
    ...new Set([
      "category",
      "extension",
      "functions",
      "database",
      "api",
      "event",
      "route",
      "memory",
      "schema",
      "shield",
      "groups",
      "person",
      "settings",
      "security",
      "link",
      "folder",
      "map",
      "timeline",
      "inventory",
      "public",
      "placeholder",
      ...Object.keys(modelingIconAliases()),
    ]),
  ];
  const changed = () => {
    container.querySelector("[data-save-notation]").textContent = "Save visual notation · unsaved";
    container.querySelector("[data-notation-status]").textContent = "Unsaved notation changes";
  };
  const render = () => {
    const defaults = defaultPsmNotation(schema);
    const allElements = defaults.elements.map((element) => {
      const custom = draft.elements.find((item) => item.type === element.type) || {};
      const merged = { ...element, ...custom, color: { ...element.color, ...custom.color } };
      if (!draft.paletteGroups.some((group) => group.id === merged.group))
        merged.group = draft.paletteGroups[0].id;
      return merged;
    });
    draft.elements = allElements;
    container.innerHTML = `<div class="psm-section-heading"><div><h3>${h(platform.name)} visual notation</h3><p>Default notation is ready to use. Customize icons, palette groups and views without changing the language or scripts.</p></div></div>
      <label>Root model type<select data-root-type>${options(
        schema.filter((type) => !type.abstract).map((type) => [typeKey(type, schema), type.name]),
        draft.rootType,
      )}</select></label>
      <section class="psm-notation-section"><div class="psm-section-heading"><div><h4>Palette groups</h4><p class="psm-muted">Organize the element types users can add to the canvas.</p></div><button type="button" class="btn btn-secondary btn-sm" data-add-group>Add group</button></div>
        ${draft.paletteGroups.map((group, index) => `<div class="psm-group-row"><input data-group-name="${index}" aria-label="Palette group ${index + 1} name" value="${h(group.name)}" maxlength="100" /><button type="button" class="btn btn-secondary btn-sm" data-remove-group="${index}" ${draft.paletteGroups.length === 1 ? "disabled" : ""}>Remove group</button></div>`).join("")}</section>
      <section class="psm-notation-section"><h4>Element notation</h4><p class="psm-muted">All types use the default icon until you choose another. Colors can be set independently for each theme.</p>
      <label>Find an element<input type="search" data-find-type placeholder="Search element types…" /></label>
        ${draft.elements
          .map(
            (
              element,
              index,
            ) => `<div class="psm-notation-row" data-type-row="${h(element.type)}"><img src="${h(resolveModelingIconSource(element.icon))}" alt="" data-preview="${index}" /><div><label>Display name<input data-element="${index}" data-property="label" value="${h(element.label)}" maxlength="120" /></label><small>${h(element.type)}${element.type === draft.rootType ? " · Model root" : ""}</small><div class="psm-notation-colors"><label>Light<input type="color" aria-label="${h(element.type)} light color" data-element="${index}" data-property="light" value="${h(element.color.light)}" /></label><label>Dark<input type="color" aria-label="${h(element.type)} dark color" data-element="${index}" data-property="dark" value="${h(element.color.dark)}" /></label></div></div>
          <label>Icon<select data-element="${index}" data-property="icon">${options(
            [...new Set([...icons, element.icon])].map((icon) => [icon, icon]),
            element.icon,
          )}</select></label>
          <label>Palette group<select data-element="${index}" data-property="group">${options(
            draft.paletteGroups.map((group) => [group.id, group.name]),
            element.group,
          )}</select><select aria-label="${h(element.type)} visual role" data-element="${index}" data-property="role">${options(
            [
              ["node", "Node"],
              ["container", "Container"],
              ["detail", "Detail"],
              ["support", "Hidden support type"],
            ],
            element.role,
          )}</select></label></div>`,
          )
          .join("")}</section>
      <section class="psm-notation-section"><div class="psm-section-heading"><div><h4>Views</h4><p class="psm-muted">Choose which element types appear in each canvas view and its palette.</p></div><button type="button" class="btn btn-secondary btn-sm" data-add-view>Add view</button></div>
        ${draft.views.map((view, index) => `<div class="psm-notation-view"><div class="psm-group-row"><input data-view-name="${index}" aria-label="View ${index + 1} name" value="${h(view.name)}" maxlength="120" /><button type="button" class="btn btn-secondary btn-sm" data-remove-view="${index}" ${draft.views.length === 1 ? "disabled" : ""}>Remove view</button></div><div class="psm-view-types">${draft.elements.map((element) => `<label><input type="checkbox" data-view-type="${index}" value="${h(element.type)}" ${view.types.includes(element.type) ? "checked" : ""} />${h(element.label)}</label>`).join("")}</div></div>`).join("")}</section>
      <div class="psm-upload-footer"><span data-notation-status>Changes apply to this project’s provider.</span><button class="btn btn-primary" type="button" data-save-notation>Save visual notation</button></div>`;
    enhanceSelects(container);
  };
  const update = (event) => {
    const node = event.target;
    if (node.matches("[data-find-type]")) {
      container.querySelectorAll("[data-type-row]").forEach((row) => {
        row.hidden =
          !row.textContent.toLowerCase().includes(node.value.toLowerCase()) &&
          !row.dataset.typeRow.toLowerCase().includes(node.value.toLowerCase());
      });
      return;
    }
    if (node.dataset.element != null) {
      const element = draft.elements[Number(node.dataset.element)];
      const property = node.dataset.property;
      if (["light", "dark"].includes(property)) element.color[property] = node.value;
      else element[property] = node.value;
      if (property === "icon")
        container.querySelector(`[data-preview="${node.dataset.element}"]`).src =
          resolveModelingIconSource(node.value);
    } else if (node.dataset.groupName != null) {
      draft.paletteGroups[Number(node.dataset.groupName)].name = node.value;
      container.querySelectorAll('select[data-property="group"]').forEach((select) => {
        const value = select.value;
        select.innerHTML = options(
          draft.paletteGroups.map((group) => [group.id, group.name]),
          value,
        );
      });
    } else if (node.dataset.viewName != null)
      draft.views[Number(node.dataset.viewName)].name = node.value;
    else if (node.dataset.viewType != null) {
      const view = draft.views[Number(node.dataset.viewType)];
      view.types = node.checked
        ? [...new Set([...view.types, node.value])]
        : view.types.filter((type) => type !== node.value);
    } else if (node.matches("[data-root-type]")) draft.rootType = node.value;
    else return;
    changed();
  };
  container.oninput = update;
  container.onchange = update;
  container.onclick = async (event) => {
    const button = event.target.closest("button");
    if (!button) return;
    if (button.hasAttribute("data-add-group"))
      draft.paletteGroups.push({ id: `group-${crypto.randomUUID()}`, name: "New group" });
    else if (button.dataset.removeGroup != null) {
      const [removed] = draft.paletteGroups.splice(Number(button.dataset.removeGroup), 1);
      draft.elements
        .filter((element) => element.group === removed.id)
        .forEach((element) => {
          element.group = draft.paletteGroups[0].id;
        });
    } else if (button.hasAttribute("data-add-view"))
      draft.views.push({
        id: `view-${crypto.randomUUID()}`,
        name: "New view",
        types: draft.elements.map((element) => element.type),
      });
    else if (button.dataset.removeView != null)
      draft.views.splice(Number(button.dataset.removeView), 1);
    else if (button.hasAttribute("data-save-notation")) {
      button.disabled = true;
      try {
        await save(structuredClone(draft));
        drafts.delete(draftKey);
        container.querySelector("[data-notation-status]").textContent = "Visual notation saved";
        button.textContent = "Save visual notation";
      } catch (error) {
        container.querySelector("[data-notation-status]").textContent = error.message;
      } finally {
        button.disabled = false;
      }
      return;
    } else return;
    render();
    changed();
  };
  render();
}
