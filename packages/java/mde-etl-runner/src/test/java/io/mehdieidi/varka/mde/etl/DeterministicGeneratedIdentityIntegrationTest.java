package io.mehdieidi.varka.mde.etl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/** End-to-end regression coverage for repeatable IDs across completely fresh ETL targets. */
@ResourceLock("epsilon-runtime")
class DeterministicGeneratedIdentityIntegrationTest {

  private static final Path REPOSITORY_ROOT = findRepositoryRoot();

  @TempDir Path tempDir;

  @Test
  void identicalCimToPimRunsProduceIdenticalGeneratedIds() throws Exception {
    Path source = REPOSITORY_ROOT.resolve("mde/samples/cim.xmi");
    Path first = tempDir.resolve("first.pim.xmi");
    Path second = tempDir.resolve("second.pim.xmi");

    execute(CimToPimDefaults.request(REPOSITORY_ROOT, source, first, true, true));
    execute(CimToPimDefaults.request(REPOSITORY_ROOT, source, second, true, true));

    assertEquals(ids(first), ids(second));
  }

  @Test
  void identicalPimToAwsPsmRunsProduceIdenticalGeneratedIds() throws Exception {
    Path source = REPOSITORY_ROOT.resolve("mde/samples/pim.xmi");
    Path first = tempDir.resolve("first.awspsm.xmi");
    Path second = tempDir.resolve("second.awspsm.xmi");

    execute(PimToAwsPsmDefaults.request(REPOSITORY_ROOT, source, first, true, true));
    execute(PimToAwsPsmDefaults.request(REPOSITORY_ROOT, source, second, true, true));

    assertEquals(ids(first), ids(second));
  }

  @Test
  void generatedAwsPsmIdsAreUniqueEvenWhenManyTrustPoliciesAreCreated() throws Exception {
    Path source = REPOSITORY_ROOT.resolve("mde/samples/pim.xmi");
    Path target = tempDir.resolve("unique.awspsm.xmi");

    execute(PimToAwsPsmDefaults.request(REPOSITORY_ROOT, source, target, true, true));

    List<String> trustStatementIds = trustStatementIds(target);
    assertEquals(
        trustStatementIds.size(),
        new LinkedHashSet<>(trustStatementIds).size(),
        "Generated trust-policy statements must have unique stable identities.");
  }

  @Test
  void renamingCimSourceWithoutChangingItsIdPreservesPimIdentities() throws Exception {
    Path source = REPOSITORY_ROOT.resolve("mde/samples/cim.xmi");
    Path renamedSource = tempDir.resolve("renamed.cim.xmi");
    Path originalTarget = tempDir.resolve("original-name.pim.xmi");
    Path renamedTarget = tempDir.resolve("renamed.pim.xmi");
    Files.writeString(
        renamedSource,
        Files.readString(source)
            .replace(
                "name=\"ClimateReliefGrantsBusinessModel\"",
                "name=\"RenamedClimateReliefBusinessModel\""));

    execute(CimToPimDefaults.request(REPOSITORY_ROOT, source, originalTarget, true, true));
    execute(CimToPimDefaults.request(REPOSITORY_ROOT, renamedSource, renamedTarget, true, true));

    assertEquals(
        idGeneratedFrom(originalTarget, "cim-root"), idGeneratedFrom(renamedTarget, "cim-root"));
  }

  @Test
  void renamingPimSourceWithoutChangingItsIdPreservesAwsPsmIdentities() throws Exception {
    Path source = REPOSITORY_ROOT.resolve("mde/samples/pim.xmi");
    Path renamedSource = tempDir.resolve("renamed.pim.xmi");
    Path originalTarget = tempDir.resolve("original-name.awspsm.xmi");
    Path renamedTarget = tempDir.resolve("renamed.awspsm.xmi");
    Files.writeString(
        renamedSource,
        Files.readString(source)
            .replace(
                "name=\"ClimateReliefGrantsBusinessModel\"",
                "name=\"RenamedClimateReliefBusinessModel\""));

    execute(PimToAwsPsmDefaults.request(REPOSITORY_ROOT, source, originalTarget, true, true));
    execute(PimToAwsPsmDefaults.request(REPOSITORY_ROOT, renamedSource, renamedTarget, true, true));

    assertEquals(
        idGeneratedFrom(originalTarget, "ad08ea87-6316-4f38-b411-5c97247d92e1"),
        idGeneratedFrom(renamedTarget, "ad08ea87-6316-4f38-b411-5c97247d92e1"));
  }

  private void execute(EtlExecutionRequest request) throws Exception {
    EtlExecutionReport report = new EpsilonEtlExecutor().execute(request);
    assertEquals(EtlExecutionStatus.SUCCEEDED, report.status(), report.diagnostics().toString());
  }

  private Set<String> ids(Path model) throws Exception {
    return new LinkedHashSet<>(serializedIds(model));
  }

  private List<String> serializedIds(Path model) throws Exception {
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
    var document = factory.newDocumentBuilder().parse(model.toFile());
    List<String> ids = new ArrayList<>();
    collectIds(document.getDocumentElement(), ids);
    assertFalse(ids.isEmpty(), "Expected transformation-generated model element IDs.");
    return ids;
  }

  private List<String> trustStatementIds(Path model) throws Exception {
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
    var document = factory.newDocumentBuilder().parse(model.toFile());
    List<String> ids = new ArrayList<>();
    collectTrustStatementIds(document.getDocumentElement(), ids);
    assertFalse(ids.isEmpty(), "Expected generated IAM trust-policy statements.");
    return ids;
  }

  private void collectTrustStatementIds(Element element, List<String> ids) {
    if ("AssumeRole".equals(element.getAttribute("sid")) && element.hasAttribute("id")) {
      ids.add(element.getAttribute("id"));
    }
    for (Node child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
      if (child instanceof Element nested) collectTrustStatementIds(nested, ids);
    }
  }

  private void collectIds(Element element, List<String> ids) {
    if (element.hasAttribute("id")) {
      ids.add(element.getAttribute("id"));
    }
    for (Node child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
      if (child instanceof Element nested) {
        collectIds(nested, ids);
      }
    }
  }

  private String idGeneratedFrom(Path model, String sourceId) throws Exception {
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
    Element root = factory.newDocumentBuilder().parse(model.toFile()).getDocumentElement();
    String id = idGeneratedFrom(root, sourceId);
    assertNotNull(id, "Expected a generated target traced to " + sourceId);
    return id;
  }

  private String idGeneratedFrom(Element element, String sourceId) {
    if (sourceId.equals(element.getAttribute("generatedFrom")) && element.hasAttribute("id")) {
      return element.getAttribute("id");
    }
    for (Node child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
      if (child instanceof Element nested) {
        String id = idGeneratedFrom(nested, sourceId);
        if (id != null) {
          return id;
        }
      }
    }
    return null;
  }

  private static Path findRepositoryRoot() {
    Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
    while (current != null) {
      if (Files.isDirectory(current.resolve("mde/metamodels"))) {
        return current;
      }
      current = current.getParent();
    }
    throw new IllegalStateException("Could not locate repository root.");
  }
}
