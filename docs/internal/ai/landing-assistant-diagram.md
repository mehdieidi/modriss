# Landing-page assistant architecture figure

The figure in `apps/landing/src/AssistantDiagram.tsx` describes the current CIM/PIM
assistant at the logical architecture level. It is embedded in the landing page's
LLM-supported modeling section and uses the existing landing-page design tokens.

## Implementation traceability

Paths below are relative to the repository root.

| Figure element                                                                                                  | Implementation evidence                                                                                                             |
| --------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------- |
| Chatbot requests, asynchronous acceptance, model revision, attachments, and user controls                       | `apps/backend/src/main/java/io/mehdieidi/modriss/backend/api/ChatbotController.java`                                                |
| Browser polling, event streaming, confirmation, continuation, rebase, undo, and rollback controls               | `apps/frontend/js/chat.js`                                                                                                          |
| Durable turns, worker leases, checkpoint transactions, events, and partial outcomes                             | `apps/backend/src/main/java/io/mehdieidi/modriss/backend/assistant/DurableAssistantTurnWorker.java`                                 |
| LLM strategy selection within backend restrictions; read-only explanation; allowlisted action loop              | `packages/java/platform-assistant/src/main/java/io/mehdieidi/modriss/platform/assistant/agent/AgentTurnLoop.java`                   |
| Obligation ledger, EClass selection, blueprint, private slices, configurable LLM review, and bounded correction | `packages/java/platform-assistant/src/main/java/io/mehdieidi/modriss/platform/assistant/agent/ConceptualInstanceModelWorkflow.java` |
| Command batches, contract checks, destructive confirmation, and structural validation                           | `packages/java/platform-assistant/src/main/java/io/mehdieidi/modriss/platform/assistant/tools/AgentModelTools.java`                 |
| Isolated candidate, structural gate, inverse patch, and revision-checked publication                            | `packages/java/platform-assistant/src/main/java/io/mehdieidi/modriss/platform/assistant/workspace/ModelWorkspace.java`              |

## Reading and maintaining the abstraction

- Arrows represent logical execution or context flow, not one HTTP call per arrow.
  Dashed return paths carry replies and model updates back to the workbench. The
  controller supports polling and authenticated event replay; transport details
  are omitted from the figure.
- The durable-turn boundary groups the controller, worker, facade, turn service,
  and adaptive loop. The LLM hub branches to three alternative strategies.
  Provider calls occur in routing and within the execution routes;
  the diagram does not imply a separate autonomous LLM service writes the model.
- Modeling context groups current model state, conversation, live Ecore contracts,
  and optional source units. Each stage uses relevant context; the figure does not
  claim that the complete metamodel is sent in every prompt or require a particular
  metamodel profile or retrieval algorithm.
- The routes are alternatives. Inspect/edit can also ask for input or answer
  without mutation. Explanation has no mutation or checkpoint edge.
- Conceptual generation includes optional LLM obligation review. Requirement
  coverage and source evidence are distinct from formal semantic validity.
- Assistant-generated actions, patches, proposals, checkpoints, and model outputs
  are gated only by structural Ecore/EMF conformance through
  `ModelService.validateStructural(...)`. EVL semantic validation belongs only to
  explicit model-validation workflows outside chatbot generation, repair, apply,
  and commit paths.
- The draft represents compilation and private staging; the diamond represents
  structural conformance. These checks occur across the implementation, rather
  than at one exclusive validation call. Revision checks additionally apply at
  publication. The repair edge abstracts bounded LLM-authored correction and
  recompilation; it does not represent autonomous deterministic semantic repair.
- Small graph fragments illustrate model structure, staged additions, and saved
  state. They are schematic symbols, not specific EClasses or example instances.
- The small-screen layout stacks the same components vertically and collapses
  the explanation return edge into its terminal read-only reply symbol. The
  desktop durable-turn boundary is omitted in that layout to preserve legibility.
- A checkpoint is atomic. A durable turn may span multiple checkpoints, so a
  partial or failed later step does not erase earlier committed checkpoints.
- User confirmation is for destructive actions, not a mandatory approve/reject
  proposal stage for every mutation. Undo and rollback use recorded inverse patches.
- Detailed generation stages (obligations, type selection, blueprint, slices, and
  optional review), the inspect/tool loop, storage tables, source provenance,
  provider-specific retries, user controls, and legacy endpoints are abstracted
  behind the corresponding components. The linked assistant guide documents
  those details. This is an implementation overview, not a reliability claim.

Check these sources when updating the figure; older proposal-oriented diagrams
may describe a superseded execution model.
