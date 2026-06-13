package io.mehdieidi.modless.mde.validation;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Objects;

/**
 * Per-module validation report with phase timing, violations, and diagnostics.
 *
 * @param moduleFile EVL module file that was executed
 * @param duration total module duration
 * @param parseDuration time spent parsing the module
 * @param modelLoadDuration time spent loading configured models
 * @param structuralValidationDuration time spent validating EMF resources
 * @param evlExecuteDuration time spent executing EVL constraints
 * @param violationMappingDuration time spent converting EVL violations
 * @param disposeDuration time spent disposing Epsilon models/context
 * @param violations mapped constraint violations
 * @param diagnostics diagnostics emitted for this module
 */
public record EvlModuleReport(
    Path moduleFile,
    Duration duration,
    Duration parseDuration,
    Duration modelLoadDuration,
    Duration structuralValidationDuration,
    Duration evlExecuteDuration,
    Duration violationMappingDuration,
    Duration disposeDuration,
    List<EvlConstraintViolation> violations,
    List<EvlDiagnostic> diagnostics) {

  /** Normalizes nullable durations and defensively copies collections. */
  public EvlModuleReport {
    Objects.requireNonNull(moduleFile, "moduleFile");
    Objects.requireNonNull(duration, "duration");
    parseDuration = parseDuration == null ? Duration.ZERO : parseDuration;
    modelLoadDuration = modelLoadDuration == null ? Duration.ZERO : modelLoadDuration;
    structuralValidationDuration =
        structuralValidationDuration == null ? Duration.ZERO : structuralValidationDuration;
    evlExecuteDuration = evlExecuteDuration == null ? Duration.ZERO : evlExecuteDuration;
    violationMappingDuration =
        violationMappingDuration == null ? Duration.ZERO : violationMappingDuration;
    disposeDuration = disposeDuration == null ? Duration.ZERO : disposeDuration;
    violations = violations == null ? List.of() : List.copyOf(violations);
    diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
  }

  /**
   * Creates a module report without detailed phase timing.
   *
   * @param moduleFile EVL module file
   * @param duration total module duration
   * @param violations mapped constraint violations
   * @param diagnostics diagnostics emitted for this module
   */
  public EvlModuleReport(
      Path moduleFile,
      Duration duration,
      List<EvlConstraintViolation> violations,
      List<EvlDiagnostic> diagnostics) {
    this(
        moduleFile,
        duration,
        Duration.ZERO,
        Duration.ZERO,
        Duration.ZERO,
        Duration.ZERO,
        Duration.ZERO,
        Duration.ZERO,
        violations,
        diagnostics);
  }
}
