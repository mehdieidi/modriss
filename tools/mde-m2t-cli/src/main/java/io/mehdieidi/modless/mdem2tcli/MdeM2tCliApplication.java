package io.mehdieidi.modless.mdem2tcli;

import picocli.CommandLine;

public final class MdeM2tCliApplication {

    private MdeM2tCliApplication() {
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new MdeM2tCommand()).execute(args);
        System.exit(exitCode);
    }
}
