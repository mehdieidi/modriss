package io.mehdieidi.modless.platform.assistant.application;

import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.mehdieidi.modless.platform.assistant.domain.SemanticModelPatch;
import io.mehdieidi.modless.platform.assistant.patch.AssistantMetamodelSchemaService;
import io.mehdieidi.modless.platform.kernel.ModelLevel;
import io.mehdieidi.modless.platform.kernel.PlatformException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Turns LLM-classified CIM source evidence into formally typed semantic operations. */
final class CimSourceModelMaterializer {

  private final AssistantMetamodelSchemaService schemas;
  private final ObjectMapper mapper;
  private final ObjectMapper tolerantMapper;

  CimSourceModelMaterializer(AssistantMetamodelSchemaService schemas, ObjectMapper mapper) {
    this.schemas = schemas;
    this.mapper = mapper;
    this.tolerantMapper =
        mapper
            .copy()
            .enable(
                JsonReadFeature.ALLOW_TRAILING_COMMA.mappedFeature(),
                JsonReadFeature.ALLOW_SINGLE_QUOTES.mappedFeature(),
                JsonReadFeature.ALLOW_JAVA_COMMENTS.mappedFeature(),
                JsonReadFeature.ALLOW_YAML_COMMENTS.mappedFeature());
  }

  Optional<Result> materialize(String sourceAnalysis) {
    Optional<JsonNode> parsed = parseJsonObject(sourceAnalysis);
    JsonNode root = parsed.orElseGet(mapper::createObjectNode);
    JsonNode elementsNode = firstArray(root, "elements", "cimElements", "modelElements");
    if (elementsNode.isMissingNode() || elementsNode.isEmpty()) {
      elementsNode =
          salvageArrayObjects(sourceAnalysis, "elements", "cimElements", "modelElements");
    }
    if (elementsNode.isMissingNode() || elementsNode.isEmpty()) {
      return Optional.empty();
    }
    Map<String, String> localIds = new LinkedHashMap<>();
    Map<String, String> localTypes = new LinkedHashMap<>();
    List<SemanticModelPatch.Operation> operations = new ArrayList<>();
    for (JsonNode element : elementsNode) {
      Optional<SemanticModelPatch.Operation> created = createElement(element);
      if (created.isEmpty()) {
        continue;
      }
      SemanticModelPatch.Operation operation = created.get();
      operations.add(operation);
      String localKey = localKey(element, operation);
      if (!localKey.isBlank()) {
        localIds.put(localKey, operation.targetElementId());
        localTypes.put(localKey, operation.elementType());
      }
    }
    ensureDomainEntityIdentityItems(operations, localIds, localTypes);
    ensureQueryOutputItems(operations, localIds, localTypes);
    ensureProcessStartEndSteps(operations, localIds, localTypes);
    ensureHumanActorRoles(operations, localIds, localTypes);
    ensureCommandBusinessErrors(operations, localIds, localTypes);
    JsonNode relationshipsNode = firstArray(root, "relationships", "links", "references");
    if (relationshipsNode.isMissingNode() || relationshipsNode.isEmpty()) {
      relationshipsNode =
          salvageArrayObjects(sourceAnalysis, "relationships", "links", "references");
    }
    for (JsonNode relationship : relationshipsNode) {
      createRelationship(relationship, localIds, localTypes).ifPresent(operations::add);
    }
    addSchemaGroundedCimLinks(operations, localIds, localTypes);
    operations = ensureRequiredLocalReferences(operations);
    return operations.isEmpty()
        ? Optional.empty()
        : Optional.of(
            new Result(
                new SemanticModelPatch(operations),
                coverageSummary(root, operations),
                coverageGaps(root)));
  }

  private void ensureDomainEntityIdentityItems(
      List<SemanticModelPatch.Operation> operations,
      Map<String, String> localIds,
      Map<String, String> localTypes) {
    List<SemanticModelPatch.Operation> entities =
        operations.stream()
            .filter(operation -> operation.type() == SemanticModelPatch.OperationType.ADD_ELEMENT)
            .filter(operation -> isType(operation.elementType(), "DomainEntity"))
            .toList();
    for (SemanticModelPatch.Operation entity : entities) {
      if (hasRelationship(operations, entity.targetElementId(), "identityAttributes")
          && hasRelationship(operations, entity.targetElementId(), "primaryIdentityAttribute")) {
        continue;
      }
      String name = entity.attributes() == null ? "" : entity.attributes().path("name").asText("");
      String identityName =
          (name == null || name.isBlank() ? "Domain entity" : name.trim()) + " identity";
      String identityId = java.util.UUID.randomUUID().toString();
      ObjectNode attributes = mapper.createObjectNode();
      attributes.put("name", identityName);
      attributes.put("summary", "Business identity attribute synthesized for " + name + ".");
      attributes.put(
          "rationale", "Required so the domain entity has an explicit business identity.");
      if (schemas.attribute(ModelLevel.CIM, "InformationItem", "type").isPresent()) {
        attributes.put("type", "TEXT");
      }
      operations.add(
          new SemanticModelPatch.Operation(
              SemanticModelPatch.OperationType.ADD_ELEMENT,
              identityId,
              "InformationItem",
              attributes,
              null,
              null));
      operations.add(
          new SemanticModelPatch.Operation(
              SemanticModelPatch.OperationType.CONNECT_ELEMENTS,
              identityId,
              null,
              null,
              entity.targetElementId(),
              "identityAttributes"));
      operations.add(
          new SemanticModelPatch.Operation(
              SemanticModelPatch.OperationType.CONNECT_ELEMENTS,
              identityId,
              null,
              null,
              entity.targetElementId(),
              "attributes"));
      operations.add(
          new SemanticModelPatch.Operation(
              SemanticModelPatch.OperationType.CONNECT_ELEMENTS,
              identityId,
              null,
              null,
              entity.targetElementId(),
              "primaryIdentityAttribute"));
      String localKey = "identity-" + entity.targetElementId();
      localIds.put(localKey, identityId);
      localTypes.put(localKey, "InformationItem");
    }
  }

  private void ensureQueryOutputItems(
      List<SemanticModelPatch.Operation> operations,
      Map<String, String> localIds,
      Map<String, String> localTypes) {
    List<SemanticModelPatch.Operation> queries =
        operations.stream()
            .filter(operation -> operation.type() == SemanticModelPatch.OperationType.ADD_ELEMENT)
            .filter(operation -> isType(operation.elementType(), "Query"))
            .toList();
    for (SemanticModelPatch.Operation query : queries) {
      if (hasRelationship(operations, query.targetElementId(), "output")) {
        continue;
      }
      String name = query.attributes() == null ? "" : query.attributes().path("name").asText("");
      String outputName = (name == null || name.isBlank() ? "Query" : name.trim()) + " output";
      String outputId = java.util.UUID.randomUUID().toString();
      ObjectNode attributes = mapper.createObjectNode();
      attributes.put("name", outputName);
      attributes.put("summary", "Information returned by " + name + ".");
      attributes.put("rationale", "Required so the query has an explicit output contract.");
      if (schemas.attribute(ModelLevel.CIM, "InformationItem", "type").isPresent()) {
        attributes.put("type", "TEXT");
      }
      operations.add(
          new SemanticModelPatch.Operation(
              SemanticModelPatch.OperationType.ADD_ELEMENT,
              outputId,
              "InformationItem",
              attributes,
              null,
              null));
      operations.add(
          new SemanticModelPatch.Operation(
              SemanticModelPatch.OperationType.CONNECT_ELEMENTS,
              outputId,
              null,
              null,
              query.targetElementId(),
              "output"));
      String localKey = "output-" + query.targetElementId();
      localIds.put(localKey, outputId);
      localTypes.put(localKey, "InformationItem");
    }
  }

  private void ensureProcessStartEndSteps(
      List<SemanticModelPatch.Operation> operations,
      Map<String, String> localIds,
      Map<String, String> localTypes) {
    List<SemanticModelPatch.Operation> processes =
        operations.stream()
            .filter(operation -> operation.type() == SemanticModelPatch.OperationType.ADD_ELEMENT)
            .filter(operation -> isType(operation.elementType(), "BusinessProcess"))
            .toList();
    for (SemanticModelPatch.Operation process : processes) {
      if (hasContainedChild(operations, process.targetElementId(), "steps", "StartStep")
          && hasContainedChild(operations, process.targetElementId(), "steps", "EndStep")) {
        continue;
      }
      String name =
          process.attributes() == null ? "" : process.attributes().path("name").asText("");
      String processName = name == null || name.isBlank() ? "Business process" : name.trim();
      String startId = java.util.UUID.randomUUID().toString();
      ObjectNode startAttributes = mapper.createObjectNode();
      startAttributes.put("name", processName + " start");
      startAttributes.put("summary", "Start of " + processName + ".");
      startAttributes.put("rationale", "Required process entry point.");
      startAttributes.put("stepKind", "START");
      startAttributes.put("orderIndex", 1);
      startAttributes.put("optional", false);
      startAttributes.put("repeatable", false);
      operations.add(
          new SemanticModelPatch.Operation(
              SemanticModelPatch.OperationType.ADD_ELEMENT,
              startId,
              "StartStep",
              startAttributes,
              process.targetElementId(),
              "steps"));
      String endId = java.util.UUID.randomUUID().toString();
      ObjectNode endAttributes = mapper.createObjectNode();
      endAttributes.put("name", processName + " complete");
      endAttributes.put("summary", "Completion of " + processName + ".");
      endAttributes.put("rationale", "Required process exit point.");
      endAttributes.put("stepKind", "END");
      endAttributes.put("orderIndex", 99);
      endAttributes.put("optional", false);
      endAttributes.put("repeatable", false);
      operations.add(
          new SemanticModelPatch.Operation(
              SemanticModelPatch.OperationType.ADD_ELEMENT,
              endId,
              "EndStep",
              endAttributes,
              process.targetElementId(),
              "steps"));
      localIds.put("start-" + process.targetElementId(), startId);
      localTypes.put("start-" + process.targetElementId(), "StartStep");
      localIds.put("end-" + process.targetElementId(), endId);
      localTypes.put("end-" + process.targetElementId(), "EndStep");
    }
  }

  private void ensureHumanActorRoles(
      List<SemanticModelPatch.Operation> operations,
      Map<String, String> localIds,
      Map<String, String> localTypes) {
    List<SemanticModelPatch.Operation> actors =
        operations.stream()
            .filter(operation -> operation.type() == SemanticModelPatch.OperationType.ADD_ELEMENT)
            .filter(operation -> isType(operation.elementType(), "Actor"))
            .filter(operation -> !isType(operation.elementType(), "ExternalSystem"))
            .filter(
                operation ->
                    operation.attributes() == null
                        || "HUMAN".equals(operation.attributes().path("actorType").asText("HUMAN")))
            .toList();
    for (SemanticModelPatch.Operation actor : actors) {
      if (hasRelationship(operations, actor.targetElementId(), "playsRoles")) {
        continue;
      }
      String name = actor.attributes() == null ? "" : actor.attributes().path("name").asText("");
      String actorName = name == null || name.isBlank() ? "Actor" : name.trim();
      String roleId = java.util.UUID.randomUUID().toString();
      ObjectNode attributes = mapper.createObjectNode();
      attributes.put("name", actorName + " role");
      attributes.put("summary", "Business responsibility assigned to " + actorName + ".");
      attributes.put(
          "rationale", "Synthesized so human actor permissions and responsibilities are explicit.");
      attributes.put(
          "responsibility", "Performs source-described business work for " + actorName + ".");
      attributes.put(
          "businessPermissionSummary",
          "May issue and read the modeled interactions assigned to " + actorName + ".");
      attributes.put("privileged", false);
      operations.add(
          new SemanticModelPatch.Operation(
              SemanticModelPatch.OperationType.ADD_ELEMENT,
              roleId,
              "Role",
              attributes,
              null,
              null));
      operations.add(
          new SemanticModelPatch.Operation(
              SemanticModelPatch.OperationType.CONNECT_ELEMENTS,
              roleId,
              null,
              null,
              actor.targetElementId(),
              "playsRoles"));
      localIds.put("role-" + actor.targetElementId(), roleId);
      localTypes.put("role-" + actor.targetElementId(), "Role");
    }
  }

  private void ensureCommandBusinessErrors(
      List<SemanticModelPatch.Operation> operations,
      Map<String, String> localIds,
      Map<String, String> localTypes) {
    List<SemanticModelPatch.Operation> commands =
        operations.stream()
            .filter(operation -> operation.type() == SemanticModelPatch.OperationType.ADD_ELEMENT)
            .filter(operation -> isType(operation.elementType(), "Command"))
            .toList();
    for (SemanticModelPatch.Operation command : commands) {
      if (hasRelationship(operations, command.targetElementId(), "possibleErrors")) {
        continue;
      }
      String name =
          command.attributes() == null ? "" : command.attributes().path("name").asText("");
      String commandName = name == null || name.isBlank() ? "Command" : name.trim();
      String errorId = java.util.UUID.randomUUID().toString();
      ObjectNode attributes = mapper.createObjectNode();
      attributes.put("name", commandName + " rejected");
      attributes.put("summary", "Business rejection for " + commandName + ".");
      attributes.put("rationale", "Synthesized so command rejection semantics are explicit.");
      attributes.put(
          "errorCode",
          commandName.toUpperCase(java.util.Locale.ROOT).replaceAll("[^A-Z0-9]+", "_")
              + "_REJECTED");
      attributes.put(
          "businessMeaning",
          "The command cannot be accepted because a required business condition is not satisfied.");
      attributes.put(
          "userVisibleMessage",
          "The request cannot be completed until the required business conditions are satisfied.");
      attributes.put("recoverable", true);
      attributes.put("retryMeaningful", true);
      attributes.put("auditRequired", true);
      operations.add(
          new SemanticModelPatch.Operation(
              SemanticModelPatch.OperationType.ADD_ELEMENT,
              errorId,
              "BusinessError",
              attributes,
              null,
              null));
      operations.add(
          new SemanticModelPatch.Operation(
              SemanticModelPatch.OperationType.CONNECT_ELEMENTS,
              errorId,
              null,
              null,
              command.targetElementId(),
              "possibleErrors"));
      localIds.put("error-" + command.targetElementId(), errorId);
      localTypes.put("error-" + command.targetElementId(), "BusinessError");
    }
  }

  private Optional<SemanticModelPatch.Operation> createElement(JsonNode element) {
    String rawType = firstText(element, "type", "eClass", "metamodelType");
    if (rawType.isBlank()) {
      return Optional.empty();
    }
    String type;
    try {
      type = schemas.canonicalType(ModelLevel.CIM, rawType);
    } catch (PlatformException failure) {
      return Optional.empty();
    }
    boolean creatable =
        schemas
            .typeSchema(ModelLevel.CIM, type)
            .map(AssistantMetamodelSchemaService.TypeSchema::creatable)
            .orElse(false);
    if (!creatable) {
      return Optional.empty();
    }
    ObjectNode attributes = mapper.createObjectNode();
    mergeAttributes(type, element.path("attributes"), attributes);
    mergeDirectAttributes(type, element, attributes);
    ensureMeaningfulLabel(type, element, attributes);
    enrichCimAttributes(type, element, attributes);
    String id = java.util.UUID.randomUUID().toString();
    return Optional.of(
        new SemanticModelPatch.Operation(
            SemanticModelPatch.OperationType.ADD_ELEMENT, id, type, attributes, null, null));
  }

  private Optional<SemanticModelPatch.Operation> createRelationship(
      JsonNode relationship, Map<String, String> localIds, Map<String, String> localTypes) {
    String sourceKey = firstText(relationship, "source", "sourceKey", "from");
    String targetKey = firstText(relationship, "target", "targetKey", "to");
    String sourceId = localIds.getOrDefault(sourceKey, sourceKey);
    String targetId = localIds.getOrDefault(targetKey, targetKey);
    String reference = firstText(relationship, "referenceName", "feature", "reference");
    if (sourceId.isBlank() || targetId.isBlank() || reference.isBlank()) {
      return Optional.empty();
    }
    String sourceType = localTypes.get(sourceKey);
    String targetType = localTypes.get(targetKey);
    if (sourceType != null
        && targetType != null
        && !schemas.acceptsReferenceTarget(ModelLevel.CIM, sourceType, reference, targetType)) {
      return Optional.empty();
    }
    return Optional.of(
        new SemanticModelPatch.Operation(
            SemanticModelPatch.OperationType.CONNECT_ELEMENTS,
            targetId,
            null,
            null,
            sourceId,
            reference));
  }

  private List<SemanticModelPatch.Operation> ensureRequiredLocalReferences(
      List<SemanticModelPatch.Operation> operations) {
    Map<String, String> typesById = new LinkedHashMap<>();
    for (SemanticModelPatch.Operation operation : operations) {
      if (operation == null
          || operation.type() != SemanticModelPatch.OperationType.ADD_ELEMENT
          || operation.targetElementId() == null
          || operation.targetElementId().isBlank()) {
        continue;
      }
      try {
        typesById.put(
            operation.targetElementId(),
            schemas.canonicalType(ModelLevel.CIM, operation.elementType()));
      } catch (PlatformException ignored) {
        typesById.put(operation.targetElementId(), operation.elementType());
      }
    }
    List<SemanticModelPatch.Operation> result = new ArrayList<>(operations);
    for (Map.Entry<String, String> entry : typesById.entrySet()) {
      String sourceId = entry.getKey();
      String sourceType = entry.getValue();
      schemas
          .typeSchema(ModelLevel.CIM, sourceType)
          .ifPresent(
              type ->
                  type.references().stream()
                      .filter(AssistantMetamodelSchemaService.ReferenceSchema::required)
                      .filter(reference -> !reference.containment())
                      .filter(reference -> !reference.readonly())
                      .filter(reference -> !hasRelationship(result, sourceId, reference.name()))
                      .forEach(
                          reference ->
                              firstCompatibleTarget(sourceId, reference, typesById)
                                  .ifPresent(
                                      targetId ->
                                          result.add(
                                              new SemanticModelPatch.Operation(
                                                  SemanticModelPatch.OperationType.CONNECT_ELEMENTS,
                                                  targetId,
                                                  null,
                                                  null,
                                                  sourceId,
                                                  reference.name())))));
    }
    return result;
  }

  private void addSchemaGroundedCimLinks(
      List<SemanticModelPatch.Operation> operations,
      Map<String, String> localIds,
      Map<String, String> localTypes) {
    Map<String, String> typesById = elementTypesById(localIds, localTypes);
    List<String> actors = idsOf(typesById, "Actor");
    List<String> externalSystems = idsOf(typesById, "ExternalSystem");
    List<String> stakeholders = idsOf(typesById, "Stakeholder");
    List<String> goals = idsOf(typesById, "BusinessGoal");
    List<String> requirements = idsOf(typesById, "Requirement");
    List<String> commands = idsOf(typesById, "Command");
    List<String> queries = idsOf(typesById, "Query");
    List<String> events = idsOf(typesById, "BusinessEvent");
    List<String> policies = idsOf(typesById, "Policy");
    List<String> processes = idsOf(typesById, "BusinessProcess");
    List<String> entities = idsOf(typesById, "DomainEntity");
    List<String> risks = idsOf(typesById, "Risk");

    first(stakeholders)
        .ifPresent(
            stakeholder -> {
              goals.forEach(
                  goal -> connectIfValid(operations, typesById, stakeholder, goal, "ownsGoals"));
              requirements.forEach(
                  requirement ->
                      connectIfValid(
                          operations, typesById, stakeholder, requirement, "providesRequirements"));
            });
    first(goals)
        .ifPresent(
            goal ->
                requirements.forEach(
                    requirement ->
                        connectIfValid(operations, typesById, requirement, goal, "supportsGoals")));
    first(goals)
        .ifPresent(
            goal ->
                idsOf(typesById, "BusinessCapability")
                    .forEach(
                        capability ->
                            connectIfValid(operations, typesById, capability, goal, "supports")));
    first(actors)
        .ifPresent(
            actor -> {
              commands.forEach(
                  command ->
                      connectIfValid(operations, typesById, actor, command, "issuesCommands"));
              queries.forEach(
                  query -> connectIfValid(operations, typesById, actor, query, "issuesQueries"));
              events.forEach(
                  event -> connectIfValid(operations, typesById, actor, event, "observesEvents"));
            });
    for (int index = 0; index < commands.size(); index++) {
      if (!events.isEmpty()) {
        connectIfValid(
            operations,
            typesById,
            commands.get(index),
            events.get(index % events.size()),
            "expectedEvents");
      }
    }
    first(commands)
        .ifPresent(
            command ->
                policies.forEach(
                    policy -> connectIfValid(operations, typesById, policy, command, "guards")));
    first(events)
        .ifPresent(
            event ->
                policies.forEach(
                    policy -> connectIfValid(operations, typesById, policy, event, "triggeredBy")));
    first(actors)
        .ifPresent(
            actor ->
                processes.forEach(
                    process ->
                        connectIfValid(operations, typesById, process, actor, "triggeringActor")));
    first(commands)
        .ifPresent(
            command ->
                processes.forEach(
                    process ->
                        connectIfValid(
                            operations, typesById, process, command, "triggeringCommand")));
    List<String> capabilities = idsOf(typesById, "BusinessCapability");
    if (!capabilities.isEmpty()) {
      for (int index = 0; index < commands.size(); index++) {
        String capability = capabilities.get(index % capabilities.size());
        connectIfValid(operations, typesById, capability, commands.get(index), "containsCommands");
        connectIfValid(operations, typesById, commands.get(index), capability, "targetCapability");
      }
      for (int index = 0; index < queries.size(); index++) {
        String capability = capabilities.get(index % capabilities.size());
        connectIfValid(operations, typesById, capability, queries.get(index), "containsQueries");
        connectIfValid(operations, typesById, queries.get(index), capability, "targetCapability");
      }
      for (int index = 0; index < events.size(); index++) {
        connectIfValid(
            operations,
            typesById,
            capabilities.get(index % capabilities.size()),
            events.get(index),
            "containsEvents");
      }
      for (int index = 0; index < entities.size(); index++) {
        String capability = capabilities.get(index % capabilities.size());
        connectIfValid(operations, typesById, capability, entities.get(index), "managesEntities");
        connectIfValid(operations, typesById, entities.get(index), capability, "owningCapability");
      }
      for (int index = 0; index < processes.size(); index++) {
        String capability = capabilities.get(index % capabilities.size());
        connectIfValid(operations, typesById, capability, processes.get(index), "ownsProcesses");
        connectIfValid(operations, typesById, processes.get(index), capability, "owningCapability");
      }
    }
    externalSystems.forEach(
        system ->
            events.forEach(
                event -> connectIfValid(operations, typesById, system, event, "producedEvents")));
    List<String> riskTargets = new ArrayList<>();
    riskTargets.addAll(capabilities);
    riskTargets.addAll(externalSystems);
    riskTargets.addAll(goals);
    riskTargets.addAll(commands);
    for (int index = 0; index < risks.size() && !riskTargets.isEmpty(); index++) {
      connectIfValid(
          operations,
          typesById,
          risks.get(index),
          riskTargets.get(index % riskTargets.size()),
          "affectedElements");
    }
  }

  private Map<String, String> elementTypesById(
      Map<String, String> localIds, Map<String, String> localTypes) {
    Map<String, String> result = new LinkedHashMap<>();
    for (Map.Entry<String, String> entry : localTypes.entrySet()) {
      String elementId = localIds.get(entry.getKey());
      if (elementId != null && !elementId.isBlank()) {
        result.put(elementId, entry.getValue());
      }
    }
    return result;
  }

  private List<String> idsOf(Map<String, String> typesById, String type) {
    return typesById.entrySet().stream()
        .filter(entry -> isType(entry.getValue(), type))
        .map(Map.Entry::getKey)
        .toList();
  }

  private boolean isType(String actual, String expected) {
    try {
      return schemas.canonicalType(ModelLevel.CIM, actual).equals(expected);
    } catch (PlatformException ignored) {
      return expected.equals(actual);
    }
  }

  private Optional<String> first(List<String> values) {
    return values == null || values.isEmpty() ? Optional.empty() : Optional.of(values.get(0));
  }

  private void connectIfValid(
      List<SemanticModelPatch.Operation> operations,
      Map<String, String> typesById,
      String sourceId,
      String targetId,
      String referenceName) {
    if (sourceId == null
        || targetId == null
        || sourceId.equals(targetId)
        || hasRelationship(operations, sourceId, referenceName, targetId)) {
      return;
    }
    String sourceType = typesById.get(sourceId);
    String targetType = typesById.get(targetId);
    if (sourceType == null
        || targetType == null
        || !schemas.acceptsReferenceTarget(ModelLevel.CIM, sourceType, referenceName, targetType)) {
      return;
    }
    operations.add(
        new SemanticModelPatch.Operation(
            SemanticModelPatch.OperationType.CONNECT_ELEMENTS,
            targetId,
            null,
            null,
            sourceId,
            referenceName));
  }

  private Optional<String> firstCompatibleTarget(
      String sourceId,
      AssistantMetamodelSchemaService.ReferenceSchema reference,
      Map<String, String> typesById) {
    return typesById.entrySet().stream()
        .filter(entry -> !entry.getKey().equals(sourceId))
        .filter(
            entry ->
                schemas.acceptsReferenceTarget(
                    ModelLevel.CIM, typesById.get(sourceId), reference.name(), entry.getValue()))
        .map(Map.Entry::getKey)
        .findFirst();
  }

  private boolean hasRelationship(
      List<SemanticModelPatch.Operation> operations, String sourceId, String referenceName) {
    return operations.stream()
        .anyMatch(
            operation ->
                operation != null
                    && operation.type() == SemanticModelPatch.OperationType.CONNECT_ELEMENTS
                    && sourceId.equals(operation.sourceElementId())
                    && referenceName.equals(operation.referenceName()));
  }

  private boolean hasContainedChild(
      List<SemanticModelPatch.Operation> operations,
      String sourceId,
      String referenceName,
      String type) {
    return operations.stream()
        .anyMatch(
            operation ->
                operation != null
                    && operation.type() == SemanticModelPatch.OperationType.ADD_ELEMENT
                    && sourceId.equals(operation.sourceElementId())
                    && referenceName.equals(operation.referenceName())
                    && isType(operation.elementType(), type));
  }

  private boolean hasRelationship(
      List<SemanticModelPatch.Operation> operations,
      String sourceId,
      String referenceName,
      String targetId) {
    return operations.stream()
        .anyMatch(
            operation ->
                operation != null
                    && operation.type() == SemanticModelPatch.OperationType.CONNECT_ELEMENTS
                    && sourceId.equals(operation.sourceElementId())
                    && referenceName.equals(operation.referenceName())
                    && targetId.equals(operation.targetElementId()));
  }

  private String coverageSummary(JsonNode root, List<SemanticModelPatch.Operation> operations) {
    int additions = 0;
    int connections = 0;
    Map<String, Integer> byType = new LinkedHashMap<>();
    for (SemanticModelPatch.Operation operation : operations) {
      if (operation == null || operation.type() == null) {
        continue;
      }
      if (operation.type() == SemanticModelPatch.OperationType.ADD_ELEMENT) {
        additions++;
        byType.merge(operation.elementType(), 1, Integer::sum);
      } else if (operation.type() == SemanticModelPatch.OperationType.CONNECT_ELEMENTS) {
        connections++;
      }
    }
    String notes = textArray(root.path("coverageNotes"));
    String typeSummary =
        byType.entrySet().stream()
            .map(entry -> entry.getKey() + "=" + entry.getValue())
            .collect(java.util.stream.Collectors.joining(", "));
    return "Coverage summary: modeled "
        + additions
        + " source-backed CIM elements and "
        + connections
        + " relationships"
        + (typeSummary.isBlank() ? "." : " (" + typeSummary + ").")
        + (notes.isBlank() ? "" : "\nSource coverage notes: " + notes);
  }

  private List<String> coverageGaps(JsonNode root) {
    JsonNode gaps = firstArray(root, "coverageGaps", "gaps", "openQuestions");
    if (gaps.isMissingNode() || gaps.isEmpty()) {
      return List.of();
    }
    List<String> result = new ArrayList<>();
    gaps.forEach(
        gap -> {
          String text = gap.isTextual() ? gap.asText("") : firstText(gap, "summary", "name", "gap");
          if (!text.isBlank()) {
            result.add(text.trim());
          }
        });
    return List.copyOf(result);
  }

  private String textArray(JsonNode node) {
    if (node == null || !node.isArray()) {
      return "";
    }
    List<String> values = new ArrayList<>();
    node.forEach(
        item -> {
          String text = item.isTextual() ? item.asText("") : firstText(item, "summary", "name");
          if (!text.isBlank()) {
            values.add(text.trim());
          }
        });
    return String.join("; ", values);
  }

  private void mergeAttributes(String type, JsonNode source, ObjectNode target) {
    if (source == null || !source.isObject()) {
      return;
    }
    source
        .fields()
        .forEachRemaining(
            entry -> {
              if (schemas.attribute(ModelLevel.CIM, type, entry.getKey()).isPresent()) {
                target.set(entry.getKey(), entry.getValue().deepCopy());
              }
            });
  }

  private void mergeDirectAttributes(String type, JsonNode source, ObjectNode target) {
    if (source == null || !source.isObject()) {
      return;
    }
    source
        .fields()
        .forEachRemaining(
            entry -> {
              if (!entry.getValue().isValueNode() || reservedElementField(entry.getKey())) {
                return;
              }
              if (schemas.attribute(ModelLevel.CIM, type, entry.getKey()).isPresent()) {
                target.set(entry.getKey(), entry.getValue().deepCopy());
              }
            });
  }

  private void ensureMeaningfulLabel(String type, JsonNode source, ObjectNode attributes) {
    if (attributes.hasNonNull("name") && !attributes.path("name").asText("").isBlank()) {
      return;
    }
    String name = firstText(source, "name", "label", "title");
    if (name.isBlank()) {
      name = firstText(source, "summary", "description", "sourceExcerpt");
    }
    if (name.isBlank()) {
      name = "Modeled " + type.replaceAll("([a-z])([A-Z])", "$1 $2");
    }
    if (schemas.attribute(ModelLevel.CIM, type, "name").isPresent()) {
      attributes.put("name", name);
    } else if (schemas.attribute(ModelLevel.CIM, type, "summary").isPresent()) {
      attributes.put("summary", name);
    }
  }

  private void enrichCimAttributes(String type, JsonNode source, ObjectNode attributes) {
    String name = attributes.path("name").asText(type);
    String evidence = firstText(source, "sourceExcerpt", "summary", "description", "name");
    String basis = evidence.isBlank() ? name : evidence;
    putTextIfMissing(type, attributes, "summary", basis);
    putTextIfMissing(type, attributes, "description", basis);
    putTextIfMissing(type, attributes, "rationale", "Derived from source evidence: " + basis);
    switch (type) {
      case "Requirement" -> {
        putTextIfMissing(type, attributes, "fitCriterion", "Satisfied when: " + basis);
        putBooleanIfMissing(type, attributes, "mandatory", true);
        putBooleanIfMissing(type, attributes, "productionBlocking", false);
        putTextIfMissing(type, attributes, "requirementType", "FUNCTIONAL");
        putTextIfMissing(type, attributes, "sourceType", "WORKSHOP");
        putTextIfMissing(type, attributes, "priority", "MEDIUM");
      }
      case "BusinessGoal" -> {
        putTextIfMissing(
            type, attributes, "successCriterion", "The business outcome is achieved: " + basis);
        putTextIfMissing(
            type, attributes, "businessValue", "Improves the modeled business outcome.");
        putTextIfMissing(
            type, attributes, "failureConsequence", "The business goal remains unmet.");
        putTextIfMissing(type, attributes, "priority", "MEDIUM");
      }
      case "Stakeholder" -> {
        putTextIfMissing(type, attributes, "stakeholderType", "Business stakeholder");
        putTextIfMissing(type, attributes, "concern", basis);
      }
      case "Actor" -> {
        putTextIfMissing(type, attributes, "actorType", "HUMAN");
        putTextIfMissing(type, attributes, "trustLevel", "TRUSTED_INTERNAL");
        putTextIfMissing(type, attributes, "interactionExpectation", "IMMEDIATE_RESPONSE_EXPECTED");
      }
      case "ExternalSystem" -> {
        putTextIfMissing(type, attributes, "actorType", "EXTERNAL_SYSTEM");
        putTextIfMissing(type, attributes, "trustLevel", "PARTIALLY_TRUSTED");
        putTextIfMissing(type, attributes, "interactionExpectation", "NOTIFICATION_EXPECTED");
        putTextIfMissing(
            type, attributes, "authenticationExpectation", "Authenticated integration channel.");
        putTextIfMissing(
            type,
            attributes,
            "authorizationExpectation",
            "Only approved integration actions are allowed.");
        putTextIfMissing(type, attributes, "owningOrganization", "External provider");
        putTextIfMissing(type, attributes, "businessPurpose", basis);
        putTextIfMissing(
            type,
            attributes,
            "trustRationale",
            "External system trust must be confirmed during transformation.");
      }
      case "BusinessCapability" -> {
        putTextIfMissing(
            type, attributes, "responsibility", "Owns business behavior for " + name + ".");
        putTextIfMissing(type, attributes, "ownerName", "Business owner to confirm");
        putTextIfMissing(type, attributes, "criticality", "IMPORTANT");
      }
      case "DomainEntity" -> {
        putTextIfMissing(type, attributes, "identityStrategy", "NATURAL_KEY");
        putTextIfMissing(type, attributes, "businessOwner", "Business owner to confirm");
        putTextIfMissing(
            type,
            attributes,
            "identityDescription",
            "Identified by source-derived business identity.");
        putBooleanIfMissing(type, attributes, "auditRelevant", false);
      }
      case "Command" -> {
        putTextIfMissing(type, attributes, "intent", basis);
        putBooleanIfMissing(type, attributes, "userInitiated", true);
        putBooleanIfMissing(type, attributes, "authorizationRequired", true);
        putTextIfMissing(
            type,
            attributes,
            "authorizationRule",
            "Authorized actor may issue this command for the modeled business process.");
        putBooleanIfMissing(type, attributes, "auditRequired", true);
        putTextIfMissing(type, attributes, "commandType", "USER_INTENT");
        putTextIfMissing(type, attributes, "interactionExpectation", "IMMEDIATE_RESPONSE_EXPECTED");
        putTextIfMissing(type, attributes, "priority", "MEDIUM");
      }
      case "Query" -> {
        putTextIfMissing(type, attributes, "intent", basis);
        putBooleanIfMissing(type, attributes, "authorizationRequired", true);
        putTextIfMissing(
            type, attributes, "authorizationRule", "Authorized actor may read this query output.");
        putBooleanIfMissing(type, attributes, "auditRequired", true);
        putBooleanIfMissing(type, attributes, "containsPersonalData", false);
        putTextIfMissing(
            type, attributes, "paginationExpectation", "Pageable when result size is large.");
        putTextIfMissing(
            type,
            attributes,
            "filteringExpectation",
            "Filter by business-relevant fields from the source.");
        putTextIfMissing(type, attributes, "queryType", "LOOKUP");
        putTextIfMissing(type, attributes, "freshnessNeed", "NEAR_REAL_TIME");
      }
      case "BusinessEvent" -> {
        putTextIfMissing(type, attributes, "semanticName", name);
        putTextIfMissing(type, attributes, "occurredInPastTenseName", name);
        putTextIfMissing(type, attributes, "businessMeaning", basis);
        putTextIfMissing(type, attributes, "eventTimeSemantics", "BUSINESS_TIME");
      }
      case "BusinessProcess" -> {
        putTextIfMissing(
            type,
            attributes,
            "businessTriggerDescription",
            "Started by the source-described business interaction.");
        putTextIfMissing(
            type,
            attributes,
            "completionCriterion",
            "The requested business outcome is completed and recorded.");
        putBooleanIfMissing(type, attributes, "longRunning", false);
        putBooleanIfMissing(type, attributes, "humanApprovalPossible", false);
        putBooleanIfMissing(type, attributes, "compensationExpected", false);
        putTextIfMissing(type, attributes, "processKind", "HUMAN_INVOLVED");
        putTextIfMissing(type, attributes, "criticality", "IMPORTANT");
      }
      case "Policy" -> {
        putTextIfMissing(type, attributes, "naturalLanguageRule", basis);
        putTextIfMissing(type, attributes, "policyType", "GUARD");
        putTextIfMissing(type, attributes, "enforcementStrength", "MANDATORY");
        putTextIfMissing(type, attributes, "violationSeverity", "ERROR");
        putBooleanIfMissing(type, attributes, "auditRequired", true);
      }
      case "Assumption" -> {
        putTextIfMissing(type, attributes, "assumptionStatement", basis);
        putTextIfMissing(
            type,
            attributes,
            "validationApproach",
            "Validate with domain stakeholders before transformation.");
        putBooleanIfMissing(type, attributes, "accepted", false);
      }
      case "Risk" -> {
        putTextIfMissing(type, attributes, "riskStatement", basis);
        putTextIfMissing(type, attributes, "probability", "UNKNOWN");
        putTextIfMissing(type, attributes, "impact", "To be assessed during transformation.");
        putTextIfMissing(
            type, attributes, "mitigation", "Assign owner and mitigation during model review.");
        putBooleanIfMissing(type, attributes, "productionBlocking", false);
      }
      default -> {
        // Type-specific defaults above cover the CIM source-document path.
      }
    }
  }

  private void putTextIfMissing(String type, ObjectNode attributes, String field, String value) {
    if (value == null || value.isBlank() || attributes.hasNonNull(field)) {
      return;
    }
    if (schemas.attribute(ModelLevel.CIM, type, field).isPresent()) {
      attributes.put(field, value);
    }
  }

  private void putBooleanIfMissing(
      String type, ObjectNode attributes, String field, boolean value) {
    if (attributes.hasNonNull(field)) {
      return;
    }
    if (schemas.attribute(ModelLevel.CIM, type, field).isPresent()) {
      attributes.put(field, value);
    }
  }

  private Optional<JsonNode> parseJsonObject(String content) {
    if (content == null || content.isBlank()) {
      return Optional.empty();
    }
    String trimmed = stripFence(content);
    for (String candidate : jsonCandidates(trimmed)) {
      try {
        JsonNode parsed = tolerantMapper.readTree(candidate);
        if (parsed != null && parsed.isObject()) {
          return Optional.of(parsed);
        }
      } catch (Exception ignored) {
        // Try the next bounded JSON object.
      }
    }
    return Optional.empty();
  }

  private List<String> jsonCandidates(String value) {
    List<String> result = new ArrayList<>();
    result.add(value.trim());
    for (int start = 0; start < value.length(); start++) {
      if (value.charAt(start) != '{') {
        continue;
      }
      int end = matchingJsonEnd(value, start);
      if (end > start) {
        result.add(value.substring(start, end + 1));
      }
    }
    return result.stream().distinct().toList();
  }

  private ArrayNode salvageArrayObjects(String content, String... names) {
    ArrayNode result = mapper.createArrayNode();
    if (content == null || content.isBlank()) {
      return result;
    }
    String value = stripFence(content);
    for (String name : names) {
      int arrayStart = arrayStart(value, name);
      if (arrayStart < 0) {
        continue;
      }
      for (JsonNode item : completedObjectsInArray(value, arrayStart)) {
        result.add(item);
      }
      if (!result.isEmpty()) {
        return result;
      }
    }
    return result;
  }

  private int arrayStart(String value, String fieldName) {
    String quoted = "\"" + fieldName + "\"";
    int field = value.indexOf(quoted);
    if (field < 0) {
      return -1;
    }
    int colon = value.indexOf(':', field + quoted.length());
    if (colon < 0) {
      return -1;
    }
    return value.indexOf('[', colon + 1);
  }

  private List<JsonNode> completedObjectsInArray(String value, int arrayStart) {
    List<JsonNode> result = new ArrayList<>();
    int depth = 0;
    boolean inString = false;
    boolean escaped = false;
    for (int index = arrayStart + 1; index < value.length(); index++) {
      char current = value.charAt(index);
      if (inString) {
        if (escaped) {
          escaped = false;
        } else if (current == '\\') {
          escaped = true;
        } else if (current == '"') {
          inString = false;
        }
        continue;
      }
      if (current == '"') {
        inString = true;
        continue;
      }
      if (current == '[') {
        depth++;
        continue;
      }
      if (current == ']') {
        if (depth == 0) {
          break;
        }
        depth--;
        continue;
      }
      if (current != '{' || depth != 0) {
        continue;
      }
      int objectEnd = matchingJsonEnd(value, index);
      if (objectEnd <= index) {
        continue;
      }
      try {
        JsonNode parsed = tolerantMapper.readTree(value.substring(index, objectEnd + 1));
        if (parsed != null && parsed.isObject()) {
          result.add(parsed);
        }
      } catch (Exception ignored) {
        // Keep salvaging later completed objects.
      }
      index = objectEnd;
    }
    return result;
  }

  private int matchingJsonEnd(String value, int start) {
    java.util.ArrayDeque<Character> expectedClosers = new java.util.ArrayDeque<>();
    boolean inString = false;
    boolean escaped = false;
    for (int index = start; index < value.length(); index++) {
      char current = value.charAt(index);
      if (inString) {
        if (escaped) {
          escaped = false;
        } else if (current == '\\') {
          escaped = true;
        } else if (current == '"') {
          inString = false;
        }
        continue;
      }
      if (current == '"') {
        inString = true;
      } else if (current == '{') {
        expectedClosers.push('}');
      } else if (current == '}') {
        if (expectedClosers.isEmpty() || expectedClosers.pop() != current) {
          return -1;
        }
        if (expectedClosers.isEmpty()) {
          return index;
        }
      }
    }
    return -1;
  }

  private JsonNode firstArray(JsonNode root, String... names) {
    for (String name : names) {
      JsonNode candidate = root.path(name);
      if (candidate.isArray()) {
        return candidate;
      }
    }
    return mapper.createArrayNode();
  }

  private String firstText(JsonNode node, String... names) {
    if (node == null || node.isMissingNode() || node.isNull()) {
      return "";
    }
    for (String name : names) {
      String value = node.path(name).asText("");
      if (!value.isBlank()) {
        return value.trim();
      }
    }
    return "";
  }

  private String localKey(JsonNode element, SemanticModelPatch.Operation operation) {
    String key = firstText(element, "key", "id", "sourceKey", "localId");
    return key.isBlank() ? operation.attributes().path("name").asText("") : key;
  }

  private boolean reservedElementField(String field) {
    return List.of("type", "eClass", "metamodelType", "key", "id", "sourceKey", "localId")
        .contains(field);
  }

  private String stripFence(String content) {
    String value = content == null ? "" : content.trim();
    if (!value.startsWith("```")) {
      return value;
    }
    int firstLineEnd = value.indexOf('\n');
    int closingFence = value.lastIndexOf("```");
    if (firstLineEnd < 0 || closingFence <= firstLineEnd) {
      return value;
    }
    return value.substring(firstLineEnd + 1, closingFence).trim();
  }

  record Result(SemanticModelPatch patch, String coverageSummary, List<String> coverageGaps) {
    Result {
      coverageSummary = coverageSummary == null ? "" : coverageSummary.trim();
      coverageGaps = coverageGaps == null ? List.of() : List.copyOf(coverageGaps);
    }
  }
}
