package io.mehdieidi.modriss.emfatictoecorecli;

import picocli.CommandLine;

/** Process entry point for the Emfatic-to-Ecore command-line application. */
public final class EmfaticToEcoreCliApplication {

  /** Prevents construction of the application entry-point class. */
  private EmfaticToEcoreCliApplication() {}

  /**
   * Executes the metamodel command and terminates with its exit code.
   *
   * @param args command-line arguments
   */
  public static void main(String[] args) {
    int exitCode = new CommandLine(new EmfaticToEcoreCommand()).execute(args);
    System.exit(exitCode);
  }
}
