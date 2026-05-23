package io.mehdieidi.modless.mdeevlcli;

import picocli.CommandLine.Command;

@Command(
        name = "mde-evl-cli",
        mixinStandardHelpOptions = true,
        version = "0.0.1-SNAPSHOT",
        description = "Executes Eclipse Epsilon EVL validations.",
        subcommands = {
                RunCommand.class,
                CimCommand.class,
                PimCommand.class,
                PsmCommand.class
        })
public final class MdeEvlCommand implements Runnable {

    @Override
    public void run() {
        throw new picocli.CommandLine.ParameterException(
                new picocli.CommandLine(this), "Choose a subcommand: run, cim, pim, or psm.");
    }
}
