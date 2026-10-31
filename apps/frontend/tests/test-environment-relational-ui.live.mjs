// Run after the relational live check, against the same isolated session.
import assert from "node:assert/strict";
import {readFile, writeFile} from "node:fs/promises";
import {chromium} from "../../landing/node_modules/playwright/index.mjs";

assert.ok(process.env.TEST_RELATIONAL_SESSION, "TEST_RELATIONAL_SESSION is required");
const session = JSON.parse(await readFile(process.env.TEST_RELATIONAL_SESSION, "utf8"));
const {token, userId} = JSON.parse(await readFile(process.env.TEST_AUTH_SESSION || "target/test-environment-session.json", "utf8"));
const api = async path => {
  const response = await fetch((process.env.TEST_BACKEND_URL || "http://127.0.0.1:8080") + "/api" + path,
    {headers:{"X-Auth-Token":token}});
  assert.ok(response.ok); return response.json();
};
const project = await api(`/projects/${session.projectId}`);
const overview = await api(`/projects/${session.projectId}/test-environment`);
assert.equal(overview.deployment.status, "SUCCEEDED");
assert.equal(overview.deploymentReady, true, "Redeploy the isolated project after an emulator restart before running browser checks");
const database = overview.deployment.result.resources.find(resource => resource.ResourceType === "AWS::RDS::DBCluster");
const browser = await chromium.launch({headless:true});
const page = await browser.newPage({viewport:{width:1512,height:1000}});
page.setDefaultTimeout(90000);
const errors = [];
page.on("pageerror", error => errors.push(error.message));
await page.addInitScript(({token, userId, projectId}) => {
  localStorage.setItem("modriss.authToken", token);
  localStorage.setItem(`modriss.lastProjectId:${userId.toLowerCase()}`, projectId);
  localStorage.setItem("modriss.editorTutorial:guest", JSON.stringify({completed:true}));
}, {token, userId, projectId:session.projectId});
try {
  await page.goto(process.env.TEST_EDITOR_URL || "http://editor.localhost:8088");
  await page.locator("#projectLabel").filter({hasText:project.name}).waitFor({state:"attached"});
  await page.locator('#modelTabs [data-type="artifact"]').click();
  await page.locator('#modelTabs [data-type="test"]').click();
  await page.locator(`[data-resource="${database.id}"]`).click();
  await page.locator("#teAction").selectOption("tables");
  await page.getByRole("button", {name:"Browse tables and columns", exact:true}).click();
  await page.locator(".te-run .te-badge").filter({hasText:"SUCCEEDED"}).waitFor();
  await page.locator(".te-data-table th").filter({hasText:"data_type"}).waitFor();
  await page.screenshot({path:"target/test-environment-relational-schema.png"});
  await page.locator(".te-properties summary").filter({hasText:"modeled tables"}).click();
  await page.locator(`[data-sql-table="${database.database.tables[0].name}"]`).click();
  assert.equal(await page.locator("#teAction").inputValue(), "query");
  assert.ok(JSON.parse(await page.locator("#teInput").inputValue()).sql.includes(database.database.tables[0].name));
  const table = '"' + database.database.tables[0].name.replaceAll('"', '""') + '"';
  await page.locator("#teInput").fill(JSON.stringify({sql:`SELECT current_database() AS database, 125.50::numeric AS amount, (SELECT count(*) FROM ${table}) AS retained_rows`,parameters:[]}));
  const request = page.waitForResponse(response => response.url().endsWith("/jobs") && response.request().method() === "POST");
  await page.getByRole("button", {name:"Run SQL", exact:true}).click();
  const submitted = await (await request).json();
  await page.locator(".te-run .te-badge").filter({hasText:"SUCCEEDED"}).waitFor();
  await page.locator(".te-data-table th").filter({hasText:"amount"}).waitFor();
  assert.ok((await page.locator(".te-data-table").innerText()).includes(database.database.database));
  const execution = await api(`/projects/${session.projectId}/test-environment/jobs/${submitted.id}`);
  assert.ok(Number(execution.result.rows[0].retained_rows) >= 3, "Earlier live evaluation data must remain after restoration");
  await page.locator(".te-run").scrollIntoViewIfNeeded();
  await page.screenshot({path:"target/test-environment-relational-desktop.png"});
  await page.setViewportSize({width:390,height:844});
  await page.locator(".te-inspector").scrollIntoViewIfNeeded();
  await page.screenshot({path:"target/test-environment-relational-mobile-inspector.png"});
  await page.locator(".te-run").scrollIntoViewIfNeeded();
  await page.screenshot({path:"target/test-environment-relational-mobile.png"});
  assert.ok(await page.locator("#testEnvironment").evaluate(element => element.scrollWidth <= element.clientWidth + 2));
  assert.deepEqual(errors, []);
  await writeFile("target/test-environment-relational-ui.json", JSON.stringify({projectId:project.id, executionId:submitted.id,
    schemaGrid:true, sqlGrid:true, tableSelection:true, retainedRows:execution.result.rows[0].retained_rows,
    mobileOverflow:false, pageErrors:errors}, null, 2));
  console.log("Relational browser checks passed: live SQL, schema/results grids, table selection, desktop/mobile, and no page errors.");
} catch(error) {
  await page.screenshot({path:"target/test-environment-relational-browser-failure.png"}).catch(()=>{});
  await writeFile("target/test-environment-relational-layout-failure.json", JSON.stringify(await page.locator("#testEnvironment").evaluate(host => {
    const edge = host.getBoundingClientRect().right;
    return {width:host.clientWidth, scrollWidth:host.scrollWidth,
      overflowing:[...host.querySelectorAll("*")].filter(element => element.getBoundingClientRect().right > edge + 2)
        .slice(0, 40).map(element => ({tag:element.tagName, class:element.className,
          width:element.clientWidth, right:element.getBoundingClientRect().right}))};
  }), null, 2)).catch(()=>{});
  throw error;
} finally { await browser.close(); }
