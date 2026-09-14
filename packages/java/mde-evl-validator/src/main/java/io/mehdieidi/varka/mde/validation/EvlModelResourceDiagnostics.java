package io.mehdieidi.varka.mde.validation;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.eclipse.emf.common.util.Diagnostic;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.util.Diagnostician;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.emf.ecore.xmi.XMLResource;

/** Converts EMF resource load and structural validation diagnostics into EVL diagnostics. */
public final class EvlModelResourceDiagnostics {

  /** Prevents construction of this helper class. */
  private EvlModelResourceDiagnostics() {}

  /**
   * Validates a loaded EMF resource and returns structural diagnostics.
   *
   * @param resource resource to inspect
   * @param modelFile file associated with the resource, when available
   * @return structural diagnostics for errors and warnings
   */
  public static List<EvlDiagnostic> validate(Resource resource, Path modelFile) {
    if (resource == null) {
      return List.of();
    }
    indexXmlIds(resource);
    List<EvlDiagnostic> diagnostics = new ArrayList<>();
    resource
        .getErrors()
        .forEach(
            error ->
                diagnostics.add(resourceDiagnostic(ValidationSeverity.ERROR, error, modelFile)));
    resource
        .getWarnings()
        .forEach(
            warning ->
                diagnostics.add(
                    resourceDiagnostic(ValidationSeverity.WARNING, warning, modelFile)));
    for (EObject root : resource.getContents()) {
      collectDiagnostics(Diagnostician.INSTANCE.validate(root), modelFile, diagnostics);
    }
    return diagnostics;
  }

  /**
   * Builds the XML resource ID index before EMF validation.
   *
   * <p>Generated in-memory resources do not populate the ID map while their XMI IDs are still
   * available through the XML resource. Without this index, EMF's {@code UniqueID} validator scans
   * the entire resource for every object, turning structural validation into a quadratic operation
   * for larger generated models.
   *
   * @param resource resource whose XML IDs should be indexed
   */
  public static void indexXmlIds(Resource resource) {
    if (!(resource instanceof XMLResource xmlResource)) {
      return;
    }
    var idToObject = xmlResource.getIDToEObjectMap();
    var objectToId = xmlResource.getEObjectToIDMap();
    for (EObject root : resource.getContents()) {
      indexXmlId(root, xmlResource, idToObject, objectToId);
      for (var contents = root.eAllContents(); contents.hasNext(); ) {
        indexXmlId(contents.next(), xmlResource, idToObject, objectToId);
      }
    }
  }

  private static void indexXmlId(
      EObject object,
      XMLResource xmlResource,
      java.util.Map<String, EObject> idToObject,
      java.util.Map<EObject, String> objectToId) {
    String id = objectToId.get(object);
    if (id == null || id.isBlank()) {
      id = EcoreUtil.getID(object);
      if (id != null && !id.isBlank()) {
        objectToId.put(object, id);
      }
    }
    if (id != null && !id.isBlank()) {
      idToObject.putIfAbsent(id, object);
    }
  }

  /**
   * Converts a resource-level load diagnostic to the validator diagnostic shape.
   *
   * @param severity mapped diagnostic severity
   * @param diagnostic EMF resource diagnostic
   * @param modelFile file associated with the resource
   * @return EVL diagnostic
   */
  private static EvlDiagnostic resourceDiagnostic(
      ValidationSeverity severity, Resource.Diagnostic diagnostic, Path modelFile) {
    return new EvlDiagnostic(
        severity,
        ValidationPhase.MODEL_LOADING,
        modelFile,
        diagnostic.getLine(),
        -1,
        diagnostic.getMessage(),
        "The EMF resource reported a structural model problem.",
        "Fix the model XMI so it conforms to the loaded Ecore metamodel.",
        diagnostic.getClass().getName());
  }

  /**
   * Recursively flattens EMF validation diagnostics, preferring leaf messages.
   *
   * @param diagnostic EMF diagnostic tree
   * @param modelFile file associated with the resource
   * @param out mutable diagnostic sink
   */
  private static void collectDiagnostics(
      Diagnostic diagnostic, Path modelFile, List<EvlDiagnostic> out) {
    if (diagnostic == null
        || diagnostic.getSeverity() == Diagnostic.OK
        || diagnostic.getSeverity() == Diagnostic.INFO) {
      return;
    }
    int before = out.size();
    diagnostic.getChildren().forEach(child -> collectDiagnostics(child, modelFile, out));
    if (out.size() == before) {
      out.add(
          new EvlDiagnostic(
              severity(diagnostic),
              ValidationPhase.MODEL_LOADING,
              modelFile,
              -1,
              -1,
              diagnostic.getMessage(),
              "The EMF model does not conform to its Ecore metamodel.",
              "Fix the reported structural problem before running semantic EVL validation.",
              Diagnostic.class.getName()));
    }
  }

  /**
   * Maps EMF diagnostic severity to EVL diagnostic severity.
   *
   * @param diagnostic EMF diagnostic
   * @return validation severity
   */
  private static ValidationSeverity severity(Diagnostic diagnostic) {
    return diagnostic.getSeverity() == Diagnostic.WARNING
        ? ValidationSeverity.WARNING
        : ValidationSeverity.ERROR;
  }
}
