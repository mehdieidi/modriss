package io.mehdieidi.varka.platform.transformation.synchronization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Map;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EReference;
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
        .add(attribute("incomingTraces", EcorePackage.Literals.ESTRING, false, true));
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
    modelPackage.getEClassifiers().add(rootClass);
    modelPackage.getEClassifiers().add(nodeClass);
  }

  @Test
  void scenario4PreservesManualRefinement() {
    var result = merge(model(512, "A"), model(1024, "A"), model(512, "A"));
    assertEquals(1024, value(result.mergedWorking(), "n1", "memory"));
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
  void scenario14KeepsGeneratorOwnedTraceMetadataConsistent() {
    Resource base = model(512, "A");
    Resource local = model(512, "A");
    Resource incoming = model(512, "A");
    set(base, "n1", "generatedFrom", "source-v1");
    set(local, "n1", "generatedFrom", "local-edit");
    set(incoming, "n1", "generatedFrom", "source-v2");
    var result = merge(base, local, incoming);
    assertEquals("source-v2", value(result.mergedWorking(), "n1", "generatedFrom"));
    assertTrue(result.conflicts().isEmpty());
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

  private ModelSynchronizationService.MergeOutcome merge(
      Resource base, Resource working, Resource incoming) {
    return service.synchronize(base, working, incoming, TransformationDirection.CIM_TO_PIM);
  }

  private Resource model(int memory, String description) {
    Resource resource = emptyModel();
    addNode(resource, "n1", "Function", memory, description);
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

  private Resource emptyModel() {
    Resource resource = resource("model-" + System.nanoTime());
    EObject root = modelPackage.getEFactoryInstance().create(rootClass);
    root.eSet(rootClass.getEStructuralFeature("id"), "root");
    resource.getContents().add(root);
    return resource;
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

  private EAttribute feature(String name) {
    return (EAttribute) nodeClass.getEStructuralFeature(name);
  }

  private String id(EObject object) {
    var idFeature = object.eClass().getEStructuralFeature("id");
    return idFeature == null ? "" : String.valueOf(object.eGet(idFeature));
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
