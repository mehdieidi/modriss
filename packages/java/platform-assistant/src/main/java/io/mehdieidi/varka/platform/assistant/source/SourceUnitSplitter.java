package io.mehdieidi.varka.platform.assistant.source;

import io.mehdieidi.varka.platform.assistant.turn.AssistantTurnStore.SourceUnit;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
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
    if (source.isEmpty()) return List.of();
    List<SourceUnit> result = new ArrayList<>();
    if (source.length() <= limit) return structuralSpans(source);
    for (int start = 0, ordinal = 1; start < source.length(); ordinal++) {
      int end = Math.min(source.length(), start + limit);
      if (end < source.length()) {
        // Preserve headings, paragraphs, and event-storming lanes/items as whole units whenever
        // possible. A hard character cut is only the final fallback for one oversized paragraph.
        int boundary =
            Math.max(
                Math.max(source.lastIndexOf("\n#", end), source.lastIndexOf("\n\n", end)),
                Math.max(source.lastIndexOf("\n- ", end), source.lastIndexOf("\n* ", end)));
        if (boundary > start + limit / 3) end = boundary;
      }
      String unit = source.substring(start, end);
      result.add(new SourceUnit(id(ordinal, start, end, unit), ordinal, start, end, unit));
      start = end;
    }
    return result;
  }

  /**
   * Keeps headings, paragraphs, list blocks and event-storming lanes addressable for provenance.
   */
  private List<SourceUnit> structuralSpans(String source) {
    List<SourceUnit> spans = new ArrayList<>();
    int start = 0;
    int ordinal = 1;
    for (int cursor = 0; cursor < source.length(); cursor++) {
      boolean heading =
          cursor > start && source.charAt(cursor) == '#' && source.charAt(cursor - 1) == '\n';
      boolean blankLine =
          cursor + 1 < source.length()
              && source.charAt(cursor) == '\n'
              && source.charAt(cursor + 1) == '\n';
      if (!heading && !blankLine) continue;
      // A heading is context for the content which follows it, not independently modelable
      // evidence.  Do not turn a document title followed by a blank line into a dead source
      // unit: it cannot ground a meaningful model element and leaves a coverage obligation that
      // a later workflow increment can never satisfy.
      if (blankLine && !hasSubstantiveContent(source.substring(start, cursor))) continue;
      int end = heading ? cursor : cursor + 2;
      if (end > start) {
        String content = source.substring(start, end);
        spans.add(
            new SourceUnit(id(ordinal++, start, end, content), ordinal - 1, start, end, content));
        start = end;
      }
    }
    if (start < source.length()) {
      String content = source.substring(start);
      spans.add(
          new SourceUnit(
              id(ordinal, start, source.length(), content),
              ordinal,
              start,
              source.length(),
              content));
    }
    return spans;
  }

  private boolean hasSubstantiveContent(String value) {
    for (String line : value.split("\\n")) {
      String trimmed = line.trim();
      if (!trimmed.isEmpty() && !trimmed.startsWith("#")) return true;
    }
    return false;
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
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException("SHA-256 unavailable", ex);
    }
  }
}
