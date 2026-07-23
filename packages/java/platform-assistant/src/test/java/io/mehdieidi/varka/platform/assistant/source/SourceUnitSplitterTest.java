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

  @Test
  void createsAddressableSpansAtStructuralBoundaries() {
    String source = "# User stories\n\n" + "As a customer I place an order.\n\n".repeat(20);

    var units = new SourceUnitSplitter(6000).split(source);

    assertTrue(units.size() > 1);
    assertEquals(
        source,
        units.stream().map(unit -> unit.content()).collect(java.util.stream.Collectors.joining()));
  }

  @Test
  void retainsDocumentHeadingWithTheFirstModelableSpan() {
    String source = "# Community pantry\n\nAs a visitor I request an appointment.\n";

    var units = new SourceUnitSplitter(6000).split(source);

    assertEquals(1, units.size());
    assertEquals(source, units.get(0).content());
  }
}
