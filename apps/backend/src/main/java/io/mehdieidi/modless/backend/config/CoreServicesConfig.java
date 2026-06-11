package io.mehdieidi.modless.backend.config;

import io.mehdieidi.modless.platform.core.model.ModelLevel;
import io.mehdieidi.modless.platform.core.repository.PlatformStore;
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
import io.mehdieidi.modless.platform.core.service.StoredViewLayoutService;
import io.mehdieidi.modless.platform.core.service.TransformationService;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Composes platform-core services and their storage-backed dependencies for the backend.
 */
@Configuration
@EnableConfigurationProperties(BackendProperties.class)
public class CoreServicesConfig {

    /**
     * Creates the authentication service.
     *
     * @param store      persistent store
     * @param properties session settings
     * @return authentication service
     */
    @Bean
    AuthService authService(PlatformStore store, BackendProperties properties) {
        return new AuthService(store, properties.sessionTtl());
    }

    /**
     * Exposes normalized MDE runtime options.
     *
     * @param properties backend configuration
     * @return MDE runtime options
     */
    @Bean
    MdeRuntimeOptions mdeRuntimeOptions(BackendProperties properties) {
        return properties.mdeRuntimeOptions();
    }

    /**
     * Creates the service that coordinates concurrent model access.
     *
     * @return model lock service
     */
    @Bean
    ModelLockService modelLockService() {
        return new ModelLockService();
    }

    /**
     * Resolves runtime workspace paths from configured options.
     *
     * @param options MDE runtime options
     * @return runtime path resolver
     */
    @Bean
    MdeRuntimePaths mdeRuntimePaths(MdeRuntimeOptions options) {
        return new MdeRuntimePaths(options);
    }

    /**
     * Creates and eagerly validates the file-backed metamodel resolver.
     *
     * @param paths runtime path resolver
     * @return metamodel resolver
     */
    @Bean
    MetamodelResolver metamodelResolver(MdeRuntimePaths paths) {
        FileMetamodelResolver resolver = new FileMetamodelResolver(paths);
        for (ModelLevel level : ModelLevel.values()) {
            resolver.resolve(level);
        }
        return resolver;
    }

    /**
     * Creates the project service.
     *
     * @param store       persistent store
     * @param authService authentication service
     * @return project service
     */
    @Bean
    ProjectService projectService(PlatformStore store, AuthService authService) {
        return new ProjectService(store, authService);
    }

    /**
     * Creates the model lifecycle service.
     *
     * @param store                 persistent store
     * @param projectService        project service
     * @param modelingConfigService modeling configuration service
     * @param mdeRuntimeOptions     MDE runtime limits
     * @param mdeRuntimePaths       MDE runtime paths
     * @param metamodelResolver     metamodel resolver
     * @param modelLockService      model concurrency coordinator
     * @return model service
     */
    @Bean
    ModelService modelService(PlatformStore store, ProjectService projectService,
            ModelingConfigService modelingConfigService, MdeRuntimeOptions mdeRuntimeOptions,
            MdeRuntimePaths mdeRuntimePaths, MetamodelResolver metamodelResolver,
            ModelLockService modelLockService) {
        return new ModelService(store, projectService, modelingConfigService, mdeRuntimeOptions,
                mdeRuntimePaths, metamodelResolver, modelLockService);
    }

    /**
     * Creates the generated-artifact service.
     *
     * @param store          persistent store
     * @param projectService project service
     * @return artifact service
     */
    @Bean
    ArtifactService artifactService(PlatformStore store, ProjectService projectService) {
        return new ArtifactService(store, projectService);
    }

    /**
     * Creates the model transformation service.
     *
     * @param store             persistent store
     * @param models            model service
     * @param artifacts         artifact service
     * @param mdeRuntimeOptions MDE runtime limits
     * @param mdeRuntimePaths   MDE runtime paths
     * @param metamodelResolver metamodel resolver
     * @param modelLockService  model concurrency coordinator
     * @return transformation service
     */
    @Bean
    TransformationService transformationService(PlatformStore store, ModelService models,
            ArtifactService artifacts, MdeRuntimeOptions mdeRuntimeOptions,
            MdeRuntimePaths mdeRuntimePaths, MetamodelResolver metamodelResolver,
            ModelLockService modelLockService) {
        return new TransformationService(store, models, artifacts, mdeRuntimeOptions,
                mdeRuntimePaths, metamodelResolver, modelLockService);
    }

    /**
     * Creates the asynchronous MDE job service.
     *
     * @param store             persistent store
     * @param projects          project service
     * @param models            model service
     * @param transformations   transformation service
     * @param mdeRuntimeOptions job runtime limits
     * @return MDE job service
     */
    @Bean
    MdeJobService mdeJobService(PlatformStore store, ProjectService projects, ModelService models,
            TransformationService transformations, MdeRuntimeOptions mdeRuntimeOptions) {
        return new MdeJobService(store, projects, models, transformations, mdeRuntimeOptions);
    }

    /**
     * Creates the stateless diagram layout service.
     *
     * @return layout service
     */
    @Bean
    LayoutService layoutService() {
        return new LayoutService();
    }

    /**
     * Creates the service that lays out and persists stored model views.
     *
     * @param models  model service
     * @param layouts diagram layout service
     * @return stored-view layout service
     */
    @Bean
    StoredViewLayoutService storedViewLayoutService(ModelService models, LayoutService layouts) {
        return new StoredViewLayoutService(models, layouts);
    }

    /**
     * Creates the modeling palette and configuration service.
     *
     * @return modeling configuration service
     */
    @Bean
    ModelingConfigService modelingConfigService() {
        return new ModelingConfigService();
    }
}
