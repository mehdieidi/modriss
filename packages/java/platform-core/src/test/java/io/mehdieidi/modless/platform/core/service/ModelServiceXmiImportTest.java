package io.mehdieidi.modless.platform.core.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.mehdieidi.modless.platform.core.model.ModelLevel;
import io.mehdieidi.modless.platform.core.model.ModelRecord;
import io.mehdieidi.modless.platform.core.model.ProjectRecord;
import io.mehdieidi.modless.platform.core.model.UserRecord;
import io.mehdieidi.modless.platform.core.repository.JsonFileStore;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ModelServiceXmiImportTest {

    @TempDir
    Path tempDir;

    @Test
    void importsCimXmiIntoSemanticAndGraphJson() {
        JsonFileStore store = new JsonFileStore(tempDir);
        store.initialize();
        AuthService authService = new AuthService(store, Duration.ofHours(1));
        ProjectService projectService = new ProjectService(store, authService);
        ModelService service = new ModelService(store, projectService);

        ModelService.ImportResult result = service.importModel(ModelLevel.CIM, "sample.xmi",
                sampleCimXmi().getBytes(StandardCharsets.UTF_8), "xmi");

        assertEquals("sample", result.name());
        assertEquals("Test Grants", result.modelJson().path("domainName").asText());
        assertEquals("BusinessGoal",
                result.modelJson().path("goals").path(0).path("eClass").asText());
        assertEquals("goal-1", result.modelJson().path("capabilities").path(0).path("supports")
                .path(0).asText());
        assertEquals("CIM", result.modelJson().path("modelLevel").asText());

        assertFalse(result.modelJson().path("graph").path("elements").isEmpty());
        assertTrue(result.modelJson().path("graph").path("elements").findValuesAsText("id")
                .contains("cap-1"));
        assertFalse(result.modelJson().path("graph").path("relationships").isEmpty());
        assertTrue(result.modelJson().path("graph").path("relationships").findValuesAsText(
                "semanticFeature").contains("supports"));
        JsonNode supports = relationship(result.modelJson().path("graph").path("relationships"),
                "cap-1", "goal-1", "SUPPORTS");
        assertNotNull(supports);
        JsonNode rootContainsCapability = relationship(result.modelJson().path("graph")
                .path("relationships"), "cim-root", "cap-1", "CONTAINS");
        assertNotNull(rootContainsCapability);
        assertTrue(rootContainsCapability.path("containment").asBoolean());
        assertNotNull(result.issues());
    }

    @Test
    void preservesStoredSourceXmiWhenFrontendSavesWithoutIt() {
        JsonFileStore store = new JsonFileStore(tempDir);
        store.initialize();
        AuthService authService = new AuthService(store, Duration.ofHours(1));
        ProjectService projectService = new ProjectService(store, authService);
        ModelService service = new ModelService(store, projectService);
        UserRecord user = authService.register("owner@example.com", "password123", "Owner").user();
        ProjectRecord project = projectService.create(user, "Climate", "");

        ObjectNode model = store.objectMapper().createObjectNode();
        model.put("eClass", "CIMModel");
        model.put("id", "cim-root");
        model.put("name", "Climate");
        model.put("_sourceXmiBase64", "PGNpbS8+");
        ModelRecord created = service.create(user, ModelLevel.CIM, project.id(), "climate", model);

        ObjectNode update = store.objectMapper().createObjectNode();
        update.put("eClass", "CIMModel");
        update.put("id", "cim-root");
        update.put("name", "Climate edited");
        ModelRecord updated = service.update(user, ModelLevel.CIM, created.id(),
                "climate-edited", update);

        assertEquals("PGNpbS8+", updated.modelJson().path("_sourceXmiBase64").asText());
        var summaries = service.listSummaries(user, ModelLevel.CIM, project.id());
        assertEquals(1, summaries.size());
        assertEquals(created.id(), summaries.get(0).id());
        assertEquals("climate-edited", summaries.get(0).name());
    }

    @Test
    void importsPsmRelationshipViewsAsFilterableEdges() {
        JsonFileStore store = new JsonFileStore(tempDir);
        store.initialize();
        AuthService authService = new AuthService(store, Duration.ofHours(1));
        ProjectService projectService = new ProjectService(store, authService);
        ModelService service = new ModelService(store, projectService);

        ModelService.ImportResult result = service.importModel(ModelLevel.PSM, "psm.xmi",
                samplePsmXmi().getBytes(StandardCharsets.UTF_8), "xmi");

        JsonNode relationships = result.modelJson().path("graph").path("relationships");
        assertFalse(relationships.isEmpty());
        JsonNode routeToLambda = null;
        JsonNode stackContainsRoute = null;
        for (JsonNode relationship : relationships) {
            if ("view_api_lambda_submit".equals(relationship.path("id").asText())) {
                routeToLambda = relationship;
            }
            if ("stack-main".equals(relationship.path("sourceElementId").asText())
                    && "route-submit".equals(relationship.path("targetElementId").asText())
                    && "CONTAINS".equals(relationship.path("kind").asText())) {
                stackContainsRoute = relationship;
            }
        }
        assertNotNull(routeToLambda);
        assertEquals("INVOKES", routeToLambda.path("kind").asText());
        assertEquals("route-submit", routeToLambda.path("sourceElementId").asText());
        assertEquals("lambda-submit", routeToLambda.path("targetElementId").asText());
        assertNotNull(stackContainsRoute);
        assertTrue(stackContainsRoute.path("containment").asBoolean());
    }

    @Test
    void importsPimReferenceEdgesWithPimSpecificKinds() {
        JsonFileStore store = new JsonFileStore(tempDir);
        store.initialize();
        AuthService authService = new AuthService(store, Duration.ofHours(1));
        ProjectService projectService = new ProjectService(store, authService);
        ModelService service = new ModelService(store, projectService);

        ModelService.ImportResult result = service.importModel(ModelLevel.PIM, "pim.xmi",
                samplePimXmi().getBytes(StandardCharsets.UTF_8), "xmi");

        JsonNode relationships = result.modelJson().path("graph").path("relationships");
        assertFalse(relationships.isEmpty());
        JsonNode routeToFunction = relationship(relationships, "route-submit", "fn-submit",
                "ROUTES_TO");
        JsonNode functionReadsStore = relationship(relationships, "fn-submit", "store-app",
                "READS");
        JsonNode workflowTransition = relationship(relationships, "wf-start", "wf-end",
                "TRANSITION");
        JsonNode principalPermission = relationship(relationships, "principal-resident",
                "permission-submit", "PERMISSION");
        JsonNode rootContainsApi = relationship(relationships, "pim-root", "api-main",
                "CONTAINS");
        assertNotNull(routeToFunction);
        assertEquals("functionIntegration", routeToFunction.path("semanticFeature").asText());
        assertNotNull(functionReadsStore);
        assertEquals("reads", functionReadsStore.path("semanticFeature").asText());
        assertNotNull(workflowTransition);
        assertEquals("WorkflowTransition", workflowTransition.path("eClass").asText());
        assertNotNull(principalPermission);
        assertTrue(principalPermission.path("containment").asBoolean());
        assertNotNull(rootContainsApi);
        assertTrue(rootContainsApi.path("containment").asBoolean());
    }

    @Test
    void validatesPimAndPsmModelsWithEvl() {
        JsonFileStore store = new JsonFileStore(tempDir);
        store.initialize();
        AuthService authService = new AuthService(store, Duration.ofHours(1));
        ProjectService projectService = new ProjectService(store, authService);
        ModelService service = new ModelService(store, projectService);

        JsonNode pim = service.importModel(ModelLevel.PIM, "pim.xmi",
                samplePimXmi().getBytes(StandardCharsets.UTF_8), "xmi").modelJson();
        JsonNode psm = service.importModel(ModelLevel.PSM, "psm.xmi",
                samplePsmXmi().getBytes(StandardCharsets.UTF_8), "xmi").modelJson();

        ModelService.ValidationResult pimValidation = service.validate(ModelLevel.PIM, pim);
        ModelService.ValidationResult psmValidation = service.validate(ModelLevel.PSM, psm);

        assertFalse(pimValidation.valid());
        assertFalse(pimValidation.issues().isEmpty());
        assertTrue(pimValidation.issues().stream().anyMatch(issue ->
                issue.constraint().startsWith("EVL_")
                        || issue.constraint().startsWith("PIM-")));
        assertFalse(psmValidation.valid());
        assertFalse(psmValidation.issues().isEmpty());
        assertTrue(psmValidation.issues().stream().anyMatch(issue ->
                issue.constraint().startsWith("EVL_")
                        || issue.constraint().startsWith("AWS-")
                        || issue.constraint().startsWith("PSM-")));
    }

    private JsonNode relationship(JsonNode relationships, String source, String target,
            String kind) {
        for (JsonNode relationship : relationships) {
            if (source.equals(relationship.path("sourceElementId").asText())
                    && target.equals(relationship.path("targetElementId").asText())
                    && kind.equals(relationship.path("kind").asText())) {
                return relationship;
            }
        }
        return null;
    }

    private String sampleCimXmi() {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <cim:CIMModel xmi:version="2.0"
                    xmlns:xmi="http://www.omg.org/XMI"
                    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                    xmlns:cim="https://modless.org/cim/1.0"
                    xmlns:kernel="https://modless.org/kernel/1.0"
                    xmi:id="cim-root"
                    id="cim-root"
                    name="Test Grants Model"
                    domainName="Test Grants">
                  <goals xmi:id="goal-1" id="goal-1" name="Protect residents"/>
                  <actors xmi:id="actor-1" id="actor-1" name="Resident"/>
                  <capabilities xmi:id="cap-1" id="cap-1" name="Review applications" supports="goal-1"/>
                </cim:CIMModel>
                """;
    }

    private String samplePimXmi() {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <pim:PIMModel xmi:version="2.0"
                    xmlns:xmi="http://www.omg.org/XMI"
                    xmlns:pim="https://modless.org/pim/1.0"
                    id="pim-root"
                    name="PIM Test"
                    architectureStyle="WORKFLOW_ORCHESTRATED_SERVERLESS"
                    domainName="Test Grants">
                  <functions id="fn-submit"
                      name="Submit Handler"
                      functionKind="COMMAND_HANDLER"
                      reads="store-app"/>
                  <apis id="api-main"
                      name="Main API"
                      apiStyle="RESOURCE_ORIENTED_HTTP">
                    <routes id="route-submit"
                        name="Submit route"
                        method="POST"
                        pathTemplate="/submit"
                        functionIntegration="fn-submit"/>
                  </apis>
                  <dataStores id="store-app"
                      name="Application Store"
                      storeKind="DOCUMENT"
                      consistencyNeed="EVENTUAL"/>
                  <workflows id="workflow-main"
                      name="Main workflow"
                      workflowKind="ORCHESTRATION"
                      startState="wf-start"
                      endStates="wf-end">
                    <states id="wf-start"
                        name="Start"
                        stateKind="TASK"
                        orderIndex="1"
                        invokesFunction="fn-submit"/>
                    <states id="wf-end"
                        name="End"
                        stateKind="SUCCESS"
                        orderIndex="2"
                        terminal="true"/>
                    <transitions id="wf-transition"
                        name="Start to End"
                        source="wf-start"
                        target="wf-end"
                        defaultTransition="true"/>
                  </workflows>
                  <principals id="principal-resident"
                      name="Resident"
                      principalKind="HUMAN_USER"
                      privileged="false">
                    <permissions id="permission-submit"
                        name="Submit permission"
                        effect="ALLOW"
                        action="invoke"
                        resource="submit"
                        targetResource="fn-submit"/>
                  </principals>
                </pim:PIMModel>
                """;
    }

    private String samplePsmXmi() {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <xmi:XMI xmi:version="2.0"
                    xmlns:xmi="http://www.omg.org/XMI"
                    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                    xmlns:awspsm="https://modless.org/awspsm/1.0"
                    xmlns:awspsmapi="https://modless.org/awspsm/api/1.0"
                    xmlns:awspsmcompute="https://modless.org/awspsm/compute/1.0"
                    xmlns:awspsmintegrations="https://modless.org/awspsm/integrations/1.0">
                  <awspsm:AwsPsmModel id="psm-root" name="PSM Test"
                      allResources="route-submit lambda-submit">
                    <stacks id="stack-main" name="Main stack" resources="route-submit lambda-submit">
                      <resources xsi:type="awspsmapi:HttpApiRoute"
                          id="route-submit"
                          name="Submit route"
                          logicalId="SubmitRoute"
                          method="POST"
                          path="/submit"
                          routeKey="POST /submit"/>
                      <resources xsi:type="awspsmcompute:AwsLambdaFunction"
                          id="lambda-submit"
                          name="Submit handler"
                          logicalId="SubmitHandler"
                          functionName="submit-handler"/>
                    </stacks>
                    <relationshipViews xsi:type="awspsmintegrations:ApiGatewayLambdaIntegrationView"
                        id="view_api_lambda_submit"
                        name="Submit route to handler"
                        source="route-submit"
                        target="lambda-submit"
                        route="route-submit"
                        function="lambda-submit"/>
                  </awspsm:AwsPsmModel>
                </xmi:XMI>
                """;
    }
}
