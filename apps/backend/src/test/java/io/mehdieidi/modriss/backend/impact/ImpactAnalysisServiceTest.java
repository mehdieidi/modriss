package io.mehdieidi.modriss.backend.impact;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import io.mehdieidi.modriss.platform.artifact.application.ArtifactService;
import io.mehdieidi.modriss.platform.artifact.domain.ArtifactRecord;
import io.mehdieidi.modriss.platform.identity.domain.UserRecord;
import io.mehdieidi.modriss.platform.kernel.ModelLevel;
import io.mehdieidi.modriss.platform.kernel.PlatformException;
import io.mehdieidi.modriss.platform.model.application.ModelService;
import io.mehdieidi.modriss.platform.model.domain.ModelRecord;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@ExtendWith(MockitoExtension.class)
class ImpactAnalysisServiceTest {

  private final ObjectMapper mapper = new ObjectMapper();

  @Mock private ModelService models;

  @Mock private ArtifactService artifacts;

  @Test
  void tracesImpactAcrossModelsAndArtifacts() {
    UserRecord user = user();
    ModelRecord cim =
        model(
            "cim-model",
            ModelLevel.CIM,
            modelJson("CIM", null, element("cim-1", "BusinessGoal", "Safer operations")));
    ModelRecord pim =
        model(
            "pim-model",
            ModelLevel.PIM,
            modelJson(
                "PIM",
                cim.id(),
                element("pim-1", "ServerlessService", "Safety service"),
                trace("cim-1", "pim-1", "TRANSFORMS_TO")));
    ModelRecord psm =
        model(
            "psm-model",
            ModelLevel.PSM,
            modelJson(
                "PSM",
                pim.id(),
                element("psm-1", "AwsLambdaFunction", "Safety handler"),
                element("psm-peer", "CloudWatchAlarm", "Safety alarm"),
                trace("pim-1", "psm-1", "REALIZES"),
                relationship("psm-1", "psm-peer", "OBSERVES")));
    ArtifactRecord artifact =
        new ArtifactRecord(
            "artifact-1",
            "project-1",
            "Generated AWS project",
            artifactJson(psm.id(), "psm-1", "template.yaml"),
            Map.of("template.yaml", "Resources: {}"),
            Instant.now(),
            Instant.now());

    when(models.get(user, ModelLevel.CIM, cim.id())).thenReturn(cim);
    when(models.get(user, ModelLevel.PSM, psm.id())).thenReturn(psm);
    when(models.list(user, ModelLevel.CIM, "project-1")).thenReturn(List.of(cim));
    when(models.list(user, ModelLevel.PIM, "project-1")).thenReturn(List.of(pim));
    when(models.list(user, ModelLevel.PSM, "project-1")).thenReturn(List.of(psm));
    when(artifacts.listSummaries(user, "project-1")).thenReturn(List.of(artifact));
    when(artifacts.get(user, artifact.id())).thenReturn(artifact);

    ImpactAnalysisService service = new ImpactAnalysisService(models, artifacts);

    ImpactAnalysisService.ElementImpactResponse cimImpact =
        service.elementImpact(user, ModelLevel.CIM, cim.id(), "cim-1");

    assertEquals("cim-1", cimImpact.focalElement().elementId());
    assertTrue(
        cimImpact.downstream().stream()
            .anyMatch(ref -> "pim-1".equals(ref.elementId()) && "PIM".equals(ref.modelType())));
    assertTrue(
        cimImpact.downstream().stream()
            .anyMatch(ref -> "psm-1".equals(ref.elementId()) && "PSM".equals(ref.modelType())));
    assertTrue(
        cimImpact.downstream().stream()
            .anyMatch(
                ref -> "artifact-1".equals(ref.modelId()) && "ARTIFACT".equals(ref.modelType())));

    ImpactAnalysisService.ElementImpactResponse psmImpact =
        service.elementImpact(user, ModelLevel.PSM, psm.id(), "psm-1");

    assertTrue(psmImpact.upstream().stream().anyMatch(ref -> "pim-1".equals(ref.elementId())));
    assertTrue(psmImpact.upstream().stream().anyMatch(ref -> "cim-1".equals(ref.elementId())));
    assertTrue(psmImpact.connectedElements().isEmpty());
    assertEquals(3, cimImpact.downstream().size());
    assertEquals(3, cimImpact.traceEdges().size());
    assertEquals("template.yaml", cimImpact.downstream().get(2).elementId());

    ImpactAnalysisService.ArtifactImpactResponse artifactImpact =
        service.artifactImpact(user, artifact.id());

    assertEquals(List.of("psm-model", "pim-model", "cim-model"), ancestorIds(artifactImpact));
    assertEquals(3, artifactImpact.upstream().size());
    assertEquals(3, artifactImpact.traceEdges().size());
  }

  @Test
  void preservesSeparateBranchesDespiteSharedNamesAndGeneratedFromValues() {
    ModelRecord cim =
        model(
            "cim",
            ModelLevel.CIM,
            modelJson("CIM", null, element("command", "Command", "RejectEmergencyGrant")));
    ObjectNode handler = element("handler", "Function", "Same name");
    handler.put("generatedFrom", "command");
    ObjectNode schema = element("schema", "Schema", "Same name");
    schema.put("generatedFrom", "command");
    ModelRecord pim =
        model(
            "pim",
            ModelLevel.PIM,
            modelJson("PIM", "cim", handler, schema, trace("command", "handler", "REALIZES")));
    ModelRecord psm =
        model(
            "psm",
            ModelLevel.PSM,
            modelJson(
                "PSM",
                "pim",
                element("lambda", "AwsLambdaFunction", "Handler"),
                element("request", "ApiGatewayRequestModel", "Request"),
                trace("handler", "lambda", "REALIZES"),
                trace("schema", "request", "REALIZES")));
    stubWorkspace(cim, pim, psm);
    when(models.get(user(), ModelLevel.CIM, "cim")).thenReturn(cim);
    when(models.get(user(), ModelLevel.PIM, "pim")).thenReturn(pim);
    ImpactAnalysisService service = new ImpactAnalysisService(models, artifacts);
    var impact = service.elementImpact(user(), ModelLevel.CIM, "cim", "command");
    assertEquals(
        List.of("handler", "schema", "lambda", "request"),
        impact.downstream().stream().map(ImpactAnalysisService.ElementRef::elementId).toList());
    assertEquals(4, impact.traceEdges().size());
    var schemaImpact = service.elementImpact(user(), ModelLevel.PIM, "pim", "schema");
    assertEquals(
        List.of("request"),
        schemaImpact.downstream().stream()
            .map(ImpactAnalysisService.ElementRef::elementId)
            .toList());
    assertEquals("command", schemaImpact.upstream().get(0).elementId());
  }

  @Test
  void ignoresOrdinaryRelationshipsAndUnresolvedOrNameBasedEndpoints() {
    ModelRecord cim =
        model(
            "cim",
            ModelLevel.CIM,
            modelJson("CIM", null, element("command", "Command", "Shared name")));
    ModelRecord pim =
        model(
            "pim",
            ModelLevel.PIM,
            modelJson(
                "PIM",
                "cim",
                element("target", "Function", "Shared name"),
                relationship("command", "target", "USES"),
                trace("Shared name", "target", "REALIZES"),
                trace("command", "missing", "REALIZES")));
    stubWorkspace(cim, pim);
    when(models.get(user(), ModelLevel.CIM, "cim")).thenReturn(cim);
    var service = new ImpactAnalysisService(models, artifacts);
    var impact = service.elementImpact(user(), ModelLevel.CIM, "cim", "command");
    assertTrue(impact.downstream().isEmpty());
    assertTrue(impact.traceEdges().isEmpty());
    assertEquals(
        404,
        assertThrows(
                PlatformException.class,
                () -> service.elementImpact(user(), ModelLevel.CIM, "cim", "unsaved"))
            .status());
  }

  @Test
  void returnsSharedFileOnceWithEveryContributorAndFiltersReverseLineageByFile() {
    ModelRecord pim =
        model(
            "pim",
            ModelLevel.PIM,
            modelJson(
                "PIM",
                null,
                element("origin-a", "Function", "A"),
                element("origin-b", "Function", "B")));
    ModelRecord psm =
        model(
            "psm",
            ModelLevel.PSM,
            modelJson(
                "PSM",
                "pim",
                element("a", "Lambda", "A"),
                element("b", "Lambda", "B"),
                trace("origin-a", "a", "REALIZES"),
                trace("origin-b", "b", "REALIZES")));
    ObjectNode metadata = artifactJson("psm", "a", "shared.yaml");
    metadata
        .withObject("traceability")
        .putArray("b")
        .add("shared.yaml")
        .add("only-b.go")
        .add("deleted.go");
    ArtifactRecord artifact =
        new ArtifactRecord(
            "artifact",
            "project-1",
            "Output",
            metadata,
            Map.of("shared.yaml", "", "only-b.go", "", "untraced.txt", ""),
            Instant.now(),
            Instant.now());
    stubWorkspace(pim, psm);
    when(artifacts.listSummaries(user(), "project-1")).thenReturn(List.of(artifact));
    when(artifacts.get(user(), "artifact")).thenReturn(artifact);
    when(models.get(user(), ModelLevel.PIM, "pim")).thenReturn(pim);
    var service = new ImpactAnalysisService(models, artifacts);
    var impact = service.elementImpact(user(), ModelLevel.PIM, "pim", "origin-a");
    assertEquals(
        List.of("a", "shared.yaml"),
        impact.downstream().stream().map(ImpactAnalysisService.ElementRef::elementId).toList());
    var shared = service.artifactImpact(user(), "artifact", "shared.yaml");
    assertEquals(
        List.of("a", "b", "origin-a", "origin-b"),
        shared.upstream().stream().map(ImpactAnalysisService.ElementRef::elementId).toList());
    assertEquals(4, shared.traceEdges().size());
    assertTrue(service.artifactImpact(user(), "artifact", "untraced.txt").upstream().isEmpty());
    var onlyB = service.artifactImpact(user(), "artifact", "only-b.go");
    assertEquals(
        List.of("b", "origin-b"),
        onlyB.upstream().stream().map(ImpactAnalysisService.ElementRef::elementId).toList());
    assertThrows(
        PlatformException.class, () -> service.artifactImpact(user(), "artifact", "missing"));
  }

  @Test
  void traversesCyclesSafelyAndDoesNotTruncateLongChains() {
    List<ModelRecord> chain = new java.util.ArrayList<>();
    for (int i = 0; i < 12; i++) {
      chain.add(
          model(
              "model-" + i,
              ModelLevel.PIM,
              modelJson(
                  "PIM",
                  "model-" + ((i + 11) % 12),
                  element("same-id", "Function", "Node " + i),
                  trace("same-id", "same-id", "REALIZES"))));
    }
    stubWorkspace(chain.toArray(ModelRecord[]::new));
    when(models.get(user(), ModelLevel.PIM, "model-0")).thenReturn(chain.get(0));
    var impact =
        new ImpactAnalysisService(models, artifacts)
            .elementImpact(user(), ModelLevel.PIM, "model-0", "same-id");
    assertEquals(11, impact.downstream().size());
    assertEquals(11, impact.upstream().size());
    assertEquals(12, impact.traceEdges().size());
  }

  @Test
  void suppressesAmbiguousLegacyFileAttributionUntilArtifactsAreRegenerated() {
    ObjectNode lambda = element("lambda", "AwsLambdaFunction", "Handler");
    lambda.put("traceId", "inherited-trace");
    ObjectNode alarm = element("alarm", "CloudWatchAlarm", "Handler");
    alarm.put("traceId", "inherited-trace");
    ModelRecord psm = model("psm", ModelLevel.PSM, modelJson("PSM", null, lambda, alarm));
    ObjectNode metadata = artifactJson("psm", "lambda", "handler.go");
    metadata.withObject("traceability").putArray("alarm").add("handler.go");
    ArtifactRecord artifact =
        new ArtifactRecord(
            "artifact",
            "project-1",
            "Old generation",
            metadata,
            Map.of("handler.go", ""),
            Instant.EPOCH,
            Instant.EPOCH);
    stubWorkspace(psm);
    when(models.get(user(), ModelLevel.PSM, "psm")).thenReturn(psm);
    when(artifacts.listSummaries(user(), "project-1")).thenReturn(List.of(artifact));
    when(artifacts.get(user(), "artifact")).thenReturn(artifact);
    var service = new ImpactAnalysisService(models, artifacts);
    var impact = service.elementImpact(user(), ModelLevel.PSM, "psm", "alarm");
    assertTrue(impact.downstream().isEmpty());
    assertEquals(1, impact.notices().size());
    var fileImpact = service.artifactImpact(user(), "artifact", "handler.go");
    assertTrue(fileImpact.upstream().isEmpty());
    assertEquals(1, fileImpact.notices().size());

    // Old alias matching also mixed distinct trace IDs when display names happened to match.
    alarm.put("traceId", "different-trace");
    assertTrue(
        service.elementImpact(user(), ModelLevel.PSM, "psm", "alarm").downstream().isEmpty());

    // Exact provenance can legitimately declare a shared file despite identical legacy keys.
    metadata.put("traceabilityVersion", 2);
    metadata.putObject("generationRules").put("handler.go", "LambdaToHandler");
    var exact = service.elementImpact(user(), ModelLevel.PSM, "psm", "lambda");
    assertEquals(1, exact.downstream().size());
    assertTrue(exact.notices().isEmpty());
    assertEquals("LambdaToHandler", exact.traceEdges().get(0).transformationRule());
  }

  private void stubWorkspace(ModelRecord... records) {
    for (ModelLevel level : ModelLevel.values()) {
      when(models.list(user(), level, "project-1"))
          .thenReturn(
              java.util.Arrays.stream(records).filter(record -> record.level() == level).toList());
    }
  }

  private List<String> ancestorIds(ImpactAnalysisService.ArtifactImpactResponse response) {
    return response.ancestors().stream().map(ImpactAnalysisService.ModelAncestor::id).toList();
  }

  private UserRecord user() {
    Instant now = Instant.EPOCH;
    return new UserRecord("user-1", "owner@example.com", "Owner", "hash", "salt", now, now);
  }

  private ModelRecord model(String id, ModelLevel level, ObjectNode modelJson) {
    Instant now = Instant.now();
    return new ModelRecord(
        id,
        "project-1",
        level,
        level.apiName() + "-model",
        modelJson,
        "test",
        "hash",
        1,
        null,
        "CURRENT",
        now,
        now);
  }

  private ObjectNode modelJson(String level, String sourceModelId, ObjectNode... nodes) {
    ObjectNode root = mapper.createObjectNode();
    root.put("id", level.toLowerCase() + "-root");
    root.put("name", level + " root");
    root.put("eClass", level + "Model");
    root.put("modelLevel", level);
    if (sourceModelId != null) {
      root.put("sourceModelId", sourceModelId);
    }
    ObjectNode graph = root.putObject("graph");
    var elements = graph.putArray("elements");
    var traceLinks = graph.putArray("traceLinks");
    var relationships = graph.putArray("relationships");
    for (ObjectNode node : nodes) {
      String graphKind = node.path("_graphKind").asText("element");
      node.remove("_graphKind");
      if ("trace".equals(graphKind)) {
        traceLinks.add(node);
      } else if ("relationship".equals(graphKind)) {
        relationships.add(node);
      } else {
        elements.add(node);
      }
    }
    return root;
  }

  private ObjectNode element(String id, String eClass, String name) {
    ObjectNode node = mapper.createObjectNode();
    node.put("id", id);
    node.put("eClass", eClass);
    node.put("name", name);
    return node;
  }

  private ObjectNode trace(String source, String target, String linkType) {
    ObjectNode node = mapper.createObjectNode();
    node.put("_graphKind", "trace");
    node.put("id", source + "-" + target);
    node.put("eClass", "TraceLink");
    node.put("sourceElementId", source);
    node.put("targetElementId", target);
    node.put("linkType", linkType);
    node.put("transformationRule", "test-rule");
    return node;
  }

  private ObjectNode relationship(String source, String target, String kind) {
    ObjectNode node = mapper.createObjectNode();
    node.put("_graphKind", "relationship");
    node.put("id", source + "-" + target + "-relationship");
    node.put("sourceElementId", source);
    node.put("targetElementId", target);
    node.put("kind", kind);
    return node;
  }

  private ObjectNode artifactJson(String sourceModelId, String sourceElementId, String path) {
    ObjectNode metadata = mapper.createObjectNode();
    metadata.put("sourceModelId", sourceModelId);
    ObjectNode traceability = mapper.createObjectNode();
    traceability.putArray(sourceElementId).add(path);
    metadata.set("traceability", traceability);
    return metadata;
  }
}
