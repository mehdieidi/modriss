package io.mehdieidi.modless.mdecli.diagnostics;

import io.mehdieidi.modless.mdecli.service.ConversionRequest;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Mutable execution report accumulated during an Emfatic-to-Ecore conversion. */
public final class ConversionReport {

  private final Instant startedAt = Instant.now();
  private final List<String> events = new ArrayList<>();
  private final List<DiagnosticEntry> diagnostics = new ArrayList<>();
  private Status status = Status.SUCCESS;
  private String summary = "Conversion completed successfully.";
  private String resolutionHint = "";
  private Throwable cause;
  private Path outputPath;

  /**
   * Creates a failed report for a runtime exception outside normal conversion handling.
   *
   * @param request conversion request
   * @param ex unexpected failure
   * @return failed conversion report
   */
  public static ConversionReport failedUnexpectedly(
      ConversionRequest request, RuntimeException ex) {
    ConversionReport report = new ConversionReport();
    report.recordEvent("Unexpected failure while converting input: " + request.input());
    report.fail(
        "The CLI failed unexpectedly while converting the metamodel.",
        "Re-run with --log-file to capture the stack trace and inspect the failing input path.",
        ex);
    return report;
  }

  /**
   * Records a chronological execution event.
   *
   * @param event event description
   */
  public void recordEvent(String event) {
    events.add(event);
  }

  /**
   * Adds a structured compiler or resource diagnostic.
   *
   * @param diagnostic diagnostic to add
   */
  public void addDiagnostic(DiagnosticEntry diagnostic) {
    diagnostics.add(Objects.requireNonNull(diagnostic));
  }

  /**
   * Marks conversion as failed.
   *
   * @param summary failure summary
   * @param resolutionHint actionable resolution guidance
   * @param throwable underlying cause, when available
   */
  public void fail(String summary, String resolutionHint, Throwable throwable) {
    this.status = Status.FAILURE;
    this.summary = Objects.requireNonNull(summary);
    this.resolutionHint = resolutionHint == null ? "" : resolutionHint;
    this.cause = throwable;
  }

  /**
   * Marks conversion as successful.
   *
   * @param summary success summary
   * @param outputPath generated Ecore path
   */
  public void succeed(String summary, Path outputPath) {
    this.status = Status.SUCCESS;
    this.summary = Objects.requireNonNull(summary);
    this.outputPath = outputPath;
  }

  /**
   * Returns the current conversion status.
   *
   * @return conversion status
   */
  public Status getStatus() {
    return status;
  }

  /**
   * Returns the human-readable result summary.
   *
   * @return result summary
   */
  public String getSummary() {
    return summary;
  }

  /**
   * Returns actionable failure guidance.
   *
   * @return resolution hint, or an empty string
   */
  public String getResolutionHint() {
    return resolutionHint;
  }

  /**
   * Returns the underlying failure cause.
   *
   * @return cause, or {@code null}
   */
  public Throwable getCause() {
    return cause;
  }

  /**
   * Returns an immutable snapshot of execution events.
   *
   * @return recorded events
   */
  public List<String> getEvents() {
    return List.copyOf(events);
  }

  /**
   * Returns an immutable snapshot of diagnostics.
   *
   * @return recorded diagnostics
   */
  public List<DiagnosticEntry> getDiagnostics() {
    return List.copyOf(diagnostics);
  }

  /**
   * Returns elapsed time since this report was created.
   *
   * @return current conversion duration
   */
  public Duration duration() {
    return Duration.between(startedAt, Instant.now());
  }

  /**
   * Returns the generated Ecore path.
   *
   * @return output path, or {@code null} before success
   */
  public Path getOutputPath() {
    return outputPath;
  }

  /** Overall conversion outcome. */
  public enum Status {
    SUCCESS,
    FAILURE
  }
}
