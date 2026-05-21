# mde-etl-cli

CLI wrapper around `mde-etl-runner`.

## CIM to PIM

```powershell
java -jar target/mde-etl-cli-0.0.1-SNAPSHOT.jar cim-to-pim `
  --repo-root D:\Repositories\My\modless `
  --source-model path\to\input-cim.xmi `
  --target-model path\to\output-pim.xmi `
  --overwrite `
  --log-file target\etl-report.json `
  --verbose
```

## Generic ETL

```powershell
java -jar target/mde-etl-cli-0.0.1-SNAPSHOT.jar run `
  --module mde\transformations\cim-to-pim\cim-to-pim.etl `
  --source-model input.xmi `
  --target-model output.xmi `
  --source-metamodel mde\metamodels\cim\cim-combined.ecore `
  --target-metamodel mde\metamodels\pim\pim-combined.ecore `
  --source-name CIM `
  --source-aliases CIM,CIMORG,CIMDOMAIN,CIMBEHAVIOR,CIMPROCESS,CIMGOV,CIMTRANSFORM `
  --target-name PIM `
  --target-aliases PIM,DEPLOY,COMPUTE,API,CONTRACTS,DATA,INTEGRATION,WORKFLOW,EXTERNAL,POLICY,SECURITY,CONFIG,KERNEL,PIMTYPES
```
