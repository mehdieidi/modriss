package io.mehdieidi.modriss.platform.transformation.application;

import static org.junit.jupiter.api.Assertions.*;

import io.mehdieidi.modriss.platform.modeling.runtime.MdeRuntimeOptions;
import io.mehdieidi.modriss.platform.modeling.runtime.MdeRuntimePaths;
import io.mehdieidi.modriss.platform.psm.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import tools.jackson.databind.ObjectMapper;

/**
 * Explicit opt-in test for the exact production execution boundary, using a freshly built image.
 */
@EnabledIfSystemProperty(named = "modriss.psm.testImage", matches = ".+")
class PsmContainerRunnerTest {
  @Test
  void packagedWorkerExecutesTheAzurePipelineInDisposableContainers() throws Exception {
    Path repository = new MdeRuntimePaths(MdeRuntimeOptions.defaults()).repositoryRoot();
    Path example = repository.resolve("methodology/modeling-framework/samples/custom-psm-azure");
    var platform =
        new PsmPlatformPackage(
            "test",
            "project",
            "owner",
            "Azure",
            "",
            Files.readAllBytes(example.resolve("azure.ecore")),
            Files.readAllBytes(example.resolve("transformation.zip")),
            Files.readAllBytes(example.resolve("generation.zip")),
            Files.readAllBytes(example.resolve("validation.zip")),
            "main.etl",
            "main.egx",
            null,
            Instant.now());
    var runner =
        new ContainerPsmExecutionRunner(
            new ObjectMapper(), System.getProperty("modriss.psm.testImage"), Duration.ofMinutes(2));
    var inspected = runner.run(new PsmWorkerRequest("inspect", platform, null, null));
    assertTrue(inspected.valid(), String.valueOf(inspected.issues()));
    assertEquals(2, inspected.schema().size());
    var transformed =
        runner.run(
            new PsmWorkerRequest(
                "transform",
                platform,
                Files.readAllBytes(example.resolve("pim.xmi")),
                Files.readAllBytes(
                    repository.resolve(
                        "methodology/modeling-framework/metamodels/pim/pim-combined.ecore"))));
    assertTrue(transformed.valid(), String.valueOf(transformed.issues()));
    byte[] xmi = transformed.xmi().getBytes(java.nio.charset.StandardCharsets.UTF_8);
    assertTrue(runner.run(new PsmWorkerRequest("validate", platform, xmi, null)).valid());
    var generated = runner.run(new PsmWorkerRequest("generate", platform, xmi, null));
    assertTrue(generated.valid(), String.valueOf(generated.issues()));
    assertTrue(generated.files().get("azure-application.txt").contains("westeurope"));
    assertTrue(generated.files().containsKey("functions/Hello.txt"));
  }
}
