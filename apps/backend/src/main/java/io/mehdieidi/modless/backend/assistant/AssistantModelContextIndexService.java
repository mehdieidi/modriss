package io.mehdieidi.modless.backend.assistant;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.mehdieidi.modless.platform.core.model.ModelLevel;
import io.mehdieidi.modless.platform.core.model.ModelRecord;
import io.mehdieidi.modless.platform.core.service.ModelService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

/** Produces a compact, stable model context for assistant prompts and proposal previews. */
@Service
public class AssistantModelContextIndexService {

  private final JdbcTemplate jdbc;
  private final ObjectMapper mapper;

  /** Creates an in-memory-only context service, used by narrow unit tests. */
  public AssistantModelContextIndexService() {
    this(null, new ObjectMapper());
  }

  /** Creates the persistent context service. */
  @Autowired
  public AssistantModelContextIndexService(@Nullable JdbcTemplate jdbc, ObjectMapper mapper) {
    this.jdbc = jdbc;
    this.mapper = mapper;
  }

  /**
   * Builds a compact context snapshot for the current model.
   *
   * @param model stored model
   * @param validationResult validation preview
   * @return compact context
   */
  public AssistantModelContext snapshot(
      ModelRecord model, ModelService.ValidationResult validationResult) {
    AssistantModelContext cached = readCached(model);
    if (cached != null) {
      return cached;
    }
    Map<String, ContextElement> elements = new LinkedHashMap<>();
    List<ContextRelationship> relationships = new ArrayList<>();
    JsonNode root = model.modelJson();
    collect(root, "", elements, relationships);
    Map<String, List<String>> neighborhoods = neighborhoods(elements, relationships);
    List<AssistantValidationSummary.Issue> issues =
        validationResult == null
            ? List.of()
            : validationResult.issues().stream()
                .map(
                    issue ->
                        new AssistantValidationSummary.Issue(
                            issue.severity(),
                            issue.constraint(),
                            issue.elementId(),
                            issue.message()))
                .toList();
    AssistantModelContext context =
        new AssistantModelContext(
            model.id(),
            model.projectId(),
            model.level(),
            model.name(),
            model.revision(),
            new ArrayList<>(elements.values()),
            relationships,
            neighborhoods,
            issues);
    writeCached(model, context, issues);
    return context;
  }

  /**
   * Builds a compact context for an in-memory model that has not been persisted yet.
   *
   * @param projectId project scope
   * @param level modeling level
   * @param modelName model display name
   * @param revision transient revision
   * @param modelJson in-memory model
   * @param validationResult validation preview
   * @return compact transient context
   */
  public AssistantModelContext transientSnapshot(
      String projectId,
      ModelLevel level,
      String modelName,
      long revision,
      JsonNode modelJson,
      ModelService.ValidationResult validationResult) {
    return buildContext(null, projectId, level, modelName, revision, modelJson, validationResult);
  }

  /**
   * Creates a compact natural-language summary for the prompt.
   *
   * @param context compact model context
   * @return prompt summary
   */
  public String summarize(AssistantModelContext context) {
    String elementSummary =
        context.elements().stream()
            .limit(60)
            .map(element -> element.id() + ":" + element.type() + ":" + element.name())
            .collect(Collectors.joining(", "));
    String issueSummary =
        context.validationIssues().stream()
            .limit(8)
            .map(issue -> issue.severity() + ":" + issue.constraint() + ":" + issue.message())
            .collect(Collectors.joining(" | "));
    return "Elements: "
        + elementSummary
        + "\nValidation: "
        + issueSummary
        + "\nNeighborhoods: "
        + context.neighborhoods().entrySet().stream()
            .limit(8)
            .map(entry -> entry.getKey() + "->" + entry.getValue())
            .collect(Collectors.joining(" | "));
  }

  private AssistantModelContext buildContext(
      String modelId,
      String projectId,
      ModelLevel level,
      String modelName,
      long revision,
      JsonNode root,
      ModelService.ValidationResult validationResult) {
    Map<String, ContextElement> elements = new LinkedHashMap<>();
    List<ContextRelationship> relationships = new ArrayList<>();
    collect(root, "", elements, relationships);
    Map<String, List<String>> neighborhoods = neighborhoods(elements, relationships);
    List<AssistantValidationSummary.Issue> issues =
        validationResult == null
            ? List.of()
            : validationResult.issues().stream()
                .map(
                    issue ->
                        new AssistantValidationSummary.Issue(
                            issue.severity(),
                            issue.constraint(),
                            issue.elementId(),
                            issue.message()))
                .toList();
    return new AssistantModelContext(
        modelId,
        projectId,
        level,
        modelName,
        revision,
        new ArrayList<>(elements.values()),
        relationships,
        neighborhoods,
        issues);
  }

  private void collect(
      JsonNode node,
      String path,
      Map<String, ContextElement> elements,
      List<ContextRelationship> relationships) {
    if (node == null || node.isNull()) {
      return;
    }
    if (node.isObject()) {
      String id = text(node.get("id"));
      if (!id.isBlank()) {
        elements.putIfAbsent(
            id,
            new ContextElement(
                id,
                text(node.get("eClass")),
                text(firstNonNull(node.get("name"), node.get("label"), node.get("type"))),
                path.isBlank() ? "/" : path));
      }
      String source = text(node.get("source"));
      String target = text(node.get("target"));
      if (!source.isBlank() && !target.isBlank()) {
        relationships.add(
            new ContextRelationship(
                id, source, target, text(node.get("kind")), path.isBlank() ? "/" : path));
      }
      node.fields()
          .forEachRemaining(
              entry ->
                  collect(entry.getValue(), path + "/" + entry.getKey(), elements, relationships));
      return;
    }
    if (node.isArray()) {
      for (int index = 0; index < node.size(); index++) {
        collect(node.get(index), path + "/" + index, elements, relationships);
      }
    }
  }

  private Map<String, List<String>> neighborhoods(
      Map<String, ContextElement> elements, List<ContextRelationship> relationships) {
    Map<String, List<String>> neighborhoods = new LinkedHashMap<>();
    for (ContextRelationship relationship : relationships) {
      if (!relationship.sourceId().isBlank()) {
        neighborhoods
            .computeIfAbsent(relationship.sourceId(), key -> new ArrayList<>())
            .add(relationship.targetId());
      }
      if (!relationship.targetId().isBlank()) {
        neighborhoods
            .computeIfAbsent(relationship.targetId(), key -> new ArrayList<>())
            .add(relationship.sourceId());
      }
    }
    neighborhoods.replaceAll(
        (key, value) -> value.stream().filter(elements::containsKey).distinct().toList());
    return neighborhoods;
  }

  private String text(JsonNode node) {
    return node == null || node.isNull() ? "" : node.asText("");
  }

  private JsonNode firstNonNull(JsonNode... nodes) {
    for (JsonNode node : nodes) {
      if (node != null && !node.isNull() && !node.asText("").isBlank()) {
        return node;
      }
    }
    return null;
  }

  private AssistantModelContext readCached(ModelRecord model) {
    if (jdbc == null) {
      return null;
    }
    return jdbc.query(
        """
        SELECT context_json FROM assistant_model_contexts
        WHERE model_id = ? AND revision = ? AND model_hash = ?
        """,
        rs -> {
          if (!rs.next()) {
            return null;
          }
          try {
            return mapper.readValue(rs.getString("context_json"), AssistantModelContext.class);
          } catch (Exception ex) {
            return null;
          }
        },
        model.id(),
        model.revision(),
        hash(model.modelJson()));
  }

  private void writeCached(
      ModelRecord model,
      AssistantModelContext context,
      List<AssistantValidationSummary.Issue> issues) {
    if (jdbc == null) {
      return;
    }
    try {
      jdbc.update(
          """
          INSERT INTO assistant_model_contexts
            (model_id, project_id, level, revision, context_json, model_hash,
             latest_issues_json, updated_at)
          VALUES (?, ?, ?, ?, ?::jsonb, ?, ?::jsonb, ?)
          ON CONFLICT (model_id, revision) DO UPDATE SET
            context_json = EXCLUDED.context_json,
            model_hash = EXCLUDED.model_hash,
            latest_issues_json = EXCLUDED.latest_issues_json,
            updated_at = EXCLUDED.updated_at
          """,
          model.id(),
          model.projectId(),
          model.level().name(),
          model.revision(),
          mapper.writeValueAsString(context),
          hash(model.modelJson()),
          mapper.writeValueAsString(issues),
          sqlTimestamp(Instant.now()));
    } catch (Exception ex) {
      throw new IllegalStateException("Could not persist assistant model context.", ex);
    }
  }

  private Timestamp sqlTimestamp(Instant instant) {
    return Timestamp.from(instant == null ? Instant.now() : instant);
  }

  private String hash(JsonNode value) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] bytes =
          value == null ? new byte[0] : value.toString().getBytes(StandardCharsets.UTF_8);
      return HexFormat.of().formatHex(digest.digest(bytes));
    } catch (Exception ex) {
      throw new IllegalStateException("Could not hash model context.", ex);
    }
  }

  /**
   * Compact assistant model context.
   *
   * @param modelId model ID
   * @param projectId project ID
   * @param level model level
   * @param modelName model name
   * @param revision revision
   * @param elements stable elements
   * @param relationships stable relationships
   * @param neighborhoods adjacency map
   * @param validationIssues latest validation issues
   */
  public record AssistantModelContext(
      String modelId,
      String projectId,
      ModelLevel level,
      String modelName,
      long revision,
      List<ContextElement> elements,
      List<ContextRelationship> relationships,
      Map<String, List<String>> neighborhoods,
      List<AssistantValidationSummary.Issue> validationIssues) {

    public AssistantModelContext {
      elements = elements == null ? List.of() : List.copyOf(elements);
      relationships = relationships == null ? List.of() : List.copyOf(relationships);
      neighborhoods = neighborhoods == null ? Map.of() : Map.copyOf(neighborhoods);
      validationIssues = validationIssues == null ? List.of() : List.copyOf(validationIssues);
    }
  }

  /**
   * Compact model element index entry.
   *
   * @param id stable element ID
   * @param type element type
   * @param name element name
   * @param path JSON path
   */
  public record ContextElement(String id, String type, String name, String path) {}

  /**
   * Compact relationship index entry.
   *
   * @param id stable relationship ID
   * @param sourceId source ID
   * @param targetId target ID
   * @param kind relationship kind
   * @param path JSON path
   */
  public record ContextRelationship(
      String id, String sourceId, String targetId, String kind, String path) {}
}
