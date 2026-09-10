# PIM validation: Deployment

Deployment rules check that services, deployment units, environments, and implementation profiles form a realizable architecture. They connect ownership to membership, production intent to approval, and code-generation settings to a coherent language/runtime toolchain.

Source profile: `mde/validation/pim/rules/deployment.evl`.

## Reading these rules

Each entry preserves the actual EVL guard and check. Read the guard as the applicability boundary, not as part of the invariant: when it is false, the rule is intentionally skipped. The diagnostic is the runtime-facing message emitted by EVL; its final sentence usually contains the repository's recommended repair.

---

## `ServiceResponsibilityRequired`

**Context:** `PIM!ServerlessService`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/pim/rules/deployment.evl:8`

### Why this rule exists

Checks that service responsibility required. The serverless service element owns the evidence for this decision, including responsibility, display name. At this level, services and deployment units can be assembled into environments with a coherent implementation toolchain; allowing the model through without that evidence would move an unresolved choice into generated infrastructure. The concrete failure this rule prevents is: [PIM-SVC-001] Service ' ' has no responsibility.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.responsibility.hasText()
```

This rule reads: `responsibility`, `displayName`.

### Diagnostic and repair

> [PIM-SVC-001] Service ' ' has no responsibility. Fix: describe the business capability, bounded context, process step or architectural role this service owns.

**How to fix it:**

describe the business capability, bounded context, process step or architectural role this service owns.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `ServiceOwnsAtLeastOneElement`

**Context:** `PIM!ServerlessService`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/pim/rules/deployment.evl:14`

### Why this rule exists

Checks that service owns at least one element. The serverless service element owns the evidence for this decision, including owned elements, display name. At this level, services and deployment units can be assembled into environments with a coherent implementation toolchain; allowing the model through without that evidence would move an unresolved choice into generated infrastructure. The concrete failure this rule prevents is: [PIM-SVC-002] Service ' ' does not own any PIM elements.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.ownedElements().notEmpty()
```

This rule reads: `ownedElements`, `displayName`.

### Diagnostic and repair

> [PIM-SVC-002] Service ' ' does not own any PIM elements. Fix: attach owned functions, APIs, channels, stores, workflows or adapters, or remove the empty service.

**How to fix it:**

attach owned functions, APIs, channels, stores, workflows or adapters, or remove the empty service.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `OwnedElementsHaveMembershipRecords`

**Context:** `PIM!ServerlessService`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/pim/rules/deployment.evl:20`

### Why this rule exists

Checks that owned elements have membership records. The serverless service element owns the evidence for this decision, including owned elements, has membership for, display name. At this level, services and deployment units can be assembled into environments with a coherent implementation toolchain; allowing the model through without that evidence would move an unresolved choice into generated infrastructure. The concrete failure this rule prevents is: [PIM-SVC-002A] Service ' ' has owned elements without ServiceElementMembership records.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.ownedElements().forAll(elementItem | self.hasMembershipFor(elementItem))
```

This rule reads: `ownedElements`, `hasMembershipFor`, `displayName`.

### Diagnostic and repair

> [PIM-SVC-002A] Service ' ' has owned elements without ServiceElementMembership records. Fix: generate one membership for every contained deployable.

**How to fix it:**

generate one membership for every contained deployable.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `ExternallyExposedServiceNeedsApi`

**Context:** `PIM!ServerlessService`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/pim/rules/deployment.evl:26`

### Why this rule exists

Checks that externally exposed service needs api. The serverless service element owns the evidence for this decision, including externally exposed, apis, display name. At this level, services and deployment units can be assembled into environments with a coherent implementation toolchain; allowing the model through without that evidence would move an unresolved choice into generated infrastructure. The concrete failure this rule prevents is: [PIM-SVC-003] Externally exposed service ' ' owns no API.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : self.externallyExposed.isTrue()
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.apis.notEmpty()
```

This rule reads: `externallyExposed`, `apis`, `displayName`.

### Diagnostic and repair

> [PIM-SVC-003] Externally exposed service ' ' owns no API. Fix: add an Api/ApiRoute model or mark externallyExposed as false if exposure is indirect.

**How to fix it:**

add an Api/ApiRoute model or mark externallyExposed as false if exposure is indirect.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `DataOwningServiceNeedsStore`

**Context:** `PIM!ServerlessService`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/pim/rules/deployment.evl:33`

### Why this rule exists

Checks that data owning service needs store. The serverless service element owns the evidence for this decision, including owns data, stores, display name. At this level, services and deployment units can be assembled into environments with a coherent implementation toolchain; allowing the model through without that evidence would move an unresolved choice into generated infrastructure. The concrete failure this rule prevents is: [PIM-SVC-004] Data-owning service ' ' owns no storage element.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : self.ownsData.isTrue()
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.stores.notEmpty()
```

This rule reads: `ownsData`, `stores`, `displayName`.

### Diagnostic and repair

> [PIM-SVC-004] Data-owning service ' ' owns no storage element. Fix: attach a DataStore or ObjectStore that represents the owned data boundary.

**How to fix it:**

attach a DataStore or ObjectStore that represents the owned data boundary.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `ServiceOwnerTeamRecommended`

**Context:** `PIM!ServerlessService`  
**Classification:** Advisory critique (warning)  
**Source:** `mde/validation/pim/rules/deployment.evl:40`

### Why this rule exists

Advises that service owner team recommended. This is a review signal about owner team, display name, not a cosmetic naming preference. In this part of the model, services and deployment units can be assembled into environments with a coherent implementation toolchain; the warning makes a decision visible while it can still be discussed and changed. The concrete gap is: [PIM-SVC-005] Service ' ' has no ownerTeam. Suggested fix: set the owning team so generated documentation and production readiness reports have an accountable owner. If it is left unexplained, a later transformation, generator, or reviewer has to invent an assumption.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.ownerTeam.hasText()
```

This rule reads: `ownerTeam`, `displayName`.

### Diagnostic and repair

> [PIM-SVC-005] Service ' ' has no ownerTeam. Suggested fix: set the owning team so generated documentation and production readiness reports have an accountable owner.

**How to fix it:**

This is advisory rather than a structural blocker. Decide whether the modeled situation genuinely needs the recommendation; if it does, change the referenced model features so the check evaluates to true and record the rationale when the omission is intentional.

A critique does not necessarily make the model invalid. It is a deliberate quality/readiness signal; disposition it by improving the model, documenting why it is acceptable, or carrying the decision into the review record.

---

## `DeploymentUnitContainsElements`

**Context:** `PIM!DeploymentUnit`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/pim/rules/deployment.evl:50`

### Why this rule exists

Checks that deployment unit contains elements. The deployment unit element owns the evidence for this decision, including contains, display name. At this level, services and deployment units can be assembled into environments with a coherent implementation toolchain; allowing the model through without that evidence would move an unresolved choice into generated infrastructure. The concrete failure this rule prevents is: [PIM-DU-001] Deployment unit ' ' contains no deployable elements.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.contains.notEmpty()
```

This rule reads: `contains`, `displayName`.

### Diagnostic and repair

> [PIM-DU-001] Deployment unit ' ' contains no deployable elements. Fix: add the functions, APIs, channels, workflows, adapters, stores, schedules or configuration that should deploy together.

**How to fix it:**

add the functions, APIs, channels, workflows, adapters, stores, schedules or configuration that should deploy together.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `DeploymentUnitTargetsEnvironment`

**Context:** `PIM!DeploymentUnit`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/pim/rules/deployment.evl:56`

### Why this rule exists

Checks that deployment unit targets environment. The deployment unit element owns the evidence for this decision, including target environments, display name. At this level, services and deployment units can be assembled into environments with a coherent implementation toolchain; allowing the model through without that evidence would move an unresolved choice into generated infrastructure. The concrete failure this rule prevents is: [PIM-DU-002] Deployment unit ' ' has no target environments.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.targetEnvironments.notEmpty()
```

This rule reads: `targetEnvironments`, `displayName`.

### Diagnostic and repair

> [PIM-DU-002] Deployment unit ' ' has no target environments. Fix: reference DEV/TEST/STAGING/PROD or another Environment where the unit can be generated/deployed.

**How to fix it:**

reference DEV/TEST/STAGING/PROD or another Environment where the unit can be generated/deployed.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `IndependentlyDeployableUnitNeedsReleaseStrategy`

**Context:** `PIM!DeploymentUnit`  
**Classification:** Advisory critique (warning)  
**Source:** `mde/validation/pim/rules/deployment.evl:62`

### Why this rule exists

Advises that independently deployable unit needs release strategy. This is a review signal about independently deployable, release strategy, versioning strategy, display name, not a cosmetic naming preference. In this part of the model, services and deployment units can be assembled into environments with a coherent implementation toolchain; the warning makes a decision visible while it can still be discussed and changed. The concrete gap is: [PIM-DU-003] Independently deployable unit ' ' lacks release or versioning strategy. Suggested fix: define how this unit is versioned, promoted and rolled back independently. If it is left unexplained, a later transformation, generator, or reviewer has to invent an assumption.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : self.independentlyDeployable.isTrue()
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.releaseStrategy.hasText() and self.versioningStrategy.hasText()
```

This rule reads: `independentlyDeployable`, `releaseStrategy`, `versioningStrategy`, `displayName`.

### Diagnostic and repair

> [PIM-DU-003] Independently deployable unit ' ' lacks release or versioning strategy. Suggested fix: define how this unit is versioned, promoted and rolled back independently.

**How to fix it:**

This is advisory rather than a structural blocker. Decide whether the modeled situation genuinely needs the recommendation; if it does, change the referenced model features so the check evaluates to true and record the rationale when the omission is intentional.

A critique does not necessarily make the model invalid. It is a deliberate quality/readiness signal; disposition it by improving the model, documenting why it is acceptable, or carrying the decision into the review record.

---

## `ProdEnvironmentIsProductionLike`

**Context:** `PIM!Environment`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/pim/rules/deployment.evl:73`

### Why this rule exists

Checks that prod environment is production like. The environment element owns the evidence for this decision, including environment class, production like, display name. At this level, services and deployment units can be assembled into environments with a coherent implementation toolchain; allowing the model through without that evidence would move an unresolved choice into generated infrastructure. The concrete failure this rule prevents is: [PIM-ENV-001] PROD environment ' ' is not marked productionLike.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : self.environmentClass.enumIs("PROD")
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.productionLike.isTrue()
```

This rule reads: `environmentClass`, `productionLike`, `displayName`.

### Diagnostic and repair

> [PIM-ENV-001] PROD environment ' ' is not marked productionLike. Fix: set productionLike to true or choose a non-PROD environmentClass.

**How to fix it:**

set productionLike to true or choose a non-PROD environmentClass.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `ProdEnvironmentShouldRequireApproval`

**Context:** `PIM!Environment`  
**Classification:** Advisory critique (warning)  
**Source:** `mde/validation/pim/rules/deployment.evl:80`

### Why this rule exists

Advises that prod environment should require approval. This is a review signal about environment class, requires approval, display name, not a cosmetic naming preference. In this part of the model, services and deployment units can be assembled into environments with a coherent implementation toolchain; the warning makes a decision visible while it can still be discussed and changed. The concrete gap is: [PIM-ENV-002] PROD environment ' ' does not require approval. Suggested fix: set requiresApproval to true so generated release workflows include a production approval gate. If it is left unexplained, a later transformation, generator, or reviewer has to invent an assumption.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : self.environmentClass.enumIs("PROD")
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.requiresApproval.isTrue()
```

This rule reads: `environmentClass`, `requiresApproval`, `displayName`.

### Diagnostic and repair

> [PIM-ENV-002] PROD environment ' ' does not require approval. Suggested fix: set requiresApproval to true so generated release workflows include a production approval gate.

**How to fix it:**

This is advisory rather than a structural blocker. Decide whether the modeled situation genuinely needs the recommendation; if it does, change the referenced model features so the check evaluates to true and record the rationale when the omission is intentional.

A critique does not necessarily make the model invalid. It is a deliberate quality/readiness signal; disposition it by improving the model, documenting why it is acceptable, or carrying the decision into the review record.

---

## `ProfileHasGenerationBasics`

**Context:** `PIM!ImplementationProfile`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/pim/rules/deployment.evl:91`

### Why this rule exists

Checks that profile has generation basics. The implementation profile element owns the evidence for this decision, including source layout, build command, display name. At this level, services and deployment units can be assembled into environments with a coherent implementation toolchain; allowing the model through without that evidence would move an unresolved choice into generated infrastructure. The concrete failure this rule prevents is: [PIM-PROFILE-001] Implementation profile ' ' lacks sourceLayout or buildCommand.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.sourceLayout.hasText() and self.buildCommand.hasText()
```

This rule reads: `sourceLayout`, `buildCommand`, `displayName`.

### Diagnostic and repair

> [PIM-PROFILE-001] Implementation profile ' ' lacks sourceLayout or buildCommand. Fix: specify where generated code should be placed and how it is built.

**How to fix it:**

specify where generated code should be placed and how it is built.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `PackageManagerMatchesRuntimeLanguage`

**Context:** `PIM!ImplementationProfile`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/pim/rules/deployment.evl:97`

### Why this rule exists

Checks that package manager matches runtime language. The implementation profile element owns the evidence for this decision, including package manager fits language, display name, package manager, primary language. At this level, services and deployment units can be assembled into environments with a coherent implementation toolchain; allowing the model through without that evidence would move an unresolved choice into generated infrastructure. The concrete failure this rule prevents is: [PIM-PROFILE-002] Implementation profile ' ' uses package manager ' ' with runtime language ' '.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.packageManagerFitsLanguage()
```

This rule reads: `packageManagerFitsLanguage`, `displayName`, `packageManager`, `primaryLanguage`.

### Diagnostic and repair

> [PIM-PROFILE-002] Implementation profile ' ' uses package manager ' ' with runtime language ' '. Fix: choose a compatible package manager, for example NPM/PNPM/YARN for TypeScript/JavaScript, PIP/POETRY for Python, MAVEN/GRADLE for Java, DOTNET for C#, GO_MOD for Go, or CARGO for Rust.

**How to fix it:**

choose a compatible package manager, for example NPM/PNPM/YARN for TypeScript/JavaScript, PIP/POETRY for Python, MAVEN/GRADLE for Java, DOTNET for C#, GO_MOD for Go, or CARGO for Rust.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---

## `RuntimeValidationShouldGenerateTypedContracts`

**Context:** `PIM!ImplementationProfile`  
**Classification:** Advisory critique (warning)  
**Source:** `mde/validation/pim/rules/deployment.evl:103`

### Why this rule exists

Advises that runtime validation should generate typed contracts. This is a review signal about generate runtime validation, generate typed contracts, not a cosmetic naming preference. In this part of the model, services and deployment units can be assembled into environments with a coherent implementation toolchain; the warning makes a decision visible while it can still be discussed and changed. The concrete gap is: [PIM-PROFILE-003] Runtime validation is requested without typed contract generation. Suggested fix: enable generateTypedContracts so generated validators and handlers share the same schema-derived types. If it is left unexplained, a later transformation, generator, or reviewer has to invent an assumption.

### When it applies

The rule is considered only when the guard below is true. A false guard is intentional: it means this invariant is not relevant to the current variant or modeling situation.

```evl
guard : self.generateRuntimeValidation.isTrue()
```

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.generateTypedContracts.isTrue()
```

This rule reads: `generateRuntimeValidation`, `generateTypedContracts`.

### Diagnostic and repair

> [PIM-PROFILE-003] Runtime validation is requested without typed contract generation. Suggested fix: enable generateTypedContracts so generated validators and handlers share the same schema-derived types.

**How to fix it:**

This is advisory rather than a structural blocker. Decide whether the modeled situation genuinely needs the recommendation; if it does, change the referenced model features so the check evaluates to true and record the rationale when the omission is intentional.

A critique does not necessarily make the model invalid. It is a deliberate quality/readiness signal; disposition it by improving the model, documenting why it is acceptable, or carrying the decision into the review record.

---

## `MembershipMatchesServiceOwnership`

**Context:** `PIM!ServiceElementMembership`  
**Classification:** Mandatory semantic constraint (error)  
**Source:** `mde/validation/pim/rules/deployment.evl:113`

### Why this rule exists

Checks that membership matches service ownership. The service element membership element owns the evidence for this decision, including service, element. At this level, services and deployment units can be assembled into environments with a coherent implementation toolchain; allowing the model through without that evidence would move an unresolved choice into generated infrastructure. The concrete failure this rule prevents is: [PIM-SVC-006] ServiceElementMembership does not match the service containment parent.

### When it applies

The rule has no guard, so it applies to every instance of this context in the validated model.

There is no guard expression; every instance of the context is checked.

### What counts as valid

The model passes when the following EVL check evaluates to `true`:

```evl
check : self.service.isDefined() and self.element.isDefined() and self.service.ownedElements().includes(self.element)
```

This rule reads: `service`, `element`.

### Diagnostic and repair

> [PIM-SVC-006] ServiceElementMembership does not match the service containment parent. Fix: synchronize membership metadata with ServerlessService val containment.

**How to fix it:**

synchronize membership metadata with ServerlessService val containment.

A constraint represents a mandatory semantic invariant for this validation profile. Transformation or deployment work should not treat the model as semantically ready while this violation remains unresolved.

---
