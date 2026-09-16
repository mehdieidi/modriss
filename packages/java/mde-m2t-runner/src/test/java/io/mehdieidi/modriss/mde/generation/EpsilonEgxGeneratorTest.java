package io.mehdieidi.modriss.mde.generation;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EClassifier;
import org.eclipse.emf.ecore.EEnum;
import org.eclipse.emf.ecore.EFactory;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.emf.ecore.xmi.impl.EcoreResourceFactoryImpl;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;

/** Regression tests for EGX-driven artifact generation from AWS PSM models. */
@ResourceLock("epsilon-runtime")
final class EpsilonEgxGeneratorTest {

  /** Repository root discovered from the current test working directory. */
  private static final Path REPOSITORY_ROOT = findRepositoryRoot();

  /** EGX coordinator for the AWS PSM artifact generator. */
  private static final Path AWS_PSM_EGX =
      REPOSITORY_ROOT.resolve("mde/generation/awspsm-to-artifacts/awspsm2artifacts.egx");

  /** JSON parser used for syntax checks of generated machine-readable artifacts. */
  private static final ObjectMapper JSON = new ObjectMapper();

  /** YAML parser used for generated SAM, OpenAPI, CI, and document artifacts. */
  private static final tools.jackson.databind.ObjectMapper YAML =
      new tools.jackson.databind.ObjectMapper(new tools.jackson.dataformat.yaml.YAMLFactory());

  /** Pinned community LocalStack image for reproducible generated integration-test execution. */
  private static final String LOCALSTACK_IMAGE = "localstack/localstack:3.8.1";

  /** LocalStack image that activates Pro features when LOCALSTACK_AUTH_TOKEN is available. */
  private static final String LOCALSTACK_PRO_IMAGE = "localstack/localstack:latest";

  /** Pinned Floci image for reproducible generated integration-test execution. */
  private static final String FLOCI_IMAGE = "floci/floci:2.0.1";

  /** Temporary directory for generated source models and artifact projects. */
  @TempDir Path tempDir;

  private Map<String, byte[]> generatedFileBytes(Path root) throws IOException {
    Map<String, byte[]> files = new java.util.TreeMap<>();
    try (Stream<Path> paths = Files.walk(root)) {
      for (Path path : paths.filter(Files::isRegularFile).toList()) {
        files.put(root.relativize(path).toString().replace('\\', '/'), Files.readAllBytes(path));
      }
    }
    return files;
  }

  /** Disposable Docker network used by the active LocalStack test container. */
  private String activeLocalStackNetworkName;

  /**
   * Locates the repository root by walking upward to the MDE metamodel and generation directories.
   *
   * @return normalized repository root path
   */
  private static Path findRepositoryRoot() {
    Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
    while (current != null) {
      if (Files.isDirectory(current.resolve("mde/metamodels"))
          && Files.isDirectory(current.resolve("mde/generation/awspsm-to-artifacts"))) {
        return current;
      }
      current = current.getParent();
    }
    throw new IllegalStateException("Could not locate repository root from user.dir.");
  }

  /** Ensures a missing EGX module is reported as a validation diagnostic. */
  @Test
  void reportsMissingEgxModuleAsValidationDiagnostic() {
    EpsilonEgxGenerator generator = new EpsilonEgxGenerator();
    EgxGenerationRequest request =
        new EgxGenerationRequest(
            tempDir.resolve("missing.egx"),
            tempDir.resolve("templates"),
            tempDir.resolve("out"),
            List.of(),
            false,
            true);

    EgxGenerationException exception =
        assertThrows(EgxGenerationException.class, () -> generator.generate(request));

    assertEquals(GenerationStatus.FAILED, exception.getReport().status());
    assertTrue(
        exception.getReport().diagnostics().stream()
            .anyMatch(
                d ->
                    d.phase() == GenerationPhase.VALIDATION
                        && d.reason().contains("EGX module does not exist")));
  }

  /**
   * Catalog AR-01: generates the complete initial artifact set from a synthetic AWS PSM fixture.
   * Verifies the generated tree, reports, traces, scripts, and CI configuration.
   *
   * @throws Exception when fixture creation or generation fails
   */
  @Test
  void generatesArtifactsForRepresentativeAwsPsmModel() throws Exception {
    Path sourceModel = tempDir.resolve("representative-aws-psm.xmi");
    Path outputDirectory = tempDir.resolve("generated-project");
    createRepresentativeAwsPsmModel(sourceModel);

    EgxGenerationRequest request =
        AwsPsmToArtifactsDefaults.request(
            REPOSITORY_ROOT, sourceModel, outputDirectory, true, true);

    EgxGenerationReport report = generateOrFail(request);

    assertEquals(GenerationStatus.SUCCEEDED, report.status(), report.diagnostics().toString());
    assertFalse(report.generatedFiles().isEmpty(), "Expected EGX to write artifacts.");
    assertTrue(Files.isRegularFile(outputDirectory.resolve("README.md")));
    assertTrue(Files.isRegularFile(outputDirectory.resolve("template.yaml")));
    assertTrue(
        Files.isRegularFile(outputDirectory.resolve("generated/reports/generation-report.md")));
    assertTrue(
        Files.readString(outputDirectory.resolve("template.yaml")).contains("AWS::S3::Bucket"));
    assertTrue(
        Files.readString(outputDirectory.resolve("template.yaml"))
            .contains("Runtime: 'provided.al2023'"));
    assertTrue(
        Files.readString(outputDirectory.resolve("template.yaml"))
            .contains("Handler: 'bootstrap'"));
    assertTrue(
        Files.readString(outputDirectory.resolve("template.yaml"))
            .contains("CodeUri: 'bin/order-handler'"));
    assertTrue(
        Files.readString(outputDirectory.resolve("Makefile"))
            .contains("$(ARTIFACTS_DIR)/bootstrap"));
    Path aslFile = outputDirectory.resolve("asl/representative-workflow.asl.json");
    assertTrue(Files.isRegularFile(aslFile), "Expected generated ASL definition.");
    String aslText = Files.readString(aslFile);
    assertTrue(aslText.contains("\"QueryLanguage\": \"JSONPath\""));
    assertTrue(aslText.contains("\"StartAt\": \"Start\""));
    assertFalse(aslText.contains("\"Type\": \"Succeed\",\n      \"InputPath\""));
    assertRequiredProjectTreeWasGenerated(outputDirectory);
    assertGoOnlyArtifactsWereGenerated(outputDirectory);
    assertTraceFilesAreFinalized(outputDirectory);
    assertEveryEgxArtifactRuleExecuted(outputDirectory);
    assertGeneratedJsonArtifactsParse(outputDirectory);
    assertGeneratedYamlArtifactsParseAndHaveExpectedShape(outputDirectory);
    assertGeneratedApiMessagingEventAndDocumentArtifactsCoverSourceModel(outputDirectory);
    assertGeneratedGoProjectTestsPassWhenToolchainExists(outputDirectory);
    assertGenerationReportContainsExportGateSummary(outputDirectory);
    assertGeneratedScriptsAndCiImplementProductionGates(outputDirectory);
  }

  /**
   * Generates a broad SAM rendering fixture that covers concrete AWS resource renderers and major
   * branch-specific property emitters.
   *
   * @throws Exception when fixture creation, generation, or artifact inspection fails
   */
  @Test
  void generatesSamForBroadAwsPsmResourceSurface() throws Exception {
    Path sourceModel = tempDir.resolve("broad-aws-psm.xmi");
    Path outputDirectory = tempDir.resolve("broad-generated-project");
    createBroadAwsPsmRenderingModel(sourceModel);
    assertSourceModelReloads(sourceModel);

    EgxGenerationReport report =
        generateOrFail(
            AwsPsmToArtifactsDefaults.request(
                REPOSITORY_ROOT, sourceModel, outputDirectory, true, true));

    assertEquals(GenerationStatus.SUCCEEDED, report.status(), report.diagnostics().toString());
    assertTraceCoversEveryGeneratedFile(outputDirectory, report);
    assertGeneratedJsonArtifactsParse(outputDirectory);
    assertGeneratedYamlArtifactsParseAndHaveExpectedShape(outputDirectory);
    assertBroadSamTemplateCoversResourceSurface(outputDirectory);
    assertBroadAslCoversStateSurface(outputDirectory);
    assertBroadStructuredDocumentsCoverFormats(outputDirectory);
  }

  /**
   * Generates from a minimal valid model and verifies guard-only artifact rules stay silent.
   *
   * @throws Exception when fixture creation, generation, or inspection fails
   */
  @Test
  void honorsEgxGuardsForMinimalAwsPsmModel() throws Exception {
    Path sourceModel = tempDir.resolve("minimal-aws-psm.xmi");
    Path outputDirectory = tempDir.resolve("minimal-generated-project");
    createMinimalGuardAwsPsmModel(sourceModel);
    assertSourceModelReloads(sourceModel);

    EgxGenerationReport report =
        generateOrFail(
            AwsPsmToArtifactsDefaults.request(
                REPOSITORY_ROOT, sourceModel, outputDirectory, true, true));

    assertEquals(GenerationStatus.SUCCEEDED, report.status(), report.diagnostics().toString());
    assertFalse(Files.isRegularFile(outputDirectory.resolve("template.yaml")));
    assertFalse(Files.isDirectory(outputDirectory.resolve("openapi")));
    assertFalse(Files.isDirectory(outputDirectory.resolve("asl")));
    assertFalse(Files.isDirectory(outputDirectory.resolve("src/functions")));
    assertTrue(Files.isRegularFile(outputDirectory.resolve("env/local.json")));
    String trace = Files.readString(outputDirectory.resolve("generated/trace/artifact-trace.json"));
    assertTrue(trace.contains("\"generatedBy\": \"AWSPSM2ART_Stage_Environment\""));
    assertFalse(trace.contains("AWSPSM2ART_Default_Local_Environment"));
    assertFalse(trace.contains("AWSPSM2ART_Stack_To_SamTemplate"));
    assertFalse(trace.contains("AWSPSM2ART_Api_To_OpenApi"));
    assertFalse(trace.contains("AWSPSM2ART_Workflow_To_Asl"));
    assertFalse(trace.contains("AWSPSM2ART_Lambda_To_Handler"));
    assertTraceCoversEveryGeneratedFile(outputDirectory, report);
    assertGeneratedJsonArtifactsParse(outputDirectory);
    assertGeneratedYamlArtifactsParseAndHaveExpectedShape(outputDirectory);
  }

  /**
   * Runs the generated Go test suite with LocalStack endpoint variables when Docker can start the
   * emulator.
   *
   * @throws Exception when generation, LocalStack startup, or generated tests fail
   */
  @Test
  void generatedGoTestsRunAgainstSelectedAwsEmulatorWhenDockerAvailable() throws Exception {
    String goExecutable = commandExecutable("go");
    Assumptions.assumeTrue(goExecutable != null, "Go toolchain is not available.");
    Assumptions.assumeTrue(commandExecutable("docker") != null, "Docker is not available.");

    Path sourceModel = tempDir.resolve("localstack-aws-psm.xmi");
    Path outputDirectory = tempDir.resolve("localstack-generated-project");
    createRepresentativeAwsPsmModel(sourceModel);

    EgxGenerationReport report =
        generateOrFail(
            AwsPsmToArtifactsDefaults.request(
                REPOSITORY_ROOT, sourceModel, outputDirectory, true, true));
    assertEquals(GenerationStatus.SUCCEEDED, report.status(), report.diagnostics().toString());

    LocalStackRuntime localStack = acquireLocalStackRuntime();
    try {
      waitForLocalStack(localStack.containerId(), localStack.endpoint());
      Map<String, String> env = new HashMap<>();
      env.put("AWS_REGION", "us-east-1");
      env.put("AWS_DEFAULT_REGION", "us-east-1");
      env.put("AWS_ENDPOINT_URL", localStack.endpoint());
      env.put("AWS_ACCESS_KEY_ID", "test");
      env.put("AWS_SECRET_ACCESS_KEY", "test");
      runGeneratedGoTests(outputDirectory, goExecutable, env);
    } finally {
      stopDockerContainer(localStack);
    }
  }

  /**
   * Builds, packages, deploys, and exercises a generated AWS project against LocalStack.
   *
   * @throws Exception when generation, deployment, resource inspection, or runtime invocation fails
   */
  @Test
  void deploysGeneratedAwsArtifactsToSelectedEmulatorAndExecutesThem() throws Exception {
    String goExecutable = commandExecutable("go");
    String awsExecutable = commandExecutable("aws");
    String samExecutable = commandExecutable("sam");
    Assumptions.assumeTrue(goExecutable != null, "Go toolchain is not available.");
    Assumptions.assumeTrue(awsExecutable != null, "AWS CLI is not available.");
    Assumptions.assumeTrue(samExecutable != null, "AWS SAM CLI is not available.");
    Assumptions.assumeTrue(commandExecutable("docker") != null, "Docker is not available.");

    Path sourceModel =
        REPOSITORY_ROOT.resolve(
            "packages/java/mde-m2t-runner/src/test/resources/awspsm/e2e/localstack-serverless-system.awspsm.xmi");
    Path outputDirectory = tempDir.resolve("localstack-deployable-generated-project");
    assertSourceModelReloads(sourceModel);

    EgxGenerationReport report =
        generateOrFail(
            AwsPsmToArtifactsDefaults.request(
                REPOSITORY_ROOT, sourceModel, outputDirectory, true, true));
    assertEquals(GenerationStatus.SUCCEEDED, report.status(), report.diagnostics().toString());
    assertGeneratedJsonArtifactsParse(outputDirectory);
    assertGeneratedYamlArtifactsParseAndHaveExpectedShape(outputDirectory);

    LocalStackRuntime localStack = acquireLocalStackRuntime();
    try {
      waitForLocalStack(localStack.containerId(), localStack.endpoint());
      Map<String, String> env = localStackAwsEnvironment(localStack.endpoint());
      deployGeneratedProjectToLocalStack(
          outputDirectory,
          awsExecutable,
          samExecutable,
          goExecutable,
          env,
          localStack.containerId());
      createGeneratedLambdaOnLocalStack(outputDirectory, awsExecutable, env);
      ensureGeneratedFunctionUrlExists(outputDirectory, awsExecutable, env);
      assertGeneratedLocalStackResourcesExist(outputDirectory, awsExecutable, env);
      assertGeneratedServiceFamiliesExecuteOnLocalStack(outputDirectory, awsExecutable, env);
      assertGeneratedHttpEntrypointsExecuteOnLocalStack(outputDirectory, awsExecutable, env);
      assertGeneratedLambdaExecutesOnLocalStack(
          outputDirectory, awsExecutable, env, localStack.containerId());
      Map<String, String> goEnv = new HashMap<>(env);
      goEnv.put("GENERATED_LAMBDA_FUNCTION_NAME", "modriss-localstack-handler");
      runGeneratedGoTests(outputDirectory, goExecutable, goEnv);
    } finally {
      stopDockerContainer(localStack);
    }
  }

  /**
   * Generates artifacts from the repository PSM sample and verifies trace coverage plus
   * duplicate-free SAM parameters.
   *
   * @throws Exception when generation or generated-file inspection fails
   */
  @Test
  void generatesCompleteUniqueTraceForRepositoryPsmSample() throws Exception {
    Path sourceModel = REPOSITORY_ROOT.resolve("mde/samples/psm.xmi");
    Path outputDirectory = tempDir.resolve("sample-generated-project");

    EgxGenerationReport report =
        generateOrFail(
            AwsPsmToArtifactsDefaults.request(
                REPOSITORY_ROOT, sourceModel, outputDirectory, true, true));

    assertEquals(GenerationStatus.SUCCEEDED, report.status(), report.diagnostics().toString());
    assertFalse(
        report.generatedFiles().isEmpty(), "Expected sample generation to write artifacts.");
    assertTraceCoversEveryGeneratedFile(outputDirectory, report);
    assertGeneratedYamlArtifactsParseAndHaveExpectedShape(outputDirectory);
    assertGeneratedSamTemplatesDoNotRepeatParameterKeys(outputDirectory);
    assertGeneratedSamTemplatesUseGeneratedGoArtifacts(outputDirectory);
    assertGeneratedSamTemplatesDoNotRepeatSseSpecification(outputDirectory);
    assertGeneratedSamTemplatesUseValidParameterTypes(outputDirectory);
    assertGeneratedSamTemplatesRenderConcreteResourceProperties(outputDirectory);
    assertGeneratedFilesUseLfLineEndings(outputDirectory);
  }

  /**
   * Catalog AR-06: merge-enabled EGL templates preserve developer-owned protected-region content.
   */
  @Test
  void preservesProtectedRegionsWhenRegeneratingExistingArtifacts() throws Exception {
    Path sourceModel = tempDir.resolve("representative-aws-psm.xmi");
    Path outputDirectory = tempDir.resolve("regenerated-project");
    createRepresentativeAwsPsmModel(sourceModel);

    generateOrFail(
        AwsPsmToArtifactsDefaults.request(
            REPOSITORY_ROOT, sourceModel, outputDirectory, true, true));
    Path protectedFile = outputDirectory.resolve("src/functions/order-handler/handler.go");
    String original = Files.readString(protectedFile);
    int begin = original.indexOf("protected region ");
    int contentStart = original.indexOf('\n', begin) + 1;
    int end = original.indexOf("protected region ", contentStart);
    int endMarkerStart = original.lastIndexOf("/*", end);
    assertTrue(begin >= 0 && contentStart > 0 && endMarkerStart > contentStart);
    String customLogic = "developer-owned protected content";
    Files.writeString(
        protectedFile,
        original.substring(0, contentStart)
            + customLogic
            + "\n"
            + original.substring(endMarkerStart));

    generateOrFail(
        AwsPsmToArtifactsDefaults.request(
            REPOSITORY_ROOT, sourceModel, outputDirectory, false, true));

    String regenerated = Files.readString(protectedFile);
    assertTrue(
        regenerated.contains(customLogic),
        () -> "Protected content was not preserved in " + protectedFile + ":\n" + regenerated);
  }

  /** Catalog AR-07: generator-owned text outside protected regions may be replaced on replay. */
  @Test
  void regenerationReplacesUserTextOutsideProtectedRegions() throws Exception {
    Path sourceModel = tempDir.resolve("owned-text-aws-psm.xmi");
    Path outputDirectory = tempDir.resolve("owned-text-project");
    createRepresentativeAwsPsmModel(sourceModel);
    generateOrFail(
        AwsPsmToArtifactsDefaults.request(
            REPOSITORY_ROOT, sourceModel, outputDirectory, true, true));
    Path protectedFile = outputDirectory.resolve("src/functions/order-handler/handler.go");
    String original = Files.readString(protectedFile);
    String marker = "// user edit outside protected region";
    int firstLineEnd = original.indexOf('\n');
    assertTrue(firstLineEnd >= 0, "Expected generated handler to contain text lines.");
    Files.writeString(
        protectedFile,
        original.substring(0, firstLineEnd + 1)
            + marker
            + "\n"
            + original.substring(firstLineEnd + 1));

    generateOrFail(
        AwsPsmToArtifactsDefaults.request(
            REPOSITORY_ROOT, sourceModel, outputDirectory, false, true));

    assertFalse(
        Files.readString(protectedFile).contains(marker),
        "Generator-owned text outside a protected region must not become implicitly user-owned.");
  }

  /** Catalog AR-08: malformed protected-region markers fail without publishing a partial run. */
  @Test
  void malformedProtectedRegionMarkersAreRejectedSafely() throws Exception {
    Path sourceModel = tempDir.resolve("malformed-regions-aws-psm.xmi");
    Path outputDirectory = tempDir.resolve("malformed-regions-project");
    createRepresentativeAwsPsmModel(sourceModel);
    generateOrFail(
        AwsPsmToArtifactsDefaults.request(
            REPOSITORY_ROOT, sourceModel, outputDirectory, true, true));
    Path handler = outputDirectory.resolve("src/functions/order-handler/handler.go");
    String content = Files.readString(handler);
    String marker =
        content
            .lines()
            .filter(line -> line.contains("protected") || line.contains("PROTECTED"))
            .findFirst()
            .orElseThrow();
    Files.writeString(handler, content.replace(marker, marker + System.lineSeparator() + marker));
    Map<String, byte[]> before = generatedFileBytes(outputDirectory);

    assertThrows(
        EgxGenerationException.class,
        () ->
            new EpsilonEgxGenerator()
                .generate(
                    AwsPsmToArtifactsDefaults.request(
                        REPOSITORY_ROOT, sourceModel, outputDirectory, false, true)));
    Map<String, byte[]> after = generatedFileBytes(outputDirectory);
    assertEquals(before.keySet(), after.keySet());
    before.forEach(
        (path, bytes) ->
            assertArrayEquals(
                bytes, after.get(path), () -> "Published malformed artifact " + path));
  }

  /**
   * Catalog AR-02: unchanged PSM regeneration is byte-equivalent and creates no duplicate paths.
   */
  @Test
  void unchangedPsmRegenerationIsIdempotent() throws Exception {
    Path sourceModel = tempDir.resolve("idempotent-aws-psm.xmi");
    Path outputDirectory = tempDir.resolve("idempotent-project");
    createRepresentativeAwsPsmModel(sourceModel);
    generateOrFail(
        AwsPsmToArtifactsDefaults.request(
            REPOSITORY_ROOT, sourceModel, outputDirectory, true, true));
    Map<String, byte[]> first = generatedFileBytes(outputDirectory);

    generateOrFail(
        AwsPsmToArtifactsDefaults.request(
            REPOSITORY_ROOT, sourceModel, outputDirectory, false, true));
    Map<String, byte[]> second = generatedFileBytes(outputDirectory);

    assertEquals(first.keySet(), second.keySet());
    first.forEach(
        (path, bytes) ->
            assertArrayEquals(bytes, second.get(path), () -> "Changed unchanged artifact " + path));
  }

  /**
   * Catalog AR-03/AR-04/AR-05: artifact paths follow generated resources across add/edit/delete.
   */
  @Test
  void artifactSetTracksGeneratedResourceLifecycle() throws Exception {
    Path sourceModel = tempDir.resolve("lifecycle-aws-psm.xmi");
    Path outputDirectory = tempDir.resolve("lifecycle-project");
    createMinimalGuardAwsPsmModel(sourceModel);
    generateOrFail(
        AwsPsmToArtifactsDefaults.request(
            REPOSITORY_ROOT, sourceModel, outputDirectory, true, true));
    Map<String, byte[]> minimal = generatedFileBytes(outputDirectory);

    createRepresentativeAwsPsmModel(sourceModel);
    generateOrFail(
        AwsPsmToArtifactsDefaults.request(
            REPOSITORY_ROOT, sourceModel, outputDirectory, false, true));
    Map<String, byte[]> expanded = generatedFileBytes(outputDirectory);
    assertTrue(
        expanded.size() > minimal.size(), "Adding generated PSM resources must add artifacts.");
    Path handler = outputDirectory.resolve("src/functions/order-handler/handler.go");
    assertTrue(Files.exists(handler));
    String originalHandler = Files.readString(handler);

    Files.writeString(
        sourceModel,
        Files.readString(sourceModel).replace("Order Handler", "Renamed Order Handler"));
    generateOrFail(
        AwsPsmToArtifactsDefaults.request(
            REPOSITORY_ROOT, sourceModel, outputDirectory, false, true));
    assertNotEquals(originalHandler, Files.readString(handler));

    createMinimalGuardAwsPsmModel(sourceModel);
    generateOrFail(
        AwsPsmToArtifactsDefaults.request(
            REPOSITORY_ROOT, sourceModel, outputDirectory, false, true));
    assertFalse(
        Files.exists(handler), "Deleting a generated PSM resource must remove its owned artifact.");
  }

  /** Catalog AR-09: regeneration never overwrites an independently owned, unknown file. */
  @Test
  void regenerationPreservesIndependentUserFiles() throws Exception {
    Path sourceModel = tempDir.resolve("user-file-aws-psm.xmi");
    Path outputDirectory = tempDir.resolve("user-file-project");
    createRepresentativeAwsPsmModel(sourceModel);
    generateOrFail(
        AwsPsmToArtifactsDefaults.request(
            REPOSITORY_ROOT, sourceModel, outputDirectory, true, true));
    Path userFile = outputDirectory.resolve("notes/developer-owned.txt");
    Files.createDirectories(userFile.getParent());
    Files.writeString(userFile, "developer-owned\n");

    generateOrFail(
        AwsPsmToArtifactsDefaults.request(
            REPOSITORY_ROOT, sourceModel, outputDirectory, false, true));

    assertEquals("developer-owned\n", Files.readString(userFile));
  }

  /** Catalog AR-10/F-11: a later EGL failure does not publish partial temporary output. */
  @Test
  void failedGenerationRetainsPreviouslyPublishedArtifactSet() throws Exception {
    Path sourceModel = tempDir.resolve("atomic-aws-psm.xmi");
    createRepresentativeAwsPsmModel(sourceModel);
    Path outputDirectory = tempDir.resolve("atomic-project");
    Files.createDirectories(outputDirectory);
    Files.writeString(outputDirectory.resolve("published.txt"), "previous release\n");
    Map<String, byte[]> before = generatedFileBytes(outputDirectory);

    Path module = tempDir.resolve("atomic.egx");
    Path templates = tempDir.resolve("atomic-templates");
    Files.createDirectories(templates);
    Files.writeString(
        module,
        "rule First {\n"
            + "  template : \"first.egl\"\n"
            + "  target : \"first.txt\"\n"
            + "}\n"
            + "rule Failing {\n"
            + "  template : \"failing.egl\"\n"
            + "  target : \"failing.txt\"\n"
            + "}\n");
    Files.writeString(templates.resolve("first.egl"), "new partial output\n");
    Files.writeString(templates.resolve("failing.egl"), "[% throw \"deliberate failure\"; %]\n");
    EgxGenerationRequest defaults =
        AwsPsmToArtifactsDefaults.request(
            REPOSITORY_ROOT, sourceModel, outputDirectory, false, true);
    EgxGenerationRequest failing =
        new EgxGenerationRequest(
            module, templates, outputDirectory, defaults.models(), false, true);

    assertThrows(EgxGenerationException.class, () -> new EpsilonEgxGenerator().generate(failing));
    Map<String, byte[]> after = generatedFileBytes(outputDirectory);
    assertEquals(before.keySet(), after.keySet());
    before.forEach(
        (path, bytes) ->
            assertArrayEquals(bytes, after.get(path), () -> "Changed published artifact " + path));
    assertFalse(Files.exists(outputDirectory.resolve("first.txt")));
  }

  /**
   * Keeps the checked-in generator coverage matrix synchronized with EGX rule IDs.
   *
   * @throws Exception when the EGX module or matrix cannot be read
   */
  @Test
  void generatorCoverageMatrixListsEveryEgxRule() throws Exception {
    String matrix =
        Files.readString(
            REPOSITORY_ROOT.resolve("docs/internal/artifacts/aws-psm-code-generator-coverage.md"));

    for (EgxRule rule : egxArtifactRules()) {
      assertTrue(
          Pattern.compile("(?m)^\\|\\s*`" + Pattern.quote(rule.name()) + "`\\s*\\|")
              .matcher(matrix)
              .find(),
          () -> "Coverage matrix missing EGX rule " + rule.name());
      assertTrue(
          matrix.contains("`" + rule.ruleId() + "`"),
          () -> "Coverage matrix missing generator rule id " + rule.ruleId());
      assertTrue(
          matrix.contains("`" + rule.template() + "`"),
          () -> "Coverage matrix missing template " + rule.template());
    }
  }

  /**
   * Verifies that every EGX artifact rule with a traceable rule id ran for the representative
   * fixture.
   *
   * @param outputDirectory generated project root
   * @throws IOException when trace files cannot be read
   */
  private void assertEveryEgxArtifactRuleExecuted(Path outputDirectory) throws IOException {
    String artifactTrace =
        Files.readString(outputDirectory.resolve("generated/trace/artifact-trace.json"));

    for (EgxRule rule : egxArtifactRules()) {
      assertTrue(
          artifactTrace.contains("\"generatedBy\": \"" + rule.ruleId() + "\""),
          () -> "Representative fixture did not execute " + rule.name());
    }
  }

  /**
   * Parses every generated JSON artifact.
   *
   * @param outputDirectory generated project root
   * @throws IOException when generated files cannot be read
   */
  private void assertGeneratedJsonArtifactsParse(Path outputDirectory) throws IOException {
    try (Stream<Path> files = Files.walk(outputDirectory)) {
      for (Path file :
          files
              .filter(Files::isRegularFile)
              .filter(path -> path.getFileName().toString().endsWith(".json"))
              .toList()) {
        JSON.readTree(file.toFile());
      }
    }
  }

  /**
   * Parses every generated YAML artifact and checks domain-specific top-level structure.
   *
   * @param outputDirectory generated project root
   * @throws IOException when generated YAML files cannot be read
   */
  @SuppressWarnings("unchecked")
  private void assertGeneratedYamlArtifactsParseAndHaveExpectedShape(Path outputDirectory)
      throws IOException {
    try (Stream<Path> files = Files.walk(outputDirectory)) {
      for (Path file :
          files
              .filter(Files::isRegularFile)
              .filter(
                  path ->
                      path.getFileName().toString().endsWith(".yaml")
                          || path.getFileName().toString().endsWith(".yml"))
              .toList()) {
        Map<String, Object> yaml = YAML.readValue(file.toFile(), Map.class);
        String normalizedPath = outputDirectory.relativize(file).toString().replace('\\', '/');
        assertFalse(yaml.isEmpty(), () -> "Generated YAML should not be empty: " + normalizedPath);
        if (file.getFileName().toString().startsWith("template")
            && file.getFileName().toString().endsWith(".yaml")) {
          assertTrue(
              yaml.containsKey("Resources"),
              () -> "SAM template missing Resources: " + normalizedPath);
          assertTrue(
              yaml.get("Resources") instanceof Map,
              () -> "SAM Resources must be a map: " + normalizedPath);
          assertTrue(
              ((Map<String, Object>) yaml.get("Resources")).size() > 0,
              () -> "SAM template should contain resources: " + normalizedPath);
          assertFalse(
              Files.readString(file).contains("Runtime: 'nodejs"),
              () -> "SAM template contains stale Node runtime hint: " + normalizedPath);
        }
        if (normalizedPath.startsWith("openapi/")) {
          assertEquals(
              "3.0.3",
              yaml.get("openapi"),
              () -> "Unexpected OpenAPI version in " + normalizedPath);
          assertTrue(
              yaml.get("paths") instanceof Map,
              () -> "OpenAPI paths must be a map: " + normalizedPath);
          Map<String, Object> paths = (Map<String, Object>) yaml.get("paths");
          assertTrue(!paths.isEmpty(), () -> "OpenAPI paths must not be empty: " + normalizedPath);
          assertTrue(
              paths.values().stream()
                  .anyMatch(
                      pathItem ->
                          pathItem instanceof Map && hasOpenApiOperation((Map<?, ?>) pathItem)),
              () -> "OpenAPI should declare at least one operation: " + normalizedPath);
          assertTrue(
              yaml.get("components") instanceof Map,
              () -> "OpenAPI components must be a map: " + normalizedPath);
        }
        if (normalizedPath.startsWith(".github/workflows/")) {
          assertTrue(
              yaml.get("jobs") instanceof Map,
              () -> "GitHub workflow jobs must be a map: " + normalizedPath);
          assertTrue(
              yaml.containsKey("on"), () -> "GitHub workflow missing trigger: " + normalizedPath);
        }
      }
    }
  }

  /**
   * Checks whether a parsed OpenAPI path item contains a standard HTTP operation member.
   *
   * @param pathItem parsed path item
   * @return true when an operation is present
   */
  private boolean hasOpenApiOperation(Map<?, ?> pathItem) {
    return pathItem.keySet().stream()
        .map(Object::toString)
        .anyMatch(
            key ->
                List.of("get", "post", "put", "patch", "delete", "options", "head", "trace")
                    .contains(key));
  }

  /**
   * Checks source-model-specific artifacts that are generated by API, messaging, events, and
   * document rules.
   *
   * @param outputDirectory generated project root
   * @throws IOException when generated files cannot be inspected
   */
  private void assertGeneratedApiMessagingEventAndDocumentArtifactsCoverSourceModel(
      Path outputDirectory) throws IOException {
    Path openApi = outputDirectory.resolve("openapi/orders-api.openapi.yaml");
    assertTrue(Files.isRegularFile(openApi), "Expected modeled API OpenAPI artifact.");
    String openApiText = Files.readString(openApi);
    assertTrue(openApiText.contains("openapi: 3.0.3"));
    assertTrue(openApiText.contains("title: 'Orders API'"));
    assertTrue(openApiText.contains("/orders:"));
    assertTrue(openApiText.contains("post:"));
    assertTrue(openApiText.contains("operationId: 'createOrder'"));
    assertTrue(openApiText.contains("aws_iam: []"));
    assertTrue(openApiText.contains("OrderRequest: {\"type\":\"object\"}"));
    assertTrue(openApiText.contains("OrderAccepted: {\"type\":\"object\"}"));
    assertTrue(openApiText.contains("lambda:path/2015-03-31/functions/"));
    assertTrue(openApiText.contains("/invocations"));

    assertJsonArtifactContains(
        outputDirectory.resolve("events/samples/rule-order-created.json"),
        "\"source\": \"modriss.eventbridge\"",
        "\"detail-type\": \"EventBridgeRule\"",
        "\"sourceStableId\":");
    assertJsonArtifactContains(
        outputDirectory.resolve("events/samples/queue-order-work-sqs-message.json"),
        "\"source\": \"modriss.sqs\"",
        "\"detail-type\": \"SqsQueue\"",
        "\"sourceStableId\":");
    assertJsonArtifactContains(
        outputDirectory.resolve("events/samples/topic-order-events-sns-message.json"),
        "\"source\": \"modriss.sns\"",
        "\"detail-type\": \"SnsTopic\"",
        "\"sourceStableId\":");

    Path structuredDocument = outputDirectory.resolve("generated/documents/operator-note.md");
    assertTrue(Files.isRegularFile(structuredDocument));
    assertTrue(Files.readString(structuredDocument).contains("Representative operator note."));
  }

  /**
   * Checks that the broad fixture rendered every expected resource family into SAM/CloudFormation.
   *
   * @param outputDirectory generated project root
   * @throws IOException when generated template cannot be read
   */
  private void assertBroadSamTemplateCoversResourceSurface(Path outputDirectory)
      throws IOException {
    Path template = outputDirectory.resolve("template.yaml");
    String samText = Files.readString(template);

    for (String expected :
        List.of(
            "BroadLambdaRole:\n    Type: AWS::IAM::Role",
            "BroadManagedPolicy:\n    Type: AWS::IAM::ManagedPolicy",
            "BroadInlinePolicy:\n    Type: AWS::IAM::Policy",
            "BroadKey:\n    Type: AWS::KMS::Key",
            "BroadKeyAlias:\n    Type: AWS::KMS::Alias",
            "BroadSecret:\n    Type: AWS::SecretsManager::Secret",
            "BroadSecretPolicy:\n    Type: AWS::SecretsManager::ResourcePolicy",
            "BroadSecretRotation:\n    Type: AWS::SecretsManager::RotationSchedule",
            "BroadParameter:\n    Type: AWS::SSM::Parameter",
            "BroadVpc:\n    Type: AWS::EC2::VPC",
            "BroadSubnet:\n    Type: AWS::EC2::Subnet",
            "BroadSecurityGroup:\n    Type: AWS::EC2::SecurityGroup",
            "BroadVpcEndpoint:\n    Type: AWS::EC2::VPCEndpoint",
            "BroadTable:\n    Type: AWS::DynamoDB::Table",
            "BroadBucket:\n    Type: AWS::S3::Bucket",
            "BroadBucketPolicy:\n    Type: AWS::S3::BucketPolicy",
            "BroadQueue:\n    Type: AWS::SQS::Queue",
            "BroadQueuePolicy:\n    Type: AWS::SQS::QueuePolicy",
            "BroadTopic:\n    Type: AWS::SNS::Topic",
            "BroadSubscription:\n    Type: AWS::SNS::Subscription",
            "BroadTopicPolicy:\n    Type: AWS::SNS::TopicPolicy",
            "BroadEventBus:\n    Type: AWS::Events::EventBus",
            "BroadEventBusPolicy:\n    Type: AWS::Events::EventBusPolicy",
            "BroadEventRule:\n    Type: AWS::Events::Rule",
            "BroadEventArchive:\n    Type: AWS::Events::Archive",
            "BroadSchedule:\n    Type: AWS::Scheduler::Schedule",
            "BroadPipe:\n    Type: AWS::Pipes::Pipe",
            "BroadConnection:\n    Type: AWS::Events::Connection",
            "BroadApiDestination:\n    Type: AWS::Events::ApiDestination",
            "BroadUserPool:\n    Type: AWS::Cognito::UserPool",
            "BroadUserPoolClient:\n    Type: AWS::Cognito::UserPoolClient",
            "BroadUserPoolGroup:\n    Type: AWS::Cognito::UserPoolGroup",
            "BroadUserPoolDomain:\n    Type: AWS::Cognito::UserPoolDomain",
            "BroadIdentityPool:\n    Type: AWS::Cognito::IdentityPool",
            "BroadLogGroup:\n    Type: AWS::Logs::LogGroup",
            "BroadMetricFilter:\n    Type: AWS::Logs::MetricFilter",
            "BroadSubscriptionFilter:\n    Type: AWS::Logs::SubscriptionFilter",
            "BroadAlarm:\n    Type: AWS::CloudWatch::Alarm",
            "BroadCompositeAlarm:\n    Type: AWS::CloudWatch::CompositeAlarm",
            "BroadDashboard:\n    Type: AWS::CloudWatch::Dashboard",
            "BroadLayer:\n    Type: AWS::Serverless::LayerVersion",
            "BroadLayerPermission:\n    Type: AWS::Lambda::LayerVersionPermission",
            "BroadFunction:\n    Type: AWS::Serverless::Function",
            "BroadFunctionVersion:\n    Type: AWS::Lambda::Version",
            "BroadFunctionAlias:\n    Type: AWS::Lambda::Alias",
            "BroadFunctionUrl:\n    Type: AWS::Lambda::Url",
            "BroadInvokeConfig:\n    Type: AWS::Lambda::EventInvokeConfig",
            "BroadSqsMapping:\n    Type: AWS::Lambda::EventSourceMapping",
            "BroadDdbMapping:\n    Type: AWS::Lambda::EventSourceMapping",
            "BroadGenericMapping:\n    Type: AWS::Lambda::EventSourceMapping",
            "BroadPermission:\n    Type: AWS::Lambda::Permission",
            "BroadHttpApi:\n    Type: AWS::Serverless::HttpApi",
            "BroadRestApi:\n    Type: AWS::Serverless::Api",
            "BroadWebSocketApi:\n    Type: AWS::ApiGatewayV2::Api",
            "BroadWsIntegration:\n    Type: AWS::ApiGatewayV2::Integration",
            "BroadWsRoute:\n    Type: AWS::ApiGatewayV2::Route",
            "BroadWsStage:\n    Type: AWS::ApiGatewayV2::Stage",
            "BroadJwtAuthorizer:\n    Type: AWS::ApiGatewayV2::Authorizer",
            "BroadDomain:\n    Type: AWS::ApiGatewayV2::DomainName",
            "BroadBasePathMapping:\n    Type: AWS::ApiGatewayV2::ApiMapping",
            "BroadApiKey:\n    Type: AWS::ApiGateway::ApiKey",
            "BroadUsagePlan:\n    Type: AWS::ApiGateway::UsagePlan",
            "BroadUsagePlanKey:\n    Type: AWS::ApiGateway::UsagePlanKey",
            "BroadDeployment:\n    Type: AWS::ApiGateway::Deployment",
            "BroadWafAssociation:\n    Type: AWS::WAFv2::WebACLAssociation",
            "BroadStateMachine:\n    Type: AWS::Serverless::StateMachine",
            "BroadNativeBucket:\n    Type: AWS::S3::Bucket")) {
      assertTrue(samText.contains(expected), () -> "Missing SAM evidence: " + expected);
    }

    for (String expectedBranch :
        List.of(
            "DeadLetterQueue:",
            "VpcConfig:",
            "FileSystemConfigs:",
            "FunctionResponseTypes:",
            "FilterCriteria:",
            "EventSourceArn: !GetAtt BroadTable.StreamArn",
            "SSESpecification:",
            "NotificationConfiguration:",
            "LambdaConfigurations:",
            "QueueConfigurations:",
            "TopicConfigurations:",
            "FilterPolicyScope: 'MessageBody'",
            "AuthParameters:",
            "ConnectionArn:",
            "DefinitionSubstitutions:",
            "Logging:",
            "Tracing:",
            "CodeUri: 'bin/broad-function'",
            "Runtime: 'provided.al2023'",
            "Handler: 'bootstrap'")) {
      assertTrue(
          samText.contains(expectedBranch), () -> "Missing branch evidence " + expectedBranch);
    }
  }

  /**
   * Verifies broad ASL output covers all Step Functions state variants and branch structures.
   *
   * @param outputDirectory generated project root
   * @throws IOException when generated ASL cannot be read
   */
  private void assertBroadAslCoversStateSurface(Path outputDirectory) throws IOException {
    Path aslFile = outputDirectory.resolve("asl/broad-workflow.asl.json");
    assertTrue(Files.isRegularFile(aslFile), "Expected broad ASL artifact.");
    String aslText = Files.readString(aslFile);
    JSON.readTree(aslFile.toFile());
    for (String expected :
        List.of(
            "\"Type\": \"Pass\"",
            "\"Type\": \"Task\"",
            "\"Type\": \"Choice\"",
            "\"Type\": \"Wait\"",
            "\"Type\": \"Succeed\"",
            "\"Type\": \"Fail\"",
            "\"Type\": \"Parallel\"",
            "\"Type\": \"Map\"",
            "\"Retry\": [",
            "\"Catch\": [",
            "\"Choices\": [",
            "\"Branches\": [",
            "\"ItemProcessor\":")) {
      assertTrue(aslText.contains(expected), () -> "Missing ASL evidence " + expected);
    }
  }

  /**
   * Verifies broad structured document fixtures cover JSON, YAML, and text rendering paths.
   *
   * @param outputDirectory generated project root
   * @throws IOException when generated documents cannot be read
   */
  private void assertBroadStructuredDocumentsCoverFormats(Path outputDirectory) throws IOException {
    Path jsonDocument = outputDirectory.resolve("generated/documents/broad-json-document.json");
    Path yamlDocument = outputDirectory.resolve("generated/documents/broad-yaml-document.yaml");
    Path textDocument = outputDirectory.resolve("generated/documents/broad-text-document.txt");
    assertTrue(Files.isRegularFile(jsonDocument), "Expected JSON structured document.");
    assertTrue(Files.isRegularFile(yamlDocument), "Expected YAML structured document.");
    assertTrue(Files.isRegularFile(textDocument), "Expected text structured document.");
    assertEquals("true", JSON.readTree(jsonDocument.toFile()).get("broad").asText());
    assertTrue(Files.readString(yamlDocument).contains("broad: true"));
    assertTrue(Files.readString(textDocument).contains("Broad text document."));
  }

  /**
   * Runs generated Go tests when a Go toolchain is installed in the executing environment.
   *
   * @param outputDirectory generated project root
   * @throws Exception when the toolchain is present and generated tests fail
   */
  private void assertGeneratedGoProjectTestsPassWhenToolchainExists(Path outputDirectory)
      throws Exception {
    String goExecutable = commandExecutable("go");
    if (goExecutable == null) {
      return;
    }

    runGeneratedGoTests(outputDirectory, goExecutable, Map.of());
  }

  /**
   * Runs generated Go dependency resolution and tests with optional environment overrides.
   *
   * @param outputDirectory generated project root
   * @param goExecutable Go executable path
   * @param environment environment variables for generated tests
   * @throws Exception when command execution fails
   */
  private void runGeneratedGoTests(
      Path outputDirectory, String goExecutable, Map<String, String> environment) throws Exception {
    Map<String, String> goEnvironment = writableGoCacheEnvironment(outputDirectory, environment);
    ProcessResult tidyResult =
        runProcessCapturing(
            processBuilder(outputDirectory, goEnvironment, goExecutable, "mod", "tidy"),
            Duration.ofMinutes(2));
    Assumptions.assumeTrue(tidyResult.finished(), "Generated Go dependency resolution timed out.");
    Assumptions.assumeFalse(
        tidyResult.exitCode() != 0 && looksLikeGoModuleNetworkFailure(tidyResult.output()),
        () -> "Generated Go module dependencies are not reachable: " + tidyResult.output());
    assertEquals(0, tidyResult.exitCode(), tidyResult.output());

    ProcessBuilder testBuilder =
        processBuilder(outputDirectory, goEnvironment, goExecutable, "test", "./...");
    runProcess(testBuilder, Duration.ofMinutes(2), "Generated Go test run");
  }

  private Map<String, String> writableGoCacheEnvironment(
      Path outputDirectory, Map<String, String> environment) throws IOException {
    Path goCache = outputDirectory.resolve(".gocache");
    Path goModCache = outputDirectory.resolve(".gomodcache");
    Files.createDirectories(goCache);
    Files.createDirectories(goModCache);
    Map<String, String> result = new HashMap<>(environment);
    result.putIfAbsent("GOCACHE", goCache.toString());
    result.putIfAbsent("GOMODCACHE", goModCache.toString());
    return result;
  }

  private boolean looksLikeGoModuleNetworkFailure(String outputText) {
    return outputText.contains("git ls-remote")
        || outputText.contains("unable to access 'https://")
        || outputText.contains("no such host")
        || outputText.contains("i/o timeout")
        || outputText.contains("TLS handshake timeout")
        || outputText.contains("connection refused")
        || outputText.contains("connection reset")
        || outputText.contains("dial tcp")
        || outputText.contains("wsarecv")
        || outputText.contains("connected host did not properly respond")
        || outputText.contains("context deadline exceeded")
        || outputText.contains("proxyconnect tcp")
        || outputText.contains("The requested URL returned error")
        || outputText.contains("Proxy Error")
        || outputText.contains("unrecognized import path");
  }

  /**
   * Builds, packages, and deploys the generated SAM project into LocalStack CloudFormation.
   *
   * @param outputDirectory generated project root
   * @param awsExecutable AWS CLI executable
   * @param samExecutable SAM CLI executable
   * @param environment AWS/LocalStack environment
   * @throws Exception when build, package, or deploy fails
   */
  private void deployGeneratedProjectToLocalStack(
      Path outputDirectory,
      String awsExecutable,
      String samExecutable,
      String goExecutable,
      Map<String, String> environment,
      String containerId)
      throws Exception {
    String endpoint = environment.get("AWS_ENDPOINT_URL");
    String bucketName = "modriss-m2t-package-" + UUID.randomUUID().toString().replace("-", "");
    compileGeneratedLambdaBootstrap(outputDirectory, goExecutable, environment);
    Path ddbItemFile = outputDirectory.resolve("localstack-ddb-item.json");
    Files.writeString(
        ddbItemFile, "{\"pk\":{\"S\":\"ORDER#live\"},\"status\":{\"S\":\"accepted\"}}");
    runProcess(
        processBuilder(
            outputDirectory,
            environment,
            samExecutable,
            "validate",
            "--template-file",
            "template.yaml"),
        Duration.ofMinutes(1),
        "Generated SAM template validation");
    runProcess(
        processBuilder(
            outputDirectory,
            environment,
            awsExecutable,
            "--endpoint-url",
            endpoint,
            "s3",
            "mb",
            "s3://" + bucketName),
        Duration.ofMinutes(1),
        "LocalStack package bucket creation");

    packageGeneratedLambdaZipForLocalStack(outputDirectory);
    runProcess(
        processBuilder(
            outputDirectory,
            environment,
            awsExecutable,
            "--endpoint-url",
            endpoint,
            "s3",
            "cp",
            "runtime-handler.zip",
            "s3://" + bucketName + "/runtime-handler.zip"),
        Duration.ofMinutes(1),
        "Generated Lambda zip upload to LocalStack S3");
    uploadGeneratedAslDefinitions(outputDirectory, awsExecutable, environment, bucketName);
    writeLocalStackPackagedTemplate(outputDirectory, bucketName);
    deleteExistingLocalStackStack(
        outputDirectory, awsExecutable, environment, "modriss-localstack-runtime");

    ProcessResult createResult =
        runProcessCapturing(
            processBuilder(
                outputDirectory,
                environment,
                awsExecutable,
                "--endpoint-url",
                endpoint,
                "cloudformation",
                "create-stack",
                "--stack-name",
                "modriss-localstack-runtime",
                "--template-body",
                "file://packaged-template.yaml",
                "--capabilities",
                "CAPABILITY_IAM",
                "CAPABILITY_NAMED_IAM",
                "CAPABILITY_AUTO_EXPAND",
                "--disable-rollback"),
            Duration.ofMinutes(1));
    if (createResult.exitCode() != 0) {
      fail("Generated CloudFormation create-stack failed:\n" + createResult.output());
    }

    ProcessResult waitResult =
        runProcessCapturing(
            processBuilder(
                outputDirectory,
                environment,
                awsExecutable,
                "--endpoint-url",
                endpoint,
                "cloudformation",
                "wait",
                "stack-create-complete",
                "--stack-name",
                "modriss-localstack-runtime"),
            Duration.ofMinutes(8));
    if (waitResult.exitCode() != 0) {
      String events =
          runProcessCapturing(
                  processBuilder(
                      outputDirectory,
                      environment,
                      awsExecutable,
                      "--endpoint-url",
                      endpoint,
                      "cloudformation",
                      "describe-stack-events",
                      "--stack-name",
                      "modriss-localstack-runtime"),
                  Duration.ofMinutes(1))
              .output();
      fail(
          "Generated CloudFormation stack did not reach CREATE_COMPLETE:\n"
              + waitResult.output()
              + "\nStack events:\n"
              + events
              + "\nLocalStack logs:\n"
              + dockerContainerLogs(containerId));
    }
  }

  /**
   * Deletes a previous failed/succeeded test stack so repeated LocalStack runs use fresh generated
   * resources.
   *
   * @param outputDirectory generated project root
   * @param awsExecutable AWS CLI executable
   * @param environment AWS/LocalStack environment
   * @param stackName CloudFormation stack name
   * @throws Exception when deletion fails unexpectedly
   */
  private void deleteExistingLocalStackStack(
      Path outputDirectory, String awsExecutable, Map<String, String> environment, String stackName)
      throws Exception {
    String endpoint = environment.get("AWS_ENDPOINT_URL");
    ProcessResult describe =
        runProcessCapturing(
            processBuilder(
                outputDirectory,
                environment,
                awsExecutable,
                "--endpoint-url",
                endpoint,
                "cloudformation",
                "describe-stacks",
                "--stack-name",
                stackName),
            Duration.ofMinutes(1));
    if (describe.exitCode() != 0) {
      return;
    }
    // The live fixture deliberately writes an object. AWS and Floci both reject deletion of a
    // non-empty modeled bucket, so empty it before deleting the previous test stack.
    runProcessCapturing(
        processBuilder(
            outputDirectory,
            environment,
            awsExecutable,
            "--endpoint-url",
            endpoint,
            "s3",
            "rm",
            "s3://modriss-localstack-bucket",
            "--recursive"),
        Duration.ofMinutes(1));
    runProcess(
        processBuilder(
            outputDirectory,
            environment,
            awsExecutable,
            "--endpoint-url",
            endpoint,
            "cloudformation",
            "delete-stack",
            "--stack-name",
            stackName),
        Duration.ofMinutes(1),
        "Previous generated LocalStack stack deletion");
    runProcess(
        processBuilder(
            outputDirectory,
            environment,
            awsExecutable,
            "--endpoint-url",
            endpoint,
            "cloudformation",
            "wait",
            "stack-delete-complete",
            "--stack-name",
            stackName),
        Duration.ofMinutes(6),
        "Previous generated LocalStack stack delete wait");
  }

  /**
   * Builds the generated Go Lambda handler into the bootstrap file expected by provided.al2023.
   *
   * @param outputDirectory generated project root
   * @param goExecutable Go executable
   * @param environment command environment
   * @throws Exception when compilation fails
   */
  private void compileGeneratedLambdaBootstrap(
      Path outputDirectory, String goExecutable, Map<String, String> environment) throws Exception {
    runProcess(
        processBuilder(outputDirectory, environment, goExecutable, "mod", "tidy"),
        Duration.ofMinutes(2),
        "Generated Go dependency resolution before deploy");
    Path functionsDirectory = outputDirectory.resolve("src/functions");
    Path functionDirectory;
    try (Stream<Path> functionDirectories = Files.list(functionsDirectory)) {
      functionDirectory =
          functionDirectories
              .filter(Files::isDirectory)
              .findFirst()
              .orElseThrow(
                  () -> new AssertionError("No generated Lambda function directory found."));
    }
    String functionPackage =
        "./" + outputDirectory.relativize(functionDirectory).toString().replace('\\', '/');
    ProcessBuilder buildBuilder =
        processBuilder(
            outputDirectory,
            environment,
            goExecutable,
            "build",
            "-o",
            "bootstrap",
            functionPackage);
    buildBuilder.environment().put("GOOS", "linux");
    buildBuilder.environment().put("GOARCH", "amd64");
    buildBuilder.environment().put("CGO_ENABLED", "0");
    runProcess(buildBuilder, Duration.ofMinutes(3), "Generated Lambda bootstrap compilation");
    assertTrue(Files.isRegularFile(outputDirectory.resolve("bootstrap")));
  }

  /**
   * Packages the generated bootstrap with Lambda-compatible executable file mode.
   *
   * @param outputDirectory generated project root
   * @throws IOException when zip creation fails
   */
  private void packageGeneratedLambdaZipForLocalStack(Path outputDirectory) throws IOException {
    Path bootstrap = outputDirectory.resolve("bootstrap");
    Path zipFile = outputDirectory.resolve("runtime-handler.zip");
    try (ZipArchiveOutputStream zipOutput =
        new ZipArchiveOutputStream(Files.newOutputStream(zipFile))) {
      ZipArchiveEntry entry = new ZipArchiveEntry("bootstrap");
      entry.setUnixMode(0755);
      entry.setSize(Files.size(bootstrap));
      zipOutput.putArchiveEntry(entry);
      Files.copy(bootstrap, zipOutput);
      zipOutput.closeArchiveEntry();
    }
  }

  /**
   * Writes a LocalStack deploy template that keeps generated SAM content but points Lambda CodeUri
   * at the uploaded S3 zip.
   *
   * @param outputDirectory generated project root
   * @param bucketName LocalStack package bucket
   * @throws IOException when template rewriting fails
   */
  private void writeLocalStackPackagedTemplate(Path outputDirectory, String bucketName)
      throws IOException {
    String templateText = Files.readString(outputDirectory.resolve("template.yaml"));
    Matcher codeUriMatcher =
        Pattern.compile("(?m)^(\\s*)CodeUri:\\s*['\"]?[^'\"\\s]+['\"]?\\s*$").matcher(templateText);
    if (!codeUriMatcher.find()) {
      Files.writeString(outputDirectory.resolve("packaged-template.yaml"), templateText);
      return;
    }
    String indent = codeUriMatcher.group(1);
    String packagedCodeUri = indent + "CodeUri: s3://" + bucketName + "/runtime-handler.zip";
    String packagedTemplate =
        codeUriMatcher.replaceFirst(Matcher.quoteReplacement(packagedCodeUri));
    Matcher definitionUriMatcher =
        Pattern.compile("(?m)^(\\s*)DefinitionUri:\\s*['\"]?(asl/[^'\"\\s]+)['\"]?\\s*$")
            .matcher(packagedTemplate);
    StringBuffer rewrittenTemplate = new StringBuffer();
    while (definitionUriMatcher.find()) {
      String definitionUri =
          definitionUriMatcher.group(1)
              + "DefinitionUri: s3://"
              + bucketName
              + "/"
              + definitionUriMatcher.group(2);
      definitionUriMatcher.appendReplacement(
          rewrittenTemplate, Matcher.quoteReplacement(definitionUri));
    }
    definitionUriMatcher.appendTail(rewrittenTemplate);
    packagedTemplate = rewrittenTemplate.toString();
    assertTrue(
        packagedTemplate.contains("runtime-handler.zip"),
        "Packaged LocalStack SAM template should reference uploaded Lambda zip.");
    Files.writeString(outputDirectory.resolve("packaged-template.yaml"), packagedTemplate);
  }

  /**
   * Uploads generated ASL files so SAM/CloudFormation can resolve DefinitionUri during deployment.
   *
   * @param outputDirectory generated project root
   * @param awsExecutable AWS CLI executable
   * @param environment AWS/LocalStack environment
   * @param bucketName package bucket
   * @throws Exception when an upload fails
   */
  private void uploadGeneratedAslDefinitions(
      Path outputDirectory,
      String awsExecutable,
      Map<String, String> environment,
      String bucketName)
      throws Exception {
    Path aslDirectory = outputDirectory.resolve("asl");
    if (!Files.isDirectory(aslDirectory)) {
      return;
    }
    try (Stream<Path> aslFiles = Files.walk(aslDirectory)) {
      for (Path aslFile : aslFiles.filter(Files::isRegularFile).sorted().toList()) {
        String key = outputDirectory.relativize(aslFile).toString().replace('\\', '/');
        runProcess(
            processBuilder(
                outputDirectory,
                environment,
                awsExecutable,
                "--endpoint-url",
                environment.get("AWS_ENDPOINT_URL"),
                "s3",
                "cp",
                key,
                "s3://" + bucketName + "/" + key),
            Duration.ofMinutes(1),
            "Generated ASL upload to LocalStack S3");
      }
    }
  }

  /**
   * Verifies deployed generated resources are visible through LocalStack service APIs.
   *
   * @param outputDirectory generated project root
   * @param awsExecutable AWS CLI executable
   * @param environment AWS/LocalStack environment
   * @throws Exception when inspection fails
   */
  private void assertGeneratedLocalStackResourcesExist(
      Path outputDirectory, String awsExecutable, Map<String, String> environment)
      throws Exception {
    String endpoint = environment.get("AWS_ENDPOINT_URL");
    runProcess(
        processBuilder(
            outputDirectory,
            environment,
            awsExecutable,
            "--endpoint-url",
            endpoint,
            "lambda",
            "get-function",
            "--function-name",
            "modriss-localstack-handler"),
        Duration.ofMinutes(1),
        "Generated Lambda function exists in LocalStack");
    runProcess(
        processBuilder(
            outputDirectory,
            environment,
            awsExecutable,
            "--endpoint-url",
            endpoint,
            "lambda",
            "get-function-url-config",
            "--function-name",
            "modriss-localstack-handler"),
        Duration.ofMinutes(1),
        "Generated Lambda function URL exists in LocalStack");
    runProcess(
        processBuilder(
            outputDirectory,
            environment,
            awsExecutable,
            "--endpoint-url",
            endpoint,
            "sqs",
            "get-queue-url",
            "--queue-name",
            "modriss-localstack-queue"),
        Duration.ofMinutes(1),
        "Generated SQS queue exists in LocalStack");
    runProcess(
        processBuilder(
            outputDirectory,
            environment,
            awsExecutable,
            "--endpoint-url",
            endpoint,
            "dynamodb",
            "describe-table",
            "--table-name",
            "modriss-localstack-table"),
        Duration.ofMinutes(1),
        "Generated DynamoDB table exists in LocalStack");
    runProcess(
        processBuilder(
            outputDirectory,
            environment,
            awsExecutable,
            "--endpoint-url",
            endpoint,
            "s3api",
            "head-bucket",
            "--bucket",
            "modriss-localstack-bucket"),
        Duration.ofMinutes(1),
        "Generated S3 bucket exists in LocalStack");
    runProcess(
        processBuilder(
            outputDirectory,
            environment,
            awsExecutable,
            "--endpoint-url",
            endpoint,
            "ssm",
            "get-parameter",
            "--name",
            "/modriss/localstack/mode"),
        Duration.ofMinutes(1),
        "Generated SSM parameter exists in LocalStack");
    runProcess(
        processBuilder(
            outputDirectory,
            environment,
            awsExecutable,
            "--endpoint-url",
            endpoint,
            "secretsmanager",
            "describe-secret",
            "--secret-id",
            "modriss/localstack/secret"),
        Duration.ofMinutes(1),
        "Generated Secrets Manager secret exists in LocalStack");
    runProcess(
        processBuilder(
            outputDirectory,
            environment,
            awsExecutable,
            "--endpoint-url",
            endpoint,
            "events",
            "describe-event-bus",
            "--name",
            "modriss-localstack-bus"),
        Duration.ofMinutes(1),
        "Generated EventBridge bus exists in LocalStack");
    String apis =
        runProcessForOutput(
            processBuilder(
                outputDirectory,
                environment,
                awsExecutable,
                "--endpoint-url",
                endpoint,
                "apigatewayv2",
                "get-apis"),
            Duration.ofMinutes(1),
            "Generated API Gateway HTTP API inspection");
    assertTrue(
        apis.contains("Runtime HTTP API") || apis.contains("RuntimeHttpApi"),
        "Generated HTTP API should exist in the selected AWS emulator.");
    String stateMachines =
        runProcessForOutput(
            processBuilder(
                outputDirectory,
                environment,
                awsExecutable,
                "--endpoint-url",
                endpoint,
                "stepfunctions",
                "list-state-machines"),
            Duration.ofMinutes(1),
            "Generated Step Functions state machine inspection");
    if (!stateMachines.contains("modriss-localstack-workflow")) {
      assertTrue(
          Files.readString(outputDirectory.resolve("template.yaml"))
              .contains("Type: AWS::Serverless::StateMachine"),
          "Generated SAM template should include the Step Functions state machine even when "
              + "LocalStack Community omits it during CloudFormation deployment.");
    }
    String topics =
        runProcessForOutput(
            processBuilder(
                outputDirectory,
                environment,
                awsExecutable,
                "--endpoint-url",
                endpoint,
                "sns",
                "list-topics"),
            Duration.ofMinutes(1),
            "Generated SNS topic inspection");
    assertTrue(topics.contains("modriss-localstack-topic"), "Generated SNS topic should exist.");
  }

  /**
   * Executes service-level API calls against resources created from the generated template.
   *
   * @param outputDirectory generated project root
   * @param awsExecutable AWS CLI executable
   * @param environment AWS/LocalStack environment
   * @throws Exception when a service call fails
   */
  private void assertGeneratedServiceFamiliesExecuteOnLocalStack(
      Path outputDirectory, String awsExecutable, Map<String, String> environment)
      throws Exception {
    String endpoint = environment.get("AWS_ENDPOINT_URL");
    String queueUrl =
        JSON.readTree(
                runProcessForOutput(
                    processBuilder(
                        outputDirectory,
                        environment,
                        awsExecutable,
                        "--endpoint-url",
                        endpoint,
                        "sqs",
                        "get-queue-url",
                        "--queue-name",
                        "modriss-localstack-queue"),
                    Duration.ofMinutes(1),
                    "Generated SQS queue URL lookup"))
            .get("QueueUrl")
            .asText();
    runProcess(
        processBuilder(
            outputDirectory,
            environment,
            awsExecutable,
            "--endpoint-url",
            endpoint,
            "sqs",
            "send-message",
            "--queue-url",
            queueUrl,
            "--message-body",
            "{\"kind\":\"generated-live-check\"}"),
        Duration.ofMinutes(1),
        "Generated SQS queue accepts messages");
    String messages =
        runProcessForOutput(
            processBuilder(
                outputDirectory,
                environment,
                awsExecutable,
                "--endpoint-url",
                endpoint,
                "sqs",
                "receive-message",
                "--queue-url",
                queueUrl,
                "--max-number-of-messages",
                "1"),
            Duration.ofMinutes(1),
            "Generated SQS queue returns messages");
    assertTrue(
        messages.contains("generated-live-check"), "Generated SQS message should round trip.");

    Path ddbItemFile = outputDirectory.resolve("localstack-ddb-item.json");
    Files.writeString(
        ddbItemFile, "{\"pk\":{\"S\":\"ORDER#live\"},\"status\":{\"S\":\"accepted\"}}");
    runProcess(
        processBuilder(
            outputDirectory,
            environment,
            awsExecutable,
            "--endpoint-url",
            endpoint,
            "dynamodb",
            "put-item",
            "--table-name",
            "modriss-localstack-table",
            "--item",
            "file://" + ddbItemFile),
        Duration.ofMinutes(1),
        "Generated DynamoDB table accepts an item");
    Path ddbKeyFile = outputDirectory.resolve("localstack-ddb-key.json");
    Files.writeString(ddbKeyFile, "{\"pk\":{\"S\":\"ORDER#live\"}}");
    String item =
        runProcessForOutput(
            processBuilder(
                outputDirectory,
                environment,
                awsExecutable,
                "--endpoint-url",
                endpoint,
                "dynamodb",
                "get-item",
                "--table-name",
                "modriss-localstack-table",
                "--key",
                "file://" + ddbKeyFile),
            Duration.ofMinutes(1),
            "Generated DynamoDB table returns an item");
    assertTrue(item.contains("accepted"), "Generated DynamoDB item should be readable.");

    Path objectFile = outputDirectory.resolve("localstack-s3-object.txt");
    Files.writeString(objectFile, "generated S3 live check");
    Path eventEntriesFile = outputDirectory.resolve("localstack-eventbridge-entries.json");
    Files.writeString(
        eventEntriesFile,
        "[{\"Source\":\"modriss.e2e\",\"DetailType\":\"GeneratedLiveCheck\",\"Detail\":\"{}\",\"EventBusName\":\"modriss-localstack-bus\"}]");
    runProcess(
        processBuilder(
            outputDirectory,
            environment,
            awsExecutable,
            "--endpoint-url",
            endpoint,
            "s3",
            "cp",
            objectFile.toString(),
            "s3://modriss-localstack-bucket/live-check.txt"),
        Duration.ofMinutes(1),
        "Generated S3 bucket accepts objects");
    String s3Objects =
        runProcessForOutput(
            processBuilder(
                outputDirectory,
                environment,
                awsExecutable,
                "--endpoint-url",
                endpoint,
                "s3api",
                "list-objects-v2",
                "--bucket",
                "modriss-localstack-bucket"),
            Duration.ofMinutes(1),
            "Generated S3 bucket lists objects");
    assertTrue(s3Objects.contains("live-check.txt"), "Generated S3 object should be listed.");

    String parameter =
        runProcessForOutput(
            processBuilder(
                outputDirectory,
                environment,
                awsExecutable,
                "--endpoint-url",
                endpoint,
                "ssm",
                "get-parameter",
                "--name",
                "/modriss/localstack/mode"),
            Duration.ofMinutes(1),
            "Generated SSM parameter returns a value");
    assertTrue(parameter.contains("e2e"), "Generated SSM parameter value should be readable.");

    String secret =
        runProcessForOutput(
            processBuilder(
                outputDirectory,
                environment,
                awsExecutable,
                "--endpoint-url",
                endpoint,
                "secretsmanager",
                "get-secret-value",
                "--secret-id",
                "modriss/localstack/secret"),
            Duration.ofMinutes(1),
            "Generated Secrets Manager secret returns a value");
    assertTrue(secret.contains("generated"), "Generated secret value should be readable.");

    String topics =
        runProcessForOutput(
            processBuilder(
                outputDirectory,
                environment,
                awsExecutable,
                "--endpoint-url",
                endpoint,
                "sns",
                "list-topics"),
            Duration.ofMinutes(1),
            "Generated SNS topic lookup before publish");
    Matcher topicMatcher =
        Pattern.compile("\"TopicArn\"\\s*:\\s*\"([^\"]*modriss-localstack-topic[^\"]*)\"")
            .matcher(topics);
    assertTrue(topicMatcher.find(), "Generated SNS topic ARN should be discoverable.");
    runProcess(
        processBuilder(
            outputDirectory,
            environment,
            awsExecutable,
            "--endpoint-url",
            endpoint,
            "sns",
            "publish",
            "--topic-arn",
            topicMatcher.group(1),
            "--message",
            "{\"kind\":\"generated-live-check\"}"),
        Duration.ofMinutes(1),
        "Generated SNS topic accepts publish");

    runProcess(
        processBuilder(
            outputDirectory,
            environment,
            awsExecutable,
            "--endpoint-url",
            endpoint,
            "events",
            "put-events",
            "--entries",
            "file://" + eventEntriesFile),
        Duration.ofMinutes(1),
        "Generated EventBridge bus accepts events");

    String stateMachineArn =
        ensureGeneratedStateMachineExecutesFromAsl(outputDirectory, awsExecutable, environment);
    String executionName = "generated-live-" + UUID.randomUUID();
    Path executionInputFile = outputDirectory.resolve("localstack-stepfunctions-input.json");
    Files.writeString(executionInputFile, "{\"kind\":\"generated-live-check\"}");
    runProcess(
        processBuilder(
            outputDirectory,
            environment,
            awsExecutable,
            "--endpoint-url",
            endpoint,
            "stepfunctions",
            "start-execution",
            "--state-machine-arn",
            stateMachineArn,
            "--name",
            executionName,
            "--input",
            "file://" + executionInputFile),
        Duration.ofMinutes(1),
        "Generated Step Functions workflow starts execution");
  }

  /**
   * Executes generated HTTP entrypoints through LocalStack, proving API Gateway and Lambda URL
   * artifacts route to the generated Lambda runtime.
   *
   * @param outputDirectory generated project root
   * @param awsExecutable AWS CLI executable
   * @param environment AWS/LocalStack environment
   * @throws Exception when lookup or HTTP execution fails
   */
  private void assertGeneratedHttpEntrypointsExecuteOnLocalStack(
      Path outputDirectory, String awsExecutable, Map<String, String> environment)
      throws Exception {
    String endpoint = environment.get("AWS_ENDPOINT_URL");
    String functionUrlConfig =
        runProcessForOutput(
            processBuilder(
                outputDirectory,
                environment,
                awsExecutable,
                "--endpoint-url",
                endpoint,
                "lambda",
                "get-function-url-config",
                "--function-name",
                "modriss-localstack-handler"),
            Duration.ofMinutes(1),
            "Generated Lambda function URL lookup");
    String functionUrl = JSON.readTree(functionUrlConfig).path("FunctionUrl").asText();
    assertFalse(functionUrl.isBlank(), "Generated Lambda function URL should be returned.");
    HttpResponse<String> functionUrlResponse =
        postJsonToFirstReachableUrl(
            localStackHttpCandidates(functionUrl, endpoint),
            "{\"payload\":{\"source\":\"junit-function-url\"}}");
    assertGeneratedHttpEntrypointResponse(functionUrlResponse, "Lambda function URL");

    String apis =
        runProcessForOutput(
            processBuilder(
                outputDirectory,
                environment,
                awsExecutable,
                "--endpoint-url",
                endpoint,
                "apigatewayv2",
                "get-apis"),
            Duration.ofMinutes(1),
            "Generated HTTP API lookup");
    JsonNode runtimeApi = null;
    for (JsonNode api : JSON.readTree(apis).path("Items")) {
      if (Set.of("Runtime HTTP API", "RuntimeHttpApi").contains(api.path("Name").asText())) {
        runtimeApi = api;
        break;
      }
    }
    assertNotNull(runtimeApi, "Generated Runtime HTTP API should be discoverable.");
    String apiId = runtimeApi.path("ApiId").asText();
    String apiEndpoint = runtimeApi.path("ApiEndpoint").asText();
    // The API endpoint reported by LocalStack may resolve to its generic edge handler when the
    // request is sent without the execute-api virtual-host routing header. Prefer the explicit
    // edge execute-api path first so a successful response is guaranteed to exercise this API's
    // deployed route rather than another edge handler returning HTTP 200.
    List<HttpEndpoint> apiCandidates = new ArrayList<>();
    apiCandidates.add(new HttpEndpoint(endpoint + "/_aws/execute-api/" + apiId + "/runtime", null));
    apiCandidates.addAll(localStackHttpCandidates(apiEndpoint + "/runtime", endpoint));
    HttpResponse<String> apiResponse =
        postJsonToFirstReachableUrl(apiCandidates, "{\"payload\":{\"source\":\"junit-http-api\"}}");
    assertGeneratedHttpEntrypointResponse(apiResponse, "API Gateway HTTP route");
  }

  /**
   * Returns host-reachable URL candidates for LocalStack virtual-host endpoints.
   *
   * @param urlText primary URL
   * @param localStackEndpoint LocalStack gateway endpoint reachable from the host JVM
   * @return primary and HTTP-normalized candidates
   */
  private List<HttpEndpoint> localStackHttpCandidates(String urlText, String localStackEndpoint) {
    List<HttpEndpoint> candidates = new ArrayList<>();
    candidates.add(new HttpEndpoint(urlText, null));
    if (urlText.startsWith("https://")) {
      candidates.add(new HttpEndpoint("http://" + urlText.substring("https://".length()), null));
    }
    try {
      java.net.URI reportedUri = java.net.URI.create(urlText);
      java.net.URI endpointUri = java.net.URI.create(localStackEndpoint);
      String host = reportedUri.getHost();
      if (host != null && !host.equals("127.0.0.1") && !host.equals("localhost")) {
        Matcher virtualHostMatcher =
            Pattern.compile("^(.*\\.(?:lambda-url|execute-api)\\.[a-z0-9-]+\\.).+$").matcher(host);
        if (virtualHostMatcher.matches()) {
          java.net.URI hostReachableUri =
              new java.net.URI(
                  "http",
                  reportedUri.getUserInfo(),
                  "127.0.0.1",
                  endpointUri.getPort(),
                  reportedUri.getPath(),
                  reportedUri.getQuery(),
                  reportedUri.getFragment());
          candidates.add(new HttpEndpoint(hostReachableUri.toString(), host));
        }
      }
    } catch (Exception ex) {
      // Keep the original candidates; the HTTP assertion will report the actual connection failure.
    }
    return candidates.stream().distinct().toList();
  }

  /**
   * Posts JSON to candidate URLs and returns the first non-missing generated endpoint response.
   *
   * @param urlCandidates LocalStack URL candidates
   * @param body JSON request body
   * @return HTTP response
   */
  private HttpResponse<String> postJsonToFirstReachableUrl(
      List<HttpEndpoint> urlCandidates, String body) throws Exception {
    // Floci's virtual AWS endpoints currently require HTTP/1.1; Java otherwise attempts h2c.
    HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
    AssertionError lastFailure = null;
    for (HttpEndpoint endpoint : urlCandidates) {
      try {
        HttpRequest.Builder requestBuilder =
            HttpRequest.newBuilder(java.net.URI.create(endpoint.url()))
                .timeout(Duration.ofMinutes(1))
                .header("content-type", "application/json");
        if (endpoint.hostHeader() != null) {
          // LocalStack routes virtual Lambda URLs by Host while the mapped URL connects to the
          // host-published edge port. Java's HTTP client requires this opt-in for Host headers.
          requestBuilder.header("Host", endpoint.hostHeader());
        }
        HttpRequest request =
            requestBuilder.POST(HttpRequest.BodyPublishers.ofString(body)).build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        // LocalStack can return 403 for a URL candidate whose virtual-host routing does not match
        // even when the model and generated template declare AuthType NONE. Try the remaining
        // equivalent endpoint candidates before treating that routing response as the handler.
        if (response.statusCode() != 403
            && response.statusCode() != 404
            && response.statusCode() != 502) {
          return response;
        }
        lastFailure =
            new AssertionError(
                "Generated HTTP endpoint returned "
                    + response.statusCode()
                    + " at "
                    + endpoint.url()
                    + ": "
                    + response.body());
      } catch (IOException | InterruptedException | IllegalArgumentException ex) {
        if (ex instanceof InterruptedException) {
          Thread.currentThread().interrupt();
        }
        lastFailure =
            new AssertionError(
                "Generated HTTP endpoint was not reachable at " + endpoint.url(), ex);
      }
    }
    throw lastFailure == null
        ? new AssertionError("No generated HTTP endpoint candidates were provided.")
        : lastFailure;
  }

  /** Host-published HTTP endpoint and optional virtual host used for LocalStack routing. */
  private record HttpEndpoint(String url, String hostHeader) {}

  /**
   * Verifies a generated HTTP entrypoint response came from the Lambda handler.
   *
   * @param response HTTP response
   * @param label entrypoint label
   */
  private void assertGeneratedHttpEntrypointResponse(HttpResponse<String> response, String label) {
    assertTrue(
        response.statusCode() >= 200 && response.statusCode() < 600,
        () -> label + " returned invalid HTTP status " + response.statusCode());
    assertTrue(
        response.body().contains("NOT_IMPLEMENTED")
            || response.body().contains("Business logic has not been implemented yet")
            || response.body().contains("statusCode")
            || (response.body().contains("code")
                && response.body().contains("correlationId")
                && response.body().contains("message")),
        () ->
            label
                + " response did not come from the generated Lambda handler: HTTP "
                + response.statusCode()
                + "\n"
                + response.body());
  }

  /**
   * Returns the generated state machine ARN, creating it from the generated ASL artifact when
   * LocalStack Community omits SAM state machines during CloudFormation deployment.
   *
   * @param outputDirectory generated project root
   * @param awsExecutable AWS CLI executable
   * @param environment AWS/LocalStack environment
   * @return state machine ARN
   * @throws Exception when lookup or fallback creation fails
   */
  private String ensureGeneratedStateMachineExecutesFromAsl(
      Path outputDirectory, String awsExecutable, Map<String, String> environment)
      throws Exception {
    String existingArn = generatedStateMachineArn(outputDirectory, awsExecutable, environment);
    if (existingArn != null) {
      return existingArn;
    }
    Path aslFile = outputDirectory.resolve("asl/modriss-localstack-workflow.asl.json");
    assertTrue(
        Files.isRegularFile(aslFile), "Generated ASL artifact should exist for live execution.");
    JSON.readTree(aslFile.toFile());
    runProcess(
        processBuilder(
            outputDirectory,
            environment,
            awsExecutable,
            "--endpoint-url",
            environment.get("AWS_ENDPOINT_URL"),
            "stepfunctions",
            "create-state-machine",
            "--name",
            "modriss-localstack-workflow",
            "--definition",
            "file://" + aslFile,
            "--role-arn",
            "arn:aws:iam::000000000000:role/modriss-localstack-runtime-role",
            "--type",
            "STANDARD"),
        Duration.ofMinutes(1),
        "Generated ASL state machine creation on LocalStack");
    String createdArn = generatedStateMachineArn(outputDirectory, awsExecutable, environment);
    assertTrue(createdArn != null, "Generated ASL state machine should be visible after creation.");
    return createdArn;
  }

  /**
   * Looks up the generated state machine ARN.
   *
   * @param outputDirectory generated project root
   * @param awsExecutable AWS CLI executable
   * @param environment AWS/LocalStack environment
   * @return ARN, or null when absent
   * @throws Exception when AWS CLI lookup fails
   */
  private String generatedStateMachineArn(
      Path outputDirectory, String awsExecutable, Map<String, String> environment)
      throws Exception {
    String machines =
        runProcessForOutput(
            processBuilder(
                outputDirectory,
                environment,
                awsExecutable,
                "--endpoint-url",
                environment.get("AWS_ENDPOINT_URL"),
                "stepfunctions",
                "list-state-machines"),
            Duration.ofMinutes(1),
            "Generated Step Functions ARN lookup");
    Matcher machineMatcher =
        Pattern.compile("\"stateMachineArn\"\\s*:\\s*\"([^\"]*modriss-localstack-workflow[^\"]*)\"")
            .matcher(machines);
    if (machineMatcher.find()) {
      return machineMatcher.group(1);
    }
    return null;
  }

  /**
   * Creates the generated Lambda function directly in LocalStack from the generated executable zip.
   *
   * @param outputDirectory generated project root
   * @param awsExecutable AWS CLI executable
   * @param environment AWS/LocalStack environment
   * @throws Exception when Lambda creation fails
   */
  private void createGeneratedLambdaOnLocalStack(
      Path outputDirectory, String awsExecutable, Map<String, String> environment)
      throws Exception {
    if (generatedLambdaExists(outputDirectory, awsExecutable, environment)) {
      refreshGeneratedLambdaCodeOnLocalStack(outputDirectory, awsExecutable, environment);
      waitForGeneratedLambdaActive(outputDirectory, awsExecutable, environment);
      return;
    }
    ProcessResult result =
        runProcessCapturing(
            processBuilder(
                outputDirectory,
                environment,
                awsExecutable,
                "--endpoint-url",
                environment.get("AWS_ENDPOINT_URL"),
                "lambda",
                "create-function",
                "--function-name",
                "modriss-localstack-handler",
                "--runtime",
                "provided.al2023",
                "--role",
                "arn:aws:iam::000000000000:role/modriss-localstack-runtime-role",
                "--handler",
                "bootstrap",
                "--zip-file",
                "fileb://runtime-handler.zip",
                "--architectures",
                "x86_64"),
            Duration.ofMinutes(3));
    Assumptions.assumeFalse(
        result.output().contains("Failed to connect to proxy URL"),
        () ->
            "LocalStack Lambda is blocked by host Docker proxy configuration: " + result.output());
    assertTrue(result.finished(), "Generated Lambda create on LocalStack timed out.");
    assertEquals(0, result.exitCode(), result.output());
    waitForGeneratedLambdaActive(outputDirectory, awsExecutable, environment);
  }

  /**
   * Checks whether the generated Lambda function already exists, usually from CloudFormation.
   *
   * @param outputDirectory generated project root
   * @param awsExecutable AWS CLI executable
   * @param environment AWS/LocalStack environment
   * @return true when the function exists
   * @throws Exception when AWS CLI process execution is interrupted
   */
  private boolean generatedLambdaExists(
      Path outputDirectory, String awsExecutable, Map<String, String> environment)
      throws Exception {
    ProcessResult result =
        runProcessCapturing(
            processBuilder(
                outputDirectory,
                environment,
                awsExecutable,
                "--endpoint-url",
                environment.get("AWS_ENDPOINT_URL"),
                "lambda",
                "get-function",
                "--function-name",
                "modriss-localstack-handler"),
            Duration.ofMinutes(1));
    return result.finished() && result.exitCode() == 0;
  }

  /**
   * Ensures the modeled public Lambda URL exists when an emulator's CloudFormation dependency
   * ordering reports the URL complete before the function has actually been provisioned.
   */
  private void ensureGeneratedFunctionUrlExists(
      Path outputDirectory, String awsExecutable, Map<String, String> environment)
      throws Exception {
    ProcessResult existing =
        runProcessCapturing(
            processBuilder(
                outputDirectory,
                environment,
                awsExecutable,
                "--endpoint-url",
                environment.get("AWS_ENDPOINT_URL"),
                "lambda",
                "get-function-url-config",
                "--function-name",
                "modriss-localstack-handler"),
            Duration.ofMinutes(1));
    if (existing.finished() && existing.exitCode() == 0) {
      return;
    }
    runProcess(
        processBuilder(
            outputDirectory,
            environment,
            awsExecutable,
            "--endpoint-url",
            environment.get("AWS_ENDPOINT_URL"),
            "lambda",
            "create-function-url-config",
            "--function-name",
            "modriss-localstack-handler",
            "--auth-type",
            "NONE"),
        Duration.ofMinutes(1),
        "Generated Lambda function URL emulator compatibility fallback");
  }

  /**
   * Waits for the generated Lambda function to become active.
   *
   * @param outputDirectory generated project root
   * @param awsExecutable AWS CLI executable
   * @param environment AWS/LocalStack environment
   * @throws Exception when Lambda wait fails
   */
  private void waitForGeneratedLambdaActive(
      Path outputDirectory, String awsExecutable, Map<String, String> environment)
      throws Exception {
    runProcess(
        processBuilder(
            outputDirectory,
            environment,
            awsExecutable,
            "--endpoint-url",
            environment.get("AWS_ENDPOINT_URL"),
            "lambda",
            "wait",
            "function-active-v2",
            "--function-name",
            "modriss-localstack-handler"),
        Duration.ofMinutes(4),
        "Generated Lambda activation on LocalStack");
  }

  /**
   * Updates the deployed Lambda with the generated zip to verify the executable package itself is
   * accepted by LocalStack Lambda.
   *
   * @param outputDirectory generated project root
   * @param awsExecutable AWS CLI executable
   * @param environment AWS/LocalStack environment
   * @throws Exception when the update fails
   */
  private void refreshGeneratedLambdaCodeOnLocalStack(
      Path outputDirectory, String awsExecutable, Map<String, String> environment)
      throws Exception {
    runProcess(
        processBuilder(
            outputDirectory,
            environment,
            awsExecutable,
            "--endpoint-url",
            environment.get("AWS_ENDPOINT_URL"),
            "lambda",
            "update-function-code",
            "--function-name",
            "modriss-localstack-handler",
            "--zip-file",
            "fileb://runtime-handler.zip"),
        Duration.ofMinutes(2),
        "Generated Lambda code update on LocalStack");
  }

  /**
   * Invokes the generated Lambda artifact through LocalStack and validates the handler response.
   *
   * @param outputDirectory generated project root
   * @param awsExecutable AWS CLI executable
   * @param environment AWS/LocalStack environment
   * @throws Exception when invocation fails
   */
  private void assertGeneratedLambdaExecutesOnLocalStack(
      Path outputDirectory,
      String awsExecutable,
      Map<String, String> environment,
      String containerId)
      throws Exception {
    Path payloadFile = outputDirectory.resolve("localstack-lambda-payload.json");
    Path responseFile = outputDirectory.resolve("localstack-lambda-response.json");
    Files.writeString(payloadFile, "{\"payload\":{\"source\":\"junit-localstack\"}}");
    ProcessResult invokeResult =
        runProcessCapturing(
            processBuilder(
                outputDirectory,
                environment,
                awsExecutable,
                "--endpoint-url",
                environment.get("AWS_ENDPOINT_URL"),
                "lambda",
                "invoke",
                "--function-name",
                "modriss-localstack-handler",
                "--payload",
                "file://" + payloadFile,
                "--cli-binary-format",
                "raw-in-base64-out",
                responseFile.toString()),
            Duration.ofMinutes(4));
    assertTrue(
        invokeResult.finished(),
        () ->
            "Generated Lambda invoke on LocalStack timed out.\nDocker Lambda containers:\n"
                + dockerLambdaDiagnostics()
                + "\nLocalStack logs:\n"
                + dockerContainerLogs(containerId));
    assertEquals(
        0,
        invokeResult.exitCode(),
        () ->
            invokeResult.output()
                + "\nLambda logs:\n"
                + lambdaLogEvents(outputDirectory, awsExecutable, environment)
                + "\nDocker Lambda containers:\n"
                + dockerLambdaDiagnostics()
                + "\nLocalStack logs:\n"
                + dockerContainerLogs(containerId));

    Map<?, ?> response = JSON.readValue(responseFile.toFile(), Map.class);
    assertTrue(
        response.containsKey("statusCode"),
        () -> "Lambda response missing statusCode: " + response);
  }

  /**
   * Reads generated Lambda CloudWatch logs from LocalStack for failure diagnostics.
   *
   * @param outputDirectory generated project root
   * @param awsExecutable AWS CLI executable
   * @param environment AWS/LocalStack environment
   * @return log diagnostic text
   */
  private String lambdaLogEvents(
      Path outputDirectory, String awsExecutable, Map<String, String> environment) {
    try {
      return runProcessCapturing(
              processBuilder(
                  outputDirectory,
                  environment,
                  awsExecutable,
                  "--endpoint-url",
                  environment.get("AWS_ENDPOINT_URL"),
                  "logs",
                  "filter-log-events",
                  "--log-group-name",
                  "/aws/lambda/modriss-localstack-handler"),
              Duration.ofMinutes(1))
          .output();
    } catch (Exception ex) {
      return ex.toString();
    }
  }

  /**
   * Captures Lambda runtime container state and logs for LocalStack invocation diagnostics.
   *
   * @return diagnostic text
   */
  private String dockerLambdaDiagnostics() {
    try {
      Process listProcess =
          new ProcessBuilder(
                  "docker",
                  "ps",
                  "-a",
                  "--filter",
                  "ancestor=public.ecr.aws/lambda/provided:al2023",
                  "--format",
                  "{{.ID}} {{.Status}} {{.Names}}")
              .redirectErrorStream(true)
              .start();
      listProcess.waitFor(Duration.ofSeconds(20).toMillis(), TimeUnit.MILLISECONDS);
      String listText = new String(listProcess.getInputStream().readAllBytes()).trim();
      if (listText.isBlank()) {
        return "No Lambda runtime containers found.";
      }
      StringBuilder diagnostics = new StringBuilder(listText);
      for (String line : listText.lines().toList()) {
        String containerId = line.split("\\s+")[0];
        Process logProcess =
            new ProcessBuilder("docker", "logs", "--tail", "80", containerId)
                .redirectErrorStream(true)
                .start();
        logProcess.waitFor(Duration.ofSeconds(20).toMillis(), TimeUnit.MILLISECONDS);
        diagnostics
            .append("\n--- logs ")
            .append(containerId)
            .append(" ---\n")
            .append(new String(logProcess.getInputStream().readAllBytes()));
      }
      return diagnostics.toString();
    } catch (Exception ex) {
      return ex.toString();
    }
  }

  /**
   * Starts a disposable LocalStack container using Docker.
   *
   * @return container id
   * @throws Exception when Docker cannot start LocalStack
   */
  private String startLocalStackContainer(String image, String provider) throws Exception {
    Map<String, String> dotEnv = loadDotEnv();
    boolean localStackProxyEnabled =
        Boolean.parseBoolean(dotEnv.getOrDefault("LOCALSTACK_PROXY_ENABLED", "false"));
    String suffix = UUID.randomUUID().toString().replace("-", "");
    String networkName = "modriss-m2t-" + provider + "-" + suffix;
    String containerName = "modriss-m2t-" + provider + "-" + suffix;
    runProcess(
        new ProcessBuilder("docker", "network", "create", networkName),
        Duration.ofSeconds(30),
        "LocalStack Docker network creation");
    activeLocalStackNetworkName = networkName;
    List<String> command =
        new ArrayList<>(
            List.of(
                "docker",
                "run",
                "-d",
                "--name",
                containerName,
                "--network",
                networkName,
                "-e",
                "AWS_DEFAULT_REGION=" + dotEnv.getOrDefault("AWS_DEFAULT_REGION", "us-east-1"),
                "-e",
                "DEBUG=" + dotEnv.getOrDefault("LOCALSTACK_DEBUG", "0"),
                "-e",
                "PERSISTENCE=" + dotEnv.getOrDefault("LOCALSTACK_PERSISTENCE", "0"),
                "-e",
                "CFN_IGNORE_UNSUPPORTED_RESOURCE_TYPES="
                    + dotEnv.getOrDefault("LOCALSTACK_CFN_IGNORE_UNSUPPORTED_RESOURCE_TYPES", "1"),
                "-e",
                "LAMBDA_RUNTIME_ENVIRONMENT_TIMEOUT="
                    + dotEnv.getOrDefault("LAMBDA_RUNTIME_ENVIRONMENT_TIMEOUT", "120"),
                "-e",
                "LOCALSTACK_HOST=localhost.localstack.cloud:4566",
                "-e",
                "S3_ENDPOINT_STRATEGY=path",
                "-e",
                "LAMBDA_DOCKER_NETWORK=" + networkName,
                "-e",
                "HTTP_PROXY="
                    + (localStackProxyEnabled
                        ? dotEnv.getOrDefault("LOCALSTACK_HTTP_PROXY", "")
                        : ""),
                "-e",
                "HTTPS_PROXY="
                    + (localStackProxyEnabled
                        ? dotEnv.getOrDefault("LOCALSTACK_HTTPS_PROXY", "")
                        : ""),
                "-e",
                "NO_PROXY=*",
                "-e",
                "http_proxy="
                    + (localStackProxyEnabled
                        ? dotEnv.getOrDefault("LOCALSTACK_HTTP_PROXY", "")
                        : ""),
                "-e",
                "https_proxy="
                    + (localStackProxyEnabled
                        ? dotEnv.getOrDefault("LOCALSTACK_HTTPS_PROXY", "")
                        : ""),
                "-e",
                "no_proxy=*"));
    command.add("-e");
    command.add("ALL_PROXY=");
    command.add("-e");
    command.add("all_proxy=");
    if ("floci".equals(provider)) {
      command.add("-e");
      command.add("FLOCI_DEFAULT_REGION=" + dotEnv.getOrDefault("AWS_DEFAULT_REGION", "us-east-1"));
      command.add("-e");
      command.add("FLOCI_HOSTNAME=localhost.floci.io");
      command.add("-e");
      command.add("LOCALSTACK_PARITY=true");
      command.add("-e");
      command.add("FLOCI_STORAGE_MODE=memory");
      command.add("-e");
      command.add("FLOCI_SERVICES_CLOUDFORMATION_ALLOW_STUB_UNSUPPORTED_RESOURCE_TYPES=false");
      command.add("-e");
      command.add("FLOCI_SERVICES_LAMBDA_DOCKER_NETWORK=" + networkName);
      command.add("-e");
      command.add("FLOCI_SERVICES_LAMBDA_DOCKER_HOST_OVERRIDE=" + containerName);
    }
    addDockerEnv(command, dotEnv, "LOCALSTACK_AUTH_TOKEN");
    if (localStackProxyEnabled) {
      addDockerEnv(command, dotEnv, "LOCALSTACK_HTTP_PROXY");
      addDockerEnv(command, dotEnv, "LOCALSTACK_HTTPS_PROXY");
      addDockerEnv(command, dotEnv, "LOCALSTACK_NO_PROXY");
    }
    command.add("-v");
    command.add("/var/run/docker.sock:/var/run/docker.sock");
    command.add("-p");
    command.add("127.0.0.1::4566");
    command.add(image);

    ProcessResult result = runProcessCapturing(new ProcessBuilder(command), Duration.ofMinutes(5));
    boolean finished = result.finished();
    String outputText = result.output().trim();
    Assumptions.assumeTrue(
        finished && result.exitCode() == 0,
        "LocalStack container could not be started: " + outputText);
    return outputText.lines().findFirst().orElseThrow();
  }

  /**
   * Adds a non-empty `.env` value as a Docker environment variable.
   *
   * @param command Docker command parts
   * @param dotEnv loaded repository environment values
   * @param key environment key
   */
  private void addDockerEnv(List<String> command, Map<String, String> dotEnv, String key) {
    String value = dotEnv.get(key);
    if (value != null && !value.isBlank()) {
      command.add("-e");
      command.add(key + "=" + value);
    }
  }

  /**
   * Checks whether Docker can inspect the requested image without pulling from the network.
   *
   * @param image Docker image tag
   * @return true when the image is already available locally
   * @throws Exception when process execution fails unexpectedly
   */
  private boolean dockerImageIsAvailable(String image) throws Exception {
    Process process =
        new ProcessBuilder("docker", "image", "inspect", image, "--format", "{{.Id}}")
            .redirectErrorStream(true)
            .start();
    boolean finished = process.waitFor(Duration.ofSeconds(20).toMillis(), TimeUnit.MILLISECONDS);
    return finished && process.exitValue() == 0;
  }

  /**
   * Resolves the host port mapped to LocalStack's edge endpoint.
   *
   * @param containerId Docker container id
   * @return host port
   * @throws Exception when Docker cannot inspect the port
   */
  private int localStackHostPort(String containerId) throws Exception {
    Integer port = localStackHostPortOrNull(containerId);
    assertTrue(port != null, () -> "Could not parse LocalStack host port for " + containerId);
    return port;
  }

  /**
   * Resolves the published LocalStack edge port without failing, for compose fallback discovery.
   *
   * @param containerId Docker container id
   * @return host port, or null when Docker reports no published 4566 mapping
   * @throws Exception when process execution is interrupted
   */
  private Integer localStackHostPortOrNull(String containerId) throws Exception {
    Process process =
        new ProcessBuilder("docker", "port", containerId, "4566/tcp")
            .redirectErrorStream(true)
            .start();
    boolean finished = process.waitFor(Duration.ofSeconds(20).toMillis(), TimeUnit.MILLISECONDS);
    String outputText = new String(process.getInputStream().readAllBytes()).trim();
    if (!finished || process.exitValue() != 0) {
      return null;
    }
    Matcher matcher = Pattern.compile(":(\\d+)").matcher(outputText);
    if (!matcher.find()) {
      return null;
    }
    return Integer.parseInt(matcher.group(1));
  }

  /**
   * Waits until LocalStack health endpoint responds successfully.
   *
   * @param endpoint LocalStack endpoint URL
   * @throws Exception when health polling fails
   */
  private void waitForLocalStack(String containerId, String endpoint) throws Exception {
    HttpClient client = HttpClient.newHttpClient();
    HttpRequest request =
        HttpRequest.newBuilder(java.net.URI.create(endpoint + "/_localstack/health"))
            .timeout(Duration.ofSeconds(5))
            .GET()
            .build();
    long deadline = System.nanoTime() + Duration.ofMinutes(2).toNanos();
    while (System.nanoTime() < deadline) {
      try {
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 200 && response.statusCode() < 300) {
          return;
        }
      } catch (IOException ignored) {
        // LocalStack is still starting.
      }
      Assumptions.assumeTrue(
          dockerContainerIsRunning(containerId),
          () -> "LocalStack exited before becoming healthy: " + dockerContainerLogs(containerId));
      Thread.sleep(1000);
    }
    Assumptions.assumeTrue(false, "LocalStack did not become healthy at " + endpoint);
  }

  /**
   * Checks whether Docker still reports the LocalStack container as running.
   *
   * @param containerId Docker container id
   * @return true when the container is still running
   * @throws Exception when Docker command execution fails unexpectedly
   */
  private boolean dockerContainerIsRunning(String containerId) throws Exception {
    Process process =
        new ProcessBuilder("docker", "inspect", "-f", "{{.State.Running}}", containerId)
            .redirectErrorStream(true)
            .start();
    boolean finished = process.waitFor(Duration.ofSeconds(10).toMillis(), TimeUnit.MILLISECONDS);
    String outputText = new String(process.getInputStream().readAllBytes()).trim();
    return finished && process.exitValue() == 0 && Boolean.parseBoolean(outputText);
  }

  /**
   * Returns a short tail of LocalStack logs for skipped startup diagnostics.
   *
   * @param containerId Docker container id
   * @return log text or Docker diagnostic text
   */
  private String dockerContainerLogs(String containerId) {
    try {
      Process process =
          new ProcessBuilder("docker", "logs", "--tail", "80", containerId)
              .redirectErrorStream(true)
              .start();
      boolean finished = process.waitFor(Duration.ofSeconds(20).toMillis(), TimeUnit.MILLISECONDS);
      String outputText = new String(process.getInputStream().readAllBytes()).trim();
      if (finished) {
        return outputText;
      }
    } catch (Exception ignored) {
      // Best-effort diagnostics.
    }
    return "";
  }

  /**
   * Stops a Docker container, ignoring cleanup failures.
   *
   * @param localStack LocalStack runtime descriptor
   */
  private void stopDockerContainer(LocalStackRuntime localStack) {
    if (!localStack.ownedByTest()) {
      return;
    }
    String containerId = localStack.containerId();
    try {
      new ProcessBuilder("docker", "stop", containerId)
          .redirectErrorStream(true)
          .start()
          .waitFor(Duration.ofSeconds(30).toMillis(), TimeUnit.MILLISECONDS);
      new ProcessBuilder("docker", "rm", "-f", containerId)
          .redirectErrorStream(true)
          .start()
          .waitFor(Duration.ofSeconds(30).toMillis(), TimeUnit.MILLISECONDS);
      if (activeLocalStackNetworkName != null) {
        removeDockerContainersByName(activeLocalStackNetworkName);
        new ProcessBuilder("docker", "network", "rm", activeLocalStackNetworkName)
            .redirectErrorStream(true)
            .start()
            .waitFor(Duration.ofSeconds(30).toMillis(), TimeUnit.MILLISECONDS);
        activeLocalStackNetworkName = null;
      }
    } catch (Exception ignored) {
      // Best-effort cleanup.
    }
  }

  /**
   * Removes disposable LocalStack child containers whose names include the generated test network
   * name.
   *
   * @param nameFragment generated LocalStack network/container name fragment
   */
  private void removeDockerContainersByName(String nameFragment) {
    try {
      Process listProcess =
          new ProcessBuilder("docker", "ps", "-aq", "--filter", "name=" + nameFragment)
              .redirectErrorStream(true)
              .start();
      listProcess.waitFor(Duration.ofSeconds(20).toMillis(), TimeUnit.MILLISECONDS);
      String ids = new String(listProcess.getInputStream().readAllBytes()).trim();
      if (ids.isBlank()) {
        return;
      }
      for (String id : ids.lines().toList()) {
        if (!id.isBlank()) {
          new ProcessBuilder("docker", "rm", "-f", id)
              .redirectErrorStream(true)
              .start()
              .waitFor(Duration.ofSeconds(30).toMillis(), TimeUnit.MILLISECONDS);
        }
      }
    } catch (Exception ignored) {
      // Best-effort cleanup.
    }
  }

  /**
   * Runs a process, captures merged output, and fails on timeout or non-zero exit.
   *
   * @param builder process builder
   * @param timeout execution timeout
   * @param label assertion label
   * @throws Exception when process execution is interrupted
   */
  private void runProcess(ProcessBuilder builder, Duration timeout, String label) throws Exception {
    runProcessForOutput(builder, timeout, label);
  }

  /**
   * Runs a process, captures merged output, and returns stdout/stderr text.
   *
   * @param builder process builder
   * @param timeout execution timeout
   * @param label assertion label
   * @return captured merged output
   * @throws Exception when process execution is interrupted
   */
  private String runProcessForOutput(ProcessBuilder builder, Duration timeout, String label)
      throws Exception {
    ProcessResult result = runProcessCapturing(builder, timeout);
    assertTrue(result.finished(), label + " timed out.");
    assertEquals(0, result.exitCode(), result.output());
    return result.output();
  }

  /**
   * Runs a process and captures merged output without asserting on the exit code.
   *
   * @param builder process builder
   * @param timeout execution timeout
   * @return captured process result
   * @throws IOException when process launch fails
   * @throws InterruptedException when interrupted while waiting
   */
  private ProcessResult runProcessCapturing(ProcessBuilder builder, Duration timeout)
      throws IOException, InterruptedException, java.util.concurrent.ExecutionException {
    Process process = builder.redirectErrorStream(true).start();
    CompletableFuture<String> outputFuture =
        CompletableFuture.supplyAsync(
            () -> {
              try {
                return new String(process.getInputStream().readAllBytes());
              } catch (IOException ex) {
                return "Process output unavailable: " + ex.getMessage();
              }
            });
    boolean finished;
    try {
      process.onExit().get(timeout.toMillis(), TimeUnit.MILLISECONDS);
      finished = true;
    } catch (java.util.concurrent.TimeoutException ex) {
      process.destroyForcibly();
      process.waitFor(Duration.ofSeconds(5).toMillis(), TimeUnit.MILLISECONDS);
      finished = false;
    }
    try {
      String outputText = outputFuture.get(5, TimeUnit.SECONDS);
      int exitCode = finished ? process.exitValue() : -1;
      return new ProcessResult(finished, exitCode, outputText);
    } catch (java.util.concurrent.TimeoutException ex) {
      outputFuture.cancel(true);
      int exitCode = finished ? process.exitValue() : -1;
      return new ProcessResult(
          finished, exitCode, "Process output unavailable after termination: " + ex.getMessage());
    }
  }

  /** Captured process result. */
  private record ProcessResult(boolean finished, int exitCode, String output) {}

  /** LocalStack endpoint plus ownership metadata for cleanup. */
  private record LocalStackRuntime(
      String provider, String containerId, String endpoint, boolean ownedByTest) {}

  /**
   * Reuses a running compose-managed LocalStack container when available, otherwise starts an
   * isolated test container from the pinned image.
   *
   * @return LocalStack runtime descriptor
   * @throws Exception when Docker inspection or startup fails
   */
  private LocalStackRuntime acquireLocalStackRuntime() throws Exception {
    Map<String, String> dotEnv = loadDotEnv();
    String provider =
        System.getenv()
            .getOrDefault("AWS_EMULATOR", dotEnv.getOrDefault("AWS_EMULATOR", "floci"))
            .trim()
            .toLowerCase(java.util.Locale.ROOT);
    Assumptions.assumeTrue(
        Set.of("floci", "localstack").contains(provider),
        () -> "AWS_EMULATOR must be 'floci' or 'localstack'; found: " + provider);
    if ("localstack".equals(provider)
        && dotEnv.getOrDefault("LOCALSTACK_AUTH_TOKEN", "").isBlank() == false
        && dockerImageIsAvailable(LOCALSTACK_PRO_IMAGE)) {
      String containerId = startLocalStackContainer(LOCALSTACK_PRO_IMAGE, provider);
      return new LocalStackRuntime(provider, containerId, localStackEndpoint(containerId), true);
    }
    String runningContainerId = runningLocalStackContainerId(provider);
    if (runningContainerId != null) {
      String endpoint = localStackEndpoint(runningContainerId);
      if (localStackHealthResponds(endpoint)) {
        return new LocalStackRuntime(provider, runningContainerId, endpoint, false);
      }
    }
    String image =
        "floci".equals(provider)
            ? dotEnv.getOrDefault("FLOCI_IMAGE", FLOCI_IMAGE)
            : dotEnv.getOrDefault("LOCALSTACK_IMAGE", LOCALSTACK_IMAGE);
    Assumptions.assumeTrue(
        dockerImageIsAvailable(image),
        () ->
            "No running "
                + provider
                + " container was found and the selected image is not available"
                + " locally: "
                + image);
    String containerId = startLocalStackContainer(image, provider);
    return new LocalStackRuntime(provider, containerId, localStackEndpoint(containerId), true);
  }

  /**
   * Resolves the endpoint for either compose-managed or test-owned LocalStack.
   *
   * @param containerId Docker container id
   * @return LocalStack edge endpoint URL
   * @throws Exception when Docker inspection fails unexpectedly
   */
  private String localStackEndpoint(String containerId) throws Exception {
    Map<String, String> dotEnv = loadDotEnv();
    Integer publishedPort = localStackHostPortOrNull(containerId);
    if (publishedPort != null) {
      return "http://127.0.0.1:" + publishedPort;
    }
    String configuredEndpoint =
        System.getenv()
            .getOrDefault(
                "AWS_EMULATOR_ENDPOINT_URL",
                dotEnv.getOrDefault(
                    "AWS_EMULATOR_ENDPOINT_URL",
                    dotEnv.getOrDefault("LOCALSTACK_ENDPOINT_URL", "")));
    if (configuredEndpoint != null && !configuredEndpoint.isBlank()) {
      return configuredEndpoint;
    }
    return "http://127.0.0.1:"
        + dotEnv.getOrDefault(
            "AWS_EMULATOR_GATEWAY_PORT", dotEnv.getOrDefault("LOCALSTACK_GATEWAY_PORT", "4566"));
  }

  /**
   * Finds a running Docker container whose image or name identifies LocalStack.
   *
   * @return container id, or null when none is running
   * @throws IOException when Docker cannot be launched
   * @throws InterruptedException when Docker inspection is interrupted
   */
  private String runningLocalStackContainerId(String provider)
      throws IOException, InterruptedException {
    Process process =
        new ProcessBuilder(
                "docker",
                "ps",
                "--filter",
                "status=running",
                "--format",
                "{{.ID}} {{.Image}} {{.Names}}")
            .redirectErrorStream(true)
            .start();
    boolean finished = process.waitFor(Duration.ofSeconds(20).toMillis(), TimeUnit.MILLISECONDS);
    String outputText = new String(process.getInputStream().readAllBytes()).trim();
    if (!finished || process.exitValue() != 0) {
      return null;
    }
    return outputText
        .lines()
        .filter(line -> line.toLowerCase(java.util.Locale.ROOT).contains(provider))
        .map(line -> line.split("\\s+")[0])
        .findFirst()
        .orElse(null);
  }

  /**
   * Performs a quick health check used only for runtime selection.
   *
   * @param endpoint candidate LocalStack endpoint
   * @return true when the health endpoint responds with 2xx
   */
  private boolean localStackHealthResponds(String endpoint) {
    try {
      HttpRequest request =
          HttpRequest.newBuilder(java.net.URI.create(endpoint + "/_localstack/health"))
              .timeout(Duration.ofSeconds(2))
              .GET()
              .build();
      HttpResponse<String> response =
          HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
      return response.statusCode() >= 200 && response.statusCode() < 300;
    } catch (Exception ignored) {
      return false;
    }
  }

  /**
   * Creates a process builder with a working directory and environment overlay.
   *
   * @param workingDirectory process working directory
   * @param environment environment variables to overlay
   * @param command command parts
   * @return configured process builder
   */
  private ProcessBuilder processBuilder(
      Path workingDirectory, Map<String, String> environment, String... command) {
    ProcessBuilder builder = new ProcessBuilder(command).directory(workingDirectory.toFile());
    builder.environment().putAll(environment);
    return builder;
  }

  /**
   * Builds LocalStack AWS client environment from `.env` values and the runtime endpoint.
   *
   * @param endpoint LocalStack edge endpoint
   * @return environment map for AWS/SAM/Go commands
   * @throws IOException when `.env` cannot be read
   */
  private Map<String, String> localStackAwsEnvironment(String endpoint) throws IOException {
    Map<String, String> dotEnv = loadDotEnv();
    Map<String, String> env = new HashMap<>();
    String region = dotEnv.getOrDefault("AWS_DEFAULT_REGION", "us-east-1");
    env.put("AWS_REGION", region);
    env.put("AWS_DEFAULT_REGION", region);
    env.put("AWS_ENDPOINT_URL", endpoint);
    env.put("AWS_ACCESS_KEY_ID", dotEnv.getOrDefault("AWS_ACCESS_KEY_ID", "test"));
    env.put("AWS_SECRET_ACCESS_KEY", dotEnv.getOrDefault("AWS_SECRET_ACCESS_KEY", "test"));
    env.put("AWS_EC2_METADATA_DISABLED", "true");
    env.put("AWS_PAGER", "");
    env.put("HTTP_PROXY", "");
    env.put("HTTPS_PROXY", "");
    env.put("ALL_PROXY", "");
    env.put("NO_PROXY", "*");
    env.put("http_proxy", "");
    env.put("https_proxy", "");
    env.put("all_proxy", "");
    env.put("no_proxy", "*");
    return env;
  }

  /**
   * Loads simple KEY=VALUE entries from the repository `.env` file.
   *
   * @return environment values, or an empty map when `.env` is absent
   * @throws IOException when `.env` cannot be read
   */
  private Map<String, String> loadDotEnv() throws IOException {
    Path dotEnvFile = REPOSITORY_ROOT.resolve(".env");
    if (!Files.isRegularFile(dotEnvFile)) {
      return Map.of();
    }
    Map<String, String> values = new HashMap<>();
    for (String rawLine : Files.readAllLines(dotEnvFile)) {
      String line = rawLine.trim();
      if (line.isEmpty() || line.startsWith("#")) {
        continue;
      }
      int equalsIndex = line.indexOf('=');
      if (equalsIndex <= 0) {
        continue;
      }
      String key = line.substring(0, equalsIndex).trim();
      String value = line.substring(equalsIndex + 1).trim();
      if ((value.startsWith("\"") && value.endsWith("\""))
          || (value.startsWith("'") && value.endsWith("'"))) {
        value = value.substring(1, value.length() - 1);
      }
      values.put(key, value);
    }
    return values;
  }

  /**
   * Ensures generated test fixtures are structurally loadable before the Epsilon runner sees them.
   *
   * @param sourceModel source model file
   */
  private void assertSourceModelReloads(Path sourceModel) {
    ResourceSet resourceSet = new ResourceSetImpl();
    resourceSet
        .getResourceFactoryRegistry()
        .getExtensionToFactoryMap()
        .put("ecore", new EcoreResourceFactoryImpl());
    resourceSet
        .getResourceFactoryRegistry()
        .getExtensionToFactoryMap()
        .put("xmi", new XMIResourceFactoryImpl());
    Resource metamodelResource =
        resourceSet.getResource(
            URI.createFileURI(
                REPOSITORY_ROOT.resolve("mde/metamodels/psm/psm-combined.ecore").toString()),
            true);
    EcoreUtil.resolveAll(resourceSet);
    registerPackages(metamodelResource);
    Resource resource = resourceSet.getResource(URI.createFileURI(sourceModel.toString()), true);
    assertTrue(resource.getErrors().isEmpty(), () -> resource.getErrors().toString());
  }

  /**
   * Checks whether a command is available on PATH.
   *
   * @param command command name
   * @return true when a version probe exits cleanly
   */
  private String commandExecutable(String command) {
    try {
      List<String> probe = new ArrayList<>();
      probe.add(command);
      probe.add(command.equals("aws") || command.equals("sam") ? "--version" : "version");
      Process process = new ProcessBuilder(probe).redirectErrorStream(true).start();
      boolean finished = process.waitFor(Duration.ofSeconds(10).toMillis(), TimeUnit.MILLISECONDS);
      if (finished && process.exitValue() == 0) {
        return command;
      }
    } catch (Exception ignored) {
      // Fall through to well-known install paths.
    }

    if (System.getProperty("os.name").toLowerCase(java.util.Locale.ROOT).contains("windows")
        && command.equals("go")) {
      Path windowsGo = Path.of("C:/Program Files/Go/bin/go.exe");
      if (Files.isRegularFile(windowsGo)) {
        return windowsGo.toString();
      }
    }
    if (System.getProperty("os.name").toLowerCase(java.util.Locale.ROOT).contains("windows")
        && command.equals("aws")) {
      Path windowsAws = Path.of("C:/Program Files/Amazon/AWSCLIV2/aws.exe");
      if (Files.isRegularFile(windowsAws)) {
        return windowsAws.toString();
      }
    }
    if (System.getProperty("os.name").toLowerCase(java.util.Locale.ROOT).contains("windows")
        && command.equals("sam")) {
      Path windowsSam = Path.of("C:/Program Files/Amazon/AWSSAMCLI/bin/sam.cmd");
      if (Files.isRegularFile(windowsSam)) {
        return windowsSam.toString();
      }
    }
    return null;
  }

  /**
   * Asserts that a generated JSON artifact exists and contains source-specific evidence.
   *
   * @param jsonFile generated JSON file
   * @param expectedSnippets snippets that must appear in the file
   * @throws IOException when the file cannot be read
   */
  private void assertJsonArtifactContains(Path jsonFile, String... expectedSnippets)
      throws IOException {
    assertTrue(Files.isRegularFile(jsonFile), () -> "Missing JSON artifact " + jsonFile);
    String text = Files.readString(jsonFile);
    for (String expectedSnippet : expectedSnippets) {
      assertTrue(text.contains(expectedSnippet), () -> jsonFile + " missing " + expectedSnippet);
    }
  }

  /**
   * Extracts EGX rules that record generated artifacts.
   *
   * @return ordered EGX rule descriptors
   * @throws IOException when the EGX module cannot be read
   */
  private List<EgxRule> egxArtifactRules() throws IOException {
    String egxText = Files.readString(AWS_PSM_EGX);
    Matcher matcher = Pattern.compile("(?m)^rule\\s+(\\w+)\\b").matcher(egxText);
    List<EgxRule> rules = new ArrayList<>();
    while (matcher.find()) {
      int blockEnd = egxText.indexOf("\nrule ", matcher.end());
      String block = egxText.substring(matcher.start(), blockEnd < 0 ? egxText.length() : blockEnd);
      Matcher templateMatcher = Pattern.compile("template\\s*:\\s*\"([^\"]+)\"").matcher(block);
      Matcher ruleIdMatcher =
          Pattern.compile("recordArtifact\\([^,]+,\\s*\"[^\"]+\",\\s*\"([^\"]+)\"").matcher(block);
      if (templateMatcher.find() && ruleIdMatcher.find()) {
        rules.add(new EgxRule(matcher.group(1), templateMatcher.group(1), ruleIdMatcher.group(1)));
      }
    }
    return List.copyOf(rules);
  }

  /** EGX rule coverage descriptor. */
  private record EgxRule(String name, String template, String ruleId) {}

  /**
   * Asserts that the generator emitted the production project skeleton expected by downstream
   * users.
   *
   * @param outputDirectory generated project root
   * @throws IOException when generated files cannot be inspected
   */
  private void assertRequiredProjectTreeWasGenerated(Path outputDirectory) throws IOException {
    List<String> requiredFiles =
        List.of(
            "template.yaml",
            "samconfig.toml",
            "README.md",
            "Makefile",
            "go.mod",
            ".gitignore",
            "docs/architecture.md",
            "docs/model-summary.md",
            "docs/traceability.md",
            "docs/security.md",
            "docs/operations.md",
            "docs/deployment.md",
            "docs/runbooks/incident-response.md",
            "docs/runbooks/rollback.md",
            "env/qa.json",
            "openapi/orders-api.openapi.yaml",
            "events/samples/rule-order-created.json",
            "events/samples/queue-order-work-sqs-message.json",
            "events/samples/topic-order-events-sns-message.json",
            "generated/documents/operator-note.md",
            "src/shared/logger.go",
            "src/shared/tracer.go",
            "src/shared/metrics.go",
            "src/shared/errors.go",
            "src/shared/validation.go",
            "src/shared/idempotency.go",
            "src/shared/config.go",
            "src/shared/event-publisher.go",
            "src/shared/data-access.go",
            "src/functions/order-handler/handler.go",
            "src/functions/order-handler/schema.json",
            "schemas/api/generated-api.schema.json",
            "schemas/events/generated-event.schema.json",
            "schemas/messages/generated-message.schema.json",
            "schemas/errors/generated-error.schema.json",
            "schemas/data/generated-data.schema.json",
            "tests/unit/order-handler_test.go",
            "tests/integration/order-handler_integration_test.go",
            "tests/contract/generated_contracts_test.go",
            "tests/events/generated_events_test.go",
            "tests/e2e/security_generated_test.go",
            "tests/e2e/generated_flows_test.go",
            "tests/fixtures/README.md",
            "env/local.json",
            "env/dev.json",
            "env/test.json",
            "env/staging.json",
            "env/prod.example.json",
            "scripts/validate-models.sh",
            "scripts/validate-template.sh",
            "scripts/validate-contracts.sh",
            "scripts/build.sh",
            "scripts/test.sh",
            "scripts/deploy.sh",
            "scripts/local-invoke.sh",
            "scripts/local-start-api.sh",
            "scripts/package.sh",
            ".github/workflows/validate.yml",
            ".github/workflows/deploy-dev.yml",
            ".github/workflows/deploy-prod.yml",
            "generated/trace/artifact-trace.json",
            "generated/trace/model-trace.json",
            "generated/trace/protected-regions.json",
            "generated/reports/generation-report.md",
            "generated/reports/security-review.md",
            "generated/reports/iam-policy-rationale.md",
            "generated/reports/manual-actions.md");

    for (String requiredFile : requiredFiles) {
      assertTrue(
          Files.isRegularFile(outputDirectory.resolve(requiredFile)),
          () -> "Missing generated project file: " + requiredFile);
    }
  }

  /**
   * Ensures the generated project is Go-only and references the generated Go module from its Lambda
   * handler.
   *
   * @param outputDirectory generated project root
   * @throws IOException when generated files cannot be inspected
   */
  private void assertGoOnlyArtifactsWereGenerated(Path outputDirectory) throws IOException {
    assertFalse(Files.exists(outputDirectory.resolve("package.json")));
    assertFalse(Files.exists(outputDirectory.resolve("pyproject.toml")));
    assertFalse(Files.exists(outputDirectory.resolve("tsconfig.json")));

    try (var files = Files.walk(outputDirectory)) {
      List<Path> forbiddenFiles =
          files
              .filter(Files::isRegularFile)
              .filter(
                  path -> {
                    String name = path.getFileName().toString();
                    return name.endsWith(".ts")
                        || name.endsWith(".js")
                        || name.endsWith(".py")
                        || name.endsWith(".java");
                  })
              .toList();
      assertTrue(
          forbiddenFiles.isEmpty(),
          () -> "Expected Go-only generated project, found: " + forbiddenFiles);
    }

    String goMod = Files.readString(outputDirectory.resolve("go.mod"));
    assertTrue(goMod.contains("module example.com/representative-aws-psm"));
    assertTrue(goMod.contains("github.com/aws/aws-lambda-go"));

    String handler =
        Files.readString(outputDirectory.resolve("src/functions/order-handler/handler.go"));
    assertTrue(handler.contains("package main"));
    assertTrue(handler.contains("lambda.Start(Handler)"));
    assertTrue(handler.contains("\"example.com/representative-aws-psm/src/shared\""));
    assertTrue(handler.contains("return mapKnownError("));
    assertFalse(
        handler.contains(
            "return GeneratedResult{}, shared.NewGeneratedHandlerError(\"NOT_IMPLEMENTED\""));
  }

  /**
   * Verifies executor-finalized trace files no longer contain placeholder hashes or checksums.
   *
   * @param outputDirectory generated project root
   * @throws IOException when trace files cannot be read
   */
  private void assertTraceFilesAreFinalized(Path outputDirectory) throws IOException {
    String artifactTrace =
        Files.readString(outputDirectory.resolve("generated/trace/artifact-trace.json"));

    assertFalse(
        artifactTrace.contains("COMPUTED_BY_EXECUTOR"),
        "Source model hash should be finalized by the runner.");
    assertFalse(
        artifactTrace.contains("COMPUTED_AFTER_WRITE"),
        "Artifact checksums should be finalized by the runner.");
    assertTrue(
        artifactTrace.contains("\"checksum\":"),
        "Artifact trace sections should include checksums.");
    assertTrue(artifactTrace.contains("\"path\": \"template.yaml\""));
    assertTrue(artifactTrace.contains("\"artifactKind\": \"SAM_TEMPLATE\""));
  }

  /**
   * Asserts artifact trace paths are unique and cover every file reported by the generation runner.
   *
   * @param outputDirectory generated project root
   * @param report successful generation report
   * @throws IOException when trace files cannot be read
   */
  private void assertTraceCoversEveryGeneratedFile(Path outputDirectory, EgxGenerationReport report)
      throws IOException {
    String artifactTrace =
        Files.readString(outputDirectory.resolve("generated/trace/artifact-trace.json"));
    List<String> tracePaths = tracePaths(artifactTrace);
    Set<String> uniqueTracePaths = new LinkedHashSet<>(tracePaths);

    assertEquals(
        tracePaths.size(),
        uniqueTracePaths.size(),
        "Artifact trace must not contain duplicate paths.");
    assertEquals(
        report.generatedFiles().size(),
        tracePaths.size(),
        "Artifact trace should contain one row per generated file.");

    for (Path generatedFile : report.generatedFiles()) {
      String normalizedPath = generatedFile.toString().replace('\\', '/');
      assertTrue(
          uniqueTracePaths.contains(normalizedPath),
          () -> "Generated file missing from artifact trace: " + normalizedPath);
    }
  }

  /**
   * Extracts path entries from the generated artifact trace JSON.
   *
   * @param artifactTrace artifact trace JSON text
   * @return ordered list of traced artifact paths
   */
  private List<String> tracePaths(String artifactTrace) {
    Matcher matcher = Pattern.compile("\"path\"\\s*:\\s*\"([^\"]+)\"").matcher(artifactTrace);
    java.util.ArrayList<String> paths = new java.util.ArrayList<>();
    while (matcher.find()) {
      paths.add(matcher.group(1));
    }
    return paths;
  }

  /**
   * Checks every generated SAM template for duplicate parameter keys.
   *
   * @param outputDirectory generated project root
   * @throws IOException when generated templates cannot be inspected
   */
  private void assertGeneratedSamTemplatesDoNotRepeatParameterKeys(Path outputDirectory)
      throws IOException {
    try (var files = Files.walk(outputDirectory)) {
      for (Path templateFile :
          files
              .filter(Files::isRegularFile)
              .filter(path -> path.getFileName().toString().startsWith("template"))
              .filter(path -> path.getFileName().toString().endsWith(".yaml"))
              .toList()) {
        assertNoDuplicateParameterKeys(templateFile);
      }
    }
  }

  /**
   * Scans a SAM template's top-level {@code Parameters} section for duplicate keys.
   *
   * @param templateFile generated template file
   * @throws IOException when the template cannot be read
   */
  private void assertNoDuplicateParameterKeys(Path templateFile) throws IOException {
    Set<String> parameterNames = new LinkedHashSet<>();
    boolean inParameters = false;
    for (String line : Files.readAllLines(templateFile)) {
      if (line.equals("Parameters:")) {
        inParameters = true;
        continue;
      }
      if (inParameters && !line.startsWith(" ") && line.endsWith(":")) {
        return;
      }
      if (inParameters && line.startsWith("  ") && !line.startsWith("    ") && line.endsWith(":")) {
        String parameterName = line.trim().replace(":", "");
        assertTrue(
            parameterNames.add(parameterName),
            () -> "Duplicate SAM parameter key " + parameterName + " in " + templateFile);
      }
    }
  }

  /**
   * Checks that model-friendly parameter aliases are rendered using valid CloudFormation names.
   *
   * @param outputDirectory generated project root
   * @throws IOException when generated templates cannot be inspected
   */
  private void assertGeneratedSamTemplatesUseValidParameterTypes(Path outputDirectory)
      throws IOException {
    boolean foundNormalizedStringParameter = false;
    try (var files = Files.walk(outputDirectory)) {
      for (Path templateFile :
          files
              .filter(Files::isRegularFile)
              .filter(path -> path.getFileName().toString().startsWith("template"))
              .filter(path -> path.getFileName().toString().endsWith(".yaml"))
              .toList()) {
        String template = Files.readString(templateFile);
        assertFalse(
            template.contains("Type: 'STRING'"),
            () -> "Invalid CloudFormation parameter type alias in " + templateFile);
        foundNormalizedStringParameter |= template.contains("Type: 'String'");
      }
    }
    assertTrue(
        foundNormalizedStringParameter, "Expected a normalized CloudFormation String parameter.");
  }

  /**
   * Ensures concrete subclasses do not fall back to the generic tags-only resource renderer.
   *
   * @param outputDirectory generated project root
   * @throws IOException when generated templates cannot be inspected
   */
  private void assertGeneratedSamTemplatesRenderConcreteResourceProperties(Path outputDirectory)
      throws IOException {
    String templates;
    try (var files = Files.walk(outputDirectory)) {
      templates =
          files
              .filter(Files::isRegularFile)
              .filter(path -> path.getFileName().toString().startsWith("template"))
              .filter(path -> path.getFileName().toString().endsWith(".yaml"))
              .map(
                  path -> {
                    try {
                      return Files.readString(path);
                    } catch (IOException exception) {
                      throw new java.io.UncheckedIOException(exception);
                    }
                  })
              .reduce("", String::concat);
    }

    assertTrue(
        templates.contains("RuntimeManagementConfig:\n      UpdateRuntimeOn: 'Auto'"), templates);
    assertFalse(templates.contains("RuntimeManagementConfig: 'Auto'"));
    assertTrue(
        templates.contains("HttpMethod: 'POST'")
            && templates.contains("ResourceId: '/grantapplications/approveemergencygrant'"),
        templates);
    assertTrue(
        templates.contains("FunctionName: !Ref Submitgrantapplicationhandlerlambda"), templates);
  }

  /**
   * Verifies the generated Markdown report summarizes manual actions and export gates.
   *
   * @param outputDirectory generated project root
   * @throws IOException when the report cannot be read
   */
  private void assertGenerationReportContainsExportGateSummary(Path outputDirectory)
      throws IOException {
    String generationReport =
        Files.readString(outputDirectory.resolve("generated/reports/generation-report.md"));

    assertTrue(generationReport.contains("## Manual Actions"));
    assertTrue(generationReport.contains("## Export Gates"));
    assertTrue(generationReport.contains("Blocking production issues"));
  }

  /**
   * Ensures generated scripts and validation workflow enforce the expected production gates with
   * Go-only tooling.
   *
   * @param outputDirectory generated project root
   * @throws IOException when generated scripts or workflow files cannot be read
   */
  private void assertGeneratedScriptsAndCiImplementProductionGates(Path outputDirectory)
      throws IOException {
    for (String scriptName :
        List.of(
            "validate-models.sh",
            "validate-template.sh",
            "validate-contracts.sh",
            "build.sh",
            "test.sh",
            "deploy.sh",
            "local-invoke.sh",
            "local-start-api.sh",
            "package.sh")) {
      String scriptText = Files.readString(outputDirectory.resolve("scripts").resolve(scriptName));
      assertTrue(
          scriptText.replace("\r\n", "\n").startsWith("#!/usr/bin/env bash\nset -euo pipefail"),
          () -> scriptName + " must use strict shell settings.");
    }

    String buildScript = Files.readString(outputDirectory.resolve("scripts/build.sh"));
    assertTrue(buildScript.contains("--install-only"));
    assertTrue(buildScript.contains("go mod tidy"));
    assertTrue(buildScript.contains("go test ./..."));
    assertTrue(buildScript.contains("go build"));

    String testScript = Files.readString(outputDirectory.resolve("scripts/test.sh"));
    assertTrue(testScript.contains("bash scripts/build.sh --install-only"));

    String deployScript = Files.readString(outputDirectory.resolve("scripts/deploy.sh"));
    String packageScript = Files.readString(outputDirectory.resolve("scripts/package.sh"));
    assertTrue(deployScript.contains("sam build"));
    assertTrue(deployScript.contains(".aws-sam/"));
    assertTrue(deployScript.contains("bash scripts/validate-models.sh"));
    assertTrue(packageScript.contains("sam build"));
    assertTrue(packageScript.contains(".aws-sam/"));
    assertTrue(packageScript.contains("bash scripts/validate-models.sh"));

    String localInvokeScript = Files.readString(outputDirectory.resolve("scripts/local-invoke.sh"));
    assertTrue(localInvokeScript.contains("matching_templates"));
    assertTrue(localInvokeScript.contains("sam build"));

    String validateModelsScript =
        Files.readString(outputDirectory.resolve("scripts/validate-models.sh"));
    assertTrue(validateModelsScript.contains("generated/reports/manual-actions.md"));
    assertTrue(validateModelsScript.contains("NOT_IMPLEMENTED"));
    assertTrue(validateModelsScript.contains("Required protected regions still contain"));

    String validateContractsScript =
        Files.readString(outputDirectory.resolve("scripts/validate-contracts.sh"));
    assertTrue(validateContractsScript.contains("go test ./tests/contract"));
    assertTrue(validateContractsScript.contains("openapi:"));
    assertTrue(validateContractsScript.contains("paths:"));

    String validateWorkflow =
        Files.readString(outputDirectory.resolve(".github/workflows/validate.yml"));
    assertTrue(validateWorkflow.contains("actions/setup-go@v5"));
    assertFalse(validateWorkflow.contains("setup-node"));
    assertFalse(validateWorkflow.contains("setup-python"));
    assertTrue(validateWorkflow.contains("bash scripts/build.sh --install-only"));
    assertTrue(validateWorkflow.contains("bash scripts/validate-contracts.sh"));
    assertTrue(validateWorkflow.contains("bash scripts/validate-template.sh"));
    assertTrue(validateWorkflow.contains("bash scripts/test.sh"));
    assertTrue(validateWorkflow.contains("bash scripts/validate-models.sh --security"));
  }

  /**
   * Ensures generated text artifacts are portable to Linux even when generation runs on Windows.
   *
   * @param outputDirectory generated project root
   * @throws IOException when generated files cannot be read
   */
  private void assertGeneratedFilesUseLfLineEndings(Path outputDirectory) throws IOException {
    try (var files = Files.walk(outputDirectory)) {
      for (Path file : files.filter(Files::isRegularFile).toList()) {
        String text = Files.readString(file);
        assertFalse(text.contains("\r"), () -> "Generated file must use LF line endings: " + file);
      }
    }
  }

  /**
   * Ensures the Go-only profile never leaves stale source-model runtime and handler hints in SAM.
   *
   * @param outputDirectory generated project root
   * @throws IOException when templates cannot be read
   */
  private void assertGeneratedSamTemplatesUseGeneratedGoArtifacts(Path outputDirectory)
      throws IOException {
    try (var files = Files.list(outputDirectory)) {
      for (Path template :
          files
              .filter(path -> path.getFileName().toString().startsWith("template"))
              .filter(path -> path.getFileName().toString().endsWith(".yaml"))
              .toList()) {
        String text = Files.readString(template);
        assertFalse(text.contains("Runtime: 'nodejs"), () -> "Stale runtime in " + template);
        assertFalse(text.contains("CodeUri: 'TBD'"), () -> "Stale code URI in " + template);
        assertTrue(text.contains("Runtime: 'provided.al2023'"));
        assertTrue(text.contains("Handler: 'bootstrap'"));
      }
    }
  }

  /**
   * Ensures each generated resource has at most one DynamoDB encryption configuration block.
   *
   * @param outputDirectory generated project root
   * @throws IOException when templates cannot be read
   */
  private void assertGeneratedSamTemplatesDoNotRepeatSseSpecification(Path outputDirectory)
      throws IOException {
    try (var files = Files.list(outputDirectory)) {
      for (Path template :
          files
              .filter(path -> path.getFileName().toString().startsWith("template"))
              .filter(path -> path.getFileName().toString().endsWith(".yaml"))
              .toList()) {
        boolean inResource = false;
        boolean sawSse = false;
        for (String line : Files.readAllLines(template)) {
          if (line.startsWith("  ") && !line.startsWith("    ") && line.endsWith(":")) {
            inResource = true;
            sawSse = false;
          } else if (inResource && line.startsWith("      SSESpecification:")) {
            assertFalse(sawSse, () -> "Duplicate SSESpecification in " + template);
            sawSse = true;
          }
        }
      }
    }
  }

  /**
   * Runs the EGX generator and fails the test with diagnostics and captured output on generation
   * failure.
   *
   * @param request generation request to execute
   * @return successful generation report
   */
  private EgxGenerationReport generateOrFail(EgxGenerationRequest request) {
    try {
      return new EpsilonEgxGenerator().generate(request);
    } catch (EgxGenerationException ex) {
      fail(
          "EGX generation failed: "
              + ex.getReport().diagnostics()
              + "\nstdout:\n"
              + ex.getReport().standardOutput()
              + "\nstderr:\n"
              + ex.getReport().errorOutput(),
          ex);
      throw new AssertionError(ex);
    }
  }

  /**
   * Creates a reusable plaintext value expression for fixture models.
   *
   * @param metamodelResource loaded AWS PSM metamodel
   * @param id stable fixture id
   * @param literal literal value
   * @return configured value expression
   */
  private EObject plaintextValue(Resource metamodelResource, String id, String literal) {
    EObject value = create(metamodelResource, "ValueExpression");
    set(value, "id", id);
    set(value, "name", id);
    set(value, "sourceKind", enumValue(metamodelResource, "ValueSourceKind", "PLAINTEXT"));
    set(value, "literal", literal);
    set(value, "secret", false);
    return value;
  }

  /**
   * Creates an allow policy document for fixture resources.
   *
   * @param metamodelResource loaded AWS PSM metamodel
   * @param id stable fixture id
   * @param action IAM action
   * @param resource IAM resource
   * @return configured IAM policy document
   */
  private EObject allowPolicyDocument(
      Resource metamodelResource, String id, String action, String resource) {
    EObject statement = create(metamodelResource, "IamStatement");
    set(statement, "id", id + "_statement");
    set(statement, "name", id + " Statement");
    set(statement, "effect", enumValue(metamodelResource, "IamEffect", "ALLOW"));
    add(statement, "actions", action);
    add(statement, "resources", resource);
    if ("*".equals(action)) {
      set(statement, "wildcardAction", true);
    }
    if ("*".equals(resource)) {
      set(statement, "wildcardResource", true);
      set(statement, "wildcardJustification", "Fixture exercises wildcard rationale rendering.");
    }

    EObject document = create(metamodelResource, "IamPolicyDocument");
    set(document, "id", id);
    set(document, "name", id + " Policy");
    set(document, "version", "2012-10-17");
    add(document, "statements", statement);
    return document;
  }

  /**
   * Creates a deployable AWS resource fixture with common identity fields.
   *
   * @param metamodelResource loaded AWS PSM metamodel
   * @param classifierName concrete AWS PSM resource classifier
   * @param id stable fixture id
   * @param name display name
   * @param logicalId CloudFormation logical id
   * @return configured resource
   */
  private EObject awsResource(
      Resource metamodelResource, String classifierName, String id, String name, String logicalId) {
    EObject resource = create(metamodelResource, classifierName);
    set(resource, "id", id);
    set(resource, "name", name);
    set(resource, "logicalId", logicalId);
    set(resource, "productionCritical", false);
    set(resource, "importedResource", false);
    return resource;
  }

  /**
   * Adds multiple values to a many-valued EMF feature.
   *
   * @param object owner object
   * @param featureName many-valued feature
   * @param values values to add
   */
  private void addAll(EObject object, String featureName, EObject... values) {
    for (EObject value : values) {
      add(object, featureName, value);
    }
  }

  /**
   * Creates a LocalStack-deployable AWS PSM model with Lambda, IAM, SQS, SNS, DynamoDB, S3, and
   * CloudWatch Logs resources.
   *
   * @param modelFile output XMI file path
   * @throws IOException when the generated fixture cannot be saved
   */
  private void createLocalStackDeployableAwsPsmModel(Path modelFile) throws IOException {
    Path metamodel = REPOSITORY_ROOT.resolve("mde/metamodels/psm/psm-combined.ecore");
    ResourceSet resourceSet = new ResourceSetImpl();
    resourceSet
        .getResourceFactoryRegistry()
        .getExtensionToFactoryMap()
        .put("ecore", new EcoreResourceFactoryImpl());
    resourceSet
        .getResourceFactoryRegistry()
        .getExtensionToFactoryMap()
        .put("xmi", new XMIResourceFactoryImpl());
    Resource metamodelResource =
        resourceSet.getResource(URI.createFileURI(metamodel.toString()), true);
    EcoreUtil.resolveAll(resourceSet);
    registerPackages(metamodelResource);

    EObject assumeStatement = create(metamodelResource, "IamStatement");
    set(assumeStatement, "id", "stmt_runtime_assume_role");
    set(assumeStatement, "name", "Runtime Assume Role Statement");
    set(assumeStatement, "effect", enumValue(metamodelResource, "IamEffect", "ALLOW"));
    add(assumeStatement, "actions", "sts:AssumeRole");
    add(assumeStatement, "resources", "*");
    set(assumeStatement, "wildcardResource", true);
    set(
        assumeStatement,
        "wildcardJustification",
        "IAM trust policies use wildcard resource semantics.");
    EObject assumePrincipal = create(metamodelResource, "IamPrincipal");
    set(assumePrincipal, "id", "principal_runtime_lambda");
    set(assumePrincipal, "name", "Runtime Lambda Principal");
    set(assumePrincipal, "principalType", "Service");
    add(assumePrincipal, "identifiers", "lambda.amazonaws.com");
    add(assumeStatement, "principals", assumePrincipal);
    EObject assumePolicy = create(metamodelResource, "IamPolicyDocument");
    set(assumePolicy, "id", "policy_runtime_assume");
    set(assumePolicy, "name", "Runtime Assume Policy");
    set(assumePolicy, "version", "2012-10-17");
    add(assumePolicy, "statements", assumeStatement);

    EObject lambdaRole =
        awsResource(
            metamodelResource,
            "IamRole",
            "role_runtime_lambda",
            "Runtime Lambda Role",
            "RuntimeLambdaRole");
    set(lambdaRole, "roleName", "modriss-localstack-runtime-role");
    set(lambdaRole, "assumeRolePolicy", assumePolicy);

    EObject inlinePolicy =
        awsResource(
            metamodelResource,
            "IamPolicy",
            "policy_runtime_lambda",
            "Runtime Lambda Policy",
            "RuntimeLambdaPolicy");
    set(inlinePolicy, "policyName", "modriss-localstack-runtime-policy");
    set(
        inlinePolicy,
        "document",
        allowPolicyDocument(metamodelResource, "policy_runtime_lambda_doc", "*", "*"));
    add(inlinePolicy, "roles", lambdaRole);

    EObject logGroup =
        awsResource(
            metamodelResource,
            "CloudWatchLogGroup",
            "log_group_runtime_lambda",
            "Runtime Lambda Log Group",
            "RuntimeLambdaLogGroup");
    set(logGroup, "logGroupName", "/aws/lambda/modriss-localstack-handler");
    set(logGroup, "retentionDays", 7);

    EObject lambdaCode = create(metamodelResource, "LambdaZipCodeConfig");
    set(lambdaCode, "id", "code_runtime_handler");
    set(lambdaCode, "name", "Runtime Handler Code");

    EObject lambdaFunction =
        awsResource(
            metamodelResource,
            "AwsLambdaFunction",
            "lambda_runtime_handler",
            "Runtime Handler",
            "RuntimeHandler");
    set(lambdaFunction, "functionName", "modriss-localstack-handler");
    set(lambdaFunction, "descriptionText", "LocalStack-deployed generated Go Lambda handler.");
    set(lambdaFunction, "memorySizeMb", 256);
    set(lambdaFunction, "timeoutSeconds", 30);
    set(
        lambdaFunction,
        "architecture",
        enumValue(metamodelResource, "LambdaArchitecture", "X86_64"));
    set(
        lambdaFunction,
        "codeSigningDecision",
        enumValue(metamodelResource, "Decision", "NOT_REQUIRED"));
    set(lambdaFunction, "code", lambdaCode);
    set(lambdaFunction, "role", lambdaRole);
    set(lambdaFunction, "logGroup", logGroup);

    EObject functionUrl =
        awsResource(
            metamodelResource,
            "LambdaFunctionUrl",
            "url_runtime_handler",
            "Runtime Handler Function URL",
            "RuntimeHandlerUrl");
    set(functionUrl, "authType", enumValue(metamodelResource, "LambdaFunctionUrlAuthType", "NONE"));
    set(functionUrl, "invokeMode", enumValue(metamodelResource, "LambdaInvokeMode", "BUFFERED"));
    set(lambdaFunction, "functionUrl", functionUrl);

    EObject functionUrlPermission =
        awsResource(
            metamodelResource,
            "LambdaPermission",
            "permission_runtime_function_url",
            "Runtime Function URL Permission",
            "RuntimeFunctionUrlPermission");
    set(functionUrlPermission, "action", "lambda:InvokeFunctionUrl");
    set(functionUrlPermission, "principal", "*");
    set(
        functionUrlPermission,
        "functionUrlAuthType",
        enumValue(metamodelResource, "LambdaFunctionUrlAuthType", "NONE"));
    add(lambdaFunction, "permissions", functionUrlPermission);

    EObject queue =
        awsResource(
            metamodelResource, "SqsQueue", "queue_runtime", "Runtime Queue", "RuntimeQueue");
    set(queue, "queueName", "modriss-localstack-queue");
    set(queue, "queueType", enumValue(metamodelResource, "SqsQueueType", "STANDARD"));
    set(queue, "visibilityTimeoutSeconds", 45);
    set(queue, "sqsManagedSseEnabled", true);

    EObject topic =
        awsResource(
            metamodelResource, "SnsTopic", "topic_runtime", "Runtime Topic", "RuntimeTopic");
    set(topic, "topicName", "modriss-localstack-topic");
    set(topic, "fifoTopic", false);

    EObject table =
        awsResource(
            metamodelResource, "DynamoDbTable", "table_runtime", "Runtime Table", "RuntimeTable");
    set(table, "tableName", "modriss-localstack-table");
    set(
        table,
        "billingMode",
        enumValue(metamodelResource, "DynamoDbBillingMode", "PAY_PER_REQUEST"));
    EObject attr = create(metamodelResource, "DynamoDbAttributeDefinition");
    set(attr, "id", "attribute_runtime_pk");
    set(attr, "name", "Runtime PK Attribute");
    set(attr, "attributeName", "pk");
    set(attr, "attributeType", enumValue(metamodelResource, "DynamoDbAttributeType", "S"));
    add(table, "attributeDefinitions", attr);
    EObject keySchema = create(metamodelResource, "DynamoDbKeySchemaElement");
    set(keySchema, "id", "key_runtime_pk");
    set(keySchema, "name", "Runtime PK Key");
    set(keySchema, "attributeName", "pk");
    set(keySchema, "keyType", enumValue(metamodelResource, "DynamoDbKeyType", "HASH"));
    add(table, "keySchema", keySchema);

    EObject bucket =
        awsResource(
            metamodelResource, "S3Bucket", "bucket_runtime", "Runtime Bucket", "RuntimeBucket");
    set(bucket, "bucketName", "modriss-localstack-bucket");
    set(bucket, "versioningStatus", enumValue(metamodelResource, "S3VersioningStatus", "ENABLED"));
    set(
        bucket,
        "publicAccessMode",
        enumValue(metamodelResource, "S3BlockPublicAccessMode", "STRICT_BLOCK_ALL"));

    EObject parameter =
        awsResource(
            metamodelResource,
            "SsmParameter",
            "parameter_runtime_mode",
            "Runtime Mode Parameter",
            "RuntimeModeParameter");
    set(parameter, "parameterName", "/modriss/localstack/mode");
    set(parameter, "parameterType", enumValue(metamodelResource, "ParameterType", "STRING"));
    set(parameter, "tier", enumValue(metamodelResource, "SsmParameterTier", "STANDARD"));
    set(parameter, "dataType", "text");
    set(parameter, "descriptionText", "LocalStack generated fixture mode.");
    set(parameter, "value", plaintextValue(metamodelResource, "value_runtime_mode", "e2e"));

    EObject secret =
        awsResource(
            metamodelResource,
            "SecretsManagerSecret",
            "secret_runtime",
            "Runtime Secret",
            "RuntimeSecret");
    set(secret, "secretName", "modriss/localstack/secret");
    set(secret, "descriptionText", "LocalStack generated fixture secret.");
    set(
        secret,
        "generateSecretStringJson",
        "{\"SecretStringTemplate\":\"{\\\"mode\\\":\\\"generated\\\"}\",\"GenerateStringKey\":\"token\"}");

    EObject eventBus =
        awsResource(
            metamodelResource,
            "EventBridgeBus",
            "bus_runtime",
            "Runtime Event Bus",
            "RuntimeEventBus");
    set(eventBus, "busName", "modriss-localstack-bus");
    EObject eventTarget = create(metamodelResource, "EventBridgeTarget");
    set(eventTarget, "id", "target_runtime_queue");
    set(eventTarget, "name", "Runtime Queue Target");
    set(eventTarget, "targetId", "RuntimeQueueTarget");
    set(eventTarget, "targetKind", enumValue(metamodelResource, "EventBridgeTargetKind", "SQS"));
    set(eventTarget, "targetResource", queue);
    EObject eventRule =
        awsResource(
            metamodelResource,
            "EventBridgeRule",
            "rule_runtime",
            "Runtime Event Rule",
            "RuntimeEventRule");
    set(eventRule, "ruleName", "modriss-localstack-rule");
    set(eventRule, "eventPatternJson", "{\"source\":[\"modriss.e2e\"]}");
    set(eventRule, "state", "ENABLED");
    set(eventRule, "bus", eventBus);
    add(eventRule, "targets", eventTarget);

    EObject workflowDone =
        createAslState(metamodelResource, "state_runtime_done", "Done", "SUCCEED");
    EObject workflowStart =
        createAslState(metamodelResource, "state_runtime_start", "Start", "PASS");
    set(workflowStart, "nextState", workflowDone);
    EObject workflowDocument = create(metamodelResource, "AslDocument");
    set(workflowDocument, "id", "document_runtime_workflow");
    set(workflowDocument, "name", "Runtime Workflow ASL");
    set(workflowDocument, "comment", "LocalStack generated fixture workflow.");
    set(workflowDocument, "startAt", "Start");
    add(workflowDocument, "states", workflowStart);
    add(workflowDocument, "states", workflowDone);
    EObject stateMachine =
        awsResource(
            metamodelResource,
            "StepFunctionStateMachine",
            "state_machine_runtime",
            "Runtime Workflow",
            "RuntimeWorkflow");
    set(stateMachine, "stateMachineName", "modriss-localstack-workflow");
    set(
        stateMachine,
        "stateMachineType",
        enumValue(metamodelResource, "StepFunctionType", "STANDARD"));
    set(stateMachine, "role", lambdaRole);
    set(stateMachine, "aslDocument", workflowDocument);

    EObject apiIntegration = create(metamodelResource, "ApiGatewayIntegration");
    set(apiIntegration, "id", "integration_runtime_http");
    set(apiIntegration, "name", "Runtime HTTP Integration");
    set(apiIntegration, "logicalId", "RuntimeHttpIntegration");
    set(
        apiIntegration,
        "integrationType",
        enumValue(metamodelResource, "ApiGatewayIntegrationType", "AWS_PROXY"));
    set(apiIntegration, "integrationMethod", "POST");
    set(apiIntegration, "payloadFormatVersion", "2.0");
    set(apiIntegration, "lambdaTarget", lambdaFunction);
    EObject apiRoute =
        awsResource(
            metamodelResource,
            "HttpApiRoute",
            "route_runtime_submit",
            "Runtime Submit Route",
            "RuntimeSubmitRoute");
    set(apiRoute, "path", "/runtime");
    set(apiRoute, "routeKey", "POST /runtime");
    set(apiRoute, "operationName", "submit-runtime");
    set(apiRoute, "method", enumValue(metamodelResource, "ApiGatewayHttpMethod", "POST"));
    set(
        apiRoute,
        "authorizationType",
        enumValue(metamodelResource, "ApiGatewayAuthorizationType", "NONE"));
    set(apiRoute, "integration", apiIntegration);
    EObject httpApi =
        awsResource(
            metamodelResource, "HttpApi", "api_runtime_http", "Runtime HTTP API", "RuntimeHttpApi");
    set(httpApi, "apiName", "Runtime HTTP API");
    set(httpApi, "descriptionText", "LocalStack generated fixture HTTP API.");
    set(httpApi, "protocolType", "HTTP");
    set(httpApi, "openApiVersion", "3.0.3");
    add(httpApi, "routes", apiRoute);

    EObject apiPermission =
        awsResource(
            metamodelResource,
            "LambdaPermission",
            "permission_runtime_http_api",
            "Runtime HTTP API Permission",
            "RuntimeHttpApiPermission");
    set(apiPermission, "action", "lambda:InvokeFunction");
    set(apiPermission, "principal", "apigateway.amazonaws.com");
    set(
        apiPermission,
        "sourceArn",
        "!Sub 'arn:${AWS::Partition}:execute-api:${AWS::Region}:${AWS::AccountId}:*/*/POST/runtime'");
    add(lambdaFunction, "permissions", apiPermission);

    EObject stack = create(metamodelResource, "SamStack");
    set(stack, "id", "stack_runtime");
    set(stack, "name", "Runtime Stack");
    set(stack, "stackName", "modriss-localstack-runtime");
    set(stack, "templatePath", "template.yaml");
    set(stack, "templateDescription", "LocalStack deployable generated stack.");
    set(stack, "useSamTransform", true);
    add(stack, "capabilities", enumValue(metamodelResource, "SamCapability", "CAPABILITY_IAM"));
    addAll(
        stack,
        "resources",
        lambdaRole,
        inlinePolicy,
        logGroup,
        lambdaFunction,
        queue,
        topic,
        table,
        bucket,
        parameter,
        secret,
        eventBus,
        eventRule,
        stateMachine,
        httpApi,
        apiIntegration);

    EObject stage = create(metamodelResource, "AwsStage");
    set(stage, "id", "stage_runtime");
    set(stage, "name", "Runtime");
    set(stage, "stageName", "localstack");
    set(stage, "environmentClass", enumValue(metamodelResource, "AwsEnvironmentClass", "TEST"));
    set(stage, "region", "us-east-1");
    set(stage, "requiresManualApproval", false);
    add(stage, "deploysStacks", stack);

    EObject model = create(metamodelResource, "AwsPsmModel");
    set(model, "id", "aws_psm_localstack_runtime");
    set(model, "name", "LocalStack Runtime AWS PSM");
    set(model, "partition", enumValue(metamodelResource, "AwsPartition", "AWS"));
    set(model, "accountStrategy", "single-account");
    set(model, "regionStrategy", "single-region");
    set(model, "defaultRegion", "us-east-1");
    set(model, "namingConvention", "kebab-case");
    set(model, "taggingStrategy", "stage-and-service");
    set(model, "productionMode", false);
    add(model, "stages", stage);
    add(model, "stacks", stack);
    addAll(
        model,
        "allResources",
        lambdaRole,
        inlinePolicy,
        logGroup,
        lambdaFunction,
        queue,
        topic,
        table,
        bucket,
        parameter,
        secret,
        eventBus,
        eventRule,
        stateMachine,
        httpApi,
        apiIntegration);

    Resource modelResource = resourceSet.createResource(URI.createFileURI(modelFile.toString()));
    modelResource.getContents().add(model);
    modelResource.save(null);
  }

  /**
   * Creates a minimal valid AWS PSM model for negative EGX guard assertions.
   *
   * @param modelFile output XMI file path
   * @throws IOException when the generated fixture cannot be saved
   */
  private void createMinimalGuardAwsPsmModel(Path modelFile) throws IOException {
    Path metamodel = REPOSITORY_ROOT.resolve("mde/metamodels/psm/psm-combined.ecore");
    ResourceSet resourceSet = new ResourceSetImpl();
    resourceSet
        .getResourceFactoryRegistry()
        .getExtensionToFactoryMap()
        .put("ecore", new EcoreResourceFactoryImpl());
    resourceSet
        .getResourceFactoryRegistry()
        .getExtensionToFactoryMap()
        .put("xmi", new XMIResourceFactoryImpl());
    Resource metamodelResource =
        resourceSet.getResource(URI.createFileURI(metamodel.toString()), true);
    EcoreUtil.resolveAll(resourceSet);
    registerPackages(metamodelResource);

    EObject stack = create(metamodelResource, "SamStack");
    set(stack, "id", "stack_minimal");
    set(stack, "name", "Minimal Stack");
    set(stack, "stackName", "modriss-minimal");
    set(stack, "templatePath", "template.yaml");
    set(stack, "templateDescription", "Minimal guard stack.");
    set(stack, "useSamTransform", true);

    EObject stage = create(metamodelResource, "AwsStage");
    set(stage, "id", "stage_local");
    set(stage, "name", "Local");
    set(stage, "stageName", "local");
    set(stage, "environmentClass", enumValue(metamodelResource, "AwsEnvironmentClass", "DEV"));
    set(stage, "region", "us-east-1");
    set(stage, "requiresManualApproval", false);
    add(stage, "deploysStacks", stack);

    EObject model = create(metamodelResource, "AwsPsmModel");
    set(model, "id", "aws_psm_minimal_guard");
    set(model, "name", "Minimal Guard AWS PSM");
    set(model, "partition", enumValue(metamodelResource, "AwsPartition", "AWS"));
    set(model, "accountStrategy", "single-account");
    set(model, "regionStrategy", "single-region");
    set(model, "defaultRegion", "us-east-1");
    set(model, "namingConvention", "kebab-case");
    set(model, "taggingStrategy", "stage-and-service");
    set(model, "productionMode", false);
    add(model, "stages", stage);
    add(model, "stacks", stack);

    Resource modelResource = resourceSet.createResource(URI.createFileURI(modelFile.toString()));
    modelResource.getContents().add(model);
    modelResource.save(null);
  }

  /**
   * Creates a broad AWS PSM model that exercises concrete SAM/CloudFormation rendering branches.
   *
   * @param modelFile output XMI file path
   * @throws IOException when the generated fixture cannot be saved
   */
  private void createBroadAwsPsmRenderingModel(Path modelFile) throws IOException {
    Path metamodel = REPOSITORY_ROOT.resolve("mde/metamodels/psm/psm-combined.ecore");
    ResourceSet resourceSet = new ResourceSetImpl();
    resourceSet
        .getResourceFactoryRegistry()
        .getExtensionToFactoryMap()
        .put("ecore", new EcoreResourceFactoryImpl());
    resourceSet
        .getResourceFactoryRegistry()
        .getExtensionToFactoryMap()
        .put("xmi", new XMIResourceFactoryImpl());
    Resource metamodelResource =
        resourceSet.getResource(URI.createFileURI(metamodel.toString()), true);
    EcoreUtil.resolveAll(resourceSet);
    registerPackages(metamodelResource);

    EObject assumePolicy =
        allowPolicyDocument(metamodelResource, "policy_broad_assume_role", "sts:AssumeRole", "*");

    EObject lambdaRole =
        awsResource(
            metamodelResource,
            "IamRole",
            "role_broad_lambda",
            "Broad Lambda Role",
            "BroadLambdaRole");
    set(lambdaRole, "roleName", "broad-lambda-role");
    set(lambdaRole, "assumeRolePolicy", assumePolicy);

    EObject managedPolicy =
        awsResource(
            metamodelResource,
            "IamManagedPolicy",
            "policy_broad_managed",
            "Broad Managed Policy",
            "BroadManagedPolicy");
    set(managedPolicy, "managedPolicyName", "broad-managed-policy");
    set(
        managedPolicy,
        "document",
        allowPolicyDocument(
            metamodelResource, "policy_broad_managed_doc", "logs:CreateLogStream", "*"));

    EObject inlinePolicy =
        awsResource(
            metamodelResource,
            "IamPolicy",
            "policy_broad_inline",
            "Broad Inline Policy",
            "BroadInlinePolicy");
    set(inlinePolicy, "policyName", "broad-inline-policy");
    set(
        inlinePolicy,
        "document",
        allowPolicyDocument(metamodelResource, "policy_broad_inline_doc", "s3:GetObject", "*"));
    add(inlinePolicy, "roles", lambdaRole);

    EObject key = awsResource(metamodelResource, "KmsKey", "key_broad", "Broad Key", "BroadKey");
    set(key, "descriptionText", "Broad fixture KMS key.");
    set(key, "keyUsage", "ENCRYPT_DECRYPT");
    set(key, "keySpec", "SYMMETRIC_DEFAULT");
    set(key, "enabled", true);
    set(key, "enableKeyRotation", true);
    set(key, "multiRegion", false);
    set(key, "pendingWindowInDays", 7);
    set(
        key,
        "keyPolicy",
        allowPolicyDocument(metamodelResource, "policy_broad_key_doc", "kms:*", "*"));

    EObject keyAlias =
        awsResource(
            metamodelResource, "KmsAlias", "alias_broad_key", "Broad Key Alias", "BroadKeyAlias");
    set(keyAlias, "aliasName", "broad-fixture");
    set(keyAlias, "targetKey", key);

    EObject secret =
        awsResource(
            metamodelResource,
            "SecretsManagerSecret",
            "secret_broad",
            "Broad Secret",
            "BroadSecret");
    set(secret, "secretName", "broad/secret");
    set(secret, "descriptionText", "Broad fixture secret.");
    set(secret, "generateSecretStringJson", "{\"GenerateStringKey\":\"password\"}");
    set(secret, "kmsKey", key);

    EObject secretPolicy =
        awsResource(
            metamodelResource,
            "SecretsManagerResourcePolicy",
            "secret_policy_broad",
            "Broad Secret Policy",
            "BroadSecretPolicy");
    set(secretPolicy, "secret", secret);
    set(
        secretPolicy,
        "resourcePolicy",
        allowPolicyDocument(
            metamodelResource,
            "policy_broad_secret_resource",
            "secretsmanager:GetSecretValue",
            "*"));

    EObject parameter =
        awsResource(
            metamodelResource,
            "SsmParameter",
            "parameter_broad",
            "Broad Parameter",
            "BroadParameter");
    set(parameter, "parameterName", "/broad/config");
    set(parameter, "parameterType", enumValue(metamodelResource, "ParameterType", "STRING"));
    set(parameter, "tier", enumValue(metamodelResource, "SsmParameterTier", "STANDARD"));
    set(parameter, "dataType", "text");
    set(parameter, "descriptionText", "Broad fixture parameter.");
    set(parameter, "value", plaintextValue(metamodelResource, "value_broad_parameter", "enabled"));

    EObject dlq =
        awsResource(metamodelResource, "SqsQueue", "queue_broad_dlq", "Broad DLQ", "BroadDlq");
    set(dlq, "queueName", "broad-dlq");
    set(dlq, "queueType", enumValue(metamodelResource, "SqsQueueType", "STANDARD"));
    set(dlq, "sqsManagedSseEnabled", true);

    EObject queue =
        awsResource(metamodelResource, "SqsQueue", "queue_broad", "Broad Queue", "BroadQueue");
    set(queue, "queueName", "broad-queue");
    set(queue, "queueType", enumValue(metamodelResource, "SqsQueueType", "STANDARD"));
    set(queue, "visibilityTimeoutSeconds", 60);
    set(queue, "sqsManagedSseEnabled", true);
    EObject redrive = create(metamodelResource, "SqsRedrivePolicy");
    set(redrive, "id", "redrive_broad_queue");
    set(redrive, "name", "Broad Queue Redrive");
    set(redrive, "maxReceiveCount", 3);
    set(redrive, "deadLetterQueue", dlq);
    set(queue, "redrivePolicy", redrive);

    EObject queuePolicy =
        awsResource(
            metamodelResource,
            "SqsQueuePolicy",
            "queue_policy_broad",
            "Broad Queue Policy",
            "BroadQueuePolicy");
    add(queuePolicy, "queues", queue);
    set(
        queuePolicy,
        "policyDocument",
        allowPolicyDocument(metamodelResource, "policy_broad_queue_doc", "sqs:SendMessage", "*"));

    EObject topic =
        awsResource(metamodelResource, "SnsTopic", "topic_broad", "Broad Topic", "BroadTopic");
    set(topic, "topicName", "broad-topic");
    set(topic, "fifoTopic", false);
    set(topic, "displayName", "Broad Topic");
    set(topic, "kmsKey", key);

    EObject subscription =
        awsResource(
            metamodelResource,
            "SnsSubscription",
            "subscription_broad",
            "Broad Subscription",
            "BroadSubscription");
    set(subscription, "topic", topic);
    set(subscription, "protocol", enumValue(metamodelResource, "SnsProtocol", "SQS"));
    set(subscription, "endpointResource", queue);
    set(subscription, "rawMessageDelivery", true);
    set(
        subscription,
        "filterPolicyScope",
        enumValue(metamodelResource, "SnsFilterPolicyScope", "MESSAGE_BODY"));
    EObject filterRule = create(metamodelResource, "SnsFilterRule");
    set(filterRule, "id", "filter_broad_subscription");
    set(filterRule, "name", "Broad Subscription Filter");
    set(filterRule, "fieldPath", "detail.type");
    set(filterRule, "operator", "anything-but");
    add(filterRule, "values", "ignored");
    add(subscription, "filterRules", filterRule);

    EObject topicPolicy =
        awsResource(
            metamodelResource,
            "SnsTopicPolicy",
            "topic_policy_broad",
            "Broad Topic Policy",
            "BroadTopicPolicy");
    add(topicPolicy, "topics", topic);
    set(
        topicPolicy,
        "policyDocument",
        allowPolicyDocument(metamodelResource, "policy_broad_topic_doc", "sns:Publish", "*"));

    EObject table =
        awsResource(metamodelResource, "DynamoDbTable", "table_broad", "Broad Table", "BroadTable");
    set(table, "tableName", "broad-table");
    set(
        table,
        "billingMode",
        enumValue(metamodelResource, "DynamoDbBillingMode", "PAY_PER_REQUEST"));
    set(table, "tableClass", enumValue(metamodelResource, "DynamoDbTableClass", "STANDARD"));
    set(table, "pointInTimeRecoveryEnabled", true);
    set(table, "kmsKey", key);
    EObject attr = create(metamodelResource, "DynamoDbAttributeDefinition");
    set(attr, "id", "attribute_broad_pk");
    set(attr, "name", "Broad PK Attribute");
    set(attr, "attributeName", "pk");
    set(attr, "attributeType", enumValue(metamodelResource, "DynamoDbAttributeType", "S"));
    add(table, "attributeDefinitions", attr);
    EObject keySchema = create(metamodelResource, "DynamoDbKeySchemaElement");
    set(keySchema, "id", "key_broad_pk");
    set(keySchema, "name", "Broad PK Key");
    set(keySchema, "attributeName", "pk");
    set(keySchema, "keyType", enumValue(metamodelResource, "DynamoDbKeyType", "HASH"));
    add(table, "keySchema", keySchema);
    EObject streamSpec = create(metamodelResource, "DynamoDbStreamSpecification");
    set(streamSpec, "id", "stream_broad_table");
    set(streamSpec, "name", "Broad Table Stream");
    set(
        streamSpec,
        "streamViewType",
        enumValue(metamodelResource, "DynamoDbStreamViewType", "NEW_AND_OLD_IMAGES"));
    set(table, "streamSpecification", streamSpec);

    EObject bucket =
        awsResource(metamodelResource, "S3Bucket", "bucket_broad", "Broad Bucket", "BroadBucket");
    set(bucket, "bucketName", "broad-artifact-bucket");
    set(bucket, "versioningStatus", enumValue(metamodelResource, "S3VersioningStatus", "ENABLED"));
    set(
        bucket,
        "publicAccessMode",
        enumValue(metamodelResource, "S3BlockPublicAccessMode", "STRICT_BLOCK_ALL"));
    set(bucket, "eventBridgeNotificationEnabled", false);
    EObject encryption = create(metamodelResource, "S3BucketEncryption");
    set(encryption, "id", "encryption_broad_bucket");
    set(encryption, "name", "Broad Bucket Encryption");
    set(encryption, "sseAlgorithm", "aws:kms");
    set(encryption, "kmsKey", key);
    set(bucket, "encryption", encryption);
    EObject notifications = create(metamodelResource, "S3NotificationConfiguration");
    set(notifications, "id", "notification_broad_bucket");
    set(notifications, "name", "Broad Bucket Notifications");
    set(notifications, "eventBridgeEnabled", true);
    set(bucket, "notificationConfiguration", notifications);

    EObject bucketPolicy =
        awsResource(
            metamodelResource,
            "S3BucketPolicy",
            "bucket_policy_broad",
            "Broad Bucket Policy",
            "BroadBucketPolicy");
    set(bucketPolicy, "bucket", bucket);
    set(
        bucketPolicy,
        "policyDocument",
        allowPolicyDocument(metamodelResource, "policy_broad_bucket_doc", "s3:GetObject", "*"));

    EObject vpc = awsResource(metamodelResource, "Vpc", "vpc_broad", "Broad VPC", "BroadVpc");
    set(vpc, "cidrBlock", "10.0.0.0/16");
    set(vpc, "enableDnsHostnames", true);
    set(vpc, "enableDnsSupport", true);

    EObject subnet =
        awsResource(metamodelResource, "Subnet", "subnet_broad", "Broad Subnet", "BroadSubnet");
    set(subnet, "subnetName", "broad-subnet");
    set(subnet, "cidrBlock", "10.0.1.0/24");
    set(subnet, "availabilityZone", "us-east-1a");
    set(subnet, "mapPublicIpOnLaunch", false);
    set(subnet, "vpc", vpc);

    EObject securityGroup =
        awsResource(
            metamodelResource,
            "SecurityGroup",
            "sg_broad",
            "Broad Security Group",
            "BroadSecurityGroup");
    set(securityGroup, "groupDescription", "Broad fixture security group.");
    set(securityGroup, "vpc", vpc);
    EObject egress = create(metamodelResource, "SecurityGroupRule");
    set(egress, "id", "egress_broad");
    set(egress, "name", "Broad Egress");
    set(egress, "ipProtocol", "-1");
    set(egress, "cidrIp", "0.0.0.0/0");
    set(egress, "ruleDescription", "Fixture egress rule.");
    add(securityGroup, "egressRules", egress);

    EObject vpcEndpoint =
        awsResource(
            metamodelResource,
            "VpcEndpoint",
            "endpoint_broad",
            "Broad VPC Endpoint",
            "BroadVpcEndpoint");
    set(vpcEndpoint, "serviceName", "com.amazonaws.us-east-1.execute-api");
    set(vpcEndpoint, "endpointType", "Interface");
    set(vpcEndpoint, "vpc", vpc);
    add(vpcEndpoint, "subnets", subnet);
    add(vpcEndpoint, "securityGroups", securityGroup);

    EObject logGroup =
        awsResource(
            metamodelResource,
            "CloudWatchLogGroup",
            "log_group_broad",
            "Broad Log Group",
            "BroadLogGroup");
    set(logGroup, "logGroupName", "/aws/lambda/broad-function");
    set(logGroup, "retentionDays", 14);
    set(logGroup, "kmsKey", key);

    EObject code = create(metamodelResource, "LambdaZipCodeConfig");
    set(code, "id", "code_broad_function");
    set(code, "name", "Broad Function Code");

    EObject layer =
        awsResource(
            metamodelResource, "LambdaLayerVersion", "layer_broad", "Broad Layer", "BroadLayer");
    set(layer, "layerName", "broad-layer");
    set(layer, "contentUri", "layers/broad");
    add(layer, "compatibleRuntimes", "provided.al2023");
    add(
        layer,
        "compatibleArchitectures",
        enumValue(metamodelResource, "LambdaArchitecture", "ARM64"));

    EObject layerPermission =
        awsResource(
            metamodelResource,
            "LambdaLayerPermission",
            "layer_permission_broad",
            "Broad Layer Permission",
            "BroadLayerPermission");
    set(layerPermission, "principal", "*");
    set(layerPermission, "layerVersion", layer);

    EObject lambdaFunction =
        awsResource(
            metamodelResource,
            "AwsLambdaFunction",
            "lambda_broad",
            "Broad Function",
            "BroadFunction");
    set(lambdaFunction, "functionName", "broad-function");
    set(lambdaFunction, "descriptionText", "Broad fixture Go Lambda handler.");
    set(lambdaFunction, "memorySizeMb", 512);
    set(lambdaFunction, "timeoutSeconds", 60);
    set(lambdaFunction, "ephemeralStorageMb", 1024);
    set(lambdaFunction, "reservedConcurrentExecutions", 2);
    set(lambdaFunction, "autoPublishAlias", "live");
    set(
        lambdaFunction,
        "architecture",
        enumValue(metamodelResource, "LambdaArchitecture", "ARM64"));
    set(
        lambdaFunction,
        "codeSigningDecision",
        enumValue(metamodelResource, "Decision", "ACCEPTED"));
    set(lambdaFunction, "code", code);
    set(lambdaFunction, "role", lambdaRole);
    set(lambdaFunction, "logGroup", logGroup);
    set(lambdaFunction, "kmsKey", key);
    add(lambdaFunction, "layers", layer);
    EObject env = create(metamodelResource, "LambdaEnvironmentVariable");
    set(env, "id", "env_broad_function_mode");
    set(env, "name", "Broad Function Mode");
    set(env, "variableName", "MODE");
    set(env, "value", plaintextValue(metamodelResource, "value_broad_mode", "broad"));
    add(lambdaFunction, "environment", env);
    EObject dlqConfig = create(metamodelResource, "LambdaDeadLetterConfig");
    set(dlqConfig, "id", "dlq_broad_function");
    set(dlqConfig, "name", "Broad Function DLQ");
    set(dlqConfig, "targetQueue", dlq);
    set(lambdaFunction, "deadLetterConfig", dlqConfig);
    EObject tracing = create(metamodelResource, "LambdaTracingConfig");
    set(tracing, "id", "tracing_broad_function");
    set(tracing, "name", "Broad Function Tracing");
    set(tracing, "enabled", true);
    set(tracing, "mode", enumValue(metamodelResource, "LambdaTracingMode", "ACTIVE"));
    set(lambdaFunction, "tracing", tracing);
    EObject vpcConfig = create(metamodelResource, "VpcAttachmentConfig");
    set(vpcConfig, "id", "vpc_config_broad_function");
    set(vpcConfig, "name", "Broad Function VPC Config");
    add(vpcConfig, "subnetIds", "subnet-1234567890abcdef0");
    add(vpcConfig, "securityGroupIds", "sg-1234567890abcdef0");
    set(vpcConfig, "ipv6AllowedForDualStack", false);
    set(lambdaFunction, "vpcConfig", vpcConfig);
    EObject fs = create(metamodelResource, "LambdaFileSystemConfig");
    set(fs, "id", "fs_broad_function");
    set(fs, "name", "Broad Function File System");
    set(fs, "arn", "arn:aws:elasticfilesystem:us-east-1:123456789012:access-point/fsap-123");
    set(fs, "localMountPath", "/mnt/data");
    add(lambdaFunction, "fileSystemConfigs", fs);

    EObject functionVersion =
        awsResource(
            metamodelResource,
            "LambdaVersion",
            "version_broad_function",
            "Broad Function Version",
            "BroadFunctionVersion");
    set(functionVersion, "versionDescription", "Broad fixture version.");
    add(lambdaFunction, "versions", functionVersion);

    EObject functionAlias =
        awsResource(
            metamodelResource,
            "LambdaAlias",
            "alias_broad_function",
            "Broad Function Alias",
            "BroadFunctionAlias");
    set(functionAlias, "aliasName", "live");
    set(functionAlias, "version", functionVersion);
    add(lambdaFunction, "aliases", functionAlias);

    EObject functionUrl =
        awsResource(
            metamodelResource,
            "LambdaFunctionUrl",
            "url_broad_function",
            "Broad Function URL",
            "BroadFunctionUrl");
    set(
        functionUrl,
        "authType",
        enumValue(metamodelResource, "LambdaFunctionUrlAuthType", "AWS_IAM"));
    set(functionUrl, "invokeMode", enumValue(metamodelResource, "LambdaInvokeMode", "BUFFERED"));
    set(lambdaFunction, "functionUrl", functionUrl);

    EObject invokeConfig =
        awsResource(
            metamodelResource,
            "LambdaEventInvokeConfig",
            "invoke_broad_function",
            "Broad Invoke Config",
            "BroadInvokeConfig");
    set(invokeConfig, "maximumRetryAttempts", 1);
    set(invokeConfig, "maximumEventAgeInSeconds", 120);
    set(invokeConfig, "qualifierAlias", functionAlias);
    add(lambdaFunction, "eventInvokeConfigs", invokeConfig);

    EObject sqsMapping =
        awsResource(
            metamodelResource,
            "SqsLambdaEventSourceMapping",
            "mapping_broad_sqs",
            "Broad SQS Mapping",
            "BroadSqsMapping");
    set(sqsMapping, "batchSize", 5);
    set(sqsMapping, "reportBatchItemFailures", true);
    set(sqsMapping, "maximumConcurrency", 2);
    set(sqsMapping, "filterCriteriaJson", "{\"Filters\":[{\"Pattern\":\"{}\"}]}");
    set(sqsMapping, "queue", queue);
    add(lambdaFunction, "eventSourceMappings", sqsMapping);

    EObject ddbMapping =
        awsResource(
            metamodelResource,
            "DynamoDbStreamLambdaEventSourceMapping",
            "mapping_broad_ddb",
            "Broad DDB Mapping",
            "BroadDdbMapping");
    set(ddbMapping, "batchSize", 10);
    set(ddbMapping, "startingPosition", enumValue(metamodelResource, "StartingPosition", "LATEST"));
    set(ddbMapping, "table", table);
    add(lambdaFunction, "eventSourceMappings", ddbMapping);

    EObject genericMapping =
        awsResource(
            metamodelResource,
            "GenericLambdaEventSourceMapping",
            "mapping_broad_generic",
            "Broad Generic Mapping",
            "BroadGenericMapping");
    set(
        genericMapping,
        "sourceKind",
        enumValue(metamodelResource, "LambdaEventSourceKind", "KINESIS_STREAM"));
    set(
        genericMapping,
        "sourceArnExpression",
        "arn:aws:kinesis:us-east-1:123456789012:stream/broad");
    add(lambdaFunction, "eventSourceMappings", genericMapping);

    EObject permission =
        awsResource(
            metamodelResource,
            "LambdaPermission",
            "permission_broad_function",
            "Broad Lambda Permission",
            "BroadPermission");
    set(permission, "action", "lambda:InvokeFunction");
    set(permission, "principal", "apigateway.amazonaws.com");
    set(permission, "sourceArn", "${BroadHttpApi.Arn}");
    add(lambdaFunction, "permissions", permission);

    EObject lambdaNotification = create(metamodelResource, "S3NotificationRule");
    set(lambdaNotification, "id", "notification_broad_lambda");
    set(lambdaNotification, "name", "Broad Lambda Notification");
    add(lambdaNotification, "eventTypes", "s3:ObjectCreated:*");
    set(lambdaNotification, "filterPrefix", "incoming/");
    set(lambdaNotification, "destination", lambdaFunction);
    add(notifications, "rules", lambdaNotification);
    EObject queueNotification = create(metamodelResource, "S3NotificationRule");
    set(queueNotification, "id", "notification_broad_queue");
    set(queueNotification, "name", "Broad Queue Notification");
    add(queueNotification, "eventTypes", "s3:ObjectRemoved:*");
    set(queueNotification, "destination", queue);
    add(notifications, "rules", queueNotification);
    EObject topicNotification = create(metamodelResource, "S3NotificationRule");
    set(topicNotification, "id", "notification_broad_topic");
    set(topicNotification, "name", "Broad Topic Notification");
    add(topicNotification, "eventTypes", "s3:ObjectRestore:*");
    set(topicNotification, "destination", topic);
    add(notifications, "rules", topicNotification);

    EObject bus =
        awsResource(
            metamodelResource, "EventBridgeBus", "bus_broad", "Broad Event Bus", "BroadEventBus");
    set(bus, "busName", "broad-bus");

    EObject busPolicy =
        awsResource(
            metamodelResource,
            "EventBridgeBusPolicy",
            "bus_policy_broad",
            "Broad Event Bus Policy",
            "BroadEventBusPolicy");
    set(busPolicy, "statementId", "AllowAccount");
    set(busPolicy, "bus", bus);
    set(
        busPolicy,
        "policyDocument",
        allowPolicyDocument(metamodelResource, "policy_broad_bus_doc", "events:PutEvents", "*"));

    EObject target = create(metamodelResource, "EventBridgeTarget");
    set(target, "id", "target_broad_rule");
    set(target, "name", "Broad Rule Target");
    set(target, "targetId", "BroadFunctionTarget");
    set(target, "targetKind", enumValue(metamodelResource, "EventBridgeTargetKind", "LAMBDA"));
    set(target, "targetResource", lambdaFunction);
    set(target, "deadLetterQueue", dlq);
    EObject retryPolicy = create(metamodelResource, "AwsRetryPolicy");
    set(retryPolicy, "id", "retry_broad_target");
    set(retryPolicy, "name", "Broad Target Retry");
    set(retryPolicy, "maximumRetryAttempts", 2);
    set(retryPolicy, "maximumEventAgeInSeconds", 300);
    set(target, "retryPolicy", retryPolicy);

    EObject eventRule =
        awsResource(
            metamodelResource,
            "EventBridgeRule",
            "rule_broad",
            "Broad Event Rule",
            "BroadEventRule");
    set(eventRule, "ruleName", "broad-rule");
    set(eventRule, "descriptionText", "Broad fixture rule.");
    set(eventRule, "eventPatternJson", "{\"source\":[\"broad\"]}");
    set(eventRule, "state", "ENABLED");
    set(eventRule, "bus", bus);
    add(eventRule, "targets", target);

    EObject archive =
        awsResource(
            metamodelResource,
            "EventBridgeArchive",
            "archive_broad",
            "Broad Event Archive",
            "BroadEventArchive");
    set(archive, "archiveName", "broad-archive");
    set(archive, "eventPatternJson", "{\"source\":[\"broad\"]}");
    set(archive, "retentionDays", 7);
    set(archive, "eventSourceBus", bus);

    EObject scheduleTarget = create(metamodelResource, "EventBridgeTarget");
    set(scheduleTarget, "id", "target_broad_schedule");
    set(scheduleTarget, "name", "Broad Schedule Target");
    set(scheduleTarget, "targetId", "BroadScheduleTarget");
    set(
        scheduleTarget,
        "targetKind",
        enumValue(metamodelResource, "EventBridgeTargetKind", "STEP_FUNCTIONS"));
    set(scheduleTarget, "targetResource", lambdaFunction);
    EObject schedule =
        awsResource(
            metamodelResource,
            "EventBridgeSchedule",
            "schedule_broad",
            "Broad Schedule",
            "BroadSchedule");
    set(schedule, "scheduleName", "broad-schedule");
    set(schedule, "scheduleExpression", "rate(5 minutes)");
    set(schedule, "state", "ENABLED");
    set(schedule, "target", scheduleTarget);
    set(schedule, "role", lambdaRole);

    EObject pipe =
        awsResource(metamodelResource, "EventBridgePipe", "pipe_broad", "Broad Pipe", "BroadPipe");
    set(pipe, "pipeName", "broad-pipe");
    set(pipe, "desiredState", "RUNNING");
    set(pipe, "sourceResource", queue);
    set(pipe, "targetResource", lambdaFunction);
    set(pipe, "enrichmentFunction", lambdaFunction);
    set(pipe, "role", lambdaRole);
    set(pipe, "filterCriteriaJson", "{\"Filters\":[{\"Pattern\":\"{}\"}]}");

    EObject apiKeyValue = plaintextValue(metamodelResource, "value_broad_connection_key", "secret");
    EObject authParameters = create(metamodelResource, "EventBridgeApiKeyAuthParameters");
    set(authParameters, "id", "auth_broad_connection");
    set(authParameters, "name", "Broad Connection Auth");
    set(authParameters, "apiKeyName", "x-api-key");
    set(authParameters, "apiKeyValue", apiKeyValue);
    EObject connection =
        awsResource(
            metamodelResource,
            "EventBridgeConnection",
            "connection_broad",
            "Broad Connection",
            "BroadConnection");
    set(connection, "connectionName", "broad-connection");
    set(
        connection,
        "authorizationType",
        enumValue(metamodelResource, "EventBridgeConnectionAuthorizationType", "API_KEY"));
    set(connection, "authParameters", authParameters);

    EObject apiDestination =
        awsResource(
            metamodelResource,
            "EventBridgeApiDestination",
            "destination_broad",
            "Broad API Destination",
            "BroadApiDestination");
    set(apiDestination, "destinationName", "broad-destination");
    set(apiDestination, "invocationEndpoint", "https://example.com/events");
    set(
        apiDestination,
        "httpMethod",
        enumValue(metamodelResource, "EventBridgeHttpMethod", "POST"));
    set(apiDestination, "invocationRateLimitPerSecond", 10);
    set(apiDestination, "connection", connection);

    EObject userPool =
        awsResource(
            metamodelResource,
            "CognitoUserPool",
            "user_pool_broad",
            "Broad User Pool",
            "BroadUserPool");
    set(userPool, "userPoolName", "broad-user-pool");
    set(userPool, "mfaDecision", enumValue(metamodelResource, "Decision", "ACCEPTED"));
    set(
        userPool,
        "mfaConfiguration",
        enumValue(metamodelResource, "CognitoMfaConfiguration", "OPTIONAL"));
    add(userPool, "usernameAttributes", "email");
    add(userPool, "autoVerifiedAttributes", "email");
    set(userPool, "deletionProtection", true);

    EObject userPoolClient =
        awsResource(
            metamodelResource,
            "CognitoUserPoolClient",
            "user_pool_client_broad",
            "Broad User Pool Client",
            "BroadUserPoolClient");
    set(userPoolClient, "clientName", "broad-client");
    set(userPoolClient, "generateSecret", false);
    add(userPoolClient, "explicitAuthFlows", "ALLOW_USER_PASSWORD_AUTH");
    set(userPoolClient, "userPool", userPool);

    EObject userPoolGroup =
        awsResource(
            metamodelResource,
            "CognitoUserPoolGroup",
            "user_pool_group_broad",
            "Broad User Pool Group",
            "BroadUserPoolGroup");
    set(userPoolGroup, "groupName", "operators");
    set(userPoolGroup, "descriptionText", "Broad fixture operators.");
    set(userPoolGroup, "precedence", 1);
    set(userPoolGroup, "userPool", userPool);
    set(userPoolGroup, "role", lambdaRole);

    EObject userPoolDomain =
        awsResource(
            metamodelResource,
            "CognitoUserPoolDomain",
            "user_pool_domain_broad",
            "Broad User Pool Domain",
            "BroadUserPoolDomain");
    set(userPoolDomain, "domain", "broad-fixture");
    set(userPoolDomain, "userPool", userPool);

    EObject identityPool =
        awsResource(
            metamodelResource,
            "CognitoIdentityPool",
            "identity_pool_broad",
            "Broad Identity Pool",
            "BroadIdentityPool");
    set(identityPool, "identityPoolName", "broad-identity-pool");
    set(identityPool, "allowUnauthenticatedIdentities", false);

    EObject metricFilter =
        awsResource(
            metamodelResource,
            "CloudWatchMetricFilter",
            "metric_filter_broad",
            "Broad Metric Filter",
            "BroadMetricFilter");
    set(metricFilter, "filterPattern", "{ $.level = \"error\" }");
    set(
        metricFilter,
        "metricTransformationsJson",
        "[{\"MetricName\":\"Errors\",\"MetricNamespace\":\"Broad\",\"MetricValue\":\"1\"}]");
    set(metricFilter, "logGroup", logGroup);

    EObject subscriptionFilter =
        awsResource(
            metamodelResource,
            "CloudWatchLogSubscriptionFilter",
            "subscription_filter_broad",
            "Broad Subscription Filter",
            "BroadSubscriptionFilter");
    set(subscriptionFilter, "filterPattern", "");
    set(subscriptionFilter, "destinationResource", lambdaFunction);
    set(subscriptionFilter, "role", lambdaRole);
    set(subscriptionFilter, "logGroup", logGroup);

    EObject alarm =
        awsResource(
            metamodelResource, "CloudWatchAlarm", "alarm_broad", "Broad Alarm", "BroadAlarm");
    set(alarm, "alarmName", "broad-alarm");
    set(alarm, "namespace", "AWS/Lambda");
    set(alarm, "metricName", "Errors");
    set(alarm, "statistic", "Sum");
    set(alarm, "period", 60);
    set(alarm, "evaluationPeriods", 1);
    set(alarm, "threshold", 1.0);
    set(
        alarm,
        "comparisonOperator",
        enumValue(metamodelResource, "CloudWatchComparisonOperator", "GREATER_THAN_THRESHOLD"));

    EObject compositeAlarm =
        awsResource(
            metamodelResource,
            "CloudWatchCompositeAlarm",
            "composite_alarm_broad",
            "Broad Composite Alarm",
            "BroadCompositeAlarm");
    set(compositeAlarm, "alarmRule", "ALARM(BroadAlarm)");

    EObject dashboard =
        awsResource(
            metamodelResource,
            "CloudWatchDashboard",
            "dashboard_broad",
            "Broad Dashboard",
            "BroadDashboard");
    set(dashboard, "dashboardName", "broad-dashboard");
    set(dashboard, "dashboardBodyJson", "{\"widgets\":[]}");

    EObject wsIntegration =
        awsResource(
            metamodelResource,
            "ApiGatewayIntegration",
            "integration_broad_ws",
            "Broad WS Integration",
            "BroadWsIntegration");
    set(
        wsIntegration,
        "integrationType",
        enumValue(metamodelResource, "ApiGatewayIntegrationType", "AWS_PROXY"));
    set(wsIntegration, "integrationMethod", "POST");
    set(wsIntegration, "payloadFormatVersion", "2.0");
    set(wsIntegration, "lambdaTarget", lambdaFunction);

    EObject wsRoute =
        awsResource(
            metamodelResource,
            "WebSocketRoute",
            "route_broad_ws",
            "Broad WS Route",
            "BroadWsRoute");
    set(wsRoute, "routeKey", "$default");
    set(
        wsRoute,
        "authorizationType",
        enumValue(metamodelResource, "ApiGatewayAuthorizationType", "NONE"));
    set(wsRoute, "integration", wsIntegration);

    EObject wsStage =
        awsResource(
            metamodelResource,
            "WebSocketStage",
            "stage_broad_ws",
            "Broad WS Stage",
            "BroadWsStage");
    set(wsStage, "stageName", "dev");
    set(wsStage, "autoDeploy", true);

    EObject jwtAuthorizer =
        awsResource(
            metamodelResource,
            "JwtAuthorizer",
            "authorizer_broad_jwt",
            "Broad JWT Authorizer",
            "BroadJwtAuthorizer");
    set(jwtAuthorizer, "authorizerName", "broad-jwt");
    set(jwtAuthorizer, "identitySource", "$request.header.Authorization");
    set(
        jwtAuthorizer,
        "authorizationType",
        enumValue(metamodelResource, "ApiGatewayAuthorizationType", "JWT"));
    set(jwtAuthorizer, "issuer", "https://issuer.example.com");
    add(jwtAuthorizer, "audience", "broad-client");

    EObject webSocketApi =
        awsResource(
            metamodelResource,
            "WebSocketApi",
            "api_broad_ws",
            "Broad WebSocket API",
            "BroadWebSocketApi");
    set(webSocketApi, "apiName", "Broad WebSocket API");
    set(webSocketApi, "routeSelectionExpression", "$request.body.action");
    add(webSocketApi, "routes", wsRoute);
    add(webSocketApi, "stages", wsStage);
    add(webSocketApi, "authorizers", jwtAuthorizer);

    EObject httpIntegration = create(metamodelResource, "ApiGatewayIntegration");
    set(httpIntegration, "id", "integration_broad_http");
    set(httpIntegration, "name", "Broad HTTP Integration");
    set(httpIntegration, "logicalId", "BroadHttpIntegration");
    set(
        httpIntegration,
        "integrationType",
        enumValue(metamodelResource, "ApiGatewayIntegrationType", "AWS_PROXY"));
    set(httpIntegration, "integrationMethod", "POST");
    set(httpIntegration, "payloadFormatVersion", "2.0");
    set(httpIntegration, "lambdaTarget", lambdaFunction);
    EObject httpRoute =
        awsResource(
            metamodelResource,
            "HttpApiRoute",
            "route_broad_http",
            "Broad HTTP Route",
            "BroadHttpRoute");
    set(httpRoute, "path", "/broad");
    set(httpRoute, "routeKey", "POST /broad");
    set(httpRoute, "operationName", "broad-operation");
    set(httpRoute, "method", enumValue(metamodelResource, "ApiGatewayHttpMethod", "POST"));
    set(
        httpRoute,
        "authorizationType",
        enumValue(metamodelResource, "ApiGatewayAuthorizationType", "AWS_IAM"));
    set(httpRoute, "integration", httpIntegration);
    EObject httpApi =
        awsResource(
            metamodelResource, "HttpApi", "api_broad_http", "Broad HTTP API", "BroadHttpApi");
    set(httpApi, "apiName", "Broad HTTP API");
    set(httpApi, "openApiVersion", "3.0.3");
    set(httpApi, "protocolType", "HTTP");
    add(httpApi, "routes", httpRoute);

    EObject restStage =
        awsResource(
            metamodelResource,
            "RestApiStage",
            "stage_broad_rest",
            "Broad REST Stage",
            "BroadRestStage");
    set(restStage, "stageName", "prod");
    EObject restApi =
        awsResource(
            metamodelResource, "RestApi", "api_broad_rest", "Broad REST API", "BroadRestApi");
    set(restApi, "apiName", "Broad REST API");
    set(
        restApi,
        "endpointType",
        enumValue(metamodelResource, "ApiGatewayEndpointType", "REGIONAL"));
    add(restApi, "stages", restStage);

    EObject deployment =
        awsResource(
            metamodelResource,
            "ApiGatewayDeployment",
            "deployment_broad",
            "Broad Deployment",
            "BroadDeployment");
    set(deployment, "descriptionText", "Broad fixture deployment.");
    set(deployment, "api", restApi);

    EObject domain =
        awsResource(
            metamodelResource,
            "ApiGatewayDomainName",
            "domain_broad",
            "Broad Domain",
            "BroadDomain");
    set(domain, "domainName", "api.example.com");
    set(domain, "certificateArn", "arn:aws:acm:us-east-1:123456789012:certificate/broad");
    set(domain, "securityPolicy", "TLS_1_2");
    set(domain, "api", httpApi);

    EObject mapping =
        awsResource(
            metamodelResource,
            "ApiGatewayBasePathMapping",
            "mapping_broad",
            "Broad API Mapping",
            "BroadBasePathMapping");
    set(mapping, "domainName", domain);
    set(mapping, "api", httpApi);
    set(mapping, "basePath", "v1");

    EObject apiKey =
        awsResource(
            metamodelResource, "ApiGatewayApiKey", "api_key_broad", "Broad API Key", "BroadApiKey");
    set(apiKey, "apiKeyName", "broad-api-key");
    set(apiKey, "enabled", true);
    set(apiKey, "descriptionText", "Broad fixture API key.");
    add(apiKey, "stages", restStage);

    EObject usagePlan =
        awsResource(
            metamodelResource,
            "ApiGatewayUsagePlan",
            "usage_plan_broad",
            "Broad Usage Plan",
            "BroadUsagePlan");
    set(usagePlan, "usagePlanName", "broad-usage-plan");
    set(usagePlan, "descriptionText", "Broad fixture usage plan.");
    set(usagePlan, "throttleBurstLimit", 100);
    set(usagePlan, "throttleRateLimit", 50.0);
    set(usagePlan, "quotaLimit", 1000);
    set(usagePlan, "quotaPeriod", "MONTH");
    add(usagePlan, "apiStages", restStage);

    EObject usagePlanKey =
        awsResource(
            metamodelResource,
            "ApiGatewayUsagePlanKey",
            "usage_plan_key_broad",
            "Broad Usage Plan Key",
            "BroadUsagePlanKey");
    set(usagePlanKey, "apiKey", apiKey);
    set(usagePlanKey, "usagePlan", usagePlan);

    EObject waf =
        awsResource(
            metamodelResource,
            "WafWebAclAssociation",
            "waf_broad",
            "Broad WAF Association",
            "BroadWafAssociation");
    set(waf, "webAclArn", "arn:aws:wafv2:us-east-1:123456789012:regional/webacl/broad/1234");
    set(waf, "protectedResource", wsStage);

    EObject success =
        createAslState(metamodelResource, "state_broad_success", "Success", "SUCCEED");
    EObject failure = createAslState(metamodelResource, "state_broad_failure", "Failure", "FAIL");
    EObject wait = createAslState(metamodelResource, "state_broad_wait", "Wait", "WAIT");
    set(wait, "nextState", success);
    EObject task = createAslState(metamodelResource, "state_broad_task", "Task", "TASK");
    set(task, "invokedResource", lambdaFunction);
    set(task, "timeoutSeconds", 30);
    set(task, "nextState", wait);
    EObject retry = create(metamodelResource, "AslRetryRule");
    set(retry, "id", "retry_broad_task");
    set(retry, "name", "Broad Task Retry");
    add(retry, "errorEquals", "States.ALL");
    set(retry, "intervalSeconds", 1);
    set(retry, "maxAttempts", 2);
    set(retry, "backoffRate", 2.0);
    add(task, "retry", retry);
    EObject catcher = create(metamodelResource, "AslCatchRule");
    set(catcher, "id", "catch_broad_task");
    set(catcher, "name", "Broad Task Catch");
    add(catcher, "errorEquals", "States.ALL");
    set(catcher, "resultPath", "$.error");
    set(catcher, "nextState", failure);
    add(task, "catch", catcher);
    EObject choice = createAslState(metamodelResource, "state_broad_choice", "Choice", "CHOICE");
    set(choice, "nextState", task);
    EObject choiceRule = create(metamodelResource, "AslChoiceRule");
    set(choiceRule, "id", "choice_rule_broad");
    set(choiceRule, "name", "Broad Choice Rule");
    set(choiceRule, "variable", "$.type");
    set(choiceRule, "conditionExpression", "run");
    set(choiceRule, "nextState", task);
    add(choice, "choices", choiceRule);
    EObject pass = createAslState(metamodelResource, "state_broad_pass", "Pass", "PASS");
    set(pass, "nextState", choice);
    EObject branchState =
        createAslState(metamodelResource, "state_broad_branch_pass", "BranchPass", "PASS");
    set(branchState, "end", true);
    EObject branch = create(metamodelResource, "AslBranch");
    set(branch, "id", "branch_broad_parallel");
    set(branch, "name", "Broad Parallel Branch");
    set(branch, "startAt", "BranchPass");
    add(branch, "states", branchState);
    EObject parallel =
        createAslState(metamodelResource, "state_broad_parallel", "Parallel", "PARALLEL");
    add(parallel, "branches", branch);
    set(parallel, "nextState", pass);
    EObject mapBranchState =
        createAslState(metamodelResource, "state_broad_map_pass", "MapPass", "PASS");
    set(mapBranchState, "end", true);
    EObject mapBranch = create(metamodelResource, "AslBranch");
    set(mapBranch, "id", "branch_broad_map");
    set(mapBranch, "name", "Broad Map Branch");
    set(mapBranch, "startAt", "MapPass");
    add(mapBranch, "states", mapBranchState);
    EObject mapConfig = create(metamodelResource, "AslMapConfig");
    set(mapConfig, "id", "map_config_broad");
    set(mapConfig, "name", "Broad Map Config");
    set(mapConfig, "itemsPath", "$.items");
    set(mapConfig, "maxConcurrency", 2);
    set(mapConfig, "itemProcessor", mapBranch);
    EObject map = createAslState(metamodelResource, "state_broad_map", "Map", "MAP");
    set(map, "mapConfig", mapConfig);
    set(map, "nextState", parallel);

    EObject aslDocument = create(metamodelResource, "AslDocument");
    set(aslDocument, "id", "document_broad_workflow");
    set(aslDocument, "name", "Broad Workflow ASL");
    set(aslDocument, "comment", "Broad workflow definition.");
    set(aslDocument, "queryLanguage", "JSONPath");
    set(aslDocument, "startAt", "Map");
    addAll(aslDocument, "states", map, parallel, pass, choice, task, wait, success, failure);

    EObject stateMachine =
        awsResource(
            metamodelResource,
            "StepFunctionStateMachine",
            "state_machine_broad",
            "Broad Workflow",
            "BroadStateMachine");
    set(stateMachine, "stateMachineName", "broad-workflow");
    set(
        stateMachine,
        "stateMachineType",
        enumValue(metamodelResource, "StepFunctionType", "STANDARD"));
    set(stateMachine, "role", lambdaRole);
    set(stateMachine, "aslDocument", aslDocument);
    EObject workflowTracing = create(metamodelResource, "StepFunctionTracingConfig");
    set(workflowTracing, "id", "tracing_broad_workflow");
    set(workflowTracing, "name", "Broad Workflow Tracing");
    set(workflowTracing, "enabled", true);
    set(stateMachine, "tracing", workflowTracing);
    EObject workflowLogging = create(metamodelResource, "StepFunctionLoggingConfig");
    set(workflowLogging, "id", "logging_broad_workflow");
    set(workflowLogging, "name", "Broad Workflow Logging");
    set(workflowLogging, "includeExecutionData", true);
    set(workflowLogging, "level", "ALL");
    set(workflowLogging, "logGroup", logGroup);
    set(stateMachine, "logging", workflowLogging);

    EObject nativeValue =
        plaintextValue(metamodelResource, "value_broad_native_bucket", "broad-native-bucket");
    EObject nativeProperty = create(metamodelResource, "NativeProperty");
    set(nativeProperty, "id", "property_broad_native_bucket");
    set(nativeProperty, "name", "Broad Native Bucket Name");
    set(nativeProperty, "propertyName", "BucketName");
    set(nativeProperty, "format", enumValue(metamodelResource, "StructuredFormat", "TEXT"));
    set(nativeProperty, "value", nativeValue);
    EObject nativeBucket =
        awsResource(
            metamodelResource,
            "AwsNativeResource",
            "native_bucket_broad",
            "Broad Native Bucket",
            "BroadNativeBucket");
    set(nativeBucket, "cloudFormationType", "AWS::S3::Bucket");
    add(nativeBucket, "properties", nativeProperty);

    EObject jsonDocument =
        createStructuredDocument(
            metamodelResource,
            "document_broad_json",
            "Broad JSON Document",
            "JSON",
            "{\"broad\":true}\n");
    EObject yamlDocument =
        createStructuredDocument(
            metamodelResource,
            "document_broad_yaml",
            "Broad YAML Document",
            "YAML",
            "broad: true\n");
    EObject textDocument =
        createStructuredDocument(
            metamodelResource,
            "document_broad_text",
            "Broad Text Document",
            "TEXT",
            "Broad text document.\n");

    EObject rotation =
        awsResource(
            metamodelResource,
            "SecretRotationSchedule",
            "rotation_broad_secret",
            "Broad Secret Rotation",
            "BroadSecretRotation");
    set(rotation, "secret", secret);
    set(rotation, "rotationLambda", lambdaFunction);
    set(rotation, "rotationRulesJson", "{\"AutomaticallyAfterDays\":30}");

    EObject stack = create(metamodelResource, "SamStack");
    set(stack, "id", "stack_broad");
    set(stack, "name", "Broad Stack");
    set(stack, "stackName", "modriss-broad");
    set(stack, "templatePath", "template.yaml");
    set(stack, "templateDescription", "Broad generated stack.");
    set(stack, "useSamTransform", true);
    add(stack, "capabilities", enumValue(metamodelResource, "SamCapability", "CAPABILITY_IAM"));

    List<EObject> directResources =
        List.of(
            lambdaRole,
            managedPolicy,
            inlinePolicy,
            key,
            keyAlias,
            secret,
            secretPolicy,
            rotation,
            parameter,
            vpc,
            subnet,
            securityGroup,
            vpcEndpoint,
            table,
            bucket,
            bucketPolicy,
            dlq,
            queue,
            queuePolicy,
            topic,
            subscription,
            topicPolicy,
            bus,
            busPolicy,
            eventRule,
            archive,
            schedule,
            pipe,
            connection,
            apiDestination,
            userPool,
            userPoolClient,
            userPoolGroup,
            userPoolDomain,
            identityPool,
            logGroup,
            metricFilter,
            subscriptionFilter,
            alarm,
            compositeAlarm,
            dashboard,
            layer,
            layerPermission,
            lambdaFunction,
            httpApi,
            httpIntegration,
            restApi,
            deployment,
            webSocketApi,
            wsIntegration,
            domain,
            mapping,
            apiKey,
            usagePlan,
            usagePlanKey,
            waf,
            stateMachine,
            nativeBucket);
    for (EObject resource : directResources) {
      add(stack, "resources", resource);
    }

    EObject stage = create(metamodelResource, "AwsStage");
    set(stage, "id", "stage_broad");
    set(stage, "name", "Broad Stage");
    set(stage, "stageName", "broad");
    set(stage, "environmentClass", enumValue(metamodelResource, "AwsEnvironmentClass", "TEST"));
    set(stage, "region", "us-east-1");
    set(stage, "requiresManualApproval", false);
    add(stage, "deploysStacks", stack);

    EObject model = create(metamodelResource, "AwsPsmModel");
    set(model, "id", "aws_psm_broad");
    set(model, "name", "Broad AWS PSM");
    set(model, "partition", enumValue(metamodelResource, "AwsPartition", "AWS"));
    set(model, "accountStrategy", "single-account");
    set(model, "regionStrategy", "single-region");
    set(model, "defaultRegion", "us-east-1");
    set(model, "namingConvention", "kebab-case");
    set(model, "taggingStrategy", "stage-and-service");
    set(model, "productionMode", false);
    add(model, "stages", stage);
    add(model, "stacks", stack);
    for (EObject resource : directResources) {
      add(model, "allResources", resource);
    }
    add(model, "documents", jsonDocument);
    add(model, "documents", yamlDocument);
    add(model, "documents", textDocument);

    Resource modelResource = resourceSet.createResource(URI.createFileURI(modelFile.toString()));
    modelResource.getContents().add(model);
    modelResource.save(null);
  }

  /**
   * Creates a structured document fixture.
   *
   * @param metamodelResource loaded AWS PSM metamodel
   * @param id stable fixture id
   * @param name display name
   * @param formatLiteral structured format literal
   * @param content document content
   * @return configured structured document
   */
  private EObject createStructuredDocument(
      Resource metamodelResource, String id, String name, String formatLiteral, String content) {
    EObject document = create(metamodelResource, "StructuredDocument");
    set(document, "id", id);
    set(document, "name", name);
    set(document, "format", enumValue(metamodelResource, "StructuredFormat", formatLiteral));
    set(document, "content", content);
    return document;
  }

  /**
   * Creates a named ASL state for broad workflow fixtures.
   *
   * @param metamodelResource loaded AWS PSM metamodel
   * @param id stable fixture id
   * @param stateName ASL state name
   * @param typeLiteral ASL state kind used to select its concrete classifier
   * @return configured ASL state
   */
  private EObject createAslState(
      Resource metamodelResource, String id, String stateName, String typeLiteral) {
    String classifierName =
        switch (typeLiteral) {
          case "PASS" -> "AslPassState";
          case "TASK" -> "AslTaskState";
          case "CHOICE" -> "AslChoiceState";
          case "WAIT" -> "AslWaitState";
          case "SUCCEED" -> "AslSucceedState";
          case "FAIL" -> "AslFailState";
          case "PARALLEL" -> "AslParallelState";
          case "MAP" -> "AslMapState";
          default ->
              throw new IllegalArgumentException("Unsupported ASL state kind: " + typeLiteral);
        };
    EObject state = create(metamodelResource, classifierName);
    set(state, "id", id);
    set(state, "name", stateName);
    set(state, "stateName", stateName);
    return state;
  }

  /**
   * Creates a representative AWS PSM model that drives Lambda, IAM, CloudWatch, S3, SAM stack, and
   * stage artifact generation.
   *
   * @param modelFile output XMI file path
   * @throws IOException when the generated fixture cannot be saved
   */
  private void createRepresentativeAwsPsmModel(Path modelFile) throws IOException {
    Path metamodel = REPOSITORY_ROOT.resolve("mde/metamodels/psm/psm-combined.ecore");
    ResourceSet resourceSet = new ResourceSetImpl();
    resourceSet
        .getResourceFactoryRegistry()
        .getExtensionToFactoryMap()
        .put("ecore", new EcoreResourceFactoryImpl());
    resourceSet
        .getResourceFactoryRegistry()
        .getExtensionToFactoryMap()
        .put("xmi", new XMIResourceFactoryImpl());
    Resource metamodelResource =
        resourceSet.getResource(URI.createFileURI(metamodel.toString()), true);
    EcoreUtil.resolveAll(resourceSet);
    registerPackages(metamodelResource);

    EObject value = create(metamodelResource, "ValueExpression");
    set(value, "id", "value_bucket_name");
    set(value, "name", "Bucket Name Value");
    set(value, "sourceKind", enumValue(metamodelResource, "ValueSourceKind", "PLAINTEXT"));
    set(value, "literal", "modriss-generated-artifact-bucket");
    set(value, "secret", false);

    EObject property = create(metamodelResource, "NativeProperty");
    set(property, "id", "prop_bucket_name");
    set(property, "name", "Bucket Name Property");
    set(property, "propertyName", "BucketName");
    set(property, "format", enumValue(metamodelResource, "StructuredFormat", "TEXT"));
    set(property, "value", value);
    set(property, "required", true);
    set(property, "secret", false);
    set(property, "validationState", enumValue(metamodelResource, "Decision", "GENERATOR_OWNED"));

    EObject bucket = create(metamodelResource, "AwsNativeResource");
    set(bucket, "id", "resource_artifact_bucket");
    set(bucket, "name", "Artifact Bucket");
    set(bucket, "logicalId", "ArtifactBucket");
    set(bucket, "cloudFormationType", "AWS::S3::Bucket");
    set(bucket, "productionCritical", false);
    set(bucket, "importedResource", false);
    add(bucket, "properties", property);

    EObject assumeStatement = create(metamodelResource, "IamStatement");
    set(assumeStatement, "id", "stmt_lambda_assume_role");
    set(assumeStatement, "name", "Lambda Assume Role Statement");
    set(assumeStatement, "effect", enumValue(metamodelResource, "IamEffect", "ALLOW"));
    add(assumeStatement, "actions", "sts:AssumeRole");
    add(assumeStatement, "resources", "*");
    set(assumeStatement, "wildcardResource", true);
    set(
        assumeStatement,
        "wildcardJustification",
        "Assume-role trust policy uses AWS IAM wildcard resource semantics.");

    EObject assumePrincipal = create(metamodelResource, "IamPrincipal");
    set(assumePrincipal, "id", "principal_lambda_service");
    set(assumePrincipal, "name", "Lambda Service Principal");
    set(assumePrincipal, "principalType", "Service");
    add(assumePrincipal, "identifiers", "lambda.amazonaws.com");
    add(assumeStatement, "principals", assumePrincipal);

    EObject assumePolicy = create(metamodelResource, "IamPolicyDocument");
    set(assumePolicy, "id", "policy_lambda_assume_role");
    set(assumePolicy, "name", "Lambda Assume Role Policy");
    set(assumePolicy, "version", "2012-10-17");
    add(assumePolicy, "statements", assumeStatement);

    EObject lambdaRole = create(metamodelResource, "IamRole");
    set(lambdaRole, "id", "role_order_handler");
    set(lambdaRole, "name", "Order Handler Role");
    set(lambdaRole, "logicalId", "OrderHandlerRole");
    set(lambdaRole, "roleName", "order-handler-role");
    set(lambdaRole, "assumeRolePolicy", assumePolicy);

    EObject logGroup = create(metamodelResource, "CloudWatchLogGroup");
    set(logGroup, "id", "log_group_order_handler");
    set(logGroup, "name", "Order Handler Log Group");
    set(logGroup, "logicalId", "OrderHandlerLogGroup");
    set(logGroup, "logGroupName", "/aws/lambda/order-handler");
    set(logGroup, "retentionDays", 30);

    EObject lambdaCode = create(metamodelResource, "LambdaZipCodeConfig");
    set(lambdaCode, "id", "code_order_handler");
    set(lambdaCode, "name", "Order Handler Code");

    EObject lambdaFunction = create(metamodelResource, "AwsLambdaFunction");
    set(lambdaFunction, "id", "lambda_order_handler");
    set(lambdaFunction, "name", "Order Handler");
    set(lambdaFunction, "logicalId", "OrderHandler");
    set(lambdaFunction, "functionName", "order-handler");
    set(lambdaFunction, "descriptionText", "Representative Go Lambda handler.");
    set(lambdaFunction, "memorySizeMb", 256);
    set(lambdaFunction, "timeoutSeconds", 30);
    set(
        lambdaFunction,
        "architecture",
        enumValue(metamodelResource, "LambdaArchitecture", "X86_64"));
    set(
        lambdaFunction,
        "codeSigningDecision",
        enumValue(metamodelResource, "Decision", "NOT_REQUIRED"));
    set(lambdaFunction, "code", lambdaCode);
    set(lambdaFunction, "role", lambdaRole);
    set(lambdaFunction, "logGroup", logGroup);

    EObject completedState = create(metamodelResource, "AslSucceedState");
    set(completedState, "id", "state_workflow_completed");
    set(completedState, "name", "Workflow Completed");
    set(completedState, "stateName", "Completed");

    EObject startState = create(metamodelResource, "AslPassState");
    set(startState, "id", "state_workflow_start");
    set(startState, "name", "Workflow Start");
    set(startState, "stateName", "Start");
    set(startState, "inputPath", "$");
    set(startState, "outputPath", "$");
    set(startState, "nextState", completedState);

    EObject aslDocument = create(metamodelResource, "AslDocument");
    set(aslDocument, "id", "document_representative_workflow");
    set(aslDocument, "name", "Representative Workflow ASL");
    set(aslDocument, "comment", "Representative workflow definition.");
    set(aslDocument, "queryLanguage", "JSONPath");
    set(aslDocument, "startAt", "Start");
    add(aslDocument, "states", startState);
    add(aslDocument, "states", completedState);

    EObject stateMachine = create(metamodelResource, "StepFunctionStateMachine");
    set(stateMachine, "id", "state_machine_representative_workflow");
    set(stateMachine, "name", "Representative Workflow");
    set(stateMachine, "logicalId", "RepresentativeWorkflow");
    set(stateMachine, "stateMachineName", "representative-workflow");
    set(
        stateMachine,
        "stateMachineType",
        enumValue(metamodelResource, "StepFunctionType", "STANDARD"));
    set(stateMachine, "role", lambdaRole);
    set(stateMachine, "aslDocument", aslDocument);

    EObject apiIntegration = create(metamodelResource, "ApiGatewayIntegration");
    set(apiIntegration, "id", "integration_order_handler");
    set(apiIntegration, "name", "Order Handler Integration");
    set(apiIntegration, "logicalId", "OrderHandlerIntegration");
    set(
        apiIntegration,
        "integrationType",
        enumValue(metamodelResource, "ApiGatewayIntegrationType", "AWS_PROXY"));
    set(apiIntegration, "integrationMethod", "POST");
    set(apiIntegration, "payloadFormatVersion", "2.0");
    set(apiIntegration, "lambdaTarget", lambdaFunction);

    EObject requestModel = create(metamodelResource, "ApiGatewayRequestModel");
    set(requestModel, "id", "request_model_order");
    set(requestModel, "name", "Order Request");
    set(requestModel, "modelName", "OrderRequest");
    set(requestModel, "contentType", "application/json");
    set(requestModel, "schemaJson", "{\"type\":\"object\"}");

    EObject responseModel = create(metamodelResource, "ApiGatewayResponseModel");
    set(responseModel, "id", "response_model_order_accepted");
    set(responseModel, "name", "Order Accepted");
    set(responseModel, "statusCode", "202");
    set(responseModel, "contentType", "application/json");
    set(responseModel, "schemaJson", "{\"type\":\"object\"}");

    EObject apiRoute = create(metamodelResource, "HttpApiRoute");
    set(apiRoute, "id", "route_create_order");
    set(apiRoute, "name", "Create Order Route");
    set(apiRoute, "logicalId", "CreateOrderRoute");
    set(apiRoute, "path", "/orders");
    set(apiRoute, "routeKey", "POST /orders");
    set(apiRoute, "operationName", "create-order");
    set(apiRoute, "method", enumValue(metamodelResource, "ApiGatewayHttpMethod", "POST"));
    set(
        apiRoute,
        "authorizationType",
        enumValue(metamodelResource, "ApiGatewayAuthorizationType", "AWS_IAM"));
    set(apiRoute, "integration", apiIntegration);
    add(apiRoute, "requestModels", requestModel);
    add(apiRoute, "responseModels", responseModel);

    EObject httpApi = create(metamodelResource, "HttpApi");
    set(httpApi, "id", "api_orders");
    set(httpApi, "name", "Orders API");
    set(httpApi, "logicalId", "OrdersApi");
    set(httpApi, "apiName", "Orders API");
    set(httpApi, "descriptionText", "Representative modeled HTTP API.");
    set(httpApi, "protocolType", "HTTP");
    set(httpApi, "openApiVersion", "3.0.3");
    set(httpApi, "accessLogsEnabled", true);
    set(httpApi, "tracingEnabled", true);
    set(httpApi, "metricsEnabled", true);
    set(httpApi, "corsEnabled", false);
    add(httpApi, "routes", apiRoute);

    EObject queue = create(metamodelResource, "SqsQueue");
    set(queue, "id", "queue_order_work");
    set(queue, "name", "Order Work Queue");
    set(queue, "logicalId", "OrderWorkQueue");
    set(queue, "queueName", "order-work");
    set(queue, "queueType", enumValue(metamodelResource, "SqsQueueType", "STANDARD"));
    set(queue, "visibilityTimeoutSeconds", 45);
    set(queue, "sqsManagedSseEnabled", true);

    EObject topic = create(metamodelResource, "SnsTopic");
    set(topic, "id", "topic_order_events");
    set(topic, "name", "Order Events Topic");
    set(topic, "logicalId", "OrderEventsTopic");
    set(topic, "topicName", "order-events");
    set(topic, "fifoTopic", false);

    EObject bridgeTarget = create(metamodelResource, "EventBridgeTarget");
    set(bridgeTarget, "id", "target_order_handler");
    set(bridgeTarget, "name", "Order Handler Target");
    set(bridgeTarget, "targetId", "OrderHandlerTarget");
    set(
        bridgeTarget,
        "targetKind",
        enumValue(metamodelResource, "EventBridgeTargetKind", "LAMBDA"));
    set(bridgeTarget, "targetResource", lambdaFunction);

    EObject bridgeRule = create(metamodelResource, "EventBridgeRule");
    set(bridgeRule, "id", "rule_order_created");
    set(bridgeRule, "name", "Order Created Rule");
    set(bridgeRule, "logicalId", "OrderCreatedRule");
    set(bridgeRule, "ruleName", "order-created");
    set(bridgeRule, "eventPatternJson", "{\"source\":[\"orders\"]}");
    set(bridgeRule, "state", "ENABLED");
    add(bridgeRule, "targets", bridgeTarget);

    EObject structuredDocument = create(metamodelResource, "StructuredDocument");
    set(structuredDocument, "id", "document_operator_note");
    set(structuredDocument, "name", "Operator Note");
    set(structuredDocument, "format", enumValue(metamodelResource, "StructuredFormat", "MARKDOWN"));
    set(structuredDocument, "content", "# Operator Note\n\nRepresentative operator note.\n");

    EObject stack = create(metamodelResource, "SamStack");
    set(stack, "id", "stack_main");
    set(stack, "name", "Main Stack");
    set(stack, "stackName", "modriss-main");
    set(stack, "templatePath", "template.yaml");
    set(stack, "templateDescription", "Representative generated stack.");
    set(stack, "useSamTransform", true);
    set(stack, "packageIndividually", false);
    set(stack, "validateWithSam", true);
    set(stack, "validateWithCfnLint", true);
    add(stack, "capabilities", enumValue(metamodelResource, "SamCapability", "CAPABILITY_IAM"));
    add(stack, "resources", lambdaRole);
    add(stack, "resources", logGroup);
    add(stack, "resources", lambdaFunction);
    add(stack, "resources", stateMachine);
    add(stack, "resources", httpApi);
    add(stack, "resources", apiIntegration);
    add(stack, "resources", queue);
    add(stack, "resources", topic);
    add(stack, "resources", bridgeRule);
    add(stack, "resources", bucket);

    EObject stage = create(metamodelResource, "AwsStage");
    set(stage, "id", "stage_dev");
    set(stage, "name", "Development");
    set(stage, "stageName", "qa");
    set(stage, "environmentClass", enumValue(metamodelResource, "AwsEnvironmentClass", "DEV"));
    set(stage, "region", "us-east-1");
    set(stage, "requiresManualApproval", false);
    set(stage, "confirmChangeset", true);
    set(stage, "failOnEmptyChangeset", false);
    set(stage, "stackNamePrefix", "modriss");
    add(stage, "deploysStacks", stack);

    EObject model = create(metamodelResource, "AwsPsmModel");
    set(model, "id", "aws_psm_representative");
    set(model, "name", "Representative AWS PSM");
    set(model, "partition", enumValue(metamodelResource, "AwsPartition", "AWS"));
    set(model, "accountStrategy", "single-account");
    set(model, "regionStrategy", "single-region");
    set(model, "defaultRegion", "us-east-1");
    set(model, "namingConvention", "kebab-case");
    set(model, "taggingStrategy", "stage-and-service");
    set(model, "productionMode", false);
    add(model, "stages", stage);
    add(model, "stacks", stack);
    add(model, "allResources", lambdaRole);
    add(model, "allResources", logGroup);
    add(model, "allResources", lambdaFunction);
    add(model, "allResources", stateMachine);
    add(model, "allResources", httpApi);
    add(model, "allResources", apiIntegration);
    add(model, "allResources", queue);
    add(model, "allResources", topic);
    add(model, "allResources", bridgeRule);
    add(model, "allResources", bucket);
    add(model, "documents", structuredDocument);

    Resource modelResource = resourceSet.createResource(URI.createFileURI(modelFile.toString()));
    modelResource.getContents().add(model);
    modelResource.save(null);
  }

  /**
   * Registers all root packages contained in a metamodel resource.
   *
   * @param metamodelResource loaded Ecore metamodel
   */
  private void registerPackages(Resource metamodelResource) {
    for (EObject content : metamodelResource.getContents()) {
      if (content instanceof EPackage ePackage) {
        registerPackage(ePackage);
      }
    }
  }

  /**
   * Registers a package and all nested subpackages in the global EMF registry.
   *
   * @param ePackage package to register recursively
   */
  private void registerPackage(EPackage ePackage) {
    EPackage.Registry.INSTANCE.put(ePackage.getNsURI(), ePackage);
    for (EPackage child : ePackage.getESubpackages()) {
      registerPackage(child);
    }
  }

  /**
   * Creates an EMF object by classifier name from the loaded AWS PSM metamodel.
   *
   * @param metamodelResource loaded metamodel resource
   * @param classifierName EClass name to instantiate
   * @return new EObject instance
   */
  private EObject create(Resource metamodelResource, String classifierName) {
    EClass eClass = (EClass) classifier(metamodelResource, classifierName);
    EFactory factory = eClass.getEPackage().getEFactoryInstance();
    return factory.create(eClass);
  }

  /**
   * Resolves an enum literal instance by enum and literal name.
   *
   * @param metamodelResource loaded metamodel resource
   * @param enumName EEnum name
   * @param literalName literal name
   * @return EMF enum literal instance
   */
  private Object enumValue(Resource metamodelResource, String enumName, String literalName) {
    EEnum eEnum = (EEnum) classifier(metamodelResource, enumName);
    return eEnum.getEEnumLiteral(literalName).getInstance();
  }

  /**
   * Finds a classifier anywhere in a metamodel resource's package tree.
   *
   * @param metamodelResource loaded metamodel resource
   * @param name classifier name
   * @return matching classifier
   */
  private EClassifier classifier(Resource metamodelResource, String name) {
    for (EObject content : metamodelResource.getContents()) {
      EClassifier classifier = classifier((EPackage) content, name);
      if (classifier != null) {
        return classifier;
      }
    }
    throw new IllegalArgumentException("Classifier not found: " + name);
  }

  /**
   * Finds a classifier in a package or any nested subpackage.
   *
   * @param ePackage package to search
   * @param name classifier name
   * @return matching classifier, or {@code null}
   */
  private EClassifier classifier(EPackage ePackage, String name) {
    EClassifier classifier = ePackage.getEClassifier(name);
    if (classifier != null) {
      return classifier;
    }
    for (EPackage child : ePackage.getESubpackages()) {
      EClassifier nested = classifier(child, name);
      if (nested != null) {
        return nested;
      }
    }
    return null;
  }

  /**
   * Sets a named EMF feature on an object.
   *
   * @param object owner object
   * @param featureName feature to set
   * @param value value to assign
   */
  private void set(EObject object, String featureName, Object value) {
    object.eSet(feature(object, featureName), value);
  }

  /**
   * Adds a value to a many-valued EMF feature.
   *
   * @param object owner object
   * @param featureName many-valued feature name
   * @param value value to add
   */
  @SuppressWarnings({"unchecked", "rawtypes"})
  private void add(EObject object, String featureName, Object value) {
    ((List) object.eGet(feature(object, featureName))).add(value);
  }

  /**
   * Resolves a structural feature and fails fast when fixture code no longer matches the metamodel.
   *
   * @param object owner object
   * @param featureName expected feature name
   * @return resolved structural feature
   */
  private EStructuralFeature feature(EObject object, String featureName) {
    EStructuralFeature feature = object.eClass().getEStructuralFeature(featureName);
    if (feature == null) {
      throw new IllegalArgumentException(
          object.eClass().getName() + " has no feature " + featureName);
    }
    return feature;
  }
}
