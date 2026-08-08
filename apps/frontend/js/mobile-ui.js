import { el } from "./dom.js";

/** Panels open without dimming the canvas. */
export function setMobileBackdropVisible(_visible) {
  if (!el.mobileBackdrop) {
    return;
  }
  el.mobileBackdrop.classList.add("hidden");
}

export function syncMobileDockState() {
  const chatOpen = el.chatWindow && !el.chatWindow.classList.contains("hidden");
  const menuOpen = document.querySelector(".topbar")?.classList.contains("mobile-menu-open");
  const validationOpen = el.validationDrawer && !el.validationDrawer.classList.contains("hidden");

  el.mobileDockPaletteBtn?.classList.toggle(
    "is-active",
    el.workspace?.classList.contains("mobile-left-open"),
  );
  el.mobileDockInspectorBtn?.classList.toggle(
    "is-active",
    el.workspace?.classList.contains("mobile-right-open"),
  );
  el.mobileDockChatBtn?.classList.toggle("is-active", Boolean(chatOpen));
  el.mobileDockMenuBtn?.classList.toggle("is-active", Boolean(menuOpen));
  el.mobileDockValidationBtn?.classList.toggle("is-active", Boolean(validationOpen));
}

export function closeMobilePanels() {
  el.workspace?.classList.remove("mobile-left-open", "mobile-right-open");
  setMobileBackdropVisible(false);
  syncMobileDockState();
}
