// Uses the same select styles as the workbench attribute panel, while keeping
// the original select as the source of truth for forms and change handlers.
const controls = new WeakMap();
let opened;
let nextId = 0;

export function closeThemedSelect() {
  opened?.close();
}

document.addEventListener("pointerdown", (event) => {
  if (opened && !opened.root.contains(event.target)) opened.close();
});
document.addEventListener(
  "scroll",
  (event) => {
    if (opened && !opened.menu.contains(event.target)) opened.position();
  },
  true,
);
window.addEventListener("resize", closeThemedSelect);

export function enhanceSelects(container) {
  container.querySelectorAll("select").forEach(enhanceSelect);
}

export function enhanceSelect(select) {
  if (controls.has(select)) {
    controls.get(select).sync();
    return;
  }
  const doc = select.ownerDocument;
  const label =
    select.getAttribute("aria-label") ||
    Array.from(select.labels?.[0]?.childNodes || [])
      .filter((node) => node.nodeType === 3)
      .map((node) => node.textContent.trim())
      .join(" ") ||
    "Choose an option";
  const root = doc.createElement("div");
  root.className = "attr-custom-select themed-select";
  const trigger = doc.createElement("button");
  trigger.type = "button";
  trigger.className = "attr-custom-select-trigger";
  trigger.setAttribute("aria-haspopup", "listbox");
  trigger.setAttribute("aria-expanded", "false");
  const caption = doc.createElement("span");
  const caret = doc.createElement("span");
  caret.className = "attr-custom-select-caret";
  caret.setAttribute("aria-hidden", "true");
  trigger.append(caption, caret);
  const menu = doc.createElement("div");
  menu.className = "attr-custom-select-menu has-search themed-select-menu";
  menu.hidden = true;
  const searchWrap = doc.createElement("div");
  searchWrap.className = "attr-custom-select-search";
  const search = doc.createElement("input");
  search.type = "search";
  search.className = "attr-custom-select-search-input";
  search.placeholder = "Search…";
  search.autocomplete = "off";
  search.setAttribute("aria-label", `Search ${label}`);
  searchWrap.append(search);
  const list = doc.createElement("div");
  list.id = `themed-select-${++nextId}`;
  list.className = "attr-custom-select-options";
  list.setAttribute("role", "listbox");
  list.setAttribute("aria-label", label);
  trigger.setAttribute("aria-controls", list.id);
  const empty = doc.createElement("div");
  empty.className = "attr-custom-select-empty";
  empty.textContent = "No matching options";
  empty.hidden = true;
  empty.setAttribute("role", "status");
  menu.append(searchWrap, list, empty);
  root.append(trigger, menu);
  select.hidden = true;
  select.classList.add("attr-native-source");
  select.after(root);

  const options = () => [...list.children].filter((option) => !option.hidden && !option.disabled);
  const close = (restoreFocus = false) => {
    menu.hidden = true;
    root.classList.remove("is-open");
    trigger.setAttribute("aria-expanded", "false");
    if (opened?.root === root) opened = undefined;
    if (restoreFocus && trigger.isConnected) trigger.focus();
  };
  const filter = () => {
    const query = search.value.trim().toLocaleLowerCase();
    [...list.children].forEach((option) => {
      option.hidden = !option.textContent.toLocaleLowerCase().includes(query);
    });
    empty.hidden = options().length > 0;
  };
  const sync = () => {
    caption.textContent = select.selectedOptions[0]?.textContent || "Choose an option";
    trigger.setAttribute("aria-label", `${label}: ${caption.textContent}`);
    trigger.disabled = select.disabled;
    if (select.disabled) close();
    list.replaceChildren();
    for (const source of select.options) {
      if (source.hidden) continue;
      const option = doc.createElement("button");
      option.type = "button";
      option.tabIndex = -1;
      option.className = `attr-custom-select-option${source.selected ? " is-active" : ""}`;
      option.setAttribute("role", "option");
      option.setAttribute("aria-selected", String(source.selected));
      option.textContent = source.textContent;
      if (source.selected) {
        const check = doc.createElement("span");
        check.className = "themed-select-check";
        check.setAttribute("aria-hidden", "true");
        check.textContent = "✓";
        option.append(check);
      }
      option.disabled = source.disabled || source.parentElement.disabled === true;
      option.addEventListener("click", () => {
        if (select.disabled || option.disabled) return;
        select.value = source.value;
        close(true);
        select.dispatchEvent(new Event("change", { bubbles: true }));
        sync();
      });
      list.append(option);
    }
    searchWrap.hidden = select.options.length < 6;
    filter();
  };
  const position = () => {
    // Fixed positioning keeps menus visible outside scrolling forms and trees,
    // but inside the modal's top layer. Open upward near the viewport edge.
    const rect = trigger.getBoundingClientRect();
    const bounds = select.closest("dialog")?.getBoundingClientRect() || {
      top: 0,
      bottom: innerHeight,
      left: 0,
      right: innerWidth,
    };
    const below = bounds.bottom - rect.bottom - 12;
    const above = rect.top - bounds.top - 12;
    const upward = below < 220 && above > below;
    const width = Math.min(
      Math.max(rect.width, select.dataset.menuWidth ? Number(select.dataset.menuWidth) : 0),
      bounds.right - bounds.left - 20,
    );
    menu.style.width = `${width}px`;
    menu.style.left = `${Math.max(bounds.left + 8, Math.min(rect.left, bounds.right - width - 8))}px`;
    menu.style.maxHeight = `${Math.max(60, Math.min(280, upward ? above : below))}px`;
    menu.style.top = upward ? "auto" : `${rect.bottom + 5}px`;
    menu.style.bottom = upward ? `${innerHeight - rect.top + 5}px` : "auto";
  };
  const open = (direction = 0) => {
    if (select.disabled) return;
    closeThemedSelect();
    sync();
    search.value = "";
    filter();
    menu.hidden = false;
    root.classList.add("is-open");
    trigger.setAttribute("aria-expanded", "true");
    opened = { root, menu, close, position };
    position();
    const choices = options();
    const selected = choices.find((option) => option.getAttribute("aria-selected") === "true");
    (direction
      ? selected || (direction > 0 ? choices[0] : choices.at(-1))
      : !searchWrap.hidden
        ? search
        : selected || choices[0]
    )?.focus({ preventScroll: true });
  };
  trigger.addEventListener("click", () => (menu.hidden ? open() : close()));
  search.addEventListener("input", filter);
  root.addEventListener("keydown", (event) => {
    if (event.key === "Escape" && !menu.hidden) {
      event.preventDefault();
      event.stopPropagation();
      close(true);
    } else if (["ArrowDown", "ArrowUp", "Home", "End"].includes(event.key)) {
      if (event.target === search && ["Home", "End"].includes(event.key)) return;
      event.preventDefault();
      if (menu.hidden) return open(event.key === "ArrowUp" || event.key === "End" ? -1 : 1);
      const choices = options();
      const index = choices.indexOf(doc.activeElement);
      const target =
        event.key === "Home"
          ? 0
          : event.key === "End"
            ? choices.length - 1
            : event.key === "ArrowDown"
              ? (index + 1) % choices.length
              : (index <= 0 ? choices.length : index) - 1;
      choices[target]?.focus({ preventScroll: true });
      choices[target]?.scrollIntoView({ block: "nearest" });
    } else if (event.key === "Enter" && event.target === search) {
      event.preventDefault();
      options()[0]?.click();
    } else if (event.key === "Tab") close(true);
  });
  root.addEventListener("focusout", (event) => {
    if (!root.contains(event.relatedTarget)) close();
  });
  select.addEventListener("change", sync);
  select.addEventListener("invalid", (event) => {
    event.preventDefault();
    trigger.focus();
    trigger.setAttribute("aria-invalid", "true");
  });
  select.addEventListener("change", () => trigger.removeAttribute("aria-invalid"));
  new MutationObserver(sync).observe(select, {
    attributes: true,
    childList: true,
    characterData: true,
    subtree: true,
  });
  controls.set(select, { sync });
  sync();
}
