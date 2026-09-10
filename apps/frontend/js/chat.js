import { state } from "./state.js";
import { el } from "./dom.js";
import { api, apiAuthHeaders, isPlannedFeatureError } from "./api.js";
import { formatUserError } from "./errors.js";
import { setError, setStatus } from "./status.js";
import { apiUrl, MODEL_TYPES } from "./config.js";
import { toDiagram } from "./diagram.js";
import { renderDiagram, scrollToNodeAndHighlight } from "./canvas.js";
import { renderMarkdown } from "./markdown.js";
import { autoLayoutCurrentDiagram, loadModelById, saveCurrentModel } from "./model-ops.js";
import { syncMobileDockState } from "./mobile-ui.js";
import { hasUnsavedModelChanges } from "./model-save-ui.js";
import { applyTextDirection } from "./text-direction.js";
import { modelingAssistantConfig, modelingLevelConfig } from "./modeling-config-data.js";

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
  ROUTING: "Choosing an approach",
  BLUEPRINTING: "Designing the model structure",
  SLICING: "Building model elements",
  REVIEWING: "Reviewing model coverage",
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

const DURABLE_WORKFLOW_LABELS = Object.freeze({
  CONCEPTUAL_GENERATION: "Model generation",
  INSPECT_AGENT: "Model update",
  ANSWER: "Model explanation",
  EXPLAIN_MODEL: "Model explanation",
  EXPLAIN_METAMODEL: "Metamodel explanation",
  RESUME_REPAIR: "Resumed model update",
});

const ASSISTANT_TOOL_PROGRESS = Object.freeze({
  inspect_model: {
    stage: "READING_MODEL",
    started: "Reviewing the current model so the requested change fits what is already there.",
    completed: "Finished reviewing the current model structure.",
  },
  describe_types: {
    stage: "QUERYING_METAMODEL",
    started: "Checking the modeling rules and available element types needed for this request.",
    completed: "Finished checking the relevant modeling rules.",
  },
  commit_model_batch: {
    stage: "APPLYING",
    started: "Building the requested model changes in a working copy.",
    completed: "Model changes have been created; checking that they are structurally valid.",
  },
  answer_user: {
    stage: "COMPLETING",
    started: "Preparing a clear response based on the model context.",
    completed: "Response is ready.",
  },
  ask_user: {
    stage: "WAITING",
    started: "Identifying the one decision needed before the model can be updated.",
    completed: "A decision is needed to continue.",
  },
});

function assistantProgressForEvent(eventType, payload = {}) {
  const tool = String(payload?.tool || "")
    .trim()
    .toLowerCase();
  const toolProgress = ASSISTANT_TOOL_PROGRESS[tool];
  if (eventType === "assistant.trace.started") {
    return {
      stage: "PLANNING",
      message: "Understanding your request and preparing the next steps.",
    };
  }
  if (eventType === "assistant.trace.step") {
    const step = Number(payload?.step) || 1;
    return {
      stage: "PLANNING",
      message:
        step === 1
          ? "Reviewing your request and the available model context."
          : "Refining the approach using the information gathered so far.",
    };
  }
  if (eventType === "tool.started" || eventType === "assistant.tool.started") {
    return (
      toolProgress || {
        stage: "PLANNING",
        message: "Reviewing the model context needed to complete your request.",
      }
    );
  }
  if (eventType === "tool.completed" || eventType === "assistant.tool.completed") {
    if (tool === "commit_model_batch" && payload?.valid === false) {
      return {
        stage: "REPAIRING",
        message: "The first draft needs a small structural adjustment; refining it now.",
      };
    }
    return toolProgress
      ? { stage: toolProgress.stage, message: toolProgress.completed }
      : {
          stage: "PLANNING",
          message: "Finished reviewing the information needed for this request.",
        };
  }
  return {
    stage: payload?.stage || "PLANNING",
    message: payload?.message || "Working on your request.",
  };
}

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
let activeTurnId = null;
let streamingAssistantEl = null;
let streamingAssistantText = "";
// Every project switch invalidates asynchronous work that belongs to the previous
// project. The chat UI is shared, so callbacks must prove they still own it before
// writing to the DOM.
let chatGeneration = 0;
// SSE and durable-turn polling can report the same checkpoint at nearly the
// same time. Share its load so the canvas receives one incremental update.
const assistantModelApplyInFlight = new Map();
// The chat window is a single DOM surface, while sessions are scoped by project and level.
// Track which scoped session owns the rendered surface so content cannot leak between levels.
let renderedChatScopeKey = null;

const CHAT_HISTORY_DAYS = 3;
function assistantAvailableFor(typeKey = state.activeType) {
  return modelingLevelConfig(String(typeKey || "").toLowerCase()).assistantEnabled === true;
}

function disconnectChatChannel(scopeKey) {
  const channel = state.chat.channels.get(scopeKey);
  if (channel?.handle) {
    try {
      channel.handle.close?.();
      channel.handle.abort?.();
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

async function createChatSession(
  typeKey,
  { forceNew = false, resumeSessionId = null, projectId = state.project?.id } = {},
) {
  const response = await api("/chatbot/sessions", {
    method: "POST",
    body: JSON.stringify({
      modelType: MODEL_TYPES[typeKey].chatType,
      modelName: (state.tabs[typeKey]?.modelName || `${typeKey}-assistant`).trim(),
      initialDocument: "Initialized from web modeling editor",
      projectId,
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

function durableWorkflowLabel(workflowKind) {
  const key = String(workflowKind || "").toUpperCase();
  return DURABLE_WORKFLOW_LABELS[key] || "Modeling assistant";
}

function durableProgressForTurn(turn) {
  const stateName = String(turn?.state || "QUEUED").toUpperCase();
  if (DURABLE_TERMINAL_STATES.has(stateName)) {
    return {
      stage: stateName === "SUCCEEDED" ? "COMPLETED" : stateName,
      message: turn?.finalMessage || `Assistant turn ${stateName.toLowerCase()}.`,
      progress: null,
    };
  }
  const phase = String(turn?.phase || "QUEUED").toUpperCase();
  const workItems = Array.isArray(turn?.workItems) ? turn.workItems : [];
  const completed = workItems.filter((item) =>
    ["COMPLETED", "GENERATED"].includes(String(item?.status || "").toUpperCase()),
  ).length;
  const activeItem = workItems.find(
    (item) => String(item?.id || "") === String(turn?.currentWorkItemId || ""),
  );
  const progress = workItems.length
    ? {
        index: Math.min(workItems.length, completed + (completed < workItems.length ? 1 : 0)),
        count: workItems.length,
      }
    : null;
  switch (phase) {
    case "READING":
      return { stage: "READING_MODEL", message: "Reviewing the current model context.", progress };
    case "ROUTED":
      return {
        stage: "ROUTING",
        message: "Choosing the best modeling workflow for your request.",
        progress,
      };
    case "BLUEPRINTING":
      return {
        stage: "BLUEPRINTING",
        message: "Selecting exact metamodel types and designing a coherent model structure.",
        progress,
      };
    case "SLICING":
      return {
        stage: "SLICING",
        message: activeItem?.label
          ? `Building ${activeItem.label}.`
          : "Building the planned model elements and relationships.",
        progress,
      };
    case "REVIEWING":
      return {
        stage: "REVIEWING",
        message: "Reviewing requirement coverage, naming, and relationships before saving.",
        progress: workItems.length ? { index: workItems.length, count: workItems.length } : null,
      };
    case "COMPILER_RECOVERY_QUEUED":
      return {
        stage: "REPAIRING",
        message: "Repairing the rejected model slice while preserving completed work.",
        progress,
      };
    case "EXECUTING":
      return {
        stage: "APPLYING",
        message: activeItem?.label
          ? `Applying ${activeItem.label}.`
          : "Applying the next planned model change in a working copy.",
        progress,
      };
    default:
      return {
        stage: stateName === "RUNNING" ? "PLANNING" : stateName,
        message:
          stateName === "QUEUED"
            ? "Waiting to start the modeling turn."
            : "Working on your request.",
        progress,
      };
  }
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
  chatGeneration += 1;
  for (const channel of state.chat.channels.values()) {
    try {
      channel.handle?.close?.();
      channel.handle?.abort?.();
    } catch {
      // ignore cleanup errors
    }
  }
  state.chat.channels.clear();
  state.chat.sessions.clear();
  state.chat.attachments = [];
  state.chat.historyOpen = false;
  renderedChatScopeKey = null;
  activeTurnId = null;
  streamingAssistantEl = null;
  streamingAssistantText = "";
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

function ensureThinkingStream(initialMessage = null, stage = "PLANNING", anchorMessage = null) {
  stripLegacyThinkingCancelButtons();
  // A background thread hydration or DOM refresh can remove the old element. Never keep a
  // disconnected reference: the next turn must create a visible progress surface immediately.
  if (!activeThinkingEl?.isConnected) {
    activeThinkingEl = null;
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
    if (anchorMessage?.isConnected && anchorMessage.parentElement === el.chatMessages) {
      anchorMessage.after(msg);
    } else {
      el.chatMessages.appendChild(msg);
    }
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
  if (!activeTurnId) {
    return;
  }
  activeTurnCanceling = true;
  updateChatComposerActionButton();
  updateThinkingStatus("Cancel requested. Waiting for the backend to stop safely.", "CANCELING");
  clearAssistantModelPreview({ restore: true });
  try {
    await api(`/chatbot/turns/${activeTurnId}/cancel`, { method: "POST" });
  } catch (error) {
    activeTurnCanceling = false;
    updateChatComposerActionButton();
    throw error;
  }
}

const DURABLE_TERMINAL_STATES = new Set([
  "SUCCEEDED",
  "PARTIAL",
  "NEEDS_INPUT",
  "NEEDS_CONFIRMATION",
  "CONFLICTED",
  "CANCELLED",
  "TIMED_OUT",
  "FAILED",
]);

function streamDurableTurnEvents(turnId, typeKey, eventCursor = 0) {
  const controller = new AbortController();
  void fetch(
    apiUrl(`/chatbot/turns/${turnId}/events?eventCursor=${encodeURIComponent(eventCursor)}`),
    {
      headers: apiAuthHeaders({ Accept: "text/event-stream" }),
      signal: controller.signal,
    },
  )
    .then(async (response) => {
      if (!response.ok || !response.body) throw new Error("Durable SSE connection failed");
      const reader = response.body.getReader();
      const decoder = new TextDecoder();
      let buffer = "";
      while (true) {
        const { value, done } = await reader.read();
        if (done) break;
        buffer += decoder.decode(value, { stream: true });
        const frames = buffer.split("\n\n");
        buffer = frames.pop() || "";
        for (const frame of frames) {
          const eventName = frame.match(/^event:\s*(.+)$/m)?.[1];
          const raw = frame.match(/^data:\s*(.+)$/m)?.[1];
          if (!eventName || !raw) continue;
          try {
            const event = JSON.parse(raw);
            if (eventName === "turn.plan.ready") {
              const count =
                Number(event?.payload?.workItems) || Number(event?.payload?.slices) || 0;
              updateThinkingStatus(
                count
                  ? `Modeling plan ready: checkpoint 1 of ${count}.`
                  : "Durable modeling plan is ready.",
                "PLANNING",
                count ? { index: 1, count } : null,
              );
            } else if (eventName === "turn.work_item.started") {
              updateThinkingStatus("Modeling the next planned work item.", "PLANNING");
            } else if (eventName === "turn.resumed") {
              updateThinkingStatus(
                event?.payload?.message || "Continuing from the latest committed checkpoint.",
                "PLANNING",
              );
            } else if (eventName === "turn.auto_continued") {
              updateThinkingStatus(
                event?.payload?.message ||
                  "Continuing automatically from the saved workflow state.",
                "PLANNING",
              );
            } else if (eventName === "turn.auto_recovery_queued") {
              const reason = String(event?.payload?.reason || "")
                .trim()
                .slice(0, 240);
              updateThinkingStatus(
                reason
                  ? `A generated slice needs repair: ${reason}`
                  : "A generated slice needs repair; preserving completed work and retrying once.",
                "REPAIRING",
              );
            } else if (eventName === "turn.validation.completed") {
              updateThinkingStatus("Validation completed for the current draft.", "VALIDATING");
            } else if (eventName === "turn.coverage.updated") {
              const unresolved = Number(event?.payload?.unresolvedSpans) || 0;
              updateThinkingStatus(
                unresolved
                  ? "Recorded source grounding for the current model slice."
                  : "Source grounding recorded for the modeled document.",
                "COMPLETING",
              );
            } else if (eventName === "turn.stage") {
              const phase = String(event?.payload?.stage || "").toUpperCase();
              const progress = durableProgressForTurn({ state: "RUNNING", phase });
              updateThinkingStatus(progress.message, progress.stage, progress.progress);
            } else if (eventName === "source.blueprint.saved") {
              const slices = Number(event?.payload?.slices) || 0;
              updateThinkingStatus(
                slices
                  ? `Source map prepared with ${slices} model slice${slices === 1 ? "" : "s"}.`
                  : "Source map prepared; applying the first model slice.",
                "PLANNING",
              );
            } else if (
              eventName === "model.checkpoint" ||
              eventName === "model.checkpoint.committed"
            ) {
              const ordinal = Number(event?.payload?.checkpointOrdinal) || 0;
              const total = Number(event?.payload?.sliceCount) || 0;
              updateThinkingStatus(
                ordinal && total
                  ? `Model checkpoint ${ordinal} of ${total} saved.`
                  : "Model checkpoint saved.",
                "APPLYING",
                ordinal && total ? { index: ordinal, count: total } : null,
              );
              if (event?.payload?.modelId) {
                void applyAssistantModelResponse(typeKey, event.payload).catch(() => {
                  setStatus("Checkpoint saved; model refresh will retry with turn polling.");
                });
              }
            } else if (eventName === "turn.completed") {
              updateThinkingStatus(
                event?.payload?.message || "Assistant turn completed.",
                "COMPLETED",
              );
            }
          } catch {
            // A malformed event never prevents polling the durable status endpoint.
          }
        }
      }
    })
    .catch(() => {
      if (!controller.signal.aborted) setStatus("Turn event stream disconnected; polling status.");
    });
  return controller;
}

async function waitForDurableTurn(turnId, typeKey, eventCursor = 0) {
  let latest = null;
  const events = streamDurableTurnEvents(turnId, typeKey, eventCursor);
  try {
    for (;;) {
      latest = await api(`/chatbot/turns/${turnId}`);
      renderDurableRun(latest, typeKey);
      const stateName = String(latest?.state || "");
      const progress = durableProgressForTurn(latest);
      updateThinkingStatus(progress.message, progress.stage, progress.progress);
      if (DURABLE_TERMINAL_STATES.has(stateName)) {
        if (latest?.modelId) await applyAssistantModelResponse(typeKey, latest);
        return latest;
      }
      await new Promise((resolve) => window.setTimeout(resolve, 1000));
    }
  } finally {
    events.abort();
  }
}

async function durableAssistantMessage(turn, sessionId) {
  const finalMessage = String(turn?.finalMessage || "").trim();
  if (finalMessage) {
    return finalMessage;
  }
  if (sessionId) {
    const thread = await api(`/chatbot/sessions/${sessionId}/thread`);
    const messages = Array.isArray(thread?.messages) ? thread.messages : [];
    const latestAssistantMessage = [...messages]
      .reverse()
      .find((message) => String(message?.role || "").toUpperCase() === "ASSISTANT");
    const recovered = String(latestAssistantMessage?.content || "").trim();
    if (recovered) {
      return recovered;
    }
  }
  return "The assistant completed without returning a response.";
}

function appendDurableTurnActions(turn, typeKey) {
  const stateName = String(turn?.state || "");
  if (
    !turn?.turnId ||
    ![
      "PARTIAL",
      "NEEDS_CONFIRMATION",
      "CONFLICTED",
      "SUCCEEDED",
      "CANCELLED",
      "TIMED_OUT",
      "FAILED",
    ].includes(stateName)
  ) {
    return;
  }
  appendDurableTurnSummary(turn);
  const actions = document.createElement("div");
  actions.className = "chat-proposal-actions";
  const runFollowUp = async (path, body = null) => {
    const accepted = await api(path, {
      method: "POST",
      ...(body ? { body: JSON.stringify(body) } : {}),
    });
    if (accepted?.turnId) {
      activeTurnId = accepted.turnId;
      beginChatActivity("Continuing assistant turn", "PLANNING");
      const completed = await waitForDurableTurn(
        accepted.turnId,
        typeKey,
        accepted.eventCursor || 0,
      );
      activeTurnId = null;
      endChatActivity(null, completed?.state || null);
      appendAssistantDeduped(
        await durableAssistantMessage(
          completed,
          state.chat.sessions.get(chatScopeKey(typeKey))?.sessionId,
        ),
      );
      appendDurableTurnActions(completed, typeKey);
    }
  };
  if (stateName === "PARTIAL") {
    const resumeButton = document.createElement("button");
    resumeButton.type = "button";
    resumeButton.className = "chat-proposal-btn";
    resumeButton.textContent = "Resume";
    resumeButton.addEventListener("click", () =>
      runFollowUp(`/chatbot/turns/${turn.turnId}/continue`).catch((error) => setError(error)),
    );
    actions.appendChild(resumeButton);
  }
  if (stateName === "CONFLICTED") {
    const rebaseButton = document.createElement("button");
    rebaseButton.type = "button";
    rebaseButton.className = "chat-proposal-btn";
    rebaseButton.textContent = "Rebase and resume";
    rebaseButton.addEventListener("click", () =>
      runFollowUp(`/chatbot/turns/${turn.turnId}/rebase`, {
        expectedRevision: Number(state.modelRevision) || 0,
      }).catch((error) => setError(error)),
    );
    actions.appendChild(rebaseButton);
  }
  if (stateName === "NEEDS_CONFIRMATION") {
    const confirmButton = document.createElement("button");
    confirmButton.type = "button";
    confirmButton.className = "chat-proposal-btn";
    confirmButton.textContent = "Confirm deletion";
    confirmButton.addEventListener("click", () =>
      runFollowUp(`/chatbot/turns/${turn.turnId}/confirm`).catch((error) => setError(error)),
    );
    actions.appendChild(confirmButton);
  }
  if (turn?.checkpointCount > 0) {
    const undoButton = document.createElement("button");
    undoButton.type = "button";
    undoButton.className = "chat-proposal-btn";
    undoButton.textContent = "Undo latest";
    undoButton.addEventListener("click", async () => {
      try {
        const undone = await api(`/chatbot/turns/${turn.turnId}/undo`, { method: "POST" });
        if (undone?.modelId)
          await loadModelById(typeKey, undone.modelId, { preserveActiveView: true });
        appendAssistantDeduped("The last saved checkpoint was undone.");
      } catch (error) {
        setError(error);
      }
    });
    actions.appendChild(undoButton);
  }
  if (actions.childElementCount) el.chatMessages.appendChild(actions);
}

function appendDurableTurnSummary(turn) {
  if (
    !turn?.turnId ||
    el.chatMessages.querySelector(`[data-chat-turn-summary="${CSS.escape(turn.turnId)}"]`)
  ) {
    return;
  }
  const saved = Number(turn.savedElementCount) || 0;
  const checkpoints = Number(turn.checkpointCount) || 0;
  const remaining = String(turn.remainingWork || "").trim();
  if (!saved && !checkpoints && !remaining) return;

  const card = document.createElement("div");
  card.className = "chat-msg assistant";
  card.dataset.chatKind = "turn-summary";
  card.dataset.chatTurnSummary = turn.turnId;
  const bubble = document.createElement("div");
  bubble.className = "chat-msg-bubble chat-proposal-card";
  const title = document.createElement("div");
  title.className = "chat-proposal-title";
  title.textContent = checkpoints ? "Saved model progress" : "Modeling progress";
  bubble.appendChild(title);
  const facts = [];
  if (saved) facts.push(`${saved} element${saved === 1 ? "" : "s"} saved`);
  if (checkpoints) facts.push(`${checkpoints} checkpoint${checkpoints === 1 ? "" : "s"}`);
  if (facts.length) {
    const detail = document.createElement("p");
    detail.className = "chat-proposal-intro";
    detail.textContent = facts.join(" · ");
    bubble.appendChild(detail);
  }
  if (remaining) {
    const next = document.createElement("div");
    next.className = "chat-proposal-meta";
    next.textContent = `Next: ${remaining}`;
    bubble.appendChild(next);
  }
  card.appendChild(bubble);
  el.chatMessages.appendChild(card);
  scrollChatToBottom();
}

function renderDurableRun(turn, typeKey) {
  if (!turn?.turnId || !el.chatMessages) return;
  let card = el.chatMessages.querySelector(`[data-chat-durable-run="${CSS.escape(turn.turnId)}"]`);
  if (!card) {
    card = document.createElement("div");
    card.className = "chat-msg assistant";
    card.dataset.chatKind = "durable-run";
    card.dataset.chatDurableRun = turn.turnId;
    const bubble = document.createElement("div");
    bubble.className = "chat-msg-bubble chat-proposal-card chat-durable-run";
    card.appendChild(bubble);
    el.chatMessages.appendChild(card);
  }
  const bubble = card.firstElementChild;
  bubble.replaceChildren();
  const title = document.createElement("div");
  title.className = "chat-proposal-title";
  title.textContent = `${durableWorkflowLabel(turn.workflowKind)} · ${String(turn.state || "QUEUED")
    .replaceAll("_", " ")
    .toLowerCase()}`;
  bubble.appendChild(title);
  const details = document.createElement("p");
  details.className = "chat-proposal-intro";
  const activeItem = (Array.isArray(turn.workItems) ? turn.workItems : []).find(
    (item) => String(item.id || "") === String(turn.currentWorkItemId || ""),
  );
  details.textContent = [
    turn.phase && `Phase: ${String(turn.phase).replaceAll("_", " ").toLowerCase()}`,
    activeItem?.label && `Current work: ${activeItem.label}`,
  ]
    .filter(Boolean)
    .join(" · ");
  if (details.textContent) bubble.appendChild(details);
  const providerCalls = Number(turn.providerCalls) || 0;
  const promptTokens = Number(turn.promptTokens) || 0;
  const completionTokens = Number(turn.completionTokens) || 0;
  if (providerCalls || promptTokens || completionTokens) {
    const usage = document.createElement("div");
    usage.className = "chat-proposal-meta";
    usage.textContent = `Provider usage: ${providerCalls} call${providerCalls === 1 ? "" : "s"} · ${(
      promptTokens + completionTokens
    ).toLocaleString()} tokens (${promptTokens.toLocaleString()} prompt + ${completionTokens.toLocaleString()} completion).`;
    bubble.appendChild(usage);
  }
  const workItems = Array.isArray(turn.workItems) ? turn.workItems : [];
  if (workItems.length) {
    const plan = document.createElement("ol");
    plan.className = "chat-durable-plan";
    for (const item of workItems) {
      const entry = document.createElement("li");
      entry.className = `is-${String(item.status || "queued").toLowerCase()}`;
      entry.textContent = `${item.label || `Work item ${item.ordinal}`}: ${String(item.status || "QUEUED").replaceAll("_", " ")}`;
      plan.appendChild(entry);
    }
    bubble.appendChild(plan);
  }
  const checkpoints = Array.isArray(turn.checkpoints) ? turn.checkpoints : [];
  if (checkpoints.length) {
    const checkpointList = document.createElement("div");
    checkpointList.className = "chat-durable-checkpoints";
    for (const checkpoint of checkpoints) {
      const row = document.createElement("div");
      row.className = "chat-durable-checkpoint";
      const label = document.createElement("span");
      label.textContent = `Checkpoint ${checkpoint.ordinal}: ${checkpoint.label || "Saved"}`;
      const rollback = document.createElement("button");
      rollback.type = "button";
      rollback.className = "chat-proposal-btn";
      rollback.textContent = "Roll back";
      rollback.addEventListener("click", async () => {
        try {
          rollback.disabled = true;
          const result = await api(
            `/chatbot/turns/${turn.turnId}/checkpoints/${checkpoint.checkpointId}/rollback`,
            { method: "POST" },
          );
          if (result?.modelId)
            await loadModelById(typeKey, result.modelId, { preserveActiveView: true });
          appendAssistantDeduped(`Rolled back to checkpoint ${checkpoint.ordinal}.`);
        } catch (error) {
          rollback.disabled = false;
          setError(error);
        }
      });
      row.append(label, rollback);
      checkpointList.appendChild(row);
    }
    bubble.appendChild(checkpointList);
  }
  if (turn.remainingWork) {
    const remaining = document.createElement("div");
    remaining.className = "chat-proposal-meta";
    remaining.textContent = `Remaining: ${turn.remainingWork}`;
    bubble.appendChild(remaining);
  }
  const provenance = Array.isArray(turn.provenance) ? turn.provenance : [];
  const assumptions = [
    ...new Set(provenance.map((item) => String(item.assumption || "").trim()).filter(Boolean)),
  ];
  if (assumptions.length) {
    const assumptionList = document.createElement("div");
    assumptionList.className = "chat-proposal-meta";
    assumptionList.textContent = `Assumptions: ${assumptions.join(" · ")}`;
    bubble.appendChild(assumptionList);
  }
  if (provenance.length) {
    const evidence = document.createElement("div");
    evidence.className = "chat-proposal-meta";
    evidence.textContent = `Evidence linked to ${provenance.length} model element${provenance.length === 1 ? "" : "s"}.`;
    bubble.appendChild(evidence);
    const evidenceList = document.createElement("div");
    evidenceList.className = "chat-proposal-actions";
    for (const item of provenance) {
      const elementId = String(item.elementId || "").trim();
      if (!elementId) continue;
      const element = document.createElement("button");
      element.type = "button";
      element.className = "chat-proposal-btn";
      element.textContent = item.requirementId || item.sourceUnitId || "View evidence";
      element.title = `Focus model element ${elementId}`;
      element.addEventListener("click", () => scrollToNodeAndHighlight(elementId));
      evidenceList.appendChild(element);
    }
    if (evidenceList.childElementCount) bubble.appendChild(evidenceList);
  }
  if (turn.state === "SUCCEEDED" || turn.state === "PARTIAL") {
    const feedback = document.createElement("div");
    feedback.className = "chat-proposal-actions";
    for (const [accepted, label] of [
      [true, "Accept"],
      [false, "Needs changes"],
    ]) {
      const button = document.createElement("button");
      button.type = "button";
      button.className = "chat-proposal-btn";
      button.textContent = label;
      button.addEventListener("click", async () => {
        try {
          button.disabled = true;
          await api(`/chatbot/turns/${turn.turnId}/feedback`, {
            method: "POST",
            body: JSON.stringify({ accepted }),
          });
          appendAssistantDeduped(
            accepted ? "Model changes accepted." : "Marked for revision. Tell me what to change.",
          );
        } catch (error) {
          button.disabled = false;
          setError(error);
        }
      });
      feedback.appendChild(button);
    }
    bubble.appendChild(feedback);
  }
  scrollChatToBottom();
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

function beginChatActivity(message, workflowState = null, anchorMessage = null) {
  chatBusyDepth += 1;
  ensureThinkingStream(
    message,
    workflowState ? idleStageForWorkflow(workflowState) : "PLANNING",
    anchorMessage,
  );
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

function updateChatModelBadge(model) {
  if (!el.chatModelBadge || !el.chatModelLabel) {
    return;
  }
  const key = typeof model === "string" ? model.trim() : "";
  el.chatModelLabel.textContent = key;
  el.chatModelBadge.title = key ? `Active language model: ${key}` : "Active language model";
  el.chatModelBadge.setAttribute(
    "aria-label",
    key ? `Active language model: ${key}` : "Active language model",
  );
  el.chatModelBadge.classList.toggle("hidden", !key);
}

async function _ensureChatRealtime(scopeKey, typeKey, sessionId) {
  const channel = state.chat.channels.get(scopeKey);
  if (channel?.kind === "fetch-sse" && !channel.handle?.signal?.aborted) {
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
  if (!assistantAvailableFor()) {
    setStatus(modelingAssistantConfig().unavailableMessage);
    return null;
  }
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
  const generation = chatGeneration;
  const projectId = state.project.id;
  const cached = state.chat.sessions.get(scopeKey);
  if (
    !options.forceNew &&
    !options.resumeSessionId &&
    cached?.sessionId &&
    cached.projectId === state.project.id
  ) {
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
    response = await createChatSession(typeKey, { ...options, projectId });
  } catch (error) {
    if (isPlannedFeatureError(error)) {
      state.chat.available = false;
      setStatus("Chat is not available in this backend build.");
      return null;
    }
    throw error;
  }

  if (generation !== chatGeneration || state.project?.id !== projectId) {
    return null;
  }

  const session = {
    sessionId: response.sessionId,
    modelId: response.modelId || null,
    modelName: response.modelName || null,
    projectId: state.project.id,
  };
  state.chat.sessions.set(scopeKey, session);
  updateChatModelBadge(session.modelName);
  if (hydrate) {
    await hydrateChatThread(typeKey, session.sessionId, scopeKey, generation);
  }

  return session;
}

export async function prepareChatWindow() {
  if (!assistantAvailableFor()) {
    closeChatWindow();
    setStatus(modelingAssistantConfig().unavailableMessage);
    return null;
  }
  const scopeKey = chatScopeKey();
  const generation = chatGeneration;
  const isScopeChange = renderedChatScopeKey !== scopeKey;
  if (isScopeChange) {
    // An empty thread must render as empty for its own level, never as the last level's thread.
    renderedChatScopeKey = scopeKey;
    resetChatMessagesUi();
    resetChatActivityUi();
  }
  stripLegacyThinkingCancelButtons();
  updateChatComposerActionButton();
  closeChatHistoryPanel();
  updateChatHeaderSubtitle();
  const session = await ensureChatSession({ hydrate: isScopeChange });
  if (session && isCurrentChatScope(scopeKey, generation)) {
    renderedChatScopeKey = scopeKey;
  }
  return session;
}

function updateChatHeaderSubtitle() {
  if (el.chatLevelLabel) {
    el.chatLevelLabel.textContent = levelLabel(state.activeType);
  }
  const session = state.chat.sessions.get(chatScopeKey());
  updateChatModelBadge(session?.modelName);
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
  state.chat.attachments = [];
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
  state.chat.attachments = [];
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

async function hydrateChatThread(
  typeKey,
  sessionId,
  expectedScopeKey = chatScopeKey(typeKey),
  generation = chatGeneration,
) {
  try {
    const thread = await api(`/chatbot/sessions/${sessionId}/thread`);
    if (!isCurrentChatScope(expectedScopeKey, generation)) {
      return;
    }
    // Provider metadata is available even for a brand-new, empty thread. Keep the model badge
    // independent from message hydration so the composer can identify the active model before the
    // first user message is sent.
    updateChatProviderLabel(thread.provider);
    const hydratedModelName =
      typeof thread.provider?.model === "string" ? thread.provider.model.trim() : "";
    const cachedSession = state.chat.sessions.get(expectedScopeKey);
    if (cachedSession && hydratedModelName) {
      cachedSession.modelName = hydratedModelName;
    }
    updateChatModelBadge(hydratedModelName || cachedSession?.modelName);
    const hasMessages = Array.isArray(thread.messages) && thread.messages.length > 0;
    if (!hasMessages) {
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
    const activeTurn = thread?.activeTurn;
    if (activeTurn?.turnId) {
      // A page reload must reconnect to the durable child turn, not merely replay the parent
      // message. Without this, the UI showed generic realtime progress but never polled the
      // checkpoint or reloaded canvas changes.
      activeTurnId = activeTurn.turnId;
      beginChatActivity("Restoring the in-progress assistant turn.", "PLANNING");
      void waitForDurableTurn(activeTurn.turnId, typeKey, activeTurn.eventCursor || 0)
        .then(async (completed) => {
          if (!isCurrentChatScope(expectedScopeKey, generation)) return;
          if (activeTurnId !== activeTurn.turnId && activeTurnId !== null) return;
          activeTurnId = null;
          endChatActivity(null, completed?.state || null);
          appendAssistantDeduped(await durableAssistantMessage(completed, sessionId));
          appendDurableTurnActions(completed, typeKey);
        })
        .catch((error) => {
          if (!isCurrentChatScope(expectedScopeKey, generation)) return;
          activeTurnId = null;
          endChatActivity("Could not restore the assistant turn.", "FAILED");
          setError(error, { prefix: "Could not restore assistant progress." });
        });
    } else if (thread.workflowState) {
      applyWorkflowSnapshot(thread.workflowState, workflowLabel(thread.workflowState));
    } else resetChatActivityUi();
  } catch {
    if (isCurrentChatScope(expectedScopeKey, generation)) {
      resetChatActivityUi();
    }
  }
}
export async function clearChatConversation() {
  return startNewChatConversation();
}

async function connectChatRealtime(scopeKey, typeKey, sessionId) {
  const generation = chatGeneration;
  const controller = new AbortController();
  state.chat.channels.set(scopeKey, { kind: "fetch-sse", handle: controller });
  void fetch(apiUrl(`/chatbot/sessions/${sessionId}/events`), {
    headers: apiAuthHeaders({ Accept: "text/event-stream" }),
    signal: controller.signal,
  })
    .then(async (response) => {
      if (!response.ok || !response.body) throw new Error("SSE connection failed");
      const reader = response.body.getReader();
      const decoder = new TextDecoder();
      let buffer = "";
      while (true) {
        const { value, done } = await reader.read();
        if (done) break;
        buffer += decoder.decode(value, { stream: true });
        const frames = buffer.split("\n\n");
        buffer = frames.pop() || "";
        for (const frame of frames) {
          const eventName = frame.match(/^event:\s*(.+)$/m)?.[1];
          const raw = frame.match(/^data:\s*(.+)$/m)?.[1];
          if (!eventName || !raw) continue;
          try {
            const data = JSON.parse(raw);
            handleChatRealtimeEvent(scopeKey, generation, typeKey, eventName, data?.payload);
          } catch (_error) {
            continue;
          }
        }
      }
    })
    .catch(() => {
      if (!controller.signal.aborted)
        setStatus("Chat realtime stream disconnected; polling turn status.");
    });
}

function isCurrentChatScope(scopeKey, generation = chatGeneration) {
  return (
    generation === chatGeneration &&
    renderedChatScopeKey === scopeKey &&
    chatScopeKey() === scopeKey
  );
}

function handleChatRealtimeEvent(scopeKey, generation, typeKey, eventType, payload) {
  // Realtime channels remain connected when the window is closed or the user changes tabs.
  // Only the active rendered scope may update this shared UI surface.
  if (!isCurrentChatScope(scopeKey, generation)) {
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
    const index = Number(payload?.index) || 0;
    const count = Number(payload?.count) || 0;
    updateThinkingStatus(
      eventType.endsWith("completed")
        ? `Finished reviewing source document${index ? ` ${index}` : ""}${
            count ? ` of ${count}` : ""
          }.`
        : `Reviewing source document${index ? ` ${index}` : ""}${
            count ? ` of ${count}` : ""
          } to capture the requested details.`,
      "ANALYZING_SOURCE",
    );
    return;
  }
  if (eventType === "assistant.trace.started") {
    const progress = assistantProgressForEvent(eventType, payload);
    updateThinkingStatus(progress.message, progress.stage);
    return;
  }
  if (eventType === "assistant.trace.step") {
    const progress = assistantProgressForEvent(eventType, payload);
    updateThinkingStatus(progress.message, progress.stage);
    return;
  }
  if (eventType === "assistant.progress") {
    const progress = assistantProgressForEvent(eventType, payload);
    updateThinkingStatus(progress.message, progress.stage);
    return;
  }
  if (
    eventType === "tool.started" ||
    eventType === "assistant.tool.started" ||
    eventType === "tool.completed" ||
    eventType === "assistant.tool.completed"
  ) {
    const progress = assistantProgressForEvent(eventType, payload);
    updateThinkingStatus(progress.message, progress.stage);
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
    return;
  }
  if (eventType === "model.checkpoint") {
    if (String(payload?.modelId || "").trim()) {
      void applyAssistantModelResponse(typeKey, payload).catch(() => {
        setStatus("Checkpoint saved; model refresh will retry with turn polling.");
      });
    }
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
  applyTextDirection(bubble, text);
  if (role === "assistant") {
    bubble.appendChild(renderMarkdown(text));
  } else {
    bubble.textContent = text;
  }

  msg.appendChild(bubble);
  el.chatMessages.appendChild(msg);
  scrollChatToBottom();
  return msg;
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
    applyTextDirection(bubble, "");
    streamingAssistantEl.appendChild(bubble);
    el.chatMessages.appendChild(streamingAssistantEl);
    streamingAssistantText = "";
  }
  streamingAssistantText += delta;
  streamingAssistantEl.dataset.chatText = streamingAssistantText;
  const bubble = streamingAssistantEl.querySelector(".chat-msg-bubble");
  applyTextDirection(bubble, streamingAssistantText);
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

function _appendProposalCard(typeKey, sessionId, proposal) {
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
    : "Validation failed, changes were not applied";
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
    const responseRevision = Number(response.revision);
    const currentRevision = Number(state.modelRevision);
    const isCurrentModel = responseModelId === liveModelId;
    // A durable turn always reports its working model ID, even for an explanation. Reloading
    // that unchanged revision recreates the canvas and may invoke layout recovery. Only a newer
    // revision (or a newly selected model) is a model change that belongs on the canvas.
    if (
      isCurrentModel &&
      Number.isFinite(responseRevision) &&
      responseRevision <= currentRevision
    ) {
      clearAssistantModelPreview({ restore: false });
      return;
    }
    const updateKey = `${typeKey}:${responseModelId}:${Number.isFinite(responseRevision) ? responseRevision : "latest"}`;
    const existing = assistantModelApplyInFlight.get(updateKey);
    if (existing) {
      await existing;
      return;
    }
    const apply = loadModelById(typeKey, responseModelId, {
      preserveActiveView: true,
      // A checkpoint must appear in the user's current viewport. Preserve the
      // viewport, but repair new or overlapping node positions before rendering;
      // the backend ELK pass below will persist the final layout.
      preserveViewport: true,
      autoLayout: false,
      skipClientLayout: false,
    });
    assistantModelApplyInFlight.set(updateKey, apply);
    try {
      await apply;
    } finally {
      assistantModelApplyInFlight.delete(updateKey);
    }
    await autoLayoutCompletedAssistantTurn(response);
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

async function autoLayoutCompletedAssistantTurn(response) {
  const turnState = String(response?.state || "").toUpperCase();
  if (!DURABLE_TERMINAL_STATES.has(turnState) || turnState === "PARTIAL") {
    return;
  }
  // A partial durable run remains resumable from its last checkpoint. Persist layout only once
  // the run is terminal, so a user-initiated resume cannot conflict with a layout revision.
  await autoLayoutCurrentDiagram({
    progress: false,
    status: false,
    busy: false,
    rethrow: true,
    force: true,
    skipClientLayout: true,
  });
}

export function updateChatAttachmentLabel() {
  const attachments = Array.isArray(state.chat.attachments) ? state.chat.attachments : [];
  if (!el.chatAttachBar) return;
  el.chatAttachBar.replaceChildren();
  for (const attachment of attachments) {
    const tag = document.createElement("div");
    tag.className = "chat-file-tag";
    const name = document.createElement("span");
    name.className = "chat-file-name";
    name.textContent = attachment.name;
    const clear = document.createElement("button");
    clear.className = "chat-file-clear";
    clear.type = "button";
    clear.title = `Remove ${attachment.name}`;
    clear.setAttribute("aria-label", clear.title);
    clear.textContent = "×";
    clear.addEventListener("click", () => removeChatAttachment(attachment.id));
    tag.append(name, clear);
    el.chatAttachBar.appendChild(tag);
  }
  el.chatAttachBar.classList.toggle("hidden", attachments.length === 0);
}

export function removeChatAttachment(attachmentId) {
  state.chat.attachments = (state.chat.attachments || []).filter(
    (attachment) => attachment.id !== attachmentId,
  );
  updateChatAttachmentLabel();
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
  const attachments = Array.isArray(state.chat.attachments) ? state.chat.attachments : [];
  if (!typedText && !attachments.length) {
    return;
  }
  const text = typedText || defaultAttachmentMessage();
  const sendScopeKey = chatScopeKey();
  const sendGeneration = chatGeneration;

  let response = null;
  try {
    const userMessage = appendChat("user", text);
    el.chatInput.value = "";
    applyTextDirection(el.chatInput, "");
    beginChatActivity("Understanding your request", null, userMessage);
    // Hydration is for opening/restoring a conversation. Doing it while sending clears the
    // just-created activity UI (and could also reset its busy state) before the turn is accepted.
    const session = await ensureChatSession({ hydrate: false });
    if (!session) {
      endChatActivity("Could not start chat session.", "FAILED");
      return;
    }
    const requestedModelId = state.modelId;
    const requestDiagramFingerprint = JSON.stringify(state.diagram || {});
    // A durable agent turn always works from a persisted revision. Sending an ad-hoc draft patch
    // would bypass structural validation and make checkpoint/conflict semantics ambiguous.
    if (hasUnsavedModelChanges()) {
      updateThinkingStatus(
        "Saving your current model before starting the assistant.",
        "VALIDATING",
      );
      await saveCurrentModel({ quiet: true, rethrow: true });
    }
    response = await api(`/chatbot/sessions/${session.sessionId}/messages`, {
      method: "POST",
      body: JSON.stringify({
        idempotencyKey:
          crypto?.randomUUID?.() || `${Date.now()}-${Math.random().toString(36).slice(2)}`,
        message: text,
        modelId: state.modelId,
        expectedRevision: state.modelRevision || null,
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
        attachmentIds: attachments.map((attachment) => attachment.id),
      }),
    });

    if (response?.turnId) {
      activeTurnId = response.turnId;
      updateThinkingStatus(
        "Turn accepted. The assistant is working in the background.",
        "PLANNING",
      );
      response = await waitForDurableTurn(
        response.turnId,
        state.activeType,
        response.eventCursor || 0,
      );
      if (!isCurrentChatScope(sendScopeKey, sendGeneration)) {
        return;
      }
      activeTurnId = null;
      endChatActivity(null, response?.state || null);
      appendAssistantDeduped(await durableAssistantMessage(response, session.sessionId));
      appendDurableTurnActions(response, state.activeType);
    } else {
      if (!isCurrentChatScope(sendScopeKey, sendGeneration)) {
        return;
      }
      applyHttpActivity(response);
      endChatActivity(null, response?.workflowState || null);
      appendAssistantDeduped(response.assistantMessage || "Done");
      await applyAssistantModelResponse(
        state.activeType,
        response,
        requestedModelId,
        requestDiagramFingerprint,
      );
    }
    if (
      !["WAITING_FOR_CHOICE", "FAILED", "PARTIAL", "CANCELLED"].includes(
        response?.workflowState || response?.state,
      )
    ) {
      state.chat.attachments = [];
      if (el.chatFileInput) {
        el.chatFileInput.value = "";
      }
      updateChatAttachmentLabel();
    }
    setStatus("Assistant response received");
  } catch (error) {
    if (!isCurrentChatScope(sendScopeKey, sendGeneration)) {
      return;
    }
    if (chatBusyDepth > 0) {
      endChatActivity("Could not complete the request.", "FAILED");
    }
    appendChat("assistant", formatUserError(error));
    setError(error, { prefix: "Chat failed." });
  }
}
