package io.mehdieidi.varka.platform.transformation.synchronization;

import io.mehdieidi.varka.platform.storage.api.PlatformStore;
import java.nio.file.Path;
import java.util.Optional;

/** Stores baselines separately from user-editable domain models. */
public final class ModelBaselineRepository {

  private final PlatformStore store;

  public ModelBaselineRepository(PlatformStore store) {
    this.store = store;
  }

  public Optional<GeneratedBaseline> get(
      String projectId, TransformationDirection direction, String sourceModelId) {
    return store.read(path(projectId, direction, sourceModelId), GeneratedBaseline.class);
  }

  public void save(GeneratedBaseline baseline) {
    store.write(
        path(baseline.projectId(), baseline.direction(), baseline.sourceModelId()), baseline);
  }

  private Path path(String projectId, TransformationDirection direction, String sourceModelId) {
    return Path.of(
        "projects",
        projectId,
        "synchronization",
        "baselines",
        direction.name().toLowerCase(),
        sourceModelId + ".json");
  }
}
