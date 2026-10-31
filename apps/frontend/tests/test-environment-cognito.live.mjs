// Real generated REST APIs, Floci Cognito, request validation, and Lambda execution.
// Supply TEST_COGNITO_DEPLOYMENT (worker deployment JSON), TEST_COGNITO_EXAMPLES
// ({[api logical ID]: {get:{path,query},post:{path,body}}}), and TEST_COGNITO_NETWORK.
// TEST_COGNITO_REFINED_GET=1 expects the completed evaluation handler fixture on GET.
import assert from "node:assert/strict";
import { readFile, writeFile, mkdir } from "node:fs/promises";
import { spawnSync } from "node:child_process";

for (const name of ["TEST_COGNITO_DEPLOYMENT", "TEST_COGNITO_EXAMPLES", "TEST_COGNITO_NETWORK"]) {
  assert.ok(process.env[name], `${name} is required; this check uses an existing isolated generated deployment`);
}
const deployed = JSON.parse(await readFile(process.env.TEST_COGNITO_DEPLOYMENT, "utf8"));
const examples = JSON.parse(await readFile(process.env.TEST_COGNITO_EXAMPLES, "utf8"));
const resources = deployed.resources;
const pool = resources.find(resource => resource.LogicalResourceId ===
  (process.env.TEST_COGNITO_POOL || "AffectedResidentIdentityProviderUserPool"));
assert.ok(pool, "The generated deployment must include the selected Cognito pool");
const refined = process.env.TEST_COGNITO_REFINED_GET === "1";
let sequence = 0;
const results = [];
function execute(resource, action, input) {
  const request = { kind:"EXECUTE", projectId:"cognito-evaluation", jobId:`cognito-${Date.now()}-${++sequence}`,
    accountId:deployed.accountId, resources, resourceId:resource.id, action, input:{...input, observeSeconds:0} };
  const processResult = spawnSync("docker", ["run", "--rm", "-i", "--network", process.env.TEST_COGNITO_NETWORK,
    process.env.TEST_RUNNER_IMAGE || "modriss-test-runner:local"], {
    input:JSON.stringify(request), encoding:"utf8", maxBuffer:20 * 1024 * 1024,
    timeout:180000, windowsHide:true });
  const events = (processResult.stdout || "").split("\n").flatMap(line => {
    try { return [JSON.parse(line)]; } catch { return []; }
  });
  const result = events.findLast(event => event.type === "result")?.result;
  assert.ok(result, "Worker must provide its actual execution result");
  // HTTP rejection is a failed execution with an intact response, which is expected in negative cases.
  if (action !== "request" || result.statusCode < 400) {
    assert.equal(processResult.status, 0, events.findLast(event => event.type === "error")?.message || "Worker failed");
  }
  return result;
}
const sdk = (operation, parameters) => execute(pool, "aws-api", {operation, parameters});
const clientId = sdk("create_user_pool_client", {UserPoolId:pool.PhysicalResourceId,
  ClientName:`evaluation-${Date.now()}`, GenerateSecret:false,
  ExplicitAuthFlows:["ALLOW_USER_PASSWORD_AUTH", "ALLOW_REFRESH_TOKEN_AUTH"]}).UserPoolClient.ClientId;
const username = `evaluation-${Date.now()}@example.test`, password = "EvaluationCase42!";
sdk("admin_create_user", {UserPoolId:pool.PhysicalResourceId, Username:username, MessageAction:"SUPPRESS",
  UserAttributes:[{Name:"email", Value:username}, {Name:"email_verified", Value:"true"}]});
sdk("admin_set_user_password", {UserPoolId:pool.PhysicalResourceId, Username:username, Password:password, Permanent:true});
const jwt = sdk("initiate_auth", {ClientId:clientId, AuthFlow:"USER_PASSWORD_AUTH",
  AuthParameters:{USERNAME:username, PASSWORD:password}}).AuthenticationResult?.IdToken;
assert.ok(jwt, "Floci Cognito must issue a real JWT");
const claims = JSON.parse(Buffer.from(jwt.split(".")[1], "base64url").toString());
const parts = jwt.split(".");
parts[2] = (parts[2][0] === "A" ? "B" : "A") + parts[2].slice(1);
const headers = {Authorization:`Bearer ${jwt}`};
const apis = resources.filter(resource => resource.ResourceType === "AWS::ApiGateway::RestApi" && examples[resource.LogicalResourceId]);
assert.ok(apis.length, "At least one generated REST API must have request examples");
for (const api of apis) {
  const example = examples[api.LogicalResourceId];
  const get = {method:"GET", path:example.get.path.replace(/\{[^}]+\}/g, "evaluation-1"), query:example.get.query};
  const post = {method:"POST", path:example.post.path, body:example.post.body};
  const cases = [
    ["missing token", {...get, headers:{}}, 401],
    ["malformed token", {...get, headers:{Authorization:"Bearer invalid"}}, 401],
    ["tampered signature", {...get, headers:{Authorization:`Bearer ${parts.join(".")}`}}, 401],
    ["missing required query", {...get, query:{}, headers}, 400],
    ["valid GET", {...get, headers}, refined ? 200 : 500],
    ["invalid POST body", {...post, body:{}, headers}, 400],
    ["valid POST scaffold", {...post, headers}, 500],
  ];
  for (const [name, input, expected] of cases) {
    const result = execute(api, "request", {...input, stage:process.env.TEST_COGNITO_STAGE || "test"});
    assert.equal(result.statusCode, expected, `${api.LogicalResourceId}: ${name}`);
    if (expected === 500) assert.match(result.body, /NOT_IMPLEMENTED|not been implemented/i,
      "Only a deliberate unrefined scaffold error is expected");
    if (expected === 200) {
      const body = JSON.parse(result.body);
      assert.equal(body.accepted, true);
      assert.equal(body.bearerHeaderForwarded, true);
      assert.equal(body.subject, claims.sub, "The verified Cognito subject must reach the handler");
    }
    results.push({api:api.LogicalResourceId, case:name, status:result.statusCode});
    console.log(`${api.LogicalResourceId}: ${name} -> ${result.statusCode}`);
  }
}
await mkdir("target", {recursive:true});
await writeFile("target/test-environment-cognito.json", JSON.stringify(results, null, 2));
console.log(`Cognito evaluation passed: ${results.length} real API checks. Tokens are excluded from the report.`);
