package io.mehdieidi.modriss.platform.psm;

import java.util.List;
import java.util.Map;
import tools.jackson.databind.JsonNode;

/** Bounded result of a package inspection, transformation, validation or generation. */
public record PsmWorkerResult(
    boolean valid,
    String xmi,
    JsonNode schema,
    List<String> issues,
    Map<String, String> files,
    JsonNode modelJson) {
  public PsmWorkerResult(
      boolean valid, String xmi, JsonNode schema, List<String> issues, Map<String, String> files) {
    this(valid, xmi, schema, issues, files, null);
  }
}
