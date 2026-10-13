package io.mehdieidi.modriss.mde.etl;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.eclipse.emf.common.util.Enumerator;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.epsilon.eol.execute.introspection.IUndefined;

/**
 * Text and naming helpers exposed to ETL modules for deterministic generated identifiers and policy
 * checks.
 */
public final class EtlTextChecks {

  /** Terms that indicate a CIM/PIM text value has leaked provider-specific concerns. */
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

  /** Cached slug conversions keyed by original text. */
  private final Map<String, String> slugs = new HashMap<>();

  /** Cached camel-case conversions keyed by original text. */
  private final Map<String, String> camelCaseValues = new HashMap<>();

  /** Cached Pascal-case conversions keyed by original text. */
  private final Map<String, String> pascalCaseValues = new HashMap<>();

  /**
   * Converts nullable, undefined, and Epsilon enum values to text.
   *
   * @param value value from an ETL script
   * @return normalized text, or an empty string for undefined values
   */
  public String asText(Object value) {
    if (isUndefined(value)) {
      return "";
    }
    if (value instanceof Enumerator enumerator) {
      return enumerator.getName();
    }
    return value.toString();
  }

  /**
   * Indicates whether the supplied value contains non-blank text.
   *
   * @param value value to inspect
   * @return {@code true} when normalized text is non-blank
   */
  public boolean hasText(Object value) {
    return !asText(value).trim().isEmpty();
  }

  /**
   * Returns the primary text value when present, otherwise the fallback text.
   *
   * @param primary preferred value
   * @param fallback fallback value
   * @return selected non-blank text or an empty string
   */
  public String firstText(Object primary, Object fallback) {
    String primaryText = asText(primary);
    if (!primaryText.trim().isEmpty()) {
      return primaryText;
    }
    String fallbackText = asText(fallback);
    return fallbackText.trim().isEmpty() ? "" : fallbackText;
  }

  /**
   * Converts a value to a stable lowercase underscore slug.
   *
   * @param value value to convert
   * @return slug suitable for generated identifiers
   */
  public String slug(Object value) {
    String text = asText(value);
    return slugs.computeIfAbsent(
        text,
        ignored -> {
          String slug =
              text.toLowerCase(Locale.ROOT)
                  .trim()
                  .replaceAll("[^a-z0-9]+", "_")
                  .replaceAll("^_+|_+$", "");
          return slug.isEmpty() ? "unnamed" : slug;
        });
  }

  /**
   * Converts a value to kebab-case.
   *
   * @param value value to convert
   * @return kebab-case identifier
   */
  public String kebab(Object value) {
    return slug(value).replace('_', '-');
  }

  /**
   * Converts a value to lower camel case.
   *
   * @param value value to convert
   * @return camel-case identifier
   */
  public String camelCase(Object value) {
    String text = asText(value);
    return camelCaseValues.computeIfAbsent(
        text,
        ignored -> {
          String normalized =
              text.trim()
                  .replaceAll("([a-z0-9])([A-Z])", "$1 $2")
                  .replaceAll("[^A-Za-z0-9]+", " ")
                  .trim();
          if (normalized.isEmpty()) {
            return "unnamed";
          }
          String[] parts = normalized.split("\\s+");
          StringBuilder result = new StringBuilder(parts[0].toLowerCase(Locale.ROOT));
          for (int index = 1; index < parts.length; index++) {
            String part = parts[index].toLowerCase(Locale.ROOT);
            if (!part.isEmpty()) {
              result.append(Character.toUpperCase(part.charAt(0))).append(part, 1, part.length());
            }
          }
          return result.toString();
        });
  }

  /**
   * Converts a value to Pascal case.
   *
   * @param value value to convert
   * @return Pascal-case identifier
   */
  public String pascalCase(Object value) {
    String text = asText(value);
    return pascalCaseValues.computeIfAbsent(
        text,
        ignored -> {
          String camel = camelCase(text);
          if (camel.isEmpty()) {
            return "Unnamed";
          }
          return Character.toUpperCase(camel.charAt(0)) + camel.substring(1);
        });
  }

  /**
   * Escapes double quotes for JSON fragments generated from ETL.
   *
   * @param value value to escape
   * @return JSON-safe string fragment
   */
  public String escapeJson(Object value) {
    return asText(value).replace("\"", "\\\"");
  }

  /**
   * Checks whether a value contains a provider-specific term.
   *
   * @param value value to inspect
   * @return {@code true} when a provider term is present
   */
  public boolean containsProviderSpecificTerm(Object value) {
    if (isUndefined(value)) {
      return false;
    }
    return containsProviderSpecificTerm(asText(value));
  }

  /**
   * Checks whether text contains a provider-specific term.
   *
   * @param text text to inspect
   * @return {@code true} when a provider term is present
   */
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

  /**
   * Checks an EMF element's common descriptive fields, or a scalar value directly, for
   * provider-specific terms.
   *
   * @param element EMF element or scalar value
   * @return {@code true} when a provider term is present
   */
  public boolean containsProviderSpecificTermIn(Object element) {
    if (element instanceof EObject object) {
      return containsFeatureTerm(object, "name")
          || containsFeatureTerm(object, "description")
          || containsFeatureTerm(object, "summary");
    }
    return containsProviderSpecificTerm(element);
  }

  /**
   * Checks a named EMF feature for provider-specific terms.
   *
   * @param object EMF object to inspect
   * @param featureName feature name to read
   * @return {@code true} when the feature contains a provider term
   */
  private boolean containsFeatureTerm(EObject object, String featureName) {
    EStructuralFeature feature = object.eClass().getEStructuralFeature(featureName);
    if (feature == null) {
      return false;
    }
    Object value = object.eGet(feature);
    return value != null && containsProviderSpecificTerm(value.toString());
  }

  /**
   * Identifies null and Epsilon undefined sentinel values.
   *
   * @param value value to inspect
   * @return {@code true} when the value is absent
   */
  private boolean isUndefined(Object value) {
    return value == null || value instanceof IUndefined;
  }
}
