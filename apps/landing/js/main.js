import { animate, createTimeline, stagger } from "https://cdn.jsdelivr.net/npm/animejs@4.4.1/+esm";
import { phases } from "./case-study.js";
import { renderCaseStudy, updateRenderedModelEdges } from "./model-renderer.js";
import { createStoryTimeline, STORY_DURATION } from "./story-timeline.js";
import { initLandingTelemetry } from "./telemetry.js";

initLandingTelemetry();

const clamp = (value, minimum = 0, maximum = 1) => Math.min(maximum, Math.max(minimum, value));
const THEME_STORAGE_KEY = "varka-theme";

function setupThemeToggle() {
  const toggle = document.querySelector("[data-theme-toggle]");
  const label = document.querySelector("[data-theme-label]");
  const root = document.documentElement;
  const normalizeTheme = (theme) => (theme === "light" || theme === "dark" ? theme : "dark");

  const applyTheme = (theme) => {
    const normalizedTheme = normalizeTheme(theme);
    root.dataset.theme = normalizedTheme;
    if (label) {
      label.textContent = normalizedTheme === "dark" ? "Dark" : "Light";
    }
    if (toggle) {
      toggle.setAttribute(
        "aria-label",
        `Switch to ${normalizedTheme === "dark" ? "light" : "dark"} theme`,
      );
      toggle.setAttribute("aria-pressed", String(normalizedTheme === "light"));
    }
  };

  applyTheme(root.dataset.theme);

  if (!toggle) {
    return;
  }

  toggle.addEventListener("click", () => {
    const nextTheme = root.dataset.theme === "dark" ? "light" : "dark";
    applyTheme(nextTheme);
    try {
      localStorage.setItem(THEME_STORAGE_KEY, nextTheme);
    } catch {
      // Theme still changes for the current page when storage is unavailable.
    }
  });
}

function prepareHero() {
  const revealTargets = document.querySelectorAll(".hero-reveal");
  revealTargets.forEach((target) => {
    target.style.opacity = "1";
    target.style.transform = "none";
  });

  animate(".hero-copy .hero-reveal", {
    opacity: { from: 0 },
    y: { from: 24 },
    duration: 850,
    delay: stagger(90),
    ease: "out(4)",
  });
}

function createHeroScrollTimeline() {
  return createTimeline({
    autoplay: false,
    defaults: {
      duration: 1000,
      ease: "inOut(3)",
    },
  })
    .add(".site-nav", { opacity: 0, y: -28 }, 0)
    .add(".hero-copy", { opacity: 0, y: -52, scale: 0.97 }, 0)
    .add(".method-strip", { opacity: 0, y: 45 }, 0);
}

function startAmbientAnimations() {
  if (window.matchMedia("(prefers-reduced-motion: reduce)").matches) {
    return;
  }

  animate(".chatbot-antenna span", {
    scale: [0.7, 1.35],
    opacity: [0.55, 1],
    duration: 900,
    alternate: true,
    loop: true,
    ease: "inOut(3)",
  });
  animate(".deploy-ready-icon span", {
    rotate: "1turn",
    duration: 9000,
    loop: true,
    ease: "linear",
  });
}

function activePhaseFor(progress) {
  return phases.reduce((active, phase) => (progress >= phase.start ? phase : active), phases[0]);
}

function updateStoryMetadata(progress) {
  const activePhase = activePhaseFor(progress);
  const fill = document.querySelector(".story-progress-fill");
  const label = document.querySelector(".story-progress-phase");
  const count = document.querySelector(".story-progress-count");
  const modelCanvas = document.querySelector(".model-canvas");
  const workbenchBody = document.querySelector(".workbench-body");
  const engineLabel = document.querySelector(".engine-label");

  fill.style.transform = `translateX(${progress * 100 - 100}%)`;
  label.textContent = activePhase.label;
  count.textContent = activePhase.count;
  if (engineLabel) {
    engineLabel.textContent = "transformation engine";
  }

  document.querySelectorAll(".level-tab").forEach((tab) => {
    tab.classList.toggle("active", tab.dataset.level === activePhase.level);
  });
  const assistantActive = progress >= 0.26 && progress < 0.39;
  modelCanvas.classList.toggle("assistant-active", assistantActive);
  workbenchBody.classList.toggle("assistant-active", assistantActive);

  const refinedNode = document.querySelector(".manual-target");
  if (refinedNode) {
    refinedNode.classList.toggle("is-refined", progress >= 0.63);
    refinedNode.classList.toggle("is-selected", progress >= 0.585 && progress < 0.665);
  }
}

function setupScrollScrubbing(storyTimeline, heroTimeline) {
  const hero = document.querySelector(".hero");
  const story = document.querySelector(".story");
  let ticking = false;

  const update = () => {
    const scrollTop = window.scrollY;
    const heroStart = hero.offsetHeight * 0.12;
    const heroDistance = hero.offsetHeight * 0.72;
    const heroProgress = clamp((scrollTop - heroStart) / heroDistance);
    heroTimeline.seek(heroProgress * 1000, true);

    const storyStart = story.offsetTop;
    const storyDistance = Math.max(1, story.offsetHeight - window.innerHeight);
    const storyProgress = clamp((scrollTop - storyStart) / storyDistance);
    storyTimeline.seek(storyProgress * STORY_DURATION, true);
    updateStoryMetadata(storyProgress);
    updateRenderedModelEdges();
    ticking = false;
  };

  const requestUpdate = () => {
    if (!ticking) {
      window.requestAnimationFrame(update);
      ticking = true;
    }
  };

  window.addEventListener("scroll", requestUpdate, { passive: true });
  window.addEventListener("resize", requestUpdate, { passive: true });
  requestUpdate();
}

function initialize() {
  setupThemeToggle();
  renderCaseStudy();
  prepareHero();
  const heroTimeline = createHeroScrollTimeline();
  const storyTimeline = createStoryTimeline();
  setupScrollScrubbing(storyTimeline, heroTimeline);
  startAmbientAnimations();
}

initialize();
