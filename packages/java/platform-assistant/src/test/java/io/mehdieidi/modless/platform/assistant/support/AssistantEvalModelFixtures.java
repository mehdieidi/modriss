package io.mehdieidi.modless.platform.assistant.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.mehdieidi.modless.platform.assistant.application.AssistantEvalRunner;
import io.mehdieidi.modless.platform.kernel.ModelLevel;

/** Seeded model JSON for live eval runs. */
public final class AssistantEvalModelFixtures {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private AssistantEvalModelFixtures() {}

  public static JsonNode modelFor(ModelLevel level, AssistantEvalRunner.EvalPrompt prompt)
      throws Exception {
    if (level == ModelLevel.PIM && !prompt.emptyCanvas()) {
      return pimPaymentWorkflow();
    }
    if (level == ModelLevel.PSM && !prompt.emptyCanvas()) {
      return psmWithTrace();
    }
    return switch (level) {
      case PIM -> pimEmpty();
      case CIM -> cimEmpty();
      case PSM -> psmEmpty();
      default -> throw new IllegalArgumentException("Unsupported eval level: " + level);
    };
  }

  static JsonNode pimEmpty() throws Exception {
    return MAPPER.readTree(
        """
        {
          "id": "pim-root",
          "eClass": "PIMModel",
          "modelLevel": "PIM",
          "name": "Eval PIM",
          "workflows": [],
          "functions": [],
          "apis": [],
          "diagram": {"elements": [], "relationships": []}
        }
        """);
  }

  static JsonNode pimPaymentWorkflow() throws Exception {
    return MAPPER.readTree(
        """
        {
          "id": "pim-root",
          "eClass": "PIMModel",
          "modelLevel": "PIM",
          "name": "Payment",
          "workflows": [
            {"id": "wf-payment", "eClass": "Workflow", "name": "Payment Workflow"}
          ],
          "functions": [],
          "apis": [],
          "diagram": {
            "elements": [
              {"id": "wf-payment", "type": "Workflow", "label": "Payment Workflow"}
            ],
            "relationships": []
          }
        }
        """);
  }

  static JsonNode cimEmpty() throws Exception {
    return MAPPER.readTree(
        """
        {
          "id": "cim-root",
          "eClass": "CIMModel",
          "modelLevel": "CIM",
          "name": "Eval CIM",
          "diagram": {"elements": [], "relationships": []}
        }
        """);
  }

  static JsonNode psmWithTrace() throws Exception {
    return MAPPER.readTree(
        """
        {
          "id": "psm-root",
          "eClass": "AwsPsmModel",
          "modelLevel": "PSM",
          "name": "Eval PSM",
          "traceModel": {
            "id": "trace-health",
            "eClass": "TraceModel",
            "name": "HealthTrace"
          },
          "diagram": {
            "elements": [
              {"id": "trace-health", "type": "TraceModel", "label": "HealthTrace"}
            ],
            "relationships": []
          }
        }
        """);
  }

  static JsonNode psmEmpty() throws Exception {
    return MAPPER.readTree(
        """
        {
          "id": "psm-root",
          "eClass": "AwsPsmModel",
          "modelLevel": "PSM",
          "name": "Eval PSM",
          "stacks": [],
          "diagram": {"elements": [], "relationships": []}
        }
        """);
  }
}
