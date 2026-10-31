// Explicit end-to-end check: real backend, PostgreSQL, disposable runner, Floci and browser.
// Run: node apps/frontend/tests/test-environment.live.mjs
import assert from "node:assert/strict";
import { readFile, writeFile, mkdir } from "node:fs/promises";
import { spawnSync } from "node:child_process";
import { verifyBrowser } from "./test-environment-ui.live.mjs";
import { files } from "./fixtures/test-environment-project.mjs";
import { verifyRedeployment } from "./test-environment-redeploy.live.mjs";

const backend = process.env.TEST_BACKEND_URL || "http://127.0.0.1:8080";
const editor = process.env.TEST_EDITOR_URL || "http://editor.localhost:8088";
const keep = process.env.TEST_KEEP_PROJECT === "1";
let token, project, browser, page, guest, secondProject;
const delay = ms => new Promise(resolve => setTimeout(resolve, ms));
async function api(path, method = "GET", body, expected = 200, overrideToken = token) {
  const response = await fetch(`${backend}/api${path}`, { method,
    headers: { ...(overrideToken ? {"X-Auth-Token":overrideToken} : {}),
      ...(body && !(body instanceof FormData) ? {"Content-Type":"application/json"} : {}) },
    body: body instanceof FormData ? body : body ? JSON.stringify(body) : undefined });
  const text = await response.text();
  assert.equal(response.status, expected, `${method} ${path}: ${text.slice(0, 1600)}`);
  return text ? JSON.parse(text) : null;
}
async function waitJob(path, id, expected = "SUCCEEDED", limit = 900000) {
  const deadline = Date.now() + limit;
  let job;
  while (Date.now() < deadline) {
    job = await api(`${path}/${id}`);
    if (!["QUEUED", "RUNNING", "PENDING"].includes(job.status)) break;
    await delay(1000);
  }
  if (job.status !== expected) await writeFile("target/test-environment-failed-job.json", JSON.stringify(job, null, 2));
  assert.equal(job.status, expected, JSON.stringify({error:job.error,diagnostics:job.diagnostics,events:job.events?.slice(-5)}));
  return job;
}

try {
  await mkdir("target", {recursive:true});
  if (process.env.TEST_RESUME_PROJECT === "1") {
    const saved = JSON.parse(await readFile("target/test-environment-session.json","utf8"));
    token = saved.token;
    project = await api(`/projects/${saved.projectId}`);
    guest = {user:{id:saved.userId}};
  } else {
  guest = await api("/auth/guest", "POST", {});
  token = guest.token;
  project = await api("/projects", "POST", {name:"Test environment evaluation",description:"Temporary full-stack case-study fixture"});
  if (keep) await writeFile("target/test-environment-session.json",JSON.stringify({token,projectId:project.id,userId:guest.user.id}));
  }
  const root = `/projects/${project.id}/test-environment`;
  let artifact;
  if (process.env.TEST_RESUME_PROJECT === "1") {
    artifact = await api(`/artifact/${(await api(root)).artifacts[0].id}`);
  } else {
  const form = new FormData();
  form.append("file", new Blob([await readFile("methodology/modeling-framework/samples/architecture-patterns/arch-http/arch-http.awspsm.xmi")]), "http-case-study.xmi");
  const imported = await api(`/psm/import?projectId=${project.id}&format=xmi`, "POST", form);
  const model = await api("/psm", "POST", {projectId:project.id,name:"HTTP case study",model:imported.modelJson});
  const generated = await api("/transformations/psm-to-artifact", "POST", {sourceModelId:model.id,expectedRevision:model.revision}, 202);
  const generation = await waitJob("/transformations/jobs", generated.id);
  artifact = await api(`/artifact/${generation.resultArtifactId}`);
  assert.ok(Object.keys(artifact.files).length > 30, "Use an artifact produced by the real generator");
  console.log(`Generated HTTP case-study artifact: ${Object.keys(artifact.files).length} files`);
  // Import a developer-completed implementation through the supported source ZIP endpoint.
  const zipped = spawnSync("python", ["-c", "import sys,json,io,zipfile; f=json.load(sys.stdin); b=io.BytesIO(); z=zipfile.ZipFile(b,'w'); [z.writestr(k,v) for k,v in f.items()]; z.close(); sys.stdout.buffer.write(b.getvalue())"], {input:JSON.stringify(files)});
  assert.equal(zipped.status, 0, zipped.stderr?.toString());
  const sources = new FormData(); sources.append("file",new Blob([zipped.stdout]),"refined-project.zip");
  await api(`/artifact/${artifact.id}/source-archive`, "POST", sources);
  }
  const outsider = await api("/auth/guest", "POST", {}, 200, null);
  await api(root, "GET", null, 403, outsider.token);
  let submitted = await api(`${root}/jobs`, "POST", {kind:"DEPLOY",artifactId:artifact.id,input:{templates:["template.yaml"]}}, 202);
  await api(`${root}/jobs`, "POST", {kind:"DEPLOY",artifactId:artifact.id,input:{}}, 409);
  const deployed = await waitJob(`${root}/jobs`, submitted.id);
  const deploymentId = deployed.id;
  const resources = deployed.result.resources;
  assert.ok(resources.some(r => r.ResourceType === "AWS::Lambda::Function"));
  assert.ok(resources.some(r => r.ResourceType === "AWS::ApiGateway::RestApi"));
  assert.ok(resources.some(r => r.ResourceType === "AWS::ApiGatewayV2::Api"));
  assert.ok(resources.some(r => r.ResourceType === "AWS::StepFunctions::StateMachine"));
  assert.ok(resources.find(r=>r.LogicalResourceId==="Api").routes.some(route=>route.path==="/secure" && route.method==="GET"));
  console.log(`Deployed real resources: ${resources.length}`);
  const find = name => resources.find(r => r.LogicalResourceId === name);
  async function run(name, action, input, expected="SUCCEEDED") {
    const job = await api(`${root}/jobs`, "POST", {kind:"EXECUTE",deploymentId,resourceId:find(name).id,action,input,scenario:`UC · ${name} ${action}`}, 202);
    return waitJob(`${root}/jobs`, job.id, expected);
  }
  const invoked = await run("Handler", "invoke", {payload:{id:"direct-1"}});
  assert.equal(invoked.result.Payload.ok, true);
  assert.ok(invoked.events.some(e => e.type === "storage" && e.phase === "After"));
  const failed = await run("Handler", "invoke", {payload:{fail:true}}, "FAILED");
  assert.ok(failed.result.FunctionError);
  const rest = await run("Api", "request", {method:"POST",path:"/orders",stage:"test",body:{id:"api-1"}});
  assert.equal(rest.result.statusCode, 200);
  const http = await run("HttpApi", "request", {method:"POST",path:"/echo",stage:"test",body:{id:"http-1"}});
  assert.equal(http.result.statusCode, 200);
  const authentication = [];
  for (const name of ["Api", "HttpApi"]) {
    for (const [authorization, status] of [["Bearer evaluation-valid",200], [null,401],
      ["Bearer evaluation-invalid",401], ["Bearer evaluation-expired",401], ["Bearer evaluation-forbidden",403]]) {
      const result = await run(name, "request", {method:"GET",path:"/secure",stage:"test",
        headers:authorization === null ? {} : {Authorization:authorization}}, status === 200 ? "SUCCEEDED" : "FAILED");
      assert.equal(result.result.statusCode, status);
      assert.equal(JSON.parse(result.result.body).authorization, authorization || "");
      authentication.push({api:name,authorization,status:result.result.statusCode});
    }
  }
  // This path uses API Gateway's actual TOKEN Lambda authorizer, before the integration.
  // Floci 2.2 short-circuits missing TOKEN identity headers before invoking the authorizer.
  for (const [authorization,status] of [["Bearer evaluation-valid",200], [null,401], ["Bearer evaluation-forbidden",403]]) {
    const result = await run("Api", "request", {method:"GET",path:"/authorized",stage:"test",
      headers:authorization === null ? {} : {Authorization:authorization}}, status === 200 ? "SUCCEEDED" : "FAILED");
    assert.equal(result.result.statusCode,status,JSON.stringify(result.result));
    authentication.push({api:"REST Lambda authorizer",authorization,status:result.result.statusCode});
  }
  await writeFile("target/test-environment-authentication.json",JSON.stringify(authentication,null,2));
  console.log("Authentication checks passed: exact bearer header forwarding, missing/invalid/expired tokens, denied access, and REST TOKEN Lambda authorizer.");
  const workflow = await run("Workflow", "start", {payload:{id:"workflow-1"}});
  assert.equal(workflow.result.status, "SUCCEEDED");
  assert.ok(workflow.events.some(e => e.stateEnteredEventDetails?.name === "Invoke"));
  const rejected = await run("Workflow", "start", {payload:{fail:true}}, "FAILED");
  assert.equal(rejected.result.error, "RejectedByScenario");
  const workflows = await run("Workflow", "executions", {});
  assert.ok(workflows.result.executions.length >= 2);
  await run("Orders", "put", {item:{id:{S:"manual-1"},status:{S:"SEEDED"}}});
  const rows = await run("Orders", "scan", {});
  assert.ok(rows.result.Items.some(item => item.id.S === "api-1"));
  assert.ok(rows.result.Items.some(item => item.id.S === "workflow-1"));
  await run("Receipts", "put", {key:"receipt.json",body:'{"id":"receipt-1"}',contentType:"application/json"});
  const object = await run("Receipts", "get", {key:"receipt.json"});
  assert.equal(JSON.parse(object.result.Body).id, "receipt-1");
  const objects = await run("Receipts", "list", {});
  assert.ok(objects.result.Contents.some(item => item.Key === "receipt.json"));
  await run("Queue", "send", {payload:{id:"queued-1"}});
  const messages = await run("Queue", "peek", {});
  assert.ok(messages.result.Messages.some(item => JSON.parse(item.Body).id === "queued-1"));
  await run("Topic", "publish", {payload:{id:"sns-delivery"}});
  await run("Bus", "publish", {payload:{id:"event-delivery"}});
  let delivery;
  for (let attempt=0;attempt<8;attempt++) {
    delivery=await run("Orders","scan",{});
    if (["sns-delivery","event-delivery"].every(id=>delivery.result.Items.some(item=>item.id.S===id && item.status.S==="CONSUMED"))) break;
    await delay(1000);
  }
  assert.ok(delivery.result.Items.some(item=>item.id.S==="sns-delivery" && item.status.S==="CONSUMED"));
  assert.ok(delivery.result.Items.some(item=>item.id.S==="event-delivery" && item.status.S==="CONSUMED"));
  const logs = await run("Handler", "logs", {});
  assert.ok([...(logs.result.events || []), ...(logs.result.runtimeEvents || [])].some(event => event.message.includes("evaluation handler called")));
  const role = await run("Role", "aws-api", {operation:"get_role",parameters:{RoleName:find("Role").PhysicalResourceId}});
  assert.ok(role.result.Role.Arn.includes(deployed.result.accountId));
  const script = await api(`${root}/jobs`, "POST", {kind:"SCRIPT",deploymentId,resourceId:"scripts/probe.py",action:"run",input:{}}, 202);
  const scriptResult = await waitJob(`${root}/jobs`,script.id);
  assert.equal(scriptResult.result.exitCode,0);
  await api(`${root}/jobs/${workflow.id}`, "GET",null,403,outsider.token);
  const evidence = await api(`${root}/jobs/${workflow.id}/evidence`);
  assert.equal(evidence.sourceHash,deployed.sourceHash);
  await api(`/artifact/${artifact.id}/files`,"PUT",{path:"README.md",content:"Refined again after deployment"});
  const afterEdit = await api(root);
  assert.equal(afterEdit.deployment.sourceHash,deployed.sourceHash);
  assert.notEqual(afterEdit.artifacts[0].updatedAt,afterEdit.deployment.artifactUpdatedAt);
  secondProject = await api("/projects", "POST", {name:"Test environment isolation",description:"Separate emulator with the same refined sources"});
  const secondRoot = `/projects/${secondProject.id}/test-environment`;
  const secondImport = new FormData();
  secondImport.append("file", new Blob([await readFile("methodology/modeling-framework/samples/architecture-patterns/arch-http/arch-http.awspsm.xmi")]), "isolation.xmi");
  const secondImported = await api(`/psm/import?projectId=${secondProject.id}&format=xmi`, "POST", secondImport);
  const secondModel = await api("/psm", "POST", {projectId:secondProject.id,name:"Isolation",model:secondImported.modelJson});
  const secondGeneration = await waitJob("/transformations/jobs", (await api("/transformations/psm-to-artifact", "POST",
    {sourceModelId:secondModel.id,expectedRevision:secondModel.revision},202)).id);
  const secondArtifact = await api(`/artifact/${secondGeneration.resultArtifactId}`);
  const secondZip = spawnSync("python", ["-c", "import sys,json,io,zipfile; f=json.load(sys.stdin); b=io.BytesIO(); z=zipfile.ZipFile(b,'w'); [z.writestr(k,v) for k,v in f.items()]; z.close(); sys.stdout.buffer.write(b.getvalue())"], {input:JSON.stringify(files)});
  assert.equal(secondZip.status,0);
  const secondSources = new FormData(); secondSources.append("file",new Blob([secondZip.stdout]),"sources.zip");
  await api(`/artifact/${secondArtifact.id}/source-archive`, "POST", secondSources);
  const secondDeploy = await waitJob(`${secondRoot}/jobs`, (await api(`${secondRoot}/jobs`, "POST",
    {kind:"DEPLOY",artifactId:secondArtifact.id,input:{templates:["template.yaml"]}},202)).id);
  assert.notEqual(secondDeploy.environment.container,deployed.environment.container);
  assert.notEqual(secondDeploy.environment.network,deployed.environment.network);
  const secondFind = name => secondDeploy.result.resources.find(resource=>resource.LogicalResourceId===name);
  const secondScan = await waitJob(`${secondRoot}/jobs`, (await api(`${secondRoot}/jobs`, "POST",{
    kind:"EXECUTE",deploymentId:secondDeploy.id,resourceId:secondFind("Orders").id,action:"scan",input:{}},202)).id);
  assert.equal(secondScan.result.Items.length,0,"Project A's data must not appear in project B");
  const secondAuth = await waitJob(`${secondRoot}/jobs`, (await api(`${secondRoot}/jobs`, "POST",{
    kind:"EXECUTE",deploymentId:secondDeploy.id,resourceId:secondFind("Api").id,action:"request",
    input:{method:"GET",path:"/secure",stage:"test",headers:{Authorization:"Bearer evaluation-valid"}}},202)).id);
  assert.equal(secondAuth.result.statusCode,200);
  const crossApi = await waitJob(`${secondRoot}/jobs`, (await api(`${secondRoot}/jobs`, "POST",{
    kind:"EXECUTE",deploymentId:secondDeploy.id,resourceId:secondFind("Api").id,action:"aws-api",
    input:{operation:"get_rest_api",parameters:{restApiId:find("Api").PhysicalResourceId}}},202)).id,"FAILED");
  assert.match(crossApi.error,/NotFound|Invalid API|not found/i);
  await writeFile("target/test-environment-isolation.json",JSON.stringify({
    first:deployed.environment,second:secondDeploy.environment,secondItems:secondScan.result.Items,
    crossApiError:crossApi.error,secondAuthentication:secondAuth.result.statusCode},null,2));
  await api(`/projects/${secondProject.id}`,"DELETE");
  secondProject = null;
  console.log("Dedicated environment checks passed: distinct containers/networks, separate data, bearer authentication in both projects, and cross-instance API rejection.");
  console.log("Backend execution checks passed: Lambda success/error, REST/HTTP APIs, workflows success/error/history, DynamoDB, S3, SQS, SNS, EventBridge, logs, scripts, isolation and snapshot evidence.");

  await verifyBrowser({api,project,token,userId:guest.user.id,workflow});
  await verifyRedeployment((path,method="GET",body)=>api(path,method,body,method==="POST" && path.endsWith("/jobs")?202:200),root,artifact.id);
  await writeFile("target/test-environment-evidence.json",JSON.stringify({deployment:deployed,workflow,api:rest,checks:"passed"},null,2));
  if (keep) await writeFile("target/test-environment-session.json",JSON.stringify({token,projectId:project.id,userId:guest.user.id}));
} catch(error) {
  if(page) await page.screenshot({path:"target/test-environment-browser-failure.png"}).catch(()=>{});
  throw error;
} finally {
  await browser?.close();
  if(secondProject) await api(`/projects/${secondProject.id}`,"DELETE");
  if(project && !keep) await api(`/projects/${project.id}`,"DELETE");
}
