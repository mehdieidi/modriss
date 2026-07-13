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
    assertEquals(4, first.size());
  }

  @Test
  void indexesApprovedLocalDocumentToCimSamplesWhenTheyAreAvailable() {
    LexicalRetrievalIndex index = new LexicalRetrievalIndex(new AssistantMetamodelSchemaService());

    var matches = index.search(ModelLevel.CIM, "community clinic patient appointment", 12);

    assertTrue(
        matches.stream().anyMatch(item -> item.source().contains("community-clinic-user-stories")));
  }
}
