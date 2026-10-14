package io.mehdieidi.modriss.mde.generation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EcoreFactory;
import org.eclipse.emf.ecore.EcorePackage;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Verifies readable, collision-safe artifact paths and preservation during path migration. */
class ArtifactPathNamingTest {

  @TempDir Path output;

  @Test
  void usesOperationNameInsteadOfLongDeploymentName() {
    EObject function =
        function(
            "one",
            "ApplyCaseFunction",
            "dev-bounded-asynchronous-csv-exports-applycasefunction-lambda");
    assertEquals("apply-case", naming().name(function, "function", List.of(function)));
  }

  @Test
  void retainsExistingShortNamesAndAvoidsWindowsDeviceNames() {
    EObject order = function("order", "Order Handler", "order-handler");
    EObject reserved = function("device", "CON", "CON");
    ArtifactPathNaming names = naming();
    assertEquals("order-handler", names.name(order, "function", List.of(order, reserved)));
    assertEquals("function-con", names.name(reserved, "function", List.of(order, reserved)));
  }

  @Test
  void keepsCollidingAndLongNamesUniqueAndIndependentOfOrder() {
    EObject first = function("first", "ApplyCaseFunction", "prod-very-long-project-apply-lambda");
    EObject second = function("second", "ApplyCaseFunction", "dev-very-long-project-apply-lambda");
    EObject longName =
        function(
            "long",
            "GenerateExceptionallyDetailedMonthlyAccountingReportFunction",
            "dev-very-long-project-generate-accounting-report-lambda");
    List<EObject> peers = List.of(first, second, longName);
    ArtifactPathNaming names = naming();
    String firstName = names.name(first, "function", peers);
    String secondName = names.name(second, "function", peers);
    assertTrue(firstName.startsWith("apply-case-"));
    assertNotEquals(firstName, secondName);
    assertTrue(names.name(longName, "function", peers).length() <= 32);
    assertEquals(firstName, naming().name(first, "function", List.of(longName, second, first)));
  }

  @Test
  void copiesOnlyPreviouslyGeneratedPathsForProtectedRegionMerging() throws Exception {
    String oldPath = "src/functions/old-long-name/handler.go";
    String newPath = "src/functions/apply-case/handler.go";
    Files.createDirectories(output.resolve(oldPath).getParent());
    Files.writeString(output.resolve(oldPath), "developer code");
    ArtifactPathNaming names = new ArtifactPathNaming(output, Set.of(oldPath));
    assertEquals(newPath, names.preservePath(newPath, oldPath));
    assertEquals("developer code", Files.readString(output.resolve(newPath)));
    Files.writeString(output.resolve(newPath), "already merged");
    names.preservePath(newPath, oldPath);
    assertEquals("already merged", Files.readString(output.resolve(newPath)));
    naming().preservePath("src/functions/untracked/handler.go", oldPath);
    assertFalse(Files.exists(output.resolve("src/functions/untracked/handler.go")));
  }

  private ArtifactPathNaming naming() {
    return new ArtifactPathNaming(output, Set.of());
  }

  @Test
  void migratesBySourceIdentityWhenNamesChangeAndProtectsUntrackedDestinations() throws Exception {
    String oldPath = "src/functions/apply-case/handler.go";
    String newPath = "src/functions/apply-case-12345678/handler.go";
    Files.createDirectories(output.resolve(oldPath).getParent());
    Files.writeString(output.resolve(oldPath), "protected developer code");
    Path trace = output.resolve("generated/trace/artifact-trace.json");
    Files.createDirectories(trace.getParent());
    Files.writeString(
        trace,
        """
        {"artifacts":[{"path":"src/functions/apply-case/handler.go",
        "artifactKind":"LAMBDA_HANDLER","sourceStableIds":["ApplyCase"]}]}
        """);
    ArtifactPathNaming names = new ArtifactPathNaming(output, Set.of(oldPath));
    names.preservePath(newPath, "unrelated-legacy-path", "ApplyCase", "LAMBDA_HANDLER");
    assertEquals("protected developer code", Files.readString(output.resolve(newPath)));
    String independent = "src/functions/independent/handler.go";
    Files.createDirectories(output.resolve(independent).getParent());
    Files.writeString(output.resolve(independent), "independent code");
    assertThrows(java.io.IOException.class, () -> names.preservePath(independent, oldPath));
    assertEquals("independent code", Files.readString(output.resolve(independent)));
  }

  private EObject function(String id, String name, String deploymentName) {
    EClass type = EcoreFactory.eINSTANCE.createEClass();
    type.setName("AwsLambdaFunction");
    EcoreFactory.eINSTANCE.createEPackage().getEClassifiers().add(type);
    for (String field : List.of("id", "name", "functionName")) {
      var attribute = EcoreFactory.eINSTANCE.createEAttribute();
      attribute.setName(field);
      attribute.setEType(EcorePackage.Literals.ESTRING);
      type.getEStructuralFeatures().add(attribute);
    }
    EObject object = EcoreUtil.create(type);
    object.eSet(type.getEStructuralFeature("id"), id);
    object.eSet(type.getEStructuralFeature("name"), name);
    object.eSet(type.getEStructuralFeature("functionName"), deploymentName);
    return object;
  }
}
