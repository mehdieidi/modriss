# WorkspaceFlow demonstration

Follow the function-backed sequential workflow, ALLOW/READY choices, failure defaults, five-second wait and concrete ASL states.

Source: `.idea/scope/11-business-orchestration/case-study/README.md`. These are small reviewed examples of the specified baseline; change exercises in the brief remain manual modeling exercises.

Import each file into the matching CIM, PIM or PSM tab of a fresh project. Use separate projects for different cases.

- [CIM model](workspaceflow.cim.xmi)
- [PIM model](workspaceflow.pim.xmi)
- [PSM model](workspaceflow.awspsm.xmi)

The PIM/PSM are authored refinements. Transforming the CIM or PIM produces a new draft with its own review decisions; retain that draft separately.

Rules to explain during the demo:

- Require 1 through 30 seats and nonblank courseCode; request replay preserves one identity.
- ValidateRequest -> ALLOW/DENY -> AllocateWorkspace -> CreateManifest -> five-second wait -> VerifyWorkspace -> READY/FAILED.
- Task results retain request identity at $.stepResults; evaluator tasks return explicit outcome envelopes and unknown values fail.
- Stable task identities prevent duplicate workspace/manifest; failures remain visible for authorized recovery, without automatic compensation.

Assistant model verification uses only structural Ecore/EMF conformance through `ModelService.validateStructural(...)`. EVL semantic validation is a separate explicit user/model workflow. Importability and model conformance do not establish implemented application behavior.
