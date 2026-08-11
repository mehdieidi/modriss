package io.mehdieidi.varka.platform.assistant.agent;

import io.mehdieidi.varka.platform.assistant.config.AiProperties;
import io.mehdieidi.varka.platform.assistant.provider.AssistantModelProvider;
import io.mehdieidi.varka.platform.kernel.PlatformException;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Dedicated JSON-only OpenRouter transport for the isolated conceptual-instance mode. */
public final class OpenRouterConceptualModelProvider implements AssistantModelProvider {
  private static final Logger log =
      LoggerFactory.getLogger(OpenRouterConceptualModelProvider.class);
  private final AiProperties properties;
  private final ObjectMapper mapper = new ObjectMapper();

  public OpenRouterConceptualModelProvider(AiProperties properties) {
    this.properties = properties;
  }

  @Override
  public AssistantProviderMetadata metadata() {
    return new AssistantProviderMetadata(
        "openrouter-conceptual", properties.openaiCompatible().baseUrl(), "configured");
  }

  @Override
  public boolean available() {
    return !key().isBlank();
  }

  @Override
  public AssistantReply complete(AssistantPrompt prompt) {
    try {
      var body = mapper.createObjectNode();
      body.put("model", properties.model());
      body.put("temperature", 0.0);
      body.put("max_tokens", properties.maxCompletionTokens());
      // DeepSeek V4 uses an object-valued thinking switch. Boolean aliases are ignored by some
      // compatible gateways and can exhaust max_tokens before any final JSON is emitted.
      body.putObject("thinking").put("type", "disabled");
      var messages = body.putArray("messages");
      messages.addObject().put("role", "system").put("content", prompt.system());
      messages.addObject().put("role", "user").put("content", prompt.user());
      body.putObject("response_format").put("type", "json_object");
      HttpClient.Builder client =
          HttpClient.newBuilder().connectTimeout(properties.requestTimeout());
      AiProperties.Proxy proxy = properties.proxyFor("openai");
      if (proxy.enabled() && proxy.type() != AiProperties.ProxyType.DIRECT)
        client.proxy(ProxySelector.of(proxy.address()));
      HttpRequest request =
          HttpRequest.newBuilder()
              .uri(URI.create(properties.openaiCompatible().baseUrl() + "/chat/completions"))
              .version(HttpClient.Version.HTTP_1_1)
              .timeout(properties.requestTimeout())
              .header("Authorization", "Bearer " + key())
              .header("Content-Type", "application/json")
              .POST(
                  HttpRequest.BodyPublishers.ofString(
                      mapper.writeValueAsString(body), StandardCharsets.UTF_8))
              .build();
      long started = System.nanoTime();
      log.info(
          "Conceptual provider call started provider=openrouter-conceptual model={} promptChars={}"
              + " timeoutMs={}",
          properties.model(),
          prompt.system().length() + prompt.user().length(),
          properties.requestTimeout().toMillis());
      CompletableFuture<HttpResponse<String>> pending =
          client.build().sendAsync(request, HttpResponse.BodyHandlers.ofString());
      HttpResponse<String> response;
      try {
        response =
            pending.get(properties.requestTimeout().toMillis() + 1000L, TimeUnit.MILLISECONDS);
      } catch (TimeoutException ex) {
        pending.cancel(true);
        throw new PlatformException(
            504,
            "Conceptual provider request timed out after "
                + properties.requestTimeout().toSeconds()
                + " seconds.");
      } catch (InterruptedException ex) {
        pending.cancel(true);
        Thread.currentThread().interrupt();
        throw new PlatformException(499, "Conceptual provider request was interrupted.");
      } catch (ExecutionException ex) {
        Throwable cause = ex.getCause() == null ? ex : ex.getCause();
        throw new PlatformException(
            502, "Conceptual provider request failed: " + cause.getMessage(), cause);
      }
      log.info(
          "Conceptual provider call completed status={} elapsedMs={}",
          response.statusCode(),
          Duration.ofNanos(System.nanoTime() - started).toMillis());
      if (response.statusCode() / 100 != 2)
        throw new PlatformException(
            response.statusCode(),
            "OpenRouter rejected conceptual-model request: " + snippet(response.body()));
      JsonNode root = mapper.readTree(response.body());
      JsonNode contentNode = root.path("choices").path(0).path("message").path("content");
      String content = contentNode.isTextual() ? contentNode.asText() : contentParts(contentNode);
      if (content.isBlank()) {
        String finish = root.path("choices").path(0).path("finish_reason").asText("unknown");
        throw new PlatformException(
            422,
            "Conceptual provider returned no structured content (finish_reason="
                + finish
                + "). Thinking must be disabled and the response must contain JSON.");
      }
      JsonNode usage = root.path("usage");
      return new AssistantReply(
          content,
          "openrouter",
          properties.model(),
          new TokenUsage(
              usage.path("prompt_tokens").asLong(-1), usage.path("completion_tokens").asLong(-1)),
          prompt.system(),
          prompt.user());
    } catch (PlatformException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new PlatformException(
          502, "OpenRouter conceptual-model request failed: " + ex.getMessage(), ex);
    }
  }

  @Override
  public ProviderCapabilities capabilities() {
    return properties.providerCapabilities();
  }

  private String key() {
    String configured = properties.openaiCompatible().apiKey();
    if (configured != null && !configured.isBlank()) return configured;
    String environment = System.getenv("OPENAI_COMPATIBLE_API_KEY");
    return environment == null ? "" : environment.trim();
  }

  private static String snippet(String value) {
    if (value == null) return "";
    return value.substring(0, Math.min(500, value.length()));
  }

  private static String contentParts(JsonNode content) {
    if (content.isTextual()) return content.asText();
    if (content.isObject()) {
      String direct = content.path("text").asText("");
      if (!direct.isBlank()) return direct;
      JsonNode nested = content.get("content");
      return nested == null ? content.path("value").asText("") : contentParts(nested);
    }
    if (!content.isArray()) return "";
    StringBuilder result = new StringBuilder();
    for (JsonNode part : content) {
      String text = contentParts(part);
      if (!text.isBlank()) result.append(text);
    }
    return result.toString();
  }
}
