package io.mehdieidi.modriss.platform.modeling.methodology;

import io.mehdieidi.modriss.platform.kernel.PlatformException;
import io.mehdieidi.modriss.platform.modeling.runtime.MdeRuntimePaths;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/** Loads SPEM-aligned modeling process definitions from {@code mde/process/definitions/}. */
public final class ModelingProcessService {

  private static final Set<String> LEVELS = Set.of("cim", "pim", "psm", "artifact", "end-to-end");

  private final ObjectMapper objectMapper = new ObjectMapper();
  private final MdeRuntimePaths runtimePaths;

  /** Creates a process definition loader using default runtime paths. */
  public ModelingProcessService() {
    this(new MdeRuntimePaths(null));
  }

  /**
   * Creates a process definition loader.
   *
   * @param runtimePaths MDE runtime path resolver
   */
  public ModelingProcessService(MdeRuntimePaths runtimePaths) {
    this.runtimePaths = runtimePaths;
  }

  /**
   * Returns a modeling process definition for the requested level.
   *
   * @param level {@code cim}, {@code pim}, {@code psm}, {@code artifact}, or {@code end-to-end}
   * @return process definition map
   */
  public Map<String, Object> processDefinition(String level) {
    String normalized = normalizeLevel(level);
    return readDefinition(normalized);
  }

  /**
   * Returns the consolidated SPEM method-content index for the full MODRISS library.
   *
   * @return reusable roles, tasks, work products, guidance, and process components
   */
  public Map<String, Object> methodContentIndex() {
    Path file =
        methodologyRoot().resolve("method").resolve("spem").resolve("method-content-index.json");
    return readJsonFile(file, "SPEM method-content index");
  }

  /**
   * Returns coverage matrix for a modeling level.
   *
   * @param level {@code cim}, {@code pim}, or {@code psm}
   * @return coverage matrix map
   */
  public Map<String, Object> coverageMatrix(String level) {
    String normalized = normalizeLevel(level);
    if ("end-to-end".equals(normalized) || "artifact".equals(normalized)) {
      throw new PlatformException(400, "Coverage matrix is not defined for " + normalized + ".");
    }
    Path file = methodologyRoot().resolve("coverage-matrix").resolve(normalized + "-coverage.json");
    return readJsonFile(file, "coverage matrix for " + normalized);
  }

  /**
   * Compares live model element types against the coverage matrix for a level.
   *
   * @param level modeling level
   * @param model imported model map (JSON object tree)
   * @return coverage status with present and missing concepts
   */
  @SuppressWarnings("unchecked")
  public Map<String, Object> coverageStatus(String level, Map<String, Object> model) {
    String normalized = normalizeLevel(level);
    if ("end-to-end".equals(normalized) || "artifact".equals(normalized)) {
      throw new PlatformException(400, "Coverage status is not defined for " + normalized + ".");
    }
    Map<String, Object> matrix = coverageMatrix(normalized);
    List<Map<String, Object>> entries = listOfMaps(matrix.get("entries"));
    Set<String> presentTypes = collectElementTypes(model);
    List<Map<String, Object>> concepts = new ArrayList<>();
    int covered = 0;
    for (Map<String, Object> entry : entries) {
      String concept = stringValue(entry.get("concept"));
      boolean inModel = presentTypes.contains(concept);
      if (inModel) {
        covered++;
      }
      concepts.add(
          Map.of(
              "concept",
              concept,
              "kind",
              stringValue(entry.get("kind")),
              "inModel",
              inModel,
              "taskIds",
              entry.getOrDefault("taskIds", List.of())));
    }
    return Map.of(
        "level", normalized,
        "totalConcepts", entries.size(),
        "presentConcepts", covered,
        "completionRatio", entries.isEmpty() ? 0.0 : (double) covered / entries.size(),
        "concepts", concepts);
  }

  private Map<String, Object> readDefinition(String level) {
    Path file = methodologyRoot().resolve("definitions").resolve(level + ".json");
    return readJsonFile(file, "process definition for " + level);
  }

  private Path methodologyRoot() {
    return runtimePaths.methodologyRoot();
  }

  private Map<String, Object> readJsonFile(Path file, String label) {
    if (!Files.isRegularFile(file)) {
      throw new PlatformException(500, "Missing " + label + ": " + file);
    }
    try (InputStream in = Files.newInputStream(file)) {
      return objectMapper.readValue(in, new TypeReference<>() {});
    } catch (IOException ex) {
      throw new PlatformException(500, "Failed to read " + label + ": " + ex.getMessage());
    }
  }

  private String normalizeLevel(String level) {
    if (level == null || level.isBlank()) {
      throw new PlatformException(400, "Modeling process level is required.");
    }
    String normalized = level.trim().toLowerCase();
    if (!LEVELS.contains(normalized)) {
      throw new PlatformException(
          400, "Unsupported modeling process level: " + level + ". Expected one of " + LEVELS);
    }
    return normalized;
  }

  @SuppressWarnings("unchecked")
  private Set<String> collectElementTypes(Map<String, Object> model) {
    Set<String> types = new LinkedHashSet<>();
    collectElementTypes(model, types);
    return types;
  }

  @SuppressWarnings("unchecked")
  private void collectElementTypes(Object node, Set<String> types) {
    if (!(node instanceof Map<?, ?> rawMap)) {
      return;
    }
    Map<String, Object> map = (Map<String, Object>) rawMap;
    Object type = map.get("type");
    if (type instanceof String typeName && !typeName.isBlank()) {
      types.add(typeName);
    }
    for (Object value : map.values()) {
      if (value instanceof Map<?, ?>) {
        collectElementTypes(value, types);
      } else if (value instanceof List<?> list) {
        for (Object item : list) {
          collectElementTypes(item, types);
        }
      }
    }
  }

  @SuppressWarnings("unchecked")
  private List<Map<String, Object>> listOfMaps(Object value) {
    if (!(value instanceof List<?> list)) {
      return List.of();
    }
    List<Map<String, Object>> result = new ArrayList<>();
    for (Object item : list) {
      if (item instanceof Map<?, ?> map) {
        result.add(new LinkedHashMap<>((Map<String, Object>) map));
      }
    }
    return result;
  }

  private String stringValue(Object value) {
    return value == null ? "" : String.valueOf(value);
  }
}
