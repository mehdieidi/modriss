import {
  GNode,
  Hoverable,
  RenderingContext,
  Selectable,
  ShapeView,
  svg,
} from "@eclipse-glsp/sprotty";
import { injectable } from "inversify";
import { VNode } from "snabbdom";
import {
  boundedText,
  lineBreak,
  lineCount,
  truncate,
} from "./modless-text.js";

function notationGlyphPath(
  geometry: string,
  left: number,
  top: number,
  width: number,
  height: number,
): string {
  const name = String(geometry || "rectangle").toLowerCase();
  const right = left + width;
  const bottom = top + height;
  const midX = left + width / 2;
  const midY = top + height / 2;
  const pointsToD = (points: Array<[number, number]>) =>
    points.map(([x, y], index) => `${index === 0 ? "M" : "L"}${x} ${y}`).join(" ") + " Z";

  if (name === "diamond") {
    return pointsToD([
      [midX, top],
      [right, midY],
      [midX, bottom],
      [left, midY],
    ]);
  }
  if (name === "hexagon") {
    const inset = width * 0.24;
    return pointsToD([
      [left + inset, top],
      [right - inset, top],
      [right, midY],
      [right - inset, bottom],
      [left + inset, bottom],
      [left, midY],
    ]);
  }
  if (name === "ellipse") {
    return `M ${midX} ${top} A ${width / 2} ${height / 2} 0 1 1 ${midX} ${bottom} A ${width / 2} ${height / 2} 0 1 1 ${midX} ${top} Z`;
  }
  return "";
}

function textLines(
  x: number,
  y: number,
  text: string,
  style: Record<string, string | number | undefined>,
  lineHeight: number,
): VNode {
  const lines = String(text || "")
    .split("\n")
    .filter(Boolean);
  if (!lines.length) {
    return <text />;
  }
  return (
    <text
      x={x}
      y={y}
      style={style}
    >
      {lines.map((line, index) => (
        <tspan x={x} dy={index === 0 ? 0 : lineHeight}>
          {line}
        </tspan>
      ))}
    </text>
  );
}

@injectable()
export class ModlessNodeView extends ShapeView {
  override isVisible(
    _node: Readonly<GNode & Hoverable & Selectable>,
    _context: RenderingContext,
  ): boolean {
    return true;
  }

  override render(
    node: Readonly<GNode & Hoverable & Selectable>,
    context: RenderingContext,
  ): VNode | undefined {
    if (!context || context.targetKind === "hidden") {
      return undefined;
    }

    const args = (node as unknown as { args?: Record<string, unknown> }).args || {};
    const accent = String(args.color || "var(--accent, #00a6e0)");
    const width = node.size?.width || 228;
    const height = node.size?.height || 112;
    const low = args.lod === "minimal" || args.lod === "summary";
    const showBadges = args.showBadges !== false && !low;
    const showDetail = args.showDetailLines !== false && !low;
    const geometry = String(args.geometry || args.sprottyShape || "rectangle");
    const labels = (node.children || []).filter((child) => child.type === "label");
    const title = String((labels[0] as { text?: string } | undefined)?.text || node.id);
    const lines = String(
      args.detailText || (labels[1] as { text?: string } | undefined)?.text || "",
    );
    const badges = showBadges && Array.isArray(args.badges) ? args.badges.slice(0, 4) : [];
    const selected = Boolean(node.selected);
    const hovered = Boolean(node.hoverFeedback);
    const hasValidation = Boolean(args.hasValidation);
    const hasImpact = Boolean(args.hasImpact);
    const headerHeight = low ? 0 : 32;
    const tagStripHeight = low ? 0 : 24;
    const glyphPath = low ? "" : notationGlyphPath(geometry, 10, 9, 20, 18);
    const kindText = String(args.kindText || args.category || args.tag || "Element");
    const bodyStroke = selected
      ? "var(--accent-select, #5ecbff)"
      : hasImpact
        ? "#f97316"
        : hasValidation
          ? "var(--danger, #dc2626)"
          : "var(--node-border, #3d495f)";
    const titleText = lineBreak(title, low ? 28 : 30, low ? 1 : 2);
    const titleLineHeight = low ? 12.5 : 14;
    const titleY = low ? 18 : 42;
    const detailY = titleY + lineCount(titleText) * titleLineHeight + 4;
    const clipId = `modless-clip-${node.id}`;

    return (
      <g
        class={{
          "modless-node": true,
          "glsp-node": true,
          "sprotty-node": true,
          node: true,
          "is-selected": selected,
          "is-hovered": hovered,
          "has-validation": hasValidation,
          "has-impact": hasImpact,
        }}
        class-node={true}
        class-selected={selected}
        class-mouseover={hovered}
      >
        <defs>
          <clipPath id={clipId}>
            <rect x={0} y={0} width={width} height={height} rx={2} ry={2} />
          </clipPath>
        </defs>
        <rect
          x={0}
          y={0}
          width={width}
          height={height}
          rx={2}
          ry={2}
          class={{
            "modless-node-body": true,
            "glsp-node-body": true,
            "sprotty-node": true,
          }}
          class-sprotty-node={true}
          class-selected={selected}
          class-mouseover={hovered}
          style={{
            fill: "var(--node-bg, #131923)",
            stroke: bodyStroke,
            strokeWidth: selected || hasValidation || hasImpact ? 2.4 : 1,
            filter:
              selected || hovered
                ? "drop-shadow(0 4px 14px rgba(8, 14, 24, 0.44))"
                : undefined,
          }}
        />
        <g attrs-clip-path={`url(#${clipId})`}>
          {!low ? (
            <rect
              x={0}
              y={0}
              width={width}
              height={headerHeight}
              rx={2}
              class-modless-node-header={true}
              style={{ fill: "rgba(35, 42, 55, 0.58)" }}
            />
          ) : undefined}
          {glyphPath ? (
            <path
              d={glyphPath}
              class-modless-node-glyph={true}
              style={{ fill: "rgba(255,255,255,0.04)", stroke: accent, strokeWidth: 1.6 }}
            />
          ) : undefined}
          {!low
            ? textLines(
                40,
                12,
                boundedText(kindText, 18, 2),
                {
                  fill: accent,
                  fontSize: "8px",
                  fontWeight: 800,
                  fontFamily: "var(--font-display)",
                  letterSpacing: "0.06em",
                },
                9,
              )
            : undefined}
          {textLines(
            11,
            titleY,
            titleText,
            {
              fill: "var(--text, #e3e8f2)",
              fontSize: low ? "10.5px" : "12px",
              fontWeight: 700,
              fontFamily: "var(--font-ui)",
            },
            titleLineHeight,
          )}
          {showDetail && lines
            ? textLines(
                11,
                detailY,
                truncate(lines, 42),
                {
                  fill: "var(--text-secondary, #98a8c0)",
                  fontSize: "9.5px",
                  fontWeight: 500,
                  fontFamily: "var(--font-ui)",
                },
                11,
              )
            : undefined}
          {badges.map((badge, index) => {
            const label = truncate(String(badge), 12).toUpperCase();
            const x = 10 + index * 58;
            return (
              <g class-modless-node-badge={true} transform={`translate(${x}, ${height - tagStripHeight - 2})`}>
                <rect
                  width={52}
                  height={13}
                  rx={3}
                  style={{ fill: "rgba(71, 85, 105, 0.82)", stroke: "rgba(148, 163, 184, 0.14)" }}
                />
                <text
                  x={26}
                  y={9}
                  attrs-text-anchor="middle"
                  style={{
                    fill: "rgba(248,250,252,0.96)",
                    fontSize: "6.8px",
                    fontWeight: 800,
                    fontFamily: "var(--font-ui)",
                  }}
                >
                  {label}
                </text>
              </g>
            );
          })}
        </g>
      </g>
    );
  }
}
