package io.mehdieidi.varka.platform.assistant.provider.springai;

import com.openai.client.okhttp.OpenAIOkHttpClient;
import io.mehdieidi.varka.platform.assistant.agent.AgentActionSchema;
import io.mehdieidi.varka.platform.assistant.application.AssistantHardeningService;
import io.mehdieidi.varka.platform.assistant.application.AssistantPromptGuard;
import io.mehdieidi.varka.platform.assistant.config.AiProperties;
import io.mehdieidi.varka.platform.assistant.domain.AssistantModelRole;
import io.mehdieidi.varka.platform.assistant.provider.ProxyAvailability;
import io.mehdieidi.varka.platform.kernel.PlatformException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.tool.function.FunctionToolCallback;

/** OpenAI-compatible provider implemented through Spring AI and a one-shot native tool adapter. */
public class OpenAiCompatibleAssistantModelProvider extends AbstractAssistantModelProvider {

  private static final Logger log =
      LoggerFactory.getLogger(OpenAiCompatibleAssistantModelProvider.class);

  /** Creates the provider using the dedicated AI-only HTTP client. */
  public OpenAiCompatibleAssistantModelProvider(
      AiProperties properties,
      ProxyAvailability proxyAvailability,
      AssistantPromptGuard promptGuard,
      AssistantHardeningService hardening) {
    super(
        AiProperties.Provider.OPENAI.key(),
        properties,
        proxyAvailability,
        promptGuard,
        hardening,
        () -> chatModel(properties));
  }

  private static OpenAiChatModel chatModel(AiProperties properties) {
    String apiKey = configuredApiKey(properties);
    if (apiKey.isBlank()) apiKey = "sk-not-configured";
    OpenAIOkHttpClient.Builder client =
        OpenAIOkHttpClient.builder()
            .baseUrl(properties.openaiCompatible().baseUrl())
            .apiKey(apiKey)
            .timeout(properties.requestTimeout())
            .maxRetries(0);
    AiProperties.Proxy proxy = properties.proxyFor(AiProperties.Provider.OPENAI.key());
    if (proxy.enabled() && proxy.type() != AiProperties.ProxyType.DIRECT) {
      client.proxy(
          new java.net.Proxy(
              proxy.type() == AiProperties.ProxyType.SOCKS
                  ? java.net.Proxy.Type.SOCKS
                  : java.net.Proxy.Type.HTTP,
              proxy.address()));
    }
    return OpenAiChatModel.builder()
        .openAiClient(client.build())
        .options(
            OpenAiChatOptions.builder()
                .model(
                    properties
                        .models()
                        .forRole(AiProperties.Provider.OPENAI, AssistantModelRole.RESPONDER))
                .temperature(0.2)
                .build())
        .build();
  }

  @Override
  protected String baseUrl() {
    return properties.openaiCompatible().baseUrl();
  }

  @Override
  protected boolean apiKeyConfigured() {
    return !configuredApiKey(properties).isBlank();
  }

  /**
   * Resolves the app property first, then the deployment credential supplied by an OpenAI-
   * compatible provider. The value is never logged or returned in provider metadata.
   */
  private static String configuredApiKey(AiProperties properties) {
    String configured = properties.openaiCompatible().apiKey();
    if (configured != null && !configured.isBlank()) return configured;
    String environment = System.getenv("OPENAI_COMPATIBLE_API_KEY");
    return environment == null ? "" : environment.trim();
  }

  @Override
  protected String modelFor(AssistantModelRole role) {
    return properties.models().forRole(AiProperties.Provider.OPENAI, role);
  }

  @Override
  protected OpenAiChatOptions.Builder options(
      String model,
      AssistantModelRole role,
      boolean toolsRequested,
      io.mehdieidi.varka.platform.assistant.provider.AssistantModelProvider.AssistantPrompt
          prompt) {
    OpenAiChatOptions.Builder builder =
        OpenAiChatOptions.builder()
            .model(model)
            .temperature(0.2)
            .maxCompletionTokens(Math.min(properties.tokenBudget(), completionLimit(role)));
    boolean useNativeTools =
        properties.openaiCompatible().protocol() == AiProperties.OpenAiProtocol.TOOLS;
    if (useNativeTools) {
      // These callbacks deliberately only echo the model's structured arguments. The workflow
      // consumes the resulting tool call and is the sole authority that executes model tools.
      builder.toolCallbacks(
          AgentActionSchema.toolNames().stream()
              .map(
                  name ->
                      (org.springframework.ai.tool.ToolCallback)
                          FunctionToolCallback.<String, String>builder(name, arguments -> arguments)
                              .description(
                                  "Varka assistant tool; backend validates and executes it.")
                              .inputSchema(toolArgumentsSchema(name, prompt.patchContracts()))
                              .build())
              .toList());
      builder.parallelToolCalls(false);
    } else {
      // outputSchema requests the strict JSON-schema fallback supported by compatible endpoints.
      builder.outputSchema(AgentActionSchema.json(prompt.patchContracts()));
    }
    return builder;
  }

  @Override
  protected org.springframework.ai.chat.model.ChatResponse callModel(
      io.mehdieidi.varka.platform.assistant.provider.AssistantModelProvider.AssistantPrompt prompt,
      String model,
      boolean toolsRequested) {
    if (properties.openaiCompatible().protocol() != AiProperties.OpenAiProtocol.TOOLS) {
      return super.callModel(prompt, model, toolsRequested);
    }
    try {
      var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
      var body = mapper.createObjectNode();
      body.put("model", model);
      var messages = body.putArray("messages");
      messages
          .addObject()
          .put("role", "system")
          .put(
              "content",
              SYSTEM_GUARDRAIL
                  + "\n"
                  + prompt.system()
                  + "\n\nNATIVE TOOL PROTOCOL (authoritative): Do not return an action JSON object"
                  + " or prose. Call exactly one supplied function. For an explanation, answer,"
                  + " or necessary clarification, you MUST call respond_to_user with a non-empty"
                  + " message. Use inspect_model or describe_types only for the corresponding"
                  + " read step; use apply_draft_patch only for a validated model mutation.");
      messages.addObject().put("role", "user").put("content", userWithContext(prompt));
      var tools = body.putArray("tools");
      for (String name : AgentActionSchema.toolNames()) {
        var tool = tools.addObject();
        tool.put("type", "function");
        var function = tool.putObject("function");
        function.put("name", name);
        function.put("description", toolDescription(name));
        function.set(
            "parameters",
            mapper.valueToTree(AgentActionSchema.toolSchema(name, prompt.patchContracts())));
      }
      // The workflow executes exactly one action at a time. Once exact contracts have been
      // returned, the executor permits only a terminal mutation; leaving every read tool
      // selectable lets compatible providers repeatedly call describe_types despite the
      // explicit state-machine instruction in the prompt.
      boolean forcePatchTool = shouldForcePatchTool(prompt.user());
      if (forcePatchTool) {
        putForcedPatchToolChoice(body, false);
      } else {
        // Requiring a tool call avoids prose that would otherwise be mistaken for structured
        // output.
        body.put("tool_choice", "required");
      }
      body.put("parallel_tool_calls", false);
      // Some compatible gateways leave a socket open indefinitely rather than returning their
      // advertised timeout response. HttpRequest.timeout alone did not reliably interrupt that
      // condition, which left durable turns RUNNING forever. Bound the future itself as well.
      var response = sendChatRequest(mapper, body);
      if (forcePatchTool
          && response.statusCode() / 100 != 2
          && response.body() != null
          && response.body().contains("tool_choice.name")) {
        putForcedPatchToolChoice(body, true);
        response = sendChatRequest(mapper, body);
      }
      if (response.statusCode() / 100 != 2) {
        String errorBody = providerErrorSnippet(response.body());
        throw new PlatformException(
            response.statusCode(),
            response.statusCode() >= 400 && response.statusCode() < 500
                ? "AI provider rejected the configured tool protocol. Check "
                    + "VARKA_AI_OPENAI_PROTOCOL, the selected model, and endpoint capabilities."
                    + errorBody
                : "AI provider returned HTTP " + response.statusCode() + "." + errorBody);
      }
      var message = mapper.readTree(response.body()).path("choices").path(0).path("message");
      String content = nativeToolAction(mapper, message);
      return new org.springframework.ai.chat.model.ChatResponse(
          List.of(
              new org.springframework.ai.chat.model.Generation(
                  new org.springframework.ai.chat.messages.AssistantMessage(content))));
    } catch (PlatformException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new IllegalStateException("OpenAI-compatible native tool request failed", ex);
    }
  }

  static boolean shouldForcePatchTool(String userPrompt) {
    return userPrompt != null
        && (userPrompt.contains("The next action must be apply_draft_patch.")
            || userPrompt.contains("The next action must be commit_model_batch")
            || userPrompt.contains("Return one corrected JSON object."));
  }

  private static void putForcedPatchToolChoice(
      com.fasterxml.jackson.databind.node.ObjectNode body, boolean flatName) {
    body.remove("tool_choice");
    var toolChoice = body.putObject("tool_choice");
    toolChoice.put("type", "function");
    if (flatName) {
      toolChoice.put("name", "apply_draft_patch");
    } else {
      toolChoice.putObject("function").put("name", "apply_draft_patch");
    }
  }

  private HttpResponse<String> sendChatRequest(
      com.fasterxml.jackson.databind.ObjectMapper mapper,
      com.fasterxml.jackson.databind.node.ObjectNode body)
      throws java.io.IOException, InterruptedException {
    var request =
        HttpRequest.newBuilder(URI.create(baseUrl() + "/chat/completions"))
            .timeout(properties.requestTimeout())
            .header("Authorization", "Bearer " + configuredApiKey(properties))
            .header("Content-Type", "application/json")
            .POST(
                HttpRequest.BodyPublishers.ofString(
                    mapper.writeValueAsString(body), StandardCharsets.UTF_8))
            .build();
    HttpClient.Builder httpClient =
        HttpClient.newBuilder().connectTimeout(properties.requestTimeout());
    AiProperties.Proxy proxy = properties.proxyFor(AiProperties.Provider.OPENAI.key());
    if (proxy.enabled() && proxy.type() != AiProperties.ProxyType.DIRECT) {
      httpClient.proxy(java.net.ProxySelector.of(proxy.address()));
    }
    return httpClient.build().send(request, HttpResponse.BodyHandlers.ofString());
  }

  private static String providerErrorSnippet(String body) {
    String value = body == null ? "" : body.replaceAll("\\s+", " ").trim();
    if (value.isBlank()) return "";
    if (value.length() > 500) value = value.substring(0, 500) + "...";
    return " Provider response: " + value;
  }

  /** Translates one raw OpenAI tool call to the legacy closed action envelope. */
  static String nativeToolAction(
      com.fasterxml.jackson.databind.ObjectMapper mapper,
      com.fasterxml.jackson.databind.JsonNode message)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    var calls = message.path("tool_calls");
    if (!calls.isArray() || calls.size() != 1) {
      return nativeContentAction(mapper, message.path("content").asText(""));
    }
    var call = calls.get(0);
    String name = call.path("function").path("name").asText();
    String arguments = call.path("function").path("arguments").asText();
    if (name.isBlank() || arguments.isBlank() || !mapper.readTree(arguments).isObject()) {
      throw new PlatformException(422, "Provider returned malformed assistant tool arguments.");
    }
    log.info("Native assistant tool received name={} argumentChars={}", name, arguments.length());
    String action =
        switch (name) {
          case "respond_to_user" -> "answer_user";
          case "plan_source_model" -> "plan_source_model";
          case "inspect_model", "describe_types" -> name;
          case "apply_draft_patch" -> "commit_model_batch";
          // These tools are included in the V2 provider contract, but the current bounded
          // executor has no separate action state for them. A model must use the executable
          // patch action after its lookup steps; rejecting an early completion is actionable.
          case "search_language", "complete_checkpoint" ->
              throw new PlatformException(
                  422, "Provider selected a tool not executable in the current workflow step.");
          default ->
              throw new PlatformException(422, "Provider returned an unsupported assistant tool.");
        };
    return "{\"action\":\"" + action + "\",\"arguments\":" + arguments + "}";
  }

  private static String nativeContentAction(
      com.fasterxml.jackson.databind.ObjectMapper mapper, String content) {
    String trimmed = content == null ? "" : content.trim();
    if (trimmed.isBlank()) {
      throw new PlatformException(422, "Provider must return exactly one assistant tool call.");
    }
    com.fasterxml.jackson.databind.JsonNode value;
    try {
      value = mapper.readTree(trimmed);
    } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
      throw new PlatformException(422, "Provider must return exactly one assistant tool call.");
    }
    if (value.path("action").isTextual() && value.path("arguments").isObject()) {
      return trimmed;
    }
    if (value.path("message").isTextual() && !value.path("message").asText().isBlank()) {
      return "{\"action\":\"answer_user\",\"arguments\":" + trimmed + "}";
    }
    throw new PlatformException(422, "Provider must return exactly one assistant tool call.");
  }

  private static String toolDescription(String name) {
    return switch (name) {
      case "respond_to_user" -> "Return the final user-facing answer in message.";
      case "plan_source_model" -> "Plan source document spans into coherent CIM modeling slices.";
      case "inspect_model" -> "Read a model element or inventory using id.";
      case "describe_types" -> "Retrieve exact Ecore contracts for names.";
      case "apply_draft_patch" -> "Submit one complete candidate model patch.";
      case "complete_checkpoint" -> "Commit a structurally valid candidate checkpoint.";
      case "search_language" -> "Search modeling-language concepts and methodology.";
      default -> throw new IllegalArgumentException("Unknown native assistant tool: " + name);
    };
  }

  private static String toolArgumentsSchema(
      String name,
      java.util.List<
              io.mehdieidi.varka.platform.assistant.metamodel.MetamodelKnowledgeService
                  .TypeContract>
          patchContracts) {
    try {
      return new com.fasterxml.jackson.databind.ObjectMapper()
          .writeValueAsString(AgentActionSchema.toolSchema(name, patchContracts));
    } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
      throw new IllegalStateException("Unable to encode native assistant tool schema", ex);
    }
  }

  private int completionLimit(AssistantModelRole role) {
    return role == AssistantModelRole.SUMMARIZER ? 2048 : 4096;
  }
}
