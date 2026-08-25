package io.mehdieidi.varka.platform.model.application;

import io.mehdieidi.varka.platform.identity.domain.UserRecord;
import io.mehdieidi.varka.platform.kernel.ModelLevel;
import io.mehdieidi.varka.platform.kernel.PlatformException;
import io.mehdieidi.varka.platform.model.domain.ModelIndexRecord;
import io.mehdieidi.varka.platform.model.domain.ModelRecord;
import io.mehdieidi.varka.platform.model.domain.StagedImportRecord;
import io.mehdieidi.varka.platform.modeling.metamodel.MetamodelResolver;
import io.mehdieidi.varka.platform.modeling.runtime.MdeRuntimeOptions;
import io.mehdieidi.varka.platform.modeling.xmi.XmiModelImportService;
import io.mehdieidi.varka.platform.storage.api.PlatformStore;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Model import/export, JSON normalization, semantic reference hydration, source XMI sidecars, and
 * staged import handling.
 */
final class ModelImportExportService {

  /** File repository used for model JSON, indexes, and XMI sidecars. */
  private final PlatformStore store;

  /** Runtime limits and asset roots. */
  private final MdeRuntimeOptions runtimeOptions;

  /** Metamodel resolver used for import/export and metadata repair. */
  private final MetamodelResolver metamodelResolver;

  /** XMI bridge between JSON and EMF resources. */
  private final XmiModelImportService xmiImportService;

  /** Validation service used for JSON import validation. */
  private ModelValidationService validationService;

  /**
   * Creates an import/export service.
   *
   * @param store backing JSON repository
   * @param runtimeOptions runtime limits and asset roots
   * @param metamodelResolver metamodel resolver
   * @param xmiImportService XMI import/export bridge
   */
  ModelImportExportService(
      PlatformStore store,
      MdeRuntimeOptions runtimeOptions,
      MetamodelResolver metamodelResolver,
      XmiModelImportService xmiImportService) {
    this.store = store;
    this.runtimeOptions = runtimeOptions;
    this.metamodelResolver = metamodelResolver;
    this.xmiImportService = xmiImportService;
  }

  /**
   * Wires the validation service after both collaborators are constructed.
   *
   * @param validationService model validation service
   */
  void setValidationService(ModelValidationService validationService) {
    this.validationService = validationService;
  }

  /**
   * Imports a model payload without project-specific staged side effects.
   *
   * @param level model level
   * @param fileName uploaded file name
   * @param bytes uploaded bytes
   * @param format import format, {@code json} or {@code xmi}
   * @return import result
   */
  ModelService.ImportResult importModel(
      ModelLevel level, String fileName, byte[] bytes, String format) {
    return importModel(null, null, level, fileName, bytes, format);
  }

  /**
   * Imports a model payload and, for XMI, attaches source bytes to the resulting JSON payload for
   * later persistence.
   *
   * @param user requesting user
   * @param projectId project identifier
   * @param level model level
   * @param fileName uploaded file name
   * @param bytes uploaded bytes
   * @param format import format, {@code json} or {@code xmi}
   * @return import result
   */
  ModelService.ImportResult importModel(
      UserRecord user,
      String projectId,
      ModelLevel level,
      String fileName,
      byte[] bytes,
      String format) {
    if (bytes != null && bytes.length > runtimeOptions.maxModelUploadBytes()) {
      throw new PlatformException(413, "Model file is too large.");
    }
    try {
      String normalizedFormat = String.valueOf(format).toLowerCase();
      JsonNode model =
          switch (normalizedFormat) {
            case "json" -> store.objectMapper().readTree(new String(bytes, StandardCharsets.UTF_8));
            case "xmi" -> xmiImportService.importModel(level, bytes);
            default ->
                throw new PlatformException(400, "Unsupported import format. Use JSON or XMI.");
          };
      String name =
          fileName == null || fileName.isBlank()
              ? level.apiName() + "-model"
              : fileName.replaceFirst("\\.[^.]+$", "");
      JsonNode normalizedModel = normalizeModel(name, level, model);
      if ("xmi".equals(normalizedFormat) && normalizedModel instanceof ObjectNode objectModel) {
        objectModel.put("_sourceXmiBase64", Base64.getEncoder().encodeToString(bytes));
      }
      ModelService.ValidationResult validation =
          "xmi".equals(normalizedFormat)
              ? new ModelService.ValidationResult(true, List.of())
              : validationService.validate(level, normalizedModel);
      return new ModelService.ImportResult(name, normalizedModel, validation.issues());
    } catch (PlatformException ex) {
      throw ex;
    } catch (Exception ex) {
      String normalizedFormat = String.valueOf(format).toLowerCase();
      throw new PlatformException(
          400,
          "Uploaded model is not valid " + ("xmi".equals(normalizedFormat) ? "XMI." : "JSON."));
    }
  }

  /**
   * Imports a bounded model stream.
   *
   * @param user requesting user
   * @param projectId project identifier
   * @param level model level
   * @param fileName uploaded file name
   * @param input uploaded stream
   * @param sizeBytes reported upload size
   * @param format import format, {@code json} or {@code xmi}
   * @return import result
   */
  ModelService.ImportResult importModelFromStream(
      UserRecord user,
      String projectId,
      ModelLevel level,
      String fileName,
      InputStream input,
      long sizeBytes,
      String format) {
    if (sizeBytes > runtimeOptions.maxModelUploadBytes()) {
      throw new PlatformException(413, "Model file is too large.");
    }
    try {
      byte[] bytes = input.readNBytes(Math.toIntExact(runtimeOptions.maxModelUploadBytes() + 1));
      if (bytes.length > runtimeOptions.maxModelUploadBytes()) {
        throw new PlatformException(413, "Model file is too large.");
      }
      return importModel(user, projectId, level, fileName, bytes, format);
    } catch (PlatformException ex) {
      throw ex;
    } catch (ArithmeticException ex) {
      throw new PlatformException(500, "Configured upload limit is too large.");
    } catch (Exception ex) {
      throw new PlatformException(400, "Uploaded model could not be read.");
    }
  }

  /**
   * Exports model JSON to JSON or XMI bytes.
   *
   * @param level model level
   * @param modelJson model JSON
   * @param format export format, {@code json} or {@code xmi}
   * @return exported bytes
   */
  byte[] exportModel(ModelLevel level, JsonNode modelJson, String format) {
    try {
      return switch (String.valueOf(format == null ? "json" : format).toLowerCase()) {
        case "json" ->
            store.objectMapper().writerWithDefaultPrettyPrinter().writeValueAsBytes(modelJson);
        case "xmi" -> xmiImportService.exportModel(level, hydrateSemanticReferences(modelJson));
        default -> throw new PlatformException(400, "Unsupported export format. Use JSON or XMI.");
      };
    } catch (PlatformException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new PlatformException(500, "Could not export model.");
    }
  }

  /**
   * Exports model JSON using CIM as the default level.
   *
   * @param modelJson model JSON
   * @param format export format
   * @return exported bytes
   */
  byte[] exportModel(JsonNode modelJson, String format) {
    return exportModel(ModelLevel.CIM, modelJson, format);
  }

  /**
   * Ensures required model-level fields exist in model JSON.
   *
   * @param name fallback model name
   * @param level model level
   * @param modelJson raw model JSON
   * @return normalized model JSON
   */
  JsonNode normalizeModel(String name, ModelLevel level, JsonNode modelJson) {
    ObjectNode copy =
        modelJson == null || !modelJson.isObject()
            ? store.objectMapper().createObjectNode()
            : ((ObjectNode) modelJson.deepCopy());
    if (!copy.hasNonNull("name")) {
      copy.put("name", requireName(name, level));
    }
    if (!copy.hasNonNull("modelLevel")) {
      copy.put("modelLevel", level.name());
    }
    if (!copy.has("views") || !copy.get("views").isArray()) {
      copy.set("views", store.objectMapper().createArrayNode());
    }
    return copy;
  }

  /**
   * Reads the stored source XMI sidecar for a model.
   *
   * @param model model record
   * @return optional source XMI bytes
   */
  Optional<byte[]> sourceXmi(ModelRecord model) {
    return store.readBytes(sourceXmiPath(model.projectId(), model.level(), model.id()));
  }

  /**
   * Attaches source XMI bytes to an existing model and updates its sidecar hash.
   *
   * @param model model record
   * @param bytes source XMI bytes
   */
  void attachSourceXmi(ModelRecord model, byte[] bytes) {
    if (bytes == null || bytes.length == 0) {
      return;
    }
    store.writeBytesAtomically(sourceXmiPath(model.projectId(), model.level(), model.id()), bytes);
    ModelRecord stored =
        store
            .read(modelPath(model.projectId(), model.level(), model.id()), ModelRecord.class)
            .orElse(model);
    ModelRecord updated =
        new ModelRecord(
            stored.id(),
            stored.projectId(),
            stored.level(),
            stored.name(),
            stored.modelJson(),
            textOrDefault(stored.metamodelVersion(), currentVersion(stored.level())),
            textOrDefault(stored.metamodelHash(), currentHash(stored.level())),
            Math.max(1, stored.revision()),
            hashBytes(bytes),
            textOrDefault(stored.migrationState(), "CURRENT"),
            stored.createdAt(),
            stored.updatedAt());
    store.write(modelPath(updated.projectId(), updated.level(), updated.id()), updated);
    writeModelIndex(updated);
  }

  /**
   * Produces canonical source XMI unless an existing requested source should be preserved.
   *
   * @param level model level
   * @param modelJson model JSON
   * @param requestedSourceXmi requested source XMI update
   * @return canonical source XMI update
   */
  SourceXmiUpdate canonicalSourceXmi(
      ModelLevel level, JsonNode modelJson, SourceXmiUpdate requestedSourceXmi) {
    if (requestedSourceXmi != null
        && (requestedSourceXmi.shouldPreserve()
            || (requestedSourceXmi.bytes() != null && requestedSourceXmi.bytes().length > 0))) {
      return requestedSourceXmi;
    }
    byte[] bytes =
        xmiImportService.exportModel(
            level, hydrateSemanticReferences(withLayoutAnnotations(modelJson)));
    return new SourceXmiUpdate(bytes, hashBytes(bytes), null);
  }

  /**
   * Regenerates the authoritative source XMI from the current model projection. This is used after
   * edits; unlike import canonicalization, an existing sidecar must never be preserved.
   */
  SourceXmiUpdate regenerateSourceXmi(ModelLevel level, JsonNode modelJson) {
    byte[] bytes =
        xmiImportService.exportModel(
            level, hydrateSemanticReferences(withLayoutAnnotations(modelJson)));
    return new SourceXmiUpdate(bytes, hashBytes(bytes), null);
  }

  /**
   * Checks whether diagram coordinates are present and should be preserved in source XMI
   * annotations.
   *
   * @param modelJson model JSON
   * @return {@code true} when diagram layout exists
   */
  boolean hasDiagramLayout(JsonNode modelJson) {
    JsonNode elements = modelJson == null ? null : modelJson.path("diagram").path("elements");
    if (elements == null || !elements.isArray()) {
      return false;
    }
    for (JsonNode element : elements) {
      if ((element.hasNonNull("x") || element.hasNonNull("y"))
          && !text(element, "id", "").isBlank()) {
        return true;
      }
    }
    return false;
  }

  /**
   * Resolves source XMI bytes from inline Base64, a staged token, preservation, or deletion
   * semantics.
   *
   * @param user requesting user
   * @param projectId project identifier
   * @param level model level
   * @param modelJson model JSON to consume transport fields from
   * @param preserveExistingWhenMissing whether a missing source should preserve current sidecar
   * @return source XMI update instruction
   */
  SourceXmiUpdate resolveSourceXmiUpdate(
      UserRecord user,
      String projectId,
      ModelLevel level,
      JsonNode modelJson,
      boolean preserveExistingWhenMissing) {
    byte[] inlineBytes = removeSourceXmiBase64(modelJson);
    if (inlineBytes != null && inlineBytes.length > 0) {
      return new SourceXmiUpdate(inlineBytes, hashBytes(inlineBytes), null);
    }
    String token = removeSourceXmiToken(modelJson);
    if (token == null || token.isBlank()) {
      return preserveExistingWhenMissing
          ? SourceXmiUpdate.preserveUpdate()
          : SourceXmiUpdate.deleteUpdate();
    }
    StagedImportRecord record =
        store.require(
            stagedSourceXmiRecordPath(token),
            StagedImportRecord.class,
            "Staged XMI import token was not found.");
    if (record.expiresAt() != null && record.expiresAt().isBefore(Instant.now())) {
      cleanupStagedImport(record);
      throw new PlatformException(410, "Staged XMI import token has expired.");
    }
    if (record.level() != level) {
      throw new PlatformException(400, "Staged XMI import token is for a different level.");
    }
    if (record.userId() != null
        && !record.userId().isBlank()
        && (user == null || !record.userId().equals(user.id()))) {
      throw new PlatformException(403, "Staged XMI import token belongs to another user.");
    }
    if (record.projectId() != null
        && !record.projectId().isBlank()
        && !record.projectId().equals(projectId)) {
      throw new PlatformException(403, "Staged XMI import token belongs to another project.");
    }
    byte[] bytes =
        store
            .readBytes(Path.of(record.xmiPath()))
            .orElseThrow(
                () -> new PlatformException(410, "Staged XMI import file is no longer available."));
    return new SourceXmiUpdate(bytes, hashBytes(bytes), record);
  }

  /**
   * Persists model JSON and source XMI as a best-effort transaction with rollback snapshots.
   *
   * @param previous previous model record, or {@code null} for create
   * @param model model record to persist
   * @param sourceXmi source XMI update instruction
   */
  void persistModelAndSourceXmi(
      ModelRecord previous, ModelRecord model, SourceXmiUpdate sourceXmi) {
    Path modelPath = modelPath(model.projectId(), model.level(), model.id());
    Path sourcePath = sourceXmiPath(model.projectId(), model.level(), model.id());
    byte[] previousModelBytes = readBytesIfPresent(modelPath);
    byte[] previousSourceBytes = readBytesIfPresent(sourcePath);
    boolean hadPreviousModel = previous != null && previousModelBytes != null;
    boolean hadPreviousSource = previousSourceBytes != null;
    try {
      store.write(modelPath, model);
      writeModelIndex(model);
      if (sourceXmi.shouldPreserve()) {
        return;
      }
      if (sourceXmi.shouldDelete()) {
        store.deleteIfExists(sourcePath);
      } else {
        store.writeBytesAtomically(sourcePath, sourceXmi.bytes());
        cleanupStagedImport(sourceXmi.record());
      }
    } catch (RuntimeException ex) {
      rollback(
          modelPath,
          sourcePath,
          previousModelBytes,
          previousSourceBytes,
          hadPreviousModel,
          hadPreviousSource,
          previous == null ? model.id() : null);
      throw ex;
    }
  }

  /** Removes expired staged XMI imports. */
  void cleanupExpiredImports() {
    try {
      store.list(Path.of("model-imports"), StagedImportRecord.class).stream()
          .filter(
              record -> record.expiresAt() != null && record.expiresAt().isBefore(Instant.now()))
          .forEach(this::cleanupStagedImport);
    } catch (Exception ex) {
      throw new PlatformException(500, "Could not clean up staged imports.");
    }
  }

  /**
   * Copies graph endpoint fields back into semantic relationships before XMI validation/export.
   *
   * @param modelJson model JSON
   * @return hydrated model JSON copy
   */
  JsonNode hydrateSemanticReferences(JsonNode modelJson) {
    if (modelJson == null || !modelJson.isObject()) {
      return modelJson;
    }
    Map<String, JsonNode> graphRelationships = new LinkedHashMap<>();
    JsonNode relationships = modelJson.path("graph").path("relationships");
    if (relationships.isArray()) {
      relationships.forEach(
          relationship -> {
            String id = text(relationship, "id", "");
            if (!id.isBlank()) {
              graphRelationships.put(id, relationship);
            }
          });
    }
    ObjectNode copy = (ObjectNode) modelJson.deepCopy();
    stripValidationExportOnlyFields(copy);
    if (!graphRelationships.isEmpty()) {
      hydrateSemanticReferences(copy, graphRelationships);
    }
    return copy;
  }

  /**
   * Hashes raw bytes with SHA-256.
   *
   * @param bytes bytes to hash
   * @return lowercase hexadecimal digest
   */
  String hashBytes(byte[] bytes) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(bytes == null ? new byte[0] : bytes));
    } catch (Exception ex) {
      throw new PlatformException(500, "Could not hash source XMI.");
    }
  }

  /**
   * Source XMI persistence instruction used while writing model records.
   *
   * @param bytes source XMI bytes, or {@code null} for preserve/delete operations
   * @param hash source XMI hash, {@code null} to preserve, empty to delete
   * @param record staged import record to clean up after commit
   */
  record SourceXmiUpdate(byte[] bytes, String hash, StagedImportRecord record) {

    /**
     * Creates an instruction to preserve the existing sidecar.
     *
     * @return preserve instruction
     */
    static SourceXmiUpdate preserveUpdate() {
      return new SourceXmiUpdate(null, null, null);
    }

    /**
     * Creates an instruction to delete the existing sidecar.
     *
     * @return delete instruction
     */
    static SourceXmiUpdate deleteUpdate() {
      return new SourceXmiUpdate(null, "", null);
    }

    /**
     * Indicates whether the current sidecar should be preserved.
     *
     * @return {@code true} for preserve instruction
     */
    boolean shouldPreserve() {
      return bytes == null && hash == null;
    }

    /**
     * Indicates whether the current sidecar should be deleted.
     *
     * @return {@code true} for delete instruction
     */
    boolean shouldDelete() {
      return bytes == null && hash != null;
    }

    /**
     * Returns the hash that should be stored on the model record.
     *
     * @param previousHash previous sidecar hash
     * @return effective hash
     */
    String effectiveHash(String previousHash) {
      return shouldPreserve() ? previousHash : hash;
    }
  }

  /**
   * Stages source XMI bytes for later model creation or update.
   *
   * @param user requesting user
   * @param projectId project identifier
   * @param level model level
   * @param bytes source XMI bytes
   * @return staging token
   */
  private String stageSourceXmi(UserRecord user, String projectId, ModelLevel level, byte[] bytes) {
    String token = UUID.randomUUID().toString();
    Instant now = Instant.now();
    Path xmiPath = stagedSourceXmiPath(token);
    store.write(
        stagedSourceXmiRecordPath(token),
        new StagedImportRecord(
            token,
            user == null ? "" : user.id(),
            projectId == null ? "" : projectId,
            level,
            xmiPath.toString().replace('\\', '/'),
            bytes == null ? 0 : bytes.length,
            now,
            now.plus(runtimeOptions.stagedImportTtl())));
    store.writeBytesAtomically(xmiPath, bytes);
    return token;
  }

  /**
   * Removes and returns a staged source XMI token from model JSON.
   *
   * @param modelJson model JSON
   * @return token or empty string
   */
  private String removeSourceXmiToken(JsonNode modelJson) {
    if (modelJson instanceof ObjectNode objectNode) {
      String token = text(objectNode, "_sourceXmiToken", "");
      objectNode.remove("_sourceXmiToken");
      return token;
    }
    return "";
  }

  /**
   * Removes and decodes inline Base64 source XMI from model JSON.
   *
   * @param modelJson model JSON
   * @return decoded bytes, or {@code null}
   */
  private byte[] removeSourceXmiBase64(JsonNode modelJson) {
    if (!(modelJson instanceof ObjectNode objectNode)) {
      return null;
    }
    String encoded = text(objectNode, "_sourceXmiBase64", "");
    objectNode.remove("_sourceXmiBase64");
    if (encoded.isBlank()) {
      return null;
    }
    try {
      return Base64.getDecoder().decode(encoded);
    } catch (IllegalArgumentException ex) {
      throw new PlatformException(400, "Imported source XMI payload is not valid base64.");
    }
  }

  /**
   * Restores model and source sidecar bytes after a failed persistence step.
   *
   * @param modelPath model JSON path
   * @param sourcePath source XMI path
   * @param previousModelBytes previous model bytes
   * @param previousSourceBytes previous source bytes
   * @param hadPreviousModel whether a previous model existed
   * @param hadPreviousSource whether a previous source sidecar existed
   * @param newModelId new model id whose index should be deleted on rollback
   */
  private void rollback(
      Path modelPath,
      Path sourcePath,
      byte[] previousModelBytes,
      byte[] previousSourceBytes,
      boolean hadPreviousModel,
      boolean hadPreviousSource,
      String newModelId) {
    try {
      if (hadPreviousModel) {
        store.writeBytesAtomically(modelPath, previousModelBytes);
      } else {
        store.deleteIfExists(modelPath);
      }
      if (hadPreviousSource) {
        store.writeBytesAtomically(sourcePath, previousSourceBytes);
      } else {
        store.deleteIfExists(sourcePath);
      }
      if (newModelId != null) {
        store.deleteIfExists(modelIndexPath(newModelId));
      }
    } catch (RuntimeException ignored) {
      // Preserve the original persistence failure.
    }
  }

  /**
   * Reads raw repository bytes when a file exists.
   *
   * @param path repository-relative path
   * @return file bytes or {@code null}
   */
  private byte[] readBytesIfPresent(Path path) {
    return store.readBytes(path).orElse(null);
  }

  /**
   * Writes the global model index entry.
   *
   * @param model model to index
   */
  private void writeModelIndex(ModelRecord model) {
    store.write(
        modelIndexPath(model.id()),
        new ModelIndexRecord(model.id(), model.projectId(), model.level()));
  }

  /**
   * Deletes a staged import record and its source XMI file.
   *
   * @param record staged import record
   */
  void cleanupStagedImport(StagedImportRecord record) {
    if (record == null) {
      return;
    }
    store.deleteIfExists(Path.of(record.xmiPath()));
    store.deleteIfExists(stagedSourceXmiRecordPath(record.token()));
  }

  /**
   * Adds layout annotations to semantic elements from diagram positions.
   *
   * @param modelJson model JSON
   * @return model JSON with layout annotations
   */
  private JsonNode withLayoutAnnotations(JsonNode modelJson) {
    if (!(modelJson instanceof ObjectNode)) {
      return modelJson;
    }
    Map<String, JsonNode> layoutById = new LinkedHashMap<>();
    JsonNode elements = modelJson.path("diagram").path("elements");
    if (elements.isArray()) {
      elements.forEach(
          element -> {
            String id = text(element, "id", "");
            if (!id.isBlank() && (element.hasNonNull("x") || element.hasNonNull("y"))) {
              layoutById.put(id, element);
            }
          });
    }
    if (layoutById.isEmpty()) {
      return modelJson;
    }
    ObjectNode copy = (ObjectNode) modelJson.deepCopy();
    applyLayoutAnnotations(copy, layoutById);
    return copy;
  }

  /**
   * Recursively applies layout annotations to matching semantic elements.
   *
   * @param node current JSON node
   * @param layoutById diagram layout nodes keyed by id
   */
  private void applyLayoutAnnotations(JsonNode node, Map<String, JsonNode> layoutById) {
    if (node == null || node.isNull()) {
      return;
    }
    if (node instanceof ObjectNode objectNode) {
      String id = text(objectNode, "id", "");
      JsonNode layout = layoutById.get(id);
      if (layout != null && !"Annotation".equals(text(objectNode, "eClass", ""))) {
        mergeLayoutAnnotations(objectNode, layout);
      }
      objectNode
          .properties()
          .forEach(entry -> applyLayoutAnnotations(entry.getValue(), layoutById));
      return;
    }
    if (node.isArray()) {
      node.forEach(child -> applyLayoutAnnotations(child, layoutById));
    }
  }

  /**
   * Replaces layout annotations on one semantic element.
   *
   * @param element semantic element JSON
   * @param layout diagram layout JSON
   */
  private void mergeLayoutAnnotations(ObjectNode element, JsonNode layout) {
    ArrayNode annotations = element.withArray("annotations");
    ArrayNode retained = store.objectMapper().createArrayNode();
    annotations.forEach(
        annotation -> {
          String key = text(annotation, "key", "");
          if (!"varka.layout.x".equals(key) && !"varka.layout.y".equals(key)) {
            retained.add(annotation.deepCopy());
          }
        });
    if (layout.hasNonNull("x")) {
      retained.add(layoutAnnotation("varka.layout.x", layout.path("x").asText()));
    }
    if (layout.hasNonNull("y")) {
      retained.add(layoutAnnotation("varka.layout.y", layout.path("y").asText()));
    }
    element.set("annotations", retained);
  }

  /**
   * Creates a layout annotation object for XMI round-tripping.
   *
   * @param key annotation key
   * @param value annotation value
   * @return annotation JSON
   */
  private ObjectNode layoutAnnotation(String key, String value) {
    ObjectNode annotation = store.objectMapper().createObjectNode();
    annotation.put("eClass", "Annotation");
    annotation.put("key", key);
    annotation.put("value", value);
    annotation.put("source", "varka.ui");
    return annotation;
  }

  /**
   * Removes transport-only fields that should not be exported for validation.
   *
   * @param model model JSON copy to mutate
   */
  private void stripValidationExportOnlyFields(ObjectNode model) {
    model.remove(List.of("graph", "diagram", "views", "manualBacklog", "validationIssues"));
  }

  /**
   * Recursively hydrates semantic relationship endpoint fields from graph relationship records.
   *
   * @param node current JSON node
   * @param graphRelationships graph relationships keyed by id
   */
  private void hydrateSemanticReferences(JsonNode node, Map<String, JsonNode> graphRelationships) {
    if (node == null || node.isNull()) {
      return;
    }
    if (node.isObject()) {
      ObjectNode object = (ObjectNode) node;
      JsonNode graphRelationship = graphRelationships.get(text(object, "id", ""));
      if (graphRelationship != null) {
        copyReferenceIfMissing(object, graphRelationship, "source", "sourceElementId");
        copyReferenceIfMissing(object, graphRelationship, "target", "targetElementId");
        copyReferenceIfMissing(object, graphRelationship, "sourceElementId", "source");
        copyReferenceIfMissing(object, graphRelationship, "targetElementId", "target");
      }
      hydrateTraceEndpointIds(object);
      object
          .properties()
          .forEach(entry -> hydrateSemanticReferences(entry.getValue(), graphRelationships));
      return;
    }
    if (node.isArray()) {
      node.forEach(child -> hydrateSemanticReferences(child, graphRelationships));
    }
  }

  /**
   * Copies a reference value from a source object when the target field is blank.
   *
   * @param target target object to update
   * @param source source object to read
   * @param fieldName preferred field name
   * @param fallbackFieldName fallback field name
   */
  private void copyReferenceIfMissing(
      ObjectNode target, JsonNode source, String fieldName, String fallbackFieldName) {
    if (target.hasNonNull(fieldName) && !target.path(fieldName).asText("").isBlank()) {
      return;
    }
    String value = text(source, fieldName, "");
    if (value.isBlank()) {
      value = text(source, fallbackFieldName, "");
    }
    if (!value.isBlank()) {
      target.put(fieldName, value);
    }
  }

  /**
   * Ensures TraceLink endpoint id fields are present for export/validation.
   *
   * @param object semantic JSON object
   */
  private void hydrateTraceEndpointIds(ObjectNode object) {
    if (!"TraceLink".equals(text(object, "eClass", ""))) {
      return;
    }
    copyReferenceIfMissing(object, object, "sourceElementId", "source");
    copyReferenceIfMissing(object, object, "targetElementId", "target");
  }

  /**
   * Returns the repository path for a model JSON record.
   *
   * @param projectId project identifier
   * @param level model level
   * @param id model identifier
   * @return repository-relative path
   */
  private Path modelPath(String projectId, ModelLevel level, String id) {
    return Path.of("projects", projectId, "models", level.apiName(), id + ".json");
  }

  /**
   * Returns the repository path for a source XMI sidecar.
   *
   * @param projectId project identifier
   * @param level model level
   * @param id model identifier
   * @return repository-relative path
   */
  Path sourceXmiPath(String projectId, ModelLevel level, String id) {
    return Path.of("projects", projectId, "models", level.apiName(), id + ".xmi");
  }

  /**
   * Returns the repository path for staged source XMI bytes.
   *
   * @param token staging token
   * @return repository-relative path
   */
  private Path stagedSourceXmiPath(String token) {
    return Path.of("model-imports", token + ".xmi");
  }

  /**
   * Returns the repository path for a staged source XMI metadata record.
   *
   * @param token staging token
   * @return repository-relative path
   */
  private Path stagedSourceXmiRecordPath(String token) {
    return Path.of("model-imports", token + ".json");
  }

  /**
   * Returns the repository path for a model index record.
   *
   * @param id model identifier
   * @return repository-relative path
   */
  private Path modelIndexPath(String id) {
    return Path.of("indexes", "models", id + ".json");
  }

  /**
   * Returns the current metamodel version for a level.
   *
   * @param level model level
   * @return current metamodel version
   */
  private String currentVersion(ModelLevel level) {
    return metamodelResolver.resolve(level).version();
  }

  /**
   * Returns the current metamodel hash for a level.
   *
   * @param level model level
   * @return current metamodel hash
   */
  private String currentHash(ModelLevel level) {
    return metamodelResolver.resolve(level).sha256();
  }

  /**
   * Validates and defaults a model name.
   *
   * @param name candidate name
   * @param level model level
   * @return trimmed or default model name
   */
  private String requireName(String name, ModelLevel level) {
    return name == null || name.trim().isEmpty() ? level.apiName() + "-model" : name.trim();
  }

  /**
   * Reads a text field from a JSON object.
   *
   * @param node JSON object
   * @param field field name
   * @param fallback fallback text
   * @return field text or fallback
   */
  private String text(JsonNode node, String field, String fallback) {
    return node == null ? fallback : node.path(field).asText(fallback);
  }

  /**
   * Returns a fallback when a string is blank.
   *
   * @param value candidate value
   * @param fallback fallback value
   * @return non-blank value
   */
  private String textOrDefault(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value;
  }
}
