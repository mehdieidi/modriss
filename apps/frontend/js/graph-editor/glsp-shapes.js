const SHAPE_BUILDERS = {
  rectangle(width, height, cornerRadius = 8) {
    return {
      tag: "rect",
      attrs: { x: 0, y: 0, width, height, rx: cornerRadius, ry: cornerRadius },
    };
  },
  hexagon(width, height) {
    const cx = width / 2;
    const cy = height / 2;
    const rx = width * 0.42;
    const ry = height * 0.38;
    const points = Array.from({ length: 6 }, (_, index) => {
      const angle = (Math.PI / 3) * index - Math.PI / 6;
      return `${cx + rx * Math.cos(angle)},${cy + ry * Math.sin(angle)}`;
    }).join(" ");
    return { tag: "polygon", attrs: { points } };
  },
  diamond(width, height) {
    const cx = width / 2;
    const cy = height / 2;
    return {
      tag: "polygon",
      attrs: { points: `${cx},4 ${width - 4},${cy} ${cx},${height - 4} 4,${cy}` },
    };
  },
  trapezoid(width, height) {
    const inset = Math.min(28, width * 0.18);
    return {
      tag: "polygon",
      attrs: {
        points: `${inset},8 ${width - inset},8 ${width - 8},${height - 8} 8,${height - 8}`,
      },
    };
  },
  octagon(width, height) {
    const inset = Math.min(18, width * 0.12);
    return {
      tag: "polygon",
      attrs: {
        points: `${inset},0 ${width - inset},0 ${width},${inset} ${width},${height - inset} ${width - inset},${height} ${inset},${height} 0,${height - inset} 0,${inset}`,
      },
    };
  },
  ellipse(width, height) {
    return {
      tag: "ellipse",
      attrs: { cx: width / 2, cy: height / 2, rx: width / 2 - 4, ry: height / 2 - 4 },
    };
  },
};

export function shapeElement(geometry, width, height, cornerRadius = 8) {
  const builder = SHAPE_BUILDERS[geometry] || SHAPE_BUILDERS.rectangle;
  return builder(width, height, cornerRadius);
}

export function finiteNumber(value, fallback = 0) {
  const n = Number(value);
  return Number.isFinite(n) ? n : fallback;
}

const NUMERIC_SVG_ATTRS = new Set([
  "x",
  "y",
  "cx",
  "cy",
  "r",
  "rx",
  "ry",
  "width",
  "height",
  "x1",
  "y1",
  "x2",
  "y2",
  "dx",
  "dy",
  "font-size",
  "stroke-width",
  "opacity",
]);

export function safeSvgValue(key, value) {
  if (value === undefined || value === null || value === "") {
    return null;
  }
  if (NUMERIC_SVG_ATTRS.has(key)) {
    const n = Number(value);
    return Number.isFinite(n) ? n : null;
  }
  return value;
}

export function escapeXml(value) {
  return String(value || "")
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;");
}
