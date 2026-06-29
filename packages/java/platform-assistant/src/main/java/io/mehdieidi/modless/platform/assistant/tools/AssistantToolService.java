package io.mehdieidi.modless.platform.assistant.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.mehdieidi.modless.platform.assistant.domain.AssistantChoice;
import io.mehdieidi.modless.platform.assistant.domain.AssistantValidationSummary;
import io.mehdieidi.modless.platform.assistant.domain.SemanticModelPatch;
import io.mehdieidi.modless.platform.assistant.domain.context.AssistantModelContextTypes.ContextRelationship;
import io.mehdieidi.modless.platform.assistant.patch.AssistantMetamodelSchemaService;
import io.mehdieidi.modless.platform.assistant.patch.AssistantPatchCompiler;
import io.mehdieidi.modless.platform.assistant.provider.AssistantModelProvider;
import io.mehdieidi.modless.platform.assistant.spi.AssistantCatalog;
import io.mehdieidi.modless.platform.assistant.spi.AssistantToolBridge;
import io.mehdieidi.modless.platform.kernel.ModelLevel;
import io.mehdieidi.modless.platform.kernel.PlatformException;
import io.mehdieidi.modless.platform.model.application.ModelService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

/** Whitelisted Spring AI tools for bounded read, preview, validation, and choice workflows. */
public class AssistantToolService implements AssistantToolBridge {

  private final AssistantCatalog catalogs;
  private final AssistantPatchCompiler patchCompiler;
  private final AssistantMetamodelSchemaService schemas;
  private final ModelService models;
  private final ObjectMapper mapper;
  private final ThreadLocal<AssistantToolBridge.ToolSession> session = new ThreadLocal<>();
  private final AtomicInteger toolCallCount = new AtomicInteger();

  public AssistantToolService(
      AssistantCatalog catalogs,
      AssistantPatchCompiler patchCompiler,
      AssistantMetamodelSchemaService schemas,
      ModelService models,
      ObjectMapper mapper) {
    this.catalogs = catalogs;
    this.patchCompiler = patchCompiler;
    this.schemas = schemas;
    this.models = models;
    this.mapper = mapper;
  }

  /** Binds model context used by element and validation tools for one agent loop turn. */
  @Override
  public void bindSession(AssistantToolBridge.ToolSession toolSession) {
    session.set(toolSession);
    toolCallCount.set(0);
  }

  /** Clears the active tool session. */
  @Override
  public void clearSession() {
    session.remove();
  }

  /** Returns and resets the number of tool invocations in the active session. */
  @Override
  public int consumeToolCallCount() {
    return toolCallCount.getAndSet(0);
  }

  /** Searches the backend-owned metamodel and methodology catalogs. */
  @Tool(
      name = "searchCatalogs",
      description = "Search compact backend-owned metamodel and methodology catalogs.")
  public List<AssistantModelProvider.ContextSnippet> searchCatalogs(
      @ToolParam(description = "Search query") String query,
      @ToolParam(description = "Configured modeling level key or display name") String level,
      @ToolParam(description = "Maximum snippets to return") int limit) {
    trackToolCall();
    return catalogs.search(query, level, Math.min(Math.max(limit, 1), 8));
  }

  /** Previews a semantic patch against a compact model snapshot. */
  @Tool(
      name = "previewSemanticPatch",
      description = "Compile and preview a typed semantic patch. Does not commit changes.")
  public PreviewResult previewSemanticPatch(
      @ToolParam(description = "Current model JSON snapshot") String modelJson,
      @ToolParam(description = "SemanticModelPatch JSON") String semanticPatchJson) {
    trackToolCall();
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

  /** Converts a structural validation result into the assistant validation schema. */
  @Tool(
      name = "summarizeValidation",
      description = "Summarize structural validation issues without committing changes.")
  public AssistantValidationSummary summarizeValidation(
      @ToolParam(description = "Configured modeling level key or display name") String level,
      @ToolParam(description = "Validation issues JSON array") String issuesJson) {
    trackToolCall();
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
    trackToolCall();
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

  /** Returns full detail for selected elements and their one-hop neighborhood. */
  @Tool(
      name = "getElementContext",
      description =
          "Return type, attributes, relationships, and writable references for element IDs.")
  public List<ElementContext> getElementContext(
      @ToolParam(description = "Comma-separated stable element IDs") String elementIds) {
    trackToolCall();
    AssistantToolBridge.ToolSession active = requireSession();
    List<String> ids = splitIds(elementIds);
    if (ids.isEmpty()) {
      throw new PlatformException(400, "At least one element id is required.");
    }
    Map<
            String,
            io.mehdieidi.modless.platform.assistant.domain.context.AssistantModelContextTypes
                .ContextElement>
        elements = elementsById(active);
    List<ContextRelationship> relationships = active.context().relationships();
    List<ElementContext> result = new ArrayList<>();
    for (String id : ids) {
      io.mehdieidi.modless.platform.assistant.domain.context.AssistantModelContextTypes
              .ContextElement
          element = elements.get(id);
      if (element == null) {
        continue;
      }
      List<String> neighbors =
          active.context().neighborhoods().getOrDefault(id, List.of()).stream()
              .map(neighborId -> formatElement(elements.get(neighborId)))
              .filter(value -> !value.isBlank())
              .toList();
      List<String> writableReferences =
          schemas
              .typeContract(active.level(), element.type())
              .content()
              .lines()
              .filter(line -> line.contains("->") && !line.contains("containment"))
              .limit(12)
              .toList();
      result.add(
          new ElementContext(
              element.id(),
              element.type(),
              element.name(),
              element.path(),
              neighbors,
              writableReferences));
    }
    if (result.isEmpty()) {
      throw new PlatformException(404, "No matching elements were found in the active model.");
    }
    return result;
  }

  /** Returns the full Ecore contract for one metamodel type. */
  @Tool(
      name = "getTypeContract",
      description = "Return the writable attribute and reference contract for a metamodel type.")
  public AssistantModelProvider.ContextSnippet getTypeContract(
      @ToolParam(description = "Metamodel type name") String typeName,
      @ToolParam(description = "Configured modeling level key or display name") String level) {
    trackToolCall();
    ModelLevel modelLevel = ModelLevel.fromApiName(level);
    return schemas.typeContract(modelLevel, typeName);
  }

  /** Validates a model snapshot without committing changes. */
  @Tool(
      name = "validateSnapshot",
      description =
          "Run structural Ecore validation against a model JSON snapshot without committing "
              + "changes.")
  public AssistantValidationSummary validateSnapshot(
      @ToolParam(description = "Configured modeling level key or display name") String level,
      @ToolParam(description = "Model JSON snapshot") String modelJson) {
    trackToolCall();
    ModelLevel modelLevel = ModelLevel.fromApiName(level);
    try {
      JsonNode model = mapper.readTree(modelJson == null ? "{}" : modelJson);
      return validationSummary(models.validateStructural(modelLevel, model));
    } catch (PlatformException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new PlatformException(400, "Invalid model snapshot.");
    }
  }

  /** Inspects a semantic patch against the bound model snapshot without committing it. */
  @Tool(
      name = "inspectCurrentSemanticPatch",
      description =
          "Compile, preview, and structurally validate a SemanticModelPatch against the active"
              + " bound model snapshot. Does not commit changes and does not require the model"
              + " JSON.")
  public PatchInspectionResult inspectCurrentSemanticPatch(
      @ToolParam(description = "SemanticModelPatch JSON") String semanticPatchJson) {
    trackToolCall();
    AssistantToolBridge.ToolSession active = requireSession();
    try {
      SemanticModelPatch semantic =
          mapper.readValue(
              semanticPatchJson == null ? "{\"operations\":[]}" : semanticPatchJson,
              SemanticModelPatch.class);
      AssistantPatchCompiler.CompiledPatch compiled =
          patchCompiler.compile(active.modelJson(), semantic);
      JsonNode preview = patchCompiler.apply(active.modelJson(), compiled);
      AssistantValidationSummary validation =
          validationSummary(models.validateStructural(active.level(), preview));
      return new PatchInspectionResult(
          semantic.operations().size(),
          compiled.patch().size(),
          compiled.affectedElements(),
          validation,
          validation.structurallyValid() && validation.mandatoryPassed());
    } catch (PlatformException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new PlatformException(400, "Invalid semantic patch inspection input.");
    }
  }

  /** Returns the level language index derived from the active metamodel. */
  @Tool(
      name = "getLanguageIndex",
      description = "Return the runtime Ecore-derived language index for a modeling level.")
  public String getLanguageIndex(
      @ToolParam(description = "Configured modeling level key or display name") String level) {
    trackToolCall();
    return schemas.languageIndex(ModelLevel.fromApiName(level));
  }

  /** Returns metamodel coverage counts for agent self-checks. */
  @Tool(
      name = "getMetamodelCoverage",
      description =
          "Return counts of available creatable types, attributes, containments, and relationship"
              + " references for a modeling level.")
  public AssistantMetamodelSchemaService.MetamodelCoverage getMetamodelCoverage(
      @ToolParam(description = "Configured modeling level key or display name") String level) {
    trackToolCall();
    return schemas.coverage(ModelLevel.fromApiName(level));
  }

  /** Returns creatable metamodel types ranked for the supplied modeling intent. */
  @Tool(
      name = "findCreatableTypes",
      description = "Find Ecore-defined creatable element types relevant to the modeling request.")
  public List<String> findCreatableTypes(
      @ToolParam(description = "Configured modeling level key or display name") String level,
      @ToolParam(description = "Natural language modeling request") String query,
      @ToolParam(description = "Maximum type names to return") int limit) {
    trackToolCall();
    return schemas.relevantTypes(ModelLevel.fromApiName(level), query, false, Math.min(limit, 32));
  }

  /** Finds valid containment owners/features in the bound model for a child metamodel type. */
  @Tool(
      name = "findContainmentOptions",
      description =
          "Find Ecore-valid containment owners and reference names in the active model for a child"
              + " metamodel type.")
  public ContainmentOptions findContainmentOptions(
      @ToolParam(description = "Child metamodel type to place") String childType,
      @ToolParam(description = "Optional owner metamodel type filter") String ownerTypeFilter,
      @ToolParam(description = "Maximum options to return") int limit) {
    trackToolCall();
    AssistantToolBridge.ToolSession active = requireSession();
    String canonicalChild = schemas.canonicalType(active.level(), childType);
    String rawOwnerFilter = ownerTypeFilter == null ? "" : ownerTypeFilter.trim();
    String ownerFilter =
        rawOwnerFilter.isBlank() ? "" : schemas.canonicalType(active.level(), rawOwnerFilter);
    int bounded = Math.min(Math.max(limit <= 0 ? 24 : limit, 1), 80);
    List<ContainmentOption> options = new ArrayList<>();
    schemas
        .rootContainment(active.level(), canonicalChild)
        .ifPresent(
            reference -> {
              String rootType = schemas.rootType(active.level());
              if (ownerFilter.isBlank() || rootType.equals(ownerFilter)) {
                options.add(
                    new ContainmentOption(
                        rootId(active),
                        rootType,
                        rootName(active),
                        reference.name(),
                        true,
                        reference.many(),
                        reference.required()));
              }
            });
    for (var element : active.context().elements()) {
      if (options.size() >= bounded) {
        break;
      }
      String ownerType = element.type();
      if (ownerType == null || ownerType.isBlank()) {
        continue;
      }
      if (schemas.rootType(active.level()).equals(ownerType)
          && element.id().equals(rootId(active))) {
        continue;
      }
      if (!ownerFilter.isBlank() && !ownerFilter.equals(ownerType)) {
        continue;
      }
      for (var reference : schemas.containments(active.level(), ownerType, canonicalChild)) {
        if (options.size() >= bounded) {
          break;
        }
        options.add(
            new ContainmentOption(
                element.id(),
                ownerType,
                element.name(),
                reference.name(),
                false,
                reference.many(),
                reference.required()));
      }
    }
    return new ContainmentOptions(canonicalChild, options.size(), options);
  }

  /** Summarizes the bound model snapshot for agent exploration. */
  @Tool(
      name = "summarizeCurrentModel",
      description = "Return counts by metamodel type and current structural issues for the model.")
  public CurrentModelSummary summarizeCurrentModel() {
    trackToolCall();
    AssistantToolBridge.ToolSession active = requireSession();
    Map<String, Integer> counts = new LinkedHashMap<>();
    active.context().elements().forEach(element -> counts.merge(element.type(), 1, Integer::sum));
    List<String> issues =
        active.context().validationIssues().stream()
            .map(issue -> issue.constraint() + ": " + issue.message())
            .limit(12)
            .toList();
    return new CurrentModelSummary(
        active.level(),
        active.context().elements().size(),
        active.context().relationships().size(),
        counts,
        issues);
  }

  /** Lists model elements with optional type and name filters. */
  @Tool(
      name = "listModelElements",
      description = "Search model elements by type or name with pagination for large models.")
  public ElementPage listModelElements(
      @ToolParam(description = "Optional type filter") String typeFilter,
      @ToolParam(description = "Optional name filter") String nameFilter,
      @ToolParam(description = "Zero-based page index") int page,
      @ToolParam(description = "Page size, maximum 40") int pageSize) {
    trackToolCall();
    AssistantToolBridge.ToolSession active = requireSession();
    String normalizedType = typeFilter == null ? "" : typeFilter.trim().toLowerCase(Locale.ROOT);
    String normalizedName = nameFilter == null ? "" : nameFilter.trim().toLowerCase(Locale.ROOT);
    int safePage = Math.max(0, page);
    int safeSize = Math.min(Math.max(pageSize <= 0 ? 20 : pageSize, 1), 40);
    List<
            io.mehdieidi.modless.platform.assistant.domain.context.AssistantModelContextTypes
                .ContextElement>
        filtered =
            active.context().elements().stream()
                .filter(
                    element ->
                        (normalizedType.isBlank()
                                || element.type().toLowerCase(Locale.ROOT).contains(normalizedType))
                            && (normalizedName.isBlank()
                                || element
                                    .name()
                                    .toLowerCase(Locale.ROOT)
                                    .contains(normalizedName)))
                .toList();
    int from = Math.min(safePage * safeSize, filtered.size());
    int to = Math.min(from + safeSize, filtered.size());
    List<ElementSummary> pageItems =
        filtered.subList(from, to).stream()
            .map(element -> new ElementSummary(element.id(), element.type(), element.name()))
            .toList();
    return new ElementPage(filtered.size(), safePage, safeSize, pageItems);
  }

  private AssistantToolBridge.ToolSession requireSession() {
    AssistantToolBridge.ToolSession active = session.get();
    if (active == null) {
      throw new PlatformException(409, "Assistant tool session is not bound.");
    }
    return active;
  }

  private void trackToolCall() {
    toolCallCount.incrementAndGet();
  }

  private List<String> splitIds(String raw) {
    if (raw == null || raw.isBlank()) {
      return List.of();
    }
    return java.util.Arrays.stream(raw.split(","))
        .map(String::trim)
        .filter(value -> !value.isBlank())
        .distinct()
        .limit(12)
        .toList();
  }

  private String formatElement(
      io.mehdieidi.modless.platform.assistant.domain.context.AssistantModelContextTypes
              .ContextElement
          element) {
    if (element == null) {
      return "";
    }
    return element.id() + ":" + element.type() + ":" + element.name();
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

  private AssistantValidationSummary validationSummary(ModelService.ValidationResult validation) {
    List<AssistantValidationSummary.Issue> issues =
        validation == null
            ? List.of()
            : validation.issues().stream()
                .map(
                    issue ->
                        new AssistantValidationSummary.Issue(
                            issue.severity(),
                            issue.constraint(),
                            issue.elementId(),
                            issue.message()))
                .toList();
    boolean mandatoryPassed =
        issues.stream().noneMatch(issue -> "ERROR".equalsIgnoreCase(issue.severity()));
    long optional =
        issues.stream().filter(issue -> "WARNING".equalsIgnoreCase(issue.severity())).count();
    return new AssistantValidationSummary(
        validation != null && validation.valid(), mandatoryPassed, (int) optional, issues);
  }

  private static Map<
          String,
          io.mehdieidi.modless.platform.assistant.domain.context.AssistantModelContextTypes
              .ContextElement>
      elementsById(AssistantToolBridge.ToolSession active) {
    Map<
            String,
            io.mehdieidi.modless.platform.assistant.domain.context.AssistantModelContextTypes
                .ContextElement>
        result = new LinkedHashMap<>();
    active.context().elements().forEach(element -> result.putIfAbsent(element.id(), element));
    return result;
  }

  private String rootId(AssistantToolBridge.ToolSession active) {
    return active.modelJson() == null ? "" : active.modelJson().path("id").asText("");
  }

  private String rootName(AssistantToolBridge.ToolSession active) {
    if (active.context() != null && active.context().modelName() != null) {
      return active.context().modelName();
    }
    return "";
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

  /**
   * Detailed element context for planner exploration.
   *
   * @param id stable element ID
   * @param type element type
   * @param name element name
   * @param path JSON path
   * @param neighbors one-hop neighborhood summaries
   * @param writableReferences writable non-containment references for the type
   */
  public record ElementContext(
      String id,
      String type,
      String name,
      String path,
      List<String> neighbors,
      List<String> writableReferences) {}

  /**
   * Paginated model element listing.
   *
   * @param totalElements total matches
   * @param page page index
   * @param pageSize page size
   * @param elements page items
   */
  public record ElementPage(
      int totalElements, int page, int pageSize, List<ElementSummary> elements) {}

  /**
   * Compact element listing entry.
   *
   * @param id stable element ID
   * @param type element type
   * @param name element name
   */
  public record ElementSummary(String id, String type, String name) {}

  /**
   * Non-committing patch inspection result.
   *
   * @param semanticOperationCount semantic operation count
   * @param executablePatchOperationCount compiled executable operation count
   * @param affectedElements stable affected IDs
   * @param validation structural validation summary
   * @param acceptable whether the preview is structurally valid
   */
  public record PatchInspectionResult(
      int semanticOperationCount,
      int executablePatchOperationCount,
      List<String> affectedElements,
      AssistantValidationSummary validation,
      boolean acceptable) {}

  /**
   * Valid containment placement options for an element type.
   *
   * @param childType canonical child EClass
   * @param totalOptions number of returned options
   * @param options owner/reference options
   */
  public record ContainmentOptions(
      String childType, int totalOptions, List<ContainmentOption> options) {}

  /**
   * One legal containment owner/reference choice.
   *
   * @param ownerElementId stable owner ID; root model ID for root containment
   * @param ownerType owner EClass
   * @param ownerName owner display name
   * @param referenceName containment feature to use in the semantic patch
   * @param root whether the owner is the model root
   * @param many whether the containment accepts multiple children
   * @param required whether the containment feature is required
   */
  public record ContainmentOption(
      String ownerElementId,
      String ownerType,
      String ownerName,
      String referenceName,
      boolean root,
      boolean many,
      boolean required) {}

  /**
   * Compact current model summary.
   *
   * @param level active modeling level
   * @param elementCount element count
   * @param relationshipCount relationship count
   * @param countsByType element counts grouped by EClass
   * @param validationIssues current validation issue summaries
   */
  public record CurrentModelSummary(
      ModelLevel level,
      int elementCount,
      int relationshipCount,
      Map<String, Integer> countsByType,
      List<String> validationIssues) {}
}
