package io.mehdieidi.modless.mde.validation;

import java.util.List;
import org.eclipse.epsilon.eol.exceptions.models.EolModelLoadingException;
import org.eclipse.epsilon.eol.models.IModel;

public interface EvlModelConfiguration {

    String name();

    IModel load() throws EolModelLoadingException;

    default List<EvlDiagnostic> validateLoadedModel(IModel model) {
        return List.of();
    }
}
