package io.mehdieidi.modless.platform.assistant.source;

import java.util.ArrayList;
import java.util.List;

/** Splits source material into bounded chunks without using document grammar for intent routing. */
public class SourceChunker {

  /** Creates approximate token-bounded chunks. */
  public List<SourceChunk> chunk(String sourceName, String content, int maxTokens, int maxChunks) {
    String text = content == null ? "" : content.trim();
    if (text.isBlank()) {
      return List.of();
    }
    int maxChars = Math.max(800, maxTokens <= 0 ? 16000 : maxTokens * 4);
    int chunkLimit = Math.max(1, maxChunks <= 0 ? 24 : maxChunks);
    List<String> parts = new ArrayList<>();
    int offset = 0;
    while (offset < text.length() && parts.size() < chunkLimit) {
      int end = Math.min(text.length(), offset + maxChars);
      if (end < text.length()) {
        int paragraph = text.lastIndexOf("\n\n", end);
        if (paragraph > offset + maxChars / 2) {
          end = paragraph;
        }
      }
      parts.add(text.substring(offset, end).trim());
      offset = end;
    }
    List<SourceChunk> result = new ArrayList<>();
    for (int index = 0; index < parts.size(); index++) {
      result.add(
          new SourceChunk(
              "chunk-" + (index + 1),
              sourceName == null || sourceName.isBlank() ? "source" : sourceName,
              parts.get(index),
              index + 1,
              parts.size(),
              SourceChunk.CoverageState.COMPRESSED));
    }
    return List.copyOf(result);
  }
}
