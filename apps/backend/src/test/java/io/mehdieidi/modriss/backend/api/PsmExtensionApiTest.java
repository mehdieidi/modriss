package io.mehdieidi.modriss.backend.api;

import static org.assertj.core.api.Assertions.assertThat;

import io.mehdieidi.modriss.backend.support.BackendPostgresTestContainer;
import io.mehdieidi.modriss.platform.identity.application.AuthService;
import io.mehdieidi.modriss.platform.kernel.ModelLevel;
import io.mehdieidi.modriss.platform.model.application.ModelService;
import io.mehdieidi.modriss.platform.modeling.runtime.MdeRuntimeOptions;
import io.mehdieidi.modriss.platform.modeling.runtime.MdeRuntimePaths;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.util.LinkedMultiValueMap;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Full authenticated HTTP pipeline with PostgreSQL and the production disposable runner. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
@EnabledIfSystemProperty(named = "modriss.psm.testImage", matches = ".+")
class PsmExtensionApiTest {
  @DynamicPropertySource
  static void configure(DynamicPropertyRegistry registry) {
    BackendPostgresTestContainer.registerDataSourceProperties(registry);
    registry.add("modriss.psm.runner-image", () -> System.getProperty("modriss.psm.testImage"));
  }

  @LocalServerPort int port;
  @Autowired TestRestTemplate rest;
  @Autowired ModelService models;
  @Autowired AuthService auth;

  @Test
  void uploadedAzurePackageRunsAlongsideNativeModelsAndSurvivesProjectExport() throws Exception {
    String token =
        (String)
            rest.postForEntity(url("/api/auth/guest"), Map.of(), Map.class).getBody().get("token");
    var headers = new HttpHeaders();
    headers.set("X-Auth-Token", token);
    var project =
        rest.exchange(
            url("/api/projects"),
            HttpMethod.POST,
            new HttpEntity<>(Map.of("name", "Azure HTTP test", "description", ""), headers),
            Map.class);
    assertThat(project.getStatusCode().value()).isEqualTo(200);
    String projectId = (String) project.getBody().get("id");
    String base = "/api/projects/" + projectId + "/psm";
    Path example =
        new MdeRuntimePaths(MdeRuntimeOptions.defaults())
            .repositoryRoot()
            .resolve("methodology/modeling-framework/samples/custom-psm-azure");
    byte[] pimXmi = Files.readAllBytes(example.resolve("pim.xmi"));
    var imported = models.importModel(ModelLevel.PIM, "pim.xmi", pimXmi, "xmi");
    var pim =
        models.create(
            auth.requireUser(token),
            ModelLevel.PIM,
            projectId,
            "AzureDemo",
            imported.modelJson(),
            pimXmi);

    var parts = new LinkedMultiValueMap<String, Object>();
    parts.add("name", "Azure");
    parts.add("description", "HTTP integration example");
    parts.add("transformationEntry", "main.etl");
    parts.add("generationEntry", "main.egx");
    for (var entry :
        Map.of(
                "ecore",
                "azure.ecore",
                "transformation",
                "transformation.zip",
                "generation",
                "generation.zip",
                "validation",
                "validation.zip")
            .entrySet()) {
      parts.add(
          entry.getKey(),
          new ByteArrayResource(Files.readAllBytes(example.resolve(entry.getValue()))) {
            @Override
            public String getFilename() {
              return entry.getValue();
            }
          });
    }
    var multipart = new HttpHeaders();
    multipart.addAll(headers);
    multipart.setContentType(MediaType.MULTIPART_FORM_DATA);
    var upload =
        rest.exchange(
            url(base + "/packages"),
            HttpMethod.POST,
            new HttpEntity<>(parts, multipart),
            Map.class);
    assertThat(upload.getStatusCode().value())
        .withFailMessage(String.valueOf(upload.getBody()))
        .isEqualTo(201);
    String packageId = (String) upload.getBody().get("id");
    assertThat(upload.getBody()).doesNotContainKeys("ecore", "transformationZip");
    var generated =
        rest.exchange(
            url(base + "/branches"),
            HttpMethod.POST,
            new HttpEntity<>(
                Map.of(
                    "packageId",
                    packageId,
                    "sourcePimId",
                    pim.id(),
                    "expectedRevision",
                    pim.revision(),
                    "name",
                    "Azure branch"),
                headers),
            Map.class);
    assertThat(generated.getStatusCode().value())
        .withFailMessage(String.valueOf(generated.getBody()))
        .isEqualTo(201);
    String branchId = (String) generated.getBody().get("id");
    String branchPath = base + "/branches/" + branchId;
    var schema =
        rest.exchange(
            url(branchPath + "/schema"), HttpMethod.GET, new HttpEntity<>(headers), Map.class);
    assertThat(schema.getStatusCode().value()).isEqualTo(200);
    assertThat((java.util.List<?>) schema.getBody().get("schema")).hasSize(2);
    var artifacts =
        rest.exchange(
            url(branchPath + "/artifacts"),
            HttpMethod.POST,
            new HttpEntity<>(Map.of("expectedRevision", 1), headers),
            Map.class);
    assertThat(artifacts.getStatusCode().value())
        .withFailMessage(String.valueOf(artifacts.getBody()))
        .isEqualTo(200);
    assertThat(((Map<?, ?>) artifacts.getBody().get("files")).containsKey("azure-application.txt"))
        .isTrue();
    String changedXmi =
        ((String) generated.getBody().get("xmi")).replace("nodejs20", "unsupported");
    var updated =
        rest.exchange(
            url(branchPath),
            HttpMethod.PUT,
            new HttpEntity<>(
                Map.of("name", "Azure branch", "xmi", changedXmi, "expectedRevision", 1), headers),
            Map.class);
    assertThat(updated.getStatusCode().value()).isEqualTo(200);
    var validation =
        rest.exchange(
            url(branchPath + "/validate"), HttpMethod.POST, new HttpEntity<>(headers), Map.class);
    assertThat(validation.getStatusCode().value()).isEqualTo(200);
    assertThat(validation.getBody()).containsEntry("valid", false);
    var stale =
        rest.exchange(
            url(branchPath + "/artifacts"),
            HttpMethod.POST,
            new HttpEntity<>(Map.of("expectedRevision", 1), headers),
            Map.class);
    assertThat(stale.getStatusCode().value()).isEqualTo(409);

    var exported =
        rest.exchange(
            url("/api/projects/" + projectId + "/download"),
            HttpMethod.GET,
            new HttpEntity<>(headers),
            byte[].class);
    assertThat(exported.getStatusCode().value()).isEqualTo(200);
    var names = new java.util.ArrayList<String>();
    try (var zip = new ZipInputStream(new ByteArrayInputStream(exported.getBody()))) {
      java.util.zip.ZipEntry entry;
      while ((entry = zip.getNextEntry()) != null) names.add(entry.getName());
    }
    assertThat(names)
        .contains(
            "modriss-project/psm-extensions/branches/" + branchId + ".xmi",
            "modriss-project/psm-extensions/packages/" + packageId + "/transformation.zip");
    String otherToken =
        (String)
            rest.postForEntity(url("/api/auth/guest"), Map.of(), Map.class).getBody().get("token");
    var otherHeaders = new HttpHeaders();
    otherHeaders.set("X-Auth-Token", otherToken);
    var denied =
        rest.exchange(
            url(base + "/packages"), HttpMethod.GET, new HttpEntity<>(otherHeaders), Map.class);
    assertThat(denied.getStatusCode().value()).isEqualTo(403);
  }

  private String url(String path) {
    return "http://127.0.0.1:" + port + path;
  }
}
