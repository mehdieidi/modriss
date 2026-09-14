package io.mehdieidi.modriss.platform.transformation.synchronization;

import com.google.common.base.Predicate;
import io.mehdieidi.modriss.mde.validation.EvlModelResourceDiagnostics;
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
import org.eclipse.emf.common.util.Diagnostic;
import org.eclipse.emf.common.util.Enumerator;
import org.eclipse.emf.compare.AttributeChange;
import org.eclipse.emf.compare.Comparison;
import org.eclipse.emf.compare.Conflict;
import org.eclipse.emf.compare.ConflictKind;
import org.eclipse.emf.compare.Diff;
import org.eclipse.emf.compare.DifferenceKind;
import org.eclipse.emf.compare.DifferenceSource;
import org.eclipse.emf.compare.DifferenceState;
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
import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.util.Diagnostician;
import org.eclipse.emf.ecore.util.EcoreUtil;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.NullNode;
import tools.jackson.databind.node.ObjectNode;

/** Performs standalone three-way EMF Compare merges. */
public final class ModelSynchronizationService {

  private final ObjectMapper mapper;
  private final SemanticMergePolicy policy;
  private final EMFCompare emfCompare;
  private final IMerger.Registry mergerRegistry;

  public ModelSynchronizationService(ObjectMapper mapper) {
    this(mapper, new SemanticMergePolicy(), UseIdentifiers.ONLY);
  }

  public ModelSynchronizationService(ObjectMapper mapper, SemanticMergePolicy policy) {
    this(mapper, policy, UseIdentifiers.ONLY);
  }

  /**
   * Creates a merger with the requested EMF Compare identity strategy.
   *
   * <p>{@link UseIdentifiers#ONLY} is the normal synchronization mode. {@link UseIdentifiers#NEVER}
   * exists solely for a controlled one-time migration of baselines written before generated IDs
   * became deterministic; callers must not use it for ordinary merges.
   */
  public ModelSynchronizationService(
      ObjectMapper mapper, SemanticMergePolicy policy, UseIdentifiers identifiers) {
    this.mapper = Objects.requireNonNull(mapper, "mapper");
    this.policy = Objects.requireNonNull(policy, "policy");
    IMatchEngine.Factory.Registry matchRegistry =
        MatchEngineFactoryRegistryImpl.createStandaloneInstance();
    matchRegistry.clear();
    MatchEngineFactoryImpl identifierFactory =
        new MatchEngineFactoryImpl(Objects.requireNonNull(identifiers, "identifiers"));
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
    Map<String, Map<String, List<EObject>>> requiredWorkingContainments =
        requiredContainmentSnapshot(working);
    Map<String, Map<String, List<EObject>>> requiredGeneratedContainments =
        requiredContainmentSnapshot(newGenerated);

    IComparisonScope scope = new DefaultComparisonScope(working, newGenerated, base);
    Comparison comparison = emfCompare.compare(scope, new BasicMonitor());
    Map<Conflict, ModelConflict> realConflicts = describeRealConflicts(comparison, direction);
    Map<Match, ModelConflict> addAddConflicts = describeAddAddConflicts(comparison, direction);
    Map<String, ModelConflict> orderedConflicts =
        describeOrderedConflicts(base, working, newGenerated, direction);
    Map<String, List<String>> originalOrderedValues = orderedFeatureValues(working);
    Set<EObject> generatedDeletionRoots =
        generatedDeletionRootsSelectedForRemoval(comparison, working, realConflicts, decisions);
    Set<String> locallyPreservedDeletionClosure =
        locallyPreservedDeletionClosure(comparison, working, realConflicts, decisions);
    boolean hasPendingGeneratedDeletion =
        realConflicts.values().stream()
            .distinct()
            .anyMatch(
                conflict ->
                    "DELETE".equals(conflict.differenceKind())
                        && decisions.get(conflict.conflictId())
                            != ConflictResolution.TAKE_GENERATED);
    // Keep EMF Compare's dependency order. Containment additions/deletions are represented by
    // several related diffs (container reference, child object, and nested references); feeding
    // BatchMerger a HashSet makes that order nondeterministic and can leave nested generated
    // objects behind or prevent an incoming subtree from being attached.
    Set<Diff> mergeable = new LinkedHashSet<>();
    List<ReferenceChange> generatedContainmentMoves = new java.util.ArrayList<>();
    int incoming = 0;
    int additions = 0;
    int deletions = 0;

    for (Diff difference : comparison.getDifferences()) {
      if (difference.getSource() != DifferenceSource.RIGHT) {
        continue;
      }
      // Until a logical generated deletion is resolved, keep incoming changes out of the pending
      // merge copy. EMF Compare can distribute one generated resource deletion over
      // shared, separately contained support trees whose provenance is not identical (IAM policy
      // documents are a common example), and ConflictMerger expands a selected non-delete sibling
      // to those deletion diffs internally. Applying a partial incoming set can therefore violate
      // required Ecore containment before the user has made any deletion decision. The comparison
      // is rerun after resolution, so accepted changes and deletions are then applied atomically.
      if (hasPendingGeneratedDeletion) {
        continue;
      }
      // A generated containment deletion is represented by a set of dependent differences.  When
      // the user rejects that deletion, applying otherwise non-conflicting child/reference
      // deletions would retain only a fragment of the local object graph.  Besides violating the
      // user's choice, that commonly produces an invalid model (for example, a Workflow without
      // its required steps).  Treat the locally retained subtree and links to it as one unit.
      if (isChangeTouchingLocallyPreservedDeletion(difference, locallyPreservedDeletionClosure)) {
        continue;
      }
      if (isRequiredLocalContainmentRetained(comparison, difference, working)) {
        continue;
      }
      MergeFeaturePolicy featurePolicy = policy.policyFor(feature(difference));
      ModelConflict orderedConflict = orderedConflicts.get(orderedFeatureKey(difference));
      if (orderedConflict != null) {
        if (decisions.get(orderedConflict.conflictId()) != ConflictResolution.TAKE_GENERATED) {
          continue;
        }
        // Ordering is applied as a complete feature value after ordinary EMF differences. This
        // prevents a partial sequence of list diffs from defeating the user's single order choice.
        continue;
      }
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
      ModelConflict description =
          conflict == null || conflict.getKind() != ConflictKind.REAL
              ? addAddConflictFor(comparison, difference, addAddConflicts)
              : realConflicts.get(conflict);
      ConflictResolution decision =
          description == null ? null : decisions.get(description.conflictId());
      if (shouldApplyContainmentMoveDirectly(
          comparison, difference, conflict, featurePolicy, decision)) {
        // ConflictMerger rejects a local containment move before its generated parent has been
        // merged when that parent was created after the common ancestor. Update the containment
        // directly after ordinary differences; incoming-only children are supplied by their ADD
        // difference and the direct step then has nothing to do.
        generatedContainmentMoves.add((ReferenceChange) difference);
        continue;
      }
      if (description == null) {
        mergeable.add(difference);
        continue;
      }
      if (featurePolicy == MergeFeaturePolicy.GENERATOR_OWNED
          || decision == ConflictResolution.TAKE_GENERATED) {
        mergeable.add(difference);
      }
    }

    // EMF Compare represents containment moves as a graph of diffs. A parent diff may be
    // intentionally excluded because it is user-owned, unresolved, or protected by a KEEP_USER
    // deletion choice. Its child diff is then no longer safe to apply: BatchMerger would otherwise
    // attempt the child move first and fail with "parent hasn't been merged yet". Reduce the
    // selected set to a dependency-closed subset so a filtered merge can never contain a child
    // whose required incoming parent was intentionally left local.
    removeDiffsWithUnmergeablePrerequisites(mergeable);
    // ConflictMerger expands a selected conflict to its sibling diffs internally, bypassing the
    // BatchMerger predicate. Mark the directly-applied containment locations as handled so that
    // expansion cannot route one of them back through ReferenceChangeMerger. Other siblings,
    // such as an incoming-only parent container, remain available to the batch merger.
    generatedContainmentMoves.forEach(change -> change.setState(DifferenceState.MERGED));
    // The failing half of a right-to-left containment move is represented as a LEFT diff. It is
    // not visited by the incoming loop above, yet ConflictMerger tries to reject it while
    // applying its selected RIGHT counterpart. Mark that local half handled as well.
    comparison.getDifferences().stream()
        .filter(this::isUnsafeContainmentMove)
        .filter(difference -> difference.getSource() == DifferenceSource.LEFT)
        .filter(
            difference -> {
              Conflict conflict = difference.getConflict();
              ModelConflict description = conflict == null ? null : realConflicts.get(conflict);
              return description != null
                  && decisions.get(description.conflictId()) == ConflictResolution.TAKE_GENERATED;
            })
        .forEach(difference -> difference.setState(DifferenceState.MERGED));

    if (!mergeable.isEmpty()) {
      Predicate<Diff> allowed = mergeable::contains;
      IBatchMerger merger = new BatchMerger(mergerRegistry, allowed);
      merger.copyAllRightToLeft(mergeable, new BasicMonitor());
    }
    generatedContainmentMoves.forEach(
        change -> applyGeneratedContainmentMove(comparison, working, change));
    applyGeneratedOrders(working, newGenerated, orderedConflicts, decisions, originalOrderedValues);
    Set<String> baseObjectIds =
        allObjects(base).stream().map(this::id).collect(java.util.stream.Collectors.toSet());
    rebindGeneratedProxyReferences(working, newGenerated, baseObjectIds);
    // A "keep generated" decision for an upstream deletion applies to the whole generated
    // resource, not just the individual EMF differences that happened to be selected by the
    // batch merger.  In particular, an IAM role, its inline policy, policy document, and
    // statements form one containment tree. Removing only some of those differences produces
    // orphaned PSM infrastructure that cannot be repaired sensibly in the UI.
    generatedDeletionRoots.forEach(root -> EcoreUtil.delete(root, true));
    reconcileIncomingOnlyContainments(working, newGenerated, base);
    restoreRequiredContainments(
        working, requiredWorkingContainments, requiredGeneratedContainments);
    validateStructuralResult(working);
    // A containment deletion can create dozens of EMF Compare Conflict instances: one for the
    // resource, its contained configuration, and the references that point to it. They are one
    // user decision, not dozens of unrelated conflicts. The map deliberately assigns those raw
    // conflicts the same stable id; expose each logical conflict once to callers.
    List<ModelConflict> logicalConflicts =
        java.util.stream.Stream.of(
                realConflicts.values().stream(),
                addAddConflicts.values().stream(),
                orderedConflicts.values().stream())
            .flatMap(java.util.function.Function.identity())
            .distinct()
            .toList();
    List<ModelConflict> unresolved =
        logicalConflicts.stream()
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
        logicalConflicts,
        incoming,
        preserved,
        mergeable.size(),
        additions,
        deletions);
  }

  private void removeDiffsWithUnmergeablePrerequisites(Set<Diff> mergeable) {
    boolean changed;
    do {
      changed = false;
      for (var iterator = mergeable.iterator(); iterator.hasNext(); ) {
        Diff difference = iterator.next();
        if (difference instanceof ReferenceChange change
            && change.getKind() == DifferenceKind.DELETE
            && change.getReference() != null
            && change.getReference().isContainment()) {
          // The owner-side containment DELETE is the atomic removal of the obsolete subtree. Its
          // child MOVE/DELETE prerequisites may be filtered independently, but retaining the
          // owner deletion prevents an empty mandatory owner from surviving the merge.
          continue;
        }
        boolean hasExcludedIncomingRequirement =
            difference.getRequires().stream()
                .anyMatch(
                    required ->
                        required.getSource() == DifferenceSource.RIGHT
                            && !mergeable.contains(required));
        if (hasExcludedIncomingRequirement) {
          iterator.remove();
          changed = true;
        }
      }
    } while (changed);
  }

  /**
   * Rebinds references copied with a freshly generated subtree to the corresponding objects in the
   * merged working resource.
   *
   * <p>EMF Compare can copy a non-containment reference before it attaches the referenced incoming
   * object. The copied value then remains a proxy whose fragment points into the temporary incoming
   * resource. Saving that graph makes the proxy permanent even though an object with the same
   * stable model ID is present in the working resource. Resolve only those proxy slots, using the
   * generated counterpart as the semantic source and stable IDs as the cross-resource key; valid
   * local references and user edits are left untouched.
   */
  private void rebindGeneratedProxyReferences(
      Resource working, Resource generated, Set<String> baseObjectIds) {
    Map<String, EObject> workingById = new HashMap<>();
    for (EObject object : allObjects(working)) workingById.put(id(object), object);
    Set<EObject> reachableWorkingObjects =
        java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
    reachableWorkingObjects.addAll(allObjects(working));
    Map<String, EObject> generatedById = new HashMap<>();
    for (EObject object : allObjects(generated)) generatedById.put(id(object), object);

    for (EObject owner : allObjects(working)) {
      EObject generatedOwner = generatedById.get(id(owner));
      if (generatedOwner == null
          || !owner.eClass().getName().equals(generatedOwner.eClass().getName())
          || !owner
              .eClass()
              .getEPackage()
              .getNsURI()
              .equals(generatedOwner.eClass().getEPackage().getNsURI())) continue;
      for (EReference reference : owner.eClass().getEAllReferences()) {
        if (reference.isContainment() || reference.isContainer()) continue;
        Object localValue = owner.eGet(reference, false);
        Object generatedValue = generatedOwner.eGet(reference, false);
        boolean generatedAddition = !baseObjectIds.contains(id(owner));
        if (reference.isMany()
            && localValue instanceof List<?> localValues
            && generatedValue instanceof List<?> generatedValues) {
          @SuppressWarnings("unchecked")
          List<EObject> writable = (List<EObject>) localValues;
          if (generatedAddition) {
            List<EObject> replacements =
                generatedValues.stream()
                    .filter(EObject.class::isInstance)
                    .map(EObject.class::cast)
                    .map(target -> workingById.get(id(target)))
                    .filter(Objects::nonNull)
                    .toList();
            if (replacements.size() == generatedValues.size()) {
              writable.clear();
              writable.addAll(replacements);
              continue;
            }
          }
          for (int index = 0; index < writable.size(); index++) {
            EObject localTarget = writable.get(index);
            if (!isUnresolvedOrForeign(localTarget, reachableWorkingObjects)
                || index >= generatedValues.size()) continue;
            Object candidate = generatedValues.get(index);
            if (candidate instanceof EObject generatedTarget) {
              EObject replacement = workingById.get(id(generatedTarget));
              if (replacement != null) writable.set(index, replacement);
            }
          }
        } else if (localValue instanceof EObject localTarget
            && (generatedAddition || isUnresolvedOrForeign(localTarget, reachableWorkingObjects))
            && generatedValue instanceof EObject generatedTarget) {
          EObject replacement = workingById.get(id(generatedTarget));
          if (replacement != null) owner.eSet(reference, replacement);
        }
      }
    }
  }

  private boolean isUnresolvedOrForeign(EObject target, Set<EObject> reachableWorkingObjects) {
    if (target.eIsProxy()) return true;
    return !reachableWorkingObjects.contains(target);
  }

  /** Finds complete local generated resources whose deletion the user accepted. */
  private Set<EObject> generatedDeletionRootsSelectedForRemoval(
      Comparison comparison,
      Resource working,
      Map<Conflict, ModelConflict> conflicts,
      Map<String, ConflictResolution> decisions) {
    Set<EObject> roots = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
    for (Conflict conflict : comparison.getConflicts()) {
      ModelConflict description = conflicts.get(conflict);
      if (description == null
          || decisions.get(description.conflictId()) != ConflictResolution.TAKE_GENERATED
          || conflict.getDifferences().stream()
              .noneMatch(
                  difference ->
                      difference.getSource() == DifferenceSource.RIGHT
                          && difference.getKind() == DifferenceKind.DELETE)) {
        continue;
      }
      EObject root = deletionRoot(conflict);
      if (root != null && root.eContainer() != null) {
        roots.add(root);
      }
    }
    return roots;
  }

  /** Applies an explicitly selected generated containment location without ConflictMerger. */
  private boolean shouldApplyContainmentMoveDirectly(
      Comparison comparison,
      Diff difference,
      Conflict conflict,
      MergeFeaturePolicy featurePolicy,
      ConflictResolution decision) {
    if (!(difference instanceof ReferenceChange change)
        || change.getReference() == null
        || !change.getReference().isContainment()) {
      return false;
    }
    boolean incomingIsSelected =
        conflict == null
            || conflict.getKind() != ConflictKind.REAL
            || featurePolicy == MergeFeaturePolicy.GENERATOR_OWNED
            || decision == ConflictResolution.TAKE_GENERATED;
    // EMF Compare direction-normalizes a move into an ADD or DELETE before it reaches the
    // merger. The observable unsafe shape is instead a local parent without an origin parent.
    // Do not let any accepted containment change for that shape reach BatchMerger: it attempts
    // to move the local child before the generated parent is considered merged.
    return incomingIsSelected && isUnsafeContainmentMove(change);
  }

  private boolean isUnsafeContainmentMove(Diff difference) {
    if (!(difference instanceof ReferenceChange change)
        || change.getReference() == null
        || !change.getReference().isContainment()) {
      return false;
    }
    Match parentMatch = change.getMatch();
    // A generated parent can be new in the right model (left == null), while an existing child
    // is moved beneath it.  EMF Compare emits the child move before the parent ADD in this shape,
    // which makes BatchMerger fail with "parent hasn't been merged yet".  Both directions are
    // unsafe and must be applied after ordinary additions have been merged.
    return parentMatch != null
        && parentMatch.getOrigin() == null
        && (parentMatch.getLeft() != null || parentMatch.getRight() != null);
  }

  /** Applies an explicitly selected generated containment location without ConflictMerger. */
  private void applyGeneratedContainmentMove(
      Comparison comparison, Resource working, ReferenceChange change) {
    Match valueMatch = matchFor(comparison, change.getValue());
    EObject localChild = valueMatch == null ? null : valueMatch.getLeft();
    Match parentMatch = change.getMatch();
    EObject localParent = parentMatch == null ? null : parentMatch.getLeft();
    if (localParent == null && parentMatch != null && parentMatch.getRight() != null) {
      String generatedParentId = id(parentMatch.getRight());
      localParent =
          allObjects(working).stream()
              .filter(candidate -> generatedParentId.equals(id(candidate)))
              .findFirst()
              .orElse(null);
    }
    EReference reference = change.getReference();
    if (localChild == null) {
      if (change.getKind() == DifferenceKind.ADD
          && localParent != null
          && change.getValue() != null) {
        EObject copiedChild = EcoreUtil.copy(change.getValue());
        if (reference.isMany()) {
          @SuppressWarnings("unchecked")
          List<EObject> children = (List<EObject>) localParent.eGet(reference);
          if (children.stream().noneMatch(child -> id(child).equals(id(copiedChild)))) {
            children.add(copiedChild);
          }
        } else {
          localParent.eSet(reference, copiedChild);
        }
      }
      return;
    }
    // A DELETE with a matching right object is one half of a move; the matching ADD/MOVE places
    // the child into its generated parent, so it must not remove that child altogether.
    if (change.getKind() == DifferenceKind.DELETE
        && valueMatch != null
        && valueMatch.getRight() != null) {
      return;
    }
    if (change.getKind() == DifferenceKind.DELETE || localParent == null) {
      EObject currentParent = localChild.eContainer();
      if (currentParent != null) {
        EStructuralFeature currentFeature = localChild.eContainmentFeature();
        if (currentFeature != null && currentFeature.isMany()) {
          @SuppressWarnings("unchecked")
          List<EObject> children = (List<EObject>) currentParent.eGet(currentFeature);
          children.remove(localChild);
        } else if (currentFeature != null) {
          currentParent.eUnset(currentFeature);
        }
      }
      return;
    }
    if (reference.isMany()) {
      @SuppressWarnings("unchecked")
      List<EObject> children = (List<EObject>) localParent.eGet(reference);
      if (!children.contains(localChild)) {
        children.add(localChild);
      }
    } else {
      localParent.eSet(reference, localChild);
    }
  }

  /**
   * Finds a match for an object even when EMF Compare's root lookup has not indexed a nested value.
   * Containment move diffs keep the child match beneath the parent match in that case.
   */
  private Match matchFor(Comparison comparison, EObject object) {
    Match direct = comparison.getMatch(object);
    if (direct != null) {
      return direct;
    }
    for (Match root : comparison.getMatches()) {
      Match match = nestedMatchFor(root, object);
      if (match != null) {
        return match;
      }
    }
    return null;
  }

  private Match nestedMatchFor(Match candidate, EObject object) {
    if (candidate.getLeft() == object
        || candidate.getRight() == object
        || candidate.getOrigin() == object) {
      return candidate;
    }
    for (Match submatch : candidate.getSubmatches()) {
      Match match = nestedMatchFor(submatch, object);
      if (match != null) {
        return match;
      }
    }
    return null;
  }

  private Set<String> locallyPreservedDeletionClosure(
      Comparison comparison,
      Resource working,
      Map<Conflict, ModelConflict> conflicts,
      Map<String, ConflictResolution> decisions) {
    Set<String> preserved = new LinkedHashSet<>();
    for (Conflict conflict : comparison.getConflicts()) {
      ModelConflict description = conflicts.get(conflict);
      if (description == null
          || decisions.get(description.conflictId()) == ConflictResolution.TAKE_GENERATED
          || conflict.getDifferences().stream()
              .noneMatch(
                  difference ->
                      difference.getSource() == DifferenceSource.RIGHT
                          && difference.getKind() == DifferenceKind.DELETE)) {
        continue;
      }
      // A deletion conflict may be represented by a containment change whose value is only
      // available on the generated side.  Resolve the stable identity at the resource boundary as
      // well, so KEEP_USER always retains the complete local generated subtree.
      EObject generatedRoot = deletionRoot(conflict);
      if (generatedRoot != null) {
        EObject localRoot =
            allObjects(working).stream()
                .filter(candidate -> id(generatedRoot).equals(id(candidate)))
                .findFirst()
                .orElse(null);
        if (localRoot != null) {
          addContainmentClosure(localRoot, preserved);
        }
      }
      boolean foundContainedRoot = false;
      for (Diff difference : conflict.getDifferences()) {
        if (difference instanceof ReferenceChange change
            && change.getReference() != null
            && change.getReference().isContainment()
            && change.getValue() != null) {
          Match contained = matchFor(comparison, change.getValue());
          if (contained != null && contained.getLeft() != null) {
            addContainmentClosure(contained.getLeft(), preserved);
            foundContainedRoot = true;
          }
        }
      }
      // A containment-reference difference is matched on its parent. Use the parent match only
      // when EMF Compare exposes no contained target match; otherwise retaining one deleted child
      // would accidentally shield every deletion in the entire model.
      if (foundContainedRoot) {
        continue;
      }
      for (Diff difference : conflict.getDifferences()) {
        Match match = difference.getMatch();
        if (match != null && match.getLeft() != null) {
          addContainmentClosure(match.getLeft(), preserved);
        }
      }
    }
    expandGeneratedProvenanceClosure(working, preserved);
    expandReferenceClosure(working, preserved);
    return preserved;
  }

  /** Keeps separately contained generated resources that belong to the retained source element. */
  private void expandGeneratedProvenanceClosure(Resource working, Set<String> ids) {
    Set<String> sourceIds = new LinkedHashSet<>();
    for (EObject candidate : allObjects(working)) {
      if (!ids.contains(id(candidate))) continue;
      EStructuralFeature generatedFrom = candidate.eClass().getEStructuralFeature("generatedFrom");
      if (generatedFrom == null) continue;
      Object value = candidate.eGet(generatedFrom, false);
      if (value != null && !String.valueOf(value).isBlank()) sourceIds.add(String.valueOf(value));
    }
    if (sourceIds.isEmpty()) return;
    for (EObject candidate : allObjects(working)) {
      EStructuralFeature generatedFrom = candidate.eClass().getEStructuralFeature("generatedFrom");
      EStructuralFeature generated =
          candidate.eClass().getEStructuralFeature("generatedByTransformation");
      if (generatedFrom == null
          || generated == null
          || !Boolean.TRUE.equals(candidate.eGet(generated, false))) continue;
      Object value = candidate.eGet(generatedFrom, false);
      if (value != null && sourceIds.contains(String.valueOf(value))) {
        addContainmentClosure(candidate, ids);
      }
    }
  }

  /**
   * Keeps direct local links needed by a retained deletion without retaining the whole model.
   *
   * <p>Following containment ancestors is unsafe: the model root contains every generated resource,
   * so its containment closure makes a single KEEP_USER decision suppress unrelated future
   * generated deletions. Likewise, recursively following shared infrastructure references (stages,
   * stacks, KMS keys, and traces) quickly captures the entire PSM. The retained element's
   * containment subtree is already present; one direct reference hop preserves its immediate
   * support resources and back-links without poisoning later synchronization runs.
   */
  private void expandReferenceClosure(Resource working, Set<String> ids) {
    Set<String> retained = Set.copyOf(ids);
    for (EObject candidate : allObjects(working)) {
      if (retained.contains(id(candidate))) {
        addDirectReferenceTargets(candidate, ids);
      } else if (referencesAny(candidate, retained)) {
        addContainmentClosure(candidate, ids);
      }
    }
    // A generated membership/trace that was retained because it points to the user's kept
    // workflow can itself require a service, stack, or policy owner. Preserve that immediate
    // owner too. One bounded second hop fixes the dangling-reference case without recursively
    // walking the whole shared infrastructure graph.
    Set<String> firstHop = Set.copyOf(ids);
    for (EObject candidate : allObjects(working)) {
      if (!retained.contains(id(candidate)) && firstHop.contains(id(candidate))) {
        addDirectReferenceTargets(candidate, ids);
      }
    }
  }

  private void addDirectReferenceTargets(EObject candidate, Set<String> ids) {
    for (var reference : candidate.eClass().getEAllReferences()) {
      if (reference.isContainment()
          || reference.isDerived()
          || reference.isTransient()
          || reference.isVolatile()) {
        continue;
      }
      Object value = candidate.eGet(reference, false);
      if (value instanceof EObject target) {
        addContainmentClosure(target, ids);
      } else if (value instanceof List<?> values) {
        for (Object item : values) {
          if (item instanceof EObject target) {
            addContainmentClosure(target, ids);
          }
        }
      }
    }
  }

  private List<EObject> allObjects(Resource resource) {
    List<EObject> objects = new java.util.ArrayList<>();
    for (EObject root : resource.getContents()) {
      objects.add(root);
      for (var contents = root.eAllContents(); contents.hasNext(); ) {
        objects.add(contents.next());
      }
    }
    return objects;
  }

  /** Captures populated required containments before a merge can apply a partial deletion. */
  private Map<String, Map<String, List<EObject>>> requiredContainmentSnapshot(Resource resource) {
    Map<String, Map<String, List<EObject>>> snapshot = new HashMap<>();
    for (EObject object : allObjects(resource)) {
      Set<String> objectIds = identityKeys(object);
      if (objectIds.isEmpty()) continue;
      for (EReference reference : object.eClass().getEAllReferences()) {
        if (!reference.isContainment() || reference.getLowerBound() < 1) continue;
        Object value = object.eGet(reference, false);
        if (reference.isMany() && value instanceof List<?> values && !values.isEmpty()) {
          for (String objectId : objectIds) {
            snapshot
                .computeIfAbsent(objectId, ignored -> new HashMap<>())
                .put(
                    reference.getName(),
                    values.stream()
                        .filter(EObject.class::isInstance)
                        .map(EObject.class::cast)
                        .map(EcoreUtil::copy)
                        .toList());
          }
        } else if (!reference.isMany() && value instanceof EObject child) {
          for (String objectId : objectIds) {
            snapshot
                .computeIfAbsent(objectId, ignored -> new HashMap<>())
                .put(reference.getName(), List.of(EcoreUtil.copy(child)));
          }
        }
      }
    }
    return snapshot;
  }

  /** Restores only Ecore-required containments that a merge left empty on an existing owner. */
  private void restoreRequiredContainments(
      Resource resource,
      Map<String, Map<String, List<EObject>>> workingSnapshot,
      Map<String, Map<String, List<EObject>>> generatedSnapshot) {
    if (workingSnapshot.isEmpty() && generatedSnapshot.isEmpty()) return;
    for (EObject owner : allObjects(resource)) {
      Map<String, List<EObject>> features = new HashMap<>();
      for (String identityKey : identityKeys(owner)) {
        features.putAll(generatedSnapshot.getOrDefault(identityKey, Map.of()));
        features.putAll(workingSnapshot.getOrDefault(identityKey, Map.of()));
      }
      for (Map.Entry<String, List<EObject>> entry : features.entrySet()) {
        EStructuralFeature feature = owner.eClass().getEStructuralFeature(entry.getKey());
        if (!(feature instanceof EReference reference) || !reference.isContainment()) continue;
        Object current = owner.eGet(reference, false);
        boolean empty =
            reference.isMany()
                ? !(current instanceof List<?> values) || values.isEmpty()
                : current == null;
        if (!empty) continue;
        if (reference.isMany()) {
          @SuppressWarnings("unchecked")
          List<EObject> children = (List<EObject>) current;
          entry
              .getValue()
              .forEach(
                  child -> {
                    EObject existing = findByIdentity(resource, identityKeys(child));
                    if (existing != null
                        && existing.eContainer() != null
                        && existing.eContainer() != owner) {
                      return;
                    }
                    EObject restored = existing == null ? EcoreUtil.copy(child) : existing;
                    if (!children.contains(restored)) children.add(restored);
                  });
        } else {
          EObject child = entry.getValue().get(0);
          EObject existing = findByIdentity(resource, identityKeys(child));
          if (existing != null && existing.eContainer() != null && existing.eContainer() != owner) {
            continue;
          }
          EObject restored = existing == null ? EcoreUtil.copy(child) : existing;
          owner.eSet(reference, restored);
        }
      }
    }
  }

  private EObject findById(Resource resource, String objectId) {
    if (objectId == null || objectId.isBlank()) return null;
    return findByIdentity(resource, Set.of(objectId));
  }

  private EObject findByIdentity(Resource resource, Set<String> keys) {
    if (keys == null || keys.isEmpty()) return null;
    return allObjects(resource).stream()
        .filter(object -> !java.util.Collections.disjoint(identityKeys(object), keys))
        .findFirst()
        .orElse(null);
  }

  private Set<String> identityKeys(EObject object) {
    Set<String> keys = new LinkedHashSet<>();
    String emfId = id(object);
    if (!emfId.isBlank()) keys.add(emfId);
    EStructuralFeature feature = object.eClass().getEStructuralFeature("id");
    if (feature instanceof EAttribute && object.eIsSet(feature)) {
      Object value = object.eGet(feature, false);
      if (value != null && !String.valueOf(value).isBlank()) keys.add(String.valueOf(value));
    }
    return keys;
  }

  /** Reattaches complete subtrees for containers introduced only by the generated resource. */
  private void reconcileIncomingOnlyContainments(
      Resource working, Resource generated, Resource base) {
    Set<String> baseIds =
        allObjects(base).stream()
            .flatMap(object -> identityKeys(object).stream())
            .collect(java.util.stream.Collectors.toSet());
    List<EObject> generatedObjects = allObjects(generated);
    for (EObject generatedOwner : generatedObjects) {
      String ownerId = id(generatedOwner);
      if (ownerId.isBlank()
          || !java.util.Collections.disjoint(identityKeys(generatedOwner), baseIds)) continue;
      EObject workingOwner = findByIdentity(working, identityKeys(generatedOwner));
      if (workingOwner == null) continue;
      for (EReference reference : generatedOwner.eClass().getEAllReferences()) {
        if (!reference.isContainment()) continue;
        Object value = generatedOwner.eGet(reference, false);
        if (reference.isMany() && value instanceof List<?> values) {
          for (Object item : values) {
            if (item instanceof EObject child)
              attachIncomingChild(working, workingOwner, reference, child);
          }
        } else if (value instanceof EObject child) {
          attachIncomingChild(working, workingOwner, reference, child);
        }
      }
    }
  }

  private void attachIncomingChild(
      Resource working, EObject owner, EReference reference, EObject generatedChild) {
    EObject child = findByIdentity(working, identityKeys(generatedChild));
    if (child == null) child = EcoreUtil.copy(generatedChild);
    EObject attachedChild = child;
    if (attachedChild.eContainer() != null && attachedChild.eContainer() != owner)
      detach(attachedChild);
    if (reference.isMany()) {
      @SuppressWarnings("unchecked")
      List<EObject> values = (List<EObject>) owner.eGet(reference);
      if (values.stream().noneMatch(existing -> id(existing).equals(id(attachedChild))))
        values.add(attachedChild);
    } else if (owner.eGet(reference, false) == null) {
      owner.eSet(reference, attachedChild);
    }
  }

  private void detach(EObject object) {
    EObject container = object.eContainer();
    EStructuralFeature feature = object.eContainmentFeature();
    if (container == null || feature == null) return;
    if (feature.isMany()) {
      @SuppressWarnings("unchecked")
      List<EObject> values = (List<EObject>) container.eGet(feature);
      values.remove(object);
    } else {
      container.eUnset(feature);
    }
  }

  private boolean referencesAny(EObject candidate, Set<String> ids) {
    for (var reference : candidate.eClass().getEAllReferences()) {
      if (reference.isContainment()
          || reference.isDerived()
          || reference.isTransient()
          || reference.isVolatile()) {
        continue;
      }
      Object value = candidate.eGet(reference, false);
      if (value instanceof EObject object && ids.contains(id(object))) {
        return true;
      }
      if (value instanceof List<?> values
          && values.stream()
              .filter(EObject.class::isInstance)
              .map(EObject.class::cast)
              .map(this::id)
              .anyMatch(ids::contains)) {
        return true;
      }
    }
    return false;
  }

  private void addContainmentClosure(EObject root, Set<String> ids) {
    ids.add(id(root));
    for (var contents = root.eAllContents(); contents.hasNext(); ) {
      ids.add(id(contents.next()));
    }
  }

  private boolean isChangeTouchingLocallyPreservedDeletion(
      Diff difference, Set<String> preservedIds) {
    if (preservedIds.isEmpty()) {
      return false;
    }
    Match match = difference.getMatch();
    if (match != null && match.getLeft() != null && preservedIds.contains(id(match.getLeft()))) {
      return true;
    }
    if (difference instanceof ReferenceChange change && change.getValue() != null) {
      return preservedIds.contains(id(change.getValue()));
    }
    return false;
  }

  /**
   * Keep an existing mandatory containment child when the incoming representation removes only that
   * child but retains its owner. This occurs for an IAM role's trust policy in a conflicted move;
   * applying the partial deletion makes the retained role invalid. If the role itself is an
   * accepted generated deletion, {@link #generatedDeletionRootsSelectedForRemoval} removes the
   * complete role later.
   */
  private boolean isRequiredLocalContainmentRetained(
      Comparison comparison, Diff difference, Resource working) {
    if (!(difference instanceof ReferenceChange change)
        || difference.getSource() != DifferenceSource.RIGHT
        || difference.getKind() != DifferenceKind.DELETE
        || change.getReference() == null
        || !change.getReference().isContainment()
        || change.getReference().getLowerBound() < 1) {
      return false;
    }
    Match valueMatch = matchFor(comparison, change.getValue());
    if (valueMatch != null && valueMatch.getRight() != null) {
      // A DELETE whose value is still present on the generated side is the old containment
      // location of a move, not a logical deletion. Let the generated location win so a later
      // required-containment repair cannot detach the moved object back into the old owner.
      return false;
    }
    Match owner = change.getMatch();
    EObject localOwner =
        owner == null
            ? null
            : owner.getLeft() != null
                ? owner.getLeft()
                : owner.getOrigin() != null ? owner.getOrigin() : owner.getRight();
    if (localOwner == null) {
      return false;
    }
    String ownerId = id(localOwner);
    return allObjects(working).stream()
        .filter(candidate -> ownerId.equals(id(candidate)))
        .anyMatch(
            candidate -> {
              EStructuralFeature localFeature =
                  candidate.eClass().getEStructuralFeature(change.getReference().getName());
              if (localFeature == null) {
                return false;
              }
              Object current = candidate.eGet(localFeature, false);
              return current instanceof List<?> values && !values.isEmpty();
            });
  }

  /**
   * Classifies incompatible same-identity additions on both sides of an empty ancestor.
   *
   * <p>EMF Compare correctly matches the two added EObjects by their Ecore ID, but represents
   * differing feature values as ordinary left/right differences rather than a REAL conflict when
   * the match has no origin. Applying the incoming difference would silently overwrite the local
   * addition. This post-classification uses only the EMF match and its Ecore feature values; it
   * neither constructs model content nor contains domain-specific class or feature rules.
   */
  private Map<Match, ModelConflict> describeAddAddConflicts(
      Comparison comparison, TransformationDirection direction) {
    Map<Match, ModelConflict> result = new HashMap<>();
    for (Match match : allMatches(comparison)) {
      if (match == null
          || match.getOrigin() != null
          || match.getLeft() == null
          || match.getRight() == null) continue;
      JsonNode localValue = semanticState(match.getLeft());
      JsonNode generatedValue = semanticState(match.getRight());
      if (Objects.equals(localValue, generatedValue)) continue;
      EObject element = match.getLeft();
      String elementId = id(element);
      String featureName = "added element";
      String stableKey = "add-add:" + elementId;
      String conflictId =
          UUID.nameUUIDFromBytes(
                  (direction.name() + "\u0000" + stableKey).getBytes(StandardCharsets.UTF_8))
              .toString();
      result.put(
          match,
          new ModelConflict(
              conflictId,
              direction,
              elementId,
              element.eClass().getName(),
              name(element),
              featureName,
              "ADD",
              "/" + escape(elementId) + "/" + escape(featureName),
              NullNode.getInstance(),
              localValue,
              generatedValue,
              "The working model and fresh generation independently added the same identity with"
                  + " different "
                  + featureName
                  + " values.",
              List.of(ConflictResolution.KEEP_USER, ConflictResolution.TAKE_GENERATED)));
    }
    return result;
  }

  private ModelConflict addAddConflictFor(
      Comparison comparison, Diff difference, Map<Match, ModelConflict> conflicts) {
    ModelConflict direct = conflicts.get(difference.getMatch());
    if (direct != null) return direct;
    if (difference instanceof ReferenceChange change && change.getValue() != null) {
      return conflicts.get(comparison.getMatch(change.getValue()));
    }
    return null;
  }

  private List<Match> allMatches(Comparison comparison) {
    List<Match> matches = new java.util.ArrayList<>();
    java.util.function.Consumer<Match> collect =
        new java.util.function.Consumer<>() {
          @Override
          public void accept(Match match) {
            matches.add(match);
            match.getSubmatches().forEach(this);
          }
        };
    comparison.getMatches().forEach(collect);
    return matches;
  }

  private JsonNode semanticState(EObject object) {
    ObjectNode state = mapper.createObjectNode();
    for (EStructuralFeature feature : object.eClass().getEAllStructuralFeatures()) {
      if (feature.isDerived()
          || feature.isTransient()
          || feature.isVolatile()
          || feature instanceof EReference reference && reference.isContainer()
          || "id".equals(feature.getName())) continue;
      state.set(feature.getName(), value(object, feature));
    }
    return state;
  }

  private Map<Conflict, ModelConflict> describeRealConflicts(
      Comparison comparison, TransformationDirection direction) {
    Map<Conflict, ModelConflict> result = new HashMap<>();
    Map<String, ModelConflict> logicalConflicts = new HashMap<>();
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
      EObject deletionRoot = deletionRoot(conflict);
      EStructuralFeature changedFeature = feature(representative);
      EObject element = deletionRoot == null ? conflictElement(representative) : deletionRoot;
      String elementId = id(element);
      String featureName =
          deletionRoot == null
              ? (changedFeature == null ? "resource" : changedFeature.getName())
              : "generated element";
      String stableKey =
          deletionRoot == null
              ? relevant.stream()
                  .map(this::differenceKey)
                  .sorted()
                  .reduce((a, b) -> a + "\u0000" + b)
                  .orElse("conflict")
              : "generated-deletion:" + elementId;
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
              deletionRoot == null ? representative.getKind().getName() : "DELETE",
              "/" + escape(elementId) + "/" + escape(featureName),
              deletionRoot == null
                  ? value(match == null ? null : match.getOrigin(), changedFeature)
                  : elementSummary(match == null ? deletionRoot : match.getOrigin()),
              deletionRoot == null
                  ? value(match == null ? null : match.getLeft(), changedFeature)
                  : elementSummary(deletionRoot),
              deletionRoot == null
                  ? value(match == null ? null : match.getRight(), changedFeature)
                  : NullNode.getInstance(),
              deletionRoot == null
                  ? "The working model and fresh generation changed "
                      + featureName
                      + " differently."
                  : "The upstream source deleted this generated element, but your model has manual"
                      + " changes in it.",
              List.of(ConflictResolution.KEEP_USER, ConflictResolution.TAKE_GENERATED));
      result.put(conflict, logicalConflicts.computeIfAbsent(stableKey, ignored -> description));
    }
    return result;
  }

  private Map<String, ModelConflict> describeOrderedConflicts(
      Resource base, Resource working, Resource generated, TransformationDirection direction) {
    Map<String, EObject> baseById = objectsById(base);
    Map<String, EObject> workingById = objectsById(working);
    Map<String, EObject> generatedById = objectsById(generated);
    Map<String, ModelConflict> result = new HashMap<>();
    for (Map.Entry<String, EObject> entry : workingById.entrySet()) {
      EObject origin = baseById.get(entry.getKey());
      EObject right = generatedById.get(entry.getKey());
      if (origin == null || right == null || !sameType(entry.getValue(), right)) continue;
      for (EStructuralFeature structuralFeature :
          entry.getValue().eClass().getEAllStructuralFeatures()) {
        if (!structuralFeature.isMany()
            || !structuralFeature.isOrdered()
            || policy.policyFor(structuralFeature) != MergeFeaturePolicy.THREE_WAY) continue;
        List<String> baseOrder = orderedValues(origin, structuralFeature);
        List<String> workingOrder = orderedValues(entry.getValue(), structuralFeature);
        List<String> generatedOrder = orderedValues(right, structuralFeature);
        if (baseOrder.equals(workingOrder)
            || baseOrder.equals(generatedOrder)
            || workingOrder.equals(generatedOrder)
            || !sameMembers(baseOrder, workingOrder)
            || !sameMembers(baseOrder, generatedOrder)) continue;
        String key = entry.getKey() + "\u0000" + structuralFeature.getName();
        String conflictId =
            UUID.nameUUIDFromBytes(
                    (direction.name() + "\u0000order:" + key).getBytes(StandardCharsets.UTF_8))
                .toString();
        result.put(
            key,
            new ModelConflict(
                conflictId,
                direction,
                entry.getKey(),
                entry.getValue().eClass().getName(),
                name(entry.getValue()),
                structuralFeature.getName(),
                "CHANGE",
                "/" + escape(entry.getKey()) + "/" + escape(structuralFeature.getName()),
                orderedValueJson(baseOrder),
                orderedValueJson(workingOrder),
                orderedValueJson(generatedOrder),
                "The working model and fresh generation reordered an ordered feature differently.",
                List.of(ConflictResolution.KEEP_USER, ConflictResolution.TAKE_GENERATED)));
      }
    }
    return result;
  }

  private void applyGeneratedOrders(
      Resource working,
      Resource generated,
      Map<String, ModelConflict> conflicts,
      Map<String, ConflictResolution> decisions,
      Map<String, List<String>> originalOrderedValues) {
    Map<String, EObject> workingById = objectsById(working);
    Map<String, EObject> generatedById = objectsById(generated);
    for (Map.Entry<String, ModelConflict> entry : conflicts.entrySet()) {
      ConflictResolution decision = decisions.get(entry.getValue().conflictId());
      // An unresolved order conflict is presented from the untouched merge copy. Clearing and
      // reconstructing a containment merely to preserve its current order can detach required
      // children after BatchMerger has processed adjacent generated differences. Apply an order
      // only after the user has actually chosen one of the two versions.
      if (decision == null) continue;
      String[] parts = entry.getKey().split("\u0000", 2);
      EObject local = workingById.get(parts[0]);
      EObject incoming = generatedById.get(parts[0]);
      if (local == null || incoming == null) continue;
      EStructuralFeature feature = featureByName(local, parts[1]);
      if (feature == null || !feature.isMany() || !feature.isOrdered()) continue;
      Object localValue = local.eGet(feature);
      Object incomingValue = incoming.eGet(feature);
      if (localValue instanceof List<?> localList
          && incomingValue instanceof List<?> incomingList) {
        @SuppressWarnings("unchecked")
        List<Object> writable = (List<Object>) localList;
        writable.clear();
        if (decision != ConflictResolution.TAKE_GENERATED) {
          for (String value : originalOrderedValues.getOrDefault(entry.getKey(), List.of())) {
            EObject original = workingById.get(value);
            writable.add(original == null ? value : original);
          }
        } else {
          for (Object value : incomingList) {
            writable.add(value instanceof EObject object ? workingById.get(id(object)) : value);
          }
        }
      }
    }
  }

  private Map<String, List<String>> orderedFeatureValues(Resource resource) {
    Map<String, List<String>> result = new HashMap<>();
    for (EObject object : allObjects(resource)) {
      String objectId = id(object);
      if (objectId == null || objectId.isBlank()) continue;
      for (EStructuralFeature feature : object.eClass().getEAllStructuralFeatures()) {
        if (feature.isMany() && feature.isOrdered()) {
          result.put(objectId + "\u0000" + feature.getName(), orderedValues(object, feature));
        }
      }
    }
    return result;
  }

  private Map<String, EObject> objectsById(Resource resource) {
    Map<String, EObject> result = new HashMap<>();
    for (EObject object : allObjects(resource)) {
      String objectId = id(object);
      if (objectId != null && !objectId.isBlank()) result.put(objectId, object);
    }
    return result;
  }

  private boolean sameType(EObject left, EObject right) {
    return left.eClass().getName().equals(right.eClass().getName())
        && left.eClass().getEPackage().getNsURI().equals(right.eClass().getEPackage().getNsURI());
  }

  private EStructuralFeature featureByName(EObject object, String name) {
    return object == null ? null : object.eClass().getEStructuralFeature(name);
  }

  private List<String> orderedValues(EObject object, EStructuralFeature feature) {
    if (object == null || feature == null) return List.of();
    Object raw = object.eGet(feature, false);
    if (!(raw instanceof List<?> values)) return List.of();
    return values.stream()
        .map(
            value -> value instanceof EObject objectValue ? id(objectValue) : String.valueOf(value))
        .toList();
  }

  private boolean sameMembers(List<String> left, List<String> right) {
    return left.size() == right.size()
        && new java.util.HashSet<>(left).equals(new java.util.HashSet<>(right));
  }

  private JsonNode orderedValueJson(List<String> values) {
    ArrayNode result = mapper.createArrayNode();
    values.forEach(result::add);
    return result;
  }

  private String orderedFeatureKey(Diff difference) {
    EStructuralFeature structuralFeature = feature(difference);
    EObject owner = difference.getMatch() == null ? null : difference.getMatch().getLeft();
    if (structuralFeature == null
        || owner == null
        || !structuralFeature.isMany()
        || !structuralFeature.isOrdered()) return "";
    return id(owner) + "\u0000" + structuralFeature.getName();
  }

  /**
   * Returns the generated resource affected by a delete/change conflict.
   *
   * <p>EMF Compare emits a real conflict for every dependent containment and reference. Grouping
   * them at the generated-resource boundary gives the user one meaningful choice and also makes
   * that choice apply consistently to the complete generated subtree. It is essential not to walk
   * to the model root here: a PSM {@code SamStack} is shared by every generated AWS resource, so
   * treating it as a deleted workflow's root removes all sibling infrastructure and leaves every
   * relationship view dangling.
   */
  private EObject deletionRoot(Conflict conflict) {
    for (Diff difference : conflict.getDifferences()) {
      if (difference.getSource() != DifferenceSource.RIGHT
          || difference.getKind() != DifferenceKind.DELETE) {
        continue;
      }
      EObject affected = conflictElement(difference);
      if (affected == null) {
        continue;
      }
      return affected;
    }
    return null;
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
      return elementSummary(object);
    }
    if (raw instanceof Enumerator enumerator) {
      return mapper.getNodeFactory().textNode(enumerator.getName());
    }
    return mapper.valueToTree(raw);
  }

  private JsonNode elementSummary(EObject object) {
    if (object == null) {
      return NullNode.getInstance();
    }
    ObjectNode summary = mapper.createObjectNode();
    summary.put("id", id(object));
    summary.put("type", object.eClass().getName());
    String objectName = name(object);
    if (!objectName.isBlank()) {
      summary.put("name", objectName);
    }
    return summary;
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
      if (resource.getContents().size() != 1) {
        throw new IllegalStateException(
            "EMF synchronization produced "
                + resource.getContents().size()
                + " document roots; refusing to repair or persist the invalid merge.");
      }
      ByteArrayOutputStream output = new ByteArrayOutputStream();
      resource.save(output, Map.of());
      return output.toByteArray();
    } catch (Exception ex) {
      throw new IllegalStateException(
          "Could not serialize the merged EMF working resource: " + ex.getMessage(), ex);
    }
  }

  /** Rejects structurally invalid candidates using the Ecore validator only. */
  private void validateStructuralResult(Resource resource) {
    EvlModelResourceDiagnostics.indexXmlIds(resource);
    for (EObject root : resource.getContents()) {
      Diagnostic diagnostic = Diagnostician.INSTANCE.validate(root);
      if (diagnostic.getSeverity() >= Diagnostic.ERROR) {
        throw new IllegalStateException(
            "EMF synchronization produced a structurally invalid model: "
                + diagnostic.getMessage()
                + diagnostic.getChildren().stream()
                    .map(Diagnostic::getMessage)
                    .filter(message -> message != null && !message.isBlank())
                    .distinct()
                    .collect(java.util.stream.Collectors.joining("; ", " [", "]")));
      }
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
