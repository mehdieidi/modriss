package io.mehdieidi.modless.mde.validation;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Structured representation of an unsatisfied EVL constraint or critique.
 *
 * @param kind           mandatory or optional violation kind
 * @param constraintName EVL constraint name
 * @param contextType    EVL context type
 * @param message        evaluated violation message
 * @param file           EVL file that declared the constraint, when known
 * @param line           one-based source line, or {@code -1} when unavailable
 * @param column         one-based source column, or {@code -1} when unavailable
 * @param element        model element that violated the constraint
 * @param fixes          available EVL fix suggestions
 * @param extras         extra values supplied by EVL
 */
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

    /**
     * Normalizes nullable fields and defensively copies collections.
     */
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
