package io.mehdieidi.varka.backend.admin;

import io.mehdieidi.varka.platform.kernel.PlatformException;
import io.mehdieidi.varka.platform.modeling.runtime.MdeRuntimePaths;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/** File-backed administration service for Concrete Visual Syntax notation documents. */
@Service
public class AdminNotationService {

  private static final List<String> LEVELS = List.of("cim", "pim", "psm");

  private final MdeRuntimePaths paths;
  private final ObjectMapper mapper;

  public AdminNotationService(MdeRuntimePaths paths, ObjectMapper mapper) {
    this.paths = paths;
    this.mapper = mapper;
  }

  public Map<String, Object> load(String level) {
    Path file = notationFile(level);
    if (!Files.isRegularFile(file)) {
      throw new PlatformException(404, "CVS notation file was not found: " + file.getFileName());
    }
    try {
      Map<String, Object> doc =
          mapper.readValue(Files.readString(file), new TypeReference<>() {});
      validate(level, doc);
      return doc;
    } catch (PlatformException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new PlatformException(500, "Could not load CVS notation file.");
    }
  }

  public void save(String level, Map<String, Object> doc) {
    validate(level, doc);
    Path file = notationFile(level);
    try {
      Files.createDirectories(file.getParent());
      Files.writeString(file, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(doc) + "\n");
    } catch (Exception ex) {
      throw new PlatformException(500, "Could not save CVS notation file.");
    }
  }

  private Path notationFile(String level) {
    return paths.notationRoot().resolve(normalizeLevel(level) + ".cvs.json").normalize();
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
          "CVS metamodelRef.level mismatch: expected "
              + normalized
              + ", found "
              + configuredLevel);
    }
    if (!(doc.get("viewpoints") instanceof List<?> viewpoints) || viewpoints.isEmpty()) {
      throw new PlatformException(400, "CVS document must define at least one viewpoint.");
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
