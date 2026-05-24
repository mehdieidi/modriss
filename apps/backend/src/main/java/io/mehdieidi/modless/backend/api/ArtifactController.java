package io.mehdieidi.modless.backend.api;

import io.mehdieidi.modless.platform.core.model.ArtifactRecord;
import io.mehdieidi.modless.platform.core.service.ArtifactService;
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

@RestController
@RequestMapping("/api/artifact")
public class ArtifactController {

    private final ArtifactService artifacts;
    private final AuthSupport auth;

    public ArtifactController(ArtifactService artifacts, AuthSupport auth) {
        this.artifacts = artifacts;
        this.auth = auth;
    }

    @GetMapping
    List<ArtifactRecord> list(@RequestHeader("X-Auth-Token") String token,
            @RequestParam("projectId") String projectId) {
        return artifacts.list(auth.user(token), projectId);
    }

    @GetMapping("/{id}")
    ArtifactRecord get(@RequestHeader("X-Auth-Token") String token, @PathVariable("id") String id) {
        return artifacts.get(auth.user(token), id);
    }

    @GetMapping(value = "/{id}/file", produces = MediaType.TEXT_PLAIN_VALUE)
    String file(@RequestHeader("X-Auth-Token") String token, @PathVariable("id") String id,
            @RequestParam("path") String path) {
        return artifacts.readFile(auth.user(token), id, path);
    }

    @PutMapping("/{id}/files")
    ArtifactRecord saveFile(@RequestHeader("X-Auth-Token") String token,
            @PathVariable("id") String id,
            @Valid @RequestBody SaveFileRequest request) {
        return artifacts.updateFile(auth.user(token), id, request.path(), request.content());
    }

    @GetMapping("/{id}/download")
    ResponseEntity<byte[]> download(@RequestHeader("X-Auth-Token") String token,
            @PathVariable("id") String id) {
        ArtifactRecord artifact = artifacts.get(auth.user(token), id);
        byte[] bytes = artifacts.zip(auth.user(token), id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/zip"))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(artifact.name() + ".zip", StandardCharsets.UTF_8).build()
                        .toString())
                .body(bytes);
    }

    public record SaveFileRequest(@NotBlank String path, String content) {

    }
}
