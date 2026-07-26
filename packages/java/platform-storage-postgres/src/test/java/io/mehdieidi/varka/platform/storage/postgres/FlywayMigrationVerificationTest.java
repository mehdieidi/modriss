package io.mehdieidi.varka.platform.storage.postgres;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/** Verifies Flyway migrations apply cleanly from an empty PostgreSQL database. */
class FlywayMigrationVerificationTest {

  @Test
  void appliesAllMigrationsFromEmptyDatabase() {
    String schema = "flyway_verify_" + System.nanoTime();
    DriverManagerDataSource dataSource =
        new DriverManagerDataSource(
            PostgresTestSupport.jdbcUrl(),
            PostgresTestSupport.username(),
            PostgresTestSupport.password());
    dataSource.setDriverClassName("org.postgresql.Driver");

    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    jdbc.execute("CREATE SCHEMA " + schema);

    try {
      Flyway.configure()
          .dataSource(dataSource)
          .schemas(schema)
          .defaultSchema(schema)
          .locations("classpath:db/migration")
          .load()
          .migrate();

      Integer usersTable =
          jdbc.queryForObject(
              "SELECT count(*) FROM information_schema.tables WHERE table_schema = ? AND table_name"
                  + " = 'users'",
              Integer.class,
              schema);
      assertTrue(usersTable != null && usersTable > 0, "users table should exist after migration");
    } finally {
      jdbc.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
    }
  }
}
