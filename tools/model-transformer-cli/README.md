# model-transformer-cli

CLI wrapper around [`epsilon-model-transformation`](../../packages/java/epsilon-model-transformation/README.md).

Build from the repository root:

```bash
mvn -q -pl tools/model-transformer-cli -am package
```

Jar: `tools/model-transformer-cli/target/model-transformer-cli-0.0.1-SNAPSHOT.jar`

## CIM to PIM

```bash
java -jar tools/model-transformer-cli/target/model-transformer-cli-0.0.1-SNAPSHOT.jar cim-to-pim \
  --repo-root . \
  --source-model path/to/input-cim.xmi \
  --target-model path/to/output-pim.xmi \
  --overwrite \
  --log-file target/etl-report.json \
  --verbose
```

## Generic ETL

```bash
java -jar tools/model-transformer-cli/target/model-transformer-cli-0.0.1-SNAPSHOT.jar run \
  --module methodology/modeling-framework/transformations/cim-to-pim/cim-to-pim.etl \
  --source-model input.xmi \
  --target-model output.xmi \
  --source-metamodel methodology/modeling-framework/metamodels/cim/cim-combined.ecore \
  --target-metamodel methodology/modeling-framework/metamodels/pim/pim-combined.ecore \
  --source-name CIM \
  --source-aliases CIM,CIMORG,CIMDOMAIN,CIMBEHAVIOR,CIMPROCESS,CIMGOV,CIMTRANSFORM \
  --target-name PIM \
  --target-aliases PIM,DEPLOY,COMPUTE,API,CONTRACTS,DATA,INTEGRATION,WORKFLOW,EXTERNAL,POLICY,SECURITY,CONFIG,KERNEL,PIMTYPES
```

See also [`methodology/modeling-framework/transformations/pim-to-awspsm/README.md`](../../methodology/modeling-framework/transformations/pim-to-awspsm/README.md)
for the PIM→PSM profile.
