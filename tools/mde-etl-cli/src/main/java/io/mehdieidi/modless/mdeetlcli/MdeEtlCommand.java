package io.mehdieidi.modless.mdeetlcli;

import picocli.CommandLine.Command;

@Command(
        name = "mde-etl-cli",
        mixinStandardHelpOptions = true,
        version = "0.0.1-SNAPSHOT",
        description = "Executes Eclipse Epsilon ETL transformations.",
        subcommands = {
                RunCommand.class,
                CimToPimCommand.class
        })
public final class MdeEtlCommand implements Runnable {

    @Override
    public void run() {
        throw new picocli.CommandLine.ParameterException(
                new picocli.CommandLine(this), "Choose a subcommand: run or cim-to-pim.");
    }
}
