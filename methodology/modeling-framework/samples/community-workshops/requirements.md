# Requirements and acceptance criteria

This is a fictional, authored evaluation baseline, not evidence of stakeholder elicitation or approval. All 21 requirements are mandatory for the reference application. A successful CIM validation does not demonstrate their runtime fulfillment. Business errors leave state unchanged; exact no-op replays produce no new facts or state-change audit entries.

Times, state transitions, notice relevance, and experiment reset rules are defined in [the model guide](model-guide.md) and [evaluation scenarios](evaluation-scenarios.md). Stable identifiers R01–R21 appear unchanged in the CIM, making omissions visible during refinement.

<a id="r01"></a>

## R01: Publish a workshop

**Type:** Functional.

An organizer shall publish an immutable workshop with a title (1-80 characters), venue (1-120), start, end, and capacity (integer 1-30). Start must be at least 26 hours after publication, and end must be after start. The booking cutoff is start minus two hours.

**Fit criterion:** Valid publication produces PUBLISHED; missing fields, invalid capacity, reversed times, or a start less than 26 hours away produce INVALID_WORKSHOP and no workshop.

**Acceptance:** Given an organizer and a complete workshop starting in 48 hours, when the organizer publishes it, then one PUBLISHED workshop is visible with the submitted details and derived cutoff.

**CIM scope:** `cmd-publish`, `ent-workshop`.

<a id="r02"></a>

## R02: Browse upcoming workshops

**Type:** Functional.

Anyone may browse PUBLISHED workshops whose start is in the future, ordered by start then workshop identifier, with title, venue, times, cutoff, capacity, available seats, waiting count, and booking-open indicator. Show no participant data. Availability is advisory until a seat request commits.

**Fit criterion:** Return only upcoming PUBLISHED workshops, 20 per page, with stable continuation; cancelled and completed workshops are absent.

**Acceptance:** Given published, cancelled, completed, and past workshops, when a visitor browses the catalogue, then only future PUBLISHED workshops appear and no participant identifiers or email addresses appear.

**CIM scope:** `qry-browse`, `ent-workshop`.

<a id="r03"></a>

## R03: Request one seat

**Type:** Functional.

An identified participant shall request one seat for themselves while the workshop is PUBLISHED and now is strictly before the cutoff. A free seat yields CONFIRMED immediately. At most one active registration (CONFIRMED or WAITLISTED) may exist per participant and workshop. A cancelled participant may submit a new request and gets a new registration.

**Fit criterion:** A valid request returns one registration and explicit CONFIRMED or WAITLISTED status. A second independent request while active returns ALREADY_REGISTERED; closed booking returns BOOKING_CLOSED without mutation.

**Acceptance:** Given one remaining seat and an eligible participant, when the participant requests a seat before cutoff, then one CONFIRMED registration exists and available seats decrease by one.

**CIM scope:** `cmd-request`, `ent-registration`.

<a id="r04"></a>

## R04: Join and order the waiting list

**Type:** Business Rule.

When no seat is available, a seat request shall create WAITLISTED only if fewer than ten entries are waiting. The participant must explicitly accept automatic promotion before cutoff. Priority is committed join time then registration identifier; a new request after cancellation goes to the tail. No consent produces WAITLIST_CONSENT_REQUIRED; a full list produces WAITLIST_FULL.

**Fit criterion:** An eleventh waiting request is rejected without mutation; equal join timestamps are ordered by registration identifier; waiting position is 1-based among current waiting entries.

**Acceptance:** Given a full workshop, nine waiting participants, and explicit promotion consent, when two different participants request seats in sequence, then the first becomes tenth on the waiting list and the second gets WAITLIST_FULL.

**CIM scope:** `cmd-request`, `pol-allocation`, `dt-allocation`.

<a id="r05"></a>

## R05: Cancel and promote fairly

**Type:** Functional.

A participant shall cancel their own CONFIRMED or WAITLISTED registration strictly before cutoff. Cancellation of a confirmed seat and promotion of the oldest waiting registration shall be one indivisible business change. Cancelling a waiting entry releases no seat. Cancelling an already CANCELLED registration is a no-op, including after cutoff. A different active registration is never cancelled by replay.

**Fit criterion:** One confirmed cancellation with a nonempty list yields CANCELLED for the caller and CONFIRMED for the oldest waiting participant; no vacancy is exposed between those changes. At cutoff an active cancellation returns BOOKING_CLOSED.

**Acceptance:** Given a confirmed Alice, waitlisted Cara then Dev, and time before cutoff, when Alice cancels, then Alice is CANCELLED, Cara is CONFIRMED, Dev is first waiting, and occupancy is unchanged.

**CIM scope:** `cmd-cancel`, `pol-promotion`, `agg-booking`.

<a id="r06"></a>

## R06: Cancel a workshop

**Type:** Functional.

An organizer shall cancel a PUBLISHED workshop only before start. The workshop and all CONFIRMED or WAITLISTED registrations become CANCELLED atomically, no waiting participant is promoted, and each affected participant receives one workshop-cancellation notice. Repeating cancellation is a no-op. Unsent reminders and obsolete confirmation notices are suppressed.

**Fit criterion:** All active registrations are cancelled; occupancy and waiting count are zero; no promotion event occurs. At or after start a new cancellation returns INVALID_STATE.

**Acceptance:** Given a published workshop with confirmed and waiting participants, when an organizer cancels before start, then the workshop and active registrations are CANCELLED, only cancellation notices remain eligible, and the workshop disappears from browse results.

**CIM scope:** `cmd-cancel-workshop`, `pol-cancel-workshop`.

<a id="r07"></a>

## R07: View my bookings

**Type:** Functional.

An identified participant shall view only their own retained registrations, including workshop details, current status, and current waiting position where applicable; newest join time first, 20 per page. A participant cannot obtain another participant's booking through a changed identifier or filter.

**Fit criterion:** A read after an acknowledged booking or cancellation reflects the committed state; another participant's details never appear.

**Acceptance:** Given cara has just been promoted and Ben also has a booking, when Cara views her bookings, then Cara sees CONFIRMED and no waiting position; Ben's booking is absent.

**CIM scope:** `qry-mine`, `ent-registration`.

<a id="r08"></a>

## R08: View roster and delivery failures

**Type:** Functional.

An organizer shall view a workshop roster with display name, participant identifier, status, waiting position, and attendance summary, and a separate list of notices that exhausted delivery attempts. Results are current after acknowledged changes, 20 per page, filterable by workshop and status. Participants have no access.

**Fit criterion:** Roster totals match authoritative registrations; failed notices show workshop, recipient, notice kind, attempt count, and last failure time without exposing message content in operational logs.

**Acceptance:** Given a workshop with confirmed, waiting, and cancelled registrations and an exhausted notice, when an organizer reads its roster and failed deliveries, then counts match active records and the failed notice is visible with three attempts.

**CIM scope:** `qry-roster`, `qry-failures`.

<a id="r09"></a>

## R09: Record attendance

**Type:** Functional.

During [start, end), an organizer shall mark a CONFIRMED registration ATTENDED. Repeating the same attendance mark is a no-op. WAITLISTED, CANCELLED, EXPIRED, and NO_SHOW registrations cannot attend; no mark can be introduced before start or at/after end.

**Fit criterion:** Only CONFIRMED to ATTENDED is accepted in the attendance window; invalid marks produce INVALID_STATE and no state change.

**Acceptance:** Given a confirmed Ben at the workshop start instant, when the organizer records Ben's attendance twice, then Ben is ATTENDED once and the attendance total increases by exactly one.

**CIM scope:** `cmd-attend`, `ent-registration`.

<a id="r10"></a>

## R10: Close workshops and classify absences

**Type:** Functional.

At or after end, passage of time shall close each still-PUBLISHED workshop: CONFIRMED becomes NO_SHOW, WAITLISTED becomes EXPIRED, ATTENDED remains unchanged, and the workshop becomes COMPLETED. Freeze anonymous attended, no-show, and expired totals. CANCELLED workshops are not completed. Repeated closure is a no-op.

**Fit criterion:** Before end closure is rejected; within 60 seconds after end an eligible workshop is COMPLETED with accurate totals, independent of notice delivery.

**Acceptance:** Given an ended workshop with one ATTENDED, one CONFIRMED, and one WAITLISTED registration, when the due closure runs twice, then the workshop is COMPLETED with one attended, one no-show, and one expired; totals do not change on replay.

**CIM scope:** `cmd-close`, `pol-close`.

<a id="r11"></a>

## R11: Request due reminders

**Type:** Functional.

During [start minus 24 hours, cutoff), due checks shall request at most one reminder per currently CONFIRMED registration, within 60 seconds of becoming eligible. A participant confirmed later in that window is included by the next check. WAITLISTED and cancelled registrations receive no reminder. A missed window is not replayed after cutoff.

**Fit criterion:** Repeated due checks create one reminder intent per registration; newly promoted participants in the window become eligible; no new reminder is requested at cutoff.

**Acceptance:** Given a confirmed and a waiting registration at start minus 24 hours, when two due checks execute, then one reminder is requested for the confirmed participant and none for the waiting participant.

**CIM scope:** `cmd-remind`, `pol-remind`.

<a id="r12"></a>

## R12: Prepare and deliver essential notices

**Type:** Functional.

Committed confirmation, waiting assignment, registration cancellation, promotion, workshop cancellation, and reminder facts shall each yield one durable notice intent per affected registration and kind. Notice content includes workshop title, venue, start with timezone, cutoff where relevant, and resulting booking status. Dispatch rechecks current state and suppresses obsolete waiting, confirmation, promotion, or reminder notices. Terminal registration/workshop cancellation notices may still be delivered. Provider acceptance means accepted by the provider, not inbox receipt.

**Fit criterion:** Duplicate facts create no duplicate intents; provider acceptance records ACCEPTED, permanent suppression records SUPPRESSED. Workshop cancellation supersedes all unsent booking/reminder notices. A reminder at/after start is obsolete.

**Acceptance:** Given a CONFIRMED fact is redelivered and its resulting notice is pending, when the preparation process runs twice and then delivery succeeds, then one notice exists with one provider acceptance; the booking remains unchanged.

**CIM scope:** `cmd-prepare`, `cmd-dispatch`, `ent-notice`, `pol-notice`.

<a id="r13"></a>

## R13: Retry delivery without changing bookings

**Type:** Business Rule.

A failed or timed-out delivery shall retry at least one minute after attempt one and at least five minutes after attempt two, with each eligible retry starting within 60 seconds. Allow three total attempts. Exhaustion becomes FAILED and appears to organizers. Recheck relevance before every attempt. Use the same notice identifier as provider deduplication key, including timeout retries; the test provider supports that key. Concurrent dispatches must not create extra attempts.

**Fit criterion:** Simulated failure, failure, success yields three attempts and ACCEPTED; three failures yield FAILED with no fourth attempt. Seat allocation and waiting order never change. An ambiguous timeout must reuse the same provider key.

**Acceptance:** Given a still-relevant notice and a provider failing the first two calls, when the first attempt and both eligible retries run, then attempt times respect the delays, attempt count is three, and the final state is ACCEPTED with unchanged registration state.

**CIM scope:** `cmd-dispatch`, `pol-retry`, `ent-notice`.

<a id="r14"></a>

## R14: Limit personal data and retention

**Type:** Privacy.

Collect only verified participant identifier, display name, email, and booking history needed to run the workshop. At end plus 30 days (also for cancelled workshops), remove identifying registrations, notices, audit details, and related idempotency records within 24 hours. Retain workshop details and anonymous totals only; retained totals must contain no person or registration identifiers. Do not copy personal data into operational logs or durable event archives beyond this retention boundary.

**Fit criterion:** After retention cleanup no participant, email, registration, notice-recipient, request key, or identifying audit/event record remains for the workshop; anonymous totals are unchanged.

**Acceptance:** Given a completed workshop at end plus 30 days with identifying notices and audit records, when the retention cleanup runs and is repeated, then all scoped identifying records are removed once and anonymous totals remain intact.

**CIM scope:** `cmd-purge`, `pol-retention`, `ent-registration`, `ent-notice`.

<a id="r15"></a>

## R15: Protect seat and waiting invariants

**Type:** Business Rule.

Each workshop is an independent strong consistency boundary. Confirmed plus attended registrations never exceed capacity. Current waiting entries never exceed ten. Allocation, cancellation/promotion, and workshop-wide cancellation preserve these invariants even when requests are simultaneous. A waiting participant cannot be bypassed by a newcomer for a released seat. Requests for different workshops do not need a shared business transaction.

**Fit criterion:** Ten distinct participants race for one remaining seat: exactly one is confirmed, nine wait if room exists, occupancy equals capacity, and all active participant/workshop pairs are unique.

**Acceptance:** Given one remaining seat, an empty waiting list, and ten distinct participants, when all ten request seats concurrently, then one becomes CONFIRMED and nine become WAITLISTED; no capacity or uniqueness invariant is violated.

**CIM scope:** `agg-booking`, `cmd-request`, `cmd-cancel`, `cmd-cancel-workshop`.

<a id="r16"></a>

## R16: Handle duplicate operations

**Type:** Business Rule.

Every mutation shall carry a stable business operation key. A participant/organizer request is identified by actor identifier, command kind, and client request identifier; replay with the same payload returns the original outcome and creates no further effects. Reuse with different payload is rejected as REQUEST_KEY_REUSED. Internal keys identify the workshop closure, registration reminder, event/recipient/kind notice intent, notice attempt ordinal, or workshop retention run. After personal cleanup, retained identities cannot be replayed into a past workshop.

**Fit criterion:** Exact replay creates no duplicate registration, promotion, audit state change, reminder, or notice; key/payload mismatch has no mutation. Retries share the provider notice key but have distinct attempt ordinals.

**Acceptance:** Given a successful seat request and its original business operation key, when the same request is replayed, followed by a changed-payload request using that key, then the replay returns the original outcome with no side effects and changed payload yields REQUEST_KEY_REUSED.

**CIM scope:** `cmd-request`, `cmd-cancel`, `cmd-dispatch`.

<a id="r17"></a>

## R17: Enforce business permissions

**Type:** Security.

Only verified participants may mutate or read their own bookings. Only centre organizers may publish, cancel workshops, see rosters/failures, or record attendance. Time- and policy-initiated operations require a trusted service identity. The email provider may receive intended notices but may not issue booking mutations. Unauthorized requests return an access error without exposing another person's data or changing any state.

**Fit criterion:** Anonymous mutation, participant roster read, and cross-participant cancellation all fail; authorized equivalents succeed; no unauthorized effect or personal-data response occurs.

**Acceptance:** Given alice and Ben with separate identities and one organizer, when Alice tries to cancel Ben's booking and read the roster, then both are denied without state changes or disclosure.

**CIM scope:** `actor-participant`, `actor-organizer`, `cmd-request`, `cmd-cancel`, `qry-mine`, `qry-roster`, `qry-failures`.

<a id="r18"></a>

## R18: Respond promptly under announcement load

**Type:** Quality.

With 100 identified participants, up to 20 concurrent requests, and 100 catalogue/booking requests over 60 seconds, the 95th percentile response time for catalogue, own-booking, and seat-request interactions shall be at most two seconds. Include cold starts and rejected requests; exclude deliberate provider-delay time. Report environment, region, concurrency, sample count, and errors. All seat invariants still hold.

**Fit criterion:** At least 100 measured interactions in the stated environment meet p95 <= 2 seconds and produce zero invariant violations; record failures separately rather than dropping them.

**Acceptance:** Given the documented evaluation deployment and a 100-request workload, when the announcement workload runs including initially idle components, then p95 response time is at most two seconds and seat invariants hold.

**CIM scope:** `cmd-request`, `qry-browse`, `qry-mine`.

<a id="r19"></a>

## R19: Preserve committed work across failures

**Type:** Quality.

An acknowledged booking outcome and its notice obligation shall survive worker restart or message redelivery. A provider outage never reverses a committed registration. In the fault test, restart after booking commit but before notice preparation; after recovery within ten minutes, materialize the missing notice within 60 seconds, without requiring the participant to resubmit. Existing exhausted delivery attempts remain visible rather than restarting silently.

**Fit criterion:** Inject a restart at the commit/notice boundary: one booking survives, exactly one notice intent is recovered, and its registration outcome is unchanged. No claim of exactly-once inbox delivery is made.

**Acceptance:** Given a committed booking whose notice obligation has not yet been processed, when the worker restarts and processing resumes within ten minutes, then the booking survives and one pending notice intent is recovered within 60 seconds.

**CIM scope:** `cmd-request`, `cmd-prepare`, `cmd-dispatch`, `ent-notice`.

<a id="r20"></a>

## R20: Keep accountable business history

**Type:** Audit.

Every successful workshop or registration state change shall append a business audit entry containing operation key, actor or responsible role, workshop and registration where relevant, occurrence time, previous state, and new state. Replayed no-ops do not append another state change. Organizers can inspect the history for their workshop. Access is restricted and identifying history follows R14 retention.

**Fit criterion:** The main lifecycle can be reconstructed in order; a replay has one state-change entry; privacy cleanup removes identifying history and retains only anonymous totals.

**Acceptance:** Given alice books, cancels, and replays her cancellation, when the organizer inspects the workshop history, then one booking and one cancellation change are visible with actor, time, and before/after states.

**CIM scope:** `info-audit`, `qry-roster`, `cmd-request`, `cmd-cancel`.

<a id="r21"></a>

## R21: Make outcomes understandable

**Type:** Quality.

For each seat request or cancellation, show the resulting status or a plain-language business error, workshop name, local start and cutoff times with timezone, and current waiting position when applicable. Clearly state that waiting is not a confirmed seat and that joining consents to automatic promotion. The own-bookings view remains the authoritative status when email is delayed.

**Fit criterion:** A first-time participant can find the workshop, request a seat, correctly distinguish confirmed from waiting, and find the cancellation cutoff in at most five minutes without moderator instruction; record success and misunderstandings in the pilot.

**Acceptance:** Given a first-time participant and the standard fixture, when they browse, request a seat, and explain the resulting screen, then they correctly identify their status, any waiting position, and the cutoff within five minutes.

**CIM scope:** `qry-browse`, `qry-mine`, `cmd-request`, `cmd-cancel`.
