package io.mehdieidi.modless.mdecli.diagnostics;

import io.mehdieidi.modless.mdecli.service.ConversionRequest;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class ConversionReport {

  public enum Status {
    SUCCESS,
    FAILURE
  }

  private final Instant startedAt = Instant.now();
  private final List<String> events = new ArrayList<>();
  private final List<DiagnosticEntry> diagnostics = new ArrayList<>();
  private Status status = Status.SUCCESS;
  private String summary = "Conversion completed successfully.";
  private String resolutionHint = "";
  private Throwable cause;
  private Path outputPath;

  public void recordEvent(String event) {
    events.add(event);
  }

  public void addDiagnostic(DiagnosticEntry diagnostic) {
    diagnostics.add(Objects.requireNonNull(diagnostic));
  }

  public void fail(String summary, String resolutionHint, Throwable throwable) {
    this.status = Status.FAILURE;
    this.summary = Objects.requireNonNull(summary);
    this.resolutionHint = resolutionHint == null ? "" : resolutionHint;
    this.cause = throwable;
  }

  public void succeed(String summary, Path outputPath) {
    this.status = Status.SUCCESS;
    this.summary = Objects.requireNonNull(summary);
    this.outputPath = outputPath;
  }

  public Status getStatus() {
    return status;
  }

  public String getSummary() {
    return summary;
  }

  public String getResolutionHint() {
    return resolutionHint;
  }

  public Throwable getCause() {
    return cause;
  }

  public List<String> getEvents() {
    return List.copyOf(events);
  }

  public List<DiagnosticEntry> getDiagnostics() {
    return List.copyOf(diagnostics);
  }

  public Duration duration() {
    return Duration.between(startedAt, Instant.now());
  }

  public Path getOutputPath() {
    return outputPath;
  }

  public static ConversionReport failedUnexpectedly(ConversionRequest request, RuntimeException ex) {
    ConversionReport report = new ConversionReport();
    report.recordEvent("Unexpected failure while converting input: " + request.input());
    report.fail(
        "The CLI failed unexpectedly while converting the metamodel.",
        "Re-run with --log-file to capture the stack trace and inspect the failing input path.",
        ex);
    return report;
  }
}
