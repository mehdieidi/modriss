package io.mehdieidi.modless.platform.assistant.persistence.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.mehdieidi.modless.platform.assistant.patch.AssistantMetamodelSchemaService;
import io.mehdieidi.modless.platform.kernel.ModelLevel;
import java.util.List;
import org.junit.jupiter.api.Test;

class JdbcAssistantModelContextIndexFocusTest {

  private final JdbcAssistantModelContextIndex contexts = new JdbcAssistantModelContextIndex();
  private final AssistantMetamodelSchemaService schemas = new AssistantMetamodelSchemaService();

  @Test
  void focusContextIncludesSelectedElementsAndContracts() throws Exception {
    var model =
        new ObjectMapper()
            .readTree(
                """
                {
                  "id":"root",
                  "eClass":"PIMModel",
                  "modelLevel":"PIM",
                  "diagram":{
                    "elements":[
                      {"id":"api-1","eClass":"Api","name":"Orders API"},
                      {"id":"fn-1","eClass":"Function","name":"Create order"}
                    ],
                    "relationships":[
                      {"id":"rel-1","source":"api-1","target":"fn-1","kind":"integration"}
                    ]
                  }
                }
                """);
    var context = contexts.transientSnapshot("project", ModelLevel.PIM, "Orders", 1L, model, null);
    String focus = contexts.focusContext(context, List.of("api-1", "fn-1"), schemas);

    assertTrue(focus.contains("api-1"));
    assertTrue(focus.contains("fn-1"));
    assertTrue(focus.contains("writable contract"));
  }

  @Test
  void summarizeNotesLargeModelStrategy() throws Exception {
    var model =
        new ObjectMapper()
            .readTree(
                """
{"id":"root","eClass":"PIMModel","modelLevel":"PIM","diagram":{"elements":[],"relationships":[]}}
""");
    var context = contexts.transientSnapshot("project", ModelLevel.PIM, "Large", 1L, model, null);
    var largeContext =
        new io.mehdieidi.modless.platform.assistant.domain.context.AssistantModelContextTypes
            .AssistantModelContext(
            "m1",
            "project",
            ModelLevel.PIM,
            "Large",
            1L,
            java.util.stream.IntStream.range(0, 75)
                .mapToObj(
                    index ->
                        new io.mehdieidi.modless.platform.assistant.domain.context
                            .AssistantModelContextTypes.ContextElement(
                            "id-" + index, "Function", "Fn " + index, "/diagram/elements/" + index))
                .toList(),
            List.of(),
            java.util.Map.of(),
            List.of());
    String summary = contexts.summarize(largeContext);
    assertTrue(summary.contains("Large model note"));
    assertTrue(summary.contains("listModelElements"));
    assertEquals(75, largeContext.elements().size());
  }
}
