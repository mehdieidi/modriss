package io.mehdieidi.modless.backend.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.mehdieidi.modless.backend.observability.ModlessMetrics;
import io.mehdieidi.modless.platform.core.model.MdeJobOperation;
import io.mehdieidi.modless.platform.core.model.MdeJobRecord;
import io.mehdieidi.modless.platform.core.model.MdeJobStatus;
import io.mehdieidi.modless.platform.core.model.ModelLevel;
import io.mehdieidi.modless.platform.core.model.UserRecord;
import io.mehdieidi.modless.platform.core.service.MdeJobService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** Controller tests for async MDE submission responses. */
class TransformationControllerAsyncTest {

  @Test
  void transformationSubmissionReturnsAcceptedLocationAndJobBody() {
    MdeJobService jobs = mock(MdeJobService.class);
    AuthSupport auth = mock(AuthSupport.class);
    ModlessMetrics metrics = mock(ModlessMetrics.class);
    UserRecord user = user();
    MdeJobRecord job = job(MdeJobOperation.CIM_TO_PIM);
    when(auth.user("token")).thenReturn(user);
    when(jobs.submitCimToPim(eq(user), eq("model-1"), eq(7L), eq("retry-1"))).thenReturn(job);

    TransformationController controller = new TransformationController(jobs, auth, metrics);
    ResponseEntity<TransformationController.JobResponse> response =
        controller.cimToPim(
            "token", "retry-1", new TransformationController.TransformRequest("model-1", 7L));

    assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());
    assertEquals("/api/transformations/jobs/job-1", response.getHeaders().getLocation().toString());
    assertEquals("job-1", response.getBody().id());
    assertEquals(MdeJobStatus.QUEUED, response.getBody().status());
  }

  private UserRecord user() {
    Instant now = Instant.now();
    return new UserRecord("user-1", "owner@example.com", "Owner", "hash", "salt", now, now);
  }

  private MdeJobRecord job(MdeJobOperation operation) {
    Instant now = Instant.now();
    return new MdeJobRecord(
        "job-1",
        "project-1",
        "user-1",
        "model-1",
        ModelLevel.CIM,
        7,
        "hash",
        operation,
        MdeJobStatus.QUEUED,
        0,
        null,
        null,
        List.of(),
        now,
        null,
        null);
  }
}
