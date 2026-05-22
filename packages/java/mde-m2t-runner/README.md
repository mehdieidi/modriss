# mde-m2t-runner

Reusable Java API for executing Eclipse Epsilon EGX/EGL model-to-text generation against EMF
models.

This package is intentionally separate from `mde-etl-runner`: ETL is model-to-model, while EGX/EGL
generation is model-to-text.

## Main Types

- `EpsilonEgxGenerator` - executes EGX modules and EGL templates.
- `EgxGenerationRequest` - immutable generation request.
- `GenerationModelConfiguration` - source EMF model configuration.
- `EgxGenerationReport` - structured result with timing, diagnostics, generated files, and captured
  output.
- `EgxGenerationException` - thrown on failed generation and always carries a report.
- `AwsPsmToArtifactsDefaults` - convenience profile for this repository's AWS PSM-to-artifacts
  generator.
