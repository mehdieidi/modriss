package io.mehdieidi.modriss.platform.assistant.metamodel;

import io.mehdieidi.modriss.platform.kernel.ModelLevel;
import java.util.List;

/** One task-friendly Ecore contract document before persistence/indexing. */
public record MetamodelContractRecord(
    ModelLevel level,
    String kind,
    String eClass,
    String feature,
    String title,
    String content,
    List<String> dependencies) {

  public MetamodelContractRecord {
    kind = kind == null ? "" : kind.trim();
    eClass = eClass == null ? "" : eClass.trim();
    feature = feature == null ? "" : feature.trim();
    title = title == null ? "" : title.trim();
    content = content == null ? "" : content.trim();
    dependencies = dependencies == null ? List.of() : List.copyOf(dependencies);
  }
}
