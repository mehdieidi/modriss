package io.mehdieidi.modless.mdeetlcli;

import picocli.CommandLine;

/**
 * Process entry point for the ETL command-line application.
 */
public final class MdeEtlCliApplication {

    /**
     * Prevents construction of the application entry-point class.
     */
    private MdeEtlCliApplication() {
    }

    /**
     * Executes the ETL command and terminates with its exit code.
     *
     * @param args command-line arguments
     */
    public static void main(String[] args) {
        int exitCode = new CommandLine(new MdeEtlCommand()).execute(args);
        System.exit(exitCode);
    }
}
