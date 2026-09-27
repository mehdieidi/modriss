# MODRISS evaluation plan for a journal submission

**Status:** proposed research protocol; no new study results are reported here.  
**Prepared:** 2026-09-27.  
**Evaluation object:** the development process, the CIM–PIM–PSM modeling framework, and their integrated use in serverless software development.  
**Intended publication:** an original methodology/design-science contribution supported by technical and practitioner evidence.

## 1. Recommendation and publication objective

Evaluate MODRISS as a socio-technical methodology, not just a code generator.
The strongest practical research package is:

1. A reproducible technical evaluation of all three DSMLs, transformations,
   validation, generation, traceability and change reconciliation.
2. Independent expert review of the method-engineering argument, domain fit,
   process handoffs and situational tailoring.
3. Two contrasting longitudinal practitioner cases, with working applications,
   operational feedback and subsequent changes.
4. A focused controlled comparison of modeling/change tasks if the paper will
   claim improvements in task accuracy or effort attributable to MODRISS.
5. Independent reassessment using Eidi's criteria and independent reproduction
   of the principal technical results.

For the whole-methodology paper, prioritize working artifacts and practitioner
cases over another author-generated walkthrough. A controlled task experiment
adds useful evidence, but cannot replace longitudinal process evaluation.
Conversely, two case studies cannot establish a general causal productivity
advantage. If resources require a choice, keep the technical evaluation, expert
review, contrasting cases and reproducibility package; narrow causal claims
rather than run an inadequately powered experiment.

Q1 is a journal classification, not a research design or an acceptance
guarantee. Before selecting a venue, record the ranking database, release year,
subject category and journal identity; JCR and SJR quartiles must not be treated
as interchangeable. This plan does not assert current quartiles. Publication
also requires a sufficiently novel contribution, relevant positioning, coherent
argument and credible reporting; a large evaluation cannot rescue a contribution
that only repackages established ideas.

**Proposed central contribution:** a situationally configurable development
method that connects serverless requirements, explicit model abstractions,
engineering automation, evidence-bearing delivery and operational evolution.
The paper must explain which design decisions are new, why existing approaches
are inadequate in the selected situations, and which transferable lessons the
evaluation establishes. "Three DSMLs implemented using EMF/Epsilon" is an
implementation description, not by itself a demonstrated research contribution.

The distinctions between verification, contextual validity and practical value
follow recent modeling-method guidance [R1]. The staged progression from early
diagnosis to evaluation of a stable artifact follows the design-science framing
of FEDS [R2]. The concrete studies, quantities and decision rules below are
recommendations for MODRISS, not requirements imposed by those publications or
by every Q1 journal.

## 2. Starting evidence and unresolved claims

Use the following existing materials as inputs, with their stated limits:

| Existing material                                                                                         | Legitimate use                                                                 | What it does not establish                                                  |
| --------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------ | --------------------------------------------------------------------------- |
| [Method construction](../mde/process/method/03-method-construction.md), requirements and fragment library | Trace the rationale for construction and reuse.                                | Independent confirmation that the right requirements/fragments were chosen. |
| [Eidi assessment](../mde/process/method/07-evaluation.md)                                                 | Initial 78-row feature assessment and identified gaps.                         | User effectiveness, comparative superiority or independent agreement.       |
| [ColdChain scenario](../mde/process/method/13-hypothetical-enactment-and-process-validation.md)           | Definition inventory and scripted lifecycle examples.                          | Tasks actually performed, usable outputs or accepted production gates.      |
| [ReturnFlow audit](../mde/process/method/14-returnflow-enactment-audit.md)                                | Thirty grouped steps, seven findings and adverse handoff cases to investigate. | An implemented ReturnFlow system or practitioner study.                     |
| [ReturnFlow checks](../mde/process/method/evaluation/returnflow/verification.md)                          | Reproducible checks of selected record contracts and repository consistency.   | General process soundness or correctness of generated applications.         |
| Repository models, tests and sample reports                                                               | Candidate technical assets and historical evidence to inspect and rerun.       | A fresh, independent end-to-end result on a frozen version.                 |
| [Existing empirical protocol](../mde/process/method/09-empirical-validation-protocol.md)                  | Foundation for longitudinal case collection.                                   | Completed empirical validation.                                             |

This document extends the existing protocol with component-level evaluation,
comparators, experimental design, measurement definitions, publication claims
and execution priorities. Resolve conflicts before registering a study; do not
maintain two incompatible protocols silently.

## 3. Claims, research questions and falsification

Use separate claim identifiers throughout the manuscript and evidence package.

| Claim / research question                                                                        | Evaluation object                                                  | Required evidence                                                                                   | Observation that weakens or refutes the claim                                                                        |
| ------------------------------------------------------------------------------------------------ | ------------------------------------------------------------------ | --------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------- |
| C1 / RQ1: Is the process sufficiently specified and connected for teams to enact it?             | Roles, task inputs/outputs, gate authority, failure/re-entry paths | Static contract checks, independent handoff review and task-occurrence records from cases           | Missing input producer, contradictory gate, repeated researcher rescue or undefined next action                      |
| C2 / RQ2: Do the DSMLs represent relevant serverless concerns with clear abstraction boundaries? | CIM/PIM/PSM abstract syntax, semantics and concrete notation       | Independently sourced domain scenarios, actual models, domain review and comprehension tasks        | Important concern requires an undocumented escape hatch, conflicting meanings or provider details leak into CIM/PIM  |
| C3 / RQ3: Does the implemented chain preserve intended properties and produce usable artifacts?  | Ecore, EVL, ETL, EGL/EGX, traces and reconciliation                | Oracle-based tests, adversarial models, regeneration/change tests and deployed vertical slices      | Silent meaning loss, unreported unsupported mapping, destructive merge, insecure/wrong generated artifact            |
| C4 / RQ4: What benefits and costs arise when the complete methodology is used?                   | Team work, product quality and engineering effort                  | Repeated practitioner enactment, comparator evidence, independent product assessment and interviews | Equal/worse correctness, excessive modeling/review cost, late defects or unmanageable coordination                   |
| C5 / RQ5: Does situational tailoring improve fit while preserving control objectives?            | Fragment selection, assembly and profile evolution                 | Independent tailoring exercises and contrasting case profiles                                       | Different situations produce the same unjustified profile, or omissions cause unowned risks/invalid acceptance       |
| C6 / RQ6: Is MODRISS learnable and useful to people other than its authors?                      | Modeling interaction and process use                               | Training records, unassisted tasks, intervention logs and practitioner observations                 | Persistent confusion, dependence on the author, abandonment or low willingness to continue despite technical success |
| C7 / RQ7: Under which circumstances do effects hold, and can results be reproduced?              | Transferability and evidence reliability                           | Contrasting contexts, boundary cases and independent artifact execution                             | Results depend on a single domain, undocumented environment, hidden customization or privileged researcher knowledge |

Do not predeclare "MODRISS is superior" as the only acceptable conclusion.
Possible contributions include a demonstrated benefit in a bounded setting,
identification of a cost/quality tradeoff, or evidence that particular method
fragments are useful only under certain conditions. A revised methodology after
negative results is legitimate; the revision and its new evidence must be
distinguished from the version that failed.

## 4. Scope and experimental treatment

Freeze a versioned evaluation release containing the process/profile schema,
method library, Emfatic and combined Ecore files, constraints, transformations,
generation templates, runtime/tool dependencies, documentation and training.
Identify implementation gaps before recruitment. A defect that prevents a
study from proceeding is evidence, not a reason to erase that session.

Disable the LLM modeling assistant in the primary methodology studies. This
keeps method/framework effects separate from AI assistance. If assistant use
is operationally unavoidable, give all conditions the same declared access,
capture usage and make the resulting treatment "methodology plus assistant".
A separate optional assistant experiment must pin model/service version,
prompt/tool configuration, task conditions and cost, and address nondeterminism.
Do not mix its results into the primary methodology effect without an explicit
factorial or other justified design.

**Validation boundary:** assistant-generated actions, patches, proposals,
checkpoints and model output are gated only by structural Ecore/EMF conformance
through `ModelService.validateStructural(...)`. EVL semantic validation is
permitted as an explicit user/model evaluation workflow outside assistant
apply/repair/commit. Study instrumentation must preserve this boundary.

The implemented PSM scope is AWS. Provider-independent CIM/PIM intent is
evaluated as an abstraction property; successful multi-cloud realization is
not claimed without a second implemented provider chain and behavioral tests.

## 5. Study portfolio and sequencing

| Study                                            | Purpose and outputs                                                                                        | Priority and dependency                                                                           |
| ------------------------------------------------ | ---------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------- |
| S0: protocol, novelty and evidence baseline      | Updated related-work map; frozen claims, measures, comparators, instruments, recruitment and analysis plan | Required first; no outcome-based redesign of confirmatory hypotheses                              |
| S1: technical verification and domain validation | Executable model/application corpus, independent oracles, failures, correctness/coverage/scaling results   | Required before practitioner deployment                                                           |
| S2: independent method and language review       | Individual assessments, handoff defects, tailoring disagreements, rationale and revisions                  | Required; formative round before freeze, separate final assessment afterwards                     |
| S3: controlled modeling/change-task comparison   | Task correctness, completion, effort and comprehension under a specified contrast                          | Recommended for bounded causal/task-performance claims; pilot and sample-size justification first |
| S4: longitudinal practitioner cases              | Full delivery/change evidence, organizational fit, effort, observed outcomes and negative cases            | Required for this plan's practical whole-methodology claim                                        |
| S5: independent reassessment and replication     | Eidi ratings with disagreement records; clean-environment reproduction; public package                     | Required before submission; can overlap final case analysis                                       |

ColdChain and ReturnFlow are formative inputs. They are not two completed S4
cases. Findings already used to change the method are not held-out confirmation
of the revised method.

### S0 — Establish the research contract

Create a claim-to-evidence matrix and update the literature search beyond the
supplied review paper. Record search date, sources, terms, inclusion rules and
versions. Examine recent serverless/MDE methodologies and general methodology
evaluation research. Use the supplied Eidi paper as a starting point, not a
closed list of competitors or a substitute for novelty analysis.

Screen the reviewed approaches, including RADON, the standards-based workflow
method and LEMMA-based work, as potential comparators for the capabilities they
actually address. Verify available versions and reproducibility before choosing
one. Select the closest credible MDE comparator for a common supported task
subset, alongside the conventional engineering baseline where practical. Do not
require a specialized modeling tool to enact an entire lifecycle it never claims
to support. Report installation failures and unsupported scope; a paper-only
feature comparison or author-built imitation is not an executed competitor.

For each claimed contribution, record: antecedent work; the gap; the proposed
mechanism; the exact MODRISS addition; expected benefit/cost; study; measure;
and the strongest rival explanation. Separate original design decisions from
adopted SME/MDA/DevOps practices.

Register the summative protocol and analysis plan before outcome collection
using an appropriate institutional or research registry. Archive the timestamped
version even if public release must wait for confidentiality reasons. Retain
amendments with dates/reasons; mark subsequent analyses exploratory. Consult
the relevant ACM SIGSOFT empirical standards as reporting/design checks, not
as proof that following a checklist ensures research quality [R3–R5].

### S1 — Verify the modeling framework and its process interfaces

Build three complementary corpora: existing regression examples, independently
specified realistic systems, and adversarial/boundary models. Predeclare a
holdout subset whose domain requirements and expected outcomes are written by
someone who did not implement the relevant mapping or template.

Use ReturnFlow as the first fully implemented vertical slice: actual CIM,
generated/refined PIM, generated/refined AWS PSM, completed application logic,
deployed test environment and an operationally motivated change. Use ColdChain
as a contrasting event/time workload if it becomes executable. Add an external
domain brief to reduce adaptation to the two familiar examples. One system
cannot establish broad metamodel coverage.

| Technical layer       | Protocol and independent oracle                                                                                                                                                                                                                       | Reported evidence                                                                                                                        |
| --------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------- |
| Abstract syntax       | Rebuild combined Ecore from modular Emfatic; check package/type/reference resolution, containment, multiplicity, serialization/load cycles and invalid instances. Compare semantic schema content, not just file bytes.                               | Metamodel/version matrix; failing cases; precise scope of structural assurance                                                           |
| Domain expressiveness | Domain experts first describe scenarios without seeing the DSML vocabulary. Model each concern as native, documented extension/manual representation, unsupported, or legitimately out of scope. Review abstraction leakage and redundant constructs. | Concern→element/relationship→decision mapping; unmet concerns and justified exclusions                                                   |
| Semantic constraints  | In the explicit model-validation workflow, run expert-labeled valid/invalid models and targeted mutations. Include interacting rules, false positives, diagnostic usefulness and accepted exceptions.                                                 | True/false positives and negatives with defined units; equivalent/invalid mutations separated; rule coverage rather than only test count |
| CIM→PIM and PIM→PSM   | Declare each mapping's preconditions, preserved intent, introduced assumptions and target invariants. Check branching, multiplicities, ownership, identifiers and trace links. Manually derive reference outcomes for a held-out subset.              | Mapping coverage, meaning-loss defects, unsupported decisions and reproducible reports                                                   |
| Reconciliation        | Exercise source-only, target-only, overlapping, deletion, reference-change and rename scenarios. Confirm preserved refinements, surfaced conflicts and explicit resolution.                                                                           | Lost-edit/conflict detection results and resolution effort; do not call forward merge general round-trip engineering                     |
| Artifact generation   | Generate from the same accepted tuple twice; compare outputs with a declared normalization of nonsemantic metadata. Build and execute results, inspect access/configuration, fill business logic separately, then regenerate.                         | Artifact manifests, build/test results, extension effort, preserved custom work, normalization rules and differences                     |
| Cross-level meaning   | Follow a requirement through each level to an independently specified acceptance/fault test; change the requirement and repeat.                                                                                                                       | Correct and missing links, revision provenance and change-propagation defects                                                            |
| Process contracts     | Exercise accepted, blocked, deferred, exception and rework decisions, including first release, emergency reconciliation and archive custody. Review conditional task inputs and legitimate external inputs.                                           | Reachability/ownership counterexamples and handoff evidence; name the formalism if formal verification is used                           |
| Scale                 | Vary model size, reference density and feature composition. Measure load/edit/validation/transform/generation time and memory under pinned conditions.                                                                                                | Distributions and practical failure boundaries; large duplicate-element models alone do not demonstrate realistic scalability            |

An ETL run plus successful EMF loading proves neither semantic preservation nor
business correctness. Tests generated by the same rules that generate the
application are useful regression artifacts but are not independent oracles.
Use separately written domain assertions, boundary examples and adversarial
inputs. Mutation scores and classifier coverage provide partial evidence, not
proof of complete semantic correctness.

Run integration tests in an isolated AWS account as well as local/emulated
environments where useful. Label emulator and cloud results separately. Record
region/date, runtime, architecture, function memory/concurrency, warm/cold
conditions, workload shape, quotas, retries, logging and deployment configuration.
Repeat independent workload windows and report uncertainty; one invocation
cannot establish latency or reliability.

Required fault scenarios include duplicate/out-of-order events, poison messages,
provider success followed by client timeout, failure between state persistence
and event publication, quota/throttle behavior, partial batch failure, schema
evolution with old consumers, permission denial, recovery with irreversible
side effects and retained records after service retirement. Use sandbox payment
or other non-production external effects. Thresholds come from each case's
requirements and risk decisions, not an arbitrary universal pass percentage.

Exit S1 when a new evaluator can build and run the declared supported chain,
the critical scenario oracles pass, and unsupported behavior fails explicitly.
Keep all failures and fixes in the evidence package. Known residual defects
must bound participant exposure and manuscript claims.

### S2 — Review method engineering, tailoring and usability assumptions

Recruit a purposive panel covering method/process engineering, MDE/DSMLs,
serverless architecture/operations and application-domain knowledge. A planning
target is **6–10 external experts**, chosen for complementary experience and
independence, not as a statistically representative population or a universal
adequacy threshold. Report invitation/recruitment outcomes, experience and
relationships to the authors. Authors may clarify documentation but must not
count as independent reviewers of their own method.

Provide two deliberately contrasting situation briefs. Each reviewer, or an
explicitly identified reviewer pair, independently selects a method profile,
identifies required tasks/artifacts, resolves a failed handoff and explains a
maintenance/retirement decision. The reference is preservation of control
objectives with justified tradeoffs; identical profile selections are not the
goal. Different valid configurations may exist.

Use anchored questions about completeness, consistency, assignability,
input/output compatibility, task granularity, cognitive burden and context fit.
Require a concrete artifact or counterexample for each major criticism. Ask
reviewers to identify omissions and unnecessary work before showing author
ratings. Collect individual assessments before discussion to reduce conformity.

Preserve initial ratings, disagreements, proposed changes, accepted/rejected
revisions and reasons. A facilitated consensus meeting is a refinement method,
not evidence of independent agreement. Use a different brief or held-out
scenario in the final review after revisions. Report unresolved minority views.
Language/notation inspection should include misunderstandings of actual models;
attractive diagrams alone are not evidence of comprehensibility.

### S3 — Compare bounded engineering tasks under controlled conditions

First choose the effect to estimate. Do not combine these contrasts into one
"methodology improvement" claim:

| Contrast                 | Treatment and comparator                                                                                                                            | What can be inferred                                                                             |
| ------------------------ | --------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------ |
| Integrated method effect | Full MODRISS versus a competent conventional serverless workflow with IaC, tests, CI/CD, review and trace documentation                             | Effect of the complete package for assigned tasks; cannot isolate process from tooling           |
| Added process guidance   | Same MODRISS framework in both conditions; full tailored lifecycle/evidence guidance versus minimal instructions necessary to operate the framework | Incremental effect of the specified guidance bundle, if access/training are otherwise comparable |
| Framework effect         | Common delivery/assurance objectives; MODRISS modeling chain versus competent code/IaC tools                                                        | Effect of the framework/tool bundle, not of a particular metamodel independently                 |

**Recommended first experiment:** added process guidance for a bounded change
episode, because the missing evidence concerns usable handoffs and coordination.
Both conditions retain all technical safety controls and enough tool instructions
to complete the work. Define exactly which planning, ownership, trace-review and
handoff supports differ. Do not remove essential safeguards from the comparator
to manufacture failures. If this separation makes either treatment incoherent,
use full-package versus conventional workflow and explicitly relinquish the
component-attribution claim.

Use independently prepared tasks: interpret a model and locate affected
requirements; implement an additive event-contract change; resolve an injected
source/target conflict; and qualify an operational fix with stale evidence or
an unknown external outcome. Tasks must have externally reviewed correct
behavior and acceptance criteria, including allowed alternative solutions.

For collaborative handoff tasks, randomize **teams**, not individuals within a
team. Prefer a blocked parallel design, balancing relevant prior MDE and
serverless experience. Multiple task observations within a team are repeated
measurements, not new independent participants. A crossover is acceptable for
isolated comprehension tasks with balanced task forms, but exposure to the
process cannot reliably be washed out; avoid crossover as the default for
whole-process training effects.

Training must reach a predefined competency check in both conditions. Record
training time and failures rather than hiding them. Use a separate practice
task, equivalent documentation access and a scripted support policy. Log every
researcher intervention, its duration and whether it supplied a solution.
Assessors should grade anonymized outputs and tests where treatment identity
can reasonably be concealed; acknowledge incomplete blinding of model notation.

Primary outcome: proportion of assigned change episodes that satisfy all
predeclared required behavioral and handoff criteria by the fixed deadline.
Co-report serious defects separately. Secondary outcomes: active person-time,
time to acceptable completion, missed trace/impact links, interventions and
perceived effort. Report time with failures/timeouts retained; analyzing only
successful participants would favor a treatment that fails difficult tasks.

Recruitment follows a design-specific power/precision analysis. Run a small
instrument pilot first; select the smallest practically important effect with
practitioners before seeing comparative results. Simulate the planned analysis
across plausible baseline success, team correlation, task variability and
attrition. Report the assumptions and sensitivity, not just a software-generated
sample number. A convenient sample of 20–30 students or counting every task as
independent does not establish adequate power. If the justified independent
team count is infeasible, report an exploratory study and interval uncertainty;
do not promise significance or recruit until a preferred p-value appears [R3].

Practitioner and student results should be described separately. Student-only
results may support learnability in that population, not claims about company
adoption. A well-executed null or adverse result remains reportable.

### S4 — Observe the whole methodology in contrasting practitioner cases

Use the existing case protocol, supplemented below. Case-study design should
preserve context, a chain of evidence and explicit rival explanations [R4,R6].

| Case                                  | Selection rationale                                                                                                                                             | Minimum observation for this plan                                                                                 |
| ------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------- |
| A: business transaction workflow      | Small cross-functional team; API/events/state/external side effect; ReturnFlow may supply a pilot task, but a real organizational problem is preferable         | Tailoring and training, initial delivery, a second increment/release, operating window and a consequential change |
| B: contrasting workload/organization  | Independently led team, preferably another organization; telemetry/late-event workload, integration-heavy system, stricter assurance or multi-team dependencies | Same lifecycle evidence, plus observations specific to the contrasting situational factors                        |
| Optional C: transfer or boundary case | New domain/team or an actual second provider implementation                                                                                                     | Test a claim not already exercised by A/B; report incompatibilities and extension costs                           |

Two systems built by the author are not two independent practitioner cases.
Prefer two independently led teams, ideally across organizations. If industrial
access is limited to one organization, report that dependence and strengthen
independent outcome assessment. If all use occurs in workshops or a teaching
environment, label the evaluation accordingly and narrow adoption claims.

Plan approximately **8–12 weeks of observation per case**, with at least two
increments, two release decisions and a subsequent change; these are scheduling
assumptions, not validated sufficiency rules. Extend observation if no meaningful
change or operational learning occurs. Where natural events are insufficient,
run labeled drills alongside ordinary use; injected incidents must not be
reported as natural production incidents. A tabletop retirement exercise
supports only retirement reasoning, not a claim of observed decommissioning.

Capture the initial profile, why each conditional fragment is selected or
omitted, actual role combinations, task occurrences, artifacts and all deviations.
Distinguish useful adaptation, misunderstanding, method deficiency, tool defect,
organizational constraint and researcher intervention. Track support throughout;
schedule an unassisted increment after initial onboarding where feasible.

Assess especially the three operations dispositions: genuinely operations-only
work, bounded model/product change, and planned-release work. Observe how
emergency restoration is reconciled, who owns service response before G7, and
whether retained records have accepted custody at G8. Include at least one
rejected or deferred decision; an all-green story is not a sufficient stress
test of control logic.

The best available comparator is a contemporaneous, reasonably matched team or
workstream using the organization's established practice. Match domain/risk,
task size, experience, workload and assurance expectations. Historical releases
can give context but differ in staff, tools and task complexity; report these
differences. Do not present historical before/after improvement as a causal
effect of MODRISS. Difference-in-differences requires defensible pre-trends and
comparable exposure; two short case timelines do not automatically supply them.

Analyze cases individually before comparing patterns. Reconstruct episodes
from requests through modeling, implementation, review, release and operational
outcome. Use interviews to explain mechanisms, and reconcile statements with
artifact and observation evidence. Record where the method causes delay or
unnecessary work. A successful delivery with persistent author intervention is
qualified feasibility, not independent adoption.

### S5 — Independent feature reassessment and reproduction

Use Eidi and Ramsin's original criterion definitions [R7], preserving the two
Simple Yes/No criteria and the categorical meanings of Definition Type,
Methodology Type and Application Scope. The local inventory has 78 rows:
20 general, 24 MDD and 34 serverless, with tracing/logging separated as in the
paper's results. Preserve that mapping and cite the source table/page.

Have at least two independent assessors score the frozen evidence package
before discussion. Keep columns for specified support, implemented support,
observed use and outcome evidence. "Not observed" must not silently become
either full support or no support. Publish original ratings, rationale,
disagreement and adjudication. For ordinal A/B/C items report raw agreement
and a justified ordinal agreement statistic where meaningful; use nominal
handling for Yes/No/categories, and do not mix all types into a single kappa.

Do not weight criteria after seeing MODRISS's strengths or compute a global
"maturity percentage" that hides absent critical capabilities. If a contextual
decision model needs weights, elicit them from stakeholders beforehand and
report sensitivity. Rerate comparators under the same current protocol when
making feature comparisons; do not treat older publication scores as current
experimental measurements. Account for shared authorship/theoretical lineage
between the framework and methodology as a potential source of criterion bias.

An evaluator outside the implementation team should install the frozen package,
execute a declared technical subset and reproduce at least one deployed
vertical slice and one change. Record setup effort, failures, assistance,
environment differences and result mismatches. Independent artifact execution
supports reproducibility; independent repetition of human studies is a stronger,
different claim. Do not use those terms interchangeably.

## 6. Measurement dictionary

Every measure needs a unit, sampling window, collection method, missing-data
rule and owner in the registered instrument. Suggested operational definitions:

| Measure                          | Definition / denominator                                                                                               | Interpretation safeguard                                                                                          |
| -------------------------------- | ---------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------- |
| Episode success                  | Episodes meeting every mandatory predeclared acceptance condition by deadline / all assigned eligible episodes         | Retain failures/timeouts; list justified exclusions without changing the denominator opportunistically            |
| Active engineering effort        | Sum of person-minutes for discovery, models, refinement, manual code, tests, review, repair and evidence handling      | Count parallel workers separately; distinguish initial learning from repeated-use effort                          |
| Lead and review time             | Wall-clock request→acceptance and review-ready→decision durations                                                      | Specify calendar/business time and external waiting; do not equate with active effort                             |
| Handoff friction                 | Blocked/reopened handoffs, reason and delay per observed handoff opportunity                                           | More honest detection may initially increase findings; interpret alongside escaped defects and resolution         |
| Artifact correctness             | Independent acceptance checks passed plus count/severity of unresolved defects                                         | A structural validator or generator's own tests are insufficient as the sole oracle                               |
| Trace completeness/correctness   | Required trace links present / required links; separately, inspected correct links / inspected links                   | Trace count alone rewards meaningless links; publish the sampling frame if auditing a subset                      |
| Change integrity                 | Unintended lost refinements, missed affected artifacts and residual live/source divergences per controlled change      | Distinguish legitimate manual extension from unmanaged drift                                                      |
| Semantic diagnostic quality      | Correctly flagged labeled violations / all flagged; detected labeled violations / all actual violations                | Declare violation/model granularity and multiple-diagnostic matching; report ambiguous labels                     |
| Automation and manual completion | Generated artifact categories; manual implementation/review/repair effort; reproducibility failures                    | Do not substitute generated lines of code or "zero manual warnings" for useful automation                         |
| Tailoring cost and fit           | Time/effort to configure; justified selections/omissions; later revisions; uncovered control objectives                | Fewer tasks are not automatically better; necessary omitted controls are adverse outcomes                         |
| Operational quality              | Requirement-specific completion/latency/error/recovery measures under declared load and failure conditions             | Keep cloud/emulator, natural/drill and cold/warm evidence separate                                                |
| Economics                        | Engineering/learning/maintenance effort plus cloud/service/tool charges; cloud cost per completed business transaction | Use dated rates and actual bills where available; include retries/logging and repeated-use break-even assumptions |
| Learnability and independence    | Training time, competency attainment, first independent success and support interventions                              | Do not remove participants who struggle from the effectiveness analysis                                           |
| Perceived usefulness/ease        | Predefined items plus examples and negative comments after actual use                                                  | Perception complements measured behavior; it is not productivity evidence                                         |

Use the Method Evaluation Model as a rationale for separating measured
effectiveness/effort from perceived usefulness/ease and adoption intention [R8].
If using an existing questionnaire, cite its original instrument, retain its
scoring rules and report adaptations. Do not label a home-built satisfaction
survey as validated SUS/TAM, or average distinct constructs into one score.
Actual continued use after the study is stronger adoption evidence than stated
intention; collect a feasible follow-up if organizational access permits it.

## 7. Analysis and inference rules

Predeclare one primary outcome per confirmatory study and identify secondary
and exploratory analyses. Report effect estimates with uncertainty and the
practical threshold chosen before results. Do not select a statistical test
only after trying alternatives until significance appears.

For S3, analyze at the randomization unit. A team-level randomized design may
use a prespecified team-level/randomization analysis; repeated-task models must
account for team and task dependence and require enough independent clusters.
Use design simulation to choose the model before data collection. Report binary
success effects in interpretable units such as absolute percentage-point
differences. Describe skewed time/effort distributions; do not drop timeouts or
assign them an invented exact completion time. Use a predefined censored-time
or bounded-time outcome as appropriate. Treat missingness, attrition and tool
outages explicitly, including sensitivity analyses. A small study with wide
intervals is inconclusive, not evidence of equivalence.

For S4, use within-case timelines, pattern matching and cross-case explanation.
Rivals include participant experience, better infrastructure, extra review
attention, smaller scope and author intervention. With two cases, focus on
analytic transfer to similar situations, not population estimates. Many model
elements, events or gate records do not increase the number of independent
organizational cases.

For interviews, publish the guide and coding approach. Use an auditable codebook
for structured categories and retain new themes/counterexamples; another
researcher reviews a declared subset and disagreements. Do not claim saturation
simply because a planned small panel finished. Triangulate qualitative accounts
with work records and preserve contradictions instead of resolving them by
majority vote.

Use a claim-level synthesis table at the end of the study: evidence supporting
the claim, adverse evidence, rival explanations, residual uncertainty and
allowable wording. Do not pool technical-test percentages, expert agreement,
participant ratings and project outcomes into a single methodology score.

## 8. Validity, ethics and change control

| Threat                       | Planned control and remaining limit                                                                                                                         |
| ---------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Author/allegiance bias       | External reviewers and graders; preregistered claims; documented assistance; retain negative cases. Complete blinding of method identity may be impossible. |
| Circular test oracles        | Independently authored requirements/assertions and held-out examples; test generator output against behavior, not only matching templates.                  |
| Weak comparator              | Practitioners review baseline competence and training; equivalent assurance goals and task scope. Report tool/version advantages as part of treatment.      |
| Learning and contamination   | Separate training tasks; blocked parallel teams for process intervention; do not move trained participants into an allegedly untreated condition.           |
| Selection/external validity  | Publish eligibility, recruitment and nonparticipation; contrast contexts and experience. Convenience/student samples remain bounded.                        |
| Pseudoreplication            | Distinguish people, teams, cases, tasks, models and workload windows; analyze at the appropriate level.                                                     |
| Researcher-created incidents | Label drills separately; do not count an injected failure as naturally observed organizational need.                                                        |
| Instrumentation/tool drift   | Freeze releases and collect versioned raw data; record amendments and break analyses into version cohorts.                                                  |
| Novelty/observation effect   | Repeated increments, independent work and follow-up; favorable attention cannot be fully eliminated.                                                        |
| Criterion-selection bias     | Source-faithful Eidi mapping, independent domain concerns and predeclared contextual weights if needed.                                                     |

Obtain the applicable institutional ethics determination before recruiting
people. Use informed consent, voluntary participation, withdrawal rules and
noncoercive compensation. Separate students' assessment and employees' job
evaluation from participation. Minimize collected personal/customer data;
record access, retention, anonymization and publication permissions. Production
fault injection requires organizational authorization; otherwise use staging
or a drill. Record cloud budget ownership and cleanup/retention responsibilities.

Publish what can be shared and explain restricted access precisely. Restricted
industrial data may require a redacted dataset, controlled access and a public
synthetic reproduction package; the synthetic package is not replacement
evidence for unavailable observations. State any generative-AI assistance in
materials, coding or writing according to the selected venue's current policy.
An AI-generated participant or interview is never research data.

During formative work, revise the method freely but retain the defect→decision→
fragment→implementation→recheck trail. During summative work, freeze the method.
Emergency fixes require a versioned amendment; affected tasks are not silently
rerun and substituted for failures. Analyze original and corrected versions
separately or justify their compatibility. Preserve the original protocol and
all exclusions.

## 9. Reproducibility and evidence package

Create the following study structure when execution starts; these paths are
planned deliverables, not an assertion that data already exists:

```text
evaluation/journal/
  protocol/          registered protocol, amendments, claims, sampling/analysis plan
  release/           method/tool versions, hashes, environment and supported scope
  instruments/       task briefs, training, questionnaires, interviews, rubrics
  technical/         models, independent oracles, mutations, traces, build/cloud reports
  cases/             anonymized profiles, task/gate occurrences, changes, observations
  raw/               immutable exports, intervention logs, measurements and metadata
  derived/           documented transformations, coded data, exclusions and summaries
  analysis/          executable scripts, dependencies, seeds and figure generation
  replication/       independent evaluator report, discrepancies and support record
  publication/       claim-evidence matrix, figures, data statement and artifact guide
```

Each task/gate occurrence should include: study/case/team ID; method/profile
version; task occurrence and scope; relevant input/output revisions and hashes;
owner/reviewer; started/blocked/review-ready/decided timestamps; evidence kind;
result; blockers/exceptions and expiry; intervention; authoritative change source;
and follow-up. Keep raw logs separate from interpreted assessments and record
time-zone/clock conventions. Do not manufacture occurrence records by iterating
through TaskDefinitions.

Freeze an artifact release and archive it with a persistent identifier before
submission. Supply a clean-environment quick reproduction path and a longer
cloud procedure with cost/duration expectations and explicit credentials/setup
requirements. Separate public replayable analysis from restricted raw data.
Check licenses and availability of models, examples and third-party tools.
Provide expected outputs and a failure-troubleshooting guide. Independent
reproduction should use this package rather than undocumented author knowledge.

## 10. Effort, sequence and decision gates

The following is a planning envelope, assuming a reasonably stable prototype,
one primary researcher, part-time independent assessors/analysis support and
access to two practitioner teams. It is not an estimate of effort already spent
or a promise of completion. Recruitment and organizational approvals may dominate.

| Window      | Work                                                                                               | Exit decision                                                                          |
| ----------- | -------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------- |
| Weeks 1–3   | S0 claims, related work, organization access, ethics submission and recruitment; draft instruments | Protocol and feasible access plan; choose which claims can actually be studied         |
| Weeks 3–8   | S1 executable vertical slice and corpus; formative S2; instrument pilot                            | Critical technical blockers resolved; updated release and frozen summative instruments |
| Weeks 8–10  | Training and pilot analysis; S3 power/precision decision; final S2 assessment                      | Confirmatory experiment justified or explicitly exploratory; cases ready               |
| Weeks 10–22 | S4 cases; S3 in a separate participant cohort where feasible; ongoing data-quality review          | Two observed delivery/change cycles with preserved deviations, or narrowed claims      |
| Weeks 22–26 | S5 reproduction and independent rating; within-/cross-case analysis                                | Evidence package reproducible; unresolved findings reflected in conclusions            |
| Weeks 26–30 | Manuscript, claim audit and venue-specific checks                                                  | Submission decision based on contribution/evidence fit, not positive-result count      |

This is approximately a **6–8 month program after access and a stable technical
baseline**, with substantial overlap possible. If executable case artifacts or
recruitment are not ready, extend the schedule rather than compressing away
operational observation.

Prioritize effort in this order: independent acceptance oracles and one complete
slice; reliable data collection; external review; real repeated case use;
independent reproduction; then additional contexts, providers or experiments.
Do not spend the core evaluation budget on more diagrams, more synthetic
checklists or a large survey of people who have not used the method.

## 11. Evidence-based submission readiness

These are proposed project decisions, not universal journal acceptance rules.

| Readiness question                                 | Evidence needed                                                                             | If absent                                                                      |
| -------------------------------------------------- | ------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------ |
| Is the novelty identifiable beyond implementation? | Current related-work comparison and explicit new mechanisms/design knowledge                | Refine contribution or choose a narrower artifact/tool paper                   |
| Is the implemented scope trustworthy?              | Independent oracles, executable chain, declared gaps and reproducible failures/fixes        | Stabilize or reduce scope; do not infer correctness from successful generation |
| Can nonauthors use the process?                    | Independent task/case use, intervention records and actual outputs                          | Report author-led demonstration; omit adoption/effectiveness claims            |
| Has development-through-change been observed?      | Initial delivery plus later release/change and operational evidence in contrasting cases    | Claim only the lifecycle stages actually observed                              |
| Are benefit and burden both visible?               | Correctness, full effort, learning, review delays and negative outcomes                     | Avoid productivity/usefulness superiority claims                               |
| Is attribution defensible?                         | Appropriate comparator/randomization or explicit noncausal interpretation                   | Use association/feasibility wording; separate process/framework attribution    |
| Can evidence be audited?                           | Versioned raw/derived data, analysis, privacy/access statement and independent reproduction | Repair the package or disclose concrete limits                                 |

Minimum defensible whole-methodology wording after successful core studies:
"MODRISS was feasible in the observed contexts, with these verified capabilities,
documented costs, benefits and boundary conditions." Stronger comparative or
causal wording requires the corresponding design and uncertainty evidence.
No provider-portability, enterprise-scale or general bidirectional-engineering
claim follows merely from an abstraction hierarchy or a large metamodel.

## 12. Journal positioning and manuscript structure

Select by scope first, then verify the required quartile in the applicable
ranking edition. Software and Systems Modeling is a scope candidate when the
central contribution is modeling languages/methods and their rigorous
application; its stated scope includes modeling techniques and processes [R9].
Information and Software Technology is a scope candidate when the emphasis is
improving development practice through the evaluated method; the publisher
describes that focus [R10]. Neither mention is a claim of current Q1 status.

Use a coherent primary paper rather than compressing the process, every
metamodel classifier, the entire platform and the LLM assistant into one set
of unsupported claims. Put full metamodels, detailed rules and study instruments
in the artifact/supplement. Explain overlap with the existing Eidi publication:
the earlier review/criteria are antecedents; the engineered method and new
empirical evidence are distinct contributions. Cite reused material, explain
the delta and follow the selected journal's current prior-publication policy.

Recommended manuscript argument:

1. Important serverless engineering problem and limitations of current methods.
2. Method requirements, situational factors and traceable construction decisions.
3. Process plus modeling framework, their integration and bounded implementation.
4. Evaluation questions, settings, independent units, comparators and protocol.
5. Technical results, practitioner results and observed tradeoffs.
6. Cross-study explanation of which mechanisms helped, failed or imposed cost.
7. Threats, negative cases, reproducibility and supported generalization.
8. Bounded conclusions and specific future improvements.

Candidate figures are a claim/evidence map, one traceable cross-level example,
case timelines with actual rework/handoffs, effort/quality distributions and a
change-integrity example. Use measured data only after execution. An all-green
criteria heatmap would obscure the central research questions.

Check the exact venue's current instructions at submission. For example,
SoSyM currently requires a Data Availability Statement for original research
and specifies how restricted data should be described [R11]. This plan does
not claim every journal has identical artifact, data or review requirements.

## 13. Immediate execution backlog

| Priority | Action                                                                                       | Responsible role                                         | Deliverable                                                                     |
| -------- | -------------------------------------------------------------------------------------------- | -------------------------------------------------------- | ------------------------------------------------------------------------------- |
| 1        | Agree the primary contribution, bounded claims and intended venue family with the supervisor | Principal researcher + supervisor                        | One-page claim/novelty contract                                                 |
| 2        | Secure two independently led case teams and domain access; seek ethics determination         | Study coordinator                                        | Access agreements and recruitment/consent plan                                  |
| 3        | Turn ReturnFlow into an executable vertical slice with independent acceptance/fault oracles  | Framework engineer + independent domain/quality reviewer | Versioned CIM/PIM/PSM, generated/completed application and reproducible reports |
| 4        | Freeze baseline/profile and build occurrence/evidence instrumentation                        | Method engineer + study coordinator                      | Evaluation release and collection manual                                        |
| 5        | Run external formative review and instrument pilot                                           | Independent experts + coordinator                        | Defect/rationale log, revised instruments and analysis assumptions              |
| 6        | Register the summative protocol; choose justified S3 scope/sample or exploratory status      | Researcher + methods/statistical reviewer                | Timestamped protocol and analysis plan                                          |
| 7        | Execute cases/experiment while preserving failures and assistance                            | Participant teams + observers                            | Raw, versioned evidence                                                         |
| 8        | Reassess, reproduce, synthesize and audit every manuscript claim                             | Independent assessors + researcher                       | Submission evidence package                                                     |

## References and source use

Online guidance was consulted on 2026-09-27. Study thresholds and sample sizes
above are proposed planning choices, not numbers prescribed by the references.

- **[R1]** Ralyté, J., Koutsopoulos, G., and Stirna, J. _Verification, validation, and evaluation of modeling methods: experiences and recommendations_. Software and Systems Modeling 25, 1305–1320 (2026; online 2025). [Publisher article](https://link.springer.com/article/10.1007/s10270-025-01304-2). Basis for distinguishing correctness, contextual relevance and practical value.
- **[R2]** Venable, J., Pries-Heje, J., and Baskerville, R. (2016). _FEDS: a Framework for Evaluation in Design Science Research_. European Journal of Information Systems 25(1), 77–89. [DOI](https://doi.org/10.1057/ejis.2014.36). Basis for selecting a sequence of formative/summative and artificial/naturalistic evaluation episodes.
- **[R3]** ACM SIGSOFT. _Empirical Standards: Experiments (with Human Participants)_. [Maintained standard](https://github.com/acmsigsoft/EmpiricalStandards/blob/master/docs/standards/Experiments.md). Protocol, independent units, sample justification and inference/reporting checks. Pin a revision when registering the study.
- **[R4]** ACM SIGSOFT. _Empirical Standards: Case Study_. [Maintained standard](https://github.com/acmsigsoft/EmpiricalStandards/blob/master/docs/standards/CaseStudy.md). Contextual case-study design/reporting checks.
- **[R5]** ACM SIGSOFT. _Empirical Standards: Engineering Research_. [Maintained standard](https://github.com/acmsigsoft/EmpiricalStandards/blob/master/docs/standards/EngineeringResearch.md). Technical artifact evaluation and reporting checks.
- **[R6]** Runeson, P., and Höst, M. (2009). _Guidelines for conducting and reporting case study research in software engineering_. Empirical Software Engineering 14, 131–164. [Publisher article](https://link.springer.com/article/10.1007/s10664-008-9102-8). Context, case protocol, triangulation and chain-of-evidence rationale.
- **[R7]** Eidi, M., and Ramsin, R. (2026). _Model-Driven Approaches for Serverless Software Development: Evaluation and Future Directions_. MODELSWARD, 560–567. [DOI](https://doi.org/10.5220/0014634200004058). Original criterion definitions/types: Tables 1–3, pp.563–564; result presentation: Tables 4–6, pp.565–566. Local source: `.idea/process_papers/Eidi.pdf`.
- **[R8]** Moody, D. L. (2003). _The Method Evaluation Model: A Theoretical Model for Validating Information Systems Design Methods_. ECIS 2003 Proceedings, paper 79. [Author publication record](https://aisel.aisnet.org/ecis2003/79/). Rationale for evaluating actual performance and adoption-related perceptions separately.
- **[R9]** Springer Nature. _Software and Systems Modeling: Aims and scope_. [Official scope](https://link.springer.com/journal/10270/aims-and-scope). Venue-fit guidance, not ranking evidence.
- **[R10]** Elsevier. _Software general journals_, Information and Software Technology entry. [Official publisher catalog](https://shop.elsevier.com/journals/subjects/physical-sciences-and-engineering/computer-science/software/software-general). Venue-fit guidance; consult the exact author instructions at submission.
- **[R11]** Springer Nature. _Software and Systems Modeling: Submission guidelines_, Research Data Policy and Data Availability Statements. [Official instructions](https://link.springer.com/journal/10270/submission-guidelines). Example of a current venue-specific requirement, not a universal Q1 rule.

The method-engineering rationale and original source corpus remain in
[01-research-synthesis.md](../mde/process/method/01-research-synthesis.md) and
[03-method-construction.md](../mde/process/method/03-method-construction.md).
This evaluation program tests that rationale and its consequences; it does
not treat citations as evidence that MODRISS already works.
