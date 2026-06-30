package io.mehdieidi.modless.platform.assistant.subset;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.mehdieidi.modless.platform.assistant.domain.AssistantTurnPlan;
import org.junit.jupiter.api.Test;

class ModelSubsetPlanParserTest {

  private final ModelSubsetPlanParser parser = new ModelSubsetPlanParser(new ObjectMapper());

  @Test
  void parsesSubsetAliasesFromProviderJson() {
    ModelSubsetTurnPlan plan =
        parser.parse(
            """
            ```json
            {
              "intent": "create",
              "kind": "subset",
              "message": "Created a slice.",
              "partialModel": {
                "id": "slice-1",
                "description": "A small slice",
                "nodes": [
                  {
                    "id": "fn",
                    "type": "Function",
                    "attributes": {"name": "Book order"},
                    "owner": {"owner": "root", "feature": "functions"}
                  }
                ],
                "edges": [{"source": "route", "kind": "functionIntegration", "target": "fn"}]
              }
            }
            ```
            """);

    assertEquals(AssistantTurnPlan.Intent.MUTATION, plan.intent());
    assertEquals(ModelSubsetTurnPlan.Kind.MODEL_SUBSET, plan.kind());
    assertEquals("slice-1", plan.subset().subsetId());
    assertEquals("fn", plan.subset().elements().get(0).localId());
    assertEquals("functions", plan.subset().elements().get(0).containedBy().referenceName());
    assertEquals("functionIntegration", plan.subset().references().get(0).referenceName());
  }
}
