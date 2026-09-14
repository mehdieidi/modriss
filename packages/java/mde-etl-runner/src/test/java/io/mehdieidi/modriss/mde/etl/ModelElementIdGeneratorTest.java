package io.mehdieidi.modriss.mde.etl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ModelElementIdGeneratorTest {

  private final ModelElementIdGenerator generator = new ModelElementIdGenerator();

  @Test
  void deterministicIdsAreStableAcrossGeneratorInstances() {
    String key = "https://modriss.io/cim::Command::command-id-123::fn_cmd";

    String first = generator.deterministicId("cim-to-pim", key);
    String second = new ModelElementIdGenerator().deterministicId("cim-to-pim", key);

    assertEquals(first, second);
  }

  @Test
  void directionAndTargetRoleSeparateGeneratedIdentities() {
    assertNotEquals(
        generator.deterministicId("cim-to-pim", "function-id-456::lambda"),
        generator.deterministicId("pim-to-awspsm", "function-id-456::lambda"));
    assertNotEquals(
        generator.deterministicId("pim-to-awspsm", "function-id-456::lambda"),
        generator.deterministicId("pim-to-awspsm", "function-id-456::iam-role"));
  }

  @Test
  void blankSemanticIdentityIsRejected() {
    assertThrows(
        IllegalArgumentException.class, () -> generator.deterministicId("cim-to-pim", " "));
  }

  @Test
  void occurrenceIdsAreUniqueWithinARunAndRepeatAcrossRuns() {
    ModelElementIdGenerator firstRun = new ModelElementIdGenerator();
    ModelElementIdGenerator secondRun = new ModelElementIdGenerator();

    String firstOccurrence = firstRun.deterministicOccurrenceId("transform", "tag:Environment=dev");
    String secondOccurrence =
        firstRun.deterministicOccurrenceId("transform", "tag:Environment=dev");

    assertNotEquals(firstOccurrence, secondOccurrence);
    assertEquals(
        firstOccurrence, secondRun.deterministicOccurrenceId("transform", "tag:Environment=dev"));
    assertEquals(
        secondOccurrence, secondRun.deterministicOccurrenceId("transform", "tag:Environment=dev"));
  }
}
