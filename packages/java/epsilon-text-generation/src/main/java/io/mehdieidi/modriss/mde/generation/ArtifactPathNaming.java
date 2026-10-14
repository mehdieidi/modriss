package io.mehdieidi.modriss.mde.generation;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.emf.ecore.util.EcoreUtil;

/** Assigns short, portable local artifact names independently of AWS deployment names. */
public final class ArtifactPathNaming {

  private static final int MAX_STEM_LENGTH = 32;
  private static final Set<String> WINDOWS_RESERVED =
      Set.of(
          "con", "prn", "aux", "nul", "com1", "com2", "com3", "com4", "com5", "com6", "com7",
          "com8", "com9", "lpt1", "lpt2", "lpt3", "lpt4", "lpt5", "lpt6", "lpt7", "lpt8", "lpt9");

  private final Path outputRoot;
  private final Set<String> previousPaths;
  private final Map<String, Map<EObject, String>> namesByKind = new HashMap<>();
  private final Set<String> migratedPaths = new java.util.HashSet<>();
  private Map<String, String> previousPathsBySource;

  ArtifactPathNaming(Path outputRoot, Set<String> previousPaths) {
    this.outputRoot = outputRoot.toAbsolutePath().normalize();
    this.previousPaths = Set.copyOf(previousPaths);
  }

  /** Allocates names across a complete namespace so collisions do not depend on iteration order. */
  public String name(EObject element, String kind, Iterable<?> peers) {
    Map<EObject, String> names = namesByKind.get(kind);
    if (names == null) {
      names = new IdentityHashMap<>();
      Map<String, List<EObject>> groups = new java.util.TreeMap<>();
      for (Object peer : peers) {
        if (peer instanceof EObject object) {
          groups
              .computeIfAbsent(candidate(object, kind), ignored -> new java.util.ArrayList<>())
              .add(object);
        }
      }
      Set<String> used = new java.util.HashSet<>();
      // Reserve readable, unambiguous names before assigning any suffixed names.
      for (Map.Entry<String, List<EObject>> group : groups.entrySet()) {
        if (group.getValue().size() == 1 && group.getKey().length() <= MAX_STEM_LENGTH) {
          names.put(group.getValue().get(0), group.getKey());
          used.add(group.getKey());
        }
      }
      for (Map.Entry<String, List<EObject>> group : groups.entrySet()) {
        List<EObject> objects = group.getValue();
        objects.sort(java.util.Comparator.comparing(ArtifactPathNaming::identity));
        for (EObject object : objects) {
          if (names.containsKey(object)) continue;
          String seed = identity(object);
          String result = shortened(group.getKey(), seed);
          int index = 2;
          while (!used.add(result)) {
            result = shortened(group.getKey(), seed + ":" + index++);
          }
          names.put(object, result);
        }
      }
      namesByKind.put(kind, names);
    }
    String result = names.get(element);
    if (result == null) {
      throw new IllegalArgumentException("Artifact naming namespace does not contain its source.");
    }
    return result;
  }

  /** Copies a previously generated artifact to its new path before EGL merges protected regions. */
  public String preservePath(String path, String legacyPath, String source, String kind)
      throws IOException {
    if (previousPathsBySource == null) {
      previousPathsBySource = new HashMap<>();
      Path trace = outputRoot.resolve("generated/trace/artifact-trace.json");
      if (Files.isRegularFile(trace)) {
        var artifacts = new ObjectMapper().readTree(trace.toFile()).path("artifacts");
        for (var artifact : artifacts) {
          String key =
              artifact.path("artifactKind").asText()
                  + ":"
                  + artifact.path("sourceStableIds").path(0).asText();
          previousPathsBySource.merge(
              key,
              artifact.path("path").asText(),
              (first, second) -> first.equals(second) ? first : "");
        }
      }
    }
    String previous = previousPathsBySource.getOrDefault(kind + ":" + source, legacyPath);
    return preservePath(path, previous.isBlank() ? legacyPath : previous);
  }

  /** Copies tracked legacy paths, leaving files already present at the destination intact. */
  public String preservePath(String path, String legacyPath) throws IOException {
    if (path.equals(legacyPath) || !previousPaths.contains(legacyPath)) return path;
    Path destination = localPath(path);
    if (Files.exists(destination)) {
      if (!previousPaths.contains(path) && !migratedPaths.contains(path)) {
        throw new IOException("Cannot migrate generated artifact over an untracked file: " + path);
      }
      return path;
    }
    Path original = localPath(legacyPath);
    if (Files.isRegularFile(original)) {
      Files.createDirectories(destination.getParent());
      Files.copy(original, destination);
      migratedPaths.add(path);
    }
    return path;
  }

  private Path localPath(String path) {
    Path resolved = outputRoot.resolve(path).normalize();
    if (!resolved.startsWith(outputRoot) || resolved.equals(outputRoot)) {
      throw new IllegalArgumentException("Artifact path must remain inside the output directory.");
    }
    return resolved;
  }

  private static String candidate(EObject element, String kind) {
    String preferred = preferredName(element, kind);
    String legacy = slug(preferred, false);
    boolean generated = "true".equals(value(element, "generatedByTransformation"));
    String source = preferred;
    if ((generated || legacy.length() > MAX_STEM_LENGTH) && !value(element, "name").isBlank()) {
      source = value(element, "name");
    }
    String result = slug(source, generated || legacy.length() > MAX_STEM_LENGTH);
    if (generated || legacy.length() > MAX_STEM_LENGTH) {
      if (kind.equals("function")) {
        result = result.replaceAll("(?:-(?:lambda|function|command-handler|query-handler))+$", "");
      } else if (kind.equals("workflow")) {
        result = result.replaceAll("(?:-(?:state-machine|workflow))+$", "");
      } else if (kind.equals("document")) {
        result = result.replaceAll("(?:-(?:service-metadata|schema-document|schema))+$", "");
      } else if (kind.equals("stack")) {
        result = result.replaceAll("-stack$", "");
      }
    }
    if (result.isBlank() || result.equals("unnamed")) result = kind;
    if (WINDOWS_RESERVED.contains(result)) result = kind + "-" + result;
    return result;
  }

  private static String preferredName(EObject element, String kind) {
    if (kind.endsWith("-fixture") && !value(element, "id").isBlank()) {
      return value(element, "id");
    }
    String feature =
        switch (kind) {
          case "function" -> "functionName";
          case "api" -> "apiName";
          case "workflow" -> "stateMachineName";
          case "event" -> "ruleName";
          case "queue" -> "queueName";
          case "topic" -> "topicName";
          case "stack" -> "stackName";
          case "stage" -> "stageName";
          default -> "name";
        };
    for (String candidate : List.of(feature, "logicalId", "name", "id")) {
      String text = value(element, candidate);
      if (!text.isBlank()) return text;
    }
    return kind;
  }

  private static String slug(String text, boolean splitCamelCase) {
    if (splitCamelCase) {
      text =
          text.replaceAll("([A-Z]+)([A-Z][a-z])", "$1-$2").replaceAll("([a-z0-9])([A-Z])", "$1-$2");
    }
    return text.replaceAll("[^A-Za-z0-9]+", "-").replaceAll("^-+|-+$", "").toLowerCase(Locale.ROOT);
  }

  private static String shortened(String name, String identity) {
    String prefix =
        name.substring(0, Math.min(name.length(), MAX_STEM_LENGTH - 9)).replaceAll("-+$", "");
    try {
      String hash =
          HexFormat.of()
              .formatHex(
                  MessageDigest.getInstance("SHA-256")
                      .digest(identity.getBytes(StandardCharsets.UTF_8)));
      return prefix + "-" + hash.substring(0, 8);
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException("SHA-256 is required for artifact names.", ex);
    }
  }

  private static String identity(EObject element) {
    String source = "";
    for (String feature :
        List.of("traceId", "externalId", "sourceQualifiedName", "logicalId", "id")) {
      source = value(element, feature);
      if (!source.isBlank()) break;
    }
    if (source.isBlank()) source = EcoreUtil.getURI(element).fragment();
    EObject parent = element.eContainer();
    String owner = parent == null ? "" : value(parent, "id");
    if (parent != null && owner.isBlank()) owner = value(parent, "name");
    return element.eClass().getName() + ":" + owner + ":" + source;
  }

  private static String value(EObject element, String name) {
    EStructuralFeature feature = element.eClass().getEStructuralFeature(name);
    Object value = feature == null ? null : element.eGet(feature);
    return value == null ? "" : value.toString().trim();
  }
}
