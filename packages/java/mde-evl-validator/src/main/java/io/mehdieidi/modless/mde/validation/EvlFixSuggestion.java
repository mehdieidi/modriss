package io.mehdieidi.modless.mde.validation;

public record EvlFixSuggestion(String title) {

    public EvlFixSuggestion {
        title = title == null ? "" : title;
    }
}
