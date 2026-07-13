package io.mehdieidi.varka.backend.assistant;

import io.mehdieidi.varka.platform.assistant.turn.AssistantTurn;
import io.mehdieidi.varka.platform.assistant.turn.AssistantTurnStore;
import io.mehdieidi.varka.platform.identity.domain.UserRecord;
import io.mehdieidi.varka.platform.kernel.PlatformException;
import io.mehdieidi.varka.platform.model.application.ModelService;
import io.mehdieidi.varka.platform.model.application.ModelService.ModelPatchOperation;
import java.util.List;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/** Applies a turn checkpoint inverse only against the checkpoint's exact current revision. */
@Component
public final class DurableTurnUndoService {
  private final AssistantTurnStore turns;
  private final ModelService models;
  private final ObjectMapper mapper;

  public DurableTurnUndoService(
      AssistantTurnStore turns, ModelService models, ObjectMapper mapper) {
    this.turns = turns;
    this.models = models;
    this.mapper = mapper;
  }

  public UndoResult undo(UserRecord user, AssistantTurn turn) {
    var checkpoint =
        turns
            .latestCheckpoint(turn.id())
            .orElseThrow(
                () -> new PlatformException(409, "This turn has no reversible checkpoint."));
    try {
      List<ModelPatchOperation> inverse =
          mapper.readValue(
              checkpoint.inversePatch().toString(),
              new TypeReference<List<ModelPatchOperation>>() {});
      if (inverse.isEmpty())
        throw new PlatformException(409, "This checkpoint has no inverse patch.");
      var model = models.get(user, turn.level(), checkpoint.modelId());
      if (model.revision() != checkpoint.revision())
        throw new PlatformException(409, "Model changed after this checkpoint; undo is unsafe.");
      var updated =
          models.patch(user, turn.level(), model.id(), model.name(), inverse, model.revision());
      turns.appendEvent(
          turn.id(),
          "model.checkpoint",
          java.util.Map.of(
              "kind", "undo", "modelId", updated.id(), "revision", updated.revision()));
      return new UndoResult(updated.id(), updated.revision());
    } catch (PlatformException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new PlatformException(409, "Could not decode turn inverse checkpoint.");
    }
  }

  public record UndoResult(String modelId, long revision) {}
}
