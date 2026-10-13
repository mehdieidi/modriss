package io.mehdieidi.modriss.mde.generation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Mutable accumulator for EGX phase timing values in milliseconds. */
public final class EgxPhaseTiming {

  private long validationMs;
  private long templateSetupMs;
  private long parseMs;
  private long sourceModelLoadMs;
  private long generationExecuteMs;
  private long fileNormalizationMs;
  private long traceFinalizationMs;
  private long artifactDiscoveryMs;
  private long disposeMs;
  private long totalMs;

  public long validationMs() {
    return validationMs;
  }

  public long templateSetupMs() {
    return templateSetupMs;
  }

  public long parseMs() {
    return parseMs;
  }

  public long sourceModelLoadMs() {
    return sourceModelLoadMs;
  }

  public long generationExecuteMs() {
    return generationExecuteMs;
  }

  public long fileNormalizationMs() {
    return fileNormalizationMs;
  }

  public long traceFinalizationMs() {
    return traceFinalizationMs;
  }

  public long artifactDiscoveryMs() {
    return artifactDiscoveryMs;
  }

  public long disposeMs() {
    return disposeMs;
  }

  public long totalMs() {
    return totalMs;
  }

  public Map<String, Long> asMap() {
    Map<String, Long> values = new LinkedHashMap<>();
    values.put("validationMs", validationMs);
    values.put("templateSetupMs", templateSetupMs);
    values.put("parseMs", parseMs);
    values.put("sourceModelLoadMs", sourceModelLoadMs);
    values.put("generationExecuteMs", generationExecuteMs);
    values.put("fileNormalizationMs", fileNormalizationMs);
    values.put("traceFinalizationMs", traceFinalizationMs);
    values.put("artifactDiscoveryMs", artifactDiscoveryMs);
    values.put("disposeMs", disposeMs);
    values.put("totalMs", totalMs);
    return Collections.unmodifiableMap(values);
  }

  void addValidation(long elapsedNanos) {
    validationMs += millis(elapsedNanos);
  }

  void addTemplateSetup(long elapsedNanos) {
    templateSetupMs += millis(elapsedNanos);
  }

  void addParse(long elapsedNanos) {
    parseMs += millis(elapsedNanos);
  }

  void addSourceModelLoad(long elapsedNanos) {
    sourceModelLoadMs += millis(elapsedNanos);
  }

  void addExecute(long elapsedNanos) {
    generationExecuteMs += millis(elapsedNanos);
  }

  void addFileNormalization(long elapsedNanos) {
    fileNormalizationMs += millis(elapsedNanos);
  }

  void addTraceFinalization(long elapsedNanos) {
    traceFinalizationMs += millis(elapsedNanos);
  }

  void addArtifactDiscovery(long elapsedNanos) {
    artifactDiscoveryMs += millis(elapsedNanos);
  }

  void addDispose(long elapsedNanos) {
    disposeMs += millis(elapsedNanos);
  }

  void setTotal(long elapsedNanos) {
    totalMs = millis(elapsedNanos);
  }

  private long millis(long elapsedNanos) {
    return Math.max(0L, elapsedNanos / 1_000_000L);
  }
}
