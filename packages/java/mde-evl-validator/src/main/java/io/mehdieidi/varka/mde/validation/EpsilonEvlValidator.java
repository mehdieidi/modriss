package io.mehdieidi.varka.mde.validation;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;
import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.epsilon.common.module.ModuleElement;
import org.eclipse.epsilon.common.parse.problem.ParseProblem;
import org.eclipse.epsilon.eol.exceptions.EolRuntimeException;
import org.eclipse.epsilon.eol.exceptions.models.EolModelLoadingException;
import org.eclipse.epsilon.eol.models.IModel;
import org.eclipse.epsilon.evl.EvlModule;
import org.eclipse.epsilon.evl.dom.Constraint;
import org.eclipse.epsilon.evl.execute.FixInstance;
import org.eclipse.epsilon.evl.execute.UnsatisfiedConstraint;

/**
 * Executes Epsilon EVL modules against configured EMF models and returns structured validation
 * reports.
 */
public final class EpsilonEvlValidator {

  /** Default watchdog limit for EVL execution when no timeout is supplied. */
  private static final Duration DEFAULT_EXECUTION_TIMEOUT = Duration.ofMinutes(5);

  /** Default maximum captured bytes per Epsilon output stream. */
  private static final int DEFAULT_MAX_CAPTURED_OUTPUT_BYTES = 1024 * 1024;

  /** Attribute names that can be copied into diagnostics without exposing secrets. */
  private static final Set<String> SAFE_DIAGNOSTIC_ATTRIBUTES =
      Set.of(
          "id", "name", "eclass", "logicalid", "stackname", "stagename", "pathtemplate", "method");

  /** Optional execution timeout; {@code null} disables the watchdog. */
  private final Duration executionTimeout;

  /** Maximum number of bytes captured from each output stream. */
  private final int maxCapturedOutputBytes;

  /** Creates a validator with default output capture limits and no timeout. */
  public EpsilonEvlValidator() {
    this(null, DEFAULT_MAX_CAPTURED_OUTPUT_BYTES);
  }

  /**
   * Creates a validator with explicit runtime limits.
   *
   * @param executionTimeout timeout for EVL execution; {@code null} or non-positive values disable
   *     the watchdog
   * @param maxCapturedOutputBytes byte limit for each captured output stream
   */
  public EpsilonEvlValidator(Duration executionTimeout, int maxCapturedOutputBytes) {
    this.executionTimeout =
        executionTimeout == null || executionTimeout.isZero() || executionTimeout.isNegative()
            ? null
            : executionTimeout;
    this.maxCapturedOutputBytes =
        maxCapturedOutputBytes <= 0 ? DEFAULT_MAX_CAPTURED_OUTPUT_BYTES : maxCapturedOutputBytes;
  }

  /**
   * Validates the requested EVL root/modules against the configured models.
   *
   * @param request validation request
   * @return successful validation report
   * @throws EvlValidationException when request validation, module discovery, module execution, or
   *     structural validation fails
   */
  public EvlValidationReport validate(EvlValidationRequest request) throws EvlValidationException {
    Instant startedAt = Instant.now();
    List<EvlDiagnostic> diagnostics = new ArrayList<>();
    List<EvlModuleReport> moduleReports = new ArrayList<>();
    List<EvlConstraintViolation> violations = new ArrayList<>();
    Duration moduleDiscoveryDuration = Duration.ZERO;
    BoundedByteArrayOutputStream stdout = new BoundedByteArrayOutputStream(maxCapturedOutputBytes);
    BoundedByteArrayOutputStream warnings =
        new BoundedByteArrayOutputStream(maxCapturedOutputBytes);
    BoundedByteArrayOutputStream stderr = new BoundedByteArrayOutputStream(maxCapturedOutputBytes);

    try {
      validateRequest(request, diagnostics);
      failIfDiagnostics(
          request,
          startedAt,
          moduleDiscoveryDuration,
          moduleReports,
          violations,
          diagnostics,
          stdout,
          warnings,
          stderr,
          null);

      Instant moduleDiscoveryStartedAt = Instant.now();
      List<Path> modules = discoverModules(request, diagnostics);
      moduleDiscoveryDuration = Duration.between(moduleDiscoveryStartedAt, Instant.now());
      failIfDiagnostics(
          request,
          startedAt,
          moduleDiscoveryDuration,
          moduleReports,
          violations,
          diagnostics,
          stdout,
          warnings,
          stderr,
          null);

      for (Path moduleFile : modules) {
        EvlModuleReport moduleReport =
            validateModule(moduleFile, request, stdout, warnings, stderr);
        moduleReports.add(moduleReport);
        violations.addAll(moduleReport.violations());
        diagnostics.addAll(moduleReport.diagnostics());
      }

      if (diagnostics.stream().anyMatch(d -> d.severity() == ValidationSeverity.ERROR)) {
        throw failure(
            "EVL validation failed.",
            request,
            startedAt,
            moduleReports,
            moduleDiscoveryDuration,
            violations,
            diagnostics,
            stdout,
            warnings,
            stderr,
            null);
      }

      return report(
          EvlValidationStatus.SUCCEEDED,
          request,
          startedAt,
          moduleReports,
          moduleDiscoveryDuration,
          violations,
          diagnostics,
          stdout,
          warnings,
          stderr);
    } catch (EvlValidationException ex) {
      throw ex;
    } catch (Exception ex) {
      diagnostics.add(
          EvlDiagnostic.error(
              ValidationPhase.UNEXPECTED,
              request.evlRoot(),
              -1,
              -1,
              ex.getMessage(),
              "An unexpected exception interrupted EVL validation.",
              "Inspect the exception type and stack trace, then verify EVL/model/metamodel"
                  + " compatibility.",
              ex));
      throw failure(
          "EVL validation failed unexpectedly.",
          request,
          startedAt,
          moduleReports,
          moduleDiscoveryDuration,
          violations,
          diagnostics,
          stdout,
          warnings,
          stderr,
          ex);
    }
  }

  /**
   * Parses, loads models for, and executes one EVL module.
   *
   * @param moduleFile module file to execute
   * @param request original validation request
   * @param stdout captured standard output
   * @param warnings captured warning output
   * @param stderr captured error output
   * @return per-module validation report
   * @throws EvlValidationException reserved for report-level failures
   */
  private EvlModuleReport validateModule(
      Path moduleFile,
      EvlValidationRequest request,
      BoundedByteArrayOutputStream stdout,
      BoundedByteArrayOutputStream warnings,
      BoundedByteArrayOutputStream stderr)
      throws EvlValidationException {
    Instant startedAt = Instant.now();
    List<EvlDiagnostic> diagnostics = new ArrayList<>();
    List<EvlConstraintViolation> violations = new ArrayList<>();
    List<IModel> loadedModels = new ArrayList<>();
    EvlModule module = new EvlModule();
    Duration parseDuration = Duration.ZERO;
    Duration modelLoadDuration = Duration.ZERO;
    Duration structuralValidationDuration = Duration.ZERO;
    Duration evlExecuteDuration = Duration.ZERO;
    Duration violationMappingDuration = Duration.ZERO;
    Duration disposeDuration = Duration.ZERO;

    try {
      Instant phaseStartedAt = Instant.now();
      boolean parsed = parseModule(moduleFile, module, diagnostics);
      parseDuration = Duration.between(phaseStartedAt, Instant.now());
      if (parsed && diagnostics.stream().noneMatch(d -> d.severity() == ValidationSeverity.ERROR)) {
        configureStreams(module, request.captureOutput(), stdout, warnings, stderr);
        for (EvlModelConfiguration modelConfiguration : request.models()) {
          phaseStartedAt = Instant.now();
          IModel model = modelConfiguration.load();
          modelLoadDuration =
              modelLoadDuration.plus(Duration.between(phaseStartedAt, Instant.now()));
          loadedModels.add(model);

          phaseStartedAt = Instant.now();
          diagnostics.addAll(modelConfiguration.validateLoadedModel(model));
          structuralValidationDuration =
              structuralValidationDuration.plus(Duration.between(phaseStartedAt, Instant.now()));
          module.getContext().getModelRepository().addModel(model);
        }
        if (diagnostics.stream().noneMatch(d -> d.severity() == ValidationSeverity.ERROR)) {
          phaseStartedAt = Instant.now();
          Set<UnsatisfiedConstraint> unsatisfiedConstraints = executeModule(module);
          evlExecuteDuration = Duration.between(phaseStartedAt, Instant.now());

          phaseStartedAt = Instant.now();
          for (UnsatisfiedConstraint unsatisfiedConstraint : unsatisfiedConstraints) {
            violations.add(toViolation(unsatisfiedConstraint));
          }
          violationMappingDuration = Duration.between(phaseStartedAt, Instant.now());
        }
      }
    } catch (EolModelLoadingException ex) {
      diagnostics.add(
          EvlDiagnostic.error(
              ValidationPhase.MODEL_LOADING,
              moduleFile,
              -1,
              -1,
              ex.getMessage(),
              "An EMF model or metamodel could not be loaded.",
              "Check model paths, metamodel paths, aliases, namespace URIs, and Ecore"
                  + " availability.",
              ex));
    } catch (EolRuntimeException ex) {
      diagnostics.add(runtimeDiagnostic(ex, moduleFile));
    } catch (EpsilonExecutionTimeoutException ex) {
      diagnostics.add(
          EvlDiagnostic.error(
              ValidationPhase.EXECUTION,
              moduleFile,
              -1,
              -1,
              ex.getMessage(),
              "The EVL module exceeded the configured execution timeout.",
              "Reduce model size, inspect EVL rules for non-terminating logic, or increase the"
                  + " timeout deliberately.",
              ex));
    } catch (Exception ex) {
      diagnostics.add(
          EvlDiagnostic.error(
              ValidationPhase.UNEXPECTED,
              moduleFile,
              -1,
              -1,
              ex.getMessage(),
              "An unexpected exception interrupted this EVL module.",
              "Inspect the exception type and stack trace, then verify the EVL module and model"
                  + " aliases.",
              ex));
    } finally {
      Instant disposeStartedAt = Instant.now();
      for (IModel model : loadedModels) {
        try {
          model.dispose();
        } catch (RuntimeException ignored) {
          // Disposal should not hide the validation result.
        }
      }
      module.getContext().dispose();
      disposeDuration = Duration.between(disposeStartedAt, Instant.now());
    }
    return moduleReport(
        moduleFile,
        startedAt,
        parseDuration,
        modelLoadDuration,
        structuralValidationDuration,
        evlExecuteDuration,
        violationMappingDuration,
        disposeDuration,
        violations,
        diagnostics);
  }

  /**
   * Performs request preflight validation before module discovery.
   *
   * @param request validation request to check
   * @param diagnostics mutable diagnostic sink
   */
  private void validateRequest(EvlValidationRequest request, List<EvlDiagnostic> diagnostics) {
    if (!Files.exists(request.evlRoot())) {
      diagnostics.add(
          EvlDiagnostic.error(
              ValidationPhase.REQUEST_VALIDATION,
              request.evlRoot(),
              -1,
              -1,
              "EVL root does not exist.",
              "The requested EVL entry point or directory cannot be found.",
              "Provide an existing .evl file or a directory containing entry .evl files.",
              null));
    }
    if (request.models().isEmpty()) {
      diagnostics.add(
          EvlDiagnostic.error(
              ValidationPhase.REQUEST_VALIDATION,
              request.evlRoot(),
              -1,
              -1,
              "At least one EMF model configuration is required.",
              "EVL has no model repository to validate.",
              "Configure a file-backed or in-memory EMF model before executing.",
              null));
    }
    for (EvlModelConfiguration model : request.models()) {
      if (model instanceof FileEvlModelConfiguration fileModel) {
        validateFileModel(fileModel, diagnostics);
      }
    }
  }

  /**
   * Validates paths used by a file-backed EVL model configuration.
   *
   * @param model model configuration to check
   * @param diagnostics mutable diagnostic sink
   */
  private void validateFileModel(FileEvlModelConfiguration model, List<EvlDiagnostic> diagnostics) {
    if (!Files.isRegularFile(model.modelFile())) {
      diagnostics.add(
          EvlDiagnostic.error(
              ValidationPhase.REQUEST_VALIDATION,
              model.modelFile(),
              -1,
              -1,
              "Model file for '" + model.name() + "' does not exist.",
              "The file-backed EMF model cannot be loaded.",
              "Provide an existing model file or use ResourceEvlModelConfiguration for in-memory"
                  + " models.",
              null));
    }
    for (Path metamodel : model.metamodelFiles()) {
      if (!Files.isRegularFile(metamodel)) {
        diagnostics.add(
            EvlDiagnostic.error(
                ValidationPhase.REQUEST_VALIDATION,
                metamodel,
                -1,
                -1,
                "Metamodel file for '" + model.name() + "' does not exist.",
                "The EMF model cannot be typed without all referenced Ecore metamodels.",
                "Provide the missing .ecore file.",
                null));
      }
    }
  }

  /**
   * Resolves the EVL modules that should be parsed and executed.
   *
   * @param request validation request
   * @param diagnostics mutable diagnostic sink
   * @return discovered module files in execution order
   */
  private List<Path> discoverModules(
      EvlValidationRequest request, List<EvlDiagnostic> diagnostics) {
    if (!request.moduleFiles().isEmpty()) {
      List<Path> modules =
          request.moduleFiles().stream()
              .map(path -> path.isAbsolute() ? path : request.evlRoot().resolve(path))
              .toList();
      for (Path module : modules) {
        if (!Files.isRegularFile(module)) {
          diagnostics.add(missingModuleDiagnostic(module));
        }
      }
      return modules;
    }
    if (Files.isRegularFile(request.evlRoot())) {
      return List.of(request.evlRoot());
    }
    try (Stream<Path> entries = Files.list(request.evlRoot())) {
      List<Path> modules =
          entries
              .filter(Files::isRegularFile)
              .filter(path -> path.getFileName().toString().endsWith(".evl"))
              .sorted(Comparator.comparing(path -> path.getFileName().toString()))
              .toList();
      if (modules.isEmpty()) {
        diagnostics.add(
            EvlDiagnostic.error(
                ValidationPhase.MODULE_DISCOVERY,
                request.evlRoot(),
                -1,
                -1,
                "No entry EVL modules were found.",
                "The directory root does not contain any direct .evl files.",
                "Point evlRoot at an .evl file or place/import an entry .evl module directly under"
                    + " the root directory.",
                null));
      }
      if (modules.size() > 1) {
        // Directory mode is supported, but each entry module reloads models independently.
        diagnostics.add(
            new EvlDiagnostic(
                ValidationSeverity.WARNING,
                ValidationPhase.MODULE_DISCOVERY,
                request.evlRoot(),
                -1,
                -1,
                "Directory-mode EVL validation discovered " + modules.size() + " entry modules.",
                "Each direct .evl file will be parsed and executed as a separate module, which"
                    + " reloads the configured models for every module.",
                "For normal CIM/PIM/PSM profile validation, point evlRoot at the semantic entry"
                    + " module or pass exactly one entry module file.",
                ""));
      }
      return modules;
    } catch (IOException ex) {
      diagnostics.add(
          EvlDiagnostic.error(
              ValidationPhase.MODULE_DISCOVERY,
              request.evlRoot(),
              -1,
              -1,
              ex.getMessage(),
              "EVL module discovery failed.",
              "Check that the directory is readable and retry.",
              ex));
      return List.of();
    }
  }

  /**
   * Creates a diagnostic for an explicitly requested missing module.
   *
   * @param module missing module path
   * @return module discovery diagnostic
   */
  private EvlDiagnostic missingModuleDiagnostic(Path module) {
    return EvlDiagnostic.error(
        ValidationPhase.MODULE_DISCOVERY,
        module,
        -1,
        -1,
        "EVL module does not exist.",
        "An explicitly configured EVL module cannot be found.",
        "Provide an existing .evl file.",
        null);
  }

  /**
   * Parses an EVL module and maps parser diagnostics into report diagnostics.
   *
   * @param moduleFile module file to parse
   * @param module Epsilon EVL module
   * @param diagnostics mutable diagnostic sink
   * @return {@code true} when Epsilon reports a parsed module
   */
  private boolean parseModule(Path moduleFile, EvlModule module, List<EvlDiagnostic> diagnostics) {
    boolean parsed;
    try {
      parsed = module.parse(moduleFile.toFile());
    } catch (Exception ex) {
      diagnostics.add(
          EvlDiagnostic.error(
              ValidationPhase.PARSE,
              moduleFile,
              -1,
              -1,
              ex.getMessage(),
              "The EVL parser could not build the module AST.",
              "Open the EVL file and imported modules, fix syntax/import errors, and retry.",
              ex));
      return false;
    }
    for (ParseProblem problem : module.getParseProblems()) {
      diagnostics.add(
          new EvlDiagnostic(
              problem.getSeverity() == ParseProblem.WARNING
                  ? ValidationSeverity.WARNING
                  : ValidationSeverity.ERROR,
              ValidationPhase.PARSE,
              moduleFile,
              problem.getLine(),
              problem.getColumn(),
              problem.getReason(),
              "The EVL module or one of its imports has a parse problem.",
              "Fix the reported EVL/EOL syntax or import path near the reported location.",
              ParseProblem.class.getName()));
    }
    return parsed;
  }

  /**
   * Converts an unsatisfied EVL constraint into the public violation shape.
   *
   * @param unsatisfiedConstraint unsatisfied EVL constraint
   * @return mapped constraint violation
   */
  private EvlConstraintViolation toViolation(UnsatisfiedConstraint unsatisfiedConstraint) {
    Constraint constraint = unsatisfiedConstraint.getConstraint();
    ModuleElement ast = constraint;
    return new EvlConstraintViolation(
        constraint.isCritique() ? EvlConstraintKind.OPTIONAL : EvlConstraintKind.MANDATORY,
        constraint.getName(),
        constraint.getConstraintContext() == null
            ? ""
            : constraint.getConstraintContext().getTypeName(),
        unsatisfiedConstraint.getMessage(),
        ast.getFile() == null ? null : ast.getFile().toPath(),
        ast.getRegion() == null ? -1 : ast.getRegion().getStart().getLine(),
        ast.getRegion() == null ? -1 : ast.getRegion().getStart().getColumn(),
        elementReference(unsatisfiedConstraint.getInstance()),
        fixSuggestions(unsatisfiedConstraint.getFixes()),
        extras(unsatisfiedConstraint.getExtras()));
  }

  /**
   * Builds a safe reference for the element associated with a violation.
   *
   * @param instance violating model element or scalar value
   * @return safe element reference
   */
  private EvlElementReference elementReference(Object instance) {
    if (instance instanceof EObject eObject) {
      Resource resource = eObject.eResource();
      String uri =
          resource == null || resource.getURI() == null ? "" : resource.getURI().toString();
      String fragment = resource == null ? "" : resource.getURIFragment(eObject);
      Map<String, String> attributes = new LinkedHashMap<>();
      for (EAttribute attribute : eObject.eClass().getEAllAttributes()) {
        if (!attribute.isMany()
            && eObject.eIsSet(attribute)
            && isSafeDiagnosticAttribute(attribute)) {
          Object value = eObject.eGet(attribute);
          attributes.put(attribute.getName(), safeDiagnosticValue(value));
        }
      }
      String summary = eObject.eClass().getName();
      if (!attributes.isEmpty()) {
        summary = summary + attributes;
      }
      return new EvlElementReference(
          eObject.eClass().getName(), uri, fragment, attributes, summary);
    }
    return new EvlElementReference(
        instance == null ? "" : instance.getClass().getName(),
        "",
        "",
        Map.of(),
        instance == null ? "" : instance.toString());
  }

  /**
   * Determines whether an EMF attribute can be exposed in diagnostics.
   *
   * @param attribute attribute to inspect
   * @return {@code true} when the attribute is identifying and non-sensitive
   */
  private boolean isSafeDiagnosticAttribute(EAttribute attribute) {
    String name = attribute.getName() == null ? "" : attribute.getName().toLowerCase(Locale.ROOT);
    if (isSensitiveAttributeName(name)) {
      return false;
    }
    return attribute.isID() || SAFE_DIAGNOSTIC_ATTRIBUTES.contains(name);
  }

  /**
   * Identifies attribute names that should never be copied into diagnostics.
   *
   * @param name lowercase attribute name
   * @return {@code true} when the name appears sensitive
   */
  private boolean isSensitiveAttributeName(String name) {
    return name.contains("secret")
        || name.contains("password")
        || name.contains("token")
        || name.contains("key")
        || name.contains("value")
        || name.contains("email");
  }

  /**
   * Converts an attribute value to bounded diagnostic text.
   *
   * @param value attribute value
   * @return safe string representation
   */
  private String safeDiagnosticValue(Object value) {
    if (value == null) {
      return "";
    }
    String text = value.toString();
    return text.length() <= 256 ? text : text.substring(0, 256) + "...";
  }

  /**
   * Evaluates available EVL fix titles into serializable suggestions.
   *
   * @param fixes EVL fix instances
   * @return evaluated fix suggestions
   */
  private List<EvlFixSuggestion> fixSuggestions(List<FixInstance> fixes) {
    if (fixes == null || fixes.isEmpty()) {
      return List.of();
    }
    List<EvlFixSuggestion> suggestions = new ArrayList<>();
    for (FixInstance fix : fixes) {
      try {
        suggestions.add(new EvlFixSuggestion(fix.getTitle()));
      } catch (EolRuntimeException ex) {
        suggestions.add(
            new EvlFixSuggestion(
                "Fix title could not be evaluated: "
                    + firstNonBlank(ex.getReason(), ex.getMessage())));
      }
    }
    return suggestions;
  }

  /**
   * Converts EVL extras to a string map for JSON-safe reporting.
   *
   * @param extras extras reported by Epsilon
   * @return string-valued extras map
   */
  private Map<String, String> extras(Map<String, Object> extras) {
    if (extras == null || extras.isEmpty()) {
      return Map.of();
    }
    Map<String, String> values = new LinkedHashMap<>();
    for (Map.Entry<String, Object> entry : extras.entrySet()) {
      values.put(entry.getKey(), entry.getValue() == null ? "" : entry.getValue().toString());
    }
    return values;
  }

  /**
   * Executes a parsed EVL module, optionally protected by the watchdog timeout.
   *
   * @param module parsed EVL module
   * @return unsatisfied constraints reported by Epsilon
   * @throws Exception when EVL execution or the watchdog fails
   */
  private Set<UnsatisfiedConstraint> executeModule(EvlModule module) throws Exception {
    if (executionTimeout == null) {
      return module.execute();
    }
    Thread executingThread = Thread.currentThread();
    AtomicBoolean timedOut = new AtomicBoolean(false);
    ScheduledExecutorService watchdog =
        Executors.newSingleThreadScheduledExecutor(
            runnable -> {
              Thread thread = new Thread(runnable, "epsilon-evl-watchdog");
              thread.setDaemon(true);
              return thread;
            });
    ScheduledFuture<?> timeout =
        watchdog.schedule(
            () -> {
              timedOut.set(true);
              // EVL execution is synchronous, so the watchdog interrupts the caller thread.
              executingThread.interrupt();
            },
            executionTimeout.toMillis(),
            TimeUnit.MILLISECONDS);
    try {
      try {
        Set<UnsatisfiedConstraint> result = module.execute();
        if (timedOut.get()) {
          throw timeoutException();
        }
        return result;
      } catch (Exception ex) {
        if (timedOut.get()) {
          throw timeoutException();
        }
        throw ex;
      }
    } finally {
      timeout.cancel(true);
      watchdog.shutdownNow();
      if (timedOut.get()) {
        Thread.interrupted();
      }
    }
  }

  /**
   * Builds the timeout exception used after watchdog interruption.
   *
   * @return timeout exception with a user-facing duration
   */
  private EpsilonExecutionTimeoutException timeoutException() {
    return new EpsilonExecutionTimeoutException(
        "EVL execution timed out after " + executionTimeout + ".", null);
  }

  /**
   * Redirects Epsilon output streams to bounded capture buffers when requested.
   *
   * @param module module whose streams should be configured
   * @param captureOutput whether capture is enabled
   * @param stdout standard output buffer
   * @param warnings warning output buffer
   * @param stderr error output buffer
   */
  private void configureStreams(
      EvlModule module,
      boolean captureOutput,
      BoundedByteArrayOutputStream stdout,
      BoundedByteArrayOutputStream warnings,
      BoundedByteArrayOutputStream stderr) {
    if (!captureOutput) {
      return;
    }
    module.getContext().setOutputStream(new PrintStream(stdout, true, StandardCharsets.UTF_8));
    module.getContext().setWarningStream(new PrintStream(warnings, true, StandardCharsets.UTF_8));
    module.getContext().setErrorStream(new PrintStream(stderr, true, StandardCharsets.UTF_8));
  }

  /**
   * Converts an Epsilon runtime exception into an execution diagnostic with source location when
   * available.
   *
   * @param ex runtime exception from Epsilon
   * @param fallbackFile file used when the AST does not expose a source file
   * @return structured runtime diagnostic
   */
  private EvlDiagnostic runtimeDiagnostic(EolRuntimeException ex, Path fallbackFile) {
    ModuleElement ast = ex.getAst();
    Path file = fallbackFile;
    if (ast != null && ast.getFile() != null) {
      file = ast.getFile().toPath();
    }
    return EvlDiagnostic.error(
        ValidationPhase.EXECUTION,
        file,
        ex.getLine(),
        ex.getColumn(),
        firstNonBlank(ex.getReason(), ex.getMessage()),
        "The EVL module failed while evaluating a constraint, guard, message, or fix.",
        "Inspect the reported file, line, and column. Check model aliases, type names, feature"
            + " names, helper imports, and null-safety.",
        ex);
  }

  /**
   * Returns the first non-blank string, or an empty string when neither value is useful.
   *
   * @param first preferred value
   * @param second fallback value
   * @return first non-blank value
   */
  private String firstNonBlank(String first, String second) {
    if (first != null && !first.isBlank()) {
      return first;
    }
    return second == null ? "" : second;
  }

  /**
   * Creates a module report from phase timing and collected results.
   *
   * @param moduleFile module that was executed
   * @param startedAt module start time
   * @param parseDuration parse duration
   * @param modelLoadDuration model load duration
   * @param structuralValidationDuration structural validation duration
   * @param evlExecuteDuration EVL execution duration
   * @param violationMappingDuration violation mapping duration
   * @param disposeDuration disposal duration
   * @param violations mapped violations
   * @param diagnostics module diagnostics
   * @return per-module report
   */
  private EvlModuleReport moduleReport(
      Path moduleFile,
      Instant startedAt,
      Duration parseDuration,
      Duration modelLoadDuration,
      Duration structuralValidationDuration,
      Duration evlExecuteDuration,
      Duration violationMappingDuration,
      Duration disposeDuration,
      List<EvlConstraintViolation> violations,
      List<EvlDiagnostic> diagnostics) {
    Instant finishedAt = Instant.now();
    return new EvlModuleReport(
        moduleFile,
        Duration.between(startedAt, finishedAt),
        parseDuration,
        modelLoadDuration,
        structuralValidationDuration,
        evlExecuteDuration,
        violationMappingDuration,
        disposeDuration,
        violations,
        diagnostics);
  }

  /**
   * Fails fast when request validation or module discovery collected errors.
   *
   * @param request original validation request
   * @param startedAt run start time
   * @param moduleDiscoveryDuration module discovery duration
   * @param moduleReports collected module reports
   * @param violations collected violations
   * @param diagnostics collected diagnostics
   * @param stdout captured standard output
   * @param warnings captured warning output
   * @param stderr captured error output
   * @param cause optional root cause
   * @throws EvlValidationException when diagnostics contain an error
   */
  private void failIfDiagnostics(
      EvlValidationRequest request,
      Instant startedAt,
      Duration moduleDiscoveryDuration,
      List<EvlModuleReport> moduleReports,
      List<EvlConstraintViolation> violations,
      List<EvlDiagnostic> diagnostics,
      BoundedByteArrayOutputStream stdout,
      BoundedByteArrayOutputStream warnings,
      BoundedByteArrayOutputStream stderr,
      Throwable cause)
      throws EvlValidationException {
    if (diagnostics.stream().anyMatch(d -> d.severity() == ValidationSeverity.ERROR)) {
      throw failure(
          "EVL validation request is invalid.",
          request,
          startedAt,
          moduleReports,
          moduleDiscoveryDuration,
          violations,
          diagnostics,
          stdout,
          warnings,
          stderr,
          cause);
    }
  }

  /**
   * Builds a validation exception carrying a failed report.
   *
   * @param message exception message
   * @param request original validation request
   * @param startedAt run start time
   * @param moduleReports collected module reports
   * @param moduleDiscoveryDuration module discovery duration
   * @param violations collected violations
   * @param diagnostics collected diagnostics
   * @param stdout captured standard output
   * @param warnings captured warning output
   * @param stderr captured error output
   * @param cause optional root cause
   * @return exception with a failed validation report
   */
  private EvlValidationException failure(
      String message,
      EvlValidationRequest request,
      Instant startedAt,
      List<EvlModuleReport> moduleReports,
      Duration moduleDiscoveryDuration,
      List<EvlConstraintViolation> violations,
      List<EvlDiagnostic> diagnostics,
      BoundedByteArrayOutputStream stdout,
      BoundedByteArrayOutputStream warnings,
      BoundedByteArrayOutputStream stderr,
      Throwable cause) {
    return new EvlValidationException(
        message,
        report(
            EvlValidationStatus.FAILED,
            request,
            startedAt,
            moduleReports,
            moduleDiscoveryDuration,
            violations,
            diagnostics,
            stdout,
            warnings,
            stderr),
        cause);
  }

  /**
   * Creates an immutable validation report from the current run state.
   *
   * @param status terminal status
   * @param request original validation request
   * @param startedAt run start time
   * @param moduleReports collected module reports
   * @param moduleDiscoveryDuration module discovery duration
   * @param violations collected violations
   * @param diagnostics collected diagnostics
   * @param stdout captured standard output
   * @param warnings captured warning output
   * @param stderr captured error output
   * @return validation report
   */
  private EvlValidationReport report(
      EvlValidationStatus status,
      EvlValidationRequest request,
      Instant startedAt,
      List<EvlModuleReport> moduleReports,
      Duration moduleDiscoveryDuration,
      List<EvlConstraintViolation> violations,
      List<EvlDiagnostic> diagnostics,
      BoundedByteArrayOutputStream stdout,
      BoundedByteArrayOutputStream warnings,
      BoundedByteArrayOutputStream stderr) {
    Instant finishedAt = Instant.now();
    return new EvlValidationReport(
        status,
        request.evlRoot(),
        startedAt,
        finishedAt,
        Duration.between(startedAt, finishedAt),
        moduleDiscoveryDuration,
        moduleReports,
        violations,
        diagnostics,
        stdout.asUtf8String(),
        warnings.asUtf8String(),
        stderr.asUtf8String());
  }

  /**
   * Runtime exception used internally to distinguish watchdog timeouts from normal Epsilon runtime
   * failures.
   */
  private static final class EpsilonExecutionTimeoutException extends RuntimeException {

    /**
     * Creates a timeout exception.
     *
     * @param message exception message
     * @param cause optional root cause
     */
    private EpsilonExecutionTimeoutException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
