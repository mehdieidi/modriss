import { cssVar } from "./g6-style.js";
import { nodeTypeLabel } from "./g6-mapper.js";
import { modelingElementDefinition } from "../modeling-config-data.js";
import { state } from "../state.js";
import {
  ICON_GAP,
  measureIconNodeSize,
  PLACEHOLDER_ICON,
  resolveIconSource,
} from "./icon-node-layout.js";
import { escapeXml, finiteNumber, safeSvgValue } from "./glsp-shapes.js";

const FONT_UI = "var(--font-ui, 'Segoe UI', sans-serif)";
const FONT_DISPLAY = "var(--font-display, 'Segoe UI', sans-serif)";

function formatKindText(text) {
  return String(text || "Element")
    .trim()
    .toUpperCase();
}

function svgEl(tag, attrs = {}) {
  const el = document.createElementNS("http://www.w3.org/2000/svg", tag);
  for (const [key, value] of Object.entries(attrs)) {
    const safe = safeSvgValue(key, value);
    if (safe !== null) {
      el.setAttribute(key, String(safe));
    }
  }
  return el;
}

function textEl(x, y, value, options = {}) {
  const {
    fill,
    fontSize = 12,
    fontWeight = 400,
    textAnchor = "start",
    dominantBaseline = "auto",
    fontFamily = FONT_UI,
    letterSpacing,
  } = options;
  const text = String(value ?? "");
  if (!text.trim()) {
    return svgEl("text");
  }
  const px = finiteNumber(x);
  const py = finiteNumber(y);
  const attrs = {
    x: px,
    y: py,
    fill,
    "font-size": fontSize,
    "font-weight": fontWeight,
    "text-anchor": textAnchor,
    "dominant-baseline": dominantBaseline,
    "font-family": fontFamily,
  };
  if (letterSpacing !== undefined) {
    attrs["letter-spacing"] = letterSpacing;
  }
  const el = svgEl("text", attrs);
  el.textContent = escapeXml(text);
  return el;
}

function appendWrappedText(g, x, y, lines, options = {}) {
  const {
    fill,
    fontSize = 10,
    fontWeight = 600,
    lineHeight = 13,
    textAnchor = "middle",
    fontFamily = FONT_UI,
    className = "",
  } = options;
  const value = Array.isArray(lines) ? lines.filter((line) => String(line || "").trim()) : [];
  if (!value.length) {
    return;
  }
  const el = svgEl("text", {
    x: finiteNumber(x),
    y: finiteNumber(y),
    fill,
    "font-size": fontSize,
    "font-weight": fontWeight,
    "text-anchor": textAnchor,
    "dominant-baseline": "hanging",
    "font-family": fontFamily,
    class: className,
  });
  value.forEach((line, index) => {
    const tspan = svgEl("tspan", {
      x: finiteNumber(x),
      dy: index === 0 ? 0 : lineHeight,
    });
    tspan.textContent = escapeXml(line);
    el.appendChild(tspan);
  });
  g.appendChild(el);
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

function appendOpenControl(
  g,
  { iconLeft, iconTop, iconSize, selected, low, openControlHover = false },
) {
  const controlWidth = low ? 30 : 36;
  const controlHeight = low ? 14 : 15;
  const x = iconLeft + iconSize / 2 - controlWidth / 2;
  const y = iconTop - controlHeight - 4;
  g.appendChild(
    svgEl("rect", {
      x,
      y: openControlHover ? y - 1 : y,
      width: controlWidth,
      height: openControlHover ? controlHeight + 2 : controlHeight,
      rx: 2,
      fill: openControlHover
        ? cssVar("--accent-glow", "rgba(94, 203, 255, 0.22)")
        : cssVar("--surface-elevated", "#ffffff"),
      stroke:
        openControlHover || selected
          ? cssVar("--accent-select", "#0ea5e9")
          : cssVar("--border-strong", "#94a3b8"),
      "stroke-width": openControlHover || selected ? 1.4 : 1,
      class: "glsp-open-control",
      cursor: "pointer",
    }),
  );
  g.appendChild(
    textEl(x + controlWidth / 2, y + controlHeight / 2, "OPEN", {
      fill: cssVar("--text-strong", "#0f172a"),
      fontSize: low ? 6.5 : 7,
      fontWeight: 800,
      textAnchor: "middle",
      dominantBaseline: "middle",
      fontFamily: FONT_DISPLAY,
    }),
  );
}

function renderIconCentricNode(g, node, attrs, flags) {
  const { width, height, low, selected, container, hovered, draft = false } = attrs;
  const warm = Boolean(attrs.sticky);
  const border = warm ? "rgba(21, 28, 40, 0.35)" : cssVar("--node-border", "#3d495f");
  const stroke = nodeStroke(flags, selected, border);
  const labelText = node.label || "";
  const measured = measureIconNodeSize(labelText, { width, low });
  const outlineVisible = showNodeOutline(flags, selected, draft);
  const resolvedIcon = resolveIconSource(attrs.iconSrc) || PLACEHOLDER_ICON;
  const accent = attrs.accent || cssVar("--accent", "#00a6e0");
  const kindFill = warm ? "rgba(35, 28, 18, 0.72)" : accent;
  const nameFill = warm ? "rgba(24, 20, 14, 0.92)" : cssVar("--text", "#e3e8f2");
  const iconLeft = (width - measured.iconSize) / 2;
  const iconTop = measured.padTop;
  const anchorLeft = iconLeft - ICON_GAP;
  const anchorTop = iconTop - ICON_GAP;
  const anchorSize = measured.iconSize + ICON_GAP * 2;

  g.appendChild(
    svgEl("rect", {
      x: 0,
      y: 0,
      width,
      height,
      fill: "transparent",
      stroke: "transparent",
      class: "modless-node-body",
      opacity: flags.dimmed ? 0.48 : 1,
      pointerEvents: "none",
    }),
  );

  g.appendChild(
    svgEl("rect", {
      x: anchorLeft,
      y: anchorTop,
      width: anchorSize,
      height: anchorSize,
      rx: 3,
      fill: "transparent",
      stroke: outlineVisible ? stroke : "transparent",
      "stroke-width": selected || flags.connectLegal || flags.connectSource ? 2 : 1,
      "stroke-dasharray": draft ? "5 3" : undefined,
      class: "modless-node-icon-anchor",
      filter:
        selected || flags.connectLegal
          ? "drop-shadow(0 4px 10px rgba(8, 14, 24, 0.24))"
          : undefined,
    }),
  );

  g.appendChild(
    svgEl("image", {
      href: resolvedIcon,
      x: iconLeft,
      y: iconTop,
      width: measured.iconSize,
      height: measured.iconSize,
      opacity: flags.dimmed ? 0.55 : 1,
      class: "modless-node-icon",
    }),
  );

  const kindY = iconTop + measured.iconSize + measured.gapAfterIcon;
  g.appendChild(
    textEl(width / 2, kindY, formatKindText(attrs.kindText), {
      fill: kindFill,
      fontSize: low ? 7 : 7.5,
      fontWeight: 800,
      textAnchor: "middle",
      dominantBaseline: "hanging",
      fontFamily: FONT_DISPLAY,
      letterSpacing: 0.8,
    }),
  );

  const nameY = kindY + measured.kindLineHeight + measured.gapKindName;
  appendWrappedText(g, width / 2, nameY, measured.nameLines, {
    fill: nameFill,
    fontSize: low ? 9 : 10,
    fontWeight: 600,
    lineHeight: measured.nameLineHeight,
    className: "modless-node-name",
  });

  if (container && hovered) {
    appendOpenControl(g, { iconLeft, iconTop, iconSize: measured.iconSize, selected, low });
  }
}

export function createModlessNodeElement(node, options = {}) {
  const attrs = node.g6 || node.args || {};
  const labelText = node.label || attrs.labelText || node.id || "";
  const low = attrs.detailLevel === "low" || options.detailLevel === "low";
  const configuredWidth = finiteNumber(node.width, 120);
  const measured = measureIconNodeSize(labelText, { width: configuredWidth, low });
  const width = measured.width;
  const height = measured.height;
  const selected = Boolean(options.selected);
  const hovered = Boolean(options.hovered);
  const flags = {
    selected,
    hovered,
    impact: Boolean(options.impact),
    connectLegal: Boolean(options.connectLegal),
    connectSource: Boolean(options.connectSource),
    dimmed: Boolean(options.dimmed),
  };

  const g = svgEl("g", {
    class: `modless-node glsp-node${selected ? " is-selected" : ""}${hovered ? " is-hovered" : ""}${options.validation?.length ? " has-validation" : ""}${options.impact ? " has-impact" : ""}`,
    "data-id": node.id,
    "data-type": node.type,
    transform: `translate(${finiteNumber(node.x)}, ${finiteNumber(node.y)})`,
  });

  const renderAttrs = {
    width,
    height,
    accent: attrs.accent || attrs.color || cssVar("--accent", "#00a6e0"),
    sticky: attrs.sticky,
    iconSrc: attrs.iconSrc,
    kindText:
      attrs.kindText ||
      nodeTypeLabel(
        modelingElementDefinition(state.activeType, attrs.elementType || node.type),
        attrs.elementType || node.type,
      ),
    container: Boolean(attrs.container || attrs.visualRole === "container"),
    low,
    selected,
    hovered,
  };

  renderIconCentricNode(g, { ...node, label: labelText }, renderAttrs, flags);

  if (options.validation?.length) {
    g.appendChild(
      svgEl("circle", {
        cx: width - 8,
        cy: 8,
        r: 5,
        fill: cssVar("--danger", "#dc2626"),
      }),
    );
  }

  if (options.impact) {
    g.appendChild(
      svgEl("rect", {
        x: -4,
        y: -4,
        width: width + 8,
        height: height + 8,
        rx: 4,
        fill: "none",
        stroke: cssVar("--warning", "#f59e0b"),
        "stroke-width": 2,
        opacity: 0.85,
      }),
    );
  }

  return g;
}

export function setNodeTransform(nodeEl, x, y) {
  if (!nodeEl) {
    return;
  }
  nodeEl.setAttribute("transform", `translate(${finiteNumber(x)} ${finiteNumber(y)})`);
}
