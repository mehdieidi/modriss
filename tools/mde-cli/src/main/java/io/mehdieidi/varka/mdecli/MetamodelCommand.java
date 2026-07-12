package io.mehdieidi.varka.mdecli;

import io.mehdieidi.varka.mdecli.diagnostics.ConsoleReportWriter;
import io.mehdieidi.varka.mdecli.diagnostics.ConversionReport;
import io.mehdieidi.varka.mdecli.diagnostics.ReportFileWriter;
import io.mehdieidi.varka.mdecli.service.ConversionException;
import io.mehdieidi.varka.mdecli.service.ConversionRequest;
import io.mehdieidi.varka.mdecli.service.EmfConversionService;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

/** Converts standalone or modular Emfatic metamodels into Ecore resources. */
@Command(
    name = "mde-cli",
    mixinStandardHelpOptions = true,
    version = "0.0.1-SNAPSHOT",
    description = "Converts Emfatic (.emf/.emfatic) metamodels into .ecore files.")
public final class MetamodelCommand implements Callable<Integer> {

  private final EmfConversionService conversionService = new EmfConversionService();
  private final ConsoleReportWriter consoleReportWriter = new ConsoleReportWriter();
  private final ReportFileWriter reportFileWriter = new ReportFileWriter();

  @Spec private CommandSpec commandSpec;

  @Parameters(
      index = "0",
      paramLabel = "INPUT",
      description = "Input .emf/.emfatic file or directory.")
  private Path input;

  @Option(
      names = {"-o", "--output"},
      paramLabel = "OUTPUT",
      description = "Target .ecore file path.")
  private Path output;

  @Option(
      names = "--root",
      paramLabel = "ROOT",
      description = "Root Emfatic file for directory conversion.")
  private Path rootFile;

  @Option(names = "--overwrite", description = "Overwrite an existing output file.")
  private boolean overwrite;

  @Option(names = "--verbose", description = "Print step-by-step execution details.")
  private boolean verbose;

  @Option(
      names = "--log-file",
      paramLabel = "FILE",
      description = "Write a detailed execution report to this file.")
  private Path logFile;

  /**
   * Executes conversion and maps user-fixable and unexpected failures to CLI exit codes.
   *
   * @return zero on success, two for user-fixable failures, or one for unexpected failures
   */
  @Override
  public Integer call() {
    PrintWriter out = commandSpec.commandLine().getOut();
    PrintWriter err = commandSpec.commandLine().getErr();

    ConversionRequest request = new ConversionRequest(input, output, rootFile, overwrite, verbose);
    try {
      ConversionReport report = conversionService.convert(request);
      consoleReportWriter.writeSuccess(out, report, verbose);
      reportFileWriter.write(logFile, report);
      return 0;
    } catch (ConversionException ex) {
      consoleReportWriter.writeFailure(err, ex.getReport(), verbose);
      reportFileWriter.write(logFile, ex.getReport());
      return ex.isUserFixable() ? 2 : 1;
    } catch (RuntimeException ex) {
      ConversionReport report = ConversionReport.failedUnexpectedly(request, ex);
      consoleReportWriter.writeFailure(err, report, verbose);
      reportFileWriter.write(logFile, report);
      return 1;
    }
  }
}
