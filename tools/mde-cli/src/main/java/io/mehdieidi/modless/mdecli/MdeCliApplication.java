package io.mehdieidi.modless.mdecli;

import picocli.CommandLine;

/**
 * Process entry point for the Emfatic-to-Ecore command-line application.
 */
public final class MdeCliApplication {

    /**
     * Prevents construction of the application entry-point class.
     */
    private MdeCliApplication() {
    }

    /**
     * Executes the metamodel command and terminates with its exit code.
     *
     * @param args command-line arguments
     */
    public static void main(String[] args) {
        int exitCode = new CommandLine(new MetamodelCommand()).execute(args);
        System.exit(exitCode);
    }
}
