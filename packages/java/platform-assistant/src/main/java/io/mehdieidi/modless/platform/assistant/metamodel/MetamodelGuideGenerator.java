package io.mehdieidi.modless.platform.assistant.metamodel;

import io.mehdieidi.modless.platform.assistant.metamodel.MetamodelKnowledgeService.AttributeContract;
import io.mehdieidi.modless.platform.assistant.metamodel.MetamodelKnowledgeService.ReferenceContract;
import io.mehdieidi.modless.platform.assistant.metamodel.MetamodelKnowledgeService.TypeContract;
import io.mehdieidi.modless.platform.kernel.ModelLevel;
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

  /** Returns a compact type index for levels whose full guide is too large for the prompt. */
  public String index(ModelLevel level) {
    return knowledge.typeContracts(level).stream()
        .filter(TypeContract::creatable)
        .map(TypeContract::eClass)
        .sorted()
        .collect(Collectors.joining(", ", level.name() + " types: ", ""));
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
    String value = attribute.name() + (attribute.required() ? "*" : "") + ":" + attribute.type();
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
