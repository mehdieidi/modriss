package io.mehdieidi.modless.platform.core.service;

import io.mehdieidi.modless.platform.core.model.ModelLevel;

/** Resolves loaded metamodel descriptors for each model level. */
public interface MetamodelResolver {

  /**
   * Resolves the descriptor for the requested model level.
   *
   * @param level model level
   * @return loaded metamodel descriptor
   */
  MetamodelDescriptor resolve(ModelLevel level);
}
