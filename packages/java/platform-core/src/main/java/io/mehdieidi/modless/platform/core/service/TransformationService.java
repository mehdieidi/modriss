package io.mehdieidi.modless.platform.core.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.mehdieidi.modless.platform.core.PlatformException;
import io.mehdieidi.modless.platform.core.model.ArtifactRecord;
import io.mehdieidi.modless.platform.core.model.ModelLevel;
import io.mehdieidi.modless.platform.core.model.ModelRecord;
import io.mehdieidi.modless.platform.core.model.UserRecord;
import io.mehdieidi.modless.platform.core.repository.JsonFileStore;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

public final class TransformationService {

    private final JsonFileStore store;
    private final ModelService modelService;
    private final ArtifactService artifactService;

    public TransformationService(JsonFileStore store, ModelService modelService,
            ArtifactService artifactService) {
        this.store = store;
        this.modelService = modelService;
        this.artifactService = artifactService;
    }

    public ModelRecord cimToPim(UserRecord user, String sourceModelId) {
        ModelRecord source = modelService.get(user, ModelLevel.CIM, sourceModelId);
        ObjectNode target = transformedModel(source, ModelLevel.PIM);
        target.put("sourceModelId", source.id());
        addManualBacklog(target,
                "Review generated PIM responsibilities and integration contracts.");
        return modelService.create(user, ModelLevel.PIM, source.projectId(), source.name() + "-pim",
                target);
    }

    public ModelRecord pimToPsm(UserRecord user, String sourceModelId) {
        ModelRecord source = modelService.get(user, ModelLevel.PIM, sourceModelId);
        ObjectNode target = transformedModel(source, ModelLevel.PSM);
        target.put("sourceModelId", source.id());
        addManualBacklog(target,
                "Review generated AWS resource sizing, IAM scope, and deployment settings.");
        return modelService.create(user, ModelLevel.PSM, source.projectId(), source.name() + "-psm",
                target);
    }

    public ArtifactRecord psmToArtifact(UserRecord user, String sourceModelId) {
        ModelRecord source = modelService.get(user, ModelLevel.PSM, sourceModelId);
        Map<String, String> files = new LinkedHashMap<>();
        String projectName = sanitize(source.name());
        files.put("README.md", "# " + source.name() + "\n\nGenerated from PSM model `" + source.id()
                + "` at " + Instant.now() + ".\n\nReview generated files before deployment.\n");
        files.put("template.yaml", """
                AWSTemplateFormatVersion: '2010-09-09'
                Transform: AWS::Serverless-2016-10-31
                Description: Generated Modless AWS serverless scaffold
                Resources: {}
                """);
        files.put("modless/model.psm.json", pretty(source.modelJson()));
        files.put("docs/generation-report.md", "Generated artifact scaffold for `" + projectName
                + "`.\n\nThe formal EGX generator can replace this file-backed implementation when XMI export is wired.\n");
        return artifactService.create(user, source.projectId(), source.name() + "-artifact", files);
    }

    private ObjectNode transformedModel(ModelRecord source, ModelLevel targetLevel) {
        ObjectNode target = source.modelJson() != null && source.modelJson().isObject()
                ? ((ObjectNode) source.modelJson().deepCopy())
                : store.objectMapper().createObjectNode();
        target.put("name", source.name() + "-" + targetLevel.apiName());
        target.put("modelLevel", targetLevel.name());
        target.put("transformedFrom", source.level().name());
        target.put("transformedFromModelId", source.id());
        target.put("transformationStatus", "GENERATED_FOR_REVIEW");
        return target;
    }

    private void addManualBacklog(ObjectNode model, String title) {
        model.withArray("manualBacklog").addObject()
                .put("id", "review-" + System.currentTimeMillis())
                .put("name", title)
                .put("status", "OPEN")
                .put("required", true)
                .put("category", "MANUAL_MODELING")
                .put("rationale",
                        "Semi-automated transformations require human review before promotion.");
    }

    private String pretty(JsonNode node) {
        try {
            return store.objectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(node);
        } catch (Exception ex) {
            throw new PlatformException(500, "Could not serialize model.");
        }
    }

    private String sanitize(String value) {
        return String.valueOf(value == null ? "artifact" : value)
                .replaceAll("[^A-Za-z0-9_.-]+", "-");
    }
}
