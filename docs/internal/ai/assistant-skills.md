# AI Modeling Assistant Skills

The modeling assistant uses packaged skills to give the LLM focused, reusable modeling guidance at
the point where it is needed. Skills improve model-choice discipline and reduce prompt noise; they
do not replace the autonomous LLM, exact Ecore contract tools, or backend validation.

## Runtime design

Skills live under
`packages/java/platform-assistant/src/main/resources/skills/assistant/<skill-name>/SKILL.md`.
Each skill has `name` and `description` YAML frontmatter plus concise imperative instructions. The
adjacent `agents/openai.yaml` supplies UI-compatible metadata and makes every folder a conventional,
portable skill package.

`AssistantSkills` loads and validates these immutable classpath resources, caches them, and adds a
content hash to the provider prompt for auditability. `AgentTurnLoop` selects skills from formal
workflow state, model level, source presence, and whether the model contains elements. It does not
classify user intent with keywords, regular expressions, or hard-coded content generation.

The selection is deliberately progressive:

| Workflow context                     | Loaded skills             |
| ------------------------------------ | ------------------------- |
| Any mutation plan                    | `plan-model-edit`         |
| Any mutation execution/repair        | `construct-valid-model`   |
| PIM planning/execution               | `model-pim-serverless`    |
| Existing-model planning/execution    | `evolve-existing-model`   |
| Source-backed CIM planning/execution | `transform-source-to-cim` |
| Read-only explanation                | `explain-model`           |

This keeps explanation turns free of mutation guidance, avoids loading CIM source instructions into
ordinary PIM work, and gives follow-up edits explicit preservation/reuse guidance without adding a
provider call.

## Included skills

- `plan-model-edit` turns complete feature requests into dependency-aware, coherent checkpoints and
  exact contract candidates.
- `construct-valid-model` applies exact ownership, containment, required-feature, enum, and
  connection rules and directs bounded diagnostic repair.
- `model-pim-serverless` covers provider-neutral end-to-end serverless behavior across APIs,
  compute, workflows, integration, data, contracts, policies, security, deployment, and
  observability.
- `evolve-existing-model` makes follow-up turns inspect and reuse saved elements, connect new
  behavior into existing paths, and preserve unrelated content.
- `transform-source-to-cim` maintains source-unit coverage and provenance while producing richer
  draft CIM content without sample-biased fallback generation.
- `explain-model` keeps explanations grounded in inventory, inspection results, and exact metamodel
  contracts without mutation.

## Authority and validation boundary

Skills are advisory system instructions. They cannot apply changes directly. The LLM must still use
the closed action protocol and exact `describe_types`/inspection results. The backend compiles each
batch against live Ecore-derived contracts, applies it to a private workspace, and persists it only
after structural Ecore/EMF conformance succeeds.

Assistant-generated actions, patches, repair attempts, and checkpoints must call only
`ModelService.validateStructural(...)`. EVL semantic validation is never part of chatbot
create/repair/apply/commit. EVL remains available only in explicit user/model validation workflows
outside the assistant path.

No skill contains executable model templates, sample-specific defaults, keyword routing, or a
deterministic fallback model. Repository samples are evaluation fixtures only when explicitly
selected.

## Adding or changing a skill

1. Keep triggering context in the `description` and the body concise and imperative.
2. State domain decisions that are not obvious from exact Ecore contracts; do not duplicate whole
   metamodels in skill text.
3. Add the skill to `AssistantSkillsTest` and select it from explicit workflow state in
   `AgentTurnLoop`.
4. Validate the folder with the skill validator and run focused assistant tests.
5. Forward-test a realistic create turn and a follow-up edit turn. Measure structural success,
   feature coverage, element reuse, repair count, provider calls, latency, and completion state.

The live scenario used for PIM skill evaluation is: start from an empty PIM root, request a complete
provider-neutral serverless architecture for a concrete feature set, then submit a second prompt in
the same session that adds another feature. The second turn must reuse the saved model rather than
recreate it, and both checkpoints must pass only the structural gate.
