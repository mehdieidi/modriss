package io.mehdieidi.varka.platform.transformation.synchronization;

import io.mehdieidi.varka.platform.identity.domain.UserRecord;
import io.mehdieidi.varka.platform.kernel.ModelLevel;
import io.mehdieidi.varka.platform.kernel.PlatformException;
import io.mehdieidi.varka.platform.model.application.ModelService;
import io.mehdieidi.varka.platform.model.domain.ModelRecord;
import io.mehdieidi.varka.platform.modeling.xmi.XmiModelImportService;
import io.mehdieidi.varka.platform.storage.api.PlatformStore;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantLock;
import org.eclipse.emf.ecore.resource.Resource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/** Coordinates fresh generation, EMF comparison, baselines, sessions, and canonical updates. */
public final class TransformationSynchronizationCoordinator {

  private static final List<String> PLATFORM_METADATA =
      List.of(
          "transformedFrom",
          "transformedFromModelId",
          "sourceModelId",
          "sourceModelRevision",
          "sourceModelHash",
          "transformationStatus");

  private final PlatformStore store;
  private final ModelService models;
  private final ModelBaselineRepository baselines;
  private final SynchronizationSessionRepository sessions;
  private final ModelSynchronizationService merger;
  private final XmiModelImportService xmi;
  private final ConcurrentMap<String, ReentrantLock> targetLocks = new ConcurrentHashMap<>();

  public TransformationSynchronizationCoordinator(PlatformStore store, ModelService models) {
    this.store = store;
    this.models = models;
    this.baselines = new ModelBaselineRepository(store);
    this.sessions = new SynchronizationSessionRepository(store);
    this.merger = new ModelSynchronizationService(store.objectMapper());
    this.xmi = new XmiModelImportService(store.objectMapper());
  }

  /** Synchronizes one freshly generated raw target and returns the canonical working model. */
  public CoordinatedResult synchronize(
      UserRecord user,
      ModelRecord source,
      ModelLevel targetLevel,
      String targetName,
      ObjectNode rawGenerated,
      byte[] rawGeneratedXmi,
      TransformationDirection direction) {
    String lockKey = source.projectId() + ":" + direction + ":" + source.id();
    ReentrantLock lock = targetLocks.computeIfAbsent(lockKey, ignored -> new ReentrantLock());
    lock.lock();
    try {
      return synchronizeLocked(
          user, source, targetLevel, targetName, rawGenerated, rawGeneratedXmi, direction);
    } finally {
      lock.unlock();
      if (!lock.hasQueuedThreads()) {
        targetLocks.remove(lockKey, lock);
      }
    }
  }

  private CoordinatedResult synchronizeLocked(
      UserRecord user,
      ModelRecord source,
      ModelLevel targetLevel,
      String targetName,
      ObjectNode rawGenerated,
      byte[] rawGeneratedXmi,
      TransformationDirection direction) {
    GeneratedBaseline baseline =
        baselines.get(source.projectId(), direction, source.id()).orElse(null);
    if (baseline == null) {
      ModelRecord legacy = findWorking(user, source, targetLevel);
      if (legacy != null) {
        return new CoordinatedResult(
            legacy, emptyResult(SynchronizationStatus.BOOTSTRAP_REQUIRED, legacy.id(), List.of()));
      }
      requireValid(targetLevel, rawGeneratedXmi);
      ModelRecord created =
          store.inTransaction(
              () -> {
                ModelRecord model =
                    models.createGenerated(
                        user,
                        targetLevel,
                        source.projectId(),
                        targetName,
                        rawGenerated,
                        rawGeneratedXmi);
                baselines.save(
                    newBaseline(source, direction, model.id(), 1, rawGenerated, rawGeneratedXmi));
                return model;
              });
      return new CoordinatedResult(
          created, emptyResult(SynchronizationStatus.APPLIED, created.id(), List.of()));
    }

    ModelRecord working = models.get(user, targetLevel, baseline.targetModelId());
    byte[] workingXmi = xmi.exportModel(targetLevel, working.modelJson());
    Resource baseResource = xmi.loadResource(targetLevel, baseline.rawGeneratedXmi(), "sync-base");
    Resource workingResource = xmi.loadResource(targetLevel, workingXmi, "sync-working");
    Resource incomingResource =
        xmi.loadResource(targetLevel, rawGeneratedXmi, "sync-new-generated");
    ModelSynchronizationService.MergeOutcome merge =
        merger.synchronize(baseResource, workingResource, incomingResource, direction);
    ObjectNode mergedJson =
        mergedJson(targetLevel, merge.mergedWorkingXmi(), working.modelJson(), rawGenerated);

    if (!merge.conflicts().isEmpty()) {
      String sessionId = UUID.randomUUID().toString();
      SynchronizationSession session =
          new SynchronizationSession(
              sessionId,
              direction,
              source.projectId(),
              source.id(),
              working.id(),
              working.revision(),
              fingerprint(working.modelJson()),
              baseline.baselineVersion(),
              source.revision(),
              fingerprint(source.modelJson()),
              "etl-assets-v1",
              Instant.now(),
              mergedJson,
              baseline.rawGeneratedXmi(),
              workingXmi,
              merge.mergedWorkingXmi(),
              rawGenerated.deepCopy(),
              rawGeneratedXmi,
              merge.conflicts(),
              Map.of());
      sessions.save(session);
      return new CoordinatedResult(
          working,
          result(SynchronizationStatus.CONFLICTS, sessionId, working.id(), merge, List.of()));
    }

    List<String> findings = validationFindings(targetLevel, merge.mergedWorkingXmi());
    if (!findings.isEmpty()) {
      return new CoordinatedResult(
          working,
          result(SynchronizationStatus.VALIDATION_FAILED, null, working.id(), merge, findings));
    }
    ModelRecord updated =
        store.inTransaction(
            () -> {
              ModelRecord model =
                  models.update(
                      user,
                      targetLevel,
                      working.id(),
                      working.name(),
                      mergedJson,
                      working.revision());
              baselines.save(
                  newBaseline(
                      source,
                      direction,
                      working.id(),
                      baseline.baselineVersion() + 1,
                      rawGenerated,
                      rawGeneratedXmi));
              return model;
            });
    return new CoordinatedResult(
        updated, result(SynchronizationStatus.APPLIED, null, updated.id(), merge, List.of()));
  }

  public SynchronizationSession getSession(UserRecord user, String projectId, String sessionId) {
    models.list(user, ModelLevel.CIM, projectId);
    return sessions
        .get(projectId, sessionId)
        .orElseThrow(() -> new PlatformException(404, "Synchronization session not found."));
  }

  /** Stores one conflict decision; canonical working and baseline remain untouched. */
  public SynchronizationSession resolve(
      UserRecord user,
      String projectId,
      String sessionId,
      String conflictId,
      ConflictResolution resolution) {
    SynchronizationSession session = getSession(user, projectId, sessionId);
    session.conflicts().stream()
        .filter(item -> item.conflictId().equals(conflictId))
        .findFirst()
        .orElseThrow(() -> new PlatformException(404, "Synchronization conflict not found."));
    Map<String, ConflictResolution> resolutions = new LinkedHashMap<>(session.resolutions());
    resolutions.put(conflictId, resolution);
    SynchronizationSession updated = copySession(session, resolutions);
    sessions.save(updated);
    return updated;
  }

  /** Commits a fully resolved, non-stale pending session and advances the raw baseline. */
  public ModelRecord finalizeSession(UserRecord user, String projectId, String sessionId) {
    SynchronizationSession session = getSession(user, projectId, sessionId);
    return models
        .modelLocks()
        .withModelLock(
            session.sourceModelId(),
            Duration.ofSeconds(30),
            () -> finalizeSessionLocked(user, projectId, session));
  }

  private ModelRecord finalizeSessionLocked(
      UserRecord user, String projectId, SynchronizationSession session) {
    session =
        sessions
            .get(projectId, session.id())
            .orElseThrow(() -> new PlatformException(404, "Synchronization session not found."));
    if (session.resolutions().size() != session.conflicts().size()) {
      throw new PlatformException(409, "Every synchronization conflict must be resolved first.");
    }
    ModelLevel level = targetLevel(session.direction());
    ModelRecord working = models.get(user, level, session.targetModelId());
    assertSessionCurrent(projectId, session, working);

    Resource base = xmi.loadResource(level, session.rawBaseXmi(), "session-base");
    Resource local = xmi.loadResource(level, session.rawWorkingXmi(), "session-working");
    Resource incoming =
        xmi.loadResource(level, session.rawNewGeneratedXmi(), "session-new-generated");
    ModelSynchronizationService.MergeOutcome merge =
        merger.synchronize(base, local, incoming, session.direction(), session.resolutions());
    if (!merge.conflicts().isEmpty()) {
      throw new PlatformException(
          409, "Conflict decisions no longer match the pending comparison.");
    }
    ObjectNode mergedJson =
        mergedJson(level, merge.mergedWorkingXmi(), working.modelJson(), session.rawNewGenerated());
    requireValid(level, merge.mergedWorkingXmi());
    GeneratedBaseline previous =
        baselines
            .get(projectId, session.direction(), session.sourceModelId())
            .orElseThrow(
                () -> new PlatformException(409, "The generated baseline no longer exists."));
    SynchronizationSession finalizedSession = session;
    return store.inTransaction(
        () -> {
          ModelRecord updated =
              models.update(
                  user, level, working.id(), working.name(), mergedJson, working.revision());
          baselines.save(
              new GeneratedBaseline(
                  finalizedSession.direction(),
                  projectId,
                  finalizedSession.sourceModelId(),
                  finalizedSession.newSourceRevision(),
                  finalizedSession.newSourceFingerprint(),
                  working.id(),
                  previous.baselineVersion() + 1,
                  finalizedSession.transformationFingerprint(),
                  Instant.now(),
                  finalizedSession.rawNewGenerated(),
                  finalizedSession.rawNewGeneratedXmi()));
          sessions.delete(projectId, finalizedSession.id());
          return updated;
        });
  }

  public void cancel(UserRecord user, String projectId, String sessionId) {
    getSession(user, projectId, sessionId);
    sessions.delete(projectId, sessionId);
  }

  private void assertSessionCurrent(
      String projectId, SynchronizationSession session, ModelRecord working) {
    if (working.revision() != session.workingRevision()
        || !fingerprint(working.modelJson()).equals(session.workingFingerprint())) {
      throw new PlatformException(
          409, "The working model changed; this synchronization session is stale.");
    }
    GeneratedBaseline previous =
        baselines
            .get(projectId, session.direction(), session.sourceModelId())
            .orElseThrow(
                () -> new PlatformException(409, "The generated baseline no longer exists."));
    if (previous.baselineVersion() != session.baselineVersion()) {
      throw new PlatformException(
          409, "The generated baseline changed; this synchronization session is stale.");
    }
  }

  private SynchronizationSession copySession(
      SynchronizationSession session, Map<String, ConflictResolution> resolutions) {
    return new SynchronizationSession(
        session.id(),
        session.direction(),
        session.projectId(),
        session.sourceModelId(),
        session.targetModelId(),
        session.workingRevision(),
        session.workingFingerprint(),
        session.baselineVersion(),
        session.newSourceRevision(),
        session.newSourceFingerprint(),
        session.transformationFingerprint(),
        session.createdAt(),
        session.pendingMergedWorking(),
        session.rawBaseXmi(),
        session.rawWorkingXmi(),
        session.pendingMergedWorkingXmi(),
        session.rawNewGenerated(),
        session.rawNewGeneratedXmi(),
        session.conflicts(),
        resolutions);
  }

  private ObjectNode mergedJson(
      ModelLevel level, byte[] bytes, JsonNode working, JsonNode generated) {
    JsonNode imported = xmi.importGeneratedModel(level, bytes);
    if (!(imported instanceof ObjectNode merged)) {
      throw new PlatformException(500, "Merged EMF resource did not contain a model object.");
    }
    copyPlatformMetadata(working, merged);
    copyPlatformMetadata(generated, merged);
    return merged;
  }

  private void copyPlatformMetadata(JsonNode source, ObjectNode target) {
    if (source == null || !source.isObject()) {
      return;
    }
    for (String field : PLATFORM_METADATA) {
      JsonNode value = source.get(field);
      if (value != null) {
        target.set(field, value.deepCopy());
      }
    }
  }

  private GeneratedBaseline newBaseline(
      ModelRecord source,
      TransformationDirection direction,
      String targetModelId,
      long version,
      JsonNode generated,
      byte[] generatedXmi) {
    return new GeneratedBaseline(
        direction,
        source.projectId(),
        source.id(),
        source.revision(),
        fingerprint(source.modelJson()),
        targetModelId,
        version,
        "etl-assets-v1",
        Instant.now(),
        generated.deepCopy(),
        generatedXmi);
  }

  private SynchronizationResult result(
      SynchronizationStatus status,
      String sessionId,
      String targetId,
      ModelSynchronizationService.MergeOutcome merge,
      List<String> findings) {
    return new SynchronizationResult(
        status,
        sessionId,
        targetId,
        merge.incomingChanges(),
        merge.preservedUserChanges(),
        merge.autoMergedChanges(),
        merge.conflicts().size(),
        merge.generatedAdditions(),
        merge.generatedDeletions(),
        merge.conflicts(),
        findings);
  }

  private SynchronizationResult emptyResult(
      SynchronizationStatus status, String targetId, List<String> findings) {
    return new SynchronizationResult(status, null, targetId, 0, 0, 0, 0, 0, 0, List.of(), findings);
  }

  private List<String> validationFindings(ModelLevel level, byte[] modelXmi) {
    ModelService.ValidationResult validation = models.validateGeneratedXmi(level, modelXmi);
    return validation.valid()
        ? List.of()
        : validation.issues().stream().map(String::valueOf).toList();
  }

  private void requireValid(ModelLevel level, byte[] modelXmi) {
    List<String> findings = validationFindings(level, modelXmi);
    if (!findings.isEmpty()) {
      throw new PlatformException(422, "Synchronized model validation failed: " + findings);
    }
  }

  private ModelLevel targetLevel(TransformationDirection direction) {
    return direction == TransformationDirection.CIM_TO_PIM ? ModelLevel.PIM : ModelLevel.PSM;
  }

  private ModelRecord findWorking(UserRecord user, ModelRecord source, ModelLevel targetLevel) {
    return models.list(user, targetLevel, source.projectId()).stream()
        .filter(
            model ->
                source.id().equals(model.modelJson().path("transformedFromModelId").asText(null))
                    || source.id().equals(model.modelJson().path("sourceModelId").asText(null)))
        .findFirst()
        .orElse(null);
  }

  private String fingerprint(JsonNode model) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(store.objectMapper().writeValueAsBytes(model)));
    } catch (Exception ex) {
      throw new PlatformException(500, "Could not fingerprint synchronization model.", ex);
    }
  }

  public record CoordinatedResult(ModelRecord model, SynchronizationResult synchronization) {}
}
