package io.mehdieidi.varka.platform.assistant.source;

import io.mehdieidi.varka.platform.assistant.provider.AssistantModelProvider;
import io.mehdieidi.varka.platform.assistant.spi.AssistantRealtimePublisher;

/**
 * Local source normalizer retained for compatibility with the turn service.
 *
 * <p>It deliberately makes no provider calls: source extraction is part of the single bounded agent
 * turn, never a parallel preliminary swarm outside its budget.
 */
public final class SourceDocumentWorkers implements AutoCloseable {
  private final SourceUnitSplitter splitter;

  public SourceDocumentWorkers(
      AssistantModelProvider ignoredProvider,
      AssistantRealtimePublisher ignoredRealtime,
      int ignoredParallelism,
      int sectionChars) {
    this.splitter = new SourceUnitSplitter(sectionChars <= 0 ? 12000 : sectionChars);
  }

  /** Normalizes and delimitates local source units without discarding any text. */
  public String extract(String sessionId, String document) {
    if (document == null || document.isBlank()) return "";
    if (document.contains("<source-unit id=\"")) return document;
    var units = splitter.split(document);
    StringBuilder result = new StringBuilder(document.length() + units.size() * 32);
    for (var unit : units) {
      result
          .append("<source-unit id=\"")
          .append(unit.id())
          .append("\" ordinal=\"")
          .append(unit.ordinal())
          .append("\">\n")
          .append(unit.content())
          .append("\n</source-unit>\n");
    }
    return result.toString();
  }

  @Override
  public void close() {
    // No worker threads are owned by this local normalizer.
  }
}
