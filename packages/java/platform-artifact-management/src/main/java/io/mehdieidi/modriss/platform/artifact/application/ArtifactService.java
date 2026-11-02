package io.mehdieidi.modriss.platform.artifact.application;

import io.mehdieidi.modriss.platform.artifact.domain.ArtifactIndexRecord;
import io.mehdieidi.modriss.platform.artifact.domain.ArtifactRecord;
import io.mehdieidi.modriss.platform.artifact.storage.ArtifactStorage;
import io.mehdieidi.modriss.platform.identity.domain.UserRecord;
import io.mehdieidi.modriss.platform.kernel.PlatformException;
import io.mehdieidi.modriss.platform.project.application.ProjectService;
import io.mehdieidi.modriss.platform.project.domain.ProjectRecord;
import io.mehdieidi.modriss.platform.storage.api.PlatformStore;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/** Manages generated artifact bundles and their editable file contents. */
public final class ArtifactService {

  /** File repository used for artifact records and indexes. */
  private final PlatformStore store;

  /** Project service used for access and editor checks. */
  private final ProjectService projectService;

  /**
   * Creates an artifact service.
   *
   * @param store backing JSON repository
   * @param projectService project access service
   */
  public ArtifactService(PlatformStore store, ProjectService projectService) {
    this.store = store;
    this.projectService = projectService;
  }

  /**
   * Lists artifact summaries for a project visible to the user.
   *
   * @param user requesting user
   * @param projectId project identifier
   * @return artifact summaries ordered by last update
   */
  public List<ArtifactRecord> list(UserRecord user, String projectId) {
    if (projectId == null || projectId.isBlank()) {
      return List.of();
    }
    projectService.get(user, projectId);
    return store.list(Path.of("projects", projectId, "artifacts"), ArtifactRecord.class).stream()
        .sorted(Comparator.comparing(ArtifactRecord::updatedAt).reversed())
        .toList();
  }

  /**
   * Lists artifact metadata and file paths without transferring file contents.
   *
   * @param user requesting user
   * @param projectId project identifier
   * @return accessible artifact summaries ordered by last update
   */
  public List<ArtifactRecord> listSummaries(UserRecord user, String projectId) {
    if (projectId == null || projectId.isBlank()) {
      return List.of();
    }
    projectService.get(user, projectId);
    if (store instanceof ArtifactStorage optimized) {
      return optimized.listSummaries(projectId).stream()
          .sorted(Comparator.comparing(ArtifactRecord::updatedAt).reversed())
          .toList();
    }
    return list(user, projectId).stream()
        .map(
            artifact ->
                new ArtifactRecord(
                    artifact.id(),
                    artifact.projectId(),
                    artifact.name(),
                    artifact.modelJson(),
                    artifact.files().keySet().stream()
                        .collect(
                            java.util.stream.Collectors.toMap(
                                path -> path,
                                path -> "",
                                (left, right) -> left,
                                LinkedHashMap::new)),
                    artifact.createdAt(),
                    artifact.updatedAt()))
        .sorted(Comparator.comparing(ArtifactRecord::updatedAt).reversed())
        .toList();
  }

  /**
   * Loads an artifact after verifying project access.
   *
   * @param user requesting user
   * @param id artifact identifier
   * @return artifact record
   */
  public ArtifactRecord get(UserRecord user, String id) {
    ArtifactRecord artifact = find(id);
    projectService.get(user, artifact.projectId());
    return repairLegacyMetadata(artifact);
  }

  /**
   * Creates an artifact bundle under a project.
   *
   * @param user requesting user
   * @param projectId project identifier
   * @param name artifact display name
   * @param files artifact files keyed by path
   * @return created artifact
   */
  public ArtifactRecord create(
      UserRecord user, String projectId, String name, Map<String, String> files) {
    return create(user, projectId, name, null, files);
  }

  /**
   * Creates an artifact bundle under a project with caller-provided metadata.
   *
   * @param user requesting user
   * @param projectId project identifier
   * @param name artifact display name
   * @param metadata additional artifact metadata, or {@code null}
   * @param files artifact files keyed by path
   * @return created artifact
   */
  public ArtifactRecord create(
      UserRecord user,
      String projectId,
      String name,
      ObjectNode metadata,
      Map<String, String> files) {
    ProjectRecord project = projectService.get(user, projectId);
    projectService.requireEditor(project, user.id());
    Instant now = Instant.now();
    String id = UUID.randomUUID().toString();
    Map<String, String> normalizedFiles = normalizeFiles(files);
    ObjectNode modelJson =
        metadata == null ? store.objectMapper().createObjectNode() : metadata.deepCopy();
    modelJson.put("name", name);
    modelJson.put("fileCount", normalizedFiles.size());
    modelJson.set("directories", directoryNode(modelJson, normalizedFiles.keySet()));
    ArtifactRecord artifact =
        new ArtifactRecord(id, projectId, name, modelJson, normalizedFiles, now, now);
    store.write(artifactPath(projectId, id), artifact);
    writeArtifactIndex(artifact);
    return artifact;
  }

  /**
   * Reads one file from an artifact bundle.
   *
   * @param user requesting user
   * @param artifactId artifact identifier
   * @param path artifact-relative file path
   * @return file content
   */
  public String readFile(UserRecord user, String artifactId, String path) {
    String normalized = normalizePath(path);
    if (store instanceof ArtifactStorage optimized) {
      var index = store.read(artifactIndexPath(artifactId), ArtifactIndexRecord.class);
      if (index.isEmpty()) {
        ArtifactRecord artifact = get(user, artifactId);
        if (!artifact.files().containsKey(normalized)) {
          throw new PlatformException(404, "Artifact file not found.");
        }
        return artifact.files().get(normalized);
      }
      projectService.get(user, index.get().projectId());
      return optimized
          .readFile(artifactId, normalized)
          .orElseThrow(() -> new PlatformException(404, "Artifact file not found."));
    }
    ArtifactRecord artifact = get(user, artifactId);
    if (!artifact.files().containsKey(normalized)) {
      throw new PlatformException(404, "Artifact file not found.");
    }
    return artifact.files().get(normalized);
  }

  /**
   * Replaces or adds one file in an artifact bundle.
   *
   * @param user requesting user
   * @param artifactId artifact identifier
   * @param path artifact-relative file path
   * @param content replacement content
   * @return updated artifact
   */
  public ArtifactRecord updateFile(
      UserRecord user, String artifactId, String path, String content) {
    ArtifactRecord artifact = get(user, artifactId);
    ProjectRecord project = projectService.get(user, artifact.projectId());
    projectService.requireEditor(project, user.id());
    String normalizedPath = normalizePath(path);
    requireGenerationManifestUnchanged(artifact, normalizedPath, content);
    Map<String, String> files = new LinkedHashMap<>(artifact.files());
    if (!files.containsKey(normalizedPath)) {
      requireExtensionFilePath(artifact, normalizedPath);
    }
    files.put(normalizedPath, content == null ? "" : content);
    ensureNoPathConflicts(files.keySet(), directoryPaths(artifact.modelJson()));
    ObjectNode modelJson =
        artifact.modelJson() != null && artifact.modelJson().isObject()
            ? ((ObjectNode) artifact.modelJson()).deepCopy()
            : store.objectMapper().createObjectNode();
    modelJson.remove("files");
    modelJson.put("name", artifact.name());
    modelJson.put("fileCount", files.size());
    modelJson.set("directories", directoryNode(modelJson, files.keySet()));
    ArtifactRecord updated =
        new ArtifactRecord(
            artifact.id(),
            artifact.projectId(),
            artifact.name(),
            modelJson,
            files,
            artifact.createdAt(),
            Instant.now());
    store.write(artifactPath(artifact.projectId(), artifact.id()), updated);
    writeArtifactIndex(updated);
    return updated;
  }

  /** Creates a user-owned file without replacing an existing artifact file. */
  public ArtifactRecord createFile(
      UserRecord user, String artifactId, String path, String content) {
    ArtifactRecord artifact = get(user, artifactId);
    String normalizedPath = normalizePath(path);
    if (artifact.files().containsKey(normalizedPath)) {
      throw new PlatformException(409, "A file already exists at that path.");
    }
    return updateFile(user, artifactId, normalizedPath, content);
  }

  /** Replaces all source files with a developer-refined project after checking edit access. */
  public ArtifactRecord replaceFiles(
      UserRecord user, String artifactId, Map<String, String> files) {
    ArtifactRecord artifact = get(user, artifactId);
    projectService.requireEditor(projectService.get(user, artifact.projectId()), user.id());
    Map<String, String> normalized = normalizeFiles(files);
    if (normalized.isEmpty())
      throw new PlatformException(400, "The refined project has no source files.");
    ObjectNode metadata =
        artifact.modelJson() == null
            ? store.objectMapper().createObjectNode()
            : ((ObjectNode) artifact.modelJson()).deepCopy();
    metadata.put("fileCount", normalized.size());
    metadata.put("refinedProjectImportedAt", Instant.now().toString());
    metadata.set("directories", directoryNode(metadata, normalized.keySet()));
    ArtifactRecord updated =
        new ArtifactRecord(
            artifact.id(),
            artifact.projectId(),
            artifact.name(),
            metadata,
            normalized,
            artifact.createdAt(),
            Instant.now());
    store.write(artifactPath(artifact.projectId(), artifact.id()), updated);
    writeArtifactIndex(updated);
    return updated;
  }

  /** Adds an empty directory at the project root or beneath an approved extension directory. */
  public ArtifactRecord createDirectory(UserRecord user, String artifactId, String path) {
    ArtifactRecord artifact = get(user, artifactId);
    ProjectRecord project = projectService.get(user, artifact.projectId());
    projectService.requireEditor(project, user.id());
    String normalized = normalizePath(path);
    Set<String> directories = directoryPaths(artifact.modelJson());
    if (artifact.files().containsKey(normalized) || directories.contains(normalized)) {
      throw new PlatformException(409, "A file or folder already exists at that path.");
    }
    requireExtensionDirectoryPath(artifact, normalized);
    addDirectoryAndParents(directories, normalized);
    ensureNoPathConflicts(artifact.files().keySet(), directories);
    ObjectNode metadata = metadataCopy(artifact);
    metadata.set("directories", directoryNode(directories));
    ArtifactRecord updated =
        new ArtifactRecord(
            artifact.id(),
            artifact.projectId(),
            artifact.name(),
            metadata,
            artifact.files(),
            artifact.createdAt(),
            Instant.now());
    store.write(artifactPath(artifact.projectId(), artifact.id()), updated);
    writeArtifactIndex(updated);
    return updated;
  }

  /** Renames a user-owned file or folder while keeping it at the same extension point. */
  public ArtifactRecord renameEntry(
      UserRecord user, String artifactId, String sourcePath, String newName) {
    ArtifactRecord artifact = get(user, artifactId);
    ProjectRecord project = projectService.get(user, artifact.projectId());
    projectService.requireEditor(project, user.id());
    String source = normalizePath(sourcePath);
    if (newName == null || newName.isBlank() || newName.matches(".*[/\\\\:].*")) {
      throw new PlatformException(400, "Enter a valid single file or folder name.");
    }
    String name = normalizePath(newName);
    Set<String> directories =
        normalizeDirectories(directoryPaths(artifact.modelJson()), artifact.files().keySet());
    boolean isFile = artifact.files().containsKey(source);
    boolean isDirectory = directories.contains(source);
    if (!isFile && !isDirectory) {
      throw new PlatformException(404, "Artifact file or folder not found.");
    }

    String parent = parentPath(source);
    String destination = parent.isEmpty() ? name : parent + "/" + name;
    if (source.equals(destination)) return artifact;

    Set<String> generatedPaths = generationRulePaths(artifact);
    if (generatedPaths.isEmpty()) generatedPaths.addAll(artifact.files().keySet());
    boolean ownsGeneratedOutput =
        isFile
            ? generatedPaths.contains(source)
            : generatedPaths.stream()
                .anyMatch(path -> path.equals(source) || path.startsWith(source + "/"));
    if (ownsGeneratedOutput) {
      throw new PlatformException(400, "Generator-owned files or folders cannot be renamed.");
    }

    Map<String, String> files = new LinkedHashMap<>(artifact.files());
    if (isFile) {
      if (files.containsKey(destination) || directories.contains(destination)) {
        throw new PlatformException(409, "A file or folder already exists at that path.");
      }
      requireExtensionFilePath(artifact, destination);
      String content = files.remove(source);
      files.put(destination, content);
    } else {
      if (files.containsKey(destination) || directories.contains(destination)) {
        throw new PlatformException(409, "A file or folder already exists at that path.");
      }
      requireExtensionDirectoryPath(artifact, destination);
      String prefix = source + "/";
      Map<String, String> movedFiles = new LinkedHashMap<>();
      files
          .entrySet()
          .removeIf(
              entry -> {
                if (!entry.getKey().startsWith(prefix)) return false;
                movedFiles.put(
                    destination + entry.getKey().substring(source.length()), entry.getValue());
                return true;
              });
      files.putAll(movedFiles);
      Set<String> renamedDirectories = new LinkedHashSet<>();
      for (String directory : directories) {
        if (directory.equals(source) || directory.startsWith(prefix)) {
          renamedDirectories.add(destination + directory.substring(source.length()));
        } else {
          renamedDirectories.add(directory);
        }
      }
      directories = renamedDirectories;
    }

    ensureNoPathConflicts(files.keySet(), directories);
    ObjectNode metadata = metadataCopy(artifact);
    metadata.put("fileCount", files.size());
    metadata.set("directories", directoryNode(directories));
    ArtifactRecord updated =
        new ArtifactRecord(
            artifact.id(),
            artifact.projectId(),
            artifact.name(),
            metadata,
            files,
            artifact.createdAt(),
            Instant.now());
    store.write(artifactPath(artifact.projectId(), artifact.id()), updated);
    writeArtifactIndex(updated);
    return updated;
  }

  /** Replaces an artifact's files with a structurally valid local project archive. */
  public ArtifactRecord importProjectArchive(
      UserRecord user,
      String artifactId,
      Map<String, String> incomingFiles,
      Set<String> incomingDirectories) {
    ArtifactRecord artifact = get(user, artifactId);
    ProjectRecord project = projectService.get(user, artifact.projectId());
    projectService.requireEditor(project, user.id());

    Map<String, String> files = normalizeFiles(incomingFiles);
    Set<String> directories = normalizeDirectories(incomingDirectories, files.keySet());
    Set<String> generatedPaths = generationRulePaths(artifact);
    if (generatedPaths.isEmpty()) {
      generatedPaths.addAll(artifact.files().keySet());
    }
    if (!containsAllPaths(files.keySet(), generatedPaths)) {
      ArchiveContents unwrapped = unwrapSingleRoot(files, directories, generatedPaths);
      if (unwrapped == null) {
        Set<String> missing = new LinkedHashSet<>(generatedPaths);
        missing.removeAll(files.keySet());
        throw new PlatformException(
            400,
            "Artifact ZIP is missing generated file(s): "
                + missing.stream().limit(5).collect(java.util.stream.Collectors.joining(", ")));
      }
      files = unwrapped.files();
      directories = unwrapped.directories();
    }

    Set<String> generatedDirectories = directoryAncestors(generatedPaths);
    Set<String> existingDirectories = directoryPaths(artifact.modelJson());
    Set<String> extensionDirectories = new LinkedHashSet<>(generatedDirectories);
    extensionDirectories.addAll(existingDirectories);
    directories = normalizeDirectories(directories, files.keySet());
    for (String manifestPath : generatedPaths) {
      if (isGenerationManifest(manifestPath)
          && !java.util.Objects.equals(
              artifact.files().get(manifestPath), files.get(manifestPath))) {
        throw new PlatformException(
            400, "Artifact ZIP cannot modify the generator trace manifest.");
      }
    }
    for (String directory :
        directories.stream().sorted(java.util.Comparator.comparingInt(String::length)).toList()) {
      if (existingDirectories.contains(directory) || generatedDirectories.contains(directory)) {
        continue;
      }
      String parent = parentPath(directory);
      if (!parent.isEmpty() && !isWithin(parent, extensionDirectories)) {
        throw new PlatformException(
            400, "Added folder is outside generator-approved extension directories: " + directory);
      }
      extensionDirectories.add(directory);
    }
    for (String path : files.keySet()) {
      if (generatedPaths.contains(path) || artifact.files().containsKey(path)) continue;
      String parent = parentPath(path);
      if (!parent.isEmpty() && !isWithin(parent, extensionDirectories)) {
        throw new PlatformException(
            400, "Added file is outside generator-approved extension directories: " + path);
      }
    }
    ensureNoPathConflicts(files.keySet(), directories);

    ObjectNode metadata = metadataCopy(artifact);
    metadata.put("fileCount", files.size());
    metadata.put("projectArchiveImportedAt", Instant.now().toString());
    metadata.set("directories", directoryNode(directories));
    ArtifactRecord updated =
        new ArtifactRecord(
            artifact.id(),
            artifact.projectId(),
            artifact.name(),
            metadata,
            files,
            artifact.createdAt(),
            Instant.now());
    store.write(artifactPath(artifact.projectId(), artifact.id()), updated);
    writeArtifactIndex(updated);
    return updated;
  }

  /**
   * Packages an artifact bundle as a UTF-8 zip archive.
   *
   * @param user requesting user
   * @param artifactId artifact identifier
   * @return zip archive bytes
   */
  public byte[] zip(UserRecord user, String artifactId) {
    ArtifactRecord artifact = get(user, artifactId);
    try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ZipOutputStream zip = new ZipOutputStream(bytes, StandardCharsets.UTF_8)) {
      for (String directory : directoryPaths(artifact.modelJson()).stream().sorted().toList()) {
        zip.putNextEntry(new ZipEntry(normalizePath(directory) + "/"));
        zip.closeEntry();
      }
      if (store instanceof ArtifactStorage optimized) {
        for (ArtifactStorage.ArtifactFile entry : optimized.listFiles(artifactId)) {
          zip.putNextEntry(new ZipEntry(normalizePath(entry.path())));
          zip.write(
              String.valueOf(entry.content() == null ? "" : entry.content())
                  .getBytes(StandardCharsets.UTF_8));
          zip.closeEntry();
        }
      } else {
        for (Map.Entry<String, String> entry : artifact.files().entrySet()) {
          zip.putNextEntry(new ZipEntry(normalizePath(entry.getKey())));
          zip.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
          zip.closeEntry();
        }
      }
      zip.finish();
      return bytes.toByteArray();
    } catch (Exception ex) {
      throw new PlatformException(500, "Could not package artifact.");
    }
  }

  /**
   * Finds an artifact by id using the global index with legacy fallback.
   *
   * @param id artifact identifier
   * @return artifact record
   */
  public ArtifactRecord find(String id) {
    var index = store.read(artifactIndexPath(id), ArtifactIndexRecord.class);
    if (index.isPresent()) {
      return store.require(
          artifactPath(index.get().projectId(), id), ArtifactRecord.class, "Artifact not found.");
    }
    return findLegacyAndIndex(id);
  }

  /**
   * Converts artifact file maps to JSON nodes for API responses.
   *
   * @param files artifact file map
   * @return JSON representation of the files map
   */
  public JsonNode filesNode(Map<String, String> files) {
    return store.objectMapper().valueToTree(files);
  }

  /**
   * Returns the repository path for an artifact record.
   *
   * @param projectId project identifier
   * @param id artifact identifier
   * @return repository-relative path
   */
  private Path artifactPath(String projectId, String id) {
    return Path.of("projects", projectId, "artifacts", id + ".json");
  }

  /**
   * Returns the repository path for an artifact index record.
   *
   * @param id artifact identifier
   * @return repository-relative path
   */
  private Path artifactIndexPath(String id) {
    return Path.of("indexes", "artifacts", id + ".json");
  }

  /**
   * Writes the global artifact index entry.
   *
   * @param artifact artifact to index
   */
  private void writeArtifactIndex(ArtifactRecord artifact) {
    store.write(
        artifactIndexPath(artifact.id()),
        new ArtifactIndexRecord(artifact.id(), artifact.projectId()));
  }

  /**
   * Searches legacy per-project artifact locations and writes an index entry after a match is
   * found.
   *
   * @param id artifact identifier
   * @return artifact record
   */
  private ArtifactRecord findLegacyAndIndex(String id) {
    try {
      ArtifactRecord artifact =
          store.list(Path.of("projects"), ProjectRecord.class).stream()
              .map(
                  project ->
                      store
                          .read(
                              Path.of("projects", project.id(), "artifacts", id + ".json"),
                              ArtifactRecord.class)
                          .orElse(null))
              .filter(candidate -> candidate != null)
              .findFirst()
              .orElseThrow(() -> new PlatformException(404, "Artifact not found."));
      writeArtifactIndex(artifact);
      return artifact;
    } catch (PlatformException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new PlatformException(500, "Could not read artifact store.");
    }
  }

  /**
   * Migrates legacy artifact metadata that incorrectly stored files inside {@code modelJson}.
   *
   * @param artifact artifact record to inspect
   * @return original or repaired artifact
   */
  private ArtifactRecord repairLegacyMetadata(ArtifactRecord artifact) {
    if (!artifact.modelJson().path("files").isObject()) {
      return artifact;
    }
    ObjectNode modelJson = ((ObjectNode) artifact.modelJson()).deepCopy();
    modelJson.remove("files");
    modelJson.put("name", artifact.name());
    modelJson.put("fileCount", artifact.files().size());
    ArtifactRecord repaired =
        new ArtifactRecord(
            artifact.id(),
            artifact.projectId(),
            artifact.name(),
            modelJson,
            artifact.files(),
            artifact.createdAt(),
            artifact.updatedAt());
    store.write(artifactPath(artifact.projectId(), artifact.id()), repaired);
    writeArtifactIndex(repaired);
    return repaired;
  }

  /**
   * Normalizes and validates an artifact-relative path.
   *
   * @param path raw path
   * @return normalized path using forward slashes
   */
  private String normalizePath(String path) {
    String normalized = String.valueOf(path == null ? "" : path).replace('\\', '/');
    normalized = normalized.replaceAll("/+", "/");
    if (normalized.isBlank() || normalized.startsWith("/") || normalized.matches("^[A-Za-z]:.*")) {
      throw new PlatformException(400, "Invalid artifact file path.");
    }
    for (String segment : normalized.split("/")) {
      if (segment.isBlank() || segment.equals(".") || segment.equals("..")) {
        throw new PlatformException(400, "Invalid artifact file path.");
      }
    }
    return normalized;
  }

  private Map<String, String> normalizeFiles(Map<String, String> files) {
    Map<String, String> normalized = new LinkedHashMap<>();
    (files == null ? Map.<String, String>of() : files)
        .forEach(
            (path, content) -> {
              String normalizedPath = normalizePath(path);
              if (normalized.putIfAbsent(normalizedPath, content == null ? "" : content) != null) {
                throw new PlatformException(400, "Duplicate artifact file path: " + normalizedPath);
              }
            });
    return normalized;
  }

  private ObjectNode metadataCopy(ArtifactRecord artifact) {
    return artifact.modelJson() != null && artifact.modelJson().isObject()
        ? ((ObjectNode) artifact.modelJson()).deepCopy()
        : store.objectMapper().createObjectNode();
  }

  private ArrayNode directoryNode(ObjectNode metadata, Set<String> filePaths) {
    Set<String> directories = directoryPaths(metadata);
    directories.addAll(directoryAncestors(filePaths));
    return directoryNode(directories);
  }

  private ArrayNode directoryNode(Set<String> directories) {
    ArrayNode node = store.objectMapper().createArrayNode();
    directories.stream().sorted().forEach(node::add);
    return node;
  }

  private Set<String> directoryPaths(JsonNode metadata) {
    Set<String> directories = new LinkedHashSet<>();
    JsonNode paths = metadata == null ? null : metadata.path("directories");
    if (paths != null && paths.isArray()) {
      for (JsonNode path : paths) {
        if (path.isTextual() && !path.asText().isBlank()) {
          directories.add(normalizePath(path.asText()));
        }
      }
    }
    return directories;
  }

  private Set<String> normalizeDirectories(Set<String> directories, Set<String> filePaths) {
    Set<String> normalized = new LinkedHashSet<>();
    if (directories != null) {
      for (String path : directories) addDirectoryAndParents(normalized, normalizePath(path));
    }
    normalized.addAll(directoryAncestors(filePaths));
    return normalized;
  }

  private void addDirectoryAndParents(Set<String> directories, String path) {
    String current = path;
    while (!current.isEmpty()) {
      directories.add(current);
      current = parentPath(current);
    }
  }

  private Set<String> generationRulePaths(ArtifactRecord artifact) {
    Set<String> paths = new LinkedHashSet<>();
    JsonNode rules =
        artifact.modelJson() == null ? null : artifact.modelJson().path("generationRules");
    if (rules != null && rules.isObject()) {
      rules.properties().forEach(entry -> paths.add(normalizePath(entry.getKey())));
    }
    return paths;
  }

  private Set<String> directoryAncestors(Iterable<String> paths) {
    Set<String> directories = new LinkedHashSet<>();
    for (String path : paths) {
      String parent = parentPath(path);
      while (!parent.isEmpty()) {
        directories.add(parent);
        parent = parentPath(parent);
      }
    }
    return directories;
  }

  private boolean containsAllPaths(Set<String> paths, Set<String> required) {
    return paths.containsAll(required);
  }

  private ArchiveContents unwrapSingleRoot(
      Map<String, String> files, Set<String> directories, Set<String> requiredPaths) {
    if (files.isEmpty()) return null;
    String first = files.keySet().iterator().next();
    int slash = first.indexOf('/');
    if (slash <= 0) return null;
    String root = first.substring(0, slash);
    String prefix = root + "/";
    if (!files.keySet().stream().allMatch(path -> path.startsWith(prefix))) return null;
    if (!directories.stream().allMatch(path -> path.equals(root) || path.startsWith(prefix))) {
      return null;
    }
    Map<String, String> unwrappedFiles = new LinkedHashMap<>();
    files.forEach((path, content) -> unwrappedFiles.put(path.substring(prefix.length()), content));
    if (!unwrappedFiles.keySet().containsAll(requiredPaths)) return null;
    Set<String> unwrappedDirectories = new LinkedHashSet<>();
    directories.stream()
        .filter(path -> path.startsWith(prefix))
        .forEach(path -> unwrappedDirectories.add(path.substring(prefix.length())));
    return new ArchiveContents(
        unwrappedFiles, normalizeDirectories(unwrappedDirectories, unwrappedFiles.keySet()));
  }

  private void requireExtensionFilePath(ArtifactRecord artifact, String path) {
    String parent = parentPath(path);
    if (parent.isEmpty()) return;
    Set<String> allowed = directoryAncestors(generationRulePaths(artifact));
    allowed.addAll(directoryPaths(artifact.modelJson()));
    if (!isWithin(parent, allowed)) {
      throw new PlatformException(
          400, "File must be created at the project root or inside a generator-approved folder.");
    }
  }

  private void requireGenerationManifestUnchanged(
      ArtifactRecord artifact, String path, String content) {
    if (isGenerationManifest(path)
        && artifact.files().containsKey(path)
        && !java.util.Objects.equals(artifact.files().get(path), content == null ? "" : content)) {
      throw new PlatformException(400, "The generator trace manifest is read-only.");
    }
  }

  private boolean isGenerationManifest(String path) {
    return path.equals("generated/trace/artifact-trace.json");
  }

  private void requireExtensionDirectoryPath(ArtifactRecord artifact, String path) {
    if (parentPath(path).isEmpty()) return;
    Set<String> allowed = directoryAncestors(generationRulePaths(artifact));
    allowed.addAll(directoryPaths(artifact.modelJson()));
    if (!isWithin(path, allowed)) {
      throw new PlatformException(
          400,
          "Folder must be created at the project root or inside a generator-approved project"
              + " folder.");
    }
  }

  private boolean isWithin(String path, Set<String> allowedDirectories) {
    return allowedDirectories.stream()
        .anyMatch(directory -> path.equals(directory) || path.startsWith(directory + "/"));
  }

  private void ensureNoPathConflicts(Set<String> files, Set<String> directories) {
    for (String file : files) {
      if (directories.contains(file)) {
        throw new PlatformException(400, "A path cannot be both a file and a folder: " + file);
      }
      String parent = parentPath(file);
      while (!parent.isEmpty()) {
        if (files.contains(parent)) {
          throw new PlatformException(400, "A file cannot contain another project path: " + parent);
        }
        parent = parentPath(parent);
      }
    }
  }

  private String parentPath(String path) {
    int slash = path.lastIndexOf('/');
    return slash < 0 ? "" : path.substring(0, slash);
  }

  private record ArchiveContents(Map<String, String> files, Set<String> directories) {}
}
