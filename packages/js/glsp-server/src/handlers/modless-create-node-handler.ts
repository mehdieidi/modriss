import { DefaultTypes, Point, CreateNodeOperation } from "@eclipse-glsp/protocol";
import { GLabel, GNode } from "@eclipse-glsp/graph";
import { GModelCreateNodeOperationHandler } from "@eclipse-glsp/server";
import { injectable } from "inversify";
import { validateCreateNode } from "../operation-validator.js";

@injectable()
export class ModlessCreateNodeHandler extends GModelCreateNodeOperationHandler {
  readonly label = "Element";

  get elementTypeIds(): string[] {
    const levelConfig = this.modelState.get<Record<string, unknown>>("levelConfig") || {};
    const elements = (levelConfig.elements as Array<{ type: string }> | undefined) || [];
    if (elements.length) {
      return elements.map((item) => item.type);
    }
    return [DefaultTypes.NODE];
  }

  createNode(operation: CreateNodeOperation, relativeLocation?: Point) {
    const elementType = operation.elementTypeId || DefaultTypes.NODE;
    const validation = validateCreateNode(
      this.modelState.get<Record<string, unknown>>("levelConfig") || {},
      elementType,
    );
    if (!validation.ok) {
      return undefined;
    }

    const id = `node-${Date.now()}`;
    const position = relativeLocation ?? Point.ORIGIN;
    return GNode.builder()
      .id(id)
      .type(DefaultTypes.NODE)
      .position(position)
      .size(228, 112)
      .addArg("elementType", elementType)
      .addChildren(
        GLabel.builder().id(`${id}-label`).type(DefaultTypes.LABEL).text(elementType).build(),
        GLabel.builder().id(`${id}-lines`).type(DefaultTypes.LABEL).text("").build(),
      )
      .build();
  }
}
