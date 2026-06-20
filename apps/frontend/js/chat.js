import { state } from "./state.js";
import { el } from "./dom.js";
import { api, isPlannedFeatureError } from "./api.js";
import { setError, setStatus } from "./status.js";
import { apiUrl, MODEL_TYPES, websocketUrl } from "./config.js";
import { toDiagram } from "./diagram.js";
import { renderDiagram } from "./canvas.js";
import { renderMarkdown } from "./markdown.js";
import { loadModelById } from "./model-ops.js";

const CHAT_STAGE_PROGRESS = Object.freeze({
  READING_MODEL: 12,
  PLANNING: 35,
  VALIDATING: 62,
  REPAIRING: 78,
  APPLYING: 88,
  COMPLETED: 100,
});

let chatBusyDepth = 0;

function setChatActivity(message, { busy = false, stage = null } = {}) {
  const label = message || (busy ? "Working on your model" : "Ready");
  if (el.chatActivityMessage) el.chatActivityMessage.textContent = label;
  if (el.chatHeaderSubtitle) el.chatHeaderSubtitle.textContent = label;
  el.chatActivity?.classList.toggle("is-busy", busy);
  el.chatTypingIndicator?.classList.toggle("hidden", !busy);
  if (el.chatProgressBar) {
    const progress = stage ? CHAT_STAGE_PROGRESS[stage] : null;
    el.chatProgressBar.classList.toggle("is-indeterminate", busy && progress == null);
    el.chatProgressBar.style.width = progress == null ? "" : `${progress}%`;
  }
}

function beginChatActivity(message) {
  chatBusyDepth += 1;
  setChatActivity(message, { busy: true });
}

function endChatActivity(message = "Ready to help") {
  chatBusyDepth = Math.max(0, chatBusyDepth - 1);
  if (chatBusyDepth === 0) setChatActivity(message);
}

// ── Chat session / realtime ───────────────────────────────────────────────────

export async function ensureChatSession() {
  if (state.chat.available === false) {
    setStatus("Chat is not available in this backend build.");
    return null;
  }
  if (!state.project?.id) {
    setStatus("Open a project before starting chat.");
    return null;
  }
  const typeKey = state.activeType;
  if (state.chat.sessions.has(typeKey)) {
    return state.chat.sessions.get(typeKey);
  }

  let response;
  try {
    response = await api("/chatbot/sessions", {
      method: "POST",
      body: JSON.stringify({
        modelType: MODEL_TYPES[typeKey].chatType,
        modelName: (state.tabs[typeKey]?.modelName || `${typeKey}-assistant`).trim(),
        initialDocument: "Initialized from web modeling editor",
        projectId: state.project.id,
      }),
    });
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
  };
  state.chat.sessions.set(typeKey, session);
  await connectChatRealtime(typeKey, session.sessionId);

  return session;
}

export async function clearChatConversation() {
  const typeKey = state.activeType;
  const session = state.chat.sessions.get(typeKey);
  if (session?.sessionId) {
    try {
      await api(`/chatbot/sessions/${session.sessionId}`, {
        method: "DELETE",
      });
    } catch (error) {
      if (!error?.featureUnavailable) {
        throw error;
      }
    }
  }

  const channel = state.chat.channels.get(typeKey);
  if (channel?.handle) {
    try {
      channel.handle.close?.();
    } catch {
      // ignore cleanup errors
    }
  }
  state.chat.channels.delete(typeKey);
  state.chat.sessions.delete(typeKey);
  state.chat.attachment = null;
  if (el.chatFileInput) {
    el.chatFileInput.value = "";
  }
  updateChatAttachmentLabel();
  el.chatMessages.innerHTML = "";
  el.chatMessages.appendChild(buildChatWelcomeCard());
  setStatus("Chat cleared");
}

async function connectChatRealtime(typeKey, sessionId) {
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
      if (state.chat.channels.get(typeKey)?.kind === "websocket") {
        setStatus("Chat websocket disconnected");
      }
    };
    state.chat.channels.set(typeKey, { kind: "websocket", handle: socket });
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
  stream.addEventListener("proposal.rejected", (event) => {
    const payload = JSON.parse(event.data)?.payload;
    handleChatRealtimeEvent(typeKey, "proposal.rejected", payload);
  });
  stream.addEventListener("assistant.choice", (event) => {
    const payload = JSON.parse(event.data)?.payload;
    handleChatRealtimeEvent(typeKey, "assistant.choice", payload);
  });
  stream.addEventListener("assistant.progress", (event) => {
    const payload = JSON.parse(event.data)?.payload;
    handleChatRealtimeEvent(typeKey, "assistant.progress", payload);
  });
  stream.onerror = () => {
    setStatus("Chat realtime stream disconnected");
  };
  state.chat.channels.set(typeKey, { kind: "sse", handle: stream });
}

function handleChatRealtimeEvent(typeKey, eventType, payload) {
  if (eventType === "assistant.progress") {
    setChatActivity(payload?.message || "Working with the model", {
      busy: true,
      stage: payload?.stage,
    });
    return;
  }
  if (eventType === "chat.assistant") {
    const message = payload?.assistantMessage || payload?.message;
    appendAssistantDeduped(message);
    const sessionId = state.chat.sessions.get(typeKey)?.sessionId;
    if (sessionId) {
      appendProposalCard(typeKey, sessionId, payload?.proposal);
      if (!payload?.proposal) {
        appendChoiceButtons(typeKey, sessionId, payload?.choices);
      }
    }
    return;
  }

  if (eventType === "proposal.rejected") {
    appendAssistantDeduped("Proposal rejected.");
    return;
  }

  if (eventType === "assistant.choice") {
    appendAssistantDeduped("Choice recorded.");
    return;
  }

  // Ignore realtime model snapshots in the editing client and rely on the
  // direct chat HTTP response to avoid clobbering newer local edits.
}

// ── Chat UI ───────────────────────────────────────────────────────────────────

export function buildChatWelcomeCard() {
  const welcome = document.createElement("div");
  welcome.className = "chat-welcome";
  welcome.innerHTML = `<span aria-hidden="true" class="chat-welcome-icon icon-svg icon-mask" style="--icon-src: url('/assets/icons/placeholder.svg');"></span>
    <div class="chat-welcome-title">Modeling Assistant</div>
    <div class="chat-welcome-text">I can help you create, modify, and understand your models. Ask me anything or describe what you'd like to build.</div>`;
  return welcome;
}

function appendChat(role, text) {
  const welcome = el.chatMessages.querySelector(".chat-welcome");
  if (welcome) {
    welcome.remove();
  }

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
  el.chatMessages.scrollTop = el.chatMessages.scrollHeight;
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
  const card = document.createElement("div");
  card.className = "chat-msg assistant";
  card.dataset.chatKind = "proposal";
  if (proposal.id) {
    card.dataset.chatProposalId = proposal.id;
  }
  const bubble = document.createElement("div");
  bubble.className = "chat-msg-bubble";

  const title = document.createElement("div");
  title.className = "chat-proposal-title";
  title.textContent = `Proposal ${
    proposal.riskLevel || "HIGH"
  }${proposal.approvalRequired ? " requires approval" : ""}`;
  bubble.appendChild(title);

  const summary = document.createElement("div");
  summary.className = "chat-proposal-summary";
  summary.textContent = `Affected: ${(proposal.affectedElements || []).join(", ") || "n/a"}`;
  bubble.appendChild(summary);

  const validation = document.createElement("div");
  validation.className = "chat-proposal-validation";
  const issues = Array.isArray(proposal.validation?.issues) ? proposal.validation.issues : [];
  validation.textContent = `Validation: ${
    proposal.validation?.mandatoryPassed
      ? "mandatory constraints pass"
      : "mandatory constraints fail"
  }; ${issues.length} issue(s)`;
  bubble.appendChild(validation);

  const operations = Array.isArray(proposal.patch?.operations) ? proposal.patch.operations : [];
  if (operations.length) {
    const details = document.createElement("details");
    details.className = "chat-proposal-preview";
    const detailsTitle = document.createElement("summary");
    detailsTitle.textContent = `${operations.length} semantic operation(s)`;
    details.appendChild(detailsTitle);
    const list = document.createElement("ul");
    for (const operation of operations) {
      const item = document.createElement("li");
      item.textContent = [
        operation.type,
        operation.targetElementId,
        operation.sourceElementId && `from ${operation.sourceElementId}`,
        operation.referenceName && `via ${operation.referenceName}`,
      ]
        .filter(Boolean)
        .join(" ");
      list.appendChild(item);
    }
    details.appendChild(list);
    bubble.appendChild(details);
  }

  if (Array.isArray(proposal.citations) && proposal.citations.length) {
    const citations = document.createElement("div");
    citations.className = "chat-proposal-citations";
    citations.textContent = `Citations: ${proposal.citations.join(", ")}`;
    bubble.appendChild(citations);
  }

  const actions = document.createElement("div");
  actions.className = "chat-proposal-actions";
  if (proposal.approvalRequired) {
    if (proposal.validation?.mandatoryPassed !== false) {
      const approve = document.createElement("button");
      approve.type = "button";
      approve.textContent = "Approve";
      approve.addEventListener("click", async () => {
        try {
          beginChatActivity("Revalidating and applying the proposal");
          setProposalActionsDisabled(actions, true);
          const response = await api(
            `/chatbot/sessions/${sessionId}/proposals/${proposal.id}/approve`,
            {
              method: "POST",
            },
          );
          appendAssistantDeduped(response.assistantMessage || "Proposal approved");
          await applyAssistantModelResponse(typeKey, response);
          setProposalDecision(card, "Applied");
        } catch (error) {
          setProposalActionsDisabled(actions, false);
          appendChat("assistant", `Error: ${error.message}`);
        } finally {
          endChatActivity();
        }
      });
      actions.appendChild(approve);
    } else {
      const blocked = document.createElement("span");
      blocked.className = "chat-proposal-decision";
      blocked.textContent = "Apply blocked by mandatory validation";
      actions.appendChild(blocked);
    }

    const reject = document.createElement("button");
    reject.type = "button";
    reject.textContent = "Reject";
    reject.addEventListener("click", async () => {
      try {
        beginChatActivity("Recording your decision");
        setProposalActionsDisabled(actions, true);
        await api(`/chatbot/sessions/${sessionId}/proposals/${proposal.id}/reject`, {
          method: "POST",
        });
        setProposalDecision(card, "Rejected");
        appendChat("assistant", "Proposal rejected.");
      } catch (error) {
        setProposalActionsDisabled(actions, false);
        appendChat("assistant", `Error: ${error.message}`);
      } finally {
        endChatActivity();
      }
    });
    actions.appendChild(reject);
  } else if (proposal.id) {
    const undo = document.createElement("button");
    undo.type = "button";
    undo.textContent = "Undo";
    undo.addEventListener("click", async () => {
      try {
        beginChatActivity("Revalidating and undoing the change");
        setProposalActionsDisabled(actions, true);
        const response = await api(`/chatbot/sessions/${sessionId}/proposals/${proposal.id}/undo`, {
          method: "POST",
        });
        appendAssistantDeduped(response.assistantMessage || "Proposal undone");
        await applyAssistantModelResponse(typeKey, response);
        setProposalDecision(card, "Undone");
      } catch (error) {
        setProposalActionsDisabled(actions, false);
        appendChat("assistant", `Error: ${error.message}`);
      } finally {
        endChatActivity();
      }
    });
    actions.appendChild(undo);
  }
  bubble.appendChild(actions);
  card.appendChild(bubble);
  el.chatMessages.appendChild(card);
  el.chatMessages.scrollTop = el.chatMessages.scrollHeight;
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

function appendChoiceButtons(typeKey, sessionId, choices) {
  if (!Array.isArray(choices) || !choices.length) {
    return;
  }
  const interactionId = choices
    .map((choice) => choice.id)
    .filter(Boolean)
    .join("--");
  if (
    interactionId &&
    el.chatMessages.querySelector(`[data-chat-choice-id="${CSS.escape(interactionId)}"]`)
  ) {
    return;
  }
  const card = document.createElement("div");
  card.className = "chat-msg assistant";
  card.dataset.chatKind = "choice";
  if (interactionId) {
    card.dataset.chatChoiceId = interactionId;
  }
  const bubble = document.createElement("div");
  bubble.className = "chat-msg-bubble";
  const heading = document.createElement("div");
  heading.className = "chat-question-heading";
  heading.textContent =
    choices.length === 1 ? "One decision needed" : `${choices.length} decisions needed`;
  bubble.appendChild(heading);

  const form = document.createElement("form");
  form.className = "chat-question-form";
  const fields = [];
  for (const [questionIndex, choice] of choices.entries()) {
    const fieldset = document.createElement("fieldset");
    fieldset.className = "chat-question";
    const legend = document.createElement("legend");
    legend.textContent = choice.prompt || "Choose an option.";
    fieldset.appendChild(legend);
    const inputType = choice.selectionMode === "MULTIPLE" ? "checkbox" : "radio";
    const name = `assistant-question-${interactionId}-${questionIndex}`;
    for (const option of choice.options || []) {
      const label = document.createElement("label");
      label.className = "chat-question-option";
      const input = document.createElement("input");
      input.type = inputType;
      input.name = name;
      input.value = option.id;
      const copy = document.createElement("span");
      const title = document.createElement("strong");
      title.textContent = option.label || option.id;
      copy.appendChild(title);
      if (option.description) {
        const description = document.createElement("small");
        description.textContent = option.description;
        copy.appendChild(description);
      }
      label.append(input, copy);
      fieldset.appendChild(label);
    }
    let freeText = null;
    if (choice.allowFreeText) {
      freeText = document.createElement("textarea");
      freeText.className = "chat-question-free-text";
      freeText.rows = 2;
      freeText.placeholder = "Add another answer or useful detail";
      fieldset.appendChild(freeText);
    }
    fields.push({ choice, fieldset, name, freeText });
    form.appendChild(fieldset);
  }

  const actions = document.createElement("div");
  actions.className = "chat-proposal-actions";
  const submit = document.createElement("button");
  submit.type = "submit";
  submit.textContent = "Continue";
  actions.appendChild(submit);
  form.appendChild(actions);
  form.addEventListener("submit", async (event) => {
    event.preventDefault();
    const answers = fields.map(({ choice, fieldset, name, freeText }) => ({
      choiceId: choice.id,
      optionIds: [...fieldset.querySelectorAll(`input[name="${CSS.escape(name)}"]:checked`)].map(
        (input) => input.value,
      ),
      freeText: freeText?.value.trim() || "",
    }));
    if (answers.some((answer) => !answer.optionIds.length && !answer.freeText)) {
      appendChat("assistant", "Please answer each question before continuing.");
      return;
    }
    try {
      beginChatActivity("Using your answers to continue the model change");
      setProposalActionsDisabled(actions, true);
      for (const input of form.querySelectorAll("input, textarea")) {
        input.disabled = true;
      }
      const response = await api(`/chatbot/sessions/${sessionId}/choices`, {
        method: "POST",
        body: JSON.stringify({ answers }),
      });
      setProposalDecision(card, "Answered");
      appendAssistantDeduped(response.assistantMessage || "Clarification received");
      appendProposalCard(typeKey, sessionId, response.proposal);
      if (!response.proposal) {
        appendChoiceButtons(typeKey, sessionId, response.choices);
      }
      await applyAssistantModelResponse(typeKey, response);
    } catch (error) {
      setProposalActionsDisabled(actions, false);
      for (const input of form.querySelectorAll("input, textarea")) {
        input.disabled = false;
      }
      appendChat("assistant", `Error: ${error.message}`);
    } finally {
      endChatActivity();
    }
  });
  bubble.appendChild(form);
  card.appendChild(bubble);
  el.chatMessages.appendChild(card);
  el.chatMessages.scrollTop = el.chatMessages.scrollHeight;
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
}

export function updateChatAttachmentLabel() {
  const attachment = state.chat.attachment;
  if (attachment) {
    if (el.chatFileNameText) {
      el.chatFileNameText.textContent = attachment.name;
    }
    el.chatFileName.classList.remove("hidden");
  } else {
    el.chatFileName.classList.add("hidden");
  }
}

// ── Send message ──────────────────────────────────────────────────────────────

export async function sendChatMessage() {
  const text = el.chatInput.value.trim();
  if (!text) {
    return;
  }

  try {
    const session = await ensureChatSession();
    if (!session) {
      return;
    }
    const requestedModelId = state.modelId;
    const requestDiagramFingerprint = JSON.stringify(state.diagram || {});
    appendChat("user", text);
    el.chatInput.value = "";
    el.chatSendBtn.disabled = true;

    beginChatActivity("Understanding your request");
    el.chatMessages.scrollTop = el.chatMessages.scrollHeight;

    const response = await api(`/chatbot/sessions/${session.sessionId}/messages`, {
      method: "POST",
      body: JSON.stringify({
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
        attachmentName: state.chat.attachment?.name || null,
        attachmentContent: state.chat.attachment?.content || null,
      }),
    });

    appendAssistantDeduped(response.assistantMessage || "Done");
    appendProposalCard(state.activeType, session.sessionId, response.proposal);
    if (!response.proposal) {
      appendChoiceButtons(state.activeType, session.sessionId, response.choices);
    }
    await applyAssistantModelResponse(
      state.activeType,
      response,
      requestedModelId,
      requestDiagramFingerprint,
    );
    state.chat.attachment = null;
    if (el.chatFileInput) {
      el.chatFileInput.value = "";
    }
    updateChatAttachmentLabel();
    setStatus("Assistant response received");
  } catch (error) {
    appendChat("assistant", `Error: ${error.message}`);
    setError(`Chat failed: ${error.message}`);
  } finally {
    endChatActivity();
    el.chatSendBtn.disabled = false;
  }
}
