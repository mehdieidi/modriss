package io.mehdieidi.modriss.modelvalidatorcli;

import picocli.CommandLine;

/** Process entry point for the EVL validation command-line application. */
public final class ModelValidatorCliApplication {

  /** Prevents construction of the application entry-point class. */
  private ModelValidatorCliApplication() {}

  /**
   * Executes the EVL command and terminates with its exit code.
   *
   * @param args command-line arguments
   */
  public static void main(String[] args) {
    int exitCode = new CommandLine(new ModelValidatorCommand()).execute(args);
    System.exit(exitCode);
  }
}
