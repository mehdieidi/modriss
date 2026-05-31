package io.mehdieidi.modless.backend.config;

import io.mehdieidi.modless.platform.core.repository.JsonFileStore;
import io.mehdieidi.modless.platform.core.service.ArtifactService;
import io.mehdieidi.modless.platform.core.service.AuthService;
import io.mehdieidi.modless.platform.core.service.FileMetamodelResolver;
import io.mehdieidi.modless.platform.core.service.LayoutService;
import io.mehdieidi.modless.platform.core.service.MdeJobService;
import io.mehdieidi.modless.platform.core.service.MdeRuntimeOptions;
import io.mehdieidi.modless.platform.core.service.MdeRuntimePaths;
import io.mehdieidi.modless.platform.core.service.MetamodelResolver;
import io.mehdieidi.modless.platform.core.service.ModelLockService;
import io.mehdieidi.modless.platform.core.service.ModelService;
import io.mehdieidi.modless.platform.core.service.ModelingConfigService;
import io.mehdieidi.modless.platform.core.service.ProjectService;
import io.mehdieidi.modless.platform.core.service.TransformationService;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(BackendProperties.class)
public class CoreServicesConfig {

    @Bean
    JsonFileStore jsonFileStore(BackendProperties properties) {
        JsonFileStore store = new JsonFileStore(properties.storageRoot());
        store.initialize();
        return store;
    }

    @Bean
    AuthService authService(JsonFileStore store, BackendProperties properties) {
        return new AuthService(store, properties.sessionTtl());
    }

    @Bean
    MdeRuntimeOptions mdeRuntimeOptions(BackendProperties properties) {
        return properties.mdeRuntimeOptions();
    }

    @Bean
    ModelLockService modelLockService() {
        return new ModelLockService();
    }

    @Bean
    MdeRuntimePaths mdeRuntimePaths(MdeRuntimeOptions options) {
        return new MdeRuntimePaths(options);
    }

    @Bean
    MetamodelResolver metamodelResolver(MdeRuntimePaths paths) {
        return new FileMetamodelResolver(paths);
    }

    @Bean
    ProjectService projectService(JsonFileStore store, AuthService authService) {
        return new ProjectService(store, authService);
    }

    @Bean
    ModelService modelService(JsonFileStore store, ProjectService projectService,
            ModelingConfigService modelingConfigService, MdeRuntimeOptions mdeRuntimeOptions,
            MdeRuntimePaths mdeRuntimePaths, MetamodelResolver metamodelResolver,
            ModelLockService modelLockService) {
        return new ModelService(store, projectService, modelingConfigService, mdeRuntimeOptions,
                mdeRuntimePaths, metamodelResolver, modelLockService);
    }

    @Bean
    ArtifactService artifactService(JsonFileStore store, ProjectService projectService) {
        return new ArtifactService(store, projectService);
    }

    @Bean
    TransformationService transformationService(JsonFileStore store, ModelService models,
            ArtifactService artifacts, MdeRuntimeOptions mdeRuntimeOptions,
            MdeRuntimePaths mdeRuntimePaths, MetamodelResolver metamodelResolver,
            ModelLockService modelLockService) {
        return new TransformationService(store, models, artifacts, mdeRuntimeOptions,
                mdeRuntimePaths, metamodelResolver, modelLockService);
    }

    @Bean
    MdeJobService mdeJobService(JsonFileStore store, ProjectService projects, ModelService models,
            TransformationService transformations, MdeRuntimeOptions mdeRuntimeOptions) {
        return new MdeJobService(store, projects, models, transformations, mdeRuntimeOptions);
    }

    @Bean
    LayoutService layoutService() {
        return new LayoutService();
    }

    @Bean
    ModelingConfigService modelingConfigService() {
        return new ModelingConfigService();
    }
}
