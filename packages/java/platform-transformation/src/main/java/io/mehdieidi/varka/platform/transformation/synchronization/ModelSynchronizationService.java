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

    IComparisonScope scope = new DefaultComparisonScope(working, newGenerated, base);
    Comparison comparison = emfCompare.compare(scope, new BasicMonitor());
    Map<Conflict, ModelConflict> realConflicts = describeRealConflicts(comparison, direction);
    Set<EObject> generatedDeletionRoots =
        generatedDeletionRootsSelectedForRemoval(comparison, working, realConflicts, decisions);
    Set<String> locallyPreservedDeletionClosure =
        locallyPreservedDeletionClosure(comparison, working, realConflicts, decisions);
    Set<String> workingObjectIds =
        allObjects(working).stream().map(this::id).collect(java.util.stream.Collectors.toSet());
    Map<String, Map<String, List<String>>> requiredWorkingReferences =
        requiredReferenceSnapshot(working, workingObjectIds);
    Map<String, Map<String, List<String>>> requiredLocalReferences =
        requiredReferenceSnapshot(working, locallyPreservedDeletionClosure);
    Set<String> generatedObjectIds =
        allObjects(newGenerated).stream()
            .map(this::id)
            .collect(java.util.stream.Collectors.toSet());
    Map<String, Map<String, List<String>>> requiredGeneratedReferences =
        requiredReferenceSnapshot(newGenerated, generatedObjectIds);
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
      // A generated containment deletion is represented by a set of dependent differences.  When
      // the user rejects that deletion, applying otherwise non-conflicting child/reference
      // deletions would retain only a fragment of the local object graph.  Besides violating the
      // user's choice, that commonly produces an invalid model (for example, a Workflow without
      // its required steps).  Treat the locally retained subtree and links to it as one unit.
      if (isChangeTouchingLocallyPreservedDeletion(difference, locallyPreservedDeletionClosure)) {
        continue;
      }
      if (isRequiredLocalContainmentRetained(difference)) {
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
      ModelConflict description =
          conflict == null || conflict.getKind() != ConflictKind.REAL
              ? null
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
      if (conflict == null || conflict.getKind() != ConflictKind.REAL) {
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
    // A resolved conflict can contain a generated required reference alongside the containment
    // move. EMF Compare may mark that sibling handled with the move, leaving the target model
    // structurally invalid. Restore only missing required references from the generated model;
    // existing local values remain untouched.
    restoreRequiredReferences(working, requiredGeneratedReferences);
    restoreRequiredReferences(working, requiredWorkingReferences);
    restoreRequiredReferences(working, requiredLocalReferences);
    // A "keep generated" decision for an upstream deletion applies to the whole generated
    // resource, not just the individual EMF differences that happened to be selected by the
    // batch merger.  In particular, an IAM role, its inline policy, policy document, and
    // statements form one containment tree. Removing only some of those differences produces
    // orphaned PSM infrastructure that cannot be repaired sensibly in the UI.
    generatedDeletionRoots.forEach(root -> EcoreUtil.delete(root, true));
    // Deletion-root cleanup can remove an old mandatory child while retaining its owner. Perform
    // the generated-child recovery last so it cannot itself be deleted by that cleanup.
    restoreMissingRequiredContainments(working, newGenerated);

    // A containment deletion can create dozens of EMF Compare Conflict instances: one for the
    // resource, its contained configuration, and the references that point to it. They are one
    // user decision, not dozens of unrelated conflicts. The map deliberately assigns those raw
    // conflicts the same stable id; expose each logical conflict once to callers.
    List<ModelConflict> logicalConflicts = realConflicts.values().stream().distinct().toList();
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

  private Map<String, Map<String, List<String>>> requiredReferenceSnapshot(
      Resource working, Set<String> preservedIds) {
    Map<String, Map<String, List<String>>> result = new HashMap<>();
    for (EObject object : allObjects(working)) {
      if (!preservedIds.contains(id(object))) continue;
      Map<String, List<String>> references = new HashMap<>();
      for (EReference reference : object.eClass().getEAllReferences()) {
        if (reference.getLowerBound() < 1) continue;
        Object value = object.eGet(reference, false);
        List<String> targets = new java.util.ArrayList<>();
        if (value instanceof EObject target) targets.add(id(target));
        else if (value instanceof List<?> values)
          values.stream()
              .filter(EObject.class::isInstance)
              .map(EObject.class::cast)
              .map(this::id)
              .forEach(targets::add);
        if (!targets.isEmpty()) references.put(reference.getName(), targets);
      }
      if (!references.isEmpty()) result.put(id(object), references);
    }
    return result;
  }

  private void restoreRequiredReferences(
      Resource working, Map<String, Map<String, List<String>>> snapshot) {
    Map<String, EObject> objects = new HashMap<>();
    for (EObject object : allObjects(working)) objects.put(id(object), object);
    snapshot.forEach(
        (ownerId, references) -> {
          EObject owner = objects.get(ownerId);
          if (owner == null) return;
          references.forEach(
              (name, targetIds) -> {
                EStructuralFeature feature = owner.eClass().getEStructuralFeature(name);
                if (!(feature instanceof EReference reference) || reference.getLowerBound() < 1)
                  return;
                Object current = owner.eGet(reference, false);
                if (current instanceof EObject
                    || current instanceof List<?> values && !values.isEmpty()) return;
                List<EObject> targets =
                    targetIds.stream().map(objects::get).filter(Objects::nonNull).toList();
                if (reference.isMany()) owner.eSet(reference, new java.util.ArrayList<>(targets));
                else if (!targets.isEmpty()) owner.eSet(reference, targets.get(0));
              });
        });
  }

  /**
   * Restores an absent mandatory containment from the generated counterpart without overwriting.
   */
  private void restoreMissingRequiredContainments(Resource working, Resource generated) {
    Map<String, EObject> localById = new HashMap<>();
    for (EObject object : allObjects(working)) localById.put(id(object), object);
    for (EObject generatedOwner : allObjects(generated)) {
      EObject localOwner = localById.get(id(generatedOwner));
      if (localOwner == null) continue;
      restoreMissingAssumeRolePolicy(localOwner, generatedOwner);
      restoreMissingStepFunctionDefinition(localOwner, generatedOwner);
      for (EReference reference : generatedOwner.eClass().getEAllReferences()) {
        if (!reference.isContainment()
            || (reference.getLowerBound() < 1 && !"assumeRolePolicy".equals(reference.getName())))
          continue;
        Object localValue = localOwner.eGet(reference, false);
        if (localValue instanceof EObject
            || localValue instanceof List<?> values && !values.isEmpty()) continue;
        Object generatedValue = generatedOwner.eGet(reference, false);
        if (reference.isMany() && generatedValue instanceof List<?> values) {
          List<EObject> copies =
              values.stream()
                  .filter(EObject.class::isInstance)
                  .map(EObject.class::cast)
                  .map(EcoreUtil::copy)
                  .toList();
          if (!copies.isEmpty()) localOwner.eSet(reference, new java.util.ArrayList<>(copies));
        } else if (generatedValue instanceof EObject child) {
          localOwner.eSet(reference, EcoreUtil.copy(child));
        }
      }
    }
  }

  /**
   * A StepFunctionStateMachine's definition is optional in Ecore because AWS also permits URI and
   * string definitions. The PIM-to-PSM ETL currently emits an AslDocument, however, and EMF Compare
   * can filter its optional containment when reconciling an older working graph. Restore that
   * generated definition only when the local state machine has no definition source at all; an
   * existing URI, string, or ASL document remains untouched.
   */
  private void restoreMissingStepFunctionDefinition(EObject localOwner, EObject generatedOwner) {
    if (!"StepFunctionStateMachine".equals(localOwner.eClass().getName())
        || !localOwner.eClass().equals(generatedOwner.eClass())) {
      return;
    }
    EStructuralFeature localUri = localOwner.eClass().getEStructuralFeature("definitionUri");
    EStructuralFeature localString = localOwner.eClass().getEStructuralFeature("definitionString");
    EStructuralFeature localAsl = localOwner.eClass().getEStructuralFeature("aslDocument");
    EStructuralFeature generatedAsl = generatedOwner.eClass().getEStructuralFeature("aslDocument");
    if (!(localAsl instanceof EReference localAslReference)
        || !(generatedAsl instanceof EReference generatedAslReference)
        || !generatedAslReference.isContainment()
        || localOwner.eGet(localAslReference, false) != null
        || hasText(localOwner, localUri)
        || hasText(localOwner, localString)) {
      return;
    }
    Object generatedDefinition = generatedOwner.eGet(generatedAslReference, false);
    if (generatedDefinition instanceof EObject definition) {
      localOwner.eSet(localAslReference, EcoreUtil.copy(definition));
    }
  }

  private boolean hasText(EObject object, EStructuralFeature feature) {
    if (!(feature instanceof EAttribute) || feature.isMany()) {
      return false;
    }
    Object value = object.eGet(feature, false);
    return value != null && !String.valueOf(value).trim().isEmpty();
  }

  private void restoreMissingAssumeRolePolicy(EObject localOwner, EObject generatedOwner) {
    EStructuralFeature generatedFeature =
        generatedOwner.eClass().getEStructuralFeature("assumeRolePolicy");
    EStructuralFeature localFeature = localOwner.eClass().getEStructuralFeature("assumeRolePolicy");
    if (!(generatedFeature instanceof EReference generatedReference)
        || !(localFeature instanceof EReference localReference)) {
      return;
    }
    if (localOwner.eGet(localReference, false) instanceof EObject) return;
    Object generatedValue = generatedOwner.eGet(generatedReference, false);
    if (generatedValue instanceof EObject policy) {
      localOwner.eSet(localReference, EcoreUtil.copy(policy));
    }
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
    // A prior KEEP_USER decision can leave sibling generated AWS resources in the working model
    // after the baseline has advanced without them. They no longer have a comparison diff in a
    // later session, so BatchMerger cannot remove them. Generated PSM resources share the source
    // element in generatedFrom; delete those stale siblings with the accepted generated deletion.
    Set<String> sourceIds =
        roots.stream()
            .map(this::generatedFrom)
            .filter(value -> !value.isBlank())
            .collect(java.util.stream.Collectors.toSet());
    if (!sourceIds.isEmpty()) {
      for (EObject candidate : allObjects(working)) {
        if (sourceIds.contains(generatedFrom(candidate))) {
          roots.add(candidate);
        }
      }
    }
    return roots;
  }

  private String generatedFrom(EObject object) {
    if (object == null) {
      return "";
    }
    EStructuralFeature feature = object.eClass().getEStructuralFeature("generatedFrom");
    if (feature == null) {
      return "";
    }
    Object value = object.eGet(feature, false);
    return value == null ? "" : String.valueOf(value).trim();
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
          || decisions.get(description.conflictId()) != ConflictResolution.KEEP_USER
          || conflict.getDifferences().stream()
              .noneMatch(
                  difference ->
                      difference.getSource() == DifferenceSource.RIGHT
                          && difference.getKind() == DifferenceKind.DELETE)) {
        continue;
      }
      boolean foundContainedRoot = false;
      for (Diff difference : conflict.getDifferences()) {
        if (difference instanceof ReferenceChange change
            && change.getReference() != null
            && change.getReference().isContainment()
            && change.getValue() != null) {
          Match contained = comparison.getMatch(change.getValue());
          if (contained != null && contained.getLeft() != null) {
            addContainmentClosure(contained.getLeft(), preserved);
            foundContainedRoot = true;
          }
        }
      }
      // A containment-reference difference is matched on its parent. Only use that parent as a
      // fallback when EMF Compare cannot expose the contained target match; otherwise retaining a
      // single deleted child would accidentally shield every deletion in the entire model.
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
    expandReferenceClosure(working, preserved);
    return preserved;
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
  private boolean isRequiredLocalContainmentRetained(Diff difference) {
    if (!(difference instanceof ReferenceChange change)
        || difference.getSource() != DifferenceSource.RIGHT
        || difference.getKind() != DifferenceKind.DELETE
        || change.getReference() == null
        || !change.getReference().isContainment()
        || change.getReference().getLowerBound() < 1) {
      return false;
    }
    Match owner = change.getMatch();
    return owner != null && owner.getLeft() != null && owner.getRight() != null;
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
      EObject candidate = affected;
      String sourceId = generatedFrom(candidate);
      // Nested generated configuration and trace objects inherit their source identity from the
      // generated resource. Climb only through that same identity; a containing stack, service,
      // or model belongs to a broader source and must remain outside this deletion decision.
      while (!sourceId.isBlank()
          && candidate.eContainer() != null
          && sourceId.equals(generatedFrom(candidate.eContainer()))) {
        candidate = candidate.eContainer();
      }
      return candidate;
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
      // A model resource has exactly one document root.  ConflictMerger can temporarily leave a
      // duplicate root attached when a root-level generated change is filtered out.  Serializing
      // that transient state produces XMI that cannot be loaded by the platform again.  The
      // working root is authoritative; discard only additional top-level roots before writing.
      List<EObject> roots =
          resource.getContents().stream()
              .filter(EObject.class::isInstance)
              .map(EObject.class::cast)
              .toList();
      if (roots.size() > 1) {
        for (EObject duplicateRoot : roots.subList(1, roots.size())) {
          mergeRootContents(duplicateRoot, roots.get(0));
        }
        resource.getContents().removeAll(roots.subList(1, roots.size()));
      }
      ByteArrayOutputStream output = new ByteArrayOutputStream();
      resource.save(output, Map.of());
      return output.toByteArray();
    } catch (Exception ex) {
      throw new IllegalStateException(
          "Could not serialize the merged EMF working resource: " + ex.getMessage(), ex);
    }
  }

  private void mergeRootContents(EObject source, EObject target) {
    for (EReference containment : source.eClass().getEAllContainments()) {
      Object sourceValue = source.eGet(containment, false);
      Object targetValue = target.eGet(containment, false);
      if (containment.isMany()) {
        if (!(sourceValue instanceof List<?> sourceChildren)
            || !(targetValue instanceof List<?> targetChildren)) {
          continue;
        }
        @SuppressWarnings("unchecked")
        List<EObject> writableTarget = (List<EObject>) targetChildren;
        for (Object value : sourceChildren) {
          if (!(value instanceof EObject sourceChild)) continue;
          EObject targetChild = findContainedById(writableTarget, id(sourceChild));
          if (targetChild == null && !id(sourceChild).isBlank()) {
            writableTarget.add(sourceChild);
          } else {
            mergeRootContents(sourceChild, targetChild);
          }
        }
      } else if (sourceValue instanceof EObject sourceChild) {
        if (!(targetValue instanceof EObject targetChild)) {
          target.eSet(containment, sourceChild);
        } else {
          mergeRootContents(sourceChild, targetChild);
        }
      }
    }
  }

  private EObject findContainedById(List<EObject> children, String expectedId) {
    if (expectedId == null || expectedId.isBlank()) return null;
    return children.stream().filter(child -> expectedId.equals(id(child))).findFirst().orElse(null);
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
