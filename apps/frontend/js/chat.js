import {state} from './state.js';
import {el} from './dom.js';
import {api, isPlannedFeatureError} from './api.js';
import {setError, setStatus} from './status.js';
import {apiUrl, MODEL_TYPES, websocketUrl} from './config.js';
import {serializeModel, toDiagram} from './diagram.js';
import {renderDiagram} from './canvas.js';

// ── Chat session / realtime ───────────────────────────────────────────────────

export async function ensureChatSession() {
  if (state.chat.available === false) {
    setStatus("Chat is not available in this backend build.");
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
        projectId: state.project?.id || null
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

  const avatar = document.createElement("div");
  avatar.className = "chat-msg-avatar";
  avatar.innerHTML = role === "user"
      ? '<span aria-hidden="true" class="chat-msg-avatar-icon icon-svg icon-mask" style="--icon-src: url(\'/assets/icons/placeholder.svg\');"></span>'
      : '<span aria-hidden="true" class="chat-msg-avatar-icon icon-svg icon-mask" style="--icon-src: url(\'/assets/icons/placeholder.svg\');"></span>';

  const bubble = document.createElement("div");
  bubble.className = "chat-msg-bubble";
  bubble.textContent = text;

  msg.appendChild(avatar);
  msg.appendChild(bubble);
  el.chatMessages.appendChild(msg);
  el.chatMessages.scrollTop = el.chatMessages.scrollHeight;
}

function appendAssistantDeduped(text) {
  if (!text) {
    return;
  }
  const last = el.chatMessages.lastElementChild;
  if (last?.classList?.contains("assistant")) {
    const bubble = last.querySelector(".chat-msg-bubble");
    if (bubble && bubble.textContent === text) {
      return;
    }
  }
  appendChat("assistant", text);
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
            currentModel: serializeModel(),
            attachmentName: state.chat.attachment?.name || null,
            attachmentContent: state.chat.attachment?.content || null
          })
        });

    el.chatTypingIndicator.classList.add("hidden");
    if (el.chatHeaderSubtitle) {
      el.chatHeaderSubtitle.textContent = "Ready to help";
    }
    appendAssistantDeduped(response.assistantMessage || "Done");
    if (response.model) {
      const responseModelId = String(response.modelId || "").trim();
      const liveModelId = String(state.modelId || "").trim();
      const requestedModelIdValue = String(requestedModelId || "").trim();
      const liveDiagramFingerprint = JSON.stringify(state.diagram || {});
      const hasLocalEditsSinceRequest = liveDiagramFingerprint
          !== requestDiagramFingerprint;
      if (responseModelId && liveModelId && responseModelId !== liveModelId
          && requestedModelIdValue && liveModelId !== requestedModelIdValue) {
        setStatus(
            "Assistant response received for another model; skipped auto-apply");
      } else if (hasLocalEditsSinceRequest) {
        setStatus("Assistant response received; kept your newer canvas edits");
      } else {
        state.modelId = responseModelId || state.modelId;
        state.modelRevision = Number(response.revision) || state.modelRevision
            || 1;
        state.baseModel = structuredClone(response.model);
        state.diagram = toDiagram(state.activeType, response.model,
            state.tabs[state.activeType]?.modelName);
        if (state.tabs[state.activeType]) {
          state.tabs[state.activeType].modelId = state.modelId;
          state.tabs[state.activeType].modelRevision = state.modelRevision;
          state.tabs[state.activeType].baseModel = state.baseModel;
          state.tabs[state.activeType].diagram = state.diagram;
          state.tabs[state.activeType].modelName = response.model.name
              || state.tabs[state.activeType].modelName;
        }
        renderDiagram();
      }
    }
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
