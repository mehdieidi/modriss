package io.mehdieidi.modless.backend.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/** Shared PostgreSQL Testcontainer wiring for backend integration tests. */
public final class BackendPostgresTestContainer {

  private static final DockerImageName IMAGE =
      DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres");

  private static final PostgreSQLContainer<?> CONTAINER =
      new PostgreSQLContainer<>(IMAGE)
          .withDatabaseName("modless")
          .withUsername("modless")
          .withPassword("modless")
          .withInitScript("db/test-init.sql");

  static {
    CONTAINER.start();
  }

  private BackendPostgresTestContainer() {}

  /**
   * Registers datasource properties backed by the shared PostgreSQL test container.
   *
   * @param registry Spring dynamic property registry
   */
  public static void registerDataSourceProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", CONTAINER::getJdbcUrl);
    registry.add("spring.datasource.username", CONTAINER::getUsername);
    registry.add("spring.datasource.password", CONTAINER::getPassword);
  }
}
