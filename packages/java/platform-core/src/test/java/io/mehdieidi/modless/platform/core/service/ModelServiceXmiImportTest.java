package io.mehdieidi.modless.platform.core.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.mehdieidi.modless.platform.core.model.ModelLevel;
import io.mehdieidi.modless.platform.core.repository.JsonFileStore;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ModelServiceXmiImportTest {

    @TempDir
    Path tempDir;

    @Test
    void importsCimXmiIntoSemanticAndGraphJson() {
        JsonFileStore store = new JsonFileStore(tempDir);
        store.initialize();
        AuthService authService = new AuthService(store, Duration.ofHours(1));
        ProjectService projectService = new ProjectService(store, authService);
        ModelService service = new ModelService(store, projectService);

        ModelService.ImportResult result = service.importModel(ModelLevel.CIM, "sample.xmi",
                sampleCimXmi().getBytes(StandardCharsets.UTF_8), "xmi");

        assertEquals("sample", result.name());
        assertEquals("Test Grants", result.modelJson().path("domainName").asText());
        assertEquals("BusinessGoal",
                result.modelJson().path("goals").path(0).path("eClass").asText());
        assertEquals("goal-1", result.modelJson().path("capabilities").path(0).path("supports")
                .path(0).asText());
        assertEquals("CIM", result.modelJson().path("modelLevel").asText());

        assertFalse(result.modelJson().path("graph").path("elements").isEmpty());
        assertTrue(result.modelJson().path("graph").path("elements").findValuesAsText("id")
                .contains("cap-1"));
        assertFalse(result.modelJson().path("graph").path("relationships").isEmpty());
        assertTrue(result.modelJson().path("graph").path("relationships").findValuesAsText(
                "semanticFeature").contains("supports"));
        assertNotNull(result.issues());
    }

    private String sampleCimXmi() {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <cim:CIMModel xmi:version="2.0"
                    xmlns:xmi="http://www.omg.org/XMI"
                    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                    xmlns:cim="https://modless.org/cim/1.0"
                    xmlns:kernel="https://modless.org/kernel/1.0"
                    xmi:id="cim-root"
                    id="cim-root"
                    name="Test Grants Model"
                    domainName="Test Grants">
                  <goals xmi:id="goal-1" id="goal-1" name="Protect residents"/>
                  <actors xmi:id="actor-1" id="actor-1" name="Resident"/>
                  <capabilities xmi:id="cap-1" id="cap-1" name="Review applications" supports="goal-1"/>
                </cim:CIMModel>
                """;
    }
}
