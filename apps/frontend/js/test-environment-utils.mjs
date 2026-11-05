export function resourceGroup(resource) {
  const type = resource.ResourceType || "";
  if (type.includes("ApiGateway")) return type.endsWith("::Api") || type.endsWith("::RestApi") ? "APIs" : "Infrastructure";
  if (type === "AWS::Lambda::Function") return "Functions";
  if (type.includes("StepFunctions::StateMachine")) return "Workflows";
  if (/DynamoDB::Table|S3::Bucket|RDS::DBCluster|RDS::DBInstance/.test(type)) return "Storage";
  if (/SQS::Queue|SNS::Topic|Events::EventBus|Events::Rule/.test(type)) return "Events";
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

const parseDocument = (value) => {
  if (typeof value !== "string") return value;
  try {
    return JSON.parse(value);
  } catch {
    return null;
  }
};

export function openApiDocument(resource = {}) {
  const properties = resource.properties || resource.Properties || {};
  return (
    parseDocument(properties.Body) ||
    parseDocument(properties.DefinitionBody) ||
    parseDocument(properties.ApiDefinition) ||
    null
  );
}

export function exampleFromSchema(schema, document, seen = new Set(), depth = 0) {
  if (!schema || typeof schema !== "object" || depth > 8) return {};
  if (Array.isArray(schema.oneOf) || Array.isArray(schema.anyOf))
    return exampleFromSchema((schema.oneOf || schema.anyOf)[0], document, seen, depth + 1);
  if (Array.isArray(schema.allOf)) {
    const parts = schema.allOf.map((part) => exampleFromSchema(part, document, seen, depth + 1));
    return Object.assign({}, ...parts.filter((part) => part && typeof part === "object" && !Array.isArray(part)));
  }
  if (Object.prototype.hasOwnProperty.call(schema, "example")) return schema.example;
  if (Object.prototype.hasOwnProperty.call(schema, "default")) return schema.default;
  if (Array.isArray(schema.enum) && schema.enum.length) return schema.enum[0];
  if (schema.$ref) {
    const name = schema.$ref.split("/").pop();
    if (!name || seen.has(name)) return {};
    const definitions = document?.components?.schemas || document?.definitions || {};
    const resolved = definitions[name];
    return resolved ? exampleFromSchema(resolved, document, new Set([...seen, name]), depth + 1) : {};
  }
  const type = (Array.isArray(schema.type) ? schema.type.find((value) => value !== "null") : schema.type) || (schema.properties ? "object" : schema.items ? "array" : "object");
  if (type === "object") {
    const properties = schema.properties || {};
    return Object.fromEntries(
      Object.entries(properties).map(([key, value]) => [
        key,
        exampleFromSchema(value, document, seen, depth + 1),
      ]),
    );
  }
  if (type === "array") return [exampleFromSchema(schema.items || {}, document, seen, depth + 1)];
  if (type === "integer" || type === "number") {
    const value = schema.minimum ?? 1;
    return schema.maximum != null && value > schema.maximum ? schema.maximum : value;
  }
  if (type === "boolean") return true;
  if (type === "string") {
    if (schema.format === "date-time") return "2026-11-05T12:00:00Z";
    if (schema.format === "date") return "2026-11-05";
    if (schema.format === "email") return "user@example.com";
    if (schema.format === "uuid") return "123e4567-e89b-12d3-a456-426614174000";
    if (schema.format === "uri" || schema.format === "url") return "https://example.com/resource";
    const length = Math.max(1, Math.min(128, schema.minLength || Math.min(7, schema.maxLength || 7)));
    return "e".repeat(length);
  }
  return null;
}

const httpMethods = new Set(["get", "post", "put", "patch", "delete", "head", "options"]);

function operationForRoute(resource, route) {
  const document = openApiDocument(resource);
  if (!document || !route) return null;
  const normalizedPath = route.path.replace(/\{[^}]+\}/g, (parameter) => parameter);
  const pathItem = document.paths?.[normalizedPath];
  const method = route.method.toLowerCase();
  if (pathItem && httpMethods.has(method) && pathItem[method])
    return {
      ...pathItem[method],
      parameters: [...(pathItem.parameters || []), ...(pathItem[method].parameters || [])],
    };
  if (method === "any" || method === "$default") {
    const entry = Object.entries(document.paths || {}).find(([, value]) =>
      Object.keys(value || {}).some((key) => httpMethods.has(key)),
    );
    if (entry) {
      const verb = Object.keys(entry[1]).find((key) => httpMethods.has(key));
      return {
        ...entry[1][verb],
        parameters: [...(entry[1].parameters || []), ...(entry[1][verb].parameters || [])],
      };
    }
  }
  return null;
}

export function apiOperations(resource = {}) {
  const document = openApiDocument(resource);
  if (!document?.paths)
    return (resource.routes || []).map((route, index) => ({
      ...route,
      index,
      operation: null,
      summary: "API operation",
    }));
  return (resource.routes || []).map((route, index) => {
    const operation = operationForRoute(resource, route);
    const documentedPath =
      ["ANY", "$default"].includes(route.method)
        ? Object.entries(document.paths).find(([, pathItem]) =>
            Object.keys(pathItem || {}).some((key) => httpMethods.has(key)),
          )?.[0]
        : null;
    return {
      ...route,
      path: documentedPath || route.path,
      index,
      operation,
      summary: operation?.summary || operation?.operationId || "API operation",
    };
  });
}

export function apiRequestExample(resource = {}, route = resource.routes?.[0]) {
  const operation = operationForRoute(resource, route);
  const document = openApiDocument(resource);
  const documentedPath =
    ["ANY", "$default"].includes(route?.method) && document?.paths
      ? Object.entries(document.paths).find(([, pathItem]) =>
          Object.keys(pathItem || {}).some((key) => httpMethods.has(key)),
        )?.[0]
      : null;
  const input = {
    method: ["ANY", "$default"].includes(route?.method) ? "GET" : route?.method || "GET",
    path: documentedPath || route?.path || "/",
    stage: resource.stages?.[0] || "Prod",
    query: {},
    headers: {},
    body: null,
  };
  const parameters = [...(operation?.parameters || []), ...(operation?.requestBody?.parameters || [])];
  for (const parameter of parameters) {
    const value = parameter.example ?? exampleFromSchema(parameter.schema || {}, openApiDocument(resource));
    const scalar = Array.isArray(value) ? value.join(",") : value && typeof value === "object" ? JSON.stringify(value) : String(value);
    if (parameter.in === "query") input.query[parameter.name] = scalar;
    if (parameter.in === "header") input.headers[parameter.name] = scalar;
    if (parameter.in === "cookie") input.headers.Cookie = `${input.headers.Cookie ? `${input.headers.Cookie}; ` : ""}${parameter.name}=${scalar}`;
    if (parameter.in === "path") input.path = input.path.replace(`{${parameter.name}}`, scalar);
  }
  const content = operation?.requestBody?.content || {};
  const bodyParameter = operation?.parameters?.find((parameter) => parameter.in === "body");
  const bodySchema = content["application/json"]?.schema || Object.values(content)[0]?.schema || bodyParameter?.schema;
  const bodyContent = content["application/json"] || Object.values(content)[0];
  if (bodyContent?.example !== undefined) input.body = bodyContent.example;
  else if (bodyContent?.examples && Object.keys(bodyContent.examples).length)
    input.body = Object.values(bodyContent.examples)[0].value ?? null;
  else if (bodySchema) input.body = exampleFromSchema(bodySchema, openApiDocument(resource));
  else if (bodyParameter) input.body = exampleFromSchema(bodyParameter.schema || {}, openApiDocument(resource));
  else if (["POST", "PUT", "PATCH"].includes(input.method)) input.body = {};
  const security = operation?.security ?? document?.security ?? [];
  for (const requirement of security.slice(0, 1)) {
    for (const name of Object.keys(requirement)) {
      const scheme = document?.components?.securitySchemes?.[name] || document?.securityDefinitions?.[name];
      if (scheme?.type === "http" && scheme.scheme === "bearer") input.headers.Authorization = "Bearer <access-token>";
      else if (scheme?.type === "http" && scheme.scheme === "basic") input.headers.Authorization = "Basic <base64-credentials>";
      else if (scheme?.type === "oauth2" || scheme?.type === "openIdConnect") input.headers.Authorization = "Bearer <access-token>";
      else if (scheme?.type === "apiKey" && scheme.in === "header")
        input.headers[scheme.name] = scheme.name.toLowerCase() === "authorization" ? "Bearer <access-token>" : "<api-key>";
      else if (scheme?.type === "apiKey" && scheme.in === "query") input.query[scheme.name] = "<api-key>";
      else if (scheme?.type === "apiKey" && scheme.in === "cookie") input.headers.Cookie = `${input.headers.Cookie ? `${input.headers.Cookie}; ` : ""}${scheme.name}=<api-key>`;
    }
  }
  return input;
}

function dynamoItemExample(resource) {
  const properties = resource.properties || resource.Properties || {};
  const attributes = new Map((properties.AttributeDefinitions || []).map((entry) => [entry.AttributeName, entry.AttributeType]));
  const keys = properties.KeySchema || [{ AttributeName: "id", KeyType: "HASH" }];
  const item = {};
  for (const key of keys) {
    const name = key.AttributeName;
    const type = attributes.get(name) || "S";
    item[name] = type === "N" ? { N: "1" } : type === "B" ? { B: "ZXhhbXBsZQ==" } : { S: "example-1" };
  }
  return item;
}

export function initialInput(action, resource = {}) {
  if (action === "query") {
    const table = resource.database?.tables?.[0]?.name;
    return {sql: table ? `SELECT * FROM "${table.replaceAll('"', '""')}" LIMIT 25` : "SELECT 1", parameters:[]};
  }
  if (action === "commit-transaction" || action === "rollback-transaction") return {transactionId:"paste-the-begin-transaction-result"};
  if (action === "run") return { args: [], env: {} };
  if (action === "aws-api") {
    const example = resource.sdk?.example;
    return example?.operation
      ? example
      : { operation: resource.sdk?.operations?.[0] || "", parameters: example?.parameters || {} };
  }
  if (action === "request") return apiRequestExample(resource);
  if (action === "put" && resource.ResourceType === "AWS::DynamoDB::Table") return { item: dynamoItemExample(resource) };
  if (action === "put") return { key: "example.json", body: "{\"message\":\"Hello\"}", contentType: "application/json" };
  if (action === "get") return { key: "example.json" };
  if (action === "list") return { prefix: "" };
  if (action === "invoke")
    return {
      payload: {
        version: "2.0",
        routeKey: "$default",
        rawPath: "/",
        headers: {},
        requestContext: { http: { method: "POST", path: "/", protocol: "HTTP/1.1" } },
        body: "{}",
        isBase64Encoded: false,
      },
    };
  if (action === "start") return { payload: { id: "example-1", source: "modriss.test", detail: {} } };
  if (action === "send") {
    const input = { payload: { id: "example-1", type: "example.created", occurredAt: "2026-11-05T12:00:00Z", data: {} } };
    if ((resource.properties || resource.Properties || {}).FifoQueue || resource.PhysicalResourceId?.endsWith(".fifo")) input.groupId = "modriss";
    return input;
  }
  if (action === "publish" && resource.ResourceType === "AWS::Events::EventBus")
    return {
      source: "modriss.test",
      detailType: "ExampleEvent",
      payload: { id: "example-1", type: "example.created", occurredAt: "2026-11-05T12:00:00Z", data: {} },
    };
  if (action === "publish")
    return { payload: { id: "example-1", type: "example.created", occurredAt: "2026-11-05T12:00:00Z", data: {} } };
  return {};
}
