package io.mehdieidi.modless.mdem2tcli;

import picocli.CommandLine.Command;

@Command(
        name = "mde-m2t-cli",
        mixinStandardHelpOptions = true,
        version = "0.0.1-SNAPSHOT",
        description = "Executes Eclipse Epsilon EGX/EGL model-to-text generations.",
        subcommands = {
                AwsPsmToArtifactsCommand.class
        })
public final class MdeM2tCommand implements Runnable {

    @Override
    public void run() {
        throw new picocli.CommandLine.ParameterException(
                new picocli.CommandLine(this), "Choose a subcommand: aws-psm-to-artifacts.");
    }
}
