package io.mehdieidi.modriss.platform.transformation.application;

import static org.junit.jupiter.api.Assertions.*;

import io.mehdieidi.modriss.platform.kernel.ModelLevel;
import io.mehdieidi.modriss.platform.kernel.PlatformException;
import io.mehdieidi.modriss.platform.modeling.metamodel.FileMetamodelResolver;
import io.mehdieidi.modriss.platform.modeling.runtime.MdeRuntimeOptions;
import io.mehdieidi.modriss.platform.modeling.runtime.MdeRuntimePaths;
import io.mehdieidi.modriss.platform.psm.*;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;

/** Uses real Ecore, ETL, EGX/EGL and EVL engines against the minimal Azure upload fixture. */
@ResourceLock("epsilon-runtime")
class PsmExtensionServiceTest {
  @TempDir Path temp;
  private final Path repository =
      new MdeRuntimePaths(MdeRuntimeOptions.defaults()).repositoryRoot();
  private final Path example =
      repository.resolve("methodology/modeling-framework/samples/custom-psm-azure");

  private PsmExtensionService extensions(PlatformTestFixtures.ServiceStack services) {
    return new PsmExtensionService(
        services.store(),
        services.projects(),
        services.models(),
        services.artifacts(),
        new FileMetamodelResolver(new MdeRuntimePaths(MdeRuntimeOptions.defaults())),
        request -> {
          try {
            return PsmPackageWorker.execute(request);
          } catch (Exception ex) {
            throw new IllegalStateException(ex);
          }
        });
  }

  private byte[] archive(String kind) throws Exception {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ZipOutputStream zip = new ZipOutputStream(bytes);
        var paths = Files.walk(example.resolve(kind))) {
      for (Path file : paths.filter(Files::isRegularFile).sorted().toList()) {
        zip.putNextEntry(
            new ZipEntry(example.resolve(kind).relativize(file).toString().replace('\\', '/')));
        zip.write(Files.readAllBytes(file));
        zip.closeEntry();
      }
    }
    return bytes.toByteArray();
  }

  private PsmPlatformPackage upload(
      PsmExtensionService extensions, PlatformTestFixtures.AuthenticatedContext context)
      throws Exception {
    return extensions.upload(
        context.user(),
        context.project().id(),
        "Azure",
        "Minimal Azure fixture",
        Files.readAllBytes(example.resolve("azure.ecore")),
        archive("transformation"),
        archive("generation"),
        archive("validation"),
        "main.etl",
        "main.egx");
  }

  @Test
  void realAzurePipelinePreservesParallelBranchesAndValidatesOnlyOnRequest() throws Exception {
    var services = PlatformTestFixtures.createServices(temp);
    var owner =
        PlatformTestFixtures.registerOwner(services, "azure@example.com", "Azure", "Multi-cloud");
    var extensions = extensions(services);
    var platform = upload(extensions, owner);
    // A second language can reuse class names without changing an existing package's namespace.
    var secondPlatform =
        extensions.upload(
            owner.user(),
            owner.project().id(),
            "Alternate cloud",
            "",
            Files.readString(example.resolve("azure.ecore"))
                .replace(
                    "https://modriss.org/examples/azure/1.0",
                    "https://modriss.org/examples/alternate/1.0")
                .getBytes(StandardCharsets.UTF_8),
            archive("transformation"),
            archive("generation"),
            null,
            "main.etl",
            "main.egx");
    byte[] pimXmi = Files.readAllBytes(example.resolve("pim.xmi"));
    var imported = services.models().importModel(ModelLevel.PIM, "pim.xmi", pimXmi, "xmi");
    var pim =
        services
            .models()
            .create(
                owner.user(),
                ModelLevel.PIM,
                owner.project().id(),
                "Hello",
                imported.modelJson(),
                pimXmi);
    var first =
        extensions.transform(
            owner.user(),
            owner.project().id(),
            platform.id(),
            pim.id(),
            pim.revision(),
            "Azure West");
    assertTrue(first.xmi().contains("AzureApplication"));
    assertTrue(first.xmi().contains("Hello"));
    assertEquals(pim.revision(), first.sourcePimRevision());
    assertEquals(64, first.sourcePimHash().length());
    var second =
        extensions.transform(
            owner.user(),
            owner.project().id(),
            secondPlatform.id(),
            pim.id(),
            pim.revision(),
            "Alternate cloud");
    assertTrue(second.xmi().contains("https://modriss.org/examples/alternate/1.0"));
    assertTrue(first.xmi().contains("https://modriss.org/examples/azure/1.0"));
    assertNotEquals(first.id(), second.id());
    assertEquals(2, extensions.branches(owner.user(), owner.project().id()).size());
    assertEquals(
        409,
        assertThrows(
                PlatformException.class,
                () ->
                    extensions.transform(
                        owner.user(),
                        owner.project().id(),
                        platform.id(),
                        pim.id(),
                        pim.revision(),
                        "Duplicate"))
            .status());
    assertTrue(
        extensions.inspectBranch(owner.user(), owner.project().id(), first.id(), true).valid());
    var artifact = extensions.generate(owner.user(), owner.project().id(), first.id(), 1);
    assertTrue(artifact.files().containsKey("azure-application.txt"));
    assertTrue(artifact.files().get("azure-application.txt").contains("westeurope"));
    assertTrue(artifact.files().containsKey("functions/Hello.txt"));
    assertEquals(first.id(), artifact.modelJson().path("psmBranchId").asText());
    assertEquals(1, services.artifacts().list(owner.user(), owner.project().id()).size());

    // Runtime value conforms to Ecore but violates EVL; ordinary saves must still succeed.
    var changed =
        extensions.update(
            owner.user(),
            owner.project().id(),
            first.id(),
            1,
            first.name(),
            first.xmi().replace("nodejs20", "unsupported"));
    assertEquals(2, changed.revision());
    var validation = extensions.inspectBranch(owner.user(), owner.project().id(), first.id(), true);
    assertFalse(validation.valid());
    assertTrue(validation.issues().stream().anyMatch(issue -> issue.contains("SupportedRuntime")));
    assertEquals(
        second.xmi(), extensions.branch(owner.user(), owner.project().id(), second.id()).xmi());
    assertEquals(1, extensions.branch(owner.user(), owner.project().id(), second.id()).revision());
    assertEquals(1, artifact.modelJson().path("sourceModelRevision").asLong());
    assertEquals(
        409,
        assertThrows(
                PlatformException.class,
                () -> extensions.generate(owner.user(), owner.project().id(), first.id(), 1))
            .status());
    assertEquals(
        409,
        assertThrows(
                PlatformException.class,
                () ->
                    extensions.update(
                        owner.user(), owner.project().id(), first.id(), 1, "stale", first.xmi()))
            .status());

    var invalid = extensions.inspectBranch(owner.user(), owner.project().id(), first.id(), false);
    assertTrue(invalid.valid());
    var failure =
        assertThrows(
            PlatformException.class,
            () ->
                extensions.update(
                    owner.user(),
                    owner.project().id(),
                    first.id(),
                    2,
                    "invalid",
                    changed.xmi().replace("runtime=\"unsupported\"", "")));
    assertEquals(422, failure.status());
    assertEquals(2, extensions.branch(owner.user(), owner.project().id(), first.id()).revision());
    assertFalse(
        services
            .projects()
            .get(owner.user(), owner.project().id())
            .activeModelIds()
            .containsKey("psm"));

    var editorModel =
        extensions.editorModel(owner.user(), owner.project().id(), first.id()).deepCopy();
    ((tools.jackson.databind.node.ObjectNode) editorModel.path("functions").get(0))
        .put("runtime", "nodejs20");
    var withGeometry = (tools.jackson.databind.node.ObjectNode) editorModel;
    withGeometry.putArray("views").addObject().put("id", "azure-view");
    var editorSaved =
        extensions.saveEditorModel(
            owner.user(),
            owner.project().id(),
            platform.id(),
            first.id(),
            2,
            "Azure canvas",
            editorModel);
    assertEquals(3, editorSaved.revision());
    assertTrue(editorSaved.xmi().contains("nodejs20"));
    assertEquals("azure-view", editorSaved.modelJson().path("views").get(0).path("id").asText());
    assertEquals(
        409,
        assertThrows(
                PlatformException.class,
                () ->
                    extensions.saveEditorModel(
                        owner.user(),
                        owner.project().id(),
                        platform.id(),
                        null,
                        0,
                        "Duplicate editor",
                        editorModel))
            .status());
    var regenerated =
        extensions.transform(
            owner.user(),
            owner.project().id(),
            platform.id(),
            pim.id(),
            pim.revision(),
            "Azure canvas",
            3L);
    assertEquals(first.id(), regenerated.id());
    assertEquals(4, regenerated.revision());
    assertEquals(2, extensions.branches(owner.user(), owner.project().id()).size());
  }

  @Test
  void ecoreAloneSupportsManualEditorAndProjectNotationWithoutGenerationScripts() throws Exception {
    var services = PlatformTestFixtures.createServices(temp);
    var owner =
        PlatformTestFixtures.registerOwner(
            services, "manual@example.com", "Manual", "Manual cloud");
    var extensions = extensions(services);
    var platform =
        extensions.upload(
            owner.user(),
            owner.project().id(),
            "Manual Azure",
            "",
            Files.readAllBytes(example.resolve("azure.ecore")),
            null,
            null,
            null,
            "",
            "");
    var json =
        services
            .store()
            .objectMapper()
            .readTree(
                """
{"eClass":"AzureApplication","id":"application","name":"Manual","location":"westeurope","functions":[]}
""");
    var saved =
        extensions.saveEditorModel(
            owner.user(), owner.project().id(), platform.id(), null, 0, "Manual model", json);
    assertEquals(platform.id(), saved.id());
    assertTrue(saved.xmi().contains("westeurope"));
    assertTrue(
        extensions.inspectBranch(owner.user(), owner.project().id(), saved.id(), true).valid());
    assertEquals(
        400,
        assertThrows(
                PlatformException.class,
                () -> extensions.generate(owner.user(), owner.project().id(), saved.id(), 1))
            .status());
    assertEquals(
        400,
        assertThrows(
                PlatformException.class,
                () ->
                    extensions.transform(
                        owner.user(),
                        owner.project().id(),
                        platform.id(),
                        "missing-pim",
                        1,
                        "Manual"))
            .status());
    var syntax =
        services
            .store()
            .objectMapper()
            .readTree(
                """
{"version":1,"rootType":"AzureApplication","paletteGroups":[{"id":"compute","name":"Compute"}],
"elements":[{"type":"AzureFunction","icon":"functions","group":"compute","role":"node"}],
"views":[{"id":"cloud","name":"Cloud model","types":["AzureFunction"]}]}
""");
    var customized =
        extensions.updateVisualSyntax(owner.user(), owner.project().id(), platform.id(), syntax);
    assertEquals(
        "functions", customized.visualSyntax().path("elements").get(0).path("icon").asText());
    assertArrayEquals(platform.ecore(), customized.ecore());
    ((tools.jackson.databind.node.ObjectNode) syntax).put("rootType", "AzureFunction");
    assertEquals(
        409,
        assertThrows(
                PlatformException.class,
                () ->
                    extensions.updateVisualSyntax(
                        owner.user(), owner.project().id(), platform.id(), syntax))
            .status());
  }

  @Test
  void packageAndBranchAccessAreScopedToTheirProject() throws Exception {
    var services = PlatformTestFixtures.createServices(temp);
    var owner =
        PlatformTestFixtures.registerOwner(services, "owner@example.com", "Owner", "Private");
    var other =
        PlatformTestFixtures.registerOwner(services, "other@example.com", "Other", "Other project");
    var extensions = extensions(services);
    var platform = upload(extensions, owner);
    assertEquals(
        403,
        assertThrows(
                PlatformException.class,
                () -> extensions.packages(other.user(), owner.project().id()))
            .status());
    assertEquals(
        404,
        assertThrows(
                PlatformException.class,
                () -> extensions.platform(other.user(), other.project().id(), platform.id()))
            .status());
    assertEquals(0, extensions.packages(other.user(), other.project().id()).size());
  }

  @Test
  void archivesRejectTraversalDuplicatesWrongTypesAndExpansionBombs() throws Exception {
    for (String path :
        List.of(
            "../escape.etl",
            "/escape.etl",
            "C:/escape.etl",
            "a\\escape.etl",
            "a/../escape.etl",
            "a//escape.etl",
            "file.exe")) {
      assertThrows(
          PlatformException.class,
          () ->
              PsmPackageArchive.read(
                  zip(path, "rule X {}".getBytes(StandardCharsets.UTF_8)), Set.of("etl")),
          path);
    }
    assertThrows(
        PlatformException.class, () -> PsmPackageArchive.read(new byte[] {1, 2}, Set.of("etl")));
    byte[] bomb = zip("bomb.etl", new byte[PsmPackageArchive.MAX_EXPANDED_BYTES + 1]);
    assertThrows(PlatformException.class, () -> PsmPackageArchive.read(bomb, Set.of("etl")));
  }

  @Test
  void missingEntryAndInvalidMetamodelNeverPersistAPackage() throws Exception {
    var services = PlatformTestFixtures.createServices(temp);
    var owner =
        PlatformTestFixtures.registerOwner(services, "invalid@example.com", "Invalid", "Invalid");
    var extensions = extensions(services);
    assertThrows(
        PlatformException.class,
        () ->
            extensions.upload(
                owner.user(),
                owner.project().id(),
                "Broken",
                "",
                Files.readAllBytes(example.resolve("azure.ecore")),
                archive("transformation"),
                archive("generation"),
                null,
                "missing.etl",
                "main.egx"));
    assertThrows(
        PlatformException.class,
        () ->
            extensions.upload(
                owner.user(),
                owner.project().id(),
                "Broken Ecore",
                "",
                "<not-ecore/>".getBytes(StandardCharsets.UTF_8),
                archive("transformation"),
                archive("generation"),
                null,
                "main.etl",
                "main.egx"));
    assertEquals(0, extensions.packages(owner.user(), owner.project().id()).size());
  }

  private static byte[] zip(String name, byte[] content) throws Exception {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
      zip.putNextEntry(new ZipEntry(name));
      zip.write(content);
      zip.closeEntry();
    }
    return bytes.toByteArray();
  }
}
