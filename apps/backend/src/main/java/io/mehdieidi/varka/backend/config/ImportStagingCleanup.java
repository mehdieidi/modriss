package io.mehdieidi.varka.backend.config;

import io.mehdieidi.varka.platform.model.application.ModelService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Periodically removes expired staged model imports. */
@Component
public final class ImportStagingCleanup {

  private final ModelService modelService;

  /**
   * Creates the cleanup task.
   *
   * @param modelService model service that owns staged imports
   */
  public ImportStagingCleanup(ModelService modelService) {
    this.modelService = modelService;
  }

  /** Removes imports whose staging lifetime has elapsed. */
  @Scheduled(fixedDelayString = "${varka.mde.import-cleanup-interval:PT15M}")
  public void cleanupExpiredImports() {
    modelService.cleanupExpiredImports();
  }
}
