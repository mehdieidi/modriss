import {
  createTimeline,
  stagger,
} from "https://cdn.jsdelivr.net/npm/animejs@4.4.1/+esm";

export const STORY_DURATION = 16500;

function selector(value) {
  return document.querySelectorAll(value);
}

function addCopyTransition(timeline, phase, showAt, hideAt) {
  const target = `.story-copy-card[data-phase="${phase}"]`;
  timeline.add(
      target,
      {
        opacity: 1,
        y: 0,
        duration: 360,
        ease: "out(3)",
      },
      showAt,
  );
  if (hideAt) {
    timeline.add(
        target,
        {
          opacity: 0,
          y: -18,
          duration: 280,
          ease: "in(3)",
        },
        hideAt,
    );
  }
}

function addEngineRun(timeline, start, end) {
  const duration = end - start;
  timeline
  .add(
      "#transform-engine, #pipeline-rails",
      {
        opacity: 1,
        scale: 0.88,
        duration: 420,
        ease: "out(4)",
      },
      start,
  )
  .add(
      ".engine-gear.gear-left",
      {
        rotate: "3turn",
        duration,
        ease: "linear",
      },
      start,
  )
  .add(
      ".engine-gear.gear-right",
      {
        rotate: "-3turn",
        duration,
        ease: "linear",
      },
      start,
  )
  .add(
      ".engine-piston.piston-one",
      {
        y: [
          {to: -8, duration: duration * 0.2},
          {to: 8, duration: duration * 0.2},
          {to: -8, duration: duration * 0.2},
          {to: 8, duration: duration * 0.2},
          {to: 0, duration: duration * 0.2},
        ],
        ease: "inOut(2)",
      },
      start,
  )
  .add(
      ".engine-piston.piston-two",
      {
        y: [
          {to: 8, duration: duration * 0.2},
          {to: -8, duration: duration * 0.2},
          {to: 8, duration: duration * 0.2},
          {to: -8, duration: duration * 0.2},
          {to: 0, duration: duration * 0.2},
        ],
        ease: "inOut(2)",
      },
      start,
  )
  .add(
      ".engine-pulse",
      {
        scaleX: [
          {to: 0.35, duration: duration * 0.2},
          {to: 1.2, duration: duration * 0.2},
          {to: 0.45, duration: duration * 0.2},
          {to: 1.3, duration: duration * 0.2},
          {to: 0.6, duration: duration * 0.2},
        ],
        opacity: [
          {to: 0.4, duration: duration * 0.2},
          {to: 1, duration: duration * 0.2},
          {to: 0.5, duration: duration * 0.2},
          {to: 1, duration: duration * 0.2},
          {to: 0.6, duration: duration * 0.2},
        ],
        ease: "inOut(2)",
      },
      start,
  );
}

function addRuleTokens(timeline, batch, start, hideAt) {
  const targets = `.mapping-token[data-batch="${batch}"]`;
  timeline
  .add(
      targets,
      {
        opacity: 1,
        scale: 1,
        duration: 380,
        delay: stagger(90),
        ease: "out(4)",
      },
      start,
  )
  .add(
      targets,
      {
        opacity: 0,
        scale: 0.82,
        duration: 260,
        delay: stagger(45),
        ease: "in(3)",
      },
      hideAt,
  );
}

function addFlowTokens(timeline, batch, start, stepGap = 250) {
  const inputs = selector(`.flow-token-input[data-batch="${batch}"]`);
  const outputs = selector(`.flow-token-output[data-batch="${batch}"]`);
  const stepCount = Math.max(inputs.length, outputs.length);

  for (let index = 0; index < stepCount; index += 1) {
    const stepStart = start + index * stepGap;
    const input = inputs[index];
    const output = outputs[index];

    if (input) {
      timeline
      .add(
          input,
          {
            opacity: 1,
            left: "42%",
            scale: 1,
            duration: 300,
            ease: "inOut(3)",
          },
          stepStart,
      )
      .add(
          input,
          {
            opacity: 0,
            left: "47%",
            scale: 0.38,
            duration: 150,
            ease: "in(4)",
          },
          stepStart + 300,
      );
    }

    if (output) {
      timeline
      .add(
          output,
          {
            opacity: 1,
            left: "66%",
            scale: 1,
            duration: 280,
            ease: "out(4)",
          },
          stepStart + 340,
      )
      .add(
          output,
          {
            opacity: 0,
            left: "88%",
            duration: 310,
            ease: "inOut(3)",
          },
          stepStart + 620,
      );
    }
  }
}

function addModelTransformation({
  timeline,
  source,
  target,
  batch,
  start,
  finish,
  targetNodeSelector,
  targetEdgeSelector,
}) {
  const sourceNodes = selector(`#model-${source} .model-node`);
  const sourceEdges = selector(
      `#model-${source} .model-edge, #model-${source} .edge-label`,
  );
  const targetNodes = selector(
      targetNodeSelector || `#model-${target} .model-node`,
  );
  const targetEdges = selector(
      targetEdgeSelector
      || `#model-${target} .model-edge, #model-${target} .edge-label`,
  );
  const stepCount = Math.max(sourceNodes.length, targetNodes.length);
  const stepGap = Math.max(150, (finish - start - 930) / stepCount);

  timeline
  .add(
      `#model-${target}`,
      {
        opacity: 1,
        duration: 1,
        ease: "out(2)",
      },
      start,
  )
  .add(
      `#model-${source}`,
      {
        opacity: 0,
        duration: 260,
        ease: "in(2)",
      },
      finish - 470,
  )
  .add(
      "#transform-engine, #pipeline-rails",
      {
        opacity: 0,
        scale: 0.72,
        duration: 350,
        ease: "in(3)",
      },
      finish - 470,
  );

  sourceEdges.forEach((edge, index) => {
    timeline.add(
        edge,
        {
          opacity: 0,
          duration: 220,
          ease: "in(2)",
        },
        start + 220 + index * Math.max(38, stepGap * 0.34),
    );
  });

  for (let index = 0; index < stepCount; index += 1) {
    const stepStart = start + 330 + index * stepGap;
    const sourceNode = sourceNodes[index];
    const targetNode = targetNodes[index];

    if (sourceNode) {
      timeline.add(
          sourceNode,
          {
            opacity: 0,
            x: "9vw",
            scale: 0.42,
            duration: 360,
            ease: "in(4)",
          },
          stepStart,
      );
    }

    if (targetNode) {
      timeline.add(
          targetNode,
          {
            opacity: 1,
            x: 0,
            scale: 1,
            duration: 430,
            ease: "out(4)",
          },
          stepStart + 350,
      );
    }
  }

  targetEdges.forEach((edge, index) => {
    timeline.add(
        edge,
        {
          opacity: 0.82,
          duration: 260,
          ease: "out(2)",
        },
        start + 760 + index * Math.max(42, stepGap * 0.38),
    );
  });

  addEngineRun(timeline, start + 80, finish - 420);
  addRuleTokens(timeline, batch, start + 260, finish - 520);
  addFlowTokens(timeline, batch, start + 350, stepGap);
}

export function createStoryTimeline() {
  const timeline = createTimeline({
    autoplay: false,
    defaults: {
      ease: "inOut(3)",
    },
  });

  addCopyTransition(timeline, "cim", 0, 1700);
  addCopyTransition(timeline, "cim-pim", 1760, 4300);
  addCopyTransition(timeline, "assistant", 4380, 6400);
  addCopyTransition(timeline, "pim-psm", 6480, 9400);
  addCopyTransition(timeline, "refine", 9480, 11050);
  addCopyTransition(timeline, "generate", 11120, 13400);
  addCopyTransition(timeline, "protected", 13480, 15100);
  addCopyTransition(timeline, "ready", 15180);

  timeline
  .add(
      "#model-cim .model-node",
      {
        opacity: 1,
        y: 0,
        duration: 520,
        delay: stagger(100),
        ease: "out(4)",
      },
      140,
  )
  .add(
      "#model-cim .model-edge, #model-cim .edge-label",
      {
        opacity: 0.8,
        duration: 360,
        delay: stagger(55),
        ease: "out(2)",
      },
      720,
  );

  addModelTransformation({
    timeline,
    source: "cim",
    target: "pim",
    batch: "cim-pim",
    start: 1850,
    finish: 4200,
    targetNodeSelector: "#model-pim .model-node:not(.ai-addition)",
    targetEdgeSelector:
        "#model-pim .model-edge:not(.ai-addition-edge), #model-pim .edge-label:not(.ai-addition-edge)",
  });

  timeline
  .add(
      "#model-pim .ai-addition-edge",
      {
        opacity: 0,
        duration: 1,
      },
      4150,
  )
  .add(
      "#assistant-demo",
      {
        opacity: 1,
        y: 0,
        duration: 440,
        ease: "out(4)",
      },
      4650,
  )
  .add(
      ".chat-window",
      {
        scale: 1,
        duration: 350,
        ease: "out(4)",
      },
      4720,
  )
  .add(
      ".chat-user",
      {
        opacity: 1,
        y: 0,
        duration: 340,
        ease: "out(3)",
      },
      4900,
  )
  .add(
      ".chat-bot",
      {
        opacity: 1,
        y: 0,
        duration: 380,
        ease: "out(3)",
      },
      5260,
  )
  .add(
      ".chat-action",
      {
        opacity: 1,
        duration: 280,
        ease: "out(2)",
      },
      5550,
  )
  .add(
      "#model-pim .ai-addition",
      {
        opacity: 1,
        x: 0,
        scale: [
          {to: 1.08, duration: 300},
          {to: 1, duration: 280},
        ],
        duration: 580,
        delay: stagger(160),
        ease: "out(4)",
      },
      5530,
  )
  .add(
      "#model-pim .ai-addition-edge",
      {
        opacity: 0.85,
        duration: 420,
        delay: stagger(100),
        ease: "out(2)",
      },
      5850,
  )
  .add(
      "#assistant-demo",
      {
        opacity: 0,
        y: 20,
        duration: 320,
        ease: "in(3)",
      },
      6250,
  );

  addModelTransformation({
    timeline,
    source: "pim",
    target: "psm",
    batch: "pim-psm",
    start: 6550,
    finish: 9180,
  });

  timeline
  .add(
      "#transform-engine, #pipeline-rails",
      {
        opacity: 0,
        scale: 0.72,
        duration: 1,
      },
      9300,
  )
  .add(
      "#model-psm",
      {
        opacity: 1,
        duration: 520,
        ease: "out(4)",
      },
      9280,
  )
  .add(
      "#manual-refinement",
      {
        opacity: 1,
        duration: 250,
        ease: "out(2)",
      },
      9580,
  )
  .add(
      ".model-cursor",
      {
        x: "-4vw",
        y: "-19vh",
        duration: 680,
        ease: "inOut(4)",
      },
      9700,
  )
  .add(
      ".refinement-inspector",
      {
        x: 0,
        duration: 520,
        ease: "out(4)",
      },
      9850,
  )
  .add(
      ".manual-target",
      {
        scale: [
          {to: 1.08, duration: 240},
          {to: 1, duration: 320},
        ],
        borderColor: "#b7ff54",
        duration: 560,
        ease: "out(4)",
      },
      10000,
  )
  .add(
      ".manual-target .node-badge",
      {
        opacity: 1,
        duration: 260,
        ease: "out(2)",
      },
      10220,
  )
  .add(
      ".value-before",
      {
        opacity: 0,
        duration: 220,
      },
      10260,
  )
  .add(
      ".value-after",
      {
        opacity: 1,
        duration: 260,
        ease: "out(3)",
      },
      10300,
  )
  .add(
      ".toggle i",
      {
        x: 13,
        backgroundColor: "#b7ff54",
        duration: 300,
        ease: "out(4)",
      },
      10380,
  )
  .add(
      ".inspector-save",
      {
        opacity: 1,
        duration: 300,
        ease: "out(3)",
      },
      10600,
  )
  .add(
      "#manual-refinement",
      {
        opacity: 0,
        duration: 300,
        ease: "in(3)",
      },
      10950,
  );

  timeline
  .add(
      "#model-psm .model-edge, #model-psm .edge-label",
      {
        opacity: 0,
        duration: 300,
        delay: stagger(35),
        ease: "in(2)",
      },
      11520,
  )
  .add(
      "#model-psm .model-node",
      {
        opacity: 0,
        x: "9vw",
        scale: 0.42,
        duration: 520,
        delay: stagger(150),
        ease: "in(4)",
      },
      11480,
  )
  .add(
      ".artifact-tree-panel",
      {
        opacity: 1,
        x: 0,
        duration: 520,
        ease: "out(4)",
      },
      11800,
  )
  .add(
      ".tree-row",
      {
        opacity: 1,
        x: 0,
        duration: 300,
        delay: stagger(52),
        ease: "out(3)",
      },
      11980,
  )
  .add(
      ".generation-summary",
      {
        opacity: 1,
        duration: 280,
        ease: "out(2)",
      },
      12700,
  )
  .add(
      "#model-psm, #transform-engine, #pipeline-rails",
      {
        opacity: 0,
        duration: 360,
        ease: "in(3)",
      },
      12940,
  )
  .add(
      ".artifact-tree-panel",
      {
        x: "-48vw",
        duration: 700,
        ease: "inOut(4)",
      },
      13020,
  )
  .add(
      ".code-panel",
      {
        opacity: 1,
        x: 0,
        duration: 620,
        ease: "out(4)",
      },
      13100,
  )
  .add(
      ".code-line:not(.manual-code)",
      {
        opacity: 1,
        y: 0,
        duration: 240,
        delay: stagger(36),
        ease: "out(2)",
      },
      13320,
  )
  .add(
      ".todo-code",
      {
        opacity: 0,
        x: 18,
        duration: 260,
        delay: stagger(70),
        ease: "in(3)",
      },
      14100,
  )
  .add(
      ".manual-code",
      {
        opacity: 1,
        y: 0,
        duration: 280,
        delay: stagger(90),
        ease: "out(3)",
      },
      14300,
  )
  .add(
      ".code-line.protected",
      {
        backgroundColor: [
          {to: "rgba(183,255,84,.12)", duration: 320},
          {to: "rgba(183,255,84,.04)", duration: 420},
        ],
        duration: 740,
        ease: "inOut(2)",
      },
      14500,
  )
  .add(
      ".artifact-tree-panel, .code-panel",
      {
        opacity: 0.18,
        scale: 0.96,
        duration: 500,
        ease: "inOut(3)",
      },
      15200,
  )
  .add(
      ".deploy-ready",
      {
        opacity: 1,
        scale: 1,
        y: "-4%",
        duration: 620,
        ease: "out(5)",
      },
      15300,
  )
  .add(
      {
        duration: 600,
      },
      STORY_DURATION - 600,
  );

  addEngineRun(timeline, 11280, 12940);
  addRuleTokens(timeline, "psm-artifacts", 11420, 12820);
  addFlowTokens(timeline, "psm-artifacts", 11520, 260);
  timeline.add(
      "#transform-engine, #pipeline-rails",
      {
        opacity: 0,
        scale: 0.72,
        duration: 320,
        ease: "in(3)",
      },
      12950,
  );

  timeline.seek(0, true);
  return timeline;
}

export function collectStoryTargets() {
  return {
    codeLines: selector(".code-line"),
    modelNodes: selector(".model-node"),
  };
}
