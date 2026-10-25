package io.mehdieidi.modriss.backend.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.mehdieidi.modriss.platform.modeling.config.ModelingConfigService;
import io.mehdieidi.modriss.platform.modeling.runtime.MdeRuntimePaths;
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
  void exposesAndPersistsRelationshipContainerProfilesWithEcoreFeatureCatalog() throws Exception {
    MdeRuntimePaths paths = mock(MdeRuntimePaths.class);
    when(paths.notationRoot()).thenReturn(tempDir);
    ModelingConfigService modelingConfig = mock(ModelingConfigService.class);
    Map<String, Object> feature =
        Map.of(
            "feature",
            "sourceMultiplicity",
            "targetType",
            "Multiplicity",
            "types",
            List.of("Multiplicity"),
            "many",
            false);
    when(modelingConfig.config())
        .thenReturn(
            Map.of(
                "levels",
                Map.of(
                    "cim",
                    Map.of(
                        "containmentPalettes",
                            Map.of(
                                "DomainRelationship",
                                Map.of(
                                    "types",
                                    List.of("Multiplicity"),
                                    "canvas",
                                    List.of("Multiplicity"),
                                    "features",
                                    List.of(feature))),
                        "semanticEdgeObjectRules",
                            List.of(Map.of("eClass", "DomainRelationship"))))));
    ObjectMapper mapper = new ObjectMapper();
    Files.writeString(tempDir.resolve("cim.cvs.json"), mapper.writeValueAsString(validDocument()));
    AdminNotationService service = new AdminNotationService(paths, mapper, modelingConfig);

    Map<String, Object> loaded = service.load("cim");
    List<?> profiles = (List<?>) loaded.get("containers");
    Map<?, ?> relationship = (Map<?, ?>) profiles.get(0);
    assertEquals("DomainRelationship", relationship.get("elementType"));
    assertEquals(List.of("Multiplicity"), relationship.get("palette"));
    assertEquals(List.of("Multiplicity"), relationship.get("canvas"));
    Map<?, ?> features = (Map<?, ?>) loaded.get("metamodelContainmentFeatures");
    assertEquals(List.of(feature), features.get("DomainRelationship"));

    service.save("cim", loaded);
    String persisted = Files.readString(tempDir.resolve("cim.cvs.json"));
    assertTrue(persisted.contains("DomainRelationship"));
    assertFalse(persisted.contains("metamodelContainmentFeatures"));
    assertFalse(persisted.contains("metamodelSemanticEdgeObjectRules"));
  }

  @Test
  void activatesTrackedFileAndStoresPreviousVersionsInBackupDirectory() throws Exception {
    MdeRuntimePaths paths = mock(MdeRuntimePaths.class);
    when(paths.notationRoot()).thenReturn(tempDir);
    ModelingConfigService modelingConfig = mock(ModelingConfigService.class);
    AdminNotationService service =
        new AdminNotationService(paths, new ObjectMapper(), modelingConfig);
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
    verify(modelingConfig, times(2)).invalidateConfig();
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
