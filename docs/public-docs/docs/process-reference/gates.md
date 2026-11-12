<!-- Process documentation is maintained by hand. -->

# Decision gates

A gate is a review of a defined scope and specific versions of the work. The decision can accept the result, request rework, defer it or stop the work. The method profile defines participants and each person's authority.

| Gate                          | Decision                                                                                                                     | Main decision owner                                                       |
| ----------------------------- | ---------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------- |
| G0. Project decision          | Do value, feasibility, cost and risk justify continuing, investigating further or changing direction?                        | Sponsor.                                                                  |
| G1. Method and team readiness | Are the method, responsibilities, controls and first increment ready for active development?                                 | Product owner and delivery lead, with the method engineer.                |
| G2. Domain acceptance         | Does the CIM correctly cover business concepts, rules and requirements for the selected scope?                               | Product owner and domain expert.                                          |
| G3. Architecture acceptance   | Are contracts, data, security and failure behavior reviewed, and can the PIM map to the chosen platform?                     | Solution architect.                                                       |
| G4. Generation readiness      | Are the PSM resources and configuration ready for code and infrastructure generation?                                        | Cloud platform engineer.                                                  |
| G5. Increment acceptance      | Does the implemented increment meet acceptance criteria with sufficient quality, security and operational evidence?          | Product owner, with quality and security engineers and the service owner. |
| G6. Release authorization     | Is the exact release candidate supported by current test evidence and an agreed deployment and recovery plan?                | Product owner and service owner.                                          |
| G7. Operational acceptance    | Is the running version accepted with monitoring, recovery, support and operational ownership in place?                       | Service owner, with the release engineer.                                 |
| G8. Closure acceptance        | Has the service ended safely, with data, records, access, resources and remaining obligations settled across both processes? | Sponsor and service owner, with the records and data steward.             |

## Keep the decision connected to the work

Record the scope, input and output versions, evidence, decision authority, outcome, reasons and next actions. An exception also needs an expiry and a review owner.

If an input or dependency changes in a way that affects the result, reopen that part of the review. An expired exception also requires review. Evidence from unchanged parts can be reused after assessing the change's impact.

G5 accepts an increment for possible inclusion in a release. G6 authorizes a specific release candidate. G7 accepts the running service and its support commitments. These decisions have different inputs and responsibilities.

Model conformance, semantic validation and runtime tests provide different kinds of evidence. A successful model check or a completed activity list cannot establish all the conditions for a process decision.
