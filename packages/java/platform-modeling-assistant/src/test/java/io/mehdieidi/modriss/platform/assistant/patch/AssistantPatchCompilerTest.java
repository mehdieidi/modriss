package io.mehdieidi.modriss.platform.assistant.patch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.mehdieidi.modriss.platform.assistant.domain.SemanticModelPatch;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.StringNode;

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
                    new StringNode("New"),
                    null,
                    "name")));

    AssistantPatchCompiler.CompiledPatch compiled = compiler.compile(model, patch);
    var preview = compiler.apply(model, compiled);

    assertEquals("/diagram/elements/0/name", compiled.patch().get(0).path());
    assertEquals("New", preview.at("/diagram/elements/0/name").asText());
    assertEquals("Old", compiled.inversePatch().get(0).value().asText());
  }

  @Test
  void connectsSemanticElementWhenDiagramContainsSameStableId() throws Exception {
    var model =
        mapper.readTree(
            """
{
  "id": "cim-root",
  "eClass": "CIMModel",
  "modelLevel": "CIM",
  "domainName": "Conference",
  "diagram": {
    "elements": [
      {"id": "query-1", "eClass": "Query", "name": "Published schedule"},
      {"id": "output-1", "eClass": "InformationItem", "name": "Published schedule output"}
    ],
    "relationships": []
  },
  "queries": [
    {"id": "query-1", "eClass": "Query", "name": "Published schedule"}
  ],
  "informationItems": [
    {"id": "output-1", "eClass": "InformationItem", "name": "Published schedule output", "type": "TEXT"}
  ]
}
""");
    SemanticModelPatch patch =
        new SemanticModelPatch(
            List.of(
                new SemanticModelPatch.Operation(
                    SemanticModelPatch.OperationType.CONNECT_ELEMENTS,
                    "output-1",
                    null,
                    null,
                    "query-1",
                    "output")));

    AssistantPatchCompiler.CompiledPatch compiled = compiler.compile(model, patch);
    var preview = compiler.apply(model, compiled);

    assertEquals("output-1", preview.at("/queries/0/output/0").asText());
    assertEquals("", preview.at("/diagram/elements/0/output").asText());
  }

  @Test
  void projectsRelationshipEClassAsAnEdgeInsteadOfADanglingDiagramNode() throws Exception {
    var model =
        mapper.readTree(
            """
{"id":"root","eClass":"PIMModel","modelLevel":"PIM","diagram":{"elements":[
{"id":"start","eClass":"StartStep","name":"Start"},
{"id":"finish","eClass":"SuccessEndStep","name":"Finish"}],"relationships":[]},
"workflows":[{"id":"workflow","eClass":"Workflow","name":"Order workflow","steps":[
{"id":"start","eClass":"StartStep","name":"Start"},
{"id":"finish","eClass":"SuccessEndStep","name":"Finish"}],"transitions":[
{"id":"transition","eClass":"WorkflowTransition"}]}]}
""");
    SemanticModelPatch patch =
        new SemanticModelPatch(
            List.of(
                new SemanticModelPatch.Operation(
                    SemanticModelPatch.OperationType.CONNECT_ELEMENTS,
                    "start",
                    "StartStep",
                    null,
                    "transition",
                    "source"),
                new SemanticModelPatch.Operation(
                    SemanticModelPatch.OperationType.CONNECT_ELEMENTS,
                    "finish",
                    "SuccessEndStep",
                    null,
                    "transition",
                    "target")));

    var preview = compiler.apply(model, compiler.compile(model, patch));

    assertEquals("start", preview.at("/workflows/0/transitions/0/source").asText());
    assertEquals("finish", preview.at("/workflows/0/transitions/0/target").asText());
    assertEquals(1, preview.path("diagram").path("relationships").size());
    assertEquals("transition", preview.at("/diagram/relationships/0/id").asText());
    assertEquals("start", preview.at("/diagram/relationships/0/sourceElementId").asText());
    assertEquals("finish", preview.at("/diagram/relationships/0/targetElementId").asText());
    assertTrue(preview.path("diagram").path("elements").size() == 2);
    assertFalse(preview.path("diagram").path("elements").toString().contains("transition"));
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
                    new StringNode("HYBRID_SERVERLESS"),
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
                    new StringNode("bad"),
                    null,
                    "../secret")));

    assertThrows(
        io.mehdieidi.modriss.platform.kernel.PlatformException.class,
        () -> compiler.compile(model, patch));
  }

  @Test
  void rejectsSetAttributeAgainstMetamodelReferences() throws Exception {
    var model =
        mapper.readTree(
            """
            {
              "eClass":"CIMModel",
              "modelLevel":"CIM",
              "relationships":[
                {"id":"relationship-1","eClass":"DomainRelationship","name":"Customer orders"}
              ],
              "diagram":{"elements":[],"relationships":[]}
            }
            """);
    SemanticModelPatch patch =
        new SemanticModelPatch(
            List.of(
                new SemanticModelPatch.Operation(
                    SemanticModelPatch.OperationType.SET_ATTRIBUTE,
                    "relationship-1",
                    "DomainRelationship",
                    mapper.readTree("{\"lowerBound\":1}"),
                    null,
                    "sourceMultiplicity")));

    assertThrows(
        io.mehdieidi.modriss.platform.kernel.PlatformException.class,
        () -> compiler.compile(model, patch));
  }

  @Test
  void rejectsGraphProjectionWhenCreateHasNoExplicitOwner() throws Exception {
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

    assertThrows(
        io.mehdieidi.modriss.platform.kernel.PlatformException.class,
        () -> compiler.compile(model, patch));
  }

  @Test
  void rejectsWorkflowWhenCompatibleOwnerWasNotExplicitlySelected() throws Exception {
    var model =
        mapper.readTree(
            """
{
  "id":"pim-root","eClass":"PIMModel","modelLevel":"PIM",
  "services":[{"id":"orders-service","eClass":"ServerlessService","name":"Orders"}],
  "graph":{"elements":[{"id":"orders-service","eClass":"ServerlessService","name":"Orders"}],"relationships":[]}
}
""");
    SemanticModelPatch patch =
        new SemanticModelPatch(
            List.of(
                new SemanticModelPatch.Operation(
                    SemanticModelPatch.OperationType.ADD_ELEMENT,
                    "order-workflow",
                    "Workflow",
                    mapper.readTree("{\"name\":\"Order workflow\"}"),
                    null,
                    null)));

    assertThrows(
        io.mehdieidi.modriss.platform.kernel.PlatformException.class,
        () -> compiler.compile(model, patch));
  }

  @Test
  void rejectsDiagramProjectionWhenCreateHasNoExplicitOwner() throws Exception {
    var model =
        mapper.readTree(
            """
            {"id":"pim-root","eClass":"PIMModel","modelLevel":"PIM","services":[]}
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

    assertThrows(
        io.mehdieidi.modriss.platform.kernel.PlatformException.class,
        () -> compiler.compile(model, patch));
  }

  @Test
  void rejectsDiagramElementProjectionWhenCreateHasNoExplicitOwner() throws Exception {
    var model =
        mapper.readTree(
            """
            {"id":"pim-root","eClass":"PIMModel","modelLevel":"PIM","services":[],"diagram":{}}
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

    assertThrows(
        io.mehdieidi.modriss.platform.kernel.PlatformException.class,
        () -> compiler.compile(model, patch));
  }

  @Test
  void createsSingleValuedRootContainment() throws Exception {
    var model =
        mapper.readTree(
            """
            {"id":"pim-root","eClass":"PIMModel","modelLevel":"PIM"}
            """);
    SemanticModelPatch patch =
        new SemanticModelPatch(
            List.of(
                new SemanticModelPatch.Operation(
                    SemanticModelPatch.OperationType.ADD_ELEMENT,
                    "profile-1",
                    "ImplementationProfile",
                    mapper.readTree(
                        "{\"name\":\"TypeScript"
                            + " profile\",\"primaryLanguage\":\"TYPESCRIPT\",\"packageManager\":\"NPM\"}"),
                    "pim-root",
                    "implementationProfile")));

    AssistantPatchCompiler.CompiledPatch compiled = compiler.compile(model, patch);
    var preview = compiler.apply(model, compiled);

    assertEquals("/diagram", compiled.patch().get(0).path());
    assertEquals("/implementationProfile", compiled.patch().get(1).path());
    assertEquals("profile-1", preview.at("/implementationProfile/id").asText());
  }

  @Test
  void rejectsBackendOwnedIdentityAttributesWithoutSalvagingTheCreate() throws Exception {
    var model =
        mapper.readTree(
            """
            {"eClass":"PIMModel","modelLevel":"PIM","services":[],
             "graph":{"elements":[],"relationships":[]}}
            """);
    SemanticModelPatch patch =
        new SemanticModelPatch(
            List.of(
                new SemanticModelPatch.Operation(
                    SemanticModelPatch.OperationType.ADD_ELEMENT,
                    "function-1",
                    "Function",
                    mapper.readTree("{\"id\":null,\"eClass\":\"Schema\",\"name\":\"Submit\"}"),
                    null,
                    null)));

    assertThrows(
        io.mehdieidi.modriss.platform.kernel.PlatformException.class,
        () -> compiler.compile(model, patch));
  }

  @Test
  void inverseToleratesPersistenceDroppingTheSelectedVisualContainer() throws Exception {
    var starter =
        mapper.readTree(
            """
            {"id":"pim-root","eClass":"PIMModel","modelLevel":"PIM","policies":[],
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
                        "{\"name\":\"Order Idempotency\",\"keySource\":\"request header\"}"),
                    "pim-root",
                    "policies")));

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
  void addsOwnedChildThroughSingleValuedContainmentReference() throws Exception {
    var model =
        mapper.readTree(
            """
            {
              "eClass":"PIMModel","modelLevel":"PIM",
              "functions":[{"id":"function-1","eClass":"Function","name":"Submit"}],
              "graph":{"elements":[],"relationships":[]}
            }
            """);
    SemanticModelPatch patch =
        new SemanticModelPatch(
            List.of(
                new SemanticModelPatch.Operation(
                    SemanticModelPatch.OperationType.ADD_ELEMENT,
                    "contract-1",
                    "FunctionContract",
                    mapper.readTree("{\"name\":\"Submit contract\"}"),
                    "function-1",
                    "contract")));

    AssistantPatchCompiler.CompiledPatch compiled = compiler.compile(model, patch);
    var preview = compiler.apply(model, compiled);

    assertEquals("/functions/0/contract", compiled.patch().get(0).path());
    assertEquals("contract-1", preview.at("/functions/0/contract/id").asText());
  }

  @Test
  void rejectsNewChildWhenItsParentWasNotExplicitlyContained() throws Exception {
    var model =
        mapper.readTree(
            """
            {
              "eClass":"PIMModel","modelLevel":"PIM","services":[],
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

    assertThrows(
        io.mehdieidi.modriss.platform.kernel.PlatformException.class,
        () -> compiler.compile(model, patch));
  }

  @Test
  void rejectsRootPlacementWhenItsContainmentFeatureWasNotExplicitlySelected() throws Exception {
    var model =
        mapper.readTree(
            """
            {
              "id":"architecture-root","eClass":"PIMModel","modelLevel":"PIM",
              "services":[],
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
                    null,
                    null)));

    assertThrows(
        io.mehdieidi.modriss.platform.kernel.PlatformException.class,
        () -> compiler.compile(model, patch));
  }

  @Test
  void rejectsCreateWithoutExplicitOwnerAndContainment() throws Exception {
    var model = mapper.readTree("{\"id\":\"root\",\"eClass\":\"PIMModel\",\"modelLevel\":\"PIM\"}");
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

    assertThrows(
        io.mehdieidi.modriss.platform.kernel.PlatformException.class,
        () -> compiler.compile(model, patch));
  }
}
