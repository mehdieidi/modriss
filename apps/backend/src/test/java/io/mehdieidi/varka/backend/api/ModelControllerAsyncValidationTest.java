package io.mehdieidi.varka.backend.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.mehdieidi.varka.platform.identity.domain.UserRecord;
import io.mehdieidi.varka.platform.kernel.ModelLevel;
import io.mehdieidi.varka.platform.model.application.ModelService;
import io.mehdieidi.varka.platform.transformation.application.MdeJobService;
import io.mehdieidi.varka.platform.transformation.domain.MdeJobOperation;
import io.mehdieidi.varka.platform.transformation.domain.MdeJobRecord;
import io.mehdieidi.varka.platform.transformation.domain.MdeJobStatus;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** Controller tests for async stored-model validation responses. */
class ModelControllerAsyncValidationTest {

  @Test
  void storedValidationSubmissionReturnsAcceptedLocationAndJobBody() {
    ModelService models = mock(ModelService.class);
    MdeJobService jobs = mock(MdeJobService.class);
    AuthSupport auth = mock(AuthSupport.class);
    UserRecord user = user();
    MdeJobRecord job = job();
    when(auth.user("token")).thenReturn(user);
    when(jobs.submitValidation(eq(user), eq(ModelLevel.PIM), eq("model-1"), eq(3L), eq("retry-1")))
        .thenReturn(job);

    ModelController controller = new ModelController(models, jobs, auth);
    ResponseEntity<ModelController.ValidationJobResponse> response =
        controller.validateStoredJob(
            "token", "retry-1", "pim", "model-1", new ModelController.ValidateStoredRequest(3L));

    assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());
    assertEquals("/api/transformations/jobs/job-1", response.getHeaders().getLocation().toString());
    assertEquals("job-1", response.getBody().id());
    assertEquals(MdeJobStatus.QUEUED, response.getBody().status());
  }

  private UserRecord user() {
    Instant now = Instant.now();
    return new UserRecord("user-1", "owner@example.com", "Owner", "hash", "salt", now, now);
  }

  private MdeJobRecord job() {
    Instant now = Instant.now();
    return new MdeJobRecord(
        "job-1",
        "project-1",
        "user-1",
        "model-1",
        ModelLevel.PIM,
        3,
        "hash",
        MdeJobOperation.VALIDATE,
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
