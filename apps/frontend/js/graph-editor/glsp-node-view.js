import { cssVar } from "./g6-style.js";
import { escapeXml, finiteNumber, safeSvgValue } from "./glsp-shapes.js";

const FONT_UI = "var(--font-ui, 'Segoe UI', sans-serif)";
const FONT_DISPLAY = "var(--font-display, 'Segoe UI', sans-serif)";

function truncate(text, max = 44) {
  const value = String(text || "");
  return value.length > max ? `${value.slice(0, Math.max(1, max - 1))}...` : value;
}

function lineBreak(text, maxLine = 23, maxLines = 2) {
  const value = String(text || "").trim();
  if (!value) {
    return "";
  }
  const words = value.split(/\s+/);
  const lines = [];
  let line = "";
  words.forEach((word) => {
    const next = line ? `${line} ${word}` : word;
    if (next.length > maxLine && line) {
      lines.push(line);
      line = word;
    } else {
      line = next;
    }
  });
  if (line) {
    lines.push(line);
  }
  return lines
    .slice(0, maxLines)
    .map((item, index) => {
      if (index === maxLines - 1 && lines.length > maxLines) {
        return truncate(item, maxLine - 1);
      }
      return truncate(item, maxLine);
    })
    .join("\n");
}

function lineCount(text) {
  const value = String(text || "");
  return value ? value.split("\n").length : 0;
}

function boundedText(text, maxLine, maxLines) {
  return lineBreak(text, maxLine, maxLines).toUpperCase();
}

function badgeFill() {
  return "rgba(71, 85, 105, 0.82)";
}

function badgeTextFill() {
  return "rgba(248, 250, 252, 0.96)";
}

export function notationGlyphPath(geometry, left, top, width, height) {
  const name = String(geometry || "rectangle").toLowerCase();
  const right = left + width;
  const bottom = top + height;
  const midX = left + width / 2;
  const midY = top + height / 2;
  const pointsToD = (points) =>
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
  if (name === "octagon") {
    const insetX = width * 0.2;
    const insetY = height * 0.2;
    return pointsToD([
      [left + insetX, top],
      [right - insetX, top],
      [right, top + insetY],
      [right, bottom - insetY],
      [right - insetX, bottom],
      [left + insetX, bottom],
      [left, bottom - insetY],
      [left, top + insetY],
    ]);
  }
  if (name === "trapezoid") {
    const inset = width * 0.18;
    return pointsToD([
      [left + inset, top],
      [right, top],
      [right - inset, bottom],
      [left, bottom],
    ]);
  }
  if (name === "ellipse") {
    return `M ${midX} ${top} A ${width / 2} ${height / 2} 0 1 1 ${midX} ${bottom} A ${width / 2} ${height / 2} 0 1 1 ${midX} ${top} Z`;
  }
  return "";
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
  } = options;
  const text = String(value ?? "");
  if (!text.trim()) {
    return svgEl("text");
  }
  const px = finiteNumber(x);
  const py = finiteNumber(y);
  const el = svgEl("text", {
    x: px,
    y: py,
    fill,
    "font-size": fontSize,
    "font-weight": fontWeight,
    "text-anchor": textAnchor,
    "dominant-baseline": dominantBaseline,
    "font-family": fontFamily,
  });
  const lines = text.split("\n");
  if (lines.length <= 1) {
    el.textContent = escapeXml(text);
    return el;
  }
  lines.forEach((line, index) => {
    const tspan = svgEl("tspan", {
      x: px,
      dy: index === 0 ? 0 : finiteNumber(fontSize * 1.15, 12),
    });
    tspan.textContent = escapeXml(line);
    el.appendChild(tspan);
  });
  return el;
}

function appendBadges(g, { badges, width, height, low, diagramType }) {
  const tags = Array.isArray(badges) ? badges.slice(0, 4) : [];
  let x = 10;
  let y = height - 21;
  const maxX = width - 10;
  const rowHeight = 13;
  tags.forEach((text) => {
    const value = String(text || "").trim();
    if (!value || low) {
      return;
    }
    const label = truncate(value, 12).toUpperCase();
    const tagWidth = Math.min(72, Math.max(28, 15 + label.length * 4.7));
    if (x + tagWidth > maxX && x > 10) {
      x = 10;
      y += rowHeight + 2;
    }
    if (y + rowHeight > height - 5) {
      return;
    }
    g.appendChild(
      svgEl("rect", {
        x,
        y,
        width: tagWidth,
        height: rowHeight,
        rx: 3,
        fill: badgeFill(value, diagramType),
        stroke: "rgba(148, 163, 184, 0.14)",
        "stroke-width": 1,
      }),
    );
    g.appendChild(
      textEl(x + tagWidth / 2, y + rowHeight / 2, label, {
        fill: badgeTextFill(diagramType),
        fontSize: 6.8,
        fontWeight: 800,
        textAnchor: "middle",
        dominantBaseline: "middle",
      }),
    );
    x += tagWidth + 4;
  });
}

function appendOpenControl(g, { width, selected, low }) {
  const controlWidth = low ? 30 : 38;
  const controlHeight = low ? 14 : 16;
  const x = width - controlWidth - 9;
  const y = low ? 8 : 7;
  g.appendChild(
    svgEl("rect", {
      x,
      y,
      width: controlWidth,
      height: controlHeight,
      rx: 2,
      fill: "rgba(226, 232, 240, 0.14)",
      stroke: selected ? cssVar("--accent-select", "#5ecbff") : "rgba(226, 232, 240, 0.22)",
      "stroke-width": selected ? 1.4 : 1,
      class: "glsp-open-control",
    }),
  );
  g.appendChild(
    textEl(x + controlWidth / 2, y + controlHeight / 2, "OPEN", {
      fill: cssVar("--text-strong", "#e3e8f2"),
      fontSize: low ? 6.5 : 7.5,
      fontWeight: 800,
      textAnchor: "middle",
      dominantBaseline: "middle",
      fontFamily: FONT_DISPLAY,
    }),
  );
}

function renderStickyNode(g, node, attrs, flags) {
  const { width, height, accent, low, selected, container } = attrs;
  const fill = attrs.sticky || "#fde68a";
  const headerHeight = low ? 0 : 32;
  const tagStripHeight = low ? 0 : 24;
  const glyph =
    low || attrs.iconSrc ? "" : notationGlyphPath(attrs.notationGeometry, 10, 7, 20, 18);

  g.appendChild(
    svgEl("rect", {
      x: 0,
      y: 0,
      width,
      height,
      rx: 2,
      class: "modless-node-body",
      fill,
      stroke: selected
        ? cssVar("--accent-select", "#5ecbff")
        : flags.connectLegal
          ? "#16a34a"
          : "rgba(21, 28, 40, 0.24)",
      "stroke-width": selected || flags.connectLegal ? 2.4 : 1,
      filter: selected ? "drop-shadow(0 4px 12px rgba(8, 14, 24, 0.36))" : undefined,
    }),
  );

  if (!low) {
    g.appendChild(
      svgEl("rect", {
        x: 0,
        y: 0,
        width,
        height: headerHeight,
        rx: 2,
        fill: "rgba(255,255,255,0.34)",
      }),
    );
  }

  if (glyph) {
    g.appendChild(
      svgEl("path", {
        d: glyph,
        fill: "rgba(255,255,255,0.2)",
        stroke: accent,
        "stroke-width": 1.6,
        class: "modless-node-glyph",
      }),
    );
  } else if (!low) {
    g.appendChild(
      svgEl("image", {
        href: attrs.iconSrc || "/assets/icons/placeholder.svg",
        x: 10,
        y: low ? 9 : 6,
        width: low ? 18 : 20,
        height: low ? 18 : 20,
        opacity: 0.9,
      }),
    );
  }

  if (!low) {
    g.appendChild(
      textEl(
        38,
        7,
        boundedText(attrs.kindText || attrs.typeText || "Element", container ? 13 : 18, 2),
        {
          fill: "rgba(35, 28, 18, 0.86)",
          fontSize: 8,
          fontWeight: 800,
          fontFamily: FONT_DISPLAY,
        },
      ),
    );
  }

  const labelText = lineBreak(node.label || "", low ? 20 : 24, low ? 1 : 2);
  const labelLineHeight = low ? 12 : 14;
  const labelY = low ? 24 : 39;
  g.appendChild(
    textEl(10, labelY, labelText, {
      fill: "rgba(24, 20, 14, 0.92)",
      fontSize: low ? 10 : 12,
      fontWeight: 700,
    }),
  );

  if (!low) {
    const idY = Math.min(
      height - tagStripHeight - 10,
      labelY + lineCount(labelText) * labelLineHeight + 8,
    );
    g.appendChild(
      textEl(10, idY, truncate(attrs.elementId || node.id, 30), {
        fill: "rgba(45, 38, 26, 0.58)",
        fontSize: 7.4,
        fontWeight: 700,
        dominantBaseline: "middle",
      }),
    );
  }

  appendBadges(g, { badges: attrs.badges, width, height, low, diagramType: attrs.diagramType });
  if (container) {
    appendOpenControl(g, { width, selected, low });
  }
}

function renderStandardNode(g, node, attrs, flags) {
  const { width, height, accent, low, selected, container } = attrs;
  const fill = cssVar("--node-bg", "#131923");
  const border = cssVar("--node-border", "#3d495f");
  const headerHeight = low ? 0 : 32;
  const tagStripHeight = low ? 0 : 24;
  const glyph =
    low || attrs.iconSrc ? "" : notationGlyphPath(attrs.notationGeometry, 10, 7, 20, 18);

  g.appendChild(
    svgEl("rect", {
      x: 0,
      y: 0,
      width,
      height,
      rx: 2,
      class: "modless-node-body",
      fill,
      stroke: flags.connectLegal
        ? "#16a34a"
        : flags.connectSource
          ? cssVar("--accent-select", "#5ecbff")
          : selected
            ? cssVar("--accent-select", "#5ecbff")
            : flags.impact
              ? "#f97316"
              : border,
      "stroke-width": selected || flags.connectLegal || flags.connectSource ? 2.4 : 1,
      filter:
        selected || flags.hovered ? "drop-shadow(0 4px 14px rgba(8, 14, 24, 0.44))" : undefined,
      opacity: flags.dimmed ? 0.48 : 1,
    }),
  );

  if (!low) {
    g.appendChild(
      svgEl("rect", {
        x: 0,
        y: 0,
        width,
        height: headerHeight,
        rx: 2,
        fill: "rgba(35,42,55,0.58)",
      }),
    );
  }

  if (glyph) {
    g.appendChild(
      svgEl("path", {
        d: glyph,
        fill: "rgba(255,255,255,0.04)",
        stroke: accent,
        "stroke-width": 1.6,
        class: "modless-node-glyph",
      }),
    );
  } else if (!low) {
    g.appendChild(
      svgEl("image", {
        href: attrs.iconSrc || "/assets/icons/placeholder.svg",
        x: 10,
        y: 6,
        width: 20,
        height: 20,
        opacity: 0.9,
      }),
    );
  }

  if (!low) {
    g.appendChild(
      textEl(
        40,
        7,
        boundedText(attrs.kindText || attrs.typeText || "Element", container ? 18 : 24, 2),
        {
          fill: accent,
          fontSize: 8.2,
          fontWeight: 800,
          fontFamily: FONT_DISPLAY,
        },
      ),
    );
  }

  const labelText = lineBreak(node.label || "", low ? 28 : 30, low ? 1 : 2);
  const labelLineHeight = low ? 12.5 : 14;
  const labelY = low ? 18 : 42;
  g.appendChild(
    textEl(11, labelY, labelText, {
      fill: cssVar("--text", "#e3e8f2"),
      fontSize: low ? 10.5 : 12,
      fontWeight: 700,
    }),
  );

  if (!low && attrs.detailText) {
    const detailY = labelY + lineCount(labelText) * labelLineHeight + 4;
    g.appendChild(
      textEl(11, detailY, truncate(attrs.detailText, 42), {
        fill: cssVar("--text-secondary", "#98a8c0"),
        fontSize: 9.5,
        fontWeight: 500,
      }),
    );
  }

  if (!low) {
    const idY = Math.min(
      height - tagStripHeight - 10,
      labelY + lineCount(labelText) * labelLineHeight + (attrs.detailText ? 22 : 8),
    );
    g.appendChild(
      textEl(11, idY, truncate(attrs.elementId || node.id, 36), {
        fill: "rgba(152, 168, 192, 0.72)",
        fontSize: 7.5,
        fontWeight: 700,
        dominantBaseline: "middle",
      }),
    );
  }

  appendBadges(g, { badges: attrs.badges, width, height, low, diagramType: attrs.diagramType });
  if (container) {
    appendOpenControl(g, { width, selected, low });
  }
}

function usesWarmCardLayout(attrs) {
  const shape = String(attrs.notationShape || "concept-card").toLowerCase();
  return shape === "concept-card" || shape === "sticky-note" || shape.endsWith("-card");
}

export function createModlessNodeElement(node, options = {}) {
  const attrs = node.g6 || node.args || {};
  const width = finiteNumber(node.width, 228);
  const height = finiteNumber(node.height, 112);
  const low = attrs.detailLevel === "low" || options.detailLevel === "low";
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
    kindText: attrs.kindText,
    typeText: attrs.tokenText || attrs.typeText,
    notationGeometry: attrs.notationGeometry || attrs.geometry || "rectangle",
    notationShape: attrs.notationShape,
    iconSrc: attrs.iconSrc,
    badges: attrs.badges || [],
    diagramType: attrs.diagramType || "",
    elementId: attrs.elementId || node.id,
    detailText: attrs.detailText || node.lines || "",
    container: Boolean(attrs.container || attrs.visualRole === "container"),
    low,
    selected,
  };

  if (usesWarmCardLayout(renderAttrs) && renderAttrs.sticky) {
    renderStickyNode(g, node, renderAttrs, flags);
  } else {
    renderStandardNode(g, node, renderAttrs, flags);
  }

  if (options.validation?.length) {
    g.appendChild(
      svgEl("circle", {
        cx: width - 10,
        cy: 10,
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
        rx: 2,
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
