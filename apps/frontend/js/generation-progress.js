import {
  hideProgressNotification,
  showProgressNotification,
  updateProgressNotification,
} from "./status.js";

const STATE = {
  active: false,
  progress: 0,
  target: 0,
  velocity: 0,
  rafId: 0,
  lastTick: 0,
  startedAt: 0,
  trickleLimit: 0,
};

function clampProgress(value) {
  return Math.max(0, Math.min(100, Number(value) || 0));
}

function renderProgress() {
  updateProgressNotification({ progress: STATE.progress });
}

function stopAnimation() {
  if (STATE.rafId) {
    cancelAnimationFrame(STATE.rafId);
    STATE.rafId = 0;
  }
  STATE.lastTick = 0;
}

function tick(timestamp) {
  if (!STATE.active) {
    stopAnimation();
    return;
  }
  if (!STATE.lastTick) {
    STATE.lastTick = timestamp;
  }
  const delta = timestamp - STATE.lastTick;
  STATE.lastTick = timestamp;
  const elapsed = timestamp - STATE.startedAt;
  if (STATE.target < STATE.trickleLimit && elapsed > 320) {
    const trickle = Math.min(
      STATE.trickleLimit,
      STATE.target +
        Math.max(0.008, (STATE.trickleLimit - STATE.target) * 0.0025 * (delta / 16.67)),
    );
    STATE.target = trickle;
  }
  const distance = STATE.target - STATE.progress;
  if (Math.abs(distance) <= 0.08 && Math.abs(STATE.velocity) < 0.03) {
    STATE.progress = STATE.target;
    STATE.velocity = 0;
    renderProgress();
    if (STATE.progress >= 100 || STATE.target >= STATE.trickleLimit) {
      stopAnimation();
      return;
    }
  }
  const seconds = Math.min(0.08, Math.max(0.001, delta / 1000));
  const stiffness = distance > 0 ? 38 : 54;
  const damping = distance > 0 ? 10 : 14;
  STATE.velocity += distance * stiffness * seconds;
  STATE.velocity *= Math.exp(-damping * seconds);
  const maxStep = Math.max(0.18, Math.abs(distance) * 0.34);
  const step = Math.max(-maxStep, Math.min(maxStep, STATE.velocity * seconds));
  STATE.progress = clampProgress(STATE.progress + step);
  if (distance > 0 && STATE.progress > STATE.target) {
    STATE.progress = STATE.target;
    STATE.velocity = 0;
  } else if (distance < 0 && STATE.progress < STATE.target) {
    STATE.progress = STATE.target;
    STATE.velocity = 0;
  }
  renderProgress();
  STATE.rafId = requestAnimationFrame(tick);
}

function animateTo(targetProgress) {
  STATE.target = Math.max(STATE.target, clampProgress(targetProgress));
  if (!STATE.active) {
    return;
  }
  if (!STATE.rafId) {
    STATE.rafId = requestAnimationFrame(tick);
  }
}

export function showGenerationProgress({
  kicker = "Generation in Progress",
  title = "Generating model",
  subtitle = "Preparing the next step in your modeling flow.",
  label = "Starting generation...",
} = {}) {
  STATE.active = true;
  STATE.progress = 6;
  STATE.target = 18;
  STATE.velocity = 0;
  STATE.startedAt = performance.now();
  STATE.trickleLimit = 88;
  showProgressNotification({
    kicker,
    title,
    subtitle,
    label,
    progress: STATE.progress,
  });
  animateTo(18);
}

export function setGenerationProgressPhase(label, targetProgress) {
  if (!STATE.active) {
    return;
  }
  updateProgressNotification({ label });
  animateTo(targetProgress);
}

export async function waitForGenerationProgress(minimumProgress, { timeoutMs = 700 } = {}) {
  if (!STATE.active || typeof window.requestAnimationFrame !== "function") {
    return;
  }
  const target = clampProgress(minimumProgress);
  const started = performance.now();
  while (STATE.active && STATE.progress < target && performance.now() - started < timeoutMs) {
    await new Promise((resolve) => window.requestAnimationFrame(resolve));
  }
}

export async function completeGenerationProgress(label = "Generation complete.") {
  if (!STATE.active) {
    return;
  }
  updateProgressNotification({ label });
  STATE.target = 100;
  STATE.progress = 100;
  STATE.velocity = 0;
  renderProgress();
  if (typeof window.requestAnimationFrame === "function") {
    await new Promise((resolve) => window.requestAnimationFrame(resolve));
  }
}

export function hideGenerationProgress() {
  stopAnimation();
  STATE.active = false;
  STATE.progress = 0;
  STATE.target = 0;
  STATE.velocity = 0;
  STATE.trickleLimit = 0;
  hideProgressNotification();
}
