package io.mehdieidi.modriss.backend.api;

import io.mehdieidi.modriss.platform.artifact.application.ArtifactService;
import io.mehdieidi.modriss.platform.artifact.domain.ArtifactRecord;
import io.mehdieidi.modriss.platform.psm.PsmBranch;
import io.mehdieidi.modriss.platform.psm.PsmExtensionService;
import io.mehdieidi.modriss.platform.psm.PsmPlatformPackage;
import io.mehdieidi.modriss.platform.psm.PsmWorkerResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Project-scoped provider packages and independent PSM branches. AWS retains its native APIs. */
@RestController
@RequestMapping("/api/projects/{projectId}/psm")
public class PsmExtensionController {
  private final PsmExtensionService extensions;
  private final ArtifactService artifacts;
  private final AuthSupport auth;

  public PsmExtensionController(
      PsmExtensionService extensions, ArtifactService artifacts, AuthSupport auth) {
    this.extensions = extensions;
    this.artifacts = artifacts;
    this.auth = auth;
  }

  @GetMapping("/packages")
  List<PackageSummary> packages(
      @RequestHeader("X-Auth-Token") String token, @PathVariable String projectId) {
    return extensions.packages(auth.user(token), projectId).stream()
        .map(PackageSummary::from)
        .toList();
  }

  @PostMapping(value = "/packages", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  ResponseEntity<PackageSummary> upload(
      @RequestHeader("X-Auth-Token") String token,
      @PathVariable String projectId,
      @RequestParam String name,
      @RequestParam(defaultValue = "") String description,
      @RequestParam(defaultValue = "") String transformationEntry,
      @RequestParam(defaultValue = "") String generationEntry,
      @RequestPart MultipartFile ecore,
      @RequestPart(required = false) MultipartFile transformation,
      @RequestPart(required = false) MultipartFile generation,
      @RequestPart(required = false) MultipartFile validation)
      throws Exception {
    // Authorize before reading any uploaded payload.
    var user = auth.user(token);
    extensions.packages(user, projectId);
    var platform =
        extensions.upload(
            user,
            projectId,
            name,
            description,
            ecore.getBytes(),
            transformation == null || transformation.isEmpty() ? null : transformation.getBytes(),
            generation == null || generation.isEmpty() ? null : generation.getBytes(),
            validation == null || validation.isEmpty() ? null : validation.getBytes(),
            transformationEntry,
            generationEntry);
    return ResponseEntity.status(201).body(PackageSummary.from(platform));
  }

  @GetMapping("/packages/{id}/{asset}")
  ResponseEntity<byte[]> downloadPackage(
      @RequestHeader("X-Auth-Token") String token,
      @PathVariable String projectId,
      @PathVariable String id,
      @PathVariable String asset) {
    var platform = extensions.platform(auth.user(token), projectId, id);
    byte[] bytes =
        switch (asset) {
          case "platform.ecore" -> platform.ecore();
          case "transformation.zip" -> platform.transformationZip();
          case "generation.zip" -> platform.generationZip();
          case "validation.zip" -> platform.validationZip();
          default -> null;
        };
    if (bytes == null)
      throw new io.mehdieidi.modriss.platform.kernel.PlatformException(
          404, "Package asset not found.");
    return ResponseEntity.ok()
        .header("Content-Disposition", "attachment; filename=\"" + asset + "\"")
        .contentType(MediaType.APPLICATION_OCTET_STREAM)
        .body(bytes);
  }

  @GetMapping("/branches")
  List<BranchSummary> branches(
      @RequestHeader("X-Auth-Token") String token, @PathVariable String projectId) {
    return extensions.branches(auth.user(token), projectId).stream()
        .map(BranchSummary::from)
        .toList();
  }

  @PostMapping("/branches")
  ResponseEntity<PsmBranch> transform(
      @RequestHeader("X-Auth-Token") String token,
      @PathVariable String projectId,
      @Valid @RequestBody CreateBranch request) {
    return ResponseEntity.status(201)
        .body(
            extensions.transform(
                auth.user(token),
                projectId,
                request.packageId(),
                request.sourcePimId(),
                request.expectedRevision(),
                request.name(),
                request.expectedTargetRevision()));
  }

  @GetMapping("/packages/{id}/definition")
  PackageDefinition definition(
      @RequestHeader("X-Auth-Token") String token,
      @PathVariable String projectId,
      @PathVariable String id) {
    var platform = extensions.platform(auth.user(token), projectId, id);
    return new PackageDefinition(platform.schema(), platform.visualSyntax());
  }

  @PutMapping("/packages/{id}/notation")
  PackageDefinition notation(
      @RequestHeader("X-Auth-Token") String token,
      @PathVariable String projectId,
      @PathVariable String id,
      @RequestBody tools.jackson.databind.JsonNode syntax) {
    var platform = extensions.updateVisualSyntax(auth.user(token), projectId, id, syntax);
    return new PackageDefinition(platform.schema(), platform.visualSyntax());
  }

  @GetMapping("/branches/{id}/editor")
  EditorModel editor(
      @RequestHeader("X-Auth-Token") String token,
      @PathVariable String projectId,
      @PathVariable String id) {
    var user = auth.user(token);
    var model = extensions.branch(user, projectId, id);
    return new EditorModel(
        model.id(),
        model.projectId(),
        model.name(),
        model.revision(),
        extensions.editorModel(user, projectId, id));
  }

  @PostMapping("/packages/{packageId}/model")
  EditorModel createEditorModel(
      @RequestHeader("X-Auth-Token") String token,
      @PathVariable String projectId,
      @PathVariable String packageId,
      @Valid @RequestBody EditorSave request) {
    return editorSaved(
        extensions.saveEditorModel(
            auth.user(token), projectId, packageId, null, 0, request.name(), request.model()));
  }

  @PutMapping("/packages/{packageId}/model/{id}")
  EditorModel saveEditorModel(
      @RequestHeader("X-Auth-Token") String token,
      @PathVariable String projectId,
      @PathVariable String packageId,
      @PathVariable String id,
      @Valid @RequestBody EditorSave request) {
    return editorSaved(
        extensions.saveEditorModel(
            auth.user(token),
            projectId,
            packageId,
            id,
            request.expectedRevision(),
            request.name(),
            request.model()));
  }

  private static EditorModel editorSaved(PsmBranch model) {
    return new EditorModel(
        model.id(), model.projectId(), model.name(), model.revision(), model.modelJson());
  }

  @PostMapping("/packages/{packageId}/import")
  tools.jackson.databind.JsonNode importEditorModel(
      @RequestHeader("X-Auth-Token") String token,
      @PathVariable String projectId,
      @PathVariable String packageId,
      @RequestBody ImportXmi request) {
    return extensions.importEditorModel(auth.user(token), projectId, packageId, request.xmi());
  }

  @GetMapping("/branches/{id}")
  PsmBranch branch(
      @RequestHeader("X-Auth-Token") String token,
      @PathVariable String projectId,
      @PathVariable String id) {
    return extensions.branch(auth.user(token), projectId, id);
  }

  @PutMapping("/branches/{id}")
  PsmBranch update(
      @RequestHeader("X-Auth-Token") String token,
      @PathVariable String projectId,
      @PathVariable String id,
      @Valid @RequestBody UpdateBranch request) {
    return extensions.update(
        auth.user(token), projectId, id, request.expectedRevision(), request.name(), request.xmi());
  }

  @DeleteMapping("/branches/{id}")
  ResponseEntity<Void> delete(
      @RequestHeader("X-Auth-Token") String token,
      @PathVariable String projectId,
      @PathVariable String id,
      @RequestParam long expectedRevision) {
    extensions.deleteBranch(auth.user(token), projectId, id, expectedRevision);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/branches/{id}/schema")
  PsmWorkerResult inspect(
      @RequestHeader("X-Auth-Token") String token,
      @PathVariable String projectId,
      @PathVariable String id) {
    return extensions.schema(auth.user(token), projectId, id);
  }

  @PostMapping("/branches/{id}/validate")
  PsmWorkerResult validate(
      @RequestHeader("X-Auth-Token") String token,
      @PathVariable String projectId,
      @PathVariable String id) {
    return extensions.inspectBranch(auth.user(token), projectId, id, true);
  }

  @PostMapping("/branches/{id}/artifacts")
  ArtifactRecord generate(
      @RequestHeader("X-Auth-Token") String token,
      @PathVariable String projectId,
      @PathVariable String id,
      @Valid @RequestBody Revision request) {
    return extensions.generate(auth.user(token), projectId, id, request.expectedRevision());
  }

  @GetMapping("/branches/{id}/artifacts")
  List<BranchArtifactSummary> artifacts(
      @RequestHeader("X-Auth-Token") String token,
      @PathVariable String projectId,
      @PathVariable String id) {
    var user = auth.user(token);
    extensions.branch(user, projectId, id);
    return artifacts.listSummaries(user, projectId).stream()
        .filter(artifact -> id.equals(artifact.modelJson().path("psmBranchId").asText()))
        .map(
            artifact ->
                new BranchArtifactSummary(
                    artifact.id(),
                    artifact.name(),
                    artifact.modelJson().path("sourceModelRevision").asLong(),
                    artifact.files().keySet().stream().toList(),
                    artifact.createdAt()))
        .toList();
  }

  @GetMapping(value = "/branches/{id}/model.xmi", produces = MediaType.APPLICATION_XML_VALUE)
  ResponseEntity<byte[]> export(
      @RequestHeader("X-Auth-Token") String token,
      @PathVariable String projectId,
      @PathVariable String id) {
    var branch = extensions.branch(auth.user(token), projectId, id);
    return ResponseEntity.ok()
        .header("Content-Disposition", "attachment; filename=\"psm-model.xmi\"")
        .body(branch.xmi().getBytes(StandardCharsets.UTF_8));
  }

  public record CreateBranch(
      @NotBlank String packageId,
      @NotBlank String sourcePimId,
      @Positive long expectedRevision,
      @NotBlank String name,
      Long expectedTargetRevision) {}

  public record EditorSave(
      @NotBlank String name,
      long expectedRevision,
      @jakarta.validation.constraints.NotNull tools.jackson.databind.JsonNode model) {}

  public record EditorModel(
      String id,
      String projectId,
      String name,
      long revision,
      tools.jackson.databind.JsonNode modelJson) {}

  public record ImportXmi(@NotBlank String xmi) {}

  public record PackageDefinition(
      tools.jackson.databind.JsonNode schema, tools.jackson.databind.JsonNode visualSyntax) {}

  public record UpdateBranch(
      @Positive long expectedRevision, @NotBlank String name, @NotBlank String xmi) {}

  public record Revision(@Positive long expectedRevision) {}

  public record BranchArtifactSummary(
      String id,
      String name,
      long sourceModelRevision,
      List<String> filePaths,
      Instant createdAt) {}

  public record PackageSummary(
      String id,
      String name,
      String description,
      String uploadedBy,
      String transformationEntry,
      String generationEntry,
      boolean hasValidation,
      Instant createdAt,
      boolean hasTransformation,
      boolean hasGeneration) {
    static PackageSummary from(PsmPlatformPackage p) {
      return new PackageSummary(
          p.id(),
          p.name(),
          p.description(),
          p.uploadedBy(),
          p.transformationEntry(),
          p.generationEntry(),
          p.validationZip() != null,
          p.createdAt(),
          p.transformationZip() != null,
          p.generationZip() != null);
    }
  }

  public record BranchSummary(
      String id,
      String packageId,
      String name,
      String sourcePimId,
      long sourcePimRevision,
      long revision,
      Instant updatedAt) {
    static BranchSummary from(PsmBranch b) {
      return new BranchSummary(
          b.id(),
          b.packageId(),
          b.name(),
          b.sourcePimId(),
          b.sourcePimRevision(),
          b.revision(),
          b.updatedAt());
    }
  }
}
