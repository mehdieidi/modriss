package io.mehdieidi.varka.backend.upload;

import io.mehdieidi.varka.platform.kernel.PlatformException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import tools.jackson.databind.ObjectMapper;

/** Local filesystem implementation of {@link UploadStorage}. */
public class LocalUploadStorage implements UploadStorage {

  private static final String METADATA_FILE = "metadata.json";

  private final Path root;
  private final ObjectMapper mapper;

  /**
   * Creates a local upload store.
   *
   * @param root upload root
   * @param mapper JSON mapper for metadata sidecars
   */
  public LocalUploadStorage(Path root, ObjectMapper mapper) {
    this.root = root.toAbsolutePath().normalize();
    this.mapper = mapper;
  }

  @Override
  public UploadedFileRecord store(
      UploadScope scope, String originalFileName, String contentType, byte[] content)
      throws IOException {
    String id = UUID.randomUUID().toString();
    String safeFileName = sanitizeFileName(originalFileName);
    Path uploadDirectory = uploadDirectory(scope, id);
    Files.createDirectories(uploadDirectory);
    UploadedFileRecord record =
        new UploadedFileRecord(
            id,
            scope.userId(),
            scope.projectId(),
            scope.level(),
            scope.sessionId(),
            safeFileName,
            safeFileName,
            contentType == null || contentType.isBlank()
                ? "application/octet-stream"
                : contentType.trim(),
            content.length,
            Instant.now());
    Files.write(
        uploadDirectory.resolve(safeFileName),
        content,
        StandardOpenOption.CREATE_NEW,
        StandardOpenOption.WRITE);
    mapper.writeValue(uploadDirectory.resolve(METADATA_FILE).toFile(), record);
    return record;
  }

  @Override
  public Optional<UploadedFileRecord> find(UploadScope scope, String id) throws IOException {
    String safeId = sanitizeId(id);
    Path metadata = uploadDirectory(scope, safeId).resolve(METADATA_FILE);
    if (!Files.isRegularFile(metadata)) {
      return Optional.empty();
    }
    UploadedFileRecord record = mapper.readValue(metadata.toFile(), UploadedFileRecord.class);
    if (!record.userId().equals(scope.userId())
        || !record.projectId().equals(scope.projectId())
        || record.level() != scope.level()
        || !record.sessionId().equals(scope.sessionId())) {
      return Optional.empty();
    }
    return Optional.of(record);
  }

  @Override
  public List<UploadedFileRecord> listRecent(UploadScope scope, int limit) throws IOException {
    if (limit <= 0) {
      return List.of();
    }
    Path directory = sessionDirectory(scope);
    if (!Files.isDirectory(directory)) {
      return List.of();
    }
    try (Stream<Path> paths = Files.list(directory)) {
      return paths
          .map(path -> path.resolve(METADATA_FILE))
          .filter(Files::isRegularFile)
          .map(path -> readMetadata(scope, path))
          .flatMap(Optional::stream)
          .sorted(Comparator.comparing(UploadedFileRecord::uploadedAt).reversed())
          .limit(limit)
          .toList();
    }
  }

  @Override
  public byte[] read(UploadedFileRecord record) throws IOException {
    UploadScope scope =
        new UploadScope(record.userId(), record.projectId(), record.level(), record.sessionId());
    Path content = uploadDirectory(scope, record.id()).resolve(record.storedFileName()).normalize();
    if (!content.startsWith(root) || !Files.isRegularFile(content)) {
      throw new PlatformException(404, "Uploaded file not found.");
    }
    return Files.readAllBytes(content);
  }

  private Path uploadDirectory(UploadScope scope, String id) {
    Path directory = sessionDirectory(scope).resolve(sanitizeId(id)).normalize();
    if (!directory.startsWith(root)) {
      throw new PlatformException(400, "Invalid upload scope.");
    }
    return directory;
  }

  private Path sessionDirectory(UploadScope scope) {
    Path directory =
        root.resolve(safeSegment(scope.userId()))
            .resolve(safeSegment(scope.projectId()))
            .resolve(scope.level().apiName())
            .resolve(safeSegment(scope.sessionId()))
            .normalize();
    if (!directory.startsWith(root)) {
      throw new PlatformException(400, "Invalid upload scope.");
    }
    return directory;
  }

  private Optional<UploadedFileRecord> readMetadata(UploadScope scope, Path metadata) {
    try {
      UploadedFileRecord record = mapper.readValue(metadata.toFile(), UploadedFileRecord.class);
      if (!record.userId().equals(scope.userId())
          || !record.projectId().equals(scope.projectId())
          || record.level() != scope.level()
          || !record.sessionId().equals(scope.sessionId())) {
        return Optional.empty();
      }
      return Optional.of(record);
    } catch (tools.jackson.core.JacksonException ex) {
      return Optional.empty();
    }
  }

  private String sanitizeId(String value) {
    try {
      return UUID.fromString(value == null ? "" : value.trim()).toString();
    } catch (IllegalArgumentException ex) {
      throw new PlatformException(400, "Invalid attachment id.");
    }
  }

  private String safeSegment(String value) {
    String sanitized =
        value == null
            ? ""
            : value.trim().replaceAll("[^A-Za-z0-9._-]+", "-").replaceAll("^-+|-+$", "");
    return sanitized.isBlank() ? "unknown" : sanitized;
  }

  private String sanitizeFileName(String value) {
    String fileName =
        value == null ? "attachment.txt" : Path.of(value).getFileName().toString().trim();
    String sanitized = fileName.replaceAll("[^A-Za-z0-9._ -]+", "-").replaceAll("^-+|-+$", "");
    if (sanitized.isBlank()) {
      return "attachment.txt";
    }
    return sanitized.length() <= 120 ? sanitized : sanitized.substring(sanitized.length() - 120);
  }
}
