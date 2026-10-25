import { escapeHtml } from "./utils.js";

let closeActivePicker = null;

/** A class can fit several Ecore features; let the modeler choose its role. */
export function chooseContainmentSlot(entries, { label, clientX, clientY }) {
  closeActivePicker?.();
  const picker = document.createElement("div");
  picker.className = "container-target-picker";
  picker.setAttribute("role", "dialog");
  picker.setAttribute("aria-label", `Choose containment in ${label}`);
  picker.style.left = `${Math.max(16, Math.min(clientX, window.innerWidth - 316))}px`;
  picker.style.top = `${Math.max(16, Math.min(clientY, window.innerHeight - 356))}px`;
  picker.innerHTML = `
    <div class="container-target-picker-head">
      <span>Choose containment</span>
      <button class="container-target-picker-close" type="button" aria-label="Cancel">&times;</button>
    </div>
    <div class="container-target-picker-body">
      ${entries
        .map(
          (
            entry,
            index,
          ) => `<button class="container-target-option" type="button" data-slot-index="${index}">
        <span class="container-target-option-label">${escapeHtml(entry.title || entry.feature)}</span>
        <span class="container-target-option-meta">${escapeHtml(entry.feature)} · ${escapeHtml(entry.targetType)}</span>
      </button>`,
        )
        .join("")}
    </div>`;
  document.body.appendChild(picker);
  return new Promise((resolve) => {
    const close = (entry = null) => {
      picker.remove();
      document.removeEventListener("pointerdown", outsideClick);
      document.removeEventListener("keydown", onKeyDown);
      closeActivePicker = null;
      resolve(entry);
    };
    const outsideClick = (event) => {
      if (!picker.contains(event.target)) close();
    };
    const onKeyDown = (event) => {
      if (event.key === "Escape") {
        event.preventDefault();
        close();
      }
    };
    closeActivePicker = () => close();
    picker.querySelector(".container-target-picker-close").addEventListener("click", () => close());
    picker.querySelectorAll("[data-slot-index]").forEach((button) => {
      button.addEventListener("click", () => close(entries[Number(button.dataset.slotIndex)]));
    });
    document.addEventListener("pointerdown", outsideClick);
    document.addEventListener("keydown", onKeyDown);
    picker.querySelector("[data-slot-index]")?.focus();
  });
}
