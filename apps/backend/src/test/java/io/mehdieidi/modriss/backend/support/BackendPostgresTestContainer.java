package io.mehdieidi.modriss.backend.support;

import org.junit.jupiter.api.Assumptions;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/** Shared PostgreSQL Testcontainer wiring for backend integration tests. */
public final class BackendPostgresTestContainer {

  private static final DockerImageName IMAGE =
      DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres");

  private static final PostgreSQLContainer<?> CONTAINER =
      new PostgreSQLContainer<>(IMAGE)
          .withDatabaseName("modriss")
          .withUsername("modriss")
          .withPassword("modriss")
          .withInitScript("db/test-init.sql");

  private static boolean started;

  private BackendPostgresTestContainer() {}

  /**
   * Registers datasource properties backed by the shared PostgreSQL test container.
   *
   * @param registry Spring dynamic property registry
   */
  public static void registerDataSourceProperties(DynamicPropertyRegistry registry) {
    assumeAvailable();
    registry.add("spring.datasource.url", CONTAINER::getJdbcUrl);
    registry.add("spring.datasource.username", CONTAINER::getUsername);
    registry.add("spring.datasource.password", CONTAINER::getPassword);
  }

  /**
   * Starts the shared PostgreSQL container or skips the calling test when Docker is unavailable.
   */
  public static synchronized void assumeAvailable() {
    if (started) {
      return;
    }
    try {
      CONTAINER.start();
      started = true;
    } catch (RuntimeException ex) {
      Assumptions.abort(
          "Docker is not available for backend PostgreSQL contract tests: " + ex.getMessage());
    }
  }
}
