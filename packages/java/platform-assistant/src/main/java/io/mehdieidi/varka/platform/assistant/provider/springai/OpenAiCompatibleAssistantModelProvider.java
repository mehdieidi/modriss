package io.mehdieidi.varka.platform.assistant.provider.springai;

import com.openai.client.okhttp.OpenAIOkHttpClient;
import io.mehdieidi.varka.platform.assistant.agent.AgentActionSchema;
import io.mehdieidi.varka.platform.assistant.agent.AgentTurnLoop;
import io.mehdieidi.varka.platform.assistant.agent.ConceptualInstanceModelWorkflow;
import io.mehdieidi.varka.platform.assistant.application.AssistantHardeningService;
import io.mehdieidi.varka.platform.assistant.application.AssistantPromptGuard;
import io.mehdieidi.varka.platform.assistant.application.ProviderRequestContext;
import io.mehdieidi.varka.platform.assistant.config.AiProperties;
import io.mehdieidi.varka.platform.assistant.provider.ProxyAvailability;
import io.mehdieidi.varka.platform.kernel.PlatformException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.tool.function.FunctionToolCallback;

/** OpenAI-compatible provider implemented through Spring AI and a one-shot native tool adapter. */
public class OpenAiCompatibleAssistantModelProvider extends AbstractAssistantModelProvider {

  private static final int STRATEGY_COMPLETION_LIMIT = 2048;
  private static final Logger log =
      LoggerFactory.getLogger(OpenAiCompatibleAssistantModelProvider.class);
  private final AtomicReference<TokenWindow> tokenWindow = new AtomicReference<>();

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
                .model(properties.models().model(AiProperties.Provider.OPENAI))
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
    if (environment == null || environment.isBlank()) environment = System.getenv("OPENAI_API_KEY");
    return environment == null ? "" : environment.trim();
  }

  @Override
  protected String model() {
    return properties.models().model(AiProperties.Provider.OPENAI);
  }

  @Override
  protected OpenAiChatOptions.Builder options(
      String model,
      boolean toolsRequested,
      io.mehdieidi.varka.platform.assistant.provider.AssistantModelProvider.AssistantPrompt
          prompt) {
    OpenAiChatOptions.Builder builder =
        OpenAiChatOptions.builder()
            .model(model)
            .temperature(0.2)
            .maxCompletionTokens(
                Math.min(properties.tokenBudget(), capabilities().maxCompletionTokens()));
    boolean useNativeTools =
        properties.openaiCompatible().protocol() == AiProperties.OpenAiProtocol.TOOLS
            && capabilities().nativeToolsPreferred();
    if (useNativeTools) {
      // These callbacks deliberately only echo the model's structured arguments. The workflow
      // consumes the resulting tool call and is the sole authority that executes model tools.
      builder.toolCallbacks(
          availableToolNames(prompt).stream()
              .map(
                  name ->
                      (org.springframework.ai.tool.ToolCallback)
                          FunctionToolCallback.<String, String>builder(name, arguments -> arguments)
                              .description(
                                  "Varka assistant tool; backend validates and executes it.")
                              .inputType(String.class)
                              .inputSchema(toolArgumentsSchema(name, prompt.patchContracts()))
                              .build())
              .toList());
      builder.parallelToolCalls(false);
    } else {
      // outputSchema requests the strict JSON-schema fallback supported by compatible endpoints.
      builder.outputSchema(
          "conceptual_instance_model".equals(prompt.requiredTool())
              ? ConceptualInstanceModelWorkflow.jsonSchema()
              : "conceptual_instance_slice".equals(prompt.requiredTool())
                  ? ConceptualInstanceModelWorkflow.jsonSchema()
                  : "conceptual_blueprint".equals(prompt.requiredTool())
                      ? ConceptualInstanceModelWorkflow.blueprintSchema()
                      : "conceptual_obligation_ledger".equals(prompt.requiredTool())
                          ? ConceptualInstanceModelWorkflow.obligationLedgerSchema()
                          : "conceptual_blueprint_patch".equals(prompt.requiredTool())
                              ? ConceptualInstanceModelWorkflow.blueprintPatchSchema()
                              : "conceptual_blueprint_review".equals(prompt.requiredTool())
                                  ? ConceptualInstanceModelWorkflow.blueprintCompletenessSchema()
                                  : "conceptual_obligation_review".equals(prompt.requiredTool())
                                      ? ConceptualInstanceModelWorkflow.obligationReviewSchema()
                                      : "conceptual_review".equals(prompt.requiredTool())
                                          ? ConceptualInstanceModelWorkflow.reviewSchema()
                                          : "conceptual_correction".equals(prompt.requiredTool())
                                              ? ConceptualInstanceModelWorkflow.correctionSchema()
                                              : "conceptual_type_selection"
                                                      .equals(prompt.requiredTool())
                                                  ? ConceptualInstanceModelWorkflow
                                                      .typeSelectionSchema()
                                                  : "assistant_strategy"
                                                          .equals(prompt.requiredTool())
                                                      ? AgentTurnLoop.strategySchema()
                                                      : AgentActionSchema.json(
                                                          prompt.patchContracts()));
    }
    return builder;
  }

  @Override
  protected org.springframework.ai.chat.model.ChatResponse callModel(
      io.mehdieidi.varka.platform.assistant.provider.AssistantModelProvider.AssistantPrompt prompt,
      String model,
      boolean toolsRequested) {
    if (properties.openaiCompatible().protocol() != AiProperties.OpenAiProtocol.TOOLS
        || !capabilities().nativeToolsPreferred()) {
      return callJsonObjectModel(prompt, model);
    }
    try {
      var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
      var body = mapper.createObjectNode();
      body.put("model", model);
      // Keep the native-tools transport subject to the same generation limits as the Spring AI
      // transport.  Without these fields a compatible gateway can use its much larger defaults,
      // making a single bounded workflow step exceed the turn's token and latency budget.
      body.put("temperature", 0.2);
      int completionLimit =
          completionLimit(prompt.requiredTool(), properties.maxCompletionTokens());
      body.put("max_tokens", Math.min(completionLimit, capabilities().maxCompletionTokens()));
      applyModelGenerationControls(body, model);
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
                  + " read step; use analyze_source_units for source analysis,"
                  + " plan_cim_blueprint for CIM blueprint planning, plan_model_edit for a"
                  + " structured edit plan; use commit_model_batch only for a validated model"
                  + " mutation.");
      messages.addObject().put("role", "user").put("content", userWithContext(prompt));
      var tools = body.putArray("tools");
      String forcedTool = forcedToolName(prompt);
      for (String name : availableToolNames(prompt)) {
        if (forcedTool != null && !forcedTool.equals(name)) {
          continue;
        }
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
      boolean forceSingleTool = capabilities().forcedToolChoiceReliable() && forcedTool != null;
      if (forceSingleTool) {
        putForcedToolChoice(body, forcedTool);
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
      return nativeChatResponse(mapper, response.body(), model);
    } catch (PlatformException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new IllegalStateException("OpenAI-compatible native tool request failed", ex);
    }
  }

  /**
   * Calls compatible endpoints in JSON-object mode without depending on native function calling.
   *
   * <p>Several gateways advertise the OpenAI chat API but do not preserve {@code tool_calls}. The
   * durable loop already owns a strict action envelope and validates it before execution, so JSON
   * mode retains the same closed action boundary while remaining portable to those gateways.
   */
  private org.springframework.ai.chat.model.ChatResponse callJsonObjectModel(
      io.mehdieidi.varka.platform.assistant.provider.AssistantModelProvider.AssistantPrompt prompt,
      String model) {
    try {
      var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
      var body = mapper.createObjectNode();
      body.put("model", model);
      body.put("temperature", 0.0);
      int completionLimit =
          completionLimit(prompt.requiredTool(), properties.maxCompletionTokens());
      body.put("max_tokens", Math.min(completionLimit, capabilities().maxCompletionTokens()));
      applyModelGenerationControls(body, model);
      var messages = body.putArray("messages");
      boolean structuredDocument = isStructuredDocument(prompt.requiredTool());
      String requiredAction =
          prompt.requiredTool() == null
              ? ""
              : structuredDocument
                  ? "\n\n"
                      + "WORKFLOW GATE: Return only the requested structured document in this"
                      + " response."
                  : "\n\nWORKFLOW GATE: Return action '"
                      + prompt.requiredTool()
                      + "' in this response. Do not choose another action.";
      messages
          .addObject()
          .put("role", "system")
          .put(
              "content",
              SYSTEM_GUARDRAIL
                  + "\n"
                  + prompt.system()
                  + "\n\nSTRICT JSON PROTOCOL: Return exactly one JSON object matching the"
                  + (structuredDocument
                      ? " requested structured-document schema."
                      : " action envelope described above.")
                  + " Do not use markdown, prose, or provider function calls."
                  + requiredAction);
      messages.addObject().put("role", "user").put("content", userWithContext(prompt));
      body.putObject("response_format").put("type", "json_object");
      var response = sendChatRequest(mapper, body);
      if (response.statusCode() / 100 != 2) {
        String errorBody = providerErrorSnippet(response.body());
        throw new PlatformException(
            response.statusCode(),
            response.statusCode() >= 400 && response.statusCode() < 500
                ? "AI provider rejected the configured JSON protocol. Check "
                    + "VARKA_AI_OPENAI_PROTOCOL, the selected model, and endpoint capabilities."
                    + errorBody
                : "AI provider returned HTTP " + response.statusCode() + "." + errorBody);
      }
      return jsonChatResponse(mapper, response.body(), model, prompt.requiredTool());
    } catch (PlatformException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new IllegalStateException("OpenAI-compatible JSON request failed", ex);
    }
  }

  /** Applies model-family protocol controls only; modeling decisions remain entirely LLM-owned. */
  static void applyModelGenerationControls(
      com.fasterxml.jackson.databind.node.ObjectNode body, String model) {
    if (isDeepSeekModel(model)) {
      // DeepSeek V4 expects an object here. Boolean `thinking:false` is ignored by compatible
      // gateways and can consume the whole completion budget in hidden reasoning, yielding
      // finish_reason=length with empty final content.
      body.putObject("thinking").put("type", "disabled");
      return;
    }
    if (isQwenModel(model)) {
      var reasoning = body.putObject("reasoning");
      reasoning.put("effort", "none");
      reasoning.put("exclude", true);
      body.put("enable_thinking", false);
    }
  }

  static int completionLimit(String requiredTool, int fallback) {
    return switch (requiredTool == null ? "" : requiredTool) {
      case "assistant_strategy" -> STRATEGY_COMPLETION_LIMIT;
      // Arvan currently returns reasoning_content for DeepSeek-V4-Flash even when the official
      // thinking:{type:"disabled"} control is present. Type selection therefore needs the
      // configured ceiling to leave room for provider-side reasoning before its tiny JSON result.
      case "conceptual_type_selection",
          "conceptual_blueprint",
          "conceptual_blueprint_patch",
          "conceptual_blueprint_review",
          "conceptual_obligation_ledger",
          "conceptual_obligation_review" ->
          fallback;
      case "conceptual_review" -> 8000;
      case "conceptual_correction", "conceptual_instance_slice" -> 8000;
      default -> fallback;
    };
  }

  /** Preserves native token usage instead of losing it while adapting a tool call to Spring AI. */
  static org.springframework.ai.chat.model.ChatResponse nativeChatResponse(
      com.fasterxml.jackson.databind.ObjectMapper mapper,
      String responseBody,
      String requestedModel)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    var root = mapper.readTree(responseBody);
    var message = root.path("choices").path(0).path("message");
    String content = nativeToolAction(mapper, message);
    var usage = root.path("usage");
    Integer promptTokens = integerOrNull(usage.get("prompt_tokens"));
    Integer completionTokens = integerOrNull(usage.get("completion_tokens"));
    Integer totalTokens = integerOrNull(usage.get("total_tokens"));
    var generations =
        List.of(
            new org.springframework.ai.chat.model.Generation(
                new org.springframework.ai.chat.messages.AssistantMessage(content)));
    if (promptTokens == null && completionTokens == null) {
      return new org.springframework.ai.chat.model.ChatResponse(generations);
    }
    org.springframework.ai.chat.metadata.Usage springUsage =
        new org.springframework.ai.chat.metadata.DefaultUsage(
            promptTokens == null ? 0 : promptTokens,
            completionTokens == null ? 0 : completionTokens,
            totalTokens,
            usage.deepCopy());
    var metadata =
        org.springframework.ai.chat.metadata.ChatResponseMetadata.builder()
            .id(root.path("id").asText(""))
            .model(root.path("model").asText(requestedModel == null ? "" : requestedModel))
            .usage(springUsage)
            .build();
    return new org.springframework.ai.chat.model.ChatResponse(generations, metadata);
  }

  /** Preserves raw JSON content so the shared action codec can handle harmless fences/preambles. */
  static org.springframework.ai.chat.model.ChatResponse jsonChatResponse(
      com.fasterxml.jackson.databind.ObjectMapper mapper,
      String responseBody,
      String requestedModel)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    return jsonChatResponse(mapper, responseBody, requestedModel, null);
  }

  static org.springframework.ai.chat.model.ChatResponse jsonChatResponse(
      com.fasterxml.jackson.databind.ObjectMapper mapper,
      String responseBody,
      String requestedModel,
      String requiredAction)
      throws com.fasterxml.jackson.core.JsonProcessingException {
    var root = mapper.readTree(responseBody);
    var choice = root.path("choices").path(0);
    var message = choice.path("message");
    String content = message.path("content").asText("");
    if (content.isBlank()) {
      String finishReason = choice.path("finish_reason").asText("unknown");
      throw new PlatformException(
          "length".equalsIgnoreCase(finishReason) ? 502 : 422,
          "AI provider returned no structured JSON content (finish_reason=" + finishReason + ").");
    }
    content = normalizeRequiredJsonAction(mapper, content, requiredAction);
    var usage = root.path("usage");
    Integer promptTokens = integerOrNull(usage.get("prompt_tokens"));
    Integer completionTokens = integerOrNull(usage.get("completion_tokens"));
    Integer totalTokens = integerOrNull(usage.get("total_tokens"));
    var generations =
        List.of(
            new org.springframework.ai.chat.model.Generation(
                new org.springframework.ai.chat.messages.AssistantMessage(content)));
    if (promptTokens == null && completionTokens == null) {
      return new org.springframework.ai.chat.model.ChatResponse(generations);
    }
    org.springframework.ai.chat.metadata.Usage springUsage =
        new org.springframework.ai.chat.metadata.DefaultUsage(
            promptTokens == null ? 0 : promptTokens,
            completionTokens == null ? 0 : completionTokens,
            totalTokens,
            usage.deepCopy());
    var metadata =
        org.springframework.ai.chat.metadata.ChatResponseMetadata.builder()
            .id(root.path("id").asText(""))
            .model(root.path("model").asText(requestedModel == null ? "" : requestedModel))
            .usage(springUsage)
            .build();
    return new org.springframework.ai.chat.model.ChatResponse(generations, metadata);
  }

  /** Adds only the workflow-owned action envelope when a JSON provider returns bare arguments. */
  private static String normalizeRequiredJsonAction(
      com.fasterxml.jackson.databind.ObjectMapper mapper, String content, String requiredAction) {
    if (requiredAction == null || requiredAction.isBlank()) return content;
    // The conceptual instance model is itself the structured document. It is not an action
    // envelope and must reach the deterministic compiler byte-for-byte apart from provider JSON
    // transport decoding.
    if (isStructuredDocument(requiredAction)) return content;
    String candidate = content == null ? "" : content.trim();
    if (candidate.startsWith("```")) {
      int firstNewline = candidate.indexOf('\n');
      int closingFence = candidate.lastIndexOf("```");
      if (firstNewline >= 0 && closingFence > firstNewline) {
        candidate = candidate.substring(firstNewline + 1, closingFence).trim();
      }
    }
    try {
      var value = mapper.readTree(candidate);
      if (!value.isObject() || value.hasNonNull("action") || value.hasNonNull("tool")) {
        return content;
      }
      var envelope = mapper.createObjectNode();
      envelope.put("action", requiredAction);
      if (value.path("arguments").isObject() && value.size() == 1) {
        envelope.set("arguments", value.path("arguments").deepCopy());
      } else {
        envelope.set("arguments", value.deepCopy());
      }
      return mapper.writeValueAsString(envelope);
    } catch (com.fasterxml.jackson.core.JsonProcessingException ignored) {
      return content;
    }
  }

  static boolean isStructuredDocument(String requiredAction) {
    return "conceptual_instance_model".equals(requiredAction)
        || "conceptual_instance_slice".equals(requiredAction)
        || "conceptual_blueprint".equals(requiredAction)
        || "conceptual_blueprint_patch".equals(requiredAction)
        || "conceptual_blueprint_review".equals(requiredAction)
        || "conceptual_obligation_ledger".equals(requiredAction)
        || "conceptual_obligation_review".equals(requiredAction)
        || "conceptual_review".equals(requiredAction)
        || "conceptual_correction".equals(requiredAction)
        || "conceptual_type_selection".equals(requiredAction)
        || "assistant_strategy".equals(requiredAction);
  }

  private static Integer integerOrNull(com.fasterxml.jackson.databind.JsonNode value) {
    return value != null && value.canConvertToInt() ? value.intValue() : null;
  }

  static boolean shouldForcePatchTool(String userPrompt) {
    return userPrompt != null
        && (userPrompt.contains("The next action must be apply_draft_patch.")
            || userPrompt.contains("The next action must be commit_model_batch")
            || userPrompt.contains("Return one corrected JSON object."));
  }

  static boolean shouldForcePlanTool(String userPrompt) {
    return userPrompt != null
        && (userPrompt.contains("First return plan_model_edit")
            || userPrompt.contains("return plan_model_edit with a compact structured plan")
            || userPrompt.contains("Return plan_model_edit with a progressive CIM plan")
            || userPrompt.contains(
                "Return plan_model_edit with a durable progressive CIM modeling plan"));
  }

  static boolean shouldForceSourceAnalysisTool(String userPrompt) {
    return userPrompt != null
        && !userPrompt.contains("A persisted ModelingPlan is already available")
        && (userPrompt.contains("The next action must be analyze_source_units")
            || userPrompt.contains("First return analyze_source_units"));
  }

  static boolean shouldForceCimBlueprintTool(String userPrompt) {
    return userPrompt != null
        && (userPrompt.contains("The next action must be plan_cim_blueprint")
            || userPrompt.contains("Return plan_cim_blueprint"));
  }

  static boolean shouldForceDescribeTypesTool(String userPrompt) {
    return userPrompt != null
        && userPrompt.contains("Current durable modeling checkpoint")
        && userPrompt.contains("using describe_types")
        && !userPrompt.contains("Exact type contracts already retrieved by the backend");
  }

  static String forcedToolName(
      io.mehdieidi.varka.platform.assistant.provider.AssistantModelProvider.AssistantPrompt
          prompt) {
    if (prompt != null && prompt.requiredTool() != null) return prompt.requiredTool();
    String userPrompt = prompt == null ? null : prompt.user();
    if (shouldForcePatchTool(userPrompt)) return "commit_model_batch";
    if (shouldForceDescribeTypesTool(userPrompt)) return "describe_types";
    if (shouldForceCimBlueprintTool(userPrompt)) return "plan_cim_blueprint";
    if (shouldForceSourceAnalysisTool(userPrompt)) return "analyze_source_units";
    if (shouldForcePlanTool(userPrompt)) return "plan_model_edit";
    return null;
  }

  /** Keeps legacy source tools out of ordinary turns unless durable workflow state requires one. */
  static List<String> availableToolNames(
      io.mehdieidi.varka.platform.assistant.provider.AssistantModelProvider.AssistantPrompt
          prompt) {
    String required = forcedToolName(prompt);
    if (required != null) return List.of(required);
    return List.of(
        "plan_model_edit",
        "inspect_model",
        "describe_types",
        "commit_model_batch",
        "respond_to_user");
  }

  private static void putForcedToolChoice(
      com.fasterxml.jackson.databind.node.ObjectNode body, String toolName) {
    body.remove("tool_choice");
    var toolChoice = body.putObject("tool_choice");
    toolChoice.put("type", "function");
    toolChoice.putObject("function").put("name", toolName);
  }

  private HttpResponse<String> sendChatRequest(
      com.fasterxml.jackson.databind.ObjectMapper mapper,
      com.fasterxml.jackson.databind.node.ObjectNode body)
      throws java.io.IOException, InterruptedException {
    String requestBody = mapper.writeValueAsString(body);
    int promptEstimate = Math.max(1, (requestBody.length() + 3) / 4);
    if (promptEstimate > properties.maxPromptTokens()) {
      throw new PlatformException(
          413,
          "AI prompt estimate "
              + promptEstimate
              + " tokens exceeds the configured per-call budget of "
              + properties.maxPromptTokens()
              + ". Reduce the source/model slice before calling the provider.");
    }
    awaitTokenCapacity(estimatedTokenDemand(requestBody, body.path("max_tokens").asInt(0)));
    var request =
        HttpRequest.newBuilder(URI.create(baseUrl() + "/chat/completions"))
            .version(HttpClient.Version.HTTP_1_1)
            .timeout(properties.requestTimeout())
            .header("Authorization", "Bearer " + configuredApiKey(properties))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
            .build();
    HttpClient.Builder httpClient =
        HttpClient.newBuilder().connectTimeout(properties.requestTimeout());
    AiProperties.Proxy proxy = properties.proxyFor(AiProperties.Provider.OPENAI.key());
    if (proxy.enabled() && proxy.type() != AiProperties.ProxyType.DIRECT) {
      httpClient.proxy(java.net.ProxySelector.of(proxy.address()));
    }
    java.util.concurrent.CompletableFuture<HttpResponse<String>> response =
        httpClient.build().sendAsync(request, HttpResponse.BodyHandlers.ofString());
    try {
      while (true) {
        ProviderRequestContext.check();
        try {
          HttpResponse<String> completed =
              response.get(100, java.util.concurrent.TimeUnit.MILLISECONDS);
          rememberTokenWindow(completed);
          return completed;
        } catch (java.util.concurrent.TimeoutException ignored) {
          // Recheck durable cancellation and the absolute turn deadline while the HTTP call runs.
        }
      }
    } catch (PlatformException ex) {
      response.cancel(true);
      throw ex;
    } catch (java.util.concurrent.ExecutionException ex) {
      Throwable cause = ex.getCause();
      if (cause instanceof java.io.IOException io) throw io;
      if (cause instanceof RuntimeException runtime) throw runtime;
      throw new java.io.IOException("OpenAI-compatible HTTP request failed", cause);
    }
  }

  private int estimatedTokenDemand(String requestBody, int maxCompletionTokens) {
    int promptEstimate = (requestBody == null ? 0 : (requestBody.length() + 3) / 4);
    return Math.max(1, promptEstimate + Math.max(0, maxCompletionTokens) + 512);
  }

  private void awaitTokenCapacity(int estimatedTokens) {
    while (true) {
      ProviderRequestContext.check();
      TokenWindow current = tokenWindow.get();
      long now = System.currentTimeMillis();
      if (current == null
          || now >= current.resetAtMillis()
          || current.remainingTokens() >= estimatedTokens) {
        return;
      }
      try {
        Thread.sleep(Math.min(100L, Math.max(1L, current.resetAtMillis() - now)));
      } catch (InterruptedException ex) {
        Thread.currentThread().interrupt();
        throw new PlatformException(503, "AI provider rate-limit wait was interrupted.");
      }
    }
  }

  private void rememberTokenWindow(HttpResponse<?> response) {
    Integer remaining =
        response
            .headers()
            .firstValue("x-ratelimit-remaining-tokens")
            .flatMap(OpenAiCompatibleAssistantModelProvider::parseInteger)
            .orElse(null);
    if (remaining == null) return;
    long resetMillis =
        response
            .headers()
            .firstValue("x-ratelimit-reset-tokens")
            .map(OpenAiCompatibleAssistantModelProvider::parseResetMillis)
            .orElse(60_000L);
    tokenWindow.set(new TokenWindow(remaining, System.currentTimeMillis() + resetMillis));
  }

  private static java.util.Optional<Integer> parseInteger(String value) {
    try {
      return java.util.Optional.of(Integer.parseInt(value.trim()));
    } catch (RuntimeException ignored) {
      return java.util.Optional.empty();
    }
  }

  static long parseResetMillis(String value) {
    if (value == null || value.isBlank()) return 60_000L;
    var matcher =
        java.util.regex.Pattern.compile("(?:(\\d+(?:\\.\\d+)?)m)?(?:(\\d+(?:\\.\\d+)?)s)?")
            .matcher(value.trim());
    if (!matcher.matches()) return 60_000L;
    double minutes = matcher.group(1) == null ? 0 : Double.parseDouble(matcher.group(1));
    double seconds = matcher.group(2) == null ? 0 : Double.parseDouble(matcher.group(2));
    return Math.max(1L, (long) Math.ceil((minutes * 60 + seconds) * 1000));
  }

  private record TokenWindow(int remainingTokens, long resetAtMillis) {}

  static boolean isQwenModel(String model) {
    if (model == null) return false;
    String normalized = model.trim().toLowerCase(java.util.Locale.ROOT);
    return normalized.startsWith("qwen") || normalized.contains("/qwen");
  }

  static boolean isDeepSeekModel(String model) {
    if (model == null) return false;
    String normalized = model.trim().toLowerCase(java.util.Locale.ROOT);
    return normalized.startsWith("deepseek") || normalized.contains("/deepseek");
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
    if (name.isBlank() || arguments.isBlank()) {
      throw new PlatformException(422, "Provider returned malformed assistant tool arguments.");
    }
    try {
      if (!mapper.readTree(arguments).isObject()) {
        throw new PlatformException(422, "Provider returned malformed assistant tool arguments.");
      }
    } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
      String diagnostic = ex.getOriginalMessage() == null ? "" : ex.getOriginalMessage();
      boolean truncated =
          diagnostic.toLowerCase(java.util.Locale.ROOT).contains("end-of-input")
              || diagnostic.toLowerCase(java.util.Locale.ROOT).contains("unexpected end")
              || diagnostic.toLowerCase(java.util.Locale.ROOT).contains("was expecting");
      throw new PlatformException(
          truncated ? 502 : 422,
          truncated
              ? "Provider returned truncated assistant tool arguments; retrying within the turn"
                  + " budget may recover."
              : "Provider returned malformed assistant tool arguments.");
    }
    log.info("Native assistant tool received name={} argumentChars={}", name, arguments.length());
    String action =
        switch (name) {
          case "respond_to_user" -> "answer_user";
          case "analyze_source_units", "plan_cim_blueprint" -> name;
          case "plan_model_edit" -> "plan_model_edit";
          case "inspect_model", "describe_types" -> name;
          case "commit_model_batch", "apply_draft_patch" -> "commit_model_batch";
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
      case "analyze_source_units" ->
          "Analyze source units into compact LLM-authored modeling concepts with source ids.";
      case "plan_cim_blueprint" ->
          "Plan CIM element candidates, contracts, relationships, and slices from source analysis.";
      case "plan_model_edit" -> "Return a compact structured plan for a CIM/PIM create or edit.";
      case "inspect_model" -> "Read a model element or inventory using id.";
      case "describe_types" -> "Retrieve exact Ecore contracts for names.";
      case "commit_model_batch" -> "Submit one complete candidate model patch.";
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
}
