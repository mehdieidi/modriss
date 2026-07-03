package io.mehdieidi.modless.platform.assistant.retrieval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.mehdieidi.modless.platform.assistant.metamodel.MetamodelKnowledgeService;
import io.mehdieidi.modless.platform.assistant.patch.AssistantMetamodelSchemaService;
import io.mehdieidi.modless.platform.assistant.provider.AssistantModelProvider;
import io.mehdieidi.modless.platform.assistant.spi.AssistantCatalog;
import io.mehdieidi.modless.platform.kernel.ModelLevel;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RetrievalCoordinatorTest {

  @Test
  void expandsCandidateTypeContractsAndWarnsWhenHashEmbeddingsAreActive() {
    AssistantCatalog catalog = mock(AssistantCatalog.class);
    when(catalog.search(anyString(), anyString(), anyInt()))
        .thenReturn(
            List.of(
                new AssistantModelProvider.ContextSnippet(
                    "methodology", "Event storming", "Use commands and events.")));
    when(catalog.describeType(anyString(), anyString(), anyInt())).thenReturn(List.of());
    when(catalog.canonicalEnumLiteral(anyString(), anyString(), anyString(), anyString()))
        .thenReturn(Optional.empty());

    RetrievalCoordinator coordinator =
        new RetrievalCoordinator(
            new MetamodelKnowledgeService(new AssistantMetamodelSchemaService()), catalog, "HASH");

    RetrievalCoordinator.RetrievalResult result =
        coordinator.retrieve(
            new RetrievalPlan(
                ModelLevel.PIM, List.of("order booking"), List.of("Function"), true, false),
            4);

    assertTrue(
        result.snippets().stream()
            .anyMatch(
                snippet ->
                    snippet.source().contains("metamodel")
                        && snippet.title().contains("Function")));
    assertTrue(
        result.snippets().stream().anyMatch(snippet -> "methodology".equals(snippet.source())));
    assertFalse(result.diagnostics().selectedContracts().isEmpty());
    assertTrue(result.diagnostics().warnings().get(0).contains("embeddingProvider=HASH"));
  }

  @Test
  void buildsAndReranksMetamodelDocumentsFromStructuredPlans() {
    MetamodelKnowledgeService metamodels =
        new MetamodelKnowledgeService(new AssistantMetamodelSchemaService());
    MetamodelRetrievalDocumentBuilder builder = new MetamodelRetrievalDocumentBuilder(metamodels);
    RetrievalReranker reranker = new RetrievalReranker();

    List<MetamodelRetrievalDocumentBuilder.RetrievalDocument> documents =
        builder.documents(ModelLevel.PIM);
    List<MetamodelRetrievalDocumentBuilder.RetrievalDocument> ranked =
        reranker.rerank(
            new RetrievalPlan(
                ModelLevel.PIM, List.of("handler", "operation"), List.of("Function"), false, false),
            documents,
            5);

    assertFalse(documents.isEmpty());
    assertTrue(
        documents.stream().anyMatch(document -> "containment-recipe".equals(document.kind())));
    assertFalse(ranked.isEmpty());
    assertEquals("Function", ranked.get(0).eClass());
  }

  @Test
  void nonKeywordParaphrasePlansRetrieveSameRequiredContracts() {
    AssistantCatalog catalog = mock(AssistantCatalog.class);
    when(catalog.search(anyString(), anyString(), anyInt())).thenReturn(List.of());
    when(catalog.describeType(anyString(), anyString(), anyInt())).thenReturn(List.of());
    when(catalog.canonicalEnumLiteral(anyString(), anyString(), anyString(), anyString()))
        .thenReturn(Optional.empty());
    RetrievalCoordinator coordinator =
        new RetrievalCoordinator(
            new MetamodelKnowledgeService(new AssistantMetamodelSchemaService()), catalog, "ONNX");

    RetrievalCoordinator.RetrievalResult first =
        coordinator.retrieve(
            new RetrievalPlan(
                ModelLevel.PIM,
                List.of("borrower onboarding"),
                List.of("Api", "Function"),
                false,
                false),
            4);
    RetrievalCoordinator.RetrievalResult paraphrase =
        coordinator.retrieve(
            new RetrievalPlan(
                ModelLevel.PIM,
                List.of("شروع نام نویسی مشتری"),
                List.of("Api", "Function"),
                false,
                false),
            4);

    assertTrue(containsContract(first, "Api"));
    assertTrue(containsContract(first, "Function"));
    assertTrue(containsContract(paraphrase, "Api"));
    assertTrue(containsContract(paraphrase, "Function"));
  }

  private boolean containsContract(RetrievalCoordinator.RetrievalResult result, String type) {
    return result.diagnostics().selectedContracts().stream()
        .anyMatch(title -> title.contains(type));
  }
}
