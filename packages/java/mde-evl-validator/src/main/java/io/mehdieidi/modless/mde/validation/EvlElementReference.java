package io.mehdieidi.modless.mde.validation;

import java.util.Map;

/**
 * Safe, compact description of the model element associated with an EVL violation.
 *
 * @param modelType   EMF classifier or Java type name
 * @param uri         resource URI, when available
 * @param uriFragment resource-local URI fragment, when available
 * @param attributes  safe identifying attributes copied from the model element
 * @param summary     human-readable fallback description
 */
public record EvlElementReference(
        String modelType,
        String uri,
        String uriFragment,
        Map<String, String> attributes,
        String summary) {

    /**
     * Normalizes nullable textual fields and defensively copies attributes.
     */
    public EvlElementReference {
        modelType = modelType == null ? "" : modelType;
        uri = uri == null ? "" : uri;
        uriFragment = uriFragment == null ? "" : uriFragment;
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
        summary = summary == null ? "" : summary;
    }
}
