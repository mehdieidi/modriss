import { DefaultTypes, EdgeTypeHint, ShapeTypeHint } from "@eclipse-glsp/protocol";
import {
  DiagramConfiguration,
  GEdge,
  GGraph,
  GLabel,
  GModelElementConstructor,
  GNode,
  ServerLayoutKind,
} from "@eclipse-glsp/server";
import { injectable } from "inversify";

function defaultShapeHint(elementTypeId: string): ShapeTypeHint {
  return {
    elementTypeId,
    repositionable: true,
    deletable: true,
    resizable: true,
    reparentable: true,
  };
}

function defaultEdgeHint(elementTypeId: string): EdgeTypeHint {
  return {
    elementTypeId,
    repositionable: true,
    deletable: true,
    routable: true,
    sourceElementTypeIds: [DefaultTypes.NODE],
    targetElementTypeIds: [DefaultTypes.NODE],
  };
}

@injectable()
export class ModlessDiagramConfiguration implements DiagramConfiguration {
  readonly layoutKind = ServerLayoutKind.MANUAL;
  readonly needsClientLayout = true;
  readonly animatedUpdate = true;

  get typeMapping(): Map<string, GModelElementConstructor> {
    return new Map<string, GModelElementConstructor>([
      [DefaultTypes.GRAPH, GGraph],
      [DefaultTypes.NODE, GNode],
      [DefaultTypes.EDGE, GEdge],
      [DefaultTypes.LABEL, GLabel],
    ]);
  }

  get shapeTypeHints(): ShapeTypeHint[] {
    return [defaultShapeHint(DefaultTypes.NODE)];
  }

  get edgeTypeHints(): EdgeTypeHint[] {
    return [defaultEdgeHint(DefaultTypes.EDGE)];
  }
}
