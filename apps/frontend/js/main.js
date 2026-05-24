import {el} from './dom.js';
import {state} from './state.js';
import {
  applyThemeColors,
  getThemeColorDefaults,
  initTheme,
  resetThemeColors,
  toggleTheme
} from './theme.js';
import {
  applyViewport,
  onCanvasMouseDown,
  onCanvasTouchStart,
  onCanvasWheel,
  onGlobalMouseMove,
  onGlobalMouseUp,
  onGlobalTouchEnd,
  onGlobalTouchMove,
  renderDiagram,
  renderPalette,
  setupDnD,
} from './canvas.js';
import {
  exportActiveModel,
  generateForCurrentContext,
  importActiveModel,
  switchTab,
  undoLastEdit,
  validateCurrentModel
} from './model-ops.js';
import {
  downloadCurrentArtifact,
  initArtifactEditor,
  saveCurrentFile,
  toggleArtifactTreeCollapsed
} from './artifact.js';
import {
  applyAttributePanel,
  closeAttributePanel,
  deleteSelection,
  openAttributePanel,
  openConnectionPanel
} from './attr-panel.js';
import {closeImpactPanel, toggleImpactMode} from './impact.js';
import {
  buildChatWelcomeCard,
  ensureChatSession,
  sendChatMessage,
  updateChatAttachmentLabel
} from './chat.js';
import {
  bindProjectDialogActions,
  deleteCurrentProject,
  restoreLastProjectIfPossible,
  showProjectDialog
} from './project.js';
import {setError, setStatus} from './status.js';
import {CHAT_ATTACHMENT_MAX_BYTES} from './config.js';
import {isMobileViewport} from './responsive.js';
import {ensureAuthenticated, logout, updateDisplayName} from './auth.js';
import {disconnectCollaboration} from './collaboration.js';
import {initSvgIconMasks} from './icons.js';
import {
  deployToGithubFromArtifacts,
  refreshGithubConnection
} from './github.js';
import {loadModelingConfig} from './modeling-config-data.js';
import {initViewWorkbench, renderViewWorkbench} from './view-explorer.js';
import {initCimWorkbenchSurface} from './cim-workbench.js';
import {configureContainerCollapse} from './container-collapse.js';

const TOPBAR_MENU_BREAKPOINT = 1100;
const CHAT_INPUT_MAX_HEIGHT = 132;
const getElementTarget = (event) => (event.target instanceof Element
    ? event.target : null);

function refreshCurrentUserLabel() {
  if (!el.currentUserLabel) {
    return;
  }
  el.currentUserLabel.textContent = state.auth.user?.displayName
      || state.auth.user?.email || "User";
}

function showProfileDialog() {
  if (!el.profileOverlay || !el.profileDisplayNameInput || !el.profileSaveBtn
      || !el.profileCancelBtn) {
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
      const updatedUser = await updateDisplayName(
          el.profileDisplayNameInput.value);
      state.auth.user = updatedUser;
      refreshCurrentUserLabel();
      close();
      setStatus("Profile updated");
    } catch (error) {
      if (el.profileError) {
        el.profileError.textContent = error.message
            || "Failed to update profile";
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
  el.userMenuProjectsBtn?.addEventListener("click", showProjectDialog);
  el.userMenuLogoutBtn?.addEventListener("click", async () => {
    try {
      await logout();
    } finally {
      disconnectCollaboration();
      window.location.reload();
    }
  });
}

// Bind actions that must remain available even if later non-critical UI setup fails.
function bindCriticalActions() {
  // Generation
  el.generateContextBtn?.addEventListener("click", async () => {
    if (state.activeType === "artifact") {
      downloadCurrentArtifact();
      return;
    }
    await generateForCurrentContext();
  });
  el.validateModelBtn?.addEventListener("click", validateCurrentModel);

  // Project dialog
  el.switchProjectBtn?.addEventListener("click", showProjectDialog);
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
    menuItems.style.left = "0";
    menuItems.style.right = "auto";
    menuItems.style.top = "calc(100% + 6px)";
    menuItems.style.bottom = "auto";
    menuItems.style.transform = "translateX(0)";

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
    const trigger = menu.querySelector(".ide-menu-trigger");
    const menuItems = menu.querySelector(".ide-menu-items");
    if (!trigger || !menuItems) {
      return;
    }

    // menu items styling handled by CSS

    trigger.addEventListener("click", (event) => {
      event.stopPropagation();
      const willOpen = !menu.classList.contains("is-open");
      closeMenus();
      menu.classList.toggle("is-open", willOpen);
      if (willOpen) {
        requestAnimationFrame(() => {
          positionMenuItems(menu, menuItems);
        });
      }
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
  window.addEventListener("scroll", positionOpenMenus, {passive: true});
}

function closeTopbarMenu() {
  document.querySelector(".topbar")?.classList.remove("mobile-menu-open");
  if (el.topbarMenuToggleBtn) {
    el.topbarMenuToggleBtn.setAttribute("aria-expanded", "false");
  }
  // also remove any global menu glass overlay
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
}

function showThemeColorDialog() {
  if (
      !el.themeColorOverlay ||
      !el.themeTopbarColorInput ||
      !el.themeCanvasColorInput ||
      !el.themeChatColorInput ||
      !el.themeColorCancelBtn ||
      !el.themeColorResetBtn ||
      !el.themeColorSaveBtn
  ) {
    setError("Theme color editor is unavailable");
    return;
  }

  const defaults = getThemeColorDefaults();
  el.themeTopbarColorInput.value = defaults.topbar;
  el.themeCanvasColorInput.value = defaults.canvas;
  el.themeChatColorInput.value = defaults.chat;
  el.themeColorOverlay.classList.remove("hidden");
  document.body.classList.add("modal-open");

  const close = () => {
    el.themeColorOverlay.classList.add("hidden");
    document.body.classList.remove("modal-open");
    el.themeColorSaveBtn.removeEventListener("click", onSave);
    el.themeColorCancelBtn.removeEventListener("click", onCancel);
    el.themeColorResetBtn.removeEventListener("click", onReset);
    el.themeColorOverlay.removeEventListener("click", onOverlayClick);
    document.removeEventListener("keydown", onKeyDown);
  };

  const onSave = () => {
    applyThemeColors({
      topbar: el.themeTopbarColorInput.value,
      canvas: el.themeCanvasColorInput.value,
      chat: el.themeChatColorInput.value
    });
    close();
    setStatus("Theme colors updated");
  };

  const onReset = () => {
    resetThemeColors();
    const resetDefaults = getThemeColorDefaults();
    el.themeTopbarColorInput.value = resetDefaults.topbar;
    el.themeCanvasColorInput.value = resetDefaults.canvas;
    el.themeChatColorInput.value = resetDefaults.chat;
    setStatus("Theme colors reset");
  };

  const onCancel = () => close();
  const onOverlayClick = (event) => {
    if (event.target === el.themeColorOverlay) {
      onCancel();
    }
  };
  const onKeyDown = (event) => {
    if (event.key === "Escape") {
      onCancel();
    }
  };

  el.themeColorSaveBtn.addEventListener("click", onSave);
  el.themeColorCancelBtn.addEventListener("click", onCancel);
  el.themeColorResetBtn.addEventListener("click", onReset);
  el.themeColorOverlay.addEventListener("click", onOverlayClick);
  document.addEventListener("keydown", onKeyDown);
}

function setMobileBackdropVisible(visible) {
  if (!el.mobileBackdrop) {
    return;
  }
  el.mobileBackdrop.classList.toggle("hidden", !visible);
}

function closeMobilePanels() {
  el.workspace.classList.remove("mobile-left-open", "mobile-right-open");
  setMobileBackdropVisible(false);
}

function toggleMobileSidebar() {
  if (!isMobileViewport()) {
    return;
  }
  const willOpen = !el.workspace.classList.contains("mobile-left-open");
  el.workspace.classList.remove("mobile-right-open");
  el.workspace.classList.toggle("mobile-left-open", willOpen);
  setMobileBackdropVisible(willOpen);
}

function toggleMobileInspector() {
  if (!isMobileViewport()) {
    return;
  }
  const inspectorVisible =
      !el.attributePanel.classList.contains("hidden")
      || !el.impactPanel.classList.contains("hidden")
      || (el.modelTreePanel && !el.modelTreePanel.classList.contains("hidden"));
  if (!inspectorVisible) {
    setStatus("Select an element or enable Impact mode first");
    return;
  }
  const willOpen = !el.workspace.classList.contains("mobile-right-open");
  el.workspace.classList.remove("mobile-left-open");
  el.workspace.classList.toggle("mobile-right-open", willOpen);
  setMobileBackdropVisible(willOpen);
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
}

function syncPaletteRailToggleState() {
  const hidden = el.workspace?.classList.contains("palette-hidden");
  el.paletteRailToggleBtn?.classList.toggle("active", !hidden);
}

function closeRailMenus() {
  el.fileRailMenu?.classList.add("hidden");
  el.projectRailMenu?.classList.add("hidden");
  el.exportModelSubmenu?.classList.add("hidden");
  el.importModelSubmenu?.classList.add("hidden");
  el.exportModelBtn?.setAttribute("aria-expanded", "false");
  el.importModelBtn?.setAttribute("aria-expanded", "false");
  el.fileBtn?.classList.remove("active");
  el.settingsRailBtn?.classList.remove("active");
}

function toggleFileSubmenu(kind) {
  const targetMenu = kind === "export" ? el.exportModelSubmenu
      : el.importModelSubmenu;
  const targetBtn = kind === "export" ? el.exportModelBtn : el.importModelBtn;
  const otherMenu = kind === "export" ? el.importModelSubmenu
      : el.exportModelSubmenu;
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
  const targetMenu = kind === "file" ? el.fileRailMenu : el.projectRailMenu;
  const targetBtn = kind === "file" ? el.fileBtn : el.settingsRailBtn;
  const otherMenu = kind === "file" ? el.projectRailMenu : el.fileRailMenu;
  const otherBtn = kind === "file" ? el.settingsRailBtn : el.fileBtn;
  if (!targetMenu || !targetBtn) {
    return;
  }
  const willOpen = targetMenu.classList.contains("hidden");
  otherMenu?.classList.add("hidden");
  otherBtn?.classList.remove("active");
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
  const maxTop = Math.max(
      minTop,
      window.innerHeight - railRect.top - menuRect.height - gutter
  );
  const preferredTop = btnRect.top - railRect.top;
  const clampedTop = Math.min(maxTop, Math.max(minTop, preferredTop));
  targetMenu.style.top = `${Math.round(clampedTop)}px`;
}

function togglePaletteRail() {
  if (!el.workspace) {
    return;
  }
  const willHide = !el.workspace.classList.contains("palette-hidden");
  el.workspace.classList.toggle("palette-hidden", willHide);
  closeRailMenus();
  syncPaletteRailToggleState();
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
  if (el.openThemeColorsBtn) {
    el.openThemeColorsBtn.addEventListener("click", showThemeColorDialog);
  }
  if (el.undoModelReplaceBtn) {
    el.undoModelReplaceBtn.addEventListener("click", async () => {
      try {
        await undoLastEdit();
      } catch (error) {
        setError(`Undo failed: ${error.message}`);
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
        setError(`Export failed: ${error.message}`);
      }
    });
  }
  if (el.exportModelXmiBtn) {
    el.exportModelXmiBtn.addEventListener("click", async () => {
      try {
        await exportActiveModel("xmi");
      } catch (error) {
        setError(`Export failed: ${error.message}`);
      }
    });
  }
  if (el.importModelJsonBtn) {
    el.importModelJsonBtn.addEventListener("click", () => {
      if (!["cim", "pim", "psm"].includes(state.activeType)) {
        setStatus("Switch to CIM, PIM, or PSM to import model JSON.");
        return;
      }
      if (el.importModelFileInput) {
        el.importModelFileInput.accept = ".json,application/json";
        el.importModelFileInput.dataset.importFormat = "json";
        el.importModelFileInput.click();
      }
    });
  }
  if (el.importModelXmiBtn) {
    el.importModelXmiBtn.addEventListener("click", () => {
      if (!["cim", "pim", "psm"].includes(state.activeType)) {
        setStatus("Switch to CIM, PIM, or PSM to import model XMI.");
        return;
      }
      if (el.importModelFileInput) {
        el.importModelFileInput.accept = ".xmi,application/xml,text/xml";
        el.importModelFileInput.dataset.importFormat = "xmi";
        el.importModelFileInput.click();
      }
    });
  }
  if (el.importModelFileInput) {
    el.importModelFileInput.addEventListener("change", async (event) => {
      const file = event.target.files?.[0];
      const format = event.target.dataset.importFormat || "json";
      try {
        await importActiveModel(file, format);
      } catch (error) {
        setError(`Import failed: ${error.message}`);
      } finally {
        event.target.value = "";
        delete event.target.dataset.importFormat;
      }
    });
  }
  if (el.topbarMenuToggleBtn) {
    el.topbarMenuToggleBtn.addEventListener("click", (event) => {
      event.stopPropagation();
      toggleTopbarMenu();
    });
  }

  if (el.mobileSidebarToggleBtn) {
    el.mobileSidebarToggleBtn.addEventListener("click", toggleMobileSidebar);
  }

  if (el.mobileInspectorToggleBtn) {
    el.mobileInspectorToggleBtn.addEventListener("click",
        toggleMobileInspector);
  }

  if (el.mobileBackdrop) {
    el.mobileBackdrop.addEventListener("click", closeMobilePanels);
  }

  if (el.paletteSearchInput) {
    el.paletteSearchInput.addEventListener("input", () => {
      if (!["cim", "pim", "psm"].includes(state.activeType)) {
        return;
      }
      state.paletteSearch[state.activeType] = el.paletteSearchInput.value || "";
      renderPalette();
    });
  }

  window.addEventListener("resize", syncResponsiveUi);
  document.addEventListener("click", (event) => {
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
    if (!(event.ctrlKey || event.metaKey) || event.shiftKey || event.altKey
        || event.key.toLowerCase() !== "z") {
      return;
    }
    const target = getElementTarget(event);
    if (
        target?.closest('input, textarea, select, [contenteditable="true"]') ||
        target?.isContentEditable
    ) {
      return;
    }
    event.preventDefault();
    try {
      await undoLastEdit();
    } catch (error) {
      setError(`Undo failed: ${error.message}`);
    }
  });

  // Canvas interaction
  el.canvasViewport?.addEventListener("mousedown", onCanvasMouseDown);
  window.addEventListener("mousemove", onGlobalMouseMove);
  window.addEventListener("mouseup", onGlobalMouseUp);
  el.canvasViewport?.addEventListener("wheel", onCanvasWheel, {passive: false});
  el.canvasViewport?.addEventListener("touchstart", onCanvasTouchStart,
      {passive: false});
  window.addEventListener("touchmove", onGlobalTouchMove, {passive: false});
  window.addEventListener("touchend", onGlobalTouchEnd, {passive: false});
  window.addEventListener("touchcancel", onGlobalTouchEnd, {passive: false});

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
    el.chatInput.style.height = `${Math.min(el.chatInput.scrollHeight,
        CHAT_INPUT_MAX_HEIGHT)}px`;
  };

  // Chat
  el.chatToggle?.addEventListener("click", () => {
    el.chatWindow.classList.toggle("hidden");
    if (!el.chatWindow.classList.contains("hidden")) {
      ensureChatSession().catch(
          (error) => setStatus(`Chat setup failed: ${error.message}`));
      el.chatInput.focus();
    }
  });
  el.chatCloseBtn?.addEventListener("click", () => {
    collapseChatInput();
    el.chatWindow.classList.add("hidden");
  });

  if (el.chatClearBtn) {
    el.chatClearBtn.addEventListener("click", () => {
      el.chatMessages.innerHTML = "";
      el.chatMessages.appendChild(buildChatWelcomeCard());
      setStatus("Chat cleared");
    });
  }

  el.chatInput?.addEventListener("focus", expandChatInput);
  el.chatInput?.addEventListener("input", expandChatInput);
  el.chatInput?.addEventListener("blur", (event) => {
    // Don't collapse when focus moves to the send button so the click still registers
    if (event.relatedTarget === el.chatSendBtn) {
      return;
    }
    collapseChatInput();
  });
  el.chatSendBtn?.addEventListener("click", () => {
    collapseChatInput();
    sendChatMessage();
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
      setError(
          `File too large (${file.size} bytes). Max ${CHAT_ATTACHMENT_MAX_BYTES} bytes.`);
      return;
    }
    try {
      const content = await file.text();
      state.chat.attachment = {name: file.name, content};
      updateChatAttachmentLabel();
      setStatus(`Attached: ${file.name}`);
    } catch (error) {
      state.chat.attachment = null;
      event.target.value = "";
      updateChatAttachmentLabel();
      setError(`Failed to read file: ${error.message}`);
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
  el.artifactTreeToggleBtn?.addEventListener("click",
      toggleArtifactTreeCollapsed);

  el.deployGithubBtn?.addEventListener("click", deployToGithubFromArtifacts);

  el.saveFileBtn?.addEventListener("click", saveCurrentFile);

  // Attribute panel
  el.attrPanelCloseBtn?.addEventListener("click", closeAttributePanel);
  el.attrPanelApplyBtn?.addEventListener("click", applyAttributePanel);
  el.attrPanelDeleteBtn?.addEventListener("click", deleteSelection);
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
    if (!state.selectedNodeId && !state.selectedConnectionId
        && !state.selectedBoundedContextName) {
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
  const authState = await ensureAuthenticated();
  await loadModelingConfig();
  initSvgIconMasks();
  refreshCurrentUserLabel();
  disconnectCollaboration();
  bindProjectDialogActions();
  setupIdeMenus();
  bindEvents();
  configureContainerCollapse(
      {renderDiagram, renderWorkbench: renderViewWorkbench});
  initViewWorkbench({renderDiagram, renderPalette});
  initCimWorkbenchSurface({
    renderDiagram,
    renderPalette,
    openAttributePanel,
    openConnectionPanel
  });
  syncPaletteRailToggleState();
  initTheme();
  syncResponsiveUi();
  setupDnD();
  renderPalette();
  renderDiagram();
  renderViewWorkbench();
  applyViewport();
  await initArtifactEditor();
  updateChatAttachmentLabel();
  await refreshGithubConnection();
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
  setError(
      `Application initialization failed: ${error.message}. Check backend logs and /api/modeling/config.`);
});
