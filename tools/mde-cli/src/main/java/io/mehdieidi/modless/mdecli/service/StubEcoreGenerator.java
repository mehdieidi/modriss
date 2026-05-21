package io.mehdieidi.modless.mdecli.service;

import java.io.IOException;
import java.nio.file.Path;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EDataType;
import org.eclipse.emf.ecore.EEnum;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.EcoreFactory;
import org.eclipse.emf.ecore.EcorePackage;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;

public final class StubEcoreGenerator {

  private final EmfResourceSupport resourceSupport = new EmfResourceSupport();

  public void generate(StubMetamodelDefinition definition, Path outputFile) throws IOException {
    ResourceSet resourceSet = resourceSupport.newResourceSet();
    Resource resource = resourceSupport.newEcoreResource(resourceSet, URI.createFileURI(outputFile.toString()));
    resource.getContents().add(createPackage(definition));
    resource.save(null);
  }

  private EPackage createPackage(StubMetamodelDefinition definition) {
    EPackage ePackage = EcoreFactory.eINSTANCE.createEPackage();
    ePackage.setName(definition.packageName());
    ePackage.setNsURI(definition.namespaceUri());
    ePackage.setNsPrefix(definition.namespacePrefix());

    for (StubClassifierDefinition classifier : definition.classifiers()) {
      ePackage.getEClassifiers().add(createClassifier(classifier));
    }
    return ePackage;
  }

  private org.eclipse.emf.ecore.EClassifier createClassifier(StubClassifierDefinition classifier) {
    return switch (classifier.kind()) {
      case "class" -> newClass(classifier, false, false);
      case "abstract class" -> newClass(classifier, true, false);
      case "interface" -> newClass(classifier, true, true);
      case "enum" -> newEnum(classifier.name());
      case "datatype" -> newDataType(classifier.name());
      default -> throw new IllegalStateException("Unsupported classifier kind for stub generation: " + classifier.kind());
    };
  }

  private EClass newClass(StubClassifierDefinition classifier, boolean isAbstract, boolean isInterface) {
    EClass eClass = EcoreFactory.eINSTANCE.createEClass();
    eClass.setName(classifier.name());
    eClass.setAbstract(isAbstract);
    eClass.setInterface(isInterface);
    for (StubFeatureDefinition feature : classifier.features()) {
      if ("attr".equals(feature.kind())) {
        var attribute = EcoreFactory.eINSTANCE.createEAttribute();
        attribute.setName(feature.name());
        attribute.setEType(EcorePackage.eINSTANCE.getEString());
        eClass.getEStructuralFeatures().add(attribute);
      } else {
        EReference reference = EcoreFactory.eINSTANCE.createEReference();
        reference.setName(feature.name());
        reference.setEType(EcorePackage.eINSTANCE.getEObject());
        eClass.getEStructuralFeatures().add(reference);
      }
    }
    return eClass;
  }

  private EEnum newEnum(String name) {
    EEnum eEnum = EcoreFactory.eINSTANCE.createEEnum();
    eEnum.setName(name);
    return eEnum;
  }

  private EDataType newDataType(String name) {
    EDataType eDataType = EcoreFactory.eINSTANCE.createEDataType();
    eDataType.setName(name);
    eDataType.setInstanceClassName("java.lang.Object");
    return eDataType;
  }
}
