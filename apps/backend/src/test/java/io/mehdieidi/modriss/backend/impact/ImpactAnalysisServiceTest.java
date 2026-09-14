package io.mehdieidi.modriss.backend.impact;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import io.mehdieidi.modriss.platform.artifact.application.ArtifactService;
import io.mehdieidi.modriss.platform.artifact.domain.ArtifactRecord;
import io.mehdieidi.modriss.platform.identity.domain.UserRecord;
import io.mehdieidi.modriss.platform.kernel.ModelLevel;
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
    when(artifacts.list(user, "project-1")).thenReturn(List.of(artifact));
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
    assertTrue(
        psmImpact.connectedElements().stream().anyMatch(ref -> "psm-peer".equals(ref.elementId())));

    ImpactAnalysisService.ArtifactImpactResponse artifactImpact =
        service.artifactImpact(user, artifact.id());

    assertEquals(List.of("psm-model", "pim-model", "cim-model"), ancestorIds(artifactImpact));
  }

  private List<String> ancestorIds(ImpactAnalysisService.ArtifactImpactResponse response) {
    return response.ancestors().stream().map(ImpactAnalysisService.ModelAncestor::id).toList();
  }

  private UserRecord user() {
    Instant now = Instant.now();
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
