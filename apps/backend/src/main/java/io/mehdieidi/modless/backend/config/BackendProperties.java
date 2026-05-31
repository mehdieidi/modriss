package io.mehdieidi.modless.backend.config;

import io.mehdieidi.modless.platform.core.service.MdeRuntimeOptions;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "modless")
public record BackendProperties(Path storageRoot, Duration sessionTtl,
                                List<String> allowedOrigins, Mde mde) {

    public BackendProperties {
        storageRoot = storageRoot == null ? Path.of("storage") : storageRoot;
        sessionTtl = sessionTtl == null ? Duration.ofDays(30) : sessionTtl;
        allowedOrigins = allowedOrigins == null ? List.of() : List.copyOf(allowedOrigins);
        mde = mde == null ? new Mde(null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null) : mde;
    }

    private static int integer(Integer value) {
        return value == null ? 0 : value;
    }

    private static long longValue(Long value) {
        return value == null ? 0 : value;
    }

    public MdeRuntimeOptions mdeRuntimeOptions() {
        return new MdeRuntimeOptions(
                mde.repositoryRoot(),
                mde.validationRoot(),
                mde.transformationRoot(),
                mde.generationRoot(),
                mde.metamodelRoot(),
                mde.executionTimeout(),
                integer(mde.maxCapturedOutputBytes()),
                integer(mde.maxConcurrentJobs()),
                integer(mde.queueCapacity()),
                mde.jobTimeout(),
                longValue(mde.maxModelUploadBytes()),
                integer(mde.maxGeneratedFiles()),
                longValue(mde.maxGeneratedFileBytes()),
                longValue(mde.maxGeneratedArtifactBytes()),
                mde.stagedImportTtl());
    }

    public record Mde(
            Path repositoryRoot,
            Path validationRoot,
            Path transformationRoot,
            Path generationRoot,
            Path metamodelRoot,
            Duration executionTimeout,
            Integer maxCapturedOutputBytes,
            Integer maxConcurrentJobs,
            Integer queueCapacity,
            Duration jobTimeout,
            Long maxModelUploadBytes,
            Integer maxGeneratedFiles,
            Long maxGeneratedFileBytes,
            Long maxGeneratedArtifactBytes,
            Duration stagedImportTtl) {

    }

}
