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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class JsonFileStore {

    private static final Logger log = LoggerFactory.getLogger(JsonFileStore.class);
    private final ObjectMapper objectMapper;
    private final Path root;

    public JsonFileStore(Path root) {
        this.root = root.toAbsolutePath().normalize();
        JsonFactory jsonFactory = JsonFactory.builder()
                .streamReadConstraints(StreamReadConstraints.builder()
                        .maxStringLength(128 * 1024 * 1024)
                        .build())
                .build();
        this.objectMapper = new ObjectMapper(jsonFactory)
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .enable(SerializationFeature.INDENT_OUTPUT);
    }

    public ObjectMapper objectMapper() {
        return objectMapper;
    }

    public Path root() {
        return root;
    }

    public void initialize() {
        try {
            Files.createDirectories(root);
            Files.createDirectories(root.resolve("users"));
            Files.createDirectories(root.resolve("sessions"));
            Files.createDirectories(root.resolve("projects"));
            Files.createDirectories(root.resolve("indexes"));
            Files.createDirectories(root.resolve("model-imports"));
            log.info("Using Modless file repository at {}", root);
        } catch (IOException ex) {
            throw new PlatformException(500, "Could not initialize repository directory.");
        }
    }

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

    public <T> T require(Path path, Class<T> type, String message) {
        return read(path, type).orElseThrow(() -> new PlatformException(404, message));
    }

    public void write(Path path, Object value) {
        Path resolved = resolve(path);
        writeJsonResolved(resolved, value);
    }

    public void writeBytesAtomically(Path path, byte[] bytes) {
        Path resolved = resolve(path);
        writeBytesResolved(resolved, bytes == null ? new byte[0] : bytes);
    }

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

    public void deleteIfExists(Path path) {
        try {
            Files.deleteIfExists(resolve(path));
        } catch (IOException ex) {
            throw new PlatformException(500, "Could not delete stored data.");
        }
    }

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

    private void moveReplacing(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ex) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public Path resolve(Path path) {
        Path resolved = root.resolve(path).normalize();
        if (!resolved.startsWith(root)) {
            throw new PlatformException(400, "Invalid repository path.");
        }
        return resolved;
    }
}
