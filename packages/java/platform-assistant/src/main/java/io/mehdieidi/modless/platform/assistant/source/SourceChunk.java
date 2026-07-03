package io.mehdieidi.modless.platform.assistant.source;

/** One bounded source-material chunk passed to evidence extraction. */
public record SourceChunk(
    String id, String title, String content, int index, int count, CoverageState coverageState) {

  public SourceChunk {
    id = id == null ? "" : id.trim();
    title = title == null ? "" : title.trim();
    content = content == null ? "" : content;
    coverageState = coverageState == null ? CoverageState.COMPRESSED : coverageState;
  }

  /** Coverage status for a source chunk. */
  public enum CoverageState {
    COVERED,
    COMPRESSED,
    NEEDS_CLARIFICATION
  }
}
