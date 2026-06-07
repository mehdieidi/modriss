package io.mehdieidi.modless.platform.core.repository;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.mehdieidi.modless.platform.core.PlatformException;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.UUID;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Small JSON-backed repository abstraction that constrains all paths to a configured root and
 * writes data atomically where possible.
 */
public final class JsonFileStore implements PlatformStore {

    /**
     * Current on-disk schema version for the JSON adapter.
     */
    public static final int SCHEMA_VERSION = 1;

    /**
     * Logger for repository read/write failures.
     */
    private static final Logger log = LoggerFactory.getLogger(JsonFileStore.class);
    /**
     * Mapper configured for Java time values and large model payloads.
     */
    private final ObjectMapper objectMapper;
    /**
     * Normalized repository root directory.
     */
    private final Path root;

    /**
     * Creates a JSON file store rooted at the supplied directory.
     *
     * @param root repository root directory
     */
    public JsonFileStore(Path root) {
        this.root = root.toAbsolutePath().normalize();
        JsonFactory jsonFactory = JsonFactory.builder()
                .streamReadConstraints(StreamReadConstraints.builder()
                        .maxStringLength(128 * 1024 * 1024)
                        .build())
                .build();
        this.objectMapper = new ObjectMapper(jsonFactory)
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    /**
     * Returns the shared object mapper used by repository services.
     *
     * @return configured object mapper
     */
    public ObjectMapper objectMapper() {
        return objectMapper;
    }

    /**
     * Returns the normalized repository root.
     *
     * @return repository root
     */
    public Path root() {
        return root;
    }

    /**
     * Creates the directory structure expected by platform services.
     */
    public void initialize() {
        try {
            Files.createDirectories(root);
            Files.createDirectories(root.resolve("users"));
            Files.createDirectories(root.resolve("sessions"));
            Files.createDirectories(root.resolve("projects"));
            Files.createDirectories(root.resolve("indexes"));
            Files.createDirectories(root.resolve("model-imports"));
            writeSchemaMarker();
            log.info("Using Modless file repository at {}", root);
        } catch (IOException ex) {
            throw new PlatformException(500, "Could not initialize repository directory.");
        }
    }

    /**
     * Writes the adapter-owned schema marker. Existing repositories are treated as replaceable
     * local state, so incompatible future versions can fail fast here.
     */
    private void writeSchemaMarker() throws IOException {
        Path marker = root.resolve("modless-store.json");
        if (Files.isRegularFile(marker)) {
            StorageSchema schema = objectMapper.readValue(marker.toFile(), StorageSchema.class);
            if (schema.version() != SCHEMA_VERSION) {
                throw new PlatformException(500, "Unsupported JSON storage schema version.");
            }
            return;
        }
        writeJsonResolved(marker, new StorageSchema(SCHEMA_VERSION, "json-file"));
    }

    /**
     * Reads a JSON file when it exists.
     *
     * @param path repository-relative path
     * @param type target value type
     * @param <T>  target value type
     * @return optional decoded value
     */
    public <T> Optional<T> read(Path path, Class<T> type) {
        Path resolved = resolve(path);
        if (!Files.isRegularFile(resolved)) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(resolved.toFile(), type));
        } catch (IOException ex) {
            log.error("Failed to read JSON file {}", resolved, ex);
            throw new PlatformException(500, "Could not read stored data.");
        }
    }

    /**
     * Reads a JSON file or throws a 404 platform exception.
     *
     * @param path    repository-relative path
     * @param type    target value type
     * @param message message used when the file is absent
     * @param <T>     target value type
     * @return decoded value
     */
    public <T> T require(Path path, Class<T> type, String message) {
        return read(path, type).orElseThrow(() -> new PlatformException(404, message));
    }

    /**
     * Writes a value as JSON using an atomic replacement when supported.
     *
     * @param path  repository-relative path
     * @param value value to serialize
     */
    public void write(Path path, Object value) {
        Path resolved = resolve(path);
        writeJsonResolved(resolved, value);
    }

    /**
     * Writes raw bytes using an atomic replacement when supported.
     *
     * @param path  repository-relative path
     * @param bytes bytes to write; {@code null} writes an empty file
     */
    public void writeBytesAtomically(Path path, byte[] bytes) {
        Path resolved = resolve(path);
        writeBytesResolved(resolved, bytes == null ? new byte[0] : bytes);
    }

    /**
     * Moves a repository file to another repository path.
     *
     * @param source repository-relative source path
     * @param target repository-relative target path
     */
    public void moveAtomically(Path source, Path target) {
        Path resolvedSource = resolve(source);
        Path resolvedTarget = resolve(target);
        try {
            Files.createDirectories(resolvedTarget.getParent());
            moveReplacing(resolvedSource, resolvedTarget);
        } catch (IOException ex) {
            log.error("Failed to move repository file {} to {}", resolvedSource, resolvedTarget,
                    ex);
            throw new PlatformException(500, "Could not persist data.");
        }
    }

    /**
     * Deletes a repository file when present.
     *
     * @param path repository-relative path
     */
    public void deleteIfExists(Path path) {
        try {
            Files.deleteIfExists(resolve(path));
        } catch (IOException ex) {
            throw new PlatformException(500, "Could not delete stored data.");
        }
    }

    @Override
    public <T> List<T> list(Path directory, Class<T> type) {
        Path resolved = resolve(directory);
        if (!Files.isDirectory(resolved)) {
            return List.of();
        }
        try (Stream<Path> files = Files.walk(resolved)) {
            return files.filter(path -> path.getFileName().toString().endsWith(".json"))
                    .map(path -> read(root.relativize(path), type).orElse(null))
                    .filter(value -> value != null)
                    .toList();
        } catch (IOException ex) {
            log.error("Failed to list JSON directory {}", resolved, ex);
            throw new PlatformException(500, "Could not list stored data.");
        }
    }

    @Override
    public void deleteTree(Path directory) {
        Path resolved = resolve(directory);
        if (!Files.exists(resolved)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(resolved)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ex) {
                    throw new PlatformException(500, "Could not delete stored data.");
                }
            });
        } catch (PlatformException ex) {
            throw ex;
        } catch (IOException ex) {
            log.error("Failed to delete repository directory {}", resolved, ex);
            throw new PlatformException(500, "Could not delete stored data.");
        }
    }

    /**
     * Writes JSON to an already-resolved path.
     *
     * @param resolved absolute path under the repository root
     * @param value    value to serialize
     */
    private void writeJsonResolved(Path resolved, Object value) {
        try {
            Files.createDirectories(resolved.getParent());
            Path temp = resolved.resolveSibling(
                    resolved.getFileName() + "." + UUID.randomUUID() + ".tmp");
            objectMapper.writeValue(temp.toFile(), value);
            Files.move(temp, resolved, StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException ex) {
            log.error("Failed to write JSON file {}", resolved, ex);
            throw new PlatformException(500, "Could not persist data.");
        }
    }

    /**
     * Writes raw bytes to an already-resolved path.
     *
     * @param resolved absolute path under the repository root
     * @param bytes    bytes to write
     */
    private void writeBytesResolved(Path resolved, byte[] bytes) {
        try {
            Files.createDirectories(resolved.getParent());
            Path temp = resolved.resolveSibling(
                    resolved.getFileName() + "." + UUID.randomUUID() + ".tmp");
            Files.write(temp, bytes);
            moveReplacing(temp, resolved);
        } catch (IOException ex) {
            log.error("Failed to write repository file {}", resolved, ex);
            throw new PlatformException(500, "Could not persist data.");
        }
    }

    /**
     * Moves a file with atomic replacement when the filesystem supports it.
     *
     * @param source resolved source path
     * @param target resolved target path
     * @throws IOException when the move fails
     */
    private void moveReplacing(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ex) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /**
     * Resolves a repository-relative path and rejects traversal outside the repository root.
     *
     * @param path repository-relative path
     * @return normalized absolute path under the repository root
     */
    public Path resolve(Path path) {
        Path resolved = root.resolve(path).normalize();
        if (!resolved.startsWith(root)) {
            throw new PlatformException(400, "Invalid repository path.");
        }
        return resolved;
    }

    private record StorageSchema(int version, String adapter) {
    }
}
