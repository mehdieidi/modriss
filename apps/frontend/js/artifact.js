import { state } from "./state.js";
import { el } from "./dom.js";
import { api, apiAuthHeaders } from "./api.js";
import { apiUrl } from "./config.js";
import { setStatus } from "./status.js";

const MONACO_VS_PATH = "/vendor/monaco/vs";
const DEFAULT_LANGUAGE = "plaintext";
const FALLBACK_LANGUAGE_LABEL = "Plain Text";
const DEFAULT_TAB_SIZE = 2;

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
let monacoThemeObserver = null;
let applyingProgrammaticEdit = false;
let currentLanguageLabel = FALLBACK_LANGUAGE_LABEL;

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

function setEditorFileName(filePath) {
  if (!el.artifactEditorFileName) {
    return;
  }
  const display = toFileDisplayName(filePath);
  el.artifactEditorFileName.textContent = display;
  el.artifactEditorFileName.title = filePath || display;
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
  if (!el.saveFileBtn) {
    return;
  }
  el.saveFileBtn.textContent = state.artifact.dirty ? "Save *" : "Save";
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

function disposeArtifactModel() {
  if (monacoModel) {
    monacoModel.dispose();
    monacoModel = null;
  }
}

function setArtifactEditorValue(value, filePath = "") {
  if (!monacoEditor || !window.monaco?.editor) {
    return;
  }
  const monaco = window.monaco;
  const languageId = detectLanguageId(monaco, filePath);
  currentLanguageLabel = languageLabelForId(monaco, languageId);

  applyingProgrammaticEdit = true;
  disposeArtifactModel();
  const uri = monaco.Uri.parse(`artifact:///${encodeURI(filePath || "untitled.txt")}`);
  const model = monaco.editor.createModel(value || "", languageId, uri);
  monacoModel = model;
  monacoEditor.setModel(model);
  monacoEditor.updateOptions({
    readOnly: !state.artifact.activeFile,
  });
  monacoEditor.setScrollTop(0);
  monacoEditor.setScrollLeft(0);
  applyingProgrammaticEdit = false;
  syncArtifactEditorStatusBar();
}

function getArtifactEditorValue() {
  return monacoEditor?.getValue() || "";
}

function resetArtifactEditor() {
  state.artifact.activeFile = null;
  setArtifactDirty(false);
  setEditorFileName("");
  currentLanguageLabel = FALLBACK_LANGUAGE_LABEL;
  if (monacoEditor) {
    setArtifactEditorValue("", "");
    monacoEditor.updateOptions({ readOnly: true });
  }
  if (el.saveFileBtn) {
    el.saveFileBtn.classList.add("hidden");
    el.saveFileBtn.textContent = "Save";
  }
  syncArtifactEditorStatusBar();
}

function createTree(files) {
  const root = { dirs: new Map(), files: [] };
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

function buildTreeNode(node, depth = 0) {
  const fragment = document.createDocumentFragment();
  const initiallyOpen = !state.artifact.treeCollapsed;

  const directories = [...node.dirs.keys()].sort((a, b) => a.localeCompare(b));
  directories.forEach((dirName) => {
    const wrapper = document.createElement("div");

    const dirHeader = document.createElement("button");
    dirHeader.type = "button";
    dirHeader.className = initiallyOpen ? "file-tree-dir open" : "file-tree-dir";
    dirHeader.appendChild(createIndent(depth));
    const chevronIcon = createIcon(
      initiallyOpen ? "chevron_down" : "chevron_right",
      "file-tree-dir-chevron",
    );
    dirHeader.appendChild(chevronIcon);
    const folderIcon = createIcon(initiallyOpen ? "folder_open" : "folder", "file-tree-icon");
    dirHeader.appendChild(folderIcon);
    const label = document.createElement("span");
    label.className = "file-tree-label";
    label.textContent = dirName;
    dirHeader.appendChild(label);

    const children = document.createElement("div");
    children.className = "file-tree-children";
    children.style.display = initiallyOpen ? "" : "none";
    children.appendChild(buildTreeNode(node.dirs.get(dirName), depth + 1));

    dirHeader.addEventListener("click", () => {
      const isOpen = dirHeader.classList.toggle("open");
      children.style.display = isOpen ? "" : "none";
      setIconSource(chevronIcon, isOpen ? "chevron_down" : "chevron_right");
      setIconSource(folderIcon, isOpen ? "folder_open" : "folder");
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
    if (!state.artifact.dirty) {
      setArtifactDirty(true);
    }
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
  state.artifact.id = null;
  state.artifact.name = "";
  state.artifact.files = [];
  state.artifact.activeFile = null;
  state.artifact.treeCollapsed = false;
  setExplorerRootLabel("No artifact loaded");
  updateTreeToggleButton();
  renderFileTree([]);
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
  updateTreeToggleButton();
  renderFileTree(state.artifact.files);
}

export function toggleArtifactTreeCollapsed() {
  setArtifactTreeCollapsed(!state.artifact.treeCollapsed);
}

export async function loadArtifactRecord(record, { collapseTree = true } = {}) {
  state.artifact.id = record.id;
  state.artifact.name = record.name || "artifact";
  setArtifactDirty(false);

  const files = record.files || record.modelJson?.files || {};
  state.artifact.files = Object.keys(files).map((path) => ({ path }));
  if (typeof collapseTree === "boolean") {
    state.artifact.treeCollapsed = collapseTree;
  }

  setExplorerRootLabel(state.artifact.name);
  updateTreeToggleButton();
  resetArtifactEditor();
  renderFileTree(state.artifact.files);
  setStatus(`Loaded artifact: ${state.artifact.name} (${state.artifact.files.length} files)`);
}

export async function loadArtifactById(id, { collapseTree = true } = {}) {
  try {
    const record = await api(`/artifact/${id}`);
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
    setStatus(error, { prefix: "Failed to load artifact.", error: true });
  }
}

export async function loadCurrentProjectArtifact(options = {}) {
  if (!state.project?.id) {
    clearArtifactState();
    return null;
  }
  if (!options.forceReload && state.artifact.id && state.artifact.files.length) {
    return {
      id: state.artifact.id,
      name: state.artifact.name,
      files: state.artifact.files,
    };
  }
  try {
    const records = await api(`/artifact?projectId=${state.project.id}`);
    const record = Array.isArray(records) ? records[0] : null;
    if (!record?.id) {
      clearArtifactState();
      setStatus("No artifact has been generated for this project yet");
      return null;
    }
    await loadArtifactById(record.id, { ...options, collapseTree: options.collapseTree ?? true });
    return record;
  } catch (error) {
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
  if (!files.length) {
    const empty = document.createElement("div");
    empty.className = "file-tree-empty";
    empty.textContent = "No files found in this artifact.";
    el.fileTree.appendChild(empty);
    return;
  }

  const tree = createTree(files);
  el.fileTree.appendChild(buildTreeNode(tree, 0));
  updateTreeSelection();
}

export async function openArtifactFile(filePath) {
  if (!state.artifact.id) {
    return;
  }
  try {
    const editorReady = await initArtifactEditor();
    if (!editorReady) {
      return;
    }
    const url = apiUrl(`/artifact/${state.artifact.id}/file?path=${encodeURIComponent(filePath)}`);
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

    state.artifact.activeFile = filePath;
    setArtifactDirty(false);

    setEditorFileName(filePath);
    setArtifactEditorValue(content, filePath);
    if (el.saveFileBtn) {
      el.saveFileBtn.classList.remove("hidden");
      el.saveFileBtn.textContent = "Save";
    }

    updateTreeSelection();
    syncArtifactEditorStatusBar();
    setStatus(`Opened: ${filePath}`);
  } catch (error) {
    setStatus(error, { prefix: "Failed to open file.", error: true });
  }
}

export async function saveCurrentFile() {
  if (!state.artifact.id || !state.artifact.activeFile || !monacoEditor) {
    return;
  }
  const content = getArtifactEditorValue();
  try {
    await api(`/artifact/${state.artifact.id}/files`, {
      method: "PUT",
      body: JSON.stringify({ path: state.artifact.activeFile, content }),
    });
    setArtifactDirty(false);
    setStatus(`Saved: ${state.artifact.activeFile}`);
  } catch (error) {
    setStatus(error, { prefix: "Save failed.", error: true });
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
  } catch (error) {
    setStatus(error, { prefix: "Download failed.", error: true });
  }
}
