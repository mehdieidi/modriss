package io.mehdieidi.varka.backend.admin;

import io.mehdieidi.varka.platform.kernel.PlatformException;
import io.mehdieidi.varka.platform.modeling.config.ModelingConfigService;
import io.mehdieidi.varka.platform.modeling.runtime.MdeRuntimePaths;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/** File-backed administration service for Concrete Visual Syntax notation documents. */
@Service
public class AdminNotationService {

  private static final List<String> LEVELS = List.of("cim", "pim", "psm");
  private static final String BACKUP_DIRECTORY = "backup";

  private final MdeRuntimePaths paths;
  private final ObjectMapper mapper;
  private final ModelingConfigService modelingConfig;

  public AdminNotationService(
      MdeRuntimePaths paths, ObjectMapper mapper, ModelingConfigService modelingConfig) {
    this.paths = paths;
    this.mapper = mapper;
    this.modelingConfig = modelingConfig;
  }

  public Map<String, Object> load(String level) {
    Path file = notationFile(level);
    if (!Files.isRegularFile(file)) {
      throw new PlatformException(404, "CVS notation file was not found: " + file.getFileName());
    }
    try {
      Map<String, Object> doc = mapper.readValue(Files.readString(file), new TypeReference<>() {});
      validate(level, doc);
      enrichForEditor(level, doc);
      return doc;
    } catch (PlatformException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new PlatformException(500, "Could not load CVS notation file.");
    }
  }

  public ActivationResult save(String level, Map<String, Object> doc) {
    Map<String, Object> persisted = withoutEditorCatalog(doc);
    validate(level, persisted);
    Path file = notationFile(level);
    Path backup = null;
    Path backupDirectory = file.getParent().resolve(BACKUP_DIRECTORY);
    Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
    try {
      Files.createDirectories(file.getParent());
      Files.writeString(
          temporary, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(persisted) + "\n");
      if (Files.isRegularFile(file)) {
        Files.createDirectories(backupDirectory);
        int suffix = 1;
        do {
          String fileName = file.getFileName().toString();
          String backupName =
              fileName.endsWith(".json")
                  ? fileName.substring(0, fileName.length() - ".json".length())
                      + ".old"
                      + suffix
                      + ".json"
                  : fileName + ".old" + suffix;
          backup = backupDirectory.resolve(backupName);
          suffix++;
        } while (Files.exists(backup));
        Files.move(file, backup, StandardCopyOption.REPLACE_EXISTING);
      }
      try {
        Files.move(
            temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
      } catch (Exception atomicMoveUnsupported) {
        Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
      }
      return new ActivationResult(
          file.getFileName().toString(),
          backup == null ? null : backupDirectory.getFileName() + "/" + backup.getFileName(),
          Instant.now().toString());
    } catch (Exception ex) {
      try {
        Files.deleteIfExists(temporary);
        if (backup != null && !Files.exists(file)) {
          Files.move(backup, file, StandardCopyOption.REPLACE_EXISTING);
        }
      } catch (Exception restoreFailure) {
        ex.addSuppressed(restoreFailure);
      }
      throw new PlatformException(500, "Could not save CVS notation file.");
    }
  }

  public record ActivationResult(String activeFile, String backupFile, String activatedAt) {}

  private Path notationFile(String level) {
    return paths.notationRoot().resolve(normalizeLevel(level) + ".cvs.json").normalize();
  }

  /** Adds the complete Ecore-derived editing catalog to the raw CVS document. */
  private void enrichForEditor(String level, Map<String, Object> doc) {
    Map<String, Object> configured = levelConfiguration(level);
    completeContainerProfiles(doc, configured);
    copyContainmentFeatures(doc, configured.get("containmentPalettes"));
    copyCatalog(doc, "metamodelRelationshipRules", configured.get("relationshipRules"));
    copyCatalog(doc, "metamodelSemanticReferenceRules", configured.get("semanticReferenceRules"));
    copyCatalog(doc, "metamodelSemanticEdgeObjectRules", configured.get("semanticEdgeObjectRules"));
  }

  private void copyContainmentFeatures(Map<String, Object> doc, Object value) {
    if (!(value instanceof Map<?, ?> rawPalettes)) {
      doc.put("metamodelContainmentFeatures", Map.of());
      return;
    }
    Map<String, Object> featuresByOwner = new LinkedHashMap<>();
    for (Map.Entry<?, ?> entry : rawPalettes.entrySet()) {
      if (entry.getValue() instanceof Map<?, ?> rawPalette) {
        featuresByOwner.put(
            String.valueOf(entry.getKey()),
            new ArrayList<>(
                rawPalette.get("features") instanceof List<?> features ? features : List.of()));
      }
    }
    doc.put("metamodelContainmentFeatures", featuresByOwner);
  }

  /** Makes every owner with an Ecore-derived containment palette available to the editor. */
  private void completeContainerProfiles(Map<String, Object> doc, Map<String, Object> configured) {
    if (!(configured.get("containmentPalettes") instanceof Map<?, ?> palettes)) {
      return;
    }
    List<Map<String, Object>> profiles = new ArrayList<>();
    if (doc.get("containers") instanceof List<?> configuredProfiles) {
      for (Object item : configuredProfiles) {
        if (item instanceof Map<?, ?> raw) {
          profiles.add(new LinkedHashMap<>(stringKeyMap(raw)));
        }
      }
    }
    Set<String> knownOwners = new LinkedHashSet<>();
    for (Map<String, Object> profile : profiles) {
      String owner = String.valueOf(profile.getOrDefault("elementType", "")).trim();
      if (!owner.isBlank()) {
        knownOwners.add(owner);
      }
    }
    for (Map.Entry<?, ?> entry : palettes.entrySet()) {
      String owner = String.valueOf(entry.getKey()).trim();
      if (owner.isBlank()
          || knownOwners.contains(owner)
          || !(entry.getValue() instanceof Map<?, ?> raw)) {
        continue;
      }
      Map<String, Object> palette = stringKeyMap(raw);
      List<String> types = stringList(palette.get("types"));
      List<String> canvas = stringList(palette.get("canvas"));
      if (canvas.isEmpty()) {
        canvas = types;
      }
      profiles.add(
          new LinkedHashMap<>(
              Map.of(
                  "elementType", owner,
                  "palette", types,
                  "canvas", canvas,
                  "relationshipKinds", List.of(),
                  "layoutHint", "CONTAINER")));
      knownOwners.add(owner);
    }
    doc.put("containers", profiles);
  }

  private Map<String, Object> levelConfiguration(String level) {
    Map<String, Object> root = modelingConfig.config();
    Object levels = root.get("levels");
    if (!(levels instanceof Map<?, ?> rawLevels)) {
      return Map.of();
    }
    Object configured = rawLevels.get(normalizeLevel(level));
    return configured instanceof Map<?, ?> raw ? stringKeyMap(raw) : Map.of();
  }

  private void copyCatalog(Map<String, Object> doc, String field, Object value) {
    if (value instanceof List<?> list) {
      doc.put(field, new ArrayList<>(list));
    } else {
      doc.put(field, List.of());
    }
  }

  private Map<String, Object> withoutEditorCatalog(Map<String, Object> doc) {
    Map<String, Object> persisted = new LinkedHashMap<>(doc == null ? Map.of() : doc);
    persisted.remove("metamodelRelationshipRules");
    persisted.remove("metamodelContainmentFeatures");
    persisted.remove("metamodelSemanticReferenceRules");
    persisted.remove("metamodelSemanticEdgeObjectRules");
    return persisted;
  }

  private Map<String, Object> stringKeyMap(Map<?, ?> source) {
    Map<String, Object> result = new LinkedHashMap<>();
    source.forEach((key, value) -> result.put(String.valueOf(key), value));
    return result;
  }

  private List<String> stringList(Object value) {
    if (!(value instanceof List<?> list)) {
      return List.of();
    }
    return list.stream().map(String::valueOf).filter(item -> !item.isBlank()).toList();
  }

  private void validate(String level, Map<String, Object> doc) {
    String normalized = normalizeLevel(level);
    if (doc == null) {
      throw new PlatformException(400, "CVS document is required.");
    }
    if (!Integer.valueOf(2).equals(doc.get("cvsVersion"))) {
      throw new PlatformException(400, "CVS document must declare cvsVersion: 2.");
    }
    Object metamodelRef = doc.get("metamodelRef");
    if (!(metamodelRef instanceof Map<?, ?> meta)) {
      throw new PlatformException(400, "CVS document must declare metamodelRef.");
    }
    String configuredLevel = String.valueOf(meta.get("level"));
    if (!normalized.equals(configuredLevel)) {
      throw new PlatformException(
          400,
          "CVS metamodelRef.level mismatch: expected " + normalized + ", found " + configuredLevel);
    }
    if (!(doc.get("elements") instanceof List<?> elements) || elements.isEmpty()) {
      throw new PlatformException(
          400, "CVS document must define at least one element visual definition.");
    }
    if (!(doc.get("views") instanceof List<?> views) || views.isEmpty()) {
      throw new PlatformException(400, "CVS document must define at least one view.");
    }
    if (!(doc.get("containers") instanceof List<?>)) {
      throw new PlatformException(400, "CVS document must define containers.");
    }
    for (String removedField :
        List.of(
            "primitives",
            "notationPrimitives",
            "elementOverrides",
            "viewpoints",
            "universalSyntax",
            "kernelSyntax",
            "kernelNotation")) {
      if (doc.containsKey(removedField)) {
        throw new PlatformException(400, "CVS document contains removed field: " + removedField);
      }
    }
    if (!(doc.get("canvasPolicy") instanceof Map<?, ?> canvasPolicy) || canvasPolicy.isEmpty()) {
      throw new PlatformException(400, "CVS document must define canvasPolicy.");
    }
  }

  private String normalizeLevel(String level) {
    String normalized = String.valueOf(level == null ? "" : level).trim().toLowerCase(Locale.ROOT);
    if (!LEVELS.contains(normalized)) {
      throw new PlatformException(400, "Unsupported CVS level.");
    }
    return normalized;
  }
}
