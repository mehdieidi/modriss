import {animate, createTimeline, stagger, svg} from "animejs";

const stages = [
  {name: "CIM model", at: 0},
  {name: "CIM to PIM transformation", at: 0.15},
  {name: "PIM draft review", at: 0.31},
  {name: "PIM to AWS PSM", at: 0.47},
  {name: "AWS PSM review", at: 0.62},
  {name: "PSM to artifacts", at: 0.78},
  {name: "Review and deploy", at: 0.93}
];

function clamp(value, min = 0, max = 1) {
  return Math.min(max, Math.max(min, value));
}

function prefersReducedMotion() {
  return window.matchMedia("(prefers-reduced-motion: reduce)").matches;
}

function initRevealAnimations() {
  const revealTargets = [...document.querySelectorAll(".reveal")];
  if (!revealTargets.length) {
    return;
  }

  if (prefersReducedMotion()) {
    revealTargets.forEach((target) => target.classList.add("is-visible"));
    return;
  }

  const observer = new IntersectionObserver((entries) => {
    entries.forEach((entry) => {
      if (!entry.isIntersecting) {
        return;
      }
      const target = entry.target;
      observer.unobserve(target);
      animate(target, {
        opacity: [0, 1],
        y: [18, 0],
        duration: 760,
        ease: "out(3)",
        onComplete: () => target.classList.add("is-visible")
      });
    });
  }, {rootMargin: "0px 0px -12% 0px", threshold: 0.16});

  revealTargets.forEach((target) => observer.observe(target));
}

function initHeroAnimation() {
  if (prefersReducedMotion()) {
    return;
  }

  animate(".hero-title__word", {
    opacity: [0, 1],
    y: [34, 0],
    duration: 980,
    ease: "out(4)"
  });

  animate(".hero .metric", {
    opacity: [0, 1],
    y: [24, 0],
    duration: 820,
    delay: stagger(90, {start: 260}),
    ease: "out(3)"
  });

  document.querySelectorAll(".hero-node").forEach((node, index) => {
    animate(node, {
      y: [0, index % 2 === 0 ? -12 : 12, 0],
      duration: 5400 + index * 520,
      loop: true,
      ease: "inOutSine"
    });
  });

  try {
    animate(svg.createDrawable(".hero-path"), {
      draw: ["0 0", "0 1"],
      duration: 1500,
      ease: "out(3)"
    });
  } catch {
    animate(".hero-path", {
      opacity: [0.12, 0.42],
      duration: 1000,
      ease: "out(3)"
    });
  }
}

function initAmbientMachineLoops() {
  if (prefersReducedMotion()) {
    return;
  }

  animate(".gear-spokes", {
    rotate: "1turn",
    duration: 9600,
    loop: true,
    ease: "linear"
  });

  animate(".engine-ring--inner", {
    rotate: "-1turn",
    duration: 7200,
    loop: true,
    ease: "linear"
  });

  animate(".trace-pulse circle", {
    scale: [0.86, 1.08, 0.86],
    opacity: [0.18, 0.55, 0.18],
    duration: 2600,
    delay: stagger(360),
    loop: true,
    ease: "inOutSine"
  });

  animate(".stage-marker", {
    scale: [0.84, 1.1, 0.84],
    duration: 1800,
    delay: stagger(120),
    loop: true,
    ease: "inOutSine"
  });
}

function createEvolutionTimeline() {
  const timeline = createTimeline({
    autoplay: false,
    defaults: {
      duration: 1000,
      ease: "linear"
    }
  });

  timeline
  .add("#layer-cim", {opacity: [1, 0.34], scale: [1, 0.97]}, 0)
  .add("#layer-transform", {opacity: [0, 1], scale: [0.94, 1]}, 450)
  .add("#engine-core", {scale: [0.94, 1.08, 1]}, 500)
  .add(".story-path--main", {opacity: [0.28, 0.95]}, 650)
  .add("#layer-pim", {opacity: [0, 1], scale: [0.96, 1]}, 1700)
  .add("#layer-cim", {opacity: [0.34, 0.14]}, 2100)
  .add("#layer-transform", {opacity: [1, 0.58]}, 2500)
  .add("#layer-pim .assistant-bubble", {opacity: [0, 1], y: [18, 0]}, 2750)
  .add("#layer-psm", {opacity: [0, 1], scale: [0.96, 1]}, 3600)
  .add("#layer-pim", {opacity: [1, 0.32]}, 4100)
  .add(".story-path--aws", {opacity: [0.2, 0.85]}, 4300)
  .add("#layer-psm .assistant-bubble--psm", {opacity: [0, 1], y: [18, 0]}, 4600)
  .add("#layer-artifacts", {opacity: [0, 1], scale: [0.96, 1]}, 5350)
  .add("#layer-psm", {opacity: [1, 0.42]}, 5750)
  .add(".file-row", {
    opacity: [0.15, 1],
    x: [-18, 0],
    delay: stagger(58)
  }, 5900)
  .add(".deploy-ribbon", {opacity: [0, 1], y: [18, 0]}, 6500);

  timeline.seek(0, true);
  return timeline;
}

function drawMachinePaths() {
  if (prefersReducedMotion()) {
    return;
  }

  try {
    animate(svg.createDrawable(".story-path"), {
      draw: ["0 0", "0 1"],
      duration: 1500,
      delay: stagger(160),
      ease: "out(3)"
    });
  } catch {
    animate(".story-path", {
      opacity: [0.12, 0.72],
      duration: 900,
      delay: stagger(100),
      ease: "out(3)"
    });
  }
}

function stageIndexForProgress(progress) {
  let activeIndex = 0;
  stages.forEach((stage, index) => {
    if (progress >= stage.at) {
      activeIndex = index;
    }
  });
  return activeIndex;
}

function syncStageState(progress) {
  const activeIndex = stageIndexForProgress(progress);
  const activeStage = stages[activeIndex];
  const label = document.querySelector("[data-stage-label]");
  const fill = document.querySelector("[data-progress-fill]");

  if (label) {
    label.textContent = activeStage.name;
  }
  if (fill) {
    fill.style.width = `${Math.round(progress * 100)}%`;
  }

  document.querySelectorAll("[data-stage-marker]").forEach((marker) => {
    marker.classList.toggle(
        "is-active",
        Number(marker.dataset.stageMarker) <= activeIndex
    );
  });

  document.querySelectorAll(".evolution__step").forEach((step) => {
    step.classList.toggle(
        "is-active",
        Number(step.dataset.stage) === activeIndex
    );
  });
}

function initScrollScrubTimeline() {
  const section = document.querySelector(".evolution");
  if (!section) {
    return;
  }

  const timeline = createEvolutionTimeline();
  const duration = () => Number(timeline.duration) || 7200;
  let smoothProgress = 0;
  let rafId = 0;

  const measureProgress = () => {
    const rect = section.getBoundingClientRect();
    const travel = Math.max(1, rect.height - window.innerHeight);
    return clamp(-rect.top / travel);
  };

  const tick = () => {
    const nextProgress = measureProgress();
    smoothProgress += (nextProgress - smoothProgress) * 0.18;

    if (prefersReducedMotion()) {
      smoothProgress = nextProgress;
    }

    const seekTime = smoothProgress * duration();
    timeline.seek(seekTime, true);
    syncStageState(smoothProgress);

    rafId = window.requestAnimationFrame(tick);
  };

  syncStageState(0);
  rafId = window.requestAnimationFrame(tick);

  window.addEventListener("beforeunload", () => {
    if (rafId) {
      window.cancelAnimationFrame(rafId);
    }
  });
}

export function initLandingAnimations() {
  document.documentElement.classList.add("anime-ready");
  initRevealAnimations();
  initHeroAnimation();
  initAmbientMachineLoops();
  drawMachinePaths();
  initScrollScrubTimeline();
}
