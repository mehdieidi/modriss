package io.mehdieidi.modless.mde.validation;

import org.eclipse.epsilon.eol.exceptions.models.EolModelLoadingException;
import org.eclipse.epsilon.eol.models.IModel;

public interface EvlModelConfiguration {

    String name();

    IModel load() throws EolModelLoadingException;
}
