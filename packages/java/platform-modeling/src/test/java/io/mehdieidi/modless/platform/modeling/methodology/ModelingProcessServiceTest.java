package io.mehdieidi.modless.platform.modeling.methodology;

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
  void loadsCimProcessWithFourteenPhases() {
    Map<String, Object> process = service.processDefinition("cim");
    assertEquals("modless.cim.modeling", process.get("processId"));
    List<?> phases = list(process.get("phases"));
    assertEquals(14, phases.size());
    assertFalse(list(process.get("roles")).isEmpty());
  }

  @Test
  void loadsEndToEndProcessWithTransformMilestones() {
    Map<String, Object> process = service.processDefinition("end-to-end");
    assertEquals("modless.end-to-end.modeling", process.get("processId"));
    List<?> phases = list(process.get("phases"));
    assertTrue(phases.size() >= 6);
    assertNotNull(process.get("milestones"));
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
