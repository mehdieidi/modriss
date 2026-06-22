import { el } from "./dom.js";
import { isMobileViewport } from "./responsive.js";

function userMenuDesktopSlot() {
  return document.querySelector(".topbar-strip-actions");
}

function userMenuMobileSlot() {
  return document.querySelector(".mobile-dock-profile-slot");
}

export function syncMobileChromeLayout() {
  const userMenu = document.querySelector(".user-menu");
  const desktopSlot = userMenuDesktopSlot();
  const mobileSlot = userMenuMobileSlot();
  if (!userMenu || !desktopSlot || !mobileSlot) {
    return;
  }
  const targetSlot = isMobileViewport() ? mobileSlot : desktopSlot;
  if (userMenu.parentElement !== targetSlot) {
    targetSlot.appendChild(userMenu);
  }
}

/** Panels open without dimming the canvas. */
export function setMobileBackdropVisible(_visible) {
  if (!el.mobileBackdrop) {
    return;
  }
  el.mobileBackdrop.classList.add("hidden");
}

export function syncMobileDockState() {
  syncMobileChromeLayout();
  const chatOpen = el.chatWindow && !el.chatWindow.classList.contains("hidden");
  const menuOpen = document.querySelector(".topbar")?.classList.contains("mobile-menu-open");
  const validationOpen = el.validationDrawer && !el.validationDrawer.classList.contains("hidden");
  const profileOpen = document.querySelector(".user-menu.is-open");

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
  el.mobileDockProfileBtn?.classList.toggle("is-active", Boolean(profileOpen));
}

export function closeMobilePanels() {
  el.workspace?.classList.remove("mobile-left-open", "mobile-right-open");
  setMobileBackdropVisible(false);
  syncMobileDockState();
}
