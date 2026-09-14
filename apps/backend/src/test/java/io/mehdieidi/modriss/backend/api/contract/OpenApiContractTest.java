package io.mehdieidi.modriss.backend.api.contract;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.mehdieidi.modriss.backend.support.BackendPostgresTestContainer;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Ensures Spring MVC handlers and the checked-in OpenAPI spec stay synchronized. */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class OpenApiContractTest {

  @DynamicPropertySource
  static void registerPostgres(DynamicPropertyRegistry registry) {
    BackendPostgresTestContainer.registerDataSourceProperties(registry);
  }

  @Autowired
  @Qualifier("requestMappingHandlerMapping") private RequestMappingHandlerMapping handlerMapping;

  @Test
  void springHandlersMatchOpenApiSpec() throws Exception {
    Set<String> springOperations = OpenApiContractSupport.loadSpringOperations(handlerMapping);
    Set<String> openApiOperations = OpenApiContractSupport.loadOpenApiOperations();

    Set<String> missingFromSpec = new HashSet<>(springOperations);
    missingFromSpec.removeAll(openApiOperations);

    Set<String> missingFromSpring = new HashSet<>(openApiOperations);
    missingFromSpring.removeAll(springOperations);

    assertTrue(
        missingFromSpec.isEmpty(),
        OpenApiContractSupport.formatMismatch(
            "Documented in Spring but missing from OpenAPI", missingFromSpec));
    assertTrue(
        missingFromSpring.isEmpty(),
        OpenApiContractSupport.formatMismatch(
            "Declared in OpenAPI but missing from Spring", missingFromSpring));
  }
}
