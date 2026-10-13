package io.mehdieidi.modriss.modeltransformercli;

import picocli.CommandLine.Command;

/** Root Picocli command for ETL transformations. */
@Command(
    name = "model-transformer-cli",
    mixinStandardHelpOptions = true,
    version = "0.0.1-SNAPSHOT",
    description = "Executes Eclipse Epsilon ETL transformations.",
    subcommands = {RunCommand.class, CimToPimCommand.class, PimToAwsPsmCommand.class})
public final class ModelTransformerCommand implements Runnable {

  /** Rejects invocation without a transformation subcommand. */
  @Override
  public void run() {
    throw new picocli.CommandLine.ParameterException(
        new picocli.CommandLine(this), "Choose a subcommand: run, cim-to-pim, or pim-to-psm.");
  }
}
