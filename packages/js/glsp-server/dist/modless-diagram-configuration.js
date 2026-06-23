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
import { GEdge, GGraph, GLabel, GNode, ServerLayoutKind } from "@eclipse-glsp/server";
import { injectable } from "inversify";
function defaultShapeHint(elementTypeId) {
  return {
    elementTypeId,
    repositionable: true,
    deletable: true,
    resizable: true,
    reparentable: true,
  };
}
function defaultEdgeHint(elementTypeId) {
  return {
    elementTypeId,
    repositionable: true,
    deletable: true,
    routable: true,
    sourceElementTypeIds: [DefaultTypes.NODE],
    targetElementTypeIds: [DefaultTypes.NODE],
  };
}
let ModlessDiagramConfiguration = class ModlessDiagramConfiguration {
  layoutKind = ServerLayoutKind.MANUAL;
  needsClientLayout = true;
  animatedUpdate = true;
  get typeMapping() {
    return new Map([
      [DefaultTypes.GRAPH, GGraph],
      [DefaultTypes.NODE, GNode],
      [DefaultTypes.EDGE, GEdge],
      [DefaultTypes.LABEL, GLabel],
    ]);
  }
  get shapeTypeHints() {
    return [defaultShapeHint(DefaultTypes.NODE)];
  }
  get edgeTypeHints() {
    return [defaultEdgeHint(DefaultTypes.EDGE)];
  }
};
ModlessDiagramConfiguration = __decorate([injectable()], ModlessDiagramConfiguration);
export { ModlessDiagramConfiguration };
