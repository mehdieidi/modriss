package io.mehdieidi.modless.mdeevlcli;

import io.mehdieidi.modless.mde.validation.EpsilonEvlValidator;
import io.mehdieidi.modless.mde.validation.EvlValidationException;
import io.mehdieidi.modless.mde.validation.EvlValidationReport;
import io.mehdieidi.modless.mde.validation.EvlValidationReportWriter;
import io.mehdieidi.modless.mde.validation.EvlValidationRequest;
import java.io.PrintWriter;
import java.nio.file.Path;
import picocli.CommandLine.Model.CommandSpec;

/** Executes EVL requests and maps validation outcomes to CLI exit codes. */
final class ValidationRunner {

  private final EpsilonEvlValidator validator = new EpsilonEvlValidator();
  private final ConsoleReportWriter consoleReportWriter = new ConsoleReportWriter();
  private final EvlValidationReportWriter reportWriter = new EvlValidationReportWriter();

  /**
   * Runs validation, writes reports, and applies configured violation exit policies.
   *
   * @param commandSpec active Picocli command
   * @param request validation request
   * @param failOnMandatoryViolations whether mandatory violations return exit code three
   * @param failOnOptionalViolations whether any violation returns exit code three
   * @param verbose whether to print detailed console output
   * @param logFile optional JSON report path
   * @return zero for accepted validation, two for execution failure, or three for rejected
   *     violations
   * @throws Exception if report output cannot be written
   */
  int execute(
      CommandSpec commandSpec,
      EvlValidationRequest request,
      boolean failOnMandatoryViolations,
      boolean failOnOptionalViolations,
      boolean verbose,
      Path logFile)
      throws Exception {
    PrintWriter out = commandSpec.commandLine().getOut();
    PrintWriter err = commandSpec.commandLine().getErr();
    try {
      EvlValidationReport report = validator.validate(request);
      consoleReportWriter.writeSuccess(out, report, verbose);
      reportWriter.write(logFile, report);
      if (failOnOptionalViolations && !report.violations().isEmpty()) {
        return 3;
      }
      if (failOnMandatoryViolations && report.hasMandatoryViolations()) {
        return 3;
      }
      return 0;
    } catch (EvlValidationException ex) {
      consoleReportWriter.writeFailure(err, ex.getReport(), verbose);
      reportWriter.write(logFile, ex.getReport());
      return 2;
    }
  }
}
