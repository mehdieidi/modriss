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
  const visit = (graph, scope, depth) => {
    for (const [name, state] of Object.entries(graph?.States || {})) {
      const id = scope + name;
      nodes.push({ id, name, type: state.Type, state, row: row++, depth });
      const links = new Set([state.Next, state.Default, ...(state.Choices || []).map(choice => choice.Next),
        ...(state.Catch || []).map(catcher => catcher.Next)].filter(Boolean));
      for (const target of links) edges.push({ source: id, target: scope + target });
      for (const [index, branch] of (state.Branches || []).entries()) visit(branch, `${id}/${index}/`, depth + 1);
      if (state.Iterator || state.ItemProcessor) visit(state.Iterator || state.ItemProcessor, `${id}/items/`, depth + 1);
    }
  };
  visit(definition, prefix, 0);
  return { nodes, edges };
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
