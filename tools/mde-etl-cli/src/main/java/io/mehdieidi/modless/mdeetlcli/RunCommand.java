package io.mehdieidi.modless.mdeetlcli;

import io.mehdieidi.modless.mde.etl.EpsilonEtlExecutor;
import io.mehdieidi.modless.mde.etl.EtlExecutionException;
import io.mehdieidi.modless.mde.etl.EtlExecutionReport;
import io.mehdieidi.modless.mde.etl.EtlExecutionReportWriter;
import io.mehdieidi.modless.mde.etl.EtlExecutionRequest;
import io.mehdieidi.modless.mde.etl.EtlModelConfiguration;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

/** Runs an arbitrary ETL module against one source and one target EMF model. */
@Command(
    name = "run",
    mixinStandardHelpOptions = true,
    description = "Runs a generic ETL module with one source EMF model and one target EMF model.")
public final class RunCommand implements Callable<Integer> {

  private final EpsilonEtlExecutor executor = new EpsilonEtlExecutor();
  private final ConsoleReportWriter consoleReportWriter = new ConsoleReportWriter();
  private final EtlExecutionReportWriter reportWriter = new EtlExecutionReportWriter();

  @Spec private CommandSpec commandSpec;

  @Option(
      names = {"-m", "--module"},
      required = true,
      description = "ETL module file.")
  private Path module;

  @Option(names = "--source-model", required = true, description = "Input source model file.")
  private Path sourceModel;

  @Option(names = "--target-model", required = true, description = "Output target model file.")
  private Path targetModel;

  @Option(
      names = "--source-metamodel",
      required = true,
      split = ",",
      description = "Source .ecore metamodel file. Repeat or comma-separate.")
  private List<Path> sourceMetamodels;

  @Option(
      names = "--target-metamodel",
      required = true,
      split = ",",
      description = "Target .ecore metamodel file. Repeat or comma-separate.")
  private List<Path> targetMetamodels;

  @Option(names = "--source-aliases", split = ",", description = "Source aliases. Default: IN.")
  private List<String> sourceAliases = List.of("IN");

  @Option(names = "--target-aliases", split = ",", description = "Target aliases. Default: OUT.")
  private List<String> targetAliases = List.of("OUT");

  @Option(names = "--source-name", description = "Source model name. Default: IN.")
  private String sourceName = "IN";

  @Option(names = "--target-name", description = "Target model name. Default: OUT.")
  private String targetName = "OUT";

  @Option(
      names = "--read-existing-target",
      description = "Read an existing target model before execution.")
  private boolean readExistingTarget;

  @Option(names = "--overwrite", description = "Overwrite an existing target model.")
  private boolean overwrite;

  @Option(names = "--verbose", description = "Print captured ETL output and exception types.")
  private boolean verbose;

  @Option(names = "--log-file", description = "Write a JSON execution report to this file.")
  private Path logFile;

  /**
   * Builds and executes the ETL request from command-line options.
   *
   * @return zero on success or two on execution failure
   * @throws Exception if report output cannot be written
   */
  @Override
  public Integer call() throws Exception {
    EtlExecutionRequest request =
        new EtlExecutionRequest(
            module,
            module.toAbsolutePath().getParent(),
            Arrays.asList(
                EtlModelConfiguration.source(
                    sourceName, sourceAliases, sourceModel, sourceMetamodels),
                EtlModelConfiguration.target(
                    targetName, targetAliases, targetModel, targetMetamodels, readExistingTarget)),
            overwrite,
            true);
    return execute(request);
  }

  /**
   * Executes an ETL request and writes both console and optional JSON reports.
   *
   * @param request normalized ETL execution request
   * @return zero on success or two on execution failure
   * @throws Exception if report output cannot be written
   */
  private Integer execute(EtlExecutionRequest request) throws Exception {
    PrintWriter out = commandSpec.commandLine().getOut();
    PrintWriter err = commandSpec.commandLine().getErr();
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
