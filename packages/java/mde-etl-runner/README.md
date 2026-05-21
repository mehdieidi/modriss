# mde-etl-runner

Reusable Java API for executing Eclipse Epsilon ETL transformations against EMF models.

The package is intentionally independent from any CLI framework so it can be used from backend
services, tests, or command-line tools.

## Main Types

- `EpsilonEtlExecutor` - executes ETL modules.
- `EtlExecutionRequest` - immutable execution request.
- `EtlModelConfiguration` - source/target EMF model configuration.
- `EtlExecutionReport` - structured result containing status, timing, diagnostics, and captured
  output.
- `EtlExecutionException` - thrown on failed execution and always carries an `EtlExecutionReport`.
- `CimToPimDefaults` - convenience profile for this repository's CIM-to-PIM transformation.

## Diagnostics

Failures are reported by phase:

- `VALIDATION`
- `PARSE`
- `MODEL_LOADING`
- `EXECUTION`
- `MODEL_STORING`
- `UNEXPECTED`

Each diagnostic includes location where available, reason, what went wrong, how to fix it, and
exception type.

## Example

```java
EtlExecutionRequest request = CimToPimDefaults.request(
        repositoryRoot,
        sourceCimModel,
        targetPimModel,
        true,
        true);

try{
EtlExecutionReport report = new EpsilonEtlExecutor().execute(request);
}catch(
EtlExecutionException ex){
EtlExecutionReport report = ex.getReport();
}
```
