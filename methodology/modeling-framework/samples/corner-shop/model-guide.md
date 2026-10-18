# CIM guide and traceability

## Model contents and boundaries

The reference uses the existing MODRISS CIM packages and a single `CIMModel` root. All references resolve within the file. It contains 23 requirements and acceptance criteria, two goals/KPIs, five actors, two human roles, three capabilities, two bounded contexts, five entities, two aggregate candidates, fourteen commands with detailed outcomes, four queries, seventeen facts, nine policies, two decision tables and eight processes. Classified information includes basket fields, payment references, notice content and structured audit fields. The XMI has 432 named, identified elements, including helper elements.

It is an authored evaluation baseline, not a record of a real stakeholder workshop or approval. `lifecycleStatus=DRAFT` and unaccepted evaluation assumptions preserve that distinction. A full zero-warning CIM validation establishes metamodel/rule conformance; it does not implement or test the application requirements.

| Responsibility                | Owns                       | Consistency intent                                                                             |
| ----------------------------- | -------------------------- | ---------------------------------------------------------------------------------------------- |
| Catalogue, stock and ordering | Product, Order, Order line | Checkout, closure, readiness and collection preserve inventory and order state                 |
| Payment and refund handling   | Payment record             | Verified payment transitions and their order/stock consequences use the same business boundary |
| Customer communication        | Order notice               | Intents/attempts are consistent per notice; delivery never reverses a sale                     |

The first two capabilities share the Shop ordering and payment context. `agg-sale` describes the atomic boundary for an order, its lines/payment state and affected product counters. Its Product member means affected records participate, not that every checkout locks the entire catalogue. `agg-notice` is separate. Capabilities need not become independently deployed services; the transformation profile deliberately does not prescribe one service per capability.

## Record states

| Record         | Allowed state progression                                                                                                                                                                                                                                                               |
| -------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Product        | Created ACTIVE with zero stock; staff may revise name/price or withdraw to WITHDRAWN. Withdrawal is permanent in this baseline. Existing order lines remain unchanged.                                                                                                                  |
| Order          | Checkout creates AWAITING_PAYMENT. Timely verified capture → PAID. Staff packing → READY. Verified pickup → COLLECTED. Eligible customer cancellation → CANCELLED; unpaid deadline → EXPIRED. CANCELLED, EXPIRED and COLLECTED never reopen.                                            |
| Order line     | Created HELD. Timely payment → COMMITTED. Eligible unpaid closure or paid cancellation → RELEASED. Collection → FULFILLED. RELEASED/FULFILLED never return to another stock state.                                                                                                      |
| Payment record | Created PENDING. Verified no-capture outcome → UNPAID. Timely known capture → CAPTURED. A captured payment on a closed order → REFUND_PENDING. Verified full refund → REFUNDED. UNPAID can become REFUND_PENDING if a capture is subsequently discovered; it must not reopen the order. |
| Notice         | PENDING or RETRY_WAIT → ACCEPTED, FAILED or SUPPRESSED; failed attempts within budget return to RETRY_WAIT. Terminal notice outcomes are not retried.                                                                                                                                   |

CAPTURED means money was verified, not that the order has been handed over. REFUND_PENDING means money is owed or a request was accepted, not that the refund completed. A retained exact replay returns the original action result; current status is available through the authorized order view.

## Stock accounting and money

For each line quantity `q`:

| Transition                         | Available stock change | Line change                                          |
| ---------------------------------- | ---------------------- | ---------------------------------------------------- |
| Successful checkout                | `-q`                   | Create HELD                                          |
| Matching timely capture            | `0`                    | HELD → COMMITTED                                     |
| Unpaid cancellation/expiry         | `+q` once              | HELD → RELEASED                                      |
| Paid cancellation before readiness | `+q` once              | COMMITTED → RELEASED; durable full-refund obligation |
| Ready for collection               | `0`                    | Remain COMMITTED                                     |
| Collection                         | `0`                    | COMMITTED → FULFILLED                                |
| Late capture after closure         | `0`                    | Remain RELEASED; durable full-refund obligation      |
| Staff replenishment                | `+quantity` once       | No existing order-line change                        |

Restoring a shelf physically after a cancellation is not another restock command: cancellation already returned those units to availability. Withdrawal blocks future sales but does not silently discard outstanding holds or paid orders; any stock returned to a withdrawn SKU remains unavailable for new checkouts because it is withdrawn.

Basket validation and deductions are all-or-nothing across the one-to-five affected products. A failed line cannot leave a partial hold. Price/name/revision snapshots on order lines are immutable. All arithmetic uses integer USD cents: `lineTotal = quantity × snapshottedUnitPrice`; `orderTotal = sum(lineTotal)`. Two 500-cent notebooks and one 200-cent pen total 1200 cents. All provider capture and full-refund amounts must equal that authoritative total. Public price edits affect only future orders.

## Exact timing and external facts

| Concern                           | Rule                                                                                                                                                                      |
| --------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Hold expiry                       | `expiresAt = checkoutCommitInstant + 15 minutes`                                                                                                                          |
| Open a new hosted session         | Owner, AWAITING_PAYMENT, `now < expiresAt`                                                                                                                                |
| Accept a first capture as payment | Verified association/amount/currency/reference, AWAITING_PAYMENT, `capturedAt <= processingNow`, and **both** `capturedAt < expiresAt` and `processingNow < expiresAt`    |
| Expire awaiting order             | `now >= expiresAt`; complete within 60 seconds                                                                                                                            |
| Duplicate already-applied capture | No-op before considering the deadline; a delayed duplicate cannot refund an otherwise valid paid/ready/collected order                                                    |
| Customer cancellation             | Awaiting before deadline → CANCELLED; awaiting at/after deadline → EXPIRED; PAID → CANCELLED and refund pending; READY/COLLECTED reject                                   |
| Reconciliation                    | Every 60 seconds while unresolved/pending and provider reachable                                                                                                          |
| Automatic refund submissions      | Initial attempt; retry no earlier than +1 minute after failure 1 and +5 minutes after failure 2; at most three automatic submissions; due retries start within 60 seconds |
| Email attempts                    | Initial attempt, then +1 minute and +5 minutes; at most three calls; relevance checked each time                                                                          |
| Retention                         | After terminal order and verified financial settlement: erase by `max(terminalAt + 30 days, settledAt) + 24 hours`                                                        |

The processing deadline deliberately prevents acceptance of a capture first learned after stock could have been released. Even if its provider capture time was just before expiry, a late first observation creates a refund obligation. The customer can see that the order expired and a refund is pending. Cancellation, expiry, capture and readiness must serialize against authoritative state, not stale read-then-write decisions.

The provider contract is deliberately small and explicit: one session/capture per order, stable session/refund keys, authenticated facts with occurrence times and lookup by those keys, and a final captured/unpaid outcome by expiry plus ten minutes. This is tested with a deterministic sandbox ledger. It is an assumption to verify before replacing the sandbox, not a claim about any named commercial provider.

Callback or reconciliation application validates credentials, association, amount, currency, fact identity and occurrence/reference consistency. Unknown orders are not created by provider facts. Unknown/erased order references are rejected without retaining the supplied private identifiers; only an anonymous rejection count remains. A return-page URL is not a payment fact. Mismatched or conflicting facts for existing retained orders are quarantined for staff review without changing inventory or monetary state. Stale unpaid results cannot downgrade a capture. A lookup that is unreachable or still PENDING produces no actionable fact to apply; the process waits for the next due lookup rather than treating it as a decline.

## Refunds, notices and durable work

A paid cancellation or first late capture commits the closed order, necessary stock release and one durable full-refund obligation together. The provider submission happens afterward. Its stable key is derived from the verified capture and is reused after ambiguous timeout. A successful submission is not sufficient for REFUNDED; completion must be verified by callback or ledger lookup. After automatic submission exhaustion the record stays REFUND_PENDING with `needsAttention=true`. Staff can retry the same obligation/key. Ledger reconciliation still detects any previously completed refund without issuing a second one.

Notice intents are keyed by source fact, order and kind; delivery uses one stable notice key. Relevant PAID, READY, CANCELLED and REFUNDED notices use their corresponding authoritative order/payment state. A readiness transition supersedes an unsent PAID notice; collection makes an unsent READY notice obsolete. All notices become ineligible when their retention window expires. Provider acceptance establishes only provider acceptance, not inbox delivery.

The implementation must preserve obligations across a crash after a business commit but before background processing. The CIM specifies this guarantee without choosing an outbox, queue or database technology. Tests inspect both the application's records and the sandbox provider's ledger to distinguish local retries from duplicate external effects.

## Privacy, replay and settlement

Customer-linked information is PERSONAL; sandbox capture/refund references and amounts are FINANCIAL with protection and access-audit expectations. Public catalogue prices and anonymous totals contain no identifying context. No card numbers, credentials or shipping addresses are modeled.

Set `terminalAt` at the first CANCELLED, EXPIRED or COLLECTED transition. Use the verified application instant for settlement evidence: a definitive unpaid result closes an uncharged order; collection of an already verified captured payment settles a collected order; a cancelled/expired captured order settles only on verified full refund. A later discovered capture clears an earlier unpaid settlement until its refund completes. Unsettled/refund-pending orders do not qualify for erasure.

Cleanup covers orders, lines, payment/session/capture/refund references, notices, identifying facts/audit, request keys, logs and scoped archives. Only anonymous totals, products and an unlinkable cleanup receipt remain. No retained order-ID tombstone is assumed. A late provider fact for an unknown erased order is rejected and cannot recreate it. Client mutations have a trusted, immutable 24-hour operation validity window; once an identifying replay result has been erased, an old request cannot create a new order. The caller cannot make that old operation fresh by altering a timestamp. This is protocol behavior, not an extra customer task.

The thirty-day policy is a fictional evaluation policy. It does not assert an appropriate accounting/retention rule for a real jurisdiction or retailer.

## Traceability and processes

| Requirements | Main CIM elements                                                              | Later evidence                                                                 |
| ------------ | ------------------------------------------------------------------------------ | ------------------------------------------------------------------------------ |
| R01–R03      | `cmd-product`, `cmd-restock`, `qry-catalogue`, `ent-product`                   | Product/revision validation, additive history, public catalogue                |
| R04–R05, R18 | `cmd-checkout`, `agg-sale`, `dt-checkout`, immutable `ent-line` fields         | Complete basket transaction, last-unit race, exact charge total                |
| R06–R08, R17 | `cmd-open-payment`, `cmd-payment`, `cmd-reconcile`, `cmd-expire`, `dt-payment` | Hosted-session binding, exact deadline, verified facts, lost-callback recovery |
| R09–R10      | `cmd-cancel`, `cmd-refund`, `pol-cancel`, `pol-refund`                         | Cancellation/readiness race, durable compensation, one completed full refund   |
| R11–R12      | `cmd-ready`, `cmd-collect`, `proc-fulfillment`                                 | Complete packing, verified handover and no duplicate collection                |
| R13–R14      | Queries, `agg-notice`, notice commands and policies                            | Owner/staff views, fresh states, relevance and failure visibility              |
| R15–R16      | Business operation keys/windows, SecurityConstraint `R16`, actors/roles        | Replay-safe mutations, cross-account denial, scoped provider identity          |
| R19–R20      | Reliability/performance scenarios, durable obligations                         | Restart recovery and reported burst workload results                           |
| R21–R22      | PrivacyConstraint `R21`, classifications, audit information, `cmd-purge`       | Complete retention inventory, safe history, settlement-dependent erasure       |
| R23          | Own-order view and usability scenario                                          | Pilot comprehension of totals, deadline and refund states                      |

The eight processes cover catalogue/stock maintenance, checkout, hosted payment and verified application, cancellation/expiry, packing/collection, refund settlement, notice delivery, and eligible erasure. They include decision branches, waits, deadlines and actionable exceptions. The payment/refund processes are business-level paths: refine provider-pending observations, due guards, interruptions and retry state before treating a generated workflow as executable.

Generate a PIM draft, then complete contracts, atomic persistence changes, identity propagation, provider adapters, timestamp/expression handling, callback verification, durable obligations, relevance/retry guards, retention coverage and query access. Review generated routes: trusted internal/provider commands must not become generally callable public operations. Review AWS mapping and generated artifact manual actions before implementing the business logic and running the acceptance scenarios. A successful transformation does not imply completed payment integration or a deployable application.
