package io.mehdieidi.modless.platform.assistant.subset;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import io.mehdieidi.modless.platform.assistant.domain.SemanticModelPatch;
import io.mehdieidi.modless.platform.assistant.patch.AssistantMetamodelSchemaService;
import io.mehdieidi.modless.platform.assistant.subset.ModelSubsetTurnPlan.Containment;
import io.mehdieidi.modless.platform.assistant.subset.ModelSubsetTurnPlan.Element;
import io.mehdieidi.modless.platform.assistant.subset.ModelSubsetTurnPlan.ModelSubset;
import io.mehdieidi.modless.platform.assistant.subset.ModelSubsetTurnPlan.Reference;
import io.mehdieidi.modless.platform.kernel.ModelLevel;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ModelSubsetPatchCompilerTest {

  private final ObjectMapper mapper = new ObjectMapper();
  private final ModelSubsetPatchCompiler compiler =
      new ModelSubsetPatchCompiler(new AssistantMetamodelSchemaService());

  @Test
  void lowersConnectedPartialModelSubsetIntoSemanticPatch() throws Exception {
    JsonNode root =
        mapper.readTree(
            """
            {
              "id": "root",
              "eClass": "PIMModel",
              "modelLevel": "PIM",
              "functions": [],
              "apis": []
            }
            """);
    ModelSubset subset =
        new ModelSubset(
            "orders-api-slice",
            "Order booking API slice",
            List.of(
                new Element(
                    "book-fn",
                    "Function",
                    JsonNodeFactory.instance
                        .objectNode()
                        .put("name", "Book order")
                        .put("functionKind", "COMMAND_HANDLER"),
                    new Containment("root", "functions"),
                    List.of()),
                new Element(
                    "orders-api",
                    "Api",
                    JsonNodeFactory.instance
                        .objectNode()
                        .put("name", "Orders API")
                        .put("apiStyle", "REST")
                        .put("basePath", "/orders"),
                    new Containment("root", "apis"),
                    List.of()),
                new Element(
                    "book-route",
                    "ApiRoute",
                    JsonNodeFactory.instance
                        .objectNode()
                        .put("name", "Book order route")
                        .put("method", "POST")
                        .put("pathTemplate", "/orders"),
                    new Containment("orders-api", "routes"),
                    List.of(new Reference("", "functionIntegration", "book-fn")))),
            List.of(),
            List.of(),
            List.of());

    SemanticModelPatch patch = compiler.compile(ModelLevel.PIM, root, Map.of(), subset);

    assertEquals(4, patch.operations().size());
    assertEquals(SemanticModelPatch.OperationType.ADD_ELEMENT, patch.operations().get(0).type());
    assertEquals("book-fn", patch.operations().get(0).targetElementId());
    assertEquals("functions", patch.operations().get(0).referenceName());
    assertEquals("orders-api", patch.operations().get(1).targetElementId());
    assertEquals("book-route", patch.operations().get(2).targetElementId());
    assertEquals("orders-api", patch.operations().get(2).sourceElementId());
    assertEquals("routes", patch.operations().get(2).referenceName());
    assertEquals(
        SemanticModelPatch.OperationType.CONNECT_ELEMENTS, patch.operations().get(3).type());
    assertEquals("book-route", patch.operations().get(3).sourceElementId());
    assertEquals("functionIntegration", patch.operations().get(3).referenceName());
    assertEquals("book-fn", patch.operations().get(3).targetElementId());
  }
}
