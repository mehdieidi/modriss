# mde-m2t-cli

CLI wrapper around [`mde-m2t-runner`](../../packages/java/mde-m2t-runner/README.md) for
model-to-text generation.

Build from the repository root:

```bash
mvn -q -pl tools/mde-m2t-cli -am package
```

Jar: `tools/mde-m2t-cli/target/mde-m2t-cli-0.0.1-SNAPSHOT.jar`

## AWS PSM to artifacts

```bash
java -jar tools/mde-m2t-cli/target/mde-m2t-cli-0.0.1-SNAPSHOT.jar aws-psm-to-artifacts \
  --repo-root . \
  --source-model path/to/input-aws-psm.xmi \
  --output-dir path/to/generated-project \
  --log-file target/aws-psm-generation-report.json \
  --verbose
```
