package io.mehdieidi.varka.platform.assistant.metamodel;

import io.mehdieidi.varka.platform.assistant.metamodel.MetamodelKnowledgeService.AttributeContract;
import io.mehdieidi.varka.platform.assistant.metamodel.MetamodelKnowledgeService.ReferenceContract;
import io.mehdieidi.varka.platform.assistant.metamodel.MetamodelKnowledgeService.TypeContract;
import io.mehdieidi.varka.platform.kernel.ModelLevel;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/** Generates the deterministic, compact language reference placed in an agent system prompt. */
public final class MetamodelGuideGenerator {

  private final MetamodelKnowledgeService knowledge;
  private final Map<String, String> cache = new ConcurrentHashMap<>();

  public MetamodelGuideGenerator(MetamodelKnowledgeService knowledge) {
    this.knowledge = java.util.Objects.requireNonNull(knowledge, "knowledge");
  }

  /** Returns a stable guide cached by level and metamodel hash. */
  public String generate(ModelLevel level) {
    return cache.computeIfAbsent(
        level.name() + ":" + knowledge.metamodelHash(level), ignored -> render(level));
  }

  /**
   * Machine-oriented rendering used by the conceptual instance workflow. It retains every Ecore
   * fact, but removes prose and indentation so a large DSML does not consume the model's generation
   * budget. The normal agent continues to use {@link #generate(ModelLevel)}.
   */
  public String generateCompact(ModelLevel level) {
    return cache.computeIfAbsent(
        level.name() + ":" + knowledge.metamodelHash(level) + ":compact",
        ignored -> renderCompact(level));
  }

  /**
   * Retrieval-oriented contract guide for the conceptual workflow. The complete exact type index
   * remains available, while full feature contracts are reserved for types related to the request
   * and their immediate Ecore targets. This keeps provider latency bounded on large DSMLs without
   * inventing model content or aliases.
   */
  public String generateRelevant(ModelLevel level, String request) {
    String key =
        level.name()
            + ":"
            + knowledge.metamodelHash(level)
            + ":relevant:"
            + (request == null ? "" : request.trim().toLowerCase(java.util.Locale.ROOT));
    return cache.computeIfAbsent(key, ignored -> renderRelevant(level, request));
  }

  /** Returns a compact type index for levels whose full guide is too large for the prompt. */
  public String index(ModelLevel level) {
    return knowledge.typeContracts(level).stream()
        .filter(TypeContract::creatable)
        .map(TypeContract::eClass)
        .sorted()
        .collect(Collectors.joining(", ", level.name() + " types: ", ""));
  }

  /**
   * Renders complete feature contracts for an LLM-selected set and its deterministic Ecore
   * construction closure. Selection remains an LLM semantic decision; this method only serializes
   * authoritative contracts and never invents model content.
   */
  public String generateForTypes(ModelLevel level, List<TypeContract> selected) {
    java.util.LinkedHashMap<String, TypeContract> unique = new java.util.LinkedHashMap<>();
    if (selected != null) selected.forEach(type -> unique.putIfAbsent(type.eClass(), type));
    StringBuilder guide =
        new StringBuilder("root=")
            .append(knowledge.rootType(level))
            .append(";selectedTypes=")
            .append(String.join(",", unique.keySet()));
    unique.values().stream()
        .sorted(Comparator.comparing(TypeContract::eClass))
        .forEach(type -> appendCompactType(guide, type));
    return guide.toString();
  }

  /** Returns whether a name is an exact EClass in the authoritative level metamodel. */
  public boolean isKnownType(ModelLevel level, String name) {
    if (name == null || name.isBlank()) return false;
    return knowledge.typeContracts(level).stream()
        .anyMatch(type -> type.eClass().equals(name.trim()));
  }

  private String render(ModelLevel level) {
    StringBuilder guide = new StringBuilder(4096);
    guide
        .append("# ")
        .append(level.name())
        .append(" modeling language\n")
        .append("Root: ")
        .append(knowledge.rootType(level))
        .append('\n')
        .append(
            "Use exact case-sensitive type, feature, and enum names. Required features are marked"
                + " *.\n\n");
    knowledge.typeContracts(level).stream()
        .filter(TypeContract::creatable)
        .sorted(Comparator.comparing(TypeContract::eClass))
        .forEach(type -> appendType(guide, type));
    return guide.toString().trim();
  }

  private String renderCompact(ModelLevel level) {
    StringBuilder guide = new StringBuilder(4096);
    guide.append("root=").append(knowledge.rootType(level)).append(';');
    knowledge.typeContracts(level).stream()
        .filter(TypeContract::creatable)
        .sorted(Comparator.comparing(TypeContract::eClass))
        .forEach(type -> appendCompactType(guide, type));
    return guide.toString();
  }

  private void appendCompactType(StringBuilder guide, TypeContract type) {
    guide.append('\n').append(type.eClass());
    if (!type.supertypes().isEmpty()) guide.append('<').append(String.join(",", type.supertypes()));
    if (!type.attributes().isEmpty())
      guide
          .append("|a:")
          .append(type.attributes().stream().map(this::attribute).collect(Collectors.joining(",")));
    List<ReferenceContract> containments =
        type.references().stream().filter(ReferenceContract::containment).toList();
    if (!containments.isEmpty())
      guide
          .append("|c:")
          .append(containments.stream().map(this::reference).collect(Collectors.joining(",")));
    List<ReferenceContract> links =
        type.references().stream().filter(reference -> !reference.containment()).toList();
    if (!links.isEmpty())
      guide
          .append("|r:")
          .append(links.stream().map(this::reference).collect(Collectors.joining(",")));
  }

  private String renderRelevant(ModelLevel level, String request) {
    List<TypeContract> allTypes =
        knowledge.typeContracts(level).stream()
            .sorted(Comparator.comparing(TypeContract::eClass))
            .toList();
    List<TypeContract> types = allTypes.stream().filter(TypeContract::creatable).toList();
    java.util.Set<String> terms =
        java.util.Arrays.stream(
                (request == null ? "" : request)
                    .toLowerCase(java.util.Locale.ROOT)
                    .split("[^a-z0-9]+"))
            .filter(term -> term.length() > 2)
            .collect(java.util.stream.Collectors.toSet());
    java.util.Set<String> selected = new java.util.LinkedHashSet<>();
    types.stream()
        .sorted(
            Comparator.comparingInt((TypeContract type) -> relevance(type, terms))
                .reversed()
                .thenComparing(TypeContract::eClass))
        .limit(6)
        .forEach(type -> selected.add(type.eClass()));
    selected.add(knowledge.rootType(level));
    // The complete Ecore type index remains available above; feature contracts are intentionally
    // bounded here because some PIM types contain very large enum/reference surfaces.
    StringBuilder guide =
        new StringBuilder("root=")
            .append(knowledge.rootType(level))
            .append(";types=")
            .append(types.stream().map(TypeContract::eClass).collect(Collectors.joining(",")));
    allTypes.stream()
        .filter(type -> selected.contains(type.eClass()))
        .forEach(
            type -> {
              guide.append('\n').append(type.eClass());
              if (!type.supertypes().isEmpty())
                guide.append('<').append(String.join(",", type.supertypes()));
              if (!type.attributes().isEmpty())
                guide
                    .append("|a:")
                    .append(
                        type.attributes().stream()
                            .map(this::attribute)
                            .collect(Collectors.joining(",")));
              List<ReferenceContract> containments =
                  type.references().stream().filter(ReferenceContract::containment).toList();
              if (!containments.isEmpty())
                guide
                    .append("|c:")
                    .append(
                        containments.stream()
                            .map(this::reference)
                            .collect(Collectors.joining(",")));
              List<ReferenceContract> links =
                  type.references().stream().filter(reference -> !reference.containment()).toList();
              if (!links.isEmpty())
                guide
                    .append("|r:")
                    .append(links.stream().map(this::reference).collect(Collectors.joining(",")));
            });
    return guide.toString();
  }

  private int relevance(TypeContract type, java.util.Set<String> terms) {
    String text =
        (type.eClass()
                + " "
                + String.join(" ", type.supertypes())
                + " "
                + type.attributes().stream()
                    .map(AttributeContract::name)
                    .collect(Collectors.joining(" "))
                + " "
                + type.references().stream()
                    .map(ReferenceContract::name)
                    .collect(Collectors.joining(" ")))
            .toLowerCase(java.util.Locale.ROOT);
    return (int) terms.stream().filter(term -> text.contains(term)).count();
  }

  private void appendType(StringBuilder out, TypeContract type) {
    out.append(type.eClass());
    if (!type.supertypes().isEmpty()) {
      out.append(" < ").append(String.join(",", type.supertypes()));
    }
    out.append('\n');
    if (!type.attributes().isEmpty()) {
      out.append("  attributes: ");
      out.append(type.attributes().stream().map(this::attribute).collect(Collectors.joining("; ")));
      out.append('\n');
    }
    List<ReferenceContract> containments =
        type.references().stream().filter(ReferenceContract::containment).toList();
    if (!containments.isEmpty()) {
      out.append("  contains: ")
          .append(containments.stream().map(this::reference).collect(Collectors.joining("; ")))
          .append('\n');
    }
    List<ReferenceContract> links =
        type.references().stream().filter(reference -> !reference.containment()).toList();
    if (!links.isEmpty()) {
      out.append("  references: ")
          .append(links.stream().map(this::reference).collect(Collectors.joining("; ")))
          .append('\n');
    }
  }

  private String attribute(AttributeContract attribute) {
    String value =
        attribute.name()
            + (attribute.required() ? "*" : "")
            + ":"
            + attribute.type()
            + (attribute.many() ? "[]" : "");
    return attribute.enumLiterals().isEmpty()
        ? value
        : value + "{" + String.join("|", attribute.enumLiterals()) + "}";
  }

  private String reference(ReferenceContract reference) {
    return reference.name()
        + (reference.required() ? "*" : "")
        + "->"
        + reference.targetType()
        + (reference.many() ? "[]" : "")
        + (reference.readonly() ? " (read-only)" : "");
  }
}
