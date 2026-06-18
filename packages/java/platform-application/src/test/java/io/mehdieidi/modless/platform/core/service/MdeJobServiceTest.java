package io.mehdieidi.modless.platform.core.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.mehdieidi.modless.platform.core.PlatformException;
import io.mehdieidi.modless.platform.core.model.MdeJobRecord;
import io.mehdieidi.modless.platform.core.model.MdeJobStatus;
import io.mehdieidi.modless.platform.core.model.ModelLevel;
import io.mehdieidi.modless.platform.core.model.ModelRecord;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Tests async MDE job submission, idempotency, and stored validation polling. */
class MdeJobServiceTest {

  @TempDir Path tempDir;

  @Test
  void idempotencyKeyReplaysOriginalJobForSameFingerprint() throws Exception {
    TestContext context = context();
    MdeJobRecord first =
        context
            .jobs()
            .submitValidation(
                context.auth().user(),
                ModelLevel.CIM,
                context.model().id(),
                context.model().revision(),
                "same");
    MdeJobRecord replay =
        context
            .jobs()
            .submitValidation(
                context.auth().user(),
                ModelLevel.CIM,
                context.model().id(),
                context.model().revision(),
                "same");

    assertEquals(first.id(), replay.id());
    assertEquals(first.fingerprint(), replay.fingerprint());
    context.jobs().close();
  }

  @Test
  void idempotencyKeyRejectsDifferentFingerprint() throws Exception {
    TestContext context = context();
    context
        .jobs()
        .submitValidation(
            context.auth().user(),
            ModelLevel.CIM,
            context.model().id(),
            context.model().revision(),
            "reuse");

    PlatformException ex =
        assertThrows(
            PlatformException.class,
            () ->
                context
                    .jobs()
                    .submitCimToPim(
                        context.auth().user(),
                        context.model().id(),
                        context.model().revision(),
                        "reuse"));

    assertEquals(409, ex.status());
    context.jobs().close();
  }

  @Test
  void storedValidationJobPublishesResultAndTimings() throws Exception {
    TestContext context = context();
    MdeJobRecord submitted =
        context
            .jobs()
            .submitValidation(
                context.auth().user(),
                ModelLevel.CIM,
                context.model().id(),
                context.model().revision(),
                "validate");

    MdeJobRecord finished = waitForTerminal(context.jobs(), context.auth().user(), submitted.id());

    assertTrue(
        finished.status() == MdeJobStatus.SUCCEEDED || finished.status() == MdeJobStatus.FAILED);
    assertNotNull(finished.validationResult());
    assertFalse(finished.timings().isEmpty());
    assertTrue(finished.timings().containsKey("worker.queueWaitMs"));
    assertTrue(finished.timings().containsKey("worker.totalMs"));
    assertTrue(finished.timings().containsKey("submit.modelLookupMs"));
    assertTrue(finished.timings().containsKey("validation.totalMs"));
    assertTrue(finished.timings().containsKey("validation.evlExecuteMs"));
    context.jobs().close();
  }

  private TestContext context() throws Exception {
    PlatformTestFixtures.ServiceStack services = PlatformTestFixtures.createServices(tempDir);
    PlatformTestFixtures.AuthenticatedContext auth =
        PlatformTestFixtures.registerOwner(
            services, "owner-" + System.nanoTime() + "@example.com", "Owner", "Climate");
    ModelService.ImportResult imported =
        services
            .models()
            .importModel(ModelLevel.CIM, "cim.xmi", PlatformTestFixtures.climateCimXmi(), "xmi");
    ModelRecord model =
        services
            .models()
            .create(
                auth.user(),
                ModelLevel.CIM,
                auth.project().id(),
                "climate-cim",
                imported.modelJson());
    MdeRuntimeOptions options =
        new MdeRuntimeOptions(
            null,
            null,
            null,
            null,
            null,
            Duration.ofMinutes(2),
            0,
            1,
            4,
            Duration.ofMinutes(3),
            0,
            0,
            0,
            0,
            null);
    MdeJobService jobs =
        new MdeJobService(services.store(), services.projects(), services.models(), null, options);
    return new TestContext(auth, model, jobs);
  }

  private MdeJobRecord waitForTerminal(
      MdeJobService jobs, io.mehdieidi.modless.platform.core.model.UserRecord user, String id)
      throws Exception {
    long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
    MdeJobRecord job = jobs.get(user, id);
    while (System.nanoTime() < deadline) {
      job = jobs.get(user, id);
      if (job.status() == MdeJobStatus.SUCCEEDED
          || job.status() == MdeJobStatus.FAILED
          || job.status() == MdeJobStatus.CANCELLED) {
        return job;
      }
      Thread.sleep(50);
    }
    return job;
  }

  private record TestContext(
      PlatformTestFixtures.AuthenticatedContext auth, ModelRecord model, MdeJobService jobs) {}
}
