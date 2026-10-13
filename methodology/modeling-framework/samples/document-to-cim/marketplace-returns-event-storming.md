# Marketplace Returns - Event Storming Workshop Output

## Workshop Context

Domain: Marketplace returns and refunds for an online retail marketplace.

Business scope: Customers request returns, sellers approve or contest requests, warehouse staff
inspect returned items, support agents resolve disputes, and finance reconciles refunds and seller
adjustments.

Primary business outcomes:

- Customers receive clear return decisions within one business day.
- Refunds are issued only for eligible returned items.
- Sellers can dispute suspicious or damaged returns with auditable evidence.
- Finance can reconcile refunds, fees, and seller adjustments daily.

## People and Systems

- Customer: starts a return, ships items, checks refund status.
- Seller Operations Specialist: reviews returns that need seller decision.
- Warehouse Inspector: records received item condition.
- Support Agent: resolves customer or seller disputes.
- Finance Analyst: reviews refund exceptions and daily reconciliation.
- Payment Gateway: receives refund instructions and reports payment status.
- Carrier Tracking System: reports shipment milestones and proof of delivery.

## Commands

- Request Return, issued by Customer.
- Upload Return Evidence, issued by Customer.
- Approve Return, issued by Seller Operations Specialist.
- Reject Return, issued by Seller Operations Specialist.
- Register Return Shipment, issued by Carrier Tracking System.
- Record Item Inspection, issued by Warehouse Inspector.
- Issue Refund, issued by Finance Analyst or policy automation.
- Open Return Dispute, issued by Customer or Seller Operations Specialist.
- Resolve Return Dispute, issued by Support Agent.

## Domain Events

- Return Requested.
- Return Evidence Uploaded.
- Return Approved.
- Return Rejected.
- Return Shipment Registered.
- Return Package Delivered.
- Returned Item Inspected.
- Refund Issued.
- Refund Failed.
- Return Dispute Opened.
- Return Dispute Resolved.
- Seller Adjustment Scheduled.

## Policies and Rules

- Auto Approval Policy: approve requests when order delivery date is within 30 days, item category
  allows returns, and no fraud flag is present.
- Inspection Policy: refund cannot be issued until every returned item has an inspection result,
  unless the seller explicitly waives inspection.
- Dispute Escalation Policy: disputes older than 48 hours move to support priority review.
- Refund Failure Policy: payment failures create a finance exception and notify support.

Decision table: Return Eligibility.

- If item is final sale, reject with reason "Final sale item".
- If return window expired, reject with reason "Return window expired".
- If fraud hold exists, require manual review.
- Otherwise approve.

Decision table: Refund Amount.

- If item condition is sellable, refund item price minus restocking fee if configured.
- If item condition is damaged by customer, refund zero and create seller adjustment review.
- If item condition is damaged in transit, refund customer and create carrier claim follow-up.

## Aggregates and Entities

- Return Request: identity is return number; lifecycle states are Draft, Submitted, Approved,
  Rejected, In Transit, Delivered, Inspected, Refunded, Disputed, Closed.
- Return Item: identity is order item id plus return number; records SKU, quantity, declared
  reason, condition, inspection result, and refund amount.
- Refund: identity is refund id; records payment reference, status, requested amount, issued
  amount, and failure reason.
- Dispute: identity is dispute id; records opened by, reason, evidence, resolution, and decision.
- Seller Adjustment: identity is adjustment id; records seller id, amount, reason, and status.

Value objects:

- Money Amount: currency and decimal amount.
- Return Window: start date, end date, and policy name.
- Evidence Attachment: file name, uploaded by, timestamp, and evidence type.
- Inspection Result: condition, notes, photos present, and inspector id.

## Queries and Read Models

- View Return Status for Customer.
- List Pending Seller Decisions.
- List Warehouse Inspections Due.
- View Refund Reconciliation Exceptions.
- View Dispute Case Timeline.

## Business Errors

- Return Window Expired.
- Item Not Returnable.
- Missing Shipment Proof.
- Inspection Required.
- Refund Payment Failed.
- Duplicate Return Request.

## Hotspots and Open Questions

- Fraud scoring provider is not selected.
- Restocking fee rules vary by seller contract and need product-owner confirmation.
- Some sellers want instant refunds before inspection; risk impact is unresolved.
- Carrier proof-of-delivery events may arrive late or out of order.

## Compliance and Privacy

- Customer identity, address, payment reference, and evidence attachments are personal data.
- Refund actions and dispute resolutions must be auditable for 7 years.
- Support agents may see evidence attachments only for assigned dispute cases.
