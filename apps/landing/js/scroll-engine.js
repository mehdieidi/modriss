import {animate, createTimeline, stagger, svg} from "animejs";

const stages = [
  {name: "CIM model", at: 0},
  {name: "CIM manual refinement", at: 0.12},
  {name: "CIM to PIM transformation", at: 0.25},
  {name: "PIM AI-assisted refinement", at: 0.38},
  {name: "PIM manual refinement", at: 0.51},
  {name: "PIM to AWS PSM transformation", at: 0.64},
  {name: "AWS PSM review", at: 0.77},
  {name: "Artifacts and code refinement", at: 0.9}
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

  animate(".hero-node, .hero-transform, .hero-chatbot, .hero-code", {
    opacity: [0, 1],
    y: [18, 0],
    duration: 900,
    delay: stagger(95, {start: 180}),
    ease: "out(4)"
  });

  animate(".hero-chatbot circle", {
    scale: [0.84, 1.12, 0.84],
    duration: 2200,
    loop: true,
    ease: "inOutSine"
  });

  try {
    animate(svg.createDrawable(".hero-path"), {
      draw: ["0 0", "0 1"],
      duration: 1600,
      delay: stagger(160),
      ease: "out(3)"
    });
  } catch {
    animate(".hero-path", {
      opacity: [0.12, 0.52],
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

  animate(".assistant-edit-edge, .resource-map-path, .refine-edge", {
    strokeDashoffset: [40, 0],
    duration: 1900,
    loop: true,
    ease: "linear"
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
  .add("#layer-cim", {opacity: [1, 1], scale: [1, 1]}, 0)
  .add("#layer-cim-refine", {opacity: [0, 1], scale: [0.96, 1]}, 860)
  .add(".manual-note--cim, .cursor-hand", {
    opacity: [0, 1],
    y: [20, 0],
    delay: stagger(80)
  }, 980)
  .add("#layer-transform", {opacity: [0, 1], scale: [0.94, 1]}, 1820)
  .add("#engine-core", {scale: [0.94, 1.08, 1]}, 1880)
  .add("#layer-cim", {opacity: [1, 0.34], scale: [1, 0.97]}, 2060)
  .add(".story-path--main", {opacity: [0.28, 0.95]}, 2240)
  .add("#layer-pim", {opacity: [0, 1], scale: [0.96, 1]}, 2920)
  .add("#layer-cim-refine", {opacity: [1, 0.34]}, 3200)
  .add("#layer-pim-assistant", {opacity: [0, 1], scale: [0.96, 1]}, 3800)
  .add(".chat-line", {
    opacity: [0, 1],
    x: [-18, 0],
    delay: stagger(160)
  }, 3980)
  .add(".live-edit", {
    opacity: [0, 1],
    y: [18, 0],
    delay: stagger(130)
  }, 4300)
  .add("#layer-pim-refine", {opacity: [0, 1], scale: [0.97, 1]}, 4920)
  .add(".validation-badge", {opacity: [0, 1], y: [16, 0]}, 5200)
  .add("#layer-transform-psm", {opacity: [0, 1], scale: [0.96, 1]}, 5900)
  .add("#layer-pim-assistant", {opacity: [1, 0.38]}, 6120)
  .add("#layer-psm", {opacity: [0, 1], scale: [0.96, 1]}, 6700)
  .add("#layer-pim", {opacity: [1, 0.32]}, 7040)
  .add(".story-path--aws", {opacity: [0.2, 0.85]}, 7200)
  .add("#layer-psm-refine", {opacity: [0, 1], scale: [0.96, 1]}, 7700)
  .add("#layer-psm .assistant-bubble--psm", {opacity: [0, 1], y: [18, 0]}, 7900)
  .add("#layer-artifacts", {opacity: [0, 1], scale: [0.96, 1]}, 8420)
  .add("#layer-psm", {opacity: [1, 0.42]}, 8660)
  .add(".file-row", {
    opacity: [0.15, 1],
    x: [-18, 0],
    delay: stagger(58)
  }, 8840)
  .add(".code-editor", {opacity: [0, 1], x: [24, 0]}, 9140)
  .add(".deploy-ribbon", {opacity: [0, 1], y: [18, 0]}, 9480);

  timeline.seek(0, true);
  return timeline;
}

function drawMachinePaths() {
  if (prefersReducedMotion()) {
    return;
  }

  try {
    animate(svg.createDrawable(
            ".story-path, .refine-edge, .assistant-edit-edge, .resource-map-path"),
        {
          draw: ["0 0", "0 1"],
          duration: 1500,
          delay: stagger(90),
          ease: "out(3)"
        });
  } catch {
    animate(
        ".story-path, .refine-edge, .assistant-edit-edge, .resource-map-path", {
          opacity: [0.12, 0.72],
          duration: 900,
          delay: stagger(80),
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
  const duration = () => Number(timeline.duration) || 9800;
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
