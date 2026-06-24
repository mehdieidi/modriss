import { GLabel, RenderingContext, ShapeView } from "@eclipse-glsp/sprotty";
import { injectable } from "inversify";
import { VNode } from "snabbdom";

/**
 * Node and edge labels are painted inside ModlessNodeView / ModlessEdgeView.
 * Suppress the default Sprotty label layer to avoid duplicate overflowing text.
 */
@injectable()
export class ModlessLabelView extends ShapeView {
  override render(
    _label: Readonly<GLabel>,
    _context: RenderingContext,
  ): VNode | undefined {
    return undefined;
  }
}
