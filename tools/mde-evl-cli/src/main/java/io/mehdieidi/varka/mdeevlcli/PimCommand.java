package io.mehdieidi.varka.mdeevlcli;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

/** Runs the repository-standard PIM semantic validation profile. */
@Command(
    name = "pim",
    mixinStandardHelpOptions = true,
    description = "Runs the repository PIM semantic validation profile.")
public final class PimCommand implements Callable<Integer> {

  private final ValidationRunner runner = new ValidationRunner();

  @Spec private CommandSpec commandSpec;

  @Option(names = "--repo-root", required = true, description = "Repository root.")
  private Path repositoryRoot;

  @Option(names = "--model", required = true, description = "Input PIM XMI model file.")
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
   * Executes PIM validation using repository-standard modules and metamodels.
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
            "pim",
            "pim-semantic-validation.evl",
            model,
            "PIM",
            List.of("KERNEL"),
            !noStructuralValidation),
        failOnMandatoryViolations,
        failOnOptionalViolations,
        verbose,
        logFile);
  }
}
