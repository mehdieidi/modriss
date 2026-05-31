package io.mehdieidi.modless.backend.config;

import io.mehdieidi.modless.platform.core.service.ModelService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public final class ImportStagingCleanup {

    private final ModelService modelService;

    public ImportStagingCleanup(ModelService modelService) {
        this.modelService = modelService;
    }

    @Scheduled(fixedDelayString = "${modless.mde.import-cleanup-interval:PT15M}")
    public void cleanupExpiredImports() {
        modelService.cleanupExpiredImports();
    }
}
