package io.mehdieidi.modriss.platform.psm;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.eclipse.emf.ecore.*;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceImpl;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.node.ObjectNode;

class PsmModelCodecTest {
  @Test
  @SuppressWarnings("unchecked")
  void preservesTypedAttributesReservedNamesContainmentsAndReferences() {
    var factory = EcoreFactory.eINSTANCE;
    var pkg = factory.createEPackage();
    pkg.setName("cloud");
    pkg.setNsPrefix("cloud");
    pkg.setNsURI("urn:cloud");
    var rootType = factory.createEClass();
    rootType.setName("Application");
    pkg.getEClassifiers().add(rootType);
    var nodeType = factory.createEClass();
    nodeType.setName("Node");
    pkg.getEClassifiers().add(nodeType);
    var name = attribute(nodeType, "name", EcorePackage.eINSTANCE.getEString());
    var semanticId = attribute(nodeType, "id", EcorePackage.eINSTANCE.getEString());
    var x = attribute(nodeType, "x", EcorePackage.eINSTANCE.getEInt());
    var enabled = attribute(nodeType, "enabled", EcorePackage.eINSTANCE.getEBoolean());
    var tags = attribute(nodeType, "tags", EcorePackage.eINSTANCE.getEString());
    tags.setUpperBound(-1);
    var children = factory.createEReference();
    children.setName("nodes");
    children.setEType(nodeType);
    children.setContainment(true);
    children.setUpperBound(-1);
    rootType.getEStructuralFeatures().add(children);
    var peer = factory.createEReference();
    peer.setName("peer");
    peer.setEType(nodeType);
    nodeType.getEStructuralFeatures().add(peer);
    var root = pkg.getEFactoryInstance().create(rootType);
    var first = pkg.getEFactoryInstance().create(nodeType);
    var second = pkg.getEFactoryInstance().create(nodeType);
    ((List<EObject>) root.eGet(children)).addAll(List.of(first, second));
    first.eSet(name, "Hello");
    first.eSet(semanticId, "semantic-id");
    first.eSet(x, 42);
    first.eSet(enabled, true);
    ((List<String>) first.eGet(tags)).addAll(List.of("one", "two"));
    first.eSet(peer, second);
    var resource = new XMIResourceImpl();
    resource.getContents().add(root);
    var codec = new PsmModelCodec(List.of(pkg));
    var json = codec.read(resource);
    var child = (ObjectNode) json.path("nodes").get(0);
    assertEquals("semantic-id", child.path("_semantic_id").asText());
    assertEquals(42, child.path("_semantic_x").asInt());
    assertTrue(child.path("enabled").asBoolean());
    assertEquals(2, child.path("_semantic_tags").size());
    assertEquals(
        json.path("nodes").get(1).path("id").asText(), child.path("peer").path("$ref").asText());
    child.put("name", "Edited");
    var restored = new XMIResourceImpl();
    new PsmModelCodec(List.of(pkg)).write(restored, json);
    var restoredNodes = (List<EObject>) restored.getContents().get(0).eGet(children);
    assertEquals("Edited", restoredNodes.get(0).eGet(name));
    assertEquals("semantic-id", restoredNodes.get(0).eGet(semanticId));
    assertEquals(42, restoredNodes.get(0).eGet(x));
    assertSame(restoredNodes.get(1), restoredNodes.get(0).eGet(peer));
    assertEquals(List.of("one", "two"), restoredNodes.get(0).eGet(tags));
    child.put("eClass", "UnrecognizedClass");
    assertThrows(
        IllegalArgumentException.class,
        () -> new PsmModelCodec(List.of(pkg)).write(new XMIResourceImpl(), json));
  }

  private EAttribute attribute(EClass owner, String name, EDataType type) {
    var attr = EcoreFactory.eINSTANCE.createEAttribute();
    attr.setName(name);
    attr.setEType(type);
    owner.getEStructuralFeatures().add(attr);
    return attr;
  }
}
