package io.mehdieidi.modriss.backend.upload;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.mehdieidi.modriss.backend.config.BackendProperties;
import io.mehdieidi.modriss.platform.kernel.ModelLevel;
import io.mehdieidi.modriss.platform.kernel.PlatformException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import tools.jackson.databind.ObjectMapper;

class UploadServiceTest {

  @TempDir Path tempDir;

  @Test
  void storesAndResolvesMarkdownWithinScope() {
    UploadService service = service(1024, 200);
    UploadScope scope = new UploadScope("user", "project", ModelLevel.CIM, "session");

    UploadedFileRecord stored =
        service.uploadAssistantAttachment(
            scope,
            new MockMultipartFile(
                "file",
                "../requirements.md",
                "text/markdown",
                "# Requirements\n\nAs a reviewer, I approve claims."
                    .getBytes(StandardCharsets.UTF_8)));

    List<UploadService.ResolvedAttachment> resolved =
        service.resolveAssistantAttachments(scope, List.of(stored.id()));

    assertEquals(1, resolved.size());
    assertEquals("requirements.md", resolved.get(0).name());
    assertTrue(resolved.get(0).content().contains("approve claims"));
  }

  @Test
  void resolvesRecentAttachmentsWithinScopeOnly() {
    UploadService service = service(1024, 200);
    UploadScope first = new UploadScope("user", "project", ModelLevel.CIM, "session-a");
    UploadScope second = new UploadScope("user", "project", ModelLevel.CIM, "session-b");

    service.uploadAssistantAttachment(
        first,
        new MockMultipartFile(
            "file",
            "event-storming.md",
            "text/markdown",
            "refund policy".getBytes(StandardCharsets.UTF_8)));

    List<UploadService.ResolvedAttachment> sameSession =
        service.resolveRecentAssistantAttachments(first, 3);
    List<UploadService.ResolvedAttachment> otherSession =
        service.resolveRecentAssistantAttachments(second, 3);

    assertEquals(1, sameSession.size());
    assertEquals("event-storming.md", sameSession.get(0).name());
    assertTrue(sameSession.get(0).content().contains("refund policy"));
    assertTrue(otherSession.isEmpty());
  }

  @Test
  void rejectsUnsupportedAssistantExtension() {
    UploadService service = service(1024, 200);
    UploadScope scope = new UploadScope("user", "project", ModelLevel.CIM, "session");

    PlatformException ex =
        assertThrows(
            PlatformException.class,
            () ->
                service.uploadAssistantAttachment(
                    scope,
                    new MockMultipartFile(
                        "file",
                        "requirements.pdf",
                        "application/pdf",
                        "not really a pdf".getBytes(StandardCharsets.UTF_8))));

    assertEquals(415, ex.status());
  }

  @Test
  void rejectsInvalidJsonAttachment() {
    UploadService service = service(1024, 200);
    UploadScope scope = new UploadScope("user", "project", ModelLevel.CIM, "session");

    PlatformException ex =
        assertThrows(
            PlatformException.class,
            () ->
                service.uploadAssistantAttachment(
                    scope,
                    new MockMultipartFile(
                        "file",
                        "requirements.json",
                        "application/json",
                        "{invalid".getBytes(StandardCharsets.UTF_8))));

    assertEquals(400, ex.status());
  }

  @Test
  void preventsCrossSessionReads() {
    UploadService service = service(1024, 200);
    UploadScope first = new UploadScope("user", "project", ModelLevel.CIM, "session-a");
    UploadScope second = new UploadScope("user", "project", ModelLevel.CIM, "session-b");
    UploadedFileRecord stored =
        service.uploadAssistantAttachment(
            first,
            new MockMultipartFile(
                "file", "story.txt", "text/plain", "story".getBytes(StandardCharsets.UTF_8)));

    PlatformException ex =
        assertThrows(
            PlatformException.class,
            () -> service.resolveAssistantAttachments(second, List.of(stored.id())));

    assertEquals(404, ex.status());
  }

  @Test
  void reportsStorageFailureAsServiceUnavailable() {
    ObjectMapper mapper = new ObjectMapper();
    BackendProperties properties =
        new BackendProperties(
            tempDir,
            Duration.ofDays(1),
            List.of(),
            null,
            new BackendProperties.Upload(Path.of("uploads"), 1024L, 200),
            null);
    UploadService service = new UploadService(new FailingUploadStorage(), properties, mapper);
    UploadScope scope = new UploadScope("user", "project", ModelLevel.CIM, "session");

    PlatformException ex =
        assertThrows(
            PlatformException.class,
            () ->
                service.uploadAssistantAttachment(
                    scope,
                    new MockMultipartFile(
                        "file",
                        "story.md",
                        "text/markdown",
                        "# Story".getBytes(StandardCharsets.UTF_8))));

    assertEquals(503, ex.status());
  }

  private UploadService service(long maxFileBytes, int maxTextChars) {
    ObjectMapper mapper = new ObjectMapper();
    BackendProperties properties =
        new BackendProperties(
            tempDir,
            Duration.ofDays(1),
            List.of(),
            null,
            new BackendProperties.Upload(Path.of("uploads"), maxFileBytes, maxTextChars),
            null);
    return new UploadService(
        new LocalUploadStorage(tempDir.resolve("uploads"), mapper), properties, mapper);
  }

  private static final class FailingUploadStorage implements UploadStorage {

    @Override
    public UploadedFileRecord store(
        UploadScope scope, String originalFileName, String contentType, byte[] content)
        throws IOException {
      throw new IOException("disk is not writable");
    }

    @Override
    public Optional<UploadedFileRecord> find(UploadScope scope, String id) {
      return Optional.empty();
    }

    @Override
    public List<UploadedFileRecord> listRecent(UploadScope scope, int limit) {
      return List.of();
    }

    @Override
    public byte[] read(UploadedFileRecord record) {
      return new byte[0];
    }
  }
}
