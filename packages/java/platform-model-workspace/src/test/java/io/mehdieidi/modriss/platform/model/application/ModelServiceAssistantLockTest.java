package io.mehdieidi.modriss.platform.model.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.mehdieidi.modriss.platform.kernel.ModelLevel;
import io.mehdieidi.modriss.platform.kernel.PlatformException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ModelServiceAssistantLockTest {
  @TempDir Path tempDir;

  @Test
  void freezesExternalMutationsWhileAllowingStructuralAssistantCheckpoints() {
    var services = PlatformTestFixtures.createServices(tempDir);
    var context =
        PlatformTestFixtures.registerOwner(
            services, "locked@example.test", "Owner", "Assistant lock");
    var models = services.models();
    var model =
        models
            .importModel(ModelLevel.CIM, "cim.xmi", PlatformTestFixtures.climateCimXmi(), "xmi")
            .modelJson();
    var created =
        models.create(
            context.user(), ModelLevel.CIM, context.project().id(), "Frozen model", model);
    var locked = new AtomicBoolean(true);
    models.setMutationGuard(
        existing -> {
          if (locked.get()) throw new PlatformException(423, "Assistant is working");
        });
    var operations =
        List.of(
            new ModelService.ModelPatchOperation(
                "replace",
                "/name",
                services.store().objectMapper().valueToTree("Assistant checkpoint")));

    assertEquals(
        423,
        assertThrows(
                PlatformException.class,
                () ->
                    models.update(
                        context.user(),
                        ModelLevel.CIM,
                        created.id(),
                        created.name(),
                        created.modelJson(),
                        created.revision()))
            .status());
    assertEquals(
        423,
        assertThrows(
                PlatformException.class,
                () ->
                    models.patch(
                        context.user(),
                        ModelLevel.CIM,
                        created.id(),
                        null,
                        operations,
                        created.revision()))
            .status());
    assertEquals(
        423,
        assertThrows(
                PlatformException.class,
                () -> models.delete(context.user(), ModelLevel.CIM, created.id()))
            .status());
    assertEquals(
        created.revision(), models.get(context.user(), ModelLevel.CIM, created.id()).revision());

    var checkpoint =
        models.patchStructurallyValid(
            context.user(), ModelLevel.CIM, created.id(), null, operations, created.revision());
    assertEquals(created.revision() + 1, checkpoint.revision());

    locked.set(false);
    var edited =
        models.update(
            context.user(),
            ModelLevel.CIM,
            created.id(),
            checkpoint.name(),
            checkpoint.modelJson(),
            checkpoint.revision());
    assertEquals(checkpoint.revision() + 1, edited.revision());
    models.delete(context.user(), ModelLevel.CIM, created.id());
    assertEquals(
        404,
        assertThrows(
                PlatformException.class,
                () -> models.get(context.user(), ModelLevel.CIM, created.id()))
            .status());
  }
}
