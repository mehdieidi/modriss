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
        io.mehdieidi.modless.platform.core.PlatformException.class,
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
}
