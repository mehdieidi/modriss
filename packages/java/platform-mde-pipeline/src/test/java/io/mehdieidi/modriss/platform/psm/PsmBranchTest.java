package io.mehdieidi.modriss.platform.psm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.NullNode;
import tools.jackson.databind.node.ObjectNode;

class PsmBranchTest {

  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void normalizesExplicitJsonNullToAbsentModelJson() {
    PsmBranch branch = branch(NullNode.getInstance());

    assertNull(branch.modelJson());
    assertEquals(branch(null), branch);
  }

  @Test
  void roundTripsBranchWithoutModelJson() {
    PsmBranch branch = branch(null);
    PsmBranch restored = mapper.readValue(mapper.writeValueAsString(branch), PsmBranch.class);

    assertEquals(branch, restored);
    assertNull(restored.modelJson());
  }

  @Test
  void readsLegacyBranchWithoutModelJsonProperty() {
    ObjectNode document = (ObjectNode) mapper.valueToTree(branch(null));
    document.remove("modelJson");

    PsmBranch restored = mapper.treeToValue(document, PsmBranch.class);

    assertEquals(branch(null), restored);
    assertNull(restored.modelJson());
  }

  @Test
  void preservesModelJsonThroughRoundTrip() {
    PsmBranch branch = branch(mapper.createObjectNode().put("eClass", "AzureModel"));

    assertEquals(branch, mapper.readValue(mapper.writeValueAsString(branch), PsmBranch.class));
  }

  private PsmBranch branch(JsonNode modelJson) {
    Instant now = Instant.parse("2026-10-17T22:42:26.538074567Z");
    return new PsmBranch(
        "branch",
        "project-1",
        "user-1",
        "azure",
        "Azure branch",
        "pim",
        3,
        "hash",
        "<azure/>",
        1,
        now,
        now,
        modelJson);
  }
}
