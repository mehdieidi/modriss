package io.mehdieidi.varka.mde.validation;

import java.util.List;
import org.eclipse.epsilon.eol.exceptions.models.EolModelLoadingException;
import org.eclipse.epsilon.eol.models.IModel;

/**
 * Strategy for loading an Epsilon model and optionally producing structural diagnostics after load.
 */
public interface EvlModelConfiguration {

  /**
   * Returns the primary Epsilon model name.
   *
   * @return model name visible to EVL modules
   */
  String name();

  /**
   * Loads the model into the Epsilon model repository.
   *
   * @return loaded Epsilon model
   * @throws EolModelLoadingException when the model cannot be loaded
   */
  IModel load() throws EolModelLoadingException;

  /**
   * Performs optional structural validation after the model has loaded.
   *
   * @param model loaded model instance
   * @return diagnostics discovered from the loaded model
   */
  default List<EvlDiagnostic> validateLoadedModel(IModel model) {
    return List.of();
  }
}
