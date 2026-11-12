<!-- Process documentation is maintained by hand. -->

# Implementation and release

This work turns the accepted cloud design into tested software and then prepares a specific version for use. The team completes an increment before deciding which accepted increments will form a release.

## Generate and record the project

The cloud platform engineer selects the accepted PSM version, generator, templates and environment profile. Epsilon's EGX coordinates generation, and EGL templates produce the files.

In the built-in AWS path, outputs include infrastructure definitions, Go code scaffolding, contracts, tests, automation and documentation according to the modeled elements. Record the origin of the output and any manual work still required.

The application engineer checks the generated structure against the increment scope. The release engineer records this initial project in version control with its model and generator versions. Resource and permission problems return to cloud design; template defects go to the generator owner.

## Complete application code and delivery configuration

The application engineer implements business logic, adapters and extension points. Respect the ownership of generated files: some belong to the generator, some have protected regions and some are created only on the first generation. Reconcile manual infrastructure changes with the maintained model.

The cloud platform engineer supplies environment values and references. The security engineer reviews secrets, encryption and permissions, including the separation of deployment permissions from application runtime permissions.

The release engineer prepares a repeatable build, package, check and deployment pipeline. Record tool and dependency versions that affect the result.

## Test and accept the increment at G5

The application and quality engineers run the unit, contract and component tests needed for the increment. Results identify the tested code and inputs. Fix defects and integrate the changes in the shared repository.

At G5, the product owner reviews acceptance criteria with quality, security and service responsibilities represented. The review considers build and test results, security findings, cost, operational readiness and open manual actions. It can accept, rework or defer the increment.

Successful generation or one working function is insufficient evidence that the business behavior is correct. An accepted increment is ready for selection in a release.

## Prepare a release

The release engineer combines one or more accepted increments and checks that their versions and dependencies are compatible. The release candidate identifies the exact code, configuration, data schema and dependencies being reviewed.

Build and deploy that candidate in a suitable non-production environment. The quality engineer and relevant specialists run integration and acceptance tests, plus security, performance and recovery tests according to risk. Check logs, metrics, traces and alerts. Record the environment's limits so reviewers can judge what the results establish.

Return each finding to its responsible source. Application logic defects go to code; resource or contract defects go to the model that owns the decision. Repeat the checks affected by the correction. Reusing earlier evidence requires an impact review. An accepted exception records its authority, reason, expiry and review owner.

## Authorize the release at G6

Prepare the deployment sequence, stop conditions, recovery plan, stakeholder communication and response coverage. The product owner and service owner review the exact candidate and current evidence, then authorize or reject release.

Before any users are exposed to the new version, identify the deployment operator and service owner responsible for responding. For the first release, recovery cannot assume a previous version exists. It must also address stopping new work and handling effects already created in external systems.

## Deploy and hand over at G7

The release engineer promotes the authorized candidate through the agreed environments. Compare each step with the acceptance limits. Stop or recover when those limits are exceeded.

The service owner checks dashboards, alerts, runbooks, recovery access and support responsibilities. G7 accepts the running version and its operational commitments. Until then, the deployment operator and named service owner retain response responsibility.

The first G7 acceptance starts the [Operations and Maintenance process](../operations-maintenance.md). Later handovers update the running service while that process continues.

## Review the release outcome

The product owner compares business results, service behavior and cost per unit of value with the release assumptions. Gaps become future work and inform priorities and cost estimates.

Keep the candidate, test evidence, deployment record, recovery plan and handover record connected. A change to an input that affects those results reopens the relevant review. The next increment and release continue within Phase 1.
