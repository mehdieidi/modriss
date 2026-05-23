package io.mehdieidi.modless.mdeevlcli;

import io.mehdieidi.modless.mde.validation.EpsilonEvlValidator;
import io.mehdieidi.modless.mde.validation.EvlValidationException;
import io.mehdieidi.modless.mde.validation.EvlValidationReport;
import io.mehdieidi.modless.mde.validation.EvlValidationReportWriter;
import io.mehdieidi.modless.mde.validation.EvlValidationRequest;
import java.io.PrintWriter;
import java.nio.file.Path;
import picocli.CommandLine.Model.CommandSpec;

final class ValidationRunner {

    private final EpsilonEvlValidator validator = new EpsilonEvlValidator();
    private final ConsoleReportWriter consoleReportWriter = new ConsoleReportWriter();
    private final EvlValidationReportWriter reportWriter = new EvlValidationReportWriter();

    int execute(
            CommandSpec commandSpec,
            EvlValidationRequest request,
            boolean failOnMandatoryViolations,
            boolean failOnOptionalViolations,
            boolean verbose,
            Path logFile) throws Exception {
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
