import {state} from '../state.js';
import {defaultRootModel, getDefaultNode, toDiagram} from '../diagram.js';
import {modelingLegalKinds, modelingPalette} from '../modeling-config-data.js';
import {setStatus} from '../status.js';
import {genId} from '../utils.js';

const DEFAULT_TYPES = {
  cim: [
    "Actor",
    "Command",
    "Query",
    "BusinessEvent",
    "Policy",
    "BusinessCapability",
    "DomainEntity",
    "ValueObject",
    "AggregateCandidate",
    "Requirement"
  ],
  pim: [
    "Api",
    "Command",
    "Query",
    "Event",
    "Function",
    "DataStore",
    "Queue",
    "Topic",
    "Workflow",
    "Policy"
  ],
  psm: [
    "ApiGatewayApi",
    "ApiGatewayRoute",
    "AwsLambdaFunction",
    "DynamoDbTable",
    "S3Bucket",
    "SqsQueue",
    "SnsTopic",
    "EventBridgeRule",
    "StepFunctionStateMachine",
    "IamRole"
  ]
};

function activeTypes(typeKey) {
  try {
    const configured = modelingPalette(typeKey);
    if (configured.length) {
      return configured.slice(0, 14);
    }
  } catch {
    // Fall back to stable type names when config is not available yet.
  }
  return DEFAULT_TYPES[typeKey] || DEFAULT_TYPES.cim;
}

function legalKind(typeKey, sourceType, targetType) {
  try {
    const kinds = modelingLegalKinds(typeKey, sourceType, targetType);
    if (kinds?.length) {
      return kinds[0];
    }
  } catch {
    // Use a generic fallback below for synthetic stress graphs.
  }
  return typeKey === "cim" ? "TRACE" : "DEPENDS_ON";
}

function buildRoot(typeKey, nodes, edges, name) {
  const root = defaultRootModel(typeKey, name);
  root.diagram ??= {};
  root.diagram.elements = nodes.map((node) => ({
    eClass: node.type,
    id: node.id,
    name: node.label,
    label: node.label,
    x: node.x,
    y: node.y,
    status: "DRAFT",
    tags: [],
    ...node.meta
  }));
  root.diagram.relationships = edges.map((edge) => ({
    id: edge.id,
    kind: edge.kind,
    source: edge.sourceId,
    target: edge.targetId,
    note: "Synthetic G6 performance edge"
  }));
  root.name = name;
  return root;
}

function generateLargeGraph({
  typeKey = state.activeType || "cim",
  nodeCount = 1000,
  edgeCount = 2000,
  columns = 40,
  spacingX = 260,
  spacingY = 150
} = {}) {
  const types = activeTypes(typeKey);
  const nodes = [];
  const edges = [];
  const cols = Math.max(1, Number(columns) || 40);
  for (let index = 0; index < nodeCount; index += 1) {
    const type = types[index % types.length];
    const x = 80 + (index % cols) * spacingX;
    const y = 80 + Math.floor(index / cols) * spacingY;
    const node = getDefaultNode(typeKey, type, x, y);
    node.label = `${type} ${index + 1}`;
    node.meta.name = node.label;
    node.meta.label = node.label;
    nodes.push(node);
  }

  const seen = new Set();
  let attempts = 0;
  while (edges.length < edgeCount && attempts < edgeCount * 12) {
    attempts += 1;
    const sourceIndex = Math.floor(Math.random() * nodes.length);
    const targetIndex = Math.floor(Math.random() * nodes.length);
    if (sourceIndex === targetIndex) {
      continue;
    }
    const source = nodes[sourceIndex];
    const target = nodes[targetIndex];
    const key = `${source.id}|${target.id}`;
    if (seen.has(key)) {
      continue;
    }
    seen.add(key);
    edges.push({
      id: genId("perf-edge"),
      sourceId: source.id,
      targetId: target.id,
      kind: legalKind(typeKey, source.type, target.type)
    });
  }
  return {nodes, edges};
}

export function installG6LargeGraphDevHelper({
  renderDiagram = () => {
  },
  renderWorkbench = () => {
  }
} = {}) {
  window.modlessGenerateLargeGraph = (options = {}) => {
    const typeKey = options.typeKey || state.activeType || "cim";
    const nodeCount = Math.max(1, Number(options.nodes || options.nodeCount)
        || 1000);
    const edgeCount = Math.max(0, Number(options.edges || options.edgeCount)
        || 2000);
    const name = `G6 performance ${nodeCount}n ${edgeCount}e`;
    const {nodes, edges} = generateLargeGraph({
      ...options,
      typeKey,
      nodeCount,
      edgeCount
    });
    const root = buildRoot(typeKey, nodes, edges, name);
    state.activeType = typeKey;
    state.baseModel = root;
    state.diagram = toDiagram(typeKey, root, name);
    state.tabs[typeKey] ??= {};
    state.tabs[typeKey].baseModel = root;
    state.tabs[typeKey].diagram = state.diagram;
    state.tabs[typeKey].modelName = name;
    state.selectedNodeId = null;
    state.selectedNodeIds = new Set();
    state.selectedConnectionId = null;
    state.selectedBoundedContextName = null;
    state.viewport.x = 0;
    state.viewport.y = 0;
    state.viewport.scale = 0.55;
    state.useG6Renderer = true;
    renderDiagram();
    renderWorkbench();
    setStatus(
        `Loaded synthetic G6 graph: ${state.diagram.nodes.length} nodes, ${state.diagram.connections.length} edges`);
    return {
      nodes: state.diagram.nodes.length,
      edges: state.diagram.connections.length
    };
  };
}
