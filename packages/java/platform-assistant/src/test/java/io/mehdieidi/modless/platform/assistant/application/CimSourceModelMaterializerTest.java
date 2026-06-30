package io.mehdieidi.modless.platform.assistant.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.mehdieidi.modless.platform.assistant.domain.SemanticModelPatch;
import io.mehdieidi.modless.platform.assistant.patch.AssistantMetamodelSchemaService;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CimSourceModelMaterializerTest {

  private final CimSourceModelMaterializer materializer =
      new CimSourceModelMaterializer(new AssistantMetamodelSchemaService(), new ObjectMapper());

  @Test
  void materializesClassifiedCimEvidenceIntoSemanticPatch() {
    String sourceAnalysis =
        """
        {
          "elements": [
            {
              "sourceKey": "goal.reduce-phone-traffic",
              "type": "BusinessGoal",
              "name": "Reduce phone scheduling traffic",
              "summary": "Patients can book without calling the clinic.",
              "sourceExcerpt": "Patients can find and book appointments without calling."
            },
            {
              "sourceKey": "actor.patient",
              "type": "Actor",
              "name": "Patient",
              "attributes": {"actorType": "HUMAN"}
            },
            {
              "sourceKey": "entity.appointment",
              "type": "DomainEntity",
              "name": "Appointment",
              "attributes": {"identityStrategy": "BUSINESS_KEY"}
            },
            {
              "sourceKey": "command.book-appointment",
              "type": "Command",
              "name": "Book appointment",
              "summary": "Patient books a selected appointment slot."
            }
          ],
          "relationships": [
            {
              "source": "actor.patient",
              "target": "command.book-appointment",
              "referenceName": "issuesCommands"
            }
          ]
        }
        """;

    Optional<CimSourceModelMaterializer.Result> result = materializer.materialize(sourceAnalysis);

    assertTrue(result.isPresent());
    SemanticModelPatch patch = result.get().patch();
    assertEquals(5, patch.operations().size());
    assertEquals(
        4,
        patch.operations().stream()
            .filter(operation -> operation.type() == SemanticModelPatch.OperationType.ADD_ELEMENT)
            .count());
    assertTrue(result.get().coverageSummary().contains("source-backed CIM elements"));
  }

  @Test
  void ignoresFreeFormAnalysisSoPlannerFallbackCanHandleIt() {
    assertTrue(
        materializer
            .materialize("Commands: Register patient. Events: Patient registered.")
            .isEmpty());
  }
}
