// Explicit live lifecycle check; restarts the local backend and this fixture's Floci instance.
// Run after TEST_KEEP_PROJECT=1 node apps/frontend/tests/test-environment.live.mjs.
import assert from "node:assert/strict";
import { readFile, writeFile } from "node:fs/promises";
import { spawnSync } from "node:child_process";

const session = JSON.parse(await readFile("target/test-environment-session.json","utf8"));
const backend = process.env.TEST_BACKEND_URL || "http://127.0.0.1:8080";
const root = `/projects/${session.projectId}/test-environment`;
const delay = ms => new Promise(resolve => setTimeout(resolve,ms));
async function api(path,method="GET",body,expected=200) {
  const response = await fetch(`${backend}/api${path}`,{method,headers:{"X-Auth-Token":session.token,
    ...(body?{"Content-Type":"application/json"}:{})},body:body?JSON.stringify(body):undefined});
  const data = await response.json();
  assert.equal(response.status,expected,JSON.stringify(data));
  return data;
}
async function wait(id) {
  let job;
  for (let attempt=0;attempt<900;attempt++) {
    job=await api(`${root}/jobs/${id}`);
    if (!["QUEUED","RUNNING"].includes(job.status)) break;
    await delay(1000);
  }
  if (job.status!=="SUCCEEDED") await writeFile("target/test-environment-lifecycle-failure.json",JSON.stringify(job,null,2));
  assert.equal(job.status,"SUCCEEDED",job.error);
  return job;
}
function docker(args) {
  return spawnSync("docker",args,{encoding:"utf8",timeout:60000,windowsHide:true});
}

const before=await api(root);
assert.equal(before.deploymentReady,true);
let restart=docker(["restart",process.env.TEST_BACKEND_CONTAINER || "modriss-backend-1"]);
assert.equal(restart.status,0,restart.stderr);
let ready=false;
for(let attempt=0;attempt<120;attempt++) {
  try { ready=(await fetch(`${backend}/actuator/health/readiness`)).ok; } catch {}
  if(ready) break;
  await delay(1000);
}
assert.equal(ready,true,"Backend must recover after restart");
const afterBackend=await api(root);
assert.equal(afterBackend.environment.id,before.environment.id);
assert.equal(afterBackend.deploymentReady,true);
const resource=before.deployment.result.resources.find(item=>item.LogicalResourceId==="Api");
const input={kind:"EXECUTE",deploymentId:before.deployment.id,resourceId:resource.id,action:"request",
  input:{method:"GET",path:"/secure",stage:"test",headers:{Authorization:"Bearer evaluation-valid"}}};
const afterRestart=await wait((await api(`${root}/jobs`,"POST",input,202)).id);
assert.equal(afterRestart.result.statusCode,200);
console.log("Backend restart retained the dedicated emulator, deployment identity, and bearer-authenticated execution.");

restart=docker(["restart",before.environment.container]);
assert.equal(restart.status,0,restart.stderr);
const stale=await api(root);
assert.equal(stale.deploymentReady,false);
const rejected=await api(`${root}/jobs`,"POST",input,409);
const evidence=await api(`${root}/jobs/${before.deployment.id}/evidence`);
assert.equal(evidence.sourceHash,before.deployment.sourceHash);
const redeployed=await wait((await api(`${root}/jobs`,"POST",{kind:"DEPLOY",artifactId:before.deployment.artifactId,
  input:{templates:["template.yaml"]}},202)).id);
assert.notEqual(redeployed.environment.id,before.environment.id);
assert.equal((await api(root)).deploymentReady,true);
const table=redeployed.result.resources.find(item=>item.LogicalResourceId==="Orders");
const rows=await wait((await api(`${root}/jobs`,"POST",{kind:"EXECUTE",deploymentId:redeployed.id,
  resourceId:table.id,action:"scan",input:{}},202)).id);
assert.ok(rows.result.Items.some(item=>item.id.S==="api-1"),"Persistent emulator data survives restart and redeployment");
console.log("Emulator restart blocked stale execution, retained evidence, and recovered by redeployment with existing data.");

const isolation=JSON.parse(await readFile("target/test-environment-isolation.json","utf8"));
const removed=isolation.second;
let cleaned=false;
for(let attempt=0;attempt<180;attempt++) {
  cleaned=docker(["container","inspect",removed.container]).status!==0
    && docker(["network","inspect",removed.network]).status!==0
    && docker(["volume","inspect",removed.network.replace("modriss-testnet-","modriss-testdata-")]).status!==0;
  if(cleaned) break;
  await delay(1000);
}
assert.equal(cleaned,true,"Deleted project's emulator, network and data volume must be cleaned");
assert.equal(docker(["ps","-a","--filter",`label=floci_namespace=${removed.container}`,"--format","{{.ID}}"])
  .stdout.trim(),"","Deleted project's child containers must be cleaned");
await writeFile("target/test-environment-lifecycle.json",JSON.stringify({before:before.environment,
  afterBackend:afterBackend.environment,afterEmulator:redeployed.environment,staleRequest:rejected,
  retainedItems:rows.result.Items.length,deletedProjectCleaned:cleaned},null,2));
console.log("Deleted project cleanup passed for the emulator, child containers, network, and data volume.");
