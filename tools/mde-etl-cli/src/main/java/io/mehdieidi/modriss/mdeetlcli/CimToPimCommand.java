package io.mehdieidi.modriss.mdeetlcli;

import io.mehdieidi.modriss.mde.etl.CimToPimDefaults;
import io.mehdieidi.modriss.mde.etl.EpsilonEtlExecutor;
import io.mehdieidi.modriss.mde.etl.EtlExecutionException;
import io.mehdieidi.modriss.mde.etl.EtlExecutionReport;
import io.mehdieidi.modriss.mde.etl.EtlExecutionReportWriter;
import io.mehdieidi.modriss.mde.etl.EtlExecutionRequest;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

/** Runs the repository's standard CIM-to-PIM transformation profile. */
@Command(
    name = "cim-to-pim",
    mixinStandardHelpOptions = true,
    description = "Runs the repository CIM-to-PIM ETL transformation profile.")
public final class CimToPimCommand implements Callable<Integer> {

  private final EpsilonEtlExecutor executor = new EpsilonEtlExecutor();
  private final ConsoleReportWriter consoleReportWriter = new ConsoleReportWriter();
  private final EtlExecutionReportWriter reportWriter = new EtlExecutionReportWriter();

  @Spec private CommandSpec commandSpec;

  @Option(
      names = "--repo-root",
      required = true,
      description = "Repository root containing mde/metamodels and mde/transformations.")
  private Path repositoryRoot;

  @Option(names = "--source-model", required = true, description = "Input CIM XMI model file.")
  private Path sourceModel;

  @Option(names = "--target-model", required = true, description = "Output PIM XMI model file.")
  private Path targetModel;

  @Option(names = "--overwrite", description = "Overwrite an existing target model.")
  private boolean overwrite;

  @Option(names = "--verbose", description = "Print captured ETL output and exception types.")
  private boolean verbose;

  @Option(names = "--log-file", description = "Write a JSON execution report to this file.")
  private Path logFile;

  /**
   * Executes the configured CIM-to-PIM transformation and writes its reports.
   *
   * @return zero on success or two on execution failure
   * @throws Exception if report output cannot be written
   */
  @Override
  public Integer call() throws Exception {
    PrintWriter out = commandSpec.commandLine().getOut();
    PrintWriter err = commandSpec.commandLine().getErr();
    EtlExecutionRequest request =
        CimToPimDefaults.request(
            repositoryRoot.toAbsolutePath().normalize(),
            sourceModel.toAbsolutePath().normalize(),
            targetModel.toAbsolutePath().normalize(),
            overwrite,
            true);
    try {
      EtlExecutionReport report = executor.execute(request);
      consoleReportWriter.writeSuccess(out, report, verbose);
      reportWriter.write(logFile, report);
      return 0;
    } catch (EtlExecutionException ex) {
      consoleReportWriter.writeFailure(err, ex.getReport(), verbose);
      reportWriter.write(logFile, ex.getReport());
      return 2;
    }
  }
}
