package io.mehdieidi.modless.backend.api;

import com.fasterxml.jackson.databind.JsonNode;
import io.mehdieidi.modless.platform.core.model.MdeJobRecord;
import io.mehdieidi.modless.platform.core.model.MdeJobStatus;
import io.mehdieidi.modless.platform.core.model.ModelLevel;
import io.mehdieidi.modless.platform.core.model.ModelRecord;
import io.mehdieidi.modless.platform.core.service.MdeJobService;
import io.mehdieidi.modless.platform.core.service.ModelService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Provides authenticated model CRUD, validation, import, export, and patch endpoints. */
@RestController
public class ModelController {

  private static final Logger LOGGER = LoggerFactory.getLogger(ModelController.class);

  private final ModelService models;
  private final MdeJobService jobs;
  private final AuthSupport auth;

  /**
   * Creates the model controller.
   *
   * @param models model service
   * @param jobs async MDE job service
   * @param auth controller authentication support
   */
  public ModelController(ModelService models, MdeJobService jobs, AuthSupport auth) {
    this.models = models;
    this.jobs = jobs;
    this.auth = auth;
  }

  /**
   * Lists model summaries for a level and optional project.
   *
   * @param token session token
   * @param level model level API name
   * @param projectId optional project identifier
   * @return matching model summaries
   */
  @GetMapping("/api/{level:cim|pim|psm}")
  List<ModelService.ModelSummary> list(
      @RequestHeader("X-Auth-Token") String token,
      @PathVariable("level") String level,
      @RequestParam(value = "projectId", required = false) String projectId) {
    return models.listSummaries(auth.user(token), ModelLevel.fromApiName(level), projectId);
  }

  /**
   * Creates a model at the requested level.
   *
   * @param token session token
   * @param level model level API name
   * @param request model details
   * @return created model summary
   */
  @PostMapping("/api/{level:cim|pim|psm}")
  ModelService.ModelSummary create(
      @RequestHeader("X-Auth-Token") String token,
      @PathVariable("level") String level,
      @Valid @RequestBody SaveModelRequest request) {
    ModelRecord created =
        models.create(
            auth.user(token),
            ModelLevel.fromApiName(level),
            request.projectId(),
            request.name(),
            request.model());
    return models.summary(created);
  }

  /**
   * Returns a model by level and identifier.
   *
   * @param token session token
   * @param level model level API name
   * @param id model identifier
   * @return requested model
   */
  @GetMapping("/api/{level:cim|pim|psm}/{id}")
  ModelRecord get(
      @RequestHeader("X-Auth-Token") String token,
      @PathVariable("level") String level,
      @PathVariable("id") String id) {
    return models.get(auth.user(token), ModelLevel.fromApiName(level), id);
  }

  /**
   * Replaces a model when its revision matches the request.
   *
   * @param token session token
   * @param level model level API name
   * @param id model identifier
   * @param request replacement model and expected revision
   * @return updated model summary
   */
  @PutMapping("/api/{level:cim|pim|psm}/{id}")
  ModelService.ModelSummary update(
      @RequestHeader("X-Auth-Token") String token,
      @PathVariable("level") String level,
      @PathVariable("id") String id,
      @Valid @RequestBody SaveModelRequest request) {
    requireExpectedRevision(request.expectedRevision());
    ModelRecord updated =
        models.update(
            auth.user(token),
            ModelLevel.fromApiName(level),
            id,
            request.name(),
            request.model(),
            request.expectedRevision());
    return models.summary(updated);
  }

  /**
   * Applies small change operations when the model revision matches the request.
   *
   * @param token session token
   * @param level model level API name
   * @param id model identifier
   * @param request optional name, operations, and expected revision
   * @return updated model summary
   */
  @PatchMapping("/api/{level:cim|pim|psm}/{id}")
  ModelService.ModelSummary patch(
      @RequestHeader("X-Auth-Token") String token,
      @PathVariable("level") String level,
      @PathVariable("id") String id,
      @RequestBody PatchModelRequest request) {
    requireExpectedRevision(request == null ? null : request.expectedRevision());
    ModelRecord updated =
        models.patch(
            auth.user(token),
            ModelLevel.fromApiName(level),
            id,
            request == null ? null : request.name(),
            request == null ? List.of() : request.operations(),
            request == null ? null : request.expectedRevision());
    return models.summary(updated);
  }

  /**
   * Deletes a model.
   *
   * @param token session token
   * @param level model level API name
   * @param id model identifier
   */
  @DeleteMapping("/api/{level:cim|pim|psm}/{id}")
  void delete(
      @RequestHeader("X-Auth-Token") String token,
      @PathVariable("level") String level,
      @PathVariable("id") String id) {
    models.delete(auth.user(token), ModelLevel.fromApiName(level), id);
  }

  /**
   * Validates an ad hoc model payload.
   *
   * @param token session token
   * @param level model level API name
   * @param request model payload
   * @return validation result
   */
  @PostMapping("/api/{level:cim|pim|psm}/validate")
  ModelService.ValidationResult validate(
      @RequestHeader("X-Auth-Token") String token,
      @PathVariable("level") String level,
      @RequestBody SaveModelRequest request) {
    auth.user(token);
    return models.validate(ModelLevel.fromApiName(level), request.model());
  }

  /**
   * Validates a stored model.
   *
   * @param token session token
   * @param level model level API name
   * @param id model identifier
   * @return validation result
   */
  @PostMapping("/api/{level:cim|pim|psm}/{id}/validate")
  ModelService.ValidationResult validateStored(
      @RequestHeader("X-Auth-Token") String token,
      @PathVariable("level") String level,
      @PathVariable("id") String id) {
    return models.validate(auth.user(token), ModelLevel.fromApiName(level), id);
  }

  /**
   * Submits asynchronous validation for a stored model.
   *
   * @param token session token
   * @param idempotencyKey optional idempotency key
   * @param level model level API name
   * @param id model identifier
   * @param request optional expected revision
   * @return queued validation job
   */
  @PostMapping("/api/{level:cim|pim|psm}/{id}/validate/jobs")
  ResponseEntity<ValidationJobResponse> validateStoredJob(
      @RequestHeader("X-Auth-Token") String token,
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
      @PathVariable("level") String level,
      @PathVariable("id") String id,
      @RequestBody(required = false) ValidateStoredRequest request) {
    long started = System.nanoTime();
    MdeJobRecord job =
        jobs.submitValidation(
            auth.user(token),
            ModelLevel.fromApiName(level),
            id,
            request == null ? null : request.expectedRevision(),
            idempotencyKey);
    long controllerMs = millisSince(started);
    jobs.appendTimings(job.id(), Map.of("controller.acceptMs", controllerMs));
    LOGGER.info(
        "mde_request_accepted route=stored-validation jobId={} projectId={} operation={} "
            + "controllerMs={} status={}",
        job.id(),
        job.projectId(),
        job.operation(),
        controllerMs,
        job.status());
    return ResponseEntity.accepted()
        .location(URI.create("/api/transformations/jobs/" + job.id()))
        .body(new ValidationJobResponse(job.id(), job.status()));
  }

  /**
   * Exports an ad hoc model as JSON or XMI.
   *
   * @param token session token
   * @param level model level API name
   * @param request export name, model, and format
   * @return downloadable model response
   */
  @PostMapping("/api/{level:cim|pim|psm}/export")
  ResponseEntity<byte[]> export(
      @RequestHeader("X-Auth-Token") String token,
      @PathVariable("level") String level,
      @RequestBody ExportRequest request) {
    auth.user(token);
    ModelLevel modelLevel = ModelLevel.fromApiName(level);
    String format = request == null ? "json" : request.format();
    byte[] bytes = models.exportModel(modelLevel, request == null ? null : request.model(), format);
    String extension = extension(format);
    String fileName =
        ((request == null || request.name() == null) ? level + "-model" : request.name())
            + extension;
    return ResponseEntity.ok()
        .contentType(mediaType(format))
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.attachment()
                .filename(fileName, StandardCharsets.UTF_8)
                .build()
                .toString())
        .body(bytes);
  }

  /**
   * Exports a stored model as JSON or XMI.
   *
   * @param token session token
   * @param level model level API name
   * @param id model identifier
   * @param request export name and format
   * @return downloadable model response
   */
  @PostMapping("/api/{level:cim|pim|psm}/{id}/export")
  ResponseEntity<byte[]> exportStored(
      @RequestHeader("X-Auth-Token") String token,
      @PathVariable("level") String level,
      @PathVariable("id") String id,
      @RequestBody ExportRequest request) {
    byte[] bytes =
        models.exportModel(
            auth.user(token),
            ModelLevel.fromApiName(level),
            id,
            request == null ? "json" : request.format());
    String format = request == null ? "json" : request.format();
    String fileName =
        ((request == null || request.name() == null) ? level + "-model" : request.name())
            + extension(format);
    return ResponseEntity.ok()
        .contentType(mediaType(format))
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.attachment()
                .filename(fileName, StandardCharsets.UTF_8)
                .build()
                .toString())
        .body(bytes);
  }

  /**
   * Stages an uploaded JSON or XMI model for import.
   *
   * @param token session token
   * @param level model level API name
   * @param projectId destination project identifier
   * @param format uploaded model format
   * @param file uploaded model file
   * @return staged import result
   * @throws Exception if the upload stream cannot be read
   */
  @PostMapping(
      value = "/api/{level:cim|pim|psm}/import",
      consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  ModelService.ImportResult importModel(
      @RequestHeader("X-Auth-Token") String token,
      @PathVariable("level") String level,
      @RequestParam("projectId") String projectId,
      @RequestParam(value = "format", defaultValue = "json") String format,
      @RequestParam("file") MultipartFile file)
      throws Exception {
    if (file.getSize() > models.maxModelUploadBytes()) {
      throw new io.mehdieidi.modless.platform.core.PlatformException(
          413, "Model file is too large.");
    }
    try (var input = file.getInputStream()) {
      return models.importModelFromStream(
          auth.user(token),
          projectId,
          ModelLevel.fromApiName(level),
          file.getOriginalFilename(),
          input,
          file.getSize(),
          format);
    }
  }

  /**
   * Enforces optimistic-concurrency input for model mutations.
   *
   * @param expectedRevision requested model revision
   */
  private void requireExpectedRevision(Long expectedRevision) {
    if (expectedRevision == null) {
      throw new io.mehdieidi.modless.platform.core.PlatformException(
          400, "expectedRevision is required.");
    }
  }

  /**
   * Resolves the download media type for an export format.
   *
   * @param format requested export format
   * @return XML for XMI, otherwise JSON
   */
  private MediaType mediaType(String format) {
    return "xmi".equalsIgnoreCase(format) ? MediaType.APPLICATION_XML : MediaType.APPLICATION_JSON;
  }

  /**
   * Resolves the file extension for an export format.
   *
   * @param format requested export format
   * @return file extension including its leading period
   */
  private String extension(String format) {
    return "xmi".equalsIgnoreCase(format) ? ".xmi" : ".json";
  }

  private long millisSince(long startedNanos) {
    return Math.max(0L, (System.nanoTime() - startedNanos) / 1_000_000L);
  }

  /**
   * Model create, update, or ad hoc validation payload.
   *
   * @param name model name
   * @param model model JSON tree
   * @param projectId owning project identifier
   * @param expectedRevision expected revision for updates
   */
  public record SaveModelRequest(
      @NotBlank String name, JsonNode model, String projectId, Long expectedRevision) {}

  /**
   * Incremental model update payload.
   *
   * @param name optional replacement model name
   * @param operations patch operations
   * @param expectedRevision expected current revision
   */
  public record PatchModelRequest(
      String name, List<ModelService.ModelPatchOperation> operations, Long expectedRevision) {}

  /**
   * Ad hoc or stored model export options.
   *
   * @param name optional download base name
   * @param model ad hoc model JSON tree
   * @param format requested export format
   */
  public record ExportRequest(String name, JsonNode model, String format) {}

  /** Stored validation job request payload. */
  public record ValidateStoredRequest(Long expectedRevision) {}

  /** Minimal async validation job response. */
  public record ValidationJobResponse(String id, MdeJobStatus status) {}
}
