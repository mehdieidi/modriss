import { cssVar } from "./g6-style.js";
import {
  PLACEHOLDER_ICON,
  computeIconNodeLayout,
  iconAnchorBoundsLocal,
  measureIconNodeSize,
  nodeHitPathLocal,
  resolveIconSource,
} from "./icon-node-metrics.js";

export * from "./icon-node-metrics.js";

const FONT_UI = "var(--font-ui, 'Segoe UI', sans-serif)";
const FONT_DISPLAY = "var(--font-display, 'Segoe UI', sans-serif)";

function formatKindText(text) {
  return String(text || "Element")
    .trim()
    .toUpperCase();
}

function nodeStroke(flags, selected, border) {
  if (flags.connectLegal) {
    return "#16a34a";
  }
  if (flags.connectSource || selected) {
    return cssVar("--accent-select", "#5ecbff");
  }
  if (flags.impact) {
    return "#f97316";
  }
  return border;
}

function showNodeOutline(flags, selected, draft) {
  return selected || flags.connectLegal || flags.connectSource || flags.impact || draft;
}

function appendG6WrappedText(shape, container, key, lines, options = {}) {
  const {
    x,
    y,
    fill,
    fontSize = 10,
    fontWeight = 600,
    fontFamily = FONT_UI,
    lineHeight = 13,
    textAlign = "center",
  } = options;
  const value = Array.isArray(lines) ? lines.filter(Boolean) : [];
  if (!value.length) {
    shape.upsert(key, "text", false, container);
    return;
  }
  shape.upsert(
    key,
    "text",
    {
      x,
      y,
      text: value.join("\n"),
      fontFamily,
      fontSize,
      fontWeight,
      lineHeight,
      fill,
      textAlign,
      textBaseline: "top",
      pointerEvents: "none",
    },
    container,
  );
}

/**
 * Render an icon-centric node in G6 (shape.upsert API).
 */
export function renderIconCentricNodeG6(shape, container, options = {}) {
  const {
    width,
    height,
    low = false,
    selected = false,
    iconSrc = "",
    labelText = "",
    kindText = "",
    warm = false,
    container: isContainer = false,
    accent = cssVar("--accent", "#00a6e0"),
    flags = {},
    openControlHover = false,
    draft = false,
    dimmed = false,
    connectIllegal = false,
  } = options;

  const hovered = Boolean(flags.hovered);
  const layout = computeIconNodeLayout(width, height, low, labelText);
  const anchor = iconAnchorBoundsLocal(width, height, low, labelText);
  const border = warm ? "rgba(21, 28, 40, 0.35)" : cssVar("--node-border", "#3d495f");
  const stroke = nodeStroke(flags, selected, border);
  const outlineVisible = showNodeOutline(flags, selected, draft);
  const resolvedIcon = resolveIconSource(iconSrc) || PLACEHOLDER_ICON;
  const kindFill = warm ? "rgba(35, 28, 18, 0.72)" : accent;
  const nameFill = warm ? "rgba(24, 20, 14, 0.92)" : cssVar("--text", "#e3e8f2");

  const measured = measureIconNodeSize(labelText, { width, low });
  const hitPath = nodeHitPathLocal(width, height, low, labelText, isContainer, kindText);

  shape.upsert(
    "key",
    "path",
    {
      d: hitPath,
      fill: "transparent",
      stroke: "transparent",
      lineWidth: 0,
      opacity: dimmed || connectIllegal ? 0.48 : 1,
      cursor: "pointer",
    },
    container,
  );

  shape.upsert(
    "iconOutline",
    "rect",
    {
      x: anchor.left,
      y: anchor.top,
      width: anchor.width,
      height: anchor.height,
      radius: 3,
      fill: "transparent",
      stroke: outlineVisible ? stroke : "transparent",
      lineWidth: selected || flags.connectLegal || flags.connectSource || draft ? 2 : 1,
      lineDash: draft ? [5, 3] : undefined,
      shadowColor: selected || flags.connectLegal ? "rgba(8, 14, 24, 0.28)" : "transparent",
      shadowBlur: selected ? 8 : 0,
      pointerEvents: "none",
    },
    container,
  );

  shape.upsert("iconAnchor", "rect", false, container);

  shape.upsert("header", "rect", false, container);
  shape.upsert("semanticShape", "path", false, container);
  shape.upsert("notation", "text", false, container);
  shape.upsert("dot", "circle", false, container);
  shape.upsert("iconTile", "rect", false, container);
  shape.upsert("iconSky", "circle", false, container);
  shape.upsert("iconMark", "path", false, container);
  shape.upsert("labelHolder", "rect", false, container);

  shape.upsert(
    "nodeIcon",
    "image",
    {
      x: layout.iconX,
      y: layout.iconY,
      width: layout.iconSize,
      height: layout.iconSize,
      src: resolvedIcon,
      opacity: dimmed ? 0.55 : 1,
      pointerEvents: "none",
    },
    container,
  );

  shape.upsert(
    "kind",
    "text",
    {
      x: layout.centerX,
      y: layout.kindY,
      text: formatKindText(kindText),
      fontFamily: FONT_DISPLAY,
      fontSize: low ? 7 : 7.5,
      fontWeight: 800,
      letterSpacing: 0.8,
      fill: kindFill,
      textAlign: "center",
      textBaseline: "top",
      pointerEvents: "none",
    },
    container,
  );

  appendG6WrappedText(shape, container, "label", layout.nameLines, {
    x: layout.centerX,
    y: layout.nameY,
    fill: nameFill,
    fontSize: low ? 9 : 10,
    fontWeight: 600,
    lineHeight: layout.nameLineHeight,
    textAlign: "center",
  });

  const showOpen = isContainer && (hovered || openControlHover);
  if (showOpen) {
    const controlWidth = low ? 30 : 36;
    const controlHeight = low ? 14 : 15;
    const x = layout.centerX - controlWidth / 2;
    const y = layout.iconY - controlHeight - 4;
    shape.upsert(
      "openControl",
      "rect",
      {
        x,
        y: openControlHover ? y - 1 : y,
        width: controlWidth,
        height: openControlHover ? controlHeight + 2 : controlHeight,
        radius: 2,
        fill: openControlHover
          ? cssVar("--accent-glow", "rgba(94, 203, 255, 0.22)")
          : cssVar("--surface-elevated", "#ffffff"),
        stroke:
          openControlHover || selected
            ? cssVar("--accent-select", "#0ea5e9")
            : cssVar("--border-strong", "#94a3b8"),
        lineWidth: openControlHover || selected ? 1.5 : 1,
        shadowColor: "rgba(15, 23, 42, 0.18)",
        shadowBlur: 4,
        cursor: "pointer",
      },
      container,
    );
    shape.upsert(
      "openControlText",
      "text",
      {
        x: x + controlWidth / 2,
        y: y + controlHeight / 2 + 0.5,
        text: "OPEN",
        fontFamily: FONT_DISPLAY,
        fontSize: low ? 6.5 : 7,
        fontWeight: 800,
        fill: cssVar("--text-strong", "#0f172a"),
        textAlign: "center",
        textBaseline: "middle",
        cursor: "pointer",
      },
      container,
    );
  } else {
    shape.upsert("openControl", "rect", false, container);
    shape.upsert("openControlText", "text", false, container);
  }

  shape.upsert("placeholderIcon", "image", false, container);
  shape.upsert("icon", "rect", false, container);
  shape.upsert("type", "text", false, container);

  return { layout, anchor };
}
