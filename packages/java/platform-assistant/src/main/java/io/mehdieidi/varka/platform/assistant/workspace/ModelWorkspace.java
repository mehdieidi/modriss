package io.mehdieidi.varka.platform.assistant.workspace;

import io.mehdieidi.varka.platform.assistant.domain.SemanticModelPatch;
import io.mehdieidi.varka.platform.assistant.patch.AssistantPatchCompiler;
import io.mehdieidi.varka.platform.identity.domain.UserRecord;
import io.mehdieidi.varka.platform.kernel.ModelLevel;
import io.mehdieidi.varka.platform.model.application.ModelService;
import io.mehdieidi.varka.platform.model.domain.ModelRecord;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/** Isolated per-turn model working copy with reversible, Ecore-aware mutations. */
public final class ModelWorkspace {

  private final ModelLevel level;
  private final String modelId;
  private final long baseRevision;
  private final AssistantPatchCompiler compiler;
  private final ModelService structuralValidator;
  private final Consumer<DeltaEvent> deltaSink;
  private final List<ModelService.ModelPatchOperation> patch = new ArrayList<>();
  private final List<ModelService.ModelPatchOperation> inversePatch = new ArrayList<>();
  private final List<String> affectedElementIds = new ArrayList<>();
  private ObjectNode model;

  public ModelWorkspace(
      ModelLevel level,
      String modelId,
      long baseRevision,
      JsonNode model,
      AssistantPatchCompiler compiler,
      Consumer<DeltaEvent> deltaSink) {
    this(level, modelId, baseRevision, model, compiler, null, deltaSink);
  }

  /** Creates a workspace that validates every complete candidate draft before accepting it. */
  public ModelWorkspace(
      ModelLevel level,
      String modelId,
      long baseRevision,
      JsonNode model,
      AssistantPatchCompiler compiler,
      ModelService structuralValidator,
      Consumer<DeltaEvent> deltaSink) {
    this.level = Objects.requireNonNull(level, "level");
    this.modelId = Objects.requireNonNull(modelId, "modelId");
    this.baseRevision = baseRevision;
    this.compiler = Objects.requireNonNull(compiler, "compiler");
    this.structuralValidator = structuralValidator;
    if (model == null || !model.isObject())
      throw new IllegalArgumentException("model must be a JSON object");
    this.model = ((ObjectNode) model).deepCopy();
    this.deltaSink = deltaSink == null ? ignored -> {} : deltaSink;
  }

  public synchronized MutationResult mutate(SemanticModelPatch operations) {
    AssistantPatchCompiler.CompiledPatch compiled = compiler.compile(model, operations);
    return mutate(compiled);
  }

  /** Applies a complete precompiled structural batch to this isolated workspace. */
  public synchronized MutationResult mutate(AssistantPatchCompiler.CompiledPatch compiled) {
    ObjectNode candidate = compiler.apply(model, compiled);
    if (structuralValidator != null) {
      ModelService.ValidationResult validation =
          structuralValidator.validateStructural(level, candidate);
      if (validation == null || !validation.valid()) {
        throw new io.mehdieidi.varka.platform.kernel.PlatformException(
            422,
            "Assistant draft patch was rejected by Ecore validation: "
                + (validation == null ? "no validation result" : validation.issues()));
      }
    }
    model = candidate;
    patch.addAll(compiled.patch());
    inversePatch.addAll(0, compiled.inversePatch());
    compiled.affectedElements().stream()
        .filter(id -> !affectedElementIds.contains(id))
        .forEach(affectedElementIds::add);
    DeltaEvent event =
        new DeltaEvent(compiled.patch(), compiled.affectedElements(), model.deepCopy());
    deltaSink.accept(event);
    return new MutationResult(
        compiled.affectedElements(), compiled.patch().size(), model.deepCopy());
  }

  public synchronized ObjectNode snapshot() {
    return model.deepCopy();
  }

  public synchronized List<ModelService.ModelPatchOperation> patch() {
    return List.copyOf(patch);
  }

  public synchronized List<ModelService.ModelPatchOperation> inversePatch() {
    return List.copyOf(inversePatch);
  }

  public synchronized List<String> affectedElementIds() {
    return List.copyOf(affectedElementIds);
  }

  /** Atomically commits all workspace mutations under ModelService's revision lock. */
  public synchronized ModelRecord apply(ModelService models, UserRecord user) {
    return models.patchStructurallyValid(user, level, modelId, null, patch(), baseRevision);
  }

  public record MutationResult(
      List<String> affectedElementIds, int patchOperations, JsonNode model) {}

  public record DeltaEvent(
      List<ModelService.ModelPatchOperation> operations,
      List<String> affectedElementIds,
      JsonNode model) {}
}
