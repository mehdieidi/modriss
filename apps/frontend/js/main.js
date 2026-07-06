import { el } from "./dom.js";
import { state } from "./state.js";
import { initTheme, toggleTheme } from "./theme.js";
import {
  applyViewport,
  fitViewportToDiagram,
  getModelingRendererDebug,
  initializeModelingRenderer,
  renderDiagram,
  renderDiagramAsync,
  renderPalette,
  resetCanvasView,
  setupDnD,
  zoomCanvasBy,
} from "./canvas.js";
import {
  exportActiveModel,
  generateForCurrentContext,
  importActiveModel,
  saveCurrentModel,
  switchTab,
  undoLastEdit,
  validateCurrentModel,
} from "./model-ops.js";
import { initArtifactEditor, saveCurrentFile, toggleArtifactTreeCollapsed } from "./artifact.js";
import {
  applyAttributePanel,
  bindConnectionDrawStateListener,
  closeAttributePanel,
  deleteSelection,
  openRootModelAttributePanel,
} from "./attr-panel.js";
import { closeImpactPanel, toggleImpactMode } from "./impact.js";
import {
  closeChatHistoryPanel,
  handleChatSendButtonClick,
  initChatComposer,
  prepareChatWindow,
  sendChatMessage,
  startNewChatConversation,
  syncChatOpenState,
  toggleChatHistoryPanel,
  uploadChatAttachment,
  updateChatAttachmentLabel,
} from "./chat.js?v=chat-stop-20260704a";
import {
  bindProjectDialogActions,
  deleteCurrentProject,
  downloadCurrentProject,
  restoreLastProjectIfPossible,
  showProjectMembersDialog,
  showProjectDialog,
} from "./project.js";
import { setError, setBusy, setStatus } from "./status.js";
import { beginModelSave } from "./model-save-ui.js";
import { formatUserError } from "./errors.js";
import { CHAT_ATTACHMENT_MAX_BYTES } from "./config.js";
import { isMobileViewport } from "./responsive.js";
import { closeMobilePanels, setMobileBackdropVisible, syncMobileDockState } from "./mobile-ui.js";
import { ensureAuthenticated, logout, updateDisplayName } from "./auth.js";
import { initSvgIconMasks } from "./icons.js";
import {
  isModelingLevel,
  loadModelingConfig,
  modelingLevelConfig,
  modelingLevelKeys,
  modelingLevelListLabel,
} from "./modeling-config-data.js";
import { hasUnsavedModelChanges, updateModelSaveUi } from "./model-save-ui.js";
import { initViewWorkbench, renderViewWorkbench } from "./view-explorer.js";
import { installG6LargeGraphDevHelper } from "./graph-editor/g6-devtools.js";
import {
  initGuidedModeling,
  onGuidedModelingContextChanged,
  showPalettePane,
  showMethodologyPane,
  syncMethodologyRailState,
} from "./guided-modeling.js";

const TOPBAR_MENU_BREAKPOINT = 1100;
const CHAT_INPUT_MAX_HEIGHT = 132;
const getElementTarget = (event) => (event.target instanceof Element ? event.target : null);

window.modlessFrontendBoot = {
  ...(window.modlessFrontendBoot || {}),
  mainModuleLoaded: true,
  mainBuild: "g6-wired-2026-05-31-02",
};
window.modlessG6Debug =
  window.modlessG6Debug ||
  (() => ({
    bootstrap: window.modlessFrontendBoot || null,
    ...getModelingRendererDebug(),
  }));

function shouldShowNotFoundPage() {
  const path = window.location.pathname.replace(/\/+$/, "") || "/";
  return path !== "/" && path !== "/index.html";
}

function showNotFoundPage() {
  if (!el.notFoundOverlay) {
    return false;
  }
  document.title = "Not Found - Modless";
  el.notFoundOverlay.classList.remove("hidden");
  document.body.classList.add("modal-open");
  el.notFoundGoHomeBtn?.addEventListener(
    "click",
    () => {
      window.history.replaceState({}, "", "/");
      window.location.reload();
    },
    { once: true },
  );
  return true;
}

function refreshCurrentUserLabel() {
  if (!el.currentUserLabel) {
    return;
  }
  el.currentUserLabel.textContent =
    state.auth.user?.displayName || state.auth.user?.email || "User";
}

function showUnsavedModelDialog() {
  if (!el.unsavedModelOverlay || !el.unsavedModelSaveBtn || !el.unsavedModelDismissBtn) {
    return;
  }
  el.unsavedModelOverlay.classList.remove("hidden");
  document.body.classList.add("modal-open");
  el.unsavedModelSaveBtn.disabled = false;
  el.unsavedModelSaveBtn.textContent = "Save Model";
  el.unsavedModelSaveBtn.focus();
}

function hideUnsavedModelDialog() {
  el.unsavedModelOverlay?.classList.add("hidden");
  document.body.classList.remove("modal-open");
  if (el.unsavedModelSaveBtn) {
    el.unsavedModelSaveBtn.disabled = false;
    el.unsavedModelSaveBtn.textContent = "Save Model";
  }
}

function bindUnsavedModelGuard() {
  window.addEventListener("beforeunload", (event) => {
    if (!hasUnsavedModelChanges()) {
      return;
    }
    showUnsavedModelDialog();
    event.preventDefault();
    event.returnValue = "";
  });

  el.unsavedModelDismissBtn?.addEventListener("click", hideUnsavedModelDialog);
  el.unsavedModelOverlay?.addEventListener("click", (event) => {
    if (event.target === el.unsavedModelOverlay) {
      hideUnsavedModelDialog();
    }
  });
  el.unsavedModelSaveBtn?.addEventListener("click", async () => {
    try {
      el.unsavedModelSaveBtn.disabled = true;
      el.unsavedModelSaveBtn.textContent = "Saving...";
      beginModelSave();
      setBusy("Saving…");
      await saveCurrentModel({ rethrow: true, skipBeginSave: true });
      hideUnsavedModelDialog();
    } catch {
      el.unsavedModelSaveBtn.disabled = false;
      el.unsavedModelSaveBtn.textContent = "Try Saving Again";
    }
  });
}

function showProfileDialog() {
  if (
    !el.profileOverlay ||
    !el.profileDisplayNameInput ||
    !el.profileSaveBtn ||
    !el.profileCancelBtn
  ) {
    setError("Profile editor is unavailable");
    return;
  }
  if (el.profileError) {
    el.profileError.classList.add("hidden");
    el.profileError.textContent = "";
  }
  el.profileDisplayNameInput.value = state.auth.user?.displayName || "";
  el.profileOverlay.classList.remove("hidden");
  document.body.classList.add("modal-open");
  el.profileDisplayNameInput.focus();
  el.profileDisplayNameInput.select();

  const setBusy = (busy) => {
    el.profileSaveBtn.disabled = busy;
    el.profileCancelBtn.disabled = busy;
    el.profileDisplayNameInput.disabled = busy;
  };

  const close = () => {
    el.profileOverlay.classList.add("hidden");
    document.body.classList.remove("modal-open");
    el.profileSaveBtn.removeEventListener("click", onSave);
    el.profileCancelBtn.removeEventListener("click", onCancel);
    el.profileOverlay.removeEventListener("click", onOverlayClick);
    el.profileDisplayNameInput.removeEventListener("keydown", onKeyDown);
  };

  const onCancel = () => close();

  const onSave = async () => {
    try {
      setBusy(true);
      const updatedUser = await updateDisplayName(el.profileDisplayNameInput.value);
      state.auth.user = updatedUser;
      refreshCurrentUserLabel();
      close();
      setStatus("Profile updated");
    } catch (error) {
      if (el.profileError) {
        el.profileError.textContent = formatUserError(error);
        el.profileError.classList.remove("hidden");
      }
      setBusy(false);
    }
  };

  const onOverlayClick = (event) => {
    if (event.target === el.profileOverlay) {
      onCancel();
    }
  };

  const onKeyDown = (event) => {
    if (event.key === "Enter") {
      event.preventDefault();
      onSave();
    } else if (event.key === "Escape") {
      event.preventDefault();
      onCancel();
    }
  };

  el.profileSaveBtn.addEventListener("click", onSave);
  el.profileCancelBtn.addEventListener("click", onCancel);
  el.profileOverlay.addEventListener("click", onOverlayClick);
  el.profileDisplayNameInput.addEventListener("keydown", onKeyDown);
}

function bindUserMenuActions() {
  el.userMenuEditProfileBtn?.addEventListener("click", showProfileDialog);
  el.userMenuLogoutBtn?.addEventListener("click", async () => {
    try {
      await logout();
    } finally {
      window.location.reload();
    }
  });
}

// Bind actions that must remain available even if later non-critical UI setup fails.
function bindCriticalActions() {
  // Generation
  el.generateContextBtn?.addEventListener("click", async () => {
    if (state.activeType === "artifact") {
      downloadCurrentProject();
      return;
    }
    await generateForCurrentContext();
  });
  el.validateModelBtn?.addEventListener("click", validateCurrentModel);

  // Project dialog
  el.switchProjectBtn?.addEventListener("click", showProjectDialog);
  el.manageProjectMembersBtn?.addEventListener("click", showProjectMembersDialog);
  el.deleteProjectBtn?.addEventListener("click", deleteCurrentProject);
  bindUserMenuActions();
}

function setupIdeMenus() {
  const menus = Array.from(document.querySelectorAll(".ide-menu"));
  if (!menus.length) {
    return;
  }
  const VIEWPORT_GUTTER = 8;

  const closeMenus = () => {
    menus.forEach((menu) => menu.classList.remove("is-open"));
  };

  const positionMenuItems = (menu, menuItems) => {
    menuItems.style.position = "";
    menuItems.style.left = "0";
    menuItems.style.right = "auto";
    menuItems.style.top = "calc(100% + 6px)";
    menuItems.style.bottom = "auto";
    menuItems.style.transform = "translateX(0)";

    if (isMobileViewport() && menu.classList.contains("user-menu") && el.mobileDockProfileBtn) {
      const anchor = el.mobileDockProfileBtn.getBoundingClientRect();
      menuItems.style.position = "fixed";
      menuItems.style.left = "auto";
      menuItems.style.right = `${Math.max(8, window.innerWidth - anchor.right)}px`;
      menuItems.style.bottom = `${Math.max(8, window.innerHeight - anchor.top + 8)}px`;
      menuItems.style.top = "auto";
      menuItems.style.transform = "none";
      menuItems.style.zIndex = "110";
      return;
    }

    const triggerRect = menu.getBoundingClientRect();
    if (triggerRect.left > window.innerWidth / 2) {
      menuItems.style.left = "auto";
      menuItems.style.right = "0";
    }

    let itemsRect = menuItems.getBoundingClientRect();
    let shiftX = 0;
    if (itemsRect.right > window.innerWidth - VIEWPORT_GUTTER) {
      shiftX -= itemsRect.right - (window.innerWidth - VIEWPORT_GUTTER);
    }
    if (itemsRect.left + shiftX < VIEWPORT_GUTTER) {
      shiftX += VIEWPORT_GUTTER - (itemsRect.left + shiftX);
    }
    if (shiftX !== 0) {
      menuItems.style.transform = `translateX(${Math.round(shiftX)}px)`;
      itemsRect = menuItems.getBoundingClientRect();
    }

    if (itemsRect.bottom > window.innerHeight - VIEWPORT_GUTTER) {
      menuItems.style.top = "auto";
      menuItems.style.bottom = "calc(100% + 6px)";
    }
  };

  // (no global overlay)

  const positionOpenMenus = () => {
    menus.forEach((menu) => {
      if (!menu.classList.contains("is-open")) {
        return;
      }
      const menuItems = menu.querySelector(".ide-menu-items");
      if (!menuItems) {
        return;
      }
      positionMenuItems(menu, menuItems);
    });
  };

  menus.forEach((menu) => {
    const triggers = menu.querySelectorAll(".ide-menu-trigger, .mobile-dock-profile-trigger");
    const menuItems = menu.querySelector(".ide-menu-items");
    if (!triggers.length || !menuItems) {
      return;
    }

    const openMenu = (event) => {
      event.stopPropagation();
      const willOpen = !menu.classList.contains("is-open");
      if (willOpen && menu.classList.contains("user-menu")) {
        closeMobilePanels();
      }
      closeMenus();
      menu.classList.toggle("is-open", willOpen);
      if (willOpen) {
        requestAnimationFrame(() => {
          positionMenuItems(menu, menuItems);
        });
      }
      syncMobileDockState();
    };

    triggers.forEach((trigger) => {
      trigger.addEventListener("click", openMenu);
    });

    menuItems.addEventListener("click", (event) => {
      const target = getElementTarget(event);
      if (target?.closest("button")) {
        closeMenus();
        if (window.innerWidth <= TOPBAR_MENU_BREAKPOINT) {
          closeTopbarMenu();
        }
      }
    });
  });

  document.addEventListener("click", (event) => {
    const target = getElementTarget(event);
    if (target?.closest(".ide-menu")) {
      return;
    }
    closeMenus();
    syncMobileDockState();
    if (!target?.closest(".topbar")) {
      closeTopbarMenu();
    }
  });

  document.addEventListener("keydown", (event) => {
    if (event.key === "Escape") {
      closeMenus();
    }
  });

  window.addEventListener("resize", positionOpenMenus);
  window.addEventListener("scroll", positionOpenMenus, { passive: true });
}

function closeTopbarMenu() {
  document.querySelector(".topbar")?.classList.remove("mobile-menu-open");
  if (el.topbarMenuToggleBtn) {
    el.topbarMenuToggleBtn.setAttribute("aria-expanded", "false");
  }
  syncMobileDockState();
}

function toggleTopbarMenu() {
  if (window.innerWidth > TOPBAR_MENU_BREAKPOINT) {
    return;
  }
  const topbar = document.querySelector(".topbar");
  if (!topbar) {
    return;
  }
  const willOpen = !topbar.classList.contains("mobile-menu-open");
  topbar.classList.toggle("mobile-menu-open", willOpen);
  if (el.topbarMenuToggleBtn) {
    el.topbarMenuToggleBtn.setAttribute("aria-expanded", String(willOpen));
  }
  if (willOpen) {
    closeMobilePanels();
  }
  syncMobileDockState();
}

function toggleMobileSidebar() {
  if (!isMobileViewport()) {
    return;
  }
  const willOpen = !el.workspace.classList.contains("mobile-left-open");
  el.workspace.classList.remove("mobile-right-open");
  el.workspace.classList.toggle("mobile-left-open", willOpen);
  setMobileBackdropVisible(willOpen);
  if (willOpen) {
    closeTopbarMenu();
  }
  syncMobileDockState();
}

function toggleMobileInspector() {
  if (!isMobileViewport()) {
    return;
  }
  const inspectorVisible =
    !el.attributePanel.classList.contains("hidden") ||
    !el.impactPanel.classList.contains("hidden") ||
    (el.modelTreePanel && !el.modelTreePanel.classList.contains("hidden"));
  if (!inspectorVisible) {
    setStatus("Select an element or enable Impact mode first");
    return;
  }
  const willOpen = !el.workspace.classList.contains("mobile-right-open");
  el.workspace.classList.remove("mobile-left-open");
  el.workspace.classList.toggle("mobile-right-open", willOpen);
  setMobileBackdropVisible(willOpen);
  if (willOpen) {
    closeTopbarMenu();
  }
  syncMobileDockState();
}

function toggleMobileChatFromDock() {
  closeMobilePanels();
  closeTopbarMenu();
  el.chatToggle?.click();
}

function syncResponsiveUi() {
  if (!isMobileViewport()) {
    closeMobilePanels();
  }
  if (isMobileViewport()) {
    closeRailMenus();
  }
  if (window.innerWidth > TOPBAR_MENU_BREAKPOINT) {
    closeTopbarMenu();
  }
  syncMobileDockState();
}

function syncPaletteRailToggleState() {
  syncMethodologyRailState();
}

function togglePaletteRail() {
  if (!el.workspace) {
    return;
  }
  const hidden = el.workspace.classList.contains("palette-hidden");
  if (hidden) {
    el.workspace.classList.remove("palette-hidden");
    showPalettePane();
  } else if (state.leftPaneMode === "methodology") {
    showPalettePane();
  } else {
    el.workspace.classList.add("palette-hidden");
  }
  closeRailMenus();
  syncMethodologyRailState();
}

function toggleMethodologyRail() {
  if (!el.workspace) {
    return;
  }
  closeRailMenus();
  const hidden = el.workspace.classList.contains("palette-hidden");
  const showingMethodology = state.leftPaneMode === "methodology" && !hidden;
  if (showingMethodology) {
    el.workspace.classList.add("palette-hidden");
  } else {
    showMethodologyPane();
  }
  syncMethodologyRailState();
}

function closeRailMenus() {
  el.fileRailMenu?.classList.add("hidden");
  el.projectRailMenu?.classList.add("hidden");
  el.helpRailPanel?.classList.add("hidden");
  el.exportModelSubmenu?.classList.add("hidden");
  el.importModelSubmenu?.classList.add("hidden");
  el.exportModelBtn?.setAttribute("aria-expanded", "false");
  el.importModelBtn?.setAttribute("aria-expanded", "false");
  el.fileBtn?.classList.remove("active");
  el.settingsRailBtn?.classList.remove("active");
  el.helpRailBtn?.classList.remove("active");
}

function toggleFileSubmenu(kind) {
  const targetMenu = kind === "export" ? el.exportModelSubmenu : el.importModelSubmenu;
  const targetBtn = kind === "export" ? el.exportModelBtn : el.importModelBtn;
  const otherMenu = kind === "export" ? el.importModelSubmenu : el.exportModelSubmenu;
  const otherBtn = kind === "export" ? el.importModelBtn : el.exportModelBtn;
  if (!targetMenu || !targetBtn) {
    return;
  }
  const willOpen = targetMenu.classList.contains("hidden");
  otherMenu?.classList.add("hidden");
  otherBtn?.setAttribute("aria-expanded", "false");
  targetMenu.classList.toggle("hidden", !willOpen);
  targetBtn.setAttribute("aria-expanded", String(willOpen));
}

function toggleRailMenu(kind) {
  if (isMobileViewport()) {
    return;
  }
  const menus = {
    file: { menu: el.fileRailMenu, button: el.fileBtn },
    project: { menu: el.projectRailMenu, button: el.settingsRailBtn },
    help: { menu: el.helpRailPanel, button: el.helpRailBtn },
  };
  const targetMenu = menus[kind]?.menu;
  const targetBtn = menus[kind]?.button;
  if (!targetMenu || !targetBtn) {
    return;
  }
  const willOpen = targetMenu.classList.contains("hidden");
  Object.values(menus).forEach(({ menu, button }) => {
    if (menu !== targetMenu) {
      menu?.classList.add("hidden");
      button?.classList.remove("active");
    }
  });
  if (!willOpen) {
    targetMenu.classList.add("hidden");
    targetBtn.classList.remove("active");
    return;
  }

  targetMenu.classList.remove("hidden");
  targetBtn.classList.add("active");

  const railRect = targetBtn.parentElement?.getBoundingClientRect();
  const btnRect = targetBtn.getBoundingClientRect();
  if (!railRect) {
    return;
  }

  const gutter = 8;
  const menuRect = targetMenu.getBoundingClientRect();
  const minTop = gutter;
  const maxTop = Math.max(minTop, window.innerHeight - railRect.top - menuRect.height - gutter);
  const preferredTop = btnRect.top - railRect.top;
  const clampedTop = Math.min(maxTop, Math.max(minTop, preferredTop));
  targetMenu.style.top = `${Math.round(clampedTop)}px`;
}

function renderConfiguredModelTabs() {
  if (!el.modelTabs) {
    return;
  }
  const projectBadge = el.modelTabs.querySelector(".project-name-badge");
  el.modelTabs.querySelectorAll(".tab").forEach((tab) => tab.remove());
  const fragment = document.createDocumentFragment();
  for (const typeKey of modelingLevelKeys()) {
    const level = modelingLevelConfig(typeKey);
    const button = document.createElement("button");
    button.className = "tab";
    button.dataset.type = typeKey;
    button.title = level.displayName || typeKey;
    button.type = "button";
    button.textContent = level.displayName || typeKey.toUpperCase();
    fragment.appendChild(button);
  }
  const artifactButton = document.createElement("button");
  artifactButton.className = "tab";
  artifactButton.dataset.type = "artifact";
  artifactButton.title =
    state.modelingConfig.config?.artifactAction?.buttonTitle || "Generated artifacts";
  artifactButton.type = "button";
  artifactButton.textContent = "Artifacts";
  fragment.appendChild(artifactButton);
  el.modelTabs.insertBefore(fragment, projectBadge || null);
}

// ── Bind all DOM event handlers ───────────────────────────────────────────────

function bindEvents() {
  bindCriticalActions();

  // Tab switching
  el.modelTabs?.addEventListener("click", (event) => {
    const target = getElementTarget(event);
    const btn = target?.closest(".tab");
    if (!btn) {
      return;
    }
    closeMobilePanels();
    closeTopbarMenu();
    switchTab(btn.dataset.type);
  });

  // Theme
  if (el.paletteRailToggleBtn) {
    el.paletteRailToggleBtn.addEventListener("click", togglePaletteRail);
  }
  if (el.methodologyRailBtn) {
    el.methodologyRailBtn.addEventListener("click", toggleMethodologyRail);
  }
  if (el.fileBtn) {
    el.fileBtn.addEventListener("click", (event) => {
      event.stopPropagation();
      toggleRailMenu("file");
    });
  }
  if (el.settingsRailBtn) {
    el.settingsRailBtn.addEventListener("click", (event) => {
      event.stopPropagation();
      toggleRailMenu("project");
    });
  }
  if (el.helpRailBtn) {
    el.helpRailBtn.addEventListener("click", (event) => {
      event.stopPropagation();
      toggleRailMenu("help");
    });
  }
  if (el.fileRailMenu) {
    el.fileRailMenu.addEventListener("click", (event) => {
      if (getElementTarget(event)?.closest(".rail-submenu-trigger")) {
        return;
      }
      if (getElementTarget(event)?.closest("button")) {
        closeRailMenus();
      }
    });
  }
  if (el.projectRailMenu) {
    el.projectRailMenu.addEventListener("click", (event) => {
      if (getElementTarget(event)?.closest("button")) {
        closeRailMenus();
      }
    });
  }
  if (el.themeRailToggleBtn) {
    el.themeRailToggleBtn.addEventListener("click", toggleTheme);
  }
  if (el.undoModelReplaceBtn) {
    el.undoModelReplaceBtn.addEventListener("click", async () => {
      try {
        await undoLastEdit();
      } catch (error) {
        setError(error, { prefix: "Undo failed." });
      }
    });
  }
  if (el.exportModelBtn) {
    el.exportModelBtn.addEventListener("click", (event) => {
      event.stopPropagation();
      toggleFileSubmenu("export");
    });
  }
  if (el.importModelBtn) {
    el.importModelBtn.addEventListener("click", (event) => {
      event.stopPropagation();
      toggleFileSubmenu("import");
    });
  }
  if (el.exportModelJsonBtn) {
    el.exportModelJsonBtn.addEventListener("click", async () => {
      try {
        await exportActiveModel("json");
      } catch (error) {
        setError(error, { prefix: "Export failed." });
      }
    });
  }
  if (el.exportModelXmiBtn) {
    el.exportModelXmiBtn.addEventListener("click", async () => {
      try {
        await exportActiveModel("xmi");
      } catch (error) {
        setError(error, { prefix: "Export failed." });
      }
    });
  }
  if (el.importModelJsonBtn) {
    el.importModelJsonBtn.addEventListener("click", () => {
      if (!isModelingLevel(state.activeType)) {
        setStatus(`Switch to ${modelingLevelListLabel()} to import model JSON.`);
        return;
      }
      if (el.importModelFileInput) {
        el.importModelFileInput.accept = ".json,application/json";
        el.importModelFileInput.dataset.importFormat = "json";
        el.importModelFileInput.dataset.importType = state.activeType;
        el.importModelFileInput.click();
      }
    });
  }
  if (el.importModelXmiBtn) {
    el.importModelXmiBtn.addEventListener("click", () => {
      if (!isModelingLevel(state.activeType)) {
        setStatus(`Switch to ${modelingLevelListLabel()} to import model XMI.`);
        return;
      }
      if (el.importModelFileInput) {
        el.importModelFileInput.accept = ".xmi,application/xml,text/xml";
        el.importModelFileInput.dataset.importFormat = "xmi";
        el.importModelFileInput.dataset.importType = state.activeType;
        el.importModelFileInput.click();
      }
    });
  }
  if (el.importModelFileInput) {
    el.importModelFileInput.addEventListener("change", async (event) => {
      const file = event.target.files?.[0];
      const format = event.target.dataset.importFormat || "json";
      const type = event.target.dataset.importType || state.activeType;
      try {
        await importActiveModel(file, format, type);
      } catch (error) {
        setError(error, { prefix: "Import failed." });
      } finally {
        event.target.value = "";
        delete event.target.dataset.importFormat;
        delete event.target.dataset.importType;
      }
    });
  }
  if (el.topbarMenuToggleBtn) {
    el.topbarMenuToggleBtn.addEventListener("click", (event) => {
      event.stopPropagation();
      toggleTopbarMenu();
    });
  }

  if (el.mobileDockPaletteBtn) {
    el.mobileDockPaletteBtn.addEventListener("click", toggleMobileSidebar);
  }
  if (el.mobileDockInspectorBtn) {
    el.mobileDockInspectorBtn.addEventListener("click", toggleMobileInspector);
  }
  el.mobileDockChatBtn?.addEventListener("click", toggleMobileChatFromDock);
  el.mobileDockValidationBtn?.addEventListener("click", (event) => {
    event.stopPropagation();
    closeMobilePanels();
    el.validationFab?.click();
    syncMobileDockState();
  });
  el.mobileDockMenuBtn?.addEventListener("click", (event) => {
    event.stopPropagation();
    closeMobilePanels();
    toggleTopbarMenu();
  });

  if (el.mobileBackdrop) {
    el.mobileBackdrop.addEventListener("click", closeMobilePanels);
  }

  if (el.paletteSearchInput) {
    el.paletteSearchInput.addEventListener("input", () => {
      if (!isModelingLevel(state.activeType)) {
        return;
      }
      state.paletteSearch[state.activeType] = el.paletteSearchInput.value || "";
      renderPalette();
    });
  }

  window.addEventListener("resize", syncResponsiveUi);
  document.addEventListener("click", (event) => {
    if (getElementTarget(event)?.closest("#saveModelBtn")) {
      event.preventDefault();
      if (isModelingLevel(state.activeType)) {
        beginModelSave();
        setBusy("Saving…");
      }
      void saveCurrentModel({ rethrow: true, skipBeginSave: true }).catch(() => {
        // saveCurrentModel updates the visible save status.
      });
      return;
    }
    if (getElementTarget(event)?.closest(".workspace-rail")) {
      return;
    }
    closeRailMenus();
  });
  document.addEventListener("keydown", (event) => {
    if (event.key === "Escape") {
      closeRailMenus();
    }
  });
  document.addEventListener("keydown", async (event) => {
    if (!(event.ctrlKey || event.metaKey) || event.shiftKey || event.altKey) {
      return;
    }
    const key = event.key.toLowerCase();
    if (key !== "z" && key !== "s") {
      return;
    }
    const target = getElementTarget(event);
    if (
      key === "z" &&
      (target?.closest('input, textarea, select, [contenteditable="true"]') ||
        target?.isContentEditable)
    ) {
      return;
    }
    event.preventDefault();
    try {
      if (key === "s") {
        if (isModelingLevel(state.activeType)) {
          beginModelSave();
          setBusy("Saving…");
        }
        await saveCurrentModel({ rethrow: true, skipBeginSave: true });
      } else {
        await undoLastEdit();
      }
    } catch (error) {
      if (key === "z") {
        setError(error, { prefix: "Undo failed." });
      }
    }
  });

  // Canvas interaction
  el.canvasZoomControl?.addEventListener("mousedown", (event) => event.stopPropagation());
  el.canvasZoomControl?.addEventListener("pointerdown", (event) => event.stopPropagation());
  el.canvasZoomControl?.addEventListener("touchstart", (event) => event.stopPropagation(), {
    passive: true,
  });
  el.canvasZoomOutBtn?.addEventListener("click", () => zoomCanvasBy(0.85));
  el.canvasZoomInBtn?.addEventListener("click", () => zoomCanvasBy(1.18));
  el.canvasZoomResetBtn?.addEventListener("click", resetCanvasView);
  el.canvasZoomFitBtn?.addEventListener("click", () => {
    void fitViewportToDiagram({ fit: true });
  });

  const collapseChatInput = () => {
    el.chatInputRow?.classList.remove("chat-input-expanded");
    if (el.chatInput) {
      el.chatInput.rows = 1;
      el.chatInput.style.height = "";
    }
  };

  const expandChatInput = () => {
    el.chatInputRow?.classList.add("chat-input-expanded");
    if (!el.chatInput) {
      return;
    }
    el.chatInput.rows = 1;
    el.chatInput.style.height = "auto";
    el.chatInput.style.height = `${Math.min(el.chatInput.scrollHeight, CHAT_INPUT_MAX_HEIGHT)}px`;
  };

  const clearExpandedChatBounds = () => {
    if (!el.chatWindow) {
      return;
    }
    el.chatWindow.style.removeProperty("--chat-expanded-left");
    el.chatWindow.style.removeProperty("--chat-expanded-top");
    el.chatWindow.style.removeProperty("--chat-expanded-width");
    el.chatWindow.style.removeProperty("--chat-expanded-height");
  };

  const setChatExpanded = (expanded) => {
    el.chatWindow?.classList.toggle("chat-window-expanded", expanded);
    el.workspace?.classList.toggle("chat-expanded", expanded);
    if (expanded) {
      syncExpandedChatBounds();
    } else {
      clearExpandedChatBounds();
    }
    el.chatExpandBtn?.setAttribute("aria-pressed", String(expanded));
    el.chatExpandBtn?.setAttribute(
      "aria-label",
      expanded ? "Restore chat window size" : "Expand chat window",
    );
    if (el.chatExpandBtn) {
      el.chatExpandBtn.title = expanded ? "Restore chat window size" : "Expand chat window";
    }
    if (el.chatExpandIcon) {
      el.chatExpandIcon.style.setProperty(
        "--icon-src",
        `url('/assets/icons/${expanded ? "panel-collapse" : "panel-expand"}.svg')`,
      );
    }
  };

  const syncExpandedChatBounds = () => {
    if (!el.chatWindow?.classList.contains("chat-window-expanded")) {
      clearExpandedChatBounds();
      return;
    }
    const anchor = el.canvasViewport?.classList.contains("hidden")
      ? el.artifactEditor
      : el.canvasViewport;
    const canvasRect = anchor?.getBoundingClientRect();
    if (!canvasRect || canvasRect.width <= 0 || canvasRect.height <= 0) {
      return;
    }

    const rootStyles = getComputedStyle(document.documentElement);
    const stageStyles = getComputedStyle(el.canvasViewport);
    const gap =
      Number.parseFloat(stageStyles.getPropertyValue("--workbench-gap")) ||
      Number.parseFloat(rootStyles.getPropertyValue("--workbench-gap")) ||
      8;
    const topbarRect = document.querySelector(".topbar")?.getBoundingClientRect();
    const modelingBarRect = document
      .querySelector(".modeling-context-bar:not(.hidden)")
      ?.getBoundingClientRect();
    const railRect = document.querySelector(".workspace-rail")?.getBoundingClientRect();
    const rightPaneRect = document
      .querySelector(".right-pane:not(.hidden), .impact-panel:not(.hidden)")
      ?.getBoundingClientRect();

    const left = Math.max(canvasRect.left, railRect?.right || 0) + gap;
    const top =
      Math.max(canvasRect.top, topbarRect?.bottom || 0, modelingBarRect?.bottom || 0) + gap;
    const right = (rightPaneRect?.left || canvasRect.right) - gap;
    const bottom = canvasRect.bottom - gap;

    el.chatWindow.style.setProperty("--chat-expanded-left", `${Math.max(gap, left)}px`);
    el.chatWindow.style.setProperty("--chat-expanded-top", `${Math.max(gap, top)}px`);
    el.chatWindow.style.setProperty("--chat-expanded-width", `${Math.max(320, right - left)}px`);
    el.chatWindow.style.setProperty("--chat-expanded-height", `${Math.max(280, bottom - top)}px`);
  };

  // Chat
  el.chatToggle?.addEventListener("click", () => {
    const willOpen = el.chatWindow.classList.contains("hidden");
    el.chatWindow.classList.toggle("hidden", !willOpen);
    syncChatOpenState();
    if (willOpen) {
      syncExpandedChatBounds();
      prepareChatWindow().catch((error) =>
        setStatus(error, { prefix: "Chat setup failed.", error: true }),
      );
      el.chatInput.focus();
    } else {
      setChatExpanded(false);
    }
  });
  el.chatCloseBtn?.addEventListener("click", () => {
    collapseChatInput();
    setChatExpanded(false);
    el.chatWindow.classList.add("hidden");
    syncChatOpenState();
  });
  el.chatExpandBtn?.addEventListener("click", () => {
    setChatExpanded(!el.chatWindow.classList.contains("chat-window-expanded"));
    el.chatMessages.scrollTop = el.chatMessages.scrollHeight;
  });
  window.addEventListener("resize", syncExpandedChatBounds);
  if (window.ResizeObserver) {
    const chatBoundsObserver = new ResizeObserver(syncExpandedChatBounds);
    if (el.canvasViewport) {
      chatBoundsObserver.observe(el.canvasViewport);
    }
    const modelingBar = document.getElementById("topbarModelingRow");
    if (modelingBar) {
      chatBoundsObserver.observe(modelingBar);
    }
  }
  if (window.MutationObserver && el.workspace) {
    const chatPanelObserver = new MutationObserver(syncExpandedChatBounds);
    chatPanelObserver.observe(el.workspace, {
      attributes: true,
      attributeFilter: ["class"],
      subtree: true,
    });
  }

  el.chatHistoryBtn?.addEventListener("click", () => {
    toggleChatHistoryPanel().catch((error) => {
      setError(error, { prefix: "Chat history failed." });
    });
  });
  el.chatHistoryCloseBtn?.addEventListener("click", closeChatHistoryPanel);
  el.chatNewBtn?.addEventListener("click", () => {
    startNewChatConversation().catch((error) => {
      setError(error, { prefix: "New conversation failed." });
    });
  });

  el.chatInput?.addEventListener("focus", expandChatInput);
  el.chatInput?.addEventListener("input", expandChatInput);
  el.chatInput?.addEventListener("blur", (event) => {
    const related = event.relatedTarget;
    if (related === el.chatSendBtn || el.chatInputRow?.contains(related)) {
      return;
    }
    collapseChatInput();
  });
  el.chatSendBtn?.addEventListener("click", () => {
    collapseChatInput();
    handleChatSendButtonClick();
  });

  el.chatFileInput?.addEventListener("change", async (event) => {
    const file = event.target.files?.[0];
    if (!file) {
      state.chat.attachment = null;
      updateChatAttachmentLabel();
      return;
    }
    if (file.size > CHAT_ATTACHMENT_MAX_BYTES) {
      state.chat.attachment = null;
      event.target.value = "";
      updateChatAttachmentLabel();
      setError(`File too large (${file.size} bytes). Max ${CHAT_ATTACHMENT_MAX_BYTES} bytes.`);
      return;
    }
    try {
      setStatus(`Uploading: ${file.name}`);
      const attachment = await uploadChatAttachment(file);
      if (!attachment) {
        state.chat.attachment = null;
        event.target.value = "";
        updateChatAttachmentLabel();
        return;
      }
      state.chat.attachment = {
        id: attachment.id,
        name: attachment.fileName || file.name,
        sizeBytes: attachment.sizeBytes || file.size,
      };
      updateChatAttachmentLabel();
      setStatus(`Attached: ${file.name}`);
    } catch (error) {
      state.chat.attachment = null;
      event.target.value = "";
      updateChatAttachmentLabel();
      setError(error, { prefix: "Failed to read file." });
    }
  });

  if (el.chatFileClearBtn) {
    el.chatFileClearBtn.addEventListener("click", () => {
      state.chat.attachment = null;
      if (el.chatFileInput) {
        el.chatFileInput.value = "";
      }
      updateChatAttachmentLabel();
      setStatus("Attachment removed");
    });
  }

  el.chatInput?.addEventListener("keydown", (event) => {
    if (event.key === "Enter" && !event.shiftKey) {
      event.preventDefault();
      collapseChatInput();
      sendChatMessage();
    }
  });

  // Artifact explorer
  el.artifactTreeToggleBtn?.addEventListener("click", toggleArtifactTreeCollapsed);

  el.saveFileBtn?.addEventListener("click", saveCurrentFile);

  // Attribute panel
  el.attrPanelCloseBtn?.addEventListener("click", closeAttributePanel);
  bindConnectionDrawStateListener();
  el.attrPanelApplyBtn?.addEventListener("click", applyAttributePanel);
  el.attrPanelDeleteBtn?.addEventListener("click", deleteSelection);
  el.rootModelAttributesBtn?.addEventListener("click", openRootModelAttributePanel);
  document.addEventListener("keydown", (event) => {
    if (event.key !== "Delete" && event.key !== "Backspace") {
      return;
    }
    const target = getElementTarget(event);
    if (
      target?.closest('input, textarea, select, [contenteditable="true"]') ||
      target?.isContentEditable
    ) {
      return;
    }
    if (!state.selectedNodeId && !state.selectedConnectionId) {
      return;
    }
    event.preventDefault();
    deleteSelection();
  });

  // Impact analysis
  el.impactToggleBtn?.addEventListener("click", toggleImpactMode);
  el.impactPanelCloseBtn?.addEventListener("click", closeImpactPanel);
}

// ── Application init ──────────────────────────────────────────────────────────

async function init() {
  initTheme();
  initSvgIconMasks();
  if (shouldShowNotFoundPage() && showNotFoundPage()) {
    return;
  }
  const authState = await ensureAuthenticated();
  await loadModelingConfig();
  initGuidedModeling();
  renderConfiguredModelTabs();
  refreshCurrentUserLabel();
  bindProjectDialogActions();
  setupIdeMenus();
  bindEvents();
  initChatComposer();
  installG6LargeGraphDevHelper({ renderDiagram, renderWorkbench: renderViewWorkbench });
  bindUnsavedModelGuard();
  initViewWorkbench({ renderDiagram, renderPalette });
  syncPaletteRailToggleState();
  syncResponsiveUi();
  try {
    await initializeModelingRenderer();
  } catch (error) {
    console.error("Diagram renderer initialization failed", error);
    setStatus("Diagram renderer unavailable. Check configuration and refresh.");
  }
  setupDnD();
  renderPalette();
  await renderDiagramAsync();
  renderViewWorkbench();
  updateModelSaveUi();
  applyViewport();
  await initArtifactEditor();
  updateChatAttachmentLabel();
  if (authState?.promptedLogin) {
    await showProjectDialog();
    return;
  }
  const restored = await restoreLastProjectIfPossible();
  if (!restored) {
    await showProjectDialog();
  }
}

init().catch((error) => {
  console.error("Application initialization failed", error);
  setError(error, { prefix: "Application initialization failed." });
});
