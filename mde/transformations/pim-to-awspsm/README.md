# PIM to AWS PSM ETL

Entry point:

```bash
java -jar tools/mde-etl-cli/target/mde-etl-cli-0.0.1-SNAPSHOT.jar run \
  --module mde/transformations/pim-to-awspsm/pim-to-awspsm.etl \
  --source-model path/to/input-pim.xmi \
  --target-model path/to/output-awspsm.xmi \
  --source-metamodel mde/metamodels/pim/pim-combined.ecore \
  --target-metamodel mde/metamodels/psm/psm-combined.ecore \
  --source-name PIM \
  --source-aliases PIM,DEPLOYMENT,COMPUTE,API,CONTRACTS,DATA,INTEGRATION,WORKFLOW,EXTERNAL,POLICY,SECURITY,CONFIG,PIMTYPES \
  --target-name AWSPSM \
  --target-aliases AWSPSM,AWSPSMCORE,AWSPSMCOMPUTE,AWSPSMAPI,AWSPSMSTORAGE,AWSPSMMESSAGING,AWSPSMEVENTS,AWSPSMWORKFLOW,AWSPSMSECURITY,AWSPSMIDENTITY,AWSPSMOBSERVABILITY,AWSPSMINTEGRATIONS,AWSPSMNETWORKING,AWSPSMENUMS,KERNEL \
  --overwrite \
  --verbose
```

Keep `KERNEL` on the target aliases only. The source and target combined Ecore files both contain
the shared kernel package, and assigning the same alias to both sides creates an ambiguous Epsilon
model group when the transformation creates target-side trace, readiness, and structured-document
elements.

## Workflow state mapping

The transformation represents ASL state kinds through concrete EClasses, matching the CIM and PIM
step-class pattern. `StartStep` and `PassStep` become `AslPassState`; `TaskStep`, `ChoiceStep`,
`WaitStep`, `ParallelStep`, `MapStep`, `SuccessEndStep`, and `FailureEndStep` become their
corresponding `Asl*State` classifiers. A task without an executable target deliberately degrades to
`AslPassState` and emits the existing `WORKFLOW_TASK_TARGET_REQUIRED` manual decision.

`AslState` is abstract. Transformation and generation code must determine a state's ASL `Type`
from its concrete EClass, not from an enum-valued feature.
