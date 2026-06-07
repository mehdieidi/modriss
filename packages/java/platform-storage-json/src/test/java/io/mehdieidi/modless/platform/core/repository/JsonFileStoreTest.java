package io.mehdieidi.modless.platform.core.repository;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.mehdieidi.modless.platform.core.PlatformException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JsonFileStoreTest {

    @TempDir
    private Path tempDir;

    @Test
    void initializeCreatesVersionOneRepositoryShape() throws Exception {
        JsonFileStore store = new JsonFileStore(tempDir.resolve("repo"));

        store.initialize();

        assertTrue(Files.isRegularFile(tempDir.resolve("repo/modless-store.json")));
        assertEquals(JsonFileStore.SCHEMA_VERSION, store.objectMapper()
                .readTree(tempDir.resolve("repo/modless-store.json").toFile())
                .path("version")
                .asInt());
        assertTrue(Files.isDirectory(tempDir.resolve("repo/users")));
        assertTrue(Files.isDirectory(tempDir.resolve("repo/sessions")));
        assertTrue(Files.isDirectory(tempDir.resolve("repo/projects")));
        assertTrue(Files.isDirectory(tempDir.resolve("repo/indexes")));
        assertTrue(Files.isDirectory(tempDir.resolve("repo/model-imports")));
    }

    @Test
    void rejectsPathTraversalOutsideRepositoryRoot() {
        JsonFileStore store = new JsonFileStore(tempDir.resolve("repo"));

        PlatformException exception = assertThrows(PlatformException.class,
                () -> store.resolve(Path.of("..", "outside.json")));

        assertEquals(400, exception.status());
    }

    @Test
    void roundTripsJsonRecordsAndDeletesStoredValues() {
        JsonFileStore store = new JsonFileStore(tempDir.resolve("repo"));
        store.initialize();
        StoredValue value = new StoredValue("model-1", 3);

        store.write(Path.of("projects", "project-1", "value.json"), value);

        assertEquals(value, store.require(Path.of("projects", "project-1", "value.json"),
                StoredValue.class, "missing"));

        store.deleteIfExists(Path.of("projects", "project-1", "value.json"));

        assertFalse(store.read(Path.of("projects", "project-1", "value.json"),
                StoredValue.class).isPresent());
    }

    @Test
    void listsJsonRecordsAndDeletesDirectoryTrees() {
        JsonFileStore store = new JsonFileStore(tempDir.resolve("repo"));
        store.initialize();
        store.write(Path.of("projects", "project-1", "records", "a.json"),
                new StoredValue("a", 1));
        store.write(Path.of("projects", "project-1", "records", "b.json"),
                new StoredValue("b", 2));
        store.writeBytesAtomically(Path.of("projects", "project-1", "records", "note.txt"),
                new byte[] {9});

        List<StoredValue> values = store.list(Path.of("projects", "project-1", "records"),
                StoredValue.class);

        assertEquals(List.of("a", "b"), values.stream().map(StoredValue::id).sorted().toList());

        store.deleteTree(Path.of("projects", "project-1"));

        assertFalse(Files.exists(store.resolve(Path.of("projects", "project-1"))));
    }

    @Test
    void writesRawBytesAtomically() throws Exception {
        JsonFileStore store = new JsonFileStore(tempDir.resolve("repo"));
        store.initialize();

        store.writeBytesAtomically(Path.of("projects", "project-1", "blob.bin"),
                new byte[] {1, 2, 3});

        assertArrayEquals(new byte[] {1, 2, 3},
                Files.readAllBytes(store.resolve(Path.of("projects", "project-1", "blob.bin"))));
    }

    private record StoredValue(String id, int revision) {
    }
}
