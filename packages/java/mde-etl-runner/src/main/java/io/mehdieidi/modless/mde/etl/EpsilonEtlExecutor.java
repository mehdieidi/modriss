package io.mehdieidi.modless.mde.etl;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.eclipse.emf.common.util.URI;
import org.eclipse.epsilon.common.module.ModuleElement;
import org.eclipse.epsilon.common.parse.problem.ParseProblem;
import org.eclipse.epsilon.common.util.StringProperties;
import org.eclipse.epsilon.emc.emf.EmfModel;
import org.eclipse.epsilon.eol.exceptions.EolRuntimeException;
import org.eclipse.epsilon.eol.exceptions.models.EolModelLoadingException;
import org.eclipse.epsilon.eol.models.IModel;
import org.eclipse.epsilon.eol.models.Model;
import org.eclipse.epsilon.etl.EtlModule;

public final class EpsilonEtlExecutor {

    public EtlExecutionReport execute(EtlExecutionRequest request) throws EtlExecutionException {
        Instant startedAt = Instant.now();
        List<EtlDiagnostic> diagnostics = new ArrayList<>();
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream warnings = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        EtlModule module = new EtlModule();
        List<IModel> loadedModels = new ArrayList<>();

        try {
            validateRequest(request, diagnostics);
            failIfDiagnostics(request, startedAt, diagnostics, stdout, warnings, stderr, null);

            prepareOutputs(request);

            boolean parsed = parseModule(request, module, diagnostics);
            if (!parsed || diagnostics.stream()
                    .anyMatch(d -> d.severity() == DiagnosticSeverity.ERROR)) {
                throw failure("ETL module could not be parsed.", request, startedAt, diagnostics,
                        stdout, warnings, stderr, null);
            }

            configureStreams(module, request.captureOutput(), stdout, warnings, stderr);
            for (EtlModelConfiguration modelConfiguration : request.models()) {
                IModel model = loadModel(modelConfiguration);
                loadedModels.add(model);
                module.getContext().getModelRepository().addModel(model);
            }

            module.execute();
            storeModels(request, loadedModels, diagnostics);
            return report(
                    EtlExecutionStatus.SUCCEEDED,
                    request,
                    startedAt,
                    diagnostics,
                    stdout,
                    warnings,
                    stderr);
        } catch (EtlExecutionException ex) {
            throw ex;
        } catch (EolModelLoadingException ex) {
            diagnostics.add(EtlDiagnostic.error(
                    ExecutionPhase.MODEL_LOADING,
                    request.moduleFile(),
                    -1,
                    -1,
                    ex.getMessage(),
                    "An EMF model or metamodel could not be loaded.",
                    "Check model paths, metamodel paths, namespace aliases, and that referenced Ecore files exist.",
                    ex));
            throw failure("ETL model loading failed.", request, startedAt, diagnostics, stdout,
                    warnings, stderr, ex);
        } catch (EolRuntimeException ex) {
            diagnostics.add(runtimeDiagnostic(ex, request.moduleFile()));
            throw failure("ETL execution failed.", request, startedAt, diagnostics, stdout,
                    warnings, stderr, ex);
        } catch (Exception ex) {
            diagnostics.add(EtlDiagnostic.error(
                    ExecutionPhase.UNEXPECTED,
                    request.moduleFile(),
                    -1,
                    -1,
                    ex.getMessage(),
                    "An unexpected exception interrupted ETL execution.",
                    "Inspect the exception type and stack trace, then verify module/model/metamodel compatibility.",
                    ex));
            throw failure("ETL execution failed unexpectedly.", request, startedAt, diagnostics,
                    stdout, warnings, stderr, ex);
        } finally {
            for (IModel model : loadedModels) {
                try {
                    model.dispose();
                } catch (RuntimeException ignored) {
                    // Disposal should not mask the primary execution diagnostic.
                }
            }
            module.getContext().dispose();
        }
    }

    private boolean parseModule(
            EtlExecutionRequest request, EtlModule module, List<EtlDiagnostic> diagnostics)
            throws Exception {
        boolean parsed;
        try {
            parsed = module.parse(request.moduleFile().toFile());
        } catch (Exception ex) {
            diagnostics.add(EtlDiagnostic.error(
                    ExecutionPhase.PARSE,
                    request.moduleFile(),
                    -1,
                    -1,
                    ex.getMessage(),
                    "The ETL parser could not build the module AST.",
                    "Open the ETL file and imported modules, fix syntax/import errors, and retry.",
                    ex));
            return false;
        }
        for (ParseProblem problem : module.getParseProblems()) {
            diagnostics.add(new EtlDiagnostic(
                    problem.getSeverity() == ParseProblem.WARNING ? DiagnosticSeverity.WARNING
                            : DiagnosticSeverity.ERROR,
                    ExecutionPhase.PARSE,
                    request.moduleFile(),
                    problem.getLine(),
                    problem.getColumn(),
                    problem.getReason(),
                    "The ETL module or one of its imports has a parse problem.",
                    "Fix the reported ETL/EOL syntax or import path near the reported location.",
                    ParseProblem.class.getName()));
        }
        return parsed;
    }

    private void validateRequest(EtlExecutionRequest request, List<EtlDiagnostic> diagnostics) {
        if (!Files.isRegularFile(request.moduleFile())) {
            diagnostics.add(EtlDiagnostic.error(
                    ExecutionPhase.VALIDATION,
                    request.moduleFile(),
                    -1,
                    -1,
                    "ETL module does not exist.",
                    "The requested transformation entry point cannot be found.",
                    "Provide an existing .etl file.",
                    null));
        }
        if (request.models().isEmpty()) {
            diagnostics.add(EtlDiagnostic.error(
                    ExecutionPhase.VALIDATION,
                    request.moduleFile(),
                    -1,
                    -1,
                    "At least one model configuration is required.",
                    "ETL has no source or target model to operate on.",
                    "Configure source/target EMF models before executing.",
                    null));
        }
        for (EtlModelConfiguration model : request.models()) {
            if (model.readOnLoad() && !Files.isRegularFile(model.modelFile())) {
                diagnostics.add(EtlDiagnostic.error(
                        ExecutionPhase.VALIDATION,
                        model.modelFile(),
                        -1,
                        -1,
                        "Model file for '" + model.name() + "' does not exist.",
                        "The model is configured to read on load, but the file is missing.",
                        "Create the model file or set readOnLoad=false for a new target model.",
                        null));
            }
            for (Path metamodel : model.metamodelFiles()) {
                if (!Files.isRegularFile(metamodel)) {
                    diagnostics.add(EtlDiagnostic.error(
                            ExecutionPhase.VALIDATION,
                            metamodel,
                            -1,
                            -1,
                            "Metamodel file for '" + model.name() + "' does not exist.",
                            "The EMF model cannot be typed without all referenced Ecore metamodels.",
                            "Generate or provide the missing .ecore file.",
                            null));
                }
            }
            if (!model.readOnly() && Files.exists(model.modelFile()) && !request.overwriteOutputs()
                    && !model.readOnLoad()) {
                diagnostics.add(EtlDiagnostic.error(
                        ExecutionPhase.VALIDATION,
                        model.modelFile(),
                        -1,
                        -1,
                        "Target model already exists and overwrite is disabled.",
                        "Writing would replace an existing model.",
                        "Use overwrite=true or choose a different target path.",
                        null));
            }
        }
    }

    private void prepareOutputs(EtlExecutionRequest request) throws Exception {
        for (EtlModelConfiguration model : request.models()) {
            if (model.readOnly()) {
                continue;
            }
            Path parent = model.modelFile().toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            if (Files.exists(model.modelFile()) && request.overwriteOutputs()
                    && !model.readOnLoad()) {
                Files.delete(model.modelFile());
            }
        }
    }

    private IModel loadModel(EtlModelConfiguration modelConfiguration)
            throws EolModelLoadingException {
        EmfModel model = new EmfModel();
        StringProperties properties = new StringProperties();
        properties.put(Model.PROPERTY_NAME, modelConfiguration.name());
        if (!modelConfiguration.aliases().isEmpty()) {
            properties.put(Model.PROPERTY_ALIASES, String.join(",", modelConfiguration.aliases()));
        }
        properties.put(Model.PROPERTY_READONLOAD,
                Boolean.toString(modelConfiguration.readOnLoad()));
        properties.put(Model.PROPERTY_STOREONDISPOSAL,
                Boolean.toString(modelConfiguration.storeOnDisposal()));
        properties.put(Model.PROPERTY_READONLY, Boolean.toString(modelConfiguration.readOnly()));
        properties.put(EmfModel.PROPERTY_MODEL_URI, fileUri(modelConfiguration.modelFile()));
        properties.put(EmfModel.PROPERTY_FILE_BASED_METAMODEL_URI,
                joinFileUris(modelConfiguration.metamodelFiles()));
        properties.put(EmfModel.PROPERTY_VALIDATE, Boolean.toString(modelConfiguration.validate()));
        model.load(properties);
        return model;
    }

    private void storeModels(
            EtlExecutionRequest request, List<IModel> loadedModels, List<EtlDiagnostic> diagnostics)
            throws EtlExecutionException {
        for (IModel model : loadedModels) {
            if (!model.isStoredOnDisposal()) {
                continue;
            }
            try {
                if (!model.store()) {
                    diagnostics.add(EtlDiagnostic.error(
                            ExecutionPhase.MODEL_STORING,
                            request.moduleFile(),
                            -1,
                            -1,
                            "Model '" + model.getName()
                                    + "' reported an unsuccessful store operation.",
                            "The ETL transformation executed, but the target model was not persisted successfully.",
                            "Check the target path, file permissions, and whether the target model resource is writable.",
                            null));
                }
            } catch (RuntimeException ex) {
                diagnostics.add(EtlDiagnostic.error(
                        ExecutionPhase.MODEL_STORING,
                        request.moduleFile(),
                        -1,
                        -1,
                        ex.getMessage(),
                        "The ETL transformation executed, but model persistence failed.",
                        "Check the target path, file permissions, disk availability, and EMF resource serialization details.",
                        ex));
            }
        }
        if (diagnostics.stream().anyMatch(d -> d.severity() == DiagnosticSeverity.ERROR
                && d.phase() == ExecutionPhase.MODEL_STORING)) {
            throw new EtlExecutionException(
                    "ETL model storing failed.",
                    report(
                            EtlExecutionStatus.FAILED,
                            request,
                            Instant.now(),
                            diagnostics,
                            new ByteArrayOutputStream(),
                            new ByteArrayOutputStream(),
                            new ByteArrayOutputStream()));
        }
    }

    private String joinFileUris(List<Path> paths) {
        return paths.stream()
                .map(this::fileUri)
                .reduce((left, right) -> left + "," + right)
                .orElse("");
    }

    private String fileUri(Path path) {
        return URI.createFileURI(path.toAbsolutePath().normalize().toString()).toString();
    }

    private void configureStreams(
            EtlModule module,
            boolean captureOutput,
            ByteArrayOutputStream stdout,
            ByteArrayOutputStream warnings,
            ByteArrayOutputStream stderr) {
        if (!captureOutput) {
            return;
        }
        module.getContext().setOutputStream(new PrintStream(stdout, true, StandardCharsets.UTF_8));
        module.getContext()
                .setWarningStream(new PrintStream(warnings, true, StandardCharsets.UTF_8));
        module.getContext().setErrorStream(new PrintStream(stderr, true, StandardCharsets.UTF_8));
    }

    private EtlDiagnostic runtimeDiagnostic(EolRuntimeException ex, Path fallbackFile) {
        ModuleElement ast = ex.getAst();
        Path file = fallbackFile;
        if (ast != null && ast.getFile() != null) {
            file = ast.getFile().toPath();
        }
        return EtlDiagnostic.error(
                ExecutionPhase.EXECUTION,
                file,
                ex.getLine(),
                ex.getColumn(),
                firstNonBlank(ex.getReason(), ex.getMessage()),
                "The ETL module failed while executing a statement.",
                "Inspect the reported file, line, and column. Check model aliases, metamodel features, type names, and target cardinalities.",
                ex);
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second == null ? "" : second;
    }

    private void failIfDiagnostics(
            EtlExecutionRequest request,
            Instant startedAt,
            List<EtlDiagnostic> diagnostics,
            ByteArrayOutputStream stdout,
            ByteArrayOutputStream warnings,
            ByteArrayOutputStream stderr,
            Throwable cause) throws EtlExecutionException {
        if (diagnostics.stream().anyMatch(d -> d.severity() == DiagnosticSeverity.ERROR)) {
            throw failure("ETL request is invalid.", request, startedAt, diagnostics, stdout,
                    warnings, stderr, cause);
        }
    }

    private EtlExecutionException failure(
            String message,
            EtlExecutionRequest request,
            Instant startedAt,
            List<EtlDiagnostic> diagnostics,
            ByteArrayOutputStream stdout,
            ByteArrayOutputStream warnings,
            ByteArrayOutputStream stderr,
            Throwable cause) {
        return new EtlExecutionException(
                message,
                report(EtlExecutionStatus.FAILED, request, startedAt, diagnostics, stdout, warnings,
                        stderr),
                cause);
    }

    private EtlExecutionReport report(
            EtlExecutionStatus status,
            EtlExecutionRequest request,
            Instant startedAt,
            List<EtlDiagnostic> diagnostics,
            ByteArrayOutputStream stdout,
            ByteArrayOutputStream warnings,
            ByteArrayOutputStream stderr) {
        Instant finishedAt = Instant.now();
        return new EtlExecutionReport(
                status,
                request.moduleFile(),
                startedAt,
                finishedAt,
                Duration.between(startedAt, finishedAt),
                diagnostics,
                stdout.toString(StandardCharsets.UTF_8),
                warnings.toString(StandardCharsets.UTF_8),
                stderr.toString(StandardCharsets.UTF_8));
    }
}
