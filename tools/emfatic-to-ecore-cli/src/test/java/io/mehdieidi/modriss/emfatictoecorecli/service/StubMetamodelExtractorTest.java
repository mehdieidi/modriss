package io.mehdieidi.modriss.emfatictoecorecli.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Tests extraction of bootstrap metadata from Emfatic declarations. */
class StubMetamodelExtractorTest {

  @TempDir Path tempDirectory;

  /**
   * Verifies namespace assignments accept compact syntax and surrounding whitespace.
   *
   * @param namespace namespace declaration to extract
   * @throws Exception if fixture creation fails
   */
  @ParameterizedTest
  @ValueSource(
      strings = {
        "@namespace(uri=\"https://example.org/test/1.0\", prefix=\"test\")",
        "@namespace(uri = \"https://example.org/test/1.0\", prefix = \"test\")",
        "@namespace(\nuri\t=\t\"https://example.org/test/1.0\",\nprefix\n=\n\"test\"\n)"
      })
  void extractsNamespaceWithWhitespaceAroundAssignments(String namespace) throws Exception {
    Path input = tempDirectory.resolve("test.emf");
    Files.writeString(input, namespace + "\npackage test;\nclass Person {}\n");

    StubMetamodelDefinition definition = new StubMetamodelExtractor().extract(input);

    assertEquals("test", definition.packageName());
    assertEquals("https://example.org/test/1.0", definition.namespaceUri());
    assertEquals("test", definition.namespacePrefix());
    assertEquals("Person", definition.classifiers().get(0).name());
  }
}
