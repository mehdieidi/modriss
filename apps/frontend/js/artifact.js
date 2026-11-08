import { state } from "./state.js";
import { el } from "./dom.js";
import { api, apiAuthHeaders } from "./api.js";
import { apiUrl } from "./config.js";
import { setStatus } from "./status.js";
import { fetchImpact } from "./impact.js";
import { confirmAction } from "./confirm-action.js";

const MONACO_VS_PATH = "/vendor/monaco/vs";
const DEFAULT_LANGUAGE = "plaintext";
const FALLBACK_LANGUAGE_LABEL = "Plain Text";
const DEFAULT_TAB_SIZE = 2;
const ARTIFACT_SIDEBAR_WIDTH_STORAGE_KEY = "modriss.artifactExplorerWidth";
const ARTIFACT_SIDEBAR_MIN_WIDTH = 180;
const ARTIFACT_SIDEBAR_MAX_WIDTH = 560;

const LANGUAGE_BY_EXTENSION = {
  js: "javascript",
  jsx: "javascript",
  ts: "typescript",
  tsx: "typescript",
  json: "json",
  css: "css",
  html: "html",
  htm: "html",
  xml: "xml",
  md: "markdown",
  markdown: "markdown",
  py: "python",
  java: "java",
  cs: "csharp",
  go: "go",
  rs: "rust",
  php: "php",
  rb: "ruby",
  yml: "yaml",
  yaml: "yaml",
  sh: "shell",
  bash: "shell",
  sql: "sql",
  dockerfile: "dockerfile",
  txt: "plaintext",
};

let monacoLoaderPromise = null;
let monacoEditor = null;
let monacoModel = null;
let activeArtifactModelPath = null;
let artifactPlaceholderModel = null;
const artifactModels = new Map();
const artifactViewStates = new Map();
const dirtyArtifactFiles = new Set();
let monacoThemeObserver = null;
let applyingProgrammaticEdit = false;
let currentLanguageLabel = FALLBACK_LANGUAGE_LABEL;
let artifactFileRequestId = 0;
let artifactLoadRequestId = 0;
let artifactContextMenuReturnFocus = null;
let artifactContextTarget = null;
let artifactCreateDialogResolve = null;
let artifactCreateDialogBound = false;
let artifactPaneResizeBound = false;
const artifactScope = () => `${state.project?.id}:${state.cloudProvider?.id || "aws"}`;

function applyArtifactSidebarWidth(width, { persist = false } = {}) {
  const main = el.artifactPaneResizeHandle?.parentElement;
  if (!main) return ARTIFACT_SIDEBAR_MIN_WIDTH;
  const availableWidth = main.getBoundingClientRect().width;
  const maximum = Math.max(
    ARTIFACT_SIDEBAR_MIN_WIDTH,
    Math.min(ARTIFACT_SIDEBAR_MAX_WIDTH, availableWidth - 360),
  );
  const boundedWidth = Math.round(
    Math.min(maximum, Math.max(ARTIFACT_SIDEBAR_MIN_WIDTH, Number(width) || 280)),
  );
  main.style.setProperty("--artifact-sidebar-w", `${boundedWidth}px`);
  el.artifactPaneResizeHandle?.setAttribute("aria-valuemax", String(maximum));
  el.artifactPaneResizeHandle?.setAttribute("aria-valuenow", String(boundedWidth));
  if (persist) {
    try {
      window.localStorage.setItem(ARTIFACT_SIDEBAR_WIDTH_STORAGE_KEY, String(boundedWidth));
    } catch {
      // Keep the current width even when browser storage is unavailable.
    }
  }
  return boundedWidth;
}

export function initArtifactPaneResizing() {
  const handle = el.artifactPaneResizeHandle;
  const main = handle?.parentElement;
  if (!handle || !main) return;
  if (artifactPaneResizeBound) return;
  artifactPaneResizeBound = true;

  let storedWidth = 280;
  try {
    const stored = Number(window.localStorage.getItem(ARTIFACT_SIDEBAR_WIDTH_STORAGE_KEY));
    if (Number.isFinite(stored) && stored > 0) storedWidth = stored;
  } catch {
    // The editor remains resizable for private browsing and restricted storage.
  }
  applyArtifactSidebarWidth(storedWidth);

  let pointerId = null;
  const finishResize = (event) => {
    if (pointerId === null || (event?.pointerId != null && event.pointerId !== pointerId)) return;
    pointerId = null;
    document.body.classList.remove("is-resizing-artifact-pane");
    applyArtifactSidebarWidth(
      Number.parseFloat(main.style.getPropertyValue("--artifact-sidebar-w")),
      { persist: true },
    );
  };

  handle.addEventListener("pointerdown", (event) => {
    if (window.matchMedia("(max-width: 920px)").matches) return;
    event.preventDefault();
    pointerId = event.pointerId;
    handle.setPointerCapture?.(pointerId);
    document.body.classList.add("is-resizing-artifact-pane");
  });
  handle.addEventListener("pointermove", (event) => {
    if (event.pointerId !== pointerId) return;
    const bounds = main.getBoundingClientRect();
    applyArtifactSidebarWidth(event.clientX - bounds.left);
  });
  handle.addEventListener("pointerup", finishResize);
  handle.addEventListener("pointercancel", finishResize);
  handle.addEventListener("lostpointercapture", finishResize);
  handle.addEventListener("keydown", (event) => {
    let width = Number.parseFloat(main.style.getPropertyValue("--artifact-sidebar-w")) || 280;
    if (event.key === "ArrowLeft") width -= event.shiftKey ? 48 : 16;
    else if (event.key === "ArrowRight") width += event.shiftKey ? 48 : 16;
    else if (event.key === "Home") width = ARTIFACT_SIDEBAR_MIN_WIDTH;
    else if (event.key === "End") width = ARTIFACT_SIDEBAR_MAX_WIDTH;
    else return;
    event.preventDefault();
    applyArtifactSidebarWidth(width, { persist: true });
  });
  window.addEventListener("resize", () => {
    applyArtifactSidebarWidth(
      Number.parseFloat(main.style.getPropertyValue("--artifact-sidebar-w")) || 280,
    );
  });
}

function setArtifactFileLoading(loading) {
  state.artifact.openingFile = Boolean(loading);
  el.artifactEditorLoading?.classList.toggle("hidden", !loading);
  el.fileTree?.classList.toggle("is-loading", loading);
  el.fileTree?.setAttribute("aria-busy", loading ? "true" : "false");
}

function toFileDisplayName(filePath) {
  if (!filePath) {
    return "No file selected";
  }
  const parts = String(filePath).split("/");
  return parts[parts.length - 1] || filePath;
}

function setExplorerRootLabel(label) {
  if (!el.artifactExplorerRoot) {
    return;
  }
  const text = el.artifactExplorerRoot.querySelector("span:last-child");
  if (text) {
    text.textContent = label;
  }
}

function setStatusField(node, value) {
  if (node) {
    node.textContent = value;
  }
}

function eolLabel(value) {
  return value === "\r\n" ? "CRLF" : "LF";
}

function updateArtifactEditorStatusPosition() {
  if (!monacoEditor) {
    setStatusField(el.artifactEditorStatusPosition, "Ln 1, Col 1");
    return;
  }
  const position = monacoEditor.getPosition();
  const line = position?.lineNumber || 1;
  const column = position?.column || 1;
  setStatusField(el.artifactEditorStatusPosition, `Ln ${line}, Col ${column}`);
}

function updateArtifactEditorStatusModelDetails() {
  if (!monacoModel) {
    setStatusField(el.artifactEditorStatusIndent, `Spaces: ${DEFAULT_TAB_SIZE}`);
    setStatusField(el.artifactEditorStatusEncoding, "UTF-8");
    setStatusField(el.artifactEditorStatusEol, "LF");
    setStatusField(el.artifactEditorStatusLanguage, FALLBACK_LANGUAGE_LABEL);
    return;
  }
  const options = monacoModel.getOptions();
  const indentKind = options.insertSpaces ? "Spaces" : "Tab Size";
  setStatusField(
    el.artifactEditorStatusIndent,
    `${indentKind}: ${options.tabSize || DEFAULT_TAB_SIZE}`,
  );
  setStatusField(el.artifactEditorStatusEncoding, "UTF-8");
  setStatusField(el.artifactEditorStatusEol, eolLabel(monacoModel.getEOL()));
  setStatusField(el.artifactEditorStatusLanguage, currentLanguageLabel || FALLBACK_LANGUAGE_LABEL);
}

function syncArtifactEditorStatusBar() {
  updateArtifactEditorStatusPosition();
  updateArtifactEditorStatusModelDetails();
}

function setArtifactDirty(dirty) {
  state.artifact.dirty = Boolean(dirty);
  if (el.saveFileBtn) {
    el.saveFileBtn.textContent = state.artifact.dirty ? "Save *" : "Save";
  }
  updateArtifactTabStates();
}

function isLightTheme() {
  return document.documentElement.classList.contains("light");
}

function applyMonacoTheme(monaco) {
  monaco.editor.setTheme(isLightTheme() ? "vs" : "vs-dark");
}

function ensureMonacoThemeSync(monaco) {
  if (monacoThemeObserver) {
    return;
  }
  monacoThemeObserver = new MutationObserver(() => {
    applyMonacoTheme(monaco);
  });
  monacoThemeObserver.observe(document.documentElement, {
    attributes: true,
    attributeFilter: ["class"],
  });
}

function getFileExtension(filePath) {
  const value = String(filePath || "");
  const parts = value.split(".");
  if (parts.length <= 1) {
    return "";
  }
  return (parts.pop() || "").toLowerCase();
}

function getBaseName(filePath) {
  return (
    String(filePath || "")
      .split("/")
      .pop()
      ?.toLowerCase() || ""
  );
}

function detectLanguageId(monaco, filePath) {
  const extension = getFileExtension(filePath);
  if (extension && LANGUAGE_BY_EXTENSION[extension]) {
    return LANGUAGE_BY_EXTENSION[extension];
  }

  const baseName = getBaseName(filePath);
  if (baseName === "dockerfile") {
    return "dockerfile";
  }

  const candidates = monaco.languages.getLanguages();
  for (const language of candidates) {
    if (language.extensions?.includes(`.${extension}`)) {
      return language.id;
    }
    if (language.filenames?.includes(baseName)) {
      return language.id;
    }
  }
  return DEFAULT_LANGUAGE;
}

function languageLabelForId(monaco, languageId) {
  const language = monaco.languages.getLanguages().find((entry) => entry.id === languageId);
  if (language?.aliases?.length) {
    return language.aliases[0];
  }
  if (languageId === "plaintext") {
    return FALLBACK_LANGUAGE_LABEL;
  }
  return languageId ? languageId.toUpperCase() : FALLBACK_LANGUAGE_LABEL;
}

function createArtifactModel(value, filePath) {
  if (!monacoEditor || !window.monaco?.editor) {
    return null;
  }
  const monaco = window.monaco;
  const languageId = detectLanguageId(monaco, filePath);
  const uri = monaco.Uri.parse(`artifact:///${encodeURI(filePath || "untitled.txt")}`);
  const existingModel = monaco.editor.getModel(uri);
  if (existingModel) return existingModel;
  return monaco.editor.createModel(value || "", languageId, uri);
}

function activateArtifactModel(filePath) {
  if (!monacoEditor) return;
  const model = filePath ? artifactModels.get(filePath) : null;
  if (monacoModel && activeArtifactModelPath) {
    artifactViewStates.set(activeArtifactModelPath, monacoEditor.saveViewState());
  }
  monacoModel = model || null;
  activeArtifactModelPath = model ? filePath : null;
  currentLanguageLabel = model
    ? languageLabelForId(window.monaco, model.getLanguageId())
    : FALLBACK_LANGUAGE_LABEL;
  applyingProgrammaticEdit = true;
  if (model) {
    if (artifactPlaceholderModel) {
      artifactPlaceholderModel.dispose();
      artifactPlaceholderModel = null;
    }
    monacoEditor.setModel(model);
    const viewState = artifactViewStates.get(filePath);
    if (viewState) monacoEditor.restoreViewState(viewState);
  } else {
    artifactPlaceholderModel ||= window.monaco.editor.createModel("");
    monacoEditor.setModel(artifactPlaceholderModel);
  }
  monacoEditor.updateOptions({
    readOnly: !model,
  });
  applyingProgrammaticEdit = false;
  syncArtifactEditorStatusBar();
}

function disposeArtifactModels() {
  artifactModels.forEach((model) => model.dispose());
  artifactModels.clear();
  artifactViewStates.clear();
  dirtyArtifactFiles.clear();
  monacoModel = null;
  activeArtifactModelPath = null;
  if (artifactPlaceholderModel) {
    artifactPlaceholderModel.dispose();
    artifactPlaceholderModel = null;
  }
}

function updateArtifactTabStates() {
  el.artifactEditorTabs?.querySelectorAll(".artifact-editor-tab").forEach((tab) => {
    const path = tab.dataset.path || "";
    const dirty = dirtyArtifactFiles.has(path);
    const active = path === state.artifact.activeFile;
    tab.classList.toggle("active", path === state.artifact.activeFile);
    tab.classList.toggle("is-dirty", dirty);
    const select = tab.querySelector("[role='tab']");
    select?.setAttribute("aria-selected", String(active));
    if (select) select.tabIndex = active ? 0 : -1;
    const marker = tab.querySelector(".artifact-editor-tab-dirty");
    if (marker) marker.setAttribute("aria-label", dirty ? "Unsaved changes" : "");
  });
}

function renderArtifactTabs() {
  const container = el.artifactEditorTabs;
  if (!container) return;
  container.innerHTML = "";
  (state.artifact.openFiles || []).forEach((filePath) => {
    const tab = document.createElement("div");
    tab.className = "artifact-editor-tab";
    tab.dataset.path = filePath;
    tab.setAttribute("role", "presentation");

    const select = document.createElement("button");
    select.type = "button";
    select.className = "artifact-editor-tab-select";
    select.setAttribute("role", "tab");
    select.setAttribute("aria-label", filePath);
    select.title = filePath;
    select.setAttribute("aria-controls", "artifactEditorContent");
    const icon = createIcon(fileIconForPath(filePath), "artifact-editor-tab-icon");
    const label = document.createElement("span");
    label.className = "artifact-editor-tab-label";
    label.textContent = toFileDisplayName(filePath);
    const dirty = document.createElement("span");
    dirty.className = "artifact-editor-tab-dirty";
    dirty.setAttribute("aria-hidden", "false");
    select.append(icon, label, dirty);
    select.addEventListener("click", () => activateOpenArtifactFile(filePath));
    select.addEventListener("keydown", (event) => {
      const tabs = [...container.querySelectorAll("[role='tab']")];
      const currentIndex = tabs.indexOf(select);
      let nextIndex = currentIndex;
      if (event.key === "ArrowLeft") nextIndex = (currentIndex - 1 + tabs.length) % tabs.length;
      else if (event.key === "ArrowRight") nextIndex = (currentIndex + 1) % tabs.length;
      else if (event.key === "Home") nextIndex = 0;
      else if (event.key === "End") nextIndex = tabs.length - 1;
      else return;
      event.preventDefault();
      const nextTab = tabs[nextIndex];
      nextTab?.focus();
      const nextPath = nextTab?.closest(".artifact-editor-tab")?.dataset.path;
      if (nextPath) activateOpenArtifactFile(nextPath);
    });

    const close = document.createElement("button");
    close.type = "button";
    close.className = "artifact-editor-tab-close";
    close.setAttribute("aria-label", `Close ${filePath}`);
    close.title = `Close ${filePath}`;
    close.textContent = "×";
    close.addEventListener("click", (event) => {
      event.stopPropagation();
      closeArtifactFile(filePath);
    });
    tab.append(select, close);
    container.appendChild(tab);
  });
  updateArtifactTabStates();
}

function resetArtifactEditor({ clearTabs = true } = {}) {
  if (clearTabs) {
    state.artifact.openFiles = [];
    state.artifact.activeFile = null;
    disposeArtifactModels();
    renderArtifactTabs();
  }
  setArtifactDirty(false);
  currentLanguageLabel = FALLBACK_LANGUAGE_LABEL;
  if (monacoEditor) {
    activateArtifactModel(null);
  }
  if (el.saveFileBtn) {
    el.saveFileBtn.classList.add("hidden");
    el.saveFileBtn.textContent = "Save";
  }
  syncArtifactEditorStatusBar();
}

function ensureTreeDirectory(root, directoryPath) {
  let node = root;
  String(directoryPath)
    .split("/")
    .filter(Boolean)
    .forEach((part) => {
      if (!node.dirs.has(part)) {
        node.dirs.set(part, { dirs: new Map(), files: [] });
      }
      node = node.dirs.get(part);
    });
}

function createTree(files, directories = []) {
  const root = { dirs: new Map(), files: [] };
  directories.forEach((path) => ensureTreeDirectory(root, path));
  files.forEach(({ path }) => {
    const parts = String(path).split("/").filter(Boolean);
    let node = root;
    parts.forEach((part, index) => {
      const isLeaf = index === parts.length - 1;
      if (isLeaf) {
        node.files.push({ name: part, path });
        return;
      }
      if (!node.dirs.has(part)) {
        node.dirs.set(part, { dirs: new Map(), files: [] });
      }
      node = node.dirs.get(part);
    });
  });
  return root;
}

function createIcon(iconName, className = "file-tree-icon") {
  const icon = document.createElement("span");
  icon.className = `icon-svg icon-mask ${className}`;
  icon.setAttribute("aria-hidden", "true");
  setIconSource(icon, iconName);
  return icon;
}

function setIconSource(icon, iconName) {
  if (!icon) {
    return;
  }
  const iconMap = {
    folder: "/assets/icons/folder.svg",
    folder_open: "/assets/icons/folder_open.svg",
    description: "/assets/icons/description.svg",
    chevron_right: "/assets/icons/chevron_right.svg",
    chevron_down: "/assets/icons/chevron_down.svg",
    expand: "/assets/icons/expand.svg",
    collapse: "/assets/icons/collapse.svg",
  };
  const iconSrc = iconMap[iconName] || iconMap.description;
  icon.style.setProperty("--icon-src", `url('${iconSrc}')`);
}

function createIndent(depth) {
  const indent = document.createElement("span");
  indent.className = "file-tree-indent";
  indent.style.setProperty("--tree-depth", String(depth));
  return indent;
}

function fileIconForPath(_filePath) {
  return "description";
}

function updateTreeSelection() {
  if (!el.fileTree) {
    return;
  }
  el.fileTree.querySelectorAll(".file-tree-item").forEach((btn) => {
    const path = btn.dataset.path || "";
    btn.classList.toggle("active", path === state.artifact.activeFile);
  });
}

function captureArtifactTreeExpansion() {
  return Object.fromEntries(
    [...(el.fileTree?.querySelectorAll(".file-tree-dir") || [])].map((directory) => [
      directory.dataset.directoryPath || "",
      directory.classList.contains("open"),
    ]),
  );
}

function isGeneratedArtifactPath(path, kind) {
  const generatedPaths = state.artifact.generatedPaths || [];
  if (kind === "file") return generatedPaths.includes(path);
  return generatedPaths.some(
    (generatedPath) => generatedPath === path || generatedPath.startsWith(`${path}/`),
  );
}

function updateTreeToggleButton() {
  if (!el.artifactTreeToggleBtn) {
    return;
  }
  const collapsed = !!state.artifact.treeCollapsed;
  el.artifactTreeToggleBtn.title = collapsed ? "Expand folders" : "Collapse folders";
  const icon = el.artifactTreeToggleBtn.querySelector(".artifact-tree-toggle-icon");
  if (icon) {
    setIconSource(icon, collapsed ? "expand" : "collapse");
  }
}

function updateArtifactActions() {
  const enabled = Boolean(state.artifact.id);
  if (el.artifactImportBtn) el.artifactImportBtn.disabled = !enabled;
}

function buildTreeNode(node, depth = 0, parentPath = "") {
  const fragment = document.createDocumentFragment();
  const initiallyOpen = !state.artifact.treeCollapsed;

  const directories = [...node.dirs.keys()].sort((a, b) => a.localeCompare(b));
  directories.forEach((dirName) => {
    const wrapper = document.createElement("div");
    const directoryPath = parentPath ? `${parentPath}/${dirName}` : dirName;
    const savedExpansion = state.artifact.expandedDirectories?.[directoryPath];
    const isOpen = typeof savedExpansion === "boolean" ? savedExpansion : initiallyOpen;

    const dirHeader = document.createElement("button");
    dirHeader.type = "button";
    dirHeader.className = isOpen ? "file-tree-dir open" : "file-tree-dir";
    dirHeader.dataset.directoryPath = directoryPath;
    dirHeader.appendChild(createIndent(depth));
    const chevronIcon = createIcon(
      isOpen ? "chevron_down" : "chevron_right",
      "file-tree-dir-chevron",
    );
    dirHeader.appendChild(chevronIcon);
    const folderIcon = createIcon(isOpen ? "folder_open" : "folder", "file-tree-icon");
    dirHeader.appendChild(folderIcon);
    const label = document.createElement("span");
    label.className = "file-tree-label";
    label.textContent = dirName;
    dirHeader.appendChild(label);

    const children = document.createElement("div");
    children.className = "file-tree-children";
    children.dataset.directoryPath = directoryPath;
    children.style.display = isOpen ? "" : "none";
    children.appendChild(buildTreeNode(node.dirs.get(dirName), depth + 1, directoryPath));

    dirHeader.addEventListener("click", () => {
      state.artifact.selectedDirectory = directoryPath;
      const nextIsOpen = !dirHeader.classList.contains("open");
      dirHeader.classList.toggle("open", nextIsOpen);
      state.artifact.expandedDirectories ||= {};
      state.artifact.expandedDirectories[directoryPath] = nextIsOpen;
      children.style.display = nextIsOpen ? "" : "none";
      setIconSource(chevronIcon, nextIsOpen ? "chevron_down" : "chevron_right");
      setIconSource(folderIcon, nextIsOpen ? "folder_open" : "folder");
    });

    wrapper.appendChild(dirHeader);
    wrapper.appendChild(children);
    fragment.appendChild(wrapper);
  });

  node.files
    .slice()
    .sort((a, b) => a.name.localeCompare(b.name))
    .forEach((file) => {
      const item = document.createElement("button");
      item.type = "button";
      item.className = "file-tree-item";
      item.dataset.path = file.path;
      item.title = file.path;
      item.appendChild(createIndent(depth));
      item.appendChild(createIcon(fileIconForPath(file.path)));
      const label = document.createElement("span");
      label.className = "file-tree-label";
      label.textContent = file.name;
      item.appendChild(label);
      item.addEventListener("click", () => openArtifactFile(file.path));
      fragment.appendChild(item);
    });

  return fragment;
}

function ensureMonacoLoaded() {
  if (window.monaco?.editor) {
    return Promise.resolve(window.monaco);
  }
  if (monacoLoaderPromise) {
    return monacoLoaderPromise;
  }
  monacoLoaderPromise = new Promise((resolve, reject) => {
    if (typeof window.require !== "function") {
      reject(new Error("Monaco loader is not available"));
      return;
    }
    window.MonacoEnvironment = {
      getWorkerUrl: () => {
        // Workers created from a data URL have an opaque origin and cannot resolve
        // root-relative URLs passed to importScripts. Resolve both URLs against
        // the document so the bootstrap works with the static frontend server.
        const workerMainUrl = new URL(
          `${MONACO_VS_PATH}/base/worker/workerMain.js`,
          document.baseURI,
        ).href;
        // workerMain resolves AMD modules as `${baseUrl}/vs/...`; the base is
        // therefore the Monaco package root, one level above its `vs` folder.
        const workerBaseUrl = new URL("../", new URL(`${MONACO_VS_PATH}/`, document.baseURI)).href;
        const workerSource = `
self.MonacoEnvironment = { baseUrl: ${JSON.stringify(workerBaseUrl)} };
importScripts(${JSON.stringify(workerMainUrl)});
`;
        return `data:text/javascript;charset=utf-8,${encodeURIComponent(workerSource)}`;
      },
    };
    window.require.config({ paths: { vs: MONACO_VS_PATH } });
    window.require(
      ["vs/editor/editor.main"],
      () => {
        if (window.monaco?.editor) {
          resolve(window.monaco);
        } else {
          reject(new Error("Monaco editor failed to initialize"));
        }
      },
      (error) => {
        reject(new Error(error?.message || "Failed to load Monaco editor"));
      },
    );
  }).catch((error) => {
    monacoLoaderPromise = null;
    throw error;
  });
  return monacoLoaderPromise;
}

function attachMonacoListeners() {
  if (!monacoEditor) {
    return;
  }
  monacoEditor.onDidChangeCursorPosition(() => {
    updateArtifactEditorStatusPosition();
  });
  monacoEditor.onDidChangeModelContent(() => {
    if (applyingProgrammaticEdit || !state.artifact.activeFile) {
      syncArtifactEditorStatusBar();
      return;
    }
    dirtyArtifactFiles.add(state.artifact.activeFile);
    setArtifactDirty(true);
    syncArtifactEditorStatusBar();
  });
}

export async function initArtifactEditor() {
  if (monacoEditor) {
    return true;
  }
  if (!el.artifactEditorContent) {
    return false;
  }
  try {
    const monaco = await ensureMonacoLoaded();
    applyMonacoTheme(monaco);
    ensureMonacoThemeSync(monaco);

    monacoEditor = monaco.editor.create(el.artifactEditorContent, {
      value: "",
      language: DEFAULT_LANGUAGE,
      readOnly: true,
      automaticLayout: true,
      fontFamily: "Consolas, 'Courier New', monospace",
      fontSize: 13,
      lineHeight: 21,
      tabSize: DEFAULT_TAB_SIZE,
      insertSpaces: true,
      minimap: { enabled: false },
      scrollBeyondLastLine: false,
      roundedSelection: false,
      renderWhitespace: "selection",
      wordWrap: "off",
      smoothScrolling: true,
      cursorBlinking: "smooth",
      padding: { top: 10, bottom: 10 },
    });

    attachMonacoListeners();
    resetArtifactEditor();
    return true;
  } catch (error) {
    console.error("Artifact editor setup failed", error);
    setStatus(error, { prefix: "Editor setup failed.", error: true });
    return false;
  }
}

export function clearArtifactState() {
  artifactLoadRequestId += 1;
  artifactFileRequestId += 1;
  state.artifact.id = null;
  state.artifact.name = "";
  state.artifact.cloudProviderId = null;
  state.artifact.files = [];
  state.artifact.directories = [];
  state.artifact.generatedPaths = [];
  state.artifact.selectedDirectory = "";
  state.artifact.expandedDirectories = {};
  state.artifact.openFiles = [];
  state.artifact.activeFile = null;
  state.artifact.openingFile = false;
  state.artifact.downloading = false;
  state.artifact.treeCollapsed = false;
  setArtifactFileLoading(false);
  setExplorerRootLabel("No artifact loaded");
  updateTreeToggleButton();
  updateArtifactActions();
  renderFileTree([], []);
  resetArtifactEditor();
}

export function syncArtifactEditorLineNumberScroll() {
  syncArtifactEditorStatusBar();
}

export function refreshArtifactEditorLineNumbers() {
  syncArtifactEditorStatusBar();
}

export function setArtifactTreeCollapsed(collapsed) {
  state.artifact.treeCollapsed = Boolean(collapsed);
  state.artifact.expandedDirectories = {};
  updateTreeToggleButton();
  renderFileTree(state.artifact.files);
}

export function toggleArtifactTreeCollapsed() {
  setArtifactTreeCollapsed(!state.artifact.treeCollapsed);
}

export async function loadArtifactRecord(
  record,
  {
    collapseTree = true,
    expandedDirectories = null,
    treeCollapsed: preserveTreeCollapsed = null,
    preserveOpenFiles = false,
  } = {},
) {
  const previousArtifactId = state.artifact.id;
  const isNewArtifact = previousArtifactId !== record.id;
  artifactLoadRequestId += 1;
  artifactFileRequestId += 1;
  if (expandedDirectories && typeof expandedDirectories === "object") {
    state.artifact.expandedDirectories = { ...expandedDirectories };
  } else if (state.artifact.id !== record.id || collapseTree) {
    state.artifact.expandedDirectories = {};
  }
  state.artifact.id = record.id;
  state.artifact.name = record.name || "artifact";
  state.artifact.cloudProviderId =
    record.cloudProviderId || record.modelJson?.psmPackageId || "aws";
  setArtifactFileLoading(false);

  const filePaths = Array.isArray(record.filePaths)
    ? record.filePaths
    : Object.keys(record.files || record.modelJson?.files || {});
  const availablePaths = new Set(filePaths);
  state.artifact.files = filePaths.map((path) => ({ path }));
  const generationRules = record.modelJson?.generationRules;
  const generatedPaths =
    generationRules && typeof generationRules === "object" && !Array.isArray(generationRules)
      ? Object.keys(generationRules)
      : [];
  state.artifact.generatedPaths = generatedPaths.length ? generatedPaths : filePaths;
  const directoryPaths = Array.isArray(record.directoryPaths)
    ? record.directoryPaths
    : record.modelJson?.directories;
  state.artifact.directories = Array.isArray(directoryPaths)
    ? [...new Set(directoryPaths.filter((path) => typeof path === "string"))]
    : [];
  state.artifact.selectedDirectory = "";
  if (typeof preserveTreeCollapsed === "boolean") {
    state.artifact.treeCollapsed = preserveTreeCollapsed;
  } else if (typeof collapseTree === "boolean") {
    state.artifact.treeCollapsed = collapseTree;
  }

  if (isNewArtifact || !preserveOpenFiles) {
    resetArtifactEditor();
  } else {
    for (const filePath of [...artifactModels.keys()]) {
      if (!availablePaths.has(filePath)) {
        if (filePath === activeArtifactModelPath) {
          monacoModel = null;
          activeArtifactModelPath = null;
        }
        artifactModels.get(filePath)?.dispose();
        artifactModels.delete(filePath);
        dirtyArtifactFiles.delete(filePath);
      }
    }
    state.artifact.openFiles = (state.artifact.openFiles || []).filter((path) =>
      availablePaths.has(path),
    );
    if (!availablePaths.has(state.artifact.activeFile)) {
      state.artifact.activeFile = state.artifact.openFiles.at(-1) || null;
    }
    renderArtifactTabs();
    if (state.artifact.activeFile && artifactModels.has(state.artifact.activeFile)) {
      activateArtifactModel(state.artifact.activeFile);
      setArtifactDirty(dirtyArtifactFiles.has(state.artifact.activeFile));
      el.saveFileBtn?.classList.remove("hidden");
    } else {
      state.artifact.activeFile = null;
      resetArtifactEditor({ clearTabs: false });
    }
  }

  setExplorerRootLabel(state.artifact.name);
  updateTreeToggleButton();
  updateArtifactActions();
  renderFileTree(state.artifact.files);
  setStatus(`Loaded artifact: ${state.artifact.name} (${state.artifact.files.length} files)`);
}

export async function loadArtifactById(id, { collapseTree = true } = {}) {
  const scope = artifactScope();
  const requestId = ++artifactLoadRequestId;
  try {
    const record = await api(`/artifact/${id}`);
    if (scope !== artifactScope() || requestId !== artifactLoadRequestId) return;
    await loadArtifactRecord(record, { collapseTree });
    if (state.project?.id) {
      try {
        const activeModelIds = {
          ...(state.project.activeModelIds || {}),
          artifact: String(record.id),
        };
        const updatedProject = await api(`/projects/${state.project.id}`, {
          method: "PUT",
          body: JSON.stringify({
            name: state.project.name,
            description: state.project.description || "",
            activeModelIds,
          }),
        });
        state.project = updatedProject;
      } catch (error) {
        console.warn("Failed to sync active artifact on project.", error);
      }
    }
  } catch (error) {
    if (scope !== artifactScope()) return;
    setStatus(error, { prefix: "Failed to load artifact.", error: true });
  }
}

export async function loadCurrentProjectArtifact(options = {}) {
  if (!state.project?.id) {
    clearArtifactState();
    return null;
  }
  const providerId = state.cloudProvider?.id || "aws";
  if (!options.forceReload && state.artifact.id && state.artifact.cloudProviderId === providerId) {
    return {
      id: state.artifact.id,
      name: state.artifact.name,
      files: state.artifact.files,
    };
  }
  clearArtifactState();
  const scope = artifactScope();
  const requestId = ++artifactLoadRequestId;
  try {
    const records = await api(`/artifact?projectId=${state.project.id}`);
    if (scope !== artifactScope() || requestId !== artifactLoadRequestId) return null;
    const record = Array.isArray(records)
      ? records.find((item) => (item.cloudProviderId || "aws") === providerId)
      : null;
    if (!record?.id) {
      clearArtifactState();
      const providerName =
        document.querySelector('#modelTabs .tab[data-type="psm"]')?.textContent || "this provider";
      setExplorerRootLabel(`${providerName} · No artifacts yet`);
      setStatus(`No artifacts have been generated for ${providerName} yet`);
      return null;
    }
    await loadArtifactById(record.id, { ...options, collapseTree: options.collapseTree ?? true });
    return record;
  } catch (error) {
    if (scope !== artifactScope() || requestId !== artifactLoadRequestId) return null;
    clearArtifactState();
    setStatus(error, { prefix: "Failed to load artifact.", error: true });
    return null;
  }
}

export function renderFileTree(files) {
  if (!el.fileTree) {
    return;
  }
  updateTreeToggleButton();
  el.fileTree.innerHTML = "";
  const directories = state.artifact.directories || [];
  if (!files.length && !directories.length) {
    const empty = document.createElement("div");
    empty.className = "file-tree-empty";
    empty.textContent = "No files found in this artifact.";
    el.fileTree.appendChild(empty);
    return;
  }

  const tree = createTree(files, directories);
  el.fileTree.appendChild(buildTreeNode(tree, 0));
  updateTreeSelection();
}

function activateOpenArtifactFile(filePath) {
  if (!artifactModels.has(filePath)) return false;
  artifactFileRequestId += 1;
  setArtifactFileLoading(false);
  state.artifact.activeFile = filePath;
  activateArtifactModel(filePath);
  setArtifactDirty(dirtyArtifactFiles.has(filePath));
  el.saveFileBtn?.classList.remove("hidden");
  el.saveFileBtn.textContent = state.artifact.dirty ? "Save *" : "Save";
  updateTreeSelection();
  syncArtifactEditorStatusBar();
  return true;
}

function closeArtifactFile(filePath) {
  const openFiles = state.artifact.openFiles || [];
  const index = openFiles.indexOf(filePath);
  if (index < 0) return;
  if (
    dirtyArtifactFiles.has(filePath) &&
    !window.confirm(`Discard unsaved changes in ${toFileDisplayName(filePath)}?`)
  ) {
    return;
  }
  state.artifact.openFiles = openFiles.filter((path) => path !== filePath);
  if (state.artifact.activeFile === filePath) {
    const nextFile =
      state.artifact.openFiles[Math.min(index, state.artifact.openFiles.length - 1)] || null;
    if (nextFile) {
      state.artifact.activeFile = nextFile;
      activateArtifactModel(nextFile);
      setArtifactDirty(dirtyArtifactFiles.has(nextFile));
      el.saveFileBtn?.classList.remove("hidden");
      el.saveFileBtn.textContent = state.artifact.dirty ? "Save *" : "Save";
    } else {
      state.artifact.activeFile = null;
      setArtifactDirty(false);
      activateArtifactModel(null);
      el.saveFileBtn?.classList.add("hidden");
    }
    updateTreeSelection();
    syncArtifactEditorStatusBar();
  }
  dirtyArtifactFiles.delete(filePath);
  artifactViewStates.delete(filePath);
  const model = artifactModels.get(filePath);
  if (model) {
    artifactModels.delete(filePath);
    if (model !== monacoModel) model.dispose();
  }
  updateArtifactTabStates();
  renderArtifactTabs();
}

function remapArtifactOpenFiles(pathMap) {
  const entries = Object.entries(pathMap || {});
  if (!entries.length) return;
  if (activeArtifactModelPath && pathMap[activeArtifactModelPath] && monacoEditor) {
    artifactViewStates.set(activeArtifactModelPath, monacoEditor.saveViewState());
    monacoModel = null;
    activeArtifactModelPath = null;
  }
  entries.forEach(([oldPath, newPath]) => {
    const oldModel = artifactModels.get(oldPath);
    if (!oldModel) return;
    const content = oldModel.getValue();
    const viewState = artifactViewStates.get(oldPath);
    const wasDirty = dirtyArtifactFiles.has(oldPath);
    artifactModels.delete(oldPath);
    artifactViewStates.delete(oldPath);
    dirtyArtifactFiles.delete(oldPath);
    const newModel = createArtifactModel(content, newPath);
    if (!newModel) return;
    if (newModel.getValue() !== content) newModel.setValue(content);
    newModel.setLanguage(detectLanguageId(window.monaco, newPath));
    if (newModel !== oldModel) oldModel.dispose();
    artifactModels.set(newPath, newModel);
    if (viewState) artifactViewStates.set(newPath, viewState);
    if (wasDirty) dirtyArtifactFiles.add(newPath);
  });
  state.artifact.openFiles = (state.artifact.openFiles || []).map((path) => pathMap[path] || path);
  if (state.artifact.activeFile) {
    state.artifact.activeFile = pathMap[state.artifact.activeFile] || state.artifact.activeFile;
  }
  renderArtifactTabs();
  if (state.artifact.activeFile && artifactModels.has(state.artifact.activeFile)) {
    activateArtifactModel(state.artifact.activeFile);
    setArtifactDirty(dirtyArtifactFiles.has(state.artifact.activeFile));
  }
}

export async function openArtifactFile(filePath, { preserveImpact = false } = {}) {
  if (!state.artifact.id) {
    return;
  }
  if (state.impactMode && state.activeType === "artifact" && !preserveImpact) {
    void fetchImpact(filePath);
  }
  if (artifactModels.has(filePath)) {
    if (!(state.artifact.openFiles || []).includes(filePath)) {
      state.artifact.openFiles = [...(state.artifact.openFiles || []), filePath];
      renderArtifactTabs();
    }
    activateOpenArtifactFile(filePath);
    return;
  }
  const requestId = ++artifactFileRequestId;
  const artifactId = state.artifact.id;
  setArtifactFileLoading(true);
  try {
    const editorReady = await initArtifactEditor();
    if (!editorReady) {
      return;
    }
    const url = apiUrl(`/artifact/${artifactId}/file?path=${encodeURIComponent(filePath)}`);
    const response = await fetch(url, {
      headers: {
        Accept: "text/plain",
        ...apiAuthHeaders(),
      },
    });
    if (!response.ok) {
      throw new Error(`HTTP ${response.status}`);
    }
    const content = await response.text();
    if (requestId !== artifactFileRequestId || artifactId !== state.artifact.id) {
      return;
    }

    const model = createArtifactModel(content, filePath);
    if (!model) return;
    artifactModels.set(filePath, model);
    state.artifact.openFiles = [...(state.artifact.openFiles || []), filePath];
    state.artifact.activeFile = filePath;
    dirtyArtifactFiles.delete(filePath);
    renderArtifactTabs();
    activateArtifactModel(filePath);
    setArtifactDirty(false);
    if (el.saveFileBtn) {
      el.saveFileBtn.classList.remove("hidden");
      el.saveFileBtn.textContent = "Save";
    }

    updateTreeSelection();
    syncArtifactEditorStatusBar();
    setStatus(`Opened: ${filePath}`);
  } catch (error) {
    setStatus(error, { prefix: "Failed to open file.", error: true });
  } finally {
    if (requestId === artifactFileRequestId) {
      setArtifactFileLoading(false);
    }
  }
}

export async function saveCurrentFile() {
  return saveArtifactFile(state.artifact.activeFile);
}

async function saveArtifactFile(filePath) {
  if (!state.artifact.id || !filePath || !artifactModels.has(filePath)) return false;
  const artifactId = state.artifact.id;
  const model = artifactModels.get(filePath);
  const content = model.getValue();
  try {
    await api(`/artifact/${artifactId}/files`, {
      method: "PUT",
      body: JSON.stringify({ path: filePath, content }),
    });
    if (state.artifact.id !== artifactId) return false;
    if (artifactModels.get(filePath) === model && model.getValue() === content) {
      dirtyArtifactFiles.delete(filePath);
    }
    if (state.artifact.activeFile === filePath) {
      setArtifactDirty(dirtyArtifactFiles.has(filePath));
    } else {
      updateArtifactTabStates();
    }
    setStatus(`Saved: ${filePath}`);
    return !dirtyArtifactFiles.has(filePath);
  } catch (error) {
    setStatus(error, { prefix: "Save failed.", error: true });
    return false;
  }
}

function finishArtifactCreateDialog(value) {
  const overlay = el.artifactCreateOverlay;
  if (!overlay) return;
  overlay.classList.add("hidden");
  overlay.setAttribute("aria-hidden", "true");
  document.body.classList.remove("modal-open");
  const resolve = artifactCreateDialogResolve;
  artifactCreateDialogResolve = null;
  resolve?.(value);
}

function bindArtifactCreateDialog() {
  if (artifactCreateDialogBound || !el.artifactCreateForm) return;
  artifactCreateDialogBound = true;

  el.artifactCreateForm.addEventListener("submit", (event) => {
    event.preventDefault();
    const name = el.artifactCreateNameInput?.value.trim() || "";
    const error = !name
      ? "Enter a name."
      : name === "." || name === ".." || /[\\/:]/.test(name)
        ? "Enter a single name without slashes."
        : "";
    if (error) {
      if (el.artifactCreateError) {
        el.artifactCreateError.textContent = error;
        el.artifactCreateError.classList.remove("hidden");
      }
      el.artifactCreateNameInput?.focus();
      return;
    }
    finishArtifactCreateDialog(name);
  });

  el.artifactCreateCancelBtn?.addEventListener("click", () => finishArtifactCreateDialog(null));
  el.artifactCreateOverlay?.addEventListener("click", (event) => {
    if (event.target === el.artifactCreateOverlay) finishArtifactCreateDialog(null);
  });
  el.artifactCreateForm.addEventListener("keydown", (event) => {
    if (event.key === "Escape") {
      event.preventDefault();
      finishArtifactCreateDialog(null);
      return;
    }
    if (event.key !== "Tab") return;
    const focusable = [
      el.artifactCreateNameInput,
      el.artifactCreateCancelBtn,
      el.artifactCreateSubmitBtn,
    ].filter(Boolean);
    const first = focusable[0];
    const last = focusable.at(-1);
    if (event.shiftKey && document.activeElement === first) {
      event.preventDefault();
      last?.focus();
    } else if (!event.shiftKey && document.activeElement === last) {
      event.preventDefault();
      first?.focus();
    }
  });
}

function requestArtifactEntryName(kind, directory, initialName = "") {
  if (!el.artifactCreateOverlay || !el.artifactCreateForm || !el.artifactCreateNameInput) {
    return Promise.resolve(null);
  }
  if (artifactCreateDialogResolve) finishArtifactCreateDialog(null);
  bindArtifactCreateDialog();
  const isRename = kind.startsWith("rename-");
  const entryKind = kind.endsWith("file") ? "file" : "folder";
  const label = isRename
    ? `Rename ${entryKind === "file" ? "File" : "Folder"}`
    : `New ${entryKind === "file" ? "File" : "Folder"}`;
  if (el.artifactCreateTitle) el.artifactCreateTitle.textContent = label;
  if (el.artifactCreateSubtitle) {
    el.artifactCreateSubtitle.textContent = `${isRename ? "Rename in" : "Create in"} ${directory || "project root"}.`;
  }
  if (el.artifactCreateNameInput) {
    el.artifactCreateNameInput.value = initialName;
    el.artifactCreateNameInput.placeholder =
      entryKind === "file" ? "e.g. README.md" : "e.g. assets";
  }
  if (el.artifactCreateError) {
    el.artifactCreateError.textContent = "";
    el.artifactCreateError.classList.add("hidden");
  }
  if (el.artifactCreateSubmitBtn)
    el.artifactCreateSubmitBtn.textContent = isRename ? "Rename" : "Create";
  el.artifactCreateOverlay.classList.remove("hidden");
  el.artifactCreateOverlay.setAttribute("aria-hidden", "false");
  document.body.classList.add("modal-open");
  requestAnimationFrame(() => {
    el.artifactCreateNameInput?.focus();
    if (isRename) el.artifactCreateNameInput?.select();
  });
  return new Promise((resolve) => {
    artifactCreateDialogResolve = resolve;
  });
}

async function preserveEditorChangesBeforeStructureEdit() {
  const dirtyPaths = [...dirtyArtifactFiles];
  for (const filePath of dirtyPaths) {
    if (!(await saveArtifactFile(filePath))) return false;
  }
  return true;
}

export async function createArtifactFile() {
  if (!state.artifact.id) return;
  const directory = state.artifact.selectedDirectory || "";
  const name = await requestArtifactEntryName("file", directory);
  if (!name || !state.artifact.id || !(await preserveEditorChangesBeforeStructureEdit())) return;
  const path = directory ? `${directory}/${name}` : name;
  try {
    const expansion = captureArtifactTreeExpansion();
    const treeCollapsed = state.artifact.treeCollapsed;
    if (directory) expansion[directory] = true;
    const record = await api(`/artifact/${state.artifact.id}/files`, {
      method: "POST",
      body: JSON.stringify({ path, content: "" }),
    });
    await loadArtifactRecord(record, {
      collapseTree: false,
      expandedDirectories: expansion,
      treeCollapsed,
      preserveOpenFiles: true,
    });
    state.artifact.selectedDirectory = directory;
    await openArtifactFile(path);
    setStatus(`Created: ${path}`);
  } catch (error) {
    setStatus(error, { prefix: "Could not create file.", error: true });
  }
}

export async function createArtifactDirectory() {
  if (!state.artifact.id) return;
  const parent = state.artifact.selectedDirectory || "";
  const name = await requestArtifactEntryName("folder", parent);
  if (!name || !state.artifact.id || !(await preserveEditorChangesBeforeStructureEdit())) return;
  const path = parent ? `${parent}/${name}` : name;
  try {
    const expansion = captureArtifactTreeExpansion();
    const treeCollapsed = state.artifact.treeCollapsed;
    if (parent) expansion[parent] = true;
    expansion[path] = false;
    const record = await api(`/artifact/${state.artifact.id}/directories`, {
      method: "POST",
      body: JSON.stringify({ path }),
    });
    await loadArtifactRecord(record, {
      collapseTree: false,
      expandedDirectories: expansion,
      treeCollapsed,
      preserveOpenFiles: true,
    });
    state.artifact.selectedDirectory = path;
    setStatus(`Created folder: ${path}`);
  } catch (error) {
    setStatus(error, { prefix: "Could not create folder.", error: true });
  }
}

export function openArtifactContextMenu(event) {
  const menu = el.artifactContextMenu;
  const target = event.target instanceof Element ? event.target : null;
  if (!menu || !target || !state.artifact.id) return;
  if (menu.parentElement !== document.body) {
    document.body.appendChild(menu);
  }

  const directory = target.closest(".file-tree-dir");
  const file = target.closest(".file-tree-item");
  const children = target.closest(".file-tree-children");
  if (directory) {
    artifactContextTarget = { kind: "directory", path: directory.dataset.directoryPath || "" };
  } else if (file) {
    artifactContextTarget = { kind: "file", path: file.dataset.path || "" };
  } else {
    artifactContextTarget = null;
  }
  if (directory) {
    state.artifact.selectedDirectory = directory.dataset.directoryPath || "";
  } else if (file) {
    const filePath = file.dataset.path || "";
    const separator = filePath.lastIndexOf("/");
    state.artifact.selectedDirectory = separator < 0 ? "" : filePath.slice(0, separator);
  } else if (children) {
    state.artifact.selectedDirectory = children.dataset.directoryPath || "";
  } else {
    state.artifact.selectedDirectory = "";
  }

  if (el.artifactContextRenameBtn) {
    const canRename = Boolean(
      artifactContextTarget?.path &&
        !isGeneratedArtifactPath(artifactContextTarget.path, artifactContextTarget.kind),
    );
    el.artifactContextRenameBtn.disabled = !canRename;
    el.artifactContextRenameBtn.setAttribute("aria-disabled", String(!canRename));
  }

  artifactContextMenuReturnFocus = directory || file || target;
  menu.style.left = `${Math.max(8, event.clientX)}px`;
  menu.style.top = `${Math.max(8, event.clientY)}px`;
  menu.classList.remove("hidden");
  menu.setAttribute("aria-hidden", "false");

  requestAnimationFrame(() => {
    if (menu.classList.contains("hidden")) return;
    const bounds = menu.getBoundingClientRect();
    const left = Math.max(8, Math.min(event.clientX, window.innerWidth - bounds.width - 8));
    const top = Math.max(8, Math.min(event.clientY, window.innerHeight - bounds.height - 8));
    menu.style.left = `${left}px`;
    menu.style.top = `${top}px`;
  });
  menu.querySelector('[role="menuitem"]')?.focus();
}

export function closeArtifactContextMenu({ restoreFocus = false } = {}) {
  const menu = el.artifactContextMenu;
  if (!menu) return;
  menu.classList.add("hidden");
  menu.setAttribute("aria-hidden", "true");
  if (restoreFocus && artifactContextMenuReturnFocus?.isConnected) {
    artifactContextMenuReturnFocus.focus();
  }
  artifactContextMenuReturnFocus = null;
}

export async function renameArtifactEntry() {
  const target = artifactContextTarget;
  artifactContextTarget = null;
  if (!state.artifact.id || !target?.path || isGeneratedArtifactPath(target.path, target.kind))
    return;
  const sourcePath = target.path;
  const kind = target.kind;
  const separator = sourcePath.lastIndexOf("/");
  const parent = separator < 0 ? "" : sourcePath.slice(0, separator);
  const initialName = sourcePath.slice(separator + 1);
  const name = await requestArtifactEntryName(`rename-${kind}`, parent, initialName);
  if (!name || !state.artifact.id || !(await preserveEditorChangesBeforeStructureEdit())) return;
  if (name === initialName) {
    setStatus(`Name unchanged: ${sourcePath}`);
    return;
  }

  const destination = parent ? `${parent}/${name}` : name;
  const expansion = captureArtifactTreeExpansion();
  const treeCollapsed = state.artifact.treeCollapsed;
  const activeFile = state.artifact.activeFile;
  let renamedActiveFile = activeFile;
  const renamedPaths = {};
  if (kind === "directory") {
    const remappedExpansion = {};
    Object.entries(expansion).forEach(([path, isOpen]) => {
      if (path === sourcePath || path.startsWith(`${sourcePath}/`)) {
        remappedExpansion[`${destination}${path.slice(sourcePath.length)}`] = isOpen;
      } else {
        remappedExpansion[path] = isOpen;
      }
    });
    Object.assign(expansion, remappedExpansion);
    Object.keys(expansion).forEach((path) => {
      if (path === sourcePath || path.startsWith(`${sourcePath}/`)) delete expansion[path];
    });
    if (activeFile === sourcePath || activeFile?.startsWith(`${sourcePath}/`)) {
      renamedActiveFile = `${destination}${activeFile.slice(sourcePath.length)}`;
    }
    (state.artifact.openFiles || []).forEach((path) => {
      if (path.startsWith(`${sourcePath}/`)) {
        renamedPaths[path] = `${destination}${path.slice(sourcePath.length)}`;
      }
    });
  } else if (activeFile === sourcePath) {
    renamedActiveFile = destination;
  }
  if (kind === "file" && (state.artifact.openFiles || []).includes(sourcePath)) {
    renamedPaths[sourcePath] = destination;
  }

  try {
    const record = await api(`/artifact/${state.artifact.id}/rename`, {
      method: "PUT",
      body: JSON.stringify({ sourcePath, newName: name }),
    });
    remapArtifactOpenFiles(renamedPaths);
    await loadArtifactRecord(record, {
      collapseTree: false,
      expandedDirectories: expansion,
      treeCollapsed,
      preserveOpenFiles: true,
    });
    state.artifact.selectedDirectory = kind === "directory" ? destination : parent;
    if (renamedActiveFile && renamedActiveFile !== activeFile) {
      await openArtifactFile(renamedActiveFile);
    }
    setStatus(`Renamed: ${sourcePath} → ${destination}`);
  } catch (error) {
    setStatus(error, { prefix: "Could not rename artifact entry.", error: true });
  } finally {
    artifactContextTarget = null;
  }
}

export function openArtifactArchivePicker() {
  if (state.artifact.id) el.artifactArchiveInput?.click();
}

export async function importArtifactArchive(file) {
  if (!state.artifact.id || !file || !(await preserveEditorChangesBeforeStructureEdit())) return;
  const confirmed = await confirmAction({
    title: "Import artifact ZIP?",
    message:
      "Import this ZIP as the current artifact project? Files missing from the ZIP will be removed, and all generated files must be present. The current generator trace manifest will be kept.",
    confirmLabel: "Import ZIP",
  });
  if (!confirmed) {
    return;
  }
  const form = new FormData();
  form.append("file", file);
  try {
    const record = await api(`/artifact/${state.artifact.id}/archive`, {
      method: "POST",
      body: form,
    });
    await loadArtifactRecord(record, { collapseTree: false, preserveOpenFiles: false });
    setStatus(`Imported ${file.name} into ${state.artifact.name}`);
  } catch (error) {
    setStatus(error, { prefix: "Artifact ZIP import failed.", error: true });
  } finally {
    if (el.artifactArchiveInput) el.artifactArchiveInput.value = "";
  }
}

export async function downloadCurrentArtifact() {
  if (!state.artifact.id) {
    setStatus("No artifact loaded");
    return;
  }
  const url = apiUrl(`/artifact/${state.artifact.id}/download`);
  try {
    const response = await fetch(url, { headers: apiAuthHeaders() });
    if (!response.ok) {
      throw new Error(`HTTP ${response.status}`);
    }
    const blob = await response.blob();
    const contentDisposition = response.headers.get("content-disposition") || "";
    const filenameMatch = contentDisposition.match(/filename="?([^";]+)"?/i);
    const filename = filenameMatch?.[1] || `${state.artifact.name || "artifact"}.zip`;
    const objectUrl = URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.href = objectUrl;
    link.download = filename;
    link.click();
    URL.revokeObjectURL(objectUrl);
    setStatus(`Downloaded artifacts: ${filename}`);
  } catch (error) {
    setStatus(error, { prefix: "Download failed.", error: true });
  }
}
