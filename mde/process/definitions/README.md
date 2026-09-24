# Executable Process Definitions

This directory contains the generated JSON contracts consumed by the MODRISS
process API, coverage tooling, documentation generators, and method-package
builder. The five definitions cover the integrated lifecycle plus the CIM,
PIM, PSM, and artifact-readiness child processes.

Do not edit the generated JSON files directly. Their authoritative sources are
under [`../tools/lib/`](../tools/lib/). Rebuild them with:

```bash
node mde/process/tools/build-process-definitions.mjs
```

The reusable method library and formal SPEM representation are kept separately
under [`../method/`](../method/).
