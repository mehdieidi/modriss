package io.mehdieidi.modless.backend.assistant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.mehdieidi.modless.platform.core.model.ModelLevel;
import io.mehdieidi.modless.platform.core.model.ModelRecord;
import io.mehdieidi.modless.platform.core.service.ModelService;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;

@ExtendWith(MockitoExtension.class)
class AssistantModelContextIndexServiceTest {

  @Mock private JdbcTemplate jdbc;

  @Test
  void snapshotBindsJdbcTimestampWhenPersistingContext() throws Exception {
    ObjectMapper mapper = new ObjectMapper();
    AssistantModelContextIndexService service = new AssistantModelContextIndexService(jdbc, mapper);
    doReturn(null)
        .when(jdbc)
        .query(anyString(), any(ResultSetExtractor.class), anyString(), anyLong(), anyString());
    ModelRecord model =
        new ModelRecord(
            "model-1",
            "project-1",
            ModelLevel.CIM,
            "Imported CIM",
            mapper.readTree(
                """
                {
                  "id": "root",
                  "eClass": "BusinessModel",
                  "name": "Imported CIM",
                  "nodes": [
                    {"id": "actor-1", "eClass": "Actor", "name": "Customer"}
                  ]
                }
                """),
            "v1",
            "hash",
            3,
            null,
            "CURRENT",
            Instant.EPOCH,
            Instant.EPOCH);

    service.snapshot(model, new ModelService.ValidationResult(true, List.of()));

    verify(jdbc)
        .update(
            anyString(),
            eq("model-1"),
            eq("project-1"),
            eq("CIM"),
            eq(3L),
            anyString(),
            anyString(),
            anyString(),
            org.mockito.ArgumentMatchers.<Timestamp>any());
  }
}
