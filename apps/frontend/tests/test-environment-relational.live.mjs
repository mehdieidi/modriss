// Real SQL against an isolated generated project. TEST_RELATIONAL_SESSION must name its
// JSON {projectId,artifactId,deploymentJobId,psmId}; TEST_AUTH_SESSION supplies {token}.
// TEST_RELATIONAL_REFINE_HANDLER=1 also installs the evaluation-only Go handler and redeploys.
import assert from "node:assert/strict";
import {readFile, writeFile} from "node:fs/promises";
import {randomUUID} from "node:crypto";

assert.ok(process.env.TEST_RELATIONAL_SESSION, "Select an isolated generated evaluation project with TEST_RELATIONAL_SESSION");
const session = JSON.parse(await readFile(process.env.TEST_RELATIONAL_SESSION, "utf8"));
const {token} = JSON.parse(await readFile(process.env.TEST_AUTH_SESSION || "target/test-environment-session.json", "utf8"));
const base = process.env.TEST_BACKEND_URL || "http://127.0.0.1:8080";
const root = `/projects/${session.projectId}/test-environment`;
const results = [];
async function api(path, method = "GET", body) {
  const response = await fetch(`${base}/api${path}`, {method, headers:{"X-Auth-Token":token, "Content-Type":"application/json"},
    body:body ? JSON.stringify(body) : undefined});
  const text = await response.text();
  assert.ok(response.ok, `${method} ${path}: ${response.status} ${text.slice(0, 1000)}`);
  return text ? JSON.parse(text) : null;
}
async function wait(id, expected = "SUCCEEDED") {
  for (let attempt = 0; attempt < 900; attempt++) {
    const job = await api(`${root}/jobs/${id}`);
    if (!["QUEUED", "RUNNING"].includes(job.status)) {
      if (job.status !== expected) await writeFile("target/test-environment-relational-failure.json", JSON.stringify(job, null, 2));
      assert.equal(job.status, expected, job.error);
      return job;
    }
    await new Promise(resolve => setTimeout(resolve, 1000));
  }
  throw Error("Relational execution timed out");
}
let deployment = await wait(session.deploymentJobId);
let database = deployment.result.resources.find(resource => resource.ResourceType === "AWS::RDS::DBCluster");
assert.ok(database?.database, "The generated deployment must expose its actual relational configuration");
async function run(resource, action, input, expected = "SUCCEEDED") {
  const job = await api(`${root}/jobs`, "POST", {kind:"EXECUTE", deploymentId:deployment.id,
    resourceId:resource.id, action, input:{...input, observeSeconds:0}, scenario:`Relational evaluation: ${action}`});
  return wait(job.id, expected);
}
const query = async(sql, parameters = [], transactionId, expected) => run(database, "query", {sql, parameters, transactionId}, expected);
function passed(name, detail = {}) { results.push({case:name, ...detail}); console.log(`Passed: ${name}`); }
const quote = name => `"${name.replaceAll('"', '""')}"`;
const tableName = database.database.tables[0].name;
const table = quote(tableName);
const count = async() => Number((await query(`SELECT count(*) AS count FROM ${table}`)).result.rows[0].count);
const engine = (await query("SELECT current_database() AS database, version() AS engine")).result.rows[0];
assert.match(engine.engine, /PostgreSQL/);
assert.equal(engine.database, database.database.database);
passed("real PostgreSQL execution", {database:engine.database});
const tables = await run(database, "tables", {});
const columns = tables.result.rows.filter(column => column.table_name === tableName);
assert.equal(columns.length, database.database.tables[0].columns.length);
passed("generated tables and typed columns", {table:tableName, columns:columns.length});
const types = {text:"text", bigint:"bigint", integer:"integer", numeric:"numeric", boolean:"boolean", date:"date",
  "time without time zone":"time", "timestamp with time zone":"timestamptz", bytea:"bytea", uuid:"uuid", jsonb:"jsonb"};
const prefix = `evaluation-${Date.now()}`;
function insert(label) {
  const parameters = columns.map((column, index) => {
    assert.ok(types[column.data_type], `Supported modeled SQL type: ${column.data_type}`);
    let value = `${prefix}-${label}-${index}`;
    if (["bigint", "integer"].includes(column.data_type)) value = "2";
    if (column.data_type === "numeric") value = "125.50";
    if (column.data_type === "boolean") value = "true";
    if (column.data_type === "date") value = "2026-10-31";
    if (column.data_type === "time without time zone") value = "12:30:00";
    if (column.data_type === "timestamp with time zone") value = "2026-10-31T12:30:00Z";
    if (column.data_type === "bytea") value = "\\x0102";
    if (column.data_type === "uuid") value = randomUUID();
    if (column.data_type === "jsonb") value = '{"evaluation":true}';
    return {name:`p${index}`, value:{stringValue:value}};
  });
  return {sql:`INSERT INTO ${table} (${columns.map(column => quote(column.column_name)).join(", ")}) VALUES (` +
    columns.map((column, index) => `CAST(:p${index} AS ${types[column.data_type]})`).join(", ") + ")", parameters};
}
const baseline = await count();
const first = insert("committed");
await query(first.sql, first.parameters);
assert.equal(await count(), baseline + 1);
passed("parameterized insert and persisted read");
const duplicate = await query(first.sql, first.parameters, undefined, "FAILED");
assert.match(duplicate.error, /duplicate|unique/i);
passed("database uniqueness constraint rejects duplicate");
const invalid = await query(`INSERT INTO ${table} DEFAULT VALUES`, [], undefined, "FAILED");
assert.match(invalid.error, /not.null|null value|constraint/i);
passed("database required-field constraint rejects invalid row");
const rollback = (await run(database, "begin-transaction", {})).result.transactionId;
const second = insert("rolled-back");
await query(second.sql, second.parameters, rollback);
await run(database, "rollback-transaction", {transactionId:rollback});
assert.equal(await count(), baseline + 1);
passed("real transaction rollback leaves no row");
const commit = (await run(database, "begin-transaction", {})).result.transactionId;
const third = insert("transaction-commit");
await query(third.sql, third.parameters, commit);
await run(database, "commit-transaction", {transactionId:commit});
assert.equal(await count(), baseline + 2);
passed("real transaction commit retains row");

if (process.env.TEST_RELATIONAL_REFINE_HANDLER === "1") {
  const artifact = await api(`/artifact/${session.artifactId}`);
  const resources = deployment.result.resources;
  const fn = resources.find(resource => resource.ResourceType === "AWS::Lambda::Function" &&
    Object.keys(resource.properties?.Environment?.Variables || {}).some(key => key.endsWith("_RESOURCE_ARN")));
  assert.ok(fn, "A generated Lambda must reference the relational store");
  const envPrefix = Object.keys(fn.properties.Environment.Variables).find(key => key.endsWith("_RESOURCE_ARN")).replace(/_RESOURCE_ARN$/, "");
  const psm = (await api(`/psm/${session.psmId}`)).modelJson;
  let modelId;
  const visit = value => {
    if (Array.isArray(value)) value.forEach(visit);
    else if (value && typeof value === "object") {
      if (value.eClass === "AwsLambdaFunction" && value.logicalId === fn.LogicalResourceId) modelId = value.id;
      Object.values(value).forEach(visit);
    }
  };
  visit(psm);
  const trace = JSON.parse(artifact.files["generated/trace/artifact-trace.json"]);
  const path = trace.artifacts.find(entry => entry.artifactKind === "LAMBDA_HANDLER" && entry.sourceElementIds.includes(modelId))?.path;
  assert.ok(path, "Traceability must locate the generated handler source");
  const modulePath = artifact.files["go.mod"].match(/^module\s+(\S+)/m)[1];
  const revision = String(Date.now());
  const source = (await readFile("apps/frontend/tests/fixtures/test-environment-relational-handler.go", "utf8"))
    .replace("MODRISS_MODULE_PATH", modulePath).replace("MODRISS_STORE_PREFIX", envPrefix)
    .replace("MODRISS_EVALUATION_REVISION", revision);
  await api(`/artifact/${session.artifactId}/files`, "PUT", {path, content:source});
  const redeploy = await api(`${root}/jobs`, "POST", {kind:"DEPLOY", artifactId:session.artifactId, input:{}});
  deployment = await wait(redeploy.id);
  session.deploymentJobId = deployment.id;
  await writeFile(process.env.TEST_RELATIONAL_SESSION, JSON.stringify(session));
  database = deployment.result.resources.find(resource => resource.LogicalResourceId === database.LogicalResourceId);
  assert.equal(await count(), baseline + 2);
  assert.ok(deployment.events.some(event => ["lambda.update_function_code", "cloudformation.update_stack"].includes(event.title)));
  passed("code redeployment keeps database and schema data");
  const functionResource = deployment.result.resources.find(resource => resource.LogicalResourceId === fn.LogicalResourceId);
  const invoke = async(payload) => {
    const execution = await run(functionResource, "invoke", {payload});
    assert.equal(execution.result.Payload.statusCode, 200);
    return JSON.parse(execution.result.Payload.body);
  };
  const read = await invoke({sql:`SELECT count(*) AS count FROM ${table}`});
  assert.equal(read.ok, true);
  assert.equal(read.revision, revision);
  assert.equal(read.database, engine.database);
  passed("generated Go client executes inside the real Lambda runtime");
  const row = insert("go-rollback");
  const params = Object.fromEntries(row.parameters.map(parameter => [parameter.name, parameter.value.stringValue]));
  const result = await invoke({sql:row.sql, parameters:params, transaction:true, rollback:true});
  assert.equal(result.rolledBack, true);
  assert.equal(await count(), baseline + 2);
  passed("generated Go transaction helper rolls back callback failure");
  await invoke({sql:row.sql, parameters:params, transaction:true});
  assert.equal(await count(), baseline + 3);
  passed("generated Go transaction helper commits callback success");
}
await writeFile("target/test-environment-relational.json", JSON.stringify({projectId:session.projectId, deploymentId:deployment.id, results}, null, 2));
console.log(`Relational evaluation passed: ${results.length} checks. Evaluation rows remain in the isolated project for inspection.`);
