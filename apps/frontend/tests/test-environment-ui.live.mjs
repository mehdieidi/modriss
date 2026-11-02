import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { pathToFileURL } from "node:url";
import { chromium } from "../../landing/node_modules/playwright/index.mjs";

export async function verifyBrowser({api,project,token,userId,workflow}) {
  const browser = await chromium.launch({headless:true});
  const page = await browser.newPage({viewport:{width:1512,height:1000}});
  page.setDefaultTimeout(60000);
  const errors=[]; page.on("pageerror",failure=>errors.push(failure.message));
  await page.addInitScript(({token,projectId,userId})=>{
    localStorage.setItem("modriss.authToken",token);
    localStorage.setItem(`modriss.lastProjectId:${userId.toLowerCase()}`,projectId);
    localStorage.setItem("modriss.editorTutorial:guest",JSON.stringify({completed:true}));
  },{token,projectId:project.id,userId});
  try {
    await page.goto(process.env.TEST_EDITOR_URL || "http://editor.localhost:8088");
    await page.locator("#projectLabel").filter({hasText:project.name}).waitFor({state:"attached"});
    for (const level of ["cim", "pim", "psm"]) {
      await page.locator(`#modelTabs [data-type="${level}"]`).click();
      await page.locator(`#modelTabs [data-type="${level}"].active`).waitFor();
      assert.equal(await page.locator('#modelTabs [data-type="test"]').isVisible(),false);
      assert.equal(await page.locator('#deployTestBtn').isVisible(),false);
    }
    await page.locator('#modelTabs [data-type="artifact"]').click();
    await page.locator('#modelTabs [data-type="test"]').waitFor({state:"visible"});
    assert.equal(await page.locator('#modelTabs [data-type="test"]').isVisible(),true);
    assert.equal(await page.locator('.model-lifecycle-group-deliver #deployTestBtn').isVisible(),true);
    assert.equal(await page.locator('#modelTabs #deployTestBtn').count(),0);
    const colors = await page.evaluate(()=>["deployTestBtn","generateContextBtn"].map(id=>
      getComputedStyle(document.getElementById(id)).backgroundColor));
    assert.notEqual(colors[0],colors[1],"Deploy and Download must use different colors");
    await page.screenshot({path:"target/test-environment-artifact-controls.png"});
    await page.locator('#deployTestBtn').click();
    await page.getByRole("heading",{name:"Deploy a project snapshot",exact:true}).waitFor();
    await page.locator('#teArtifact').waitFor({state:"visible"});
    assert.ok(await page.locator('#teArtifact option').count() > 0,
      "Saved AWS artifacts must remain available after opening the deployment view");
    assert.equal(await page.locator('#deployTestBtn').isVisible(),false);
    assert.equal(await page.locator('#modelTabs [data-type="test"]').isVisible(),false);
    await page.locator('#modelTabs [data-type="artifact"]').click();
    await page.locator('#modelTabs [data-type="test"]').click();
    await page.getByRole("heading",{name:"Test environment",exact:true}).waitFor();
    await page.getByText("Saved artifacts have changed",{exact:false}).waitFor();
    await page.locator('[data-view="resources"]').click();
    await page.locator('[data-resource]').filter({has:page.getByText("Api",{exact:true})}).click();
    await page.locator("#teInput").fill(JSON.stringify({method:"GET",path:"/secure",stage:"test",
      headers:{Authorization:"Bearer evaluation-valid"}}));
    await page.locator("#teScenario").fill("UC-AUTH · Standard bearer header");
    await page.locator('[data-te="execute"]').click();
    await page.locator('.te-run .te-badge').filter({hasText:"SUCCEEDED"}).waitFor();
    assert.match(await page.locator(".te-response").innerText(),/Bearer evaluation-valid/);
    await page.locator('[data-te="refresh"]').click();
    await page.waitForFunction(()=>!document.querySelector('[data-te="refresh"]')?.disabled
      && document.getElementById("teInput")?.value.includes("Bearer evaluation-valid"));
    await page.locator(".te-run").scrollIntoViewIfNeeded();
    await page.screenshot({path:"target/test-environment-bearer.png",fullPage:true});
    await page.locator('[data-resource]').filter({hasText:"Workflow"}).click();
    assert.ok(await page.locator(".te-state").count() >= 3);
    await page.locator('[data-resource]').filter({hasText:"Handler"}).first().click();
    await page.locator("#teInput").fill(JSON.stringify({payload:{id:"browser-1"}}));
    await page.locator("#teScenario").fill("UC-BROWSER · Invoke refined function");
    await page.locator('[data-te="execute"]').click();
    await page.locator('.te-run .te-badge').filter({hasText:"SUCCEEDED"}).waitFor();
    const custom = new FormData();
    custom.append("name","Azure visibility " + Date.now());
    custom.append("ecore",new Blob([await readFile("methodology/modeling-framework/samples/custom-psm-azure/azure.ecore")]),"azure.ecore");
    const platform = await api(`/projects/${project.id}/psm/packages`,"POST",custom,201);
    await page.evaluate(async id => {
      const module = await import('/js/cloud-providers.js');
      await module.loadCloudProviders();
      await module.selectCloudProvider(id,{openLevel:'artifact'});
    },platform.id);
    assert.equal(await page.locator('#modelTabs [data-type="test"]').isVisible(),false);
    assert.equal(await page.locator('#deployTestBtn').isVisible(),false);
    await page.evaluate(async () => {
      await (await import('/js/cloud-providers.js')).selectCloudProvider('aws',{openLevel:'artifact'});
    });
    assert.equal(await page.locator('#modelTabs [data-type="test"]').isVisible(),true);
    assert.equal(await page.locator('#deployTestBtn').isVisible(),true);
    await page.locator('#modelTabs [data-type="test"]').click();
    await page.locator('#modelTabs [data-type="test"]').waitFor({state:"hidden"});
    assert.equal(await page.locator('#modelTabs [data-type="test"]').isVisible(),false);
    assert.equal(await page.locator('#deployTestBtn').isVisible(),false);
    await page.locator('[data-view="runs"]').click();
    await page.locator(`[data-job="${workflow.id}"]`).click();
    await page.locator(".te-run").filter({hasText:"SUCCEEDED"}).waitFor();
    await page.screenshot({path:"target/test-environment-desktop.png"});
    await page.locator('[data-view="resources"]').click();
    await page.locator('[data-resource]').filter({hasText:"Workflow"}).click();
    assert.ok(await page.locator(".te-state-succeeded").count() >= 2);
    await page.locator('[data-state="Invoke"]').press("Enter");
    await page.locator(".te-state-detail").waitFor();
    await page.screenshot({path:"target/test-environment-workflow.png"});
    await page.evaluate(()=>document.documentElement.classList.remove("light"));
    await page.screenshot({path:"target/test-environment-dark.png"});
    await page.evaluate(()=>document.documentElement.classList.add("light"));
    await page.setViewportSize({width:390,height:844});
    await page.screenshot({path:"target/test-environment-mobile.png"});
    assert.ok(await page.locator("#testEnvironment").evaluate(el=>el.scrollWidth<=el.clientWidth+2));
    assert.deepEqual(errors,[]);
    console.log("Browser checks passed: AWS-only controls, real invocation, navigation, workflow state evidence, history, stale-source notice, desktop/mobile and light/dark layouts.");
  } catch(error) {
    await page.screenshot({path:"target/test-environment-browser-failure.png"}).catch(()=>{});
    throw error;
  } finally { await browser.close(); }
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  const session=JSON.parse(await readFile("target/test-environment-session.json","utf8"));
  async function api(path,method="GET",body,expected=200) {
    const response=await fetch(`${process.env.TEST_BACKEND_URL || "http://127.0.0.1:8080"}/api${path}`,{method,
      headers:{"X-Auth-Token":session.token,...(body && !(body instanceof FormData)?{"Content-Type":"application/json"}:{})},
      body:body instanceof FormData?body:body?JSON.stringify(body):undefined});
    const data=await response.json();
    assert.equal(response.status,expected,data.message);return data;
  }
  const project=await api(`/projects/${session.projectId}`);
  const root=`/projects/${project.id}/test-environment`;
  const overview=await api(root);
  const successful=overview.jobs.find(job=>job.action==="start" && job.status==="SUCCEEDED");
  const workflow=await api(`${root}/jobs/${successful.id}`);
  await verifyBrowser({api,project,token:session.token,userId:session.userId,workflow});
}
