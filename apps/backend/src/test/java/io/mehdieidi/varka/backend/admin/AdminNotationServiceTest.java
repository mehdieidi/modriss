package io.mehdieidi.varka.backend.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.mehdieidi.varka.platform.modeling.runtime.MdeRuntimePaths;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;

class AdminNotationServiceTest {

  @TempDir Path tempDir;

  @Test
  void activatesTrackedFileAndStoresPreviousVersionsInBackupDirectory() throws Exception {
    MdeRuntimePaths paths = mock(MdeRuntimePaths.class);
    when(paths.notationRoot()).thenReturn(tempDir);
    AdminNotationService service = new AdminNotationService(paths, new ObjectMapper(), null);
    Path activeFile = tempDir.resolve("cim.cvs.json");
    Files.writeString(activeFile, "previous");

    Map<String, Object> document = validDocument();
    AdminNotationService.ActivationResult activation = service.save("cim", document);

    Path backupFile = tempDir.resolve("backup/cim.cvs.old1.json");
    assertTrue(Files.isRegularFile(activeFile));
    assertTrue(Files.isRegularFile(backupFile));
    assertEquals("previous", Files.readString(backupFile));
    assertFalse(Files.exists(tempDir.resolve("cim.cvs.old1.json")));
    assertEquals("cim.cvs.json", activation.activeFile());
    assertEquals("backup/cim.cvs.old1.json", activation.backupFile());

    service.save("cim", document);

    assertTrue(Files.isRegularFile(tempDir.resolve("backup/cim.cvs.old2.json")));
  }

  private Map<String, Object> validDocument() {
    return Map.of(
        "cvsVersion",
        2,
        "metamodelRef",
        Map.of("level", "cim"),
        "elements",
        List.of(Map.of("type", "Model")),
        "views",
        List.of(Map.of("id", "default")),
        "containers",
        List.of(),
        "canvasPolicy",
        Map.of("layout", "freeform"));
  }
}
