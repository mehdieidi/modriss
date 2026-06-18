package io.mehdieidi.modless.backend.assistant;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.mehdieidi.modless.platform.core.PlatformException;
import io.mehdieidi.modless.platform.core.model.ModelLevel;
import io.mehdieidi.modless.platform.core.service.ModelService;
import java.util.List;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

/** Whitelisted Spring AI tools for bounded read, preview, validation, and choice workflows. */
@Service
public class AssistantToolService {

  private final AssistantCatalogService catalogs;
  private final AssistantPatchCompiler patchCompiler;
  private final ObjectMapper mapper;

  public AssistantToolService(
      AssistantCatalogService catalogs, AssistantPatchCompiler patchCompiler, ObjectMapper mapper) {
    this.catalogs = catalogs;
    this.patchCompiler = patchCompiler;
    this.mapper = mapper;
  }

  /** Searches the backend-owned metamodel and EVL catalogs. */
  @Tool(
      name = "searchCatalogs",
      description = "Search compact backend-owned metamodel and EVL catalogs.")
  public List<AssistantModelProvider.ContextSnippet> searchCatalogs(
      @ToolParam(description = "Search query") String query,
      @ToolParam(description = "Configured modeling level key or display name") String level,
      @ToolParam(description = "Maximum snippets to return") int limit) {
    return catalogs.search(query, level, Math.min(Math.max(limit, 1), 8));
  }

  /** Previews a semantic patch against a compact model snapshot. */
  @Tool(
      name = "previewSemanticPatch",
      description = "Compile and preview a typed semantic patch. Does not commit changes.")
  public PreviewResult previewSemanticPatch(
      @ToolParam(description = "Current model JSON snapshot") String modelJson,
      @ToolParam(description = "SemanticModelPatch JSON") String semanticPatchJson) {
    try {
      JsonNode model = mapper.readTree(modelJson == null ? "{}" : modelJson);
      SemanticModelPatch semantic =
          mapper.readValue(
              semanticPatchJson == null ? "{\"operations\":[]}" : semanticPatchJson,
              SemanticModelPatch.class);
      AssistantPatchCompiler.CompiledPatch compiled = patchCompiler.compile(model, semantic);
      JsonNode preview = patchCompiler.apply(model, compiled);
      return new PreviewResult(
          compiled.affectedElements(), compiled.patch(), compiled.inversePatch(), preview);
    } catch (PlatformException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new PlatformException(400, "Invalid semantic patch preview input.");
    }
  }

  /** Converts a validation result into the assistant validation schema. */
  @Tool(
      name = "summarizeValidation",
      description = "Summarize structural and EVL validation issues without committing changes.")
  public AssistantValidationSummary summarizeValidation(
      @ToolParam(description = "Configured modeling level key or display name") String level,
      @ToolParam(description = "Validation issues JSON array") String issuesJson) {
    List<AssistantValidationSummary.Issue> issues = parseIssues(issuesJson);
    boolean mandatoryPassed =
        issues.stream().noneMatch(issue -> "ERROR".equalsIgnoreCase(issue.severity()));
    long optional =
        issues.stream().filter(issue -> "WARNING".equalsIgnoreCase(issue.severity())).count();
    ModelLevel.fromApiName(level);
    return new AssistantValidationSummary(mandatoryPassed, mandatoryPassed, (int) optional, issues);
  }

  /** Builds a bounded choice request payload. */
  @Tool(
      name = "requestUserChoice",
      description = "Create a bounded user-choice request for ambiguous modeling intent.")
  public AssistantChoice requestUserChoice(
      @ToolParam(description = "Choice id") String id,
      @ToolParam(description = "Prompt shown to user") String prompt,
      @ToolParam(description = "Comma-separated option ids") String optionIds) {
    List<AssistantChoice.Option> options =
        java.util.Arrays.stream((optionIds == null ? "" : optionIds).split(","))
            .map(String::trim)
            .filter(value -> !value.isBlank())
            .limit(4)
            .map(value -> new AssistantChoice.Option(value, value, "User selected " + value))
            .toList();
    if (options.isEmpty()) {
      throw new PlatformException(400, "At least one choice option is required.");
    }
    return new AssistantChoice(
        id == null || id.isBlank() ? "assistant-choice" : id,
        prompt == null || prompt.isBlank() ? "Choose how to proceed." : prompt,
        options);
  }

  private List<AssistantValidationSummary.Issue> parseIssues(String issuesJson) {
    if (issuesJson == null || issuesJson.isBlank()) {
      return List.of();
    }
    try {
      return mapper.readValue(issuesJson, new com.fasterxml.jackson.core.type.TypeReference<>() {});
    } catch (Exception ex) {
      throw new PlatformException(400, "Invalid validation issue payload.");
    }
  }

  /**
   * Patch preview result.
   *
   * @param affectedElements stable affected IDs
   * @param patch executable backend patch
   * @param inversePatch inverse patch for undo
   * @param preview patched model preview
   */
  public record PreviewResult(
      List<String> affectedElements,
      List<ModelService.ModelPatchOperation> patch,
      List<ModelService.ModelPatchOperation> inversePatch,
      JsonNode preview) {}
}
