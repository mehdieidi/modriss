import {state} from './state.js';
import {el} from './dom.js';
import {setStatus} from './status.js';
import {renderDiagram} from './canvas.js';
import {websocketUrl} from './config.js';

const COLLABORATION_ENABLED = false;
const CURSOR_THROTTLE_MS = 60;
const DIAGRAM_UPDATE_THROTTLE_MS = 120;
const NODE_MOVE_THROTTLE_MS = 40;
const RECONNECT_BASE_MS = 800;
const RECONNECT_MAX_MS = 8000;
const MODEL_TYPES = new Set(["cim", "pim", "psm"]);
const nodeMoveSentAt = new Map();

function collaborationDisabled() {
  return !COLLABORATION_ENABLED;
}

function flushPendingDiagramUpdate() {
  if (!state.collaboration.pendingDiagramUpdate) {
    return;
  }
  publishDiagramUpdate({immediate: true});
}

function clearRemoteCursors() {
  el.canvasViewport?.querySelectorAll(".remote-cursor").forEach(
      (node) => node.remove());
}

function clearReconnectTimer() {
  if (state.collaboration.reconnectTimer) {
    clearTimeout(state.collaboration.reconnectTimer);
    state.collaboration.reconnectTimer = null;
  }
}

function socketReady() {
  const ws = state.collaboration.ws;
  return Boolean(
      ws
      && ws.readyState === WebSocket.OPEN
      && state.collaboration.isReady
  );
}

function sendCollaborationPayload(payload,
    {queueDiagramOnFailure = false} = {}) {
  const ws = state.collaboration.ws;
  if (!ws || ws.readyState !== WebSocket.OPEN || !state.collaboration.isReady) {
    if (queueDiagramOnFailure) {
      state.collaboration.pendingDiagramUpdate = true;
    }
    return false;
  }
  try {
    ws.send(JSON.stringify(payload));
    return true;
  } catch {
    if (queueDiagramOnFailure) {
      state.collaboration.pendingDiagramUpdate = true;
    }
    return false;
  }
}

function normalizeActiveType(type, fallback = "cim") {
  const normalized = String(type || "").trim().toLowerCase();
  if (MODEL_TYPES.has(normalized)) {
    return normalized;
  }
  return MODEL_TYPES.has(fallback) ? fallback : "cim";
}

function normalizeModelId(modelId) {
  const normalized = String(modelId || "").trim();
  return normalized || null;
}

function syncLocalRevisionFromPayload(payload) {
  const revision = Number(payload?.revision);
  if (Number.isFinite(revision) && revision >= 0) {
    state.collaboration.revision = revision;
  }
}

function modelContextMatches(localModelId, remoteModelId) {
  const local = normalizeModelId(localModelId);
  const remote = normalizeModelId(remoteModelId);
  if (!remote) {
    return true;
  }
  if (!local) {
    return true;
  }
  return local === remote;
}

function normalizeStatus(status) {
  const normalized = String(status || "").trim().toUpperCase();
  if (normalized === "ONLINE" || normalized === "ACTIVE" || normalized
      === "IDLE" || normalized === "OFFLINE") {
    return normalized;
  }
  return "ACTIVE";
}

function readFiniteNumber(value) {
  return Number.isFinite(value) ? value : null;
}

function normalizeParticipant(rawParticipant) {
  const participant = rawParticipant && typeof rawParticipant === "object"
      ? rawParticipant : {};
  const canvasX = readFiniteNumber(participant.canvasX ?? participant.x);
  const canvasY = readFiniteNumber(participant.canvasY ?? participant.y);
  const userId = String(participant.userId || "");
  return {
    sessionId: String(participant.sessionId || userId || ""),
    userId,
    displayName: String(participant.displayName || "User"),
    avatarColor: String(participant.avatarColor || "#64748b"),
    status: normalizeStatus(participant.status),
    activeType: normalizeActiveType(participant.activeType, "cim"),
    canvasX,
    canvasY
  };
}

function mergeParticipant(existing, incoming) {
  if (!existing) {
    return incoming;
  }
  return {
    ...existing,
    ...incoming,
    canvasX: incoming.canvasX ?? existing.canvasX,
    canvasY: incoming.canvasY ?? existing.canvasY
  };
}

function normalizeParticipants(participants = []) {
  const bySessionId = new Map();
  participants.forEach((rawParticipant) => {
    const participant = normalizeParticipant(rawParticipant);
    if (!participant.sessionId) {
      return;
    }
    bySessionId.set(participant.sessionId,
        mergeParticipant(bySessionId.get(participant.sessionId), participant));
  });
  return [...bySessionId.values()];
}

function connectedParticipants(participants = []) {
  return participants.filter((participant) => participant.status !== "OFFLINE");
}

function initialsFromName(name) {
  const normalized = String(name || "").trim();
  if (!normalized) {
    return "U";
  }
  const parts = normalized.split(/\s+/).filter(Boolean);
  if (!parts.length) {
    return normalized.slice(0, 1).toUpperCase();
  }
  if (parts.length === 1) {
    return parts[0].slice(0, 1).toUpperCase();
  }
  return `${parts[0].slice(0, 1)}${parts[1].slice(0, 1)}`.toUpperCase();
}

const remoteCursorCache = new Map(); // sessionId -> {element, participant}

export function renderRemoteCursors() {
  if (collaborationDisabled()) {
    clearRemoteCursors();
    return;
  }
  clearRemoteCursors();
  const viewportRect = el.canvasViewport?.getBoundingClientRect();
  if (!viewportRect) {
    return;
  }
  const selfSessionId = state.collaboration.sessionId;
  const scale = Number.isFinite(state.viewport.scale) && state.viewport.scale
  > 0
      ? state.viewport.scale : 1;

  state.collaboration.participants.forEach((participant) => {
    if (participant.sessionId === selfSessionId) {
      return;
    }
    if (participant.status === "OFFLINE") {
      return;
    }
    if (participant.activeType !== state.activeType) {
      return;
    }
    if (participant.canvasX === null || participant.canvasY === null) {
      return;
    }

    const localX = participant.canvasX * scale + state.viewport.x;
    const localY = participant.canvasY * scale + state.viewport.y;
    const visibleMargin = 30;
    if (localX < -visibleMargin || localX > viewportRect.width + visibleMargin
        || localY < -visibleMargin
        || localY > viewportRect.height + visibleMargin) {
      return;
    }

    const cursor = document.createElement("div");
    cursor.className = "remote-cursor";
    cursor.style.left = `${localX}px`;
    cursor.style.top = `${localY}px`;
    cursor.style.setProperty("--cursor-color",
        participant.avatarColor || "#64748b");
    const label = document.createElement("span");
    label.className = "remote-cursor-label";
    label.textContent = participant.displayName || "User";
    cursor.appendChild(label);
    el.canvasViewport.appendChild(cursor);
  });

}

function renderPresence(participants = []) {
  const normalized = normalizeParticipants(participants);
  const connected = connectedParticipants(normalized);
  state.collaboration.participants = normalized;
  const selfSessionId = state.collaboration.sessionId;
  const others = connected.filter(
      (participant) => participant.sessionId !== selfSessionId);
  if (el.collaborationCount) {
    el.collaborationCount.textContent = String(others.length);
  }
  renderRemoteCursors();
  if (!el.presenceList) {
    return;
  }
  el.presenceList.innerHTML = "";
  if (!connected.length) {
    const empty = document.createElement("div");
    empty.className = "collaboration-list-empty";
    empty.textContent = "No collaborators connected";
    el.presenceList.appendChild(empty);
  } else {
    connected.forEach((participant) => {
      const item = document.createElement("div");
      item.className = "collaboration-list-item";
      item.style.setProperty("--presence-color",
          String(participant.avatarColor || "#64748b"));

      const name = document.createElement("span");
      name.className = "collaboration-list-name";
      const isSelf = participant.sessionId === selfSessionId;
      name.textContent = isSelf
          ? `${String(participant.displayName || "You")} (You)`
          : String(participant.displayName || "User");

      const status = document.createElement("span");
      status.className = "collaboration-list-status";
      status.textContent = String(participant.status || "ACTIVE");

      item.appendChild(name);
      item.appendChild(status);
      el.presenceList.appendChild(item);
    });
  }
}

function ensureDiagramShape(diagram) {
  if (!diagram || typeof diagram !== "object") {
    return {nodes: [], connections: []};
  }
  if (!Array.isArray(diagram.nodes)) {
    diagram.nodes = [];
  }
  if (!Array.isArray(diagram.connections)) {
    diagram.connections = [];
  }
  return diagram;
}

function findNode(diagram, nodeId) {
  if (!diagram || !Array.isArray(diagram.nodes)) {
    return null;
  }
  return diagram.nodes.find((node) => String(node?.id) === String(nodeId))
      || null;
}

function applyOperationToDiagram(diagram, activeType, operation) {
  const safeDiagram = ensureDiagramShape(diagram);
  if (!operation || typeof operation !== "object") {
    return false;
  }
  const opType = String(operation.opType || "").toUpperCase();
  if (!opType) {
    return false;
  }

  if (opType === "NODE_ADD") {
    const incoming = operation.node;
    if (!incoming || typeof incoming !== "object") {
      return false;
    }
    const nodeId = String(incoming.id || "").trim();
    if (!nodeId) {
      return false;
    }
    const existingIndex = safeDiagram.nodes.findIndex(
        (node) => String(node?.id) === nodeId);
    const clone = structuredClone(incoming);
    if (existingIndex >= 0) {
      safeDiagram.nodes[existingIndex] = clone;
    } else {
      safeDiagram.nodes.push(clone);
    }
    return true;
  }

  if (opType === "NODE_MOVE") {
    const node = findNode(safeDiagram, operation.nodeId);
    if (!node) {
      return false;
    }
    const nextX = Number(operation.x);
    const nextY = Number(operation.y);
    if (!Number.isFinite(nextX) || !Number.isFinite(nextY)) {
      return false;
    }
    const roundedX = Math.round(nextX);
    const roundedY = Math.round(nextY);
    if (node.x === roundedX && node.y === roundedY) {
      return false;
    }
    node.x = roundedX;
    node.y = roundedY;
    node.meta = node.meta && typeof node.meta === "object" ? node.meta : {};
    node.meta.x = roundedX;
    node.meta.y = roundedY;
    return true;
  }

  if (opType === "NODE_RENAME") {
    const node = findNode(safeDiagram, operation.nodeId);
    if (!node) {
      return false;
    }
    const label = String(operation.label || "").trim();
    if (!label) {
      return false;
    }
    if (String(node.label || "") === label) {
      return false;
    }
    node.label = label;
    node.meta = node.meta && typeof node.meta === "object" ? node.meta : {};
    if (activeType === "cim") {
      node.meta.label = label;
    } else {
      node.meta.name = label;
    }
    return true;
  }

  return false;
}

function applyRemoteCanvasOperation(activeType, operation) {
  if (!MODEL_TYPES.has(activeType)) {
    return;
  }
  if (activeType === state.activeType) {
    const changed = applyOperationToDiagram(state.diagram, activeType,
        operation);
    if (!changed) {
      return;
    }
    if (state.tabs[state.activeType]) {
      state.tabs[state.activeType].diagram = state.diagram;
    }
    renderDiagram();
    return;
  }
  const tab = state.tabs[activeType];
  if (!tab) {
    return;
  }
  tab.diagram = ensureDiagramShape(tab.diagram || {});
  applyOperationToDiagram(tab.diagram, activeType, operation);
}

export function disconnectCollaboration() {
  state.collaboration.intentionalDisconnect = true;
  state.collaboration.isReady = false;
  state.collaboration.pendingDiagramUpdate = false;
  clearReconnectTimer();
  if (state.collaboration.diagramThrottleTimer) {
    clearTimeout(state.collaboration.diagramThrottleTimer);
    state.collaboration.diagramThrottleTimer = null;
  }
  if (state.collaboration.ws) {
    try {
      state.collaboration.ws.close();
    } catch {
      // ignore close errors
    }
    state.collaboration.ws = null;
  }
  clearRemoteCursors();
  state.collaboration.projectId = null;
  state.collaboration.sessionId = null;
  nodeMoveSentAt.clear();
  state.collaboration.reconnectAttempts = 0;
  state.collaboration.revision = 0;
  state.collaboration.lastDiagramPublishAt = 0;
  state.collaboration.participants = [];
  if (el.collaborationCount) {
    el.collaborationCount.textContent = "0";
  }
  if (el.presenceList) {
    el.presenceList.innerHTML = '<div class="collaboration-list-empty">No collaborators connected</div>';
  }
}

function scheduleReconnect(projectId) {
  if (state.collaboration.intentionalDisconnect || !projectId) {
    return;
  }
  clearReconnectTimer();
  const attempt = state.collaboration.reconnectAttempts;
  const delay = Math.min(RECONNECT_BASE_MS * (2 ** attempt),
      RECONNECT_MAX_MS);
  state.collaboration.reconnectAttempts = attempt + 1;
  state.collaboration.reconnectTimer = setTimeout(() => {
    openCollaborationSocket(projectId);
  }, delay);
}

function openCollaborationSocket(projectId) {
  const token = state.auth.token || window.localStorage.getItem(
      "func2.authToken");
  if (!projectId || !token) {
    return;
  }

  if (state.collaboration.ws) {
    try {
      state.collaboration.ws.close();
    } catch {
      // ignore close errors
    }
  }

  const ws = new WebSocket(
      websocketUrl(`/ws/collaboration/projects/${projectId}`));
  state.collaboration.ws = ws;

  ws.addEventListener("open", () => {
    if (state.collaboration.ws !== ws) {
      return;
    }
    state.collaboration.isReady = false;
    state.collaboration.reconnectAttempts = 0;
    clearReconnectTimer();
    ws.send(JSON.stringify({type: "auth", token}));
  });

  ws.addEventListener("message", (event) => {
    if (state.collaboration.ws !== ws) {
      return;
    }
    try {
      const payload = JSON.parse(event.data);
      if (payload.type === "session.ready") {
        state.collaboration.revision = payload.revision || 0;
        state.collaboration.sessionId = String(payload.sessionId || "");
        state.collaboration.isReady = true;
        renderPresence(payload.participants || []);
        const modelIds = payload.modelIds && typeof payload.modelIds
        === "object"
            ? payload.modelIds : {};
        const remoteModelId = normalizeModelId(modelIds[state.activeType]);
        const shared = payload.diagrams?.[state.activeType];
        const shouldHydrateFromShared = Boolean(
            shared
            && ((payload.revision || 0) > 0 || state.diagram?.nodes?.length
                === 0)
            && modelContextMatches(state.modelId, remoteModelId));
        if (shouldHydrateFromShared) {
          state.collaboration.remoteApplying = true;
          state.diagram = structuredClone(shared);
          if (state.tabs[state.activeType]) {
            state.tabs[state.activeType].diagram = state.diagram;
          }
          renderDiagram();
          state.collaboration.remoteApplying = false;
        }
        flushPendingDiagramUpdate();
        return;
      }
      if (payload.type === "presence.updated") {
        renderPresence(payload.participants || []);
        return;
      }
      if (payload.type === "canvas.updated") {
        const activeType = String(payload.activeType || "").toLowerCase();
        if (!MODEL_TYPES.has(activeType)) {
          return;
        }
        const remoteModelId = normalizeModelId(payload.modelId);
        const tabModelId = activeType === state.activeType
            ? state.modelId
            : state.tabs[activeType]?.modelId;
        if (!modelContextMatches(tabModelId, remoteModelId)) {
          return;
        }
        state.collaboration.revision = payload.revision
            ?? state.collaboration.revision;
        const actorSessionId = String(payload.actorSessionId || "");
        const selfSessionId = String(state.collaboration.sessionId || "");
        if (actorSessionId && selfSessionId && actorSessionId
            === selfSessionId) {
          syncLocalRevisionFromPayload(payload);
          return;
        }
        if (activeType !== state.activeType) {
          if (state.tabs[activeType]) {
            state.tabs[activeType].diagram = structuredClone(
                payload.diagram);
          }
          return;
        }
        state.collaboration.remoteApplying = true;
        state.diagram = structuredClone(payload.diagram);
        if (state.tabs[state.activeType]) {
          state.tabs[state.activeType].diagram = state.diagram;
        }
        renderDiagram();
        state.collaboration.remoteApplying = false;
        return;
      }
      if (payload.type === "canvas.op") {
        const activeType = String(payload.activeType || "").toLowerCase();
        if (!MODEL_TYPES.has(activeType)) {
          return;
        }
        const remoteModelId = normalizeModelId(payload.modelId);
        const tabModelId = activeType === state.activeType
            ? state.modelId
            : state.tabs[activeType]?.modelId;
        if (!modelContextMatches(tabModelId, remoteModelId)) {
          return;
        }
        state.collaboration.revision = payload.revision
            ?? state.collaboration.revision;
        const actorSessionId = String(payload.actorSessionId || "");
        const selfSessionId = String(state.collaboration.sessionId || "");
        if (actorSessionId && selfSessionId && actorSessionId
            === selfSessionId) {
          syncLocalRevisionFromPayload(payload);
          return;
        }
        state.collaboration.remoteApplying = true;
        applyRemoteCanvasOperation(activeType, payload.operation || payload.op);
        state.collaboration.remoteApplying = false;
        return;
      }
      if (payload.type === "canvas.conflict") {
        const activeType = String(payload.activeType || "").toLowerCase();
        if (!MODEL_TYPES.has(activeType)) {
          return;
        }
        const remoteModelId = normalizeModelId(payload.modelId);
        const tabModelId = activeType === state.activeType
            ? state.modelId
            : state.tabs[activeType]?.modelId;
        if (!modelContextMatches(tabModelId, remoteModelId)) {
          return;
        }
        state.collaboration.revision = payload.revision
            ?? state.collaboration.revision;
        if (activeType === state.activeType && payload.diagram) {
          state.collaboration.remoteApplying = true;
          state.diagram = structuredClone(payload.diagram);
          if (state.tabs[state.activeType]) {
            state.tabs[state.activeType].diagram = state.diagram;
          }
          renderDiagram();
          state.collaboration.remoteApplying = false;
        }
        setStatus("Remote conflict resolved with latest shared state");
      }
    } catch {
      // ignore malformed frames
    }
  });

  ws.addEventListener("close", (event) => {
    if (state.collaboration.ws !== ws) {
      return;
    }
    state.collaboration.ws = null;
    state.collaboration.isReady = false;
    if (state.collaboration.intentionalDisconnect) {
      return;
    }
    if (event.code === 1008) {
      setStatus("Collaboration authentication failed");
      return;
    }
    scheduleReconnect(projectId);
  });

  ws.addEventListener("error", () => {
    // close handler drives reconnect behavior
  });
}

export function connectCollaboration(projectId) {
  if (collaborationDisabled()) {
    disconnectCollaboration();
    return;
  }
  if (!projectId) {
    disconnectCollaboration();
    return;
  }
  clearReconnectTimer();
  if (state.collaboration.ws) {
    try {
      state.collaboration.ws.close();
    } catch {
      // ignore close errors
    }
    state.collaboration.ws = null;
  }
  state.collaboration.intentionalDisconnect = false;
  state.collaboration.projectId = String(projectId);
  state.collaboration.sessionId = null;
  state.collaboration.isReady = false;
  state.collaboration.pendingDiagramUpdate = false;
  state.collaboration.reconnectAttempts = 0;
  state.collaboration.revision = 0;
  state.collaboration.lastDiagramPublishAt = 0;
  nodeMoveSentAt.clear();
  state.collaboration.participants = [];
  openCollaborationSocket(projectId);
}

export function publishCursor(clientX, clientY, status = "ACTIVE") {
  if (collaborationDisabled()) {
    return;
  }
  if (!socketReady()) {
    return;
  }
  const now = Date.now();
  if (now - state.collaboration.cursorThrottleAt < CURSOR_THROTTLE_MS) {
    return;
  }
  const viewportRect = el.canvasViewport?.getBoundingClientRect();
  if (!viewportRect || !Number.isFinite(state.viewport.scale)
      || state.viewport.scale <= 0) {
    return;
  }

  const localX = clientX - viewportRect.left;
  const localY = clientY - viewportRect.top;
  if (localX < 0 || localY < 0 || localX > viewportRect.width
      || localY > viewportRect.height) {
    return;
  }
  const canvasX = (localX - state.viewport.x) / state.viewport.scale;
  const canvasY = (localY - state.viewport.y) / state.viewport.scale;
  if (!Number.isFinite(canvasX) || !Number.isFinite(canvasY)) {
    return;
  }

  state.collaboration.cursorThrottleAt = now;
  sendCollaborationPayload({
    type: "cursor.update",
    x: canvasX,
    y: canvasY,
    canvasX,
    canvasY,
    activeType: state.activeType,
    status
  });
}

function sendDiagramUpdateNow() {
  if (!socketReady()) {
    state.collaboration.pendingDiagramUpdate = true;
    return false;
  }
  if (state.collaboration.remoteApplying) {
    return false;
  }
  state.collaboration.lastDiagramPublishAt = Date.now();
  return sendCollaborationPayload({
    type: "canvas.update",
    activeType: state.activeType,
    modelId: state.modelId || null,
    baseRevision: state.collaboration.revision,
    diagram: state.diagram
  }, {queueDiagramOnFailure: true});
}

export function publishDiagramUpdate({immediate = false} = {}) {
  if (collaborationDisabled()) {
    return;
  }
  if (state.collaboration.remoteApplying) {
    return;
  }
  if (!socketReady()) {
    state.collaboration.pendingDiagramUpdate = true;
    return;
  }
  const now = Date.now();
  const elapsed = now - (state.collaboration.lastDiagramPublishAt || 0);
  if (immediate || elapsed >= DIAGRAM_UPDATE_THROTTLE_MS) {
    if (state.collaboration.diagramThrottleTimer) {
      clearTimeout(state.collaboration.diagramThrottleTimer);
      state.collaboration.diagramThrottleTimer = null;
    }
    if (!sendDiagramUpdateNow()) {
      state.collaboration.pendingDiagramUpdate = true;
    }
    return;
  }
  if (state.collaboration.diagramThrottleTimer) {
    return;
  }
  const waitMs = Math.max(16, DIAGRAM_UPDATE_THROTTLE_MS - elapsed);
  state.collaboration.diagramThrottleTimer = setTimeout(() => {
    state.collaboration.diagramThrottleTimer = null;
    if (!sendDiagramUpdateNow()) {
      state.collaboration.pendingDiagramUpdate = true;
    }
  }, waitMs);
}

export function publishNodeMove(nodeId, x, y, {immediate = false} = {}) {
  if (collaborationDisabled()) {
    return;
  }
  if (state.collaboration.remoteApplying) {
    return;
  }
  const id = String(nodeId || "").trim();
  if (!id || !Number.isFinite(x) || !Number.isFinite(y)) {
    return;
  }
  if (!socketReady()) {
    state.collaboration.pendingDiagramUpdate = true;
    return;
  }
  const now = Date.now();
  const lastAt = nodeMoveSentAt.get(id) || 0;
  if (!immediate && now - lastAt < NODE_MOVE_THROTTLE_MS) {
    return;
  }
  nodeMoveSentAt.set(id, now);
  sendCollaborationPayload({
    type: "canvas.op",
    activeType: state.activeType,
    modelId: state.modelId || null,
    baseRevision: state.collaboration.revision,
    operation: {
      opType: "NODE_MOVE",
      nodeId: id,
      x: Math.round(x),
      y: Math.round(y)
    }
  }, {queueDiagramOnFailure: true});
}

export function publishNodeRename(nodeId, label) {
  if (collaborationDisabled()) {
    return;
  }
  if (state.collaboration.remoteApplying) {
    return;
  }
  const id = String(nodeId || "").trim();
  const normalized = String(label || "").trim();
  if (!id || !normalized) {
    return;
  }
  sendCollaborationPayload({
    type: "canvas.op",
    activeType: state.activeType,
    modelId: state.modelId || null,
    baseRevision: state.collaboration.revision,
    operation: {
      opType: "NODE_RENAME",
      nodeId: id,
      label: normalized
    }
  }, {queueDiagramOnFailure: true});
}

export function publishNodeAdd(node) {
  if (collaborationDisabled()) {
    return;
  }
  if (state.collaboration.remoteApplying) {
    return;
  }
  if (!node || typeof node !== "object" || !node.id) {
    return;
  }
  sendCollaborationPayload({
    type: "canvas.op",
    activeType: state.activeType,
    modelId: state.modelId || null,
    baseRevision: state.collaboration.revision,
    operation: {
      opType: "NODE_ADD",
      node: structuredClone(node)
    }
  }, {queueDiagramOnFailure: true});
}
