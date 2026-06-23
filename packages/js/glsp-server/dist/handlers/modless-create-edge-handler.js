var __decorate =
  (this && this.__decorate) ||
  function (decorators, target, key, desc) {
    var c = arguments.length,
      r =
        c < 3
          ? target
          : desc === null
            ? (desc = Object.getOwnPropertyDescriptor(target, key))
            : desc,
      d;
    if (typeof Reflect === "object" && typeof Reflect.decorate === "function")
      r = Reflect.decorate(decorators, target, key, desc);
    else
      for (var i = decorators.length - 1; i >= 0; i--)
        if ((d = decorators[i]))
          r = (c < 3 ? d(r) : c > 3 ? d(target, key, r) : d(target, key)) || r;
    return c > 3 && r && Object.defineProperty(target, key, r), r;
  };
import { DefaultTypes } from "@eclipse-glsp/protocol";
import { GEdge } from "@eclipse-glsp/graph";
import { GModelCreateEdgeOperationHandler } from "@eclipse-glsp/server";
import { injectable } from "inversify";
import { validateCreateEdge } from "../operation-validator.js";
let ModlessCreateEdgeHandler = class ModlessCreateEdgeHandler extends GModelCreateEdgeOperationHandler {
  label = "Relationship";
  elementTypeIds = [DefaultTypes.EDGE];
  createEdge(source, target) {
    const sourceType = String(source.args?.elementType || DefaultTypes.NODE);
    const targetType = String(target.args?.elementType || DefaultTypes.NODE);
    const validation = validateCreateEdge(
      this.modelState.get("levelConfig") || {},
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
};
ModlessCreateEdgeHandler = __decorate([injectable()], ModlessCreateEdgeHandler);
export { ModlessCreateEdgeHandler };
