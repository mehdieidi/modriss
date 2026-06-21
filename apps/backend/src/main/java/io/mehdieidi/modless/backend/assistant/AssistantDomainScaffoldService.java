package io.mehdieidi.modless.backend.assistant;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.mehdieidi.modless.platform.kernel.ModelLevel;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Metamodel-grounded domain scaffolds for empty-canvas creation requests. */
@Service
public class AssistantDomainScaffoldService {

  private static final Set<String> SERVERLESS_INTENT =
      Set.of("serverless", "backend", "api", "function", "microservice", "vending", "service");
  private static final Set<String> BUSINESS_INTENT =
      Set.of("business", "domain", "capability", "process", "organization", "actor", "cim");
  private static final Set<String> AWS_INTENT =
      Set.of("aws", "lambda", "gateway", "dynamodb", "eventbridge", "cloud", "psm", "sam");

  private final AssistantMetamodelSchemaService schemas;

  public AssistantDomainScaffoldService(AssistantMetamodelSchemaService schemas) {
    this.schemas = schemas;
  }

  /**
   * Returns whether a deterministic scaffold should be used instead of a thin LLM patch.
   *
   * @param level model level
   * @param request user message
   * @param emptyModel whether the canvas is empty
   * @return true when a scaffold is available for this request
   */
  public boolean shouldScaffold(ModelLevel level, String request, boolean emptyModel) {
    return emptyModel && supports(level, request);
  }

  /**
   * Builds a substantive serverless backend scaffold for an empty canvas.
   *
   * @param level model level
   * @param request user message
   * @return semantic patch when supported
   */
  public Optional<SemanticModelPatch> build(ModelLevel level, String request) {
    if (!supports(level, request)) {
      return Optional.empty();
    }
    return switch (level) {
      case PIM -> Optional.of(serverlessBackend(request));
      case CIM -> Optional.of(businessModel(request));
      case PSM -> Optional.of(awsPlatform(request));
      default -> Optional.empty();
    };
  }

  /** Adds observability-oriented elements that can be composed onto an existing scaffold. */
  public SemanticModelPatch observabilityPack(ModelLevel level, String request) {
    return switch (level) {
      case PIM -> observabilityPimPack(request);
      case PSM -> observabilityPsmPack(request);
      default -> new SemanticModelPatch(List.of());
    };
  }

  /** Adds security-oriented elements that can be composed onto an existing scaffold. */
  public SemanticModelPatch securityPack(ModelLevel level, String request) {
    return switch (level) {
      case PIM -> securityPimPack(request);
      case PSM -> securityPsmPack(request);
      default -> new SemanticModelPatch(List.of());
    };
  }

  /** Returns a human-readable domain label inferred from the request. */
  public String deriveDomainName(String request) {
    String normalized = request == null ? "" : request.toLowerCase(Locale.ROOT);
    String stripped = normalized;
    stripped =
        stripped.replaceAll("(?i)^\\s*(please\\s+)?(create|build|design|scaffold)\\s+(a\\s+)?", "");
    stripped = stripped.replaceAll("(?i)^\\s*serverless\\s+model\\s+for\\s+", "");
    stripped = stripped.replaceAll("(?i)\\s+serverless\\s+model\\s*$", "");
    stripped = stripped.replaceAll("(?i)\\s+serverless\\s+backend\\s*$", "");
    stripped = stripped.replaceAll("(?i)\\s+backend\\s*$", "");
    stripped = stripped.replaceAll("(?i)\\s+model\\s*$", "");
    stripped = stripped.trim();
    if (stripped.isBlank()) {
      return "Domain";
    }
    return titleCase(stripped);
  }

  private boolean supports(ModelLevel level, String request) {
    if (request == null || request.isBlank() || !looksLikeCreation(request)) {
      return false;
    }
    String normalized = request.toLowerCase(Locale.ROOT);
    return switch (level) {
      case PIM -> SERVERLESS_INTENT.stream().anyMatch(normalized::contains);
      case CIM -> BUSINESS_INTENT.stream().anyMatch(normalized::contains);
      case PSM -> AWS_INTENT.stream().anyMatch(normalized::contains);
      default -> false;
    };
  }

  private SemanticModelPatch businessModel(String request) {
    String domain = deriveDomainName(request);
    String actorId = uuid();
    String capabilityId = uuid();
    String processId = uuid();
    List<SemanticModelPatch.Operation> operations = new ArrayList<>();
    operations.add(
        add(
            actorId,
            "Actor",
            attrs(
                "name",
                domain + " Operator",
                "actorType",
                "HUMAN",
                "description",
                "Primary actor interacting with the " + domain + " capability.")));
    operations.add(
        add(
            capabilityId,
            "BusinessCapability",
            attrs(
                "name",
                domain + " Management",
                "purpose",
                "Manage the core " + domain + " business capability.",
                "maturity",
                "INITIAL")));
    operations.add(
        add(
            processId,
            "BusinessProcess",
            attrs(
                "name",
                domain + " Core Process",
                "purpose",
                "Coordinate the main " + domain + " workflow.",
                "automationLevel",
                "PARTIALLY_AUTOMATED")));
    return new SemanticModelPatch(operations);
  }

  private SemanticModelPatch awsPlatform(String request) {
    String domain = deriveDomainName(request);
    String stackId = uuid();
    String apiId = uuid();
    String routeId = uuid();
    String functionId = uuid();
    String tableId = uuid();
    String busId = uuid();
    List<SemanticModelPatch.Operation> operations = new ArrayList<>();
    operations.add(
        add(
            stackId,
            "SamStack",
            attrs(
                "stackName",
                domain.replace(" ", "") + "Stack",
                "templateDescription",
                "AWS serverless stack for " + domain + ".")));
    operations.add(
        add(
            apiId,
            "HttpApi",
            attrs("apiName", domain + " API", "descriptionText", "Public HTTP API for " + domain),
            stackId,
            "resources"));
    operations.add(
        add(
            routeId,
            "HttpApiRoute",
            attrs(
                "routeKey",
                "POST /events",
                "operationName",
                "publishEvent",
                "authorizationType",
                "NONE"),
            stackId,
            "resources"));
    operations.add(
        add(
            functionId,
            "AwsLambdaFunction",
            attrs(
                "functionName",
                domain.replace(" ", "") + "Handler",
                "runtime",
                "java21",
                "handler",
                "com.example.Handler::handleRequest",
                "memorySize",
                512,
                "timeout",
                30),
            stackId,
            "resources"));
    operations.add(
        add(
            tableId,
            "DynamoDbTable",
            attrs(
                "tableName",
                domain.replace(" ", "") + "Table",
                "billingMode",
                "PAY_PER_REQUEST",
                "pointInTimeRecoveryEnabled",
                true),
            stackId,
            "resources"));
    operations.add(
        add(
            busId,
            "EventBridgeBus",
            attrs(
                "busName", domain.replace(" ", "") + "Bus", "descriptionText", domain + " events"),
            stackId,
            "resources"));
    connect(operations, routeId, functionId, "integration");
    return new SemanticModelPatch(operations);
  }

  private SemanticModelPatch observabilityPimPack(String request) {
    String domain = deriveDomainName(request);
    String eventId = uuid();
    List<SemanticModelPatch.Operation> operations = new ArrayList<>();
    operations.add(
        add(
            eventId,
            "EventType",
            attrs(
                "name",
                domain + "Observed",
                "description",
                "Domain observability event for " + domain + ".")));
    return new SemanticModelPatch(operations);
  }

  private SemanticModelPatch securityPimPack(String request) {
    String domain = deriveDomainName(request);
    String policyId = uuid();
    List<SemanticModelPatch.Operation> operations = new ArrayList<>();
    operations.add(
        add(
            policyId,
            "Policy",
            attrs(
                "name",
                domain + " Security Policy",
                "policyKind",
                "AUTHORIZATION",
                "description",
                "Baseline authorization policy for " + domain + ".")));
    return new SemanticModelPatch(operations);
  }

  private SemanticModelPatch observabilityPsmPack(String request) {
    String domain = deriveDomainName(request);
    String ruleId = uuid();
    List<SemanticModelPatch.Operation> operations = new ArrayList<>();
    operations.add(
        add(
            ruleId,
            "EventBridgeRule",
            attrs(
                "ruleName",
                domain.replace(" ", "") + "ObservabilityRule",
                "descriptionText",
                "Route operational events for " + domain + ".")));
    return new SemanticModelPatch(operations);
  }

  private SemanticModelPatch securityPsmPack(String request) {
    String domain = deriveDomainName(request);
    String authorizerId = uuid();
    List<SemanticModelPatch.Operation> operations = new ArrayList<>();
    operations.add(
        add(
            authorizerId,
            "ApiGatewayAuthorizer",
            attrs(
                "authorizerName",
                domain.replace(" ", "") + "Authorizer",
                "authorizerType",
                "JWT")));
    return new SemanticModelPatch(operations);
  }

  private SemanticModelPatch serverlessBackend(String request) {
    String domain = deriveDomainName(request);
    String serviceId = uuid();
    String apiId = uuid();
    String dispenseRouteId = uuid();
    String inventoryRouteId = uuid();
    String paymentRouteId = uuid();
    String dispenseFnId = uuid();
    String inventoryFnId = uuid();
    String paymentFnId = uuid();
    String storeId = uuid();

    List<SemanticModelPatch.Operation> operations = new ArrayList<>();
    operations.add(
        add(
            serviceId,
            "ServerlessService",
            attrs(
                "name",
                domain + " Service",
                "responsibility",
                "Owns the " + domain + " serverless backend capability.",
                "boundaryType",
                "CAPABILITY_BASED",
                "externallyExposed",
                true,
                "ownsData",
                true)));
    operations.add(
        add(
            apiId,
            "Api",
            attrs(
                "name",
                domain + " API",
                "publicName",
                domain + " API",
                "version",
                "1.0.0",
                "basePath",
                "/api/v1",
                "apiStyle",
                "RESOURCE_ORIENTED_HTTP",
                "authRequired",
                false,
                "externalConsumerFacing",
                true)));
    operations.add(
        add(
            dispenseRouteId,
            "ApiRoute",
            attrs(
                "name",
                "Dispense item",
                "pathTemplate",
                "/items/dispense",
                "method",
                "POST",
                "operationId",
                "dispenseItem",
                "publicRoute",
                true),
            apiId,
            "routes"));
    operations.add(
        add(
            inventoryRouteId,
            "ApiRoute",
            attrs(
                "name",
                "Read inventory",
                "pathTemplate",
                "/inventory",
                "method",
                "GET",
                "operationId",
                "getInventory",
                "publicRoute",
                true),
            apiId,
            "routes"));
    operations.add(
        add(
            paymentRouteId,
            "ApiRoute",
            attrs(
                "name",
                "Process payment",
                "pathTemplate",
                "/payments",
                "method",
                "POST",
                "operationId",
                "processPayment",
                "publicRoute",
                true),
            apiId,
            "routes"));
    operations.add(
        add(
            dispenseFnId,
            "Function",
            attrs(
                "name",
                "Dispense item",
                "handlerResponsibility",
                "Dispense a selected product and update stock.",
                "functionKind",
                "COMMAND_HANDLER",
                "writesState",
                true,
                "readsState",
                true,
                "publicEntryPoint",
                true)));
    operations.add(
        add(
            inventoryFnId,
            "Function",
            attrs(
                "name",
                "Read inventory",
                "handlerResponsibility",
                "Return current stock levels.",
                "functionKind",
                "QUERY_HANDLER",
                "readsState",
                true,
                "publicEntryPoint",
                true)));
    operations.add(
        add(
            paymentFnId,
            "Function",
            attrs(
                "name",
                "Process payment",
                "handlerResponsibility",
                "Authorize and capture payment for a purchase.",
                "functionKind",
                "COMMAND_HANDLER",
                "writesState",
                true,
                "readsState",
                true,
                "publicEntryPoint",
                true)));
    operations.add(
        add(
            storeId,
            "DataStore",
            attrs(
                "name",
                domain + " inventory store",
                "storeKind",
                "DOCUMENT",
                "consistencyNeed",
                "STRONG",
                "transactional",
                true,
                "persistent",
                true)));

    connect(operations, serviceId, dispenseFnId, "ownsFunctions");
    connect(operations, serviceId, inventoryFnId, "ownsFunctions");
    connect(operations, serviceId, paymentFnId, "ownsFunctions");
    connect(operations, serviceId, apiId, "ownsApis");
    connect(operations, serviceId, storeId, "ownsStores");
    connect(operations, dispenseRouteId, dispenseFnId, "functionIntegration");
    connect(operations, inventoryRouteId, inventoryFnId, "functionIntegration");
    connect(operations, paymentRouteId, paymentFnId, "functionIntegration");
    connect(operations, dispenseFnId, storeId, "reads");
    connect(operations, dispenseFnId, storeId, "writes");
    connect(operations, inventoryFnId, storeId, "reads");
    connect(operations, paymentFnId, storeId, "reads");
    connect(operations, paymentFnId, storeId, "writes");

    addMembership(operations, serviceId, dispenseFnId, domain);
    addMembership(operations, serviceId, inventoryFnId, domain);
    addMembership(operations, serviceId, paymentFnId, domain);
    addMembership(operations, serviceId, apiId, domain);
    addMembership(operations, serviceId, storeId, domain);

    return new SemanticModelPatch(operations);
  }

  private void addMembership(
      List<SemanticModelPatch.Operation> operations,
      String serviceId,
      String elementId,
      String domain) {
    String membershipId = uuid();
    operations.add(
        add(
            membershipId,
            "ServiceElementMembership",
            attrs("name", domain + " membership", "ownershipKind", "OWNS")));
    connect(operations, membershipId, serviceId, "service");
    connect(operations, membershipId, elementId, "element");
  }

  private void connect(
      List<SemanticModelPatch.Operation> operations,
      String sourceId,
      String targetId,
      String referenceName) {
    operations.add(
        new SemanticModelPatch.Operation(
            SemanticModelPatch.OperationType.CONNECT_ELEMENTS,
            targetId,
            null,
            null,
            sourceId,
            referenceName));
  }

  private SemanticModelPatch.Operation add(
      String id, String type, ObjectNode attributes, String ownerId, String reference) {
    return new SemanticModelPatch.Operation(
        SemanticModelPatch.OperationType.ADD_ELEMENT, id, type, attributes, ownerId, reference);
  }

  private SemanticModelPatch.Operation add(String id, String type, ObjectNode attributes) {
    return add(id, type, attributes, null, null);
  }

  private ObjectNode attrs(Object... keyValues) {
    ObjectNode node = JsonNodeFactory.instance.objectNode();
    for (int index = 0; index < keyValues.length; index += 2) {
      String key = String.valueOf(keyValues[index]);
      Object value = keyValues[index + 1];
      if (value instanceof Boolean booleanValue) {
        node.put(key, booleanValue);
      } else if (value instanceof Integer integerValue) {
        node.put(key, integerValue);
      } else {
        node.put(key, String.valueOf(value));
      }
    }
    return node;
  }

  private String titleCase(String value) {
    String[] parts = value.split("[^a-z0-9]+");
    StringBuilder builder = new StringBuilder();
    for (String part : parts) {
      if (part.isBlank()) {
        continue;
      }
      if (!builder.isEmpty()) {
        builder.append(' ');
      }
      builder
          .append(Character.toUpperCase(part.charAt(0)))
          .append(part.length() > 1 ? part.substring(1) : "");
    }
    return builder.isEmpty() ? "Domain" : builder.toString();
  }

  private boolean looksLikeCreation(String request) {
    return request
        .toLowerCase(Locale.ROOT)
        .matches("(?s).*(\\bcreate\\b|\\bbuild\\b|\\bdesign\\b|\\bscaffold\\b|\\bmodel\\b).*");
  }

  private String uuid() {
    return UUID.randomUUID().toString();
  }
}
