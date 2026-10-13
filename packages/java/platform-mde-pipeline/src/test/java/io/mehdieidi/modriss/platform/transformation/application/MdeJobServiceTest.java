package io.mehdieidi.modriss.platform.transformation.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.mehdieidi.modriss.platform.kernel.ModelLevel;
import io.mehdieidi.modriss.platform.kernel.PlatformException;
import io.mehdieidi.modriss.platform.model.application.ModelService;
import io.mehdieidi.modriss.platform.model.domain.ModelRecord;
import io.mehdieidi.modriss.platform.modeling.runtime.MdeRuntimeOptions;
import io.mehdieidi.modriss.platform.transformation.domain.MdeJobRecord;
import io.mehdieidi.modriss.platform.transformation.domain.MdeJobStatus;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Tests async MDE job submission, idempotency, and stored validation polling. */
class MdeJobServiceTest {

  @TempDir Path tempDir;

  @Test
  /** Catalog F-12: retrying an identical request reuses the original logical job. */
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
  /** Catalog F-13: an idempotency key cannot be reused for different input. */
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
      MdeJobService jobs, io.mehdieidi.modriss.platform.identity.domain.UserRecord user, String id)
      throws Exception {
    // Stored validation uses the same runtime timeout as the worker and may pay the one-time
    // EMF/EVL initialization cost on a clean build. The polling window must cover that contract.
    long deadline = System.nanoTime() + Duration.ofMinutes(3).toNanos();
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
