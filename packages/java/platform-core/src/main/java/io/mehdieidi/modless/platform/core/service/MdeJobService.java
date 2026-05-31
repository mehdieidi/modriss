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

public final class MdeJobService implements AutoCloseable {

    private final JsonFileStore store;
    private final ProjectService projectService;
    private final ModelService modelService;
    private final TransformationService transformationService;
    private final ThreadPoolExecutor executor;
    private final Map<String, Future<?>> runningJobs = new ConcurrentHashMap<>();

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

    public MdeJobRecord submitCimToPim(UserRecord user, String sourceModelId) {
        return submitCimToPim(user, sourceModelId, null);
    }

    public MdeJobRecord submitCimToPim(UserRecord user, String sourceModelId,
            Long expectedRevision) {
        return submit(user, ModelLevel.CIM, sourceModelId, MdeJobOperation.CIM_TO_PIM,
                expectedRevision);
    }

    public MdeJobRecord submitPimToPsm(UserRecord user, String sourceModelId) {
        return submitPimToPsm(user, sourceModelId, null);
    }

    public MdeJobRecord submitPimToPsm(UserRecord user, String sourceModelId,
            Long expectedRevision) {
        return submit(user, ModelLevel.PIM, sourceModelId, MdeJobOperation.PIM_TO_PSM,
                expectedRevision);
    }

    public MdeJobRecord submitPsmToArtifact(UserRecord user, String sourceModelId) {
        return submitPsmToArtifact(user, sourceModelId, null);
    }

    public MdeJobRecord submitPsmToArtifact(UserRecord user, String sourceModelId,
            Long expectedRevision) {
        return submit(user, ModelLevel.PSM, sourceModelId, MdeJobOperation.PSM_TO_ARTIFACT,
                expectedRevision);
    }

    public MdeJobRecord get(UserRecord user, String id) {
        MdeJobRecord job = find(id);
        projectService.get(user, job.projectId());
        return job;
    }

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

    private MdeJobRecord find(String id) {
        MdeJobIndexRecord index = store.require(jobIndexPath(id), MdeJobIndexRecord.class,
                "MDE job not found.");
        return store.require(jobPath(index.projectId(), id), MdeJobRecord.class,
                "MDE job not found.");
    }

    private MdeJobRecord write(MdeJobRecord job) {
        store.write(jobPath(job.projectId(), job.id()), job);
        store.write(jobIndexPath(job.id()), new MdeJobIndexRecord(job.id(), job.projectId()));
        return job;
    }

    private MdeJobRecord status(MdeJobRecord job, MdeJobStatus status, int progress,
            String resultModelId, String resultArtifactId, List<String> diagnostics,
            Instant startedAt, Instant finishedAt) {
        return new MdeJobRecord(job.id(), job.projectId(), job.userId(), job.sourceModelId(),
                job.sourceLevel(), job.sourceRevision(), job.sourceModelHash(), job.operation(),
                status, progress, resultModelId, resultArtifactId, diagnostics, job.createdAt(),
                startedAt == null ? job.startedAt() : startedAt, finishedAt);
    }

    private String hash(JsonNode modelJson) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(
                    store.objectMapper().writeValueAsBytes(modelJson)));
        } catch (Exception ex) {
            throw new PlatformException(500, "Could not hash source model.");
        }
    }

    private String message(Exception ex) {
        if (ex instanceof PlatformException platformException) {
            return platformException.getMessage();
        }
        return ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
    }

    private Path jobPath(String projectId, String id) {
        return Path.of("projects", projectId, "mde-jobs", id + ".json");
    }

    private Path jobIndexPath(String id) {
        return Path.of("indexes", "mde-jobs", id + ".json");
    }

    @Override
    public void close() {
        executor.shutdownNow();
    }
}
