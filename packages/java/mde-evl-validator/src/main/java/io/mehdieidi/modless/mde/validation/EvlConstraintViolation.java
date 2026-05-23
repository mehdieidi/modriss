package io.mehdieidi.modless.mde.validation;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record EvlConstraintViolation(
        EvlConstraintKind kind,
        String constraintName,
        String contextType,
        String message,
        Path file,
        int line,
        int column,
        EvlElementReference element,
        List<EvlFixSuggestion> fixes,
        Map<String, String> extras) {

    public EvlConstraintViolation {
        Objects.requireNonNull(kind, "kind");
        constraintName = constraintName == null ? "" : constraintName;
        contextType = contextType == null ? "" : contextType;
        message = message == null ? "" : message;
        element = element == null ? new EvlElementReference("", "", "", Map.of(), "") : element;
        fixes = fixes == null ? List.of() : List.copyOf(fixes);
        extras = extras == null ? Map.of() : Map.copyOf(extras);
    }
}
