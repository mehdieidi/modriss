package io.mehdieidi.modless.platform.assistant.delta;

import com.fasterxml.jackson.databind.JsonNode;
import io.mehdieidi.modless.platform.assistant.domain.SemanticModelPatch;
import io.mehdieidi.modless.platform.assistant.patch.AssistantMetamodelSchemaService;
import io.mehdieidi.modless.platform.kernel.ModelLevel;
import java.util.List;
import java.util.Map;

/** Deterministically lowers ModelDelta to backend executable semantic operations. */
public class DeltaCompiler {

  private final DeltaPatchCompiler compiler;
  private final AssistantMetamodelSchemaService schemas;

  public DeltaCompiler(AssistantMetamodelSchemaService schemas) {
    this.schemas = schemas;
    this.compiler = new DeltaPatchCompiler(schemas);
  }

  /** Compiles a ModelDelta into the existing backend-owned operation IR. */
  public SemanticModelPatch compile(
      ModelLevel level, JsonNode baseModel, Map<String, String> existingTypes, ModelDelta delta) {
    if (delta == null) {
      return new SemanticModelPatch(List.of());
    }
    ModelDelta normalized = new DeltaNormalizer(schemas).normalize(level, delta);
    return compiler.compile(level, baseModel, existingTypes, toDraft(normalized));
  }

  private DeltaPatchCompiler.PatchDraft toDraft(ModelDelta delta) {
    return new DeltaPatchCompiler.PatchDraft(
        delta.elements().stream().map(this::toElement).toList(),
        delta.references().stream().map(this::toReference).toList(),
        delta.attributeUpdates().stream().map(this::toAttributeUpdate).toList(),
        delta.deletions().stream().map(this::toDeletion).toList());
  }

  private DeltaPatchCompiler.Element toElement(ModelDelta.Element element) {
    ModelDelta.Placement placement = element.placement();
    return new DeltaPatchCompiler.Element(
        element.localId(),
        element.eClass(),
        element.attributes(),
        placement == null
            ? null
            : new DeltaPatchCompiler.Containment(placement.ownerId(), placement.referenceName()),
        element.references().stream().map(this::toReference).toList());
  }

  private DeltaPatchCompiler.Reference toReference(ModelDelta.Reference reference) {
    return new DeltaPatchCompiler.Reference(
        reference.sourceId(), reference.referenceName(), reference.targetId());
  }

  private DeltaPatchCompiler.AttributeUpdate toAttributeUpdate(ModelDelta.AttributeUpdate update) {
    return new DeltaPatchCompiler.AttributeUpdate(
        update.elementId(), update.attributeName(), update.value());
  }

  private DeltaPatchCompiler.Deletion toDeletion(ModelDelta.Deletion deletion) {
    return new DeltaPatchCompiler.Deletion(deletion.elementId());
  }
}
