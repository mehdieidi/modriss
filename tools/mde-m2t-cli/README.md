# mde-m2t-cli

CLI wrapper around `mde-m2t-runner` for model-to-text generation.

## AWS PSM to Artifacts

```powershell
java -jar target/mde-m2t-cli-0.0.1-SNAPSHOT.jar aws-psm-to-artifacts `
  --repo-root D:\Repositories\My\modless `
  --source-model path\to\input-aws-psm.xmi `
  --output-dir path\to\generated-project `
  --log-file target\aws-psm-generation-report.json `
  --verbose
```
