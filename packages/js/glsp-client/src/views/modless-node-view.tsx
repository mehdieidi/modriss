import {
  GNode,
  Hoverable,
  RenderingContext,
  Selectable,
  ShapeView,
} from "@eclipse-glsp/sprotty";
import { injectable } from "inversify";
import { VNode } from "snabbdom";

const ICON_GAP = 2;
const ICON_SIZE_NORMAL = 72;
const ICON_SIZE_LOW = 44;
const ICON_NODE_WIDTH = 120;
const PLACEHOLDER_ICON = "/assets/icons/placeholder.svg";

function resolveIconSource(icon: unknown): string {
  const normalized = String(icon || "").trim();
  if (!normalized) {
    return "";
  }
  if (normalized.startsWith("/") || normalized.startsWith(".") || normalized.endsWith(".svg")) {
    return normalized;
  }
  if (/^[a-z0-9_-]+$/i.test(normalized)) {
    return `/assets/icons/${normalized}.svg`;
  }
  return "";
}

function wrapLabelLines(text: string, maxCharsPerLine = 17): string[] {
  const value = String(text || "").trim();
  if (!value) {
    return [];
  }
  const lines: string[] = [];
  let current = "";
  const flush = () => {
    if (current) {
      lines.push(current);
      current = "";
    }
  };
  value.split(/\s+/).forEach((word) => {
    if (!word) {
      return;
    }
    if (word.length > maxCharsPerLine) {
      flush();
      for (let index = 0; index < word.length; index += maxCharsPerLine) {
        lines.push(word.slice(index, index + maxCharsPerLine));
      }
      return;
    }
    const next = current ? `${current} ${word}` : word;
    if (next.length > maxCharsPerLine && current) {
      flush();
      current = word;
    } else {
      current = next;
    }
  });
  flush();
  return lines.length ? lines : [""];
}

function measureIconNodeSize(labelText: string, low: boolean, width = ICON_NODE_WIDTH) {
  const iconSize = low ? ICON_SIZE_LOW : ICON_SIZE_NORMAL;
  const padTop = low ? 2 : 4;
  const gapAfterIcon = low ? 4 : 6;
  const kindLineHeight = low ? 10 : 11;
  const nameLineHeight = low ? 12 : 13;
  const gapKindName = 2;
  const padBottom = 4;
  const maxChars = Math.max(10, Math.floor(width / (low ? 5.8 : 6.2)));
  const nameLines = wrapLabelLines(labelText, maxChars);
  const nameHeight = Math.max(nameLineHeight, nameLines.length * nameLineHeight);
  const height =
    padTop + iconSize + gapAfterIcon + kindLineHeight + gapKindName + nameHeight + padBottom;
  return {
    width,
    height,
    iconSize,
    padTop,
    gapAfterIcon,
    kindLineHeight,
    nameLineHeight,
    gapKindName,
    nameLines,
  };
}

function humanizeType(value: string): string {
  return String(value || "Element")
    .replaceAll("_", " ")
    .replaceAll(/([a-z])([A-Z])/g, "$1 $2")
    .replace(/\s+/g, " ")
    .trim();
}

function formatKindText(text: string): string {
  return String(text || "Element")
    .trim()
    .toUpperCase();
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
    const labels = (node.children || []).filter((child) => child.type === "label");
    const title = String((labels[0] as { text?: string } | undefined)?.text || node.id);
    const low = args.lod === "minimal" || args.lod === "summary";
    const measured = measureIconNodeSize(title, low);
    const width = node.size?.width || measured.width;
    const height = node.size?.height || measured.height;
    const kindText = String(
      args.kindText ||
        args.displayName ||
        args.label ||
        humanizeType(String(args.elementType || "Element")),
    );
    const accent = String(args.color || "var(--accent, #00a6e0)");
    const selected = Boolean(node.selected);
    const hovered = Boolean(node.hoverFeedback);
    const hasValidation = Boolean(args.hasValidation);
    const hasImpact = Boolean(args.hasImpact);
    const isContainer = String(args.visualRole || "") === "container";
    const iconSrc = resolveIconSource(args.iconSrc || args.icon) || PLACEHOLDER_ICON;
    const iconLeft = (width - measured.iconSize) / 2;
    const iconTop = measured.padTop;
    const anchorLeft = iconLeft - ICON_GAP;
    const anchorTop = iconTop - ICON_GAP;
    const anchorSize = measured.iconSize + ICON_GAP * 2;
    const kindY = iconTop + measured.iconSize + measured.gapAfterIcon;
    const nameY = kindY + measured.kindLineHeight + measured.gapKindName;

    const showOutline = selected || hasValidation || hasImpact;

    const bodyStroke = hasImpact
      ? "#f97316"
      : hasValidation
        ? "var(--danger, #dc2626)"
        : selected
          ? "var(--accent-select, #5ecbff)"
          : "var(--node-border, #3d495f)";

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
        <rect
          x={0}
          y={0}
          width={width}
          height={height}
          class={{
            "modless-node-body": true,
            "glsp-node-body": true,
            "sprotty-node": true,
          }}
          class-sprotty-node={true}
          style={{
            fill: "transparent",
            stroke: "transparent",
          }}
        />
        <rect
          x={anchorLeft}
          y={anchorTop}
          width={anchorSize}
          height={anchorSize}
          rx={3}
          class-modless-node-icon-anchor={true}
          style={{
            fill: "transparent",
            stroke: showOutline ? bodyStroke : "transparent",
            strokeWidth: selected || hasValidation || hasImpact ? 2 : 1,
            filter: selected ? "drop-shadow(0 4px 10px rgba(8, 14, 24, 0.24))" : undefined,
          }}
        />
        <image
          attrs-href={iconSrc}
          x={iconLeft}
          y={iconTop}
          width={measured.iconSize}
          height={measured.iconSize}
          class-modless-node-icon={true}
        />
        <text
          x={width / 2}
          y={kindY}
          attrs-text-anchor="middle"
          attrs-dominant-baseline="hanging"
          class-modless-node-kind={true}
          style={{
            fill: accent,
            fontSize: low ? "7px" : "7.5px",
            fontWeight: 800,
            fontFamily: "var(--font-display)",
            letterSpacing: "0.08em",
          }}
        >
          {formatKindText(kindText)}
        </text>
        <text
          x={width / 2}
          y={nameY}
          attrs-text-anchor="middle"
          attrs-dominant-baseline="hanging"
          class-modless-node-name={true}
          style={{
            fill: "var(--text, #e3e8f2)",
            fontSize: low ? "9px" : "10px",
            fontWeight: 600,
            fontFamily: "var(--font-ui)",
          }}
        >
          {measured.nameLines.map((line, index) => (
            <tspan x={width / 2} dy={index === 0 ? 0 : measured.nameLineHeight}>
              {line}
            </tspan>
          ))}
        </text>
        {isContainer && hovered ? (
          <g class-modless-open-control={true}>
            <rect
              x={iconLeft + measured.iconSize / 2 - 18}
              y={iconTop - 19}
              width={36}
              height={15}
              rx={2}
              style={{
                fill: "var(--surface-elevated, #ffffff)",
                stroke: "var(--accent-select, #0ea5e9)",
                strokeWidth: 1,
              }}
            />
            <text
              x={iconLeft + measured.iconSize / 2}
              y={iconTop - 11.5}
              attrs-text-anchor="middle"
              attrs-dominant-baseline="middle"
              style={{
                fill: "var(--text-strong, #0f172a)",
                fontSize: "7px",
                fontWeight: 800,
                fontFamily: "var(--font-display)",
              }}
            >
              OPEN
            </text>
          </g>
        ) : undefined}
      </g>
    );
  }
}
