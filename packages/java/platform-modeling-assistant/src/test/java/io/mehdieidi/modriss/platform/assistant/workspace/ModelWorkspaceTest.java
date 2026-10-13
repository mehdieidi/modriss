package io.mehdieidi.modriss.platform.assistant.workspace;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.mehdieidi.modriss.platform.assistant.domain.SemanticModelPatch;
import io.mehdieidi.modriss.platform.assistant.patch.AssistantPatchCompiler;
import io.mehdieidi.modriss.platform.kernel.ModelLevel;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.StringNode;

class ModelWorkspaceTest {

  @Test
  void accumulatesForwardAndInversePatchesAndPublishesDelta() throws Exception {
    var model =
        new ObjectMapper()
            .readTree(
                """
                {"eClass":"PIMModel","modelLevel":"PIM","diagram":{"elements":[
                {"id":"fn-1","eClass":"Function","name":"Old"}],"relationships":[]}}
                """);
    AtomicInteger events = new AtomicInteger();
    ModelWorkspace workspace =
        new ModelWorkspace(
            ModelLevel.PIM,
            "model-1",
            3,
            model,
            new AssistantPatchCompiler(),
            ignored -> events.incrementAndGet());

    workspace.mutate(
        new SemanticModelPatch(
            List.of(
                new SemanticModelPatch.Operation(
                    SemanticModelPatch.OperationType.SET_ATTRIBUTE,
                    "fn-1",
                    "Function",
                    new StringNode("New"),
                    null,
                    "name"))));

    assertEquals("New", workspace.snapshot().at("/diagram/elements/0/name").asText());
    assertEquals("Old", workspace.inversePatch().get(0).value().asText());
    assertEquals(1, events.get());
  }
}
