package io.mehdieidi.modless.mdeevlcli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

final class MdeEvlCliApplicationTest {

    private static final Path REPOSITORY_ROOT = findRepositoryRoot();

    @TempDir
    Path tempDir;

    private static Path findRepositoryRoot() {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        while (current != null) {
            if (Files.isDirectory(current.resolve("mde/metamodels"))
                    && Files.isDirectory(current.resolve("mde/validation/psm"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Could not locate repository root from user.dir.");
    }

    @Test
    void psmCommandReturnsViolationExitCodeAndWritesReport() throws Exception {
        Path reportFile = tempDir.resolve("psm-validation-report.json");
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();

        int exitCode = new CommandLine(new MdeEvlCommand())
                .setOut(new PrintWriter(out))
                .setErr(new PrintWriter(err))
                .execute(
                        "psm",
                        "--repo-root", REPOSITORY_ROOT.toString(),
                        "--model", REPOSITORY_ROOT.resolve("mde/samples/psm.xmi").toString(),
                        "--log-file", reportFile.toString(),
                        "--fail-on-mandatory-violations");

        assertEquals(3, exitCode);
        assertTrue(out.toString().contains("EVL validation succeeded"));
        assertTrue(err.toString().isBlank());
        String reportJson = Files.readString(reportFile);
        assertTrue(reportJson.contains("\"status\": \"SUCCEEDED\""));
        assertTrue(reportJson.contains("\"mandatoryViolationCount\": 57"));
    }
}
