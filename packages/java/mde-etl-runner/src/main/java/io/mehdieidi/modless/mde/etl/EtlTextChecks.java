package io.mehdieidi.modless.mde.etl;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.eclipse.emf.common.util.Enumerator;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.epsilon.eol.execute.introspection.IUndefined;

public final class EtlTextChecks {

    private static final String[] PROVIDER_SPECIFIC_TERMS = {
            "lambda",
            "dynamodb",
            "sqs",
            "sns",
            "eventbridge",
            "api gateway",
            "step functions",
            "cloudwatch",
            "iam",
            "cloudformation",
            "azure functions",
            "google cloud functions"
    };

    private final Map<String, String> slugs = new HashMap<>();
    private final Map<String, String> camelCaseValues = new HashMap<>();
    private final Map<String, String> pascalCaseValues = new HashMap<>();

    public String asText(Object value) {
        if (isUndefined(value)) {
            return "";
        }
        if (value instanceof Enumerator enumerator) {
            return enumerator.getName();
        }
        return value.toString();
    }

    public boolean hasText(Object value) {
        return !asText(value).trim().isEmpty();
    }

    public String firstText(Object primary, Object fallback) {
        String primaryText = asText(primary);
        if (!primaryText.trim().isEmpty()) {
            return primaryText;
        }
        String fallbackText = asText(fallback);
        return fallbackText.trim().isEmpty() ? "" : fallbackText;
    }

    public String slug(Object value) {
        String text = asText(value);
        return slugs.computeIfAbsent(text, ignored -> {
            String slug = text.toLowerCase(Locale.ROOT).trim()
                    .replaceAll("[^a-z0-9]+", "_")
                    .replaceAll("^_+|_+$", "");
            return slug.isEmpty() ? "unnamed" : slug;
        });
    }

    public String kebab(Object value) {
        return slug(value).replace('_', '-');
    }

    public String camelCase(Object value) {
        String text = asText(value);
        return camelCaseValues.computeIfAbsent(text, ignored -> {
            String[] parts = slug(text).split("_");
            StringBuilder result = new StringBuilder(parts[0]);
            for (int index = 1; index < parts.length; index++) {
                String part = parts[index];
                if (!part.isEmpty()) {
                    result.append(Character.toUpperCase(part.charAt(0)))
                            .append(part, 1, part.length());
                }
            }
            return result.toString();
        });
    }

    public String pascalCase(Object value) {
        String text = asText(value);
        return pascalCaseValues.computeIfAbsent(text, ignored -> {
            String camel = camelCase(text);
            if (camel.isEmpty()) {
                return "Unnamed";
            }
            return Character.toUpperCase(camel.charAt(0)) + camel.substring(1);
        });
    }

    public String escapeJson(Object value) {
        return asText(value).replace("\"", "\\\"");
    }

    public boolean containsProviderSpecificTerm(Object value) {
        if (isUndefined(value)) {
            return false;
        }
        return containsProviderSpecificTerm(asText(value));
    }

    public boolean containsProviderSpecificTerm(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String normalized = text.toLowerCase(Locale.ROOT);
        for (String term : PROVIDER_SPECIFIC_TERMS) {
            if (normalized.contains(term)) {
                return true;
            }
        }
        return false;
    }

    public boolean containsProviderSpecificTermIn(Object element) {
        if (element instanceof EObject object) {
            return containsFeatureTerm(object, "name")
                    || containsFeatureTerm(object, "description")
                    || containsFeatureTerm(object, "summary");
        }
        return containsProviderSpecificTerm(element);
    }

    private boolean containsFeatureTerm(EObject object, String featureName) {
        EStructuralFeature feature = object.eClass().getEStructuralFeature(featureName);
        if (feature == null) {
            return false;
        }
        Object value = object.eGet(feature);
        return value != null && containsProviderSpecificTerm(value.toString());
    }

    private boolean isUndefined(Object value) {
        return value == null || value instanceof IUndefined;
    }
}
