package io.mehdieidi.modless.platform.core.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.mehdieidi.modless.platform.core.model.ModelLevel;
import io.mehdieidi.modless.platform.core.model.ModelRecord;
import io.mehdieidi.modless.platform.core.model.ProjectRecord;
import io.mehdieidi.modless.platform.core.model.UserRecord;
import io.mehdieidi.modless.platform.core.repository.JsonFileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TransformationServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void cimToPimCreatesPimSemanticModelInsteadOfRelabelingCimJson() throws Exception {
        JsonFileStore store = new JsonFileStore(tempDir);
        store.initialize();
        AuthService authService = new AuthService(store, Duration.ofHours(1));
        ProjectService projectService = new ProjectService(store, authService);
        ModelService modelService = new ModelService(store, projectService);
        ArtifactService artifactService = new ArtifactService(store, projectService);
        TransformationService transformations = new TransformationService(store, modelService,
                artifactService);
        UserRecord user = authService.register("owner@example.com", "password123", "Owner").user();
        ProjectRecord project = projectService.create(user, "Climate", "");

        byte[] sample = Files.readAllBytes(Path.of("..", "..", "..", "mde", "samples",
                "climate-relief-grants-cim-sample.xmi").normalize());
        ModelService.ImportResult imported = modelService.importModel(ModelLevel.CIM,
                "climate-relief-grants-cim-sample.xmi", sample, "xmi");
        ModelRecord cim = modelService.create(user, ModelLevel.CIM, project.id(), "climate-cim",
                imported.modelJson());

        ModelRecord pim = transformations.cimToPim(user, cim.id());

        assertEquals(ModelLevel.PIM, pim.level());
        assertEquals("PIM", pim.modelJson().path("modelLevel").asText());
        assertEquals("PIMModel", pim.modelJson().path("eClass").asText());
        assertFalse(pim.modelJson().path("services").isEmpty());
        assertFalse(pim.modelJson().path("functions").isEmpty());
        assertFalse(pim.modelJson().path("apis").path(0).path("routes").isEmpty());
        assertFalse(pim.modelJson().path("eventTypes").isEmpty());
        assertFalse(pim.modelJson().path("dataStores").isEmpty());
        assertEquals("GENERATED_BY_ETL", pim.modelJson().path("transformationStatus").asText());
        assertTrue(pim.modelJson().path("commands").isMissingNode(),
                "Generated PIM must not retain CIM root containments.");
        assertTrue(pim.modelJson().path("manualBacklog").size() > 1,
                "ETL readiness findings and decisions should be mirrored into the frontend backlog.");
        assertTrue(pim.modelJson().path("graph").path("elements").findValuesAsText("eClass")
                .contains("Function"));
    }
}
