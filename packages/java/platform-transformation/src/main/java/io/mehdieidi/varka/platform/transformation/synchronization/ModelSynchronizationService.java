package io.mehdieidi.varka.platform.transformation.synchronization;

import com.google.common.base.Predicate;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.eclipse.emf.common.util.BasicMonitor;
import org.eclipse.emf.common.util.Enumerator;
import org.eclipse.emf.compare.AttributeChange;
import org.eclipse.emf.compare.Comparison;
import org.eclipse.emf.compare.Conflict;
import org.eclipse.emf.compare.ConflictKind;
import org.eclipse.emf.compare.Diff;
import org.eclipse.emf.compare.DifferenceKind;
import org.eclipse.emf.compare.DifferenceSource;
import org.eclipse.emf.compare.EMFCompare;
import org.eclipse.emf.compare.Match;
import org.eclipse.emf.compare.ReferenceChange;
import org.eclipse.emf.compare.match.IMatchEngine;
import org.eclipse.emf.compare.match.impl.MatchEngineFactoryImpl;
import org.eclipse.emf.compare.match.impl.MatchEngineFactoryRegistryImpl;
import org.eclipse.emf.compare.merge.BatchMerger;
import org.eclipse.emf.compare.merge.IBatchMerger;
import org.eclipse.emf.compare.merge.IMerger;
import org.eclipse.emf.compare.scope.DefaultComparisonScope;
import org.eclipse.emf.compare.scope.IComparisonScope;
import org.eclipse.emf.compare.utils.UseIdentifiers;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.util.EcoreUtil;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.NullNode;

/** Performs standalone, identifier-only, three-way EMF Compare merges. */
public final class ModelSynchronizationService {

  private final ObjectMapper mapper;
  private final SemanticMergePolicy policy;
  private final EMFCompare emfCompare;
  private final IMerger.Registry mergerRegistry;

  public ModelSynchronizationService(ObjectMapper mapper) {
    this(mapper, new SemanticMergePolicy());
  }

  public ModelSynchronizationService(ObjectMapper mapper, SemanticMergePolicy policy) {
    this.mapper = Objects.requireNonNull(mapper, "mapper");
    this.policy = Objects.requireNonNull(policy, "policy");
    IMatchEngine.Factory.Registry matchRegistry =
        MatchEngineFactoryRegistryImpl.createStandaloneInstance();
    matchRegistry.clear();
    MatchEngineFactoryImpl identifierFactory = new MatchEngineFactoryImpl(UseIdentifiers.ONLY);
    identifierFactory.setRanking(1000);
    matchRegistry.add(identifierFactory);
    this.emfCompare = EMFCompare.builder().setMatchEngineFactoryRegistry(matchRegistry).build();
    this.mergerRegistry = IMerger.RegistryImpl.createStandaloneInstance();
  }

  /**
   * Compares {@code working} (left) and {@code newGenerated} (right) against {@code base} (origin),
   * then applies safe incoming changes to the working resource.
   */
  public MergeOutcome synchronize(
      Resource base, Resource working, Resource newGenerated, TransformationDirection direction) {
    return synchronize(base, working, newGenerated, direction, Map.of());
  }

  /** Re-runs a comparison and applies explicit decisions for real conflicts. */
  public MergeOutcome synchronize(
      Resource base,
      Resource working,
      Resource newGenerated,
      TransformationDirection direction,
      Map<String, ConflictResolution> resolutions) {
    Objects.requireNonNull(base, "base");
    Objects.requireNonNull(working, "working");
    Objects.requireNonNull(newGenerated, "newGenerated");
    Objects.requireNonNull(direction, "direction");
    Map<String, ConflictResolution> decisions =
        resolutions == null ? Map.of() : Map.copyOf(resolutions);

    IComparisonScope scope = new DefaultComparisonScope(working, newGenerated, base);
    Comparison comparison = emfCompare.compare(scope, new BasicMonitor());
    Map<Conflict, ModelConflict> realConflicts = describeRealConflicts(comparison, direction);
    // Keep EMF Compare's dependency order. Containment additions/deletions are represented by
    // several related diffs (container reference, child object, and nested references); feeding
    // BatchMerger a HashSet makes that order nondeterministic and can leave nested generated
    // objects behind or prevent an incoming subtree from being attached.
    Set<Diff> mergeable = new LinkedHashSet<>();
    int incoming = 0;
    int additions = 0;
    int deletions = 0;

    for (Diff difference : comparison.getDifferences()) {
      if (difference.getSource() != DifferenceSource.RIGHT) {
        continue;
      }
      MergeFeaturePolicy featurePolicy = policy.policyFor(feature(difference));
      if (featurePolicy == MergeFeaturePolicy.IGNORE
          || featurePolicy == MergeFeaturePolicy.USER_OWNED
          || featurePolicy == MergeFeaturePolicy.IMMUTABLE) {
        continue;
      }
      incoming++;
      if (difference.getKind() == DifferenceKind.ADD) {
        additions++;
      } else if (difference.getKind() == DifferenceKind.DELETE) {
        deletions++;
      }
      Conflict conflict = difference.getConflict();
      if (conflict == null || conflict.getKind() != ConflictKind.REAL) {
        mergeable.add(difference);
        continue;
      }
      ModelConflict description = realConflicts.get(conflict);
      ConflictResolution decision =
          description == null ? null : decisions.get(description.conflictId());
      if (featurePolicy == MergeFeaturePolicy.GENERATOR_OWNED
          || decision == ConflictResolution.TAKE_GENERATED) {
        mergeable.add(difference);
      }
    }

    if (!mergeable.isEmpty()) {
      Predicate<Diff> allowed = mergeable::contains;
      IBatchMerger merger = new BatchMerger(mergerRegistry, allowed);
      merger.copyAllRightToLeft(mergeable, new BasicMonitor());
    }

    List<ModelConflict> unresolved =
        realConflicts.values().stream()
            .filter(conflict -> !decisions.containsKey(conflict.conflictId()))
            .toList();
    int preserved =
        (int)
            comparison.getDifferences().stream()
                .filter(diff -> diff.getSource() == DifferenceSource.LEFT)
                .filter(diff -> policy.policyFor(feature(diff)) == MergeFeaturePolicy.THREE_WAY)
                .count();
    return new MergeOutcome(
        working,
        save(working),
        unresolved,
        List.copyOf(realConflicts.values()),
        incoming,
        preserved,
        mergeable.size(),
        additions,
        deletions);
  }

  private Map<Conflict, ModelConflict> describeRealConflicts(
      Comparison comparison, TransformationDirection direction) {
    Map<Conflict, ModelConflict> result = new HashMap<>();
    for (Conflict conflict : comparison.getConflicts()) {
      if (conflict.getKind() != ConflictKind.REAL) {
        continue;
      }
      List<Diff> relevant =
          conflict.getDifferences().stream()
              .filter(diff -> policy.policyFor(feature(diff)) == MergeFeaturePolicy.THREE_WAY)
              .toList();
      if (relevant.isEmpty()) {
        continue;
      }
      Diff representative =
          relevant.stream()
              .filter(diff -> diff.getSource() == DifferenceSource.RIGHT)
              .findFirst()
              .orElse(relevant.get(0));
      EStructuralFeature changedFeature = feature(representative);
      EObject element = conflictElement(representative);
      String elementId = id(element);
      String featureName = changedFeature == null ? "resource" : changedFeature.getName();
      String stableKey =
          relevant.stream()
              .map(this::differenceKey)
              .sorted()
              .reduce((a, b) -> a + "\u0000" + b)
              .orElse("conflict");
      String conflictId =
          UUID.nameUUIDFromBytes(
                  (direction.name() + "\u0000" + stableKey).getBytes(StandardCharsets.UTF_8))
              .toString();
      Match match = representative.getMatch();
      ModelConflict description =
          new ModelConflict(
              conflictId,
              direction,
              elementId,
              element == null ? "" : element.eClass().getName(),
              name(element),
              featureName,
              representative.getKind().getName(),
              "/" + escape(elementId) + "/" + escape(featureName),
              value(match == null ? null : match.getOrigin(), changedFeature),
              value(match == null ? null : match.getLeft(), changedFeature),
              value(match == null ? null : match.getRight(), changedFeature),
              "The working model and fresh generation changed " + featureName + " differently.",
              List.of(ConflictResolution.KEEP_USER, ConflictResolution.TAKE_GENERATED));
      result.put(conflict, description);
    }
    return result;
  }

  private EStructuralFeature feature(Diff difference) {
    if (difference instanceof AttributeChange change) {
      return change.getAttribute();
    }
    if (difference instanceof ReferenceChange change) {
      return change.getReference();
    }
    return null;
  }

  private EObject conflictElement(Diff difference) {
    if (difference instanceof ReferenceChange change
        && change.getReference() != null
        && change.getReference().isContainment()
        && change.getValue() != null) {
      return change.getValue();
    }
    Match match = difference.getMatch();
    if (match == null) {
      return null;
    }
    return match.getLeft() != null
        ? match.getLeft()
        : (match.getRight() != null ? match.getRight() : match.getOrigin());
  }

  private String differenceKey(Diff difference) {
    EStructuralFeature changedFeature = feature(difference);
    String valueId = difference instanceof ReferenceChange change ? id(change.getValue()) : "";
    return difference.getSource().getName()
        + ":"
        + difference.getKind().getName()
        + ":"
        + id(conflictElement(difference))
        + ":"
        + (changedFeature == null ? "resource" : changedFeature.getName())
        + ":"
        + valueId;
  }

  private JsonNode value(EObject object, EStructuralFeature feature) {
    if (object == null) {
      return NullNode.getInstance();
    }
    if (feature == null) {
      return mapper.getNodeFactory().textNode(id(object));
    }
    Object raw = object.eGet(feature, false);
    if (raw instanceof List<?> values) {
      ArrayNode array = mapper.createArrayNode();
      values.forEach(item -> array.add(value(item)));
      return array;
    }
    return value(raw);
  }

  private JsonNode value(Object raw) {
    if (raw == null) {
      return NullNode.getInstance();
    }
    if (raw instanceof EObject object) {
      return mapper.getNodeFactory().textNode(id(object));
    }
    if (raw instanceof Enumerator enumerator) {
      return mapper.getNodeFactory().textNode(enumerator.getName());
    }
    return mapper.valueToTree(raw);
  }

  private String id(EObject object) {
    if (object == null) {
      return "";
    }
    String ecoreId = EcoreUtil.getID(object);
    if (ecoreId != null && !ecoreId.isBlank()) {
      return ecoreId;
    }
    EStructuralFeature idFeature = object.eClass().getEStructuralFeature("id");
    if (idFeature != null) {
      Object value = object.eGet(idFeature, false);
      return value == null ? "" : String.valueOf(value);
    }
    return EcoreUtil.getURI(object).toString();
  }

  private String name(EObject object) {
    if (object == null) {
      return "";
    }
    EStructuralFeature nameFeature = object.eClass().getEStructuralFeature("name");
    if (nameFeature == null) {
      return "";
    }
    Object value = object.eGet(nameFeature, false);
    return value == null ? "" : String.valueOf(value);
  }

  private byte[] save(Resource resource) {
    try {
      ByteArrayOutputStream output = new ByteArrayOutputStream();
      resource.save(output, Map.of());
      return output.toByteArray();
    } catch (Exception ex) {
      throw new IllegalStateException("Could not serialize the merged EMF working resource.", ex);
    }
  }

  private String escape(String token) {
    return String.valueOf(token).replace("~", "~0").replace("/", "~1");
  }

  /** Merge output; EMF Compare implementation types remain internal to this service. */
  public record MergeOutcome(
      Resource mergedWorking,
      byte[] mergedWorkingXmi,
      List<ModelConflict> conflicts,
      List<ModelConflict> allConflicts,
      int incomingChanges,
      int preservedUserChanges,
      int autoMergedChanges,
      int generatedAdditions,
      int generatedDeletions) {
    public MergeOutcome {
      mergedWorkingXmi = mergedWorkingXmi.clone();
      conflicts = List.copyOf(conflicts);
      allConflicts = List.copyOf(allConflicts);
    }

    @Override
    public byte[] mergedWorkingXmi() {
      return mergedWorkingXmi.clone();
    }
  }
}
