import { DefaultTypes } from "@eclipse-glsp/protocol";
import { GEdge, GModelElement } from "@eclipse-glsp/graph";
import { GModelCreateEdgeOperationHandler } from "@eclipse-glsp/server";
import { injectable } from "inversify";
import { validateCreateEdge } from "../operation-validator.js";

@injectable()
export class ModlessCreateEdgeHandler extends GModelCreateEdgeOperationHandler {
  readonly label = "Relationship";
  readonly elementTypeIds = [DefaultTypes.EDGE];

  createEdge(source: GModelElement, target: GModelElement): GEdge | undefined {
    const sourceType = String((source as { args?: Record<string, unknown> }).args?.elementType || DefaultTypes.NODE);
    const targetType = String((target as { args?: Record<string, unknown> }).args?.elementType || DefaultTypes.NODE);
    const validation = validateCreateEdge(
      this.modelState.get<Record<string, unknown>>("levelConfig") || {},
      sourceType,
      targetType,
    );
    if (!validation.ok || !validation.edgeKind) {
      return undefined;
    }

    const id = `edge-${Date.now()}`;
    return GEdge.builder()
      .id(id)
      .type(DefaultTypes.EDGE)
      .sourceId(source.id)
      .targetId(target.id)
      .addArg("kind", validation.edgeKind || "DEPENDS_ON")
      .build();
  }
}
