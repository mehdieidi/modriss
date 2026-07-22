package io.mehdieidi.varka.platform.assistant.prompt;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PromptTemplatesTest {
  @Test
  void versionedPromptTemplatesHaveStableAuditMetadata() {
    PromptTemplates.Template template = PromptTemplates.load("modeling-executor");

    assertTrue(template.version().startsWith("2."));
    assertFalse(template.body().isBlank());
    assertTrue(template.hash().matches("[0-9a-f]{64}"));
  }
}
