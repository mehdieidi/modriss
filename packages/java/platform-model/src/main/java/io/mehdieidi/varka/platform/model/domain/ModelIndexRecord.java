package io.mehdieidi.varka.platform.model.domain;

import io.mehdieidi.varka.platform.kernel.ModelLevel;

/**
 * Global lookup entry mapping a model id to its owning project and level.
 *
 * @param modelId model identifier
 * @param projectId owning project identifier
 * @param level model level
 */
public record ModelIndexRecord(String modelId, String projectId, ModelLevel level) {}
