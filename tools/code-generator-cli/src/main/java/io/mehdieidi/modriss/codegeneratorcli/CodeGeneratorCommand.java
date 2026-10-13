package io.mehdieidi.modriss.codegeneratorcli;

import picocli.CommandLine.Command;

/** Root Picocli command for model-to-text generation. */
@Command(
    name = "code-generator-cli",
    mixinStandardHelpOptions = true,
    version = "0.0.1-SNAPSHOT",
    description = "Executes Eclipse Epsilon EGX/EGL model-to-text generations.",
    subcommands = {AwsPsmToArtifactsCommand.class})
public final class CodeGeneratorCommand implements Runnable {

  /** Rejects invocation without a generation subcommand. */
  @Override
  public void run() {
    throw new picocli.CommandLine.ParameterException(
        new picocli.CommandLine(this), "Choose a subcommand: psm-to-artifacts.");
  }
}
