import { state } from "./state.js";
import { el } from "./dom.js";
import { api, isPlannedFeatureError } from "./api.js";
import { formatUserError } from "./errors.js";
import { setError, setStatus } from "./status.js";
import { apiUrl, CHAT_ATTACHMENT_MAX_BYTES, MODEL_TYPES, websocketUrl } from "./config.js";
import { toDiagram } from "./diagram.js";
import { renderDiagram } from "./canvas.js";
import { renderMarkdown } from "./markdown.js";
import { loadModelById } from "./model-ops.js";
import { syncMobileDockState } from "./mobile-ui.js";
import { hasUnsavedModelChanges } from "./model-save-ui.js";

const TERMINAL_WORKFLOW_STATES = new Set([
  "EXPLAINED",
  "FAILED",
  "WAITING_FOR_CHOICE",
  "APPLIED",
  "UNDONE",
]);

const WORKFLOW_LABELS = Object.freeze({
  PLANNING: "Planning",
  VALIDATING: "Validating",
  COMPLETING: "Completing",
  REPAIRING: "Repairing",
  WAITING_FOR_CHOICE: "Waiting for you",
  EXPLAINED: "Explained",
  FAILED: "Failed",
  APPLIED: "Applied",
  UNDONE: "Undone",
});

const THINKING_STAGE_LABELS = Object.freeze({
  READING_MODEL: "Reading your model",
  ANALYZING_SOURCE: "Analyzing source document",
  QUERYING_METAMODEL: "Inspecting the metamodel",
  PLANNING: "Planning changes",
  PREVIEWING_PATCH: "Previewing model operations",
  MODELING_PREVIEW: "Streaming canvas preview",
  VALIDATING: "Validating the change",
  COMPLETING: "Completing formal details",
  REPAIRING: "Refining the patch",
  APPLYING: "Applying changes",
  WAITING: "Waiting for input",
  COMPLETED: "Finished",
  FAILED: "Encountered an issue",
});

const RISK_LABELS = Object.freeze({
  LOW: "Low risk",
  MEDIUM: "Medium impact",
  HIGH: "High impact",
});

let chatBusyDepth = 0;
let chatActivityHistory = [];
let activeThinkingEl = null;
let thinkingSteps = [];
let thinkingStartTime = 0;
let thinkingProgress = null;
let activeTurnCanceling = false;
let streamingAssistantEl = null;
let streamingAssistantText = "";
// The chat window is a single DOM surface, while sessions are scoped by project and level.
// Track which scoped session owns the rendered surface so content cannot leak between levels.
let renderedChatScopeKey = null;

const CHAT_HISTORY_DAYS = 3;

function disconnectChatChannel(scopeKey) {
  const channel = state.chat.channels.get(scopeKey);
  if (channel?.handle) {
    try {
      channel.handle.close?.();
    } catch {
      // ignore cleanup errors
    }
  }
  state.chat.channels.delete(scopeKey);
}

function resetChatMessagesUi() {
  if (!el.chatMessages) {
    return;
  }
  el.chatMessages.innerHTML = "";
  el.chatMessages.appendChild(buildChatWelcomeCard());
}

function levelLabel(typeKey = state.activeType) {
  return MODEL_TYPES[typeKey]?.chatType || String(typeKey || "").toUpperCase();
}

function formatHistoryTimestamp(value) {
  if (!value) {
    return "";
  }
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return "";
  }
  const now = new Date();
  const sameDay =
    date.getFullYear() === now.getFullYear() &&
    date.getMonth() === now.getMonth() &&
    date.getDate() === now.getDate();
  if (sameDay) {
    return date.toLocaleTimeString([], { hour: "numeric", minute: "2-digit" });
  }
  return date.toLocaleString([], {
    month: "short",
    day: "numeric",
    hour: "numeric",
    minute: "2-digit",
  });
}

export function closeChatHistoryPanel() {
  el.chatHistoryPanel?.classList.add("hidden");
  state.chat.historyOpen = false;
}

export function syncChatOpenState() {
  const isOpen = el.chatWindow && !el.chatWindow.classList.contains("hidden");
  el.workspace?.classList.toggle("chat-open", isOpen);
  el.chatToggle?.classList.toggle("is-active", Boolean(isOpen));
  syncMobileDockState();
}

export function closeChatWindow() {
  closeChatHistoryPanel();
  el.chatWindow?.classList.remove("chat-window-expanded");
  el.chatExpandBtn?.setAttribute("aria-pressed", "false");
  el.chatInputRow?.classList.remove("chat-input-expanded");
  el.chatWindow?.classList.add("hidden");
  syncChatOpenState();
}

async function createChatSession(typeKey, { forceNew = false, resumeSessionId = null } = {}) {
  const response = await api("/chatbot/sessions", {
    method: "POST",
    body: JSON.stringify({
      modelType: MODEL_TYPES[typeKey].chatType,
      modelName: (state.tabs[typeKey]?.modelName || `${typeKey}-assistant`).trim(),
      initialDocument: "Initialized from web modeling editor",
      projectId: state.project.id,
      forceNew,
      resumeSessionId,
    }),
  });
  return {
    sessionId: response.sessionId,
    modelId: response.modelId || null,
    projectId: state.project.id,
  };
}

function chatScopeKey(typeKey = state.activeType) {
  const projectId = state.project?.id;
  return projectId ? `${projectId}:${typeKey}` : String(typeKey || "");
}

function workflowLabel(state) {
  return WORKFLOW_LABELS[state] || state || "Working";
}

function idleStageForWorkflow(workflowState) {
  return workflowState === "FAILED" ? "FAILED" : "COMPLETED";
}

export function resetChatActivityUi() {
  chatBusyDepth = 0;
  chatActivityHistory = [];
  clearThinkingStream();
  clearAssistantModelPreview({ restore: false });
  updateChatComposerActionButton();
}

function applyWorkflowSnapshot(_workflowState, _message = null) {}

export function resetChatForProjectChange() {
  for (const channel of state.chat.channels.values()) {
    try {
      channel.handle?.close?.();
    } catch {
      // ignore cleanup errors
    }
  }
  state.chat.channels.clear();
  state.chat.sessions.clear();
  state.chat.attachment = null;
  state.chat.historyOpen = false;
  renderedChatScopeKey = null;
  resetChatActivityUi();
  closeChatHistoryPanel();
  resetChatMessagesUi();
  updateChatAttachmentLabel();
}

function removeChatWelcome() {
  el.chatMessages.querySelector(".chat-welcome")?.remove();
}

function scrollChatToBottom() {
  el.chatMessages.scrollTop = el.chatMessages.scrollHeight;
}

function stripLegacyThinkingCancelButtons() {
  el.chatMessages?.querySelectorAll(".chat-thinking-cancel").forEach((node) => node.remove());
}

function ensureThinkingStream(initialMessage = null, stage = "PLANNING") {
  stripLegacyThinkingCancelButtons();
  if (!activeThinkingEl) {
    removeChatWelcome();
    const msg = document.createElement("div");
    msg.className = "chat-msg assistant chat-thinking-live";
    msg.dataset.chatKind = "thinking";

    const bubble = document.createElement("div");
    bubble.className = "chat-msg-bubble chat-thinking-bubble";

    const header = document.createElement("div");
    header.className = "chat-thinking-header";
    header.innerHTML =
      '<span class="chat-thinking-spinner" aria-hidden="true"></span><span class="chat-thinking-title">Working on your request</span>';
    bubble.appendChild(header);

    const status = document.createElement("div");
    status.className = "chat-thinking-status";
    status.setAttribute("aria-live", "polite");

    const stageEl = document.createElement("span");
    stageEl.className = "chat-thinking-step-stage";
    stageEl.textContent = THINKING_STAGE_LABELS[stage] || stage || "Working";

    const detail = document.createElement("span");
    detail.className = "chat-thinking-step-detail";
    detail.textContent = initialMessage || "Creating the model and updating the canvas.";

    const progress = document.createElement("span");
    progress.className = "chat-thinking-progress hidden";

    status.append(stageEl, detail, progress);
    bubble.appendChild(status);

    msg.appendChild(bubble);
    el.chatMessages.appendChild(msg);
    activeThinkingEl = msg;
    thinkingSteps = [];
    thinkingProgress = null;
    thinkingStartTime = Date.now();
  }
  if (initialMessage) {
    pushThinkingStep(initialMessage, stage);
  }
  scrollChatToBottom();
  return activeThinkingEl;
}

function renderThinkingSteps() {
  const status = activeThinkingEl?.querySelector(".chat-thinking-status");
  if (!status) return;
  const current = thinkingSteps[thinkingSteps.length - 1] || null;
  const stage = status.querySelector(".chat-thinking-step-stage");
  const detail = status.querySelector(".chat-thinking-step-detail");
  const progress = status.querySelector(".chat-thinking-progress");
  if (stage)
    stage.textContent = THINKING_STAGE_LABELS[current?.stage] || current?.stage || "Working";
  if (detail) detail.textContent = current?.message || "Working with the model.";
  if (progress) {
    const index = Number(thinkingProgress?.index) || 0;
    const count = Number(thinkingProgress?.count) || 0;
    progress.textContent = index && count ? `${index} / ${count}` : "";
    progress.classList.toggle("hidden", !(index && count));
  }
  scrollChatToBottom();
}
function pushThinkingStep(message, stage = null) {
  if (!message) {
    return;
  }
  ensureThinkingStream();
  const entry = { stage: stage || "PLANNING", message };
  const last = chatActivityHistory[chatActivityHistory.length - 1];
  if (!(last?.stage === entry.stage && last?.message === entry.message)) {
    chatActivityHistory.push(entry);
  }
  thinkingSteps = chatActivityHistory.map((item) => ({ ...item }));
  renderThinkingSteps();
  el.chatTypingIndicator?.classList.add("hidden");
}

function updateThinkingStatus(message, stage = null, progress = null) {
  if (progress) {
    thinkingProgress = progress;
  }
  pushThinkingStep(message, stage);
}

function finalizeThinkingStream() {
  if (!activeThinkingEl) {
    return;
  }
  const durationSec = Math.max(1, Math.round((Date.now() - thinkingStartTime) / 1000));
  const msg = activeThinkingEl;
  msg.classList.remove("chat-thinking-live");
  msg.classList.add("chat-thinking-done");
  msg.dataset.chatKind = "thinking-summary";

  const bubble = msg.querySelector(".chat-msg-bubble");
  if (!bubble) {
    activeThinkingEl = null;
    thinkingSteps = [];
    chatActivityHistory = [];
    return;
  }

  bubble.replaceChildren();
  const summaryEl = document.createElement("div");
  summaryEl.className = "chat-thinking-summary";
  summaryEl.textContent = `Worked for ${durationSec}s`;
  bubble.appendChild(summaryEl);
  activeThinkingEl = null;
  thinkingSteps = [];
  chatActivityHistory = [];
  thinkingProgress = null;
  activeTurnCanceling = false;
  scrollChatToBottom();
}

function clearThinkingStream() {
  activeThinkingEl?.remove();
  activeThinkingEl = null;
  thinkingSteps = [];
  chatActivityHistory = [];
  thinkingProgress = null;
  activeTurnCanceling = false;
}

async function cancelActiveChatTurn() {
  if (activeTurnCanceling) {
    return;
  }
  const sessionId = state.chat.sessions.get(chatScopeKey())?.sessionId;
  if (!sessionId) {
    return;
  }
  activeTurnCanceling = true;
  updateChatComposerActionButton();
  updateThinkingStatus("Cancel requested. Waiting for the backend to stop safely.", "CANCELING");
  clearAssistantModelPreview({ restore: true });
  try {
    await api(`/chatbot/sessions/${sessionId}/cancel`, { method: "POST" });
  } catch (error) {
    activeTurnCanceling = false;
    updateChatComposerActionButton();
    throw error;
  }
}

function updateChatComposerActionButton() {
  if (!el.chatSendBtn) {
    return;
  }
  const busy = chatBusyDepth > 0;
  el.chatSendBtn.classList.toggle("is-stopping", busy);
  el.chatSendBtn.disabled = busy && activeTurnCanceling;
  if (busy) {
    const label = activeTurnCanceling ? "Stopping..." : "Stop";
    el.chatSendBtn.title = label;
    el.chatSendBtn.setAttribute("aria-label", label);
  } else {
    el.chatSendBtn.title = "Send message";
    el.chatSendBtn.setAttribute("aria-label", "Send message");
  }
}

export function handleChatSendButtonClick() {
  if (chatBusyDepth > 0) {
    cancelActiveChatTurn().catch((error) => {
      setError(error, { prefix: "Could not cancel assistant turn." });
    });
    return;
  }
  sendChatMessage();
}

export function initChatComposer() {
  stripLegacyThinkingCancelButtons();
  updateChatComposerActionButton();
}

function cloneValue(value) {
  return value == null ? value : structuredClone(value);
}

function ensureCanvasTraceOverlay() {
  let overlay = document.getElementById("assistantCanvasTrace");
  if (overlay) {
    return overlay;
  }
  overlay = document.createElement("div");
  overlay.id = "assistantCanvasTrace";
  overlay.className = "assistant-canvas-trace hidden";
  overlay.setAttribute("aria-live", "polite");
  overlay.innerHTML =
    '<div class="assistant-canvas-trace-kicker">AI modeling</div><div class="assistant-canvas-trace-title"></div><div class="assistant-canvas-trace-meta"></div>';
  (el.canvasViewport || document.body).appendChild(overlay);
  return overlay;
}

function updateCanvasTraceOverlay(payload) {
  const overlay = ensureCanvasTraceOverlay();
  const title = overlay.querySelector(".assistant-canvas-trace-title");
  const meta = overlay.querySelector(".assistant-canvas-trace-meta");
  const operationIndex = Number(payload?.operationIndex) || 0;
  const operationCount = Number(payload?.operationCount) || 0;
  const label = payload?.operationLabel || "Previewing model update";
  const phase = String(payload?.phase || "").toLowerCase();
  const phaseLabel = phase === "draft" ? "draft" : phase === "validated" ? "validated" : "preview";
  if (title) {
    title.textContent = label;
  }
  if (meta) {
    meta.textContent =
      operationIndex && operationCount
        ? `${operationIndex} / ${operationCount} ${phaseLabel} operations`
        : `${phaseLabel[0].toUpperCase()}${phaseLabel.slice(1)} preview`;
  }
  overlay.classList.remove("hidden");
  el.canvasViewport?.classList.add("assistant-preview-active");
}

function hideCanvasTraceOverlay() {
  document.getElementById("assistantCanvasTrace")?.classList.add("hidden");
  el.canvasViewport?.classList.remove("assistant-preview-active");
}

function capturePreviewSnapshot(typeKey, modelId) {
  return {
    typeKey,
    modelId,
    modelRevision: state.modelRevision || 0,
    baseModel: cloneValue(state.baseModel),
    diagram: cloneValue(state.diagram),
  };
}

function clearAssistantModelPreview({ restore = false } = {}) {
  const preview = state.assistantPreview;
  if (restore && preview?.active && preview.typeKey === state.activeType) {
    state.modelRevision = preview.modelRevision || state.modelRevision || 0;
    state.baseModel = cloneValue(preview.baseModel);
    state.diagram = cloneValue(preview.diagram);
    renderDiagram();
  }
  state.assistantPreview = null;
  hideCanvasTraceOverlay();
}

function updateModelingProgress(message, stage, payload) {
  const operationIndex = Number(payload?.operationIndex) || 0;
  const operationCount = Number(payload?.operationCount) || 0;
  updateThinkingStatus(message, stage, {
    index: operationIndex,
    count: operationCount,
  });
}

function applyAssistantModelPreview(typeKey, payload) {
  const model = unwrapAssistantModel(payload?.model || null);
  if (!model) {
    updateCanvasTraceOverlay(payload);
    return;
  }
  const modelId = String(payload?.modelId || "").trim();
  const liveModelId = String(state.modelId || "").trim();
  if (modelId && liveModelId && modelId !== liveModelId) {
    return;
  }
  if (!state.assistantPreview?.active) {
    state.assistantPreview = {
      active: true,
      ...capturePreviewSnapshot(typeKey, modelId || liveModelId),
    };
  }
  const phase = String(payload?.phase || "").toLowerCase();
  updateModelingProgress(
    phase === "validated"
      ? "Validating model changes on the canvas."
      : "Creating the model on the canvas.",
    "MODELING_PREVIEW",
    payload,
  );
  updateCanvasTraceOverlay(payload);
  state.baseModel = cloneValue(model);
  state.diagram = toDiagram(typeKey, model, state.tabs[typeKey]?.modelName);
  renderDiagram();
}

function applyHttpActivity(response) {
  const activity = response?.activity;
  if (!activity?.message) {
    return;
  }
  pushThinkingStep(activity.message, activity.stage);
}

function beginChatActivity(message, workflowState = null) {
  chatBusyDepth += 1;
  ensureThinkingStream(message, workflowState ? idleStageForWorkflow(workflowState) : "PLANNING");
  updateChatComposerActionButton();
}

function endChatActivity(message = null, workflowState = null) {
  chatBusyDepth = Math.max(0, chatBusyDepth - 1);
  if (chatBusyDepth === 0) {
    finalizeThinkingStream();
    if (workflowState && TERMINAL_WORKFLOW_STATES.has(workflowState)) {
      applyWorkflowSnapshot(workflowState, message);
    }
  }
  updateChatComposerActionButton();
}

function updateChatProviderLabel(provider) {
  if (!el.chatProviderLabel) {
    return;
  }
  const key = provider?.provider ? String(provider.provider).trim() : "";
  if (!key) {
    el.chatProviderLabel.classList.add("hidden");
    el.chatProviderLabel.textContent = "";
    return;
  }
  el.chatProviderLabel.textContent = ` · ${key}`;
  el.chatProviderLabel.classList.remove("hidden");
}

async function ensureChatRealtime(scopeKey, typeKey, sessionId) {
  const channel = state.chat.channels.get(scopeKey);
  if (channel?.kind === "websocket" && channel.handle?.readyState === WebSocket.OPEN) {
    return;
  }
  if (channel?.handle) {
    try {
      channel.handle.close?.();
    } catch {
      // ignore cleanup errors
    }
  }
  state.chat.channels.delete(scopeKey);
  await connectChatRealtime(scopeKey, typeKey, sessionId);
}

// ── Chat session / realtime ───────────────────────────────────────────────────

export async function ensureChatSession({ hydrate = true, ...options } = {}) {
  if (state.chat.available === false) {
    setStatus("Chat is not available in this backend build.");
    return null;
  }
  if (!state.project?.id) {
    setStatus("Open a project before starting chat.");
    return null;
  }
  const typeKey = state.activeType;
  const scopeKey = chatScopeKey(typeKey);
  const cached = state.chat.sessions.get(scopeKey);
  if (
    !options.forceNew &&
    !options.resumeSessionId &&
    cached?.sessionId &&
    cached.projectId === state.project.id
  ) {
    await ensureChatRealtime(scopeKey, typeKey, cached.sessionId);
    if (hydrate) {
      await hydrateChatThread(typeKey, cached.sessionId);
    }
    return cached;
  }
  if (cached) {
    disconnectChatChannel(scopeKey);
    state.chat.sessions.delete(scopeKey);
  }

  let response;
  try {
    response = await createChatSession(typeKey, options);
  } catch (error) {
    if (isPlannedFeatureError(error)) {
      state.chat.available = false;
      setStatus("Chat is not available in this backend build.");
      return null;
    }
    throw error;
  }

  const session = {
    sessionId: response.sessionId,
    modelId: response.modelId || null,
    projectId: state.project.id,
  };
  state.chat.sessions.set(scopeKey, session);
  await connectChatRealtime(scopeKey, typeKey, session.sessionId);
  if (hydrate) {
    await hydrateChatThread(typeKey, session.sessionId);
  }

  return session;
}

export async function prepareChatWindow() {
  const scopeKey = chatScopeKey();
  const isScopeChange = renderedChatScopeKey !== scopeKey;
  if (isScopeChange) {
    // An empty thread must render as empty for its own level, never as the last level's thread.
    resetChatMessagesUi();
    resetChatActivityUi();
  }
  stripLegacyThinkingCancelButtons();
  updateChatComposerActionButton();
  closeChatHistoryPanel();
  updateChatHeaderSubtitle();
  const session = await ensureChatSession({ hydrate: isScopeChange });
  if (session) {
    renderedChatScopeKey = scopeKey;
  }
  return session;
}

function updateChatHeaderSubtitle() {
  if (el.chatLevelLabel) {
    el.chatLevelLabel.textContent = levelLabel(state.activeType);
  }
}

export async function loadChatHistory() {
  if (!state.project?.id || state.chat.available === false) {
    return [];
  }
  const typeKey = state.activeType;
  const level = MODEL_TYPES[typeKey]?.chatType;
  if (!level) {
    return [];
  }
  const conversations = await api(
    `/chatbot/conversations?projectId=${encodeURIComponent(state.project.id)}&level=${encodeURIComponent(level)}&days=${CHAT_HISTORY_DAYS}`,
  );
  return Array.isArray(conversations) ? conversations : [];
}

function renderChatHistoryList(conversations) {
  if (!el.chatHistoryList) {
    return;
  }
  el.chatHistoryList.replaceChildren();
  const activeSessionId = state.chat.sessions.get(chatScopeKey())?.sessionId;

  if (!conversations.length) {
    const empty = document.createElement("div");
    empty.className = "chat-history-empty";
    empty.textContent = `No ${levelLabel()} conversations in the last ${CHAT_HISTORY_DAYS} days yet.`;
    el.chatHistoryList.appendChild(empty);
    return;
  }

  for (const conversation of conversations) {
    const item = document.createElement("button");
    item.type = "button";
    item.className = "chat-history-item";
    if (conversation.sessionId === activeSessionId) {
      item.classList.add("is-active");
    }

    const title = document.createElement("div");
    title.className = "chat-history-item-title";
    title.textContent = conversation.title || conversation.preview || "Conversation";

    const preview = document.createElement("div");
    preview.className = "chat-history-item-preview";
    preview.textContent = conversation.preview || "No messages yet";

    const meta = document.createElement("div");
    meta.className = "chat-history-item-meta";
    const time = document.createElement("span");
    time.textContent = formatHistoryTimestamp(conversation.updatedAt);
    const count = document.createElement("span");
    count.className = "chat-history-item-count";
    const messageCount = Number(conversation.messageCount) || 0;
    count.textContent = `${messageCount} message${messageCount === 1 ? "" : "s"}`;
    meta.append(time, count);

    item.append(title, preview, meta);
    item.addEventListener("click", () => {
      resumeChatConversation(conversation.sessionId).catch((error) => {
        setError(error, { prefix: "Could not open conversation." });
      });
    });
    el.chatHistoryList.appendChild(item);
  }
}

export async function toggleChatHistoryPanel() {
  if (!el.chatHistoryPanel) {
    return;
  }
  const willOpen = el.chatHistoryPanel.classList.contains("hidden");
  if (!willOpen) {
    closeChatHistoryPanel();
    return;
  }
  if (!state.project?.id) {
    setStatus("Open a project before browsing chat history.");
    return;
  }
  el.chatHistorySubtitle.textContent = `${levelLabel()} · past ${CHAT_HISTORY_DAYS} days`;
  el.chatHistoryPanel.classList.remove("hidden");
  state.chat.historyOpen = true;
  el.chatHistoryList.replaceChildren();
  const loading = document.createElement("div");
  loading.className = "chat-history-empty";
  loading.textContent = "Loading conversations...";
  el.chatHistoryList.appendChild(loading);
  try {
    const conversations = await loadChatHistory();
    renderChatHistoryList(conversations);
  } catch (error) {
    el.chatHistoryList.replaceChildren();
    const failure = document.createElement("div");
    failure.className = "chat-history-empty";
    failure.textContent = formatUserError(error, { prefix: "Could not load history." });
    el.chatHistoryList.appendChild(failure);
  }
}

export async function resumeChatConversation(sessionId) {
  if (!sessionId) {
    return;
  }
  const typeKey = state.activeType;
  const scopeKey = chatScopeKey(typeKey);
  disconnectChatChannel(scopeKey);
  state.chat.sessions.delete(scopeKey);
  state.chat.attachment = null;
  if (el.chatFileInput) {
    el.chatFileInput.value = "";
  }
  updateChatAttachmentLabel();
  resetChatActivityUi();
  closeChatHistoryPanel();

  const session = await ensureChatSession({ resumeSessionId: sessionId });
  if (!session) {
    return;
  }
  setStatus("Conversation restored");
}

export async function startNewChatConversation() {
  const typeKey = state.activeType;
  const scopeKey = chatScopeKey(typeKey);
  disconnectChatChannel(scopeKey);
  state.chat.sessions.delete(scopeKey);
  state.chat.attachment = null;
  if (el.chatFileInput) {
    el.chatFileInput.value = "";
  }
  updateChatAttachmentLabel();
  resetChatMessagesUi();
  resetChatActivityUi();
  closeChatHistoryPanel();

  const session = await ensureChatSession({ forceNew: true });
  if (!session) {
    return;
  }
  setStatus("Started a new conversation");
}

async function hydrateChatThread(typeKey, sessionId) {
  try {
    const thread = await api(`/chatbot/sessions/${sessionId}/thread`);
    const hasMessages = Array.isArray(thread.messages) && thread.messages.length > 0;
    const hasProposal = Boolean(thread.proposal);
    if (!hasMessages && !hasProposal) {
      resetChatActivityUi();
      return;
    }
    if (hasMessages) {
      el.chatMessages.innerHTML = "";
      for (const message of thread.messages) {
        const role = String(message.role || "").toLowerCase() === "user" ? "user" : "assistant";
        appendChat(role, message.content || "");
      }
    }
    if (thread.proposal) appendProposalCard(typeKey, sessionId, thread.proposal);
    if (thread.workflowState)
      applyWorkflowSnapshot(thread.workflowState, workflowLabel(thread.workflowState));
    else resetChatActivityUi();
    updateChatProviderLabel(thread.provider);
  } catch {
    resetChatActivityUi();
  }
}
export async function clearChatConversation() {
  return startNewChatConversation();
}

async function connectChatRealtime(scopeKey, typeKey, sessionId) {
  const wsUrl = websocketUrl(`/ws/chatbot/sessions/${sessionId}`);

  try {
    const socket = new WebSocket(wsUrl);
    await new Promise((resolve, reject) => {
      socket.onopen = () => resolve();
      socket.onerror = () => reject(new Error("websocket connection failed"));
    });

    socket.onmessage = (event) => {
      try {
        const data = JSON.parse(event.data);
        handleChatRealtimeEvent(typeKey, data?.type, data?.payload);
      } catch {
        // ignore malformed events
      }
    };
    socket.onclose = () => {
      if (state.chat.channels.get(scopeKey)?.kind === "websocket") {
        setStatus("Chat websocket disconnected");
      }
    };
    state.chat.channels.set(scopeKey, { kind: "websocket", handle: socket });
    return;
  } catch {
    // fallback to SSE
  }

  const stream = new EventSource(apiUrl(`/chatbot/sessions/${sessionId}/events`));
  stream.onmessage = () => {};
  stream.addEventListener("chat.assistant", (event) => {
    const payload = JSON.parse(event.data)?.payload;
    handleChatRealtimeEvent(typeKey, "chat.assistant", payload);
  });
  stream.addEventListener("model.updated", (event) => {
    const payload = JSON.parse(event.data)?.payload;
    handleChatRealtimeEvent(typeKey, "model.updated", payload);
  });
  stream.addEventListener("assistant.progress", (event) => {
    const payload = JSON.parse(event.data)?.payload;
    handleChatRealtimeEvent(typeKey, "assistant.progress", payload);
  });
  for (const eventName of [
    "assistant.text.delta",
    "assistant.plan",
    "model.delta",
    "assistant.worker.started",
    "assistant.worker.completed",
    "assistant.trace.started",
    "assistant.trace.step",
    "assistant.tool.started",
    "assistant.tool.completed",
    "assistant.turn.completed",
    "assistant.turn.failed",
  ]) {
    stream.addEventListener(eventName, (event) => {
      const payload = JSON.parse(event.data)?.payload;
      handleChatRealtimeEvent(typeKey, eventName, payload);
    });
  }
  stream.addEventListener("assistant.model.preview", (event) => {
    const payload = JSON.parse(event.data)?.payload;
    handleChatRealtimeEvent(typeKey, "assistant.model.preview", payload);
  });
  stream.onerror = () => {
    setStatus("Chat realtime stream disconnected");
  };
  state.chat.channels.set(scopeKey, { kind: "sse", handle: stream });
}

function handleChatRealtimeEvent(typeKey, eventType, payload) {
  // Realtime channels remain connected when the window is closed or the user changes tabs.
  // Only the active rendered scope may update this shared UI surface.
  if (renderedChatScopeKey !== chatScopeKey(typeKey)) {
    return;
  }
  if (eventType === "assistant.text.delta") {
    appendAssistantTextDelta(payload?.delta || "");
    return;
  }
  if (eventType === "assistant.plan") {
    const items = Array.isArray(payload?.items) ? payload.items : [];
    if (items.length)
      updateThinkingStatus(
        `Plan: ${items.map((item) => item.text || item).join(" · ")}`,
        "PLANNING",
      );
    return;
  }
  if (eventType === "model.delta") {
    applyAssistantModelPreview(typeKey, { model: payload?.model });
    return;
  }
  if (eventType === "assistant.worker.started" || eventType === "assistant.worker.completed") {
    updateThinkingStatus(
      `Document worker ${payload?.index || "?"}/${payload?.count || "?"} ${
        eventType.endsWith("completed") ? "completed" : "started"
      }`,
      "ANALYZING_SOURCE",
    );
    return;
  }
  if (eventType === "assistant.trace.started") {
    updateThinkingStatus("Started the modeling turn.", "PLANNING");
    return;
  }
  if (eventType === "assistant.trace.step" || eventType === "assistant.progress") {
    updateThinkingStatus(payload?.message || "Working with the model", payload?.stage);
    return;
  }
  if (eventType === "assistant.tool.started") {
    updateThinkingStatus(
      payload?.message || "Inspecting model context.",
      payload?.stage || "PLANNING",
    );
    return;
  }
  if (eventType === "assistant.tool.completed") {
    updateThinkingStatus(
      payload?.message || "Context inspection finished.",
      payload?.stage || "PLANNING",
    );
    return;
  }
  if (eventType === "assistant.turn.completed" || eventType === "assistant.turn.failed") {
    if (chatBusyDepth === 0) {
      applyWorkflowSnapshot(
        payload?.workflowState || (eventType.endsWith("failed") ? "FAILED" : "APPLIED"),
        payload?.message || "Assistant turn finished",
      );
    }
    return;
  }
  if (eventType === "assistant.model.preview") {
    applyAssistantModelPreview(typeKey, payload);
    return;
  }
  if (eventType === "chat.assistant") {
    const message = payload?.assistantMessage || payload?.message;
    if (streamingAssistantEl) {
      streamingAssistantEl.classList.remove("chat-msg-streaming");
      streamingAssistantEl = null;
      streamingAssistantText = "";
    }
    if (chatBusyDepth === 0) {
      appendAssistantDeduped(message);
    }
    const sessionId = state.chat.sessions.get(chatScopeKey(typeKey))?.sessionId;
    if (sessionId && chatBusyDepth === 0) {
      appendProposalCard(typeKey, sessionId, payload?.proposal);
      if (!payload?.proposal) {
      }
    }
    if (payload?.workflowState && chatBusyDepth === 0) {
      applyWorkflowSnapshot(
        payload.workflowState,
        payload?.activity?.message || workflowLabel(payload.workflowState),
      );
    }
    if (payload?.workflowState === "FAILED") {
      clearAssistantModelPreview({ restore: true });
    }
    return;
  }

  if (eventType === "model.updated") {
    const modelId = String(payload?.modelId || "").trim();
    if (!modelId) {
      return;
    }
    const operationIndex = Number(payload?.operationIndex) || null;
    const operationCount = Number(payload?.operationCount) || null;
    if (operationIndex && operationCount) {
      updateModelingProgress("Applying the model changes.", "APPLYING", payload);
    }
    void applyAssistantModelResponse(typeKey, {
      modelId,
      revision: payload?.revision,
    })
      .then(() => clearAssistantModelPreview({ restore: false }))
      .catch(() => clearAssistantModelPreview({ restore: true }));
  }
}

// ── Chat UI ───────────────────────────────────────────────────────────────────

export function buildChatWelcomeCard() {
  const welcome = document.createElement("div");
  welcome.className = "chat-welcome";
  welcome.innerHTML = `<span aria-hidden="true" class="chat-welcome-icon icon-svg icon-mask" style="--icon-src: url('/assets/icons/chatbot.svg');"></span>
    <div class="chat-welcome-title">Modeling Assistant</div>
    <div class="chat-welcome-text">I can help you create, modify, and understand your models. Ask me anything or describe what you'd like to build.</div>`;
  return welcome;
}

function appendChat(role, text) {
  removeChatWelcome();

  const msg = document.createElement("div");
  msg.className = `chat-msg ${role}`;
  msg.dataset.chatKind = "message";
  msg.dataset.chatText = text;

  const bubble = document.createElement("div");
  bubble.className = "chat-msg-bubble";
  if (role === "assistant") {
    bubble.appendChild(renderMarkdown(text));
  } else {
    bubble.textContent = text;
  }

  msg.appendChild(bubble);
  el.chatMessages.appendChild(msg);
  scrollChatToBottom();
}

function appendAssistantDeduped(text) {
  if (!text) {
    return;
  }
  const messages = [
    ...el.chatMessages.querySelectorAll('.chat-msg.assistant[data-chat-kind="message"]'),
  ];
  const last = messages[messages.length - 1];
  if (last?.dataset.chatText === text) {
    return;
  }
  appendChat("assistant", text);
}

function appendAssistantTextDelta(delta) {
  if (!delta) return;
  removeChatWelcome();
  if (!streamingAssistantEl) {
    streamingAssistantEl = document.createElement("div");
    streamingAssistantEl.className = "chat-msg assistant chat-msg-streaming";
    streamingAssistantEl.dataset.chatKind = "message";
    const bubble = document.createElement("div");
    bubble.className = "chat-msg-bubble";
    streamingAssistantEl.appendChild(bubble);
    el.chatMessages.appendChild(streamingAssistantEl);
    streamingAssistantText = "";
  }
  streamingAssistantText += delta;
  streamingAssistantEl.dataset.chatText = streamingAssistantText;
  const bubble = streamingAssistantEl.querySelector(".chat-msg-bubble");
  bubble.replaceChildren(renderMarkdown(streamingAssistantText));
  scrollChatToBottom();
}

function unwrapAssistantModel(model) {
  if (
    model &&
    typeof model === "object" &&
    model.modelJson &&
    typeof model.modelJson === "object"
  ) {
    return model.modelJson;
  }
  return model;
}

function appendProposalCard(typeKey, sessionId, proposal) {
  if (!proposal) {
    return;
  }
  if (
    proposal.id &&
    el.chatMessages.querySelector(`[data-chat-proposal-id="${CSS.escape(proposal.id)}"]`)
  ) {
    return;
  }

  const changeCount = Array.isArray(proposal?.patch?.operations)
    ? proposal.patch.operations.length
    : 0;
  const risk = String(proposal.riskLevel || "HIGH").toUpperCase();
  const issues = Array.isArray(proposal.validation?.issues) ? proposal.validation.issues : [];
  const validationPassed = proposal.validation?.mandatoryPassed !== false;

  const card = document.createElement("div");
  card.className = "chat-msg assistant";
  card.dataset.chatKind = "proposal";
  if (proposal.id) {
    card.dataset.chatProposalId = proposal.id;
  }

  const bubble = document.createElement("div");
  bubble.className = "chat-msg-bubble chat-proposal-card";

  const header = document.createElement("div");
  header.className = "chat-proposal-header";
  const title = document.createElement("div");
  title.className = "chat-proposal-title";
  title.textContent = "Applied model change";
  const riskBadge = document.createElement("span");
  riskBadge.className = `chat-proposal-risk chat-proposal-risk-${risk.toLowerCase()}`;
  riskBadge.textContent = RISK_LABELS[risk] || risk;
  header.append(title, riskBadge);
  bubble.appendChild(header);

  const intro = document.createElement("p");
  intro.className = "chat-proposal-intro";
  intro.textContent =
    changeCount === 1
      ? "The assistant applied one change to the canvas."
      : `The assistant applied ${changeCount} changes to the canvas.`;
  bubble.appendChild(intro);

  const affected = Array.isArray(proposal.affectedElements) ? proposal.affectedElements : [];
  if (affected.length) {
    const affectedBlock = document.createElement("div");
    affectedBlock.className = "chat-proposal-meta";
    affectedBlock.textContent = `${affected.length} element${affected.length === 1 ? "" : "s"} affected`;
    bubble.appendChild(affectedBlock);
  }

  const validation = document.createElement("div");
  validation.className = `chat-proposal-validation ${validationPassed ? "is-pass" : "is-fail"}`;
  validation.textContent = validationPassed
    ? "Validation passed and applied"
    : "Validation failed — changes were not applied";
  bubble.appendChild(validation);

  if (issues.length) {
    const issueList = document.createElement("ul");
    issueList.className = "chat-proposal-issues";
    for (const issue of issues.slice(0, 5)) {
      const item = document.createElement("li");
      item.textContent = issue.message || issue.constraint || "Validation issue";
      issueList.appendChild(item);
    }
    if (issues.length > 5) {
      const more = document.createElement("li");
      more.className = "chat-proposal-issues-more";
      more.textContent = `+${issues.length - 5} more issue(s)`;
      issueList.appendChild(more);
    }
    bubble.appendChild(issueList);
  }

  if (Array.isArray(proposal.citations) && proposal.citations.length) {
    const citations = document.createElement("details");
    citations.className = "chat-proposal-citations";
    const summary = document.createElement("summary");
    summary.textContent = `${proposal.citations.length} reference${proposal.citations.length === 1 ? "" : "s"}`;
    citations.appendChild(summary);
    const list = document.createElement("ul");
    for (const citation of proposal.citations) {
      const item = document.createElement("li");
      item.textContent = citation;
      list.appendChild(item);
    }
    citations.appendChild(list);
    bubble.appendChild(citations);
  }

  const actions = document.createElement("div");
  actions.className = "chat-proposal-actions";
  if (proposal.id) {
    const undo = document.createElement("button");
    undo.type = "button";
    undo.className = "chat-proposal-btn";
    undo.textContent = "Undo changes";
    undo.addEventListener("click", async () => {
      let response = null;
      try {
        beginChatActivity("Undoing the applied changes");
        setProposalActionsDisabled(actions, true);
        response = await api(`/chatbot/sessions/${sessionId}/proposals/${proposal.id}/undo`, {
          method: "POST",
        });
        endChatActivity(null, response?.workflowState || "UNDONE");
        appendAssistantDeduped(response.assistantMessage || "Changes undone.");
        await applyAssistantModelResponse(typeKey, response);
        setProposalDecision(card, "Undone");
      } catch (error) {
        if (chatBusyDepth > 0) {
          endChatActivity("Could not undo the changes.", "FAILED");
        }
        setProposalActionsDisabled(actions, false);
        appendChat("assistant", formatUserError(error));
      }
    });
    actions.appendChild(undo);
  }
  bubble.appendChild(actions);
  card.appendChild(bubble);
  el.chatMessages.appendChild(card);
  scrollChatToBottom();
}

function setProposalActionsDisabled(actions, disabled) {
  for (const button of actions.querySelectorAll("button")) {
    button.disabled = disabled;
  }
}

function setProposalDecision(card, label) {
  const actions = card.querySelector(".chat-proposal-actions");
  if (!actions) {
    return;
  }
  actions.replaceChildren();
  const status = document.createElement("span");
  status.className = "chat-proposal-decision";
  status.textContent = label;
  actions.appendChild(status);
}

async function applyAssistantModelResponse(
  typeKey,
  response,
  requestedModelId = null,
  requestDiagramFingerprint = null,
) {
  if (!response) {
    return;
  }
  const responseModelId = String(response.modelId || "").trim();
  const liveModelId = String(state.modelId || "").trim();
  const requestedModelIdValue = String(requestedModelId || "").trim();
  const liveDiagramFingerprint = JSON.stringify(state.diagram || {});
  const hasLocalEditsSinceRequest =
    requestDiagramFingerprint != null && liveDiagramFingerprint !== requestDiagramFingerprint;
  if (
    responseModelId &&
    liveModelId &&
    responseModelId !== liveModelId &&
    requestedModelIdValue &&
    liveModelId !== requestedModelIdValue
  ) {
    setStatus("Assistant response received for another model; skipped auto-apply");
    return;
  }
  if (hasLocalEditsSinceRequest) {
    setStatus("Assistant response received; kept your newer canvas edits");
    return;
  }
  if (responseModelId) {
    await loadModelById(typeKey, responseModelId);
    if (state.project) {
      state.project.activeModelIds = {
        ...(state.project.activeModelIds || {}),
        [typeKey]: responseModelId,
      };
    }
    clearAssistantModelPreview({ restore: false });
    return;
  }
  const model = unwrapAssistantModel(response.model || null);
  if (!model) {
    return;
  }
  state.modelRevision = Number(response.revision) || state.modelRevision || 1;
  state.baseModel = structuredClone(model);
  state.diagram = toDiagram(typeKey, model, state.tabs[typeKey]?.modelName);
  if (state.tabs[typeKey]) {
    state.tabs[typeKey].modelRevision = state.modelRevision;
    state.tabs[typeKey].baseModel = state.baseModel;
    state.tabs[typeKey].diagram = state.diagram;
    state.tabs[typeKey].modelName = model.name || state.tabs[typeKey].modelName;
  }
  renderDiagram();
  clearAssistantModelPreview({ restore: false });
}

export function updateChatAttachmentLabel() {
  const attachment = state.chat.attachment;
  if (attachment) {
    if (el.chatFileNameText) {
      el.chatFileNameText.textContent = attachment.name;
    }
    el.chatFileName?.classList.remove("hidden");
    el.chatAttachBar?.classList.remove("hidden");
  } else {
    el.chatFileName?.classList.add("hidden");
    el.chatAttachBar?.classList.add("hidden");
  }
}

export async function uploadChatAttachment(file) {
  const session = await ensureChatSession();
  if (!session) {
    return null;
  }
  const form = new FormData();
  form.append("file", file);
  return api(`/chatbot/sessions/${session.sessionId}/attachments`, {
    method: "POST",
    body: form,
  });
}

function defaultAttachmentMessage() {
  const level = levelLabel(state.activeType);
  if (level === "CIM") {
    return "Create a complete CIM model from the attached requirements document.";
  }
  return `Analyze the attached document and update the ${level} model.`;
}

// ── Send message ──────────────────────────────────────────────────────────────

export async function sendChatMessage() {
  if (chatBusyDepth > 0) {
    return;
  }
  const typedText = el.chatInput.value.trim();
  const attachment = state.chat.attachment;
  if (!typedText && !attachment) {
    return;
  }
  const text = typedText || defaultAttachmentMessage();

  beginChatActivity("Understanding your request");
  el.chatMessages.scrollTop = el.chatMessages.scrollHeight;

  let response = null;
  try {
    const session = await ensureChatSession();
    if (!session) {
      endChatActivity("Could not start chat session.", "FAILED");
      return;
    }
    const requestedModelId = state.modelId;
    const requestDiagramFingerprint = JSON.stringify(state.diagram || {});
    appendChat("user", text);
    el.chatInput.value = "";

    await ensureChatRealtime(chatScopeKey(state.activeType), state.activeType, session.sessionId);

    response = await api(`/chatbot/sessions/${session.sessionId}/messages`, {
      method: "POST",
      body: JSON.stringify({
        idempotencyKey:
          crypto?.randomUUID?.() || `${Date.now()}-${Math.random().toString(36).slice(2)}`,
        message: text,
        modelId: state.modelId,
        revision: state.modelRevision || null,
        activeView: state.activeType,
        selectedElementIds: [
          ...new Set(
            [
              ...(state.selectedNodeIds instanceof Set ? [...state.selectedNodeIds] : []),
              state.selectedNodeId,
              state.selectedConnectionId,
            ].filter(Boolean),
          ),
        ],
        attachmentIds: attachment?.id ? [attachment.id] : [],
        unsavedDraftPatch:
          hasUnsavedModelChanges() && state.baseModel ? JSON.stringify(state.baseModel) : null,
      }),
    });

    applyHttpActivity(response);
    endChatActivity(null, response?.workflowState || null);
    appendAssistantDeduped(response.assistantMessage || "Done");
    appendProposalCard(state.activeType, session.sessionId, response.proposal);
    if (!response.proposal) {
    }
    await applyAssistantModelResponse(
      state.activeType,
      response,
      requestedModelId,
      requestDiagramFingerprint,
    );
    if (!["WAITING_FOR_CHOICE", "FAILED"].includes(response?.workflowState)) {
      state.chat.attachment = null;
      if (el.chatFileInput) {
        el.chatFileInput.value = "";
      }
      updateChatAttachmentLabel();
    }
    setStatus("Assistant response received");
  } catch (error) {
    if (chatBusyDepth > 0) {
      endChatActivity("Could not complete the request.", "FAILED");
    }
    appendChat("assistant", formatUserError(error));
    setError(error, { prefix: "Chat failed." });
  }
}
