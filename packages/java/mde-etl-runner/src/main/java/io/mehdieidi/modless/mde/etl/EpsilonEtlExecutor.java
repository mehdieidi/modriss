package io.mehdieidi.modless.mde.etl;

import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.resource.Resource;
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

/**
 * Executes Epsilon ETL modules against isolated per-job EMF models and returns a structured report
 * for both successes and failures.
 */
public final class EpsilonEtlExecutor {

  /** Default watchdog limit for ETL execution when no timeout is supplied. */
  private static final Duration DEFAULT_EXECUTION_TIMEOUT = Duration.ofMinutes(5);

  /** Default maximum captured bytes per Epsilon output stream. */
  private static final int DEFAULT_MAX_CAPTURED_OUTPUT_BYTES = 1024 * 1024;

  /** Optional execution timeout; {@code null} disables the watchdog. */
  private final Duration executionTimeout;

  /** Maximum number of bytes captured from each output stream. */
  private final int maxCapturedOutputBytes;

  /** Creates an executor with default output capture limits and no timeout. */
  public EpsilonEtlExecutor() {
    this(null, DEFAULT_MAX_CAPTURED_OUTPUT_BYTES);
  }

  /**
   * Creates an executor with explicit runtime limits.
   *
   * @param executionTimeout timeout for ETL execution; {@code null} or non-positive values disable
   *     the watchdog
   * @param maxCapturedOutputBytes byte limit for each captured output stream
   */
  public EpsilonEtlExecutor(Duration executionTimeout, int maxCapturedOutputBytes) {
    this.executionTimeout =
        executionTimeout == null || executionTimeout.isZero() || executionTimeout.isNegative()
            ? null
            : executionTimeout;
    this.maxCapturedOutputBytes =
        maxCapturedOutputBytes <= 0 ? DEFAULT_MAX_CAPTURED_OUTPUT_BYTES : maxCapturedOutputBytes;
  }

  /**
   * Executes the requested ETL module and returns a completed report.
   *
   * @param request ETL execution request
   * @return successful execution report
   * @throws EtlExecutionException when validation, parsing, loading, execution, or storage fails
   */
  public EtlExecutionReport execute(EtlExecutionRequest request) throws EtlExecutionException {
    Instant startedAt = Instant.now();
    long startedNanos = System.nanoTime();
    EtlPhaseTiming phaseTiming = new EtlPhaseTiming();
    List<EtlDiagnostic> diagnostics = new ArrayList<>();
    BoundedByteArrayOutputStream stdout = new BoundedByteArrayOutputStream(maxCapturedOutputBytes);
    BoundedByteArrayOutputStream warnings =
        new BoundedByteArrayOutputStream(maxCapturedOutputBytes);
    BoundedByteArrayOutputStream stderr = new BoundedByteArrayOutputStream(maxCapturedOutputBytes);
    EtlModule module = new EtlModule();
    EtlRuleExecutionProfiler ruleProfiler = EtlRuleExecutionProfiler.createIfEnabled();
    List<LoadedEtlModel> loadedModels = new ArrayList<>();

    try {
      long phaseStarted = System.nanoTime();
      validateRequest(request, diagnostics);
      phaseTiming.addValidation(System.nanoTime() - phaseStarted);
      failIfDiagnostics(
          request, startedAt, phaseTiming, diagnostics, stdout, warnings, stderr, null);

      phaseStarted = System.nanoTime();
      prepareOutputs(request);
      phaseTiming.addPrepareOutputs(System.nanoTime() - phaseStarted);

      phaseStarted = System.nanoTime();
      boolean parsed = parseModule(request, module, diagnostics);
      phaseTiming.addParse(System.nanoTime() - phaseStarted);
      if (!parsed || diagnostics.stream().anyMatch(d -> d.severity() == DiagnosticSeverity.ERROR)) {
        throw failure(
            "ETL module could not be parsed.",
            request,
            startedAt,
            phaseTiming,
            diagnostics,
            stdout,
            warnings,
            stderr,
            null);
      }

      configureStreams(module, request.captureOutput(), stdout, warnings, stderr);
      for (EtlModelConfiguration modelConfiguration : request.models()) {
        phaseStarted = System.nanoTime();
        LoadedEtlModel loadedModel = loadModel(modelConfiguration);
        phaseTiming.addModelLoad(modelConfiguration.readOnly(), System.nanoTime() - phaseStarted);
        loadedModels.add(loadedModel);
        module.getContext().getModelRepository().addModel(loadedModel.model());
      }

      configureTransformationState(module);
      if (ruleProfiler != null) {
        module.getContext().getExecutorFactory().addExecutionListener(ruleProfiler);
      }
      phaseStarted = System.nanoTime();
      executeModule(module);
      phaseTiming.addExecute(System.nanoTime() - phaseStarted);
      phaseStarted = System.nanoTime();
      try {
        storeModels(
            request, startedAt, phaseTiming, loadedModels, diagnostics, stdout, warnings, stderr);
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
      diagnostics.add(
          EtlDiagnostic.error(
              ExecutionPhase.MODEL_LOADING,
              request.moduleFile(),
              -1,
              -1,
              ex.getMessage(),
              "An EMF model or metamodel could not be loaded.",
              "Check model paths, metamodel paths, namespace aliases, and that referenced Ecore"
                  + " files exist.",
              ex));
      throw failure(
          "ETL model loading failed.",
          request,
          startedAt,
          phaseTiming,
          diagnostics,
          stdout,
          warnings,
          stderr,
          ex);
    } catch (EolRuntimeException ex) {
      diagnostics.add(runtimeDiagnostic(ex, request.moduleFile()));
      throw failure(
          "ETL execution failed.",
          request,
          startedAt,
          phaseTiming,
          diagnostics,
          stdout,
          warnings,
          stderr,
          ex);
    } catch (EpsilonExecutionTimeoutException ex) {
      diagnostics.add(
          EtlDiagnostic.error(
              ExecutionPhase.EXECUTION,
              request.moduleFile(),
              -1,
              -1,
              ex.getMessage(),
              "The ETL module exceeded the configured execution timeout.",
              "Reduce model size, inspect the ETL script for non-terminating logic, or increase the"
                  + " timeout deliberately.",
              ex));
      throw failure(
          "ETL execution timed out.",
          request,
          startedAt,
          phaseTiming,
          diagnostics,
          stdout,
          warnings,
          stderr,
          ex);
    } catch (Exception ex) {
      diagnostics.add(
          EtlDiagnostic.error(
              ExecutionPhase.UNEXPECTED,
              request.moduleFile(),
              -1,
              -1,
              ex.getMessage(),
              "An unexpected exception interrupted ETL execution.",
              "Inspect the exception type and stack trace, then verify module/model/metamodel"
                  + " compatibility.",
              ex));
      throw failure(
          "ETL execution failed unexpectedly.",
          request,
          startedAt,
          phaseTiming,
          diagnostics,
          stdout,
          warnings,
          stderr,
          ex);
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
      if (ruleProfiler != null) {
        ruleProfiler.printReport();
      }
      phaseTiming.addDispose(System.nanoTime() - disposeStarted);
      phaseTiming.setTotal(System.nanoTime() - startedNanos);
    }
  }

  /**
   * Parses an ETL module and translates parser diagnostics into report diagnostics.
   *
   * @param request execution request containing the module file
   * @param module Epsilon ETL module to parse
   * @param diagnostics mutable diagnostic sink
   * @return {@code true} when Epsilon reports a parsed module
   * @throws Exception when Epsilon internals fail outside normal parse errors
   */
  private boolean parseModule(
      EtlExecutionRequest request, EtlModule module, List<EtlDiagnostic> diagnostics)
      throws Exception {
    boolean parsed;
    try {
      parsed = module.parse(request.moduleFile().toFile());
    } catch (Exception ex) {
      diagnostics.add(
          EtlDiagnostic.error(
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
      diagnostics.add(
          new EtlDiagnostic(
              problem.getSeverity() == ParseProblem.WARNING
                  ? DiagnosticSeverity.WARNING
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

  /**
   * Performs preflight validation before files are created or models are loaded.
   *
   * @param request execution request to validate
   * @param diagnostics mutable diagnostic sink
   */
  private void validateRequest(EtlExecutionRequest request, List<EtlDiagnostic> diagnostics) {
    if (!Files.isRegularFile(request.moduleFile())) {
      diagnostics.add(
          EtlDiagnostic.error(
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
      diagnostics.add(
          EtlDiagnostic.error(
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
        diagnostics.add(
            EtlDiagnostic.error(
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
          diagnostics.add(
              EtlDiagnostic.error(
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
      if (!model.readOnly()
          && Files.exists(model.modelFile())
          && !request.overwriteOutputs()
          && !model.readOnLoad()) {
        diagnostics.add(
            EtlDiagnostic.error(
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

  /**
   * Ensures writable target directories exist and removes stale targets when overwrite mode allows
   * it.
   *
   * @param request execution request
   * @throws Exception when filesystem preparation fails
   */
  private void prepareOutputs(EtlExecutionRequest request) throws Exception {
    for (EtlModelConfiguration model : request.models()) {
      if (model.readOnly()) {
        continue;
      }
      Path parent = model.modelFile().toAbsolutePath().getParent();
      if (parent != null) {
        Files.createDirectories(parent);
      }
      if (Files.exists(model.modelFile()) && request.overwriteOutputs() && !model.readOnLoad()) {
        Files.delete(model.modelFile());
      }
    }
  }

  /**
   * Creates and loads an Epsilon EMF model from a model configuration.
   *
   * @param modelConfiguration model configuration to load
   * @return loaded model state
   * @throws EolModelLoadingException when Epsilon cannot load the model
   */
  private LoadedEtlModel loadModel(EtlModelConfiguration modelConfiguration)
      throws EolModelLoadingException {
    EmfModel model = new EmfModel();
    StringProperties properties = new StringProperties();
    properties.put(Model.PROPERTY_NAME, modelConfiguration.name());
    if (!modelConfiguration.aliases().isEmpty()) {
      properties.put(Model.PROPERTY_ALIASES, String.join(",", modelConfiguration.aliases()));
    }
    properties.put(Model.PROPERTY_READONLOAD, Boolean.toString(modelConfiguration.readOnLoad()));
    properties.put(Model.PROPERTY_STOREONDISPOSAL, "false");
    properties.put(Model.PROPERTY_READONLY, Boolean.toString(modelConfiguration.readOnly()));
    properties.put(EmfModel.PROPERTY_MODEL_URI, fileUri(modelConfiguration.modelFile()));
    properties.put(
        EmfModel.PROPERTY_FILE_BASED_METAMODEL_URI,
        joinFileUris(modelConfiguration.metamodelFiles()));
    properties.put(
        EmfModel.PROPERTY_REUSE_UNMODIFIED_FILE_BASED_METAMODELS, Boolean.FALSE.toString());
    properties.put(EmfModel.PROPERTY_VALIDATE, Boolean.toString(modelConfiguration.validate()));
    model.load(properties);
    return new LoadedEtlModel(modelConfiguration, model, model.getResource());
  }

  /**
   * Adds helper objects and cache variables expected by the bundled ETL modules.
   *
   * @param module module whose context should be initialized
   */
  private void configureTransformationState(EtlModule module) {
    // The ETL library treats these globals as shared per-run caches; initializing
    // them here keeps script imports deterministic and avoids stale JVM state.
    module
        .getContext()
        .getFrameStack()
        .putGlobal(
            new Variable(
                "etlProfiler",
                new EtlProfiler(),
                org.eclipse.epsilon.eol.types.EolAnyType.Instance),
            new Variable(
                "etlTextChecks",
                new EtlTextChecks(),
                org.eclipse.epsilon.eol.types.EolAnyType.Instance),
            new Variable(
                "modelElementIdGenerator",
                new ModelElementIdGenerator(),
                org.eclipse.epsilon.eol.types.EolAnyType.Instance),
            new Variable(
                "usedAwsLogicalIds",
                new java.util.LinkedHashSet<>(),
                org.eclipse.epsilon.eol.types.EolAnyType.Instance),
            new Variable(
                "usedAwsPhysicalNames",
                new java.util.LinkedHashSet<>(),
                org.eclipse.epsilon.eol.types.EolAnyType.Instance),
            new Variable("cachedPimRoot", null, org.eclipse.epsilon.eol.types.EolAnyType.Instance),
            new Variable("cachedCimRoot", null, org.eclipse.epsilon.eol.types.EolAnyType.Instance),
            new Variable("cachedAwsRoot", null, org.eclipse.epsilon.eol.types.EolAnyType.Instance),
            new Variable(
                "cachedDefaultStack", null, org.eclipse.epsilon.eol.types.EolAnyType.Instance),
            new Variable(
                "cachedDefaultStage", null, org.eclipse.epsilon.eol.types.EolAnyType.Instance),
            new Variable(
                "cachedIsProductionRoot", false, org.eclipse.epsilon.eol.types.EolAnyType.Instance),
            new Variable(
                "cachedCimAggregateByEntityId",
                null,
                org.eclipse.epsilon.eol.types.EolAnyType.Instance),
            new Variable(
                "cachedCimContextByCapabilityId",
                null,
                org.eclipse.epsilon.eol.types.EolAnyType.Instance),
            new Variable(
                "cachedCimOwnerContextByElementId",
                null,
                org.eclipse.epsilon.eol.types.EolAnyType.Instance),
            new Variable(
                "cachedCimServiceBySourceId",
                null,
                org.eclipse.epsilon.eol.types.EolAnyType.Instance),
            new Variable(
                "cachedCimEquivalentByRule",
                null,
                org.eclipse.epsilon.eol.types.EolAnyType.Instance),
            new Variable(
                "cachedCimFallbackService",
                null,
                org.eclipse.epsilon.eol.types.EolAnyType.Instance),
            new Variable(
                "cachedCimPolicyTargetsBySourceId",
                null,
                org.eclipse.epsilon.eol.types.EolAnyType.Instance),
            new Variable(
                "cachedPimObservabilityById",
                null,
                org.eclipse.epsilon.eol.types.EolAnyType.Instance),
            new Variable(
                "cachedPimResilienceById", null, org.eclipse.epsilon.eol.types.EolAnyType.Instance),
            new Variable(
                "cachedPimIdempotencyById",
                null,
                org.eclipse.epsilon.eol.types.EolAnyType.Instance),
            new Variable(
                "cachedPimSchemaById", null, org.eclipse.epsilon.eol.types.EolAnyType.Instance),
            new Variable(
                "cachedPimElementById", null, org.eclipse.epsilon.eol.types.EolAnyType.Instance),
            new Variable(
                "cachedPimValidationTargets",
                null,
                org.eclipse.epsilon.eol.types.EolAnyType.Instance),
            new Variable(
                "cachedPimDataAccessIds", null, org.eclipse.epsilon.eol.types.EolAnyType.Instance),
            new Variable(
                "cachedPimExternalFlowIds",
                null,
                org.eclipse.epsilon.eol.types.EolAnyType.Instance),
            new Variable(
                "cachedPimQueueById", null, org.eclipse.epsilon.eol.types.EolAnyType.Instance),
            new Variable(
                "cachedPimServiceOwnedKeys",
                null,
                org.eclipse.epsilon.eol.types.EolAnyType.Instance),
            new Variable(
                "cachedPimServiceMembershipKeys",
                null,
                org.eclipse.epsilon.eol.types.EolAnyType.Instance),
            new Variable(
                "cachedPimRetentionPolicyById",
                null,
                org.eclipse.epsilon.eol.types.EolAnyType.Instance),
            new Variable(
                "cachedPimBackupPolicyById",
                null,
                org.eclipse.epsilon.eol.types.EolAnyType.Instance),
            new Variable(
                "cachedPimSchemasByItemId",
                null,
                org.eclipse.epsilon.eol.types.EolAnyType.Instance),
            new Variable(
                "cachedReadinessManualDecisionsById",
                null,
                org.eclipse.epsilon.eol.types.EolAnyType.Instance),
            new Variable(
                "cachedReadinessFindingsById",
                null,
                org.eclipse.epsilon.eol.types.EolAnyType.Instance),
            new Variable(
                "cachedReadinessChecksById",
                null,
                org.eclipse.epsilon.eol.types.EolAnyType.Instance),
            new Variable(
                "cachedAwsTargetHadPreloadedContent",
                false,
                org.eclipse.epsilon.eol.types.EolAnyType.Instance),
            new Variable(
                "cachedAwsExistingTargetByKey",
                null,
                org.eclipse.epsilon.eol.types.EolAnyType.Instance),
            new Variable(
                "cachedPimServiceNameByElementId",
                null,
                org.eclipse.epsilon.eol.types.EolAnyType.Instance),
            new Variable(
                "cachedPimOwnerTeamByElementId",
                null,
                org.eclipse.epsilon.eol.types.EolAnyType.Instance),
            new Variable(
                "cachedPimDeploymentAssignedIds",
                null,
                org.eclipse.epsilon.eol.types.EolAnyType.Instance),
            new Variable(
                "cachedPimDeploymentUnitByElementId",
                null,
                org.eclipse.epsilon.eol.types.EolAnyType.Instance),
            new Variable(
                "cachedAwsEquivalentBySourceId",
                null,
                org.eclipse.epsilon.eol.types.EolAnyType.Instance),
            new Variable(
                "cachedAwsGeneratedResources",
                null,
                org.eclipse.epsilon.eol.types.EolAnyType.Instance),
            new Variable(
                "cachedAwsGeneratedResourceObjects",
                null,
                org.eclipse.epsilon.eol.types.EolAnyType.Instance),
            new Variable(
                "cachedGeneratedIdByKey", null, org.eclipse.epsilon.eol.types.EolAnyType.Instance),
            new Variable(
                "cachedNextLogicalIdIndexBySeed",
                null,
                org.eclipse.epsilon.eol.types.EolAnyType.Instance),
            new Variable(
                "cachedNextPhysicalNameIndexBySeed",
                null,
                org.eclipse.epsilon.eol.types.EolAnyType.Instance),
            new Variable(
                "cachedDataAccessesByFunctionId",
                null,
                org.eclipse.epsilon.eol.types.EolAnyType.Instance),
            new Variable(
                "cachedStageStackLinks", null, org.eclipse.epsilon.eol.types.EolAnyType.Instance),
            new Variable(
                "cachedApiGatewayRoutesWithLambdaIntegration",
                null,
                org.eclipse.epsilon.eol.types.EolAnyType.Instance),
            new Variable(
                "cachedSqsLambdaMappings", null, org.eclipse.epsilon.eol.types.EolAnyType.Instance),
            new Variable(
                "cachedSnsLambdaSubscriptions",
                null,
                org.eclipse.epsilon.eol.types.EolAnyType.Instance),
            new Variable(
                "cachedEventBridgeRules", null, org.eclipse.epsilon.eol.types.EolAnyType.Instance),
            new Variable(
                "cachedAwsResolvingRelationships",
                false,
                org.eclipse.epsilon.eol.types.EolAnyType.Instance),
            new Variable(
                "cachedAwsAuthorizerByApiId",
                null,
                org.eclipse.epsilon.eol.types.EolAnyType.Instance),
            new Variable(
                "cachedLambdaPermissionByKey",
                null,
                org.eclipse.epsilon.eol.types.EolAnyType.Instance),
            new Variable(
                "cachedSqsLambdaMappingByKey",
                null,
                org.eclipse.epsilon.eol.types.EolAnyType.Instance),
            new Variable(
                "cachedSnsSubscriptionByKey",
                null,
                org.eclipse.epsilon.eol.types.EolAnyType.Instance),
            new Variable(
                "cachedTopicsByEventTypeId",
                null,
                org.eclipse.epsilon.eol.types.EolAnyType.Instance),
            new Variable(
                "cachedEventBusesByEventTypeId",
                null,
                org.eclipse.epsilon.eol.types.EolAnyType.Instance),
            new Variable(
                "cachedQueuesByEventTypeId",
                null,
                org.eclipse.epsilon.eol.types.EolAnyType.Instance),
            new Variable(
                "cachedJsonSchemaById", null, org.eclipse.epsilon.eol.types.EolAnyType.Instance),
            new Variable(
                "cachedOpenApiSchemaById", null, org.eclipse.epsilon.eol.types.EolAnyType.Instance),
            new Variable(
                "cachedPimRootName", null, org.eclipse.epsilon.eol.types.EolAnyType.Instance),
            new Variable(
                "cachedAwsReadinessDecisionIds",
                null,
                org.eclipse.epsilon.eol.types.EolAnyType.Instance),
            new Variable(
                "cachedAwsReadinessFindingIds",
                null,
                org.eclipse.epsilon.eol.types.EolAnyType.Instance),
            new Variable(
                "cachedAwsReadinessCheckIds",
                null,
                org.eclipse.epsilon.eol.types.EolAnyType.Instance));
  }

  /**
   * Executes the parsed module, optionally protected by the watchdog timeout.
   *
   * @param module parsed ETL module
   * @throws Exception when the module or watchdog reports failure
   */
  private void executeModule(EtlModule module) throws Exception {
    if (executionTimeout == null) {
      module.execute();
      return;
    }
    Thread executingThread = Thread.currentThread();
    AtomicBoolean timedOut = new AtomicBoolean(false);
    ScheduledExecutorService watchdog =
        Executors.newSingleThreadScheduledExecutor(
            runnable -> {
              Thread thread = new Thread(runnable, "epsilon-etl-watchdog");
              thread.setDaemon(true);
              return thread;
            });
    ScheduledFuture<?> timeout =
        watchdog.schedule(
            () -> {
              timedOut.set(true);
              // Epsilon execution is synchronous, so the watchdog interrupts the caller thread.
              executingThread.interrupt();
            },
            executionTimeout.toMillis(),
            TimeUnit.MILLISECONDS);
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

  /**
   * Builds the timeout exception used after watchdog interruption.
   *
   * @return timeout exception with a user-facing duration
   */
  private EpsilonExecutionTimeoutException timeoutException() {
    return new EpsilonExecutionTimeoutException(
        "ETL execution timed out after " + executionTimeout + ".", null);
  }

  /**
   * Raises a timeout exception when the watchdog fired after module execution returned normally.
   *
   * @param timedOut watchdog state
   */
  private void throwIfTimedOut(AtomicBoolean timedOut) {
    if (timedOut.get()) {
      throw new EpsilonExecutionTimeoutException(
          "ETL execution timed out after " + executionTimeout + ".", null);
    }
  }

  /**
   * Persists writable models and fails the run when persistence diagnostics are recorded.
   *
   * @param request original execution request
   * @param startedAt run start time
   * @param phaseTiming mutable phase timing accumulator
   * @param loadedModels loaded model/configuration pairs
   * @param diagnostics mutable diagnostic sink
   * @param stdout captured standard output
   * @param warnings captured warning output
   * @param stderr captured error output
   * @throws EtlExecutionException when any target model cannot be stored
   */
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
        Path target = loadedModel.configuration().modelFile().toAbsolutePath().normalize();
        Path parent = target.getParent();
        if (parent != null) {
          Files.createDirectories(parent);
        }
        try (OutputStream output = Files.newOutputStream(target)) {
          loadedModel.resource().save(output, Map.of());
        }
      } catch (Exception ex) {
        diagnostics.add(
            EtlDiagnostic.error(
                ExecutionPhase.MODEL_STORING,
                request.moduleFile(),
                -1,
                -1,
                ex.getMessage(),
                "The ETL transformation executed, but model persistence failed.",
                "Check the target path, file permissions, disk availability, and EMF resource"
                    + " serialization details.",
                ex));
      }
    }
    if (diagnostics.stream()
        .anyMatch(
            d ->
                d.severity() == DiagnosticSeverity.ERROR
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
      EtlModule module,
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

  private String joinFileUris(List<Path> paths) {
    return paths.stream().map(this::fileUri).reduce((left, right) -> left + "," + right).orElse("");
  }

  private String fileUri(Path path) {
    return URI.createFileURI(path.toAbsolutePath().normalize().toString()).toString();
  }

  /**
   * Converts an Epsilon runtime exception into an execution diagnostic with source location when
   * available.
   *
   * @param ex runtime exception from Epsilon
   * @param fallbackFile file used when the AST does not expose a source file
   * @return structured runtime diagnostic
   */
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
        "Inspect the reported file, line, and column. Check model aliases, metamodel features, type"
            + " names, and target cardinalities.",
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
   * Fails fast when request validation collected any error diagnostics.
   *
   * @param request original execution request
   * @param startedAt run start time
   * @param phaseTiming phase timing accumulator
   * @param diagnostics collected diagnostics
   * @param stdout captured standard output
   * @param warnings captured warning output
   * @param stderr captured error output
   * @param cause optional root cause
   * @throws EtlExecutionException when diagnostics contain an error
   */
  private void failIfDiagnostics(
      EtlExecutionRequest request,
      Instant startedAt,
      EtlPhaseTiming phaseTiming,
      List<EtlDiagnostic> diagnostics,
      BoundedByteArrayOutputStream stdout,
      BoundedByteArrayOutputStream warnings,
      BoundedByteArrayOutputStream stderr,
      Throwable cause)
      throws EtlExecutionException {
    if (diagnostics.stream().anyMatch(d -> d.severity() == DiagnosticSeverity.ERROR)) {
      throw failure(
          "ETL request is invalid.",
          request,
          startedAt,
          phaseTiming,
          diagnostics,
          stdout,
          warnings,
          stderr,
          cause);
    }
  }

  /**
   * Builds an ETL exception carrying a failed execution report.
   *
   * @param message exception message
   * @param request original execution request
   * @param startedAt run start time
   * @param phaseTiming phase timing accumulator
   * @param diagnostics collected diagnostics
   * @param stdout captured standard output
   * @param warnings captured warning output
   * @param stderr captured error output
   * @param cause optional root cause
   * @return exception with a failure report
   */
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
        report(
            EtlExecutionStatus.FAILED,
            request,
            startedAt,
            phaseTiming,
            diagnostics,
            stdout,
            warnings,
            stderr),
        cause);
  }

  /**
   * Creates an immutable execution report from the current run state.
   *
   * @param status terminal status
   * @param request original execution request
   * @param startedAt run start time
   * @param phaseTiming phase timing accumulator
   * @param diagnostics collected diagnostics
   * @param stdout captured standard output
   * @param warnings captured warning output
   * @param stderr captured error output
   * @return execution report
   */
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

  /**
   * Couples a loaded model with the configuration that controls storage and timing buckets.
   *
   * @param configuration original model configuration
   * @param model loaded Epsilon model
   */
  private record LoadedEtlModel(
      EtlModelConfiguration configuration, IModel model, Resource resource) {}

  /**
   * Runtime exception used internally to distinguish watchdog timeouts from Epsilon runtime
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
