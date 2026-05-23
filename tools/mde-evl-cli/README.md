# mde-evl-cli

Command-line runner for Eclipse Epsilon EVL validations. The CLI uses the reusable
`mde-evl-validator` library and is intentionally thin: it handles arguments, profile defaults,
console output, exit codes, and JSON reports.

## Commands

- `run` - generic EVL runner for one read-only file-backed EMF model.
- `cim` - repository CIM semantic validation profile.
- `pim` - repository PIM semantic validation profile.
- `psm` - repository AWS PSM semantic validation profile.

## Exit Codes

- `0` - EVL executed successfully. Constraint violations may still be present unless a fail option
  is used.
- `2` - EVL execution failed, such as parse, model loading, or runtime errors.
- `3` - EVL executed successfully, but violations were found and a fail option requested a non-zero
  result.

## Examples

```powershell
java -jar tools/mde-evl-cli/target/mde-evl-cli-0.0.1-SNAPSHOT.jar pim `
  --repo-root . `
  --model path/to/model.xmi `
  --log-file target/pim-validation-report.json
```

```powershell
java -jar tools/mde-evl-cli/target/mde-evl-cli-0.0.1-SNAPSHOT.jar run `
  --evl-root mde/validation/pim/pim-semantic-validation.evl `
  --model path/to/model.xmi `
  --metamodel mde/metamodels/pim/pim-combined.ecore `
  --model-name PIM `
  --aliases KERNEL `
  --fail-on-mandatory-violations
```

Use `--no-structural-validation` for diagnostic EVL development runs against intentionally sparse
or synthetic models. Production validation should keep structural validation enabled.
