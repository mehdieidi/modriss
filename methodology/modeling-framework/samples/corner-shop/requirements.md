# Requirements and acceptance criteria

This is an authored, fictional ordering baseline for MODRISS evaluation. All 23 requirements are mandatory. The payment provider and monetary transactions in experiments use a sandbox; this specification does not assert legal or production compliance. Passing CIM validation does not demonstrate runtime fulfillment.

Read the [scenario](README.md), [model guide](model-guide.md), and [evaluation scenarios](evaluation-scenarios.md) together with these requirements.

<a id="r01"></a>

## R01 — Maintain a small product catalogue

**Type:** Functional.

Staff shall create or revise products with a unique immutable SKU, name (1-80 characters), price (1-100000 USD cents), and ACTIVE or WITHDRAWN state. At most twenty products are active. A price change increments its revision. Withdrawal prevents new checkouts, while existing orders keep their original line snapshots. Withdrawn products cannot be reactivated in this baseline. Creation starts with zero available stock.

**Fit criterion:** Valid changes affect future catalogue/checkouts only; duplicate SKU, invalid price/name, active-product overflow, or attempted reactivation has no effect.

**Acceptance:** Given a product at 500 cents and an existing order containing it, when staff changes its price to 600 cents and later withdraws it, then the existing order still shows 500 cents and the product is unavailable to a new checkout.

**CIM scope:** `cmd-product`, `ent-product`.

<a id="r02"></a>

## R02 — Browse products

**Type:** Functional.

Anyone may browse ACTIVE products with SKU, name, current price/revision, currency, and advisory available quantity. Return twenty per page, ordered by name then SKU, with stable continuation. Browsing does not reserve stock and exposes no customer or payment data.

**Fit criterion:** Only active products appear; browsing out-of-stock products is allowed but the screen states that checkout cannot reserve unavailable units.

**Acceptance:** Given active, withdrawn, and out-of-stock products, when a visitor browses, then only active products appear with correct prices and advisory stock, without personal information.

**CIM scope:** `qry-catalogue`.

<a id="r03"></a>

## R03 — Add stock explicitly

**Type:** Functional.

Staff shall add a positive integer quantity from 1 to 1000 to an ACTIVE product using a reason and a request key. Available stock may not be directly overwritten. Replaying the same addition has no extra effect. Existing holds and sold units are unaffected.

**Fit criterion:** A stock addition increases available quantity exactly once; zero, negative, fractional quantities and unauthorized additions fail without mutation.

**Acceptance:** Given a product with two available units, when staff adds five units and replays that request, then available quantity is seven and one stock-addition history entry exists.

**CIM scope:** `cmd-restock`, `ent-product`.

<a id="r04"></a>

## R04 — Check out a bounded basket

**Type:** Functional.

An identified customer shall submit 1-5 distinct ACTIVE products with integer quantities 1-5 each, quoted unit prices and price revisions, their verified display name/email, and a request key. Reject duplicate product lines, stale price/revision, invalid input, or insufficient stock without creating any order or hold. A valid checkout atomically creates an AWAITING_PAYMENT order, immutable line snapshots, one payment record, and HELD line quantities; deduct all requested units from availability. Set expiresAt to committed checkout instant plus fifteen minutes. The basket is a client-side draft, not a separately persisted business record.

**Fit criterion:** Either every line is reserved and the order exists or no line changes; client-supplied totals cannot determine the charge.

**Acceptance:** Given two products with enough stock and current quoted prices, when a customer checks out both products, then one order with correct immutable lines and a fifteen-minute deadline is returned, and both stock quantities decrease.

**CIM scope:** `cmd-checkout`, `ent-order`, `ent-line`, `agg-sale`.

<a id="r05"></a>

## R05 — Prevent overselling and double release

**Type:** Business Rule.

Checkout and each later stock-affecting transition shall preserve nonnegative availability across all affected products. A line progresses HELD to COMMITTED on timely payment, or HELD/COMMITTED to RELEASED on eligible expiry/cancellation; collection makes COMMITTED into FULFILLED. Release each line at most once. Concurrent expiry, payment, cancellation, restocking, and checkout must serialize into valid complete outcomes. A failed multi-product checkout may not hold any subset.

**Fit criterion:** Ten customers competing for one unit produce exactly one held order and nine OUT_OF_STOCK outcomes; racing closure paths return stock at most once.

**Acceptance:** Given one available unit and ten different customers, when ten valid checkouts race, then one order holds the unit, nine requests fail, and availability is zero.

**CIM scope:** `agg-sale`, `cmd-checkout`, `cmd-expire`, `cmd-cancel`, `cmd-payment`.

<a id="r06"></a>

## R06 — Open a hosted payment session

**Type:** Functional.

The owner of an unexpired AWAITING_PAYMENT order may open its hosted payment session. Send authoritative order reference, amount, USD currency and expiresAt to the provider. Use one stable provider session key per order; repeated requests return the same session. A provider timeout may be retried using that key while the order is eligible. The application never collects card data. Visiting a payment return URL does not mark an order paid.

**Fit criterion:** A session uses the server-computed amount/deadline; at now >= expiresAt or after cancellation no new session is opened; a timeout/retry does not create another session.

**Acceptance:** Given an unpaid unexpired order, when its owner opens payment twice, then the same provider session for the authoritative amount is returned and the order is still AWAITING_PAYMENT.

**CIM scope:** `cmd-open-payment`, `actor-payment`.

<a id="r07"></a>

## R07 — Apply verified payment results

**Type:** Business Rule.

Only authenticated provider facts or trusted reconciliation observations may change payment state. Verify order/session association, one capture reference per order, amount, currency, fact identity, and provider occurrence time. A first matching capture pays an AWAITING_PAYMENT order only when both provider capturedAt and processing time are strictly before expiresAt. Commit PAID, payment CAPTURED and HELD-to-COMMITTED together. A capture first learned at/after the deadline or after cancellation/expiry creates one full refund obligation; never reopen that order. A repeated already-applied capture is a no-op even after the deadline. Stale unpaid/decline facts cannot downgrade a capture. Invalid, conflicting, or mismatched facts are quarantined for staff inspection with no order/stock mutation. Unknown or erased order references are rejected without retaining the supplied personal or financial references; only an anonymous rejection count may remain. Quarantine applies to verifiable existing retained orders.

**Fit criterion:** A valid timely capture produces PAID once; a late capture remains CANCELLED/EXPIRED with REFUND_PENDING; duplicates never refund a timely PAID/READY/COLLECTED order.

**Acceptance:** Given an awaiting order and a verified exact-amount capture before its deadline, when the capture fact is applied twice, then the order is PAID once with committed lines and no stock release or duplicate effect.

**CIM scope:** `cmd-payment`, `dt-payment`, `ent-payment`.

<a id="r08"></a>

## R08 — Expire unpaid orders

**Type:** Functional.

At now >= expiresAt, trusted due processing shall change each still-AWAITING_PAYMENT order to EXPIRED and release every HELD line atomically, completing within sixty seconds. A verified definitive unpaid/declined result before expiry instead changes an awaiting order to CANCELLED and releases its holds. Already-paid orders are never expired. Unknown payment outcomes remain under reconciliation, and a later discovered capture follows the refund rule.

**Fit criterion:** At deadline minus 1ms expiry has no effect; at the deadline it releases stock once. Repeated expiry or stale decline facts cannot release paid stock.

**Acceptance:** Given an unpaid order at its exact deadline, when expiry runs twice, then the order is EXPIRED and each held unit returns once to availability.

**CIM scope:** `cmd-expire`, `cmd-payment`, `pol-expiry`.

<a id="r09"></a>

## R09 — Cancel before packing is complete

**Type:** Functional.

A customer may cancel only their own AWAITING_PAYMENT or PAID order. Cancellation of an awaiting order before its deadline produces CANCELLED and releases holds; at/after the deadline it is processed as EXPIRED. Cancellation of PAID atomically produces CANCELLED, releases committed stock, and records REFUND_PENDING with a full-refund obligation. READY and COLLECTED orders cannot be cancelled. Repeating a completed cancellation is harmless. If staff readiness races cancellation, exactly one eligible transition wins.

**Fit criterion:** Paid cancellation immediately restores stock but reports refund pending; it never claims money has already been returned. A losing cancel/ready request has no partial effects.

**Acceptance:** Given a paid order not yet ready, when the owner cancels, then the order is CANCELLED, stock is released once, and one full refund is pending.

**CIM scope:** `cmd-cancel`, `pol-cancel`.

<a id="r10"></a>

## R10 — Complete or expose full refunds

**Type:** Functional.

Refunds are full amount only, against the verified capture, using one immutable provider refund key per capture. Durable refund obligations arise from paid cancellation or late capture. Trusted processing submits immediately, then retries eligible failures after at least one minute and five minutes, within sixty seconds of each due time, with at most three automatic submission attempts. A timeout uses lookup/reconciliation and the same key, never a new refund. Verified completion changes payment to REFUNDED and records refundedAt. After automatic exhaustion keep REFUND_PENDING with needsAttention=true; staff may retry the same obligation/key, without changing order or stock. Provider acceptance of a refund request alone is not refund completion.

**Fit criterion:** No refund exceeds captured amount or occurs twice; three failures remain visible to staff. Provider timeout-after-success plus retry yields one completed refund.

**Acceptance:** Given a cancelled paid order and a provider failing its first two refund submissions, when the obligation and due retries run, then a verified completion arrives, then the same key is used, one full refund completes, and payment becomes REFUNDED without another stock change.

**CIM scope:** `cmd-refund`, `cmd-payment`, `pol-refund`.

<a id="r11"></a>

## R11 — Mark an order ready

**Type:** Functional.

Staff shall mark only a PAID order with matching captured payment and COMMITTED lines READY after physically packing the complete order. This closes customer cancellation and creates a ready-for-collection notice obligation. Repeating readiness is a no-op. Partial packing and substitutions are outside scope.

**Fit criterion:** Unpaid, cancelled, expired, or collected orders cannot newly become READY; paid readiness preserves inventory counters.

**Acceptance:** Given a paid complete order and packed goods, when staff marks it ready twice, then the order is READY once and exactly one ready notice obligation exists.

**CIM scope:** `cmd-ready`.

<a id="r12"></a>

## R12 — Record store collection

**Type:** Functional.

Staff shall record collection only for a READY order after checking the order reference and the customer identity shown on its receipt. Atomically change the order to COLLECTED and all COMMITTED lines to FULFILLED. Repeated collection is a no-op; collection never increases available stock. Uncollected READY orders remain visible for staff follow-up; no automatic pickup deadline or refund is assumed.

**Fit criterion:** Only READY to COLLECTED succeeds, exactly once; there is no available-stock increase.

**Acceptance:** Given a ready order whose customer presents its receipt, when staff verifies identity and records collection twice, then one COLLECTED state change exists, its lines are FULFILLED, and availability is unchanged.

**CIM scope:** `cmd-collect`.

<a id="r13"></a>

## R13 — Inspect customer and staff order views

**Type:** Functional.

Customers shall see only their own orders with immutable line details, total, order/payment state, payment deadline, session action when eligible, refund progress, and chronological status history. Staff shall see all orders, stock movement history, payment exceptions and refund obligations needing attention; delivery failures are separately listed. Lists use twenty results per page with stable continuation, filters by relevant state, and newest creation time then order identifier. Reads after acknowledged mutations reflect committed state.

**Fit criterion:** Changing an order identifier never exposes another customer; staff can distinguish waiting for payment, paid, ready, collected, cancelled/refund pending, refunded, and expired.

**Acceptance:** Given two customers with different order states and one failed refund obligation, when each customer and a staff member opens the appropriate view, then customers see only their orders; staff sees correct states, history, and the refund needing attention.

**CIM scope:** `qry-mine`, `qry-staff`, `qry-notice-failures`.

<a id="r14"></a>

## R14 — Deliver relevant order notices

**Type:** Functional.

Committed paid, cancelled, ready, and refund-completed facts shall create one durable notice intent per fact/order/kind. Include order reference, resulting status, authoritative amount when relevant, and store pickup details for readiness. Deduplicate redelivered facts. Before every send, suppress obsolete notices: PAID requires order PAID, READY requires READY, CANCELLED requires CANCELLED, REFUNDED requires payment REFUNDED. Each kind also requires unexpired retention. Provider acceptance means accepted for email delivery, not arrival in an inbox. Retry failures after one and five minutes, three total attempts maximum; exhausted failures remain visible to staff and never change order/payment/stock.

**Fit criterion:** Duplicate facts create one intent; a held PAID notice is suppressed if readiness supersedes it; provider failures do not change purchase outcomes.

**Acceptance:** Given a paid fact delivered twice and a temporarily failing email service, when notice processing and eligible retries run, then one relevant notice intent is accepted or visibly exhausted, without another order or stock mutation.

**CIM scope:** `cmd-prepare-notice`, `cmd-deliver-notice`, `ent-notice`.

<a id="r15"></a>

## R15 — Make every mutation replay safe

**Type:** Business Rule.

Customer/staff mutations use verified actor + command kind + client request identifier and a payload fingerprint. Exact replay returns the original outcome without extra stock changes, charge/session/refund requests, audit state changes, or notice obligations. Reusing a key with changed payload returns REQUEST_KEY_REUSED. Internal keys identify order expiry, provider fact/capture, refund obligation/attempt, notice intent/attempt, or retention run. Provider session/capture/refund keys remain stable across caller retries. New customer/staff mutations carry a trusted operation validity deadline fixed at issue time plus twenty-four hours and bound to the original actor/key/payload. Unknown or erased keys cannot start a mutation at/after that deadline. A retained exact-replay result may still be read afterwards. A caller cannot extend an old operation validity window by changing a timestamp.

**Fit criterion:** Exact duplicate checkout creates one order/hold, duplicate cancellation releases once, and changed-payload reuse has no effects.

**Acceptance:** Given a successful checkout and its original request key, when the request is replayed and then a changed basket uses the same key, then the replay returns the original order and changed payload is rejected without stock mutation.

**CIM scope:** `cmd-checkout`, `cmd-cancel`, `cmd-payment`, `cmd-refund`, `agg-sale`.

<a id="r16"></a>

## R16 — Enforce identities and permissions

**Type:** Security.

Customer identities and verified contact details are provisioned for evaluation. Customers may act only on their own orders. Staff alone maintain catalogue/stock, mark readiness/collection, inspect all orders/history and retry exhausted refunds. Trusted services perform due, reconciliation, notice, and retention work. Provider callback identities may supply verified payment facts but cannot edit catalogue, cancel arbitrary orders, or mark collection. Anonymous browsing is public; private reads and all mutations require their assigned identity.

**Fit criterion:** Anonymous checkout, cross-customer reads/cancellation, customer staff-view access, forged callbacks and provider-origin inventory mutations fail without unauthorized effects or disclosure.

**Acceptance:** Given Alice and Ben have different accounts and one order each, when Alice requests Ben's order/cancellation and a forged callback is submitted, then all are denied with unchanged business state and no foreign personal-data response.

**CIM scope:** `actor-customer`, `actor-staff`, `cmd-checkout`, `cmd-cancel`, `cmd-payment`, `qry-mine`, `qry-staff`.

<a id="r17"></a>

## R17 — Reconcile missing provider results

**Type:** Quality.

While payment outcome is unresolved, or a refund is pending, trusted processing shall query the provider ledger by the stable order/capture/refund keys at least once per sixty seconds while the provider is reachable. Apply verified observations through the same guarded payment-result command as callbacks. Recover missed captured, definitively unpaid, and completed-refund results. A definitive unpaid result for an existing capture is stale and cannot downgrade it. Invalid/conflicting facts are visible for staff review. The test provider supplies a final unpaid/capture result no later than ten minutes after session expiry.

**Fit criterion:** If a callback is lost, the next reachable reconciliation recovers it within sixty seconds without a second capture/refund or an order reopening.

**Acceptance:** Given a captured payment or completed refund with its callback withheld, when the next due reconciliation succeeds, then the correct guarded state appears within sixty seconds without duplicate financial effects.

**CIM scope:** `cmd-reconcile`, `cmd-payment`.

<a id="r18"></a>

## R18 — Calculate totals exactly

**Type:** Business Rule.

All prices and amounts are integer USD cents, bounded as in R01/R04. Line total equals snapshotted unit price times quantity; order total equals the exact sum. Listed prices include all charges for this fictional pickup shop; tax calculation, shipping fees, coupons and currency conversion are outside scope. Reject float/fractional quantities and inconsistent quotes; do not trust a client total. Refunds equal the captured order total and never exceed it.

**Fit criterion:** Two notebooks at 500 cents and one pen at 200 cents cost 1200 cents; later product price edits leave that order total unchanged.

**Acceptance:** Given a basket of two 500-cent notebooks and one 200-cent pen, when checkout computes its total, then line totals are 1000 and 200 cents, total is 1200, and provider/refund amounts match exactly.

**CIM scope:** `ent-line`, `ent-order`, `info-order-total`, `cmd-checkout`.

<a id="r19"></a>

## R19 — Recover committed obligations

**Type:** Quality.

An acknowledged stock/order/payment transition and its required refund/notice obligations shall survive worker restart. After recovery within ten minutes, process pending obligations within sixty seconds of becoming eligible/reachable. Do not require the customer to resubmit checkout. A crash between provider acceptance and local recording must reconcile using stable keys. Do not publish a receipt indicating payment or refund completion before it is verified.

**Fit criterion:** Crash after paid cancellation commits but before refund submission: one cancelled order and released inventory survive; one refund obligation is recovered and submitted without duplicate stock release.

**Acceptance:** Given a committed paid cancellation before refund or notice processing, when processing restarts and resumes within ten minutes, then one durable refund and notice obligation survives and completes according to provider outcomes.

**CIM scope:** `cmd-cancel`, `cmd-refund`, `cmd-prepare-notice`, `agg-sale`.

<a id="r20"></a>

## R20 — Handle a small launch burst

**Type:** Quality.

For a documented test deployment, measure 100 catalogue, own-order and checkout requests over sixty seconds, up to twenty concurrently, including cold starts and rejections. The 95th percentile response time shall be at most two seconds for those local interactions; external hosted-payment and email time is measured separately. Report sample count, errors, concurrency, deployment/environment and percentile method. Stock invariants must hold.

**Fit criterion:** At least 100 measured interactions meet p95 <=2 seconds with no overselling; unsuccessful requests are not omitted from results.

**Acceptance:** Given an initially idle deployment and the stated 100-request workload, when the burst runs, then p95 local response latency is at most two seconds and all inventory invariants hold.

**CIM scope:** `cmd-checkout`, `qry-catalogue`, `qry-mine`.

<a id="r21"></a>

## R21 — Minimize and remove identifying data

**Type:** Privacy.

Use only customer identifier, display name, verified email, order history and provider transaction references needed for this scenario; no card data, shipping address, or marketing profile is stored. For CANCELLED, EXPIRED or COLLECTED orders, erase identifying orders/lines/payment/notice/audit/fact/request-key records within twenty-four hours after the later of terminal order time plus thirty days and financial settlement time. Financial settlement means verified captured payment for a collected order, definitive unpaid closure, or verified full refund for a cancelled/expired charged order. Pending/unresolved refunds are not erased and remain visible to staff. Retain only anonymous sales/refund totals, product data, and a non-identifying erasure receipt that cannot link back to a person/order/provider reference. Stop late facts from recreating erased personal records.

**Fit criterion:** Eligible cleanup removes all scoped identifiers and contact/transaction/history data across every store while anonymous totals remain; unresolved refunds are retained and flagged.

**Acceptance:** Given a terminal financially settled order beyond its retention eligibility and a separate unresolved refund, when retention processing runs twice, then the eligible order's identifying records are gone and the unresolved refund remains visible; replay cannot recreate erased data.

**CIM scope:** `cmd-purge`, `pol-retention`, `ent-order`, `ent-payment`, `ent-notice`.

<a id="r22"></a>

## R22 — Keep accountable history

**Type:** Audit.

Every successful product/stock/order/payment state change shall record operation key, responsible verified actor/role, occurrence time, related identifiers, and before/after values. Stock entries include quantity and reason; payment/refund entries include safe provider references and result, never card secrets. No-op replay does not append another state change. Staff alone inspect identifying history; operational logs contain no email, payment credentials, or message bodies. Identifying history follows R21.

**Fit criterion:** Checkout, capture, cancellation, refund and stock restoration can be reconstructed; replay adds no duplicate transition, and retention removes identifying history.

**Acceptance:** Given an order that is checked out, paid, then cancelled and refunded, when staff inspects its history after duplicate callbacks, then one coherent transition history and one release/refund appear, without sensitive secrets.

**CIM scope:** `info-audit`, `qry-staff`, `cmd-payment`, `cmd-cancel`.

<a id="r23"></a>

## R23 — Make ordering and refund states understandable

**Type:** Quality.

Show a readable receipt with product/quantity/price snapshots, exact total, order state, payment deadline with timezone, and next eligible action. Distinguish awaiting payment, paid, ready, collected, expired, cancelled/refund pending, and refunded. Explain that stock is held for fifteen minutes and a browser payment-return screen is not proof of payment. Own-order status remains authoritative when email is delayed.

**Fit criterion:** In a pilot, a first-time customer can order the fixture basket and correctly explain total, hold deadline and payment state in five minutes without moderator instruction; after cancellation correctly distinguish refund pending from refunded.

**Acceptance:** Given a first-time customer and the reference catalogue, when they order and inspect a cancellation/refund example, then they explain total, deadline and states correctly within five minutes; record misunderstandings.

**CIM scope:** `qry-mine`, `cmd-checkout`, `cmd-cancel`.
