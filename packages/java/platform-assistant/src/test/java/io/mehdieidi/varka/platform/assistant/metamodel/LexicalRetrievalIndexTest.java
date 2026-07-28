package io.mehdieidi.varka.platform.assistant.metamodel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.mehdieidi.varka.platform.assistant.patch.AssistantMetamodelSchemaService;
import io.mehdieidi.varka.platform.kernel.ModelLevel;
import org.junit.jupiter.api.Test;

class LexicalRetrievalIndexTest {
  @Test
  void returnsDeterministicFocusedContracts() {
    LexicalRetrievalIndex index = new LexicalRetrievalIndex(new AssistantMetamodelSchemaService());
    var first = index.search(ModelLevel.CIM, "goal actor", 4);
    var second = index.search(ModelLevel.CIM, "goal actor", 4);
    assertEquals(first, second);
    assertTrue(first.size() >= 4);
  }

  @Test
  void excludesDocumentToCimSamplesFromImplicitRetrievalCorpus() {
    LexicalRetrievalIndex index = new LexicalRetrievalIndex(new AssistantMetamodelSchemaService());

    var matches = index.search(ModelLevel.CIM, "community clinic patient appointment", 12);

    assertTrue(
        matches.stream()
            .noneMatch(
                item ->
                    item.source().contains("community-clinic-user-stories")
                        || item.source().contains("marketplace-returns-event-storming")));
  }

  @Test
  void ecoreClosureIncludesAFunctionsRequiredContract() {
    var knowledge = new MetamodelKnowledgeService(new AssistantMetamodelSchemaService());

    var closure = knowledge.contractClosure(ModelLevel.PIM, java.util.List.of("Function"));

    assertTrue(closure.stream().anyMatch(item -> item.title().equals("Function EClass contract")));
    assertTrue(
        closure.stream().anyMatch(item -> item.title().equals("FunctionContract EClass contract")));
  }
}
