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
{"eClass":"PIMModel","modelLevel":"PIM","diagram":{"elements":[{"id":"service-1","eClass":"Function","name":"Old"}],"relationships":[]}}
""");
    SemanticModelPatch patch =
        new SemanticModelPatch(
            List.of(
                new SemanticModelPatch.Operation(
                    SemanticModelPatch.OperationType.SET_ATTRIBUTE,
                    "service-1",
                    "Function",
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
{"eClass":"PIMModel","modelLevel":"PIM","architectureStyle":"HYBRID_SERVERLESS","diagram":{"elements":[],"relationships":[]}}
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
{"eClass":"PIMModel","modelLevel":"PIM","diagram":{"elements":[{"id":"service-1","eClass":"Function","name":"Old"}],"relationships":[]}}
""");
    SemanticModelPatch patch =
        new SemanticModelPatch(
            List.of(
                new SemanticModelPatch.Operation(
                    SemanticModelPatch.OperationType.SET_ATTRIBUTE,
                    "service-1",
                    "Function",
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
{"eClass":"PIMModel","modelLevel":"PIM","services":[],"graph":{"elements":[],"relationships":[]}}
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
  void inverseToleratesPersistenceDroppingTheSelectedVisualContainer() throws Exception {
    var starter =
        mapper.readTree(
            """
            {"eClass":"PIMModel","modelLevel":"PIM","policies":[],
             "diagram":{"elements":[],"relationships":[]},
             "graph":{"elements":[],"relationships":[]}}
            """);
    SemanticModelPatch patch =
        new SemanticModelPatch(
            List.of(
                new SemanticModelPatch.Operation(
                    SemanticModelPatch.OperationType.ADD_ELEMENT,
                    "order-idempotency",
                    "IdempotencyPolicy",
                    mapper.readTree(
                        "{\"displayName\":\"Order Idempotency\",\"keySource\":\"request header\"}"),
                    null,
                    null)));

    AssistantPatchCompiler.CompiledPatch compiled = compiler.compile(starter, patch);
    var persisted = compiler.apply(starter, compiled);
    persisted.remove("diagram");
    var adapted =
        compiler.adaptToSnapshot(
            persisted,
            new AssistantPatchCompiler.CompiledPatch(
                compiled.inversePatch(), List.of(), compiled.affectedElements()));
    var undone = compiler.apply(persisted, adapted);

    assertEquals(1, adapted.patch().size());
    assertEquals(0, undone.at("/policies").size());
  }

  @Test
  void deletingSemanticElementAlsoRemovesVisualDuplicateAndRelationships() throws Exception {
    var model =
        mapper.readTree(
            """
            {
              "eClass":"PIMModel","modelLevel":"PIM",
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
              "eClass":"PIMModel","modelLevel":"PIM",
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
              "eClass":"PIMModel","modelLevel":"PIM","apis":[],
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
                    "ApiRoute",
                    mapper.readTree(
                        "{\"name\":\"Book\",\"pathTemplate\":\"/books\",\"method\":\"GET\"}"),
                    "api-1",
                    "routes")));

    AssistantPatchCompiler.CompiledPatch compiled = compiler.compile(model, patch);
    var preview = compiler.apply(model, compiled);

    assertEquals("/apis/-", compiled.patch().get(0).path());
    assertEquals("Api", compiled.patch().get(0).value().get("eClass").asText());
    assertEquals("/apis/0/routes", compiled.patch().get(2).path());
    assertEquals("resource-1", preview.at("/apis/0/routes/0/id").asText());
  }

  @Test
  void routesStarterRootOwnedElementThroughKnownSemanticCollection() throws Exception {
    var model =
        mapper.readTree(
            """
            {
              "id":"architecture-root","eClass":"PIMModel","modelLevel":"PIM",
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
