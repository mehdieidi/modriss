package io.mehdieidi.modless.platform.core.service;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.mehdieidi.modless.mde.validation.EpsilonEvlValidator;
import io.mehdieidi.modless.mde.validation.EvlConstraintKind;
import io.mehdieidi.modless.mde.validation.EvlConstraintViolation;
import io.mehdieidi.modless.mde.validation.EvlDiagnostic;
import io.mehdieidi.modless.mde.validation.EvlValidationException;
import io.mehdieidi.modless.mde.validation.EvlValidationReport;
import io.mehdieidi.modless.mde.validation.EvlValidationRequest;
import io.mehdieidi.modless.mde.validation.ResourceEvlModelConfiguration;
import io.mehdieidi.modless.mde.validation.ValidationSeverity;
import io.mehdieidi.modless.platform.core.PlatformException;
import io.mehdieidi.modless.platform.core.model.ModelIndexRecord;
import io.mehdieidi.modless.platform.core.model.ModelLevel;
import io.mehdieidi.modless.platform.core.model.ModelRecord;
import io.mehdieidi.modless.platform.core.model.ProjectRecord;
import io.mehdieidi.modless.platform.core.model.StagedImportRecord;
import io.mehdieidi.modless.platform.core.model.UserRecord;
import io.mehdieidi.modless.platform.core.repository.PlatformStore;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Manages stored platform models, validation, import/export, XMI sidecars, and
 * optimistic-concurrency updates.
 */
public final class ModelService {

  /** File repository used for model JSON, indexes, and XMI sidecars. */
  private final PlatformStore store;

  /** Project service used for access and editor checks. */
  private final ProjectService projectService;

  /** Modeling configuration service used for JSON-level validation. */
  private final ModelingConfigService modelingConfig;

  /** Runtime limits and asset roots. */
  private final MdeRuntimeOptions runtimeOptions;

  /** Runtime path resolver for validation assets. */
  private final MdeRuntimePaths mdePaths;

  /** Metamodel resolver used for import/export and metadata repair. */
  private final MetamodelResolver metamodelResolver;

  /** Per-model lock service for mutations. */
  private final ModelLockService modelLocks;

  /** XMI bridge between JSON and EMF resources. */
  private final XmiModelImportService xmiImportService;

  /** EVL validator used for semantic validation. */
  private final EpsilonEvlValidator evlValidator;

  /** Small LRU validation cache keyed by model revision and XMI hash. */
  private final Map<String, ValidationResult> validationCache =
      Collections.synchronizedMap(
          new LinkedHashMap<>(128, 0.75f, true) {
            /**
             * Evicts the least-recently used validation result once the cache reaches its fixed
             * service-local capacity.
             *
             * @param eldest least-recently accessed cache entry
             * @return {@code true} when the eldest entry should be removed
             */
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, ValidationResult> eldest) {
              return size() > 128;
            }
          });

  /**
   * Creates a model service using a default modeling configuration service.
   *
   * @param store backing JSON repository
   * @param projectService project access service
   */
  public ModelService(PlatformStore store, ProjectService projectService) {
    this(store, projectService, new ModelingConfigService());
  }

  /**
   * Creates a model service with default runtime options.
   *
   * @param store backing JSON repository
   * @param projectService project access service
   * @param modelingConfig modeling configuration service
   */
  public ModelService(
      PlatformStore store, ProjectService projectService, ModelingConfigService modelingConfig) {
    this(
        store,
        projectService,
        modelingConfig,
        MdeRuntimeOptions.defaults(),
        new ModelLockService());
  }

  /**
   * Creates a model service with explicit runtime options and lock service.
   *
   * @param store backing JSON repository
   * @param projectService project access service
   * @param modelingConfig modeling configuration service
   * @param runtimeOptions runtime limits and asset roots
   * @param modelLocks per-model lock service
   */
  public ModelService(
      PlatformStore store,
      ProjectService projectService,
      ModelingConfigService modelingConfig,
      MdeRuntimeOptions runtimeOptions,
      ModelLockService modelLocks) {
    this(
        store,
        projectService,
        modelingConfig,
        runtimeOptions,
        new MdeRuntimePaths(runtimeOptions),
        null,
        modelLocks);
  }

  /**
   * Creates a fully configurable model service.
   *
   * @param store backing JSON repository
   * @param projectService project access service
   * @param modelingConfig modeling configuration service
   * @param runtimeOptions runtime limits and asset roots
   * @param mdePaths runtime path resolver
   * @param metamodelResolver metamodel resolver
   * @param modelLocks per-model lock service
   */
  public ModelService(
      PlatformStore store,
      ProjectService projectService,
      ModelingConfigService modelingConfig,
      MdeRuntimeOptions runtimeOptions,
      MdeRuntimePaths mdePaths,
      MetamodelResolver metamodelResolver,
      ModelLockService modelLocks) {
    this.store = store;
    this.projectService = projectService;
    this.modelingConfig = modelingConfig;
    this.runtimeOptions = runtimeOptions == null ? MdeRuntimeOptions.defaults() : runtimeOptions;
    this.mdePaths = mdePaths == null ? new MdeRuntimePaths(this.runtimeOptions) : mdePaths;
    this.metamodelResolver =
        metamodelResolver == null ? new FileMetamodelResolver(this.mdePaths) : metamodelResolver;
    this.modelLocks = modelLocks == null ? new ModelLockService() : modelLocks;
    this.xmiImportService = new XmiModelImportService(store.objectMapper(), this.metamodelResolver);
    this.evlValidator =
        new EpsilonEvlValidator(
            this.runtimeOptions.executionTimeout(), this.runtimeOptions.maxCapturedOutputBytes());
  }

  /**
   * Creates a compact validation issue.
   *
   * @param severity issue severity
   * @param constraint issue constraint identifier
   * @param message issue message
   * @return validation issue
   */
  private static ValidationIssue issue(String severity, String constraint, String message) {
    return new ValidationIssue(severity, constraint, "MODEL", message, null, null, null);
  }

  /**
   * Returns the lock service shared with transformation operations.
   *
   * @return model lock service
   */
  ModelLockService modelLocks() {
    return modelLocks;
  }

  /**
   * Returns the maximum model upload size.
   *
   * @return maximum bytes accepted for uploaded models
   */
  public long maxModelUploadBytes() {
    return runtimeOptions.maxModelUploadBytes();
  }

  /**
   * Lists models for a project and level after verifying project access.
   *
   * @param user requesting user
   * @param level model level
   * @param projectId project identifier
   * @return full model records ordered by last update
   */
  public List<ModelRecord> list(UserRecord user, ModelLevel level, String projectId) {
    if (projectId == null || projectId.isBlank()) {
      return List.of();
    }
    projectService.get(user, projectId);
    return store.list(modelDir(projectId, level), ModelRecord.class).stream()
        .sorted(Comparator.comparing(ModelRecord::updatedAt).reversed())
        .toList();
  }

  /**
   * Lists lightweight model summaries for a project and level.
   *
   * @param user requesting user
   * @param level model level
   * @param projectId project identifier
   * @return summaries ordered by last update
   */
  public List<ModelSummary> listSummaries(UserRecord user, ModelLevel level, String projectId) {
    if (projectId == null || projectId.isBlank()) {
      return List.of();
    }
    projectService.get(user, projectId);
    return store.list(modelDir(projectId, level), ModelRecord.class).stream()
        .map(this::summary)
        .sorted(Comparator.comparing(ModelSummary::updatedAt).reversed())
        .toList();
  }

  /**
   * Loads a model by id and returns a client-safe record.
   *
   * @param user requesting user
   * @param level model level
   * @param id model identifier
   * @return model record
   */
  public ModelRecord get(UserRecord user, ModelLevel level, String id) {
    ModelRecord model = find(level, id);
    projectService.get(user, model.projectId());
    return clientRecord(model);
  }

  /**
   * Loads a model for transformation, preserving server-only metadata needed by MDE flows.
   *
   * @param user requesting user
   * @param level model level
   * @param id model identifier
   * @return model record repaired for internal use
   */
  ModelRecord getForTransformation(UserRecord user, ModelLevel level, String id) {
    ModelRecord model = find(level, id);
    projectService.get(user, model.projectId());
    return repairMetadataIfNeeded(model);
  }

  /**
   * Loads a model by project id and model id.
   *
   * @param user requesting user
   * @param level model level
   * @param projectId project identifier
   * @param id model identifier
   * @return client-safe model record
   */
  public ModelRecord get(UserRecord user, ModelLevel level, String projectId, String id) {
    projectService.get(user, projectId);
    ModelRecord model =
        store.require(modelPath(projectId, level, id), ModelRecord.class, "Model not found.");
    writeModelIndex(model);
    return clientRecord(repairMetadataIfNeeded(model));
  }

  /**
   * Creates a model without an explicit source XMI payload.
   *
   * @param user requesting user
   * @param level model level
   * @param projectId project identifier
   * @param name model name
   * @param modelJson model JSON
   * @return created model
   */
  public ModelRecord create(
      UserRecord user, ModelLevel level, String projectId, String name, JsonNode modelJson) {
    return create(user, level, projectId, name, modelJson, null);
  }

  /**
   * Creates a model with optional source XMI bytes.
   *
   * @param user requesting user
   * @param level model level
   * @param projectId project identifier
   * @param name model name
   * @param modelJson model JSON
   * @param sourceXmiBytes optional source XMI sidecar bytes
   * @return created model
   */
  public ModelRecord create(
      UserRecord user,
      ModelLevel level,
      String projectId,
      String name,
      JsonNode modelJson,
      byte[] sourceXmiBytes) {
    ProjectRecord project = projectService.get(user, projectId);
    projectService.requireEditor(project, user.id());
    Instant now = Instant.now();
    JsonNode normalizedModel = normalizeModel(name, level, modelJson);
    SourceXmiUpdate sourceXmi =
        sourceXmiBytes == null || sourceXmiBytes.length == 0
            ? resolveSourceXmiUpdate(user, projectId, level, normalizedModel, false)
            : new SourceXmiUpdate(sourceXmiBytes, hashBytes(sourceXmiBytes), null);
    sourceXmi = canonicalSourceXmi(level, normalizedModel, sourceXmi);
    MetamodelDescriptor metamodel = metamodelResolver.resolve(level);
    ModelRecord model =
        new ModelRecord(
            UUID.randomUUID().toString(),
            projectId,
            level,
            requireName(name, level),
            normalizedModel,
            metamodel.version(),
            metamodel.sha256(),
            1,
            sourceXmi.hash(),
            "CURRENT",
            now,
            now);
    persistModelAndSourceXmi(null, model, sourceXmi);
    return clientRecord(model);
  }

  /**
   * Creates a generated model while preserving generated JSON fields for the caller.
   *
   * @param user requesting user
   * @param level generated model level
   * @param projectId project identifier
   * @param name model name
   * @param modelJson generated model JSON
   * @param sourceXmiBytes canonical source XMI sidecar bytes
   * @return generated model record
   */
  ModelRecord createGenerated(
      UserRecord user,
      ModelLevel level,
      String projectId,
      String name,
      ObjectNode modelJson,
      byte[] sourceXmiBytes) {
    ProjectRecord project = projectService.get(user, projectId);
    projectService.requireEditor(project, user.id());
    Instant now = Instant.now();
    ObjectNode normalizedModel =
        modelJson == null ? store.objectMapper().createObjectNode() : modelJson;
    if (!normalizedModel.hasNonNull("name")) {
      normalizedModel.put("name", requireName(name, level));
    }
    if (!normalizedModel.hasNonNull("modelLevel")) {
      normalizedModel.put("modelLevel", level.name());
    }
    SourceXmiUpdate sourceXmi =
        sourceXmiBytes == null || sourceXmiBytes.length == 0
            ? resolveSourceXmiUpdate(user, projectId, level, normalizedModel, false)
            : new SourceXmiUpdate(sourceXmiBytes, hashBytes(sourceXmiBytes), null);
    sourceXmi = canonicalSourceXmi(level, normalizedModel, sourceXmi);
    MetamodelDescriptor metamodel = metamodelResolver.resolve(level);
    ModelRecord model =
        new ModelRecord(
            UUID.randomUUID().toString(),
            projectId,
            level,
            requireName(name, level),
            normalizedModel,
            metamodel.version(),
            metamodel.sha256(),
            1,
            sourceXmi.hash(),
            "CURRENT",
            now,
            now);
    persistModelAndSourceXmi(null, model, sourceXmi);
    return generatedClientRecord(model);
  }

  /**
   * Updates a model without a revision precondition.
   *
   * @param user requesting user
   * @param level model level
   * @param id model identifier
   * @param name replacement model name
   * @param modelJson replacement model JSON
   * @return updated model
   */
  public ModelRecord update(
      UserRecord user, ModelLevel level, String id, String name, JsonNode modelJson) {
    return update(user, level, id, name, modelJson, null);
  }

  /**
   * Updates a model and its XMI sidecar under a model lock.
   *
   * @param user requesting user
   * @param level model level
   * @param id model identifier
   * @param name replacement model name
   * @param modelJson replacement model JSON
   * @param expectedRevision expected revision, or {@code null}
   * @return updated model
   */
  public ModelRecord update(
      UserRecord user,
      ModelLevel level,
      String id,
      String name,
      JsonNode modelJson,
      Long expectedRevision) {
    return modelLocks.withModelLock(
        id,
        Duration.ofSeconds(30),
        () -> {
          ModelRecord existing = get(user, level, id);
          requireExpectedRevision(existing, expectedRevision);
          ProjectRecord project = projectService.get(user, existing.projectId());
          projectService.requireEditor(project, user.id());
          JsonNode normalizedModel = normalizeModel(name, level, modelJson);
          SourceXmiUpdate sourceXmi =
              resolveSourceXmiUpdate(user, existing.projectId(), level, normalizedModel, true);
          if (!(sourceXmi.shouldPreserve()
              && sourceXmi(existing).isPresent()
              && !hasDiagramLayout(normalizedModel))) {
            sourceXmi = canonicalSourceXmi(level, normalizedModel, sourceXmi);
          }
          MetamodelDescriptor metamodel = metamodelResolver.resolve(level);
          ModelRecord updated =
              new ModelRecord(
                  existing.id(),
                  existing.projectId(),
                  level,
                  requireName(name, level),
                  normalizedModel,
                  metamodel.version(),
                  metamodel.sha256(),
                  nextRevision(existing),
                  sourceXmi.effectiveHash(existing.sourceXmiHash()),
                  "CURRENT",
                  existing.createdAt(),
                  Instant.now());
          persistModelAndSourceXmi(existing, updated, sourceXmi);
          return clientRecord(updated);
        });
  }

  /**
   * Applies JSON Patch-like operations without a revision precondition.
   *
   * @param user requesting user
   * @param level model level
   * @param id model identifier
   * @param name replacement model name, or {@code null} to keep existing
   * @param operations patch operations
   * @return updated model
   */
  public ModelRecord patch(
      UserRecord user,
      ModelLevel level,
      String id,
      String name,
      List<ModelPatchOperation> operations) {
    return patch(user, level, id, name, operations, null);
  }

  /**
   * Applies JSON Patch-like operations and persists the resulting model under a model lock.
   *
   * @param user requesting user
   * @param level model level
   * @param id model identifier
   * @param name replacement model name, or {@code null} to keep existing
   * @param operations patch operations
   * @param expectedRevision expected revision, or {@code null}
   * @return updated model
   */
  public ModelRecord patch(
      UserRecord user,
      ModelLevel level,
      String id,
      String name,
      List<ModelPatchOperation> operations,
      Long expectedRevision) {
    return modelLocks.withModelLock(
        id,
        Duration.ofSeconds(30),
        () -> {
          ModelRecord existing = get(user, level, id);
          requireExpectedRevision(existing, expectedRevision);
          ProjectRecord project = projectService.get(user, existing.projectId());
          projectService.requireEditor(project, user.id());
          if (operations == null || operations.isEmpty()) {
            return clientRecord(existing);
          }
          ObjectNode patchedModel =
              existing.modelJson() instanceof ObjectNode objectNode
                  ? objectNode.deepCopy()
                  : store.objectMapper().createObjectNode();
          for (ModelPatchOperation operation : operations) {
            applyPatchOperation(patchedModel, operation);
          }
          JsonNode normalizedModel =
              normalizeModel(name == null ? existing.name() : name, level, patchedModel);
          SourceXmiUpdate sourceXmi =
              resolveSourceXmiUpdate(user, existing.projectId(), level, normalizedModel, true);
          if (!(sourceXmi.shouldPreserve()
              && sourceXmi(existing).isPresent()
              && !hasDiagramLayout(normalizedModel))) {
            sourceXmi = canonicalSourceXmi(level, normalizedModel, sourceXmi);
          }
          MetamodelDescriptor metamodel = metamodelResolver.resolve(level);
          ModelRecord updated =
              new ModelRecord(
                  existing.id(),
                  existing.projectId(),
                  level,
                  requireName(name == null ? existing.name() : name, level),
                  normalizedModel,
                  metamodel.version(),
                  metamodel.sha256(),
                  nextRevision(existing),
                  sourceXmi.effectiveHash(existing.sourceXmiHash()),
                  "CURRENT",
                  existing.createdAt(),
                  Instant.now());
          persistModelAndSourceXmi(existing, updated, sourceXmi);
          return clientRecord(updated);
        });
  }

  /**
   * Deletes a model, its XMI sidecar, and global index entry.
   *
   * @param user requesting user
   * @param level model level
   * @param id model identifier
   */
  public void delete(UserRecord user, ModelLevel level, String id) {
    modelLocks.withModelLock(
        id,
        Duration.ofSeconds(30),
        () -> {
          ModelRecord model = get(user, level, id);
          ProjectRecord project = projectService.get(user, model.projectId());
          projectService.requireEditor(project, user.id());
          store.deleteIfExists(modelPath(model.projectId(), level, id));
          store.deleteIfExists(sourceXmiPath(model.projectId(), level, id));
          store.deleteIfExists(modelIndexPath(id));
          return null;
        });
  }

  /**
   * Validates model JSON using EVL and level-specific JSON checks.
   *
   * @param level model level
   * @param modelJson model JSON
   * @return validation result
   */
  public ValidationResult validate(ModelLevel level, JsonNode modelJson) {
    if (modelJson == null || modelJson.isNull()) {
      return new ValidationResult(
          false, List.of(issue("ERROR", "ModelRequired", "Model JSON is required.")));
    }
    List<ValidationIssue> issues = new ArrayList<>(validateWithEvl(level, modelJson));
    if (level == ModelLevel.CIM) {
      issues.addAll(validateCimModel(modelJson));
    }
    boolean valid = issues.stream().noneMatch(issue -> "ERROR".equals(issue.severity()));
    return new ValidationResult(valid, issues);
  }

  /**
   * Validates a stored model using its source XMI sidecar when available.
   *
   * @param user requesting user
   * @param level model level
   * @param id model identifier
   * @return validation result
   */
  public ValidationResult validate(UserRecord user, ModelLevel level, String id) {
    ModelRecord model = get(user, level, id);
    String cacheKey = validationCacheKey(model);
    ValidationResult cached = validationCache.get(cacheKey);
    if (cached != null) {
      return cached;
    }
    Optional<byte[]> xmiBytes = sourceXmi(model);
    ValidationResult result;
    if (xmiBytes.isPresent()) {
      result = validateGeneratedXmi(level, xmiBytes.get());
      if (hasRecoverableStaleSourceError(result)) {
        ValidationResult repairedResult = validateRegeneratedSourceXmi(model);
        if (!hasRecoverableStaleSourceError(repairedResult)) {
          result = repairedResult;
        }
      }
    } else {
      // Legacy JSON-only records are converted in memory so existing projects remain usable.
      result = validate(level, model.modelJson());
    }
    validationCache.put(cacheKey, result);
    return result;
  }

  /**
   * Regenerates a source XMI sidecar for validation when stored XMI appears stale but recoverable.
   *
   * @param model stored model
   * @return validation result from regenerated XMI or JSON fallback
   */
  private ValidationResult validateRegeneratedSourceXmi(ModelRecord model) {
    try {
      SourceXmiUpdate sourceXmi = canonicalSourceXmi(model.level(), model.modelJson(), null);
      ValidationResult result = validateGeneratedXmi(model.level(), sourceXmi.bytes());
      if (!hasRecoverableStaleSourceError(result)) {
        attachSourceXmi(model, sourceXmi.bytes());
      }
      return result;
    } catch (RuntimeException ex) {
      return validate(model.level(), model.modelJson());
    }
  }

  /**
   * Detects validation failures that can be repaired by regenerating source XMI from JSON.
   *
   * @param result validation result
   * @return {@code true} when stale source XMI is likely
   */
  private boolean hasRecoverableStaleSourceError(ValidationResult result) {
    return hasRelationshipEndpointLoadingError(result) || hasTraceEndpointError(result);
  }

  /**
   * Detects missing relationship endpoint errors from EMF model loading.
   *
   * @param result validation result
   * @return {@code true} when endpoint loading failed
   */
  private boolean hasRelationshipEndpointLoadingError(ValidationResult result) {
    return result != null
        && result.issues().stream()
            .anyMatch(
                issue ->
                    "ERROR".equals(issue.severity())
                        && "EVL_MODEL_LOADING".equals(issue.constraint())
                        && (issue.message().contains("required feature 'source'")
                            || issue.message().contains("required feature 'target'")));
  }

  /**
   * Detects TraceLink endpoint validation errors that can be repaired from graph endpoint ids.
   *
   * @param result validation result
   * @return {@code true} when TraceLink endpoints are stale
   */
  private boolean hasTraceEndpointError(ValidationResult result) {
    return result != null
        && result.issues().stream()
            .anyMatch(
                issue ->
                    "ERROR".equals(issue.severity())
                        && "TraceLinkHasReferenceOrExternalId".equals(issue.constraint()));
  }

  /**
   * Validates generated or uploaded XMI bytes with EVL.
   *
   * @param level model level
   * @param xmiBytes XMI payload
   * @return validation result
   */
  public ValidationResult validateGeneratedXmi(ModelLevel level, byte[] xmiBytes) {
    List<ValidationIssue> issues = validateWithEvl(level, xmiBytes);
    boolean valid = issues.stream().noneMatch(issue -> "ERROR".equals(issue.severity()));
    return new ValidationResult(valid, issues);
  }

  /**
   * Exports model JSON to an in-memory EMF resource and validates it with EVL.
   *
   * @param level model level
   * @param modelJson model JSON
   * @return validation issues
   */
  private List<ValidationIssue> validateWithEvl(ModelLevel level, JsonNode modelJson) {
    try {
      MetamodelDescriptor metamodel = metamodelResolver.resolve(level);
      EvlValidationReport report =
          evlValidator.validate(
              new EvlValidationRequest(
                  mdePaths.validationRoot(level),
                  List.of(mdePaths.validationEntryFile(level)),
                  List.of(
                      ResourceEvlModelConfiguration.readOnly(
                          validationModelName(level),
                          validationModelAliases(level),
                          xmiImportService.exportResource(
                              level, hydrateSemanticReferences(modelJson)),
                          metamodel.packages())),
                  true));
      return validationIssues(report);
    } catch (PlatformException ex) {
      return List.of(issue("ERROR", "XmiExport", ex.getMessage()));
    } catch (EvlValidationException ex) {
      return validationIssues(ex.getReport());
    } catch (Exception ex) {
      return List.of(
          issue(
              "ERROR",
              "EvlValidationExecution",
              "EVL validation could not run: "
                  + (ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage())));
    }
  }

  /**
   * Loads XMI bytes into an EMF resource and validates it with EVL.
   *
   * @param level model level
   * @param xmiBytes source XMI bytes
   * @return validation issues
   */
  private List<ValidationIssue> validateWithEvl(ModelLevel level, byte[] xmiBytes) {
    try {
      MetamodelDescriptor metamodel = metamodelResolver.resolve(level);
      EvlValidationReport report =
          evlValidator.validate(
              new EvlValidationRequest(
                  mdePaths.validationRoot(level),
                  List.of(mdePaths.validationEntryFile(level)),
                  List.of(
                      ResourceEvlModelConfiguration.readOnly(
                          validationModelName(level),
                          validationModelAliases(level),
                          xmiImportService.loadResource(level, xmiBytes, "validation"),
                          metamodel.packages())),
                  true));
      return validationIssues(report);
    } catch (PlatformException ex) {
      return List.of(issue("ERROR", "XmiLoad", ex.getMessage()));
    } catch (EvlValidationException ex) {
      return validationIssues(ex.getReport());
    } catch (Exception ex) {
      return List.of(
          issue(
              "ERROR",
              "EvlValidationExecution",
              "EVL validation could not run: "
                  + (ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage())));
    }
  }

  /**
   * Converts an EVL report to platform validation issues.
   *
   * @param report EVL validation report
   * @return validation issues
   */
  private List<ValidationIssue> validationIssues(EvlValidationReport report) {
    if (report == null) {
      return List.of(
          issue("ERROR", "EvlValidationExecution", "EVL validation failed without a report."));
    }
    List<ValidationIssue> issues = new ArrayList<>();
    report.violations().forEach(violation -> issues.add(validationIssue(violation)));
    report.diagnostics().forEach(diagnostic -> issues.add(validationIssue(diagnostic)));
    return issues;
  }

  /**
   * Converts an EVL constraint violation to a platform validation issue.
   *
   * @param violation EVL violation
   * @return validation issue
   */
  private ValidationIssue validationIssue(EvlConstraintViolation violation) {
    String severity = violation.kind() == EvlConstraintKind.MANDATORY ? "ERROR" : "WARNING";
    String elementId = violation.element().attributes().getOrDefault("id", null);
    String elementName =
        violation.element().attributes().getOrDefault("name", violation.element().summary());
    String guidance =
        violation.fixes().isEmpty() ? "" : "Suggested fix: " + violation.fixes().get(0).title();
    return new ValidationIssue(
        severity,
        textOrDefault(violation.constraintName(), "EvlConstraint"),
        violation.contextType(),
        violation.message(),
        guidance,
        elementId,
        elementName);
  }

  /**
   * Converts an EVL diagnostic to a platform validation issue.
   *
   * @param diagnostic EVL diagnostic
   * @return validation issue
   */
  private ValidationIssue validationIssue(EvlDiagnostic diagnostic) {
    String severity = diagnostic.severity() == ValidationSeverity.ERROR ? "ERROR" : "WARNING";
    String constraint = "EVL_" + diagnostic.phase().name();
    String message =
        !diagnostic.reason().isBlank() ? diagnostic.reason() : diagnostic.whatWentWrong();
    String guidance = diagnostic.howToFix();
    return new ValidationIssue(
        severity,
        constraint,
        "EVL_DIAGNOSTIC",
        message,
        guidance,
        null,
        diagnostic.file() == null ? null : diagnostic.file().toString());
  }

  /**
   * Performs additional CIM JSON checks using merged modeling metadata.
   *
   * @param modelJson CIM model JSON
   * @return validation issues
   */
  private List<ValidationIssue> validateCimModel(JsonNode modelJson) {
    List<ValidationIssue> issues = new java.util.ArrayList<>();
    Map<String, Object> config = modelingConfig.config();
    Map<?, ?> levels = (Map<?, ?>) config.get("levels");
    Map<?, ?> cim = (Map<?, ?>) levels.get("cim");
    Map<String, Map<?, ?>> definitionsByType = new java.util.HashMap<>();
    if (cim.get("elements") instanceof List<?> elements) {
      elements.stream()
          .filter(Map.class::isInstance)
          .map(Map.class::cast)
          .forEach(
              element -> {
                Object type = element.get("type");
                if (type != null) {
                  definitionsByType.put(String.valueOf(type), element);
                }
              });
    }
    Map<String, JsonNode> graphObjectsById = new java.util.HashMap<>();
    indexGraphObjects(modelJson.path("graph").path("elements"), graphObjectsById);
    indexGraphObjects(modelJson.path("graph").path("relationships"), graphObjectsById);
    List<JsonNode> graphRelationships = new java.util.ArrayList<>();
    collectGraphRelationships(modelJson.path("graph").path("relationships"), graphRelationships);
    List<JsonNode> elements = new java.util.ArrayList<>();
    collectSemanticElements(modelJson, elements);
    if (elements.isEmpty()) {
      issues.add(issue("WARNING", "CimElementsMissing", "CIM model has no semantic elements."));
      return issues;
    }
    for (JsonNode element : elements) {
      String type = element.path("eClass").asText(element.path("type").asText(""));
      String elementId = element.path("id").asText(null);
      String elementName = element.path("name").asText(element.path("label").asText(null));
      Map<?, ?> definition = definitionsByType.get(type);
      if (definition == null) {
        issues.add(
            new ValidationIssue(
                "ERROR",
                "UnknownCimElement",
                type,
                "Element type is not defined by the CIM metamodel.",
                null,
                elementId,
                elementName));
        continue;
      }
      JsonNode fallback = resolveGraphFallback(element, graphObjectsById, graphRelationships);
      validateRequiredFeatures(element, fallback, definition, issues, elementId, elementName);
    }
    return issues;
  }

  /**
   * Indexes graph objects by id.
   *
   * @param nodes graph object array
   * @param index mutable index
   */
  private void indexGraphObjects(JsonNode nodes, Map<String, JsonNode> index) {
    if (!nodes.isArray()) {
      return;
    }
    for (JsonNode node : nodes) {
      String id = node.path("id").asText("");
      if (!id.isBlank()) {
        index.put(id, node);
      }
    }
  }

  /**
   * Appends graph relationships to a mutable list.
   *
   * @param nodes graph relationship array
   * @param relationships mutable relationship sink
   */
  private void collectGraphRelationships(JsonNode nodes, List<JsonNode> relationships) {
    if (!nodes.isArray()) {
      return;
    }
    nodes.forEach(relationships::add);
  }

  /**
   * Resolves a graph object that can supply missing semantic fields.
   *
   * @param element semantic element
   * @param graphObjectsById graph objects keyed by id
   * @param graphRelationships graph relationship list
   * @return fallback graph object, or {@code null}
   */
  private JsonNode resolveGraphFallback(
      JsonNode element, Map<String, JsonNode> graphObjectsById, List<JsonNode> graphRelationships) {
    String id = element.path("id").asText("");
    if (!id.isBlank()) {
      JsonNode byId = graphObjectsById.get(id);
      if (byId != null) {
        return byId;
      }
    }
    String type = element.path("eClass").asText("");
    String name = element.path("name").asText("");
    for (JsonNode relationship : graphRelationships) {
      if (!type.equals(relationship.path("eClass").asText(""))) {
        continue;
      }
      if (!name.isBlank() && name.equals(relationship.path("name").asText(""))) {
        return relationship;
      }
    }
    return null;
  }

  /**
   * Recursively collects semantic objects while skipping transport graph data.
   *
   * @param node current JSON node
   * @param elements mutable semantic element sink
   */
  private void collectSemanticElements(JsonNode node, List<JsonNode> elements) {
    if (node == null || node.isNull()) {
      return;
    }
    if (node.isObject()) {
      if (node.hasNonNull("eClass")) {
        elements.add(node);
      }
      node.fields()
          .forEachRemaining(
              entry -> {
                if ("graph".equals(entry.getKey()) || "diagram".equals(entry.getKey())) {
                  return;
                }
                collectSemanticElements(entry.getValue(), elements);
              });
      return;
    }
    if (node.isArray()) {
      node.forEach(child -> collectSemanticElements(child, elements));
    }
  }

  /**
   * Returns the primary Epsilon model name used for validation.
   *
   * @param level model level
   * @return validation model name
   */
  private String validationModelName(ModelLevel level) {
    return switch (level) {
      case CIM -> "CIM";
      case PIM -> "PIM";
      case PSM -> "AWSPSM";
    };
  }

  /**
   * Returns additional model aliases used by EVL validation.
   *
   * @param level model level
   * @return validation model aliases
   */
  private List<String> validationModelAliases(ModelLevel level) {
    return switch (level) {
      case CIM, PIM -> List.of("KERNEL");
      case PSM -> List.of("AWSPSMENUMS", "KERNEL");
    };
  }

  /**
   * Validates required attributes and references for one semantic element.
   *
   * @param element semantic element
   * @param fallbackElement graph fallback object
   * @param definition metadata definition
   * @param issues mutable issue sink
   * @param elementId element identifier
   * @param elementName element display name
   */
  private void validateRequiredFeatures(
      JsonNode element,
      JsonNode fallbackElement,
      Map<?, ?> definition,
      List<ValidationIssue> issues,
      String elementId,
      String elementName) {
    validateRequiredFeatureList(
        element,
        fallbackElement,
        definition,
        definition.get("attributes"),
        "RequiredAttribute",
        issues,
        elementId,
        elementName);
    validateRequiredFeatureList(
        element,
        fallbackElement,
        definition,
        definition.get("references"),
        "RequiredReference",
        issues,
        elementId,
        elementName);
  }

  /**
   * Validates one configured required feature list.
   *
   * @param element semantic element
   * @param fallbackElement graph fallback object
   * @param definition metadata definition
   * @param fieldsObject configured field list
   * @param constraint validation constraint name
   * @param issues mutable issue sink
   * @param elementId element identifier
   * @param elementName element display name
   */
  private void validateRequiredFeatureList(
      JsonNode element,
      JsonNode fallbackElement,
      Map<?, ?> definition,
      Object fieldsObject,
      String constraint,
      List<ValidationIssue> issues,
      String elementId,
      String elementName) {
    if (!(fieldsObject instanceof List<?> fields)) {
      return;
    }
    for (Object fieldObject : fields) {
      if (!(fieldObject instanceof Map<?, ?> field)
          || !Boolean.TRUE.equals(field.get("required"))) {
        continue;
      }
      if ("RequiredReference".equals(constraint) && Boolean.TRUE.equals(field.get("readonly"))) {
        continue;
      }
      String name = String.valueOf(field.get("name"));
      if ("RequiredReference".equals(constraint)
          && Boolean.TRUE.equals(definition.get("relationshipElement"))
          && ("source".equals(name) || "target".equals(name))) {
        continue;
      }
      JsonNode value = element.get(name);
      if ((value == null
              || value.isNull()
              || (value.isTextual() && value.asText().isBlank())
              || (value.isArray() && value.isEmpty()))
          && fallbackElement != null) {
        value = fallbackElement.get(name);
      }
      if (value == null
          || value.isNull()
          || (value.isTextual() && value.asText().isBlank())
          || (value.isArray() && value.isEmpty())) {
        issues.add(
            new ValidationIssue(
                "ERROR",
                constraint,
                element.path("eClass").asText(),
                "Required CIM feature is missing: " + name,
                null,
                elementId,
                elementName));
      }
    }
  }

  /**
   * Copies graph endpoint fields back into semantic relationships before XMI validation/export.
   *
   * @param modelJson model JSON
   * @return hydrated model JSON copy
   */
  private JsonNode hydrateSemanticReferences(JsonNode modelJson) {
    if (modelJson == null || !modelJson.isObject()) {
      return modelJson;
    }
    Map<String, JsonNode> graphRelationships = new LinkedHashMap<>();
    JsonNode relationships = modelJson.path("graph").path("relationships");
    if (relationships.isArray()) {
      relationships.forEach(
          relationship -> {
            String id = text(relationship, "id", "");
            if (!id.isBlank()) {
              graphRelationships.put(id, relationship);
            }
          });
    }
    ObjectNode copy = (ObjectNode) modelJson.deepCopy();
    stripValidationExportOnlyFields(copy);
    if (!graphRelationships.isEmpty()) {
      hydrateSemanticReferences(copy, graphRelationships);
    }
    return copy;
  }

  /**
   * Removes transport-only fields that should not be exported for validation.
   *
   * @param model model JSON copy to mutate
   */
  private void stripValidationExportOnlyFields(ObjectNode model) {
    model.remove(List.of("graph", "diagram", "views", "manualBacklog", "validationIssues"));
  }

  /**
   * Recursively hydrates semantic relationship endpoint fields from graph relationship records.
   *
   * @param node current JSON node
   * @param graphRelationships graph relationships keyed by id
   */
  private void hydrateSemanticReferences(JsonNode node, Map<String, JsonNode> graphRelationships) {
    if (node == null || node.isNull()) {
      return;
    }
    if (node.isObject()) {
      ObjectNode object = (ObjectNode) node;
      JsonNode graphRelationship = graphRelationships.get(text(object, "id", ""));
      if (graphRelationship != null) {
        copyReferenceIfMissing(object, graphRelationship, "source", "sourceElementId");
        copyReferenceIfMissing(object, graphRelationship, "target", "targetElementId");
        copyReferenceIfMissing(object, graphRelationship, "sourceElementId", "source");
        copyReferenceIfMissing(object, graphRelationship, "targetElementId", "target");
      }
      hydrateTraceEndpointIds(object);
      object
          .fields()
          .forEachRemaining(
              entry -> hydrateSemanticReferences(entry.getValue(), graphRelationships));
      return;
    }
    if (node.isArray()) {
      node.forEach(child -> hydrateSemanticReferences(child, graphRelationships));
    }
  }

  /**
   * Copies a reference value from a source object when the target field is blank.
   *
   * @param target target object to update
   * @param source source object to read
   * @param fieldName preferred field name
   * @param fallbackFieldName fallback field name
   */
  private void copyReferenceIfMissing(
      ObjectNode target, JsonNode source, String fieldName, String fallbackFieldName) {
    if (target.hasNonNull(fieldName) && !target.path(fieldName).asText("").isBlank()) {
      return;
    }
    String value = text(source, fieldName, "");
    if (value.isBlank()) {
      value = text(source, fallbackFieldName, "");
    }
    if (!value.isBlank()) {
      target.put(fieldName, value);
    }
  }

  /**
   * Ensures TraceLink endpoint id fields are present for export/validation.
   *
   * @param object semantic JSON object
   */
  private void hydrateTraceEndpointIds(ObjectNode object) {
    if (!"TraceLink".equals(text(object, "eClass", ""))) {
      return;
    }
    copyReferenceIfMissing(object, object, "sourceElementId", "source");
    copyReferenceIfMissing(object, object, "targetElementId", "target");
  }

  /**
   * Reads a text field from a JSON object.
   *
   * @param node JSON object
   * @param field field name
   * @param fallback fallback text
   * @return field text or fallback
   */
  private String text(JsonNode node, String field, String fallback) {
    return node == null ? fallback : node.path(field).asText(fallback);
  }

  /**
   * Returns a fallback when a string is blank.
   *
   * @param value candidate value
   * @param fallback fallback value
   * @return non-blank value
   */
  private String textOrDefault(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value;
  }

  /**
   * Best-effort recursive deletion for temporary validation directories.
   *
   * @param path directory to delete
   */
  private void deleteQuietly(Path path) {
    try (var paths = Files.walk(path)) {
      paths
          .sorted(java.util.Comparator.reverseOrder())
          .forEach(
              item -> {
                try {
                  Files.deleteIfExists(item);
                } catch (Exception ignored) {
                  // Temporary validation files should not mask validation results.
                }
              });
    } catch (Exception ignored) {
      // Best-effort cleanup.
    }
  }

  /**
   * Imports a model payload without project-specific staged side effects.
   *
   * @param level model level
   * @param fileName uploaded file name
   * @param bytes uploaded bytes
   * @param format import format, {@code json} or {@code xmi}
   * @return import result
   */
  public ImportResult importModel(ModelLevel level, String fileName, byte[] bytes, String format) {
    return importModel(null, null, level, fileName, bytes, format);
  }

  /**
   * Imports a model payload and, for XMI, attaches source bytes to the resulting JSON payload for
   * later persistence.
   *
   * @param user requesting user
   * @param projectId project identifier
   * @param level model level
   * @param fileName uploaded file name
   * @param bytes uploaded bytes
   * @param format import format, {@code json} or {@code xmi}
   * @return import result
   */
  public ImportResult importModel(
      UserRecord user,
      String projectId,
      ModelLevel level,
      String fileName,
      byte[] bytes,
      String format) {
    if (bytes != null && bytes.length > runtimeOptions.maxModelUploadBytes()) {
      throw new PlatformException(413, "Model file is too large.");
    }
    try {
      String normalizedFormat = String.valueOf(format).toLowerCase();
      JsonNode model =
          switch (normalizedFormat) {
            case "json" -> store.objectMapper().readTree(new String(bytes, StandardCharsets.UTF_8));
            case "xmi" -> xmiImportService.importModel(level, bytes);
            default ->
                throw new PlatformException(400, "Unsupported import format. Use JSON or XMI.");
          };
      String name =
          fileName == null || fileName.isBlank()
              ? level.apiName() + "-model"
              : fileName.replaceFirst("\\.[^.]+$", "");
      JsonNode normalizedModel = normalizeModel(name, level, model);
      if ("xmi".equals(normalizedFormat) && normalizedModel instanceof ObjectNode objectModel) {
        objectModel.put("_sourceXmiBase64", Base64.getEncoder().encodeToString(bytes));
      }
      ValidationResult validation =
          "xmi".equals(normalizedFormat)
              ? new ValidationResult(true, List.of())
              : validate(level, normalizedModel);
      return new ImportResult(name, normalizedModel, validation.issues());
    } catch (PlatformException ex) {
      throw ex;
    } catch (Exception ex) {
      String normalizedFormat = String.valueOf(format).toLowerCase();
      throw new PlatformException(
          400,
          "Uploaded model is not valid " + ("xmi".equals(normalizedFormat) ? "XMI." : "JSON."));
    }
  }

  /**
   * Imports a bounded model stream.
   *
   * @param user requesting user
   * @param projectId project identifier
   * @param level model level
   * @param fileName uploaded file name
   * @param input uploaded stream
   * @param sizeBytes reported upload size
   * @param format import format, {@code json} or {@code xmi}
   * @return import result
   */
  public ImportResult importModelFromStream(
      UserRecord user,
      String projectId,
      ModelLevel level,
      String fileName,
      InputStream input,
      long sizeBytes,
      String format) {
    if (sizeBytes > runtimeOptions.maxModelUploadBytes()) {
      throw new PlatformException(413, "Model file is too large.");
    }
    try {
      byte[] bytes = input.readNBytes(Math.toIntExact(runtimeOptions.maxModelUploadBytes() + 1));
      if (bytes.length > runtimeOptions.maxModelUploadBytes()) {
        throw new PlatformException(413, "Model file is too large.");
      }
      return importModel(user, projectId, level, fileName, bytes, format);
    } catch (PlatformException ex) {
      throw ex;
    } catch (ArithmeticException ex) {
      throw new PlatformException(500, "Configured upload limit is too large.");
    } catch (Exception ex) {
      throw new PlatformException(400, "Uploaded model could not be read.");
    }
  }

  /**
   * Exports model JSON to JSON or XMI bytes.
   *
   * @param level model level
   * @param modelJson model JSON
   * @param format export format, {@code json} or {@code xmi}
   * @return exported bytes
   */
  public byte[] exportModel(ModelLevel level, JsonNode modelJson, String format) {
    try {
      return switch (String.valueOf(format == null ? "json" : format).toLowerCase()) {
        case "json" ->
            store.objectMapper().writerWithDefaultPrettyPrinter().writeValueAsBytes(modelJson);
        case "xmi" -> xmiImportService.exportModel(level, hydrateSemanticReferences(modelJson));
        default -> throw new PlatformException(400, "Unsupported export format. Use JSON or XMI.");
      };
    } catch (PlatformException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new PlatformException(500, "Could not export model.");
    }
  }

  /**
   * Exports model JSON using CIM as the default level.
   *
   * @param modelJson model JSON
   * @param format export format
   * @return exported bytes
   */
  public byte[] exportModel(JsonNode modelJson, String format) {
    return exportModel(ModelLevel.CIM, modelJson, format);
  }

  /**
   * Exports a stored model, preferring the source XMI sidecar for XMI exports.
   *
   * @param user requesting user
   * @param level model level
   * @param id model identifier
   * @param format export format
   * @return exported bytes
   */
  public byte[] exportModel(UserRecord user, ModelLevel level, String id, String format) {
    ModelRecord model = get(user, level, id);
    if ("xmi".equalsIgnoreCase(String.valueOf(format))) {
      Optional<byte[]> xmiBytes = sourceXmi(model);
      if (xmiBytes.isPresent()) {
        return xmiBytes.get();
      }
    }
    return exportModel(level, model.modelJson(), format);
  }

  /**
   * Finds a model by id using the global index with legacy fallback.
   *
   * @param level model level
   * @param id model identifier
   * @return repaired model record
   */
  public ModelRecord find(ModelLevel level, String id) {
    Optional<ModelIndexRecord> index = store.read(modelIndexPath(id), ModelIndexRecord.class);
    if (index.isPresent()) {
      ModelIndexRecord record = index.get();
      if (record.level() != level) {
        throw new PlatformException(404, "Model not found.");
      }
      return repairMetadataIfNeeded(
          store.require(
              modelPath(record.projectId(), level, id), ModelRecord.class, "Model not found."));
    }
    return findLegacyAndIndex(level, id);
  }

  /**
   * Ensures required model-level fields exist in model JSON.
   *
   * @param name fallback model name
   * @param level model level
   * @param modelJson raw model JSON
   * @return normalized model JSON
   */
  private JsonNode normalizeModel(String name, ModelLevel level, JsonNode modelJson) {
    ObjectNode copy =
        modelJson == null || !modelJson.isObject()
            ? store.objectMapper().createObjectNode()
            : ((ObjectNode) modelJson.deepCopy());
    if (!copy.hasNonNull("name")) {
      copy.put("name", requireName(name, level));
    }
    if (!copy.hasNonNull("modelLevel")) {
      copy.put("modelLevel", level.name());
    }
    return copy;
  }

  /**
   * Creates a lightweight model summary.
   *
   * @param model model record
   * @return model summary
   */
  public ModelSummary summary(ModelRecord model) {
    return new ModelSummary(
        model.id(),
        model.projectId(),
        model.level(),
        model.name(),
        Math.max(1, model.revision()),
        model.metamodelVersion(),
        model.migrationState(),
        model.createdAt(),
        model.updatedAt());
  }

  /**
   * Reads the stored source XMI sidecar for a model.
   *
   * @param model model record
   * @return optional source XMI bytes
   */
  public Optional<byte[]> sourceXmi(ModelRecord model) {
    return store.readBytes(sourceXmiPath(model.projectId(), model.level(), model.id()));
  }

  /**
   * Attaches source XMI bytes to an existing model and updates its sidecar hash.
   *
   * @param model model record
   * @param bytes source XMI bytes
   */
  public void attachSourceXmi(ModelRecord model, byte[] bytes) {
    if (bytes == null || bytes.length == 0) {
      return;
    }
    store.writeBytesAtomically(sourceXmiPath(model.projectId(), model.level(), model.id()), bytes);
    ModelRecord stored =
        store
            .read(modelPath(model.projectId(), model.level(), model.id()), ModelRecord.class)
            .orElse(model);
    ModelRecord updated =
        new ModelRecord(
            stored.id(),
            stored.projectId(),
            stored.level(),
            stored.name(),
            stored.modelJson(),
            textOrDefault(stored.metamodelVersion(), currentVersion(stored.level())),
            textOrDefault(stored.metamodelHash(), currentHash(stored.level())),
            Math.max(1, stored.revision()),
            hashBytes(bytes),
            textOrDefault(stored.migrationState(), "CURRENT"),
            stored.createdAt(),
            stored.updatedAt());
    store.write(modelPath(updated.projectId(), updated.level(), updated.id()), updated);
    writeModelIndex(updated);
  }

  /**
   * Produces canonical source XMI unless an existing requested source should be preserved.
   *
   * @param level model level
   * @param modelJson model JSON
   * @param requestedSourceXmi requested source XMI update
   * @return canonical source XMI update
   */
  private SourceXmiUpdate canonicalSourceXmi(
      ModelLevel level, JsonNode modelJson, SourceXmiUpdate requestedSourceXmi) {
    if (requestedSourceXmi != null
        && requestedSourceXmi.bytes() != null
        && !hasDiagramLayout(modelJson)) {
      return requestedSourceXmi;
    }
    byte[] bytes =
        xmiImportService.exportModel(
            level, hydrateSemanticReferences(withLayoutAnnotations(modelJson)));
    return new SourceXmiUpdate(bytes, hashBytes(bytes), null);
  }

  /**
   * Checks whether diagram coordinates are present and should be preserved in source XMI
   * annotations.
   *
   * @param modelJson model JSON
   * @return {@code true} when diagram layout exists
   */
  private boolean hasDiagramLayout(JsonNode modelJson) {
    JsonNode elements = modelJson == null ? null : modelJson.path("diagram").path("elements");
    if (elements == null || !elements.isArray()) {
      return false;
    }
    for (JsonNode element : elements) {
      if ((element.hasNonNull("x") || element.hasNonNull("y"))
          && !text(element, "id", "").isBlank()) {
        return true;
      }
    }
    return false;
  }

  /**
   * Adds layout annotations to semantic elements from diagram positions.
   *
   * @param modelJson model JSON
   * @return model JSON with layout annotations
   */
  private JsonNode withLayoutAnnotations(JsonNode modelJson) {
    if (!(modelJson instanceof ObjectNode)) {
      return modelJson;
    }
    Map<String, JsonNode> layoutById = new LinkedHashMap<>();
    JsonNode elements = modelJson.path("diagram").path("elements");
    if (elements.isArray()) {
      elements.forEach(
          element -> {
            String id = text(element, "id", "");
            if (!id.isBlank() && (element.hasNonNull("x") || element.hasNonNull("y"))) {
              layoutById.put(id, element);
            }
          });
    }
    if (layoutById.isEmpty()) {
      return modelJson;
    }
    ObjectNode copy = (ObjectNode) modelJson.deepCopy();
    applyLayoutAnnotations(copy, layoutById);
    return copy;
  }

  /**
   * Recursively applies layout annotations to matching semantic elements.
   *
   * @param node current JSON node
   * @param layoutById diagram layout nodes keyed by id
   */
  private void applyLayoutAnnotations(JsonNode node, Map<String, JsonNode> layoutById) {
    if (node == null || node.isNull()) {
      return;
    }
    if (node instanceof ObjectNode objectNode) {
      String id = text(objectNode, "id", "");
      JsonNode layout = layoutById.get(id);
      if (layout != null && !"Annotation".equals(text(objectNode, "eClass", ""))) {
        mergeLayoutAnnotations(objectNode, layout);
      }
      objectNode
          .fields()
          .forEachRemaining(entry -> applyLayoutAnnotations(entry.getValue(), layoutById));
      return;
    }
    if (node.isArray()) {
      node.forEach(child -> applyLayoutAnnotations(child, layoutById));
    }
  }

  /**
   * Replaces layout annotations on one semantic element.
   *
   * @param element semantic element JSON
   * @param layout diagram layout JSON
   */
  private void mergeLayoutAnnotations(ObjectNode element, JsonNode layout) {
    ArrayNode annotations = element.withArray("annotations");
    ArrayNode retained = store.objectMapper().createArrayNode();
    annotations.forEach(
        annotation -> {
          String key = text(annotation, "key", "");
          if (!"modless.layout.x".equals(key) && !"modless.layout.y".equals(key)) {
            retained.add(annotation.deepCopy());
          }
        });
    if (layout.hasNonNull("x")) {
      retained.add(layoutAnnotation("modless.layout.x", layout.path("x").asText()));
    }
    if (layout.hasNonNull("y")) {
      retained.add(layoutAnnotation("modless.layout.y", layout.path("y").asText()));
    }
    element.set("annotations", retained);
  }

  /**
   * Creates a layout annotation object for XMI round-tripping.
   *
   * @param key annotation key
   * @param value annotation value
   * @return annotation JSON
   */
  private ObjectNode layoutAnnotation(String key, String value) {
    ObjectNode annotation = store.objectMapper().createObjectNode();
    annotation.put("eClass", "Annotation");
    annotation.put("key", key);
    annotation.put("value", value);
    annotation.put("source", "modless.ui");
    return annotation;
  }

  /**
   * Removes transport-only fields before returning a model to clients.
   *
   * @param model stored model record
   * @return client-safe model record
   */
  private ModelRecord clientRecord(ModelRecord model) {
    JsonNode modelJson = model.modelJson();
    if (modelJson instanceof ObjectNode objectNode) {
      ObjectNode copy = objectNode.deepCopy();
      stripTransportOnlyFields(copy);
      copy.remove("_sourceXmiToken");
      modelJson = copy;
    }
    return new ModelRecord(
        model.id(),
        model.projectId(),
        model.level(),
        model.name(),
        modelJson,
        textOrDefault(model.metamodelVersion(), currentVersion(model.level())),
        textOrDefault(model.metamodelHash(), currentHash(model.level())),
        Math.max(1, model.revision()),
        model.sourceXmiHash(),
        textOrDefault(model.migrationState(), migrationState(model)),
        model.createdAt(),
        model.updatedAt());
  }

  /**
   * Returns a generated model record without stripping generated model fields.
   *
   * @param model stored generated model
   * @return client record for generated model response
   */
  private ModelRecord generatedClientRecord(ModelRecord model) {
    return new ModelRecord(
        model.id(),
        model.projectId(),
        model.level(),
        model.name(),
        model.modelJson(),
        textOrDefault(model.metamodelVersion(), currentVersion(model.level())),
        textOrDefault(model.metamodelHash(), currentHash(model.level())),
        Math.max(1, model.revision()),
        model.sourceXmiHash(),
        textOrDefault(model.migrationState(), migrationState(model)),
        model.createdAt(),
        model.updatedAt());
  }

  /**
   * Removes fields used only during transport/import.
   *
   * @param model model JSON to mutate
   */
  private void stripTransportOnlyFields(ObjectNode model) {
    model.remove("_sourceXmiBase64");
    JsonNode diagram = model.get("diagram");
    if (isGeneratedDiagramDuplicate(model.path("graph"), diagram)) {
      model.remove("diagram");
    }
  }

  /**
   * Detects duplicated generated diagram data that can be omitted from client responses.
   *
   * @param graph graph JSON
   * @param diagram diagram JSON
   * @return {@code true} when diagram duplicates graph content
   */
  private boolean isGeneratedDiagramDuplicate(JsonNode graph, JsonNode diagram) {
    if (graph == null || diagram == null || !graph.isObject() || !diagram.isObject()) {
      return false;
    }
    JsonNode graphElements = graph.path("elements");
    JsonNode graphRelationships = graph.path("relationships");
    JsonNode diagramElements = diagram.path("elements");
    JsonNode diagramRelationships = diagram.path("relationships");
    if (!graphElements.isArray()
        || !graphRelationships.isArray()
        || !diagramElements.isArray()
        || !diagramRelationships.isArray()) {
      return false;
    }
    return graphElements.equals(diagramElements)
        && sameRelationshipEndpoints(graphRelationships, diagramRelationships);
  }

  /**
   * Compares relationship endpoint fields between graph and diagram arrays.
   *
   * @param graphRelationships graph relationship array
   * @param diagramRelationships diagram relationship array
   * @return {@code true} when ids, kinds, and endpoints match
   */
  private boolean sameRelationshipEndpoints(
      JsonNode graphRelationships, JsonNode diagramRelationships) {
    if (graphRelationships.size() != diagramRelationships.size()) {
      return false;
    }
    for (int i = 0; i < graphRelationships.size(); i++) {
      JsonNode graphRelationship = graphRelationships.get(i);
      JsonNode diagramRelationship = diagramRelationships.get(i);
      if (!text(graphRelationship, "id", "").equals(text(diagramRelationship, "id", ""))
          || !text(graphRelationship, "kind", "").equals(text(diagramRelationship, "kind", ""))
          || !text(graphRelationship, "source", "").equals(text(diagramRelationship, "source", ""))
          || !text(graphRelationship, "target", "")
              .equals(text(diagramRelationship, "target", ""))) {
        return false;
      }
    }
    return true;
  }

  /**
   * Stages source XMI bytes for later model creation or update.
   *
   * @param user requesting user
   * @param projectId project identifier
   * @param level model level
   * @param bytes source XMI bytes
   * @return staging token
   */
  private String stageSourceXmi(UserRecord user, String projectId, ModelLevel level, byte[] bytes) {
    String token = UUID.randomUUID().toString();
    Instant now = Instant.now();
    Path xmiPath = stagedSourceXmiPath(token);
    store.write(
        stagedSourceXmiRecordPath(token),
        new StagedImportRecord(
            token,
            user == null ? "" : user.id(),
            projectId == null ? "" : projectId,
            level,
            xmiPath.toString().replace('\\', '/'),
            bytes == null ? 0 : bytes.length,
            now,
            now.plus(runtimeOptions.stagedImportTtl())));
    store.writeBytesAtomically(xmiPath, bytes);
    return token;
  }

  /**
   * Removes and returns a staged source XMI token from model JSON.
   *
   * @param modelJson model JSON
   * @return token or empty string
   */
  private String removeSourceXmiToken(JsonNode modelJson) {
    if (modelJson instanceof ObjectNode objectNode) {
      String token = text(objectNode, "_sourceXmiToken", "");
      objectNode.remove("_sourceXmiToken");
      return token;
    }
    return "";
  }

  /**
   * Resolves source XMI bytes from inline Base64, a staged token, preservation, or deletion
   * semantics.
   *
   * @param user requesting user
   * @param projectId project identifier
   * @param level model level
   * @param modelJson model JSON to consume transport fields from
   * @param preserveExistingWhenMissing whether a missing source should preserve current sidecar
   * @return source XMI update instruction
   */
  private SourceXmiUpdate resolveSourceXmiUpdate(
      UserRecord user,
      String projectId,
      ModelLevel level,
      JsonNode modelJson,
      boolean preserveExistingWhenMissing) {
    byte[] inlineBytes = removeSourceXmiBase64(modelJson);
    if (inlineBytes != null && inlineBytes.length > 0) {
      return new SourceXmiUpdate(inlineBytes, hashBytes(inlineBytes), null);
    }
    String token = removeSourceXmiToken(modelJson);
    if (token == null || token.isBlank()) {
      return preserveExistingWhenMissing
          ? SourceXmiUpdate.preserveUpdate()
          : SourceXmiUpdate.deleteUpdate();
    }
    StagedImportRecord record =
        store.require(
            stagedSourceXmiRecordPath(token),
            StagedImportRecord.class,
            "Staged XMI import token was not found.");
    if (record.expiresAt() != null && record.expiresAt().isBefore(Instant.now())) {
      cleanupStagedImport(record);
      throw new PlatformException(410, "Staged XMI import token has expired.");
    }
    if (record.level() != level) {
      throw new PlatformException(400, "Staged XMI import token is for a different level.");
    }
    if (record.userId() != null
        && !record.userId().isBlank()
        && (user == null || !record.userId().equals(user.id()))) {
      throw new PlatformException(403, "Staged XMI import token belongs to another user.");
    }
    if (record.projectId() != null
        && !record.projectId().isBlank()
        && !record.projectId().equals(projectId)) {
      throw new PlatformException(403, "Staged XMI import token belongs to another project.");
    }
    byte[] bytes =
        store
            .readBytes(Path.of(record.xmiPath()))
            .orElseThrow(
                () -> new PlatformException(410, "Staged XMI import file is no longer available."));
    return new SourceXmiUpdate(bytes, hashBytes(bytes), record);
  }

  /**
   * Removes and decodes inline Base64 source XMI from model JSON.
   *
   * @param modelJson model JSON
   * @return decoded bytes, or {@code null}
   */
  private byte[] removeSourceXmiBase64(JsonNode modelJson) {
    if (!(modelJson instanceof ObjectNode objectNode)) {
      return null;
    }
    String encoded = text(objectNode, "_sourceXmiBase64", "");
    objectNode.remove("_sourceXmiBase64");
    if (encoded.isBlank()) {
      return null;
    }
    try {
      return Base64.getDecoder().decode(encoded);
    } catch (IllegalArgumentException ex) {
      throw new PlatformException(400, "Imported source XMI payload is not valid base64.");
    }
  }

  /**
   * Persists model JSON and source XMI as a best-effort transaction with rollback snapshots.
   *
   * @param previous previous model record, or {@code null} for create
   * @param model model record to persist
   * @param sourceXmi source XMI update instruction
   */
  private void persistModelAndSourceXmi(
      ModelRecord previous, ModelRecord model, SourceXmiUpdate sourceXmi) {
    Path modelPath = modelPath(model.projectId(), model.level(), model.id());
    Path sourcePath = sourceXmiPath(model.projectId(), model.level(), model.id());
    byte[] previousModelBytes = readBytesIfPresent(modelPath);
    byte[] previousSourceBytes = readBytesIfPresent(sourcePath);
    boolean hadPreviousModel = previous != null && previousModelBytes != null;
    boolean hadPreviousSource = previousSourceBytes != null;
    try {
      store.write(modelPath, model);
      writeModelIndex(model);
      if (sourceXmi.shouldPreserve()) {
        return;
      }
      if (sourceXmi.shouldDelete()) {
        store.deleteIfExists(sourcePath);
      } else {
        store.writeBytesAtomically(sourcePath, sourceXmi.bytes());
        cleanupStagedImport(sourceXmi.record());
      }
    } catch (RuntimeException ex) {
      rollback(
          modelPath,
          sourcePath,
          previousModelBytes,
          previousSourceBytes,
          hadPreviousModel,
          hadPreviousSource,
          previous == null ? model.id() : null);
      throw ex;
    }
  }

  /**
   * Restores model and source sidecar bytes after a failed persistence step.
   *
   * @param modelPath model JSON path
   * @param sourcePath source XMI path
   * @param previousModelBytes previous model bytes
   * @param previousSourceBytes previous source bytes
   * @param hadPreviousModel whether a previous model existed
   * @param hadPreviousSource whether a previous source sidecar existed
   * @param newModelId new model id whose index should be deleted on rollback
   */
  private void rollback(
      Path modelPath,
      Path sourcePath,
      byte[] previousModelBytes,
      byte[] previousSourceBytes,
      boolean hadPreviousModel,
      boolean hadPreviousSource,
      String newModelId) {
    try {
      if (hadPreviousModel) {
        store.writeBytesAtomically(modelPath, previousModelBytes);
      } else {
        store.deleteIfExists(modelPath);
      }
      if (hadPreviousSource) {
        store.writeBytesAtomically(sourcePath, previousSourceBytes);
      } else {
        store.deleteIfExists(sourcePath);
      }
      if (newModelId != null) {
        store.deleteIfExists(modelIndexPath(newModelId));
      }
    } catch (RuntimeException ignored) {
      // Preserve the original persistence failure.
    }
  }

  /**
   * Reads raw repository bytes when a file exists.
   *
   * @param path repository-relative path
   * @return file bytes or {@code null}
   */
  private byte[] readBytesIfPresent(Path path) {
    return store.readBytes(path).orElse(null);
  }

  /**
   * Writes the global model index entry.
   *
   * @param model model to index
   */
  private void writeModelIndex(ModelRecord model) {
    store.write(
        modelIndexPath(model.id()),
        new ModelIndexRecord(model.id(), model.projectId(), model.level()));
  }

  /**
   * Searches legacy per-project model locations and writes an index entry after a match is found.
   *
   * @param level model level
   * @param id model identifier
   * @return model record
   */
  private ModelRecord findLegacyAndIndex(ModelLevel level, String id) {
    try {
      ModelRecord model =
          store.list(Path.of("projects"), ProjectRecord.class).stream()
              .map(
                  project ->
                      store
                          .read(
                              Path.of(
                                  "projects",
                                  project.id(),
                                  "models",
                                  level.apiName(),
                                  id + ".json"),
                              ModelRecord.class)
                          .orElse(null))
              .filter(candidate -> candidate != null)
              .findFirst()
              .orElseThrow(() -> new PlatformException(404, "Model not found."));
      writeModelIndex(model);
      return repairMetadataIfNeeded(model);
    } catch (PlatformException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new PlatformException(500, "Could not read model store.");
    }
  }

  /**
   * Backfills metamodel metadata and migration state on older model records.
   *
   * @param model model record
   * @return original or repaired model
   */
  private ModelRecord repairMetadataIfNeeded(ModelRecord model) {
    if (model == null) {
      return null;
    }
    MetamodelDescriptor metamodel = metamodelResolver.resolve(model.level());
    long revision = Math.max(1, model.revision());
    String migrationState =
        model.metamodelHash() == null
                || model.metamodelHash().isBlank()
                || metamodel.sha256().equals(model.metamodelHash())
            ? "CURRENT"
            : "NEEDS_MIGRATION";
    if (model.metamodelVersion() != null
        && !model.metamodelVersion().isBlank()
        && model.metamodelHash() != null
        && !model.metamodelHash().isBlank()
        && model.revision() > 0
        && model.migrationState() != null
        && !model.migrationState().isBlank()) {
      return model;
    }
    ModelRecord repaired =
        new ModelRecord(
            model.id(),
            model.projectId(),
            model.level(),
            model.name(),
            model.modelJson(),
            metamodel.version(),
            metamodel.sha256(),
            revision,
            model.sourceXmiHash(),
            migrationState,
            model.createdAt(),
            model.updatedAt());
    store.write(modelPath(model.projectId(), model.level(), model.id()), repaired);
    writeModelIndex(repaired);
    return repaired;
  }

  /**
   * Enforces an optional optimistic revision precondition.
   *
   * @param existing existing model
   * @param expectedRevision expected revision, or {@code null}
   */
  private void requireExpectedRevision(ModelRecord existing, Long expectedRevision) {
    if (expectedRevision == null) {
      return;
    }
    if (expectedRevision.longValue() != existing.revision()) {
      throw new PlatformException(409, "Model was modified by another operation.");
    }
  }

  /**
   * Computes the next persisted revision.
   *
   * @param existing existing model
   * @return next revision number
   */
  private long nextRevision(ModelRecord existing) {
    return Math.max(1, existing.revision()) + 1;
  }

  /**
   * Returns the current metamodel version for a level.
   *
   * @param level model level
   * @return current metamodel version
   */
  private String currentVersion(ModelLevel level) {
    return metamodelResolver.resolve(level).version();
  }

  /**
   * Returns the current metamodel hash for a level.
   *
   * @param level model level
   * @return current metamodel hash
   */
  private String currentHash(ModelLevel level) {
    return metamodelResolver.resolve(level).sha256();
  }

  /**
   * Computes migration state for a model relative to the current metamodel.
   *
   * @param model model record
   * @return migration state
   */
  private String migrationState(ModelRecord model) {
    String hash = model.metamodelHash();
    return hash == null || hash.isBlank() || hash.equals(currentHash(model.level()))
        ? "CURRENT"
        : "NEEDS_MIGRATION";
  }

  /**
   * Deletes a staged import record and its source XMI file.
   *
   * @param record staged import record
   */
  private void cleanupStagedImport(StagedImportRecord record) {
    if (record == null) {
      return;
    }
    store.deleteIfExists(Path.of(record.xmiPath()));
    store.deleteIfExists(stagedSourceXmiRecordPath(record.token()));
  }

  /** Removes expired staged XMI imports. */
  public void cleanupExpiredImports() {
    try {
      store.list(Path.of("model-imports"), StagedImportRecord.class).stream()
          .filter(
              record -> record.expiresAt() != null && record.expiresAt().isBefore(Instant.now()))
          .forEach(this::cleanupStagedImport);
    } catch (Exception ex) {
      throw new PlatformException(500, "Could not clean up staged imports.");
    }
  }

  /**
   * Hashes raw bytes with SHA-256.
   *
   * @param bytes bytes to hash
   * @return lowercase hexadecimal digest
   */
  private String hashBytes(byte[] bytes) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(bytes == null ? new byte[0] : bytes));
    } catch (Exception ex) {
      throw new PlatformException(500, "Could not hash source XMI.");
    }
  }

  /**
   * Builds a validation cache key from model identity and content metadata.
   *
   * @param model model record
   * @return cache key
   */
  private String validationCacheKey(ModelRecord model) {
    return model.level().name()
        + ":"
        + model.id()
        + ":"
        + model.revision()
        + ":"
        + String.valueOf(model.sourceXmiHash());
  }

  /**
   * Returns the repository directory for models at a level within a project.
   *
   * @param projectId project identifier
   * @param level model level
   * @return repository-relative directory
   */
  private Path modelDir(String projectId, ModelLevel level) {
    return Path.of("projects", projectId, "models", level.apiName());
  }

  /**
   * Reads only summary fields from a model JSON file.
   *
   * @param path resolved model JSON path
   * @param fallbackLevel level used when the record omits level
   * @return model summary, or {@code null} when the file cannot be summarized
   */
  private ModelSummary readSummary(Path path, ModelLevel fallbackLevel) {
    try (JsonParser parser = store.objectMapper().getFactory().createParser(path.toFile())) {
      String id = null;
      String projectId = null;
      ModelLevel level = fallbackLevel;
      String name = null;
      long revision = 1;
      String metamodelVersion = null;
      String migrationState = null;
      Instant updatedAt = Files.getLastModifiedTime(path).toInstant();
      Instant createdAt = updatedAt;
      if (parser.nextToken() != JsonToken.START_OBJECT) {
        return null;
      }
      while (parser.nextToken() != JsonToken.END_OBJECT) {
        String field = parser.currentName();
        parser.nextToken();
        if ("id".equals(field)) {
          id = parser.getValueAsString();
        } else if ("projectId".equals(field)) {
          projectId = parser.getValueAsString();
        } else if ("level".equals(field)) {
          level = ModelLevel.valueOf(parser.getValueAsString());
        } else if ("name".equals(field)) {
          name = parser.getValueAsString();
        } else if ("revision".equals(field)) {
          revision = Math.max(1, parser.getLongValue());
        } else if ("metamodelVersion".equals(field)) {
          metamodelVersion = parser.getValueAsString();
        } else if ("migrationState".equals(field)) {
          migrationState = parser.getValueAsString();
        } else if ("createdAt".equals(field)) {
          createdAt = parseInstant(parser.getValueAsString(), createdAt);
        } else if ("updatedAt".equals(field)) {
          updatedAt = parseInstant(parser.getValueAsString(), updatedAt);
        } else if ("modelJson".equals(field)) {
          break;
        } else {
          parser.skipChildren();
        }
      }
      if (id == null || projectId == null) {
        return null;
      }
      return new ModelSummary(
          id,
          projectId,
          level,
          name == null ? id : name,
          revision,
          metamodelVersion,
          migrationState,
          createdAt,
          updatedAt);
    } catch (Exception ex) {
      return null;
    }
  }

  /**
   * Parses an instant with a fallback for absent or legacy values.
   *
   * @param value raw instant text
   * @param fallback fallback timestamp
   * @return parsed or fallback timestamp
   */
  private Instant parseInstant(String value, Instant fallback) {
    try {
      return value == null || value.isBlank() ? fallback : Instant.parse(value);
    } catch (Exception ex) {
      return fallback;
    }
  }

  /**
   * Returns the repository path for a model JSON record.
   *
   * @param projectId project identifier
   * @param level model level
   * @param id model identifier
   * @return repository-relative path
   */
  private Path modelPath(String projectId, ModelLevel level, String id) {
    return modelDir(projectId, level).resolve(id + ".json");
  }

  /**
   * Returns the repository path for a source XMI sidecar.
   *
   * @param projectId project identifier
   * @param level model level
   * @param id model identifier
   * @return repository-relative path
   */
  private Path sourceXmiPath(String projectId, ModelLevel level, String id) {
    return modelDir(projectId, level).resolve(id + ".xmi");
  }

  /**
   * Returns the repository path for staged source XMI bytes.
   *
   * @param token staging token
   * @return repository-relative path
   */
  private Path stagedSourceXmiPath(String token) {
    return Path.of("model-imports", token + ".xmi");
  }

  /**
   * Returns the repository path for a staged source XMI metadata record.
   *
   * @param token staging token
   * @return repository-relative path
   */
  private Path stagedSourceXmiRecordPath(String token) {
    return Path.of("model-imports", token + ".json");
  }

  /**
   * Returns the repository path for a model index record.
   *
   * @param id model identifier
   * @return repository-relative path
   */
  private Path modelIndexPath(String id) {
    return Path.of("indexes", "models", id + ".json");
  }

  /**
   * Validates and defaults a model name.
   *
   * @param name candidate name
   * @param level model level
   * @return trimmed or default model name
   */
  private String requireName(String name, ModelLevel level) {
    return name == null || name.trim().isEmpty() ? level.apiName() + "-model" : name.trim();
  }

  /**
   * Applies a single supported JSON patch operation to a model object.
   *
   * @param root model root object
   * @param operation patch operation
   */
  private void applyPatchOperation(ObjectNode root, ModelPatchOperation operation) {
    if (operation == null || operation.op() == null || operation.path() == null) {
      throw new PlatformException(400, "Patch operations require op and path.");
    }
    String op = operation.op().trim().toLowerCase();
    String path = operation.path().trim();
    if (!path.startsWith("/")) {
      throw new PlatformException(400, "Patch paths must be JSON Pointer paths.");
    }
    switch (op) {
      case "add" -> addPatchValue(root, path, operation.value());
      case "replace" -> replacePatchValue(root, path, operation.value());
      case "remove" -> removePatchValue(root, path);
      default -> throw new PlatformException(400, "Unsupported patch operation: " + operation.op());
    }
  }

  /**
   * Adds a value to an object field or array position.
   *
   * @param root model root object
   * @param path JSON Pointer path
   * @param value value to add
   */
  private void addPatchValue(ObjectNode root, String path, JsonNode value) {
    JsonNode parent = patchParent(root, path);
    String key = lastPointerSegment(path);
    JsonNode copy = value == null ? store.objectMapper().nullNode() : value.deepCopy();
    if (parent instanceof ObjectNode objectNode) {
      objectNode.set(key, copy);
      return;
    }
    if (parent instanceof ArrayNode arrayNode) {
      if ("-".equals(key)) {
        arrayNode.add(copy);
        return;
      }
      int index = arrayIndex(key, arrayNode.size());
      arrayNode.insert(index, copy);
      return;
    }
    throw new PlatformException(400, "Patch parent is not writable.");
  }

  /**
   * Replaces an existing object field or array item.
   *
   * @param root model root object
   * @param path JSON Pointer path
   * @param value replacement value
   */
  private void replacePatchValue(ObjectNode root, String path, JsonNode value) {
    JsonNode parent = patchParent(root, path);
    String key = lastPointerSegment(path);
    JsonNode copy = value == null ? store.objectMapper().nullNode() : value.deepCopy();
    if (parent instanceof ObjectNode objectNode) {
      if (!objectNode.has(key)) {
        throw new PlatformException(400, "Patch replace path does not exist: " + path);
      }
      objectNode.set(key, copy);
      return;
    }
    if (parent instanceof ArrayNode arrayNode) {
      int index = arrayIndex(key, arrayNode.size() - 1);
      arrayNode.set(index, copy);
      return;
    }
    throw new PlatformException(400, "Patch parent is not writable.");
  }

  /**
   * Removes an object field or array item.
   *
   * @param root model root object
   * @param path JSON Pointer path
   */
  private void removePatchValue(ObjectNode root, String path) {
    JsonNode parent = patchParent(root, path);
    String key = lastPointerSegment(path);
    if (parent instanceof ObjectNode objectNode) {
      objectNode.remove(key);
      return;
    }
    if (parent instanceof ArrayNode arrayNode) {
      arrayNode.remove(arrayIndex(key, arrayNode.size() - 1));
      return;
    }
    throw new PlatformException(400, "Patch parent is not writable.");
  }

  /**
   * Resolves the parent node for a JSON Pointer patch path.
   *
   * @param root model root object
   * @param path JSON Pointer path
   * @return parent node
   */
  private JsonNode patchParent(ObjectNode root, String path) {
    String[] segments = pointerSegments(path);
    if (segments.length == 0) {
      throw new PlatformException(400, "Root model replacement is not supported by patch.");
    }
    JsonNode current = root;
    for (int i = 0; i < segments.length - 1; i++) {
      if (current instanceof ObjectNode objectNode) {
        current = objectNode.get(segments[i]);
      } else if (current instanceof ArrayNode arrayNode) {
        current = arrayNode.get(arrayIndex(segments[i], arrayNode.size() - 1));
      } else {
        current = null;
      }
      if (current == null || current.isMissingNode() || current.isNull()) {
        throw new PlatformException(400, "Patch parent path does not exist: " + path);
      }
    }
    return current;
  }

  /**
   * Splits and unescapes a JSON Pointer path.
   *
   * @param path JSON Pointer path
   * @return unescaped path segments
   */
  private String[] pointerSegments(String path) {
    if ("/".equals(path)) {
      return new String[] {""};
    }
    String[] raw = path.substring(1).split("/", -1);
    for (int i = 0; i < raw.length; i++) {
      raw[i] = raw[i].replace("~1", "/").replace("~0", "~");
    }
    return raw;
  }

  /**
   * Returns the final segment of a JSON Pointer path.
   *
   * @param path JSON Pointer path
   * @return final segment
   */
  private String lastPointerSegment(String path) {
    String[] segments = pointerSegments(path);
    return segments[segments.length - 1];
  }

  /**
   * Parses and bounds-checks an array index.
   *
   * @param value raw index value
   * @param maxInclusive maximum allowed index
   * @return parsed index
   */
  private int arrayIndex(String value, int maxInclusive) {
    try {
      int index = Integer.parseInt(value);
      if (index < 0 || index > maxInclusive) {
        throw new PlatformException(400, "Patch array index is out of bounds.");
      }
      return index;
    } catch (NumberFormatException ex) {
      throw new PlatformException(400, "Patch array index must be numeric.");
    }
  }

  /**
   * Validation result returned by model validation calls.
   *
   * @param valid whether no error issues were reported
   * @param issues validation issues
   */
  public record ValidationResult(boolean valid, List<ValidationIssue> issues) {}

  /**
   * Platform validation issue suitable for API responses.
   *
   * @param severity issue severity
   * @param constraint constraint or diagnostic identifier
   * @param issueClass issue class or model context
   * @param message user-facing message
   * @param guidance optional remediation guidance
   * @param elementId related element id
   * @param elementName related element name or file
   */
  public record ValidationIssue(
      String severity,
      String constraint,
      String issueClass,
      String message,
      String guidance,
      String elementId,
      String elementName) {}

  /**
   * Result produced after importing a model payload.
   *
   * @param name derived model name
   * @param modelJson imported model JSON
   * @param issues validation issues
   */
  public record ImportResult(String name, JsonNode modelJson, List<ValidationIssue> issues) {}

  /**
   * Supported JSON patch operation.
   *
   * @param op operation name, {@code add}, {@code replace}, or {@code remove}
   * @param path JSON Pointer path
   * @param value value used by add and replace operations
   */
  public record ModelPatchOperation(String op, String path, JsonNode value) {}

  /**
   * Lightweight model summary used for list responses.
   *
   * @param id model identifier
   * @param projectId owning project identifier
   * @param level model level
   * @param name display name
   * @param revision optimistic concurrency revision
   * @param metamodelVersion metamodel version at persistence time
   * @param migrationState migration state relative to current metamodel
   * @param createdAt creation timestamp
   * @param updatedAt last update timestamp
   */
  public record ModelSummary(
      String id,
      String projectId,
      ModelLevel level,
      String name,
      long revision,
      String metamodelVersion,
      String migrationState,
      Instant createdAt,
      Instant updatedAt) {}

  /**
   * Source XMI persistence instruction used while writing model records.
   *
   * @param bytes source XMI bytes, or {@code null} for preserve/delete operations
   * @param hash source XMI hash, {@code null} to preserve, empty to delete
   * @param record staged import record to clean up after commit
   */
  private record SourceXmiUpdate(byte[] bytes, String hash, StagedImportRecord record) {

    /**
     * Creates an instruction to preserve the existing sidecar.
     *
     * @return preserve instruction
     */
    static SourceXmiUpdate preserveUpdate() {
      return new SourceXmiUpdate(null, null, null);
    }

    /**
     * Creates an instruction to delete the existing sidecar.
     *
     * @return delete instruction
     */
    static SourceXmiUpdate deleteUpdate() {
      return new SourceXmiUpdate(null, "", null);
    }

    /**
     * Indicates whether the current sidecar should be preserved.
     *
     * @return {@code true} for preserve instruction
     */
    boolean shouldPreserve() {
      return bytes == null && hash == null;
    }

    /**
     * Indicates whether the current sidecar should be deleted.
     *
     * @return {@code true} for delete instruction
     */
    boolean shouldDelete() {
      return bytes == null && hash != null;
    }

    /**
     * Returns the hash that should be stored on the model record.
     *
     * @param previousHash previous sidecar hash
     * @return effective hash
     */
    String effectiveHash(String previousHash) {
      return shouldPreserve() ? previousHash : hash;
    }
  }
}
