package io.mehdieidi.modless.mde.validation;

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

public final class EpsilonEvlValidator {

    private static final Duration DEFAULT_EXECUTION_TIMEOUT = Duration.ofMinutes(5);
    private static final int DEFAULT_MAX_CAPTURED_OUTPUT_BYTES = 1024 * 1024;
    private static final Set<String> SAFE_DIAGNOSTIC_ATTRIBUTES = Set.of(
            "id",
            "name",
            "eclass",
            "logicalid",
            "stackname",
            "stagename",
            "pathtemplate",
            "method");

    private final Duration executionTimeout;
    private final int maxCapturedOutputBytes;

    public EpsilonEvlValidator() {
        this(null, DEFAULT_MAX_CAPTURED_OUTPUT_BYTES);
    }

    public EpsilonEvlValidator(Duration executionTimeout, int maxCapturedOutputBytes) {
        this.executionTimeout = executionTimeout == null || executionTimeout.isZero()
                || executionTimeout.isNegative() ? null : executionTimeout;
        this.maxCapturedOutputBytes = maxCapturedOutputBytes <= 0
                ? DEFAULT_MAX_CAPTURED_OUTPUT_BYTES : maxCapturedOutputBytes;
    }

    public EvlValidationReport validate(EvlValidationRequest request)
            throws EvlValidationException {
        Instant startedAt = Instant.now();
        List<EvlDiagnostic> diagnostics = new ArrayList<>();
        List<EvlModuleReport> moduleReports = new ArrayList<>();
        List<EvlConstraintViolation> violations = new ArrayList<>();
        BoundedByteArrayOutputStream stdout = new BoundedByteArrayOutputStream(
                maxCapturedOutputBytes);
        BoundedByteArrayOutputStream warnings = new BoundedByteArrayOutputStream(
                maxCapturedOutputBytes);
        BoundedByteArrayOutputStream stderr = new BoundedByteArrayOutputStream(
                maxCapturedOutputBytes);

        try {
            validateRequest(request, diagnostics);
            failIfDiagnostics(request, startedAt, moduleReports, violations, diagnostics,
                    stdout, warnings, stderr, null);

            List<Path> modules = discoverModules(request, diagnostics);
            failIfDiagnostics(request, startedAt, moduleReports, violations, diagnostics,
                    stdout, warnings, stderr, null);

            for (Path moduleFile : modules) {
                EvlModuleReport moduleReport = validateModule(
                        moduleFile, request, stdout, warnings, stderr);
                moduleReports.add(moduleReport);
                violations.addAll(moduleReport.violations());
                diagnostics.addAll(moduleReport.diagnostics());
            }

            if (diagnostics.stream().anyMatch(d -> d.severity() == ValidationSeverity.ERROR)) {
                throw failure("EVL validation failed.", request, startedAt, moduleReports,
                        violations, diagnostics, stdout, warnings, stderr, null);
            }

            return report(EvlValidationStatus.SUCCEEDED, request, startedAt, moduleReports,
                    violations, diagnostics, stdout, warnings, stderr);
        } catch (EvlValidationException ex) {
            throw ex;
        } catch (Exception ex) {
            diagnostics.add(EvlDiagnostic.error(
                    ValidationPhase.UNEXPECTED,
                    request.evlRoot(),
                    -1,
                    -1,
                    ex.getMessage(),
                    "An unexpected exception interrupted EVL validation.",
                    "Inspect the exception type and stack trace, then verify EVL/model/metamodel compatibility.",
                    ex));
            throw failure("EVL validation failed unexpectedly.", request, startedAt, moduleReports,
                    violations, diagnostics, stdout, warnings, stderr, ex);
        }
    }

    private EvlModuleReport validateModule(
            Path moduleFile,
            EvlValidationRequest request,
            BoundedByteArrayOutputStream stdout,
            BoundedByteArrayOutputStream warnings,
            BoundedByteArrayOutputStream stderr) throws EvlValidationException {
        Instant startedAt = Instant.now();
        List<EvlDiagnostic> diagnostics = new ArrayList<>();
        List<EvlConstraintViolation> violations = new ArrayList<>();
        List<IModel> loadedModels = new ArrayList<>();
        EvlModule module = new EvlModule();

        try {
            boolean parsed = parseModule(moduleFile, module, diagnostics);
            if (!parsed || diagnostics.stream()
                    .anyMatch(d -> d.severity() == ValidationSeverity.ERROR)) {
                return moduleReport(moduleFile, startedAt, violations, diagnostics);
            }

            configureStreams(module, request.captureOutput(), stdout, warnings, stderr);
            for (EvlModelConfiguration modelConfiguration : request.models()) {
                IModel model = modelConfiguration.load();
                loadedModels.add(model);
                diagnostics.addAll(modelConfiguration.validateLoadedModel(model));
                module.getContext().getModelRepository().addModel(model);
            }
            if (diagnostics.stream().anyMatch(d -> d.severity() == ValidationSeverity.ERROR)) {
                return moduleReport(moduleFile, startedAt, violations, diagnostics);
            }

            Set<UnsatisfiedConstraint> unsatisfiedConstraints = executeModule(module);
            for (UnsatisfiedConstraint unsatisfiedConstraint : unsatisfiedConstraints) {
                violations.add(toViolation(unsatisfiedConstraint));
            }
            return moduleReport(moduleFile, startedAt, violations, diagnostics);
        } catch (EolModelLoadingException ex) {
            diagnostics.add(EvlDiagnostic.error(
                    ValidationPhase.MODEL_LOADING,
                    moduleFile,
                    -1,
                    -1,
                    ex.getMessage(),
                    "An EMF model or metamodel could not be loaded.",
                    "Check model paths, metamodel paths, aliases, namespace URIs, and Ecore availability.",
                    ex));
            return moduleReport(moduleFile, startedAt, violations, diagnostics);
        } catch (EolRuntimeException ex) {
            diagnostics.add(runtimeDiagnostic(ex, moduleFile));
            return moduleReport(moduleFile, startedAt, violations, diagnostics);
        } catch (EpsilonExecutionTimeoutException ex) {
            diagnostics.add(EvlDiagnostic.error(
                    ValidationPhase.EXECUTION,
                    moduleFile,
                    -1,
                    -1,
                    ex.getMessage(),
                    "The EVL module exceeded the configured execution timeout.",
                    "Reduce model size, inspect EVL rules for non-terminating logic, or increase the timeout deliberately.",
                    ex));
            return moduleReport(moduleFile, startedAt, violations, diagnostics);
        } catch (Exception ex) {
            diagnostics.add(EvlDiagnostic.error(
                    ValidationPhase.UNEXPECTED,
                    moduleFile,
                    -1,
                    -1,
                    ex.getMessage(),
                    "An unexpected exception interrupted this EVL module.",
                    "Inspect the exception type and stack trace, then verify the EVL module and model aliases.",
                    ex));
            return moduleReport(moduleFile, startedAt, violations, diagnostics);
        } finally {
            for (IModel model : loadedModels) {
                try {
                    model.dispose();
                } catch (RuntimeException ignored) {
                    // Disposal should not hide the validation result.
                }
            }
            module.getContext().dispose();
        }
    }

    private void validateRequest(EvlValidationRequest request, List<EvlDiagnostic> diagnostics) {
        if (!Files.exists(request.evlRoot())) {
            diagnostics.add(EvlDiagnostic.error(
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
            diagnostics.add(EvlDiagnostic.error(
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

    private void validateFileModel(
            FileEvlModelConfiguration model, List<EvlDiagnostic> diagnostics) {
        if (!Files.isRegularFile(model.modelFile())) {
            diagnostics.add(EvlDiagnostic.error(
                    ValidationPhase.REQUEST_VALIDATION,
                    model.modelFile(),
                    -1,
                    -1,
                    "Model file for '" + model.name() + "' does not exist.",
                    "The file-backed EMF model cannot be loaded.",
                    "Provide an existing model file or use ResourceEvlModelConfiguration for in-memory models.",
                    null));
        }
        for (Path metamodel : model.metamodelFiles()) {
            if (!Files.isRegularFile(metamodel)) {
                diagnostics.add(EvlDiagnostic.error(
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

    private List<Path> discoverModules(
            EvlValidationRequest request, List<EvlDiagnostic> diagnostics) {
        if (!request.moduleFiles().isEmpty()) {
            List<Path> modules = request.moduleFiles().stream()
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
            List<Path> modules = entries
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".evl"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .toList();
            if (modules.isEmpty()) {
                diagnostics.add(EvlDiagnostic.error(
                        ValidationPhase.MODULE_DISCOVERY,
                        request.evlRoot(),
                        -1,
                        -1,
                        "No entry EVL modules were found.",
                        "The directory root does not contain any direct .evl files.",
                        "Point evlRoot at an .evl file or place/import an entry .evl module directly under the root directory.",
                        null));
            }
            return modules;
        } catch (IOException ex) {
            diagnostics.add(EvlDiagnostic.error(
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

    private boolean parseModule(
            Path moduleFile, EvlModule module, List<EvlDiagnostic> diagnostics) {
        boolean parsed;
        try {
            parsed = module.parse(moduleFile.toFile());
        } catch (Exception ex) {
            diagnostics.add(EvlDiagnostic.error(
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
            diagnostics.add(new EvlDiagnostic(
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

    private EvlElementReference elementReference(Object instance) {
        if (instance instanceof EObject eObject) {
            Resource resource = eObject.eResource();
            String uri = resource == null || resource.getURI() == null
                    ? ""
                    : resource.getURI().toString();
            String fragment = resource == null ? "" : resource.getURIFragment(eObject);
            Map<String, String> attributes = new LinkedHashMap<>();
            for (EAttribute attribute : eObject.eClass().getEAllAttributes()) {
                if (!attribute.isMany() && eObject.eIsSet(attribute)
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

    private boolean isSafeDiagnosticAttribute(EAttribute attribute) {
        String name = attribute.getName() == null ? ""
                : attribute.getName().toLowerCase(Locale.ROOT);
        if (isSensitiveAttributeName(name)) {
            return false;
        }
        return attribute.isID() || SAFE_DIAGNOSTIC_ATTRIBUTES.contains(name);
    }

    private boolean isSensitiveAttributeName(String name) {
        return name.contains("secret")
                || name.contains("password")
                || name.contains("token")
                || name.contains("key")
                || name.contains("value")
                || name.contains("email");
    }

    private String safeDiagnosticValue(Object value) {
        if (value == null) {
            return "";
        }
        String text = value.toString();
        return text.length() <= 256 ? text : text.substring(0, 256) + "...";
    }

    private List<EvlFixSuggestion> fixSuggestions(List<FixInstance> fixes) {
        if (fixes == null || fixes.isEmpty()) {
            return List.of();
        }
        List<EvlFixSuggestion> suggestions = new ArrayList<>();
        for (FixInstance fix : fixes) {
            try {
                suggestions.add(new EvlFixSuggestion(fix.getTitle()));
            } catch (EolRuntimeException ex) {
                suggestions.add(new EvlFixSuggestion("Fix title could not be evaluated: "
                        + firstNonBlank(ex.getReason(), ex.getMessage())));
            }
        }
        return suggestions;
    }

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

    private Set<UnsatisfiedConstraint> executeModule(EvlModule module) throws Exception {
        if (executionTimeout == null) {
            return module.execute();
        }
        Thread executingThread = Thread.currentThread();
        AtomicBoolean timedOut = new AtomicBoolean(false);
        ScheduledExecutorService watchdog = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "epsilon-evl-watchdog");
            thread.setDaemon(true);
            return thread;
        });
        ScheduledFuture<?> timeout = watchdog.schedule(() -> {
            timedOut.set(true);
            executingThread.interrupt();
        }, executionTimeout.toMillis(), TimeUnit.MILLISECONDS);
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

    private EpsilonExecutionTimeoutException timeoutException() {
        return new EpsilonExecutionTimeoutException(
                "EVL execution timed out after " + executionTimeout + ".", null);
    }

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
        module.getContext()
                .setWarningStream(new PrintStream(warnings, true, StandardCharsets.UTF_8));
        module.getContext().setErrorStream(new PrintStream(stderr, true, StandardCharsets.UTF_8));
    }

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
                "Inspect the reported file, line, and column. Check model aliases, type names, feature names, helper imports, and null-safety.",
                ex);
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second == null ? "" : second;
    }

    private EvlModuleReport moduleReport(
            Path moduleFile,
            Instant startedAt,
            List<EvlConstraintViolation> violations,
            List<EvlDiagnostic> diagnostics) {
        Instant finishedAt = Instant.now();
        return new EvlModuleReport(
                moduleFile,
                Duration.between(startedAt, finishedAt),
                violations,
                diagnostics);
    }

    private void failIfDiagnostics(
            EvlValidationRequest request,
            Instant startedAt,
            List<EvlModuleReport> moduleReports,
            List<EvlConstraintViolation> violations,
            List<EvlDiagnostic> diagnostics,
            BoundedByteArrayOutputStream stdout,
            BoundedByteArrayOutputStream warnings,
            BoundedByteArrayOutputStream stderr,
            Throwable cause) throws EvlValidationException {
        if (diagnostics.stream().anyMatch(d -> d.severity() == ValidationSeverity.ERROR)) {
            throw failure("EVL validation request is invalid.", request, startedAt, moduleReports,
                    violations, diagnostics, stdout, warnings, stderr, cause);
        }
    }

    private EvlValidationException failure(
            String message,
            EvlValidationRequest request,
            Instant startedAt,
            List<EvlModuleReport> moduleReports,
            List<EvlConstraintViolation> violations,
            List<EvlDiagnostic> diagnostics,
            BoundedByteArrayOutputStream stdout,
            BoundedByteArrayOutputStream warnings,
            BoundedByteArrayOutputStream stderr,
            Throwable cause) {
        return new EvlValidationException(
                message,
                report(EvlValidationStatus.FAILED, request, startedAt, moduleReports,
                        violations, diagnostics, stdout, warnings, stderr),
                cause);
    }

    private EvlValidationReport report(
            EvlValidationStatus status,
            EvlValidationRequest request,
            Instant startedAt,
            List<EvlModuleReport> moduleReports,
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
                moduleReports,
                violations,
                diagnostics,
                stdout.asUtf8String(),
                warnings.asUtf8String(),
                stderr.asUtf8String());
    }

    private static final class EpsilonExecutionTimeoutException extends RuntimeException {

        private EpsilonExecutionTimeoutException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
