#!/usr/bin/env node
/** Generate the exhaustive public process reference from canonical JSON definitions. */
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { resolveTaskUse } from "./lib/process-walk.mjs";

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "../../..");
const DEFINITIONS = path.join(ROOT, "mde/process/definitions");
const OUTPUT = path.join(ROOT, "docs/public-docs/docs/process-reference");
const levels = ["end-to-end", "cim", "pim", "psm", "artifact"];
const definitions = Object.fromEntries(levels.map((level) => [
  level,
  JSON.parse(fs.readFileSync(path.join(DEFINITIONS, `${level}.json`), "utf8")),
]));

const title = { "end-to-end": "Development and Delivery", cim: "CIM modeling", pim: "PIM modeling", psm: "AWS PSM modeling", artifact: "Generated-artifact readiness" };
const guide = { "end-to-end": "../guides/full-lifecycle-method.md", cim: "../guides/cim-modeling-methodology.md", pim: "../guides/pim-modeling-methodology.md", psm: "../guides/psm-modeling-methodology.md", artifact: "../guides/generated-artifacts.md" };
const esc = (value) => String(value ?? "").replaceAll("|", "\\|").replaceAll("\n", " ");
const list = (values, empty = "None declared") => values?.length ? values.map((v) => `- ${v}`).join("\n") : `- ${empty}`;
const names = (items) => new Map((items || []).map((item) => [item.id, item.name]));
const refs = (ids, lookup) => (ids || []).map((id) => lookup.get(id) || id);

const commonReferences = `## References

These sources explain the standards and practices on which the process structure is based. They are foundations for tailoring and professional judgement, rather than substitutes for project evidence.

- [OMG Software & Systems Process Engineering Meta-Model (SPEM) 2.0](https://www.omg.org/spec/SPEM/2.0/PDF/)
- [ISO/IEC/IEEE 12207:2026, software life cycle processes](https://www.iso.org/standard/90219.html)
- [ISO/IEC/IEEE 15288:2023, system life cycle processes](https://www.iso.org/standard/81702.html)
- [SWEBOK Guide, version 4.0a](https://ieeecs-media.computer.org/media/education/swebok/swebok-v4.pdf)
- [Agile Manifesto principles](https://agilemanifesto.org/principles)
- [The Kanban Guide](https://kanbanguides.org/the-kanban-guide/)
- [FinOps Framework](https://www.finops.org/framework/)
- [AWS Well-Architected Serverless Applications Lens](https://docs.aws.amazon.com/wellarchitected/latest/serverless-applications-lens/welcome.html)
- [Brinkkemper, Method engineering](https://doi.org/10.1016/S0950-5849(95)01059-9)
- [Asadi, Esfahani, and Ramsin, Process patterns for MDA-based software development](https://mason.gmu.edu/~nesfaha2/Publications/SERA2010.pdf)
`;

function contextFor(process) {
  const roleNames = names(process.methodContent.roleDefinitions);
  const wpNames = names(process.methodContent.workProductDefinitions);
  return { roleNames, wpNames };
}

function renderTask(process, task, heading = "####") {
  const { roleNames, wpNames } = contextFor(process);
  const performers = refs(task.performerRoleRefs, roleNames);
  const primary = roleNames.get(task.primaryPerformerRoleRef) || task.primaryPerformerRoleRef || "No primary performer declared";
  const inputs = refs(task.inputWorkProductRefs, wpNames);
  const outputs = refs(task.outputWorkProductRefs, wpNames);
  return `${heading} ${task.name}

<small>Task definition: \`${task.id}\`</small>

${task.purpose || `Complete ${task.name.toLowerCase()} and retain enough evidence for review.`} This task should be performed in the context of the current increment or operational item. Its result is reviewed through the stated exit criteria and validation rules, rather than accepted because an activity was marked complete.

**Accountability and collaboration**

The primary performer is **${primary}**.${performers.length > 1 ? ` The work normally involves ${performers.slice(1).join(", ")}. Role names express responsibilities and may be combined or distributed according to the approved method profile.` : " The approved method profile may assign this responsibility to a person who holds several roles."}

**Inputs**

${list(inputs, "No formal input work product is required. The task still uses the accepted scope, decisions, and project context.")}

**How to perform the task**

${task.steps?.length ? task.steps.map((step, index) => `${index + 1}. ${step}`).join("\n") : "1. Confirm the scope and evidence need.\n2. Perform the work and record decisions.\n3. Review the result with its accountable owner."}

**Outputs**

${list(outputs)}

**Ready to start when**

${list(task.entryCriteria, "The responsible people understand the current scope and the required decision or result.")}

**Complete when**

${list(task.exitCriteria, "The result is recorded, reviewed, and connected to its inputs and downstream use.")}

**Checks and evidence**

${list(task.validationRules, task.progressEvidence || "Record the owner, status, decision, and evidence links.")}
`;
}

function renderStage(process, stage, depth = 0) {
  const hashes = "#".repeat(Math.min(3 + depth, 6));
  const tasks = (stage.taskUses || []).map((use) => resolveTaskUse(process, use)).filter(Boolean);
  let out = `${hashes} ${stage.name}\n\n<small>${stage.activityKind || stage.type || "Activity"}: \`${stage.id}\`</small>\n\n${stage.objective || "This activity groups related work and provides a reviewable point in the process."}\n\n`;
  if (stage.iterative) out += "This activity is iterative. Repeat it when evidence changes, a review finds rework, or the current item needs another controlled refinement.\n\n";
  for (const task of tasks) out += renderTask(process, task, `${hashes}#`) + "\n";
  for (const child of stage.subStages || []) out += renderStage(process, child, depth + 1);
  return out;
}

function renderDevelopment() {
  const process = definitions["end-to-end"];
  let out = `# Development and Delivery reference

This page explains every phase, activity, and task in the Development and Delivery process. Read the [lifecycle guide](${guide["end-to-end"]}) first if you need the shorter conceptual view. The reference follows the canonical process definition, including the one-time inception and retirement phases and the repeatable work inside active product construction.

The entries are practical descriptions. They do not require separate job titles or documents. Tailoring may combine work while preserving ownership, required evidence, and gate obligations.

## Phase summary

| Phase | Purpose | Activities | Entry conditions | Exit conditions |
| --- | --- | ---: | ---: | ---: |
${process.phases.map((phase) => `| ${esc(phase.name)} | ${esc(phase.objective)} | ${(phase.stages || []).length} | ${(phase.entryCriteria || []).length} | ${(phase.exitCriteria || []).length} |`).join("\n")}

`;
  for (const phase of process.phases) {
    out += `## ${phase.name}\n\n<small>Phase: \`${phase.id}\`</small>\n\n${phase.objective}\n\n**Entry conditions**\n\n${list(phase.entryCriteria)}\n\n**Exit conditions**\n\n${list(phase.exitCriteria)}\n\n`;
    for (const stage of phase.stages || []) out += renderStage(process, stage);
  }
  return out + commonReferences;
}

function renderOperations() {
  const process = definitions["end-to-end"];
  const component = process.processComponents.find((item) => item.id === "modriss.operations-maintenance");
  let out = `# Operations and Maintenance reference

Operations and Maintenance is an ongoing, event-driven process for every accepted live release. It begins at the first G7 handover and closes only when G8 is accepted and no release remains live. Its Kanban system controls production demand while Development and Delivery continues to engineer planned or product-changing work.

${component.objective}

**Entry conditions**

${list(component.entryCriteria)}

**Exit conditions**

${list(component.exitCriteria)}

An operational item can finish as runbook or service work, follow the shortest safe model-driven change path, or enter a planned release backlog. Emergency restoration remains temporary until the authoritative source and downstream outputs are reconciled.

## Activity summary

| Activity | Purpose | Task uses | Iterative |
| --- | --- | ---: | --- |
${component.activities.map((activity) => `| ${esc(activity.name)} | ${esc(activity.objective)} | ${(activity.taskUses || []).length} | ${activity.iterative ? "Yes" : "No"} |`).join("\n")}

`;
  for (const activity of component.activities || []) out += renderStage(process, activity);
  return out + commonReferences;
}

function renderLibrary(process, level) {
  const mc = process.methodContent;
  const roleNames = names(mc.roleDefinitions);
  const wpNames = names(mc.workProductDefinitions);
  let out = `# ${title[level]} method content

This catalog covers the reusable roles, work products, guidance, and gates for the ${title[level]} process. The related [process guide](${guide[level]}) explains how the items fit into the lifecycle. Identifiers are included because they connect the public explanation to the executable process definition and API.

## Tasks

Tasks describe a purposeful unit of work, the people involved, the evidence it consumes and produces, and the conditions used in review. Their order and use in a specific process run are shown in the related process guide.

`;
  for (const task of mc.taskDefinitions) out += renderTask(process, task, "###") + "\n";
  out += `## Roles

Roles describe accountability. They do not require one person per role. A tailored team may combine roles if decision rights remain clear and any independence required for review or assurance is protected.

`;
  for (const role of mc.roleDefinitions) out += `### ${role.name}\n\n<small>Role definition: \`${role.id}\`</small>\n\n${list(role.responsibilities)}\n\nA person assigned this role is expected to keep the relevant decisions and evidence visible, collaborate with the performers who depend on the result, and raise unresolved risks before the next gate.\n\n`;
  out += "## Work products\n\nA work product is maintained evidence. It can be a model, record, report, configuration, or another controlled result. Its useful form depends on the project, but its content must remain reviewable and traceable.\n\n";
  for (const wp of mc.workProductDefinitions) out += `### ${wp.name}\n\n<small>${wp.workProductKind || "Work product"}: \`${wp.id}\`</small>\n\n${wp.description || "A controlled result used or produced by this process."}\n\nKeep this item under suitable version control. Record its owner, status, relevant revision, and links to the decisions or downstream work that depend on it.\n\n`;
  out += "## Guidance\n\nGuidance explains how to apply the process without turning advice into an unconditional gate. Teams should record any material departure in the method profile.\n\n";
  for (const item of mc.guidance || []) out += `### ${item.name}\n\n<small>Guidance: \`${item.id}\`; applies to ${item.appliesTo || "the process"}</small>\n\n${item.text}\n\nUse this guidance during planning and review. Adapt it when project evidence supports another approach, while retaining the process intent and required assurance.\n\n`;
  out += "## Gates and milestones\n\nA gate is an evidence-based decision point. Task completion alone does not pass a gate. The named authorities review the required evidence and record the decision, conditions, and any follow-up work.\n\n";
  for (const gate of process.milestones || []) out += `### ${gate.gateId ? `${gate.gateId}: ` : ""}${gate.name}\n\n<small>Milestone: \`${gate.id}\`</small>\n\n**Decision condition:** ${gate.acceptanceCondition || "The responsible authority accepts the required evidence."}\n\n**Decision authorities**\n\n${list(refs(gate.authorityRoleRefs, roleNames))}\n\n**Required evidence**\n\n${list(refs(gate.requiredEvidenceRefs, wpNames))}\n\n`;
  return out + commonReferences;
}

function renderIndex() {
  return `# Process reference

This section is the exhaustive public reference for the two coordinated MODRISS processes. Development and Delivery organizes inception, iterative model-driven construction, release, transition, and retirement. Operations and Maintenance manages live-service work through an ongoing Kanban service-delivery system.

The child-process catalogs describe the reusable method content used inside Development and Delivery. Together, these pages cover every canonical phase, activity, stage, task, role, work product, guidance item, and gate exposed by the process definitions.

## Start with the process you are performing

- [Development and Delivery](development-delivery.md) explains all work from product intent to closure.
- [Operations and Maintenance](operations-maintenance.md) explains live-service observation, flow, incidents, maintenance, and learning.

| Process area | Control style | Starts | Ends |
| --- | --- | --- | --- |
| Development and Delivery | Sequential lifecycle phases containing iterative delivery work | Authorized product or change opportunity | G8 lifecycle closure |
| Operations and Maintenance | Ongoing, event-driven Kanban service-delivery process | First accepted G7 handover | G8 accepted and no live release remains |

## Browse by SPEM element

Each process has separate pages for phases and activities, tasks, roles, work products, guidance, and gates. Begin with [How the process maps to SPEM](spem-structure.md), then choose a process from the navigation. For example, the Development and Delivery section has dedicated [tasks](development-delivery/tasks.md), [roles](development-delivery/roles.md), and [work products](development-delivery/work-products.md) pages. Operations and Maintenance has its own item pages because it is an ongoing process with a different control system.

## How to use the reference

Begin with the phase or operational activity that owns the current work. Check its entry conditions, identify the accountable and supporting roles, inspect the required inputs, and perform the task steps. Retain the named outputs and evidence. A review or gate should decide whether the work is accepted, needs rework, or can be deferred with an explicit rationale.

The process is intentionally tailorable. Combining activities can be reasonable for a small team. Omitting evidence, ownership, or an applicable control requires a recorded rationale and a suitable compensating practice.

${commonReferences}`;
}

function walkActivities(activities, parent = null, depth = 0, out = []) {
  for (const activity of activities || []) {
    out.push({ activity, parent, depth });
    walkActivities(activity.stages, activity, depth + 1, out);
    walkActivities(activity.subStages, activity, depth + 1, out);
  }
  return out;
}

function scopeFor(key) {
  if (key === "development-delivery") {
    const process = definitions["end-to-end"];
    return { key, label: "Development and Delivery", process, roots: process.phases, gates: process.milestones };
  }
  if (key === "operations-maintenance") {
    const process = definitions["end-to-end"];
    const component = process.processComponents.find((item) => item.id === "modriss.operations-maintenance");
    return { key, label: "Operations and Maintenance", process, roots: component.activities, component, gates: process.milestones.filter((item) => ["G7", "G8"].includes(item.gateId)) };
  }
  const process = definitions[key];
  return { key, label: title[key], process, roots: process.phases, gates: process.milestones };
}

function scopedContent(scope) {
  const activities = walkActivities(scope.roots);
  const taskUses = activities.flatMap(({ activity }) => (activity.taskUses || []).map((use) => ({ ...use, activityRef: activity.id })));
  const taskDefinitionIds = new Set(taskUses.map((item) => item.taskDefinitionRef));
  const tasks = scope.process.methodContent.taskDefinitions.filter((item) => taskDefinitionIds.has(item.id));
  const roleIds = new Set(tasks.flatMap((item) => item.performerRoleRefs || []));
  const workProductIds = new Set(tasks.flatMap((item) => [...(item.inputWorkProductRefs || []), ...(item.outputWorkProductRefs || [])]));
  const isOperationsScope = scope.key === "operations-maintenance";
  return {
    activities,
    taskUses,
    tasks,
    roles: scope.process.methodContent.roleDefinitions.filter((item) => !isOperationsScope || roleIds.has(item.id)),
    workProducts: scope.process.methodContent.workProductDefinitions.filter((item) => !isOperationsScope || workProductIds.has(item.id)),
    guidance: scope.process.methodContent.guidance || [],
  };
}

function sectionIntro(scope, kind, spemType) {
  return `# ${scope.label}: ${kind}\n\nThis page documents the **${spemType}** elements used by the ${scope.label} process. It is generated from the canonical executable definition, so the names and identifiers match the API and process interface. The explanation adds practical teaching around the normative data.\n\n`;
}

function renderActivitiesPage(scope) {
  const { activities } = scopedContent(scope);
  const roleNames = names(scope.process.methodContent.roleDefinitions);
  const taskNames = names(scope.process.methodContent.taskDefinitions);
  let out = sectionIntro(scope, "phases and activities", "SPEM process-structure Activity, Phase, and TaskUse");
  out += "A phase establishes a significant lifecycle period and normally ends at a major checkpoint. An activity groups related work within a phase or process component. A TaskUse places reusable task guidance into that process context. Entry and exit conditions describe evidence states; they are not calendar dates.\n\n";
  out += "## Summary\n\n| Phase or activity | SPEM type | Contained by | Task uses | Execution |\n| --- | --- | --- | ---: | --- |\n";
  for (const { activity, parent } of activities) out += `| ${esc(activity.name)} | ${esc(activity.type || activity.activityKind || "Activity")} | ${esc(parent?.name || "Process")} | ${(activity.taskUses || []).length} | ${esc([activity.iterative && "iterative", activity.isOngoing && "ongoing", activity.isEventDriven && "event-driven"].filter(Boolean).join(", ") || "defined sequence")} |\n`;
  out += "\n## Detailed activities\n\n";
  for (const { activity, parent, depth } of activities) {
    const heading = "#".repeat(Math.min(2 + depth, 6));
    const taskUses = activity.taskUses || [];
    out += `${heading} ${activity.name}\n\n<small>${activity.type || "Activity"}${activity.activityKind ? ` · ${activity.activityKind}` : ""}: \`${activity.id}\`${parent ? ` · contained by **${parent.name}**` : ""}</small>\n\n${activity.objective || "This process element groups related work and its review evidence."}\n\n`;
    if (activity.entryCriteria?.length) out += `**Entry conditions**\n\n${list(activity.entryCriteria)}\n\n`;
    if (activity.exitCriteria?.length) out += `**Exit conditions**\n\n${list(activity.exitCriteria)}\n\n`;
    const activityRoles = refs(activity.roleUseRefs?.map((id) => scope.process.roleUses.find((use) => use.id === id)?.roleDefinitionRef).filter(Boolean), roleNames);
    if (activityRoles.length) out += `**Participating roles**\n\n${list(activityRoles)}\n\n`;
    if (taskUses.length) {
      out += "**Task uses in this activity**\n\n";
      for (const use of taskUses) out += `- **${taskNames.get(use.taskDefinitionRef) || use.taskDefinitionRef}** (TaskUse \`${use.id}\`)\n`;
      out += "\n";
    }
    if (activity.iterative || activity.isOngoing || activity.isEventDriven) out += `**Execution character:** ${[activity.iterative && "iterative", activity.isOngoing && "ongoing", activity.isEventDriven && "event-driven"].filter(Boolean).join(", ")}. Re-enter this work when its trigger or feedback condition applies, and retain the evidence from each material decision.\n\n`;
  }
  return out + commonReferences;
}

function renderTasksPage(scope) {
  const { tasks, taskUses, activities } = scopedContent(scope);
  const activityNames = new Map(activities.map(({ activity }) => [activity.id, activity.name]));
  let out = sectionIntro(scope, "tasks", "SPEM TaskDefinition and TaskUse");
  out += "A TaskDefinition describes reusable work. A TaskUse places that definition inside an activity and binds it to process performers and work-product uses. The same responsibility may appear in another process context with different inputs, outputs, or selected steps.\n\n";
  const roleNames = names(scope.process.methodContent.roleDefinitions);
  out += "## Summary\n\n| Task | Primary role | Inputs | Outputs | Task uses |\n| --- | --- | ---: | ---: | ---: |\n";
  for (const task of tasks) out += `| ${esc(task.name)} | ${esc(roleNames.get(task.primaryPerformerRoleRef) || task.primaryPerformerRoleRef || "Not assigned")} | ${(task.inputWorkProductRefs || []).length} | ${(task.outputWorkProductRefs || []).length} | ${taskUses.filter((item) => item.taskDefinitionRef === task.id).length} |\n`;
  out += "\n## Detailed tasks\n\n";
  for (const task of tasks) {
    const uses = taskUses.filter((item) => item.taskDefinitionRef === task.id);
    out += renderTask(scope.process, task, "##");
    out += "\n**Uses in this process**\n\n";
    for (const use of uses) out += `- \`${use.id}\` in **${activityNames.get(use.activityRef) || "its containing activity"}**\n`;
    out += "\n";
  }
  return out + commonReferences;
}

function renderRolesPage(scope) {
  const { roles, tasks, activities } = scopedContent(scope);
  const activityIds = new Set(activities.map(({ activity }) => activity.id));
  if (scope.component?.id) activityIds.add(scope.component.id);
  let out = sectionIntro(scope, "roles", "SPEM RoleDefinition, RoleUse, and ProcessPerformer");
  out += "A RoleDefinition states a responsibility. RoleUse places it in an activity, while ProcessPerformer connects it to a TaskUse as a primary or supporting performer. Roles are hats worn by people or teams. They are not required organization-chart positions.\n\n";
  out += "## Summary\n\n| Role | Main responsibility | Primary tasks | Supporting tasks | Role uses |\n| --- | --- | ---: | ---: | ---: |\n";
  for (const role of roles) {
    const primaryCount = tasks.filter((task) => task.primaryPerformerRoleRef === role.id).length;
    const supportingCount = tasks.filter((task) => task.primaryPerformerRoleRef !== role.id && task.performerRoleRefs?.includes(role.id)).length;
    const useCount = scope.process.roleUses.filter((item) => item.roleDefinitionRef === role.id && activityIds.has(item.activityRef)).length;
    out += `| ${esc(role.name)} | ${esc(role.responsibilities?.[0] || "Responsibility defined by the tailored method profile")} | ${primaryCount} | ${supportingCount} | ${useCount} |\n`;
  }
  out += "\n## Detailed roles\n\n";
  for (const role of roles) {
    const primary = tasks.filter((task) => task.primaryPerformerRoleRef === role.id);
    const supporting = tasks.filter((task) => task.primaryPerformerRoleRef !== role.id && task.performerRoleRefs?.includes(role.id));
    const uses = scope.process.roleUses.filter((item) => item.roleDefinitionRef === role.id && activityIds.has(item.activityRef));
    out += `## ${role.name}\n\n<small>RoleDefinition: \`${role.id}\` · ${uses.length} RoleUse occurrence${uses.length === 1 ? "" : "s"}</small>\n\n**Responsibilities**\n\n${list(role.responsibilities)}\n\n`;
    out += `**Primary task accountability**\n\n${list(primary.map((item) => item.name), "No primary task in this process scope.")}\n\n**Supporting participation**\n\n${list(supporting.map((item) => item.name), "No supporting task in this process scope.")}\n\n**Role uses**\n\n${list(uses.map((item) => `\`${item.id}\` in \`${item.activityRef}\``), "This reusable role has no RoleUse occurrence in the current process scope.")}\n\nThe method profile should name the person or team carrying this role. Where one person holds several roles, record how reviews and conflicting decision rights are handled.\n\n`;
  }
  return out + commonReferences;
}

function renderWorkProductsPage(scope) {
  const { workProducts, tasks, activities } = scopedContent(scope);
  const activityIds = new Set(activities.map(({ activity }) => activity.id));
  if (scope.component?.id) activityIds.add(scope.component.id);
  let out = sectionIntro(scope, "work products", "SPEM WorkProductDefinition, WorkProductUse, and ProcessParameter");
  out += "A WorkProductDefinition describes maintained information or a tangible result. WorkProductUse binds that definition to an activity or task as an input or output. ProcessParameter makes the direction explicit. A work product can be stored in several physical files or systems if its identity, owner, revision, and evidence links remain clear.\n\n";
  out += "## Summary\n\n| Work product | Kind | Producing tasks | Consuming tasks | Uses |\n| --- | --- | ---: | ---: | ---: |\n";
  for (const wp of workProducts) {
    const producerCount = tasks.filter((task) => task.outputWorkProductRefs?.includes(wp.id)).length;
    const consumerCount = tasks.filter((task) => task.inputWorkProductRefs?.includes(wp.id)).length;
    const useCount = scope.process.workProductUses.filter((item) => item.workProductDefinitionRef === wp.id && activityIds.has(item.activityRef)).length;
    out += `| ${esc(wp.name)} | ${esc(wp.workProductKind || "Work product")} | ${producerCount} | ${consumerCount} | ${useCount} |\n`;
  }
  out += "\n## Detailed work products\n\n";
  for (const wp of workProducts) {
    const consumers = tasks.filter((task) => task.inputWorkProductRefs?.includes(wp.id));
    const producers = tasks.filter((task) => task.outputWorkProductRefs?.includes(wp.id));
    const uses = scope.process.workProductUses.filter((item) => item.workProductDefinitionRef === wp.id && activityIds.has(item.activityRef));
    out += `## ${wp.name}\n\n<small>${wp.workProductKind || "WorkProductDefinition"}: \`${wp.id}\` · ${uses.length} WorkProductUse occurrence${uses.length === 1 ? "" : "s"}</small>\n\n${wp.description || "A controlled result used or produced by this process."}\n\n**Produced or updated by**\n\n${list(producers.map((item) => item.name))}\n\n**Consumed by**\n\n${list(consumers.map((item) => item.name), "No task in this scope declares this item as an input.")}\n\n**Work-product uses**\n\n${list(uses.map((item) => `\`${item.id}\` as **${item.usage}** in task \`${item.taskUseRef}\``), "This reusable work product has no WorkProductUse occurrence in the current process scope.")}\n\nKeep the accepted revision under suitable configuration control. Record its owner, approval state, and trace links to source decisions and downstream evidence.\n\n`;
  }
  return out + commonReferences;
}

function renderGuidancePage(scope) {
  const { guidance } = scopedContent(scope);
  let out = sectionIntro(scope, "guidance", "SPEM Guidance");
  out += "Guidance teaches how to perform or tailor work. It supports tasks and activities without becoming an automatic gate. A project may adapt guidance when its context requires another approach, provided that the method profile records the reasoning and preserves applicable evidence obligations.\n\n";
  out += "## Summary\n\n| Guidance | Applies to | Purpose |\n| --- | --- | --- |\n";
  for (const item of guidance) out += `| ${esc(item.name)} | ${esc(item.appliesTo || "Process")} | ${esc(item.text)} |\n`;
  out += "\n## Detailed guidance\n\n";
  for (const item of guidance) out += `## ${item.name}\n\n<small>Guidance: \`${item.id}\` · applies to ${item.appliesTo || "the process"}</small>\n\n${item.text}\n\n**How to use it:** consult this item while planning and reviewing the affected work. Record a material departure in the method profile, including the project evidence and any compensating control.\n\n`;
  return out + commonReferences;
}

function renderGatesPage(scope) {
  const roleNames = names(scope.process.methodContent.roleDefinitions);
  const wpNames = names(scope.process.methodContent.workProductDefinitions);
  let out = sectionIntro(scope, "gates and milestones", "SPEM Milestone with MODRISS evidence and authority extensions");
  out += "A milestone marks a meaningful decision in the process. MODRISS adds explicit authority and required-evidence references so a gate cannot pass only because its preceding tasks are complete. The decision record should state acceptance, conditional acceptance, redirection, rework, deferral, or closure as applicable.\n\n";
  out += "## Summary\n\n| Gate | Decision | Authorities | Required evidence |\n| --- | --- | ---: | ---: |\n";
  for (const gate of scope.gates || []) out += `| ${esc(gate.gateId || gate.id)} | ${esc(gate.name)} | ${(gate.authorityRoleRefs || []).length} | ${(gate.requiredEvidenceRefs || []).length} |\n`;
  out += "\n## Detailed gates and milestones\n\n";
  for (const gate of scope.gates || []) out += `## ${gate.gateId ? `${gate.gateId}: ` : ""}${gate.name}\n\n<small>Milestone: \`${gate.id}\` · phase/activity \`${gate.phaseId || gate.stageId || "process"}\`</small>\n\n${gate.acceptanceCondition || "The responsible authorities accept the required evidence."}\n\n**Decision authorities**\n\n${list(refs(gate.authorityRoleRefs, roleNames))}\n\n**Required evidence**\n\n${list(refs(gate.requiredEvidenceRefs, wpNames))}\n\nPassing this gate means the named evidence has been reviewed for this decision. It does not remove later verification, operational, compliance, or change-control obligations.\n\n`;
  return out + commonReferences;
}

function renderRelationshipsPage(scope) {
  const { activities, taskUses } = scopedContent(scope);
  const ids = new Set([...activities.map(({ activity }) => activity.id), ...taskUses.map((item) => item.id)]);
  const sequences = (scope.process.workSequences || []).filter((item) => ids.has(item.predecessorRef) || ids.has(item.successorRef));
  let out = sectionIntro(scope, "flow and bindings", "SPEM WorkSequence, ProcessPerformer, and ProcessParameter");
  out += "WorkSequence records explicit process flow. ProcessPerformer binds a role use to a task use. ProcessParameter binds a work-product use to a task use and states whether it enters or leaves the task. These relationships make dependencies inspectable and prevent document order from being treated as hidden process logic.\n\n## Summary\n\n| Relationship area | Items | What it controls |\n| --- | ---: | --- |\n| Work sequences | " + sequences.length + " | Ordering, iteration, feedback, and conditional flow |\n| Task uses | " + taskUses.length + " | Placement of reusable tasks in activities |\n| Process performers | " + taskUses.reduce((sum, item) => sum + (item.processPerformers || []).length, 0) + " | Primary and supporting role bindings |\n| Process parameters | " + taskUses.reduce((sum, item) => sum + (item.processParameters || []).length, 0) + " | Input and output work-product bindings |\n\n## Work sequences\n\n";
  for (const item of sequences) out += `### ${item.predecessorRef} → ${item.successorRef}\n\n<small>WorkSequence: \`${item.id}\` · ${item.linkKind || item.relation || "dependency"}</small>\n\n${item.guidance || "Complete the predecessor condition before treating the successor as ready."}\n\n${item.condition ? `**Condition:** ${item.condition}\n\n` : ""}`;
  out += "## Task performer and parameter bindings\n\n";
  for (const use of taskUses) {
    out += `### ${use.id}\n\n<small>TaskUse of \`${use.taskDefinitionRef}\`</small>\n\n**Process performers**\n\n${list((use.processPerformers || []).map((item) => `\`${item.id}\`: ${item.kind} RoleUse \`${item.roleUseRef}\``))}\n\n**Process parameters**\n\n${list((use.processParameters || []).map((item) => `\`${item.id}\`: **${item.direction}** WorkProductUse \`${item.workProductUseRef}\`${item.optional ? " (optional)" : ""}`))}\n\n`;
  }
  return out + commonReferences;
}

function renderSpemStructure() {
  return `# How the process maps to SPEM\n\nThe MODRISS process documentation follows the central SPEM separation between **method content** and **process structure**. This distinction prevents reusable teaching from being confused with one occurrence of work in a lifecycle.\n\n| Public documentation section | SPEM element | Meaning in MODRISS |\n| --- | --- | --- |\n| Tasks | TaskDefinition | Reusable instructions, criteria, and evidence expectations |\n| Roles | RoleDefinition | Reusable responsibility and competence |\n| Work products | WorkProductDefinition | Reusable description of an input, output, model, record, or artifact |\n| Guidance | Guidance | Supporting advice for performing or tailoring work |\n| Phases and activities | Phase and Activity | The lifecycle context that organizes work |\n| Task occurrences | TaskUse | Use of a TaskDefinition within an Activity |\n| Role participation | RoleUse and ProcessPerformer | Use of a role in an Activity and its connection to a TaskUse |\n| Input and output bindings | WorkProductUse and ProcessParameter | Use of a work product and its direction for a TaskUse |\n| Flow | WorkSequence | An explicit predecessor-successor relationship and its condition |\n| Gates | Milestone | A significant evidence-based decision point |\n\nMODRISS uses labeled extensions for execution state, governance, metamodel coverage, iteration, change routing, and evidence-based gate decisions. These additions support the executable platform. They do not claim to be native SPEM metaclasses.\n\n## Reading an item page\n\nStart with **Phases and activities** to locate the current work. Open **Tasks** for the procedure and completion conditions. Use **Roles** to assign accountability and collaboration. Consult **Work products** to understand the evidence entering and leaving the task. Apply the relevant **Guidance**, then use **Gates and milestones** when an authority decision is required.\n\n${commonReferences}`;
}

fs.mkdirSync(OUTPUT, { recursive: true });
fs.writeFileSync(path.join(OUTPUT, "index.md"), renderIndex());
fs.writeFileSync(path.join(OUTPUT, "development-delivery.md"), renderDevelopment());
fs.writeFileSync(path.join(OUTPUT, "operations-maintenance.md"), renderOperations());
fs.writeFileSync(path.join(OUTPUT, "spem-structure.md"), renderSpemStructure());
for (const key of ["development-delivery", "operations-maintenance", "cim", "pim", "psm", "artifact"]) {
  const scope = scopeFor(key);
  const dir = path.join(OUTPUT, key);
  fs.mkdirSync(dir, { recursive: true });
  fs.writeFileSync(path.join(dir, "activities.md"), renderActivitiesPage(scope));
  fs.writeFileSync(path.join(dir, "tasks.md"), renderTasksPage(scope));
  fs.writeFileSync(path.join(dir, "roles.md"), renderRolesPage(scope));
  fs.writeFileSync(path.join(dir, "work-products.md"), renderWorkProductsPage(scope));
  fs.writeFileSync(path.join(dir, "guidance.md"), renderGuidancePage(scope));
  fs.writeFileSync(path.join(dir, "gates.md"), renderGatesPage(scope));
  fs.writeFileSync(path.join(dir, "relationships.md"), renderRelationshipsPage(scope));
}
console.log(`Generated public process reference in ${OUTPUT}`);
