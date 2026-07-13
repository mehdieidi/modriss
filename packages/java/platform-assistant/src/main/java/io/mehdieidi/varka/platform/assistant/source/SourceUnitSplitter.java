package io.mehdieidi.varka.platform.assistant.source;

import io.mehdieidi.varka.platform.assistant.turn.AssistantTurnStore.SourceUnit;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/** Deterministically splits source material by headings/paragraphs without provider calls. */
public final class SourceUnitSplitter {
  private final int limit;

  public SourceUnitSplitter(int limit) {
    this.limit = limit <= 0 ? 12000 : limit;
  }

  public List<SourceUnit> split(String text) {
    String source = text == null ? "" : text.replace("\r\n", "\n");
    List<SourceUnit> result = new ArrayList<>();
    for (int start = 0, ordinal = 1; start < source.length(); ordinal++) {
      int end = Math.min(source.length(), start + limit);
      if (end < source.length()) {
        int boundary = Math.max(source.lastIndexOf("\n#", end), source.lastIndexOf("\n\n", end));
        if (boundary > start + limit / 3) end = boundary;
      }
      String unit = source.substring(start, end);
      result.add(new SourceUnit(id(ordinal, start, end, unit), ordinal, start, end, unit));
      start = end;
    }
    return result;
  }

  private String id(int ordinal, int start, int end, String value) {
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
      return "src-"
          + ordinal
          + "-"
          + start
          + "-"
          + end
          + "-"
          + HexFormat.of().formatHex(digest).substring(0, 12);
    } catch (Exception ex) {
      throw new IllegalStateException("SHA-256 unavailable", ex);
    }
  }
}
