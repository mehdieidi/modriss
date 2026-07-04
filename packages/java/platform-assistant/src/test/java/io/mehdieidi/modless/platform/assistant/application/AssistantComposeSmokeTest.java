package io.mehdieidi.modless.platform.assistant.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.mehdieidi.modless.platform.assistant.support.TestEnvFiles;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Opt-in smoke test against a running docker-compose backend stack. */
class AssistantComposeSmokeTest {

  @Test
  void composeBackendIsHealthyAndAssistantConfigIsReachable() throws Exception {
    Map<String, String> env = TestEnvFiles.load();
    assumeTrue(
        "true".equalsIgnoreCase(env.getOrDefault("MODLESS_RUN_COMPOSE_SMOKE", "")),
        "Set MODLESS_RUN_COMPOSE_SMOKE=true after `docker compose up --build`.");
    String backendUrl =
        env.getOrDefault("MODLESS_BACKEND_URL", "http://127.0.0.1:8080").replaceAll("/$", "");
    HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    HttpResponse<String> health =
        client.send(
            HttpRequest.newBuilder(URI.create(backendUrl + "/actuator/health/readiness"))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofString());
    assertEquals(200, health.statusCode(), health.body());
    assertTrue(health.body().contains("UP"), health.body());

    HttpResponse<String> modelingConfig =
        client.send(
            HttpRequest.newBuilder(URI.create(backendUrl + "/api/modeling/config"))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofString());
    assertEquals(200, modelingConfig.statusCode(), modelingConfig.body());
    assertTrue(modelingConfig.body().contains("levels"), modelingConfig.body());
  }
}
