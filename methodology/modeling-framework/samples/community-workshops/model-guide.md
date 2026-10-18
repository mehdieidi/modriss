# CIM guide and traceability

## Business model

The CIM uses the repository's fixed `https://modriss.org/cim/1.0` language and its organization, domain-data, behavior, process-policy, governance, transformation, and shared kernel packages. It has one `CIMModel` root. References use stable, descriptive `id` values; no external model or application database is needed to resolve them.

The model contains 21 requirements with acceptance criteria, two business goals and KPIs, one fictional stakeholder, four actors, two roles, two capabilities and contexts, three entities, two aggregate candidates, ten commands with detailed outcomes, four queries, fourteen business facts, nine policies, one ordered decision table, and seven processes. The requirements include specialized privacy and security constraints and measurable quality scenarios. Risks and unaccepted assumptions record the evaluation boundary. Every element has a business name, explanation, and source reference. All information is classified, including structured audit fields. Nullable Boolean decisions are explicit for compatibility with the current ETL rules.

This is an authored reference baseline. `lifecycleStatus=DRAFT` and unaccepted assumptions deliberately avoid claiming stakeholder approval or production readiness. Semantic completeness is relative to the stated scenario and metamodel, not a certification of a future application.

## Responsibilities and consistency

| Capability / context                                          | Owns                      | Business boundary                                                                                                                                                                                                        |
| ------------------------------------------------------------- | ------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| Workshop booking and attendance / Workshop bookings           | Workshop and Registration | One workshop and its registrations form `agg-booking`. Allocation, participant cancellation with promotion, attendance, closure, and workshop cancellation preserve its invariants. Different workshops are independent. |
| Essential participant communication / Essential communication | Notice                    | Each notice is `agg-notice`. Intent deduplication and delivery attempts are consistent per notice; notice work never changes seat allocation.                                                                            |

Capabilities express responsibilities and need not become separate deployed services. In particular, splitting workshop capacity and registrations into unrelated transactions would violate R05/R15. Personal-data cleanup coordinates both contexts within its allowed 24-hour window; it does not introduce a global seat-allocation transaction.

## Record lifecycles

| Record       | Allowed changes                                                                                                                                                                         | Interpretation                                                                                                                                                                                                                             |
| ------------ | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| Workshop     | Publication → PUBLISHED; PUBLISHED → CANCELLED before start; PUBLISHED → COMPLETED at/after end                                                                                         | Published details never change. Cancelled and completed are terminal states. Capacity is fixed. Initialize anonymous totals to zero; completion freezes final totals.                                                                      |
| Registration | Conceptual REQUESTED → CONFIRMED or WAITLISTED; WAITLISTED → CONFIRMED by promotion; CONFIRMED/WAITLISTED → CANCELLED; CONFIRMED → ATTENDED or NO_SHOW; WAITLISTED → EXPIRED at closure | REQUESTED is a conceptual entry state, never an acknowledged or stored partial booking. Rejection leaves no registration. ATTENDED, NO_SHOW, EXPIRED, and CANCELLED are terminal. Rebooking after cancellation creates a new registration. |
| Notice       | PENDING → ACCEPTED, RETRY_WAIT, or SUPPRESSED; RETRY_WAIT → RETRY_WAIT, ACCEPTED, FAILED, or SUPPRESSED                                                                                 | First/second failure waits; third failure is FAILED. Provider acceptance is ACCEPTED. Terminal records are not retried. Suppression makes no provider call.                                                                                |

Retention removal is deletion of identifying records rather than an additional participant status. A non-identifying workshop cleanup marker prevents late processing from recreating notices. At or after the retention deadline, no new personal notice may be prepared or sent. Retained workshop totals have counts only.

## Time and fairness

All comparisons use authoritative instants, rather than browser clocks or ambiguous local times. Display times include the centre's timezone. The reference fixture uses UTC to make expected values easy to compare.

| Operation                                         | Eligible interval or deadline                                                                                                                            |
| ------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Publish                                           | `start >= publication instant + 26 hours`; `end > start`                                                                                                 |
| Request seat; cancel active registration; promote | Workshop PUBLISHED and `now < start - 2 hours`                                                                                                           |
| Request reminder                                  | `[start - 24 hours, start - 2 hours)` and registration currently CONFIRMED and not previously requested                                                  |
| Organizer cancels workshop                        | PUBLISHED and `now < start`                                                                                                                              |
| Record new attendance                             | CONFIRMED and `start <= now < end`                                                                                                                       |
| Close workshop                                    | PUBLISHED and `now >= end`; finish within 60 seconds                                                                                                     |
| Delivery retries                                  | Attempt 2: at least 1 minute after failed attempt 1; attempt 3: at least 5 minutes after failed attempt 2. Each eligible retry starts within 60 seconds. |
| Personal cleanup                                  | Eligible at `end + 30 days`; finish by `end + 30 days + 24 hours`, including cancelled workshops                                                         |

Waiting priority is the committed join instant followed by lexical registration identifier. Cancel-and-promote is one indivisible operation, not a sequence that exposes an available seat. At the exact cutoff, a fresh active cancellation and fresh seat request both fail. Replaying an already completed cancellation remains a no-op, even after cutoff. Simultaneous changes must serialize into an order satisfying the same rules. If workshop cancellation wins a race with participant cancellation, no promotion may survive in the final cancelled workshop.

## Notice relevance and failure behavior

The notice intent key is `(business fact identifier, registration identifier, notice kind)`. Workshop cancellation snapshots the identifiers of registrations active immediately before cancellation, so preparation can notify precisely those participants. Each notice has a stable identifier; each attempt also has an ordinal. The provider deduplication/reconciliation key is always the notice identifier, including after a timeout that may have followed provider acceptance.

| Notice kind               | Relevant before sending                                                                                                                      |
| ------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------- |
| CONFIRMATION or PROMOTION | Workshop PUBLISHED, registration CONFIRMED, and now before start                                                                             |
| WAITING                   | Workshop PUBLISHED, registration WAITLISTED, and now before cutoff                                                                           |
| REMINDER                  | Workshop PUBLISHED, registration CONFIRMED, and now before start; its intent must have been requested during the reminder window             |
| CANCELLATION              | That registration is CANCELLED and the workshop is not CANCELLED; rebooking creates another registration and does not undo this cancellation |
| WORKSHOP_CANCELLATION     | Workshop CANCELLED and registration is in its formerly-active cancellation snapshot                                                          |

Every kind is also subject to retention. Workshop cancellation supersedes unsent individual cancellation notices as well as booking/reminder notices. A notice that becomes obsolete during a retry delay is SUPPRESSED. A relevant failed attempt is recorded even though its command successfully processed the failure; it does not roll back a booking. After three failures the notice is FAILED and visible to organizers. The test provider can distinguish acceptance from rejection and emulate timeout after acceptance. Real-provider deduplication capability must be confirmed during refinement; accepted email does not establish inbox receipt.

The model's optional external-interaction process step observes the response from the dispatch command's single provider call. It is skipped for local suppression and must not be refined into a second send.

## Requirement-to-model map

The XMI contains the full requirement statements, fit criteria, and given/when/then acceptance criteria. Each requirement's `constrains` references and each capability's `realizesRequirements` provide local traceability. Transformation reports and generated trace links supply the subsequent cross-level evidence.

| Requirements     | Main CIM elements                                                             | Later implementation concern / observable evidence                                                  |
| ---------------- | ----------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------- |
| R01–R02          | `cmd-publish`, `qry-browse`, `ent-workshop`                                   | Validated publication and public catalogue without personal fields                                  |
| R03–R05, R15–R16 | `cmd-request`, `cmd-cancel`, `agg-booking`, `dt-allocation`, `pol-promotion`  | Atomic conditional writes, unique active membership, deterministic FIFO, replay protection          |
| R06              | `cmd-cancel-workshop`, `pol-cancel-workshop`, `evt-workshop-cancelled`        | Consistent workshop-wide change, formerly-active recipient snapshot, obsolete notice suppression    |
| R07–R08          | `qry-mine`, `qry-roster`, `qry-failures`                                      | Identity filtering, authoritative reads, pagination, restricted delivery-failure view               |
| R09–R11          | `cmd-attend`, `cmd-close`, `cmd-remind`, `proc-lifecycle`                     | Attendance window, durable due work, no-shows, expired waiting entries, reminder deduplication      |
| R12–R13, R19     | `cmd-prepare`, `cmd-dispatch`, `agg-notice`, `proc-notices`                   | Durable communication obligation, relevance checks, bounded retries, restart recovery, provider key |
| R14              | PrivacyConstraint `R14`, data classifications, `cmd-purge`, `pol-retention`   | Minimized data, complete retention inventory, protection and erasure across stores and history      |
| R17              | SecurityConstraint `R17`, actors/roles, per-command/query authorization rules | Trusted identities and business access enforcement                                                  |
| R18              | Performance quality scenario `R18-scenario`                                   | Stated load, cold starts, latency measurements, error reporting                                     |
| R20              | Auditability quality scenario, `info-audit`, `qry-roster`                     | Append state-change history, deny participant access, erase identifying history at retention        |
| R21              | Usability quality scenario, explicit statuses and glossary                    | Pilot comprehension, timezone/cutoff presentation, five-minute task observation                     |

## Seven processes

| Process ID             | Purpose and exceptional outcomes                                                                                                                  |
| ---------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------- |
| `proc-publish`         | Publish or reject invalid/unauthorized publication without mutation                                                                               |
| `proc-request`         | Guard and atomically allocate, then expose the confirmed/waiting/rejected result; the later decision reads the result and does not allocate again |
| `proc-cancel`          | Cancel and atomically promote if appropriate; reject wrong owner, invalid state, or closed window                                                 |
| `proc-cancel-workshop` | Cancel all active registrations without promotion; obsolete notices are suppressed                                                                |
| `proc-lifecycle`       | Publication through reminder window, optional attendance, closure, and settled anonymous totals; organizer cancellation interrupts the timeline   |
| `proc-notices`         | Durable intent, eligible dispatch, optional provider-response observation, bounded retry, suppression, or visible exhaustion                      |
| `proc-purge`           | Remove expired identifying records and preserve anonymous results; repeat is harmless                                                             |

## Refinement work expected in an experiment

The CIM intentionally expresses rules and temporal conditions in business language. During PIM refinement, reviewers must decide persistence boundaries and atomicity, contracts and concrete response shapes, identity propagation, durable fact/notice processing, scheduler bindings and time calculations, retry state, authorized query filtering, and the full retention inventory. Natural-language expressions and relative deadlines must become suitable runtime expressions and schedules. Internal and policy-triggered commands must not become public unauthenticated operations merely because a draft transformation creates a route.

The generated draft may contain readiness findings and manual decisions. Preserve and resolve those findings; a zero-warning CIM does not imply a zero-warning generated PIM. Then review the AWS mapping, generate artifacts, implement the specified business rules in supported extension regions, configure the delivery test double and test clock, build, and execute the acceptance scenarios. Trace the resulting tests back to R01–R21. None of that business implementation or cloud deployment is claimed by this CIM delivery.
