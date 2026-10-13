package io.mehdieidi.modriss.platform.assistant.source;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Set;
import org.junit.jupiter.api.Test;

class RequirementCoverageEvaluatorTest {
  @Test
  void reportsRecallAndPrecisionForLabelledRequirements() {
    var score =
        RequirementCoverageEvaluator.score(Set.of("R1", "R2", "R3"), Set.of("R1", "R3", "R4"));
    assertEquals(2d / 3d, score.recall());
    assertEquals(2d / 3d, score.precision());
    assertEquals(1, score.falsePositives());
    assertEquals(1, score.falseNegatives());
  }
}
