package io.mehdieidi.modless.platform.core.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Tests the merged modeling configuration exposed to the UI.
 */
class ModelingConfigServiceTest {

    /**
     * Service under test.
     */
    private final ModelingConfigService service = new ModelingConfigService();

    /**
     * Verifies that Ecore-derived structural fields coexist with JSON-owned UI metadata.
     */
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

    /**
     * Verifies that each level exposes the JSON-owned editor sections required by the frontend.
     */
    @Test
    void requiresJsonOwnedEditorSectionsForEveryLevel() {
        for (String key : List.of("cim", "pim", "psm")) {
            Map<String, Object> level = level(key);
            assertFalse(stringList(level.get("relationshipKinds")).isEmpty());
            assertNotNull(level.get("relationshipKindLabels"));
            assertFalse(listOfMaps(level.get("relationshipVisualRules")).isEmpty());
            assertNotNull(level.get("relationshipRules"));
            assertNotNull(level.get("viewDefinitions"));
            assertNotNull(level.get("rootTemplate"));
        }
    }

    /**
     * Verifies that every Ecore type receives complete visual metadata.
     */
    @Test
    void appliesJsonOwnedVisualRulesToEveryEcoreType() {
        for (String key : List.of("cim", "pim", "psm")) {
            Map<String, Object> level = level(key);
            for (Map<String, Object> element : listOfMaps(level.get("elements"))) {
                assertFalse(Boolean.TRUE.equals(element.get("uiMetadataMissing")));
                assertFalse(String.valueOf(element.get("label")).isBlank());
                assertFalse(String.valueOf(element.get("icon")).isBlank());
                assertFalse(String.valueOf(element.get("color")).isBlank());
                assertFalse(String.valueOf(element.get("category")).isBlank());
                assertNotNull(element.get("notation"));
            }
        }

        Map<String, Object> modelElement = element(listOfMaps(level("pim").get("elements")),
                "ModelElement");
        assertEquals(Boolean.TRUE, modelElement.get("supportOnly"));
        assertEquals(Boolean.FALSE, modelElement.get("creatable"));
    }

    /**
     * Verifies that root templates are sourced from JSON metadata.
     */
    @Test
    void rootTemplatesComeFromJsonMetadata() {
        assertEquals("CIMModel", map(level("cim").get("rootTemplate")).get("eClass"));
        assertEquals("PIMModel", map(level("pim").get("rootTemplate")).get("eClass"));
        assertEquals("AwsPsmModel", map(level("psm").get("rootTemplate")).get("eClass"));
    }

    /**
     * Verifies that new elements do not receive an invalid empty enum literal.
     */
    @Test
    void leavesEnumAttributesUnsetByDefault() {
        Map<String, Object> goal = element(listOfMaps(level("cim").get("elements")),
                "BusinessGoal");
        Map<String, Object> priority = attribute(goal, "priority");

        assertEquals("select", priority.get("fieldType"));
        assertNull(priority.get("defaultValue"));
        assertTrue(stringList(priority.get("options")).contains("MEDIUM"));
    }

    /**
     * Returns one level configuration from the service output.
     *
     * @param key level key
     * @return level configuration
     */
    private Map<String, Object> level(String key) {
        return map(map(service.config().get("levels")).get(key));
    }

    /**
     * Finds an element metadata entry by type.
     *
     * @param elements element metadata list
     * @param type     element type
     * @return matching element metadata
     */
    private Map<String, Object> element(List<Map<String, Object>> elements, String type) {
        return elements.stream().filter(item -> type.equals(item.get("type"))).findFirst()
                .orElseThrow();
    }

    /**
     * Finds an attribute metadata entry by name.
     *
     * @param element element metadata
     * @param name    attribute name
     * @return matching attribute metadata
     */
    private Map<String, Object> attribute(Map<String, Object> element, String name) {
        return listOfMaps(element.get("attributes")).stream()
                .filter(item -> name.equals(item.get("name"))).findFirst().orElseThrow();
    }

    /**
     * Casts a value to a map after asserting it is present.
     *
     * @param value value to cast
     * @return cast map
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> map(Object value) {
        assertNotNull(value);
        return (Map<String, Object>) value;
    }

    /**
     * Casts a value to a list of maps after asserting it is present.
     *
     * @param value value to cast
     * @return cast list
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> listOfMaps(Object value) {
        assertNotNull(value);
        return (List<Map<String, Object>>) value;
    }

    /**
     * Casts a value to a string list after asserting it is present.
     *
     * @param value value to cast
     * @return cast list
     */
    @SuppressWarnings("unchecked")
    private List<String> stringList(Object value) {
        assertNotNull(value);
        return (List<String>) value;
    }
}
