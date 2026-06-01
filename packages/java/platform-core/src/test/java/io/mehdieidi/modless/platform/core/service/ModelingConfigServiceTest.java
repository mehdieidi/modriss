package io.mehdieidi.modless.platform.core.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ModelingConfigServiceTest {

    private final ModelingConfigService service = new ModelingConfigService();

    @Test
    void exposesEcoreDerivedStructureAndJsonOwnedUiMetadata() {
        Map<String, Object> pim = level("pim");
        List<Map<String, Object>> elements = listOfMaps(pim.get("elements"));

        Map<String, Object> function = element(elements, "Function");
        assertTrue(stringList(function.get("supertypes")).contains("FunctionTarget"));
        assertNotNull(function.get("attributes"));
        assertNotNull(function.get("references"));

        assertEquals("Function", function.get("label"));
        assertEquals("functions", function.get("icon"));
        assertEquals("#2563EB", function.get("color"));
        assertEquals("Compute", function.get("category"));
    }

    @Test
    void requiresJsonOwnedEditorSectionsForEveryLevel() {
        for (String key : List.of("cim", "pim", "psm")) {
            Map<String, Object> level = level(key);
            assertFalse(stringList(level.get("relationshipKinds")).isEmpty());
            assertNotNull(level.get("relationshipKindLabels"));
            assertNotNull(level.get("relationshipRules"));
            assertNotNull(level.get("viewDefinitions"));
            assertNotNull(level.get("rootTemplate"));
        }
    }

    @Test
    void exposesTypesMissingUiMetadataAsNonCreatableStructuralTypes() {
        Map<String, Object> pim = level("pim");
        Map<String, Object> modelElement = element(listOfMaps(pim.get("elements")),
                "ModelElement");

        assertEquals(Boolean.TRUE, modelElement.get("uiMetadataMissing"));
        assertEquals(Boolean.FALSE, modelElement.get("creatable"));
        assertNotNull(modelElement.get("attributes"));
        assertNotNull(modelElement.get("references"));
    }

    @Test
    void rootTemplatesComeFromJsonMetadata() {
        assertEquals("CIMModel", map(level("cim").get("rootTemplate")).get("eClass"));
        assertEquals("PIMModel", map(level("pim").get("rootTemplate")).get("eClass"));
        assertEquals("AwsPsmModel", map(level("psm").get("rootTemplate")).get("eClass"));
    }

    private Map<String, Object> level(String key) {
        return map(map(service.config().get("levels")).get(key));
    }

    private Map<String, Object> element(List<Map<String, Object>> elements, String type) {
        return elements.stream().filter(item -> type.equals(item.get("type"))).findFirst()
                .orElseThrow();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> map(Object value) {
        assertNotNull(value);
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> listOfMaps(Object value) {
        assertNotNull(value);
        return (List<Map<String, Object>>) value;
    }

    @SuppressWarnings("unchecked")
    private List<String> stringList(Object value) {
        assertNotNull(value);
        return (List<String>) value;
    }
}
