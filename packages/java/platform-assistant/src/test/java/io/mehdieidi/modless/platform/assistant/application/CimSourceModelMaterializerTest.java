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
    assertEquals(
        7,
        patch.operations().stream()
            .filter(operation -> operation.type() == SemanticModelPatch.OperationType.ADD_ELEMENT)
            .count());
    String patientId =
        patch.operations().stream()
            .filter(operation -> operation.type() == SemanticModelPatch.OperationType.ADD_ELEMENT)
            .filter(operation -> "Actor".equals(operation.elementType()))
            .findFirst()
            .orElseThrow()
            .targetElementId();
    String roleId =
        patch.operations().stream()
            .filter(operation -> operation.type() == SemanticModelPatch.OperationType.ADD_ELEMENT)
            .filter(operation -> "Role".equals(operation.elementType()))
            .findFirst()
            .orElseThrow()
            .targetElementId();
    String appointmentId =
        patch.operations().stream()
            .filter(operation -> operation.type() == SemanticModelPatch.OperationType.ADD_ELEMENT)
            .filter(operation -> "DomainEntity".equals(operation.elementType()))
            .findFirst()
            .orElseThrow()
            .targetElementId();
    String identityId =
        patch.operations().stream()
            .filter(operation -> operation.type() == SemanticModelPatch.OperationType.ADD_ELEMENT)
            .filter(operation -> "InformationItem".equals(operation.elementType()))
            .findFirst()
            .orElseThrow()
            .targetElementId();
    String commandId =
        patch.operations().stream()
            .filter(operation -> operation.type() == SemanticModelPatch.OperationType.ADD_ELEMENT)
            .filter(operation -> "Command".equals(operation.elementType()))
            .findFirst()
            .orElseThrow()
            .targetElementId();
    String errorId =
        patch.operations().stream()
            .filter(operation -> operation.type() == SemanticModelPatch.OperationType.ADD_ELEMENT)
            .filter(operation -> "BusinessError".equals(operation.elementType()))
            .findFirst()
            .orElseThrow()
            .targetElementId();
    assertTrue(hasConnection(patch, patientId, roleId, "playsRoles"));
    assertTrue(hasConnection(patch, appointmentId, identityId, "identityAttributes"));
    assertTrue(hasConnection(patch, appointmentId, identityId, "primaryIdentityAttribute"));
    assertTrue(hasConnection(patch, commandId, errorId, "possibleErrors"));
    assertTrue(result.get().coverageSummary().contains("source-backed CIM elements"));
  }

  @Test
  void ignoresFreeFormAnalysisSoPlannerFallbackCanHandleIt() {
    assertTrue(
        materializer
            .materialize("Commands: Register patient. Events: Patient registered.")
            .isEmpty());
  }

  @Test
  void createsRequiredQueryOutputInformationItems() {
    String sourceAnalysis =
        """
        {
          "elements": [
            {
              "sourceKey": "query.published-schedule",
              "type": "Query",
              "name": "Published schedule"
            }
          ],
          "relationships": []
        }
        """;

    SemanticModelPatch patch = materializer.materialize(sourceAnalysis).orElseThrow().patch();
    String queryId =
        patch.operations().stream()
            .filter(operation -> operation.type() == SemanticModelPatch.OperationType.ADD_ELEMENT)
            .filter(operation -> "Query".equals(operation.elementType()))
            .findFirst()
            .orElseThrow()
            .targetElementId();
    String outputId =
        patch.operations().stream()
            .filter(operation -> operation.type() == SemanticModelPatch.OperationType.ADD_ELEMENT)
            .filter(operation -> "InformationItem".equals(operation.elementType()))
            .findFirst()
            .orElseThrow()
            .targetElementId();

    assertTrue(hasConnection(patch, queryId, outputId, "output"));
  }

  @Test
  void salvagesCompletedElementsFromTruncatedSourceAnalysisJson() {
    String truncated =
        """
        ```json
        {
          "elements": [
            {
              "sourceKey": "goal.reduce-phone-traffic",
              "type": "BusinessGoal",
              "name": "Reduce phone scheduling traffic",
              "summary": "Patients can book without calling the clinic."
            },
            {
              "sourceKey": "actor.patient",
              "type": "Actor",
              "name": "Patient",
              "attributes": {"actorType": "HUMAN"}
            },
            {
              "sourceKey": "command.book-appointment",
              "type": "Command",
              "name": "Book appointment",
              "attributes": {"semanticName": "BookAppointment"}
            },
            {
              "sourceKey": "event.appointment-booked",
              "type": "BusinessEvent",
              "name": "Appointment booked",
              "attributes": {
        """;

    Optional<CimSourceModelMaterializer.Result> result = materializer.materialize(truncated);

    assertTrue(result.isPresent());
    assertEquals(
        5,
        result.get().patch().operations().stream()
            .filter(operation -> operation.type() == SemanticModelPatch.OperationType.ADD_ELEMENT)
            .count());
  }

  @Test
  void extractsStructuredEventStormingDocumentBeforeLlmSourceAnalysis() {
    String source =
        """
        Stakeholders and goals:
        - Organizer wants to publish a conference program and sell tickets.
        - Speaker wants to submit proposals and confirm acceptance.

        User stories:
        1. As an organizer, I create an event with rooms and tracks.
        2. As a speaker, I submit a session proposal.
        3. As an attendee, I buy a ticket and receive a QR code.

        Commands:
        - CreateEvent, SubmitProposal, PurchaseTicket

        Business events:
        - EventCreated, ProposalSubmitted, TicketPurchased

        Policies and rules:
        - Ticket purchase requires successful payment before QR code is issued.

        External systems:
        - Payment gateway captures charges and reports failures.

        Queries/views:
        - PublishedSchedule, TicketSalesDashboard
        """;

    Optional<String> analysis =
        new CimSourceDocumentExtractor(new ObjectMapper()).extract("event-storming.md", source);

    assertTrue(analysis.isPresent());
    Optional<CimSourceModelMaterializer.Result> result = materializer.materialize(analysis.get());
    assertTrue(result.isPresent());
    assertTrue(
        result.get().patch().operations().stream()
                .filter(
                    operation -> operation.type() == SemanticModelPatch.OperationType.ADD_ELEMENT)
                .count()
            >= 15);
  }

  private boolean hasConnection(
      SemanticModelPatch patch, String sourceId, String targetId, String referenceName) {
    return patch.operations().stream()
        .anyMatch(
            operation ->
                operation.type() == SemanticModelPatch.OperationType.CONNECT_ELEMENTS
                    && sourceId.equals(operation.sourceElementId())
                    && targetId.equals(operation.targetElementId())
                    && referenceName.equals(operation.referenceName()));
  }
}
