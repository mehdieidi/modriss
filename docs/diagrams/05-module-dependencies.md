# Dependency Architecture

## Maven Module Dependencies

```mermaid
flowchart TB
    backend["apps/backend"]
    kernel["platform-shared-types"]
    storageApi["platform-storage-api"]
    identity["platform-user-authentication"]
    project["platform-project-management"]
    modeling["platform-modeling-infrastructure"]
    model["platform-model-workspace"]
    artifact["platform-artifact-management"]
    transform["platform-mde-pipeline"]
    assistant["platform-modeling-assistant"]
    export["platform-project-export"]
    storage["platform-storage-postgres"]
    etl["epsilon-model-transformation"]
    evl["epsilon-model-validation"]
    m2t["epsilon-text-generation"]
    modelTransformationCli["tools/model-transformer-cli"]
    modelValidationCli["tools/model-validator-cli"]
    codeGeneratorCli["tools/code-generator-cli"]

    storageApi --> kernel
    identity --> storageApi
    project --> identity
    modeling --> kernel
    model --> project
    model --> modeling
    model --> evl
    artifact --> project
    transform --> model
    transform --> artifact
    transform --> etl
    transform --> m2t
    assistant --> project
    assistant --> model
    assistant --> modeling
    export --> project
    export --> model
    export --> artifact
    export --> transform

    storage --> storageApi
    storage --> identity
    storage --> project
    storage --> model
    storage --> artifact
    storage --> transform

    backend --> storage
    backend --> identity
    backend --> project
    backend --> model
    backend --> modeling
    backend --> artifact
    backend --> transform
    backend --> assistant
    backend --> export

    etl --> evl
    modelTransformationCli --> etl
    modelValidationCli --> evl
    codeGeneratorCli --> m2t
```

## External Library Dependencies

```mermaid
flowchart LR
    backend["Backend"] --> spring["Spring Boot MVC, JDBC, Validation, Actuator"]
    backend --> springai["Spring AI chat, OpenAI, Google GenAI, JDBC chat memory, transformers"]
    backend --> flyway["Flyway"]
    backend --> pg["PostgreSQL driver"]

    modeling["platform-modeling-infrastructure"] --> emf["Eclipse EMF"]
    modeling --> elk["Eclipse Layout Kernel"]
    etl["epsilon-model-transformation"] --> epsilonEtl["Epsilon ETL + EMC EMF"]
    evl["epsilon-model-validation"] --> epsilonEvl["Epsilon EVL + EMC EMF"]
    m2t["epsilon-text-generation"] --> epsilonEgl["Epsilon EGL/EGX + EMC EMF"]
    frontend["Browser frontend"] --> g6["AntV G6"]
    tools["CLI tools"] --> picocli["Picocli"]
```

## MDE Asset Dependencies

```mermaid
flowchart LR
    kernel["shared/kernel.ecore"]
    cim["CIM combined Ecore"]
    pim["PIM combined Ecore"]
    psm["PSM combined Ecore"]
    cimEvl["CIM EVL profile"]
    pimEvl["PIM EVL profile"]
    psmEvl["PSM EVL profile"]
    cimPim["CIM-to-PIM ETL"]
    pimPsm["PIM-to-PSM ETL"]
    psmArt["PSM-to-artifacts EGX/EGL"]

    kernel --> cim
    kernel --> pim
    kernel --> psm
    cim --> cimEvl
    pim --> pimEvl
    psm --> psmEvl
    cim --> cimPim
    pim --> cimPim
    pim --> pimPsm
    psm --> pimPsm
    psm --> psmArt
```
