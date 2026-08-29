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
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantLock;
import org.eclipse.emf.common.util.TreeIterator;
import org.eclipse.emf.compare.utils.UseIdentifiers;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/** Coordinates fresh generation, EMF comparison, baselines, sessions, and canonical updates. */
public final class TransformationSynchronizationCoordinator {

  private static final String STABLE_ID_TRANSFORMATION_FINGERPRINT = "etl-assets-v2-stable-ids";

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
  private final ModelSynchronizationService legacyIdentityMigrationMerger;
  private final XmiModelImportService xmi;
  private final ConcurrentMap<String, ReentrantLock> targetLocks = new ConcurrentHashMap<>();

  public TransformationSynchronizationCoordinator(PlatformStore store, ModelService models) {
    this.store = store;
    this.models = models;
    this.baselines = new ModelBaselineRepository(store);
    this.sessions = new SynchronizationSessionRepository(store);
    this.merger = new ModelSynchronizationService(store.objectMapper());
    this.legacyIdentityMigrationMerger =
        new ModelSynchronizationService(
            store.objectMapper(), new SemanticMergePolicy(), UseIdentifiers.NEVER);
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
      requireValid(targetLevel, rawGeneratedXmi, rawGenerated);
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
        mergerFor(baseResource, incomingResource, baseline.transformationFingerprint())
            .synchronize(baseResource, workingResource, incomingResource, direction);
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
              baseline.transformationFingerprint(),
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

    requireValid(targetLevel, merge.mergedWorkingXmi(), mergedJson);
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
    return resolveAll(user, projectId, sessionId, Map.of(conflictId, resolution));
  }

  /**
   * Stores multiple conflict decisions in one durable session update.
   *
   * <p>This is intentionally atomic at the session level: a bulk UI choice must never leave a
   * partially written set of decisions when a request fails midway through a large conflict list.
   */
  public SynchronizationSession resolveAll(
      UserRecord user,
      String projectId,
      String sessionId,
      Map<String, ConflictResolution> requestedResolutions) {
    SynchronizationSession session = getSession(user, projectId, sessionId);
    if (requestedResolutions == null || requestedResolutions.isEmpty()) {
      throw new PlatformException(400, "At least one synchronization resolution is required.");
    }
    if (requestedResolutions.keySet().stream().anyMatch(java.util.Objects::isNull)
        || requestedResolutions.values().stream().anyMatch(java.util.Objects::isNull)) {
      throw new PlatformException(400, "Every synchronization resolution must have a value.");
    }
    Map<String, ConflictResolution> requested = Map.copyOf(requestedResolutions);
    Map<String, ModelConflict> conflictsById =
        session.conflicts().stream()
            .collect(
                java.util.stream.Collectors.toMap(
                    ModelConflict::conflictId, item -> item, (first, ignored) -> first));
    for (String conflictId : requested.keySet()) {
      if (!conflictsById.containsKey(conflictId)) {
        throw new PlatformException(404, "Synchronization conflict not found.");
      }
    }
    Map<String, ConflictResolution> resolutions = new LinkedHashMap<>(session.resolutions());
    resolutions.putAll(requested);
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
    boolean generatedReplacement =
        !session.resolutions().isEmpty()
            && session.resolutions().values().stream()
                .allMatch(ConflictResolution.TAKE_GENERATED::equals);
    ModelSynchronizationService.MergeOutcome merge;
    ObjectNode mergedJson;
    byte[] mergedXmi;
    if (generatedReplacement) {
      // Once every logical conflict is explicitly resolved to the incoming model, the incoming
      // ETL resource is authoritative. Reusing the partially merged local graph here can retain
      // both sides of a containment/reference move and create duplicate IDs, especially when a
      // deleted generated workflow still has a manually edited PSM subtree. Re-importing the raw
      // XMI normalizes its graph/index references before structural validation.
      mergedXmi = session.rawNewGeneratedXmi();
      // Re-import the XMI so graph/index references are normalized instead of treating repeated
      // JSON references to one EObject as repeated contained objects during validation.
      mergedJson = (ObjectNode) xmi.importGeneratedModel(level, mergedXmi);
      merge =
          mergerFor(base, incoming, session.transformationFingerprint())
              .synchronize(base, local, incoming, session.direction(), session.resolutions());
      requireValid(level, mergedXmi, mergedJson);
    } else {
      merge =
          mergerFor(base, incoming, session.transformationFingerprint())
              .synchronize(base, local, incoming, session.direction(), session.resolutions());
      if (!merge.conflicts().isEmpty()) {
        throw new PlatformException(
            409, "Conflict decisions no longer match the pending comparison.");
      }
      mergedXmi = merge.mergedWorkingXmi();
      mergedJson = mergedJson(level, mergedXmi, working.modelJson(), session.rawNewGenerated());
      requireValid(level, mergedXmi, mergedJson);
    }
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
                  STABLE_ID_TRANSFORMATION_FINGERPRINT,
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
    mergeManualBacklog(working, generated, merged);
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

  /**
   * Reconciles issue-board tasks around the XMI merge.
   *
   * <p>The manual backlog is platform metadata and is intentionally not part of the domain XMI.
   * Rebuilding the JSON model from the merged XMI therefore cannot be allowed to replace it with
   * the empty backlog emitted by the XMI importer. Generated tasks are identified by their stable
   * transformation categories; all other tasks are user-owned and are retained.
   */
  private void mergeManualBacklog(JsonNode working, JsonNode generated, ObjectNode merged) {
    Map<String, JsonNode> workingTasks = backlogById(working);
    Map<String, JsonNode> generatedTasks = backlogById(generated);
    ArrayNode backlog = store.objectMapper().createArrayNode();

    workingTasks.forEach(
        (id, task) -> {
          String category = task.path("category").asText("");
          if (!isGeneratedBacklogTask(category) || generatedTasks.containsKey(id)) {
            backlog.add(task.deepCopy());
          }
        });
    generatedTasks.forEach(
        (id, task) -> {
          if (!workingTasks.containsKey(id)) {
            backlog.add(task.deepCopy());
          }
        });

    merged.set("manualBacklog", backlog);
    merged.withObject("graph").set("manualBacklog", backlog.deepCopy());
  }

  private Map<String, JsonNode> backlogById(JsonNode model) {
    Map<String, JsonNode> tasks = new LinkedHashMap<>();
    JsonNode backlog = model == null ? null : model.get("manualBacklog");
    if (backlog == null || !backlog.isArray()) {
      backlog = model == null ? null : model.path("graph").get("manualBacklog");
    }
    if (backlog != null && backlog.isArray()) {
      backlog.forEach(
          task -> {
            String id = task.path("id").asText("");
            if (!id.isBlank()) {
              tasks.putIfAbsent(id, task);
            }
          });
    }
    return tasks;
  }

  private boolean isGeneratedBacklogTask(String category) {
    return "ETL_MANUAL_DECISION".equals(category) || "MANUAL_MODELING".equals(category);
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
        STABLE_ID_TRANSFORMATION_FINGERPRINT,
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

  private void requireValid(ModelLevel level, byte[] modelXmi, ObjectNode modelJson) {
    ModelService.ValidationResult validation =
        models.validateGeneratedXmi(level, modelXmi, modelJson);
    if (!validation.valid()) {
      throw new TransformationValidationException(validation.issues());
    }
  }

  private ModelLevel targetLevel(TransformationDirection direction) {
    return direction == TransformationDirection.CIM_TO_PIM ? ModelLevel.PIM : ModelLevel.PSM;
  }

  /**
   * Uses EMF Compare's proximity matcher only while replacing a pre-deterministic generated
   * baseline. Old baselines contain UUIDs for every generated helper while a fresh generator has
   * stable IDs, which would otherwise manufacture hundreds of unrelated ADD/DELETE/MOVE conflicts.
   * It is deliberately limited to incompatible historic baselines; newly generated baselines always
   * use strict identifier matching.
   */
  private ModelSynchronizationService mergerFor(
      Resource base, Resource incoming, String transformationFingerprint) {
    // Baselines written before deterministic generated IDs were introduced are explicitly
    // migrated once. This marker is authoritative and avoids relying on serialized
    // reference/index IDs, whose overlap varies by metamodel.
    if (!STABLE_ID_TRANSFORMATION_FINGERPRINT.equals(transformationFingerprint)) {
      return legacyIdentityMigrationMerger;
    }
    Set<String> baseIds = ids(base);
    Set<String> incomingIds = ids(incoming);
    if (baseIds.size() < 20 || incomingIds.size() < 20) {
      return merger;
    }
    long sharedIds = baseIds.stream().filter(incomingIds::contains).count();
    double overlap = (double) sharedIds / Math.min(baseIds.size(), incomingIds.size());
    // Legacy PSM baselines can retain a sizeable island of stable IDs in trace/index metadata,
    // making aggregate XMI ID overlap an unreliable signal.  The generated-source/EClass groups
    // directly test the identity invariant instead: old ETL output has shared provenance but
    // different object IDs, while deterministic output retains the IDs for existing objects.
    return sharesGeneratedSources(base, incoming)
            && (overlap < 0.60d || hasIncompatibleGeneratedIdentities(base, incoming))
        ? legacyIdentityMigrationMerger
        : merger;
  }

  private boolean hasIncompatibleGeneratedIdentities(Resource base, Resource incoming) {
    Map<String, Set<String>> baseGroups = generatedIdentityGroups(base);
    Map<String, Set<String>> incomingGroups = generatedIdentityGroups(incoming);
    long comparable = 0;
    long shared = 0;
    int comparableGroups = 0;
    for (Map.Entry<String, Set<String>> entry : baseGroups.entrySet()) {
      Set<String> generatedIds = incomingGroups.get(entry.getKey());
      if (generatedIds == null || generatedIds.isEmpty()) continue;
      comparableGroups++;
      comparable += Math.min(entry.getValue().size(), generatedIds.size());
      Set<String> intersection = new HashSet<>(entry.getValue());
      intersection.retainAll(generatedIds);
      shared += intersection.size();
    }
    return comparable >= 50 && ((double) shared / comparable) < 0.80d;
  }

  private Map<String, Set<String>> generatedIdentityGroups(Resource resource) {
    Map<String, Set<String>> groups = new HashMap<>();
    for (EObject root : resource.getContents()) {
      collectGeneratedIdentity(root, groups);
      for (TreeIterator<EObject> iterator = root.eAllContents(); iterator.hasNext(); ) {
        collectGeneratedIdentity(iterator.next(), groups);
      }
    }
    return groups;
  }

  private void collectGeneratedIdentity(EObject object, Map<String, Set<String>> groups) {
    String source = generatedSource(object);
    String objectId = objectId(object);
    if (source == null || objectId == null) return;
    String key = source + "\u0000" + object.eClass().getName();
    groups.computeIfAbsent(key, ignored -> new HashSet<>()).add(objectId);
  }

  private String generatedSource(EObject object) {
    var feature = object.eClass().getEStructuralFeature("generatedFrom");
    if (feature == null) return null;
    Object value = object.eGet(feature, false);
    return value == null || String.valueOf(value).isBlank() ? null : String.valueOf(value);
  }

  private String objectId(EObject object) {
    var feature = object.eClass().getEStructuralFeature("id");
    if (feature == null) return null;
    Object value = object.eGet(feature, false);
    return value == null || String.valueOf(value).isBlank() ? null : String.valueOf(value);
  }

  private Set<String> ids(Resource resource) {
    Set<String> result = new java.util.HashSet<>();
    for (EObject root : resource.getContents()) {
      addId(root, result);
      for (TreeIterator<EObject> iterator = root.eAllContents(); iterator.hasNext(); ) {
        addId(iterator.next(), result);
      }
    }
    return result;
  }

  private void addId(EObject object, Set<String> ids) {
    var feature = object.eClass().getEStructuralFeature("id");
    if (feature == null) return;
    Object value = object.eGet(feature, false);
    if (value != null && !String.valueOf(value).isBlank()) ids.add(String.valueOf(value));
  }

  private boolean sharesGeneratedSources(Resource base, Resource incoming) {
    Set<String> sources = generatedSources(base);
    if (sources.isEmpty()) return false;
    Set<String> incomingSources = generatedSources(incoming);
    incomingSources.retainAll(sources);
    return !incomingSources.isEmpty();
  }

  private Set<String> generatedSources(Resource resource) {
    Set<String> result = new java.util.HashSet<>();
    for (EObject root : resource.getContents()) {
      addGeneratedSource(root, result);
      for (TreeIterator<EObject> iterator = root.eAllContents(); iterator.hasNext(); ) {
        addGeneratedSource(iterator.next(), result);
      }
    }
    return result;
  }

  private void addGeneratedSource(EObject object, Set<String> sources) {
    var feature = object.eClass().getEStructuralFeature("generatedFrom");
    if (feature == null) return;
    Object value = object.eGet(feature, false);
    if (value != null && !String.valueOf(value).isBlank()) sources.add(String.valueOf(value));
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
