package io.mehdieidi.varka.mdem2tcli;

import io.mehdieidi.varka.mde.generation.AwsPsmToArtifactsDefaults;
import io.mehdieidi.varka.mde.generation.EgxGenerationException;
import io.mehdieidi.varka.mde.generation.EgxGenerationReport;
import io.mehdieidi.varka.mde.generation.EgxGenerationReportWriter;
import io.mehdieidi.varka.mde.generation.EgxGenerationRequest;
import io.mehdieidi.varka.mde.generation.EpsilonEgxGenerator;
import io.mehdieidi.varka.mde.generation.GenerationDiagnostic;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

/** Generates deployable artifacts from an AWS PSM through the repository EGX/EGL profile. */
@Command(
    name = "aws-psm-to-artifacts",
    mixinStandardHelpOptions = true,
    description = "Runs the AWS PSM-to-artifacts EGX/EGL code generation profile.")
public final class AwsPsmToArtifactsCommand implements Callable<Integer> {

  private final EpsilonEgxGenerator generator = new EpsilonEgxGenerator();
  private final EgxGenerationReportWriter reportWriter = new EgxGenerationReportWriter();

  @Spec private CommandSpec commandSpec;

  @Option(
      names = "--repo-root",
      required = true,
      description = "Repository root containing mde/metamodels and mde/generation.")
  private Path repositoryRoot;

  @Option(names = "--source-model", required = true, description = "Input AWS PSM XMI model file.")
  private Path sourceModel;

  @Option(
      names = "--output-dir",
      required = true,
      description = "Directory where generated project artifacts are written.")
  private Path outputDirectory;

  @Option(
      names = "--fail-if-output-not-empty",
      description = "Fail before generation when the output directory already contains files.")
  private boolean failIfOutputDirectoryIsNotEmpty;

  @Option(names = "--verbose", description = "Print captured EGX/EGL output and exception types.")
  private boolean verbose;

  @Option(names = "--log-file", description = "Write a JSON generation report to this file.")
  private Path logFile;

  /**
   * Executes artifact generation and writes console and optional JSON reports.
   *
   * @return zero on success or two on generation failure
   * @throws Exception if report output cannot be written
   */
  @Override
  public Integer call() throws Exception {
    PrintWriter out = commandSpec.commandLine().getOut();
    PrintWriter err = commandSpec.commandLine().getErr();
    EgxGenerationRequest request =
        AwsPsmToArtifactsDefaults.request(
            repositoryRoot.toAbsolutePath().normalize(),
            sourceModel.toAbsolutePath().normalize(),
            outputDirectory.toAbsolutePath().normalize(),
            failIfOutputDirectoryIsNotEmpty,
            true);
    try {
      EgxGenerationReport report = generator.generate(request);
      writeSuccess(out, report);
      reportWriter.write(logFile, report);
      return 0;
    } catch (EgxGenerationException ex) {
      writeFailure(err, ex.getReport());
      reportWriter.write(logFile, ex.getReport());
      return 2;
    }
  }

  /**
   * Writes a successful generation summary.
   *
   * @param out destination writer
   * @param report successful generation report
   */
  private void writeSuccess(PrintWriter out, EgxGenerationReport report) {
    out.printf("AWS PSM artifact generation succeeded in %d ms.%n", report.duration().toMillis());
    out.printf("Module: %s%n", report.moduleFile());
    out.printf("Output: %s%n", report.outputDirectory());
    out.printf("Generated files: %d%n", report.generatedFiles().size());
    if (verbose) {
      writeCaptured(out, report);
    }
  }

  /**
   * Writes a failed generation summary and its diagnostics.
   *
   * @param err destination writer
   * @param report failed generation report
   */
  private void writeFailure(PrintWriter err, EgxGenerationReport report) {
    err.printf("AWS PSM artifact generation failed in %d ms.%n", report.duration().toMillis());
    err.printf("Module: %s%n", report.moduleFile());
    err.printf("Output: %s%n", report.outputDirectory());
    for (GenerationDiagnostic diagnostic : report.diagnostics()) {
      err.printf("[%s/%s] %s%n", diagnostic.severity(), diagnostic.phase(), diagnostic.reason());
      if (diagnostic.file() != null) {
        err.printf("  where: %s", diagnostic.file());
        if (diagnostic.line() > 0) {
          err.printf(":%d", diagnostic.line());
          if (diagnostic.column() > 0) {
            err.printf(":%d", diagnostic.column());
          }
        }
        err.println();
      }
      if (!diagnostic.whatWentWrong().isBlank()) {
        err.printf("  what went wrong: %s%n", diagnostic.whatWentWrong());
      }
      if (!diagnostic.howToFix().isBlank()) {
        err.printf("  how to fix: %s%n", diagnostic.howToFix());
      }
      if (verbose && !diagnostic.exceptionType().isBlank()) {
        err.printf("  exception: %s%n", diagnostic.exceptionType());
      }
    }
    if (verbose) {
      writeCaptured(err, report);
    }
  }

  /**
   * Writes non-empty captured EGX and EGL output streams.
   *
   * @param writer destination writer
   * @param report generation report
   */
  private void writeCaptured(PrintWriter writer, EgxGenerationReport report) {
    if (!report.standardOutput().isBlank()) {
      writer.println("---- EGX stdout ----");
      writer.println(report.standardOutput());
    }
    if (!report.warningOutput().isBlank()) {
      writer.println("---- EGX warnings ----");
      writer.println(report.warningOutput());
    }
    if (!report.errorOutput().isBlank()) {
      writer.println("---- EGX stderr ----");
      writer.println(report.errorOutput());
    }
  }
}
