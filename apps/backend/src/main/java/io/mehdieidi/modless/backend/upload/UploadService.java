package io.mehdieidi.modless.backend.upload;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.mehdieidi.modless.backend.config.BackendProperties;
import io.mehdieidi.modless.platform.kernel.PlatformException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.multipart.MultipartFile;

/** Validates, stores, and resolves user uploaded files. */
public class UploadService {

  private static final Logger log = LoggerFactory.getLogger(UploadService.class);
  private static final Set<String> ASSISTANT_TEXT_EXTENSIONS = Set.of("md", "txt", "json");

  private final UploadStorage storage;
  private final ObjectMapper mapper;
  private final long maxFileBytes;
  private final int maxTextChars;

  /**
   * Creates the upload service.
   *
   * @param storage storage adapter
   * @param properties backend configuration
   * @param mapper JSON mapper for structured validation
   */
  public UploadService(UploadStorage storage, BackendProperties properties, ObjectMapper mapper) {
    this.storage = storage;
    this.mapper = mapper;
    this.maxFileBytes = Math.max(1, properties.upload().maxFileBytes());
    this.maxTextChars = Math.max(1, properties.upload().maxTextChars());
  }

  /**
   * Stores one text attachment for assistant use.
   *
   * @param scope ownership scope
   * @param file uploaded multipart file
   * @return upload metadata
   */
  public UploadedFileRecord uploadAssistantAttachment(UploadScope scope, MultipartFile file) {
    if (file == null || file.isEmpty()) {
      throw new PlatformException(400, "Attachment file is required.");
    }
    if (file.getSize() > maxFileBytes) {
      throw new PlatformException(413, "Attachment file is too large.");
    }
    String fileName = file.getOriginalFilename();
    String extension = extension(fileName);
    if (!ASSISTANT_TEXT_EXTENSIONS.contains(extension)) {
      throw new PlatformException(415, "Only .md, .txt, and .json attachments are supported.");
    }
    byte[] bytes = readRequestContent(file);
    String text = decodeUtf8(bytes);
    rejectSuspiciousText(text);
    if ("json".equals(extension)) {
      validateJson(text);
    }
    try {
      return storage.store(scope, fileName, file.getContentType(), bytes);
    } catch (PlatformException ex) {
      throw ex;
    } catch (IOException ex) {
      log.error(
          "Attachment storage failed for user={}, project={}, level={}, session={}, file={}",
          scope.userId(),
          scope.projectId(),
          scope.level().apiName(),
          scope.sessionId(),
          fileName,
          ex);
      throw new PlatformException(503, "Attachment storage is temporarily unavailable.");
    }
  }

  /**
   * Resolves attachment IDs into prompt-safe text documents.
   *
   * @param scope ownership scope
   * @param attachmentIds uploaded attachment IDs
   * @return resolved text attachments
   */
  public List<ResolvedAttachment> resolveAssistantAttachments(
      UploadScope scope, List<String> attachmentIds) {
    if (attachmentIds == null || attachmentIds.isEmpty()) {
      return List.of();
    }
    if (attachmentIds.size() > 3) {
      throw new PlatformException(400, "At most 3 attachments can be sent in one message.");
    }
    return attachmentIds.stream().map(id -> resolveAssistantAttachment(scope, id)).toList();
  }

  /**
   * Resolves the newest assistant attachments in the supplied scope.
   *
   * @param scope ownership scope
   * @param limit maximum number of attachments
   * @return prompt-safe text attachments, newest first
   */
  public List<ResolvedAttachment> resolveRecentAssistantAttachments(UploadScope scope, int limit) {
    if (limit <= 0) {
      return List.of();
    }
    try {
      return storage.listRecent(scope, Math.min(limit, 3)).stream()
          .map(record -> resolveAssistantAttachment(scope, record.id()))
          .toList();
    } catch (PlatformException ex) {
      throw ex;
    } catch (IOException ex) {
      throw new PlatformException(400, "Recent attachments could not be read.");
    }
  }

  private ResolvedAttachment resolveAssistantAttachment(UploadScope scope, String attachmentId) {
    try {
      UploadedFileRecord record =
          storage
              .find(scope, attachmentId)
              .orElseThrow(() -> new PlatformException(404, "Attachment not found."));
      String text = decodeUtf8(storage.read(record)).trim();
      if (text.length() > maxTextChars) {
        text = text.substring(0, maxTextChars) + "\n...[attachment truncated by backend]";
      }
      return new ResolvedAttachment(record.id(), record.originalFileName(), text);
    } catch (PlatformException ex) {
      throw ex;
    } catch (IOException ex) {
      throw new PlatformException(400, "Attachment could not be read.");
    }
  }

  private byte[] readBounded(InputStream input, long maxBytes) throws IOException {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    byte[] buffer = new byte[8192];
    long total = 0;
    int read;
    while ((read = input.read(buffer)) != -1) {
      total += read;
      if (total > maxBytes) {
        throw new PlatformException(413, "Attachment file is too large.");
      }
      output.write(buffer, 0, read);
    }
    return output.toByteArray();
  }

  private byte[] readRequestContent(MultipartFile file) {
    try {
      return readBounded(file.getInputStream(), maxFileBytes);
    } catch (PlatformException ex) {
      throw ex;
    } catch (IOException ex) {
      throw new PlatformException(400, "Attachment could not be read from the request.");
    }
  }

  private void validateJson(String text) {
    try {
      mapper.readTree(text);
    } catch (IOException ex) {
      throw new PlatformException(400, "Attachment must contain valid JSON.");
    }
  }

  private String decodeUtf8(byte[] bytes) {
    try {
      return StandardCharsets.UTF_8
          .newDecoder()
          .onMalformedInput(CodingErrorAction.REPORT)
          .onUnmappableCharacter(CodingErrorAction.REPORT)
          .decode(ByteBuffer.wrap(bytes))
          .toString();
    } catch (CharacterCodingException ex) {
      throw new PlatformException(415, "Attachment must be valid UTF-8 text.");
    }
  }

  private void rejectSuspiciousText(String text) {
    if (text.indexOf('\u0000') >= 0) {
      throw new PlatformException(415, "Attachment must be text, not binary data.");
    }
  }

  private String extension(String fileName) {
    if (fileName == null || fileName.isBlank()) {
      return "";
    }
    String name = fileName.trim();
    int index = name.lastIndexOf('.');
    return index < 0 || index == name.length() - 1
        ? ""
        : name.substring(index + 1).toLowerCase(Locale.ROOT);
  }

  /** Prompt-safe text content for one attachment. */
  public record ResolvedAttachment(String id, String name, String content) {}
}
