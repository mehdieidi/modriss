package io.mehdieidi.modriss.platform.storage.postgres;

import io.mehdieidi.modriss.platform.artifact.domain.ArtifactIndexRecord;
import io.mehdieidi.modriss.platform.artifact.domain.ArtifactRecord;
import io.mehdieidi.modriss.platform.artifact.storage.ArtifactStorage;
import io.mehdieidi.modriss.platform.identity.domain.AuthSession;
import io.mehdieidi.modriss.platform.identity.domain.UserRecord;
import io.mehdieidi.modriss.platform.kernel.ModelLevel;
import io.mehdieidi.modriss.platform.kernel.PlatformException;
import io.mehdieidi.modriss.platform.model.domain.ModelIndexRecord;
import io.mehdieidi.modriss.platform.model.domain.ModelRecord;
import io.mehdieidi.modriss.platform.model.domain.StagedImportRecord;
import io.mehdieidi.modriss.platform.project.domain.ProjectMember;
import io.mehdieidi.modriss.platform.project.domain.ProjectRecord;
import io.mehdieidi.modriss.platform.storage.api.PlatformStore;
import io.mehdieidi.modriss.platform.transformation.domain.MdeJobIdempotencyRecord;
import io.mehdieidi.modriss.platform.transformation.domain.MdeJobIndexRecord;
import io.mehdieidi.modriss.platform.transformation.domain.MdeJobOperation;
import io.mehdieidi.modriss.platform.transformation.domain.MdeJobRecord;
import io.mehdieidi.modriss.platform.transformation.domain.MdeJobStatus;
import io.mehdieidi.modriss.platform.transformation.synchronization.GeneratedBaseline;
import io.mehdieidi.modriss.platform.transformation.synchronization.SynchronizationSession;
import java.nio.file.Path;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.core.StreamReadConstraints;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * PostgreSQL implementation of the platform persistence port.
 *
 * <p>Logical keys remain at the application boundary for compatibility, but every supported record
 * is persisted in an explicit relational table.
 */
public final class PostgresPlatformStore implements PlatformStore, ArtifactStorage {

  private final JdbcTemplate jdbc;
  private final TransactionTemplate transactions;
  private final ObjectMapper mapper;

  public PostgresPlatformStore(JdbcTemplate jdbc, TransactionTemplate transactions) {
    this.jdbc = jdbc;
    this.transactions = transactions;
    JsonFactory factory =
        JsonFactory.builder()
            .streamReadConstraints(
                StreamReadConstraints.builder().maxStringLength(128 * 1024 * 1024).build())
            .build();
    this.mapper = new ObjectMapper(factory);
  }

  @Override
  public ObjectMapper objectMapper() {
    return mapper;
  }

  @Override
  public <T> T inTransaction(Supplier<T> operation) {
    return transactions.execute(status -> operation.get());
  }

  @Override
  public <T> Optional<T> read(Path path, Class<T> type) {
    try {
      String[] key = key(path);
      return Optional.ofNullable(type.cast(readValue(key, type)));
    } catch (DataAccessException ex) {
      throw persistenceFailure(ex);
    }
  }

  private Object readValue(String[] key, Class<?> type) {
    if (type == UserRecord.class) {
      return maybeOne("SELECT * FROM users WHERE id = ?", this::user, id(key, 1)).orElse(null);
    }
    if (type == AuthSession.class) {
      return maybeOne("SELECT * FROM auth_sessions WHERE token = ?", this::session, id(key, 1))
          .orElse(null);
    }
    if (type == ProjectRecord.class) {
      return maybeOne("SELECT * FROM projects WHERE id = ?", this::project, id(key, 1))
          .orElse(null);
    }
    if (type == ModelRecord.class) {
      return maybeOne("SELECT * FROM models WHERE id = ?", this::model, id(key, key.length - 1))
          .orElse(null);
    }
    if (type == ArtifactRecord.class) {
      return maybeOne(
              "SELECT * FROM artifacts WHERE id = ?", this::artifact, id(key, key.length - 1))
          .orElse(null);
    }
    if (type == MdeJobRecord.class) {
      return maybeOne("SELECT * FROM mde_jobs WHERE id = ?", this::job, id(key, key.length - 1))
          .orElse(null);
    }
    if (type == StagedImportRecord.class) {
      return maybeOne(
              "SELECT * FROM staged_imports WHERE token = ?", this::stagedImport, id(key, 1))
          .orElse(null);
    }
    if (type == ModelIndexRecord.class) {
      return maybeOne(
              "SELECT id, project_id, level FROM models WHERE id = ?",
              (rs, row) ->
                  new ModelIndexRecord(
                      rs.getString("id"),
                      rs.getString("project_id"),
                      ModelLevel.valueOf(rs.getString("level"))),
              id(key, key.length - 1))
          .orElse(null);
    }
    if (type == ArtifactIndexRecord.class) {
      return maybeOne(
              "SELECT id, project_id FROM artifacts WHERE id = ?",
              (rs, row) -> new ArtifactIndexRecord(rs.getString("id"), rs.getString("project_id")),
              id(key, key.length - 1))
          .orElse(null);
    }
    if (type == MdeJobIndexRecord.class) {
      return maybeOne(
              "SELECT id, project_id FROM mde_jobs WHERE id = ?",
              (rs, row) -> new MdeJobIndexRecord(rs.getString("id"), rs.getString("project_id")),
              id(key, key.length - 1))
          .orElse(null);
    }
    if (type == MdeJobIdempotencyRecord.class) {
      return maybeOne(
              "SELECT * FROM mde_job_idempotency WHERE scope_hash = ?",
              this::jobIdempotency,
              id(key, key.length - 1))
          .orElse(null);
    }
    if (type == GeneratedBaseline.class || type == SynchronizationSession.class) {
      return maybeOne(
              "SELECT payload::text FROM model_synchronization_records "
                  + "WHERE project_id = ? AND record_kind = ? AND record_id = ?",
              (rs, row) -> readJson(rs.getString(1), type),
              key[1],
              synchronizationKind(type),
              synchronizationRecordId(key))
          .orElse(null);
    }
    throw unsupported(path(key), type);
  }

  @Override
  public void write(Path path, Object value) {
    try {
      if (value instanceof UserRecord record) {
        writeUser(record);
      } else if (value instanceof AuthSession record) {
        writeSession(record);
      } else if (value instanceof ProjectRecord record) {
        writeProject(record);
      } else if (value instanceof ModelRecord record) {
        writeModel(record);
      } else if (value instanceof ArtifactRecord record) {
        writeArtifact(record);
      } else if (value instanceof MdeJobRecord record) {
        writeJob(record);
      } else if (value instanceof MdeJobIdempotencyRecord record) {
        writeJobIdempotency(record);
      } else if (value instanceof StagedImportRecord record) {
        writeStagedImport(record);
      } else if (value instanceof GeneratedBaseline record) {
        writeSynchronizationRecord(
            record.projectId(),
            "BASELINE",
            record.direction().name() + ":" + record.sourceModelId(),
            record);
      } else if (value instanceof SynchronizationSession record) {
        writeSynchronizationRecord(record.projectId(), "SESSION", record.id(), record);
      } else if (value instanceof ModelIndexRecord
          || value instanceof ArtifactIndexRecord
          || value instanceof MdeJobIndexRecord) {
        // Primary tables already provide globally indexed identifiers.
      } else {
        throw unsupported(path, value == null ? Object.class : value.getClass());
      }
    } catch (DataAccessException ex) {
      throw persistenceFailure(ex);
    }
  }

  @Override
  public void writeBytesAtomically(Path path, byte[] bytes) {
    String[] key = key(path);
    byte[] payload = bytes == null ? new byte[0] : bytes;
    if (isModelXmi(key)) {
      jdbc.update(
          "UPDATE models SET source_xmi = ? WHERE id = ?", payload, id(key, key.length - 1));
      return;
    }
    if (isStagedXmi(key)) {
      jdbc.update(
          """
          INSERT INTO staged_import_payloads(token, payload) VALUES (?, ?)
          ON CONFLICT (token) DO UPDATE SET payload = EXCLUDED.payload
          """,
          id(key, 1),
          payload);
      return;
    }
    if (key[key.length - 1].endsWith(".json")) {
      try {
        write(path, mapper.readValue(payload, recordType(key)));
        return;
      } catch (Exception ex) {
        throw new PlatformException(500, "Could not restore stored data.");
      }
    }
    throw unsupported(path, byte[].class);
  }

  @Override
  public Optional<byte[]> readBytes(Path path) {
    String[] key = key(path);
    if (isModelXmi(key)) {
      return maybeOne(
          "SELECT source_xmi FROM models WHERE id = ? AND source_xmi IS NOT NULL",
          (rs, row) -> rs.getBytes(1),
          id(key, key.length - 1));
    }
    if (isStagedXmi(key)) {
      return maybeOne(
          "SELECT payload FROM staged_import_payloads WHERE token = ?",
          (rs, row) -> rs.getBytes(1),
          id(key, 1));
    }
    if (key[key.length - 1].endsWith(".json")) {
      return read(path, recordType(key)).map(this::jsonBytes);
    }
    return Optional.empty();
  }

  @Override
  public void deleteIfExists(Path path) {
    String[] key = key(path);
    if (isModelXmi(key)) {
      jdbc.update(
          "UPDATE models SET source_xmi = NULL, source_xmi_hash = NULL WHERE id = ?",
          id(key, key.length - 1));
    } else if (isStagedXmi(key)) {
      jdbc.update("DELETE FROM staged_import_payloads WHERE token = ?", id(key, 1));
    } else if (key[0].equals("sessions")) {
      jdbc.update("DELETE FROM auth_sessions WHERE token = ?", id(key, 1));
    } else if (key[0].equals("model-imports")) {
      jdbc.update("DELETE FROM staged_imports WHERE token = ?", id(key, 1));
    } else if (key[0].equals("projects") && key.length >= 5 && key[2].equals("models")) {
      jdbc.update("DELETE FROM models WHERE id = ?", id(key, key.length - 1));
    } else if (isSynchronizationKey(key)) {
      jdbc.update(
          "DELETE FROM model_synchronization_records WHERE project_id = ? "
              + "AND record_kind = ? AND record_id = ?",
          key[1],
          synchronizationKind(key),
          synchronizationRecordId(key));
    } else if (key[0].equals("indexes")) {
      // Index records are database views of primary tables.
    } else {
      throw unsupported(path, Object.class);
    }
  }

  @Override
  public <T> List<T> list(Path directory, Class<T> type) {
    String[] key = key(directory);
    try {
      List<?> values;
      if (type == UserRecord.class) {
        values = jdbc.query("SELECT * FROM users ORDER BY created_at", this::user);
      } else if (type == ProjectRecord.class) {
        values = jdbc.query("SELECT * FROM projects ORDER BY updated_at DESC", this::project);
      } else if (type == ModelRecord.class) {
        values =
            jdbc.query(
                """
                SELECT * FROM models WHERE project_id = ? AND level = ?
                ORDER BY updated_at DESC
                """,
                this::model,
                key[1],
                key[3].toUpperCase());
      } else if (type == ArtifactRecord.class) {
        values =
            jdbc.query(
                """
                SELECT * FROM artifacts WHERE project_id = ? ORDER BY updated_at DESC
                """,
                this::artifact,
                key[1]);
      } else if (type == MdeJobRecord.class) {
        values =
            jdbc.query(
                """
                SELECT * FROM mde_jobs WHERE project_id = ? ORDER BY created_at DESC
                """,
                this::job,
                key[1]);
      } else if (type == StagedImportRecord.class) {
        values = jdbc.query("SELECT * FROM staged_imports ORDER BY created_at", this::stagedImport);
      } else if (type == GeneratedBaseline.class || type == SynchronizationSession.class) {
        if (type == GeneratedBaseline.class && key.length >= 5) {
          values =
              jdbc.query(
                  "SELECT payload::text FROM model_synchronization_records "
                      + "WHERE project_id = ? AND record_kind = ? "
                      + "AND split_part(record_id, ':', 1) = ? "
                      + "ORDER BY updated_at DESC",
                  (rs, row) -> readJson(rs.getString(1), type),
                  key[1],
                  synchronizationKind(type),
                  key[4].toUpperCase());
        } else {
          values =
              jdbc.query(
                  "SELECT payload::text FROM model_synchronization_records "
                      + "WHERE project_id = ? AND record_kind = ? ORDER BY updated_at DESC",
                  (rs, row) -> readJson(rs.getString(1), type),
                  key[1],
                  synchronizationKind(type));
        }
      } else {
        throw unsupported(directory, type);
      }
      return values.stream().map(type::cast).toList();
    } catch (DataAccessException ex) {
      throw persistenceFailure(ex);
    }
  }

  @Override
  public List<ArtifactRecord> listSummaries(String projectId) {
    try {
      return jdbc.query(
          """
          SELECT a.*, COALESCE(
            array_agg(f.path ORDER BY f.path) FILTER (WHERE f.path IS NOT NULL),
            ARRAY[]::text[]) AS file_paths
          FROM artifacts a
          LEFT JOIN artifact_files f ON f.artifact_id = a.id
          WHERE a.project_id = ?
          GROUP BY a.id
          ORDER BY a.updated_at DESC
          """,
          this::artifactSummary,
          projectId);
    } catch (DataAccessException ex) {
      throw persistenceFailure(ex);
    }
  }

  @Override
  public Optional<String> readFile(String artifactId, String path) {
    try {
      return maybeOne(
          "SELECT content FROM artifact_files WHERE artifact_id = ? AND path = ?",
          (rs, row) -> rs.getString(1),
          artifactId,
          path);
    } catch (DataAccessException ex) {
      throw persistenceFailure(ex);
    }
  }

  @Override
  public List<ArtifactStorage.ArtifactFile> listFiles(String artifactId) {
    try {
      return jdbc.query(
          "SELECT path, content FROM artifact_files WHERE artifact_id = ? ORDER BY path",
          (rs, row) -> new ArtifactStorage.ArtifactFile(rs.getString(1), rs.getString(2)),
          artifactId);
    } catch (DataAccessException ex) {
      throw persistenceFailure(ex);
    }
  }

  @Override
  public void deleteTree(Path directory) {
    String[] key = key(directory);
    if (key.length == 2 && key[0].equals("projects")) {
      List<String> stagedTokens =
          jdbc.queryForList(
              "SELECT token FROM staged_imports WHERE project_id = ?", String.class, key[1]);
      for (String token : stagedTokens) {
        jdbc.update("DELETE FROM staged_import_payloads WHERE token = ?", token);
      }
      jdbc.update("DELETE FROM staged_imports WHERE project_id = ?", key[1]);
      jdbc.update("DELETE FROM projects WHERE id = ?", key[1]);
      return;
    }
    throw unsupported(directory, Object.class);
  }

  private void writeUser(UserRecord r) {
    jdbc.update(
        """
        INSERT INTO users VALUES (?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT (id) DO UPDATE SET email=EXCLUDED.email,
          display_name=EXCLUDED.display_name, password_hash=EXCLUDED.password_hash,
          salt=EXCLUDED.salt, updated_at=EXCLUDED.updated_at
        """,
        r.id(),
        r.email(),
        r.displayName(),
        r.passwordHash(),
        r.salt(),
        timestamp(r.createdAt()),
        timestamp(r.updatedAt()));
  }

  private void writeSession(AuthSession r) {
    jdbc.update(
        """
        INSERT INTO auth_sessions VALUES (?, ?, ?, ?)
        ON CONFLICT (token) DO UPDATE SET user_id=EXCLUDED.user_id,
          created_at=EXCLUDED.created_at, expires_at=EXCLUDED.expires_at
        """,
        r.token(),
        r.userId(),
        timestamp(r.createdAt()),
        timestamp(r.expiresAt()));
  }

  private void writeProject(ProjectRecord r) {
    transactions.executeWithoutResult(
        status -> {
          jdbc.update(
              """
              INSERT INTO projects VALUES (?, ?, ?, ?, ?, ?)
              ON CONFLICT (id) DO UPDATE SET name=EXCLUDED.name,
                description=EXCLUDED.description, owner_user_id=EXCLUDED.owner_user_id,
                updated_at=EXCLUDED.updated_at
              """,
              r.id(),
              r.name(),
              r.description(),
              r.ownerUserId(),
              timestamp(r.createdAt()),
              timestamp(r.updatedAt()));
          jdbc.update("DELETE FROM project_members WHERE project_id = ?", r.id());
          for (ProjectMember member : safe(r.members())) {
            jdbc.update(
                "INSERT INTO project_members VALUES (?, ?, ?, ?, ?, ?)",
                r.id(),
                member.userId(),
                member.email(),
                member.displayName(),
                member.role(),
                timestamp(member.addedAt()));
          }
          jdbc.update("DELETE FROM project_active_models WHERE project_id = ?", r.id());
          safe(r.activeModelIds())
              .forEach(
                  (name, modelId) ->
                      jdbc.update(
                          "INSERT INTO project_active_models VALUES (?, ?, ?)",
                          r.id(),
                          name,
                          modelId));
        });
  }

  private void writeModel(ModelRecord r) {
    jdbc.update(
        """
        INSERT INTO models(id, project_id, level, name, model_json, metamodel_version,
          metamodel_hash, revision, source_xmi_hash, migration_state, created_at, updated_at)
        VALUES (?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT (id) DO UPDATE SET project_id=EXCLUDED.project_id, level=EXCLUDED.level,
          name=EXCLUDED.name, model_json=EXCLUDED.model_json,
          metamodel_version=EXCLUDED.metamodel_version, metamodel_hash=EXCLUDED.metamodel_hash,
          revision=EXCLUDED.revision, source_xmi_hash=EXCLUDED.source_xmi_hash,
          migration_state=EXCLUDED.migration_state, updated_at=EXCLUDED.updated_at
        """,
        r.id(),
        r.projectId(),
        r.level().name(),
        r.name(),
        json(r.modelJson()),
        r.metamodelVersion(),
        r.metamodelHash(),
        r.revision(),
        r.sourceXmiHash(),
        r.migrationState(),
        timestamp(r.createdAt()),
        timestamp(r.updatedAt()));
  }

  private void writeSynchronizationRecord(
      String projectId, String kind, String recordId, Object value) {
    jdbc.update(
        """
        INSERT INTO model_synchronization_records(project_id, record_kind, record_id, payload)
        VALUES (?, ?, ?, ?::jsonb)
        ON CONFLICT (project_id, record_kind, record_id) DO UPDATE SET
          payload = EXCLUDED.payload,
          updated_at = CURRENT_TIMESTAMP
        """,
        projectId,
        kind,
        recordId,
        json(value));
  }

  private void writeStagedImport(StagedImportRecord r) {
    jdbc.update(
        """
        INSERT INTO staged_imports VALUES (?, NULLIF(?, ''), NULLIF(?, ''), ?, ?, ?, ?)
        ON CONFLICT (token) DO UPDATE SET user_id=EXCLUDED.user_id,
          project_id=EXCLUDED.project_id, level=EXCLUDED.level,
          size_bytes=EXCLUDED.size_bytes, created_at=EXCLUDED.created_at,
          expires_at=EXCLUDED.expires_at
        """,
        r.token(),
        r.userId(),
        r.projectId(),
        r.level().name(),
        r.sizeBytes(),
        timestamp(r.createdAt()),
        timestamp(r.expiresAt()));
  }

  private void writeArtifact(ArtifactRecord r) {
    transactions.executeWithoutResult(
        status -> {
          jdbc.update(
              """
INSERT INTO artifacts(id, project_id, name, model_json, created_at, updated_at)
VALUES (?, ?, ?, ?::jsonb, ?, ?)
ON CONFLICT (id) DO UPDATE SET project_id=EXCLUDED.project_id,
  name=EXCLUDED.name, model_json=EXCLUDED.model_json,
  updated_at=EXCLUDED.updated_at
""",
              r.id(),
              r.projectId(),
              r.name(),
              json(r.modelJson()),
              timestamp(r.createdAt()),
              timestamp(r.updatedAt()));
          jdbc.update("DELETE FROM artifact_files WHERE artifact_id = ?", r.id());
          safe(r.files())
              .forEach(
                  (path, content) ->
                      jdbc.update(
                          "INSERT INTO artifact_files VALUES (?, ?, ?)", r.id(), path, content));
        });
  }

  private void writeJob(MdeJobRecord r) {
    transactions.executeWithoutResult(
        status -> {
          jdbc.update(
              """
              INSERT INTO mde_jobs(id, project_id, user_id, source_model_id, source_level,
                source_revision, source_model_hash, operation, status, progress_percent,
                result_model_id, result_artifact_id, created_at, started_at, finished_at,
                validation_result, timings, idempotency_key, fingerprint)
              VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?, ?)
              ON CONFLICT (id) DO UPDATE SET status=EXCLUDED.status,
                progress_percent=EXCLUDED.progress_percent,
                result_model_id=EXCLUDED.result_model_id,
                result_artifact_id=EXCLUDED.result_artifact_id,
                validation_result=EXCLUDED.validation_result,
                timings=EXCLUDED.timings,
                started_at=EXCLUDED.started_at, finished_at=EXCLUDED.finished_at
              """,
              r.id(),
              r.projectId(),
              r.userId(),
              r.sourceModelId(),
              r.sourceLevel().name(),
              r.sourceRevision(),
              r.sourceModelHash(),
              r.operation().name(),
              r.status().name(),
              r.progressPercent(),
              r.resultModelId(),
              r.resultArtifactId(),
              timestamp(r.createdAt()),
              timestamp(r.startedAt()),
              timestamp(r.finishedAt()),
              json(r.validationResult()),
              json(r.timings()),
              r.idempotencyKey(),
              r.fingerprint());
          jdbc.update("DELETE FROM mde_job_diagnostics WHERE job_id = ?", r.id());
          for (int i = 0; i < r.diagnostics().size(); i++) {
            jdbc.update(
                "INSERT INTO mde_job_diagnostics VALUES (?, ?, ?)",
                r.id(),
                i,
                r.diagnostics().get(i));
          }
        });
  }

  private void writeJobIdempotency(MdeJobIdempotencyRecord r) {
    jdbc.update(
        """
        INSERT INTO mde_job_idempotency VALUES (?, ?, ?, ?)
        ON CONFLICT (scope_hash) DO UPDATE SET job_id=EXCLUDED.job_id,
          project_id=EXCLUDED.project_id, fingerprint=EXCLUDED.fingerprint
        """,
        r.scopeHash(),
        r.jobId(),
        r.projectId(),
        r.fingerprint());
  }

  private MdeJobIdempotencyRecord jobIdempotency(ResultSet rs, int row) throws SQLException {
    return new MdeJobIdempotencyRecord(
        rs.getString("scope_hash"),
        rs.getString("job_id"),
        rs.getString("project_id"),
        rs.getString("fingerprint"));
  }

  private UserRecord user(ResultSet rs, int row) throws SQLException {
    return new UserRecord(
        rs.getString("id"),
        rs.getString("email"),
        rs.getString("display_name"),
        rs.getString("password_hash"),
        rs.getString("salt"),
        instant(rs, "created_at"),
        instant(rs, "updated_at"));
  }

  private AuthSession session(ResultSet rs, int row) throws SQLException {
    return new AuthSession(
        rs.getString("token"),
        rs.getString("user_id"),
        instant(rs, "created_at"),
        instant(rs, "expires_at"));
  }

  private ProjectRecord project(ResultSet rs, int row) throws SQLException {
    String id = rs.getString("id");
    List<ProjectMember> members =
        jdbc.query(
            """
            SELECT * FROM project_members WHERE project_id = ? ORDER BY added_at
            """,
            (member, ignored) ->
                new ProjectMember(
                    member.getString("user_id"),
                    member.getString("email_snapshot"),
                    member.getString("display_name_snapshot"),
                    member.getString("role"),
                    instant(member, "added_at")),
            id);
    Map<String, String> active = new LinkedHashMap<>();
    jdbc.query(
        "SELECT model_key, model_id FROM project_active_models WHERE project_id = ?",
        (RowCallbackHandler) values -> active.put(values.getString(1), values.getString(2)),
        id);
    return new ProjectRecord(
        id,
        rs.getString("name"),
        rs.getString("description"),
        rs.getString("owner_user_id"),
        active,
        members,
        instant(rs, "created_at"),
        instant(rs, "updated_at"));
  }

  private ModelRecord model(ResultSet rs, int row) throws SQLException {
    return new ModelRecord(
        rs.getString("id"),
        rs.getString("project_id"),
        ModelLevel.valueOf(rs.getString("level")),
        rs.getString("name"),
        tree(rs.getString("model_json")),
        rs.getString("metamodel_version"),
        rs.getString("metamodel_hash"),
        rs.getLong("revision"),
        rs.getString("source_xmi_hash"),
        rs.getString("migration_state"),
        instant(rs, "created_at"),
        instant(rs, "updated_at"));
  }

  private ArtifactRecord artifact(ResultSet rs, int row) throws SQLException {
    String id = rs.getString("id");
    Map<String, String> files = new LinkedHashMap<>();
    jdbc.query(
        "SELECT path, content FROM artifact_files WHERE artifact_id = ? ORDER BY path",
        (RowCallbackHandler) values -> files.put(values.getString(1), values.getString(2)),
        id);
    return new ArtifactRecord(
        id,
        rs.getString("project_id"),
        rs.getString("name"),
        tree(rs.getString("model_json")),
        files,
        instant(rs, "created_at"),
        instant(rs, "updated_at"));
  }

  private ArtifactRecord artifactSummary(ResultSet rs, int row) throws SQLException {
    Map<String, String> paths = new LinkedHashMap<>();
    java.sql.Array filePaths = rs.getArray("file_paths");
    if (filePaths != null) {
      Object values = filePaths.getArray();
      if (values instanceof Object[] pathsArray) {
        for (Object value : pathsArray) {
          if (value != null) {
            paths.put(String.valueOf(value), "");
          }
        }
      }
    }
    return new ArtifactRecord(
        rs.getString("id"),
        rs.getString("project_id"),
        rs.getString("name"),
        tree(rs.getString("model_json")),
        paths,
        instant(rs, "created_at"),
        instant(rs, "updated_at"));
  }

  private MdeJobRecord job(ResultSet rs, int row) throws SQLException {
    List<String> diagnostics =
        jdbc.query(
            """
            SELECT diagnostic FROM mde_job_diagnostics WHERE job_id = ? ORDER BY position
            """,
            (value, ignored) -> value.getString(1),
            rs.getString("id"));
    return new MdeJobRecord(
        rs.getString("id"),
        rs.getString("project_id"),
        rs.getString("user_id"),
        rs.getString("source_model_id"),
        ModelLevel.valueOf(rs.getString("source_level")),
        rs.getLong("source_revision"),
        rs.getString("source_model_hash"),
        MdeJobOperation.valueOf(rs.getString("operation")),
        MdeJobStatus.valueOf(rs.getString("status")),
        rs.getInt("progress_percent"),
        rs.getString("result_model_id"),
        rs.getString("result_artifact_id"),
        diagnostics,
        nullableTree(rs.getString("validation_result")),
        longMap(rs.getString("timings")),
        rs.getString("idempotency_key"),
        rs.getString("fingerprint"),
        instant(rs, "created_at"),
        instant(rs, "started_at"),
        instant(rs, "finished_at"));
  }

  private StagedImportRecord stagedImport(ResultSet rs, int row) throws SQLException {
    String token = rs.getString("token");
    return new StagedImportRecord(
        token,
        rs.getString("user_id"),
        rs.getString("project_id"),
        ModelLevel.valueOf(rs.getString("level")),
        "model-imports/" + token + ".xmi",
        rs.getLong("size_bytes"),
        instant(rs, "created_at"),
        instant(rs, "expires_at"));
  }

  private <T> T requireOne(
      String sql, org.springframework.jdbc.core.RowMapper<T> rowMapper, Object... args) {
    return maybeOne(sql, rowMapper, args)
        .orElseThrow(() -> new PlatformException(404, "Stored record was not found."));
  }

  private <T> Optional<T> maybeOne(
      String sql, org.springframework.jdbc.core.RowMapper<T> rowMapper, Object... args) {
    List<T> values = jdbc.query(sql, rowMapper, args);
    return values.stream().findFirst();
  }

  private String[] key(Path path) {
    String value = path.normalize().toString().replace('\\', '/');
    if (value.startsWith("../") || value.equals("..") || value.startsWith("/")) {
      throw new PlatformException(400, "Invalid repository path.");
    }
    return value.split("/");
  }

  private String id(String[] key, int index) {
    return key[index].replaceFirst("\\.(json|xmi)$", "");
  }

  private boolean isModelXmi(String[] key) {
    return key.length >= 5
        && key[0].equals("projects")
        && key[2].equals("models")
        && key[key.length - 1].endsWith(".xmi");
  }

  private boolean isStagedXmi(String[] key) {
    return key.length == 2 && key[0].equals("model-imports") && key[1].endsWith(".xmi");
  }

  private Class<?> recordType(String[] key) {
    if (key[0].equals("projects") && key.length == 3) {
      return ProjectRecord.class;
    }
    if (key[0].equals("projects") && key.length >= 5 && key[2].equals("models")) {
      return ModelRecord.class;
    }
    if (key[0].equals("projects") && key.length >= 4 && key[2].equals("artifacts")) {
      return ArtifactRecord.class;
    }
    if (key[0].equals("projects") && key.length >= 4 && key[2].equals("mde-jobs")) {
      return MdeJobRecord.class;
    }
    if (key[0].equals("users")) {
      return UserRecord.class;
    }
    if (key[0].equals("sessions")) {
      return AuthSession.class;
    }
    if (key[0].equals("model-imports")) {
      return StagedImportRecord.class;
    }
    if (isSynchronizationKey(key)) {
      return "baselines".equals(key[3]) ? GeneratedBaseline.class : SynchronizationSession.class;
    }
    if (key.length > 1 && key[0].equals("indexes") && key[1].equals("mde-job-idempotency")) {
      return MdeJobIdempotencyRecord.class;
    }
    throw unsupported(path(key), Object.class);
  }

  private String json(Object value) {
    try {
      return mapper.writeValueAsString(value);
    } catch (Exception ex) {
      throw new PlatformException(500, "Could not serialize stored data.");
    }
  }

  private Object readJson(String value, Class<?> type) {
    try {
      return mapper.readValue(value, type);
    } catch (Exception ex) {
      throw new PlatformException(500, "Could not deserialize synchronization data.", ex);
    }
  }

  private boolean isSynchronizationKey(String[] key) {
    return key.length >= 5
        && "projects".equals(key[0])
        && "synchronization".equals(key[2])
        && ("baselines".equals(key[3]) || "sessions".equals(key[3]));
  }

  private String synchronizationKind(String[] key) {
    return "baselines".equals(key[3]) ? "BASELINE" : "SESSION";
  }

  private String synchronizationKind(Class<?> type) {
    return type == GeneratedBaseline.class ? "BASELINE" : "SESSION";
  }

  private String synchronizationRecordId(String[] key) {
    String recordId = id(key, key.length - 1);
    if ("baselines".equals(key[3])) {
      if (key.length < 6) {
        throw new PlatformException(400, "Baseline path must include a transformation direction.");
      }
      return key[4].toUpperCase() + ":" + recordId;
    }
    return recordId;
  }

  private byte[] jsonBytes(Object value) {
    try {
      return mapper.writeValueAsBytes(value);
    } catch (Exception ex) {
      throw new PlatformException(500, "Could not serialize stored data.");
    }
  }

  private JsonNode tree(String value) {
    try {
      return mapper.readTree(value);
    } catch (Exception ex) {
      throw new PlatformException(500, "Could not decode stored data.");
    }
  }

  private JsonNode nullableTree(String value) {
    if (value == null || value.isBlank() || "null".equals(value)) {
      return null;
    }
    return tree(value);
  }

  private Map<String, Long> longMap(String value) {
    if (value == null || value.isBlank() || "null".equals(value)) {
      return Map.of();
    }
    JsonNode node = tree(value);
    Map<String, Long> values = new LinkedHashMap<>();
    node.properties().forEach(entry -> values.put(entry.getKey(), entry.getValue().asLong()));
    return values;
  }

  private Instant instant(ResultSet rs, String column) throws SQLException {
    Timestamp value = rs.getTimestamp(column);
    return value == null ? null : value.toInstant();
  }

  private Timestamp timestamp(Instant value) {
    return value == null ? null : Timestamp.from(value);
  }

  private <T> List<T> safe(List<T> value) {
    return value == null ? List.of() : value;
  }

  private <K, V> Map<K, V> safe(Map<K, V> value) {
    return value == null ? Map.of() : value;
  }

  private Path path(String[] key) {
    return Path.of(String.join("/", key));
  }

  private PlatformException unsupported(Path path, Class<?> type) {
    return new PlatformException(
        500, "Unsupported PostgreSQL storage key: " + path + " (" + type.getSimpleName() + ")");
  }

  private PlatformException persistenceFailure(Exception ex) {
    return new PlatformException(500, "Could not access persisted data.", ex);
  }
}
