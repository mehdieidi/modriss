package io.mehdieidi.varka.platform.assistant.patch;

import io.mehdieidi.varka.platform.kernel.ModelLevel;
import io.mehdieidi.varka.platform.modeling.metamodel.MetamodelResolver;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EEnum;

/**
 * Immutable structural contract derived only from the active combined Ecore resource.
 *
 * <p>The resolver hashes the actual Ecore bytes. A changed hash replaces the cached graph, so UI
 * labels and CVS metadata can never mask structural metamodel drift.
 */
public final class MetamodelContractGraph {
  private final MetamodelResolver resolver;
  private final Map<ModelLevel, Snapshot> cache = new ConcurrentHashMap<>();

  public MetamodelContractGraph(MetamodelResolver resolver) {
    this.resolver = resolver;
  }

  /** Returns a graph rebuilt whenever the combined Ecore SHA changes. */
  public Snapshot forLevel(ModelLevel level) {
    var descriptor = resolver.resolve(level);
    Snapshot existing = cache.get(level);
    if (existing != null && existing.sha256().equals(descriptor.sha256())) return existing;
    Snapshot rebuilt =
        build(
            descriptor.sha256(),
            descriptor.packages().stream()
                .flatMap(ePackage -> ePackage.getEClassifiers().stream())
                .filter(EClass.class::isInstance)
                .map(EClass.class::cast)
                .toList());
    cache.put(level, rebuilt);
    return rebuilt;
  }

  private Snapshot build(String sha256, List<EClass> classes) {
    Map<String, Type> types = new LinkedHashMap<>();
    Map<String, EnumType> enums = new LinkedHashMap<>();
    Map<String, List<ContainmentRoute>> routes = new LinkedHashMap<>();
    classes.stream()
        .sorted(Comparator.comparing(EClass::getName))
        .forEach(
            type -> {
              List<Attribute> attributes =
                  type.getEAllAttributes().stream()
                      .map(
                          attribute -> {
                            if (attribute.getEAttributeType() instanceof EEnum enumeration) {
                              enums.putIfAbsent(
                                  enumeration.getName(),
                                  new EnumType(
                                      enumeration.getName(),
                                      enumeration.getELiterals().stream()
                                          .map(literal -> literal.getLiteral())
                                          .toList()));
                            }
                            return new Attribute(
                                attribute.getName(),
                                attribute.getEAttributeType().getName(),
                                attribute.getLowerBound(),
                                attribute.getUpperBound(),
                                attribute.isChangeable(),
                                attribute.getDefaultValueLiteral(),
                                attribute.getEAttributeType().getInstanceClassName());
                          })
                      .toList();
              List<Reference> references =
                  type.getEAllReferences().stream()
                      .map(
                          reference -> {
                            if (reference.isContainment()) {
                              routes
                                  .computeIfAbsent(
                                      reference.getEReferenceType().getName(),
                                      ignored -> new java.util.ArrayList<>())
                                  .add(
                                      new ContainmentRoute(
                                          type.getName(),
                                          reference.getName(),
                                          reference.getEReferenceType().getName()));
                            }
                            return new Reference(
                                reference.getName(),
                                reference.getEReferenceType().getName(),
                                reference.getLowerBound(),
                                reference.getUpperBound(),
                                reference.isContainment(),
                                reference.isChangeable(),
                                reference.isDerived(),
                                reference.isTransient(),
                                reference.getEOpposite() == null
                                    ? null
                                    : reference.getEOpposite().getName());
                          })
                      .toList();
              types.put(
                  type.getName(),
                  new Type(
                      type.getName(),
                      type.isAbstract(),
                      type.getEAllSuperTypes().stream().map(EClass::getName).sorted().toList(),
                      attributes,
                      references,
                      attributes.stream()
                          .filter(
                              attribute ->
                                  attribute.lowerBound() > 0
                                      && (attribute.defaultLiteral() == null
                                          || attribute.defaultLiteral().isBlank()))
                          .map(Attribute::name)
                          .toList(),
                      references.stream()
                          .filter(
                              reference -> reference.lowerBound() > 0 && reference.containment())
                          .map(Reference::name)
                          .toList(),
                      references.stream()
                          .filter(
                              reference -> reference.lowerBound() > 0 && !reference.containment())
                          .map(Reference::name)
                          .toList()));
            });
    List<String> rootTypes =
        types.keySet().stream().filter(type -> !routes.containsKey(type)).sorted().toList();
    return new Snapshot(
        sha256,
        Map.copyOf(types),
        Map.copyOf(enums),
        rootTypes,
        routes.entrySet().stream()
            .collect(
                java.util.stream.Collectors.toUnmodifiableMap(
                    Map.Entry::getKey, entry -> List.copyOf(entry.getValue()))));
  }

  /** One Ecore-byte-versioned metamodel graph. */
  public record Snapshot(
      String sha256,
      Map<String, Type> types,
      Map<String, EnumType> enums,
      List<String> rootTypes,
      Map<String, List<ContainmentRoute>> containmentRoutes) {}

  /** EClass structural contract. */
  public record Type(
      String name,
      boolean abstractType,
      List<String> supertypes,
      List<Attribute> attributes,
      List<Reference> references,
      List<String> requiredAttributes,
      List<String> requiredContainments,
      List<String> requiredReferences) {}

  /** EAttribute structural contract. */
  public record Attribute(
      String name,
      String dataType,
      int lowerBound,
      int upperBound,
      boolean changeable,
      String defaultLiteral,
      String instanceClassName) {}

  /** EReference structural contract. */
  public record Reference(
      String name,
      String targetType,
      int lowerBound,
      int upperBound,
      boolean containment,
      boolean changeable,
      boolean derived,
      boolean transientFeature,
      String opposite) {}

  /** Exact serialized EEnum literals, never display labels. */
  public record EnumType(String name, List<String> literals) {}

  /** A direct containment edge; callers may compose these into valid owner paths. */
  public record ContainmentRoute(String ownerType, String reference, String targetType) {}
}
