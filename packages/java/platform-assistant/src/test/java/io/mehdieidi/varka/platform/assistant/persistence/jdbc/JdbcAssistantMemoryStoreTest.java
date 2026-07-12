package io.mehdieidi.varka.platform.assistant.persistence.jdbc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.mehdieidi.varka.platform.assistant.domain.AssistantProposal;
import io.mehdieidi.varka.platform.assistant.domain.AssistantValidationSummary;
import io.mehdieidi.varka.platform.assistant.domain.SemanticModelPatch;
import io.mehdieidi.varka.platform.identity.domain.UserRecord;
import io.mehdieidi.varka.platform.kernel.ModelLevel;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;

@ExtendWith(MockitoExtension.class)
class JdbcAssistantMemoryStoreTest {

  @Mock private JdbcTemplate jdbc;

  @Mock private ObjectMapper mapper;

  @Test
  void ensureThreadBindsJdbcTimestamps() {
    JdbcAssistantMemoryStore repository = new JdbcAssistantMemoryStore(jdbc, mapper);
    doReturn(null).when(jdbc).query(anyString(), any(ResultSetExtractor.class), any());

    UserRecord user =
        new UserRecord("user-1", "user@example.com", "User", "", "", Instant.EPOCH, Instant.EPOCH);

    repository.ensureThread(user, "project-1", ModelLevel.PIM, "Assistant", "model-1", 7L);

    verify(jdbc)
        .update(
            anyString(),
            eq("user-1:project-1:PIM"),
            eq("user-1"),
            eq("project-1"),
            eq("PIM"),
            eq("model-1"),
            eq(7L),
            eq("Assistant"),
            org.mockito.ArgumentMatchers.<Timestamp>any(),
            org.mockito.ArgumentMatchers.<Timestamp>any());
  }

  @Test
  void saveProposalBindsApprovalRequiredForLiveSchema() throws Exception {
    JdbcAssistantMemoryStore repository = new JdbcAssistantMemoryStore(jdbc, mapper);
    when(mapper.writeValueAsString(any())).thenReturn("{}");
    AssistantProposal proposal =
        new AssistantProposal(
            "proposal-1",
            List.of(),
            new SemanticModelPatch(List.of()),
            List.of(),
            new AssistantValidationSummary(true, true, 0, List.of()),
            AssistantProposal.RiskLevel.MEDIUM,
            List.of(),
            Instant.EPOCH);

    repository.saveProposal("thread-1", "project-1", "model-1", 2L, proposal, "APPLIED");

    ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
    verify(jdbc)
        .update(
            sql.capture(),
            eq("proposal-1"),
            eq("thread-1"),
            eq("project-1"),
            eq("model-1"),
            eq(2L),
            eq("MEDIUM"),
            eq("{}"),
            eq("{}"),
            eq("{}"),
            eq("{}"),
            eq("APPLIED"),
            eq(false),
            org.mockito.ArgumentMatchers.<Timestamp>any(),
            org.mockito.ArgumentMatchers.<Timestamp>any());
    org.junit.jupiter.api.Assertions.assertTrue(sql.getValue().contains("approval_required"));
  }
}
