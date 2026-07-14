package io.mehdieidi.varka.platform.assistant.source;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SourceUnitSplitterTest {
  @Test
  void preservesEveryCharacterAndProducesStableIds() {
    String source = "# Heading\n\n" + "x".repeat(80) + "\n\n# Next\n" + "y".repeat(80);
    SourceUnitSplitter splitter = new SourceUnitSplitter(64);
    var first = splitter.split(source);
    var second = splitter.split(source);
    assertEquals(
        source,
        first.stream().map(unit -> unit.content()).collect(java.util.stream.Collectors.joining()));
    assertEquals(
        first.stream().map(unit -> unit.id()).toList(),
        second.stream().map(unit -> unit.id()).toList());
    assertTrue(first.stream().allMatch(unit -> unit.endOffset() > unit.startOffset()));
  }

  @Test
  void sourceWorkerDoesNotResplitAlreadyAnnotatedUnits() {
    String annotated = "<source-unit id=\"src-1\" ordinal=\"1\">\nkeep me\n</source-unit>\n";
    SourceDocumentWorkers workers = new SourceDocumentWorkers(null, null, 2, 16);

    assertEquals(annotated, workers.extract("session-1", annotated));
  }
}
