package io.mehdieidi.modless.platform.assistant.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.mehdieidi.modless.platform.assistant.support.TestEnvFiles;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Full-stack live E2E against docker-compose backend: auth, sessions, attachments, and orchestrator
 * turns for CIM source documents, PIM mutations, and PSM explain/create flows.
 */
class AssistantComposeLiveE2ETest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  @org.junit.jupiter.api.Timeout(900)
  @Test
  void composeStackHandlesRealModelingConversations() throws Exception {
    Map<String, String> env = TestEnvFiles.load();
    assumeTrue(
        "true".equalsIgnoreCase(env.getOrDefault("MODLESS_RUN_COMPOSE_LIVE_E2E", "")),
        "Set MODLESS_RUN_COMPOSE_LIVE_E2E=true with docker compose up and"
            + " MODLESS_AI_ENABLED=true.");
    assumeTrue(
        "true".equalsIgnoreCase(env.getOrDefault("MODLESS_AI_ENABLED", "")),
        "MODLESS_AI_ENABLED must be true in .env for compose live E2E.");

    String backendUrl =
        env.getOrDefault("MODLESS_BACKEND_URL", "http://127.0.0.1:8080").replaceAll("/$", "");
    HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    assumeBackendReady(client, backendUrl);

    String token = registerUser(client, backendUrl);
    String projectId = createProject(client, backendUrl, token);

    runCimUserStoryScenario(client, backendUrl, token, projectId);
    runPimConversationScenario(client, backendUrl, token, projectId);
    runPsmScenario(client, backendUrl, token, projectId);
  }

  private void assumeBackendReady(HttpClient client, String backendUrl) throws Exception {
    HttpResponse<String> health =
        client.send(
            HttpRequest.newBuilder(URI.create(backendUrl + "/actuator/health/readiness"))
                .timeout(Duration.ofSeconds(15))
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofString());
    assumeTrue(
        health.statusCode() == 200 && health.body().contains("UP"),
        "Compose backend is not ready at " + backendUrl);
  }

  private String registerUser(HttpClient client, String backendUrl) throws Exception {
    String email = "live-e2e-" + System.nanoTime() + "@example.com";
    String body =
        MAPPER.writeValueAsString(
            Map.of("email", email, "password", "Live-E2E-Test-123!", "displayName", "Live E2E"));
    HttpResponse<String> response =
        client.send(
            HttpRequest.newBuilder(URI.create(backendUrl + "/api/auth/register"))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build(),
            HttpResponse.BodyHandlers.ofString());
    assertEquals(200, response.statusCode(), response.body());
    return MAPPER.readTree(response.body()).path("token").asText();
  }

  private String createProject(HttpClient client, String backendUrl, String token)
      throws Exception {
    String body =
        MAPPER.writeValueAsString(
            Map.of("name", "Live E2E Project", "description", "Assistant compose live E2E"));
    HttpResponse<String> response =
        client.send(
            authJsonRequest(backendUrl + "/api/projects", token, body),
            HttpResponse.BodyHandlers.ofString());
    assertEquals(200, response.statusCode(), response.body());
    return MAPPER.readTree(response.body()).path("id").asText();
  }

  private void runCimUserStoryScenario(
      HttpClient client, String backendUrl, String token, String projectId) throws Exception {
    String sessionId = createSession(client, backendUrl, token, projectId, "cim", "Clinic CIM");
    Path source =
        Path.of("../../../mde/samples/document-to-cim/community-clinic-user-stories.md")
            .normalize()
            .toAbsolutePath();
    String attachment = Files.readString(source);

    JsonNode response =
        sendMessage(
            client,
            backendUrl,
            token,
            sessionId,
            """
            Transform this user story requirements document into a complete draft CIM model.
            Include actors, goals, user stories, business rules, domain terms, risks, assumptions,
            commands, events, and relationships grounded in the source.
            """,
            "community-clinic-user-stories.md",
            attachment,
            null,
            null);

    response = resolveClarificationIfNeeded(client, backendUrl, token, sessionId, response);
    assertApplied(response, "cim-user-stories");
    String modelId = response.path("modelId").asText();
    JsonNode model = fetchModel(client, backendUrl, token, "cim", modelId);
    assertTrue(countModelElements(model) >= 4, () -> "Expected CIM model content, got: " + model);
  }

  private void runPimConversationScenario(
      HttpClient client, String backendUrl, String token, String projectId) throws Exception {
    String sessionId = createSession(client, backendUrl, token, projectId, "pim", "Payment PIM");

    JsonNode createResponse =
        sendMessage(
            client,
            backendUrl,
            token,
            sessionId,
            """
            Create a payment processing serverless model with one workflow named Payment Workflow,
            one API named Payments API, one function named Capture Payment, and one event channel
            named Payment Captured.
            """,
            null,
            null,
            null,
            null);
    assertApplied(createResponse, "pim-create");
    String modelId = createResponse.path("modelId").asText();
    Long revision = createResponse.path("revision").asLong();

    JsonNode extendResponse =
        sendMessage(
            client,
            backendUrl,
            token,
            sessionId,
            "Add refund processing to the payment workflow with a Refund Payment function and a "
                + "Payment Refunded event channel.",
            null,
            null,
            modelId,
            revision);
    assertApplied(extendResponse, "pim-extend-refund");

    JsonNode explainResponse =
        sendMessage(
            client,
            backendUrl,
            token,
            sessionId,
            "Explain how the payment workflow, APIs, functions, and events interact.",
            null,
            null,
            extendResponse.path("modelId").asText(),
            extendResponse.path("revision").asLong());
    assertTrue(
        "EXPLAINED".equals(explainResponse.path("workflowState").asText())
            || explainResponse.path("assistantMessage").asText().length() > 40,
        () -> "Expected explain response, got: " + explainResponse);
  }

  private void runPsmScenario(HttpClient client, String backendUrl, String token, String projectId)
      throws Exception {
    String sessionId = createSession(client, backendUrl, token, projectId, "psm", "Ticketing PSM");

    JsonNode createResponse =
        sendMessage(
            client,
            backendUrl,
            token,
            sessionId,
            """
            Create an AWS serverless PSM skeleton for a ticketing platform with API Gateway,
            Lambda, DynamoDB, and EventBridge resources.
            """,
            null,
            null,
            null,
            null);
    assertApplied(createResponse, "psm-create");

    JsonNode explainResponse =
        sendMessage(
            client,
            backendUrl,
            token,
            sessionId,
            "Explain deployment and operational risks for this AWS serverless PSM.",
            null,
            null,
            createResponse.path("modelId").asText(),
            createResponse.path("revision").asLong());
    assertTrue(
        "EXPLAINED".equals(explainResponse.path("workflowState").asText())
            || explainResponse.path("assistantMessage").asText().length() > 40,
        () -> "Expected PSM explain response, got: " + explainResponse);
  }

  private String createSession(
      HttpClient client,
      String backendUrl,
      String token,
      String projectId,
      String level,
      String modelName)
      throws Exception {
    String body =
        MAPPER.writeValueAsString(
            Map.of(
                "projectId", projectId,
                "modelType", level,
                "modelName", modelName,
                "forceNew", true));
    HttpResponse<String> response =
        client.send(
            authJsonRequest(backendUrl + "/api/chatbot/sessions", token, body),
            HttpResponse.BodyHandlers.ofString());
    assertEquals(200, response.statusCode(), response.body());
    return MAPPER.readTree(response.body()).path("sessionId").asText();
  }

  private JsonNode sendMessage(
      HttpClient client,
      String backendUrl,
      String token,
      String sessionId,
      String message,
      String attachmentName,
      String attachmentContent,
      String modelId,
      Long revision)
      throws Exception {
    var payload =
        MAPPER
            .createObjectNode()
            .put("message", message)
            .put("idempotencyKey", UUID.randomUUID().toString());
    if (modelId != null && !modelId.isBlank()) {
      payload.put("modelId", modelId);
    }
    if (revision != null) {
      payload.put("revision", revision);
    }
    if (attachmentName != null && attachmentContent != null) {
      payload.put("attachmentName", attachmentName);
      payload.put("attachmentContent", attachmentContent);
    }

    HttpResponse<String> response =
        client.send(
            authJsonRequest(
                backendUrl + "/api/chatbot/sessions/" + sessionId + "/messages",
                token,
                MAPPER.writeValueAsString(payload)),
            HttpResponse.BodyHandlers.ofString());
    assertEquals(200, response.statusCode(), response.body());
    return MAPPER.readTree(response.body());
  }

  private JsonNode fetchModel(
      HttpClient client, String backendUrl, String token, String level, String modelId)
      throws Exception {
    HttpResponse<String> response =
        client.send(
            HttpRequest.newBuilder(URI.create(backendUrl + "/api/" + level + "/" + modelId))
                .timeout(Duration.ofMinutes(2))
                .header("X-Auth-Token", token)
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofString());
    assertEquals(200, response.statusCode(), response.body());
    return MAPPER.readTree(response.body()).path("model");
  }

  private JsonNode resolveClarificationIfNeeded(
      HttpClient client, String backendUrl, String token, String sessionId, JsonNode response)
      throws Exception {
    if (!"WAITING_FOR_CHOICE".equals(response.path("workflowState").asText())) {
      return response;
    }
    JsonNode choices = response.path("choices");
    if (!choices.isArray() || choices.isEmpty()) {
      return response;
    }
    String choiceId = choices.get(0).path("id").asText();
    JsonNode options = choices.get(0).path("options");
    String optionId =
        options.isArray() && !options.isEmpty() ? options.get(0).path("id").asText() : "yes";
    String body =
        MAPPER.writeValueAsString(
            Map.of(
                "choiceId", choiceId,
                "optionId", optionId,
                "answers",
                    List.of(
                        Map.of(
                            "choiceId",
                            choiceId,
                            "optionIds",
                            List.of(optionId),
                            "freeText",
                            ""))));
    HttpResponse<String> choiceResponse =
        client.send(
            authJsonRequest(
                backendUrl + "/api/chatbot/sessions/" + sessionId + "/choices", token, body),
            HttpResponse.BodyHandlers.ofString());
    assertEquals(200, choiceResponse.statusCode(), choiceResponse.body());
    return MAPPER.readTree(choiceResponse.body());
  }

  private void assertApplied(JsonNode response, String scenario) {
    assertTrue(
        "APPLIED".equals(response.path("workflowState").asText()),
        () -> scenario + " expected APPLIED but got: " + response);
  }

  private int countModelElements(JsonNode model) {
    if (model == null || model.isMissingNode()) {
      return 0;
    }
    JsonNode diagramElements = model.path("diagram").path("elements");
    if (diagramElements.isArray() && diagramElements.size() > 0) {
      return diagramElements.size();
    }
    int count = 0;
    var fields = model.fields();
    while (fields.hasNext()) {
      var entry = fields.next();
      if (entry.getValue().isArray()) {
        count += entry.getValue().size();
      }
    }
    return count;
  }

  private HttpRequest authJsonRequest(String url, String token, String body) {
    return HttpRequest.newBuilder(URI.create(url))
        .timeout(Duration.ofMinutes(10))
        .header("X-Auth-Token", token)
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(body))
        .build();
  }
}
