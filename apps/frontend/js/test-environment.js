import { state } from "./state.js";
import { api } from "./api.js";
import { escapeHtml as h } from "./utils.js";
import { enhanceSelects } from "./themed-select.js";
import {
  resourceGroup,
  workflowGraph,
  layoutWorkflowGraph,
  workflowStates,
  initialInput,
} from "./test-environment-utils.mjs?v=mjs-module-mime-20261101";

let scope = "",
  overview = null,
  selected = "",
  activeJob = null,
  timer = null;
let filter = "All",
  search = "",
  view = "resources",
  action = "",
  loading = false,
  error = "";
let expandedEvent = null,
  selectedState = "",
  deploying = false,
  generation = 0,
  workflowZoom = 1,
  eventScrollJobId = null,
  eventScrollTop = 0,
  followLatestEvents = true,
  runScrollContext = "",
  runScrollPositions = new Map();
const requestDrafts = new Map();
const host = () => document.getElementById("testEnvironment");
const base = () => `/projects/${state.project.id}/test-environment`;
const isAws = () => (state.cloudProvider?.id || "aws") === "aws";
const isOpen = () => state.activeType === "test" && isAws();
const running = (job) => ["QUEUED", "RUNNING"].includes(job?.status);
const busy = () => deploying || (overview?.jobs || []).some(running) || running(activeJob);
const environmentReady = () => {
  const attempt = (overview?.jobs || []).find((job) => job.kind === "DEPLOY");
  return (
    overview?.deploymentReady &&
    overview?.deployment &&
    (!attempt || attempt.id === overview.deployment.id)
  );
};
const json = (value) => JSON.stringify(value, null, 2);
const pretty = (value) => (typeof value === "string" ? value : json(value));
const stamp = (value) => (value ? new Date(value).toLocaleString() : "");
const badge = (status) =>
  `<span class="te-badge te-${h(String(status || "ready").toLowerCase())}">${h(status || "Ready")}</span>`;
const button = (key, label, disabled = false, cls = "") =>
  `<button type="button" data-te="${h(key)}" class="te-button ${cls}" ${disabled ? "disabled" : ""}>${h(label)}</button>`;

function resources() {
  const deployment = overview?.deployment;
  return [
    ...(deployment?.result?.resources || []),
    ...(deployment?.scripts || []).map((path) => ({
      id: path,
      name: path,
      ResourceType: "Script",
      PhysicalResourceId: path,
      actions: ["run"],
    })),
  ];
}
function resource() {
  return resources().find((item) => item.id === selected);
}

export function renderTestControls() {
  const tabs = document.getElementById("modelTabs");
  if (!tabs) return;
  let tab = tabs.querySelector('[data-type="test"]');
  if (!tab) {
    tab = document.createElement("button");
    tab.className = "tab";
    tab.type = "button";
    tab.dataset.type = "test";
    tab.textContent = "Test";
    tab.title = "Deploy and test the AWS project on Floci";
    tabs.append(tab);
  }
  const showArtifactControls = isAws() && state.activeType === "artifact";
  tab.hidden = !showArtifactControls;
  tab.classList.toggle("active", state.activeType === "test");
  const deploy = document.getElementById("deployTestBtn");
  if (!deploy) return;
  if (!deploy.dataset.bound) {
    deploy.dataset.bound = "true";
    deploy.addEventListener("click", () => void showDeployment());
  }
  deploy.hidden = !showArtifactControls;
  deploy.disabled = !state.project?.id || deploying;
  deploy.setAttribute("aria-busy", deploying ? "true" : "false");
  deploy.querySelector(".topbar-btn-label").textContent = deploying
    ? "Deploying…"
    : "Deploy to Test Environment";
  deploy.title = "Build and deploy the saved AWS artifact snapshot to Floci";
}

function resetScope() {
  if (scope === state.project?.id) return;
  scope = state.project?.id || "";
  generation++;
  overview = activeJob = null;
  deploying = loading = false;
  eventScrollJobId = runScrollContext = null;
  eventScrollTop = 0;
  followLatestEvents = true;
  runScrollPositions.clear();
  selected = action = error = search = "";
  requestDrafts.clear();
  view = "resources";
  filter = "All";
  clearTimeout(timer);
}

async function refresh({ quiet = false } = {}) {
  resetScope();
  if (!scope) {
    render();
    return;
  }
  const project = scope,
    token = generation;
  if (!quiet) {
    loading = true;
    render();
  }
  try {
    const result = await api(base());
    if (scope !== project || generation !== token) return;
    overview = result;
    if (!selected || !resources().some((item) => item.id === selected))
      selected =
        resources().find((item) => resourceGroup(item) === "APIs")?.id ||
        resources().find((item) =>
          item.actions.some((value) => !["inspect", "aws-api"].includes(value)),
        )?.id ||
        resources()[0]?.id ||
        "";
    error = "";
    const job = (overview.jobs || []).find(running);
    if (job && !running(activeJob)) {
      const detail = await api(`${base()}/jobs/${job.id}`);
      if (scope === project && generation === token) activeJob = detail;
    }
  } catch (failure) {
    if (scope === project) error = failure.message;
  } finally {
    if (scope === project) {
      loading = false;
      render();
      schedule();
    }
  }
}

export async function openTestEnvironment() {
  resetScope();
  const node = host();
  if (!node) return;
  node.classList.remove("hidden");
  if (!node.dataset.bound) {
    node.dataset.bound = "true";
    node.addEventListener("click", (event) => void onClick(event));
    node.addEventListener("change", onChange);
    node.addEventListener("keydown", (event) => {
      if ((event.key === "Enter" || event.key === " ") && event.target.matches("[data-state]")) {
        event.preventDefault();
        void onClick(event);
      }
    });
    node.addEventListener("input", (event) => {
      if (event.target.id === "teSearch") {
        search = event.target.value;
        renderResourceList();
      }
    });
  }
  await refresh();
}

async function showDeployment() {
  if (!isAws() || !state.project?.id) return;
  try {
    await (await import("./model-ops.js")).switchTab("test");
    view = "deployment";
    render();
  } catch (failure) {
    error = failure.message;
    render();
  }
}

function schedule() {
  clearTimeout(timer);
  if (!isOpen() || !running(activeJob)) return;
  timer = setTimeout(() => void poll(), 1000);
}

async function poll() {
  if (!isOpen() || !activeJob) return;
  const project = scope,
    id = activeJob.id;
  try {
    const result = await api(`${base()}/jobs/${id}`);
    if (scope !== project || activeJob?.id !== id) return;
    activeJob = result;
    renderRun();
    if (!running(result)) {
      deploying = false;
      renderTestControls();
      await refresh({ quiet: true });
    } else schedule();
  } catch (failure) {
    error = failure.message;
    render();
  }
}

function render() {
  if (!host() || !isOpen()) return;
  const previousScrollTop = host().scrollTop;
  captureEventScroll(host());
  captureRunScroll(host());
  const previous = document.getElementById("teInput");
  if (previous?.dataset.project === scope && previous.dataset.draftKey)
    requestDrafts.set(previous.dataset.draftKey, previous.value);
  const deployment = overview?.deployment;
  const list = resources();
  const groups = ["Functions", "APIs", "Workflows", "Storage", "Events", "Scripts"];
  const stale =
    deployment &&
    overview.artifacts?.find((a) => a.id === deployment.artifactId)?.updatedAt !==
      deployment.artifactUpdatedAt;
  const changedEnvironment = deployment && !environmentReady();
  host().innerHTML = `<div class="te-header">
    <div><div class="te-eyebrow"><span class="te-status-dot"></span> AWS · FLOCI EMULATOR</div><h1>Test environment</h1><p>Execute the deployed project and inspect its responses, data, and execution history.</p></div>
    <div class="te-header-actions">${button("refresh", "Refresh", loading || busy())}${button("deployment", deployment ? "Redeploy project" : "Deploy project", busy(), "te-primary")}</div>
  </div>
  <div class="te-context"><span>${h(state.project?.name || "Choose a project")}</span><span>${h(overview?.region || "us-east-1")}</span><span>Account ${h(overview?.accountId || "—")}</span>${deployment ? `<span>Snapshot ${h(deployment.sourceHash?.slice(0, 10))}</span>${badge(changedEnvironment ? "REDEPLOY REQUIRED" : "DEPLOYED")}` : badge("NOT DEPLOYED")}</div>
  ${error ? `<div class="te-alert" role="alert">${h(error)}</div>` : ""}
  ${stale ? `<div class="te-notice">Saved artifacts have changed since this deployment. Tests use the deployed snapshot. Redeploy to test your latest edits.</div>` : ""}
  ${changedEnvironment ? '<div class="te-notice">Redeploy to restore this project’s test environment. Its instance may have restarted, an earlier deployment used the shared emulator, or a newer deployment attempt changed its resources. Previous execution evidence is retained.</div>' : ""}
  <div class="te-metrics">${groups.map((group) => `<button type="button" data-group="${group}" class="te-metric ${filter === group ? "is-active" : ""}"><span>${group}</span><strong>${list.filter((item) => resourceGroup(item) === group).length}</strong></button>`).join("")}</div>
  <nav class="te-tabs" aria-label="Test environment views">${[
    ["resources", "Resources"],
    ["runs", "Execution history"],
    ["deployment", "Deployment"],
  ]
    .map(
      ([key, label]) =>
        `<button type="button" data-view="${key}" aria-current="${view === key ? "page" : "false"}" class="${view === key ? "is-active" : ""}">${label}</button>`,
    )
    .join("")}</nav>
  <div class="te-content">${loading && !overview ? '<div class="te-empty" role="status">Loading your test environment…</div>' : view === "deployment" ? deploymentView() : view === "runs" ? historyView() : resourcesView()}</div>`;
  enhanceSelects(host());
  host().scrollTop = previousScrollTop;
  renderRun();
}

function eventListIsAtBottom(list) {
  return list.scrollHeight - list.clientHeight - list.scrollTop <= 28;
}

function captureEventScroll(root) {
  const list = root?.querySelector(".te-event-list");
  if (!list || eventScrollJobId !== activeJob?.id) return;
  eventScrollTop = list.scrollTop;
  followLatestEvents = eventListIsAtBottom(list);
}

function runScrollKey(element) {
  if (element.matches(".te-workflow-scroll")) return "workflow";
  if (element.matches(".te-data-table"))
    return `data-table:${[...element.parentElement.querySelectorAll(".te-data-table")].indexOf(element)}`;
  const event = element.closest(".te-event[data-event]");
  if (event) return `event-detail:${event.dataset.event}`;
  const response = element.closest(".te-response");
  if (response) return `response:${[...response.querySelectorAll("pre")].indexOf(element)}`;
  return `run-output:${[...element.closest(".te-run").querySelectorAll("pre")].indexOf(element)}`;
}

function runScrollables(root) {
  return [...(root?.querySelectorAll(".te-workflow-scroll, .te-data-table, .te-run pre") || [])];
}

function captureRunScroll(root) {
  if (!activeJob) return;
  const context = `${activeJob.id}:${selected}`;
  if (runScrollContext !== context) return;
  for (const element of runScrollables(root)) {
    runScrollPositions.set(runScrollKey(element), {
      top: element.scrollTop,
      left: element.scrollLeft,
      followsBottom: element.scrollHeight - element.clientHeight - element.scrollTop <= 28,
      followsRight: element.scrollWidth - element.clientWidth - element.scrollLeft <= 28,
    });
  }
}

function restoreRunScroll(root) {
  for (const element of runScrollables(root)) {
    const position = runScrollPositions.get(runScrollKey(element));
    if (!position) continue;
    element.scrollTop = position.followsBottom
      ? element.scrollHeight
      : Math.min(position.top, element.scrollHeight - element.clientHeight);
    element.scrollLeft = position.followsRight
      ? element.scrollWidth
      : Math.min(position.left, element.scrollWidth - element.clientWidth);
  }
}

function scrollToLatestRun() {
  requestAnimationFrame(() =>
    document.getElementById("teRun")?.scrollIntoView({ behavior: "smooth", block: "start" }),
  );
}

function resourcesView() {
  if (!overview?.deployment)
    return `<div class="te-empty"><div class="te-empty-mark">▷</div><h2>Your project, running locally</h2><p>Complete the generated implementation in Artifacts, then deploy a saved snapshot. Your APIs, functions, workflows, events, scripts, and storage will appear here.</p>${button("deployment", "Set up a deployment", false, "te-primary")}<div class="te-empty-path"><span>Saved artifacts</span><span>→</span><span>Build & deploy</span><span>→</span><span>Floci</span><span>→</span><span>Execution evidence</span></div></div>`;
  return `<div class="te-resource-layout"><aside class="te-explorer" aria-label="Deployed resources"><div class="te-explorer-heading"><strong>Project resources</strong>${button("all", "All")}</div><input type="search" id="teSearch" placeholder="Find a resource…" aria-label="Find a resource" value="${h(search)}" /><div id="teResourceList">${resourceList()}</div></aside><div class="te-detail">${inspector()}<div id="teRun"></div></div></div>`;
}

function resourceList() {
  const filtered = resources().filter(
    (item) =>
      (filter === "All" || resourceGroup(item) === filter) &&
      `${item.name} ${item.PhysicalResourceId} ${item.ResourceType}`
        .toLowerCase()
        .includes(search.toLowerCase()),
  );
  return (
    ["APIs", "Functions", "Workflows", "Storage", "Events", "Logs", "Scripts", "Infrastructure"]
      .map((group) => {
        const entries = filtered.filter((item) => resourceGroup(item) === group);
        if (!entries.length) return "";
        return `<div class="te-group"><h3>${group}<span>${entries.length}</span></h3>${entries.map((item) => `<button type="button" class="te-resource ${item.id === selected ? "is-active" : ""}" data-resource="${h(item.id)}" title="${h(item.PhysicalResourceId)}"><span class="te-resource-icon">${{ APIs: "↗", Functions: "λ", Workflows: "⑂", Storage: "▤", Events: "↝", Scripts: "›_", Logs: "≡" }[group] || "◇"}</span><span><strong>${h(item.name)}</strong><small>${h(item.ResourceType.replace("AWS::", ""))}</small></span></button>`).join("")}</div>`;
      })
      .join("") || '<p class="te-muted te-pad">No matching resources.</p>'
  );
}

function renderResourceList() {
  const node = document.getElementById("teResourceList");
  if (node) node.innerHTML = resourceList();
}

function inspector() {
  const item = resource();
  if (!item) return '<div class="te-empty">Choose a deployed resource.</div>';
  if (!item.actions.includes(action)) action = item.actions[0] || "inspect";
  const input = initialInput(action, item);
  const draftKey = json([scope, overview?.deployment?.id, item.id, action]);
  return `<section class="te-inspector"><div class="te-resource-heading"><div><span class="te-eyebrow">${h(resourceGroup(item))}</span><h2>${h(item.name)}</h2><p class="te-resource-address">${h(item.PhysicalResourceId)}</p></div>${badge(item.ResourceStatus?.replace(/_/g, " ") || "READY")}</div>
    ${item.discoveryError ? `<div class="te-alert">Resource discovery: ${h(item.discoveryError)}</div>` : ""}
    ${item.definition ? `<div class="te-workflow-heading"><strong>Workflow</strong><span>Actual state events appear during execution</span></div><div id="teWorkflow">${workflowView(item)}</div><div id="teStateDetail"></div>` : ""}
    ${item.routes?.length ? `<div class="te-routes" aria-label="API routes">${item.routes.map((route, i) => `<button type="button" data-route="${i}"><b>${h(route.method)}</b> ${h(route.path)}</button>`).join("")}</div>` : ""}
    ${item.database ? `<details class="te-properties"><summary>Database ${h(item.database.database)} · ${item.database.tables.length} modeled tables</summary><div class="te-object-list">${item.database.tables.map((table) => `<button type="button" data-sql-table="${h(table.name)}"><span>${h(table.name)}</span><small>${table.columns.length} columns</small></button>`).join("")}</div><p class="te-muted">Select a table to prepare a query. Transaction controls return an ID to include as transactionId in subsequent SQL inputs.</p></details>` : ""}
    <div class="te-invoke-heading"><div class="te-action-field"><span>Action</span><select id="teAction" aria-label="Action">${item.actions.map((value) => `<option value="${value}" ${value === action ? "selected" : ""}>${h(actionLabel(value))}</option>`).join("")}</select></div><span class="te-muted">${action === "peek" ? "Messages remain in the queue; consumers may still receive them." : item.ResourceType === "Script" ? "Runs the deployed script snapshot in an isolated backend container." : "Executes on the backend emulator"}</span></div>
    ${action === "aws-api" ? `<details class="te-properties"><summary>${h(item.sdk.service)} · available AWS operations</summary><p>Choose an operation and its AWS SDK parameters. Requests use this project's Floci account.</p><select id="teSdkOperation" aria-label="AWS SDK operation"><option value="">Choose an operation…</option>${item.sdk.operations.map((operation) => `<option value="${h(operation)}">${h(operation)}</option>`).join("")}</select></details>` : ""}
    ${action !== "inspect" ? `<label class="te-input-label" for="teInput">${action === "request" ? "HTTP request" : "Input"} <span>JSON · editable</span></label><textarea id="teInput" data-project="${h(scope)}" data-draft-key="${h(draftKey)}" class="te-json-input" spellcheck="false" rows="${action === "request" ? 12 : 7}" aria-label="Execution input JSON">${h(requestDrafts.get(draftKey) ?? json(input))}</textarea>` : `<details class="te-properties"><summary>Resource definition</summary><pre>${h(json(item))}</pre></details>`}
    <div class="te-invoke-footer"><label>Requirement / scenario <input id="teScenario" placeholder="e.g. UC-03 · Book a workshop" aria-label="Requirement or scenario" /></label>${button("execute", busy() ? "Operation in progress…" : environmentReady() ? actionLabel(action) : "Redeployment required", busy() || !environmentReady(), "te-primary")}</div></section>`;
}

function actionLabel(value) {
  return (
    {
      "aws-api": "Advanced AWS request",
      invoke: "Invoke function",
      start: "Start workflow",
      executions: "List executions",
      request: "Send request",
      scan: "Browse items",
      query: "Run SQL",
      tables: "Browse tables and columns",
      "begin-transaction": "Begin transaction",
      "commit-transaction": "Commit transaction",
      "rollback-transaction": "Roll back transaction",
      put: "Save data",
      get: "Read object",
      list: "Browse objects",
      send: "Send message",
      peek: "Inspect messages",
      publish: "Publish event",
      logs: "Read logs",
      inspect: "Inspect resource",
      run: "Run script",
    }[value] || value
  );
}

function deploymentView() {
  const artifacts = overview?.artifacts || [];
  const artifact = artifacts.find((item) => item.id === state.artifact.id) || artifacts[0];
  return `<div class="te-deployment-layout"><section class="te-card"><div class="te-eyebrow">SAVED PROJECT → FLOCI</div><h2>Deploy a project snapshot</h2><p>Build the refined implementation and provision its infrastructure. Deployment uses the saved files in this project’s dedicated Floci instance.</p>
  ${state.artifact.dirty ? '<div class="te-notice">The artifact editor contains unsaved changes. Save them in Artifacts before deploying.</div>' : ""}
  ${
    artifacts.length
      ? `<div class="te-field"><span>AWS artifact</span><select id="teArtifact" aria-label="AWS artifact">${artifacts.map((item) => `<option value="${h(item.id)}" ${item.id === artifact.id ? "selected" : ""}>${h(item.name)} · ${h(stamp(item.updatedAt))}</option>`).join("")}</select></div><div id="teDeploymentOptions">${deploymentOptions(artifact)}</div>
  <label class="te-field">Import refined project sources (optional ZIP)<input type="file" id="teSourceArchive" accept=".zip" ${busy() ? "disabled" : ""} /><span class="te-muted">Replaces this artifact’s source files with your local edits. UTF-8 text; build output is excluded.</span></label>
  <label class="te-input-label" for="teParameters">Template parameters <span>JSON key / value pairs</span></label><textarea id="teParameters" class="te-json-input" rows="4" spellcheck="false">{}</textarea>
  <div class="te-invoke-footer"><span class="te-muted">Build logs and provisioning events are retained.</span>${button("deploy", "Build & deploy to Floci", busy() || state.artifact.dirty, "te-primary")}</div>`
      : '<div class="te-notice">Generate AWS artifacts first. Open the AWS model and choose Generate artifacts.</div>'
  }</section>
  <aside class="te-card te-deployment-summary"><h3>Current deployment</h3>${overview?.deployment ? `<dl><dt>Artifact</dt><dd>${h(overview.deployment.artifactName)}</dd><dt>Deployed</dt><dd>${h(stamp(overview.deployment.finishedAt))}</dd><dt>Source SHA-256</dt><dd class="te-hash">${h(overview.deployment.sourceHash)}</dd><dt>Resources</dt><dd>${overview.deployment.result?.resources?.length || 0}</dd></dl>${activeJob?.id === overview.deployment.id ? "" : deploymentProfileNotice(overview.deployment.result?.deploymentProfile)}${button("show-deployment", "View deployment evidence")}` : "<p>No successful deployment yet.</p>"}<h3>How this runs</h3><ol><li>Capture the saved artifact files</li><li>Run the project’s build script</li><li>Package local Lambda and workflow files</li><li>Provision the selected stacks on Floci</li><li>Discover deployed resources</li></ol></aside></div><div id="teRun"></div>`;
}

function deploymentOptions(artifact) {
  return `<fieldset class="te-template-list"><legend>Stacks to deploy</legend>${artifact.templates?.length ? artifact.templates.map((path) => `<label><input type="checkbox" name="teTemplate" value="${h(path)}" checked /> ${h(path)}</label>`).join("") : "<p>Templates will be detected from the saved project files.</p>"}</fieldset>`;
}

function historyView() {
  const jobs = overview?.jobs || [];
  return `<div class="te-history-layout"><aside class="te-card te-history-list"><h2>Execution history <span>${jobs.length}</span></h2><p>Deployment snapshots, scenario inputs, and observed results.</p>${jobs.map((job) => `<button type="button" data-job="${h(job.id)}" class="te-history-item ${activeJob?.id === job.id ? "is-active" : ""}"><div>${badge(job.status)}<small>${h(stamp(job.createdAt))}</small></div><strong>${h(job.scenario || job.action || job.kind)}</strong><span>${h(job.resourceId?.split("/").pop() || job.artifactName || "Project deployment")}</span></button>`).join("") || "<p>No runs yet. Execute a resource to record evidence.</p>"}${overview?.historyCursor ? button("more-history", "Load older runs") : ""}</aside><div id="teRun"></div></div>`;
}

function workflowView(item) {
  const graph = layoutWorkflowGraph(workflowGraph(item.definition));
  const events = activeJob?.resourceId === item.id ? activeJob.events || [] : [];
  const states = workflowStates(events);
  const positions = new Map(graph.nodes.map((node) => [node.id, node]));
  const statusLabel = (status) =>
    ({ running: "Running", succeeded: "Succeeded", failed: "Failed" })[status] || "Not run";
  const trim = (value, length) =>
    value.length > length ? `${value.slice(0, length - 1)}…` : value;
  const start = positions.get(graph.startAt);
  const startX = start ? start.x + start.width / 2 : graph.width / 2;
  const edges = graph.edges
    .map((edge) => {
      const source = positions.get(edge.source);
      const target = positions.get(edge.target);
      if (!source || !target) return "";
      let path, labelX, labelY;
      if (target.y > source.y) {
        const x1 = source.x + source.width / 2;
        const y1 = source.y + source.height;
        const x2 = target.x + target.width / 2;
        const y2 = target.y;
        const middleY = y1 + (y2 - y1) / 2;
        path = `M${x1} ${y1} C${x1} ${middleY}, ${x2} ${middleY}, ${x2} ${y2}`;
        labelX = (x1 + x2) / 2;
        labelY = middleY - 5;
      } else {
        const x1 = source.x + source.width;
        const y1 = source.y + source.height / 2;
        const x2 = target.x + target.width;
        const y2 = target.y + target.height / 2;
        const bendX = graph.width - 14;
        path = `M${x1} ${y1} C${bendX} ${y1}, ${bendX} ${y2}, ${x2} ${y2}`;
        labelX = bendX - 8;
        labelY = (y1 + y2) / 2 - 5;
      }
      return `<path class="te-workflow-edge te-workflow-edge-${h(edge.kind || "next")}" d="${path}" marker-end="url(#teWorkflowArrow)"/>${edge.label ? `<text class="te-workflow-edge-label" x="${labelX}" y="${labelY}" text-anchor="middle">${h(trim(edge.label, 30))}<title>${h(edge.label)}</title></text>` : ""}`;
    })
    .join("");
  const nodes = graph.nodes
    .map((node) => {
      if (node.terminal) {
        const parent = positions.get(node.sourceStateId);
        const terminalStatus = states.get(parent?.name)?.status || "idle";
        return `<g class="te-workflow-terminal te-terminal-status-${terminalStatus}" transform="translate(${node.x} ${node.y})"><rect width="${node.width}" height="${node.height}" rx="21"/><circle cx="19" cy="21" r="5"/><text x="33" y="25">${h(node.name)}</text></g>`;
      }
      const status = states.get(node.name)?.status || "idle";
      const label = statusLabel(status);
      const selectedClass = selectedState === node.id ? "te-state-selected" : "";
      return `<g role="button" tabindex="0" data-state="${h(node.name)}" data-state-id="${h(node.id)}" class="te-state te-state-${status} te-state-type-${h(String(node.type || "state").toLowerCase())} ${selectedClass}" transform="translate(${node.x} ${node.y})" aria-label="${h(node.name)} · ${h(node.type)} · ${label}"><title>${h(node.name)} · ${h(node.type)} · ${label}</title><rect class="te-state-card" width="${node.width}" height="${node.height}" rx="10"/><rect class="te-state-accent" width="4" height="${node.height - 2}" x="1" y="1" rx="2"/><text class="te-state-type" x="15" y="23">${h(node.type || "State")}</text><text class="te-state-name" x="15" y="47">${h(trim(node.name, 29))}</text><circle class="te-state-dot" cx="${node.width - 13}" cy="20" r="4"/><text class="te-state-status" x="${node.width - 14}" y="61" text-anchor="end">${h(label)}</text></g>`;
    })
    .join("");
  const startMarker = start
    ? `<g class="te-workflow-start"><rect x="${startX - 31}" y="20" width="62" height="28" rx="14"/><text x="${startX}" y="38" text-anchor="middle">START</text><path d="M${startX} 48 V${start.y - 8}" marker-end="url(#teWorkflowArrow)"/></g>`
    : "";
  const scale = workflowZoom;
  return `<div class="te-workflow-panel"><div class="te-workflow-toolbar"><div class="te-workflow-summary"><strong>${graph.nodes.filter((node) => !node.terminal).length} states</strong><span>Click a state to inspect its observed input and output</span></div><div class="te-workflow-tools" aria-label="Workflow diagram controls"><button type="button" data-workflow-zoom="out" aria-label="Zoom out">−</button><button type="button" data-workflow-zoom="reset" aria-label="Reset zoom">${Math.round(scale * 100)}%</button><button type="button" data-workflow-zoom="in" aria-label="Zoom in">+</button></div></div><div class="te-workflow-legend"><span><i></i>Not run</span><span><i class="is-running"></i>Running</span><span><i class="is-succeeded"></i>Succeeded</span><span><i class="is-failed"></i>Failed</span><span><i class="te-legend-line is-choice"></i>Choice</span><span><i class="te-legend-line is-catch"></i>Error handler</span><span><i class="te-legend-line is-subflow"></i>Branch / map</span></div><div class="te-workflow-scroll"><svg viewBox="0 0 ${graph.width} ${graph.height}" width="${Math.ceil(graph.width * scale)}" height="${Math.ceil(graph.height * scale)}" class="te-workflow-svg" role="group" aria-label="Step Functions workflow with live execution status"><defs><pattern id="teWorkflowGrid" width="22" height="22" patternUnits="userSpaceOnUse"><circle cx="1" cy="1" r="1" class="te-workflow-grid-dot"/></pattern><marker id="teWorkflowArrow" markerWidth="8" markerHeight="8" refX="6" refY="4" orient="auto"><path d="M1 1 L6 4 L1 7" class="te-workflow-arrowhead"/></marker></defs><rect width="100%" height="100%" fill="url(#teWorkflowGrid)"/>${edges}${startMarker}${nodes}</svg></div></div>`;
}

function renderRun() {
  const node = document.getElementById("teRun");
  if (!node || !activeJob) return;
  const job = activeJob;
  if (eventScrollJobId !== job.id) {
    eventScrollJobId = job.id;
    eventScrollTop = 0;
    followLatestEvents = true;
  } else {
    captureEventScroll(node);
  }
  const context = `${job.id}:${selected}`;
  if (runScrollContext !== context) {
    runScrollContext = context;
    runScrollPositions = new Map();
  } else {
    captureRunScroll(node);
  }
  const events = job.events || [];
  const deploymentProfile =
    job.result?.deploymentProfile ||
    events.find((event) => event.type === "deployment-profile")?.profile;
  const templateIssues = events.flatMap((event) =>
    event.type === "validation" ? event.diagnostics || [] : [],
  );
  node.innerHTML = `<section class="te-run" aria-label="Execution evidence"><div class="te-run-heading"><div><span class="te-eyebrow">${h(job.kind)} · ${h(job.id?.slice(0, 8))}</span><h2>${h(job.scenario || job.action || "Deployment")}</h2></div><div>${badge(job.status)}${button("export", "Export evidence")}</div></div>
  <div class="te-run-meta"><span>${h(job.resourceId || job.artifactName || "Project deployment")}</span><span>${h(stamp(job.startedAt || job.createdAt))}</span><span>${job.sourceHash ? `Snapshot ${h(job.sourceHash.slice(0, 10))}` : ""}</span><span>${job.finishedAt && job.startedAt ? `${((new Date(job.finishedAt) - new Date(job.startedAt)) / 1000).toFixed(1)} s` : running(job) ? "Observing execution…" : ""}</span></div>
  ${job.error ? `<div class="te-alert" role="alert">${h(job.error)}</div>` : ""}
  ${deploymentProfile ? deploymentProfileNotice(deploymentProfile) : ""}
  ${templateIssues.length ? `<details class="te-template-issues" open><summary>${templateIssues.length} deployment template issues</summary><ul>${templateIssues.map((issue) => `<li><strong>${h(issue.template)} · ${h(issue.resourceId)} · ${h(issue.field)}</strong><p>${h(issue.message)}</p><p>${h(issue.remedy)}</p></li>`).join("")}</ul></details>` : ""}
  <div class="te-evidence-grid"><section><h3>Execution timeline <span>${events.length} observations</span></h3><div class="te-event-list">${
    events
      .map((event, index) => {
        const detail = event.stateEnteredEventDetails || event.stateExitedEventDetails;
        const title = ["runtime-log", "application-log"].includes(event.type)
          ? `${event.title} · ${event.message}`
          : event.title ||
            (detail
              ? `${event.type} · ${detail.name}`
              : event.ResourceStatus
                ? `${event.LogicalResourceId} · ${event.ResourceStatus}`
                : event.message || event.type);
        return `<details ${index === expandedEvent ? "open" : ""} data-event="${index}" class="te-event ${/Failed|FAILED|error/.test(event.type + (event.ResourceStatus || "")) ? "te-event-error" : ""}"><summary><span class="te-event-dot"></span><time>${h(event.at ? new Date(event.at * 1000).toLocaleTimeString() : event.timestamp ? new Date(event.timestamp).toLocaleTimeString() : "")}</time><span>${h(title)}</span></summary><pre>${h(json(event))}</pre></details>`;
      })
      .join("") || '<p class="te-muted">Waiting for the backend runner…</p>'
  }</div></section><section class="te-response"><h3>Response / output</h3>${job.result ? dataView(job.result) : '<p class="te-muted">Output appears when the operation responds.</p>'}${job.input ? `<details><summary>Submitted input</summary><pre>${h(json(job.input))}</pre></details>` : ""}</section></div>
  ${job.eventsTruncated ? '<div class="te-notice">The capture limit was reached. Some observations were omitted.</div>' : ""}</section>`;
  const list = node.querySelector(".te-event-list");
  if (list) {
    list.scrollTop = followLatestEvents
      ? list.scrollHeight
      : Math.min(eventScrollTop, list.scrollHeight - list.clientHeight);
    list.addEventListener(
      "scroll",
      () => {
        eventScrollTop = list.scrollTop;
        followLatestEvents = eventListIsAtBottom(list);
      },
      { passive: true },
    );
  }
  restoreRunScroll(node);
  node.querySelectorAll("details[data-event]").forEach((detail) =>
    detail.addEventListener("toggle", () => {
      if (detail.open) expandedEvent = Number(detail.dataset.event);
    }),
  );
  const workflow = document.getElementById("teWorkflow");
  if (workflow && resource()?.definition) workflow.innerHTML = workflowView(resource());
}

function deploymentProfileNotice(profile) {
  if (!profile) return "";
  const adjustments = profile.adjustments || [];
  return `<div class="te-notice" role="status"><strong>${h(profile.name || "Floci Test")} deployment profile</strong><p>This profile deploys each workflow definition directly as an executable state machine. Step Functions version and alias publication is disabled for this Floci test deployment.</p>${adjustments.length ? `<p>Publication or alias-based deployment settings were omitted for: ${adjustments.map((item) => `<strong>${h(item.resourceId)}</strong> (${h(item.template)})`).join(", ")}.</p>` : "<p>No workflow in this snapshot requested SAM alias publication.</p>"}</div>`;
}

function dataView(result) {
  if (Array.isArray(result.rows)) {
    const columns = [...new Set(result.rows.flatMap((row) => Object.keys(row)))];
    return `<p class="te-muted">${result.rows.length} returned rows${result.numberOfRecordsUpdated != null ? ` · ${result.numberOfRecordsUpdated} affected rows` : ""}</p>${columns.length ? `<div class="te-data-table"><table><thead><tr>${columns.map((column) => `<th>${h(column)}</th>`).join("")}</tr></thead><tbody>${result.rows.map((row) => `<tr>${columns.map((column) => `<td>${row[column] == null ? '<span class="te-muted">NULL</span>' : h(pretty(row[column]))}</td>`).join("")}</tr>`).join("")}</tbody></table></div>` : ""}<details><summary>Raw Data API response</summary><pre>${h(json(result))}</pre></details>`;
  }
  if (Array.isArray(result.Items) && result.Items.length) {
    const columns = [...new Set(result.Items.flatMap((item) => Object.keys(item)))];
    return `<div class="te-data-table"><table><thead><tr>${columns.map((c) => `<th>${h(c)}</th>`).join("")}</tr></thead><tbody>${result.Items.map((item) => `<tr>${columns.map((c) => `<td>${h(pretty(item[c]))}</td>`).join("")}</tr>`).join("")}</tbody></table></div><pre>${h(json(result))}</pre>`;
  }
  if (Array.isArray(result.Contents))
    return `<div class="te-object-list">${result.Contents.map((object) => `<button type="button" data-object="${h(object.Key)}"><span>▤ ${h(object.Key)}</span><small>${object.Size} bytes</small></button>`).join("") || "<p>Bucket is empty.</p>"}</div><pre>${h(json(result))}</pre>`;
  if (Array.isArray(result.executions))
    return `<div class="te-object-list">${result.executions.map((execution) => `<button type="button" data-execution="${h(execution.executionArn)}"><span>${h(execution.name)}</span>${badge(execution.status)}</button>`).join("") || "<p>No workflow executions.</p>"}</div><pre>${h(json(result))}</pre>`;
  return `<pre tabindex="0">${h(pretty(result))}</pre>`;
}

async function submit(payload) {
  const project = scope;
  error = "";
  try {
    const job = await api(`${base()}/jobs`, { method: "POST", body: JSON.stringify(payload) });
    if (scope !== project) return;
    activeJob = job;
    overview.jobs = [job, ...(overview.jobs || [])];
    expandedEvent = null;
    deploying = payload.kind === "DEPLOY";
    renderTestControls();
    render();
    scrollToLatestRun();
    schedule();
  } catch (failure) {
    error = failure.message;
    render();
  }
}

async function onClick(event) {
  const target = event.target.closest("button, [data-state]");
  if (!target) return;
  if (target.dataset.workflowZoom) {
    if (target.dataset.workflowZoom === "reset") workflowZoom = 1;
    else
      workflowZoom = Math.max(
        0.6,
        Math.min(1.8, workflowZoom + (target.dataset.workflowZoom === "in" ? 0.2 : -0.2)),
      );
    const svg = target.closest(".te-workflow-panel")?.querySelector(".te-workflow-svg");
    if (svg) {
      const { width, height } = svg.viewBox.baseVal;
      svg.setAttribute("width", String(Math.ceil(width * workflowZoom)));
      svg.setAttribute("height", String(Math.ceil(height * workflowZoom)));
    }
    const reset = target
      .closest(".te-workflow-panel")
      ?.querySelector('[data-workflow-zoom="reset"]');
    if (reset) reset.textContent = `${Math.round(workflowZoom * 100)}%`;
    return;
  }
  if (target.dataset.sqlTable) {
    action = "query";
    const draftKey = json([scope, overview?.deployment?.id, selected, action]);
    const value = json({
      sql: `SELECT * FROM "${target.dataset.sqlTable.replaceAll('"', '""')}" LIMIT 25`,
      parameters: [],
    });
    const input = document.getElementById("teInput");
    if (input?.dataset.draftKey === draftKey) input.value = value;
    requestDrafts.set(draftKey, value);
    render();
    return;
  }
  if (target.dataset.view) {
    view = target.dataset.view;
    render();
    return;
  }
  if (target.dataset.group) {
    filter = target.dataset.group;
    view = "resources";
    search = "";
    const candidates = resources().filter((item) => resourceGroup(item) === filter);
    if (!candidates.some((item) => item.id === selected)) {
      selected = candidates[0]?.id || "";
      action = "";
    }
    render();
    return;
  }
  if (target.dataset.resource) {
    selected = target.dataset.resource;
    action = "";
    selectedState = "";
    render();
    return;
  }
  if (target.dataset.route != null) {
    action = "request";
    const route = resource().routes[Number(target.dataset.route)];
    render();
    document.getElementById("teInput").value = json({
      ...initialInput(action, resource()),
      method: route.method === "ANY" ? "GET" : route.method,
      path: route.path,
    });
    return;
  }
  if (target.dataset.object != null) {
    const key = target.dataset.object;
    action = "get";
    render();
    document.getElementById("teInput").value = json({ key });
    return;
  }
  if (target.dataset.state) {
    selectedState = target.dataset.stateId || target.dataset.state;
    host()
      .querySelectorAll(".te-state")
      .forEach((node) =>
        node.classList.toggle("te-state-selected", node.dataset.stateId === selectedState),
      );
    const states = workflowStates(activeJob?.events || []);
    const detail = states.get(target.dataset.state);
    document.getElementById("teStateDetail").innerHTML =
      `<details open class="te-state-detail"><summary>${h(target.dataset.state)} · ${h(detail?.status || "Not observed")}</summary><pre>${h(json(detail || { message: "Start a workflow execution to observe this state." }))}</pre></details>`;
    return;
  }
  if (target.dataset.job) {
    const project = scope;
    try {
      const detail = await api(`${base()}/jobs/${target.dataset.job}`);
      if (scope !== project) return;
      activeJob = detail;
      render();
      schedule();
    } catch (failure) {
      error = failure.message;
      render();
    }
    return;
  }
  if (target.dataset.execution) {
    await submit({
      kind: "OBSERVE",
      deploymentId: overview.deployment.id,
      resourceId: selected,
      input: { executionArn: target.dataset.execution },
    });
    return;
  }
  try {
    switch (target.dataset.te) {
      case "refresh":
        await refresh();
        break;
      case "more-history": {
        const project = scope;
        const more = await api(`${base()}?before=${encodeURIComponent(overview.historyCursor)}`);
        if (scope !== project) return;
        overview.jobs.push(
          ...more.jobs.filter((job) => !overview.jobs.some((existing) => existing.id === job.id)),
        );
        overview.historyCursor = more.historyCursor;
        render();
        break;
      }
      case "all":
        filter = "All";
        search = "";
        render();
        break;
      case "deployment":
        view = "deployment";
        render();
        break;
      case "show-deployment":
        activeJob = overview.deployment;
        renderRun();
        break;
      case "deploy": {
        if (state.artifact.dirty) throw new Error("Save your artifact edits before deployment.");
        const artifactId = document.getElementById("teArtifact").value;
        const templates = [...host().querySelectorAll('[name="teTemplate"]:checked')].map(
          (input) => input.value,
        );
        const parameters = JSON.parse(document.getElementById("teParameters").value);
        if (!parameters || Array.isArray(parameters) || typeof parameters !== "object")
          throw new Error("Template parameters must be a JSON object.");
        const options = { parameters };
        if (host().querySelector('[name="teTemplate"]')) {
          if (!templates.length) throw new Error("Select at least one stack.");
          options.templates = templates;
        }
        await submit({ kind: "DEPLOY", artifactId, input: options });
        break;
      }
      case "execute": {
        const input =
          action === "inspect" ? {} : JSON.parse(document.getElementById("teInput").value);
        if (!input || typeof input !== "object" || Array.isArray(input))
          throw new Error("Input must be a JSON object.");
        await submit({
          kind: resource().ResourceType === "Script" ? "SCRIPT" : "EXECUTE",
          deploymentId: overview.deployment.id,
          resourceId: selected,
          action,
          input,
          scenario: document.getElementById("teScenario").value.trim(),
        });
        break;
      }
      case "export": {
        const blob = new Blob([json(activeJob)], { type: "application/json" });
        const url = URL.createObjectURL(blob),
          link = document.createElement("a");
        link.href = url;
        link.download = `modriss-test-${activeJob.id}.json`;
        link.click();
        setTimeout(() => URL.revokeObjectURL(url), 1000);
        break;
      }
    }
  } catch (failure) {
    error = failure.message;
    const alert = host().querySelector('[role="alert"]');
    if (alert) alert.textContent = error;
    else
      host().insertAdjacentHTML(
        "afterbegin",
        `<div class="te-alert" role="alert">${h(error)}</div>`,
      );
  }
}

async function onChange(event) {
  if (event.target.id === "teSdkOperation") {
    const input = document.getElementById("teInput");
    try {
      input.value = json({ ...JSON.parse(input.value), operation: event.target.value });
    } catch {
      input.value = json({ operation: event.target.value, parameters: {} });
    }
  }
  if (event.target.id === "teAction") {
    action = event.target.value;
    render();
  }
  if (event.target.id === "teArtifact") {
    const artifact = overview.artifacts.find((item) => item.id === event.target.value);
    document.getElementById("teDeploymentOptions").innerHTML = deploymentOptions(artifact);
  }
  if (event.target.id === "teSourceArchive" && event.target.files[0]) {
    const project = scope;
    const artifactId = document.getElementById("teArtifact").value;
    try {
      if (state.artifact.dirty)
        throw new Error("Save your open artifact edits before importing replacement sources.");
      const { confirmAction } = await import("./confirm-action.js");
      if (
        !(await confirmAction({
          title: "Import refined project?",
          message:
            "This replaces the selected artifact’s saved source files. Existing deployment snapshots and evidence are retained.",
          confirmLabel: "Import sources",
        }))
      )
        return;
      const data = new FormData();
      data.append("file", event.target.files[0]);
      await api(`/artifact/${artifactId}/source-archive`, { method: "POST", body: data });
      if (scope !== project) return;
      await refresh();
    } catch (failure) {
      error = failure.message;
      render();
    }
  }
}

window.addEventListener("modriss-active-modeling-level-change", () => {
  resetScope();
  clearTimeout(timer);
  if (state.activeType !== "test") {
    host()?.classList.add("hidden");
    document.getElementById("workspace")?.classList.remove("test-mode");
    document.querySelector(".topbar")?.classList.remove("test-mode");
  }
  renderTestControls();
});
window.addEventListener("modriss-cloud-provider-change", () => {
  renderTestControls();
  if (!isAws() && state.activeType === "test")
    void import("./model-ops.js").then((module) => module.switchTab("artifact"));
});
