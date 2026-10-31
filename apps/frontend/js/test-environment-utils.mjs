export function resourceGroup(resource) {
  const type = resource.ResourceType || "";
  if (type.includes("ApiGateway")) return type.endsWith("::Api") || type.endsWith("::RestApi") ? "APIs" : "Infrastructure";
  if (type === "AWS::Lambda::Function") return "Functions";
  if (type.includes("StepFunctions::StateMachine")) return "Workflows";
  if (/DynamoDB::Table|S3::Bucket|RDS::DBCluster|RDS::DBInstance/.test(type)) return "Storage";
  if (/SQS::Queue|SNS::Topic|Events::EventBus/.test(type)) return "Events";
  if (type === "AWS::Logs::LogGroup") return "Logs";
  if (type === "Script") return "Scripts";
  return "Infrastructure";
}

export function workflowGraph(definition, prefix = "") {
  const nodes = [], edges = [];
  let row = 0;
  const choiceLabel = (choice) => {
    if (!choice.Variable) return "Choice";
    const operators = [
      "StringEquals", "StringEqualsPath", "StringLessThan", "StringLessThanPath",
      "StringGreaterThan", "StringGreaterThanPath", "NumericEquals", "NumericEqualsPath",
      "NumericLessThan", "NumericLessThanPath", "NumericLessThanEquals", "NumericLessThanEqualsPath",
      "NumericGreaterThan", "NumericGreaterThanPath", "NumericGreaterThanEquals", "NumericGreaterThanEqualsPath",
      "BooleanEquals", "BooleanEqualsPath", "TimestampEquals", "TimestampEqualsPath",
      "TimestampLessThan", "TimestampLessThanPath", "TimestampGreaterThan", "TimestampGreaterThanPath",
      "IsPresent", "IsNull", "IsString", "IsNumeric", "IsBoolean", "IsTimestamp",
    ];
    const operator = operators.find((key) => Object.prototype.hasOwnProperty.call(choice, key));
    if (!operator) return `${choice.Variable} condition`;
    const value = choice[operator];
    return `${choice.Variable} ${operator} ${typeof value === "string" ? JSON.stringify(value) : value}`;
  };
  const visit = (graph, scope, depth, parent = null, entryLabel = "") => {
    const graphNodes = [];
    for (const [name, state] of Object.entries(graph?.States || {})) {
      const id = scope + name;
      const node = { id, name, type: state.Type, state, row: row++, depth, scope };
      nodes.push(node);
      graphNodes.push(node);
    }
    const entry = graphNodes.find((node) => node.name === graph?.StartAt);
    if (parent && entry) edges.push({ source: parent.id, target: entry.id, label: entryLabel, kind: "subflow" });

    for (const node of graphNodes) {
      const { id, state } = node;
      if (state.Next) edges.push({ source: id, target: scope + state.Next });
      for (const [index, choice] of (state.Choices || []).entries())
        if (choice.Next) edges.push({ source: id, target: scope + choice.Next, label: choiceLabel(choice), kind: "choice" });
      if (state.Default) edges.push({ source: id, target: scope + state.Default, label: "Default", kind: "choice" });
      for (const catcher of state.Catch || [])
        if (catcher.Next) edges.push({ source: id, target: scope + catcher.Next, label: `Catch · ${(catcher.ErrorEquals || []).join(", ") || "Error"}`, kind: "catch" });

      for (const [index, branch] of (state.Branches || []).entries())
        visit(branch, `${id}/${index}/`, depth + 1, node, `Branch ${index + 1}`);
      if (state.Iterator || state.ItemProcessor)
        visit(state.Iterator || state.ItemProcessor, `${id}/items/`, depth + 1, node, "Each item");

      if (state.End) {
        const terminalId = `${id}/@complete`;
        nodes.push({
          id: terminalId,
          name: entryLabel ? `${entryLabel} complete` : "Workflow complete",
          type: "Terminal",
          terminal: "succeeded",
          sourceStateId: id,
          row: row++,
          depth,
          scope,
        });
        edges.push({ source: id, target: terminalId, kind: "terminal" });
      }
    }
  };
  visit(definition, prefix, 0);
  return { nodes, edges, startAt: definition?.StartAt ? prefix + definition.StartAt : "" };
}

export function layoutWorkflowGraph(graph, options = {}) {
  const nodeWidth = options.nodeWidth || 248;
  const nodeHeight = options.nodeHeight || 78;
  const horizontalGap = options.horizontalGap || 72;
  const verticalGap = options.verticalGap || 136;
  const top = 74;
  const side = 30;
  const byId = new Map(graph.nodes.map((node) => [node.id, node]));
  const rank = new Map();
  const queue = [];
  if (graph.startAt && byId.has(graph.startAt)) {
    rank.set(graph.startAt, 0);
    queue.push(graph.startAt);
  }
  for (let cursor = 0; cursor < queue.length; cursor++) {
    const source = queue[cursor];
    for (const edge of graph.edges) {
      if (edge.source !== source || !byId.has(edge.target) || rank.has(edge.target)) continue;
      rank.set(edge.target, rank.get(source) + 1);
      queue.push(edge.target);
    }
  }
  let nextRank = Math.max(0, ...rank.values()) + 1;
  for (const node of graph.nodes) {
    if (!rank.has(node.id)) rank.set(node.id, nextRank++);
  }

  const layers = new Map();
  for (const node of graph.nodes) {
    const layer = rank.get(node.id);
    if (!layers.has(layer)) layers.set(layer, []);
    layers.get(layer).push(node);
  }
  for (const layer of layers.values()) layer.sort((a, b) => a.row - b.row);

  const layerNumbers = [...layers.keys()].sort((a, b) => a - b);
  const order = new Map();
  for (const layer of layerNumbers)
    layers.get(layer).forEach((node, index) => order.set(node.id, index));
  for (let pass = 0; pass < 4; pass++) {
    const forward = pass % 2 === 0;
    const scan = forward ? layerNumbers : [...layerNumbers].reverse();
    for (const layer of scan) {
      const siblings = layers.get(layer);
      const neighbors = (node) => graph.edges
        .filter((edge) => forward ? edge.target === node.id : edge.source === node.id)
        .map((edge) => byId.get(forward ? edge.source : edge.target))
        .filter((candidate) => candidate && rank.get(candidate.id) !== layer);
      siblings.sort((a, b) => {
        const average = (node) => {
          const linked = neighbors(node);
          return linked.length ? linked.reduce((sum, item) => sum + (order.get(item.id) || 0), 0) / linked.length : node.row;
        };
        return average(a) - average(b) || a.row - b.row;
      });
      siblings.forEach((node, index) => order.set(node.id, index));
    }
  }

  const widthOf = (node) => node.terminal ? 172 : nodeWidth;
  const maxLayerWidth = Math.max(1, ...[...layers.values()].map((layer) =>
    layer.reduce((sum, node) => sum + widthOf(node), 0) + Math.max(0, layer.length - 1) * horizontalGap,
  ));
  const width = maxLayerWidth + side * 2;
  const positioned = graph.nodes.map((node) => {
    const layer = layers.get(rank.get(node.id));
    const index = layer.indexOf(node);
    const layerWidth = layer.reduce((sum, item) => sum + widthOf(item), 0) + Math.max(0, layer.length - 1) * horizontalGap;
    const before = layer.slice(0, index).reduce((sum, item) => sum + widthOf(item) + horizontalGap, 0);
    return {
      ...node,
      rank: rank.get(node.id),
      x: (width - layerWidth) / 2 + before,
      y: top + rank.get(node.id) * verticalGap,
      width: node.terminal ? 172 : nodeWidth,
      height: node.terminal ? 42 : nodeHeight,
    };
  });
  const height = top + Math.max(1, ...rank.values()) * verticalGap + nodeHeight + 30;
  return { nodes: positioned, edges: graph.edges, startAt: graph.startAt, width, height, nodeWidth, nodeHeight };
}

export function workflowStates(events) {
  const states = new Map();
  for (const event of events || []) {
    const entered = event.stateEnteredEventDetails;
    const exited = event.stateExitedEventDetails;
    if (entered) states.set(entered.name, { status: "running", input: entered.input, entered: event.timestamp });
    if (exited) states.set(exited.name, { ...states.get(exited.name), status: "succeeded", output: exited.output, exited: event.timestamp });
    if (/Failed$|TimedOut$|Aborted$/.test(event.type || "")) {
      const active = [...states].reverse().find(([, state]) => state.status === "running");
      if (active) states.set(active[0], { ...active[1], status: "failed", error: event });
    }
  }
  return states;
}

export function initialInput(action, resource = {}) {
  if (action === "query") {
    const table = resource.database?.tables?.[0]?.name;
    return {sql: table ? `SELECT * FROM "${table.replaceAll('"', '""')}" LIMIT 25` : "SELECT 1", parameters:[]};
  }
  if (action === "commit-transaction" || action === "rollback-transaction") return {transactionId:"paste-the-begin-transaction-result"};
  if (action === "run") return { args: [], env: {} };
  if (action === "aws-api") return resource.sdk?.example || { operation: "", parameters: {} };
  if (action === "request") return {
    method: resource.routes?.[0]?.method === "ANY" ? "GET" : resource.routes?.[0]?.method || "GET",
    path: resource.routes?.[0]?.path || "/", stage: resource.stages?.[0] || "Prod",
    query: {}, headers: {}, body: null,
  };
  if (action === "put" && resource.ResourceType === "AWS::DynamoDB::Table") return { item: { id: { S: "example-1" } } };
  if (action === "put") return { key: "example.json", body: "{\"message\":\"Hello\"}", contentType: "application/json" };
  if (action === "get") return { key: "example.json" };
  if (action === "list") return { prefix: "" };
  if (["invoke", "start", "send", "publish"].includes(action)) return { payload: {} };
  return {};
}
