package io.mehdieidi.modless.backend.assistant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.TextNode;
import java.util.List;
import org.junit.jupiter.api.Test;

class AssistantPatchCompilerTest {

  private final ObjectMapper mapper = new ObjectMapper();
  private final AssistantPatchCompiler compiler = new AssistantPatchCompiler();

  @Test
  void compilesStableIdAttributeUpdateWithoutModelAuthoredJsonPointer() throws Exception {
    var model =
        mapper.readTree(
            """
            {"diagram":{"elements":[{"id":"service-1","name":"Old"}],"relationships":[]}}
            """);
    SemanticModelPatch patch =
        new SemanticModelPatch(
            List.of(
                new SemanticModelPatch.Operation(
                    SemanticModelPatch.OperationType.SET_ATTRIBUTE,
                    "service-1",
                    "Service",
                    new TextNode("New"),
                    null,
                    "name")));

    AssistantPatchCompiler.CompiledPatch compiled = compiler.compile(model, patch);
    var preview = compiler.apply(model, compiled);

    assertEquals("/diagram/elements/0/name", compiled.patch().get(0).path());
    assertEquals("New", preview.at("/diagram/elements/0/name").asText());
    assertEquals("Old", compiled.inversePatch().get(0).value().asText());
  }

  @Test
  void omitsAttributeUpdateWhenValueIsUnchanged() throws Exception {
    var model =
        mapper.readTree(
            """
            {"architectureStyle":"HYBRID_SERVERLESS","diagram":{"elements":[],"relationships":[]}}
            """);
    SemanticModelPatch patch =
        new SemanticModelPatch(
            List.of(
                new SemanticModelPatch.Operation(
                    SemanticModelPatch.OperationType.SET_ATTRIBUTE,
                    "",
                    null,
                    new TextNode("HYBRID_SERVERLESS"),
                    null,
                    "architectureStyle")));

    AssistantPatchCompiler.CompiledPatch compiled = compiler.compile(model, patch);

    assertEquals(0, compiled.patch().size());
    assertEquals(0, compiled.inversePatch().size());
  }

  @Test
  void rejectsModelAuthoredPointerLikeAttributeNames() throws Exception {
    var model =
        mapper.readTree(
            """
            {"diagram":{"elements":[{"id":"service-1","name":"Old"}],"relationships":[]}}
            """);
    SemanticModelPatch patch =
        new SemanticModelPatch(
            List.of(
                new SemanticModelPatch.Operation(
                    SemanticModelPatch.OperationType.SET_ATTRIBUTE,
                    "service-1",
                    "Service",
                    new TextNode("bad"),
                    null,
                    "../secret")));

    assertThrows(
        io.mehdieidi.modless.platform.kernel.PlatformException.class,
        () -> compiler.compile(model, patch));
  }

  @Test
  void appendsVisualElementsToGraphWhenSavedModelHasNoDiagram() throws Exception {
    var model =
        mapper.readTree(
            """
            {"services":[],"graph":{"elements":[],"relationships":[]}}
            """);
    SemanticModelPatch patch =
        new SemanticModelPatch(
            List.of(
                new SemanticModelPatch.Operation(
                    SemanticModelPatch.OperationType.ADD_ELEMENT,
                    "service-1",
                    "ServerlessService",
                    mapper.readTree("{\"name\":\"Orders\"}"),
                    null,
                    null)));

    AssistantPatchCompiler.CompiledPatch compiled = compiler.compile(model, patch);
    var preview = compiler.apply(model, compiled);

    assertEquals("/services/-", compiled.patch().get(0).path());
    assertEquals("/graph/elements/-", compiled.patch().get(1).path());
    assertEquals("Orders", preview.at("/graph/elements/0/name").asText());
  }

  @Test
  void deletingSemanticElementAlsoRemovesVisualDuplicateAndRelationships() throws Exception {
    var model =
        mapper.readTree(
            """
            {
              "functions":[{"id":"function-1","eClass":"Function","name":"Submit"}],
              "graph":{
                "elements":[
                  {"id":"function-1","eClass":"Function","name":"Submit"},
                  {"id":"store-1","eClass":"DataStore","name":"Orders"}
                ],
                "relationships":[
                  {"id":"writes","source":"function-1","target":"store-1"},
                  {"id":"calls","source":"api-1","target":"function-1"}
                ]
              }
            }
            """);
    SemanticModelPatch patch =
        new SemanticModelPatch(
            List.of(
                new SemanticModelPatch.Operation(
                    SemanticModelPatch.OperationType.DELETE_ELEMENT,
                    "function-1",
                    "Function",
                    null,
                    null,
                    null)));

    AssistantPatchCompiler.CompiledPatch compiled = compiler.compile(model, patch);
    var preview = compiler.apply(model, compiled);

    assertEquals(4, compiled.patch().size());
    assertEquals(0, preview.at("/functions").size());
    assertEquals(1, preview.at("/graph/elements").size());
    assertEquals(0, preview.at("/graph/relationships").size());
  }

  @Test
  void addsOwnedChildThroughTypedContainmentReference() throws Exception {
    var model =
        mapper.readTree(
            """
            {
              "dataStores":[{"id":"store-1","eClass":"DataStore","name":"Orders"}],
              "graph":{"elements":[],"relationships":[]}
            }
            """);
    SemanticModelPatch patch =
        new SemanticModelPatch(
            List.of(
                new SemanticModelPatch.Operation(
                    SemanticModelPatch.OperationType.ADD_ELEMENT,
                    "model-1",
                    "DataModel",
                    mapper.readTree("{\"name\":\"Order\"}"),
                    "store-1",
                    "ownedDataModels")));

    AssistantPatchCompiler.CompiledPatch compiled = compiler.compile(model, patch);
    var preview = compiler.apply(model, compiled);

    assertEquals("/dataStores/0/ownedDataModels", compiled.patch().get(0).path());
    assertEquals("model-1", preview.at("/dataStores/0/ownedDataModels/0/id").asText());
    assertEquals("Order", preview.at("/graph/elements/0/name").asText());
  }

  @Test
  void createsMissingContainmentCollectionUnderNewCanonicalizedOwner() throws Exception {
    var model =
        mapper.readTree(
            """
            {
              "apis":[],
              "graph":{"elements":[],"relationships":[]}
            }
            """);
    SemanticModelPatch patch =
        new SemanticModelPatch(
            List.of(
                new SemanticModelPatch.Operation(
                    SemanticModelPatch.OperationType.ADD_ELEMENT,
                    "api-1",
                    "API",
                    mapper.readTree("{\"name\":\"Library\"}"),
                    null,
                    null),
                new SemanticModelPatch.Operation(
                    SemanticModelPatch.OperationType.ADD_ELEMENT,
                    "resource-1",
                    "Resource",
                    mapper.readTree("{\"name\":\"Book\"}"),
                    "api-1",
                    "resources")));

    AssistantPatchCompiler.CompiledPatch compiled = compiler.compile(model, patch);
    var preview = compiler.apply(model, compiled);

    assertEquals("/apis/-", compiled.patch().get(0).path());
    assertEquals("Api", compiled.patch().get(0).value().get("eClass").asText());
    assertEquals("/apis/0/resources", compiled.patch().get(2).path());
    assertEquals("resource-1", preview.at("/apis/0/resources/0/id").asText());
  }

  @Test
  void routesStarterRootOwnedElementThroughKnownSemanticCollection() throws Exception {
    var model =
        mapper.readTree(
            """
            {
              "id":"architecture-root",
              "dataStores":[],
              "graph":{"elements":[],"relationships":[]}
            }
            """);
    SemanticModelPatch patch =
        new SemanticModelPatch(
            List.of(
                new SemanticModelPatch.Operation(
                    SemanticModelPatch.OperationType.ADD_ELEMENT,
                    "store-1",
                    "DataStore",
                    mapper.readTree("{\"name\":\"Orders\"}"),
                    "architecture-root",
                    "elements")));

    AssistantPatchCompiler.CompiledPatch compiled = compiler.compile(model, patch);
    var preview = compiler.apply(model, compiled);

    assertEquals("/dataStores/-", compiled.patch().get(0).path());
    assertEquals("store-1", preview.at("/dataStores/0/id").asText());
    assertEquals(false, preview.has("elements"));
  }
}
