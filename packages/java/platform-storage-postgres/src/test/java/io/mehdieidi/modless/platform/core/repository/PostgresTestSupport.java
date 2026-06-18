package io.mehdieidi.modless.platform.core.repository;

import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/** Shared PostgreSQL Testcontainer for storage integration tests. */
public final class PostgresTestSupport {

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

  private PostgresTestSupport() {}

  /**
   * Returns the JDBC URL for the shared PostgreSQL test container.
   *
   * @return JDBC connection URL
   */
  public static String jdbcUrl() {
    return CONTAINER.getJdbcUrl();
  }

  /**
   * Returns the database username for the shared PostgreSQL test container.
   *
   * @return database username
   */
  public static String username() {
    return CONTAINER.getUsername();
  }

  /**
   * Returns the database password for the shared PostgreSQL test container.
   *
   * @return database password
   */
  public static String password() {
    return CONTAINER.getPassword();
  }
}
