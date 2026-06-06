package io.mehdieidi.modless.mde.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EcoreFactory;
import org.eclipse.emf.ecore.EcorePackage;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceImpl;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.emf.ecore.xmi.impl.EcoreResourceFactoryImpl;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Regression tests for EVL validation, diagnostics, and model-loading behavior.
 */
class EpsilonEvlValidatorTest {

    /**
     * Repository root discovered from the current test working directory.
     */
    private static final Path REPOSITORY_ROOT = findRepositoryRoot();

    /**
     * Temporary directory for generated EVL, Ecore, and XMI fixtures.
     */
    @TempDir
    Path tempDir;

    /**
     * Counts violations for a named EVL constraint.
     *
     * @param report         validation report to inspect
     * @param constraintName EVL constraint name
     * @return number of matching violations
     */
    private static long countViolations(EvlValidationReport report, String constraintName) {
        return report.violations().stream()
                .filter(v -> v.constraintName().equals(constraintName))
                .count();
    }

    /**
     * Locates the repository root by walking upward to the MDE metamodel and validation
     * directories.
     *
     * @return normalized repository root path
     */
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

    /**
     * Verifies imported EVL modules report both mandatory constraints and optional critiques while
     * exposing only safe element attributes.
     *
     * @throws Exception when fixture writing or validation fails
     */
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
        person.eSet(testModel.secretToken, "secret-token-value");
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
        EvlConstraintViolation mandatory = report.violations().stream()
                .filter(v -> v.kind() == EvlConstraintKind.MANDATORY
                        && v.constraintName().equals("PersonMustBeAdult")
                        && v.contextType().equals("M!Person"))
                .findFirst()
                .orElseThrow();
        assertTrue(mandatory.element().attributes().containsKey("name"));
        assertFalse(mandatory.element().attributes().containsKey("age"));
        assertFalse(mandatory.element().attributes().containsKey("secretToken"));
        assertTrue(report.violations().stream()
                .anyMatch(v -> v.kind() == EvlConstraintKind.OPTIONAL
                        && v.constraintName().equals("PersonShouldHaveName")));
    }

    /**
     * Ensures a valid in-memory model succeeds with no violations.
     *
     * @throws Exception when fixture writing or validation fails
     */
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

    /**
     * Validates the repository AWS PSM sample and verifies readiness manual decisions remain
     * explicit without triggering mandatory violations.
     *
     * @throws Exception when sample loading or validation fails
     */
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

    /**
     * Ensures EVL parse errors are reported as structured diagnostics.
     *
     * @throws Exception when fixture writing fails
     */
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

    /**
     * Confirms that one module's parse diagnostics do not prevent independent modules from
     * producing their own validation results.
     *
     * @throws Exception when fixture writing or validation fails
     */
    @Test
    void continuesIndependentModulesAfterModuleDiagnostics() throws Exception {
        Path evlRoot = tempDir.resolve("evl-continue");
        Files.createDirectories(evlRoot);
        Files.writeString(evlRoot.resolve("broken.evl"),
                "context M!Person { constraint Broken { check : } }");
        Files.writeString(evlRoot.resolve("valid.evl"), """
                context M!Person {
                  constraint PersonMustBeAdult {
                    check : self.age >= 18
                    message : "Person must be at least 18."
                  }
                }
                """);

        TestModel testModel = testModel();
        EObject person = testModel.newPerson();
        person.eSet(testModel.age, 12);
        person.eSet(testModel.name, "Ada");
        testModel.resource.getContents().add(person);

        EvlValidationException exception = assertThrows(
                EvlValidationException.class,
                () -> new EpsilonEvlValidator().validate(new EvlValidationRequest(
                        evlRoot,
                        List.of(Path.of("broken.evl"), Path.of("valid.evl")),
                        List.of(ResourceEvlModelConfiguration.readOnly(
                                "M", testModel.resource, List.of(testModel.ePackage))),
                        true)));

        assertEquals(EvlValidationStatus.FAILED, exception.getReport().status());
        assertEquals(2, exception.getReport().moduleReports().size());
        assertTrue(exception.getReport().diagnostics().stream()
                .anyMatch(d -> d.phase() == ValidationPhase.PARSE));
        assertTrue(exception.getReport().violations().stream()
                .anyMatch(v -> "PersonMustBeAdult".equals(v.constraintName())));
    }

    /**
     * Ensures runtime failures include the EVL execution phase, source location, and failure
     * reason.
     *
     * @throws Exception when fixture writing or validation fails
     */
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

    /**
     * Verifies file-backed EMF validation reports all structural model-loading errors for missing
     * required attributes.
     *
     * @throws Exception when metamodel/model fixtures cannot be written
     */
    @Test
    void fileModelValidationReportsAllStructuralDiagnostics() throws Exception {
        Path evlFile = tempDir.resolve("structural.evl");
        Files.writeString(evlFile, """
                context M!Person {
                  constraint AlwaysPasses {
                    check : true
                  }
                }
                """);
        EcoreFactory factory = EcoreFactory.eINSTANCE;
        EPackage ePackage = factory.createEPackage();
        ePackage.setName("requiredmodel");
        ePackage.setNsPrefix("requiredmodel");
        ePackage.setNsURI("urn:test:requiredmodel");
        EClass person = factory.createEClass();
        person.setName("Person");
        EAttribute firstName = factory.createEAttribute();
        firstName.setName("firstName");
        firstName.setEType(EcorePackage.Literals.ESTRING);
        firstName.setLowerBound(1);
        EAttribute lastName = factory.createEAttribute();
        lastName.setName("lastName");
        lastName.setEType(EcorePackage.Literals.ESTRING);
        lastName.setLowerBound(1);
        person.getEStructuralFeatures().add(firstName);
        person.getEStructuralFeatures().add(lastName);
        ePackage.getEClassifiers().add(person);

        Path metamodelFile = tempDir.resolve("required.ecore");
        Path modelFile = tempDir.resolve("required.xmi");
        ResourceSet resourceSet = new ResourceSetImpl();
        resourceSet.getResourceFactoryRegistry().getExtensionToFactoryMap()
                .put("ecore", new EcoreResourceFactoryImpl());
        resourceSet.getResourceFactoryRegistry().getExtensionToFactoryMap()
                .put("xmi", new XMIResourceFactoryImpl());
        Resource metamodelResource = resourceSet.createResource(
                URI.createFileURI(metamodelFile.toString()));
        metamodelResource.getContents().add(ePackage);
        metamodelResource.save(Map.of());
        Resource modelResource = resourceSet.createResource(
                URI.createFileURI(modelFile.toString()));
        modelResource.getContents().add(EcoreUtil.create(person));
        modelResource.save(Map.of());

        EvlValidationException exception = assertThrows(
                EvlValidationException.class,
                () -> new EpsilonEvlValidator().validate(
                        EvlValidationRequest.forRoot(
                                evlFile,
                                List.of(FileEvlModelConfiguration.readOnly(
                                        "M", List.of(), modelFile, List.of(metamodelFile))),
                                true)));

        long structuralErrors = exception.getReport().diagnostics().stream()
                .filter(d -> d.phase() == ValidationPhase.MODEL_LOADING
                        && d.severity() == ValidationSeverity.ERROR)
                .count();
        assertTrue(structuralErrors >= 2, exception.getReport().diagnostics().toString());
    }

    /**
     * Builds a minimal in-memory Ecore model with public and sensitive Person attributes for
     * validation tests.
     *
     * @return configured in-memory test model
     */
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

        EAttribute secretToken = factory.createEAttribute();
        secretToken.setName("secretToken");
        secretToken.setEType(EcorePackage.Literals.ESTRING);
        person.getEStructuralFeatures().add(secretToken);

        ePackage.getEClassifiers().add(person);
        Resource resource = new ResourceImpl(URI.createURI("memory:/people.xmi"));
        return new TestModel(ePackage, resource, person, name, age, secretToken);
    }

    /**
     * In-memory Ecore fixture used by EVL validator tests.
     *
     * @param ePackage    package containing the test classifier
     * @param resource    resource receiving test instances
     * @param person      Person EClass
     * @param name        public name attribute
     * @param age         non-exposed age attribute
     * @param secretToken sensitive attribute used to verify diagnostic filtering
     */
    private record TestModel(
            EPackage ePackage,
            Resource resource,
            EClass person,
            EAttribute name,
            EAttribute age,
            EAttribute secretToken) {

        /**
         * Creates a new Person instance from the fixture metamodel.
         *
         * @return new Person EObject
         */
        EObject newPerson() {
            return EcoreUtil.create(person);
        }
    }
}
