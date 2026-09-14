package io.mehdieidi.modriss.mdeevlcli;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

/** Runs the repository-standard CIM semantic validation profile. */
@Command(
    name = "cim",
    mixinStandardHelpOptions = true,
    description = "Runs the repository CIM semantic validation profile.")
public final class CimCommand implements Callable<Integer> {

  private final ValidationRunner runner = new ValidationRunner();

  @Spec private CommandSpec commandSpec;

  @Option(names = "--repo-root", required = true, description = "Repository root.")
  private Path repositoryRoot;

  @Option(names = "--model", required = true, description = "Input CIM XMI model file.")
  private Path model;

  @Option(
      names = "--no-structural-validation",
      description = "Disable EMF structural validation while loading the model.")
  private boolean noStructuralValidation;

  @Option(
      names = "--fail-on-mandatory-violations",
      description = "Return exit code 3 when mandatory EVL constraints are violated.")
  private boolean failOnMandatoryViolations;

  @Option(
      names = "--fail-on-optional-violations",
      description = "Return exit code 3 when any EVL constraint or critique is violated.")
  private boolean failOnOptionalViolations;

  @Option(
      names = "--verbose",
      description = "Print captured EVL output and detailed element references.")
  private boolean verbose;

  @Option(names = "--log-file", description = "Write a JSON validation report to this file.")
  private Path logFile;

  /**
   * Executes CIM validation using repository-standard modules and metamodels.
   *
   * @return CLI validation exit code
   * @throws Exception if validation or report output fails
   */
  @Override
  public Integer call() throws Exception {
    return runner.execute(
        commandSpec,
        ProfileCommandSupport.request(
            repositoryRoot,
            "cim",
            "cim-semantic-validation.evl",
            model,
            "CIM",
            List.of("KERNEL"),
            !noStructuralValidation),
        failOnMandatoryViolations,
        failOnOptionalViolations,
        verbose,
        logFile);
  }
}
