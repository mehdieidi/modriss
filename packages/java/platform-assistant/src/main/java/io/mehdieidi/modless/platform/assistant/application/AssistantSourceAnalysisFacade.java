package io.mehdieidi.modless.platform.assistant.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.mehdieidi.modless.platform.assistant.session.AssistantSessionStore;
import io.mehdieidi.modless.platform.assistant.source.SourceChunk;
import io.mehdieidi.modless.platform.assistant.source.SourceChunkProgressListener;
import io.mehdieidi.modless.platform.assistant.source.SourceCoverageMatrix;
import io.mehdieidi.modless.platform.assistant.source.SourceEvidenceGraph;
import io.mehdieidi.modless.platform.assistant.source.SourceUnderstandingService;
import io.mehdieidi.modless.platform.assistant.spi.AssistantSourceEvidenceStore;
import io.mehdieidi.modless.platform.kernel.ModelLevel;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Unified CIM source-understanding facade: chunk, extract, merge, coverage, persist. */
public class AssistantSourceAnalysisFacade {

  private static final Logger log = LoggerFactory.getLogger(AssistantSourceAnalysisFacade.class);

  private final SourceUnderstandingService sourceUnderstanding;
  private final AssistantSourceEvidenceStore sourceEvidenceStore;
  private final RealtimeTraceService traceEvents;
  private final ObjectMapper mapper;

  public AssistantSourceAnalysisFacade(
      SourceUnderstandingService sourceUnderstanding,
      AssistantSourceEvidenceStore sourceEvidenceStore,
      RealtimeTraceService traceEvents,
      ObjectMapper mapper) {
    this.sourceUnderstanding =
        sourceUnderstanding == null ? new SourceUnderstandingService(null) : sourceUnderstanding;
    this.sourceEvidenceStore =
        sourceEvidenceStore == null ? AssistantSourceEvidenceStore.noop() : sourceEvidenceStore;
    this.traceEvents = traceEvents;
    this.mapper = mapper;
  }

  /** Whether a CIM attachment still needs source evidence extraction. */
  public boolean requiresSourceAnalysis(
      AssistantSessionStore.AssistantSession session, SourceTurnRequest request) {
    return session != null
        && session.level() == ModelLevel.CIM
        && request != null
        && !blank(request.attachmentContent())
        && blank(request.sourceAnalysis());
  }

  /**
   * Runs the unified source pipeline and returns the evidence graph JSON for modeling context.
   *
   * @param session assistant session
   * @param request turn request with attachment content
   * @param maxChunkTokens per-chunk token budget
   * @param maxChunks maximum chunks per turn
   * @return analysis result with graph and serialized JSON
   */
  public SourceAnalysisResult analyze(
      AssistantSessionStore.AssistantSession session,
      SourceTurnRequest request,
      int maxChunkTokens,
      int maxChunks) {
    if (session == null || request == null || blank(request.attachmentContent())) {
      return SourceAnalysisResult.empty();
    }
    String sourceId = nonBlank(request.attachmentName(), "source");
    long started = System.nanoTime();
    SourceEvidenceGraph graph =
        sourceUnderstanding.understand(
            sourceId,
            request.attachmentName(),
            request.attachmentContent(),
            maxChunkTokens,
            maxChunks,
            new SourceChunkProgressListener() {
              @Override
              public void onChunkStarting(int chunkIndex, int totalChunks, SourceChunk chunk) {
                publishChunkExtractionStarted(session.id(), chunkIndex, totalChunks, chunk);
              }

              @Override
              public void onChunkProcessed(
                  int chunkIndex, int totalChunks, SourceEvidenceGraph partialGraph) {
                publishSourceCoverage(session.id(), partialGraph, chunkIndex, totalChunks);
              }
            });
    try {
      String json = mapper.writeValueAsString(graph);
      persistSourceEvidence(session, request, graph, json);
      log.atInfo()
          .addKeyValue("event", "source_analysis_completed")
          .addKeyValue("sessionId", session.id())
          .addKeyValue("facts", graph.facts().size())
          .addKeyValue("chunks", graph.coverage().size())
          .addKeyValue(
              "phaseElapsedMs",
              java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started))
          .log("assistant source analysis completed");
      if (traceEvents != null) {
        traceEvents.progress(
            session.id(), "", "ANALYZING_SOURCE", "Source evidence is ready for CIM planning");
      }
      return new SourceAnalysisResult(graph, json, true);
    } catch (Exception ex) {
      log.warn("Could not serialize source evidence graph.", ex);
      return SourceAnalysisResult.empty();
    }
  }

  /** Persists pre-serialized evidence when source analysis was supplied by the client. */
  public void persistIfPresent(
      AssistantSessionStore.AssistantSession session, SourceTurnRequest request) {
    if (session == null
        || request == null
        || blank(request.attachmentContent())
        || blank(request.sourceAnalysis())) {
      return;
    }
    try {
      SourceEvidenceGraph graph =
          mapper.readValue(request.sourceAnalysis(), SourceEvidenceGraph.class);
      persistSourceEvidence(session, request, graph, request.sourceAnalysis());
    } catch (Exception ex) {
      log.warn("Could not persist client-supplied source evidence.", ex);
    }
  }

  private void persistSourceEvidence(
      AssistantSessionStore.AssistantSession session,
      SourceTurnRequest request,
      SourceEvidenceGraph graph,
      String sourceAnalysis) {
    if (blank(sourceAnalysis)) {
      return;
    }
    try {
      JsonNode evidence = mapper.readTree(sourceAnalysis);
      JsonNode coverage = mapper.valueToTree(new SourceCoverageMatrix().summarize(graph));
      String sourceHash = sha256(request.attachmentContent());
      sourceEvidenceStore.save(
          new AssistantSourceEvidenceStore.SourceEvidenceRecord(
              session.id() + ":" + sourceHash,
              session.id(),
              request.modelId(),
              sourceHash,
              evidence,
              coverage,
              Instant.now()));
    } catch (Exception ex) {
      log.warn("Could not persist assistant source evidence.", ex);
    }
  }

  private void publishChunkExtractionStarted(
      String sessionId, int chunkIndex, int totalChunks, SourceChunk chunk) {
    if (traceEvents == null) {
      return;
    }
    String chunkId = chunk == null || chunk.id().isBlank() ? "chunk-" + chunkIndex : chunk.id();
    traceEvents.progress(
        sessionId,
        "",
        "ANALYZING_SOURCE",
        "Calling provider to extract source evidence for chunk "
            + chunkIndex
            + " of "
            + totalChunks);
    traceEvents.sourceCoverage(
        sessionId,
        Math.max(0, chunkIndex - 1),
        totalChunks,
        chunkId,
        "EXTRACTING",
        "Extracting chunk " + chunkIndex + " of " + totalChunks);
  }

  private void publishSourceCoverage(
      String sessionId, SourceEvidenceGraph graph, int chunkIndex, int totalChunks) {
    if (graph == null || traceEvents == null) {
      return;
    }
    int covered = 0;
    for (SourceEvidenceGraph.CoverageEntry entry : graph.coverage()) {
      if (entry.state()
          == io.mehdieidi.modless.platform.assistant.source.SourceChunk.CoverageState.COVERED) {
        covered++;
      }
    }
    String chunkId =
        graph.coverage().isEmpty()
            ? "chunk-" + chunkIndex
            : graph.coverage().get(graph.coverage().size() - 1).chunkId();
    String status =
        graph.coverage().isEmpty()
            ? "PENDING"
            : graph.coverage().get(graph.coverage().size() - 1).state().name();
    String note =
        graph.coverage().isEmpty()
            ? "Extracting chunk " + chunkIndex + " of " + totalChunks
            : graph.coverage().get(graph.coverage().size() - 1).note();
    traceEvents.sourceCoverage(
        sessionId, covered, Math.max(totalChunks, graph.coverage().size()), chunkId, status, note);
  }

  private String sha256(String value) {
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256")
              .digest((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (Exception ex) {
      return UUID.nameUUIDFromBytes((value == null ? "" : value).getBytes(StandardCharsets.UTF_8))
          .toString();
    }
  }

  private boolean blank(String value) {
    return value == null || value.isBlank();
  }

  private String nonBlank(String value, String fallback) {
    return blank(value) ? fallback : value.trim();
  }

  /** Minimal source-analysis input from a turn request. */
  public record SourceTurnRequest(
      String message,
      String modelId,
      String attachmentName,
      String attachmentContent,
      String sourceAnalysis) {

    public static SourceTurnRequest from(AssistantOrchestrator.AssistantTurnRequest request) {
      if (request == null) {
        return new SourceTurnRequest("", "", "", "", "");
      }
      return new SourceTurnRequest(
          request.message(),
          request.modelId(),
          request.attachmentName(),
          request.attachmentContent(),
          request.sourceAnalysis());
    }
  }

  /** Result of unified source analysis. */
  public record SourceAnalysisResult(SourceEvidenceGraph graph, String json, boolean success) {
    public static SourceAnalysisResult empty() {
      return new SourceAnalysisResult(
          new SourceEvidenceGraph(
              "", java.util.List.of(), java.util.List.of(), java.util.List.of()),
          "",
          false);
    }
  }
}
