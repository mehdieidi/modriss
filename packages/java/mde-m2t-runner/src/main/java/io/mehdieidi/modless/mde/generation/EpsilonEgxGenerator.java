package io.mehdieidi.modless.mde.generation;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Stream;
import org.eclipse.epsilon.common.module.ModuleElement;
import org.eclipse.epsilon.common.parse.problem.ParseProblem;
import org.eclipse.epsilon.common.util.StringProperties;
import org.eclipse.epsilon.egl.EgxModule;
import org.eclipse.epsilon.emc.emf.EmfModel;
import org.eclipse.epsilon.eol.exceptions.EolRuntimeException;
import org.eclipse.epsilon.eol.exceptions.models.EolModelLoadingException;
import org.eclipse.epsilon.eol.models.IModel;
import org.eclipse.epsilon.eol.models.Model;

public final class EpsilonEgxGenerator {

    public EgxGenerationReport generate(EgxGenerationRequest request)
            throws EgxGenerationException {
        Instant startedAt = Instant.now();
        List<GenerationDiagnostic> diagnostics = new ArrayList<>();
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream warnings = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        List<IModel> loadedModels = new ArrayList<>();
        EgxModule module = null;

        try {
            validateRequest(request, diagnostics);
            failIfDiagnostics(request, startedAt, diagnostics, stdout, warnings, stderr, null);
            Files.createDirectories(request.outputDirectory());

            PreludeInjectingTemplateFactory factory = new PreludeInjectingTemplateFactory(
                    request.outputDirectory(), request.moduleFile().toAbsolutePath().getParent()
                    .resolve("lib"));
            factory.setTemplateRoot(request.templateRoot().toAbsolutePath().toUri().toString());
            module = new EgxModule(factory);

            boolean parsed = parseModule(request, module, diagnostics);
            if (!parsed || diagnostics.stream()
                    .anyMatch(d -> d.severity() == GenerationSeverity.ERROR)) {
                throw failure("EGX module could not be parsed.", request, startedAt, diagnostics,
                        stdout, warnings, stderr, null);
            }
            factory.copyState(module.getContext());

            configureStreams(module, request.captureOutput(), stdout, warnings, stderr);
            for (GenerationModelConfiguration modelConfiguration : request.models()) {
                IModel model = loadModel(modelConfiguration);
                loadedModels.add(model);
                module.getContext().getModelRepository().addModel(model);
            }

            module.execute();
            finalizeGeneratedTraceFiles(request);
            return report(GenerationStatus.SUCCEEDED, request, startedAt, diagnostics, stdout,
                    warnings, stderr);
        } catch (EgxGenerationException ex) {
            throw ex;
        } catch (EolModelLoadingException ex) {
            diagnostics.add(GenerationDiagnostic.error(
                    GenerationPhase.MODEL_LOADING,
                    request.moduleFile(),
                    -1,
                    -1,
                    ex.getMessage(),
                    "An EMF model or metamodel could not be loaded for EGX generation.",
                    "Check model paths, metamodel paths, namespace aliases, and referenced Ecore files.",
                    ex));
            throw failure("EGX model loading failed.", request, startedAt, diagnostics, stdout,
                    warnings, stderr, ex);
        } catch (EolRuntimeException ex) {
            diagnostics.add(runtimeDiagnostic(ex, request.moduleFile()));
            throw failure("EGX generation failed.", request, startedAt, diagnostics, stdout,
                    warnings, stderr, ex);
        } catch (Exception ex) {
            diagnostics.add(GenerationDiagnostic.error(
                    GenerationPhase.UNEXPECTED,
                    request.moduleFile(),
                    -1,
                    -1,
                    ex.getMessage(),
                    "An unexpected exception interrupted EGX generation.",
                    "Inspect the exception type, stack trace, EGX module, templates, and model/metamodel compatibility.",
                    ex));
            throw failure("EGX generation failed unexpectedly.", request, startedAt, diagnostics,
                    stdout, warnings, stderr, ex);
        } finally {
            for (IModel model : loadedModels) {
                try {
                    model.dispose();
                } catch (RuntimeException ignored) {
                    // Disposal should not hide the primary generation failure.
                }
            }
            if (module != null) {
                module.getContext().dispose();
            }
        }
    }

    private void validateRequest(EgxGenerationRequest request,
            List<GenerationDiagnostic> diagnostics)
            throws Exception {
        if (!Files.isRegularFile(request.moduleFile())) {
            diagnostics.add(GenerationDiagnostic.error(
                    GenerationPhase.VALIDATION,
                    request.moduleFile(),
                    -1,
                    -1,
                    "EGX module does not exist.",
                    "The requested generation entry point cannot be found.",
                    "Provide an existing .egx file.",
                    null));
        }
        if (!Files.isDirectory(request.templateRoot())) {
            diagnostics.add(GenerationDiagnostic.error(
                    GenerationPhase.VALIDATION,
                    request.templateRoot(),
                    -1,
                    -1,
                    "EGL template root does not exist.",
                    "The EGX module cannot resolve template declarations.",
                    "Provide the directory that contains the EGL templates.",
                    null));
        }
        if (request.models().isEmpty()) {
            diagnostics.add(GenerationDiagnostic.error(
                    GenerationPhase.VALIDATION,
                    request.moduleFile(),
                    -1,
                    -1,
                    "At least one model configuration is required.",
                    "EGX has no source model to generate from.",
                    "Configure an EMF model before executing generation.",
                    null));
        }
        if (Files.exists(request.outputDirectory())
                && request.failIfOutputDirectoryIsNotEmpty()
                && isNotEmpty(request.outputDirectory())) {
            diagnostics.add(GenerationDiagnostic.error(
                    GenerationPhase.VALIDATION,
                    request.outputDirectory(),
                    -1,
                    -1,
                    "Output directory is not empty.",
                    "Generation was configured to avoid writing into an existing project tree.",
                    "Choose an empty output directory or disable fail-if-not-empty mode.",
                    null));
        }
        for (GenerationModelConfiguration model : request.models()) {
            if (!Files.isRegularFile(model.modelFile())) {
                diagnostics.add(GenerationDiagnostic.error(
                        GenerationPhase.VALIDATION,
                        model.modelFile(),
                        -1,
                        -1,
                        "Model file for '" + model.name() + "' does not exist.",
                        "The source model cannot be loaded.",
                        "Provide an existing EMF/XMI model file.",
                        null));
            }
            for (Path metamodel : model.metamodelFiles()) {
                if (!Files.isRegularFile(metamodel)) {
                    diagnostics.add(GenerationDiagnostic.error(
                            GenerationPhase.VALIDATION,
                            metamodel,
                            -1,
                            -1,
                            "Metamodel file for '" + model.name() + "' does not exist.",
                            "The EMF model cannot be typed without all referenced Ecore metamodels.",
                            "Generate or provide the missing .ecore file.",
                            null));
                }
            }
        }
    }

    private boolean isNotEmpty(Path directory) throws Exception {
        try (Stream<Path> children = Files.list(directory)) {
            return children.findAny().isPresent();
        }
    }

    private boolean parseModule(
            EgxGenerationRequest request, EgxModule module, List<GenerationDiagnostic> diagnostics)
            throws Exception {
        boolean parsed;
        try {
            parsed = module.parse(request.moduleFile().toFile());
        } catch (Exception ex) {
            diagnostics.add(GenerationDiagnostic.error(
                    GenerationPhase.PARSE,
                    request.moduleFile(),
                    -1,
                    -1,
                    ex.getMessage(),
                    "The EGX parser could not build the module AST.",
                    "Open the EGX file and imported modules, fix syntax/import errors, and retry.",
                    ex));
            return false;
        }
        for (ParseProblem problem : module.getParseProblems()) {
            diagnostics.add(new GenerationDiagnostic(
                    problem.getSeverity() == ParseProblem.WARNING ? GenerationSeverity.WARNING
                            : GenerationSeverity.ERROR,
                    GenerationPhase.PARSE,
                    request.moduleFile(),
                    problem.getLine(),
                    problem.getColumn(),
                    problem.getReason(),
                    "The EGX module or one of its imports has a parse problem.",
                    "Fix the reported EGX/EOL syntax or import path near the reported location.",
                    ParseProblem.class.getName()));
        }
        return parsed;
    }

    private IModel loadModel(GenerationModelConfiguration modelConfiguration)
            throws EolModelLoadingException {
        EmfModel model = new EmfModel();
        StringProperties properties = new StringProperties();
        properties.put(Model.PROPERTY_NAME, modelConfiguration.name());
        if (!modelConfiguration.aliases().isEmpty()) {
            properties.put(Model.PROPERTY_ALIASES, String.join(",", modelConfiguration.aliases()));
        }
        properties.put(Model.PROPERTY_READONLOAD, "true");
        properties.put(Model.PROPERTY_STOREONDISPOSAL, "false");
        properties.put(Model.PROPERTY_READONLY, "true");
        properties.put(EmfModel.PROPERTY_IS_METAMODEL_FILE_BASED, "true");
        properties.put(EmfModel.PROPERTY_MODEL_FILE,
                modelConfiguration.modelFile().toAbsolutePath().toString());
        properties.put(EmfModel.PROPERTY_METAMODEL_FILE,
                joinPaths(modelConfiguration.metamodelFiles()));
        properties.put(EmfModel.PROPERTY_VALIDATE, Boolean.toString(modelConfiguration.validate()));
        model.load(properties);
        return model;
    }

    private String joinPaths(List<Path> paths) {
        return paths.stream()
                .map(path -> path.toAbsolutePath().toString())
                .reduce((left, right) -> left + "," + right)
                .orElse("");
    }

    private void configureStreams(
            EgxModule module,
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

    private GenerationDiagnostic runtimeDiagnostic(EolRuntimeException ex, Path fallbackFile) {
        ModuleElement ast = ex.getAst();
        Path file = fallbackFile;
        if (ast != null && ast.getFile() != null) {
            file = ast.getFile().toPath();
        }
        return GenerationDiagnostic.error(
                GenerationPhase.EXECUTION,
                file,
                ex.getLine(),
                ex.getColumn(),
                firstNonBlank(ex.getReason(), ex.getMessage()),
                "The EGX/EGL generation failed while executing a statement.",
                "Inspect the reported file, line, and column. Check model aliases, metamodel features, template parameters, and target expressions.",
                ex);
    }

    private void finalizeGeneratedTraceFiles(EgxGenerationRequest request) throws Exception {
        Path artifactTrace = request.outputDirectory()
                .resolve("generated/trace/artifact-trace.json");
        if (!Files.isRegularFile(artifactTrace)) {
            return;
        }

        List<String> updatedLines = new ArrayList<>();
        String currentPath = null;
        for (String line : Files.readAllLines(artifactTrace, StandardCharsets.UTF_8)) {
            String trimmed = line.trim();
            if (trimmed.startsWith("\"sourceModelHash\"")) {
                updatedLines.add(line.replace("COMPUTED_BY_EXECUTOR",
                        sha256(request.models().get(0).modelFile())));
                continue;
            }
            if (trimmed.startsWith("\"path\"")) {
                currentPath = jsonStringValue(trimmed);
                updatedLines.add(line);
                continue;
            }
            if (line.contains("COMPUTED_AFTER_WRITE") && currentPath != null) {
                Path artifact = request.outputDirectory().resolve(currentPath).normalize();
                String checksum = Files.isRegularFile(artifact) ? sha256(artifact) : "MISSING";
                updatedLines.add(line.replace("COMPUTED_AFTER_WRITE", checksum));
                continue;
            }
            updatedLines.add(line);
        }
        Files.write(artifactTrace, updatedLines, StandardCharsets.UTF_8);
    }

    private String jsonStringValue(String line) {
        int colon = line.indexOf(':');
        int start = line.indexOf('"', colon + 1);
        int end = line.indexOf('"', start + 1);
        if (colon < 0 || start < 0 || end < 0) {
            return "";
        }
        return line.substring(start + 1, end)
                .replace("\\/", "/")
                .replace("\\\"", "\"")
                .replace("\\\\", "\\");
    }

    private String sha256(Path path) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(digest.digest(Files.readAllBytes(path)));
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second == null ? "" : second;
    }

    private void failIfDiagnostics(
            EgxGenerationRequest request,
            Instant startedAt,
            List<GenerationDiagnostic> diagnostics,
            ByteArrayOutputStream stdout,
            ByteArrayOutputStream warnings,
            ByteArrayOutputStream stderr,
            Throwable cause) throws EgxGenerationException {
        if (diagnostics.stream().anyMatch(d -> d.severity() == GenerationSeverity.ERROR)) {
            throw failure("EGX generation request is invalid.", request, startedAt, diagnostics,
                    stdout, warnings, stderr, cause);
        }
    }

    private EgxGenerationException failure(
            String message,
            EgxGenerationRequest request,
            Instant startedAt,
            List<GenerationDiagnostic> diagnostics,
            ByteArrayOutputStream stdout,
            ByteArrayOutputStream warnings,
            ByteArrayOutputStream stderr,
            Throwable cause) {
        return new EgxGenerationException(
                message,
                report(GenerationStatus.FAILED, request, startedAt, diagnostics, stdout, warnings,
                        stderr),
                cause);
    }

    private EgxGenerationReport report(
            GenerationStatus status,
            EgxGenerationRequest request,
            Instant startedAt,
            List<GenerationDiagnostic> diagnostics,
            ByteArrayOutputStream stdout,
            ByteArrayOutputStream warnings,
            ByteArrayOutputStream stderr) {
        Instant finishedAt = Instant.now();
        return new EgxGenerationReport(
                status,
                request.moduleFile(),
                request.outputDirectory(),
                startedAt,
                finishedAt,
                Duration.between(startedAt, finishedAt),
                diagnostics,
                listGeneratedFiles(request.outputDirectory()),
                stdout.toString(StandardCharsets.UTF_8),
                warnings.toString(StandardCharsets.UTF_8),
                stderr.toString(StandardCharsets.UTF_8));
    }

    private List<Path> listGeneratedFiles(Path outputDirectory) {
        if (!Files.isDirectory(outputDirectory)) {
            return List.of();
        }
        try (Stream<Path> files = Files.walk(outputDirectory)) {
            return files.filter(Files::isRegularFile)
                    .map(outputDirectory.toAbsolutePath().normalize()::relativize)
                    .sorted()
                    .toList();
        } catch (Exception ignored) {
            return List.of();
        }
    }
}
