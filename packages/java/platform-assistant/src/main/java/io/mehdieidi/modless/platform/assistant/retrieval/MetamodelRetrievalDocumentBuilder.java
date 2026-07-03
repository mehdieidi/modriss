package io.mehdieidi.modless.platform.assistant.retrieval;

import io.mehdieidi.modless.platform.assistant.metamodel.MetamodelContractRecord;
import io.mehdieidi.modless.platform.assistant.metamodel.MetamodelKnowledgeService;
import io.mehdieidi.modless.platform.assistant.provider.AssistantModelProvider;
import io.mehdieidi.modless.platform.kernel.ModelLevel;
import java.util.List;

/** Builds retrieval documents from the resolved Ecore metamodel knowledge index. */
public class MetamodelRetrievalDocumentBuilder {

  private final MetamodelKnowledgeService metamodels;

  public MetamodelRetrievalDocumentBuilder(MetamodelKnowledgeService metamodels) {
    this.metamodels = metamodels;
  }

  public List<RetrievalDocument> documents(ModelLevel level) {
    if (metamodels == null || level == null) {
      return List.of();
    }
    return metamodels.index().records(level).stream().map(this::document).toList();
  }

  public AssistantModelProvider.ContextSnippet snippet(RetrievalDocument document) {
    return new AssistantModelProvider.ContextSnippet(
        document.source(), document.title(), document.content());
  }

  private RetrievalDocument document(MetamodelContractRecord record) {
    return new RetrievalDocument(
        "metamodel-" + record.kind(),
        record.title(),
        record.content(),
        record.level(),
        record.kind(),
        record.eClass(),
        record.feature(),
        record.dependencies());
  }

  /** One retrieval-ready metamodel document. */
  public record RetrievalDocument(
      String source,
      String title,
      String content,
      ModelLevel level,
      String kind,
      String eClass,
      String feature,
      List<String> dependencies) {

    public RetrievalDocument {
      source = source == null ? "" : source.trim();
      title = title == null ? "" : title.trim();
      content = content == null ? "" : content.trim();
      kind = kind == null ? "" : kind.trim();
      eClass = eClass == null ? "" : eClass.trim();
      feature = feature == null ? "" : feature.trim();
      dependencies = dependencies == null ? List.of() : List.copyOf(dependencies);
    }
  }
}
