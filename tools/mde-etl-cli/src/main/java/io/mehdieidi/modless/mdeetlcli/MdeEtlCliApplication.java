package io.mehdieidi.modless.mdeetlcli;

import picocli.CommandLine;

public final class MdeEtlCliApplication {

    private MdeEtlCliApplication() {
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new MdeEtlCommand()).execute(args);
        System.exit(exitCode);
    }
}
