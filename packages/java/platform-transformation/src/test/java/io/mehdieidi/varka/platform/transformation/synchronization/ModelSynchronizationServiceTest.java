package io.mehdieidi.varka.platform.transformation.synchronization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.compare.utils.UseIdentifiers;
import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.emf.ecore.EcoreFactory;
import org.eclipse.emf.ecore.EcorePackage;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

/** End-to-end EMF resource scenarios for the standalone three-way merge engine. */
class ModelSynchronizationServiceTest {

  private final ModelSynchronizationService service =
      new ModelSynchronizationService(new ObjectMapper());
  private EPackage modelPackage;
  private EClass rootClass;
  private EClass nodeClass;
  private EReference nodes;
  private EReference childNodes;
  private EReference peer;
  private EClass requiredNodeClass;
  private EReference requiredNodes;
  private EReference requiredNodeChildren;

  @BeforeEach
  void createMetamodel() {
    EcoreFactory factory = EcoreFactory.eINSTANCE;
    modelPackage = factory.createEPackage();
    modelPackage.setName("sync");
    modelPackage.setNsPrefix("sync");
    modelPackage.setNsURI("urn:varka:test:sync");
    rootClass = factory.createEClass();
    rootClass.setName("Root");
    rootClass
        .getEStructuralFeatures()
        .add(attribute("id", EcorePackage.Literals.ESTRING, true, false));
    nodeClass = factory.createEClass();
    nodeClass.setName("Node");
    nodeClass
        .getEStructuralFeatures()
        .add(attribute("id", EcorePackage.Literals.ESTRING, true, false));
    nodeClass
        .getEStructuralFeatures()
        .add(attribute("name", EcorePackage.Literals.ESTRING, false, false));
    nodeClass
        .getEStructuralFeatures()
        .add(attribute("memory", EcorePackage.Literals.EINT, false, false));
    nodeClass
        .getEStructuralFeatures()
        .add(attribute("description", EcorePackage.Literals.ESTRING, false, false));
    nodeClass
        .getEStructuralFeatures()
        .add(attribute("generatedFrom", EcorePackage.Literals.ESTRING, false, false));
    nodeClass
        .getEStructuralFeatures()
        .add(attribute("manuallyMaintained", EcorePackage.Literals.EBOOLEAN, false, false));
    EAttribute incomingTraces =
        attribute("incomingTraces", EcorePackage.Literals.ESTRING, false, true);
    incomingTraces.setTransient(true);
    nodeClass.getEStructuralFeatures().add(incomingTraces);
    nodeClass
        .getEStructuralFeatures()
        .add(attribute("tags", EcorePackage.Literals.ESTRING, false, true));
    nodes = factory.createEReference();
    nodes.setName("nodes");
    nodes.setContainment(true);
    nodes.setUpperBound(-1);
    nodes.setEType(nodeClass);
    rootClass.getEStructuralFeatures().add(nodes);
    childNodes = factory.createEReference();
    childNodes.setName("children");
    childNodes.setContainment(true);
    childNodes.setUpperBound(-1);
    childNodes.setEType(nodeClass);
    nodeClass.getEStructuralFeatures().add(childNodes);
    peer = factory.createEReference();
    peer.setName("peer");
    peer.setEType(nodeClass);
    nodeClass.getEStructuralFeatures().add(peer);
    requiredNodeClass = factory.createEClass();
    requiredNodeClass.setName("RequiredNode");
    requiredNodeClass
        .getEStructuralFeatures()
        .add(attribute("id", EcorePackage.Literals.ESTRING, true, false));
    requiredNodeChildren = factory.createEReference();
    requiredNodeChildren.setName("children");
    requiredNodeChildren.setContainment(true);
    requiredNodeChildren.setLowerBound(1);
    requiredNodeChildren.setUpperBound(-1);
    requiredNodeChildren.setEType(nodeClass);
    requiredNodeClass.getEStructuralFeatures().add(requiredNodeChildren);
    requiredNodes = factory.createEReference();
    requiredNodes.setName("requiredNodes");
    requiredNodes.setContainment(true);
    requiredNodes.setLowerBound(0);
    requiredNodes.setUpperBound(-1);
    requiredNodes.setEType(requiredNodeClass);
    rootClass.getEStructuralFeatures().add(requiredNodes);
    modelPackage.getEClassifiers().add(rootClass);
    modelPackage.getEClassifiers().add(nodeClass);
    modelPackage.getEClassifiers().add(requiredNodeClass);
  }

  @Test
  void scenario4PreservesManualRefinement() {
    var result = merge(model(512, "A"), model(1024, "A"), model(512, "A"));
    assertEquals(1024, value(result.mergedWorking(), "n1", "memory"));
    assertTrue(result.conflicts().isEmpty());
  }

  /** Catalog A-01: a user-only scalar edit is preserved. */
  @Test
  void a01PreservesUserScalarEditWhenGeneratorIsUnchanged() {
    var result = merge(model(30, "A"), model(60, "A"), model(30, "A"));

    assertEquals(60, value(result.mergedWorking(), "n1", "memory"));
    assertTrue(result.conflicts().isEmpty());
  }

  /** Catalog A-02: a generator-only scalar edit is applied. */
  @Test
  void a02AppliesGeneratedScalarEditWhenUserIsUnchanged() {
    var result = merge(model(30, "A"), model(30, "A"), model(45, "A"));

    assertEquals(45, value(result.mergedWorking(), "n1", "memory"));
    assertTrue(result.conflicts().isEmpty());
  }

  /** Catalog A-03: equal scalar edits converge without a conflict. */
  @Test
  void a03AcceptsConvergentScalarEditWithoutConflict() {
    var result = merge(model(30, "A"), model(60, "A"), model(60, "A"));

    assertEquals(60, value(result.mergedWorking(), "n1", "memory"));
    assertTrue(result.conflicts().isEmpty());
  }

  /** Catalog A-04: incompatible scalar edits produce an explicit conflict. */
  @Test
  void a04ReportsIncompatibleScalarEditsAsAConflict() {
    var result = merge(model(30, "A"), model(60, "A"), model(45, "A"));

    assertEquals(60, value(result.mergedWorking(), "n1", "memory"));
    assertEquals(1, result.conflicts().size());
    assertEquals("memory", result.conflicts().get(0).featureName());
    assertEquals(
        List.of(ConflictResolution.KEEP_USER, ConflictResolution.TAKE_GENERATED),
        result.conflicts().get(0).availableResolutions());
  }

  /**
   * Catalog A-08: Ecore datatype conversion makes alternate lexical integer representations equal.
   * The value is normalized by the Ecore resource loader, not by a Java merge fallback.
   */
  @Test
  void a08TreatsEquivalentEcoreDatatypeRepresentationsAsEqual() throws Exception {
    Resource base = model(512, "A");
    Resource working = load(bytes(base), "a08-working");
    byte[] lexicalVariant =
        new String(bytes(base), StandardCharsets.UTF_8)
            .replace("memory=\"512\"", "memory=\"0512\"")
            .getBytes(StandardCharsets.UTF_8);
    Resource generated = load(lexicalVariant, "a08-generated");

    var result = merge(base, working, generated);

    assertEquals(512, value(result.mergedWorking(), "n1", "memory"));
    assertTrue(result.conflicts().isEmpty());
    assertEquals(0, result.incomingChanges());
  }

  @Test
  void legacyIdentityMigrationMatchesGeneratedObjectsWhoseIdsPredateStableGeneration()
      throws Exception {
    Resource base = model(512, "A");
    Resource local = load(bytes(base), "legacy-local");
    Resource incoming = load(bytes(base), "legacy-incoming");
    set(base, "n1", "generatedFrom", "source-workflow");
    set(local, "n1", "generatedFrom", "source-workflow");
    set(incoming, "n1", "generatedFrom", "source-workflow");
    set(base, "n1", "id", "legacy-generated-id");
    set(local, "n1", "id", "legacy-generated-id");
    set(incoming, "n1", "id", "stable-generated-id");
    set(local, "legacy-generated-id", "memory", 1024);
    set(incoming, "stable-generated-id", "description", "B");

    ModelSynchronizationService migrationMerger =
        new ModelSynchronizationService(
            new ObjectMapper(), new SemanticMergePolicy(), UseIdentifiers.NEVER);
    var result =
        migrationMerger.synchronize(base, local, incoming, TransformationDirection.PIM_TO_AWS_PSM);

    assertEquals(1024, value(result.mergedWorking(), "legacy-generated-id", "memory"));
    assertEquals("B", value(result.mergedWorking(), "legacy-generated-id", "description"));
    assertTrue(result.conflicts().isEmpty());
  }

  @Test
  void scenario5PropagatesIncomingChange() {
    var result = merge(model(512, "A"), model(512, "A"), model(512, "B"));
    assertEquals("B", value(result.mergedWorking(), "n1", "description"));
  }

  @Test
  void scenario6CombinesIndependentEdits() {
    var result = merge(model(30, "A"), model(60, "A"), model(30, "B"));
    assertEquals(60, value(result.mergedWorking(), "n1", "memory"));
    assertEquals("B", value(result.mergedWorking(), "n1", "description"));
    assertTrue(result.conflicts().isEmpty());
  }

  @Test
  void sameUserAndGeneratedChangeConvergesWithoutConflict() {
    var result = merge(model(512, "A"), model(1024, "A"), model(1024, "A"));
    assertEquals(1024, value(result.mergedWorking(), "n1", "memory"));
    assertTrue(result.conflicts().isEmpty());
    assertEquals(1, result.incomingChanges());
  }

  @Test
  void scenario7ReportsAndResolvesSameFeatureConflictBothWays() {
    Resource base = model(30, "A");
    Resource local = model(60, "A");
    Resource incoming = model(45, "A");
    var pending = merge(base, local, incoming);
    assertEquals(60, value(pending.mergedWorking(), "n1", "memory"));
    assertEquals(1, pending.conflicts().size());
    assertEquals(30, pending.conflicts().get(0).baseValue().asInt());
    assertEquals("n1", pending.conflicts().get(0).elementId());
    assertEquals("Node", pending.conflicts().get(0).elementEClass());
    assertEquals("memory", pending.conflicts().get(0).featureName());
    assertEquals("/n1/memory", pending.conflicts().get(0).jsonPointer());
    assertEquals(60, pending.conflicts().get(0).workingValue().asInt());
    assertEquals(45, pending.conflicts().get(0).generatedValue().asInt());
    assertNotEquals(
        pending.conflicts().get(0).conflictId(),
        service
            .synchronize(
                model(30, "A"),
                model(60, "A"),
                model(45, "A"),
                TransformationDirection.PIM_TO_AWS_PSM)
            .conflicts()
            .get(0)
            .conflictId());

    String conflictId = pending.conflicts().get(0).conflictId();
    var keepUser =
        service.synchronize(
            model(30, "A"),
            model(60, "A"),
            model(45, "A"),
            TransformationDirection.CIM_TO_PIM,
            Map.of(conflictId, ConflictResolution.KEEP_USER));
    var takeGenerated =
        service.synchronize(
            model(30, "A"),
            model(60, "A"),
            model(45, "A"),
            TransformationDirection.CIM_TO_PIM,
            Map.of(conflictId, ConflictResolution.TAKE_GENERATED));
    assertEquals(60, value(keepUser.mergedWorking(), "n1", "memory"));
    assertEquals(45, value(takeGenerated.mergedWorking(), "n1", "memory"));
  }

  /** Catalog M-05: incompatible edits to the same scalar require an explicit resolution. */
  @Test
  void m05IncompatibleEditEditRequiresKeepUserOrTakeGenerated() {
    Resource base = model(30, "A");
    Resource local = model(60, "A");
    Resource generated = model(45, "A");

    var pending = service.synchronize(base, local, generated, TransformationDirection.CIM_TO_PIM);

    assertEquals(1, pending.conflicts().size());
    assertEquals(60, value(pending.mergedWorking(), "n1", "memory"));
    String conflictId = pending.conflicts().get(0).conflictId();

    var keepUser =
        service.synchronize(
            model(30, "A"),
            model(60, "A"),
            model(45, "A"),
            TransformationDirection.CIM_TO_PIM,
            Map.of(conflictId, ConflictResolution.KEEP_USER));
    var takeGenerated =
        service.synchronize(
            model(30, "A"),
            model(60, "A"),
            model(45, "A"),
            TransformationDirection.CIM_TO_PIM,
            Map.of(conflictId, ConflictResolution.TAKE_GENERATED));

    assertEquals(60, value(keepUser.mergedWorking(), "n1", "memory"));
    assertEquals(45, value(takeGenerated.mergedWorking(), "n1", "memory"));
    assertTrue(keepUser.conflicts().isEmpty());
    assertTrue(takeGenerated.conflicts().isEmpty());
  }

  /** Catalog U-01: KEEP_USER retains the manually edited scalar value. */
  @Test
  void u01KeepUserRetainsWorkingEdit() {
    Resource base = model(30, "A");
    Resource working = model(60, "A");
    Resource generated = model(45, "A");
    var pending = merge(base, working, generated);
    String conflictId = pending.conflicts().get(0).conflictId();

    var resolved =
        service.synchronize(
            model(30, "A"),
            model(60, "A"),
            model(45, "A"),
            TransformationDirection.CIM_TO_PIM,
            Map.of(conflictId, ConflictResolution.KEEP_USER));

    assertTrue(resolved.conflicts().isEmpty());
    assertEquals(60, value(resolved.mergedWorking(), "n1", "memory"));
  }

  /** Catalog G-01: TAKE_GENERATED replaces the manually edited scalar value. */
  @Test
  void g01TakeGeneratedAppliesGeneratedEdit() {
    Resource base = model(30, "A");
    Resource working = model(60, "A");
    Resource generated = model(45, "A");
    var pending = merge(base, working, generated);
    String conflictId = pending.conflicts().get(0).conflictId();

    var resolved =
        service.synchronize(
            model(30, "A"),
            model(60, "A"),
            model(45, "A"),
            TransformationDirection.CIM_TO_PIM,
            Map.of(conflictId, ConflictResolution.TAKE_GENERATED));

    assertTrue(resolved.conflicts().isEmpty());
    assertEquals(45, value(resolved.mergedWorking(), "n1", "memory"));
  }

  @Test
  void scenario8AddsGeneratedElementWithoutLosingRefinement() {
    Resource base = model(512, "A");
    Resource local = model(1024, "A");
    Resource incoming = model(512, "A");
    addNode(incoming, "n2", "Generated", 256, "new");
    var result = merge(base, local, incoming);
    assertEquals(1024, value(result.mergedWorking(), "n1", "memory"));
    assertEquals("Generated", value(result.mergedWorking(), "n2", "name"));
  }

  @Test
  void scenario9DeletesUnmodifiedGeneratedElement() {
    Resource incoming = emptyModel();
    var result = merge(model(512, "A"), model(512, "A"), incoming);
    assertNull(find(result.mergedWorking(), "n1"));
    assertTrue(result.conflicts().isEmpty());
  }

  /** Catalog M-10: deleting an untouched generated child removes the obsolete subtree cleanly. */
  @Test
  void m10UntouchedGeneratedDeletionRemovesTheCompleteSubtree() {
    Resource base = modelWithChild("generated-child");
    Resource local = modelWithChild("generated-child");
    Resource generated = model(512, "A");

    var result = service.synchronize(base, local, generated, TransformationDirection.CIM_TO_PIM);

    assertTrue(result.conflicts().isEmpty());
    assertNotNull(find(result.mergedWorking(), "n1"));
    assertNull(find(result.mergedWorking(), "child"));
    assertEquals(1, result.mergedWorking().getContents().size());
  }

  @Test
  void scenario10ReportsDeleteChangeConflict() {
    var result = merge(model(30, "A"), model(60, "A"), emptyModel());
    assertFalse(result.conflicts().isEmpty());
    assertEquals(60, value(result.mergedWorking(), "n1", "memory"));
  }

  @Test
  void scenario11PreservesUserCreatedElement() {
    Resource local = model(512, "A");
    addNode(local, "user", "Manual", 1, "owned by user");
    var result = merge(model(512, "A"), local, model(512, "A"));
    assertEquals("Manual", value(result.mergedWorking(), "user", "name"));
  }

  @Test
  void scenario12PreservesUserDeletionAndConflictsWithIncomingChange() {
    var unchanged = merge(model(512, "A"), emptyModel(), model(512, "A"));
    assertNull(find(unchanged.mergedWorking(), "n1"));
    assertTrue(unchanged.conflicts().isEmpty());
    var changed = merge(model(512, "A"), emptyModel(), model(512, "B"));
    assertFalse(changed.conflicts().isEmpty());
  }

  @Test
  void scenario13PreservesReferenceWhileApplyingUnrelatedChange() {
    Resource base = twoNodeModel("A");
    Resource local = twoNodeModel("A");
    Resource incoming = twoNodeModel("B");
    find(local, "n1").eSet(peer, find(local, "n2"));
    var result = merge(base, local, incoming);
    assertEquals("B", value(result.mergedWorking(), "n1", "description"));
    assertEquals("n2", id((EObject) find(result.mergedWorking(), "n1").eGet(peer)));
  }

  @Test
  void traceMetadataUsesNormalThreeWaySemanticsWithoutJavaOwnershipOverrides() {
    Resource base = model(512, "A");
    Resource local = model(512, "A");
    Resource incoming = model(512, "A");
    set(base, "n1", "generatedFrom", "source-v1");
    set(local, "n1", "generatedFrom", "local-edit");
    set(incoming, "n1", "generatedFrom", "source-v2");
    var result = merge(base, local, incoming);
    assertEquals("local-edit", value(result.mergedWorking(), "n1", "generatedFrom"));
    assertEquals(1, result.conflicts().size());

    String conflictId = result.conflicts().get(0).conflictId();
    var resolved =
        service.synchronize(
            base,
            modelWithGeneratedFrom("local-edit"),
            modelWithGeneratedFrom("source-v2"),
            TransformationDirection.CIM_TO_PIM,
            Map.of(conflictId, ConflictResolution.TAKE_GENERATED));
    assertEquals("source-v2", value(resolved.mergedWorking(), "n1", "generatedFrom"));
  }

  @Test
  void ignoresInverseTraceMetadataEvenWhenGeneratedChangesIt() {
    Resource base = model(512, "A");
    Resource local = model(512, "A");
    Resource incoming = model(512, "A");
    set(base, "n1", "incomingTraces", List.of("trace-a"));
    set(local, "n1", "incomingTraces", List.of("trace-user"));
    set(incoming, "n1", "incomingTraces", List.of("trace-generated"));

    var result = merge(base, local, incoming);
    assertEquals(List.of("trace-user"), value(result.mergedWorking(), "n1", "incomingTraces"));
    assertTrue(result.conflicts().isEmpty());
    assertEquals(0, result.incomingChanges());
  }

  @Test
  void appliesGeneratedReferenceAddAndRemoval() {
    Resource base = twoNodeModel("A");
    Resource local = twoNodeModel("A");
    Resource added = twoNodeModel("A");
    find(added, "n1").eSet(peer, find(added, "n2"));
    var addResult = merge(base, local, added);
    assertEquals("n2", id((EObject) find(addResult.mergedWorking(), "n1").eGet(peer)));

    Resource removed = twoNodeModel("A");
    var removeResult = merge(added, addResult.mergedWorking(), removed);
    assertNull(find(removeResult.mergedWorking(), "n1").eGet(peer));
  }

  @Test
  void preservesManualNestedEditButReportsNestedDeleteChangeConflict() {
    Resource base = modelWithChild("base-child");
    Resource local = modelWithChild("user-child");
    Resource incoming = model(512, "A");
    var pending = merge(base, local, incoming);
    assertFalse(pending.conflicts().isEmpty());
    assertEquals("user-child", value(pending.mergedWorking(), "child", "name"));

    String conflictId = pending.conflicts().get(0).conflictId();
    var resolved =
        service.synchronize(
            base,
            local,
            incoming,
            TransformationDirection.CIM_TO_PIM,
            Map.of(conflictId, ConflictResolution.TAKE_GENERATED));
    assertNull(find(resolved.mergedWorking(), "child"));
  }

  @Test
  void acceptingGeneratedDeletionRemovesSelectedSubtreeButPreservesIndependentLocalAddition()
      throws Exception {
    Resource base = model(512, "A");
    set(base, "n1", "generatedFrom", "workflow-source");
    Resource local = load(bytes(base), "iam-local");
    set(local, "n1", "description", "manual workflow role refinement");
    // This role is absent from Base and therefore is an independent local addition. A matching
    // metadata string must never make Java infer that it belongs to another deletion.
    EObject role = addNode(local, "iam-role", "Workflow execution role", 0, "generated role");
    role.eSet(feature("generatedFrom"), "workflow-source");
    EObject inlinePolicy = addChild(local, "iam-role", "inline-policy", "Event invoke");
    inlinePolicy.eSet(feature("generatedFrom"), "workflow-source");
    EObject policyDocument = addChild(local, "inline-policy", "policy-document", "Policy document");
    policyDocument.eSet(feature("generatedFrom"), "workflow-source");
    EObject statement = addChild(local, "policy-document", "statement", "Allow StartExecution");
    statement.eSet(feature("generatedFrom"), "workflow-source");
    EObject eventTarget = addNode(local, "event-target", "Event target", 0, "generated support");
    eventTarget.eSet(feature("generatedFrom"), "workflow-source");
    eventTarget.eSet(peer, role);
    Resource incoming = emptyModel();
    byte[] rawBase = bytes(base);
    byte[] rawLocal = bytes(local);
    byte[] rawIncoming = bytes(incoming);

    var pending =
        service.synchronize(base, local, incoming, TransformationDirection.PIM_TO_AWS_PSM);
    assertEquals(1, pending.conflicts().size());
    String conflictId = pending.conflicts().get(0).conflictId();

    var resolved =
        service.synchronize(
            load(rawBase, "iam-resolve-base"),
            load(rawLocal, "iam-resolve-local"),
            load(rawIncoming, "iam-resolve-incoming"),
            TransformationDirection.PIM_TO_AWS_PSM,
            Map.of(conflictId, ConflictResolution.TAKE_GENERATED));

    assertTrue(resolved.conflicts().isEmpty());
    assertNull(find(resolved.mergedWorking(), "n1"));
    assertNotNull(find(resolved.mergedWorking(), "iam-role"));
    assertNotNull(find(resolved.mergedWorking(), "inline-policy"));
    assertNotNull(find(resolved.mergedWorking(), "policy-document"));
    assertNotNull(find(resolved.mergedWorking(), "statement"));
    EObject remainingEventTarget = find(resolved.mergedWorking(), "event-target");
    assertNotNull(remainingEventTarget);
    assertEquals("iam-role", id((EObject) remainingEventTarget.eGet(peer)));
  }

  @Test
  void keepingUserVersionOfDeletedContainerKeepsItsCompleteLocalSubtree() throws Exception {
    Resource base = modelWithChild("generated-child");
    Resource local = modelWithChild("generated-child");
    // This mirrors a user refinement of a generated workflow before its source process is
    // removed. The child itself is unchanged, so EMF Compare exposes its deletion separately.
    set(local, "n1", "description", "manual workflow refinement");
    find(base, "child").eSet(peer, find(base, "n1"));
    find(local, "child").eSet(peer, find(local, "n1"));
    // A sibling reference models a ServiceElementMembership/trace record: it is not contained by
    // the workflow but must remain when the workflow is retained.
    addNode(base, "membership", "Membership", 0, "generated support").eSet(peer, find(base, "n1"));
    addNode(local, "membership", "Membership", 0, "generated support")
        .eSet(peer, find(local, "n1"));
    Resource incoming = emptyModel();
    byte[] rawBase = bytes(base);
    byte[] rawLocal = bytes(local);
    byte[] rawIncoming = bytes(incoming);

    var pending = merge(base, local, incoming);
    assertFalse(pending.conflicts().isEmpty());
    String conflictId = pending.conflicts().get(0).conflictId();

    // A pending comparison is intentionally allowed to merge safe changes into its temporary
    // working resource. A resolution always starts from the separately persisted raw snapshots.
    var kept =
        service.synchronize(
            load(rawBase, "keep-user-base"),
            load(rawLocal, "keep-user-local"),
            load(rawIncoming, "keep-user-incoming"),
            TransformationDirection.CIM_TO_PIM,
            Map.of(conflictId, ConflictResolution.KEEP_USER));

    assertTrue(kept.conflicts().isEmpty());
    assertEquals("manual workflow refinement", value(kept.mergedWorking(), "n1", "description"));
    assertEquals("generated-child", value(kept.mergedWorking(), "child", "name"));
    assertEquals("n1", id((EObject) find(kept.mergedWorking(), "child").eGet(peer)));
    assertEquals("n1", id((EObject) find(kept.mergedWorking(), "membership").eGet(peer)));

    // The raw baseline advances without the retained workflow. A later, unrelated generated
    // deletion must still apply; KEEP_USER must not make the model root/reference graph sticky.
    Resource nextBase = emptyModel();
    addNode(nextBase, "obsolete", "Obsolete generated resource", 1, "old");
    Resource nextWorking = load(kept.mergedWorkingXmi(), "next-working");
    addNode(nextWorking, "obsolete", "Obsolete generated resource", 1, "old");
    var next = merge(nextBase, nextWorking, emptyModel());
    assertNull(find(next.mergedWorking(), "obsolete"));
    assertNotNull(find(next.mergedWorking(), "n1"));
    assertTrue(next.conflicts().isEmpty());
  }

  @Test
  void reportsOneReadableConflictForADeletedGeneratedSubtree() {
    Resource base = modelWithChild("generated-child");
    Resource local = modelWithChild("generated-child");
    set(local, "n1", "description", "manual workflow refinement");
    for (int index = 0; index < 24; index++) {
      addChild(base, "n1", "child-" + index, "Generated support " + index);
      addChild(local, "n1", "child-" + index, "Generated support " + index);
    }

    var pending = merge(base, local, emptyModel());

    assertEquals(1, pending.conflicts().size());
    var conflict = pending.conflicts().get(0);
    assertEquals("generated element", conflict.featureName());
    assertEquals("DELETE", conflict.differenceKind());
    assertTrue(conflict.description().contains("upstream source deleted"));
    assertEquals("Function", conflict.workingValue().path("name").asText());
    assertEquals("Node", conflict.workingValue().path("type").asText());
    assertTrue(conflict.generatedValue().isNull());
  }

  /** Catalog X-06: many EMF deletion differences collapse to one logical subtree conflict. */
  @Test
  void x06CollapsesManyDeletedSubtreeDifferencesIntoOneConflict() {
    Resource base = modelWithChild("generated-child");
    for (int index = 0; index < 12; index++) {
      addChild(base, "child", "nested-" + index, "Nested generated support");
    }
    Resource working = loadUnchecked(bytesUnchecked(base), "x06-working");
    set(working, "child", "description", "manual subtree refinement");

    var result = merge(base, working, emptyModel());

    assertEquals(1, result.conflicts().size());
    assertEquals("generated element", result.conflicts().get(0).featureName());
    assertEquals("DELETE", result.conflicts().get(0).differenceKind());
    assertTrue(result.conflicts().get(0).description().contains("upstream source deleted"));
  }

  /** Catalog X-08: a user reference to a deleted generated element is never left dangling. */
  @Test
  void x08ReportsDependencyConflictForUserReferenceToDeletedGeneratedElement() {
    Resource base = twoNodeModel("A");
    Resource working = loadUnchecked(bytesUnchecked(base), "x08-working");
    EObject user = addNode(working, "user-element", "User element", 1, "manual");
    user.eSet(peer, find(working, "n1"));
    Resource generated = emptyModel();

    var result = merge(base, working, generated);
    EObject target = (EObject) find(result.mergedWorking(), "user-element").eGet(peer, false);

    assertFalse(result.conflicts().isEmpty());
    assertNotNull(target);
    assertNotNull(find(result.mergedWorking(), id(target)));
  }

  @Test
  void resolvesAConflictingMoveWhenTheLocalParentWasAddedOnBothSides() throws Exception {
    Resource base = model(512, "A");
    addChild(base, "n1", "statement", "Assume role");
    Resource local = loadUnchecked(bytes(base), "move-local");
    Resource incoming = loadUnchecked(bytes(base), "move-incoming");
    EObject localPolicy = addNode(local, "policy", "Policy document", 0, "local container");
    addNode(incoming, "policy", "Policy document", 0, "generated container");
    EObject incomingPolicy = addNode(incoming, "generated-policy", "Generated policy", 0, "target");
    moveChild(local, "n1", localPolicy, "statement");
    moveChild(incoming, "n1", incomingPolicy, "statement");
    byte[] rawBase = bytes(base);
    byte[] rawLocal = bytes(local);
    byte[] rawIncoming = bytes(incoming);

    var pending =
        service.synchronize(base, local, incoming, TransformationDirection.PIM_TO_AWS_PSM);
    assertFalse(pending.conflicts().isEmpty());
    Map<String, ConflictResolution> decisions = new java.util.LinkedHashMap<>();
    pending
        .conflicts()
        .forEach(
            conflict -> decisions.put(conflict.conflictId(), ConflictResolution.TAKE_GENERATED));
    var resolved =
        service.synchronize(
            load(rawBase, "move-resolve-base"),
            load(rawLocal, "move-resolve-local"),
            load(rawIncoming, "move-resolve-incoming"),
            TransformationDirection.PIM_TO_AWS_PSM,
            decisions);

    assertTrue(resolved.conflicts().isEmpty());
    assertEquals("generated-policy", id(find(resolved.mergedWorking(), "statement").eContainer()));
  }

  @Test
  void scenario15IsIdempotentAndDoesNotDuplicateElements() throws Exception {
    Resource incoming = model(512, "B");
    addNode(incoming, "n2", "Generated", 256, "new");
    var first = merge(model(512, "A"), model(1024, "A"), incoming);
    Resource secondWorking = load(first.mergedWorkingXmi(), "second-working");
    Resource nextBase = load(bytes(incoming), "next-base");
    Resource nextIncoming = load(bytes(incoming), "next-incoming");
    var second = merge(nextBase, secondWorking, nextIncoming);
    assertEquals(0, second.incomingChanges());
    assertTrue(second.conflicts().isEmpty());
    assertEquals(2, contents(second.mergedWorking()).size());
  }

  @Test
  void mergesMultiValuedAttributesAndContainmentMovesSemantically() {
    Resource base = twoNodeModel("A");
    Resource local = twoNodeModel("A");
    Resource incoming = twoNodeModel("A");
    @SuppressWarnings("unchecked")
    List<String> localTags = (List<String>) find(local, "n1").eGet(feature("tags"));
    @SuppressWarnings("unchecked")
    List<String> incomingTags = (List<String>) find(incoming, "n1").eGet(feature("tags"));
    localTags.add("manual");
    incomingTags.add("generated");
    var result = merge(base, local, incoming);
    @SuppressWarnings("unchecked")
    List<String> mergedTags =
        (List<String>) find(result.mergedWorking(), "n1").eGet(feature("tags"));
    assertTrue(mergedTags.containsAll(List.of("manual", "generated")));
  }

  @Test
  void mergesGeneratedNestedSubtreeAdditionAndDeletion() {
    Resource base = model(512, "A");
    Resource working = model(1024, "A");
    Resource incoming = model(512, "A");
    addChild(incoming, "n1", "child", "Generated child");

    var added = merge(base, working, incoming);
    assertEquals("Generated child", value(added.mergedWorking(), "child", "description"));

    Resource nextBase = incoming;
    Resource nextWorking = loadUnchecked(added.mergedWorkingXmi(), "nested-delete-working");
    Resource nextIncoming = model(512, "A");
    var deleted = merge(nextBase, nextWorking, nextIncoming);
    assertNull(find(deleted.mergedWorking(), "child"));
  }

  /** Catalog T-01: a generated nested subtree is added with all descendants and links. */
  @Test
  void t01AddsCompleteGeneratedNestedSubtree() {
    Resource base = twoNodeModel("A");
    Resource working = loadUnchecked(bytesUnchecked(base), "t01-working");
    Resource generated = loadUnchecked(bytesUnchecked(base), "t01-generated");
    addNode(generated, "generated-parent", "Generated parent", 0, "Generated parent");
    EObject generatedChild =
        addChild(generated, "generated-parent", "generated-child", "Generated child");
    generatedChild.eSet(peer, find(generated, "n2"));

    var result = merge(base, working, generated);
    Resource reloaded = loadUnchecked(result.mergedWorkingXmi(), "t01-reloaded");

    assertTrue(result.conflicts().isEmpty());
    assertNotNull(find(reloaded, "generated-parent"));
    assertNotNull(find(reloaded, "generated-child"));
    assertEquals("generated-parent", id(find(reloaded, "generated-child").eContainer()));
    assertEquals("n2", id((EObject) find(reloaded, "generated-child").eGet(peer)));
  }

  /** Catalog T-02: an untouched generated subtree is removed completely, including links. */
  @Test
  void t02RemovesCompleteUntouchedGeneratedSubtree() {
    Resource base = twoNodeModel("A");
    EObject child = addChild(base, "n1", "generated-child", "Generated child");
    find(base, "n2").eSet(peer, child);
    Resource working = loadUnchecked(bytesUnchecked(base), "t02-working");
    Resource generated = twoNodeModel("A");

    var result = merge(base, working, generated);

    assertTrue(result.conflicts().isEmpty());
    assertNotNull(find(result.mergedWorking(), "n1"));
    assertNull(find(result.mergedWorking(), "generated-child"));
    assertNull(find(result.mergedWorking(), "n2").eGet(peer));
  }

  /** Catalog T-04: a child edit and ancestor deletion become one logical conflict. */
  @Test
  void t04ReportsOneConflictWhenEditedChildAncestorIsDeleted() {
    Resource base = modelWithChild("base-child");
    Resource working = loadUnchecked(bytesUnchecked(base), "t04-working");
    set(working, "child", "description", "manual child edit");

    var result = merge(base, working, emptyModel());

    assertEquals(1, result.conflicts().size());
    assertEquals("generated element", result.conflicts().get(0).featureName());
    assertEquals("DELETE", result.conflicts().get(0).differenceKind());
  }

  /** Catalog T-05: KEEP_USER retains a deleted container and its complete local subtree. */
  @Test
  void t05KeepUserRetainsDeletedContainerSubtree() {
    Resource base = modelWithChild("base-child");
    Resource working = loadUnchecked(bytesUnchecked(base), "t05-working");
    set(working, "n1", "description", "manual parent edit");
    Resource generated = emptyModel();
    byte[] rawBase = bytesUnchecked(base);
    byte[] rawWorking = bytesUnchecked(working);
    byte[] rawGenerated = bytesUnchecked(generated);
    var pending = merge(base, working, generated);
    String conflictId = pending.conflicts().get(0).conflictId();

    var kept =
        service.synchronize(
            loadUnchecked(rawBase, "t05-resolve-base"),
            loadUnchecked(rawWorking, "t05-resolve-working"),
            loadUnchecked(rawGenerated, "t05-resolve-generated"),
            TransformationDirection.CIM_TO_PIM,
            Map.of(conflictId, ConflictResolution.KEEP_USER));

    assertTrue(kept.conflicts().isEmpty());
    assertEquals("manual parent edit", value(kept.mergedWorking(), "n1", "description"));
    assertNotNull(find(kept.mergedWorking(), "child"));
  }

  /** Catalog T-06: TAKE_GENERATED removes the whole deleted container subtree. */
  @Test
  void t06TakeGeneratedRemovesDeletedContainerSubtree() {
    Resource base = modelWithChild("base-child");
    Resource working = loadUnchecked(bytesUnchecked(base), "t06-working");
    set(working, "child", "description", "manual child edit");
    Resource generated = emptyModel();
    byte[] rawBase = bytesUnchecked(base);
    byte[] rawWorking = bytesUnchecked(working);
    byte[] rawGenerated = bytesUnchecked(generated);
    var pending = merge(base, working, generated);
    String conflictId = pending.conflicts().get(0).conflictId();

    var removed =
        service.synchronize(
            loadUnchecked(rawBase, "t06-resolve-base"),
            loadUnchecked(rawWorking, "t06-resolve-working"),
            loadUnchecked(rawGenerated, "t06-resolve-generated"),
            TransformationDirection.CIM_TO_PIM,
            Map.of(conflictId, ConflictResolution.TAKE_GENERATED));

    assertTrue(removed.conflicts().isEmpty());
    assertNull(find(removed.mergedWorking(), "n1"));
    assertNull(find(removed.mergedWorking(), "child"));
  }

  /** Catalog T-08: a generated child move is applied after its newly added parent. */
  @Test
  void t08MovesChildUnderParentAddedByTheGeneratedSide() {
    Resource base = model(512, "A");
    addChild(base, "n1", "movable", "Movable child");
    Resource working = loadUnchecked(bytesUnchecked(base), "t08-working");
    Resource generated = loadUnchecked(bytesUnchecked(base), "t08-generated");
    EObject newParent = addNode(generated, "new-parent", "Generated parent", 0, "new parent");
    moveChild(generated, "n1", newParent, "movable");

    var result = merge(base, working, generated);

    assertTrue(result.conflicts().isEmpty());
    assertEquals("new-parent", id(find(result.mergedWorking(), "movable").eContainer()));
  }

  /** Regression: an incoming-only owner must retain its Ecore-required nested containment. */
  @Test
  void incomingOnlyOwnerRetainsRequiredNestedContainment() {
    requiredNodes.setLowerBound(1);
    Resource base = model(512, "A");
    addRequiredNode(base, "base-required", "base-child");
    Resource working = loadUnchecked(bytesUnchecked(base), "required-incoming-working");
    Resource generated = loadUnchecked(bytesUnchecked(base), "required-incoming-generated");
    addRequiredNode(generated, "required-parent", "required-child");

    var result = merge(base, working, generated);

    assertNotNull(find(result.mergedWorking(), "required-parent"));
    assertEquals(2, requiredNodeValues(result.mergedWorking()).size());
  }

  /** Catalog T-09: deleting a parent cannot leave a newly introduced reference dangling. */
  @Test
  void t09RejectsOrConflictsWithReferenceToChildOfDeletedParent() {
    Resource base = twoNodeModel("A");
    addChild(base, "n1", "child", "Child");
    Resource working = loadUnchecked(bytesUnchecked(base), "t09-working");
    removeNode(working, "n1");
    Resource generated = loadUnchecked(bytesUnchecked(base), "t09-generated");
    find(generated, "n2").eSet(peer, find(generated, "child"));

    var result = merge(base, working, generated);
    EObject target = (EObject) find(result.mergedWorking(), "n2").eGet(peer);
    assertTrue(
        target == null || find(result.mergedWorking(), id(target)) != null,
        "Reference target must remain resolvable: " + new String(result.mergedWorkingXmi()));
  }

  /** Catalog T-10/V-03/V-06: invalid structural candidates are rejected without repair. */
  @Test
  void t10RejectsMergeThatRemovesRequiredContainment() {
    childNodes.setLowerBound(1);
    Resource base = modelWithChild("required-child");
    Resource working = loadUnchecked(bytesUnchecked(base), "t10-working");
    removeContained(working, "child");
    Resource generated = loadUnchecked(bytesUnchecked(base), "t10-generated");

    org.junit.jupiter.api.Assertions.assertThrows(
        RuntimeException.class, () -> merge(base, working, generated));
  }

  /**
   * Catalog E-01: accepted generations advance the Base while refinements remain distinguishable.
   */
  @Test
  void e01AdvancesBaseAcrossSeveralUpstreamGenerations() {
    Resource v1 = model(1, "A");
    Resource refined = loadUnchecked(bytesUnchecked(v1), "e01-refined");
    set(refined, "n1", "description", "manual refinement");
    Resource v2 = loadUnchecked(bytesUnchecked(v1), "e01-v2");
    set(v2, "n1", "memory", 2);

    var first = merge(v1, refined, v2);
    assertTrue(first.conflicts().isEmpty());
    assertEquals("manual refinement", value(first.mergedWorking(), "n1", "description"));
    assertEquals(2, value(first.mergedWorking(), "n1", "memory"));

    Resource v3 = loadUnchecked(bytesUnchecked(v2), "e01-v3");
    set(v3, "n1", "memory", 3);
    var second = merge(v2, loadUnchecked(first.mergedWorkingXmi(), "e01-working-v2"), v3);
    assertTrue(second.conflicts().isEmpty());
    assertEquals("manual refinement", value(second.mergedWorking(), "n1", "description"));
    assertEquals(3, value(second.mergedWorking(), "n1", "memory"));
  }

  /** Catalog E-02: a manual refinement survives repeated generator no-op cycles. */
  @Test
  void e02PreservesManualRefinementAcrossRepeatedNoOpCycles() {
    Resource base = model(1, "A");
    Resource working = loadUnchecked(bytesUnchecked(base), "e02-working");
    set(working, "n1", "description", "persistent manual refinement");
    for (int cycle = 0; cycle < 4; cycle++) {
      var result =
          merge(base, working, loadUnchecked(bytesUnchecked(base), "e02-generated-" + cycle));
      assertTrue(result.conflicts().isEmpty());
      assertEquals(
          "persistent manual refinement", value(result.mergedWorking(), "n1", "description"));
      working = loadUnchecked(result.mergedWorkingXmi(), "e02-working-next-" + cycle);
    }
  }

  /**
   * Catalog E-03: unrelated generated changes apply on every cycle beside a retained refinement.
   */
  @Test
  void e03AppliesRepeatedUnrelatedGeneratedChangesBesideManualRefinement() {
    Resource base = model(1, "A");
    Resource working = loadUnchecked(bytesUnchecked(base), "e03-working");
    set(working, "n1", "description", "manual refinement");
    for (int memory = 2; memory <= 5; memory++) {
      Resource generated = loadUnchecked(bytesUnchecked(base), "e03-generated-" + memory);
      set(generated, "n1", "memory", memory);
      var result = merge(base, working, generated);
      assertTrue(result.conflicts().isEmpty());
      assertEquals(memory, value(result.mergedWorking(), "n1", "memory"));
      assertEquals("manual refinement", value(result.mergedWorking(), "n1", "description"));
      base = generated;
      working = loadUnchecked(result.mergedWorkingXmi(), "e03-working-next-" + memory);
    }
  }

  /** Catalog E-04: accepting a generated conflict makes the accepted result the next clean Base. */
  @Test
  void e04AcceptedGeneratedConflictBecomesCleanNextBaseline() {
    Resource base = model(1, "A");
    Resource working = loadUnchecked(bytesUnchecked(base), "e04-working");
    set(working, "n1", "memory", 2);
    Resource generated = loadUnchecked(bytesUnchecked(base), "e04-generated");
    set(generated, "n1", "memory", 3);
    var pending = merge(base, working, generated);
    assertEquals(1, pending.conflicts().size());
    Map<String, ConflictResolution> takeGenerated =
        pending.conflicts().stream()
            .collect(
                java.util.stream.Collectors.toMap(
                    c -> c.conflictId(), c -> ConflictResolution.TAKE_GENERATED));
    var accepted = mergeWithDecisions(base, working, generated, takeGenerated);
    assertTrue(accepted.conflicts().isEmpty());
    assertEquals(3, value(accepted.mergedWorking(), "n1", "memory"));
    var clean =
        merge(
            generated,
            loadUnchecked(accepted.mergedWorkingXmi(), "e04-accepted"),
            loadUnchecked(bytesUnchecked(generated), "e04-next-generated"));
    assertTrue(clean.conflicts().isEmpty());
    assertEquals(0, clean.incomingChanges());
  }

  /** Catalog E-05: keeping a manual value leaves each later incompatible generation explicit. */
  @Test
  void e05RepeatedIncompatibleGenerationRemainsAConflictUntilAccepted() {
    Resource base = model(1, "A");
    Resource working = loadUnchecked(bytesUnchecked(base), "e05-working");
    set(working, "n1", "memory", 2);
    for (int generatedMemory = 3; generatedMemory <= 5; generatedMemory++) {
      Resource generated = loadUnchecked(bytesUnchecked(base), "e05-generated-" + generatedMemory);
      set(generated, "n1", "memory", generatedMemory);
      var pending = merge(base, working, generated);
      assertEquals(1, pending.conflicts().size());
      Map<String, ConflictResolution> keepUser =
          pending.conflicts().stream()
              .collect(
                  java.util.stream.Collectors.toMap(
                      c -> c.conflictId(), c -> ConflictResolution.KEEP_USER));
      var kept = mergeWithDecisions(base, working, generated, keepUser);
      assertEquals(2, value(kept.mergedWorking(), "n1", "memory"));
      assertTrue(kept.conflicts().isEmpty());
    }
  }

  /**
   * Catalog E-06: a kept refined deletion can be matched again when the generated identity returns.
   */
  @Test
  void e06KeepsRefinedDeletedElementAndMatchesItsReintroduction() {
    Resource base = modelWithChild("generated child");
    Resource working = loadUnchecked(bytesUnchecked(base), "e06-working");
    set(working, "child", "description", "manual child refinement");
    Resource deleted = emptyModel();
    var pending = merge(base, working, deleted);
    assertEquals(1, pending.conflicts().size());
    Map<String, ConflictResolution> keepUser =
        pending.conflicts().stream()
            .collect(
                java.util.stream.Collectors.toMap(
                    c -> c.conflictId(), c -> ConflictResolution.KEEP_USER));
    var kept = mergeWithDecisions(base, working, deleted, keepUser);
    assertNotNull(find(kept.mergedWorking(), "child"));
    assertEquals("manual child refinement", value(kept.mergedWorking(), "child", "description"));

    Resource reintroduced = loadUnchecked(bytesUnchecked(base), "e06-reintroduced");
    var reconciled = merge(base, loadUnchecked(kept.mergedWorkingXmi(), "e06-kept"), reintroduced);
    assertTrue(reconciled.conflicts().isEmpty());
    assertEquals(
        "manual child refinement", value(reconciled.mergedWorking(), "child", "description"));
  }

  /**
   * Catalog E-07: a generated analogue does not absorb an independently user-created same-name
   * element.
   */
  @Test
  void e07DoesNotMatchGeneratedAnalogueByDisplayName() {
    Resource base = model(1, "A");
    Resource working = loadUnchecked(bytesUnchecked(base), "e07-working");
    addNode(working, "user-node", "Same name", 10, "user-created");
    Resource generated = loadUnchecked(bytesUnchecked(base), "e07-generated");
    addNode(generated, "generated-node", "Same name", 20, "generated analogue");
    var result = merge(base, working, generated);
    assertTrue(result.conflicts().isEmpty());
    assertEquals("user-created", value(result.mergedWorking(), "user-node", "description"));
    assertEquals(
        "generated analogue", value(result.mergedWorking(), "generated-node", "description"));
    assertNotEquals(
        id(result.mergedWorking(), "user-node"), id(result.mergedWorking(), "generated-node"));
  }

  /**
   * Catalog E-08: rename/edit preserves identity, while a later generated disappearance is
   * explicit.
   */
  @Test
  void e08PreservesRenamedIdentityBeforeFinalGeneratedDeletion() {
    Resource base = model(1, "A");
    Resource working = loadUnchecked(bytesUnchecked(base), "e08-working");
    set(working, "n1", "name", "renamed function");
    set(working, "n1", "description", "manual after rename");
    Resource renamedGenerated = loadUnchecked(bytesUnchecked(base), "e08-renamed-generated");
    set(renamedGenerated, "n1", "name", "renamed function");
    var renamed = merge(base, working, renamedGenerated);
    assertTrue(renamed.conflicts().isEmpty());
    assertEquals("renamed function", value(renamed.mergedWorking(), "n1", "name"));
    assertEquals("manual after rename", value(renamed.mergedWorking(), "n1", "description"));

    var deleted =
        merge(
            renamedGenerated,
            loadUnchecked(renamed.mergedWorkingXmi(), "e08-renamed-working"),
            emptyModel());
    assertEquals(1, deleted.conflicts().size());
    Map<String, ConflictResolution> takeGenerated =
        deleted.conflicts().stream()
            .collect(
                java.util.stream.Collectors.toMap(
                    c -> c.conflictId(), c -> ConflictResolution.TAKE_GENERATED));
    var acceptedDeletion =
        mergeWithDecisions(
            renamedGenerated,
            loadUnchecked(renamed.mergedWorkingXmi(), "e08-delete-working"),
            emptyModel(),
            takeGenerated);
    assertTrue(acceptedDeletion.conflicts().isEmpty());
    assertNull(find(acceptedDeletion.mergedWorking(), "n1"));
  }

  /** Catalog E-09: the downstream relationship uses the accepted upstream Working as its source. */
  @Test
  void e09DownstreamBaselineConsumesAcceptedUpstreamRefinement() {
    Resource pimBase = model(1, "A");
    Resource pimWorking = loadUnchecked(bytesUnchecked(pimBase), "e09-pim-working");
    set(pimWorking, "n1", "description", "accepted PIM refinement");
    Resource pimGenerated = loadUnchecked(bytesUnchecked(pimBase), "e09-pim-generated");
    var pim = merge(pimBase, pimWorking, pimGenerated);
    assertTrue(pim.conflicts().isEmpty());
    Resource psmBase = loadUnchecked(pim.mergedWorkingXmi(), "e09-psm-base");
    Resource psmWorking = loadUnchecked(bytesUnchecked(psmBase), "e09-psm-working");
    Resource psmGenerated = loadUnchecked(bytesUnchecked(psmBase), "e09-psm-generated");
    var psm = merge(psmBase, psmWorking, psmGenerated);
    assertTrue(psm.conflicts().isEmpty());
    assertEquals("accepted PIM refinement", value(psm.mergedWorking(), "n1", "description"));
  }

  /** Catalog E-10: restoring an older source is compared as a new generated state. */
  @Test
  void e10RestoredOlderUpstreamRevisionUsesNormalThreeWayComparison() {
    Resource v1 = model(1, "A");
    Resource v2 = loadUnchecked(bytesUnchecked(v1), "e10-v2");
    set(v2, "n1", "memory", 2);
    Resource working = loadUnchecked(bytesUnchecked(v2), "e10-working");
    set(working, "n1", "description", "manual refinement");
    Resource restored = loadUnchecked(bytesUnchecked(v1), "e10-restored-v1");
    var result = merge(v2, working, restored);
    assertTrue(result.conflicts().isEmpty());
    assertEquals(1, value(result.mergedWorking(), "n1", "memory"));
    assertEquals("manual refinement", value(result.mergedWorking(), "n1", "description"));
  }

  @Test
  void generatedNestedAdditionRebindsCrossReferenceToExistingWorkingObjectAfterReload() {
    Resource base = twoNodeModel("A");
    Resource working = loadUnchecked(bytesUnchecked(base), "cross-reference-working");
    Resource incoming = loadUnchecked(bytesUnchecked(base), "cross-reference-incoming");
    EObject generatedChild = addChild(incoming, "n2", "generated-step", "Generated step");
    generatedChild.eSet(peer, find(incoming, "n1"));

    var merged = merge(base, working, incoming);
    Resource reloaded = loadUnchecked(merged.mergedWorkingXmi(), "cross-reference-reloaded");
    EObject mergedStep = find(reloaded, "generated-step");
    EObject invokedTarget = (EObject) mergedStep.eGet(peer, false);

    assertFalse(invokedTarget.eIsProxy());
    assertEquals("n1", id(invokedTarget));
    assertTrue(invokedTarget == find(reloaded, "n1"));
  }

  /** Catalog R-08: generated subtree references are rebound to the merged working graph by ID. */
  @Test
  void r08RebindsGeneratedCrossReferenceAfterSubtreeReload() {
    Resource base = twoNodeModel("A");
    Resource working = loadUnchecked(bytesUnchecked(base), "r08-working");
    Resource generated = loadUnchecked(bytesUnchecked(base), "r08-generated");
    EObject generatedStep = addChild(generated, "n2", "generated-step", "Generated step");
    generatedStep.eSet(peer, find(generated, "n1"));

    var result = merge(base, working, generated);
    Resource reloaded = loadUnchecked(result.mergedWorkingXmi(), "r08-reloaded");
    EObject target = (EObject) find(reloaded, "generated-step").eGet(peer, false);

    assertTrue(result.conflicts().isEmpty());
    assertFalse(target.eIsProxy());
    assertEquals("n1", id(target));
    assertTrue(target == find(reloaded, "n1"));
  }

  /** Catalog R-09: inverse/transient navigation changes do not create duplicate conflicts. */
  @Test
  void r09IgnoresDerivedInverseNavigationConflictNoise() {
    Resource base = model(512, "A");
    Resource working = loadUnchecked(bytesUnchecked(base), "r09-working");
    Resource generated = loadUnchecked(bytesUnchecked(base), "r09-generated");
    set(base, "n1", "incomingTraces", List.of("trace-base"));
    set(working, "n1", "incomingTraces", List.of("trace-user"));
    set(generated, "n1", "incomingTraces", List.of("trace-generated"));

    var result = merge(base, working, generated);

    assertTrue(result.conflicts().isEmpty());
    assertEquals(List.of("trace-user"), value(result.mergedWorking(), "n1", "incomingTraces"));
    assertEquals(0, result.incomingChanges());
  }

  /** Catalog R-12: an intentional, resolvable external-resource reference is preserved. */
  @Test
  void r12PreservesResolvableExternalResourceReference() {
    ResourceSet shared = configuredResourceSet();
    Resource external = shared.createResource(URI.createURI("memory:/r12-external.xmi"));
    EObject externalRoot = modelPackage.getEFactoryInstance().create(rootClass);
    externalRoot.eSet(rootClass.getEStructuralFeature("id"), "external-root");
    external.getContents().add(externalRoot);
    EObject externalTarget = addNode(external, "external-target", "External target", 1, "external");

    Resource base = emptyModel(shared, "r12-base");
    EObject baseNode = addNode(base, "n1", "Function", 512, "A");
    baseNode.eSet(peer, externalTarget);
    Resource working = emptyModel(shared, "r12-working");
    EObject workingNode = addNode(working, "n1", "Function", 512, "A");
    workingNode.eSet(peer, externalTarget);
    Resource generated = emptyModel(shared, "r12-generated");
    EObject generatedNode = addNode(generated, "n1", "Function", 512, "A");
    generatedNode.eSet(peer, externalTarget);

    var result = merge(base, working, generated);
    EObject reference = (EObject) find(result.mergedWorking(), "n1").eGet(peer, false);

    assertTrue(result.conflicts().isEmpty());
    assertFalse(reference.eIsProxy());
    assertEquals("external-target", id(reference));
    assertTrue(reference == externalTarget);
  }

  /** Catalog A-05, A-06, and A-07: optional scalar clearing follows three-way history. */
  @Test
  void optionalScalarClearingIsPreservedAppliedOrConflictedFromTheAncestor() {
    Resource base = model(512, "base");
    Resource userCleared = model(512, null);
    Resource unchanged = model(512, "base");
    var manualOnly = merge(base, userCleared, unchanged);
    assertNull(value(manualOnly.mergedWorking(), "n1", "description"));
    assertTrue(manualOnly.conflicts().isEmpty());

    var generatedOnly = merge(model(512, "base"), model(512, "base"), model(512, null));
    assertNull(value(generatedOnly.mergedWorking(), "n1", "description"));
    assertTrue(generatedOnly.conflicts().isEmpty());

    var divergent = merge(model(512, "base"), model(512, null), model(512, "generated"));
    assertEquals(1, divergent.conflicts().size());
    assertNull(value(divergent.mergedWorking(), "n1", "description"));
  }

  /** Catalog R-05/R-06 and U-05/G-05: scalar reference conflicts resolve explicitly. */
  @Test
  void scalarReferenceChangesConvergeOrRequireExplicitResolution() {
    Resource base = threeNodeModel();
    find(base, "n1").eSet(peer, find(base, "n2"));
    Resource local = loadUnchecked(bytesUnchecked(base), "reference-local");
    Resource incoming = loadUnchecked(bytesUnchecked(base), "reference-incoming");
    find(local, "n1").eSet(peer, find(local, "n2"));
    find(local, "n1").eSet(peer, find(local, "n3"));
    find(incoming, "n1").eSet(peer, find(incoming, "n1"));
    var conflict = merge(base, local, incoming);
    assertEquals(1, conflict.conflicts().size());

    String conflictId = conflict.conflicts().get(0).conflictId();
    Resource takeGeneratedLocal = loadUnchecked(bytesUnchecked(local), "reference-generated-local");
    Resource takeGeneratedIncoming =
        loadUnchecked(bytesUnchecked(incoming), "reference-generated-incoming");
    var generated =
        service.synchronize(
            base,
            takeGeneratedLocal,
            takeGeneratedIncoming,
            TransformationDirection.CIM_TO_PIM,
            Map.of(conflictId, ConflictResolution.TAKE_GENERATED));
    assertEquals("n1", id((EObject) find(generated.mergedWorking(), "n1").eGet(peer)));

    Resource sameLocal = loadUnchecked(bytesUnchecked(base), "reference-same-local");
    Resource sameIncoming = loadUnchecked(bytesUnchecked(base), "reference-same-incoming");
    find(sameLocal, "n1").eSet(peer, find(sameLocal, "n3"));
    find(sameIncoming, "n1").eSet(peer, find(sameIncoming, "n3"));
    var convergent = merge(base, sameLocal, sameIncoming);
    assertTrue(convergent.conflicts().isEmpty());
    assertEquals("n3", id((EObject) find(convergent.mergedWorking(), "n1").eGet(peer)));
  }

  /** Catalog M-08/M-09 and C-02/C-11: identity matches additions, never display names. */
  @Test
  void independentlyAddedIdentityConvergesOrConflictsWithoutDuplicates() {
    Resource base = emptyModel();
    Resource sameLocal = emptyModel();
    Resource sameIncoming = emptyModel();
    addNode(sameLocal, "shared", "Same", 128, "same");
    addNode(sameIncoming, "shared", "Same", 128, "same");
    var convergent = merge(base, sameLocal, sameIncoming);
    assertTrue(convergent.conflicts().isEmpty());
    assertEquals(1, contents(convergent.mergedWorking()).size());

    Resource differentLocal = emptyModel();
    Resource differentIncoming = emptyModel();
    addNode(differentLocal, "shared", "Same", 128, "local");
    addNode(differentIncoming, "shared", "Same", 256, "generated");
    var divergent = merge(emptyModel(), differentLocal, differentIncoming);
    assertFalse(divergent.conflicts().isEmpty());
    assertEquals(1, contents(divergent.mergedWorking()).size());
  }

  /** Catalog M-12/M-14 and C-03/C-04: independent and convergent removals use Base history. */
  @Test
  void collectionRemovalsConvergeAndCombineWithIndependentAdditions() {
    Resource base = twoNodeModel("A");
    Resource local = loadUnchecked(bytesUnchecked(base), "remove-add-local");
    Resource incoming = loadUnchecked(bytesUnchecked(base), "remove-add-incoming");
    removeNode(local, "n1");
    addNode(incoming, "n3", "Generated", 64, "new");

    var independent = merge(base, local, incoming);
    assertTrue(independent.conflicts().isEmpty());
    assertNull(find(independent.mergedWorking(), "n1"));
    assertNotNull(find(independent.mergedWorking(), "n2"));
    assertNotNull(find(independent.mergedWorking(), "n3"));

    Resource bothLocal = loadUnchecked(bytesUnchecked(base), "both-delete-local");
    Resource bothIncoming = loadUnchecked(bytesUnchecked(base), "both-delete-incoming");
    removeNode(bothLocal, "n2");
    removeNode(bothIncoming, "n2");
    var convergent = merge(base, bothLocal, bothIncoming);
    assertTrue(convergent.conflicts().isEmpty());
    assertNull(find(convergent.mergedWorking(), "n2"));
  }

  /** Catalog M-15/M-16/M-17 and T-07: containment moves merge by stable identity. */
  @Test
  void oneSidedAndConvergentContainmentMovesApplyExactlyOnce() {
    Resource base = threeParentMoveModel();
    Resource manual = loadUnchecked(bytesUnchecked(base), "manual-move");
    Resource unchangedIncoming = loadUnchecked(bytesUnchecked(base), "manual-move-incoming");
    moveChild(manual, "n1", find(manual, "n2"), "movable");
    var manualResult = merge(base, manual, unchangedIncoming);
    assertTrue(manualResult.conflicts().isEmpty());
    assertEquals("n2", id(find(manualResult.mergedWorking(), "movable").eContainer()));

    Resource unchangedLocal = loadUnchecked(bytesUnchecked(base), "generated-move-local");
    Resource generated = loadUnchecked(bytesUnchecked(base), "generated-move");
    moveChild(generated, "n1", find(generated, "n3"), "movable");
    var generatedResult = merge(base, unchangedLocal, generated);
    assertTrue(generatedResult.conflicts().isEmpty());
    assertEquals("n3", id(find(generatedResult.mergedWorking(), "movable").eContainer()));

    Resource sameLocal = loadUnchecked(bytesUnchecked(base), "same-move-local");
    Resource sameIncoming = loadUnchecked(bytesUnchecked(base), "same-move-incoming");
    moveChild(sameLocal, "n1", find(sameLocal, "n2"), "movable");
    moveChild(sameIncoming, "n1", find(sameIncoming, "n2"), "movable");
    var convergent = merge(base, sameLocal, sameIncoming);
    assertTrue(convergent.conflicts().isEmpty());
    assertEquals("n2", id(find(convergent.mergedWorking(), "movable").eContainer()));
  }

  /** Catalog I-01/I-02/I-03/A-09: names never substitute for immutable stable identity. */
  @Test
  void renamesPreserveIdentityWhileSameNamesAndReplacementIdsRemainDistinct() {
    Resource renamed = model(512, "A");
    set(renamed, "n1", "name", "Renamed");
    var rename = merge(model(512, "A"), model(512, "A"), renamed);
    assertEquals("Renamed", value(rename.mergedWorking(), "n1", "name"));
    assertEquals(1, contents(rename.mergedWorking()).size());

    Resource sameNames = emptyModel();
    addNode(sameNames, "left-id", "Duplicate display name", 1, "left");
    addNode(sameNames, "right-id", "Duplicate display name", 2, "right");
    assertEquals(2, contents(sameNames).size());

    Resource replacement = emptyModel();
    addNode(replacement, "replacement-id", "Function", 512, "A");
    var deleteAdd = merge(model(512, "A"), model(512, "A"), replacement);
    assertNull(find(deleteAdd.mergedWorking(), "n1"));
    assertNotNull(find(deleteAdd.mergedWorking(), "replacement-id"));
  }

  /** Catalog I-06: a user-created element receives one immutable stable identity. */
  @Test
  void i06PreservesUserCreatedElementIdentityAcrossLaterCycles() {
    Resource base = model(512, "A");
    Resource working = loadUnchecked(bytesUnchecked(base), "i06-working");
    addNode(working, "user-created-id", "User-created", 1, "manual");
    Resource generated = loadUnchecked(bytesUnchecked(base), "i06-generated");

    var first = merge(base, working, generated);
    Resource nextWorking = loadUnchecked(first.mergedWorkingXmi(), "i06-next-working");
    var second =
        merge(
            loadUnchecked(bytesUnchecked(base), "i06-next-base"),
            nextWorking,
            loadUnchecked(bytesUnchecked(base), "i06-next-generated"));

    assertEquals("user-created-id", id(find(first.mergedWorking(), "user-created-id")));
    assertEquals("user-created-id", id(find(second.mergedWorking(), "user-created-id")));
    assertEquals(2, contents(second.mergedWorking()).size());
  }

  /** Catalog I-07: equivalent generation from the same source yields identical target IDs. */
  @Test
  void i07EquivalentGenerationConvergesOnIdenticalIds() {
    Resource generatedA = model(512, "A");
    Resource generatedB = model(512, "A");
    var result = merge(emptyModel(), generatedA, generatedB);

    assertTrue(result.conflicts().isEmpty());
    assertEquals(1, contents(result.mergedWorking()).size());
    assertEquals("n1", id(contents(result.mergedWorking()).get(0)));
  }

  /** Catalog I-08: multiple generated roles have distinct stable identities. */
  @Test
  void i08KeepsDistinctIdsForMultipleGeneratedRoles() {
    Resource generated = emptyModel();
    addNode(generated, "role-a", "Same role", 1, "role A");
    addNode(generated, "role-b", "Same role", 1, "role B");

    var result = merge(emptyModel(), emptyModel(), generated);

    assertTrue(result.conflicts().isEmpty());
    assertNotNull(find(result.mergedWorking(), "role-a"));
    assertNotNull(find(result.mergedWorking(), "role-b"));
    assertEquals(2, contents(result.mergedWorking()).size());
  }

  /** Catalog I-09: regenerating an existing trace identity updates it instead of duplicating it. */
  @Test
  void i09RegeneratedTraceIdentityIsMatchedAndUpdated() {
    Resource base = model(512, "A");
    addNode(base, "trace-1", "TraceLink", 0, "old trace");
    Resource working = loadUnchecked(bytesUnchecked(base), "i09-working");
    Resource generated = loadUnchecked(bytesUnchecked(base), "i09-generated");
    set(generated, "trace-1", "description", "updated trace");

    var result = merge(base, working, generated);

    assertTrue(result.conflicts().isEmpty());
    assertEquals("updated trace", value(result.mergedWorking(), "trace-1", "description"));
    assertEquals(2, contents(result.mergedWorking()).size());
  }

  /** Catalog I-10: generator-owned trace metadata changes propagate to a surviving target. */
  @Test
  void i10PropagatesChangedGeneratedTraceMetadata() {
    Resource base = modelWithGeneratedFrom("source-v1");
    var result =
        merge(
            base,
            loadUnchecked(bytesUnchecked(base), "i10-working"),
            modelWithGeneratedFrom("source-v2"));

    assertTrue(result.conflicts().isEmpty());
    assertEquals("source-v2", value(result.mergedWorking(), "n1", "generatedFrom"));
  }

  /** Catalog I-11: a user trace edit conflicts with a generated endpoint change explicitly. */
  @Test
  void i11TraceEndpointEditRequiresExplicitGeneratedResolution() {
    Resource base = modelWithGeneratedFrom("source-v1");
    Resource working = modelWithGeneratedFrom("user-endpoint");
    Resource generated = modelWithGeneratedFrom("source-v2");
    var pending = merge(base, working, generated);
    String conflictId = pending.conflicts().get(0).conflictId();

    var resolved =
        service.synchronize(
            base,
            modelWithGeneratedFrom("user-endpoint"),
            modelWithGeneratedFrom("source-v2"),
            TransformationDirection.CIM_TO_PIM,
            Map.of(conflictId, ConflictResolution.TAKE_GENERATED));

    assertEquals(1, pending.conflicts().size());
    assertTrue(resolved.conflicts().isEmpty());
    assertEquals("source-v2", value(resolved.mergedWorking(), "n1", "generatedFrom"));
  }

  /** Catalog I-12: manuallyMaintained is an ordinary Ecore feature, not blanket ownership. */
  @Test
  void i12ManuallyMaintainedStillUsesThreeWayComparison() {
    Resource base = model(512, "A");
    set(base, "n1", "manuallyMaintained", false);
    Resource user = loadUnchecked(bytesUnchecked(base), "i12-user");
    set(user, "n1", "manuallyMaintained", true);
    Resource generated = loadUnchecked(bytesUnchecked(base), "i12-generated");
    var userChange = merge(base, user, generated);

    Resource generatedUser = loadUnchecked(bytesUnchecked(base), "i12-generated-user");
    set(generatedUser, "n1", "manuallyMaintained", true);
    var generatedChange =
        merge(base, loadUnchecked(bytesUnchecked(base), "i12-unchanged-user"), generatedUser);

    assertTrue(userChange.conflicts().isEmpty());
    assertEquals(true, value(userChange.mergedWorking(), "n1", "manuallyMaintained"));
    assertTrue(generatedChange.conflicts().isEmpty());
    assertEquals(true, value(generatedChange.mergedWorking(), "n1", "manuallyMaintained"));
  }

  /** Catalog M-01/M-02/M-03/M-04: unchanged and one-sided/convergent edits are not conflicts. */
  @Test
  void unchangedOneSidedAndConvergentScalarEditsAreAutomatic() {
    var unchanged = merge(model(512, "A"), model(512, "A"), model(512, "A"));
    assertTrue(unchanged.conflicts().isEmpty());
    assertEquals(512, value(unchanged.mergedWorking(), "n1", "memory"));

    var generated = merge(model(512, "A"), model(512, "A"), model(768, "A"));
    assertEquals(768, value(generated.mergedWorking(), "n1", "memory"));
    assertTrue(generated.conflicts().isEmpty());

    var manual = merge(model(512, "A"), model(768, "A"), model(512, "A"));
    assertEquals(768, value(manual.mergedWorking(), "n1", "memory"));
    assertTrue(manual.conflicts().isEmpty());

    var convergent = merge(model(512, "A"), model(768, "A"), model(768, "A"));
    assertEquals(768, value(convergent.mergedWorking(), "n1", "memory"));
    assertTrue(convergent.conflicts().isEmpty());
  }

  /** Catalog M-06/M-07 and C-01/C-04/C-05/C-12: independent additions and edits form a union. */
  @Test
  void independentGeneratedAndUserCollectionChangesFormOneGraph() {
    Resource base = twoNodeModel("A");
    Resource working = loadUnchecked(bytesUnchecked(base), "working-union");
    Resource generated = loadUnchecked(bytesUnchecked(base), "generated-union");
    addNode(working, "user-added", "User", 1, "manual");
    set(working, "n1", "description", "manual edit");
    addNode(generated, "generated-added", "Generated", 2, "generated");
    set(generated, "n2", "description", "generated edit");

    var result = merge(base, working, generated);
    assertTrue(result.conflicts().isEmpty());
    assertEquals("manual edit", value(result.mergedWorking(), "n1", "description"));
    assertEquals("generated edit", value(result.mergedWorking(), "n2", "description"));
    assertNotNull(find(result.mergedWorking(), "user-added"));
    assertNotNull(find(result.mergedWorking(), "generated-added"));
    assertEquals(4, contents(result.mergedWorking()).size());
  }

  /** Catalog C-02/C-03/C-06/C-07: matched members converge, while edit/delete remains explicit. */
  @Test
  void collectionMemberIdentityAndDeleteChangeConflictsAreExplicit() {
    Resource base = twoNodeModel("A");
    Resource sameUser = loadUnchecked(bytesUnchecked(base), "same-user");
    Resource sameGenerated = loadUnchecked(bytesUnchecked(base), "same-generated");
    set(sameUser, "n2", "description", "same");
    set(sameGenerated, "n2", "description", "same");
    var convergent = merge(base, sameUser, sameGenerated);
    assertTrue(convergent.conflicts().isEmpty());
    assertEquals(2, contents(convergent.mergedWorking()).size());

    Resource removed = loadUnchecked(bytesUnchecked(base), "removed-member");
    removeNode(removed, "n2");
    Resource edited = loadUnchecked(bytesUnchecked(base), "edited-member");
    set(edited, "n2", "description", "new generated state");
    var conflict = merge(base, removed, edited);
    assertEquals(1, conflict.conflicts().size());
    assertEquals("n2", conflict.conflicts().get(0).elementId());
  }

  /**
   * Catalog M-11/M-13 and U-02/U-03/G-02/G-03: subtree delete/change choices are whole-subtree
   * choices.
   */
  @Test
  void deleteChangeResolutionKeepsOrRemovesCompleteSubtree() {
    Resource base = modelWithChild("base child");
    Resource working = loadUnchecked(bytesUnchecked(base), "delete-change-working");
    set(working, "child", "name", "manual child");
    Resource generated = emptyModel();
    byte[] rawBase = bytesUnchecked(base);
    byte[] rawWorking = bytesUnchecked(working);
    byte[] rawGenerated = bytesUnchecked(generated);
    var pending = merge(base, working, generated);
    assertEquals(1, pending.conflicts().size());
    String conflictId = pending.conflicts().get(0).conflictId();

    var keep =
        service.synchronize(
            loadUnchecked(rawBase, "keep-subtree-base"),
            loadUnchecked(rawWorking, "keep-subtree"),
            loadUnchecked(rawGenerated, "keep-generated"),
            TransformationDirection.CIM_TO_PIM,
            Map.of(conflictId, ConflictResolution.KEEP_USER));
    assertNotNull(find(keep.mergedWorking(), "n1"));
    assertNotNull(find(keep.mergedWorking(), "child"));
    assertEquals("manual child", value(keep.mergedWorking(), "child", "name"));

    var remove =
        service.synchronize(
            loadUnchecked(rawBase, "remove-subtree-base"),
            loadUnchecked(rawWorking, "remove-subtree"),
            loadUnchecked(rawGenerated, "remove-generated"),
            TransformationDirection.CIM_TO_PIM,
            Map.of(conflictId, ConflictResolution.TAKE_GENERATED));
    assertNull(find(remove.mergedWorking(), "n1"));
    assertNull(find(remove.mergedWorking(), "child"));
  }

  /** Catalog M-18/M-19 and U-04/G-04: incompatible containment choices remain resolvable. */
  @Test
  void containmentMoveConflictSupportsBothOwnersAndDeleteConflict() {
    Resource base = threeParentMoveModel();
    Resource working = loadUnchecked(bytesUnchecked(base), "move-working");
    Resource generated = loadUnchecked(bytesUnchecked(base), "move-generated");
    moveChild(working, "n1", find(working, "n2"), "movable");
    moveChild(generated, "n1", find(generated, "n3"), "movable");
    var pending = merge(base, working, generated);
    assertFalse(pending.conflicts().isEmpty());
    String conflictId = pending.conflicts().get(0).conflictId();

    var keep =
        service.synchronize(
            base,
            loadUnchecked(bytesUnchecked(working), "keep-move"),
            loadUnchecked(bytesUnchecked(generated), "keep-generated-move"),
            TransformationDirection.CIM_TO_PIM,
            Map.of(conflictId, ConflictResolution.KEEP_USER));
    assertEquals("n2", id((EObject) find(keep.mergedWorking(), "movable").eContainer()));

    var take =
        service.synchronize(
            base,
            loadUnchecked(bytesUnchecked(working), "take-move"),
            loadUnchecked(bytesUnchecked(generated), "take-generated-move"),
            TransformationDirection.CIM_TO_PIM,
            Map.of(conflictId, ConflictResolution.TAKE_GENERATED));
    assertEquals("n3", id((EObject) find(take.mergedWorking(), "movable").eContainer()));
  }

  /**
   * Catalog R-01/R-02/R-03/R-04/R-05/R-06/R-11: references merge independently and preserve cycles.
   */
  @Test
  void scalarAndCircularReferencesConvergeOrConflictWithoutDuplicateObjects() {
    Resource base = twoNodeModel("A");
    Resource working = loadUnchecked(bytesUnchecked(base), "reference-working");
    Resource generated = loadUnchecked(bytesUnchecked(base), "reference-generated");
    setReference(working, "n1", find(working, "n2"));
    setReference(generated, "n1", find(generated, "n2"));
    setReference(working, "n2", find(working, "n1"));
    setReference(generated, "n2", find(generated, "n1"));
    var cycle = merge(base, working, generated);
    assertTrue(cycle.conflicts().isEmpty());
    assertEquals("n2", id((EObject) find(cycle.mergedWorking(), "n1").eGet(peer)));
    assertEquals("n1", id((EObject) find(cycle.mergedWorking(), "n2").eGet(peer)));

    Resource targetA = loadUnchecked(bytesUnchecked(base), "reference-a");
    Resource targetB = loadUnchecked(bytesUnchecked(base), "reference-b");
    setReference(targetA, "n1", find(targetA, "n2"));
    setReference(targetB, "n1", find(targetB, "n1"));
    var conflict = merge(base, targetA, targetB);
    assertEquals(1, conflict.conflicts().size());
  }

  /**
   * Catalog X-01/X-04/X-05/X-07: safe changes are prepared but every real conflict stays visible.
   */
  @Test
  void mixedSafeChangesAndMultipleConflictsRemainIndependent() {
    Resource base = threeNodeModel();
    Resource working = loadUnchecked(bytesUnchecked(base), "mixed-working");
    Resource generated = loadUnchecked(bytesUnchecked(base), "mixed-generated");
    set(working, "n1", "memory", 600);
    set(working, "n2", "description", "user conflict");
    set(working, "n3", "description", "second user conflict");
    set(generated, "n1", "description", "generated safe");
    set(generated, "n2", "description", "generated conflict");
    set(generated, "n3", "description", "second generated conflict");
    set(generated, "n3", "memory", 99);
    var result = merge(base, working, generated);
    assertEquals(2, result.conflicts().size());
    assertEquals(600, value(result.mergedWorking(), "n1", "memory"));
    assertEquals("generated safe", value(result.mergedWorking(), "n1", "description"));
    assertEquals(99, value(result.mergedWorking(), "n3", "memory"));
  }

  /** Catalog C-08/C-09: ordered containment has convergent and incompatible reorder outcomes. */
  @Test
  void orderedCollectionReordersConvergeOrConflict() {
    Resource base = threeNodeModel();
    Resource sameWorking = loadUnchecked(bytesUnchecked(base), "same-order-working");
    Resource sameGenerated = loadUnchecked(bytesUnchecked(base), "same-order-generated");
    reorderNodes(sameWorking, "n3", "n1", "n2");
    reorderNodes(sameGenerated, "n3", "n1", "n2");
    var convergent = merge(base, sameWorking, sameGenerated);
    assertTrue(convergent.conflicts().isEmpty());
    assertEquals(List.of("n3", "n1", "n2"), ids(convergent.mergedWorking()));

    Resource differentWorking = loadUnchecked(bytesUnchecked(base), "different-order-working");
    Resource differentGenerated = loadUnchecked(bytesUnchecked(base), "different-order-generated");
    reorderNodes(differentWorking, "n2", "n3", "n1");
    reorderNodes(differentGenerated, "n3", "n2", "n1");
    var conflict = merge(base, differentWorking, differentGenerated);
    assertEquals(1, conflict.conflicts().size());
    assertEquals(
        List.of("n2", "n3", "n1"),
        ids(conflict.mergedWorking()),
        "An unresolved order conflict must not detach and rebuild required containment children.");
    String conflictId = conflict.conflicts().get(0).conflictId();
    var keep =
        service.synchronize(
            base,
            loadUnchecked(bytesUnchecked(differentWorking), "keep-order-working"),
            loadUnchecked(bytesUnchecked(differentGenerated), "keep-order-generated"),
            TransformationDirection.CIM_TO_PIM,
            Map.of(conflictId, ConflictResolution.KEEP_USER));
    assertEquals(List.of("n2", "n3", "n1"), ids(keep.mergedWorking()));
    var take =
        service.synchronize(
            base,
            loadUnchecked(bytesUnchecked(differentWorking), "take-order-working"),
            loadUnchecked(bytesUnchecked(differentGenerated), "take-order-generated"),
            TransformationDirection.CIM_TO_PIM,
            Map.of(conflictId, ConflictResolution.TAKE_GENERATED));
    assertEquals(List.of("n3", "n2", "n1"), ids(take.mergedWorking()));
  }

  /**
   * Catalog C-10: an explicitly unordered collection does not turn order-only noise into conflict.
   */
  @Test
  void unorderedCollectionCanIgnoreOrderOnlyDifferences() {
    nodes.setOrdered(false);
    Resource base = threeNodeModel();
    Resource working = loadUnchecked(bytesUnchecked(base), "unordered-working");
    Resource generated = loadUnchecked(bytesUnchecked(base), "unordered-generated");
    reorderNodes(working, "n3", "n1", "n2");
    reorderNodes(generated, "n2", "n3", "n1");
    var result = merge(base, working, generated);
    assertTrue(result.conflicts().isEmpty());
    assertEquals(3, contents(result.mergedWorking()).size());
  }

  /** Catalog A-10/I-04/I-05/V-08: malformed identity is retained for structural rejection. */
  @Test
  void missingAndDuplicateStableIdsAreNotAcceptedAsValidModelIdentity() {
    Resource missing = model(512, "A");
    EObject missingNode = find(missing, "n1");
    missingNode.eUnset(feature("id"));
    assertTrue(id(missingNode).isBlank());

    Resource duplicate = twoNodeModel("A");
    EObject duplicateNode = find(duplicate, "n2");
    duplicateNode.eSet(feature("id"), "n1");
    assertEquals("n1", id(duplicateNode));
    assertEquals(
        2, contents(duplicate).size(), "The raw graph retains ambiguity for validation to reject.");
  }

  /**
   * Catalog T-03/X-02/X-03: unrelated nested edits, additions, and deletions remain independent.
   */
  @Test
  void unrelatedNestedAndCollectionChangesMergeIndependently() {
    Resource base = modelWithChild("first");
    addChild(base, "n1", "second", "second");
    addNode(base, "sibling", "Sibling", 3, "sibling");
    Resource working = loadUnchecked(bytesUnchecked(base), "nested-independent-working");
    Resource generated = loadUnchecked(bytesUnchecked(base), "nested-independent-generated");
    set(working, "child", "name", "manual first");
    removeContained(working, "second");
    addNode(working, "user-addition", "User", 1, "manual");
    set(generated, "sibling", "description", "generated sibling");
    addNode(generated, "generated-addition", "Generated", 2, "generated");
    var result = merge(base, working, generated);
    assertTrue(result.conflicts().isEmpty());
    assertEquals("manual first", value(result.mergedWorking(), "child", "name"));
    assertNull(find(result.mergedWorking(), "second"));
    assertNotNull(find(result.mergedWorking(), "user-addition"));
    assertNotNull(find(result.mergedWorking(), "generated-addition"));
    assertEquals("generated sibling", value(result.mergedWorking(), "sibling", "description"));
  }

  /**
   * Catalog R-07/R-10: deletion/reference incompatibility is surfaced and wrong-type links fail at
   * Ecore.
   */
  @Test
  void deletedReferenceTargetIsNotLeftDanglingAndWrongTypeReferenceIsRejected() {
    Resource base = twoNodeModel("A");
    setReference(base, "n1", find(base, "n2"));
    Resource working = loadUnchecked(bytesUnchecked(base), "reference-delete-working");
    removeNode(working, "n2");
    Resource generated = loadUnchecked(bytesUnchecked(base), "reference-delete-generated");
    set(generated, "n2", "description", "changed target");
    org.junit.jupiter.api.Assertions.assertThrows(
        RuntimeException.class, () -> merge(base, working, generated));

    Resource wrongType = model(1, "wrong-type");
    EObject root = wrongType.getContents().get(0);
    org.junit.jupiter.api.Assertions.assertThrows(
        RuntimeException.class, () -> root.eSet(nodes, root));
  }

  private ModelSynchronizationService.MergeOutcome merge(
      Resource base, Resource working, Resource incoming) {
    return service.synchronize(base, working, incoming, TransformationDirection.CIM_TO_PIM);
  }

  private ModelSynchronizationService.MergeOutcome mergeWithDecisions(
      Resource base,
      Resource working,
      Resource incoming,
      Map<String, ConflictResolution> decisions) {
    return service.synchronize(
        base, working, incoming, TransformationDirection.CIM_TO_PIM, decisions);
  }

  private Resource model(int memory, String description) {
    Resource resource = emptyModel();
    addNode(resource, "n1", "Function", memory, description);
    return resource;
  }

  private Resource modelWithGeneratedFrom(String generatedFrom) {
    Resource resource = model(512, "A");
    set(resource, "n1", "generatedFrom", generatedFrom);
    return resource;
  }

  private Resource modelWithChild(String childName) {
    Resource resource = model(512, "A");
    EObject child = addChild(resource, "n1", "child", "child");
    child.eSet(feature("name"), childName);
    return resource;
  }

  private Resource twoNodeModel(String description) {
    Resource resource = model(512, description);
    addNode(resource, "n2", "Peer", 128, "peer");
    return resource;
  }

  private Resource threeNodeModel() {
    Resource resource = twoNodeModel("A");
    addNode(resource, "n3", "Other peer", 64, "other");
    return resource;
  }

  private Resource threeParentMoveModel() {
    Resource resource = threeNodeModel();
    addChild(resource, "n1", "movable", "child");
    return resource;
  }

  private Resource emptyModel() {
    Resource resource = resource("model-" + System.nanoTime());
    return emptyModel(resource);
  }

  private Resource emptyModel(ResourceSet resourceSet, String name) {
    return emptyModel(resourceSet.createResource(URI.createURI("memory:/" + name + ".xmi")));
  }

  private Resource emptyModel(Resource resource) {
    EObject root = modelPackage.getEFactoryInstance().create(rootClass);
    root.eSet(rootClass.getEStructuralFeature("id"), "root");
    resource.getContents().add(root);
    return resource;
  }

  private ResourceSet configuredResourceSet() {
    ResourceSet resourceSet = new ResourceSetImpl();
    resourceSet.getPackageRegistry().put(modelPackage.getNsURI(), modelPackage);
    resourceSet
        .getResourceFactoryRegistry()
        .getExtensionToFactoryMap()
        .put("xmi", new XMIResourceFactoryImpl());
    return resourceSet;
  }

  private EObject addNode(
      Resource resource, String id, String name, int memory, String description) {
    EObject node = modelPackage.getEFactoryInstance().create(nodeClass);
    node.eSet(feature("id"), id);
    node.eSet(feature("name"), name);
    node.eSet(feature("memory"), memory);
    node.eSet(feature("description"), description);
    @SuppressWarnings("unchecked")
    List<EObject> values = (List<EObject>) resource.getContents().get(0).eGet(nodes);
    values.add(node);
    return node;
  }

  private EObject addRequiredNode(Resource resource, String id, String childId) {
    EObject required = modelPackage.getEFactoryInstance().create(requiredNodeClass);
    required.eSet(requiredNodeClass.getEStructuralFeature("id"), id);
    EObject child = modelPackage.getEFactoryInstance().create(nodeClass);
    child.eSet(feature("id"), childId);
    child.eSet(feature("name"), childId);
    @SuppressWarnings("unchecked")
    List<EObject> children = (List<EObject>) required.eGet(requiredNodeChildren);
    children.add(child);
    @SuppressWarnings("unchecked")
    List<EObject> values = (List<EObject>) resource.getContents().get(0).eGet(requiredNodes);
    values.add(required);
    return required;
  }

  private List<?> requiredNodeValues(Resource resource) {
    return (List<?>) resource.getContents().get(0).eGet(requiredNodes);
  }

  private EObject addChild(Resource resource, String parentId, String id, String description) {
    EObject child = modelPackage.getEFactoryInstance().create(nodeClass);
    child.eSet(feature("id"), id);
    child.eSet(feature("name"), id);
    child.eSet(feature("description"), description);
    @SuppressWarnings("unchecked")
    List<EObject> values = (List<EObject>) find(resource, parentId).eGet(childNodes);
    values.add(child);
    return child;
  }

  private void moveChild(Resource resource, String oldParentId, EObject newParent, String childId) {
    @SuppressWarnings("unchecked")
    List<EObject> oldParent = (List<EObject>) find(resource, oldParentId).eGet(childNodes);
    EObject child = find(resource, childId);
    oldParent.remove(child);
    @SuppressWarnings("unchecked")
    List<EObject> newChildren = (List<EObject>) newParent.eGet(childNodes);
    newChildren.add(child);
  }

  private void removeNode(Resource resource, String nodeId) {
    @SuppressWarnings("unchecked")
    List<EObject> values = (List<EObject>) resource.getContents().get(0).eGet(nodes);
    values.remove(find(resource, nodeId));
  }

  private void removeContained(Resource resource, String objectId) {
    EObject object = find(resource, objectId);
    EObject container = object == null ? null : object.eContainer();
    if (container == null) return;
    EStructuralFeature containment = object.eContainmentFeature();
    if (containment.isMany()) {
      @SuppressWarnings("unchecked")
      List<EObject> values = (List<EObject>) container.eGet(containment);
      values.remove(object);
    } else {
      container.eUnset(containment);
    }
  }

  private void reorderNodes(Resource resource, String... orderedIds) {
    List<EObject> values = contents(resource);
    Map<String, EObject> byId = new java.util.HashMap<>();
    for (EObject value : values) byId.put(id(value), value);
    values.clear();
    for (String orderedId : orderedIds) values.add(byId.get(orderedId));
  }

  private List<String> ids(Resource resource) {
    return contents(resource).stream().map(this::id).toList();
  }

  private Resource resource(String name) {
    ResourceSet resourceSet = new ResourceSetImpl();
    resourceSet.getPackageRegistry().put(modelPackage.getNsURI(), modelPackage);
    resourceSet
        .getResourceFactoryRegistry()
        .getExtensionToFactoryMap()
        .put("xmi", new XMIResourceFactoryImpl());
    return resourceSet.createResource(URI.createURI("memory:/" + name + ".xmi"));
  }

  private Resource load(byte[] bytes, String name) throws Exception {
    Resource resource = resource(name);
    resource.load(new ByteArrayInputStream(bytes), Map.of());
    return resource;
  }

  private byte[] bytes(Resource resource) throws Exception {
    java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
    resource.save(output, Map.of());
    return output.toByteArray();
  }

  private byte[] bytesUnchecked(Resource resource) {
    try {
      return bytes(resource);
    } catch (Exception ex) {
      throw new IllegalStateException(ex);
    }
  }

  private List<EObject> contents(Resource resource) {
    @SuppressWarnings("unchecked")
    List<EObject> values = (List<EObject>) resource.getContents().get(0).eGet(nodes);
    return values;
  }

  private EObject find(Resource resource, String expectedId) {
    for (EObject root : resource.getContents()) {
      EObject found = findContained(root, expectedId);
      if (found != null) {
        return found;
      }
    }
    return null;
  }

  private EObject findContained(EObject object, String expectedId) {
    if (expectedId.equals(id(object))) {
      return object;
    }
    for (var iterator = object.eAllContents(); iterator.hasNext(); ) {
      EObject candidate = iterator.next();
      if (expectedId.equals(id(candidate))) {
        return candidate;
      }
    }
    return null;
  }

  private Resource loadUnchecked(byte[] bytes, String name) {
    try {
      return load(bytes, name);
    } catch (Exception ex) {
      throw new IllegalStateException(ex);
    }
  }

  private Object value(Resource resource, String id, String featureName) {
    EObject object = find(resource, id);
    return object == null ? null : object.eGet(feature(featureName));
  }

  private void set(Resource resource, String id, String featureName, Object value) {
    find(resource, id).eSet(feature(featureName), value);
  }

  private void setReference(Resource resource, String id, EObject target) {
    find(resource, id).eSet(peer, target);
  }

  private EAttribute feature(String name) {
    return (EAttribute) nodeClass.getEStructuralFeature(name);
  }

  private String id(EObject object) {
    var idFeature = object.eClass().getEStructuralFeature("id");
    Object value = idFeature == null ? null : object.eGet(idFeature);
    return value == null ? "" : String.valueOf(value);
  }

  private String id(Resource resource, String expectedId) {
    EObject object = find(resource, expectedId);
    return object == null ? "" : id(object);
  }

  private EAttribute attribute(
      String name, org.eclipse.emf.ecore.EDataType type, boolean identifier, boolean many) {
    EAttribute attribute = EcoreFactory.eINSTANCE.createEAttribute();
    attribute.setName(name);
    attribute.setEType(type);
    attribute.setID(identifier);
    if (many) {
      attribute.setUpperBound(-1);
    }
    return attribute;
  }
}
