import { createTimeline, stagger } from "https://cdn.jsdelivr.net/npm/animejs@4.4.1/+esm";

export const STORY_DURATION = 16500;
const TRANSFORM_ENGINE_SELECTOR = "#transform-engine";

function selector(value) {
  return document.querySelectorAll(value);
}

function setEngineLabel(label) {
  const engineLabel = document.querySelector(".engine-window strong");
  if (engineLabel) {
    engineLabel.textContent = label;
  }
}

function centerInCanvas(element) {
  const canvasRect = document.querySelector(".model-canvas").getBoundingClientRect();
  const rect = element.getBoundingClientRect();
  return {
    x: rect.left - canvasRect.left + rect.width / 2,
    y: rect.top - canvasRect.top + rect.height / 2,
  };
}

function engineCenter() {
  return centerInCanvas(document.querySelector(TRANSFORM_ENGINE_SELECTOR));
}

function nodeLabel(node) {
  return (
    node.querySelector(".model-node-title strong")?.textContent || node.dataset.nodeType || "model"
  );
}

function nodeColor(node) {
  return node.style.getPropertyValue("--node-color") || "var(--accent)";
}

function clearTravelers() {
  document.querySelector("#transformation-travelers")?.replaceChildren();
}

function createTraveler(label, color, className) {
  const traveler = document.createElement("span");
  traveler.className = `traveler-node ${className}`;
  traveler.textContent = label;
  traveler.style.setProperty("--traveler-color", color);
  document.querySelector("#transformation-travelers").append(traveler);
  return traveler;
}

function createModelTravelers(sourceNodes, targetNodes) {
  clearTravelers();
  sourceNodes.forEach((node, index) => {
    const traveler = createTraveler(nodeLabel(node), nodeColor(node), "traveler-source");
    traveler.dataset.index = String(index);
  });
  targetNodes.forEach((node, index) => {
    const traveler = createTraveler(nodeLabel(node), nodeColor(node), "traveler-target");
    traveler.dataset.index = String(index);
  });
}

function placeModelTravelers(sourceNodes, _targetNodes) {
  const engine = engineCenter();
  selector(".traveler-source").forEach((traveler, index) => {
    const source = sourceNodes[index];
    const point = source ? centerInCanvas(source) : engine;
    traveler.style.left = `${point.x}px`;
    traveler.style.top = `${point.y}px`;
  });
  selector(".traveler-target").forEach((traveler) => {
    traveler.style.left = `${engine.x}px`;
    traveler.style.top = `${engine.y}px`;
  });
}

function createArtifactTravelers(sourceNodes, targets) {
  clearTravelers();
  sourceNodes.forEach((node, index) => {
    const traveler = createTraveler(nodeLabel(node), nodeColor(node), "traveler-source");
    traveler.dataset.index = String(index);
  });
  targets.forEach(([label, color], index) => {
    const traveler = createTraveler(label, color, "traveler-target");
    traveler.dataset.index = String(index);
  });
}

function artifactTargetPoint(index) {
  const rows = selector(".tree-row");
  const fallback = document.querySelector(".artifact-tree-panel");
  return centerInCanvas(rows[index] || fallback);
}

function placeArtifactTravelers(sourceNodes) {
  const engine = engineCenter();
  selector(".traveler-source").forEach((traveler, index) => {
    const source = sourceNodes[index];
    const point = source ? centerInCanvas(source) : engine;
    traveler.style.left = `${point.x}px`;
    traveler.style.top = `${point.y}px`;
  });
  selector(".traveler-target").forEach((traveler) => {
    traveler.style.left = `${engine.x}px`;
    traveler.style.top = `${engine.y}px`;
  });
}

function addEngineRun(timeline, start, _end) {
  timeline.add(
    TRANSFORM_ENGINE_SELECTOR,
    {
      opacity: 1,
      scale: 1,
      duration: 420,
      ease: "out(4)",
    },
    start,
  );
}

function _addRuleTokens(timeline, batch, start, hideAt) {
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

function _addFlowTokens(timeline, batch, start, stepGap = 250) {
  const inputs = selector(`.flow-token-input[data-batch="${batch}"]`);
  const outputs = selector(`.flow-token-output[data-batch="${batch}"]`);
  const stepCount = Math.max(inputs.length, outputs.length);

  for (let index = 0; index < stepCount; index += 1) {
    const stepStart = start + index * stepGap;
    const input = inputs[index];
    const output = outputs[index];
    const laneOffset = ((index % 3) - 1) * 7;
    const tokenTop = `${50 + laneOffset}%`;

    if (input) {
      timeline
        .add(
          input,
          {
            opacity: 1,
            left: "32%",
            top: tokenTop,
            scale: 0.72,
            duration: 300,
            ease: "inOut(3)",
          },
          stepStart,
        )
        .add(
          input,
          {
            opacity: 0,
            left: "50%",
            scale: 0.24,
            duration: 230,
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
            left: "60%",
            top: tokenTop,
            scale: 0.72,
            duration: 280,
            ease: "out(4)",
          },
          stepStart + 340,
        )
        .add(
          output,
          {
            opacity: 0,
            left: "74%",
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
  batch: _batch,
  start,
  finish,
  engineLabel = "ETL",
  targetNodeSelector,
  targetEdgeSelector,
}) {
  const sourceNodes = selector(`#model-${source} .model-node`);
  const sourceEdges = selector(`#model-${source} .model-edge, #model-${source} .edge-label`);
  const targetNodes = selector(targetNodeSelector || `#model-${target} .model-node`);
  const targetEdges = selector(
    targetEdgeSelector || `#model-${target} .model-edge, #model-${target} .edge-label`,
  );
  const stepCount = Math.max(sourceNodes.length, targetNodes.length);
  const stepGap = Math.max(105, (finish - start - 1400) / stepCount);
  const feedStart = start + 620;
  const targetNodeIndexes = new Map(
    [...targetNodes].map((node, index) => [node.dataset.nodeId, index]),
  );

  timeline
    .add(
      {},
      {
        duration: 1,
        onBegin: () => {
          setEngineLabel(engineLabel);
          createModelTravelers(sourceNodes, targetNodes);
        },
      },
      start,
    )
    .add(
      `#model-${source}`,
      {
        opacity: 0.84,
        x: "-34%",
        y: 0,
        scale: 0.38,
        duration: 460,
        ease: "out(4)",
      },
      start,
    )
    .add(
      `#model-${target}`,
      {
        opacity: 1,
        x: "34%",
        y: 0,
        scale: 0.38,
        duration: 1,
        ease: "out(2)",
      },
      start,
    )
    .add(
      targetNodes,
      {
        opacity: 0,
        x: 0,
        y: 0,
        scale: 0.18,
        duration: 1,
      },
      start,
    )
    .add(
      {},
      {
        duration: 1,
        onBegin: () => placeModelTravelers(sourceNodes, targetNodes),
      },
      start + 470,
    )
    .add(
      `#model-${source}`,
      {
        opacity: 0,
        x: "-39%",
        scale: 0.32,
        duration: 320,
        ease: "in(2)",
      },
      finish - 700,
    )
    .add(
      TRANSFORM_ENGINE_SELECTOR,
      {
        opacity: 0,
        scale: 0.72,
        duration: 350,
        ease: "in(3)",
      },
      finish - 450,
    )
    .add(
      targetEdges,
      {
        opacity: 0,
        duration: 1,
      },
      start,
    )
    .add(
      `#model-${target}`,
      {
        x: 0,
        y: 0,
        scale: 1,
        opacity: 1,
        duration: 520,
        ease: "out(4)",
      },
      finish - 360,
    )
    .add(
      targetNodes,
      {
        x: 0,
        y: 0,
        scale: 1,
        opacity: 1,
        duration: 500,
        delay: stagger(32),
        ease: "out(4)",
      },
      finish - 350,
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
    const stepStart = feedStart + index * stepGap;
    const sourceNode = sourceNodes[index];
    const targetNode = targetNodes[index];
    const sourceTraveler = `.traveler-source[data-index="${index}"]`;
    const targetTraveler = `.traveler-target[data-index="${index}"]`;

    if (sourceNode) {
      const engine = engineCenter;
      timeline.add(
        sourceTraveler,
        {
          opacity: [
            { to: 1, duration: 60 },
            { to: 1, duration: 180 },
            { to: 0, duration: 80 },
          ],
          left: () => `${engine().x}px`,
          top: () => `${engine().y}px`,
          scale: 0.38,
          duration: 380,
          ease: "in(4)",
        },
        stepStart,
      );
      timeline.add(
        sourceNode,
        {
          opacity: 0.12,
          scale: 0.76,
          duration: 260,
          ease: "in(3)",
        },
        stepStart + 110,
      );
    }

    if (targetNode) {
      const targetPoint = () => centerInCanvas(targetNode);
      timeline.add(
        targetTraveler,
        {
          opacity: 1,
          left: () => `${targetPoint().x}px`,
          top: () => `${targetPoint().y}px`,
          scale: 1,
          duration: 420,
          ease: "out(4)",
        },
        stepStart + 260,
      );
      timeline.add(
        targetNode,
        {
          opacity: 1,
          scale: [
            { to: 1.08, duration: 120 },
            { to: 1, duration: 180 },
          ],
          duration: 300,
          ease: "out(4)",
        },
        stepStart + 520,
      );
      timeline.add(
        targetTraveler,
        {
          opacity: 0,
          scale: 0.74,
          duration: 180,
          ease: "in(2)",
        },
        stepStart + 760,
      );
    }
  }

  targetEdges.forEach((edge, index) => {
    const sourceIndex = targetNodeIndexes.get(edge.dataset.source);
    const targetIndex = targetNodeIndexes.get(edge.dataset.target);
    const nodeRevealIndex = Math.max(sourceIndex ?? 0, targetIndex ?? 0);
    const revealAt =
      sourceIndex == null && targetIndex == null
        ? finish - 840 + index * Math.max(24, stepGap * 0.16)
        : feedStart + nodeRevealIndex * stepGap + 780;
    timeline.add(
      edge,
      {
        opacity: edge.classList.contains("edge-label") ? 0.84 : 0.72,
        duration: 260,
        ease: "out(2)",
      },
      Math.min(revealAt, finish - 430),
    );
  });

  addEngineRun(timeline, start + 80, finish - 360);
  timeline.add(
    ".traveler-source, .traveler-target",
    {
      opacity: 0,
      duration: 220,
      ease: "in(2)",
    },
    finish - 430,
  );
}

export function createStoryTimeline() {
  const timeline = createTimeline({
    autoplay: false,
    defaults: {
      ease: "inOut(3)",
    },
  });

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
        y: 0,
        scale: [
          { to: 1.08, duration: 300 },
          { to: 1, duration: 280 },
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
      TRANSFORM_ENGINE_SELECTOR,
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
        x: 0,
        y: 0,
        scale: 1,
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
          { to: 1.08, duration: 240 },
          { to: 1, duration: 320 },
        ],
        borderColor: "var(--accent)",
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
        backgroundColor: "var(--accent)",
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

  const artifactSourceNodes = selector("#model-psm .model-node");
  const artifactTargets = [
    ["handler.go", "#71a7ff"],
    ["template.yaml", "#ffa828"],
    ["openapi.yaml", "#b497ff"],
    ["tests", "#8dff55"],
    ["trace.json", "var(--accent)"],
  ];

  timeline
    .add(
      {},
      {
        duration: 1,
        onBegin: () => {
          setEngineLabel("EGX");
          createArtifactTravelers(artifactSourceNodes, artifactTargets);
        },
      },
      11280,
    )
    .add(
      "#model-psm",
      {
        opacity: 0.84,
        x: "-34%",
        y: 0,
        scale: 0.38,
        duration: 460,
        ease: "out(4)",
      },
      11280,
    )
    .add(
      {},
      {
        duration: 1,
        onBegin: () => placeArtifactTravelers(artifactSourceNodes),
      },
      11740,
    )
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
      `#model-psm, ${TRANSFORM_ENGINE_SELECTOR}`,
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
          {
            to: "color-mix(in srgb, var(--accent) 12%, transparent)",
            duration: 320,
          },
          {
            to: "color-mix(in srgb, var(--accent) 4%, transparent)",
            duration: 420,
          },
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
  const artifactRows = selector(".tree-row");
  const artifactStepCount = Math.max(artifactSourceNodes.length, artifactTargets.length);
  for (let index = 0; index < artifactStepCount; index += 1) {
    const stepStart = 11820 + index * 160;
    const sourceTraveler = `.traveler-source[data-index="${index}"]`;
    const targetTraveler = `.traveler-target[data-index="${index}"]`;
    const sourceNode = artifactSourceNodes[index];
    const targetRow = artifactRows[index];

    if (sourceNode) {
      timeline
        .add(
          sourceTraveler,
          {
            opacity: [
              { to: 1, duration: 50 },
              { to: 1, duration: 120 },
              { to: 0, duration: 50 },
            ],
            left: () => `${engineCenter().x}px`,
            top: () => `${engineCenter().y}px`,
            scale: 0.38,
            duration: 220,
            ease: "in(4)",
          },
          stepStart,
        )
        .add(
          sourceNode,
          {
            opacity: 0.12,
            scale: 0.76,
            duration: 180,
            ease: "in(3)",
          },
          stepStart + 70,
        );
    }

    if (targetRow && index < artifactTargets.length) {
      timeline
        .add(
          targetTraveler,
          {
            opacity: 1,
            left: () => `${artifactTargetPoint(index).x}px`,
            top: () => `${artifactTargetPoint(index).y}px`,
            scale: 1,
            duration: 180,
            ease: "out(4)",
          },
          stepStart + 120,
        )
        .add(
          targetTraveler,
          {
            opacity: 0,
            scale: 0.74,
            duration: 140,
            ease: "in(2)",
          },
          stepStart + 330,
        );
    }
  }
  timeline.add(
    ".traveler-source, .traveler-target",
    {
      opacity: 0,
      duration: 220,
      ease: "in(2)",
    },
    13080,
  );
  timeline.add(
    TRANSFORM_ENGINE_SELECTOR,
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
