# CIM-to-PIM transformation review

Date: 2026-09-05

## Scenario and execution

The browser-facing REST workflow was exercised with the Climate Relief Grants domain:

1. Registered a modeler session and created a project.
2. Imported the checked-in `climate-relief-grants.cim.xmi` through `POST /api/cim/import`.
3. Created the CIM model through `POST /api/cim`.
4. Ran structural and semantic CIM validation.
5. Submitted `POST /api/transformations/cim-to-pim` and polled the asynchronous job.
6. Inspected the stored PIM model, trace links, generated mappings, and both PIM validation modes.

The CIM and PIM models were structurally and semantically valid. The transformation completed with
671 trace links, and every inspected source element had trace coverage. The defects below are
therefore semantic mapping defects that the current validators do not detect.

## Finding CIM-PIM-001: relationship requiredness uses the wrong multiplicity end

`DomainRelationship2SchemaReference` creates a field on the relationship source schema pointing
to the target. Its `required` value was derived from `sourceMultiplicity.lowerBound`. In the
metamodel, however, the multiplicity of the referenced target at that field is
`targetMultiplicity`. The same error was repeated in `AttachRelationshipDataFields`.

Observed example:

- `Application contains reviews`: source multiplicity `1..1`, target multiplicity `0..*`.
- Generated PIM schema field `GrantApplication.reviewCases`: `required=true`, `array=true`.
- Correct result: `required=false`, `array=true`.

This makes an optional relationship mandatory in generated contracts and storage metadata.

### Fix

Use `targetMultiplicity.lowerBound >= 1` when computing `required` for both the contract
`SchemaField` and the storage `DataField`.

## Finding CIM-PIM-002: storage relationship fields lose collection cardinality

`AttachRelationshipDataFields` always emitted relationship storage fields with
`fieldType=OBJECT`. The PIM `DataField` metamodel has no separate `array` attribute, so a
many-valued relationship must use `fieldType=ARRAY` to preserve its collection cardinality.

Observed examples included `reviewCases`, `appeals`, `disbursements`, and `supplierInvoices`, all
of which were generated as scalar `OBJECT` storage fields despite `targetMultiplicity=0..*` or
`1..*`.

### Fix

Set relationship storage `fieldType` to `ARRAY` when `targetMultiplicity.isMany()`; retain
`OBJECT` for single-valued relationships. Keep the existing schema field’s object reference and
array flag so contract-level element typing remains available.

## Regression coverage

Add focused transformation assertions for optional one-to-many, required one-to-many, and
required one-to-one relationships. The assertions must cover both the contract schema field and
the storage data field, including requiredness and collection cardinality.

## Resolution

Implemented the ETL fixes in `mde/transformations/cim-to-pim/domain-data.etl`, added the focused
regression test, and synchronized the checked-in PIM fixtures. The live frontend-facing REST
workflow was rerun after the fix; the regenerated PIM model passed structural and semantic
validation and produced the corrected relationship projections.

Verification:

- `CimToPimEtlRegressionTest`: 8 tests, 0 failures, 0 errors.
- All synchronized PIM XMI fixtures parse successfully.
- `git diff --check` completed without diagnostics.
