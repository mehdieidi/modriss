package io.mehdieidi.varka.backend.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.mehdieidi.varka.backend.observability.VarkaMetrics;
import io.mehdieidi.varka.platform.identity.domain.UserRecord;
import io.mehdieidi.varka.platform.kernel.ModelLevel;
import io.mehdieidi.varka.platform.model.application.ModelService;
import io.mehdieidi.varka.platform.transformation.application.MdeJobService;
import io.mehdieidi.varka.platform.transformation.domain.MdeJobOperation;
import io.mehdieidi.varka.platform.transformation.domain.MdeJobRecord;
import io.mehdieidi.varka.platform.transformation.domain.MdeJobStatus;
import io.mehdieidi.varka.platform.transformation.synchronization.ConflictResolution;
import io.mehdieidi.varka.platform.transformation.synchronization.SynchronizationSession;
import io.mehdieidi.varka.platform.transformation.synchronization.TransformationDirection;
import io.mehdieidi.varka.platform.transformation.synchronization.TransformationSynchronizationCoordinator;
import io.mehdieidi.varka.platform.transformation.synchronization.TransformationValidationException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** Controller tests for async MDE submission responses. */
class TransformationControllerAsyncTest {

  @Test
  void transformationSubmissionReturnsAcceptedLocationAndJobBody() {
    MdeJobService jobs = mock(MdeJobService.class);
    AuthSupport auth = mock(AuthSupport.class);
    VarkaMetrics metrics = mock(VarkaMetrics.class);
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

  @Test
  void batchResolutionSubmitsEveryDecisionInOneCoordinatorCall() {
    TransformationSynchronizationCoordinator synchronization =
        mock(TransformationSynchronizationCoordinator.class);
    AuthSupport auth = mock(AuthSupport.class);
    UserRecord user = user();
    Map<String, ConflictResolution> decisions =
        Map.of(
            "conflict-1",
            ConflictResolution.KEEP_USER,
            "conflict-2",
            ConflictResolution.TAKE_GENERATED);
    when(auth.user("token")).thenReturn(user);
    when(synchronization.resolveAll(eq(user), eq("project-1"), eq("session-1"), eq(decisions)))
        .thenReturn(session(decisions));

    SynchronizationController controller = new SynchronizationController(synchronization, auth);
    SynchronizationController.SynchronizationSessionResponse response =
        controller.resolveBatch(
            "token",
            "project-1",
            "session-1",
            new SynchronizationController.BatchResolutionRequest(decisions));

    assertEquals(decisions, response.resolutions());
    verify(synchronization).resolveAll(user, "project-1", "session-1", decisions);
  }

  @Test
  void finalizationValidationReturnsStructuredIssuesForTheIssueBoard() {
    ModelService.ValidationIssue issue =
        new ModelService.ValidationIssue(
            "ERROR",
            "RequiredReference",
            "StepFunctionStateMachine",
            "A Step Function must reference an IAM role.",
            "Choose the generated role or add a valid role reference.",
            "state-machine-1",
            "Eligibility workflow");

    var response =
        new GlobalExceptionHandler()
            .transformationValidation(new TransformationValidationException(List.of(issue)));

    assertEquals(422, response.getStatusCode().value());
    assertEquals(issue, response.getBody().issues().get(0));
  }

  private SynchronizationSession session(Map<String, ConflictResolution> resolutions) {
    Instant now = Instant.now();
    return new SynchronizationSession(
        "session-1",
        TransformationDirection.CIM_TO_PIM,
        "project-1",
        "source-1",
        "target-1",
        1,
        "working",
        1,
        1,
        "source",
        "etl-assets-v1",
        now,
        null,
        new byte[0],
        new byte[0],
        new byte[0],
        null,
        new byte[0],
        List.of(),
        resolutions);
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
