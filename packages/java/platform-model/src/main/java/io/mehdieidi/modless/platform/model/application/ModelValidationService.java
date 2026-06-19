package io.mehdieidi.modless.platform.model.application;

import com.fasterxml.jackson.databind.JsonNode;
import io.mehdieidi.modless.mde.validation.EpsilonEvlValidator;
import io.mehdieidi.modless.mde.validation.EvlConstraintKind;
import io.mehdieidi.modless.mde.validation.EvlConstraintViolation;
import io.mehdieidi.modless.mde.validation.EvlDiagnostic;
import io.mehdieidi.modless.mde.validation.EvlModelConfiguration;
import io.mehdieidi.modless.mde.validation.EvlValidationException;
import io.mehdieidi.modless.mde.validation.EvlValidationReport;
import io.mehdieidi.modless.mde.validation.EvlValidationRequest;
import io.mehdieidi.modless.mde.validation.ResourceEvlModelConfiguration;
import io.mehdieidi.modless.mde.validation.ValidationSeverity;
import io.mehdieidi.modless.platform.kernel.ModelLevel;
import io.mehdieidi.modless.platform.kernel.PlatformException;
import io.mehdieidi.modless.platform.model.domain.ModelRecord;
import io.mehdieidi.modless.platform.modeling.config.ModelingConfigService;
import io.mehdieidi.modless.platform.modeling.metamodel.MetamodelDescriptor;
import io.mehdieidi.modless.platform.modeling.metamodel.MetamodelResolver;
import io.mehdieidi.modless.platform.modeling.runtime.MdeRuntimeOptions;
import io.mehdieidi.modless.platform.modeling.runtime.MdeRuntimePaths;
import io.mehdieidi.modless.platform.modeling.xmi.XmiModelImportService;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.resource.Resource;

/**
 * EVL and JSON validation for platform models, including validation caching and per-thread timing
 * capture.
 */
final class ModelValidationService {

  /** Per-worker timing data captured by the last stored validation on the current thread. */
  private static final ThreadLocal<Map<String, Long>> LAST_VALIDATION_TIMINGS =
      ThreadLocal.withInitial(LinkedHashMap::new);

  /** Modeling configuration service used for JSON-level validation. */
  private final ModelingConfigService modelingConfig;

  /** Runtime path resolver for validation assets. */
  private final MdeRuntimePaths mdePaths;

  /** Metamodel resolver used for validation model configuration. */
  private final MetamodelResolver metamodelResolver;

  /** XMI bridge between JSON and EMF resources. */
  private final XmiModelImportService xmiImportService;

  /** EVL validator used for semantic validation. */
  private final EpsilonEvlValidator evlValidator;

  /** Hydrates semantic references before JSON-to-XMI export for validation. */
  private final UnaryOperator<JsonNode> semanticReferenceHydrator;

  /** Small LRU validation cache keyed by model revision and XMI hash. */
  private final Map<String, ModelService.ValidationResult> validationCache =
      Collections.synchronizedMap(
          new LinkedHashMap<>(128, 0.75f, true) {
            /**
             * Evicts the least-recently used validation result once the cache reaches its fixed
             * service-local capacity.
             *
             * @param eldest least-recently accessed cache entry
             * @return {@code true} when the eldest entry should be removed
             */
            @Override
            protected boolean removeEldestEntry(
                Map.Entry<String, ModelService.ValidationResult> eldest) {
              return size() > 128;
            }
          });

  /**
   * Creates a model validation service.
   *
   * @param modelingConfig modeling configuration service
   * @param runtimeOptions runtime limits and asset roots
   * @param mdePaths runtime path resolver
   * @param metamodelResolver metamodel resolver
   * @param xmiImportService XMI import/export bridge
   * @param semanticReferenceHydrator hydrates graph endpoints into semantic JSON
   */
  ModelValidationService(
      ModelingConfigService modelingConfig,
      MdeRuntimeOptions runtimeOptions,
      MdeRuntimePaths mdePaths,
      MetamodelResolver metamodelResolver,
      XmiModelImportService xmiImportService,
      UnaryOperator<JsonNode> semanticReferenceHydrator) {
    this.modelingConfig = modelingConfig;
    this.mdePaths = mdePaths;
    this.metamodelResolver = metamodelResolver;
    this.xmiImportService = xmiImportService;
    this.semanticReferenceHydrator = semanticReferenceHydrator;
    this.evlValidator =
        new EpsilonEvlValidator(
            runtimeOptions.executionTimeout(), runtimeOptions.maxCapturedOutputBytes());
  }

  /**
   * Returns and clears timing data captured by the last stored validation on the current thread.
   *
   * @return immutable timing map
   */
  static Map<String, Long> consumeLastValidationTimings() {
    Map<String, Long> timings = Map.copyOf(LAST_VALIDATION_TIMINGS.get());
    LAST_VALIDATION_TIMINGS.remove();
    return timings;
  }

  /**
   * Validates model JSON using EVL and level-specific JSON checks.
   *
   * @param level model level
   * @param modelJson model JSON
   * @return validation result
   */
  ModelService.ValidationResult validate(ModelLevel level, JsonNode modelJson) {
    if (modelJson == null || modelJson.isNull()) {
      return new ModelService.ValidationResult(
          false, List.of(issue("ERROR", "ModelRequired", "Model JSON is required.")));
    }
    List<ModelService.ValidationIssue> issues = new ArrayList<>(validateWithEvl(level, modelJson));
    if (level == ModelLevel.CIM) {
      issues.addAll(validateCimModel(modelJson));
    }
    boolean valid = issues.stream().noneMatch(issue -> "ERROR".equals(issue.severity()));
    return new ModelService.ValidationResult(valid, issues);
  }

  /**
   * Validates generated or uploaded XMI bytes with EVL.
   *
   * @param level model level
   * @param xmiBytes XMI payload
   * @return validation result
   */
  ModelService.ValidationResult validateGeneratedXmi(ModelLevel level, byte[] xmiBytes) {
    List<ModelService.ValidationIssue> issues = validateWithEvl(level, xmiBytes);
    boolean valid = issues.stream().noneMatch(issue -> "ERROR".equals(issue.severity()));
    return new ModelService.ValidationResult(valid, issues);
  }

  /** Resets per-thread validation timing capture for a stored validation call. */
  void resetValidationTimings() {
    LAST_VALIDATION_TIMINGS.set(new LinkedHashMap<>());
  }

  /**
   * Records a validation phase duration in milliseconds.
   *
   * @param name timing key
   * @param elapsedNanos elapsed nanoseconds
   */
  void addValidationTiming(String name, long elapsedNanos) {
    LAST_VALIDATION_TIMINGS.get().merge(name, Math.max(0L, elapsedNanos / 1_000_000L), Long::sum);
  }

  /**
   * Returns a cached validation result when present.
   *
   * @param cacheKey validation cache key
   * @return cached result, or {@code null}
   */
  ModelService.ValidationResult cachedResult(String cacheKey) {
    return validationCache.get(cacheKey);
  }

  /**
   * Stores a validation result in the LRU cache.
   *
   * @param cacheKey validation cache key
   * @param result validation result
   */
  void putCachedResult(String cacheKey, ModelService.ValidationResult result) {
    validationCache.put(cacheKey, result);
  }

  /**
   * Detects validation failures that can be repaired by regenerating source XMI from JSON.
   *
   * @param result validation result
   * @return {@code true} when stale source XMI is likely
   */
  boolean hasRecoverableStaleSourceError(ModelService.ValidationResult result) {
    return hasRelationshipEndpointLoadingError(result) || hasTraceEndpointError(result);
  }

  /**
   * Builds a validation cache key from model identity and content metadata.
   *
   * @param model model record
   * @return cache key
   */
  String validationCacheKey(ModelRecord model) {
    return model.level().name()
        + ":"
        + model.id()
        + ":"
        + model.revision()
        + ":"
        + String.valueOf(model.sourceXmiHash());
  }

  /**
   * Creates a compact validation issue.
   *
   * @param severity issue severity
   * @param constraint issue constraint identifier
   * @param message issue message
   * @return validation issue
   */
  private static ModelService.ValidationIssue issue(
      String severity, String constraint, String message) {
    return new ModelService.ValidationIssue(
        severity, constraint, "MODEL", message, null, null, null);
  }

  /**
   * Detects missing relationship endpoint errors from EMF model loading.
   *
   * @param result validation result
   * @return {@code true} when endpoint loading failed
   */
  private boolean hasRelationshipEndpointLoadingError(ModelService.ValidationResult result) {
    return result != null
        && result.issues().stream()
            .anyMatch(
                issue ->
                    "ERROR".equals(issue.severity())
                        && "EVL_MODEL_LOADING".equals(issue.constraint())
                        && (issue.message().contains("required feature 'source'")
                            || issue.message().contains("required feature 'target'")));
  }

  /**
   * Detects TraceLink endpoint validation errors that can be repaired from graph endpoint ids.
   *
   * @param result validation result
   * @return {@code true} when TraceLink endpoints are stale
   */
  private boolean hasTraceEndpointError(ModelService.ValidationResult result) {
    return result != null
        && result.issues().stream()
            .anyMatch(
                issue ->
                    "ERROR".equals(issue.severity())
                        && "TraceLinkHasReferenceOrExternalId".equals(issue.constraint()));
  }

  /**
   * Exports model JSON to an in-memory EMF resource and validates it with EVL.
   *
   * @param level model level
   * @param modelJson model JSON
   * @return validation issues
   */
  private List<ModelService.ValidationIssue> validateWithEvl(ModelLevel level, JsonNode modelJson) {
    try {
      long phaseStarted = System.nanoTime();
      MetamodelDescriptor metamodel = metamodelResolver.resolve(level);
      addValidationTiming("validation.metamodelResolveMs", System.nanoTime() - phaseStarted);
      phaseStarted = System.nanoTime();
      Resource resource =
          xmiImportService.exportResource(level, semanticReferenceHydrator.apply(modelJson));
      addValidationTiming("validation.xmiExportResourceMs", System.nanoTime() - phaseStarted);
      phaseStarted = System.nanoTime();
      EvlValidationReport report =
          evlValidator.validate(
              new EvlValidationRequest(
                  mdePaths.validationRoot(level),
                  List.of(mdePaths.validationEntryFile(level)),
                  validationModelConfigurations(level, resource, metamodel.packages()),
                  true));
      addValidationTiming("validation.evlTotalMs", System.nanoTime() - phaseStarted);
      addValidationReportTimings(report);
      return validationIssues(report);
    } catch (PlatformException ex) {
      return List.of(issue("ERROR", "XmiExport", ex.getMessage()));
    } catch (EvlValidationException ex) {
      addValidationReportTimings(ex.getReport());
      return validationIssues(ex.getReport());
    } catch (Exception ex) {
      return List.of(
          issue(
              "ERROR",
              "EvlValidationExecution",
              "EVL validation could not run: "
                  + (ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage())));
    }
  }

  /**
   * Loads XMI bytes into an EMF resource and validates it with EVL.
   *
   * @param level model level
   * @param xmiBytes source XMI bytes
   * @return validation issues
   */
  private List<ModelService.ValidationIssue> validateWithEvl(ModelLevel level, byte[] xmiBytes) {
    try {
      long phaseStarted = System.nanoTime();
      MetamodelDescriptor metamodel = metamodelResolver.resolve(level);
      addValidationTiming("validation.metamodelResolveMs", System.nanoTime() - phaseStarted);
      phaseStarted = System.nanoTime();
      Resource resource = xmiImportService.loadResource(level, xmiBytes, "validation");
      addValidationTiming("validation.xmiLoadResourceMs", System.nanoTime() - phaseStarted);
      phaseStarted = System.nanoTime();
      EvlValidationReport report =
          evlValidator.validate(
              new EvlValidationRequest(
                  mdePaths.validationRoot(level),
                  List.of(mdePaths.validationEntryFile(level)),
                  validationModelConfigurations(level, resource, metamodel.packages()),
                  true));
      addValidationTiming("validation.evlTotalMs", System.nanoTime() - phaseStarted);
      addValidationReportTimings(report);
      return validationIssues(report);
    } catch (PlatformException ex) {
      return List.of(issue("ERROR", "XmiLoad", ex.getMessage()));
    } catch (EvlValidationException ex) {
      addValidationReportTimings(ex.getReport());
      return validationIssues(ex.getReport());
    } catch (Exception ex) {
      return List.of(
          issue(
              "ERROR",
              "EvlValidationExecution",
              "EVL validation could not run: "
                  + (ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage())));
    }
  }

  /**
   * Converts an EVL report to platform validation issues.
   *
   * @param report EVL validation report
   * @return validation issues
   */
  private List<ModelService.ValidationIssue> validationIssues(EvlValidationReport report) {
    if (report == null) {
      return List.of(
          issue("ERROR", "EvlValidationExecution", "EVL validation failed without a report."));
    }
    List<ModelService.ValidationIssue> issues = new ArrayList<>();
    report.violations().forEach(violation -> issues.add(validationIssue(violation)));
    report.diagnostics().forEach(diagnostic -> issues.add(validationIssue(diagnostic)));
    return issues;
  }

  private void addValidationReportTimings(EvlValidationReport report) {
    if (report == null) {
      return;
    }
    addValidationDuration("validation.moduleDiscoveryMs", report.moduleDiscoveryDuration());
    addValidationDuration("validation.reportDurationMs", report.duration());
    report
        .moduleReports()
        .forEach(
            moduleReport -> {
              addValidationDuration("validation.moduleDurationMs", moduleReport.duration());
              addValidationDuration("validation.parseMs", moduleReport.parseDuration());
              addValidationDuration("validation.modelLoadMs", moduleReport.modelLoadDuration());
              addValidationDuration(
                  "validation.structuralValidationMs", moduleReport.structuralValidationDuration());
              addValidationDuration("validation.evlExecuteMs", moduleReport.evlExecuteDuration());
              addValidationDuration(
                  "validation.violationMappingMs", moduleReport.violationMappingDuration());
              addValidationDuration("validation.disposeMs", moduleReport.disposeDuration());
            });
  }

  private void addValidationDuration(String name, Duration duration) {
    if (duration == null) {
      return;
    }
    LAST_VALIDATION_TIMINGS.get().merge(name, Math.max(0L, duration.toMillis()), Long::sum);
  }

  /**
   * Converts an EVL constraint violation to a platform validation issue.
   *
   * @param violation EVL violation
   * @return validation issue
   */
  private ModelService.ValidationIssue validationIssue(EvlConstraintViolation violation) {
    String severity = violation.kind() == EvlConstraintKind.MANDATORY ? "ERROR" : "WARNING";
    String elementId = violation.element().attributes().getOrDefault("id", null);
    String elementName =
        violation.element().attributes().getOrDefault("name", violation.element().summary());
    String guidance =
        violation.fixes().isEmpty() ? "" : "Suggested fix: " + violation.fixes().get(0).title();
    return new ModelService.ValidationIssue(
        severity,
        textOrDefault(violation.constraintName(), "EvlConstraint"),
        violation.contextType(),
        violation.message(),
        guidance,
        elementId,
        elementName);
  }

  /**
   * Converts an EVL diagnostic to a platform validation issue.
   *
   * @param diagnostic EVL diagnostic
   * @return validation issue
   */
  private ModelService.ValidationIssue validationIssue(EvlDiagnostic diagnostic) {
    String severity = diagnostic.severity() == ValidationSeverity.ERROR ? "ERROR" : "WARNING";
    String constraint = "EVL_" + diagnostic.phase().name();
    String message =
        !diagnostic.reason().isBlank() ? diagnostic.reason() : diagnostic.whatWentWrong();
    String guidance = diagnostic.howToFix();
    return new ModelService.ValidationIssue(
        severity,
        constraint,
        "EVL_DIAGNOSTIC",
        message,
        guidance,
        null,
        diagnostic.file() == null ? null : diagnostic.file().toString());
  }

  /**
   * Performs additional CIM JSON checks using merged modeling metadata.
   *
   * @param modelJson CIM model JSON
   * @return validation issues
   */
  private List<ModelService.ValidationIssue> validateCimModel(JsonNode modelJson) {
    List<ModelService.ValidationIssue> issues = new java.util.ArrayList<>();
    Map<String, Object> config = modelingConfig.config();
    Map<?, ?> levels = (Map<?, ?>) config.get("levels");
    Map<?, ?> cim = (Map<?, ?>) levels.get("cim");
    Map<String, Map<?, ?>> definitionsByType = new java.util.HashMap<>();
    if (cim.get("elements") instanceof List<?> elements) {
      elements.stream()
          .filter(Map.class::isInstance)
          .map(Map.class::cast)
          .forEach(
              element -> {
                Object type = element.get("type");
                if (type != null) {
                  definitionsByType.put(String.valueOf(type), element);
                }
              });
    }
    Map<String, JsonNode> graphObjectsById = new java.util.HashMap<>();
    indexGraphObjects(modelJson.path("graph").path("elements"), graphObjectsById);
    indexGraphObjects(modelJson.path("graph").path("relationships"), graphObjectsById);
    List<JsonNode> graphRelationships = new java.util.ArrayList<>();
    collectGraphRelationships(modelJson.path("graph").path("relationships"), graphRelationships);
    List<JsonNode> elements = new java.util.ArrayList<>();
    collectSemanticElements(modelJson, elements);
    if (elements.isEmpty()) {
      issues.add(issue("WARNING", "CimElementsMissing", "CIM model has no semantic elements."));
      return issues;
    }
    for (JsonNode element : elements) {
      String type = element.path("eClass").asText(element.path("type").asText(""));
      String elementId = element.path("id").asText(null);
      String elementName = element.path("name").asText(element.path("label").asText(null));
      Map<?, ?> definition = definitionsByType.get(type);
      if (definition == null) {
        issues.add(
            new ModelService.ValidationIssue(
                "ERROR",
                "UnknownCimElement",
                type,
                "Element type is not defined by the CIM metamodel.",
                null,
                elementId,
                elementName));
        continue;
      }
      JsonNode fallback = resolveGraphFallback(element, graphObjectsById, graphRelationships);
      validateRequiredFeatures(element, fallback, definition, issues, elementId, elementName);
    }
    return issues;
  }

  /**
   * Indexes graph objects by id.
   *
   * @param nodes graph object array
   * @param index mutable index
   */
  private void indexGraphObjects(JsonNode nodes, Map<String, JsonNode> index) {
    if (!nodes.isArray()) {
      return;
    }
    for (JsonNode node : nodes) {
      String id = node.path("id").asText("");
      if (!id.isBlank()) {
        index.put(id, node);
      }
    }
  }

  /**
   * Appends graph relationships to a mutable list.
   *
   * @param nodes graph relationship array
   * @param relationships mutable relationship sink
   */
  private void collectGraphRelationships(JsonNode nodes, List<JsonNode> relationships) {
    if (!nodes.isArray()) {
      return;
    }
    nodes.forEach(relationships::add);
  }

  /**
   * Resolves a graph object that can supply missing semantic fields.
   *
   * @param element semantic element
   * @param graphObjectsById graph objects keyed by id
   * @param graphRelationships graph relationship list
   * @return fallback graph object, or {@code null}
   */
  private JsonNode resolveGraphFallback(
      JsonNode element, Map<String, JsonNode> graphObjectsById, List<JsonNode> graphRelationships) {
    String id = element.path("id").asText("");
    if (!id.isBlank()) {
      JsonNode byId = graphObjectsById.get(id);
      if (byId != null) {
        return byId;
      }
    }
    String type = element.path("eClass").asText("");
    String name = element.path("name").asText("");
    for (JsonNode relationship : graphRelationships) {
      if (!type.equals(relationship.path("eClass").asText(""))) {
        continue;
      }
      if (!name.isBlank() && name.equals(relationship.path("name").asText(""))) {
        return relationship;
      }
    }
    return null;
  }

  /**
   * Recursively collects semantic objects while skipping transport graph data.
   *
   * @param node current JSON node
   * @param elements mutable semantic element sink
   */
  private void collectSemanticElements(JsonNode node, List<JsonNode> elements) {
    if (node == null || node.isNull()) {
      return;
    }
    if (node.isObject()) {
      if (node.hasNonNull("eClass")) {
        elements.add(node);
      }
      node.fields()
          .forEachRemaining(
              entry -> {
                if ("graph".equals(entry.getKey()) || "diagram".equals(entry.getKey())) {
                  return;
                }
                collectSemanticElements(entry.getValue(), elements);
              });
      return;
    }
    if (node.isArray()) {
      node.forEach(child -> collectSemanticElements(child, elements));
    }
  }

  /**
   * Returns the primary Epsilon model name used for validation.
   *
   * @param level model level
   * @return validation model name
   */
  private String validationModelName(ModelLevel level) {
    return switch (level) {
      case CIM -> "CIM";
      case PIM -> "PIM";
      case PSM -> "AWSPSM";
    };
  }

  /**
   * Returns additional model aliases used by EVL validation.
   *
   * @param level model level
   * @return validation model aliases
   */
  private List<String> validationModelAliases(ModelLevel level) {
    return switch (level) {
      case CIM, PIM -> List.of("KERNEL");
      case PSM -> List.of("AWSPSMENUMS", "KERNEL");
    };
  }

  private List<EvlModelConfiguration> validationModelConfigurations(
      ModelLevel level, Resource resource, List<EPackage> metamodelPackages) {
    List<EvlModelConfiguration> configurations = new ArrayList<>();
    configurations.add(
        ResourceEvlModelConfiguration.readOnly(
            validationModelName(level), resource, metamodelPackages));
    validationModelAliases(level)
        .forEach(
            alias ->
                configurations.add(
                    new ResourceEvlModelConfiguration(
                        alias, List.of(), resource, metamodelPackages, true, true, false)));
    return List.copyOf(configurations);
  }

  /**
   * Validates required attributes and references for one semantic element.
   *
   * @param element semantic element
   * @param fallbackElement graph fallback object
   * @param definition metadata definition
   * @param issues mutable issue sink
   * @param elementId element identifier
   * @param elementName element display name
   */
  private void validateRequiredFeatures(
      JsonNode element,
      JsonNode fallbackElement,
      Map<?, ?> definition,
      List<ModelService.ValidationIssue> issues,
      String elementId,
      String elementName) {
    validateRequiredFeatureList(
        element,
        fallbackElement,
        definition,
        definition.get("attributes"),
        "RequiredAttribute",
        issues,
        elementId,
        elementName);
    validateRequiredFeatureList(
        element,
        fallbackElement,
        definition,
        definition.get("references"),
        "RequiredReference",
        issues,
        elementId,
        elementName);
  }

  /**
   * Validates one configured required feature list.
   *
   * @param element semantic element
   * @param fallbackElement graph fallback object
   * @param definition metadata definition
   * @param fieldsObject configured field list
   * @param constraint validation constraint name
   * @param issues mutable issue sink
   * @param elementId element identifier
   * @param elementName element display name
   */
  private void validateRequiredFeatureList(
      JsonNode element,
      JsonNode fallbackElement,
      Map<?, ?> definition,
      Object fieldsObject,
      String constraint,
      List<ModelService.ValidationIssue> issues,
      String elementId,
      String elementName) {
    if (!(fieldsObject instanceof List<?> fields)) {
      return;
    }
    for (Object fieldObject : fields) {
      if (!(fieldObject instanceof Map<?, ?> field)
          || !Boolean.TRUE.equals(field.get("required"))) {
        continue;
      }
      if ("RequiredReference".equals(constraint) && Boolean.TRUE.equals(field.get("readonly"))) {
        continue;
      }
      String name = String.valueOf(field.get("name"));
      if ("RequiredReference".equals(constraint)
          && Boolean.TRUE.equals(definition.get("relationshipElement"))
          && ("source".equals(name) || "target".equals(name))) {
        continue;
      }
      JsonNode value = element.get(name);
      if ((value == null
              || value.isNull()
              || (value.isTextual() && value.asText().isBlank())
              || (value.isArray() && value.isEmpty()))
          && fallbackElement != null) {
        value = fallbackElement.get(name);
      }
      if (value == null
          || value.isNull()
          || (value.isTextual() && value.asText().isBlank())
          || (value.isArray() && value.isEmpty())) {
        issues.add(
            new ModelService.ValidationIssue(
                "ERROR",
                constraint,
                element.path("eClass").asText(),
                "Required CIM feature is missing: " + name,
                null,
                elementId,
                elementName));
      }
    }
  }

  /**
   * Returns a fallback when a string is blank.
   *
   * @param value candidate value
   * @param fallback fallback value
   * @return non-blank value
   */
  private String textOrDefault(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value;
  }
}
