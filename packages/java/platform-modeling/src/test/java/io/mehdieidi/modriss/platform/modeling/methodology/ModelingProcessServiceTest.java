package io.mehdieidi.modriss.platform.modeling.methodology;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Tests modeling process definition loading from repository methodology assets. */
class ModelingProcessServiceTest {

  private final ModelingProcessService service = new ModelingProcessService();

  @Test
  void loadsCimProcessWithSpemHierarchy() {
    Map<String, Object> process = service.processDefinition("cim");
    assertEquals("modriss.cim.modeling", process.get("processId"));
    assertEquals("2.0", process.get("spemVersion"));
    List<?> phases = list(process.get("phases"));
    assertEquals(5, phases.size());
    assertFalse(list(process.get("roles")).isEmpty());
    assertFalse(list(process.get("artifactKinds")).isEmpty());
    assertFalse(list(process.get("guidelines")).isEmpty());
    assertNotNull(process.get("processEngine"));
    assertNotNull(process.get("progressModel"));
    assertNotNull(process.get("governance"));

    @SuppressWarnings("unchecked")
    Map<String, Object> firstPhase = (Map<String, Object>) phases.get(0);
    List<?> stages = list(firstPhase.get("stages"));
    assertFalse(stages.isEmpty());
    @SuppressWarnings("unchecked")
    Map<String, Object> firstStage = (Map<String, Object>) stages.get(0);
    assertFalse(list(firstStage.get("tasks")).isEmpty());
  }

  @Test
  void loadsFullLifecycleEndToEndProcessWithTransformMilestones() {
    Map<String, Object> process = service.processDefinition("end-to-end");
    assertEquals("modriss.end-to-end.modeling", process.get("processId"));
    List<?> phases = list(process.get("phases"));
    assertEquals(5, phases.size());
    @SuppressWarnings("unchecked")
    Map<String, Object> initiation = (Map<String, Object>) phases.get(0);
    assertEquals("e2e.ph0", initiation.get("id"));
    @SuppressWarnings("unchecked")
    Map<String, Object> increment = (Map<String, Object>) phases.get(1);
    assertEquals("e2e.ph1", increment.get("id"));
    assertTrue(list(increment.get("stages")).size() >= 8);
    @SuppressWarnings("unchecked")
    Map<String, Object> retirement = (Map<String, Object>) phases.get(4);
    assertEquals("e2e.ph4", retirement.get("id"));
    assertNotNull(process.get("milestones"));
    assertNotNull(process.get("processEngine"));
    assertNotNull(process.get("progressModel"));
    assertNotNull(process.get("governance"));
  }

  @Test
  void loadsArtifactDeploymentReadinessProcess() {
    Map<String, Object> process = service.processDefinition("artifact");
    assertEquals("modriss.artifact.deployment-readiness", process.get("processId"));
    assertEquals(4, list(process.get("phases")).size());
    assertFalse(list(process.get("roles")).isEmpty());
    assertFalse(list(process.get("artifactKinds")).isEmpty());
    assertNotNull(process.get("processEngine"));
    assertNotNull(process.get("progressModel"));
    assertNotNull(process.get("governance"));
  }

  @Test
  void coverageMatrixCoversAllCimConcepts() {
    Map<String, Object> matrix = service.coverageMatrix("cim");
    assertEquals(0, list(matrix.get("orphanedConcepts")).size());
    assertTrue(((Number) matrix.get("coveredConcepts")).intValue() > 0);
    assertEquals(matrix.get("totalConcepts"), matrix.get("coveredConcepts"));
  }

  @Test
  void coverageStatusDetectsPresentElementTypes() {
    Map<String, Object> model =
        Map.of(
            "type",
            "CIMModel",
            "goals",
            List.of(Map.of("type", "BusinessGoal", "name", "Grow revenue")));
    Map<String, Object> status = service.coverageStatus("cim", model);
    assertTrue(((Number) status.get("presentConcepts")).intValue() >= 2);
    assertNotNull(status.get("concepts"));
  }

  @SuppressWarnings("unchecked")
  private List<Object> list(Object value) {
    return value instanceof List<?> list ? (List<Object>) list : List.of();
  }
}
