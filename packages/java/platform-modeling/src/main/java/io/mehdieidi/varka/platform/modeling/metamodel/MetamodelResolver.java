package io.mehdieidi.varka.platform.modeling.metamodel;

import io.mehdieidi.varka.platform.kernel.ModelLevel;

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
