package io.mehdieidi.modless.mdecli.diagnostics;

public record DiagnosticEntry(
    Severity severity,
    String message,
    String location,
    int line,
    int column,
    String hint,
    String sourceExcerpt) {

  public enum Severity {
    ERROR,
    WARNING,
    INFO
  }
}
