package io.mehdieidi.modriss.platform.psm;

/** Execution boundary; production implementations must isolate user-supplied executable code. */
@FunctionalInterface
public interface PsmExecutionRunner {
  PsmWorkerResult run(PsmWorkerRequest request);
}
