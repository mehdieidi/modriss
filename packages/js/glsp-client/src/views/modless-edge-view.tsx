import type { GEdge } from "@eclipse-glsp/client";
import { IView, RenderingContext, Point, svg } from "@eclipse-glsp/sprotty";
import { injectable } from "inversify";
import { VNode } from "snabbdom";

function arrowHeadPoints(route: Point[]): string {
  const last = route[route.length - 1];
  const prev = route[route.length - 2];
  const dx = last.x - prev.x;
  const dy = last.y - prev.y;
  const length = Math.hypot(dx, dy) || 1;
  const ux = dx / length;
  const uy = dy / length;
  const size = 8;
  const leftX = last.x - ux * size - uy * size * 0.4;
  const leftY = last.y - uy * size + ux * size * 0.4;
  const rightX = last.x - ux * size + uy * size * 0.4;
  const rightY = last.y - uy * size - ux * size * 0.4;
  return `${last.x},${last.y} ${leftX},${leftY} ${rightX},${rightY}`;
}

@injectable()
export class ModlessEdgeView implements IView {
  render(edge: Readonly<GEdge>, context: RenderingContext): VNode {
    const route = edge.routingPoints || [];
    if (route.length < 2) {
      return svg`<g></g>`;
    }
    const path = route.map((point, index) => `${index === 0 ? "M" : "L"} ${point.x} ${point.y}`).join(" ");
    const args = (edge as unknown as { args?: Record<string, unknown> }).args || {};
    const kind = String(args.kind || "");
    const label = (edge.children || []).find((child) => child.type === "label");
    const stroke = kind.includes("TRACE") ? "#64748b" : "var(--edge-stroke, #64748b)";
    const mid = route[Math.floor(route.length / 2)];

    return svg`
      <g class="modless-edge glsp-edge" data-id="${edge.id}" data-element-id="${edge.id}">
        <path d="${path}" fill="none" stroke="${stroke}" stroke-width="1.5" />
        <polygon points="${arrowHeadPoints(route)}" fill="${stroke}" stroke="none" />
        ${label ? svg`<text x="${mid.x}" y="${mid.y - 6}" style="fill: var(--text-secondary, #94a3b8); font-size: 10px;">${label.text || kind}</text>` : ""}
      </g>
    `;
  }
}
