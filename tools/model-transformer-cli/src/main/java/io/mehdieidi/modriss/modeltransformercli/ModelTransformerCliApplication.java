package io.mehdieidi.modriss.modeltransformercli;

import picocli.CommandLine;

/** Process entry point for the ETL command-line application. */
public final class ModelTransformerCliApplication {

  /** Prevents construction of the application entry-point class. */
  private ModelTransformerCliApplication() {}

  /**
   * Executes the ETL command and terminates with its exit code.
   *
   * @param args command-line arguments
   */
  public static void main(String[] args) {
    int exitCode = new CommandLine(new ModelTransformerCommand()).execute(args);
    System.exit(exitCode);
  }
}
