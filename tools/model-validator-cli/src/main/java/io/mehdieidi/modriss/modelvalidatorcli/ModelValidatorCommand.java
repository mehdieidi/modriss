package io.mehdieidi.modriss.modelvalidatorcli;

import picocli.CommandLine.Command;

/** Root Picocli command for EVL validation. */
@Command(
    name = "model-validator-cli",
    mixinStandardHelpOptions = true,
    version = "0.0.1-SNAPSHOT",
    description = "Executes Eclipse Epsilon EVL validations.",
    subcommands = {RunCommand.class, CimCommand.class, PimCommand.class, PsmCommand.class})
public final class ModelValidatorCommand implements Runnable {

  /** Rejects invocation without a validation subcommand. */
  @Override
  public void run() {
    throw new picocli.CommandLine.ParameterException(
        new picocli.CommandLine(this), "Choose a subcommand: run, cim, pim, or psm.");
  }
}
