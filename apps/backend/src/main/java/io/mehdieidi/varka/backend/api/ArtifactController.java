package io.mehdieidi.varka.backend.api;

import io.mehdieidi.varka.platform.artifact.application.ArtifactService;
import io.mehdieidi.varka.platform.artifact.domain.ArtifactRecord;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Provides authenticated access to generated artifacts and their files. */
@RestController
@RequestMapping("/api/artifact")
public class ArtifactController {

  private final ArtifactService artifacts;
  private final AuthSupport auth;

  /**
   * Creates the artifact controller.
   *
   * @param artifacts artifact service
   * @param auth controller authentication support
   */
  public ArtifactController(ArtifactService artifacts, AuthSupport auth) {
    this.artifacts = artifacts;
    this.auth = auth;
  }

  /**
   * Lists artifacts belonging to a project.
   *
   * @param token session token
   * @param projectId project identifier
   * @return accessible project artifacts
   */
  @GetMapping
  List<ArtifactRecord> list(
      @RequestHeader("X-Auth-Token") String token, @RequestParam("projectId") String projectId) {
    return artifacts.list(auth.user(token), projectId);
  }

  /**
   * Returns an artifact by identifier.
   *
   * @param token session token
   * @param id artifact identifier
   * @return requested artifact
   */
  @GetMapping("/{id}")
  ArtifactRecord get(@RequestHeader("X-Auth-Token") String token, @PathVariable("id") String id) {
    return artifacts.get(auth.user(token), id);
  }

  /**
   * Reads a text file from an artifact.
   *
   * @param token session token
   * @param id artifact identifier
   * @param path artifact-relative file path
   * @return file content
   */
  @GetMapping(value = "/{id}/file", produces = MediaType.TEXT_PLAIN_VALUE)
  String file(
      @RequestHeader("X-Auth-Token") String token,
      @PathVariable("id") String id,
      @RequestParam("path") String path) {
    return artifacts.readFile(auth.user(token), id, path);
  }

  /**
   * Replaces or creates one text file in an artifact.
   *
   * @param token session token
   * @param id artifact identifier
   * @param request file path and content
   * @return updated artifact
   */
  @PutMapping("/{id}/files")
  ArtifactRecord saveFile(
      @RequestHeader("X-Auth-Token") String token,
      @PathVariable("id") String id,
      @Valid @RequestBody SaveFileRequest request) {
    return artifacts.updateFile(auth.user(token), id, request.path(), request.content());
  }

  /**
   * Downloads an artifact and all of its files as a ZIP archive.
   *
   * @param token session token
   * @param id artifact identifier
   * @return ZIP download response
   */
  @GetMapping("/{id}/download")
  ResponseEntity<byte[]> download(
      @RequestHeader("X-Auth-Token") String token, @PathVariable("id") String id) {
    ArtifactRecord artifact = artifacts.get(auth.user(token), id);
    byte[] bytes = artifacts.zip(auth.user(token), id);
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType("application/zip"))
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.attachment()
                .filename(artifact.name() + ".zip", StandardCharsets.UTF_8)
                .build()
                .toString())
        .body(bytes);
  }

  /**
   * Artifact file update payload.
   *
   * @param path artifact-relative file path
   * @param content replacement file content
   */
  public record SaveFileRequest(@NotBlank String path, String content) {}
}
