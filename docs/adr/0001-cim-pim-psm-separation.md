# ADR 0001: CIM/PIM/PSM Modeling Separation

- **Status:** Accepted
- **Date:** 2026-03-19
- **Deciders:** Modless core team

## Context

Modless targets AWS serverless applications through model-driven engineering. Stakeholders reason
about business intent, platform-independent architecture, and deployment-specific infrastructure at
different levels of abstraction. A single undifferentiated model would mix concerns and make
validation, transformation, and code generation brittle.

## Decision

Adopt a strict three-level modeling separation:

1. **CIM** — computation-independent business and capability models
2. **PIM** — platform-independent application architecture
3. **PSM** — AWS serverless platform-specific models

Each level has its own metamodel (Emfatic/Ecore), EVL validation rules, UI metadata, and storage
namespace. Transformations are explicit: CIM→PIM, PIM→PSM, PSM→artifacts.

## Consequences

### Positive

- Clear ownership boundaries for validation and generation
- Transformations can evolve independently per level
- Generated artifacts trace back to explicit PSM decisions
- Assistant context can be scoped to the active modeling level

### Negative

- Users must understand three workspaces instead of one canvas
- Cross-level consistency requires pipeline discipline and regression tests
- Metamodel or rule changes often require synchronized updates across levels

## Alternatives considered

- **Single unified metamodel** — rejected because it obscures platform decisions and complicates EVL
- **Two-level CIM/PSM only** — rejected because PIM captures reusable architecture before AWS binding
