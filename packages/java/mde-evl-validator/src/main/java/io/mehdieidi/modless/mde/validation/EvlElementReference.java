package io.mehdieidi.modless.mde.validation;

import java.util.Map;

public record EvlElementReference(
        String modelType,
        String uri,
        String uriFragment,
        Map<String, String> attributes,
        String summary) {

    public EvlElementReference {
        modelType = modelType == null ? "" : modelType;
        uri = uri == null ? "" : uri;
        uriFragment = uriFragment == null ? "" : uriFragment;
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
        summary = summary == null ? "" : summary;
    }
}
