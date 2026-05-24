package io.mehdieidi.modless.backend.config;

import io.mehdieidi.modless.platform.core.repository.JsonFileStore;
import io.mehdieidi.modless.platform.core.service.ArtifactService;
import io.mehdieidi.modless.platform.core.service.AuthService;
import io.mehdieidi.modless.platform.core.service.LayoutService;
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
    ProjectService projectService(JsonFileStore store, AuthService authService) {
        return new ProjectService(store, authService);
    }

    @Bean
    ModelService modelService(JsonFileStore store, ProjectService projectService,
            ModelingConfigService modelingConfigService) {
        return new ModelService(store, projectService, modelingConfigService);
    }

    @Bean
    ArtifactService artifactService(JsonFileStore store, ProjectService projectService) {
        return new ArtifactService(store, projectService);
    }

    @Bean
    TransformationService transformationService(JsonFileStore store, ModelService models,
            ArtifactService artifacts) {
        return new TransformationService(store, models, artifacts);
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
