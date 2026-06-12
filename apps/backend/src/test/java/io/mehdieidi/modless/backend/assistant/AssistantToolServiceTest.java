package io.mehdieidi.modless.backend.assistant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.annotation.Tool;

class AssistantToolServiceTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void exposesOnlyWhitelistedSpringAiTools() {
        long annotated = java.util.Arrays.stream(AssistantToolService.class.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(Tool.class))
                .count();

        assertEquals(4, annotated);
        assertNotNull(tool("searchCatalogs"));
        assertNotNull(tool("previewSemanticPatch"));
        assertNotNull(tool("summarizeValidation"));
        assertNotNull(tool("requestUserChoice"));
    }

    @Test
    void previewsSemanticPatchWithoutCommitting() throws Exception {
        AssistantToolService tools = new AssistantToolService(new AssistantCatalogService(null) {
            @Override
            public java.util.List<AssistantModelProvider.ContextSnippet> search(String query,
                    String level, int limit) {
                return java.util.List.of();
            }
        }, new AssistantPatchCompiler(), mapper);

        AssistantToolService.PreviewResult result = tools.previewSemanticPatch(
                "{\"diagram\":{\"elements\":[{\"id\":\"service-1\",\"name\":\"Old\"}],\"relationships\":[]}}",
                "{\"operations\":[{\"type\":\"SET_ATTRIBUTE\",\"targetElementId\":\"service-1\",\"attributes\":\"New\",\"referenceName\":\"name\"}]}");

        assertEquals("service-1", result.affectedElements().get(0));
        assertEquals("New", result.preview().at("/diagram/elements/0/name").asText());
        assertTrue(result.inversePatch().size() == 1);
    }

    private Method tool(String name) {
        return java.util.Arrays.stream(AssistantToolService.class.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(Tool.class))
                .filter(method -> method.getAnnotation(Tool.class).name().equals(name))
                .findFirst()
                .orElse(null);
    }
}
