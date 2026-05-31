package io.mehdieidi.modless.backend.api;

import com.fasterxml.jackson.databind.JsonNode;
import io.mehdieidi.modless.platform.core.model.ModelLevel;
import io.mehdieidi.modless.platform.core.model.ModelRecord;
import io.mehdieidi.modless.platform.core.service.ModelService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.nio.charset.StandardCharsets;
import java.util.List;
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

@RestController
public class ModelController {

    private final ModelService models;
    private final AuthSupport auth;

    public ModelController(ModelService models, AuthSupport auth) {
        this.models = models;
        this.auth = auth;
    }

    @GetMapping("/api/{level:cim|pim|psm}")
    List<ModelService.ModelSummary> list(@RequestHeader("X-Auth-Token") String token,
            @PathVariable("level") String level,
            @RequestParam(value = "projectId", required = false) String projectId) {
        return models.listSummaries(auth.user(token), ModelLevel.fromApiName(level), projectId);
    }

    @PostMapping("/api/{level:cim|pim|psm}")
    ModelService.ModelSummary create(@RequestHeader("X-Auth-Token") String token,
            @PathVariable("level") String level,
            @Valid @RequestBody SaveModelRequest request) {
        ModelRecord created = models.create(auth.user(token), ModelLevel.fromApiName(level),
                request.projectId(), request.name(), request.model());
        return models.summary(created);
    }

    @GetMapping("/api/{level:cim|pim|psm}/{id}")
    ModelRecord get(@RequestHeader("X-Auth-Token") String token,
            @PathVariable("level") String level,
            @PathVariable("id") String id) {
        return models.get(auth.user(token), ModelLevel.fromApiName(level), id);
    }

    @PutMapping("/api/{level:cim|pim|psm}/{id}")
    ModelService.ModelSummary update(@RequestHeader("X-Auth-Token") String token,
            @PathVariable("level") String level,
            @PathVariable("id") String id,
            @Valid @RequestBody SaveModelRequest request) {
        requireExpectedRevision(request.expectedRevision());
        ModelRecord updated = models.update(auth.user(token), ModelLevel.fromApiName(level), id,
                request.name(), request.model(), request.expectedRevision());
        return models.summary(updated);
    }

    @PatchMapping("/api/{level:cim|pim|psm}/{id}")
    ModelService.ModelSummary patch(@RequestHeader("X-Auth-Token") String token,
            @PathVariable("level") String level,
            @PathVariable("id") String id,
            @RequestBody PatchModelRequest request) {
        requireExpectedRevision(request == null ? null : request.expectedRevision());
        ModelRecord updated = models.patch(auth.user(token), ModelLevel.fromApiName(level), id,
                request == null ? null : request.name(),
                request == null ? List.of() : request.operations(),
                request == null ? null : request.expectedRevision());
        return models.summary(updated);
    }

    @DeleteMapping("/api/{level:cim|pim|psm}/{id}")
    void delete(@RequestHeader("X-Auth-Token") String token, @PathVariable("level") String level,
            @PathVariable("id") String id) {
        models.delete(auth.user(token), ModelLevel.fromApiName(level), id);
    }

    @PostMapping("/api/{level:cim|pim|psm}/validate")
    ModelService.ValidationResult validate(@RequestHeader("X-Auth-Token") String token,
            @PathVariable("level") String level,
            @RequestBody SaveModelRequest request) {
        auth.user(token);
        return models.validate(ModelLevel.fromApiName(level), request.model());
    }

    @PostMapping("/api/{level:cim|pim|psm}/{id}/validate")
    ModelService.ValidationResult validateStored(@RequestHeader("X-Auth-Token") String token,
            @PathVariable("level") String level,
            @PathVariable("id") String id) {
        return models.validate(auth.user(token), ModelLevel.fromApiName(level), id);
    }

    @PostMapping("/api/{level:cim|pim|psm}/export")
    ResponseEntity<byte[]> export(@RequestHeader("X-Auth-Token") String token,
            @PathVariable("level") String level,
            @RequestBody ExportRequest request) {
        auth.user(token);
        ModelLevel modelLevel = ModelLevel.fromApiName(level);
        String format = request == null ? "json" : request.format();
        byte[] bytes = models.exportModel(modelLevel, request == null ? null : request.model(),
                format);
        String extension = extension(format);
        String fileName = ((request == null || request.name() == null) ? level + "-model"
                : request.name()) + extension;
        return ResponseEntity.ok()
                .contentType(mediaType(format))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(fileName, StandardCharsets.UTF_8)
                                .build().toString())
                .body(bytes);
    }

    @PostMapping("/api/{level:cim|pim|psm}/{id}/export")
    ResponseEntity<byte[]> exportStored(@RequestHeader("X-Auth-Token") String token,
            @PathVariable("level") String level,
            @PathVariable("id") String id,
            @RequestBody ExportRequest request) {
        byte[] bytes = models.exportModel(auth.user(token), ModelLevel.fromApiName(level), id,
                request == null ? "json" : request.format());
        String format = request == null ? "json" : request.format();
        String fileName = ((request == null || request.name() == null) ? level + "-model"
                : request.name()) + extension(format);
        return ResponseEntity.ok()
                .contentType(mediaType(format))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(fileName, StandardCharsets.UTF_8)
                                .build().toString())
                .body(bytes);
    }

    @PostMapping(value = "/api/{level:cim|pim|psm}/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ModelService.ImportResult importModel(@RequestHeader("X-Auth-Token") String token,
            @PathVariable("level") String level,
            @RequestParam("projectId") String projectId,
            @RequestParam(value = "format", defaultValue = "json") String format,
            @RequestParam("file") MultipartFile file) throws Exception {
        if (file.getSize() > models.maxModelUploadBytes()) {
            throw new io.mehdieidi.modless.platform.core.PlatformException(413,
                    "Model file is too large.");
        }
        try (var input = file.getInputStream()) {
            return models.importModelFromStream(auth.user(token), projectId,
                    ModelLevel.fromApiName(level), file.getOriginalFilename(), input,
                    file.getSize(), format);
        }
    }

    private void requireExpectedRevision(Long expectedRevision) {
        if (expectedRevision == null) {
            throw new io.mehdieidi.modless.platform.core.PlatformException(400,
                    "expectedRevision is required.");
        }
    }

    private MediaType mediaType(String format) {
        return "xmi".equalsIgnoreCase(format)
                ? MediaType.APPLICATION_XML
                : MediaType.APPLICATION_JSON;
    }

    private String extension(String format) {
        return "xmi".equalsIgnoreCase(format) ? ".xmi" : ".json";
    }

    public record SaveModelRequest(@NotBlank String name, JsonNode model, String projectId,
                                   Long expectedRevision) {

    }

    public record PatchModelRequest(String name,
                                    List<ModelService.ModelPatchOperation> operations,
                                    Long expectedRevision) {

    }

    public record ExportRequest(String name, JsonNode model, String format) {

    }
}
