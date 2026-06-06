package io.mehdieidi.modless.mde.validation;

/**
 * User-facing title of an EVL fix that was available for a violation.
 *
 * @param title evaluated EVL fix title
 */
public record EvlFixSuggestion(String title) {

    /**
     * Normalizes a nullable title to an empty string.
     */
    public EvlFixSuggestion {
        title = title == null ? "" : title;
    }
}
