import {el} from './dom.js';

const STATE = {
  active: false,
  progress: 0,
  target: 0,
  rafId: 0,
  lastTick: 0
};

function clampProgress(value) {
  return Math.max(0, Math.min(100, Number(value) || 0));
}

function renderProgress() {
  if (el.generationProgressBarFill) {
    el.generationProgressBarFill.style.width = `${STATE.progress.toFixed(1)}%`;
  }
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
  const distance = STATE.target - STATE.progress;
  if (Math.abs(distance) <= 0.2) {
    STATE.progress = STATE.target;
    renderProgress();
    stopAnimation();
    return;
  }
  const easing = distance > 0 ? 0.09 : 0.18;
  const nextStep = Math.max(0.18, Math.abs(distance) * easing) * (delta
      / 16.67);
  STATE.progress += Math.sign(distance) * Math.min(Math.abs(distance),
      nextStep);
  renderProgress();
  STATE.rafId = requestAnimationFrame(tick);
}

function animateTo(targetProgress) {
  STATE.target = clampProgress(targetProgress);
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
  label = "Starting generation…"
} = {}) {
  if (!el.generationProgressOverlay) {
    return;
  }
  STATE.active = true;
  STATE.progress = 6;
  STATE.target = 18;
  renderProgress();
  if (el.generationProgressTitle) {
    el.generationProgressTitle.textContent = title;
  }
  if (el.generationProgressKicker) {
    el.generationProgressKicker.textContent = kicker;
  }
  if (el.generationProgressSubtitle) {
    el.generationProgressSubtitle.textContent = subtitle;
  }
  if (el.generationProgressLabel) {
    el.generationProgressLabel.textContent = label;
  }
  el.generationProgressOverlay.classList.remove("hidden");
  el.generationProgressOverlay.setAttribute("aria-hidden", "false");
  document.body.classList.add("modal-open");
  animateTo(18);
}

export function setGenerationProgressPhase(label, targetProgress) {
  if (!STATE.active) {
    return;
  }
  if (el.generationProgressLabel) {
    el.generationProgressLabel.textContent = label;
  }
  animateTo(targetProgress);
}

export async function completeGenerationProgress(label = "Generation complete.") {
  if (!STATE.active) {
    return;
  }
  if (el.generationProgressLabel) {
    el.generationProgressLabel.textContent = label;
  }
  animateTo(100);
  await new Promise((resolve) => window.setTimeout(resolve, 260));
}

export function hideGenerationProgress() {
  if (!el.generationProgressOverlay) {
    return;
  }
  stopAnimation();
  STATE.active = false;
  STATE.progress = 0;
  STATE.target = 0;
  renderProgress();
  el.generationProgressOverlay.classList.add("hidden");
  el.generationProgressOverlay.setAttribute("aria-hidden", "true");
  document.body.classList.remove("modal-open");
}
