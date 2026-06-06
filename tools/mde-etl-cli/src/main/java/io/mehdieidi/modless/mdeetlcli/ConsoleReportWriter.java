package io.mehdieidi.modless.mdeetlcli;

import io.mehdieidi.modless.mde.etl.EtlDiagnostic;
import io.mehdieidi.modless.mde.etl.EtlExecutionReport;
import java.io.PrintWriter;

/**
 * Renders ETL execution reports for interactive console use.
 */
final class ConsoleReportWriter {

    /**
     * Writes a successful execution summary.
     *
     * @param out     destination writer
     * @param report  successful execution report
     * @param verbose whether to include timing and captured output
     */
    void writeSuccess(PrintWriter out, EtlExecutionReport report, boolean verbose) {
        out.printf("ETL execution succeeded in %d ms.%n", report.duration().toMillis());
        out.printf("Module: %s%n", report.moduleFile());
        if (verbose) {
            writeTiming(out, report);
            writeCaptured(out, report);
        }
    }

    /**
     * Writes a failed execution summary and its diagnostics.
     *
     * @param err     destination writer
     * @param report  failed execution report
     * @param verbose whether to include exception types, timing, and captured output
     */
    void writeFailure(PrintWriter err, EtlExecutionReport report, boolean verbose) {
        err.printf("ETL execution failed in %d ms.%n", report.duration().toMillis());
        err.printf("Module: %s%n", report.moduleFile());
        for (EtlDiagnostic diagnostic : report.diagnostics()) {
            err.printf(
                    "[%s/%s] %s%n",
                    diagnostic.severity(),
                    diagnostic.phase(),
                    diagnostic.reason());
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
            writeTiming(err, report);
            writeCaptured(err, report);
        }
    }

    /**
     * Writes per-phase ETL timing.
     *
     * @param writer destination writer
     * @param report execution report
     */
    private void writeTiming(PrintWriter writer, EtlExecutionReport report) {
        var timing = report.phaseTiming();
        writer.printf(
                "ETL timing: validation=%dms, prepare=%dms, parse=%dms, sourceLoad=%dms, targetLoad=%dms, execute=%dms, store=%dms, dispose=%dms, total=%dms%n",
                timing.validationMs(), timing.prepareOutputsMs(), timing.parseMs(),
                timing.sourceModelLoadMs(), timing.targetModelLoadMs(), timing.etlExecuteMs(),
                timing.modelStoreMs(), timing.disposeMs(), timing.totalMs());
    }

    /**
     * Writes non-empty captured ETL output streams.
     *
     * @param writer destination writer
     * @param report execution report
     */
    private void writeCaptured(PrintWriter writer, EtlExecutionReport report) {
        if (!report.standardOutput().isBlank()) {
            writer.println("---- ETL stdout ----");
            writer.println(report.standardOutput());
        }
        if (!report.warningOutput().isBlank()) {
            writer.println("---- ETL warnings ----");
            writer.println(report.warningOutput());
        }
        if (!report.errorOutput().isBlank()) {
            writer.println("---- ETL stderr ----");
            writer.println(report.errorOutput());
        }
    }
}
