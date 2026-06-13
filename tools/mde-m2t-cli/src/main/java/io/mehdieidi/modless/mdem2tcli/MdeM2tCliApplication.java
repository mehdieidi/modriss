package io.mehdieidi.modless.mdem2tcli;

import picocli.CommandLine;

/** Process entry point for the model-to-text command-line application. */
public final class MdeM2tCliApplication {

  /** Prevents construction of the application entry-point class. */
  private MdeM2tCliApplication() {}

  /**
   * Executes the model-to-text command and terminates with its exit code.
   *
   * @param args command-line arguments
   */
  public static void main(String[] args) {
    int exitCode = new CommandLine(new MdeM2tCommand()).execute(args);
    System.exit(exitCode);
  }
}
