package io.mehdieidi.modriss.mdem2tcli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;
import picocli.CommandLine;

/** Integration tests for the repository-backed model-to-text CLI profile. */
@ResourceLock("epsilon-runtime")
final class MdeM2tCliApplicationTest {

  /** Repository root containing the generation profile and sample models. */
  private static final Path REPOSITORY_ROOT = findRepositoryRoot();

  /** Per-test output directory. */
  @TempDir Path tempDir;

  /**
   * Locates the repository root from the active test working directory.
   *
   * @return repository root
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

  /**
   * Verifies that the AWS PSM profile generates expected files and a JSON report.
   *
   * @throws Exception if the command or file assertions cannot be completed
   */
  @Test
  void awsPsmToArtifactsCommandGeneratesRepositorySampleAndWritesReport() throws Exception {
    Path outputDirectory = tempDir.resolve("generated-artifacts");
    Path reportFile = tempDir.resolve("generation-report.json");
    StringWriter out = new StringWriter();
    StringWriter err = new StringWriter();

    int exitCode =
        new CommandLine(new MdeM2tCommand())
            .setOut(new PrintWriter(out))
            .setErr(new PrintWriter(err))
            .execute(
                "aws-psm-to-artifacts",
                "--repo-root",
                REPOSITORY_ROOT.toString(),
                "--source-model",
                REPOSITORY_ROOT.resolve("mde/samples/psm.xmi").toString(),
                "--output-dir",
                outputDirectory.toString(),
                "--fail-if-output-not-empty",
                "--log-file",
                reportFile.toString());

    assertEquals(0, exitCode);
    assertTrue(out.toString().contains("AWS PSM artifact generation succeeded"));
    assertTrue(err.toString().isBlank());
    assertTrue(Files.isRegularFile(outputDirectory.resolve("generated/trace/artifact-trace.json")));
    assertTrue(
        Files.isRegularFile(outputDirectory.resolve("generated/reports/generation-report.md")));
    assertTrue(Files.readString(reportFile).contains("\"status\": \"SUCCEEDED\""));
  }
}
