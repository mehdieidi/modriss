package io.mehdieidi.modriss.mdeevlcli;

import picocli.CommandLine;

/** Process entry point for the EVL validation command-line application. */
public final class MdeEvlCliApplication {

  /** Prevents construction of the application entry-point class. */
  private MdeEvlCliApplication() {}

  /**
   * Executes the EVL command and terminates with its exit code.
   *
   * @param args command-line arguments
   */
  public static void main(String[] args) {
    int exitCode = new CommandLine(new MdeEvlCommand()).execute(args);
    System.exit(exitCode);
  }
}
