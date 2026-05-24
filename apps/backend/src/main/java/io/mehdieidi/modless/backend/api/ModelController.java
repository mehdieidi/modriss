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
    List<ModelRecord> list(@RequestHeader("X-Auth-Token") String token,
            @PathVariable("level") String level,
            @RequestParam(value = "projectId", required = false) String projectId) {
        return models.list(auth.user(token), ModelLevel.fromApiName(level), projectId);
    }

    @PostMapping("/api/{level:cim|pim|psm}")
    ModelRecord create(@RequestHeader("X-Auth-Token") String token,
            @PathVariable("level") String level,
            @Valid @RequestBody SaveModelRequest request) {
        return models.create(auth.user(token), ModelLevel.fromApiName(level), request.projectId(),
                request.name(),
                request.model());
    }

    @GetMapping("/api/{level:cim|pim|psm}/{id}")
    ModelRecord get(@RequestHeader("X-Auth-Token") String token,
            @PathVariable("level") String level,
            @PathVariable("id") String id) {
        return models.get(auth.user(token), ModelLevel.fromApiName(level), id);
    }

    @PutMapping("/api/{level:cim|pim|psm}/{id}")
    ModelRecord update(@RequestHeader("X-Auth-Token") String token,
            @PathVariable("level") String level,
            @PathVariable("id") String id,
            @Valid @RequestBody SaveModelRequest request) {
        return models.update(auth.user(token), ModelLevel.fromApiName(level), id, request.name(),
                request.model());
    }

    @DeleteMapping("/api/{level:cim|pim|psm}/{id}")
    void delete(@RequestHeader("X-Auth-Token") String token, @PathVariable("level") String level,
            @PathVariable("id") String id) {
        models.delete(auth.user(token), ModelLevel.fromApiName(level), id);
    }

    @PostMapping("/api/{level:cim|pim|psm}/validate")
    ModelService.ValidationResult validate(@PathVariable("level") String level,
            @RequestBody SaveModelRequest request) {
        return models.validate(ModelLevel.fromApiName(level), request.model());
    }

    @PostMapping("/api/{level:cim|pim|psm}/export")
    ResponseEntity<byte[]> export(@PathVariable("level") String level,
            @RequestBody ExportRequest request) {
        byte[] bytes = models.exportModel(request.model(), request.format());
        String fileName = (request.name() == null ? level + "-model" : request.name()) + ".json";
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(fileName, StandardCharsets.UTF_8)
                                .build().toString())
                .body(bytes);
    }

    @PostMapping(value = "/api/{level:cim|pim|psm}/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ModelService.ImportResult importModel(@PathVariable("level") String level,
            @RequestParam(value = "format", defaultValue = "json") String format,
            @RequestParam("file") MultipartFile file) throws Exception {
        return models.importModel(ModelLevel.fromApiName(level), file.getOriginalFilename(),
                file.getBytes(), format);
    }

    public record SaveModelRequest(@NotBlank String name, JsonNode model, String projectId) {

    }

    public record ExportRequest(String name, JsonNode model, String format) {

    }
}
