package io.mehdieidi.varka.backend.api.contract;

import static org.assertj.core.api.Assertions.assertThat;

import io.mehdieidi.varka.backend.support.BackendPostgresTestContainer;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** Smoke tests for critical REST flows against a real PostgreSQL-backed application context. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ApiSmokeContractTest {

  @DynamicPropertySource
  static void registerPostgres(DynamicPropertyRegistry registry) {
    BackendPostgresTestContainer.registerDataSourceProperties(registry);
  }

  @LocalServerPort private int port;

  @Autowired private TestRestTemplate restTemplate;

  @Test
  void healthEndpointIsPublic() {
    ResponseEntity<Map> response = restTemplate.getForEntity(url("/api/health"), Map.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).containsEntry("status", "UP");
  }

  @Test
  void authProjectAndModelLifecycle() {
    String email = "contract-" + System.nanoTime() + "@example.com";
    String password = "Contract-Test-123!";

    ResponseEntity<Map> register =
        restTemplate.postForEntity(
            url("/api/auth/register"),
            Map.of("email", email, "password", password, "displayName", "Contract Tester"),
            Map.class);
    assertThat(register.getStatusCode()).isEqualTo(HttpStatus.OK);
    String token = (String) register.getBody().get("token");
    assertThat(token).isNotBlank();

    HttpHeaders authHeaders = authHeaders(token);

    ResponseEntity<Map> me =
        restTemplate.exchange(
            url("/api/auth/me"), HttpMethod.GET, new HttpEntity<>(authHeaders), Map.class);
    assertThat(me.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(me.getBody()).containsEntry("email", email);

    ResponseEntity<Map> project =
        restTemplate.exchange(
            url("/api/projects"),
            HttpMethod.POST,
            new HttpEntity<>(
                Map.of("name", "Contract Project", "description", "API smoke test"), authHeaders),
            Map.class);
    assertThat(project.getStatusCode()).isEqualTo(HttpStatus.OK);
    String projectId = (String) project.getBody().get("id");

    ResponseEntity<Map> model =
        restTemplate.exchange(
            url("/api/cim"),
            HttpMethod.POST,
            new HttpEntity<>(
                Map.of(
                    "projectId",
                    projectId,
                    "name",
                    "Contract Model",
                    "model",
                    Map.of("eClass", "CIMModel", "name", "Contract Model")),
                authHeaders),
            Map.class);
    assertThat(model.getStatusCode()).isEqualTo(HttpStatus.OK);
    String modelId = (String) model.getBody().get("id");

    ResponseEntity<Map> storedModel =
        restTemplate.exchange(
            url("/api/cim/" + modelId), HttpMethod.GET, new HttpEntity<>(authHeaders), Map.class);
    assertThat(storedModel.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(storedModel.getBody()).containsEntry("id", modelId);

    ResponseEntity<Map> modelingConfig =
        restTemplate.exchange(
            url("/api/modeling/config"), HttpMethod.GET, new HttpEntity<>(authHeaders), Map.class);
    assertThat(modelingConfig.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(modelingConfig.getBody()).isNotNull();
  }

  @Test
  void actuatorHealthAndPrometheusEndpointsRespond() {
    ResponseEntity<String> readiness =
        restTemplate.getForEntity(url("/actuator/health/readiness"), String.class);
    assertThat(readiness.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(readiness.getBody()).contains("UP");

    ResponseEntity<String> prometheus =
        restTemplate.getForEntity(url("/actuator/prometheus"), String.class);
    assertThat(prometheus.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(prometheus.getBody()).contains("jvm_memory_used_bytes");
  }

  private HttpHeaders authHeaders(String token) {
    HttpHeaders headers = new HttpHeaders();
    headers.set("X-Auth-Token", token);
    headers.setContentType(MediaType.APPLICATION_JSON);
    return headers;
  }

  private String url(String path) {
    return "http://127.0.0.1:" + port + path;
  }
}
