package io.mehdieidi.varka.mde.etl;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Immutable summary of an ETL run, including timing, diagnostics, and captured output streams.
 *
 * @param status final execution status
 * @param moduleFile ETL entry module
 * @param startedAt wall-clock start time
 * @param finishedAt wall-clock finish time
 * @param duration total wall-clock duration
 * @param phaseTiming phase-level timing values
 * @param diagnostics diagnostics emitted during the run
 * @param standardOutput captured standard output
 * @param warningOutput captured warning output
 * @param errorOutput captured error output
 */
public record EtlExecutionReport(
    EtlExecutionStatus status,
    Path moduleFile,
    Instant startedAt,
    Instant finishedAt,
    Duration duration,
    EtlPhaseTiming phaseTiming,
    List<EtlDiagnostic> diagnostics,
    String standardOutput,
    String warningOutput,
    String errorOutput) {

  /** Normalizes nullable optional fields and defensively copies collections. */
  public EtlExecutionReport {
    Objects.requireNonNull(status, "status");
    Objects.requireNonNull(moduleFile, "moduleFile");
    Objects.requireNonNull(startedAt, "startedAt");
    Objects.requireNonNull(finishedAt, "finishedAt");
    Objects.requireNonNull(duration, "duration");
    phaseTiming = phaseTiming == null ? new EtlPhaseTiming() : phaseTiming;
    diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
    standardOutput = standardOutput == null ? "" : standardOutput;
    warningOutput = warningOutput == null ? "" : warningOutput;
    errorOutput = errorOutput == null ? "" : errorOutput;
  }

  /**
   * Indicates whether the ETL run completed successfully.
   *
   * @return {@code true} when the status is {@link EtlExecutionStatus#SUCCEEDED}
   */
  public boolean succeeded() {
    return status == EtlExecutionStatus.SUCCEEDED;
  }
}
