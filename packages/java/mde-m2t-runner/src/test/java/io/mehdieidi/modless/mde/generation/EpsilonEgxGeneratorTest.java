package io.mehdieidi.modless.mde.generation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Regression tests for EGX-driven artifact generation from AWS PSM models. */
final class EpsilonEgxGeneratorTest {

  /** Repository root discovered from the current test working directory. */
  private static final Path REPOSITORY_ROOT = findRepositoryRoot();

  /** Temporary directory for generated source models and artifact projects. */
  @TempDir Path tempDir;

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
   * Generates a project from a synthetic AWS PSM fixture and verifies the generated tree, reports,
   * traces, scripts, and CI configuration.
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
    assertRequiredProjectTreeWasGenerated(outputDirectory);
    assertGoOnlyArtifactsWereGenerated(outputDirectory);
    assertTraceFilesAreFinalized(outputDirectory);
    assertGenerationReportContainsExportGateSummary(outputDirectory);
    assertGeneratedScriptsAndCiImplementProductionGates(outputDirectory);
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
    assertGeneratedSamTemplatesDoNotRepeatParameterKeys(outputDirectory);
  }

  /** Ensures merge-enabled EGL templates preserve developer-owned protected-region content. */
  @Test
  void preservesProtectedRegionsWhenRegeneratingExistingArtifacts() throws Exception {
    Path sourceModel = tempDir.resolve("representative-aws-psm.xmi");
    Path outputDirectory = tempDir.resolve("regenerated-project");
    createRepresentativeAwsPsmModel(sourceModel);

    generateOrFail(
        AwsPsmToArtifactsDefaults.request(
            REPOSITORY_ROOT, sourceModel, outputDirectory, true, true));
    Path handler = outputDirectory.resolve("src/functions/order-handler/handler.go");
    String customLogic = "\treturn GeneratedResult{Status: \"developer-owned\"}, nil";
    Files.writeString(
        handler,
        Files.readString(handler)
            .replace(
                "\treturn GeneratedResult{}, shared.NewGeneratedHandlerError(\"NOT_IMPLEMENTED\","
                    + " \"Business logic has not been implemented yet.\")",
                customLogic));

    generateOrFail(
        AwsPsmToArtifactsDefaults.request(
            REPOSITORY_ROOT, sourceModel, outputDirectory, false, true));

    assertTrue(Files.readString(handler).contains(customLogic));
  }

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
            "tests/unit/order-handler.test.go",
            "tests/integration/order-handler.integration.test.go",
            "tests/contract/generated-contracts.test.go",
            "tests/events/generated-events.test.go",
            "tests/e2e/security.generated.test.go",
            "tests/e2e/generated-flows.test.go",
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
    assertTrue(buildScript.contains("go mod download"));
    assertTrue(buildScript.contains("go test ./..."));
    assertTrue(buildScript.contains("go build"));

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
    set(value, "literal", "modless-generated-artifact-bucket");
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

    EObject stack = create(metamodelResource, "SamStack");
    set(stack, "id", "stack_main");
    set(stack, "name", "Main Stack");
    set(stack, "stackName", "modless-main");
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
    add(stack, "resources", bucket);

    EObject stage = create(metamodelResource, "AwsStage");
    set(stage, "id", "stage_dev");
    set(stage, "name", "Development");
    set(stage, "stageName", "dev");
    set(stage, "environmentClass", enumValue(metamodelResource, "AwsEnvironmentClass", "DEV"));
    set(stage, "region", "us-east-1");
    set(stage, "requiresManualApproval", false);
    set(stage, "confirmChangeset", true);
    set(stage, "failOnEmptyChangeset", false);
    set(stage, "stackNamePrefix", "modless");
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
    add(model, "allResources", bucket);

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
