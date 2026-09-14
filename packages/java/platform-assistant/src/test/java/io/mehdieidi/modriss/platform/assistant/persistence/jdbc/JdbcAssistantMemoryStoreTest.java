package io.mehdieidi.modriss.platform.assistant.persistence.jdbc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

import io.mehdieidi.modriss.platform.identity.domain.UserRecord;
import io.mehdieidi.modriss.platform.kernel.ModelLevel;
import java.sql.Timestamp;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import tools.jackson.databind.ObjectMapper;

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
}
