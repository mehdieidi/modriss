package io.mehdieidi.modless.mdecli;

import picocli.CommandLine;

public final class MdeCliApplication {

  private MdeCliApplication() {
  }

  public static void main(String[] args) {
    int exitCode = new CommandLine(new MetamodelCommand()).execute(args);
    System.exit(exitCode);
  }
}
