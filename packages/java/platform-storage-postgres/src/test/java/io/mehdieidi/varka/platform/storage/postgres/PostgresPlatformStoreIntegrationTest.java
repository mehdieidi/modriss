package io.mehdieidi.varka.platform.storage.postgres;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.mehdieidi.varka.platform.artifact.domain.ArtifactRecord;
import io.mehdieidi.varka.platform.identity.domain.AuthSession;
import io.mehdieidi.varka.platform.identity.domain.UserRecord;
import io.mehdieidi.varka.platform.kernel.ModelLevel;
import io.mehdieidi.varka.platform.model.domain.ModelIndexRecord;
import io.mehdieidi.varka.platform.model.domain.ModelRecord;
import io.mehdieidi.varka.platform.model.domain.StagedImportRecord;
import io.mehdieidi.varka.platform.project.domain.ProjectMember;
import io.mehdieidi.varka.platform.project.domain.ProjectRecord;
import io.mehdieidi.varka.platform.transformation.domain.MdeJobIndexRecord;
import io.mehdieidi.varka.platform.transformation.domain.MdeJobOperation;
import io.mehdieidi.varka.platform.transformation.domain.MdeJobRecord;
import io.mehdieidi.varka.platform.transformation.domain.MdeJobStatus;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.node.ObjectNode;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ResourceLock("postgres")
class PostgresPlatformStoreIntegrationTest {

  private final String baseUrl = PostgresTestSupport.jdbcUrl();
  private final String user = PostgresTestSupport.username();
  private final String password = PostgresTestSupport.password();
  private String schema;
  private JdbcTemplate jdbc;
  private PostgresPlatformStore store;

  @BeforeAll
  void setUpSchema() {
    schema = "test_" + UUID.randomUUID().toString().replace("-", "");
    DriverManagerDataSource admin = dataSource(baseUrl);
    JdbcTemplate adminJdbc = new JdbcTemplate(admin);
    adminJdbc.execute("CREATE EXTENSION IF NOT EXISTS vector WITH SCHEMA public");
    adminJdbc.execute("CREATE SCHEMA " + schema);

    String schemaUrl = withCurrentSchema(baseUrl, schema);
    Flyway.configure()
        .dataSource(schemaUrl, user, password)
        .schemas(schema)
        .defaultSchema(schema)
        .locations("classpath:db/migration")
        .load()
        .migrate();

    DriverManagerDataSource dataSource = dataSource(schemaUrl);
    jdbc = new JdbcTemplate(dataSource);
    store =
        new PostgresPlatformStore(
            jdbc, new TransactionTemplate(new DataSourceTransactionManager(dataSource)));
  }

  @BeforeEach
  void cleanTables() {
    truncateAllTables();
  }

  @AfterAll
  void tearDownSchema() {
    if (schema != null) {
      new JdbcTemplate(dataSource(baseUrl)).execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
    }
  }

  @Test
  void persistsDomainRecordsAndBlobsInPostgresTables() {
    Instant now = Instant.now();
    UserRecord userRecord =
        new UserRecord("user-1", "user@example.com", "User", "hash", "salt", now, now);
    store.write(Path.of("users", "user-1.json"), userRecord);
    store.write(
        Path.of("sessions", "token-1.json"),
        new AuthSession("token-1", "user-1", now, now.plusSeconds(3600)));

    ProjectRecord project =
        new ProjectRecord(
            "project-1",
            "Project",
            "Description",
            "user-1",
            new LinkedHashMap<>(Map.of("cim", "model-1")),
            List.of(new ProjectMember("user-1", "user@example.com", "User", "OWNER", now)),
            now,
            now);
    store.write(Path.of("projects", "project-1", "project.json"), project);

    ObjectNode modelJson = store.objectMapper().createObjectNode();
    modelJson.put("eClass", "CIMModel");
    modelJson.put("name", "Model");
    ModelRecord model =
        new ModelRecord(
            "model-1",
            "project-1",
            ModelLevel.CIM,
            "Model",
            modelJson,
            "1.0",
            "hash",
            1,
            "xmi-hash",
            "CURRENT",
            now,
            now);
    store.write(Path.of("projects", "project-1", "models", "cim", "model-1.json"), model);
    store.writeBytesAtomically(
        Path.of("projects", "project-1", "models", "cim", "model-1.xmi"), new byte[] {1, 2, 3});

    store.write(
        Path.of("model-imports", "stage-1.json"),
        new StagedImportRecord(
            "stage-1",
            "user-1",
            "project-1",
            ModelLevel.CIM,
            "model-imports/stage-1.xmi",
            3,
            now,
            now.plusSeconds(60)));
    store.writeBytesAtomically(Path.of("model-imports", "stage-1.xmi"), new byte[] {4, 5, 6});

    ArtifactRecord artifact =
        new ArtifactRecord(
            "artifact-1",
            "project-1",
            "Artifact",
            store.objectMapper().createObjectNode(),
            Map.of("README.md", "hello"),
            now,
            now);
    store.write(Path.of("projects", "project-1", "artifacts", "artifact-1.json"), artifact);
    MdeJobRecord job =
        new MdeJobRecord(
            "job-1",
            "project-1",
            "user-1",
            "model-1",
            ModelLevel.CIM,
            1,
            "model-hash",
            MdeJobOperation.CIM_TO_PIM,
            MdeJobStatus.FAILED,
            100,
            null,
            null,
            List.of("diagnostic"),
            now,
            now,
            now);
    store.write(Path.of("projects", "project-1", "mde-jobs", "job-1.json"), job);
    UserRecord storedUser =
        store.require(Path.of("users", "user-1.json"), UserRecord.class, "missing");
    assertEquals(userRecord.id(), storedUser.id());
    assertEquals(userRecord.email(), storedUser.email());
    assertEquals(1, store.list(Path.of("projects"), ProjectRecord.class).size());
    assertEquals(
        1, store.list(Path.of("projects", "project-1", "models", "cim"), ModelRecord.class).size());
    assertArrayEquals(
        new byte[] {1, 2, 3},
        store
            .readBytes(Path.of("projects", "project-1", "models", "cim", "model-1.xmi"))
            .orElseThrow());
    assertArrayEquals(
        new byte[] {4, 5, 6},
        store.readBytes(Path.of("model-imports", "stage-1.xmi")).orElseThrow());
    assertEquals(
        "hello",
        store
            .require(
                Path.of("projects", "project-1", "artifacts", "artifact-1.json"),
                ArtifactRecord.class,
                "missing")
            .files()
            .get("README.md"));
    assertEquals(
        "project-1",
        store
            .require(
                Path.of("indexes", "models", "model-1.json"), ModelIndexRecord.class, "missing")
            .projectId());
    assertEquals(
        "project-1",
        store
            .require(
                Path.of("indexes", "mde-jobs", "job-1.json"), MdeJobIndexRecord.class, "missing")
            .projectId());
    assertEquals(
        List.of("diagnostic"),
        store
            .require(
                Path.of("projects", "project-1", "mde-jobs", "job-1.json"),
                MdeJobRecord.class,
                "missing")
            .diagnostics());
    byte[] serializedModel =
        store
            .readBytes(Path.of("projects", "project-1", "models", "cim", "model-1.json"))
            .orElseThrow();
    store.writeBytesAtomically(
        Path.of("projects", "project-1", "models", "cim", "model-1.json"), serializedModel);
    store.deleteIfExists(Path.of("sessions", "token-1.json"));
    store.deleteIfExists(Path.of("model-imports", "stage-1.json"));

    store.deleteTree(Path.of("projects", "project-1"));

    assertFalse(
        store
            .read(Path.of("projects", "project-1", "project.json"), ProjectRecord.class)
            .isPresent());
    assertEquals(0, count("models"));
    assertEquals(0, count("artifact_files"));
    assertEquals(0, count("mde_jobs"));
    assertEquals(0, count("staged_imports"));
    assertEquals(0, count("staged_import_payloads"));
    assertEquals(0, count("auth_sessions"));
  }

  @Test
  void missingOptionalRecordsReadAsEmpty() {
    assertTrue(
        store
            .read(
                Path.of("indexes", "models", "missing.json"),
                io.mehdieidi.varka.platform.model.domain.ModelIndexRecord.class)
            .isEmpty());
  }

  private void truncateAllTables() {
    List<String> tables =
        jdbc.queryForList(
            "SELECT tablename FROM pg_tables WHERE schemaname = ?", String.class, schema);
    if (tables.isEmpty()) {
      return;
    }
    String tableList =
        tables.stream()
            .map(table -> quoteIdentifier(table))
            .reduce((left, right) -> left + ", " + right)
            .orElse("");
    jdbc.execute("TRUNCATE TABLE " + tableList + " RESTART IDENTITY CASCADE");
  }

  private String quoteIdentifier(String identifier) {
    return "\"" + identifier.replace("\"", "\"\"") + "\"";
  }

  private int count(String table) {
    return jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class);
  }

  private DriverManagerDataSource dataSource(String url) {
    DriverManagerDataSource dataSource = new DriverManagerDataSource(url, user, password);
    dataSource.setDriverClassName("org.postgresql.Driver");
    return dataSource;
  }

  private String withCurrentSchema(String url, String schema) {
    return url + (url.contains("?") ? "&" : "?") + "currentSchema=" + schema + ",public";
  }
}
