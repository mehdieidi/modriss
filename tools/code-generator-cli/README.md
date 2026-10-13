# code-generator-cli

CLI wrapper around [`mde-m2t-runner`](../../packages/java/mde-m2t-runner/README.md) for
model-to-text generation.

Build from the repository root:

```bash
mvn -q -pl tools/code-generator-cli -am package
```

Jar: `tools/code-generator-cli/target/code-generator-cli-0.0.1-SNAPSHOT.jar`

## PSM to artifacts

```bash
java -jar tools/code-generator-cli/target/code-generator-cli-0.0.1-SNAPSHOT.jar psm-to-artifacts \
  --repo-root . \
  --source-model path/to/input-psm.xmi \
  --output-dir path/to/generated-project \
  --log-file target/psm-generation-report.json \
  --verbose
```
