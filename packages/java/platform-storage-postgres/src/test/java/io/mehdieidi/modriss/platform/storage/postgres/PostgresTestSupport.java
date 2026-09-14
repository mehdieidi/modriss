package io.mehdieidi.modriss.platform.storage.postgres;

import org.junit.jupiter.api.Assumptions;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/** Shared PostgreSQL Testcontainer for storage integration tests. */
public final class PostgresTestSupport {

  private static final DockerImageName IMAGE =
      DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres");

  private static final PostgreSQLContainer<?> CONTAINER =
      new PostgreSQLContainer<>(IMAGE)
          .withDatabaseName("modriss")
          .withUsername("modriss")
          .withPassword("modriss")
          .withInitScript("db/test-init.sql");

  private static boolean started;

  private PostgresTestSupport() {}

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
          "Docker is not available for PostgreSQL integration tests: " + ex.getMessage());
    }
  }

  /**
   * Returns the JDBC URL for the shared PostgreSQL test container.
   *
   * @return JDBC connection URL
   */
  public static String jdbcUrl() {
    assumeAvailable();
    return CONTAINER.getJdbcUrl();
  }

  /**
   * Returns the database username for the shared PostgreSQL test container.
   *
   * @return database username
   */
  public static String username() {
    assumeAvailable();
    return CONTAINER.getUsername();
  }

  /**
   * Returns the database password for the shared PostgreSQL test container.
   *
   * @return database password
   */
  public static String password() {
    assumeAvailable();
    return CONTAINER.getPassword();
  }
}
