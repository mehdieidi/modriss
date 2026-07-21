package io.mehdieidi.varka.platform.assistant.persistence.jdbc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import io.mehdieidi.varka.platform.assistant.turn.AssistantTurnStore.ProviderCall;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class JdbcAssistantTurnStoreTelemetryTest {
  @Mock private JdbcTemplate jdbc;

  @Test
  void persistsReportedUsageForEachProviderCall() {
    JdbcAssistantTurnStore store = new JdbcAssistantTurnStore(jdbc, new ObjectMapper());
    store.recordProviderCalls(
        "turn-1",
        List.of(
            new ProviderCall("gemini", "flash", 42, 11, 7, true, "system prompt", "user prompt")));
    verify(jdbc)
        .update(
            anyString(),
            eq("turn-1"),
            eq("gemini"),
            eq("flash"),
            any(),
            eq(42L),
            eq(11L),
            eq(7L),
            eq("COMPLETED"),
            eq("system prompt"),
            eq("user prompt"));
  }

  @Test
  void persistsRequirementMappingWithProvenance() {
    JdbcAssistantTurnStore store = new JdbcAssistantTurnStore(jdbc, new ObjectMapper());
    store.saveProvenance("turn-1", "element-1", "unit-1", "R1", "SOURCE_GROUNDED", "");
    verify(jdbc)
        .update(
            anyString(),
            eq("turn-1"),
            eq("element-1"),
            eq("unit-1"),
            eq("R1"),
            eq("SOURCE_GROUNDED"),
            eq(""));
  }
}
