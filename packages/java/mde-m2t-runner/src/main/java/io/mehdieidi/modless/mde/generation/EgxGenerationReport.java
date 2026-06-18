package io.mehdieidi.modless.mde.generation;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Immutable summary of an EGX generation run.
 *
 * @param status final generation status
 * @param moduleFile EGX entry module
 * @param outputDirectory generation output directory
 * @param startedAt wall-clock start time
 * @param finishedAt wall-clock finish time
 * @param duration total wall-clock duration
 * @param phaseTiming phase-level timing values
 * @param diagnostics diagnostics emitted during generation
 * @param generatedFiles generated files relative to the output directory
 * @param standardOutput captured standard output
 * @param warningOutput captured warning output
 * @param errorOutput captured error output
 */
public record EgxGenerationReport(
    GenerationStatus status,
    Path moduleFile,
    Path outputDirectory,
    Instant startedAt,
    Instant finishedAt,
    Duration duration,
    EgxPhaseTiming phaseTiming,
    List<GenerationDiagnostic> diagnostics,
    List<Path> generatedFiles,
    String standardOutput,
    String warningOutput,
    String errorOutput) {

  /** Normalizes nullable optional fields and defensively copies collections. */
  public EgxGenerationReport {
    Objects.requireNonNull(status, "status");
    Objects.requireNonNull(moduleFile, "moduleFile");
    Objects.requireNonNull(outputDirectory, "outputDirectory");
    Objects.requireNonNull(startedAt, "startedAt");
    Objects.requireNonNull(finishedAt, "finishedAt");
    Objects.requireNonNull(duration, "duration");
    phaseTiming = phaseTiming == null ? new EgxPhaseTiming() : phaseTiming;
    diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
    generatedFiles = generatedFiles == null ? List.of() : List.copyOf(generatedFiles);
    standardOutput = standardOutput == null ? "" : standardOutput;
    warningOutput = warningOutput == null ? "" : warningOutput;
    errorOutput = errorOutput == null ? "" : errorOutput;
  }

  /**
   * Indicates whether generation completed successfully.
   *
   * @return {@code true} when the status is {@link GenerationStatus#SUCCEEDED}
   */
  public boolean succeeded() {
    return status == GenerationStatus.SUCCEEDED;
  }
}
