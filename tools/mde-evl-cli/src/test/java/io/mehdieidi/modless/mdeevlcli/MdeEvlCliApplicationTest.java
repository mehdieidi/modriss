package io.mehdieidi.modless.mdeevlcli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import picocli.CommandLine;

/**
 * Integration tests for repository-backed EVL CLI profiles.
 */
final class MdeEvlCliApplicationTest {

    /**
     * Repository root containing validation profiles and sample models.
     */
    private static final Path REPOSITORY_ROOT = findRepositoryRoot();

    /**
     * Per-test output directory.
     */
    @TempDir
    Path tempDir;

    /**
     * Locates the repository root from the active test working directory.
     *
     * @return repository root
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
     * Verifies mandatory-violation exit behavior and JSON report generation.
     *
     * @throws Exception if the command or file assertions cannot be completed
     */
    @Test
    void psmCommandReturnsViolationExitCodeAndWritesReport() throws Exception {
        Path reportFile = tempDir.resolve("psm-validation-report.json");
        Path invalidPsm = psmWithProdApprovalDisabled();
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();

        int exitCode = new CommandLine(new MdeEvlCommand())
                .setOut(new PrintWriter(out))
                .setErr(new PrintWriter(err))
                .execute(
                        "psm",
                        "--repo-root", REPOSITORY_ROOT.toString(),
                        "--model", invalidPsm.toString(),
                        "--log-file", reportFile.toString(),
                        "--fail-on-mandatory-violations");

        assertEquals(3, exitCode);
        assertTrue(out.toString().contains("EVL validation succeeded"));
        assertTrue(err.toString().isBlank());
        String reportJson = Files.readString(reportFile);
        assertTrue(reportJson.contains("\"status\": \"SUCCEEDED\""));
        assertTrue(Pattern.compile("\"mandatoryViolationCount\"\\s*:\\s*[1-9][0-9]*")
                .matcher(reportJson)
                .find());
        assertTrue(reportJson.contains("\"constraintName\": \"ProdRequiresApproval\""));
    }

    /**
     * Creates a secure, temporary PSM fixture that violates production approval rules.
     *
     * @return path to the modified PSM fixture
     * @throws Exception if the fixture cannot be parsed or written
     */
    private Path psmWithProdApprovalDisabled() throws Exception {
        DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
        documentBuilderFactory.setNamespaceAware(true);
        // The fixture is local, but keep parser settings hardened against external XML entities.
        documentBuilderFactory.setFeature("http://apache.org/xml/features/disallow-doctype-decl",
                true);
        documentBuilderFactory.setFeature(
                "http://xml.org/sax/features/external-general-entities", false);
        documentBuilderFactory.setFeature(
                "http://xml.org/sax/features/external-parameter-entities", false);
        documentBuilderFactory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        documentBuilderFactory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");

        Document document = documentBuilderFactory.newDocumentBuilder()
                .parse(REPOSITORY_ROOT.resolve("mde/samples/psm.xmi").toFile());
        NodeList stages = document.getDocumentElement().getElementsByTagName("stages");
        boolean changed = false;
        for (int i = 0; i < stages.getLength(); i++) {
            Element stage = (Element) stages.item(i);
            if ("PROD".equals(stage.getAttribute("environmentClass"))) {
                stage.setAttribute("requiresManualApproval", "false");
                changed = true;
            }
        }
        if (!changed) {
            throw new IllegalStateException("PSM sample does not contain a PROD stage.");
        }

        Path invalidPsm = tempDir.resolve("psm-prod-approval-disabled.xmi");
        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        transformerFactory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        transformerFactory.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
        var transformer = transformerFactory.newTransformer();
        transformer.setOutputProperty(OutputKeys.ENCODING, "ASCII");
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.transform(new DOMSource(document), new StreamResult(invalidPsm.toFile()));
        return invalidPsm;
    }
}
