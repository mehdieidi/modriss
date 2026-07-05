package io.mehdieidi.modless.platform.assistant.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.mehdieidi.modless.platform.assistant.session.AssistantSessionStore;
import io.mehdieidi.modless.platform.assistant.source.SourceChunker;
import io.mehdieidi.modless.platform.assistant.source.SourceEvidenceExtractor;
import io.mehdieidi.modless.platform.assistant.source.SourceEvidenceMerger;
import io.mehdieidi.modless.platform.assistant.source.SourceUnderstandingService;
import io.mehdieidi.modless.platform.kernel.ModelLevel;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class AssistantSourceAnalysisFacadeTest {

  @Test
  void smallAttachmentUsesFastLocalPathWithoutProvider() {
    SlowSourceUnderstandingService slow = new SlowSourceUnderstandingService();
    AssistantSourceAnalysisFacade facade =
        new AssistantSourceAnalysisFacade(
            slow,
            new SourceChunker(),
            new SourceEvidenceExtractor(),
            new SourceEvidenceMerger(),
            null,
            null,
            new ObjectMapper());
    AssistantSessionStore.AssistantSession session =
        new AssistantSessionStore.AssistantSession(
            "session-1",
            "user-1",
            "project-1",
            ModelLevel.CIM,
            "Clinic",
            Instant.now(),
            Instant.now());

    AssistantSourceAnalysisFacade.SourceAnalysisResult result =
        facade.analyze(
            session,
            new AssistantSourceAnalysisFacade.SourceTurnRequest(
                "Create a CIM model",
                null,
                "notes.md",
                "Patient books an appointment and receives a reminder.",
                ""),
            4000,
            24);

    assertTrue(result.success());
    assertTrue(slow.invoked == 0, "fast path should not call LLM source understanding");
    assertEquals(1, result.graph().coverage().size());
  }

  private static final class SlowSourceUnderstandingService extends SourceUnderstandingService {
    private int invoked;

    SlowSourceUnderstandingService() {
      super(new SourceChunker());
    }

    @Override
    public io.mehdieidi.modless.platform.assistant.source.SourceEvidenceGraph understand(
        String sourceId,
        String sourceName,
        String content,
        int maxTokens,
        int maxChunks,
        io.mehdieidi.modless.platform.assistant.source.SourceChunkProgressListener listener) {
      invoked++;
      return super.understand(sourceId, sourceName, content, maxTokens, maxChunks, listener);
    }
  }
}
