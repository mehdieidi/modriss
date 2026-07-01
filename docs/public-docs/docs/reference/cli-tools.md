# CLI Tools

Modless includes standalone Java command-line tools for formal MDE tasks. Build a tool with Maven
from the repository root using `-pl <module> -am package`.

## Metamodel Compiler

Module: `tools/mde-cli`

Converts standalone or modular Emfatic metamodels to Ecore.

```powershell
java -jar tools/mde-cli/target/mde-cli-0.0.1-SNAPSHOT.jar `
  mde/metamodels/cim `
  --root mde/metamodels/cim/cim-root.emf `
  --output mde/metamodels/cim/cim-combined.ecore `
  --overwrite
```

Important options include `--root`, `--output`, `--overwrite`, `--verbose`, and `--log-file`.
Conversion failures return exit code `2`; unexpected failures return `1`.

## Validation CLI

Module: `tools/mde-evl-cli`

Commands:

- `cim`
- `pim`
- `psm`
- `run` for a generic EVL profile

```powershell
java -jar tools/mde-evl-cli/target/mde-evl-cli-0.0.1-SNAPSHOT.jar pim `
  --repo-root . `
  --model path/to/model.xmi `
  --log-file target/pim-validation-report.json
```

## Transformation CLI

Module: `tools/mde-etl-cli`

Provides CIM-to-PIM, PIM-to-AWS-PSM, and generic ETL execution using configured source and target
models, metamodels, aliases, and entry modules.

```powershell
java -jar tools/mde-etl-cli/target/mde-etl-cli-0.0.1-SNAPSHOT.jar cim-to-pim `
  --repo-root . `
  --source-model input-cim.xmi `
  --target-model output-pim.xmi `
  --overwrite
```

## Generation CLI

Module: `tools/mde-m2t-cli`

```powershell
java -jar tools/mde-m2t-cli/target/mde-m2t-cli-0.0.1-SNAPSHOT.jar aws-psm-to-artifacts `
  --repo-root . `
  --source-model input-aws-psm.xmi `
  --output-dir generated-project `
  --log-file target/generation-report.json `
  --verbose
```

The underlying runner packages are independent of Picocli and can also be embedded directly into
Java services and tests.

## Notation Migration

Module: `tools/notation-migrate`

Migrates legacy `*-ui-metadata.json` files to Concrete Visual Syntax (CVS) v2 JSON:

```powershell
npm run migrate:all -w @modless/notation-migrate
```

See [concrete-visual-syntax-v2.md](../../../internal/mde/concrete-visual-syntax-v2.md) for the CVS
formalism and migration workflow.
