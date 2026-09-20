/**
 * JSDoc type definitions for the MODRISS JSON representation mapped to SPEM.
 */

/**
 * @typedef {Object} ArtifactKind
 * @property {string} id
 * @property {string} name
 * @property {string} description
 * @property {string} [workProductKind] - Artifact | Deliverable | other MODRISS kind
 */

/**
 * @typedef {Object} MetamodelBinding
 * @property {string} metamodel
 * @property {string} classifier
 * @property {string} kind - EClass | EEnum | EDataType
 */

/**
 * @typedef {Object} TaskDefinition
 * @property {string} id
 * @property {string} name
 * @property {string} purpose
 * @property {string[]} performerRoleRefs
 * @property {string[]} inputWorkProductRefs
 * @property {string} inputSource - declared; compiler never infers task inputs from sequence position
 * @property {string[]} outputWorkProductRefs
 * @property {{type: string, workProductDefinitionRef: string, direction: string, optional: boolean}[]} workProductParameters
 * @property {MetamodelBinding[]} metamodelBindings
 * @property {string[]} steps
 * @property {string[]} entryCriteria
 * @property {string[]} exitCriteria
 * @property {string[]} validationRules
 */

/**
 * @typedef {Object} TaskUse
 * @property {string} id
 * @property {string} taskDefinitionRef
 * @property {string[]} performerRoleUseRefs
 * @property {{id: string, type: string, taskUseRef: string, kind: string, roleUseRef: string}[]} processPerformers
 * @property {number[]} selectedStepIndices
 * @property {string[]} inputWorkProductUseRefs
 * @property {string[]} outputWorkProductUseRefs
 * @property {{id: string, type: string, taskUseRef: string, direction: string, optional: boolean, workProductUseRef: string}[]} processParameters
 */

/**
 * @typedef {Object} Guideline
 * @property {string} id
 * @property {string} name
 * @property {string} appliesTo - process | phase id
 * @property {string} text
 */

/**
 * @typedef {Object} TaskSpec
 * @property {string} id
 * @property {string} name
 * @property {string} primaryRole - compact authoring alias compiled to RoleUse
 * @property {string} [viewpoint]
 * @property {string[]} [artifactIds]
 * @property {string[]} [inputArtifactIds] - explicit input WorkProductDefinition IDs
 * @property {string[]} [optionalInputArtifactIds]
 * @property {string[]} [types] - metamodel EClass/EEnum names
 * @property {string[]} [coverageGroups] - shared metamodel groups owned by this task
 * @property {boolean} [readinessTypes]
 * @property {string[]} steps
 * @property {string[]} entryCriteria
 * @property {string[]} exitCriteria
 * @property {string[]} validationRules
 * @property {string} [progressEvidence]
 * @property {string} [durationEstimate]
 */

/**
 * @typedef {Object} StageSpec
 * @property {string} id
 * @property {string} name
 * @property {string} objective
 * @property {string} primaryRole
 * @property {string} [viewpoint]
 * @property {StageSpec[]} [subStages]
 * @property {TaskSpec[]} [tasks]
 * @property {boolean} [iterative]
 * @property {boolean} [inEngine]
 */

/**
 * @typedef {Object} ProcessPhaseSpec
 * @property {string} id
 * @property {string} name
 * @property {number} order
 * @property {string} objective
 * @property {string} primaryRole
 * @property {string[]} entryCriteria
 * @property {string[]} exitCriteria
 * @property {boolean} [inEngine]
 * @property {StageSpec[]} stages
 * @property {string} [tailoringNote]
 */

/**
 * @typedef {Object} IterationLoop
 * @property {string} id
 * @property {string} name
 * @property {string} fromStageId
 * @property {string} toStageId
 * @property {string} [fromPhaseId] - legacy
 * @property {string} [toPhaseId]
 * @property {string} trigger
 * @property {string} guidance
 * @property {boolean} [twinPeaks]
 * @property {boolean} [crossLevel]
 */

/**
 * @typedef {Object} ChangeWorkflow
 * @property {string} id
 * @property {string} name
 * @property {string} trigger
 * @property {string[]} steps
 * @property {string[]} impactedStages
 * @property {boolean} [crossLevel]
 */

/**
 * @typedef {Object} EngineCycleStep
 * @property {string} id
 * @property {string} name
 * @property {string} type
 * @property {string} [primaryRole]
 * @property {string[]} [steps]
 * @property {string[]} [phaseIds]
 * @property {string[]} [stageIds]
 * @property {string[]} [reworkLoopIds]
 * @property {string} [validationGate]
 * @property {string} [milestoneId]
 * @property {string} [childProcessId]
 * @property {string} [transform]
 */

/**
 * @typedef {Object} ProcessEngineSpec
 * @property {string} id
 * @property {string} displayName
 * @property {string} description
 * @property {string} incrementUnit
 * @property {{ name: string, description: string }} deliverable
 * @property {{ name: string, description: string, phaseIds?: string[], stageIds?: string[] }} [onboarding]
 * @property {EngineCycleStep[]} cycle
 * @property {{ fromStepId: string, toStepId: string, condition: string, guidance: string }} loop
 * @property {string[]} [reworkLoopIds]
 * @property {IterationLoop[]} [reworkLoops]
 * @property {Object} [progressModel]
 * @property {Object} [governance]
 */

export {};
