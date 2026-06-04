package io.mehdieidi.modless.mde.etl;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.eclipse.emf.common.util.URI;
import org.eclipse.epsilon.common.module.ModuleElement;
import org.eclipse.epsilon.common.parse.problem.ParseProblem;
import org.eclipse.epsilon.common.util.StringProperties;
import org.eclipse.epsilon.emc.emf.EmfModel;
import org.eclipse.epsilon.eol.exceptions.EolRuntimeException;
import org.eclipse.epsilon.eol.exceptions.models.EolModelLoadingException;
import org.eclipse.epsilon.eol.execute.context.Variable;
import org.eclipse.epsilon.eol.models.IModel;
import org.eclipse.epsilon.eol.models.Model;
import org.eclipse.epsilon.etl.EtlModule;

public final class EpsilonEtlExecutor {

    private static final Duration DEFAULT_EXECUTION_TIMEOUT = Duration.ofMinutes(5);
    private static final int DEFAULT_MAX_CAPTURED_OUTPUT_BYTES = 1024 * 1024;

    private final Duration executionTimeout;
    private final int maxCapturedOutputBytes;

    public EpsilonEtlExecutor() {
        this(null, DEFAULT_MAX_CAPTURED_OUTPUT_BYTES);
    }

    public EpsilonEtlExecutor(Duration executionTimeout, int maxCapturedOutputBytes) {
        this.executionTimeout = executionTimeout == null || executionTimeout.isZero()
                || executionTimeout.isNegative() ? null : executionTimeout;
        this.maxCapturedOutputBytes = maxCapturedOutputBytes <= 0
                ? DEFAULT_MAX_CAPTURED_OUTPUT_BYTES : maxCapturedOutputBytes;
    }

    public EtlExecutionReport execute(EtlExecutionRequest request) throws EtlExecutionException {
        Instant startedAt = Instant.now();
        long startedNanos = System.nanoTime();
        EtlPhaseTiming phaseTiming = new EtlPhaseTiming();
        List<EtlDiagnostic> diagnostics = new ArrayList<>();
        BoundedByteArrayOutputStream stdout = new BoundedByteArrayOutputStream(
                maxCapturedOutputBytes);
        BoundedByteArrayOutputStream warnings = new BoundedByteArrayOutputStream(
                maxCapturedOutputBytes);
        BoundedByteArrayOutputStream stderr = new BoundedByteArrayOutputStream(
                maxCapturedOutputBytes);
        EtlModule module = new EtlModule();
        List<LoadedEtlModel> loadedModels = new ArrayList<>();

        try {
            long phaseStarted = System.nanoTime();
            validateRequest(request, diagnostics);
            phaseTiming.addValidation(System.nanoTime() - phaseStarted);
            failIfDiagnostics(request, startedAt, phaseTiming, diagnostics, stdout, warnings,
                    stderr, null);

            phaseStarted = System.nanoTime();
            prepareOutputs(request);
            phaseTiming.addPrepareOutputs(System.nanoTime() - phaseStarted);

            phaseStarted = System.nanoTime();
            boolean parsed = parseModule(request, module, diagnostics);
            phaseTiming.addParse(System.nanoTime() - phaseStarted);
            if (!parsed || diagnostics.stream()
                    .anyMatch(d -> d.severity() == DiagnosticSeverity.ERROR)) {
                throw failure("ETL module could not be parsed.", request, startedAt, phaseTiming,
                        diagnostics, stdout, warnings, stderr, null);
            }

            configureStreams(module, request.captureOutput(), stdout, warnings, stderr);
            for (EtlModelConfiguration modelConfiguration : request.models()) {
                phaseStarted = System.nanoTime();
                IModel model = loadModel(modelConfiguration);
                phaseTiming.addModelLoad(modelConfiguration.readOnly(),
                        System.nanoTime() - phaseStarted);
                loadedModels.add(new LoadedEtlModel(modelConfiguration, model));
                module.getContext().getModelRepository().addModel(model);
            }

            configureTransformationState(module);
            phaseStarted = System.nanoTime();
            executeModule(module);
            phaseTiming.addExecute(System.nanoTime() - phaseStarted);
            phaseStarted = System.nanoTime();
            try {
                storeModels(request, startedAt, phaseTiming, loadedModels, diagnostics, stdout,
                        warnings, stderr);
            } finally {
                phaseTiming.addStore(System.nanoTime() - phaseStarted);
            }
            return report(
                    EtlExecutionStatus.SUCCEEDED,
                    request,
                    startedAt,
                    phaseTiming,
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
            throw failure("ETL model loading failed.", request, startedAt, phaseTiming, diagnostics,
                    stdout, warnings, stderr, ex);
        } catch (EolRuntimeException ex) {
            diagnostics.add(runtimeDiagnostic(ex, request.moduleFile()));
            throw failure("ETL execution failed.", request, startedAt, phaseTiming, diagnostics,
                    stdout, warnings, stderr, ex);
        } catch (EpsilonExecutionTimeoutException ex) {
            diagnostics.add(EtlDiagnostic.error(
                    ExecutionPhase.EXECUTION,
                    request.moduleFile(),
                    -1,
                    -1,
                    ex.getMessage(),
                    "The ETL module exceeded the configured execution timeout.",
                    "Reduce model size, inspect the ETL script for non-terminating logic, or increase the timeout deliberately.",
                    ex));
            throw failure("ETL execution timed out.", request, startedAt, phaseTiming, diagnostics,
                    stdout, warnings, stderr, ex);
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
            throw failure("ETL execution failed unexpectedly.", request, startedAt, phaseTiming,
                    diagnostics, stdout, warnings, stderr, ex);
        } finally {
            long disposeStarted = System.nanoTime();
            for (LoadedEtlModel loadedModel : loadedModels) {
                try {
                    loadedModel.model().dispose();
                } catch (RuntimeException ignored) {
                    // Disposal should not mask the primary execution diagnostic.
                }
            }
            module.getContext().dispose();
            phaseTiming.addDispose(System.nanoTime() - disposeStarted);
            phaseTiming.setTotal(System.nanoTime() - startedNanos);
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
        properties.put(EmfModel.PROPERTY_REUSE_UNMODIFIED_FILE_BASED_METAMODELS,
                Boolean.TRUE.toString());
        properties.put(EmfModel.PROPERTY_VALIDATE, Boolean.toString(modelConfiguration.validate()));
        model.load(properties);
        return model;
    }

    private void configureTransformationState(EtlModule module) {
        module.getContext().getFrameStack().putGlobal(
                new Variable("usedModelElementIds", new java.util.LinkedHashSet<>(),
                        org.eclipse.epsilon.eol.types.EolAnyType.Instance),
                new Variable("usedAwsLogicalIds", new java.util.LinkedHashSet<>(),
                        org.eclipse.epsilon.eol.types.EolAnyType.Instance),
                new Variable("cachedPimRoot", null,
                        org.eclipse.epsilon.eol.types.EolAnyType.Instance),
                new Variable("cachedCimRoot", null,
                        org.eclipse.epsilon.eol.types.EolAnyType.Instance),
                new Variable("cachedAwsRoot", null,
                        org.eclipse.epsilon.eol.types.EolAnyType.Instance),
                new Variable("cachedDefaultStack", null,
                        org.eclipse.epsilon.eol.types.EolAnyType.Instance),
                new Variable("cachedDefaultStage", null,
                        org.eclipse.epsilon.eol.types.EolAnyType.Instance),
                new Variable("cachedIsProductionRoot", false,
                        org.eclipse.epsilon.eol.types.EolAnyType.Instance),
                new Variable("cachedCimAggregateByEntityId", null,
                        org.eclipse.epsilon.eol.types.EolAnyType.Instance),
                new Variable("cachedCimContextByCapabilityId", null,
                        org.eclipse.epsilon.eol.types.EolAnyType.Instance),
                new Variable("cachedCimOwnerContextByElementId", null,
                        org.eclipse.epsilon.eol.types.EolAnyType.Instance),
                new Variable("cachedCimServiceBySourceId", null,
                        org.eclipse.epsilon.eol.types.EolAnyType.Instance),
                new Variable("cachedCimEquivalentByRule", null,
                        org.eclipse.epsilon.eol.types.EolAnyType.Instance),
                new Variable("cachedCimFallbackService", null,
                        org.eclipse.epsilon.eol.types.EolAnyType.Instance),
                new Variable("cachedCimPolicyTargetsBySourceId", null,
                        org.eclipse.epsilon.eol.types.EolAnyType.Instance),
                new Variable("cachedPimObservabilityById", null,
                        org.eclipse.epsilon.eol.types.EolAnyType.Instance),
                new Variable("cachedPimResilienceById", null,
                        org.eclipse.epsilon.eol.types.EolAnyType.Instance),
                new Variable("cachedPimIdempotencyById", null,
                        org.eclipse.epsilon.eol.types.EolAnyType.Instance),
                new Variable("cachedPimSchemaById", null,
                        org.eclipse.epsilon.eol.types.EolAnyType.Instance),
                new Variable("cachedPimDataAccessIds", null,
                        org.eclipse.epsilon.eol.types.EolAnyType.Instance),
                new Variable("cachedPimExternalFlowIds", null,
                        org.eclipse.epsilon.eol.types.EolAnyType.Instance),
                new Variable("cachedPimQueueById", null,
                        org.eclipse.epsilon.eol.types.EolAnyType.Instance),
                new Variable("cachedPimServiceOwnedKeys", null,
                        org.eclipse.epsilon.eol.types.EolAnyType.Instance),
                new Variable("cachedPimServiceMembershipKeys", null,
                        org.eclipse.epsilon.eol.types.EolAnyType.Instance),
                new Variable("cachedPimRetentionPolicyById", null,
                        org.eclipse.epsilon.eol.types.EolAnyType.Instance),
                new Variable("cachedPimBackupPolicyById", null,
                        org.eclipse.epsilon.eol.types.EolAnyType.Instance),
                new Variable("cachedPimSchemasByItemId", null,
                        org.eclipse.epsilon.eol.types.EolAnyType.Instance),
                new Variable("cachedReadinessManualDecisionsById", null,
                        org.eclipse.epsilon.eol.types.EolAnyType.Instance),
                new Variable("cachedReadinessFindingsById", null,
                        org.eclipse.epsilon.eol.types.EolAnyType.Instance),
                new Variable("cachedReadinessChecksById", null,
                        org.eclipse.epsilon.eol.types.EolAnyType.Instance),
                new Variable("cachedAwsTargetHadPreloadedContent", false,
                        org.eclipse.epsilon.eol.types.EolAnyType.Instance),
                new Variable("cachedAwsExistingTargetByKey", null,
                        org.eclipse.epsilon.eol.types.EolAnyType.Instance),
                new Variable("cachedPimServiceNameByElementId", null,
                        org.eclipse.epsilon.eol.types.EolAnyType.Instance),
                new Variable("cachedPimOwnerTeamByElementId", null,
                        org.eclipse.epsilon.eol.types.EolAnyType.Instance),
                new Variable("cachedPimDeploymentAssignedIds", null,
                        org.eclipse.epsilon.eol.types.EolAnyType.Instance),
                new Variable("cachedPimDeploymentUnitByElementId", null,
                        org.eclipse.epsilon.eol.types.EolAnyType.Instance),
                new Variable("cachedAwsEquivalentBySourceId", null,
                        org.eclipse.epsilon.eol.types.EolAnyType.Instance),
                new Variable("cachedAwsGeneratedResources", null,
                        org.eclipse.epsilon.eol.types.EolAnyType.Instance),
                new Variable("cachedAwsGeneratedResourceObjects", null,
                        org.eclipse.epsilon.eol.types.EolAnyType.Instance),
                new Variable("cachedGeneratedIdByKey", null,
                        org.eclipse.epsilon.eol.types.EolAnyType.Instance),
                new Variable("cachedNextUniqueIdIndexBySeed", null,
                        org.eclipse.epsilon.eol.types.EolAnyType.Instance),
                new Variable("cachedNextLogicalIdIndexBySeed", null,
                        org.eclipse.epsilon.eol.types.EolAnyType.Instance),
                new Variable("cachedDataAccessesByFunctionId", null,
                        org.eclipse.epsilon.eol.types.EolAnyType.Instance),
                new Variable("cachedStageStackLinks", null,
                        org.eclipse.epsilon.eol.types.EolAnyType.Instance),
                new Variable("cachedApiGatewayRoutesWithLambdaIntegration", null,
                        org.eclipse.epsilon.eol.types.EolAnyType.Instance),
                new Variable("cachedSqsLambdaMappings", null,
                        org.eclipse.epsilon.eol.types.EolAnyType.Instance),
                new Variable("cachedSnsLambdaSubscriptions", null,
                        org.eclipse.epsilon.eol.types.EolAnyType.Instance),
                new Variable("cachedEventBridgeRules", null,
                        org.eclipse.epsilon.eol.types.EolAnyType.Instance),
                new Variable("cachedAwsResolvingRelationships", false,
                        org.eclipse.epsilon.eol.types.EolAnyType.Instance),
                new Variable("cachedAwsAuthorizerByApiId", null,
                        org.eclipse.epsilon.eol.types.EolAnyType.Instance),
                new Variable("cachedLambdaPermissionByKey", null,
                        org.eclipse.epsilon.eol.types.EolAnyType.Instance),
                new Variable("cachedSqsLambdaMappingByKey", null,
                        org.eclipse.epsilon.eol.types.EolAnyType.Instance),
                new Variable("cachedSnsSubscriptionByKey", null,
                        org.eclipse.epsilon.eol.types.EolAnyType.Instance),
                new Variable("cachedTopicsByEventTypeId", null,
                        org.eclipse.epsilon.eol.types.EolAnyType.Instance),
                new Variable("cachedEventBusesByEventTypeId", null,
                        org.eclipse.epsilon.eol.types.EolAnyType.Instance),
                new Variable("cachedQueuesByEventTypeId", null,
                        org.eclipse.epsilon.eol.types.EolAnyType.Instance),
                new Variable("cachedAwsReadinessDecisionIds", null,
                        org.eclipse.epsilon.eol.types.EolAnyType.Instance),
                new Variable("cachedAwsReadinessFindingIds", null,
                        org.eclipse.epsilon.eol.types.EolAnyType.Instance),
                new Variable("cachedAwsReadinessCheckIds", null,
                        org.eclipse.epsilon.eol.types.EolAnyType.Instance),
                new Variable("cachedSlugByText", null,
                        org.eclipse.epsilon.eol.types.EolAnyType.Instance),
                new Variable("cachedCamelByText", null,
                        org.eclipse.epsilon.eol.types.EolAnyType.Instance),
                new Variable("cachedPascalByText", null,
                        org.eclipse.epsilon.eol.types.EolAnyType.Instance));
    }

    private void executeModule(EtlModule module) throws Exception {
        if (executionTimeout == null) {
            module.execute();
            return;
        }
        Thread executingThread = Thread.currentThread();
        AtomicBoolean timedOut = new AtomicBoolean(false);
        ScheduledExecutorService watchdog = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "epsilon-etl-watchdog");
            thread.setDaemon(true);
            return thread;
        });
        ScheduledFuture<?> timeout = watchdog.schedule(() -> {
            timedOut.set(true);
            executingThread.interrupt();
        }, executionTimeout.toMillis(), TimeUnit.MILLISECONDS);
        try {
            try {
                module.execute();
            } catch (Exception ex) {
                if (timedOut.get()) {
                    throw timeoutException();
                }
                throw ex;
            }
            throwIfTimedOut(timedOut);
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
                "ETL execution timed out after " + executionTimeout + ".", null);
    }

    private void throwIfTimedOut(AtomicBoolean timedOut) {
        if (timedOut.get()) {
            throw new EpsilonExecutionTimeoutException(
                    "ETL execution timed out after " + executionTimeout + ".", null);
        }
    }

    private void storeModels(
            EtlExecutionRequest request,
            Instant startedAt,
            EtlPhaseTiming phaseTiming,
            List<LoadedEtlModel> loadedModels,
            List<EtlDiagnostic> diagnostics,
            BoundedByteArrayOutputStream stdout,
            BoundedByteArrayOutputStream warnings,
            BoundedByteArrayOutputStream stderr)
            throws EtlExecutionException {
        for (LoadedEtlModel loadedModel : loadedModels) {
            if (loadedModel.configuration().readOnly()) {
                continue;
            }
            IModel model = loadedModel.model();
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
                            startedAt,
                            phaseTiming,
                            diagnostics,
                            stdout,
                            warnings,
                            stderr));
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
            EtlPhaseTiming phaseTiming,
            List<EtlDiagnostic> diagnostics,
            BoundedByteArrayOutputStream stdout,
            BoundedByteArrayOutputStream warnings,
            BoundedByteArrayOutputStream stderr,
            Throwable cause) throws EtlExecutionException {
        if (diagnostics.stream().anyMatch(d -> d.severity() == DiagnosticSeverity.ERROR)) {
            throw failure("ETL request is invalid.", request, startedAt, phaseTiming, diagnostics,
                    stdout, warnings, stderr, cause);
        }
    }

    private EtlExecutionException failure(
            String message,
            EtlExecutionRequest request,
            Instant startedAt,
            EtlPhaseTiming phaseTiming,
            List<EtlDiagnostic> diagnostics,
            BoundedByteArrayOutputStream stdout,
            BoundedByteArrayOutputStream warnings,
            BoundedByteArrayOutputStream stderr,
            Throwable cause) {
        return new EtlExecutionException(
                message,
                report(EtlExecutionStatus.FAILED, request, startedAt, phaseTiming, diagnostics,
                        stdout, warnings, stderr),
                cause);
    }

    private EtlExecutionReport report(
            EtlExecutionStatus status,
            EtlExecutionRequest request,
            Instant startedAt,
            EtlPhaseTiming phaseTiming,
            List<EtlDiagnostic> diagnostics,
            BoundedByteArrayOutputStream stdout,
            BoundedByteArrayOutputStream warnings,
            BoundedByteArrayOutputStream stderr) {
        Instant finishedAt = Instant.now();
        return new EtlExecutionReport(
                status,
                request.moduleFile(),
                startedAt,
                finishedAt,
                Duration.between(startedAt, finishedAt),
                phaseTiming,
                diagnostics,
                stdout.asUtf8String(),
                warnings.asUtf8String(),
                stderr.asUtf8String());
    }

    private record LoadedEtlModel(EtlModelConfiguration configuration, IModel model) {

    }

    private static final class EpsilonExecutionTimeoutException extends RuntimeException {

        private EpsilonExecutionTimeoutException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
