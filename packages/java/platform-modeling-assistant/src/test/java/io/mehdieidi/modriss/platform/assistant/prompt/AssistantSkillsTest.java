package io.mehdieidi.modriss.platform.assistant.prompt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class AssistantSkillsTest {
  @Test
  void packagedSkillsHaveValidMetadataAndStableHashes() {
    List<String> names =
        List.of(
            "plan-model-edit",
            "construct-valid-model",
            "model-pim-serverless",
            "evolve-existing-model",
            "transform-source-to-cim",
            "explain-model");

    for (String name : names) {
      AssistantSkills.Skill skill = AssistantSkills.load(name);
      assertEquals(name, skill.name());
      assertFalse(skill.description().isBlank());
      assertFalse(skill.instructions().isBlank());
      assertTrue(skill.hash().matches("[0-9a-f]{64}"));
    }
  }

  @Test
  void promptIncludesOnlyRequestedSkillsOnce() {
    String prompt =
        AssistantSkills.prompt(
            "construct-valid-model", "model-pim-serverless", "construct-valid-model");

    assertTrue(prompt.contains("name=\"construct-valid-model\""));
    assertTrue(prompt.contains("name=\"model-pim-serverless\""));
    assertEquals(1, occurrences(prompt, "name=\"construct-valid-model\""));
    assertFalse(prompt.contains("name=\"transform-source-to-cim\""));
  }

  @Test
  void constructionSkillEnforcesStructuralOnlyAssistantGate() {
    String prompt = AssistantSkills.prompt("construct-valid-model");

    assertTrue(prompt.contains("structural Ecore/EMF conformance"));
    assertTrue(prompt.contains("Never request or use EVL validation"));
  }

  private int occurrences(String value, String needle) {
    int count = 0;
    for (int index = 0; (index = value.indexOf(needle, index)) >= 0; index += needle.length()) {
      count++;
    }
    return count;
  }
}
