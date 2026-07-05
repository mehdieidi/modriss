package io.mehdieidi.modless.platform.assistant.source;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SourceChunkerTest {

  @Test
  void splitsUserStoryDocumentIntoMultipleSemanticChunks() {
    String document =
        """
        # Clinic Portal

        ## User Stories

        ### US-01 Search Appointments
        As a patient, I want to search slots.

        ### US-02 Book Appointment
        As a patient, I want to book a slot.

        ### US-03 Cancel Appointment
        As a patient, I want to cancel a booking.
        """;

    SourceChunker chunker = new SourceChunker();
    var chunks = chunker.chunk("stories.md", document, 400, 24);

    assertTrue(chunks.size() >= 3, "expected one chunk per user story section");
    assertTrue(
        chunks.stream().anyMatch(chunk -> chunk.content().contains("US-01")),
        "chunk should retain user story heading");
    assertTrue(
        chunks.stream().anyMatch(chunk -> chunk.content().contains("US-02")),
        "chunk should retain second user story");
  }
}
