package io.mehdieidi.modriss.platform.psm;

import io.mehdieidi.modriss.platform.artifact.application.ArtifactService;
import io.mehdieidi.modriss.platform.artifact.domain.ArtifactRecord;
import io.mehdieidi.modriss.platform.identity.domain.UserRecord;
import io.mehdieidi.modriss.platform.kernel.ModelLevel;
import io.mehdieidi.modriss.platform.kernel.PlatformException;
import io.mehdieidi.modriss.platform.model.application.ModelLockService;
import io.mehdieidi.modriss.platform.model.application.ModelService;
import io.mehdieidi.modriss.platform.modeling.metamodel.MetamodelResolver;
import io.mehdieidi.modriss.platform.project.application.ProjectService;
import io.mehdieidi.modriss.platform.storage.api.PlatformStore;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Semaphore;

/**
 * Owns immutable extension packages and parallel PSM branches within the project's access scope.
 */
public final class PsmExtensionService {
  private final PlatformStore store;
  private final ProjectService projects;
  private final ModelService models;
  private final ArtifactService artifacts;
  private final MetamodelResolver metamodels;
  private final PsmExecutionRunner runner;
  private final ModelLockService locks;
  private final Semaphore slots = new Semaphore(2);

  public PsmExtensionService(
      PlatformStore store,
      ProjectService projects,
      ModelService models,
      ArtifactService artifacts,
      MetamodelResolver metamodels,
      PsmExecutionRunner runner) {
    this.store = store;
    this.projects = projects;
    this.models = models;
    this.artifacts = artifacts;
    this.metamodels = metamodels;
    this.runner = runner;
    this.locks = models.modelLocks();
  }

  public List<PsmPlatformPackage> packages(UserRecord user, String projectId) {
    projects.get(user, projectId);
    return store.list(directory(projectId, "packages"), PsmPlatformPackage.class);
  }

  public PsmPlatformPackage platform(UserRecord user, String projectId, String id) {
    projects.get(user, projectId);
    PsmPlatformPackage platform =
        store.require(
            path(projectId, "packages", id),
            PsmPlatformPackage.class,
            "Platform package not found.");
    if (!projectId.equals(platform.projectId()))
      throw new PlatformException(404, "Platform package not found.");
    return platform;
  }

  public PsmPlatformPackage upload(
      UserRecord user,
      String projectId,
      String name,
      String description,
      byte[] ecore,
      byte[] etl,
      byte[] generation,
      byte[] evl,
      String etlEntry,
      String generationEntry) {
    requireEditor(user, projectId);
    if (ecore == null || ecore.length == 0 || ecore.length > 5 * 1024 * 1024)
      throw new PlatformException(400, "Supply an Ecore file of at most 5 MB.");
    if (etl != null)
      requireEntry(PsmPackageArchive.read(etl, Set.of("etl", "eol")), etlEntry, ".etl");
    if (generation != null) {
      var generationFiles = PsmPackageArchive.read(generation, Set.of("egx", "egl", "eol"));
      requireEntry(generationFiles, generationEntry, ".egx");
      if (generationFiles.keySet().stream().noneMatch(file -> file.endsWith(".egl")))
        throw new PlatformException(400, "Generation ZIP must include EGL templates.");
    }
    if (evl != null
        && PsmPackageArchive.read(evl, Set.of("evl", "eol")).keySet().stream()
            .noneMatch(file -> file.endsWith(".evl")))
      throw new PlatformException(400, "Validation ZIP must include an EVL module.");
    PsmPlatformPackage platform =
        new PsmPlatformPackage(
            UUID.randomUUID().toString(),
            projectId,
            user.id(),
            name(name),
            description == null ? "" : description.strip(),
            ecore,
            etl,
            generation,
            evl,
            etlEntry,
            generationEntry,
            null,
            Instant.now());
    var inspection = run(new PsmWorkerRequest("inspect", platform, null, null));
    requireValid(inspection);
    if (inspection.schema() == null
        || java.util.stream.StreamSupport.stream(inspection.schema().spliterator(), false)
            .noneMatch(type -> !type.path("abstract").asBoolean()))
      throw new PlatformException(
          422, "The metamodel needs at least one concrete EClass for modeling.");
    platform =
        new PsmPlatformPackage(
            platform.id(),
            platform.projectId(),
            platform.uploadedBy(),
            platform.name(),
            platform.description(),
            platform.ecore(),
            platform.transformationZip(),
            platform.generationZip(),
            platform.validationZip(),
            platform.transformationEntry(),
            platform.generationEntry(),
            inspection.schema(),
            platform.createdAt());
    store.write(path(projectId, "packages", platform.id()), platform);
    return platform;
  }

  public List<PsmBranch> branches(UserRecord user, String projectId) {
    projects.get(user, projectId);
    return store.list(directory(projectId, "branches"), PsmBranch.class);
  }

  public PsmBranch branch(UserRecord user, String projectId, String id) {
    projects.get(user, projectId);
    PsmBranch branch =
        store.require(path(projectId, "branches", id), PsmBranch.class, "PSM branch not found.");
    if (!projectId.equals(branch.projectId()))
      throw new PlatformException(404, "PSM branch not found.");
    return branch;
  }

  public PsmBranch transform(
      UserRecord user,
      String projectId,
      String packageId,
      String pimId,
      long expectedRevision,
      String name) {
    return transform(user, projectId, packageId, pimId, expectedRevision, name, null);
  }

  public PsmBranch transform(
      UserRecord user,
      String projectId,
      String packageId,
      String pimId,
      long expectedRevision,
      String name,
      Long expectedTargetRevision) {
    requireEditor(user, projectId);
    PsmPlatformPackage platform = platform(user, projectId, packageId);
    if (platform.transformationZip() == null)
      throw new PlatformException(
          400,
          "This provider has no PIM to PSM transformation package. Create or import a model in its"
              + " editor.");
    return locks.withModelLock(
        "psm-platform:" + projectId + ":" + packageId,
        Duration.ofSeconds(30),
        () -> {
          var existing =
              branches(user, projectId).stream()
                  .filter(b -> b.packageId().equals(packageId))
                  .toList();
          if (existing.size() > 1)
            throw new PlatformException(
                409,
                "This legacy package has multiple models. Export or remove the extra models before"
                    + " regenerating.");
          PsmBranch previous = existing.isEmpty() ? null : existing.get(0);
          if (previous != null
              && (expectedTargetRevision == null
                  || previous.revision() != expectedTargetRevision)) {
            throw new PlatformException(
                409,
                "This platform already has a model. Confirm regeneration with its current"
                    + " revision.");
          }
          if (previous == null && expectedTargetRevision != null && expectedTargetRevision != 0)
            throw new PlatformException(409, "The target model was removed. Reload the platform.");
          return locks.withModelLock(
              previous == null ? "psm-branch:" + packageId : "psm-branch:" + previous.id(),
              Duration.ofSeconds(30),
              () -> {
                // Recheck after acquiring the same lock used by save and generation.
                if (previous != null)
                  requireRevision(branch(user, projectId, previous.id()), expectedTargetRevision);
                return locks.withModelLock(
                    pimId,
                    Duration.ofSeconds(30),
                    () -> {
                      var source = models.get(user, ModelLevel.PIM, projectId, pimId);
                      if (source.revision() != expectedRevision)
                        throw new PlatformException(
                            409, "PIM changed. Reload it before generating a new PSM branch.");
                      byte[] xmi = models.exportModel(user, ModelLevel.PIM, pimId, "xmi");
                      try {
                        PsmWorkerResult result =
                            run(
                                new PsmWorkerRequest(
                                    "transform",
                                    platform,
                                    xmi,
                                    Files.readAllBytes(metamodels.resolve(ModelLevel.PIM).file())));
                        requireValid(result);
                        Instant now = Instant.now();
                        // A deterministic id also prevents concurrent first generations on separate
                        // backend instances from creating multiple records for a new package.
                        PsmBranch branch =
                            new PsmBranch(
                                previous == null ? packageId : previous.id(),
                                projectId,
                                user.id(),
                                packageId,
                                name(name),
                                pimId,
                                source.revision(),
                                HexFormat.of()
                                    .formatHex(MessageDigest.getInstance("SHA-256").digest(xmi)),
                                result.xmi(),
                                previous == null ? 1 : previous.revision() + 1,
                                previous == null ? now : previous.createdAt(),
                                now,
                                result.modelJson());
                        store.write(path(projectId, "branches", branch.id()), branch);
                        return branch;
                      } catch (PlatformException ex) {
                        throw ex;
                      } catch (Exception ex) {
                        throw new PlatformException(500, "Could not prepare PIM input.");
                      }
                    });
              });
        });
  }

  public PsmWorkerResult inspectBranch(
      UserRecord user, String projectId, String id, boolean semantic) {
    PsmBranch branch = branch(user, projectId, id);
    return run(
        new PsmWorkerRequest(
            semantic ? "validate" : "read",
            platform(user, projectId, branch.packageId()),
            branch.xmi().getBytes(StandardCharsets.UTF_8),
            null));
  }

  public PsmWorkerResult schema(UserRecord user, String projectId, String id) {
    PsmBranch branch = branch(user, projectId, id);
    PsmPlatformPackage platform = platform(user, projectId, branch.packageId());
    if (platform.schema() != null)
      return new PsmWorkerResult(true, null, platform.schema(), List.of(), java.util.Map.of());
    return run(new PsmWorkerRequest("inspect", platform, null, null));
  }

  public PsmBranch update(
      UserRecord user, String projectId, String id, long revision, String name, String xmi) {
    requireEditor(user, projectId);
    return locks.withModelLock(
        "psm-branch:" + id,
        Duration.ofSeconds(30),
        () -> {
          PsmBranch branch = branch(user, projectId, id);
          requireRevision(branch, revision);
          PsmWorkerResult result =
              run(
                  new PsmWorkerRequest(
                      "read",
                      platform(user, projectId, branch.packageId()),
                      xmi == null ? null : xmi.getBytes(StandardCharsets.UTF_8),
                      null));
          requireValid(
              result); // Structural EMF conformance only. EVL is explicitly initiated separately.
          PsmBranch updated =
              new PsmBranch(
                  branch.id(),
                  branch.projectId(),
                  branch.createdBy(),
                  branch.packageId(),
                  name(name),
                  branch.sourcePimId(),
                  branch.sourcePimRevision(),
                  branch.sourcePimHash(),
                  result.xmi(),
                  branch.revision() + 1,
                  branch.createdAt(),
                  Instant.now(),
                  result.modelJson());
          store.write(path(projectId, "branches", id), updated);
          return updated;
        });
  }

  public tools.jackson.databind.JsonNode editorModel(UserRecord user, String projectId, String id) {
    var model = branch(user, projectId, id);
    if (model.modelJson() != null) return model.modelJson();
    var result = inspectBranch(user, projectId, id, false);
    requireValid(result);
    return result.modelJson();
  }

  public PsmBranch saveEditorModel(
      UserRecord user,
      String projectId,
      String packageId,
      String id,
      long revision,
      String name,
      tools.jackson.databind.JsonNode model) {
    requireEditor(user, projectId);
    var platform = platform(user, projectId, packageId);
    return locks.withModelLock(
        "psm-platform:" + projectId + ":" + packageId,
        Duration.ofSeconds(30),
        () -> {
          var existing =
              branches(user, projectId).stream()
                  .filter(b -> b.packageId().equals(packageId))
                  .toList();
          var previous = id == null ? null : branch(user, projectId, id);
          if (previous != null && !previous.packageId().equals(packageId))
            throw new PlatformException(404, "Model not found in this platform.");
          if (previous == null && !existing.isEmpty())
            throw new PlatformException(
                409, "This platform already has a model. Open the existing model.");
          String modelId = previous == null ? packageId : previous.id();
          return locks.withModelLock(
              "psm-branch:" + modelId,
              Duration.ofSeconds(30),
              () -> {
                if (previous != null) requireRevision(branch(user, projectId, modelId), revision);
                var result = run(new PsmWorkerRequest("write", platform, null, null, model));
                requireValid(
                    result); // Structural conformance; semantic EVL remains an explicit action.
                var document =
                    (tools.jackson.databind.node.ObjectNode) result.modelJson().deepCopy();
                for (String key : List.of("graph", "views", "fragments", "activeViewId")) {
                  if (model.has(key)) document.set(key, model.get(key).deepCopy());
                }
                Instant now = Instant.now();
                var saved =
                    new PsmBranch(
                        modelId,
                        projectId,
                        previous == null ? user.id() : previous.createdBy(),
                        packageId,
                        name(name),
                        previous == null ? null : previous.sourcePimId(),
                        previous == null ? 0 : previous.sourcePimRevision(),
                        previous == null ? null : previous.sourcePimHash(),
                        result.xmi(),
                        previous == null ? 1 : previous.revision() + 1,
                        previous == null ? now : previous.createdAt(),
                        now,
                        document);
                store.write(path(projectId, "branches", modelId), saved);
                return saved;
              });
        });
  }

  public PsmPlatformPackage updateVisualSyntax(
      UserRecord user, String projectId, String id, tools.jackson.databind.JsonNode syntax) {
    requireEditor(user, projectId);
    if (syntax == null || !syntax.isObject() || syntax.toString().length() > 1024 * 1024)
      throw new PlatformException(400, "Supply a visual notation document under 1 MB.");
    return locks.withModelLock(
        "psm-notation:" + id,
        Duration.ofSeconds(30),
        () -> {
          var p = platform(user, projectId, id);
          PsmVisualSyntax.validate(syntax, p.schema());
          for (var model : branches(user, projectId)) {
            if (model.packageId().equals(id)
                && !syntax
                    .path("rootType")
                    .asText()
                    .equals(editorModel(user, projectId, model.id()).path("eClass").asText())) {
              throw new PlatformException(
                  409,
                  "The existing model uses another root EClass. Keep its root type when editing"
                      + " notation.");
            }
          }
          var updated =
              new PsmPlatformPackage(
                  p.id(),
                  p.projectId(),
                  p.uploadedBy(),
                  p.name(),
                  p.description(),
                  p.ecore(),
                  p.transformationZip(),
                  p.generationZip(),
                  p.validationZip(),
                  p.transformationEntry(),
                  p.generationEntry(),
                  p.schema(),
                  p.createdAt(),
                  syntax.deepCopy());
          store.write(path(projectId, "packages", id), updated);
          return updated;
        });
  }

  public tools.jackson.databind.JsonNode importEditorModel(
      UserRecord user, String projectId, String packageId, String xmi) {
    requireEditor(user, projectId);
    var result =
        run(
            new PsmWorkerRequest(
                "read",
                platform(user, projectId, packageId),
                xmi.getBytes(StandardCharsets.UTF_8),
                null));
    requireValid(result);
    return result.modelJson();
  }

  public ArtifactRecord generate(UserRecord user, String projectId, String id, long revision) {
    requireEditor(user, projectId);
    return locks.withModelLock(
        "psm-branch:" + id,
        Duration.ofSeconds(30),
        () -> {
          PsmBranch branch = branch(user, projectId, id);
          if (platform(user, projectId, branch.packageId()).generationZip() == null)
            throw new PlatformException(400, "This provider has no artifact generation package.");
          requireRevision(branch, revision);
          PsmWorkerResult result =
              run(
                  new PsmWorkerRequest(
                      "generate",
                      platform(user, projectId, branch.packageId()),
                      branch.xmi().getBytes(StandardCharsets.UTF_8),
                      null));
          requireValid(result);
          var metadata =
              store
                  .objectMapper()
                  .createObjectNode()
                  .put("psmBranchId", id)
                  .put("psmPackageId", branch.packageId())
                  .put("sourceModelRevision", branch.revision())
                  .put("sourcePimId", branch.sourcePimId())
                  .put("sourcePimRevision", branch.sourcePimRevision());
          return artifacts.create(
              user, projectId, branch.name() + "-artifacts", metadata, result.files());
        });
  }

  public void deleteBranch(UserRecord user, String projectId, String id, long revision) {
    requireEditor(user, projectId);
    locks.withModelLock(
        "psm-branch:" + id,
        Duration.ofSeconds(30),
        () -> {
          requireRevision(branch(user, projectId, id), revision);
          store.deleteIfExists(path(projectId, "branches", id));
          return true;
        });
  }

  private PsmWorkerResult run(PsmWorkerRequest request) {
    if (!slots.tryAcquire())
      throw new PlatformException(
          429, "Two platform package operations are running. Try again shortly.");
    try {
      return runner.run(request);
    } finally {
      slots.release();
    }
  }

  private void requireEditor(UserRecord user, String projectId) {
    projects.requireEditor(projects.get(user, projectId), user.id());
  }

  private static void requireRevision(PsmBranch branch, long revision) {
    if (branch.revision() != revision)
      throw new PlatformException(409, "This PSM branch changed. Reload it before continuing.");
  }

  private static void requireValid(PsmWorkerResult result) {
    if (!result.valid()) throw new PlatformException(422, String.join("\n", result.issues()));
  }

  private static String name(String name) {
    if (name == null || name.isBlank() || name.strip().length() > 120)
      throw new PlatformException(400, "Name must contain 1–120 characters.");
    return name.strip();
  }

  private static void requireEntry(
      java.util.Map<String, byte[]> files, String entry, String extension) {
    PsmPackageArchive.requirePath(entry);
    if (!entry.endsWith(extension) || !files.containsKey(entry))
      throw new PlatformException(
          400, "Entry script must name a " + extension + " file inside the ZIP: " + entry);
  }

  public static Path directory(String projectId, String kind) {
    safeId(projectId);
    return Path.of("projects", projectId, "psm-extensions", kind);
  }

  private static Path path(String projectId, String kind, String id) {
    safeId(id);
    return directory(projectId, kind).resolve(id + ".json");
  }

  private static void safeId(String id) {
    if (id == null || !id.matches("[A-Za-z0-9_-]{1,100}"))
      throw new PlatformException(400, "Invalid identifier.");
  }
}
