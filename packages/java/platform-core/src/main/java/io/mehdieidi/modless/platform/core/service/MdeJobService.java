package io.mehdieidi.modless.platform.core.service;

import com.fasterxml.jackson.databind.JsonNode;
import io.mehdieidi.modless.platform.core.PlatformException;
import io.mehdieidi.modless.platform.core.model.ArtifactRecord;
import io.mehdieidi.modless.platform.core.model.MdeJobIndexRecord;
import io.mehdieidi.modless.platform.core.model.MdeJobOperation;
import io.mehdieidi.modless.platform.core.model.MdeJobRecord;
import io.mehdieidi.modless.platform.core.model.MdeJobStatus;
import io.mehdieidi.modless.platform.core.model.ModelLevel;
import io.mehdieidi.modless.platform.core.model.ModelRecord;
import io.mehdieidi.modless.platform.core.model.ProjectRecord;
import io.mehdieidi.modless.platform.core.model.UserRecord;
import io.mehdieidi.modless.platform.core.repository.JsonFileStore;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Submits and tracks asynchronous MDE transformation and generation jobs.
 */
public final class MdeJobService implements AutoCloseable {

    /**
     * File repository used for job records and indexes.
     */
    private final JsonFileStore store;
    /**
     * Project service used for access and editor checks.
     */
    private final ProjectService projectService;
    /**
     * Model service used to resolve source models.
     */
    private final ModelService modelService;
    /**
     * Transformation service that performs the actual MDE work.
     */
    private final TransformationService transformationService;
    /**
     * Bounded worker pool used for asynchronous jobs.
     */
    private final ThreadPoolExecutor executor;
    /**
     * Futures keyed by job id for cancellation and cleanup.
     */
    private final Map<String, Future<?>> runningJobs = new ConcurrentHashMap<>();

    /**
     * Creates an MDE job service with a bounded worker pool.
     *
     * @param store                 backing JSON repository
     * @param projectService        project access service
     * @param modelService          model service
     * @param transformationService transformation service
     * @param options               runtime options controlling pool and queue sizes
     */
    public MdeJobService(JsonFileStore store, ProjectService projectService,
            ModelService modelService, TransformationService transformationService,
            MdeRuntimeOptions options) {
        this.store = store;
        this.projectService = projectService;
        this.modelService = modelService;
        this.transformationService = transformationService;
        MdeRuntimeOptions runtimeOptions = options == null ? MdeRuntimeOptions.defaults() : options;
        this.executor = new ThreadPoolExecutor(
                runtimeOptions.maxConcurrentJobs(),
                runtimeOptions.maxConcurrentJobs(),
                30,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(runtimeOptions.queueCapacity()),
                runnable -> {
                    Thread thread = new Thread(runnable, "modless-mde-job");
                    thread.setDaemon(true);
                    return thread;
                },
                new ThreadPoolExecutor.AbortPolicy());
    }

    /**
     * Submits a CIM-to-PIM job without revision precondition.
     *
     * @param user          requesting user
     * @param sourceModelId source CIM model id
     * @return queued job record
     */
    public MdeJobRecord submitCimToPim(UserRecord user, String sourceModelId) {
        return submitCimToPim(user, sourceModelId, null);
    }

    /**
     * Submits a CIM-to-PIM job with an optional source revision precondition.
     *
     * @param user             requesting user
     * @param sourceModelId    source CIM model id
     * @param expectedRevision expected source revision, or {@code null}
     * @return queued job record
     */
    public MdeJobRecord submitCimToPim(UserRecord user, String sourceModelId,
            Long expectedRevision) {
        return submit(user, ModelLevel.CIM, sourceModelId, MdeJobOperation.CIM_TO_PIM,
                expectedRevision);
    }

    /**
     * Submits a PIM-to-PSM job without revision precondition.
     *
     * @param user          requesting user
     * @param sourceModelId source PIM model id
     * @return queued job record
     */
    public MdeJobRecord submitPimToPsm(UserRecord user, String sourceModelId) {
        return submitPimToPsm(user, sourceModelId, null);
    }

    /**
     * Submits a PIM-to-PSM job with an optional source revision precondition.
     *
     * @param user             requesting user
     * @param sourceModelId    source PIM model id
     * @param expectedRevision expected source revision, or {@code null}
     * @return queued job record
     */
    public MdeJobRecord submitPimToPsm(UserRecord user, String sourceModelId,
            Long expectedRevision) {
        return submit(user, ModelLevel.PIM, sourceModelId, MdeJobOperation.PIM_TO_PSM,
                expectedRevision);
    }

    /**
     * Submits a PSM-to-artifact job without revision precondition.
     *
     * @param user          requesting user
     * @param sourceModelId source PSM model id
     * @return queued job record
     */
    public MdeJobRecord submitPsmToArtifact(UserRecord user, String sourceModelId) {
        return submitPsmToArtifact(user, sourceModelId, null);
    }

    /**
     * Submits a PSM-to-artifact job with an optional source revision precondition.
     *
     * @param user             requesting user
     * @param sourceModelId    source PSM model id
     * @param expectedRevision expected source revision, or {@code null}
     * @return queued job record
     */
    public MdeJobRecord submitPsmToArtifact(UserRecord user, String sourceModelId,
            Long expectedRevision) {
        return submit(user, ModelLevel.PSM, sourceModelId, MdeJobOperation.PSM_TO_ARTIFACT,
                expectedRevision);
    }

    /**
     * Loads a job after verifying project access.
     *
     * @param user requesting user
     * @param id   job identifier
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
     * @param id   job identifier
     * @return terminal or unchanged job record
     */
    public MdeJobRecord cancel(UserRecord user, String id) {
        MdeJobRecord job = get(user, id);
        ProjectRecord project = projectService.get(user, job.projectId());
        projectService.requireEditor(project, user.id());
        if (job.status() == MdeJobStatus.SUCCEEDED || job.status() == MdeJobStatus.FAILED
                || job.status() == MdeJobStatus.CANCELLED) {
            return job;
        }
        Future<?> future = runningJobs.get(id);
        if (future != null) {
            future.cancel(true);
        }
        return write(status(job, MdeJobStatus.CANCELLED, 100, job.resultModelId(),
                job.resultArtifactId(), List.of("Job was cancelled."), job.startedAt(),
                Instant.now()));
    }

    /**
     * Creates and enqueues a job for an operation.
     *
     * @param user             requesting user
     * @param sourceLevel      source model level
     * @param sourceModelId    source model id
     * @param operation        job operation
     * @param expectedRevision expected source revision, or {@code null}
     * @return queued job record
     */
    private MdeJobRecord submit(UserRecord user, ModelLevel sourceLevel, String sourceModelId,
            MdeJobOperation operation, Long expectedRevision) {
        ModelRecord source = modelService.get(user, sourceLevel, sourceModelId);
        if (expectedRevision != null && expectedRevision.longValue() != source.revision()) {
            throw new PlatformException(409, "Source model was modified by another operation.");
        }
        ProjectRecord project = projectService.get(user, source.projectId());
        projectService.requireEditor(project, user.id());
        Instant now = Instant.now();
        MdeJobRecord job = new MdeJobRecord(UUID.randomUUID().toString(), source.projectId(),
                user.id(), source.id(), source.level(), source.revision(), hash(source.modelJson()),
                operation, MdeJobStatus.QUEUED, 0, null, null, List.of(), now, null, null);
        write(job);
        try {
            Future<?> future = executor.submit(() -> run(job.id(), user));
            runningJobs.put(job.id(), future);
        } catch (RuntimeException ex) {
            write(status(job, MdeJobStatus.FAILED, 100, null, null,
                    List.of("Job queue is full."), null, Instant.now()));
            throw new PlatformException(503, "MDE job queue is full. Retry later.");
        }
        return job;
    }

    /**
     * Executes a persisted job and updates terminal status.
     *
     * @param jobId job identifier
     * @param user  submitting user
     */
    private void run(String jobId, UserRecord user) {
        MdeJobRecord job = find(jobId);
        if (job.status() == MdeJobStatus.CANCELLED) {
            return;
        }
        write(status(job, MdeJobStatus.RUNNING, 10, null, null, List.of(), Instant.now(), null));
        try {
            MdeJobRecord latest = find(jobId);
            switch (latest.operation()) {
                case CIM_TO_PIM -> {
                    ModelRecord model = transformationService.cimToPim(user,
                            latest.sourceModelId(), latest.sourceRevision());
                    write(status(latest, MdeJobStatus.SUCCEEDED, 100, model.id(), null,
                            List.of(), latest.startedAt(), Instant.now()));
                }
                case PIM_TO_PSM -> {
                    ModelRecord model = transformationService.pimToPsm(user,
                            latest.sourceModelId(), latest.sourceRevision());
                    write(status(latest, MdeJobStatus.SUCCEEDED, 100, model.id(), null,
                            List.of(), latest.startedAt(), Instant.now()));
                }
                case PSM_TO_ARTIFACT -> {
                    ArtifactRecord artifact = transformationService.psmToArtifact(user,
                            latest.sourceModelId(), latest.sourceRevision());
                    write(status(latest, MdeJobStatus.SUCCEEDED, 100, null, artifact.id(),
                            List.of(), latest.startedAt(), Instant.now()));
                }
                case VALIDATE -> throw new PlatformException(400,
                        "Validation jobs are not supported by this endpoint.");
            }
        } catch (Exception ex) {
            MdeJobRecord failed = find(jobId);
            if (failed.status() == MdeJobStatus.CANCELLED) {
                return;
            }
            write(status(failed, MdeJobStatus.FAILED, 100, failed.resultModelId(),
                    failed.resultArtifactId(), List.of(message(ex)), failed.startedAt(),
                    Instant.now()));
        } finally {
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
        MdeJobIndexRecord index = store.require(jobIndexPath(id), MdeJobIndexRecord.class,
                "MDE job not found.");
        return store.require(jobPath(index.projectId(), id), MdeJobRecord.class,
                "MDE job not found.");
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
     * @param job              existing job
     * @param status           new status
     * @param progress         new progress percentage
     * @param resultModelId    generated model id
     * @param resultArtifactId generated artifact id
     * @param diagnostics      job diagnostics
     * @param startedAt        start timestamp override
     * @param finishedAt       finish timestamp
     * @return updated job record
     */
    private MdeJobRecord status(MdeJobRecord job, MdeJobStatus status, int progress,
            String resultModelId, String resultArtifactId, List<String> diagnostics,
            Instant startedAt, Instant finishedAt) {
        return new MdeJobRecord(job.id(), job.projectId(), job.userId(), job.sourceModelId(),
                job.sourceLevel(), job.sourceRevision(), job.sourceModelHash(), job.operation(),
                status, progress, resultModelId, resultArtifactId, diagnostics, job.createdAt(),
                startedAt == null ? job.startedAt() : startedAt, finishedAt);
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
            return HexFormat.of().formatHex(digest.digest(
                    store.objectMapper().writeValueAsBytes(modelJson)));
        } catch (Exception ex) {
            throw new PlatformException(500, "Could not hash source model.");
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
     * @param id        job identifier
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

    /**
     * Stops worker threads when the service is closed.
     */
    @Override
    public void close() {
        executor.shutdownNow();
    }
}
