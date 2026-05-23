# mde-evl-validator

Reusable Java API for executing Eclipse Epsilon EVL validations against EMF models.

The package is library-oriented and intentionally independent from any backend or CLI framework. It
can validate file-backed XMI models or in-memory EMF `Resource` instances and returns structured
reports for both execution problems and semantic constraint violations.

## Main Types

- `EpsilonEvlValidator` - executes EVL modules and returns an `EvlValidationReport`.
- `EvlValidationRequest` - immutable validation request.
- `FileEvlModelConfiguration` - read-only EMF model loaded from model and Ecore files.
- `ResourceEvlModelConfiguration` - read-only in-memory EMF `Resource` plus `EPackage`s.
- `EvlValidationReport` - status, timing, module reports, diagnostics, captured output, and
  violations.
- `EvlConstraintViolation` - a failed EVL `constraint` or `critique` with rule, location, message,
  element reference, attributes, extras, and fix suggestions where EVL provides them.
- `EvlValidationException` - thrown for parse, model loading, runtime, discovery, or unexpected
  execution failures. The exception always carries an `EvlValidationReport`.

## EVL Roots

`evlRoot` can be:

- a single `.evl` entry module, or
- a directory whose direct child `.evl` files are entry modules.

Directory discovery intentionally does not execute nested `rules/` or `lib/` files directly. Those
files should be imported by an entry module so relative EVL imports continue to work normally.
Explicit `moduleFiles` can be supplied in `EvlValidationRequest` when a caller needs tighter
control.

## Diagnostics

Execution failures are reported by phase:

- `REQUEST_VALIDATION`
- `MODULE_DISCOVERY`
- `PARSE`
- `MODEL_LOADING`
- `EXECUTION`
- `UNEXPECTED`

Each diagnostic includes file, line, column, reason, what went wrong, suggested fix, and exception
type when available.

## Constraint Kinds

EVL severities are mapped to domain-friendly kinds:

- EVL `constraint` -> `MANDATORY`
- EVL `critique` -> `OPTIONAL`

Violations keep the EVL message verbatim. In this repository, messages are expected to include the
human-facing reason and fix guidance, for example `[PIM-API-001] ... Fix: ...`.

## Example

```java
EvlValidationRequest request = EvlValidationRequest.forRoot(
        repositoryRoot.resolve("mde/validation/pim"),
        List.of(FileEvlModelConfiguration.readOnly(
                "PIM",
                List.of("KERNEL"),
                repositoryRoot.resolve("model.xmi"),
                List.of(
                        repositoryRoot.resolve("mde/metamodels/pim.ecore"),
                        repositoryRoot.resolve("mde/metamodels/kernel.ecore")))),
        true);

try{
EvlValidationReport report = new EpsilonEvlValidator().validate(request);
List<EvlConstraintViolation> violations = report.violations();
}catch(
EvlValidationException ex){
EvlValidationReport failedReport = ex.getReport();
}
```
