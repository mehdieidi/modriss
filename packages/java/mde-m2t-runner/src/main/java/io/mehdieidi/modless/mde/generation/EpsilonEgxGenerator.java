package io.mehdieidi.modless.mde.generation;

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
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
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

/**
 * Executes Epsilon EGX modules against file-backed EMF models and returns a structured generation
 * report.
 */
public final class EpsilonEgxGenerator {

  /** Default watchdog limit for EGX execution when no timeout is supplied. */
  private static final Duration DEFAULT_EXECUTION_TIMEOUT = Duration.ofMinutes(5);

  /** Default maximum captured bytes per Epsilon output stream. */
  private static final int DEFAULT_MAX_CAPTURED_OUTPUT_BYTES = 1024 * 1024;

  /**
   * Serializes Epsilon EGX execution within a JVM. The Epsilon engine keeps mutable global state
   * that is not safe under concurrent module execution.
   */
  private static final Object EPSILON_RUNTIME_MONITOR =
      "io.mehdieidi.modless.mde.EPSILON_RUNTIME".intern();

  /** Optional execution timeout; {@code null} disables the watchdog. */
  private final Duration executionTimeout;

  /** Maximum number of bytes captured from each output stream. */
  private final int maxCapturedOutputBytes;

  /** Creates a generator with default output capture limits and no timeout. */
  public EpsilonEgxGenerator() {
    this(null, DEFAULT_MAX_CAPTURED_OUTPUT_BYTES);
  }

  /**
   * Creates a generator with explicit runtime limits.
   *
   * @param executionTimeout timeout for EGX execution; {@code null} or non-positive values disable
   *     the watchdog
   * @param maxCapturedOutputBytes byte limit for each captured output stream
   */
  public EpsilonEgxGenerator(Duration executionTimeout, int maxCapturedOutputBytes) {
    this.executionTimeout =
        executionTimeout == null || executionTimeout.isZero() || executionTimeout.isNegative()
            ? null
            : executionTimeout;
    this.maxCapturedOutputBytes =
        maxCapturedOutputBytes <= 0 ? DEFAULT_MAX_CAPTURED_OUTPUT_BYTES : maxCapturedOutputBytes;
  }

  /**
   * Executes the requested EGX module and returns a completed report.
   *
   * @param request generation request
   * @return successful generation report
   * @throws EgxGenerationException when validation, parsing, loading, execution, or trace
   *     finalization fails
   */
  public EgxGenerationReport generate(EgxGenerationRequest request) throws EgxGenerationException {
    synchronized (EPSILON_RUNTIME_MONITOR) {
      return generateInternal(request);
    }
  }

  private EgxGenerationReport generateInternal(EgxGenerationRequest request)
      throws EgxGenerationException {
    Instant startedAt = Instant.now();
    List<GenerationDiagnostic> diagnostics = new ArrayList<>();
    BoundedByteArrayOutputStream stdout = new BoundedByteArrayOutputStream(maxCapturedOutputBytes);
    BoundedByteArrayOutputStream warnings =
        new BoundedByteArrayOutputStream(maxCapturedOutputBytes);
    BoundedByteArrayOutputStream stderr = new BoundedByteArrayOutputStream(maxCapturedOutputBytes);
    List<IModel> loadedModels = new ArrayList<>();
    EgxModule module = null;

    try {
      validateRequest(request, diagnostics);
      failIfDiagnostics(request, startedAt, diagnostics, stdout, warnings, stderr, null);
      Files.createDirectories(request.outputDirectory());

      PreludeInjectingTemplateFactory factory =
          new PreludeInjectingTemplateFactory(
              request.outputDirectory(),
              request.moduleFile().toAbsolutePath().getParent().resolve("lib"));
      factory.setTemplateRoot(request.templateRoot().toAbsolutePath().toUri().toString());
      module = new EgxModule(factory);

      boolean parsed = parseModule(request, module, diagnostics);
      if (!parsed || diagnostics.stream().anyMatch(d -> d.severity() == GenerationSeverity.ERROR)) {
        throw failure(
            "EGX module could not be parsed.",
            request,
            startedAt,
            diagnostics,
            stdout,
            warnings,
            stderr,
            null);
      }
      factory.copyState(module.getContext());

      configureStreams(module, request.captureOutput(), stdout, warnings, stderr);
      for (GenerationModelConfiguration modelConfiguration : request.models()) {
        IModel model = loadModel(modelConfiguration);
        loadedModels.add(model);
        module.getContext().getModelRepository().addModel(model);
      }

      executeModule(module);
      normalizeGeneratedTextFiles(request.outputDirectory());
      finalizeGeneratedTraceFiles(request);
      return report(
          GenerationStatus.SUCCEEDED, request, startedAt, diagnostics, stdout, warnings, stderr);
    } catch (EgxGenerationException ex) {
      throw ex;
    } catch (EolModelLoadingException ex) {
      diagnostics.add(
          GenerationDiagnostic.error(
              GenerationPhase.MODEL_LOADING,
              request.moduleFile(),
              -1,
              -1,
              ex.getMessage(),
              "An EMF model or metamodel could not be loaded for EGX generation.",
              "Check model paths, metamodel paths, namespace aliases, and referenced Ecore files.",
              ex));
      throw failure(
          "EGX model loading failed.",
          request,
          startedAt,
          diagnostics,
          stdout,
          warnings,
          stderr,
          ex);
    } catch (EolRuntimeException ex) {
      diagnostics.add(runtimeDiagnostic(ex, request.moduleFile()));
      throw failure(
          "EGX generation failed.", request, startedAt, diagnostics, stdout, warnings, stderr, ex);
    } catch (EpsilonExecutionTimeoutException ex) {
      diagnostics.add(
          GenerationDiagnostic.error(
              GenerationPhase.EXECUTION,
              request.moduleFile(),
              -1,
              -1,
              ex.getMessage(),
              "The EGX module exceeded the configured execution timeout.",
              "Reduce model size, inspect EGX/EGL for non-terminating logic, or increase the"
                  + " timeout deliberately.",
              ex));
      throw failure(
          "EGX generation timed out.",
          request,
          startedAt,
          diagnostics,
          stdout,
          warnings,
          stderr,
          ex);
    } catch (Exception ex) {
      diagnostics.add(
          GenerationDiagnostic.error(
              GenerationPhase.UNEXPECTED,
              request.moduleFile(),
              -1,
              -1,
              ex.getMessage(),
              "An unexpected exception interrupted EGX generation.",
              "Inspect the exception type, stack trace, EGX module, templates, and model/metamodel"
                  + " compatibility.",
              ex));
      throw failure(
          "EGX generation failed unexpectedly.",
          request,
          startedAt,
          diagnostics,
          stdout,
          warnings,
          stderr,
          ex);
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

  /**
   * Normalizes generated text artifacts to UTF-8 with LF line endings.
   *
   * <p>EGL inherits the host platform line separator. Generated projects are deployed and executed
   * on Linux even when generation runs on Windows, so leaving CRLF in shell scripts makes an
   * otherwise valid export unusable. Files containing a NUL byte are treated as binary and left
   * untouched.
   *
   * @param outputDirectory generated project root
   * @throws Exception when generated files cannot be inspected or rewritten
   */
  private void normalizeGeneratedTextFiles(Path outputDirectory) throws Exception {
    if (!Files.isDirectory(outputDirectory)) {
      return;
    }
    try (Stream<Path> files = Files.walk(outputDirectory)) {
      for (Path file : files.filter(Files::isRegularFile).toList()) {
        byte[] bytes = Files.readAllBytes(file);
        if (containsNullByte(bytes)) {
          continue;
        }
        String text = new String(bytes, StandardCharsets.UTF_8);
        String normalized = text.replace("\r\n", "\n").replace('\r', '\n');
        if (!normalized.equals(text)) {
          Files.writeString(file, normalized, StandardCharsets.UTF_8);
        }
      }
    }
  }

  /**
   * Checks whether content is likely binary.
   *
   * @param bytes file content
   * @return whether the content contains a NUL byte
   */
  private boolean containsNullByte(byte[] bytes) {
    for (byte value : bytes) {
      if (value == 0) {
        return true;
      }
    }
    return false;
  }

  /**
   * Performs request preflight validation before output directories are created.
   *
   * @param request generation request to validate
   * @param diagnostics mutable diagnostic sink
   * @throws Exception when directory inspection fails
   */
  private void validateRequest(EgxGenerationRequest request, List<GenerationDiagnostic> diagnostics)
      throws Exception {
    if (!Files.isRegularFile(request.moduleFile())) {
      diagnostics.add(
          GenerationDiagnostic.error(
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
      diagnostics.add(
          GenerationDiagnostic.error(
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
      diagnostics.add(
          GenerationDiagnostic.error(
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
      diagnostics.add(
          GenerationDiagnostic.error(
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
        diagnostics.add(
            GenerationDiagnostic.error(
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
          diagnostics.add(
              GenerationDiagnostic.error(
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

  /**
   * Checks whether a directory contains at least one child entry.
   *
   * @param directory directory to inspect
   * @return {@code true} when the directory is non-empty
   * @throws Exception when children cannot be listed
   */
  private boolean isNotEmpty(Path directory) throws Exception {
    try (Stream<Path> children = Files.list(directory)) {
      return children.findAny().isPresent();
    }
  }

  /**
   * Parses an EGX module and maps parser diagnostics into report diagnostics.
   *
   * @param request generation request containing the module file
   * @param module Epsilon EGX module to parse
   * @param diagnostics mutable diagnostic sink
   * @return {@code true} when Epsilon reports a parsed module
   * @throws Exception when Epsilon internals fail outside normal parse errors
   */
  private boolean parseModule(
      EgxGenerationRequest request, EgxModule module, List<GenerationDiagnostic> diagnostics)
      throws Exception {
    boolean parsed;
    try {
      parsed = module.parse(request.moduleFile().toFile());
    } catch (Exception ex) {
      diagnostics.add(
          GenerationDiagnostic.error(
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
      diagnostics.add(
          new GenerationDiagnostic(
              problem.getSeverity() == ParseProblem.WARNING
                  ? GenerationSeverity.WARNING
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

  /**
   * Creates and loads an Epsilon EMF model from a model configuration.
   *
   * @param modelConfiguration model configuration to load
   * @return loaded Epsilon model
   * @throws EolModelLoadingException when Epsilon cannot load the model
   */
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
    properties.put(
        EmfModel.PROPERTY_MODEL_FILE, modelConfiguration.modelFile().toAbsolutePath().toString());
    properties.put(
        EmfModel.PROPERTY_METAMODEL_FILE, joinPaths(modelConfiguration.metamodelFiles()));
    properties.put(EmfModel.PROPERTY_VALIDATE, Boolean.toString(modelConfiguration.validate()));
    model.load(properties);
    return model;
  }

  /**
   * Joins metamodel paths for Epsilon's file-based model configuration.
   *
   * @param paths metamodel paths
   * @return comma-separated absolute path list
   */
  private String joinPaths(List<Path> paths) {
    return paths.stream()
        .map(path -> path.toAbsolutePath().toString())
        .reduce((left, right) -> left + "," + right)
        .orElse("");
  }

  /**
   * Executes the parsed module, optionally protected by the watchdog timeout.
   *
   * @param module parsed EGX module
   * @throws Exception when the module or watchdog reports failure
   */
  private void executeModule(EgxModule module) throws Exception {
    if (executionTimeout == null) {
      module.execute();
      return;
    }
    Thread executingThread = Thread.currentThread();
    AtomicBoolean timedOut = new AtomicBoolean(false);
    ScheduledExecutorService watchdog =
        Executors.newSingleThreadScheduledExecutor(
            runnable -> {
              Thread thread = new Thread(runnable, "epsilon-egx-watchdog");
              thread.setDaemon(true);
              return thread;
            });
    ScheduledFuture<?> timeout =
        watchdog.schedule(
            () -> {
              timedOut.set(true);
              // EGX execution is synchronous, so the watchdog interrupts the caller thread.
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
      if (timedOut.get()) {
        throw timeoutException();
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
        "EGX execution timed out after " + executionTimeout + ".", null);
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
      EgxModule module,
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
        "Inspect the reported file, line, and column. Check model aliases, metamodel features,"
            + " template parameters, and target expressions.",
        ex);
  }

  /**
   * Replaces trace placeholders that can only be computed after files are written.
   *
   * @param request original generation request
   * @throws Exception when trace files or generated artifacts cannot be read
   */
  private void finalizeGeneratedTraceFiles(EgxGenerationRequest request) throws Exception {
    Path artifactTrace = request.outputDirectory().resolve("generated/trace/artifact-trace.json");
    if (!Files.isRegularFile(artifactTrace)) {
      return;
    }

    List<String> updatedLines = new ArrayList<>();
    String currentPath = null;
    for (String line : Files.readAllLines(artifactTrace, StandardCharsets.UTF_8)) {
      String trimmed = line.trim();
      if (trimmed.startsWith("\"sourceModelHash\"")) {
        updatedLines.add(
            line.replace("COMPUTED_BY_EXECUTOR", sha256(request.models().get(0).modelFile())));
        continue;
      }
      if (trimmed.startsWith("\"path\"")) {
        currentPath = jsonStringValue(trimmed);
        updatedLines.add(line);
        continue;
      }
      if (line.contains("COMPUTED_AFTER_WRITE") && currentPath != null) {
        // Template code cannot know final file hashes until all artifacts are written.
        Path artifact = request.outputDirectory().resolve(currentPath).normalize();
        String checksum = Files.isRegularFile(artifact) ? sha256(artifact) : "MISSING";
        updatedLines.add(line.replace("COMPUTED_AFTER_WRITE", checksum));
        continue;
      }
      updatedLines.add(line);
    }
    Files.writeString(
        artifactTrace, String.join("\n", updatedLines) + "\n", StandardCharsets.UTF_8);
  }

  /**
   * Extracts a JSON string value from a simple property line.
   *
   * @param line JSON line containing a string property
   * @return unescaped string value, or an empty string when parsing fails
   */
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

  /**
   * Computes a SHA-256 hash for a file.
   *
   * @param path file to hash
   * @return lowercase hexadecimal digest
   * @throws Exception when hashing or reading fails
   */
  private String sha256(Path path) throws Exception {
    MessageDigest digest = MessageDigest.getInstance("SHA-256");
    return HexFormat.of().formatHex(digest.digest(Files.readAllBytes(path)));
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
   * Fails fast when request validation collected error diagnostics.
   *
   * @param request original generation request
   * @param startedAt run start time
   * @param diagnostics collected diagnostics
   * @param stdout captured standard output
   * @param warnings captured warning output
   * @param stderr captured error output
   * @param cause optional root cause
   * @throws EgxGenerationException when diagnostics contain an error
   */
  private void failIfDiagnostics(
      EgxGenerationRequest request,
      Instant startedAt,
      List<GenerationDiagnostic> diagnostics,
      BoundedByteArrayOutputStream stdout,
      BoundedByteArrayOutputStream warnings,
      BoundedByteArrayOutputStream stderr,
      Throwable cause)
      throws EgxGenerationException {
    if (diagnostics.stream().anyMatch(d -> d.severity() == GenerationSeverity.ERROR)) {
      throw failure(
          "EGX generation request is invalid.",
          request,
          startedAt,
          diagnostics,
          stdout,
          warnings,
          stderr,
          cause);
    }
  }

  /**
   * Builds a generation exception carrying a failed report.
   *
   * @param message exception message
   * @param request original generation request
   * @param startedAt run start time
   * @param diagnostics collected diagnostics
   * @param stdout captured standard output
   * @param warnings captured warning output
   * @param stderr captured error output
   * @param cause optional root cause
   * @return exception with a failed generation report
   */
  private EgxGenerationException failure(
      String message,
      EgxGenerationRequest request,
      Instant startedAt,
      List<GenerationDiagnostic> diagnostics,
      BoundedByteArrayOutputStream stdout,
      BoundedByteArrayOutputStream warnings,
      BoundedByteArrayOutputStream stderr,
      Throwable cause) {
    return new EgxGenerationException(
        message,
        report(GenerationStatus.FAILED, request, startedAt, diagnostics, stdout, warnings, stderr),
        cause);
  }

  /**
   * Creates an immutable generation report from the current run state.
   *
   * @param status terminal status
   * @param request original generation request
   * @param startedAt run start time
   * @param diagnostics collected diagnostics
   * @param stdout captured standard output
   * @param warnings captured warning output
   * @param stderr captured error output
   * @return generation report
   */
  private EgxGenerationReport report(
      GenerationStatus status,
      EgxGenerationRequest request,
      Instant startedAt,
      List<GenerationDiagnostic> diagnostics,
      BoundedByteArrayOutputStream stdout,
      BoundedByteArrayOutputStream warnings,
      BoundedByteArrayOutputStream stderr) {
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
        stdout.asUtf8String(),
        warnings.asUtf8String(),
        stderr.asUtf8String());
  }

  /**
   * Lists generated files relative to the output directory.
   *
   * @param outputDirectory generation output directory
   * @return sorted relative file paths, or an empty list when listing fails
   */
  private List<Path> listGeneratedFiles(Path outputDirectory) {
    if (!Files.isDirectory(outputDirectory)) {
      return List.of();
    }
    try (Stream<Path> files = Files.walk(outputDirectory)) {
      return files
          .filter(Files::isRegularFile)
          .map(outputDirectory.toAbsolutePath().normalize()::relativize)
          .sorted()
          .toList();
    } catch (Exception ignored) {
      return List.of();
    }
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
