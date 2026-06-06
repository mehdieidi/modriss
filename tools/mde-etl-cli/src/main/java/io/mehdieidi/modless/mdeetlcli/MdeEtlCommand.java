package io.mehdieidi.modless.mdeetlcli;

import picocli.CommandLine.Command;

/**
 * Root Picocli command for ETL transformations.
 */
@Command(
        name = "mde-etl-cli",
        mixinStandardHelpOptions = true,
        version = "0.0.1-SNAPSHOT",
        description = "Executes Eclipse Epsilon ETL transformations.",
        subcommands = {
                RunCommand.class,
                CimToPimCommand.class,
                PimToAwsPsmCommand.class
        })
public final class MdeEtlCommand implements Runnable {

    /**
     * Rejects invocation without a transformation subcommand.
     */
    @Override
    public void run() {
        throw new picocli.CommandLine.ParameterException(
                new picocli.CommandLine(this),
                "Choose a subcommand: run, cim-to-pim, or pim-to-awspsm.");
    }
}
