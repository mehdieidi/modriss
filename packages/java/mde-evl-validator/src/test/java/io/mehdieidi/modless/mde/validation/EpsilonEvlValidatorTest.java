package io.mehdieidi.modless.mde.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EcoreFactory;
import org.eclipse.emf.ecore.EcorePackage;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.impl.ResourceImpl;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EpsilonEvlValidatorTest {

    private static final Path REPOSITORY_ROOT = findRepositoryRoot();

    @TempDir
    Path tempDir;

    private static long countViolations(EvlValidationReport report, String constraintName) {
        return report.violations().stream()
                .filter(v -> v.constraintName().equals(constraintName))
                .count();
    }

    private static Path findRepositoryRoot() {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        while (current != null) {
            if (Files.isDirectory(current.resolve("mde/metamodels"))
                    && Files.isDirectory(current.resolve("mde/validation/psm"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Could not locate repository root from user.dir.");
    }

    @Test
    void reportsMandatoryAndOptionalViolationsFromImportedEvl() throws Exception {
        Path evlRoot = tempDir.resolve("evl");
        Files.createDirectories(evlRoot.resolve("rules"));
        Files.writeString(evlRoot.resolve("main.evl"), "import \"rules/person.evl\";\n");
        Files.writeString(evlRoot.resolve("rules/person.evl"), """
                context M!Person {
                  constraint PersonMustBeAdult {
                    check : self.age >= 18
                    message : "Person must be at least 18. Fix: increase age or remove adult-only role."
                  }
                
                  critique PersonShouldHaveName {
                    check : self.name.isDefined() and self.name <> ""
                    message : "Person should have a name. Suggested fix: set name."
                  }
                }
                """);

        TestModel testModel = testModel();
        EObject person = testModel.newPerson();
        person.eSet(testModel.age, 12);
        person.eSet(testModel.name, "");
        testModel.resource.getContents().add(person);

        EvlValidationReport report = new EpsilonEvlValidator().validate(
                EvlValidationRequest.forRoot(
                        evlRoot,
                        List.of(ResourceEvlModelConfiguration.readOnly(
                                "M", testModel.resource, List.of(testModel.ePackage))),
                        true));

        assertEquals(EvlValidationStatus.SUCCEEDED, report.status());
        assertTrue(report.diagnostics().isEmpty());
        assertEquals(2, report.violations().size());
        assertTrue(report.hasMandatoryViolations());
        assertTrue(report.violations().stream()
                .anyMatch(v -> v.kind() == EvlConstraintKind.MANDATORY
                        && v.constraintName().equals("PersonMustBeAdult")
                        && v.contextType().equals("M!Person")
                        && v.element().attributes().get("age").equals("12")));
        assertTrue(report.violations().stream()
                .anyMatch(v -> v.kind() == EvlConstraintKind.OPTIONAL
                        && v.constraintName().equals("PersonShouldHaveName")));
    }

    @Test
    void succeedsWithNoViolationsForValidModel() throws Exception {
        Path evlFile = tempDir.resolve("person.evl");
        Files.writeString(evlFile, """
                context M!Person {
                  constraint PersonMustBeAdult {
                    check : self.age >= 18
                    message : "Person must be at least 18."
                  }
                }
                """);

        TestModel testModel = testModel();
        EObject person = testModel.newPerson();
        person.eSet(testModel.age, 23);
        person.eSet(testModel.name, "Ada");
        testModel.resource.getContents().add(person);

        EvlValidationReport report = new EpsilonEvlValidator().validate(
                EvlValidationRequest.forRoot(
                        evlFile,
                        List.of(ResourceEvlModelConfiguration.readOnly(
                                "M", testModel.resource, List.of(testModel.ePackage))),
                        false));

        assertEquals(EvlValidationStatus.SUCCEEDED, report.status());
        assertTrue(report.violations().isEmpty());
        assertFalse(report.hasMandatoryViolations());
    }

    @Test
    void validatesRepositoryPsmSampleWithReadinessBacklog() throws Exception {
        Path psmSample = REPOSITORY_ROOT.resolve("mde/samples/psm.xmi");
        EvlValidationReport report = new EpsilonEvlValidator().validate(
                EvlValidationRequest.forRoot(
                        REPOSITORY_ROOT.resolve("mde/validation/psm/psm-semantic-validation.evl"),
                        List.of(FileEvlModelConfiguration.readOnly(
                                "AWSPSM",
                                List.of("AWSPSMENUMS", "KERNEL"),
                                psmSample,
                                List.of(REPOSITORY_ROOT.resolve(
                                        "mde/metamodels/psm/psm-combined.ecore")))),
                        true));

        assertEquals(EvlValidationStatus.SUCCEEDED, report.status());
        assertTrue(report.diagnostics().isEmpty(), report.diagnostics().toString());
        assertFalse(report.hasMandatoryViolations(),
                "The repository sample should pass mandatory PSM semantic validation.");
        assertEquals(0, report.violations().stream()
                .filter(v -> v.kind() == EvlConstraintKind.MANDATORY)
                .count());
        assertEquals(0, countViolations(report, "StatementHasActionAndResourceSide"));
        assertEquals(0, countViolations(report, "ZipCodeHasRuntimeAndHandler"));
        assertEquals(0, countViolations(report, "IntegrationHasSingleTarget"));
        assertEquals(0, countViolations(report, "AccessLogStageRequiresGroupAndFormat"));
        String sampleXml = Files.readString(psmSample);
        assertTrue(sampleXml.contains("<manualDecisions"),
                "Readiness blockers should remain represented as manual decisions.");
        assertTrue(sampleXml.contains("blocking=\"true\""),
                "The sample should still carry explicit manual review blockers.");
        assertEquals(0, countViolations(report, "CriticalEventTargetsHaveRetryOrDlq"));
    }

    @Test
    void parseFailuresCarryStructuredDiagnostics() throws Exception {
        Path evlFile = tempDir.resolve("broken.evl");
        Files.writeString(evlFile, "context M!Person { constraint Broken { check : } }");

        TestModel testModel = testModel();
        EvlValidationException exception = assertThrows(
                EvlValidationException.class,
                () -> new EpsilonEvlValidator().validate(
                        EvlValidationRequest.forRoot(
                                evlFile,
                                List.of(ResourceEvlModelConfiguration.readOnly(
                                        "M", testModel.resource, List.of(testModel.ePackage))),
                                true)));

        assertEquals(EvlValidationStatus.FAILED, exception.getReport().status());
        assertTrue(exception.getReport().diagnostics().stream()
                .anyMatch(d -> d.phase() == ValidationPhase.PARSE
                        && d.severity() == ValidationSeverity.ERROR));
    }

    @Test
    void runtimeFailuresCarryLocationAndReason() throws Exception {
        Path evlFile = tempDir.resolve("runtime.evl");
        Files.writeString(evlFile, """
                context M!MissingType {
                  constraint FailsAtRuntime {
                    check : true
                    message : "Should never reach here."
                  }
                }
                """);

        TestModel testModel = testModel();
        EObject person = testModel.newPerson();
        person.eSet(testModel.age, 30);
        testModel.resource.getContents().add(person);

        EvlValidationException exception = assertThrows(
                EvlValidationException.class,
                () -> new EpsilonEvlValidator().validate(
                        EvlValidationRequest.forRoot(
                                evlFile,
                                List.of(ResourceEvlModelConfiguration.readOnly(
                                        "M", testModel.resource, List.of(testModel.ePackage))),
                                true)));

        EvlDiagnostic diagnostic = exception.getReport().diagnostics().get(0);
        assertEquals(ValidationPhase.EXECUTION, diagnostic.phase());
        assertTrue(diagnostic.line() > 0);
        assertTrue(diagnostic.reason().contains("MissingType"));
    }

    private TestModel testModel() {
        EcoreFactory factory = EcoreFactory.eINSTANCE;
        EPackage ePackage = factory.createEPackage();
        ePackage.setName("model");
        ePackage.setNsPrefix("model");
        ePackage.setNsURI("urn:test:model");

        EClass person = factory.createEClass();
        person.setName("Person");

        EAttribute name = factory.createEAttribute();
        name.setName("name");
        name.setEType(EcorePackage.Literals.ESTRING);
        person.getEStructuralFeatures().add(name);

        EAttribute age = factory.createEAttribute();
        age.setName("age");
        age.setEType(EcorePackage.Literals.EINT);
        person.getEStructuralFeatures().add(age);

        ePackage.getEClassifiers().add(person);
        Resource resource = new ResourceImpl(URI.createURI("memory:/people.xmi"));
        return new TestModel(ePackage, resource, person, name, age);
    }

    private record TestModel(
            EPackage ePackage,
            Resource resource,
            EClass person,
            EAttribute name,
            EAttribute age) {

        EObject newPerson() {
            return EcoreUtil.create(person);
        }
    }
}
