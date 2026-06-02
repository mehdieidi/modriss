import {state} from '../state.js';

export const MODLESS_NODE_TYPE = "modless-node";
export const MODLESS_EDGE_TYPE = "modless-edge";
export const G6_BASE_NODE_TYPE = MODLESS_NODE_TYPE;
export const G6_BASE_EDGE_TYPE = MODLESS_EDGE_TYPE;
export const NODE_SIZE = {
  default: {width: 228, height: 112},
  cim: {width: 176, height: 96}
};

const FALLBACK_ACCENTS = {
  Command: "#3b82f6",
  Query: "#10b981",
  BusinessEvent: "#f59e0b",
  Policy: "#a855f7",
  BusinessError: "#ef4444",
  Actor: "#ec4899",
  ExternalSystem: "#94a3b8",
  BusinessCapability: "#14b8a6",
  BoundedContextCandidate: "#6366f1",
  DomainEntity: "#06b6d4",
  ValueObject: "#84cc16",
  AggregateCandidate: "#f97316",
  InformationItem: "#22c55e",
  DataClassification: "#65a30d",
  BusinessProcess: "#06b6d4",
  Risk: "#ef4444",
  Assumption: "#8b5cf6",
  Hotspot: "#ef4444",
  Function: "#3b82f6",
  Api: "#f59e0b",
  DataStore: "#84cc16",
  Queue: "#f97316",
  Topic: "#ec4899",
  EventBus: "#a855f7",
  Workflow: "#06b6d4",
  AwsLambdaFunction: "#3b82f6",
  ApiGatewayApi: "#f59e0b",
  ApiGatewayRoute: "#fbbf24",
  EventBridgeRule: "#9333ea",
  DynamoDbTable: "#84cc16",
  S3Bucket: "#22d3ee",
  IamRole: "#f43f5e"
};

const STICKY_BY_NOTATION = {
  event: "#fbbf24",
  command: "#93c5fd",
  query: "#86efac",
  policy: "#d8b4fe",
  error: "#fca5a5",
  risk: "#fca5a5",
  hotspot: "#fca5a5",
  condition: "#c4b5fd",
  participant: "#f9a8d4",
  stakeholder: "#f9a8d4",
  external: "#cbd5e1",
  capability: "#5eead4",
  context: "#c7d2fe",
  entity: "#67e8f9",
  "value object": "#bef264",
  aggregate: "#fdba74",
  data: "#bbf7d0",
  classification: "#bbf7d0",
  process: "#a5f3fc",
  start: "#e5e7eb",
  end: "#e5e7eb",
  decision: "#fde68a",
  security: "#ddd6fe",
  privacy: "#ddd6fe",
  compliance: "#ddd6fe",
  quality: "#ddd6fe"
};

const STICKY_BY_TYPE = {
  BusinessGoal: "#fde68a",
  Objective: "#fef08a",
  KPI: "#fde68a",
  Requirement: "#fde68a",
  StakeholderConcern: "#fecaca",
  Role: "#fbcfe8",
  Persona: "#f5d0fe",
  ExternalSystem: "#cbd5e1",
  CapabilityDependency: "#fde68a",
  BoundedContextCandidate: "#bfdbfe",
  AggregateCandidate: "#fdba74",
  BusinessEvent: "#fcd34d",
  BusinessProcess: "#99f6e4",
  ProcessStep: "#67e8f9",
  Decision: "#c4b5fd",
  Precondition: "#e9d5ff",
  Postcondition: "#ddd6fe",
  ExceptionScenario: "#fecdd3",
  TemporalConstraint: "#fde68a",
  DomainEntity: "#67e8f9",
  ValueObject: "#bef264",
  DomainRelationship: "#bfdbfe",
  InformationItem: "#86efac",
  DataClassification: "#bbf7d0",
  ConsentRequirement: "#fef9c3",
  NonFunctionalRequirement: "#d9f99d",
  QualityScenario: "#a7f3d0",
  SecurityConstraint: "#fecaca",
  PrivacyConstraint: "#fecdd3",
  RegulatoryConstraint: "#fde68a",
  OpenQuestion: "#fbcfe8",
  Risk: "#fda4af",
  RequirementLink: "#c7d2fe",
  GoalSatisfactionLink: "#bfdbfe",
  Assumption: "#ddd6fe"
};

export function nodeSizeForDiagram(typeKey = state.activeType) {
  return typeKey === "cim" ? NODE_SIZE.cim : NODE_SIZE.default;
}

export function cssVar(name, fallback = "") {
  const root = document.documentElement;
  const value = root ? getComputedStyle(root).getPropertyValue(name).trim()
      : "";
  return value || fallback;
}

export function normalizeColor(value, fallback) {
  const text = String(value || "").trim();
  return text || fallback;
}

export function nodeAccent(node, definition = null) {
  const ui = definition?.ui && typeof definition.ui === "object"
      ? definition.ui : {};
  return normalizeColor(ui.color || definition?.color,
      FALLBACK_ACCENTS[node?.type] || cssVar("--accent", "#00a6e0"));
}

export function stickyColor(node, notation = null) {
  const key = String(notation?.tag || "").trim().toLowerCase();
  return STICKY_BY_NOTATION[key] || STICKY_BY_TYPE[node?.type] || "#fde68a";
}

export function edgeStyleForKind(kind, presentation = {}) {
  const base = {
    stroke: cssVar("--accent", "#00a6e0"),
    lineWidth: 1.7,
    opacity: 0.9,
    lineDash: undefined
  };
  return {...base, ...(presentation.style || {})};
}

export function isLightTheme() {
  return document.documentElement?.classList.contains("light");
}

export function canvasBackgroundColor() {
  return cssVar("--canvas-custom-bg", cssVar("--canvas-bg", "#0e1117"));
}
