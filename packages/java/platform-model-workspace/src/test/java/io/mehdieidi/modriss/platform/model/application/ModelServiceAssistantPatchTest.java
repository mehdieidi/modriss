package io.mehdieidi.modriss.platform.model.application;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.mehdieidi.modriss.platform.kernel.ModelLevel;
import io.mehdieidi.modriss.platform.kernel.PlatformException;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.node.ObjectNode;

class ModelServiceAssistantPatchTest {
  @TempDir Path tempDir;

  @Test
  void serviceRenamePreservesCrossModelTracesAndOnlyChangesTheRequestedName() {
    var services = PlatformTestFixtures.createServices(tempDir);
    var context =
        PlatformTestFixtures.registerOwner(
            services, "assistant-traces@example.test", "Owner", "Assistant traces");
    var models = services.models();
    var imported =
        models
            .importModel(ModelLevel.PIM, "pim.xmi", PlatformTestFixtures.climatePimXmi(), "xmi")
            .modelJson();
    var created =
        models.create(context.user(), ModelLevel.PIM, context.project().id(), "PIM", imported);
    var before = models.semanticObjectsById(ModelLevel.PIM, created.modelJson());
    var links = created.modelJson().path("traceModel").path("links");
    assertFalse(links.isEmpty(), "The fixture must contain cross-model trace links");
    assertTrue(
        links.valueStream()
            .anyMatch(link -> !before.containsKey(link.path("sourceElementId").asText())),
        "At least one trace endpoint must belong to another model");
    var validation = models.validateStructural(ModelLevel.PIM, created.modelJson());
    assertTrue(validation.valid(), () -> validation.issues().toString());

    ObjectNode expected = (ObjectNode) created.modelJson().deepCopy();
    ((ObjectNode) expected.path("services").path(0)).put("name", "Renamed service");
    var operations =
        List.of(
            new ModelService.ModelPatchOperation(
                "replace",
                "/services/0/name",
                services.store().objectMapper().valueToTree("Renamed service")));
    var updated =
        models.patchStructurallyValid(
            context.user(), ModelLevel.PIM, created.id(), null, operations, created.revision());

    assertEquals(
        before.size(),
        models.semanticObjectsById(ModelLevel.PIM, updated.modelJson()).size(),
        "A scalar assistant patch must preserve every semantic object");
    assertEquals(links, updated.modelJson().path("traceModel").path("links"));
    assertEquals(expected, updated.modelJson(), "Only the requested service name may change");
    assertEquals(created.revision() + 1, updated.revision());
    assertEquals(expected, models.get(context.user(), ModelLevel.PIM, created.id()).modelJson());

    var sourceXmi = models.sourceXmi(updated).orElseThrow();
    var reimported =
        models.importModel(ModelLevel.PIM, "updated.xmi", sourceXmi, "xmi").modelJson();
    assertEquals(before.keySet(), models.semanticObjectsById(ModelLevel.PIM, reimported).keySet());
    assertEquals("Renamed service", reimported.path("services").path(0).path("name").asText());
    assertEquals(links.size(), reimported.path("traceModel").path("links").size());

    assertEquals(
        409,
        assertThrows(
                PlatformException.class,
                () ->
                    models.patchStructurallyValid(
                        context.user(),
                        ModelLevel.PIM,
                        created.id(),
                        null,
                        operations,
                        created.revision()))
            .status());
    assertEquals(updated, models.get(context.user(), ModelLevel.PIM, created.id()));
    assertArrayEquals(sourceXmi, models.sourceXmi(updated).orElseThrow());
  }

  @Test
  void structurallyInvalidAssistantPatchLeavesTheModelAndSourceXmiUnchanged() {
    var services = PlatformTestFixtures.createServices(tempDir);
    var context =
        PlatformTestFixtures.registerOwner(
            services, "assistant-invalid@example.test", "Owner", "Invalid assistant patch");
    var models = services.models();
    var imported =
        models
            .importModel(ModelLevel.CIM, "cim.xmi", PlatformTestFixtures.climateCimXmi(), "xmi")
            .modelJson();
    var created =
        models.create(context.user(), ModelLevel.CIM, context.project().id(), "CIM", imported);
    var sourceXmi = models.sourceXmi(created).orElseThrow();

    assertEquals(
        422,
        assertThrows(
                PlatformException.class,
                () ->
                    models.patchStructurallyValid(
                        context.user(),
                        ModelLevel.CIM,
                        created.id(),
                        null,
                        List.of(
                            new ModelService.ModelPatchOperation(
                                "replace",
                                "/eClass",
                                services.store().objectMapper().valueToTree("UnknownModelType"))),
                        created.revision()))
            .status());
    assertEquals(created, models.get(context.user(), ModelLevel.CIM, created.id()));
    assertArrayEquals(sourceXmi, models.sourceXmi(created).orElseThrow());
  }
}
