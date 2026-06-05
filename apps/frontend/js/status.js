import {el} from './dom.js';

const TOAST_TTL_MS = 4600;
let toastHost = null;
let toastSeq = 0;
let progressToast = null;
let progressToastParts = null;

function ensureToastHost() {
  if (toastHost?.isConnected) {
    return toastHost;
  }
  toastHost = document.getElementById("toastHost");
  if (!toastHost) {
    toastHost = document.createElement("div");
    toastHost.id = "toastHost";
    toastHost.className = "toast-host";
    toastHost.setAttribute("aria-live", "polite");
    toastHost.setAttribute("aria-atomic", "false");
    document.body.appendChild(toastHost);
  }
  return toastHost;
}

function removeToast(toast) {
  if (!toast?.isConnected) {
    return;
  }
  if (toast === progressToast) {
    progressToast = null;
    progressToastParts = null;
  }
  toast.classList.add("toast-leaving");
  window.setTimeout(() => toast.remove(), 180);
}

function normalizeProgress(value) {
  return Math.max(0, Math.min(100, Number(value) || 0));
}

export function showProgressNotification({
  kicker = "Working",
  title = "Processing",
  subtitle = "",
  label = "Starting...",
  progress = 0
} = {}) {
  const host = ensureToastHost();
  if (progressToast?.isConnected && progressToastParts) {
    progressToastParts.kicker.textContent = kicker;
    progressToastParts.title.textContent = title;
    progressToastParts.message.textContent = subtitle;
    updateProgressNotification({label, progress});
    return;
  }

  const toast = document.createElement("div");
  toast.className = "toast toast-busy toast-progress";
  toast.setAttribute("role", "status");
  toast.dataset.toastId = String(++toastSeq);

  const icon = document.createElement("span");
  icon.className = "toast-icon";
  icon.setAttribute("aria-hidden", "true");

  const body = document.createElement("div");
  body.className = "toast-body";

  const kickerEl = document.createElement("div");
  kickerEl.className = "toast-progress-kicker";
  kickerEl.textContent = kicker;

  const titleEl = document.createElement("div");
  titleEl.className = "toast-title";
  titleEl.textContent = title;

  const messageEl = document.createElement("div");
  messageEl.className = "toast-message";
  messageEl.textContent = subtitle;

  const track = document.createElement("div");
  track.className = "toast-progress-track";
  track.setAttribute("aria-hidden", "true");

  const fill = document.createElement("div");
  fill.className = "toast-progress-fill";

  const sheen = document.createElement("div");
  sheen.className = "toast-progress-sheen";
  track.append(fill, sheen);

  const caption = document.createElement("div");
  caption.className = "toast-progress-caption";
  caption.setAttribute("aria-live", "polite");
  caption.textContent = label;

  body.append(kickerEl, titleEl, messageEl, track, caption);
  toast.append(icon, body);
  host.appendChild(toast);

  progressToast = toast;
  progressToastParts = {
    kicker: kickerEl,
    title: titleEl,
    message: messageEl,
    fill,
    caption
  };
  updateProgressNotification({label, progress});
}

export function updateProgressNotification({label, progress} = {}) {
  if (!progressToast?.isConnected || !progressToastParts) {
    return;
  }
  if (label !== undefined) {
    progressToastParts.caption.textContent = String(label || "");
  }
  if (progress !== undefined) {
    const normalized = normalizeProgress(progress);
    progressToastParts.fill.style.transform =
        `scaleX(${(normalized / 100).toFixed(4)})`;
  }
}

export function hideProgressNotification({delayMs = 0} = {}) {
  if (!progressToast?.isConnected) {
    progressToast = null;
    progressToastParts = null;
    return;
  }
  const toast = progressToast;
  if (delayMs > 0) {
    window.setTimeout(() => removeToast(toast), delayMs);
    return;
  }
  removeToast(toast);
}

// ── Status bar helpers ─────────────────────────────────────────────────────────
export function setStatus(message, {busy = false, error = false} = {}) {
  void el;
  const text = String(message || "").trim();
  if (!text) {
    return;
  }

  if (busy && progressToast?.isConnected) {
    updateProgressNotification({label: text});
    return;
  }

  const host = ensureToastHost();
  const kind = error ? "error" : busy ? "busy" : "success";
  const toast = document.createElement("div");
  toast.className = `toast toast-${kind}`;
  toast.setAttribute("role", error ? "alert" : "status");
  toast.dataset.toastId = String(++toastSeq);

  const icon = document.createElement("span");
  icon.className = "toast-icon";
  icon.setAttribute("aria-hidden", "true");

  const body = document.createElement("div");
  body.className = "toast-body";
  const title = document.createElement("div");
  title.className = "toast-title";
  title.textContent = error ? "Needs attention" : busy ? "Working" : "Done";
  const messageEl = document.createElement("div");
  messageEl.className = "toast-message";
  messageEl.textContent = text;
  body.append(title, messageEl);

  const close = document.createElement("button");
  close.className = "toast-close";
  close.type = "button";
  close.title = "Dismiss notification";
  close.setAttribute("aria-label", "Dismiss notification");
  close.textContent = "x";
  close.addEventListener("click", () => removeToast(toast));

  toast.append(icon, body, close);
  host.appendChild(toast);
  window.setTimeout(() => removeToast(toast), busy ? 2800 : TOAST_TTL_MS);
}

export function setBusy(message) {
  setStatus(message, {busy: true});
}

export function setError(message) {
  setStatus(message, {error: true});
}
