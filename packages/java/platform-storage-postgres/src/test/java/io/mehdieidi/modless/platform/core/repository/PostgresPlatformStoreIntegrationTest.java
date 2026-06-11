package io.mehdieidi.modless.platform.core.repository;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.mehdieidi.modless.platform.core.model.ArtifactRecord;
import io.mehdieidi.modless.platform.core.model.AuthSession;
import io.mehdieidi.modless.platform.core.model.MemberRole;
import io.mehdieidi.modless.platform.core.model.ModelLevel;
import io.mehdieidi.modless.platform.core.model.ModelRecord;
import io.mehdieidi.modless.platform.core.model.ProjectMember;
import io.mehdieidi.modless.platform.core.model.ProjectRecord;
import io.mehdieidi.modless.platform.core.model.StagedImportRecord;
import io.mehdieidi.modless.platform.core.model.UserRecord;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

class PostgresPlatformStoreIntegrationTest {

    private final String baseUrl = System.getenv().getOrDefault("MODLESS_POSTGRES_TEST_URL",
            "jdbc:postgresql://localhost:5432/modless");
    private final String user = System.getenv().getOrDefault("MODLESS_POSTGRES_TEST_USER",
            "modless");
    private final String password = System.getenv().getOrDefault("MODLESS_POSTGRES_TEST_PASSWORD",
            "modless");
    private String schema;
    private JdbcTemplate jdbc;
    private PostgresPlatformStore store;

    @BeforeEach
    void setUp() throws Exception {
        assumeTrue(databaseAvailable(), "PostgreSQL test database is not available.");
        schema = "test_" + UUID.randomUUID().toString().replace("-", "");
        DriverManagerDataSource admin = dataSource(baseUrl);
        new JdbcTemplate(admin).execute("CREATE SCHEMA " + schema);

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
        store = new PostgresPlatformStore(jdbc,
                new TransactionTemplate(new DataSourceTransactionManager(dataSource)));
    }

    @AfterEach
    void tearDown() {
        if (schema != null && databaseAvailable()) {
            new JdbcTemplate(dataSource(baseUrl)).execute("DROP SCHEMA IF EXISTS " + schema
                    + " CASCADE");
        }
    }

    @Test
    void persistsDomainRecordsAndBlobsInPostgresTables() {
        Instant now = Instant.now();
        UserRecord userRecord = new UserRecord("user-1", "user@example.com", "User",
                "hash", "salt", now, now);
        store.write(Path.of("users", "user-1.json"), userRecord);
        store.write(Path.of("sessions", "token-1.json"),
                new AuthSession("token-1", "user-1", now, now.plusSeconds(3600)));

        ProjectRecord project = new ProjectRecord("project-1", "Project", "Description",
                "user-1", new LinkedHashMap<>(Map.of("cim", "model-1")),
                List.of(new ProjectMember("user-1", "user@example.com", "User",
                        MemberRole.OWNER, now)), now, now);
        store.write(Path.of("projects", "project-1", "project.json"), project);

        ObjectNode modelJson = store.objectMapper().createObjectNode();
        modelJson.put("eClass", "CIMModel");
        modelJson.put("name", "Model");
        ModelRecord model = new ModelRecord("model-1", "project-1", ModelLevel.CIM, "Model",
                modelJson, "1.0", "hash", 1, "xmi-hash", "CURRENT", now, now);
        store.write(Path.of("projects", "project-1", "models", "cim", "model-1.json"), model);
        store.writeBytesAtomically(Path.of("projects", "project-1", "models", "cim",
                "model-1.xmi"), new byte[]{1, 2, 3});

        store.write(Path.of("model-imports", "stage-1.json"),
                new StagedImportRecord("stage-1", "user-1", "project-1", ModelLevel.CIM,
                        "model-imports/stage-1.xmi", 3, now, now.plusSeconds(60)));
        store.writeBytesAtomically(Path.of("model-imports", "stage-1.xmi"),
                new byte[]{4, 5, 6});

        ArtifactRecord artifact = new ArtifactRecord("artifact-1", "project-1", "Artifact",
                store.objectMapper().createObjectNode(), Map.of("README.md", "hello"), now, now);
        store.write(Path.of("projects", "project-1", "artifacts", "artifact-1.json"), artifact);

        UserRecord storedUser = store.require(Path.of("users", "user-1.json"),
                UserRecord.class, "missing");
        assertEquals(userRecord.id(), storedUser.id());
        assertEquals(userRecord.email(), storedUser.email());
        assertEquals(1, store.list(Path.of("projects"), ProjectRecord.class).size());
        assertEquals(1, store.list(Path.of("projects", "project-1", "models", "cim"),
                ModelRecord.class).size());
        assertArrayEquals(new byte[]{1, 2, 3}, store.readBytes(Path.of("projects",
                "project-1", "models", "cim", "model-1.xmi")).orElseThrow());
        assertArrayEquals(new byte[]{4, 5, 6}, store.readBytes(Path.of("model-imports",
                "stage-1.xmi")).orElseThrow());
        assertEquals("hello", store.require(Path.of("projects", "project-1", "artifacts",
                "artifact-1.json"), ArtifactRecord.class, "missing").files().get("README.md"));

        store.deleteTree(Path.of("projects", "project-1"));

        assertFalse(store.read(Path.of("projects", "project-1", "project.json"),
                ProjectRecord.class).isPresent());
        assertEquals(0, count("models"));
        assertEquals(0, count("artifact_files"));
        assertEquals(0, count("staged_imports"));
        assertEquals(0, count("staged_import_payloads"));
    }

    @Test
    void missingOptionalRecordsReadAsEmpty() {
        assertTrue(store.read(Path.of("indexes", "models", "missing.json"),
                io.mehdieidi.modless.platform.core.model.ModelIndexRecord.class).isEmpty());
    }

    private int count(String table) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class);
    }

    private boolean databaseAvailable() {
        try (var connection = DriverManager.getConnection(baseUrl, user, password)) {
            return connection.isValid(2);
        } catch (Exception ex) {
            return false;
        }
    }

    private DriverManagerDataSource dataSource(String url) {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(url, user, password);
        dataSource.setDriverClassName("org.postgresql.Driver");
        return dataSource;
    }

    private String withCurrentSchema(String url, String schema) {
        return url + (url.contains("?") ? "&" : "?") + "currentSchema=" + schema;
    }
}
