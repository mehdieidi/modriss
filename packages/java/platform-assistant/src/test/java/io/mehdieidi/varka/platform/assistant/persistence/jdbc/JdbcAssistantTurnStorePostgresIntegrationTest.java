package io.mehdieidi.varka.platform.assistant.persistence.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.mehdieidi.varka.platform.assistant.turn.AssistantTurn;
import io.mehdieidi.varka.platform.assistant.turn.AssistantTurnStore;
import io.mehdieidi.varka.platform.kernel.ModelLevel;
import java.time.Instant;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.ObjectMapper;

/**
 * Exercises the durable store against the actual PostgreSQL/Flyway schema, not JdbcTemplate mocks.
 */
@Testcontainers(disabledWithoutDocker = true)
class JdbcAssistantTurnStorePostgresIntegrationTest {
  @Container
  static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("pgvector/pgvector:pg16")
          .withDatabaseName("assistant")
          .withUsername("assistant")
          .withPassword("assistant");

  private JdbcTemplate jdbc;
  private JdbcAssistantTurnStore turns;

  @BeforeEach
  @SuppressWarnings("unused")
  void setUp() {
    var dataSource =
        new DriverManagerDataSource(
            POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    jdbc = new JdbcTemplate(dataSource);
    jdbc.execute("DROP SCHEMA public CASCADE; CREATE SCHEMA public");
    jdbc.execute("CREATE TABLE users (id text PRIMARY KEY)");
    jdbc.execute("CREATE TABLE projects (id text PRIMARY KEY)");
    jdbc.update("INSERT INTO users(id) VALUES ('user')");
    jdbc.update("INSERT INTO projects(id) VALUES ('project')");
    Flyway.configure()
        .dataSource(dataSource)
        .locations("classpath:db/assistant-migration")
        .baselineOnMigrate(true)
        .load()
        .migrate();
    jdbc.update(
        "INSERT INTO assistant_threads(id, user_id, project_id, level, title, created_at,"
            + " updated_at) VALUES ('thread', 'user', 'project', 'CIM', 'test', now(), now())");
    turns = new JdbcAssistantTurnStore(jdbc, new ObjectMapper());
  }

  @Test
  void persistsProvenanceTelemetryRecursiveContinuationsAndCheckpoints() {
    AssistantTurn first = turn("turn-1");
    AssistantTurn second = turn("turn-2");
    AssistantTurn third = turn("turn-3");
    turns.create(first);
    turns.create(second);
    turns.create(third);
    turns.linkContinuation(first.id(), second.id());
    turns.linkContinuation(second.id(), third.id());
    turns.saveProvenance(first.id(), "element-1", null, "R1", "INFERRED", "derived from policy");
    turns.recordProviderCalls(
        first.id(), List.of(new AssistantTurnStore.ProviderCall("test", "model", 23, 7, 5, true)));
    turns.saveCheckpoint(first.id(), "model", 4, java.util.Map.of("operations", List.of()));
    turns.saveContextCache(
        first.id(),
        new AssistantTurnStore.ContextCache(List.of("source-1"), List.of("Goal closure")));

    assertEquals(2, turns.continuations(first.id()).size());
    assertEquals("R1", turns.provenance(first.id()).get(0).requirementId());
    assertEquals(
        1,
        jdbc.queryForObject(
            "SELECT count(*) FROM assistant_provider_calls WHERE turn_id = 'turn-1'",
            Integer.class));
    assertEquals(4L, turns.latestCheckpoint(first.id()).orElseThrow().revision());
    assertEquals(
        List.of("source-1"), turns.contextCache(first.id()).orElseThrow().selectedSourceUnitIds());
  }

  @Test
  void checkpointRemainsAvailableForUndoAfterCompletion() {
    AssistantTurn turn = turn("turn-undo");
    turns.create(turn);
    turns.saveCheckpoint(turn.id(), "model", 9, java.util.Map.of("inverse", "remove"));
    turns.complete(turn.id(), AssistantTurn.State.SUCCEEDED, "done", 9L, null);
    assertTrue(turns.latestCheckpoint(turn.id()).isPresent());
    assertEquals(1, turns.checkpoints(turn.id()).size());
  }

  @Test
  void persistsSourceBlueprintAndSliceCursorForAContinuation() throws Exception {
    AssistantTurn parent = turn("turn-blueprint-parent");
    AssistantTurn child = turn("turn-blueprint-child");
    turns.create(parent);
    turns.create(child);
    turns.linkContinuation(parent.id(), child.id());
    var blueprint =
        new ObjectMapper()
            .readTree(
                """
                {"domain":"Commerce","slices":[
                  {"focus":"Order intake","sourceUnitIds":["src-1"]},
                  {"focus":"Fulfilment","sourceUnitIds":["src-2"]}
                ]}
                """);

    turns.saveSourceBlueprint(parent.id(), blueprint, 1);
    var saved = turns.sourceBlueprint(parent.id()).orElseThrow();
    turns.saveSourceBlueprint(child.id(), saved.blueprint(), saved.nextSlice());

    assertEquals(1, saved.nextSlice());
    assertEquals("Commerce", saved.blueprint().path("domain").asText());
    assertEquals(1, turns.sourceBlueprint(child.id()).orElseThrow().nextSlice());
  }

  @Test
  void cancellationCancelsQueuedTurnsInTheContinuationTree() {
    AssistantTurn parent = turn("turn-cancel-parent");
    AssistantTurn child = turn("turn-cancel-child");
    turns.create(parent);
    turns.create(child);
    turns.linkContinuation(parent.id(), child.id());

    turns.requestCancellation(parent.id());

    assertEquals(AssistantTurn.State.CANCELLED, turns.find(parent.id()).orElseThrow().state());
    assertEquals(AssistantTurn.State.CANCELLED, turns.find(child.id()).orElseThrow().state());
    assertTrue(turns.find(parent.id()).orElseThrow().cancellationRequested());
    assertTrue(turns.find(child.id()).orElseThrow().cancellationRequested());
  }

  private AssistantTurn turn(String id) {
    Instant now = Instant.now();
    return new AssistantTurn(
        id,
        "thread",
        "user",
        "project",
        ModelLevel.CIM,
        null,
        null,
        id,
        "message",
        null,
        List.of(),
        AssistantTurn.State.QUEUED,
        now,
        now.plusSeconds(60),
        null,
        null,
        false,
        null,
        0,
        0,
        null,
        null,
        null,
        0,
        0,
        0);
  }
}
