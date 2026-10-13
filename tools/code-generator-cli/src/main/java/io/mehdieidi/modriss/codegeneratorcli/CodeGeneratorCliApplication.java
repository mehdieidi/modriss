package io.mehdieidi.modriss.codegeneratorcli;

import picocli.CommandLine;

/** Process entry point for the model-to-text command-line application. */
public final class CodeGeneratorCliApplication {

  /** Prevents construction of the application entry-point class. */
  private CodeGeneratorCliApplication() {}

  /**
   * Executes the model-to-text command and terminates with its exit code.
   *
   * @param args command-line arguments
   */
  public static void main(String[] args) {
    int exitCode = new CommandLine(new CodeGeneratorCommand()).execute(args);
    System.exit(exitCode);
  }
}
