import assert from "node:assert/strict";
import { readFile, writeFile } from "node:fs/promises";
import { pathToFileURL } from "node:url";

export async function verifyRedeployment(api, root, artifactId) {
  const before = (await api(root)).deployment;
  async function wait(id) {
    let job;
    for (let attempt = 0; attempt < 900; attempt++) {
      job = await api(`${root}/jobs/${id}`);
      if (!["QUEUED", "RUNNING"].includes(job.status)) break;
      await new Promise(resolve => setTimeout(resolve, 1000));
    }
    if (job.status !== "SUCCEEDED") await writeFile("target/test-environment-redeploy-failure.json",JSON.stringify(job,null,2));
    assert.equal(job.status,"SUCCEEDED",job.error);
    return job;
  }
  const same = await wait((await api(`${root}/jobs`,"POST",{kind:"DEPLOY",artifactId,input:{templates:["template.yaml"]}})).id);
  assert.ok(same.events.some(event=>event.title?.includes("Infrastructure template unchanged")));
  const artifact = await api(`/artifact/${artifactId}`);
  const source = artifact.files["src/handler/main.go"];
  const revision = `evaluation handler revision ${Date.now()}`;
  const revised = source.replace(/evaluation handler (called|revision \d+)/, revision);
  assert.notEqual(revised,source,"The evaluation fixture must contain its handler log marker");
  await api(`/artifact/${artifactId}/files`,"PUT",{path:"src/handler/main.go",content:revised});
  const updated = await wait((await api(`${root}/jobs`,"POST",{kind:"DEPLOY",artifactId,input:{templates:["template.yaml"]}})).id);
  assert.ok(updated.events.some(event=>event.title === "lambda.update_function_code"));
  const find = name => updated.result.resources.find(resource=>resource.LogicalResourceId===name);
  for (const name of ["Orders","Queue","DeliveryQueue","Handler"]) {
    assert.equal(find(name).PhysicalResourceId,before.result.resources.find(resource=>resource.LogicalResourceId===name).PhysicalResourceId);
  }
  const request = async(name,action,input) => wait((await api(`${root}/jobs`,"POST",{
    kind:"EXECUTE",deploymentId:updated.id,resourceId:find(name).id,action,input,scenario:"Redeployment regression"})).id);
  const invoked = await request("Handler","invoke",{payload:{id:"revision-2"}});
  assert.equal(invoked.result.Payload.ok,true);
  assert.ok(invoked.events.some(event=>event.type==="runtime-log" && event.message.includes(revision)));
  const rows = await request("Orders","scan",{});
  assert.ok(rows.result.Items.some(item=>item.id.S==="api-1"),"Existing DynamoDB data survives a code-only deployment");
  await writeFile("target/test-environment-redeployment.json",JSON.stringify({unchanged:same,updated,invoked,retainedData:rows.result},null,2));
  console.log("Redeployment checks passed: unchanged infrastructure, real Lambda package updates, new code execution, stable resource identities, and retained data.");
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  const session = JSON.parse(await readFile("target/test-environment-session.json","utf8"));
  const backend = process.env.TEST_BACKEND_URL || "http://127.0.0.1:8080";
  async function api(path,method="GET",body) {
    const response = await fetch(`${backend}/api${path}`,{method,headers:{"X-Auth-Token":session.token,
      ...(body?{"Content-Type":"application/json"}:{})},body:body?JSON.stringify(body):undefined});
    const data = await response.json();
    assert.ok(response.ok,`${method} ${path}: ${data.message}`);
    return data;
  }
  const root = `/projects/${session.projectId}/test-environment`;
  await verifyRedeployment(api,root,(await api(root)).deployment.artifactId);
}
