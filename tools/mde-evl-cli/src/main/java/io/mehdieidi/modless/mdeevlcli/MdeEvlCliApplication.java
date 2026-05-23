package io.mehdieidi.modless.mdeevlcli;

import picocli.CommandLine;

public final class MdeEvlCliApplication {

    private MdeEvlCliApplication() {
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new MdeEvlCommand()).execute(args);
        System.exit(exitCode);
    }
}
