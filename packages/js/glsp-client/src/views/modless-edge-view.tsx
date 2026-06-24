import { GEdgeView } from "@eclipse-glsp/client";
import type { GEdge } from "@eclipse-glsp/client";
import { Hoverable, Point, RenderingContext, Selectable, svg } from "@eclipse-glsp/sprotty";
import { injectable } from "inversify";
import { VNode } from "snabbdom";

function arrowHeadPoints(route: Point[]): string {
  const last = route[route.length - 1];
  const prev = route[route.length - 2] || last;
  const dx = last.x - prev.x;
  const dy = last.y - prev.y;
  const length = Math.hypot(dx, dy) || 1;
  const ux = dx / length;
  const uy = dy / length;
  const size = 9;
  const leftX = last.x - ux * size - uy * size * 0.42;
  const leftY = last.y - uy * size + ux * size * 0.42;
  const rightX = last.x - ux * size + uy * size * 0.42;
  const rightY = last.y - uy * size - ux * size * 0.42;
  return `${last.x},${last.y} ${leftX},${leftY} ${rightX},${rightY}`;
}

function pathFromRoute(route: Point[]): string {
  if (route.length < 2) {
    return "";
  }
  if (route.length === 2) {
    const [start, end] = route;
    const midY = (start.y + end.y) / 2;
    return `M ${start.x} ${start.y} C ${start.x} ${midY}, ${end.x} ${midY}, ${end.x} ${end.y}`;
  }
  return route.map((point, index) => `${index === 0 ? "M" : "L"} ${point.x} ${point.y}`).join(" ");
}

@injectable()
export class ModlessEdgeView extends GEdgeView {
  override isVisible(
    _edge: Readonly<GEdge & Hoverable & Selectable>,
    _route: Point[],
    _context: RenderingContext,
  ): boolean {
    return true;
  }

  override render(
    edge: Readonly<GEdge & Hoverable & Selectable>,
    context: RenderingContext,
  ): VNode | undefined {
    if (!context || context.targetKind === "hidden") {
      return undefined;
    }

    const router = this.edgeRouterRegistry.get(edge.routerKind);
    const route = router.route(edge);
    if (route.length < 2) {
      return this.renderDanglingEdge("Cannot compute route", edge, context);
    }

    const path = pathFromRoute(route);
    const args = (edge as unknown as { args?: Record<string, unknown> }).args || {};
    const kind = String(args.kind || "");
    const shortcut = Boolean(args.shortcut);
    const selected = Boolean(edge.selected);
    const hovered = Boolean(edge.hoverFeedback);
    const label = (edge.children || []).find((child) => child.type === "label") as
      | { text?: string }
      | undefined;
    const stroke = String(
      args.stroke || (kind.includes("TRACE") ? "#64748b" : "var(--edge-stroke, #64748b)"),
    );
    const lineDash = Array.isArray(args.lineDash)
      ? args.lineDash.join(",")
      : shortcut || kind === "TRACE"
        ? "5,4"
        : "";
    const mid = route[Math.floor(route.length / 2)];
    const strokeWidth = selected || hovered ? 2.2 : 1.5;
    const extraClass = String(args.className || "").trim();

    return (
      <g
        class={{
          "modless-edge": true,
          "glsp-edge": true,
          "sprotty-edge": true,
          "is-shortcut": shortcut,
          "is-selected": selected,
          "is-hovered": hovered,
          ...(extraClass ? { [extraClass]: true } : {}),
        }}
        class-sprotty-edge={true}
        class-selected={selected}
        class-mouseover={hovered}
      >
        <path
          d={path}
          class-mouse-handle={true}
          style={{
            fill: "none",
            stroke: "transparent",
            strokeWidth: 12,
            strokeLinecap: "round",
            strokeLinejoin: "round",
          }}
        />
        <path
          d={path}
          class-modless-edge-path={true}
          style={{
            fill: "none",
            stroke,
            strokeWidth,
            strokeDasharray: lineDash || undefined,
            strokeLinecap: "round",
            strokeLinejoin: "round",
          }}
        />
        <polygon
          points={arrowHeadPoints(route)}
          class-modless-edge-arrow={true}
          style={{ fill: stroke, stroke: "none" }}
        />
        {label?.text ? (
          <text
            x={mid.x}
            y={mid.y - 8}
            class-modless-edge-label={true}
            style={{
              fill: "var(--text-secondary, #94a3b8)",
              fontSize: "10px",
              fontFamily: "var(--font-ui)",
            }}
          >
            {label.text}
          </text>
        ) : undefined}
      </g>
    );
  }
}
