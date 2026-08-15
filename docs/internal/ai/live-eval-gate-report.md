# Assistant live-evaluation status

Updated: 2026-08-15

This is a curated index of the latest acceptance evidence. Raw reports under `target/` remain the
authoritative per-run record, including failed attempts.

## 2026-08-15 persisted-CIM evolution investigation

One bounded `cim-feature-evolution` run used Gemma in normal metamodel mode with external replay
disabled. The fixture timed out after 428 seconds during its first, empty-model creation turn; the
second persisted edit was never reached. The turn generated and durably retained all 14 planned
objects, used 20 provider calls and 43,775 / 9,617 prompt/completion tokens according to the
authoritative call ledger, committed no model checkpoint, and was cancelled safely with no visible
model mutation.

This was a product-workflow failure rather than a zero-token provider outage. Three invalid
associations had been accepted by focused slices and discovered only after all objects were staged:
two links declared `Stakeholder` while targeting `Actor`, and one `ValueObject` emitted a
nonexistent `attributes` containment. Six late correction calls and the bounded automatic recovery
then exhausted the latency envelope.

Focused slice acceptance now checks every emitted association against live Ecore contracts:
writable feature existence, containment/reference placement, non-empty fields, target ID and exact
target EClass, target assignability, single-valued multiplicity, and blueprint containment
ownership. These checks are structural only. They cause the original small slice to be corrected
before later generation calls are spent. A focused regression reproduces the live
`Stakeholder`/`Actor` mismatch, and the conceptual workflow suite now passes 23 tests.

The run also exposed stale cancellation accounting: the per-call ledger contained 20 calls while
the turn summary showed 18. Provider-call insertion now reconciles aggregate calls and tokens from
the idempotent ledger, so cancellation or a worker crash after a provider response cannot hide
already-spent cost. The frontend now displays aggregate provider calls/tokens and explicit bounded
automatic-continuation/recovery progress. The post-fix live acceptance run remains outstanding;
this evidence does not prove persisted evolution reliability.

The one permitted post-fix rerun also timed out at 429 seconds during initial creation, before the
persisted edit. Early validation behaved as intended: invalid object shape, both required
`DomainEntity` identity references, and the invalid `ValueObject.attributes` association were
rejected in their focused slices. Gemma nevertheless needed repeated corrections and had staged
only 8 of 14 objects when the deadline arrived. The terminal ledger records 17 calls and 37,554 /
9,618 prompt/completion tokens, zero committed checkpoints, and no visible mutation. An in-flight
call returned HTTP 403 after cancellation was requested, exposing a state race where provider
failure could win over cancellation. The worker now gives cancellation precedence after recording
telemetry, and migration V31 corrected affected historical terminal rows and events. This rerun was
not repeated; current evidence says early checking is fixed, while Gemma latency/output quality
still prevents this fixture from meeting its acceptance budget.

The final Docker Hub metadata lookup returned HTTP 403 for the Maven base image. Because the
application JAR built locally and the prior backend image already contained the unchanged JRE,
system packages, metamodel files, non-root user, and entrypoint, deployment replaced only
`/app/backend.jar` in a container derived from that image. The final running image is
`sha256:82d5d818c82a43dfe7a4875de9b724ce1745993e777f3176b0b3e222a97433ed`; prior images are retained
under dated `pre-*` tags. Migrations V30 and V31 reconciled provider aggregates and cancellation
race outcomes. Health is UP with Gemma, normal mode, LLM review disabled, one provider retry, and
no queued or running turns.

## 2026-08-14 Gemma reliability follow-up

With `Gemma-4-31B-IT`, LLM review disabled, structural validation enabled, and manual durable
resumes disabled, `create-pim-serverless` reached `SUCCEEDED` in 151 seconds. It committed one
structurally valid checkpoint with 33 structural nodes, 20 audited provider calls, and 60,100 /
13,149 prompt/completion tokens. The first compiler budget ended on an invalid `Subscription`
reference; the worker automatically requeued the same turn, restored its obligation ledger,
selected types, blueprint, and staged objects, and completed after two additional real calls. The
committed model includes API routing, command handling, event publication and consumption,
persistence and schema, payment integration, observability, authorization, and workflow behavior.

The raw report
`target/live-eval/create-pim-serverless-gemma-accounted-auto-recovery-20260814.md` recorded the
product turn as successful but the gate as failed because the gate recognized `SecurityPolicy`
but not the valid `AuthorizationPolicy` subtype selected by Gemma. That stale gate condition is now
corrected. This is one successful stochastic run, not the repeated-run release campaign.

The exact user prompt, `Create a serverless model for online doctor visit appointment booking.`,
is now a versioned live fixture (`create-pim-doctor-booking`). Provider access recovered after an
initial HTTP 403. Two of the next three independent product runs succeeded in 118 and 103 seconds,
committing structurally valid 27- and 28-node models. They used different valid architectures: one
function-centric and one workflow-centric. The third run failed safely after malformed provider
JSON on the initial pass and its single automatic recovery; its complete blueprint remained
durable and the visible model was not changed. Current exact-prompt evidence is therefore 2/3, not
release-grade reliability.

The Docker Hub 403 is also cleared. A fresh backend image was built from the Temurin base image and
deployed healthy. The latest image after the required-reference slice fix is
`sha256:e6554d9db1bb9d827689aaa690f9d724d4b2399d8cbde8390f4255a4d8c86833`.

Persisted evolution exposed a late-validation defect: a correct blueprint planned a
`DomainEntity` identifier, but generated slices could omit one of its named required references
and consume the full generation budget before compiler rejection. Required writable Ecore
references are now checked and corrected inside each focused slice. All 22 focused conceptual
workflow tests pass. The post-deployment live rerun was blocked before reasoning by two Gemma
transport failures (zero tokens and zero mutation), so live evolution proof is still outstanding.
The application permits one bounded transport retry. The eval harness now also defaults to at most
one isolated replay and aggregates latency, calls, and tokens across attempts instead of hiding
earlier replay cost.

The persisted-state audit also found a historical expired turn stranded in `RUNNING` after its
worker lease ended. Timeout cleanup now includes abandoned running leases as well as queued turns.
The PostgreSQL regression suite passes, and after deployment the historical row automatically
transitioned to `TIMED_OUT`; no queued or running turns remain.

## Inspect/contract agent baseline

| Fixture                 | Result | Latency | Calls | Prompt/completion tokens | Outcome                                                        |
| ----------------------- | ------ | ------: | ----: | -----------------------: | -------------------------------------------------------------- |
| `create-cim-library`    | Passed |   112 s |     4 |          22,193 / 15,748 | 46 nodes, structural validity                                  |
| `cim-feature-evolution` | Passed |   233 s |     8 |          56,904 / 36,843 | 106 final nodes, original feature preserved, new feature added |
| `source-to-cim-pantry`  | Passed |   118 s |     3 |          17,659 / 25,357 | 69 nodes, complete source coverage                             |
| `create-pim-serverless` | Passed |   163 s |    10 |          52,161 / 22,652 | 28 nodes, structural validity                                  |

## Conceptual strategy

| Fixture                 | Result                  | Evidence                                                                                                   |
| ----------------------- | ----------------------- | ---------------------------------------------------------------------------------------------------------- |
| `create-cim-library`    | Passed in optimized run | 91 s, 2 calls, 4,439 / 14,895 tokens, 40 nodes                                                             |
| `cim-feature-evolution` | Failed final acceptance | First feature checkpoint saved, but the second persisted-model update failed; earlier attempts also failed |
| `source-to-cim-pantry`  | Passed                  | 145 s, 3 calls, 5,373 / 12,240 tokens, complete coverage                                                   |
| `create-pim-serverless` | Passed                  | 124 s, 4 calls, 20,350 / 22,346 tokens, compiler repair then structural checkpoint                         |

## Unified production strategy

| Fixture                 | Result                | Evidence                                                                                                                                                                                             |
| ----------------------- | --------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `create-cim-library`    | Not reliably accepted | A 243 s run succeeded and saved a structural checkpoint, but the then-current four-call gate rejected it. The final eight-call rerun failed after 398 s on length-limited output with no checkpoint. |
| `cim-feature-evolution` | Passed                | 266 s, 9 calls, two checkpoints; prior feature preserved and new feature added through the agent path                                                                                                |
| `source-to-cim-pantry`  | Passed                | 121 s, 4 calls, complete source coverage/provenance                                                                                                                                                  |
| `create-pim-serverless` | Passed                | 85 s, 6 calls, structural checkpoint through conceptual generation                                                                                                                                   |

## Staged conceptual follow-up

After replacing monolithic conceptual output with the stable-ID blueprint/slice/review protocol,
`create-cim-library` passed a live unified gate against Arvan `DeepSeek-V4-Flash`: `SUCCEEDED`, 304
seconds, 8 audited calls, 21,720 prompt tokens, 23,286 completion tokens, one checkpoint, and
structural validity. Earlier failed staged attempts remain in `target/`; this single pass does not
yet satisfy the repeated-run release gate.

The subsequent final two-object verification on 2026-08-12 failed safely after 130 seconds: two
slice responses were length-limited, four of eight private objects were durably staged, the
conceptual review reserve was reached after five conceptual calls (seven calls including adaptive
routing), and no checkpoint or partial model revision was published. The active follow-up profile
therefore uses one DeepSeek object per slice, stricter bounded JSON collections, smaller
stage-specific completion limits, and explicit recovery/review capacity.

A later one-object run generated and durably staged all seven planned objects without publishing a
partial revision, but its 14-call ceiling was consumed by routing, blueprint correction, two slice
corrections, and review. The LLM reviewer then correctly rejected a missing planned policy
relationship, with no call left to apply its correction. The active ceiling is now 18 so review and
compiler correction cannot be starved by earlier bounded recoveries. Focused tests pass; this final
budget still awaits a successful live rerun and the repeated acceptance campaign.

The first 18-call run then completed all slices, review, and one compiler correction, but correctly
published no checkpoint because the blueprint had selected `DomainEntity` without planning its
required `identityAttributes` and `primaryIdentityAttribute` references to `InformationItem`.
Required writable containment and non-containment reference closure is now checked against Ecore at
blueprint time, where DeepSeek can add the stable dependency IDs or choose another suitable EClass.
That final invariant is covered by focused tests and the rebuilt backend is healthy, but it has not
yet completed a live acceptance run.

The immediate rerun failed safely before slicing when DeepSeek's blueprint correction reached a
temporary 2,500-token stage cap. The cap has been restored to the previously proven 8,000-token
blueprint headroom; object count, schema collections, one-object slices, provider calls, and total
turn time remain bounded. No committed model checkpoint was created by this attempt.

The final bounded-attribute configuration passed a fresh live `create-cim-library` run against
Arvan `DeepSeek-V4-Flash` on 2026-08-12 with provider retries and automatic durable resumes both
disabled. The turn succeeded with 16 audited calls, 21,120 prompt tokens, 21,278 completion tokens,
one committed checkpoint at revision 2, and a structurally valid result containing seven conceptual
objects and eight relationships. One type-selection response was length-limited; the workflow's
bounded semantic retry recovered within the 20-call budget. The persisted turn totals reconcile
exactly with the 16 provider-call rows, and all eight conceptual work items are `generated`.

This is one successful stochastic run of the final configuration. It does not satisfy the required
ten consecutive passes, controlled live-provider restart matrix, paraphrase-corpus threshold, or
human usefulness gate.

## 2026-08-13 Arvan hardening follow-up

With provider retries and automatic durable resumes disabled, the final eight-object profile passed
`create-pim-serverless` in 269 seconds with 15 audited calls, 22,460 prompt tokens, 32,396
completion tokens, one committed checkpoint, and structural validity. Persisted call rows and token
totals reconciled exactly. Manual semantic inspection then found that the structurally valid model
had omitted explicitly requested command, workflow, and payment-integration concepts; the old gate's
five-node threshold did not detect that shallow result.

The conceptual protocol now rejects a blueprint that silently omits an EClass selected by the LLM,
uses the actual combined Ecore closure instead of summing overlapping per-type closure costs, gives
the LLM exact marginal closure savings when a selection is too large, and preserves those diagnostics
across Arvan length truncation. Required-attribute and malformed-association diagnostics are also
combined into one bounded slice correction. Focused regressions cover each behavior.

An expanded 12-object run subsequently produced a much stronger model with 12 objects and 12 real
relationships, including `Function`, `EventType`, `DataModel`, `DataStore`, `ExternalAdapter`, its
`ExternalEndpoint`, observability, and security. It committed atomically and was structurally valid,
with exact reconciliation of 19 calls and 39,631 / 39,859 tokens, but took 459 seconds and omitted
the requested workflow after capacity pressure. It therefore failed the 420-second gate and is not a
release acceptance pass.

The active bounded ceiling is now 16 private blueprint objects. Large DeepSeek blueprints start with
two-object slices and durably split to one on length truncation. Blueprint correction has three
bounded attempts, retains earlier rejection diagnostics after truncation, and cannot accidentally
accept the final rejected candidate. This newest profile is focused-test green but still requires a
fresh live acceptance run and repeated reliability evidence.

The first 16-object live run failed safely after 537 seconds and 16 calls with no checkpoint because
the Ecore closure exposed abstract `WorkflowStep` as though it were instantiable. The active code now
counts an abstract required target as a real object in capacity estimates, supplies the LLM with all
exact concrete assignable subtype choices, permits those choices in the otherwise closed blueprint
vocabulary, and rejects abstract/non-creatable blueprint objects before slicing. This post-run fix is
focused-test green and deployed locally, but has not yet received a confirming live run.

The exact post-fix rerun (`create-pim-serverless-abstract-target-fixed-20260813.md`) confirmed that
the abstract-target failure is gone. DeepSeek selected `TaskStep` for `Workflow.steps`; all 14
blueprint objects were privately generated, including API, event type/bus, datastore/data model,
payment adapter/endpoint, observability, security/auth, workflow, schema, and the concrete step.
The turn nevertheless failed atomically after 453 seconds and 16 audited calls because both final
quality-review attempts ended with `finish_reason=length`. There was no committed checkpoint or
published model mutation. Persisted accounting reconciled exactly to 32,828 prompt and 30,456
completion tokens; three calls were length-truncated. The checkpoint table contained only its
initial `INTENT` row at base revision 1, not a committed checkpoint.

That failure exposed both a provider-latency problem and the architectural weakness of relying on a
broad final review without durable requirement identity. The active follow-up now persists an
LLM-generated obligation ledger before type selection, requires mandatory obligation allocations in
the blueprint, and uses a compact independent final satisfaction verdict. Cited object IDs and
relationships are checked against the actual private staged model; any mandatory `PARTIAL` or
`MISSING` result prevents a checkpoint. The live gate now additionally requires 100% mandatory
obligation coverage and exact structural evidence for API, function behavior, event type,
datastore, payment adapter, observability, security, workflow, a concrete workflow step, and
meaningful relationships. Focused tests are green, but this new ledger profile still needs a clean
Arvan confirmation and therefore is not release evidence yet.

Four zero-provider-retry, zero-durable-resume follow-ups exercised that profile. The first exposed
a JSON-transport defect: the new ledger/review stages were mistakenly action-wrapped, so the
ledger parser honestly failed after 45 seconds and four calls with no mutation. The provider now
classifies both stages as structured documents, with a regression covering the Arvan JSON path.

The second run reached all 14 private work items in 317 seconds and 17 audited calls. Its
independent review rejected six obligations because it interpreted each obligation's
`expectedEClasses` alternatives as a conjunctive checklist. No checkpoint was published. Review
instructions now state the intended disjunctive semantics and forbid inventing stronger
requirements; the fixture prompt was also aligned with its workflow-evidence gate.

The third run generated a sound eight-obligation ledger, then encountered three consecutive
Arvan type-selection `finish_reason=length` responses and failed atomically after 320 seconds and
six calls. Truncated type-selection retries now use only the LLM-authored candidate EClasses,
their exact closure costs, the ledger, and the request instead of repeating the global PIM index.
The fourth run confirmed that reduction: retry user prompts fell from 6,312 characters to roughly
3,200 and wall time fell to 239 seconds. It still published no checkpoint because the third and
final LLM selection had a 20-type combined closure against capacity 14. The deterministic gate
reported exact marginal savings; one bounded fourth selection attempt is now available so the LLM
can act on that final diagnostic. This latest correction is focused-test green but not yet live
confirmed.

Across these failures, the existing model remained at revision 1 with its two initial structural
nodes. The gate no longer records structurally valid but semantically shallow output as success.
These runs are evidence of honest atomic failure and improved diagnosis, not production readiness.

All assistant-generated output remains gated only by structural Ecore/EMF conformance through
`ModelService.validateStructural(...)`. EVL is never invoked in assistant generation, correction,
review, apply, or commit paths.

## Interpretation

- Provider connectivity alone is not acceptance.
- A structurally valid checkpoint is not proof of semantic usefulness.
- Source coverage is accounting, not EVL validity.
- A successful stochastic attempt does not erase a later gated failure.
- Current evidence justifies conceptual generation for bounded empty models and the inspect/contract
  agent for persisted updates.

Focused conceptual/compiler/provider/adaptive tests passed 75/75 before the final bounded-attempt
change, and focused coverage for the new fourth-attempt behavior passes. The complete backend
reactor package build passed before the final bounded-attempt change. No formatter or linter was
used.

See [assistant-approach-comparison.md](assistant-approach-comparison.md) for the complete comparison
and exact report filenames.
