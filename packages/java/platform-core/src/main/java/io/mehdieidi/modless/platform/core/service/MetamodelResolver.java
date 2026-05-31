package io.mehdieidi.modless.platform.core.service;

import io.mehdieidi.modless.platform.core.model.ModelLevel;

public interface MetamodelResolver {

    MetamodelDescriptor resolve(ModelLevel level);
}
