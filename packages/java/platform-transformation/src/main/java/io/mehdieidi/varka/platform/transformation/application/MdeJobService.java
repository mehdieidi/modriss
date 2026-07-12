package io.mehdieidi.varka.platform.transformation.application;

import com.fasterxml.jackson.databind.JsonNode;
import io.mehdieidi.varka.platform.artifact.domain.ArtifactRecord;
import io.mehdieidi.varka.platform.identity.domain.UserRecord;
import io.mehdieidi.varka.platform.kernel.ModelLevel;
import io.mehdieidi.varka.platform.kernel.PlatformException;
import io.mehdieidi.varka.platform.model.application.ModelService;
import io.mehdieidi.varka.platform.model.domain.ModelRecord;
import io.mehdieidi.varka.platform.modeling.runtime.MdeRuntimeOptions;
import io.mehdieidi.varka.platform.project.application.ProjectService;
import io.mehdieidi.varka.platform.project.domain.ProjectRecord;
import io.mehdieidi.varka.platform.storage.api.PlatformStore;
import io.mehdieidi.varka.platform.transformation.domain.MdeJobIdempotencyRecord;
import io.mehdieidi.varka.platform.transformation.domain.MdeJobIndexRecord;
import io.mehdieidi.varka.platform.transformation.domain.MdeJobOperation;
import io.mehdieidi.varka.platform.transformation.domain.MdeJobRecord;
import io.mehdieidi.varka.platform.transformation.domain.MdeJobStatus;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Submits and tracks asynchronous MDE transformation and generation jobs. */
public final class MdeJobService implements AutoCloseable {

  private static final Logger LOGGER = LoggerFactory.getLogger(MdeJobService.class);

  /** File repository used for job records and indexes. */
  private final PlatformStore store;

  /** Project service used for access and editor checks. */
  private final ProjectService projectService;

  /** Model service used to resolve source models. */
  private final ModelService modelService;

  /** Transformation service that performs the actual MDE work. */
  private final TransformationService transformationService;

  /** Bounded worker pool used for asynchronous jobs. */
  private final ThreadPoolExecutor executor;

  /** Futures keyed by job id for cancellation and cleanup. */
  private final Map<String, Future<?>> runningJobs = new ConcurrentHashMap<>();

  /** Per-project gates serialize overlapping MDE jobs until project versioning exists. */
  private final Map<String, Semaphore> projectGates = new ConcurrentHashMap<>();

  /** JVM-local locks that make duplicate idempotent submissions atomic in this service instance. */
  private final Map<String, Object> idempotencyLocks = new ConcurrentHashMap<>();

  /**
   * Creates an MDE job service with a bounded worker pool.
   *
   * @param store backing JSON repository
   * @param projectService project access service
   * @param modelService model service
   * @param transformationService transformation service
   * @param options runtime options controlling pool and queue sizes
   */
  public MdeJobService(
      PlatformStore store,
      ProjectService projectService,
      ModelService modelService,
      TransformationService transformationService,
      MdeRuntimeOptions options) {
    this.store = store;
    this.projectService = projectService;
    this.modelService = modelService;
    this.transformationService = transformationService;
    MdeRuntimeOptions runtimeOptions = options == null ? MdeRuntimeOptions.defaults() : options;
    this.executor =
        new ThreadPoolExecutor(
            runtimeOptions.maxConcurrentJobs(),
            runtimeOptions.maxConcurrentJobs(),
            30,
            TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(runtimeOptions.queueCapacity()),
            runnable -> {
              Thread thread = new Thread(runnable, "varka-mde-job");
              thread.setDaemon(true);
              return thread;
            },
            new ThreadPoolExecutor.AbortPolicy());
  }

  /**
   * Submits a CIM-to-PIM job without revision precondition.
   *
   * @param user requesting user
   * @param sourceModelId source CIM model id
   * @return queued job record
   */
  public MdeJobRecord submitCimToPim(UserRecord user, String sourceModelId) {
    return submitCimToPim(user, sourceModelId, null);
  }

  /**
   * Submits a CIM-to-PIM job with an optional source revision precondition.
   *
   * @param user requesting user
   * @param sourceModelId source CIM model id
   * @param expectedRevision expected source revision, or {@code null}
   * @return queued job record
   */
  public MdeJobRecord submitCimToPim(UserRecord user, String sourceModelId, Long expectedRevision) {
    return submitCimToPim(user, sourceModelId, expectedRevision, null);
  }

  public MdeJobRecord submitCimToPim(
      UserRecord user, String sourceModelId, Long expectedRevision, String idempotencyKey) {
    return submit(
        user,
        ModelLevel.CIM,
        sourceModelId,
        MdeJobOperation.CIM_TO_PIM,
        expectedRevision,
        idempotencyKey);
  }

  /**
   * Submits a PIM-to-PSM job without revision precondition.
   *
   * @param user requesting user
   * @param sourceModelId source PIM model id
   * @return queued job record
   */
  public MdeJobRecord submitPimToPsm(UserRecord user, String sourceModelId) {
    return submitPimToPsm(user, sourceModelId, null);
  }

  /**
   * Submits a PIM-to-PSM job with an optional source revision precondition.
   *
   * @param user requesting user
   * @param sourceModelId source PIM model id
   * @param expectedRevision expected source revision, or {@code null}
   * @return queued job record
   */
  public MdeJobRecord submitPimToPsm(UserRecord user, String sourceModelId, Long expectedRevision) {
    return submitPimToPsm(user, sourceModelId, expectedRevision, null);
  }

  public MdeJobRecord submitPimToPsm(
      UserRecord user, String sourceModelId, Long expectedRevision, String idempotencyKey) {
    return submit(
        user,
        ModelLevel.PIM,
        sourceModelId,
        MdeJobOperation.PIM_TO_PSM,
        expectedRevision,
        idempotencyKey);
  }

  /**
   * Submits a PSM-to-artifact job without revision precondition.
   *
   * @param user requesting user
   * @param sourceModelId source PSM model id
   * @return queued job record
   */
  public MdeJobRecord submitPsmToArtifact(UserRecord user, String sourceModelId) {
    return submitPsmToArtifact(user, sourceModelId, null);
  }

  /**
   * Submits a PSM-to-artifact job with an optional source revision precondition.
   *
   * @param user requesting user
   * @param sourceModelId source PSM model id
   * @param expectedRevision expected source revision, or {@code null}
   * @return queued job record
   */
  public MdeJobRecord submitPsmToArtifact(
      UserRecord user, String sourceModelId, Long expectedRevision) {
    return submitPsmToArtifact(user, sourceModelId, expectedRevision, null);
  }

  public MdeJobRecord submitPsmToArtifact(
      UserRecord user, String sourceModelId, Long expectedRevision, String idempotencyKey) {
    return submit(
        user,
        ModelLevel.PSM,
        sourceModelId,
        MdeJobOperation.PSM_TO_ARTIFACT,
        expectedRevision,
        idempotencyKey);
  }

  /**
   * Submits asynchronous validation of a stored model.
   *
   * @param user requesting user
   * @param level stored model level
   * @param sourceModelId source model id
   * @param expectedRevision expected source revision, or {@code null}
   * @param idempotencyKey optional idempotency key
   * @return queued validation job
   */
  public MdeJobRecord submitValidation(
      UserRecord user,
      ModelLevel level,
      String sourceModelId,
      Long expectedRevision,
      String idempotencyKey) {
    return submit(
        user, level, sourceModelId, MdeJobOperation.VALIDATE, expectedRevision, idempotencyKey);
  }

  /**
   * Loads a job after verifying project access.
   *
   * @param user requesting user
   * @param id job identifier
   * @return job record
   */
  public MdeJobRecord get(UserRecord user, String id) {
    MdeJobRecord job = find(id);
    projectService.get(user, job.projectId());
    return job;
  }

  /**
   * Cancels a queued or running job when possible.
   *
   * @param user requesting user
   * @param id job identifier
   * @return terminal or unchanged job record
   */
  public MdeJobRecord cancel(UserRecord user, String id) {
    long cancelStarted = System.nanoTime();
    MdeJobRecord job = get(user, id);
    ProjectRecord project = projectService.get(user, job.projectId());
    projectService.requireEditor(project, user.id());
    if (job.status() == MdeJobStatus.SUCCEEDED
        || job.status() == MdeJobStatus.FAILED
        || job.status() == MdeJobStatus.CANCELLED) {
      return job;
    }
    Future<?> future = runningJobs.get(id);
    if (future != null) {
      future.cancel(true);
    }
    LOGGER.info(
        "mde_job_cancelled jobId={} projectId={} operation={} status={}",
        job.id(),
        job.projectId(),
        job.operation(),
        job.status());
    return write(
        status(
            job,
            MdeJobStatus.CANCELLED,
            100,
            job.resultModelId(),
            job.resultArtifactId(),
            List.of("Job was cancelled."),
            job.validationResult(),
            mergeTimings(job.timings(), Map.of("cancel.requestMs", millisSince(cancelStarted))),
            job.startedAt(),
            Instant.now()));
  }

  /**
   * Appends controller-level timing data after a submission response has been prepared.
   *
   * @param jobId job identifier
   * @param timings timing data in milliseconds
   */
  public void appendTimings(String jobId, Map<String, Long> timings) {
    if (timings == null || timings.isEmpty()) {
      return;
    }
    MdeJobRecord job = find(jobId);
    write(
        new MdeJobRecord(
            job.id(),
            job.projectId(),
            job.userId(),
            job.sourceModelId(),
            job.sourceLevel(),
            job.sourceRevision(),
            job.sourceModelHash(),
            job.operation(),
            job.status(),
            job.progressPercent(),
            job.resultModelId(),
            job.resultArtifactId(),
            job.diagnostics(),
            job.validationResult(),
            mergeTimings(job.timings(), timings),
            job.idempotencyKey(),
            job.fingerprint(),
            job.createdAt(),
            job.startedAt(),
            job.finishedAt()));
  }

  /**
   * Creates and enqueues a job for an operation.
   *
   * @param user requesting user
   * @param sourceLevel source model level
   * @param sourceModelId source model id
   * @param operation job operation
   * @param expectedRevision expected source revision, or {@code null}
   * @return queued job record
   */
  private MdeJobRecord submit(
      UserRecord user,
      ModelLevel sourceLevel,
      String sourceModelId,
      MdeJobOperation operation,
      Long expectedRevision,
      String idempotencyKey) {
    long submitStarted = System.nanoTime();
    Map<String, Long> timings = new LinkedHashMap<>();
    long phaseStarted = System.nanoTime();
    ModelRecord source = modelService.get(user, sourceLevel, sourceModelId);
    timings.put("submit.modelLookupMs", millisSince(phaseStarted));
    phaseStarted = System.nanoTime();
    if (expectedRevision != null && expectedRevision.longValue() != source.revision()) {
      throw new PlatformException(409, "Source model was modified by another operation.");
    }
    timings.put("submit.revisionCheckMs", millisSince(phaseStarted));
    phaseStarted = System.nanoTime();
    ProjectRecord project = projectService.get(user, source.projectId());
    projectService.requireEditor(project, user.id());
    timings.put("submit.projectAccessMs", millisSince(phaseStarted));
    phaseStarted = System.nanoTime();
    String sourceHash = hash(source.modelJson());
    timings.put("submit.sourceHashMs", millisSince(phaseStarted));
    phaseStarted = System.nanoTime();
    String fingerprint = fingerprint(user, operation, source, sourceHash, expectedRevision);
    timings.put("submit.fingerprintMs", millisSince(phaseStarted));
    phaseStarted = System.nanoTime();
    String normalizedIdempotencyKey = normalizeIdempotencyKey(idempotencyKey);
    timings.put("submit.idempotencyKeyNormalizeMs", millisSince(phaseStarted));
    if (normalizedIdempotencyKey != null) {
      String scopeHash = idempotencyScope(user.id(), normalizedIdempotencyKey);
      Object lock = idempotencyLocks.computeIfAbsent(scopeHash, ignored -> new Object());
      synchronized (lock) {
        phaseStarted = System.nanoTime();
        MdeJobRecord replay = replayOrConflict(scopeHash, source.projectId(), fingerprint);
        timings.put("submit.idempotencyLookupMs", millisSince(phaseStarted));
        if (replay != null) {
          LOGGER.info(
              "mde_job_idempotency_replay jobId={} projectId={} operation={} timings={}",
              replay.id(),
              replay.projectId(),
              replay.operation(),
              timings);
          return replay;
        }
        timings.put("submit.totalMs", millisSince(submitStarted));
        return createAndEnqueue(
            user,
            source,
            operation,
            expectedRevision,
            sourceHash,
            normalizedIdempotencyKey,
            scopeHash,
            fingerprint,
            timings);
      }
    }
    timings.put("submit.totalMs", millisSince(submitStarted));
    return createAndEnqueue(
        user, source, operation, expectedRevision, sourceHash, null, null, fingerprint, timings);
  }

  private MdeJobRecord createAndEnqueue(
      UserRecord user,
      ModelRecord source,
      MdeJobOperation operation,
      Long expectedRevision,
      String sourceHash,
      String idempotencyKey,
      String idempotencyScopeHash,
      String fingerprint,
      Map<String, Long> submitTimings) {
    Instant now = Instant.now();
    Map<String, Long> timings =
        new LinkedHashMap<>(submitTimings == null ? Map.of() : submitTimings);
    MdeJobRecord job =
        new MdeJobRecord(
            UUID.randomUUID().toString(),
            source.projectId(),
            user.id(),
            source.id(),
            source.level(),
            source.revision(),
            sourceHash,
            operation,
            MdeJobStatus.QUEUED,
            0,
            null,
            null,
            List.of(),
            null,
            timings,
            idempotencyKey,
            fingerprint,
            now,
            null,
            null);
    long phaseStarted = System.nanoTime();
    write(job);
    long persistMs = millisSince(phaseStarted);
    timings = mergeTimings(job.timings(), Map.of("submit.jobPersistMs", persistMs));
    job =
        new MdeJobRecord(
            job.id(),
            job.projectId(),
            job.userId(),
            job.sourceModelId(),
            job.sourceLevel(),
            job.sourceRevision(),
            job.sourceModelHash(),
            job.operation(),
            job.status(),
            job.progressPercent(),
            job.resultModelId(),
            job.resultArtifactId(),
            job.diagnostics(),
            job.validationResult(),
            timings,
            job.idempotencyKey(),
            job.fingerprint(),
            job.createdAt(),
            job.startedAt(),
            job.finishedAt());
    write(job);
    LOGGER.info(
        "mde_job_submitted jobId={} projectId={} userId={} operation={} sourceModelId={} "
            + "sourceRevision={} idempotent={} queueDepth={} timings={}",
        job.id(),
        job.projectId(),
        job.userId(),
        job.operation(),
        job.sourceModelId(),
        job.sourceRevision(),
        job.idempotencyKey() != null,
        executor.getQueue().size(),
        timings);
    if (idempotencyScopeHash != null) {
      phaseStarted = System.nanoTime();
      writeIdempotency(idempotencyScopeHash, job, fingerprint);
      appendTimings(job.id(), Map.of("submit.idempotencyPersistMs", millisSince(phaseStarted)));
      job = find(job.id());
    }
    try {
      phaseStarted = System.nanoTime();
      String queuedJobId = job.id();
      Future<?> future = executor.submit(() -> run(queuedJobId, user));
      runningJobs.put(queuedJobId, future);
      appendTimings(queuedJobId, Map.of("submit.enqueueMs", millisSince(phaseStarted)));
    } catch (RejectedExecutionException ex) {
      write(
          status(
              job,
              MdeJobStatus.FAILED,
              100,
              null,
              null,
              List.of("Job queue is full."),
              null,
              mergeTimings(job.timings(), Map.of("submit.queueRejectedMs", 0L)),
              null,
              Instant.now()));
      LOGGER.warn(
          "mde_job_rejected jobId={} projectId={} operation={} reason=queue_full queueDepth={}",
          job.id(),
          job.projectId(),
          job.operation(),
          executor.getQueue().size());
      throw new PlatformException(503, "MDE job queue is full. Retry later.");
    }
    return job;
  }

  /**
   * Executes a persisted job and updates terminal status.
   *
   * @param jobId job identifier
   * @param user submitting user
   */
  private void run(String jobId, UserRecord user) {
    MdeJobRecord job = find(jobId);
    if (job.status() == MdeJobStatus.CANCELLED) {
      return;
    }
    Semaphore gate = projectGates.computeIfAbsent(job.projectId(), ignored -> new Semaphore(1));
    Instant runStartedAt = Instant.now();
    boolean acquired = false;
    try {
      gate.acquire();
      acquired = true;
      MdeJobRecord queued = find(jobId);
      if (queued.status() == MdeJobStatus.CANCELLED) {
        return;
      }
      Map<String, Long> startTimings =
          mergeTimings(
              queued.timings(),
              Map.of("worker.queueWaitMs", elapsedMs(queued.createdAt(), runStartedAt)));
      write(
          status(
              queued,
              MdeJobStatus.RUNNING,
              10,
              null,
              null,
              List.of(),
              null,
              startTimings,
              runStartedAt,
              null));
      LOGGER.info(
          "mde_job_started jobId={} projectId={} operation={} queueWaitMs={}",
          queued.id(),
          queued.projectId(),
          queued.operation(),
          startTimings.getOrDefault("worker.queueWaitMs", 0L));
      MdeJobRecord latest = find(jobId);
      long workerStarted = System.nanoTime();
      switch (latest.operation()) {
        case CIM_TO_PIM -> {
          ModelRecord model =
              transformationService.cimToPim(user, latest.sourceModelId(), latest.sourceRevision());
          Map<String, Long> timings = finishTimings(latest, workerStarted);
          write(
              status(
                  latest,
                  MdeJobStatus.SUCCEEDED,
                  100,
                  model.id(),
                  null,
                  List.of(),
                  null,
                  timings,
                  latest.startedAt(),
                  Instant.now()));
          logFinished(latest, MdeJobStatus.SUCCEEDED, timings);
        }
        case PIM_TO_PSM -> {
          ModelRecord model =
              transformationService.pimToPsm(user, latest.sourceModelId(), latest.sourceRevision());
          Map<String, Long> timings = finishTimings(latest, workerStarted);
          write(
              status(
                  latest,
                  MdeJobStatus.SUCCEEDED,
                  100,
                  model.id(),
                  null,
                  List.of(),
                  null,
                  timings,
                  latest.startedAt(),
                  Instant.now()));
          logFinished(latest, MdeJobStatus.SUCCEEDED, timings);
        }
        case PSM_TO_ARTIFACT -> {
          ArtifactRecord artifact =
              transformationService.psmToArtifact(
                  user, latest.sourceModelId(), latest.sourceRevision());
          Map<String, Long> timings = finishTimings(latest, workerStarted);
          write(
              status(
                  latest,
                  MdeJobStatus.SUCCEEDED,
                  100,
                  null,
                  artifact.id(),
                  List.of(),
                  null,
                  timings,
                  latest.startedAt(),
                  Instant.now()));
          logFinished(latest, MdeJobStatus.SUCCEEDED, timings);
        }
        case VALIDATE -> {
          ModelService.ValidationResult validation =
              modelService.validate(user, latest.sourceLevel(), latest.sourceModelId());
          Map<String, Long> timings =
              finishTimings(latest, workerStarted, ModelService.consumeLastValidationTimings());
          write(
              status(
                  latest,
                  validation.valid() ? MdeJobStatus.SUCCEEDED : MdeJobStatus.FAILED,
                  100,
                  null,
                  null,
                  validation.issues().stream()
                      .filter(issue -> "ERROR".equals(issue.severity()))
                      .map(ModelService.ValidationIssue::message)
                      .limit(10)
                      .toList(),
                  validation,
                  timings,
                  latest.startedAt(),
                  Instant.now()));
          logFinished(
              latest, validation.valid() ? MdeJobStatus.SUCCEEDED : MdeJobStatus.FAILED, timings);
        }
      }
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      MdeJobRecord cancelled = find(jobId);
      write(
          status(
              cancelled,
              MdeJobStatus.CANCELLED,
              100,
              cancelled.resultModelId(),
              cancelled.resultArtifactId(),
              List.of("Job was cancelled."),
              cancelled.validationResult(),
              cancelled.timings(),
              cancelled.startedAt(),
              Instant.now()));
      LOGGER.info(
          "mde_job_cancelled jobId={} projectId={} operation={} running=true",
          cancelled.id(),
          cancelled.projectId(),
          cancelled.operation());
    } catch (Exception ex) {
      MdeJobRecord failed = find(jobId);
      if (failed.status() == MdeJobStatus.CANCELLED) {
        return;
      }
      write(
          status(
              failed,
              MdeJobStatus.FAILED,
              100,
              failed.resultModelId(),
              failed.resultArtifactId(),
              List.of(message(ex)),
              failed.validationResult(),
              mergeTimings(
                  failed.timings(),
                  Map.of("worker.totalMs", elapsedMs(failed.startedAt(), Instant.now()))),
              failed.startedAt(),
              Instant.now()));
      LOGGER.warn(
          "mde_job_failed jobId={} projectId={} operation={} message={}",
          failed.id(),
          failed.projectId(),
          failed.operation(),
          message(ex));
    } finally {
      if (acquired) {
        gate.release();
      }
      runningJobs.remove(jobId);
    }
  }

  /**
   * Finds a job by id through the global index.
   *
   * @param id job identifier
   * @return job record
   */
  private MdeJobRecord find(String id) {
    MdeJobIndexRecord index =
        store.require(jobIndexPath(id), MdeJobIndexRecord.class, "MDE job not found.");
    return store.require(jobPath(index.projectId(), id), MdeJobRecord.class, "MDE job not found.");
  }

  /**
   * Persists a job and its global index entry.
   *
   * @param job job record
   * @return persisted job record
   */
  private MdeJobRecord write(MdeJobRecord job) {
    store.write(jobPath(job.projectId(), job.id()), job);
    store.write(jobIndexPath(job.id()), new MdeJobIndexRecord(job.id(), job.projectId()));
    return job;
  }

  /**
   * Creates a copy of a job with updated status fields.
   *
   * @param job existing job
   * @param status new status
   * @param progress new progress percentage
   * @param resultModelId generated model id
   * @param resultArtifactId generated artifact id
   * @param diagnostics job diagnostics
   * @param startedAt start timestamp override
   * @param finishedAt finish timestamp
   * @return updated job record
   */
  private MdeJobRecord status(
      MdeJobRecord job,
      MdeJobStatus status,
      int progress,
      String resultModelId,
      String resultArtifactId,
      List<String> diagnostics,
      Object validationResult,
      Map<String, Long> timings,
      Instant startedAt,
      Instant finishedAt) {
    return new MdeJobRecord(
        job.id(),
        job.projectId(),
        job.userId(),
        job.sourceModelId(),
        job.sourceLevel(),
        job.sourceRevision(),
        job.sourceModelHash(),
        job.operation(),
        status,
        progress,
        resultModelId,
        resultArtifactId,
        diagnostics,
        validationResult,
        timings,
        job.idempotencyKey(),
        job.fingerprint(),
        job.createdAt(),
        startedAt == null ? job.startedAt() : startedAt,
        finishedAt);
  }

  private MdeJobRecord replayOrConflict(String scopeHash, String projectId, String fingerprint) {
    return store
        .read(idempotencyPath(scopeHash), MdeJobIdempotencyRecord.class)
        .map(
            record -> {
              if (!fingerprint.equals(record.fingerprint())) {
                throw new PlatformException(
                    409, "Idempotency-Key was already used for a different MDE request.");
              }
              return store.require(
                  jobPath(record.projectId(), record.jobId()),
                  MdeJobRecord.class,
                  "MDE job not found.");
            })
        .orElse(null);
  }

  private void writeIdempotency(String scopeHash, MdeJobRecord job, String fingerprint) {
    store.write(
        idempotencyPath(scopeHash),
        new MdeJobIdempotencyRecord(scopeHash, job.id(), job.projectId(), fingerprint));
  }

  private String normalizeIdempotencyKey(String idempotencyKey) {
    if (idempotencyKey == null || idempotencyKey.isBlank()) {
      return null;
    }
    String trimmed = idempotencyKey.trim();
    if (trimmed.length() > 256) {
      throw new PlatformException(400, "Idempotency-Key is too long.");
    }
    return trimmed;
  }

  private String fingerprint(
      UserRecord user,
      MdeJobOperation operation,
      ModelRecord source,
      String sourceHash,
      Long expectedRevision) {
    return hashText(
        user.id()
            + "\n"
            + operation
            + "\n"
            + source.projectId()
            + "\n"
            + source.id()
            + "\n"
            + source.level()
            + "\n"
            + source.revision()
            + "\n"
            + sourceHash
            + "\n"
            + String.valueOf(expectedRevision));
  }

  private String idempotencyScope(String userId, String idempotencyKey) {
    return hashText(userId + "\n" + idempotencyKey);
  }

  private Map<String, Long> finishTimings(MdeJobRecord latest, long workerStarted) {
    return finishTimings(latest, workerStarted, TransformationService.consumeLastTimings());
  }

  private Map<String, Long> finishTimings(
      MdeJobRecord latest, long workerStarted, Map<String, Long> operationTimings) {
    return mergeTimings(
        mergeTimings(latest.timings(), operationTimings),
        Map.of("worker.totalMs", millisSince(workerStarted)));
  }

  private Map<String, Long> mergeTimings(Map<String, Long> left, Map<String, Long> right) {
    java.util.LinkedHashMap<String, Long> merged = new java.util.LinkedHashMap<>();
    if (left != null) {
      merged.putAll(left);
    }
    if (right != null) {
      merged.putAll(right);
    }
    return Map.copyOf(merged);
  }

  private long elapsedMs(Instant start, Instant end) {
    if (start == null || end == null) {
      return 0L;
    }
    return Math.max(0L, java.time.Duration.between(start, end).toMillis());
  }

  private long millisSince(long startedNanos) {
    return Math.max(0L, (System.nanoTime() - startedNanos) / 1_000_000L);
  }

  private void logFinished(MdeJobRecord job, MdeJobStatus status, Map<String, Long> timings) {
    LOGGER.info(
        "mde_job_finished jobId={} projectId={} operation={} status={} controllerMs={} "
            + "submitMs={} queueWaitMs={} workerMs={} epsilonMs={} egxMs={} validationMs={} "
            + "dbMs={} timings={}",
        job.id(),
        job.projectId(),
        job.operation(),
        status,
        timings.getOrDefault("controller.acceptMs", 0L),
        timings.getOrDefault("submit.totalMs", 0L),
        timings.getOrDefault("worker.queueWaitMs", 0L),
        timings.getOrDefault("worker.totalMs", 0L),
        timings.getOrDefault("epsilon.totalMs", 0L),
        timings.getOrDefault("egx.totalMs", 0L),
        timings.getOrDefault("validation.totalMs", 0L),
        timings.getOrDefault("java.generatedModelPersistenceMs", 0L)
            + timings.getOrDefault("java.artifactPersistenceMs", 0L),
        timings);
  }

  /**
   * Hashes a model JSON payload for source snapshot tracking.
   *
   * @param modelJson model JSON
   * @return lowercase hexadecimal SHA-256 digest
   */
  private String hash(JsonNode modelJson) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of()
          .formatHex(digest.digest(store.objectMapper().writeValueAsBytes(modelJson)));
    } catch (Exception ex) {
      throw new PlatformException(500, "Could not hash source model.");
    }
  }

  private String hashText(String value) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of()
          .formatHex(
              digest.digest(
                  String.valueOf(value).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    } catch (Exception ex) {
      throw new PlatformException(500, "Could not hash MDE request.");
    }
  }

  /**
   * Extracts a user-facing message from a job exception.
   *
   * @param ex job exception
   * @return diagnostic message
   */
  private String message(Exception ex) {
    if (ex instanceof PlatformException platformException) {
      return platformException.getMessage();
    }
    return ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
  }

  /**
   * Returns the repository path for a job record.
   *
   * @param projectId project identifier
   * @param id job identifier
   * @return repository-relative path
   */
  private Path jobPath(String projectId, String id) {
    return Path.of("projects", projectId, "mde-jobs", id + ".json");
  }

  /**
   * Returns the repository path for a job index record.
   *
   * @param id job identifier
   * @return repository-relative path
   */
  private Path jobIndexPath(String id) {
    return Path.of("indexes", "mde-jobs", id + ".json");
  }

  private Path idempotencyPath(String scopeHash) {
    return Path.of("indexes", "mde-job-idempotency", scopeHash + ".json");
  }

  /** Stops worker threads when the service is closed. */
  @Override
  public void close() {
    executor.shutdownNow();
  }
}
