import {state} from './state.js';
import {el} from './dom.js';
import {api, isPlannedFeatureError} from './api.js';
import {setError, setStatus} from './status.js';
import {apiUrl, MODEL_TYPES, websocketUrl} from './config.js';
import {toDiagram} from './diagram.js';
import {renderDiagram} from './canvas.js';
import {renderMarkdown} from './markdown.js';

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
        modelName: (state.tabs[typeKey]?.modelName
            || `${typeKey}-assistant`).trim(),
        initialDocument: "Initialized from web modeling editor",
        projectId: state.project.id
      })
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
    modelId: response.modelId || null
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
        method: "DELETE"
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
    state.chat.channels.set(typeKey, {kind: "websocket", handle: socket});
    return;
  } catch {
    // fallback to SSE
  }

  const stream = new EventSource(
      apiUrl(`/chatbot/sessions/${sessionId}/events`));
  stream.onmessage = () => {
  };
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
  stream.onerror = () => {
    setStatus("Chat realtime stream disconnected");
  };
  state.chat.channels.set(typeKey, {kind: "sse", handle: stream});
}

function handleChatRealtimeEvent(typeKey, eventType, payload) {
  if (eventType === "chat.assistant") {
    const message = payload?.assistantMessage || payload?.message;
    appendAssistantDeduped(message);
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

  const avatar = document.createElement("div");
  avatar.className = "chat-msg-avatar";
  avatar.innerHTML = role === "user"
      ? '<span aria-hidden="true" class="chat-msg-avatar-icon icon-svg icon-mask" style="--icon-src: url(\'/assets/icons/placeholder.svg\');"></span>'
      : '<span aria-hidden="true" class="chat-msg-avatar-icon icon-svg icon-mask" style="--icon-src: url(\'/assets/icons/placeholder.svg\');"></span>';

  const bubble = document.createElement("div");
  bubble.className = "chat-msg-bubble";
  if (role === "assistant") {
    bubble.appendChild(renderMarkdown(text));
  } else {
    bubble.textContent = text;
  }

  msg.appendChild(avatar);
  msg.appendChild(bubble);
  el.chatMessages.appendChild(msg);
  el.chatMessages.scrollTop = el.chatMessages.scrollHeight;
}

function appendAssistantDeduped(text) {
  if (!text) {
    return;
  }
  const messages = [...el.chatMessages.querySelectorAll(
      '.chat-msg.assistant[data-chat-kind="message"]')];
  const last = messages[messages.length - 1];
  if (last?.dataset.chatText === text) {
    return;
  }
  appendChat("assistant", text);
}

function appendProposalCard(typeKey, sessionId, proposal) {
  if (!proposal) {
    return;
  }
  const card = document.createElement("div");
  card.className = "chat-msg assistant";
  card.dataset.chatKind = "proposal";
  const bubble = document.createElement("div");
  bubble.className = "chat-msg-bubble";

  const title = document.createElement("div");
  title.className = "chat-proposal-title";
  title.textContent = `Proposal ${proposal.riskLevel
  || "HIGH"}${proposal.approvalRequired ? " requires approval" : ""}`;
  bubble.appendChild(title);

  const summary = document.createElement("div");
  summary.className = "chat-proposal-summary";
  summary.textContent = `Affected: ${(proposal.affectedElements || []).join(
      ", ") || "n/a"}`;
  bubble.appendChild(summary);

  const validation = document.createElement("div");
  validation.className = "chat-proposal-validation";
  const issues = Array.isArray(proposal.validation?.issues)
      ? proposal.validation.issues : [];
  validation.textContent = `Validation: ${proposal.validation?.mandatoryPassed
      ? "mandatory constraints pass"
      : "mandatory constraints fail"}; ${issues.length} issue(s)`;
  bubble.appendChild(validation);

  const operations = Array.isArray(proposal.patch?.operations)
      ? proposal.patch.operations : [];
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
        operation.referenceName && `via ${operation.referenceName}`
      ].filter(Boolean).join(" ");
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
    const approve = document.createElement("button");
    approve.type = "button";
    approve.textContent = "Approve";
    approve.addEventListener("click", async () => {
      try {
        const response = await api(
            `/chatbot/sessions/${sessionId}/proposals/${proposal.id}/approve`, {
              method: "POST"
            });
        appendAssistantDeduped(
            response.assistantMessage || "Proposal approved");
        await applyAssistantModelResponse(typeKey, response);
      } catch (error) {
        appendChat("assistant", `Error: ${error.message}`);
      }
    });
    actions.appendChild(approve);

    const reject = document.createElement("button");
    reject.type = "button";
    reject.textContent = "Reject";
    reject.addEventListener("click", async () => {
      try {
        await api(
            `/chatbot/sessions/${sessionId}/proposals/${proposal.id}/reject`, {
              method: "POST"
            });
        appendChat("assistant", "Proposal rejected.");
      } catch (error) {
        appendChat("assistant", `Error: ${error.message}`);
      }
    });
    actions.appendChild(reject);
  } else if (proposal.id) {
    const undo = document.createElement("button");
    undo.type = "button";
    undo.textContent = "Undo";
    undo.addEventListener("click", async () => {
      try {
        const response = await api(
            `/chatbot/sessions/${sessionId}/proposals/${proposal.id}/undo`, {
              method: "POST"
            });
        appendAssistantDeduped(response.assistantMessage || "Proposal undone");
        await applyAssistantModelResponse(typeKey, response);
      } catch (error) {
        appendChat("assistant", `Error: ${error.message}`);
      }
    });
    actions.appendChild(undo);
  }
  bubble.appendChild(actions);
  card.appendChild(bubble);
  el.chatMessages.appendChild(card);
  el.chatMessages.scrollTop = el.chatMessages.scrollHeight;
}

function appendChoiceButtons(typeKey, sessionId, choices) {
  if (!Array.isArray(choices) || !choices.length) {
    return;
  }
  const choice = choices[0];
  const card = document.createElement("div");
  card.className = "chat-msg assistant";
  card.dataset.chatKind = "choice";
  const bubble = document.createElement("div");
  bubble.className = "chat-msg-bubble";
  bubble.textContent = choice.prompt || "Choose an option.";
  const actions = document.createElement("div");
  actions.className = "chat-proposal-actions";
  for (const option of choice.options || []) {
    const button = document.createElement("button");
    button.type = "button";
    button.textContent = option.label || option.id;
    button.addEventListener("click", async () => {
      try {
        await api(`/chatbot/sessions/${sessionId}/choices`, {
          method: "POST",
          body: JSON.stringify({
            choiceId: choice.id,
            optionId: option.id
          })
        });
        appendChat("assistant", `Selected ${option.label || option.id}.`);
      } catch (error) {
        appendChat("assistant", `Error: ${error.message}`);
      }
    });
    actions.appendChild(button);
  }
  bubble.appendChild(actions);
  card.appendChild(bubble);
  el.chatMessages.appendChild(card);
  el.chatMessages.scrollTop = el.chatMessages.scrollHeight;
}

async function applyAssistantModelResponse(typeKey, response,
    requestedModelId = null,
    requestDiagramFingerprint = null) {
  if (!response) {
    return;
  }
  const responseModelId = String(response.modelId || "").trim();
  const liveModelId = String(state.modelId || "").trim();
  const requestedModelIdValue = String(requestedModelId || "").trim();
  const liveDiagramFingerprint = JSON.stringify(state.diagram || {});
  const hasLocalEditsSinceRequest = requestDiagramFingerprint != null
      && liveDiagramFingerprint !== requestDiagramFingerprint;
  let model = response.model || null;
  if (!model && responseModelId) {
    try {
      model = await api(`/${MODEL_TYPES[typeKey].apiType}/${responseModelId}`);
    } catch {
      model = null;
    }
  }
  if (responseModelId && liveModelId && responseModelId !== liveModelId
      && requestedModelIdValue && liveModelId !== requestedModelIdValue) {
    setStatus(
        "Assistant response received for another model; skipped auto-apply");
    return;
  }
  if (hasLocalEditsSinceRequest) {
    setStatus("Assistant response received; kept your newer canvas edits");
    return;
  }
  if (!model) {
    return;
  }
  state.modelId = responseModelId || state.modelId;
  state.modelRevision = Number(response.revision) || state.modelRevision || 1;
  state.baseModel = structuredClone(model);
  state.diagram = toDiagram(typeKey, model, state.tabs[typeKey]?.modelName);
  if (state.tabs[typeKey]) {
    state.tabs[typeKey].modelId = state.modelId;
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

    el.chatTypingIndicator.classList.remove("hidden");
    el.chatMessages.scrollTop = el.chatMessages.scrollHeight;
    if (el.chatHeaderSubtitle) {
      el.chatHeaderSubtitle.textContent = "Thinking…";
    }

    const response = await api(
        `/chatbot/sessions/${session.sessionId}/messages`, {
          method: "POST",
          body: JSON.stringify({
            message: text,
            modelId: state.modelId,
            revision: state.modelRevision || null,
            activeView: state.activeType,
            selectedElementIds: [...new Set([
              ...(state.selectedNodeIds instanceof Set
                  ? [...state.selectedNodeIds]
                  : []),
              state.selectedNodeId,
              state.selectedConnectionId
            ].filter(Boolean))],
            attachmentName: state.chat.attachment?.name || null,
            attachmentContent: state.chat.attachment?.content || null
          })
        });

    el.chatTypingIndicator.classList.add("hidden");
    if (el.chatHeaderSubtitle) {
      el.chatHeaderSubtitle.textContent = "Ready to help";
    }
    appendAssistantDeduped(response.assistantMessage || "Done");
    appendProposalCard(state.activeType, session.sessionId, response.proposal);
    appendChoiceButtons(state.activeType, session.sessionId, response.choices);
    await applyAssistantModelResponse(state.activeType, response,
        requestedModelId,
        requestDiagramFingerprint);
    state.chat.attachment = null;
    if (el.chatFileInput) {
      el.chatFileInput.value = "";
    }
    updateChatAttachmentLabel();
    setStatus("Assistant response received");
  } catch (error) {
    el.chatTypingIndicator.classList.add("hidden");
    if (el.chatHeaderSubtitle) {
      el.chatHeaderSubtitle.textContent = "Ready to help";
    }
    appendChat("assistant", `Error: ${error.message}`);
    setError(`Chat failed: ${error.message}`);
  } finally {
    el.chatSendBtn.disabled = false;
  }
}
