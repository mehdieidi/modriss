package io.mehdieidi.modriss.platform.assistant.source;

import io.mehdieidi.modriss.platform.assistant.turn.AssistantTurnStore.SourceFact;
import io.mehdieidi.modriss.platform.assistant.turn.AssistantTurnStore.SourceUnit;
import java.util.List;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/**
 * The durable, minimum fact graph for a supplied document.
 *
 * <p>Every span starts as a grounded fact. The source analyst may enrich those facts, but it cannot
 * make a source span disappear: it must eventually be modeled or explicitly classified.
 */
public final class SourceFactGraph {
  private SourceFactGraph() {}

  public static List<SourceFact> fromSpans(List<SourceUnit> spans) {
    return spans.stream().map(SourceFactGraph::fact).toList();
  }

  private static SourceFact fact(SourceUnit span) {
    ObjectNode payload = JsonNodeFactory.instance.objectNode();
    payload.put("sourceSpanId", span.id());
    payload.put("ordinal", span.ordinal());
    payload.put("startOffset", span.startOffset());
    payload.put("endOffset", span.endOffset());
    return new SourceFact("fact-" + span.id(), "SOURCE_SPAN", "UNRESOLVED", payload, null);
  }
}
