package io.mehdieidi.modriss.platform.assistant.source;

import io.mehdieidi.modriss.platform.assistant.turn.AssistantTurnStore.WorkItem;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import tools.jackson.databind.JsonNode;

/** Converts the analyst's complete-source plan into stable, resumable work items. */
public final class SourceWorkPlan {
  private SourceWorkPlan() {}

  public static List<WorkItem> fromBlueprint(String turnId, JsonNode blueprint) {
    List<WorkItem> items = new ArrayList<>();
    int ordinal = 1;
    for (JsonNode slice : blueprint.path("slices")) {
      String focus = slice.path("focus").asText("Source modeling increment");
      String key = digest(turnId + ":" + ordinal + ":" + slice);
      items.add(new WorkItem("work-" + key, ordinal, focus, "PLANNED", key, slice));
      ordinal++;
    }
    return items;
  }

  private static String digest(String value) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)))
          .substring(0, 20);
    } catch (java.security.NoSuchAlgorithmException ex) {
      throw new IllegalStateException("SHA-256 unavailable", ex);
    }
  }
}
