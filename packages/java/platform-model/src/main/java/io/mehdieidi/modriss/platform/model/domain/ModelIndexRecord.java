package io.mehdieidi.modriss.platform.model.domain;

import io.mehdieidi.modriss.platform.kernel.ModelLevel;

/**
 * Global lookup entry mapping a model id to its owning project and level.
 *
 * @param modelId model identifier
 * @param projectId owning project identifier
 * @param level model level
 */
public record ModelIndexRecord(String modelId, String projectId, ModelLevel level) {}
