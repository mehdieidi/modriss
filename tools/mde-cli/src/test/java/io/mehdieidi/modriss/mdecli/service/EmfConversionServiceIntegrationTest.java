package io.mehdieidi.modriss.mdecli.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.mehdieidi.modriss.mdecli.diagnostics.ConversionReport;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.xmi.impl.EcoreResourceFactoryImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;

/** Integration tests for standalone and modular Emfatic conversion. */
@ResourceLock("emf-registry")
class EmfConversionServiceIntegrationTest {

  /** Detects unstable positional references in serialized modular Ecore output. */
  private static final Pattern POSITIONAL_FRAGMENT_PATTERN =
      Pattern.compile("(eType|eSuperTypes|eOpposite)=\"#/\\d");

  private final EmfConversionService conversionService = new EmfConversionService();

  /**
   * Verifies standalone Emfatic conversion.
   *
   * @throws Exception if fixture creation or conversion fails
   */
  @Test
  void convertsSingleEmfaticFileIntoEcore() throws Exception {
    Path tempDirectory = Files.createTempDirectory("mde-cli-test-");
    Path input = tempDirectory.resolve("simple.emf");
    Files.writeString(
        input,
        """
        @namespace(uri="https://example.org/test/1.0", prefix="test")
        package test;

        class Person {
          attr String[1] name;
        }
        """);
    Path output = tempDirectory.resolve("simple.ecore");

    ConversionReport report =
        conversionService.convert(new ConversionRequest(input, output, null, true, false));

    assertEquals(ConversionReport.Status.SUCCESS, report.getStatus());
    assertTrue(Files.exists(output));
  }

  /**
   * Verifies repository CIM modules combine into a normalized Ecore resource.
   *
   * @throws Exception if conversion or output inspection fails
   */
  @Test
  void convertsSampleCimDirectoryIntoCombinedEcore() throws Exception {
    Path projectRoot = findProjectRoot();
    Path inputDirectory = projectRoot.resolve("mde/metamodels/cim");
    Path output = Files.createTempDirectory("mde-cli-cim-").resolve("cim-combined.ecore");

    ConversionReport report =
        conversionService.convert(
            new ConversionRequest(
                inputDirectory, output, inputDirectory.resolve("cim-root.emf"), true, false));

    assertEquals(ConversionReport.Status.SUCCESS, report.getStatus());
    assertTrue(Files.exists(output));

    ResourceSet resourceSet = new ResourceSetImpl();
    resourceSet
        .getResourceFactoryRegistry()
        .getExtensionToFactoryMap()
        .put("ecore", new EcoreResourceFactoryImpl());
    Resource resource = resourceSet.getResource(URI.createFileURI(output.toString()), true);
    assertTrue(resource.getContents().stream().anyMatch(EPackage.class::isInstance));

    String serialized = Files.readString(output);
    assertFalse(
        serialized.contains("name=\"description\"/>"),
        "Combined output should not leave string attributes without explicit eType.");
    assertFalse(
        serialized.contains("<eSubpackages name=\"kernel\""),
        "Combined output should keep modular packages as top-level packages.");
  }

  /**
   * Verifies modular single-file output uses final resource URIs and stable fragments.
   *
   * @throws Exception if conversion or output inspection fails
   */
  @Test
  void convertsModularSingleFileWithoutLeakingTempUris() throws Exception {
    Path projectRoot = findProjectRoot();
    Path input = projectRoot.resolve("mde/metamodels/cim/cim-root.emf");
    Path outputDirectory = Files.createTempDirectory("mde-cli-single-cim-");
    Path output = outputDirectory.resolve("cim-root.ecore");

    ConversionReport report =
        conversionService.convert(new ConversionRequest(input, output, null, true, false));

    assertEquals(ConversionReport.Status.SUCCESS, report.getStatus());
    assertTrue(Files.exists(output));
    assertTrue(Files.exists(outputDirectory.resolve("kernel.ecore")));

    String serialized = Files.readString(output);
    assertFalse(
        serialized.contains("mde-cli-single-ecore-"),
        "Single-file modular output should not point to temp workspaces.");
    assertFalse(
        POSITIONAL_FRAGMENT_PATTERN.matcher(serialized).find(),
        "Single-file modular output should not use positional local XMI fragments.");
  }

  /**
   * Locates the repository root from the active test working directory.
   *
   * @return repository root
   */
  private Path findProjectRoot() {
    try (Stream<Path> parents =
        Stream.iterate(
            Path.of("").toAbsolutePath().normalize(), path -> path != null, Path::getParent)) {
      return parents
          .filter(path -> Files.exists(path.resolve("mde/metamodels/cim/cim-root.emf")))
          .findFirst()
          .orElseThrow(
              () ->
                  new IllegalStateException(
                      "Could not locate the repository root from "
                          + Path.of("").toAbsolutePath().normalize()));
    }
  }
}
