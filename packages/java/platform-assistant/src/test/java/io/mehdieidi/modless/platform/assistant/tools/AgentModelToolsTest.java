package io.mehdieidi.modless.platform.assistant.tools;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.mehdieidi.modless.platform.assistant.metamodel.MetamodelKnowledgeService;
import io.mehdieidi.modless.platform.assistant.metamodel.TypeContractService;
import io.mehdieidi.modless.platform.assistant.patch.AssistantMetamodelSchemaService;
import io.mehdieidi.modless.platform.assistant.patch.AssistantPatchCompiler;
import io.mehdieidi.modless.platform.assistant.workspace.ModelWorkspace;
import io.mehdieidi.modless.platform.kernel.ModelLevel;
import io.mehdieidi.modless.platform.kernel.PlatformException;
import io.mehdieidi.modless.platform.model.application.ModelService;
import java.util.List;
import org.junit.jupiter.api.Test;

class AgentModelToolsTest {

  @Test
  void rejectsUnknownTypeWithActionableSuggestion() throws Exception {
    var knowledge = new MetamodelKnowledgeService(new AssistantMetamodelSchemaService());
    AgentModelTools tools =
        new AgentModelTools(new TypeContractService(knowledge), mock(ModelService.class));
    var model =
        new ObjectMapper()
            .readTree(
                """
{"id":"root","eClass":"CIMModel","modelLevel":"CIM","diagram":{"elements":[],"relationships":[]}}
""");
    tools.bind(
        ModelLevel.CIM,
        new ModelWorkspace(ModelLevel.CIM, "m", 1, model, new AssistantPatchCompiler(), null));

    PlatformException error =
        assertThrows(
            PlatformException.class,
            () ->
                tools.createElements(
                    List.of(
                        new AgentModelTools.CreateElement(
                            null, "BusinesProces", "Checkout", null, null, null))));

    assertTrue(error.getMessage().contains("Did you mean"));
  }
}
