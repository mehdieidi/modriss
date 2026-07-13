package io.mehdieidi.varka.platform.model.application;

import io.mehdieidi.varka.platform.identity.domain.UserRecord;
import io.mehdieidi.varka.platform.kernel.ModelLevel;
import io.mehdieidi.varka.platform.kernel.PlatformException;
import io.mehdieidi.varka.platform.model.domain.ModelIndexRecord;
import io.mehdieidi.varka.platform.model.domain.ModelRecord;
import io.mehdieidi.varka.platform.modeling.config.ModelingConfigService;
import io.mehdieidi.varka.platform.modeling.metamodel.FileMetamodelResolver;
import io.mehdieidi.varka.platform.modeling.metamodel.MetamodelDescriptor;
import io.mehdieidi.varka.platform.modeling.metamodel.MetamodelResolver;
import io.mehdieidi.varka.platform.modeling.runtime.MdeRuntimeOptions;
import io.mehdieidi.varka.platform.modeling.runtime.MdeRuntimePaths;
import io.mehdieidi.varka.platform.modeling.xmi.XmiModelImportService;
import io.mehdieidi.varka.platform.project.application.ProjectService;
import io.mehdieidi.varka.platform.project.domain.ProjectRecord;
import io.mehdieidi.varka.platform.storage.api.PlatformStore;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

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

  /** Model import, export, and XMI sidecar operations. */
  private final ModelImportExportService importExport;

  /** Model validation operations. */
  private final ModelValidationService validationService;

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
    XmiModelImportService xmiImportService =
        new XmiModelImportService(store.objectMapper(), this.metamodelResolver, modelingConfig);
    this.importExport =
        new ModelImportExportService(
            store, this.runtimeOptions, this.metamodelResolver, xmiImportService);
    this.validationService =
        new ModelValidationService(
            modelingConfig,
            this.runtimeOptions,
            this.mdePaths,
            this.metamodelResolver,
            xmiImportService,
            this.importExport::hydrateSemanticReferences);
    this.importExport.setValidationService(this.validationService);
  }

  /**
   * Returns the lock service shared with transformation operations.
   *
   * @return model lock service
   */
  public ModelLockService modelLocks() {
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
   * Loads a model for the client, optionally omitting persisted view payloads so the frontend can
   * materialize views lazily from the graph.
   *
   * @param user requesting user
   * @param level model level
   * @param id model identifier
   * @param includeViews whether persisted views and fragments should be included
   * @return client-safe model record
   */
  public ModelRecord getForClient(
      UserRecord user, ModelLevel level, String id, boolean includeViews) {
    ModelRecord record = get(user, level, id);
    return includeViews ? record : withoutViewPayload(record);
  }

  /**
   * Returns one persisted view from a stored model.
   *
   * @param user requesting user
   * @param level model level
   * @param id model identifier
   * @param viewId view identifier
   * @return stored view JSON
   */
  public JsonNode getStoredView(UserRecord user, ModelLevel level, String id, String viewId) {
    ModelRecord record = get(user, level, id);
    JsonNode views = record.modelJson().path("views");
    if (!views.isArray()) {
      throw new PlatformException(404, "Model has no stored views.");
    }
    for (JsonNode view : views) {
      if (viewId.equals(view.path("id").asText(""))) {
        return view;
      }
    }
    throw new PlatformException(404, "View not found: " + viewId);
  }

  private ModelRecord withoutViewPayload(ModelRecord record) {
    if (!(record.modelJson() instanceof ObjectNode objectNode)) {
      return record;
    }
    ObjectNode copy = objectNode.deepCopy();
    copy.remove("views");
    copy.remove("fragments");
    return new ModelRecord(
        record.id(),
        record.projectId(),
        record.level(),
        record.name(),
        copy,
        record.metamodelVersion(),
        record.metamodelHash(),
        record.revision(),
        record.sourceXmiHash(),
        record.migrationState(),
        record.createdAt(),
        record.updatedAt());
  }

  /**
   * Loads a model for transformation, preserving server-only metadata needed by MDE flows.
   *
   * @param user requesting user
   * @param level model level
   * @param id model identifier
   * @return model record repaired for internal use
   */
  public ModelRecord getForTransformation(UserRecord user, ModelLevel level, String id) {
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
    if (projectId == null || projectId.isBlank()) {
      throw new PlatformException(400, "A project id is required to create a model.");
    }
    ProjectRecord project = projectService.get(user, projectId);
    projectService.requireEditor(project, user.id());
    Instant now = Instant.now();
    JsonNode normalizedModel = importExport.normalizeModel(name, level, modelJson);
    ModelImportExportService.SourceXmiUpdate sourceXmi =
        sourceXmiBytes == null || sourceXmiBytes.length == 0
            ? importExport.resolveSourceXmiUpdate(user, projectId, level, normalizedModel, false)
            : new ModelImportExportService.SourceXmiUpdate(
                sourceXmiBytes, importExport.hashBytes(sourceXmiBytes), null);
    sourceXmi = importExport.canonicalSourceXmi(level, normalizedModel, sourceXmi);
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
  public ModelRecord createGenerated(
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
    if (!normalizedModel.has("views") || !normalizedModel.get("views").isArray()) {
      normalizedModel.set("views", store.objectMapper().createArrayNode());
    }
    ModelImportExportService.SourceXmiUpdate sourceXmi =
        sourceXmiBytes == null || sourceXmiBytes.length == 0
            ? importExport.resolveSourceXmiUpdate(user, projectId, level, normalizedModel, false)
            : new ModelImportExportService.SourceXmiUpdate(
                sourceXmiBytes, importExport.hashBytes(sourceXmiBytes), null);
    sourceXmi = importExport.canonicalSourceXmi(level, normalizedModel, sourceXmi);
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
    projectService.setActiveModel(user, project.id(), level, model.id());
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
          JsonNode normalizedModel = importExport.normalizeModel(name, level, modelJson);
          ModelImportExportService.SourceXmiUpdate sourceXmi =
              importExport.resolveSourceXmiUpdate(
                  user, existing.projectId(), level, normalizedModel, true);
          if (!(sourceXmi.shouldPreserve()
              && importExport.sourceXmi(existing).isPresent()
              && !importExport.hasDiagramLayout(normalizedModel))) {
            sourceXmi = importExport.canonicalSourceXmi(level, normalizedModel, sourceXmi);
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
              importExport.normalizeModel(
                  name == null ? existing.name() : name, level, patchedModel);
          ModelImportExportService.SourceXmiUpdate sourceXmi =
              importExport.resolveSourceXmiUpdate(
                  user, existing.projectId(), level, normalizedModel, true);
          if (!(sourceXmi.shouldPreserve()
              && importExport.sourceXmi(existing).isPresent()
              && !importExport.hasDiagramLayout(normalizedModel))) {
            sourceXmi = importExport.canonicalSourceXmi(level, normalizedModel, sourceXmi);
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
          store.deleteIfExists(importExport.sourceXmiPath(model.projectId(), level, id));
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
    return validationService.validate(level, modelJson);
  }

  /**
   * Validates model JSON against the Ecore metamodel without executing EVL semantic constraints.
   *
   * @param level model level
   * @param modelJson model JSON
   * @return structural validation result
   */
  public ValidationResult validateStructural(ModelLevel level, JsonNode modelJson) {
    return validationService.validateStructural(level, modelJson);
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
    validationService.resetValidationTimings();
    long validationStarted = System.nanoTime();
    long phaseStarted = System.nanoTime();
    ModelRecord model = get(user, level, id);
    validationService.addValidationTiming(
        "validation.modelLookupMs", System.nanoTime() - phaseStarted);
    phaseStarted = System.nanoTime();
    String cacheKey = validationService.validationCacheKey(model);
    ValidationResult cached = validationService.cachedResult(cacheKey);
    validationService.addValidationTiming(
        "validation.cacheLookupMs", System.nanoTime() - phaseStarted);
    if (cached != null) {
      validationService.addValidationTiming("validation.cacheHitMs", 0L);
      validationService.addValidationTiming(
          "validation.totalMs", System.nanoTime() - validationStarted);
      return cached;
    }
    phaseStarted = System.nanoTime();
    Optional<byte[]> xmiBytes = importExport.sourceXmi(model);
    validationService.addValidationTiming(
        "validation.sourceXmiReadMs", System.nanoTime() - phaseStarted);
    ValidationResult result;
    if (xmiBytes.isPresent()) {
      result = validateGeneratedXmi(level, xmiBytes.get());
      if (validationService.hasRecoverableStaleSourceError(result)) {
        ValidationResult repairedResult = validateRegeneratedSourceXmi(model);
        if (!validationService.hasRecoverableStaleSourceError(repairedResult)) {
          result = repairedResult;
        }
      }
    } else {
      // Legacy JSON-only records are converted in memory so existing projects remain usable.
      result = validate(level, model.modelJson());
    }
    phaseStarted = System.nanoTime();
    validationService.putCachedResult(cacheKey, result);
    validationService.addValidationTiming(
        "validation.cacheWriteMs", System.nanoTime() - phaseStarted);
    validationService.addValidationTiming(
        "validation.totalMs", System.nanoTime() - validationStarted);
    return result;
  }

  /**
   * Returns and clears timing data captured by the last stored validation on the current thread.
   *
   * @return immutable timing map
   */
  public static Map<String, Long> consumeLastValidationTimings() {
    return ModelValidationService.consumeLastValidationTimings();
  }

  /**
   * Regenerates a source XMI sidecar for validation when stored XMI appears stale but recoverable.
   *
   * @param model stored model
   * @return validation result from regenerated XMI or JSON fallback
   */
  private ValidationResult validateRegeneratedSourceXmi(ModelRecord model) {
    try {
      long phaseStarted = System.nanoTime();
      ModelImportExportService.SourceXmiUpdate sourceXmi =
          importExport.canonicalSourceXmi(model.level(), model.modelJson(), null);
      validationService.addValidationTiming(
          "validation.sourceXmiRegenerateMs", System.nanoTime() - phaseStarted);
      ValidationResult result = validateGeneratedXmi(model.level(), sourceXmi.bytes());
      if (!validationService.hasRecoverableStaleSourceError(result)) {
        phaseStarted = System.nanoTime();
        importExport.attachSourceXmi(model, sourceXmi.bytes());
        validationService.addValidationTiming(
            "validation.sourceXmiAttachMs", System.nanoTime() - phaseStarted);
        return result;
      }
      // The semantic JSON already contains the hydrated graph endpoints used by the editor. If a
      // regenerated sidecar still cannot resolve them, validate that canonical in-memory model
      // instead of surfacing duplicate stale-XMI endpoint errors to the user.
      ValidationResult jsonResult = validate(model.level(), model.modelJson());
      if (validationService.hasOnlyRecoverableStaleSourceErrors(jsonResult)) {
        return new ValidationResult(true, List.of());
      }
      return jsonResult;
    } catch (RuntimeException ex) {
      return validate(model.level(), model.modelJson());
    }
  }

  /**
   * Validates generated or uploaded XMI bytes with EVL.
   *
   * @param level model level
   * @param xmiBytes XMI payload
   * @return validation result
   */
  public ValidationResult validateGeneratedXmi(ModelLevel level, byte[] xmiBytes) {
    return validationService.validateGeneratedXmi(level, xmiBytes);
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
    return importExport.importModel(level, fileName, bytes, format);
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
    return importExport.importModel(user, projectId, level, fileName, bytes, format);
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
    return importExport.importModelFromStream(
        user, projectId, level, fileName, input, sizeBytes, format);
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
    return importExport.exportModel(level, modelJson, format);
  }

  /**
   * Exports model JSON using CIM as the default level.
   *
   * @param modelJson model JSON
   * @param format export format
   * @return exported bytes
   */
  public byte[] exportModel(JsonNode modelJson, String format) {
    return importExport.exportModel(modelJson, format);
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
      Optional<byte[]> xmiBytes = importExport.sourceXmi(model);
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
    return importExport.sourceXmi(model);
  }

  /**
   * Attaches source XMI bytes to an existing model and updates its sidecar hash.
   *
   * @param model model record
   * @param bytes source XMI bytes
   */
  public void attachSourceXmi(ModelRecord model, byte[] bytes) {
    importExport.attachSourceXmi(model, bytes);
  }

  /** Removes expired staged XMI imports. */
  public void cleanupExpiredImports() {
    importExport.cleanupExpiredImports();
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
   * Persists model JSON and source XMI as a best-effort transaction with rollback snapshots.
   *
   * @param previous previous model record, or {@code null} for create
   * @param model model record to persist
   * @param sourceXmi source XMI update instruction
   */
  private void persistModelAndSourceXmi(
      ModelRecord previous, ModelRecord model, ModelImportExportService.SourceXmiUpdate sourceXmi) {
    Path modelPath = modelPath(model.projectId(), model.level(), model.id());
    Path sourcePath = importExport.sourceXmiPath(model.projectId(), model.level(), model.id());
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
        importExport.cleanupStagedImport(sourceXmi.record());
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
    try (JsonParser parser = store.objectMapper().createParser(path.toFile())) {
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
}
