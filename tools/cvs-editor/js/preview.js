import { GEOMETRIES, ICON_PATH } from "./constants.js";

export function escapeHtml(value) {
  return String(value ?? "")
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;");
}

export function iconUrl(name) {
  const normalized = String(name || "placeholder").trim() || "placeholder";
  return `${ICON_PATH}/${normalized}.svg`;
}

function geometryPath(geometry, x, y, w, h) {
  const name = String(geometry || "rectangle").toLowerCase();
  const right = x + w;
  const bottom = y + h;
  const midX = x + w / 2;
  const midY = y + h / 2;
  const pointsToD = (points) =>
    points.map((p, i) => `${i === 0 ? "M" : "L"}${p[0].toFixed(1)},${p[1].toFixed(1)}`).join(" ") + " Z";

  if (name === "diamond") {
    return pointsToD([
      [midX, y],
      [right, midY],
      [midX, bottom],
      [x, midY],
    ]);
  }
  if (name === "hexagon") {
    const inset = w * 0.24;
    return pointsToD([
      [x + inset, y],
      [right - inset, y],
      [right, midY],
      [right - inset, bottom],
      [x + inset, bottom],
      [x, midY],
    ]);
  }
  if (name === "octagon") {
    const ix = w * 0.2;
    const iy = h * 0.2;
    return pointsToD([
      [x + ix, y],
      [right - ix, y],
      [right, y + iy],
      [right, bottom - iy],
      [right - ix, bottom],
      [x + ix, bottom],
      [x, bottom - iy],
      [x, y + iy],
    ]);
  }
  if (name === "trapezoid") {
    const inset = w * 0.18;
    return pointsToD([
      [x + inset, y],
      [right, y],
      [right - inset, bottom],
      [x, bottom],
    ]);
  }
  if (name === "ellipse") {
    return `M ${midX} ${y} A ${w / 2} ${h / 2} 0 1 1 ${midX} ${bottom} A ${w / 2} ${h / 2} 0 1 1 ${midX} ${y} Z`;
  }
  if (name === "rounded-rectangle") {
    const r = Math.min(14, w / 6, h / 6);
    return `M ${x + r} ${y} H ${right - r} Q ${right} ${y} ${right} ${y + r} V ${bottom - r} Q ${right} ${bottom} ${right - r} ${bottom} H ${x + r} Q ${x} ${bottom} ${x} ${bottom - r} V ${y + r} Q ${x} ${y} ${x + r} ${y} Z`;
  }
  return `M ${x} ${y} H ${right} V ${bottom} H ${x} Z`;
}

export function primitivePreviewSvg(primitive, { width = 120, height = 72 } = {}) {
  const geometry = primitive?.geometry || "rounded-rectangle";
  const pad = 8;
  const path = geometryPath(geometry, pad, pad, width - pad * 2, height - pad * 2);
  const fill = "rgba(99, 179, 237, 0.15)";
  const stroke = "#38bdf8";
  return `<svg viewBox="0 0 ${width} ${height}" class="primitive-svg" aria-hidden="true">
    <path d="${path}" fill="${fill}" stroke="${stroke}" stroke-width="2"/>
  </svg>`;
}

export function nodePreviewSvg(visual, { width = 200, height = 112, selected = false } = {}) {
  const color = visual.color || "#475569";
  const tag = escapeHtml(visual.tag || visual.primitive?.slice(0, 4)?.toUpperCase() || "TYPE");
  const label = escapeHtml(visual.label || "Element");
  const role = escapeHtml(visual.visualRole || "node");
  const icon = iconUrl(visual.icon);
  const isContainer = role === "container";
  const cardH = isContainer ? height - 8 : height;
  const stroke = selected ? "#38bdf8" : "rgba(255,255,255,0.12)";
  const strokeW = selected ? 2.5 : 1.2;

  return `<svg viewBox="0 0 ${width} ${height}" class="node-preview-svg" aria-hidden="true">
    <defs>
      <linearGradient id="cardGrad" x1="0" y1="0" x2="0" y2="1">
        <stop offset="0%" stop-color="${color}" stop-opacity="0.35"/>
        <stop offset="100%" stop-color="${color}" stop-opacity="0.12"/>
      </linearGradient>
    </defs>
    <rect x="4" y="4" width="${width - 8}" height="${cardH - 8}" rx="${isContainer ? 16 : 12}" fill="url(#cardGrad)" stroke="${stroke}" stroke-width="${strokeW}"/>
    <rect x="4" y="4" width="${width - 8}" height="28" rx="12" fill="${color}" fill-opacity="0.55"/>
    <image href="${icon}" x="14" y="10" width="20" height="20" opacity="0.95"/>
    <text x="42" y="22" fill="#f8fafc" font-size="11" font-weight="600" font-family="DM Sans, sans-serif">${label.length > 18 ? `${label.slice(0, 16)}…` : label}</text>
    <text x="${width - 14}" y="22" fill="#e2e8f0" font-size="9" font-weight="700" text-anchor="end" font-family="JetBrains Mono, monospace">${tag}</text>
    <text x="14" y="52" fill="#94a3b8" font-size="9" font-family="JetBrains Mono, monospace">${escapeHtml((visual.lineFields || []).slice(0, 2).join(" · ") || "line fields…")}</text>
    <rect x="14" y="${cardH - 22}" width="42" height="14" rx="7" fill="rgba(15,23,42,0.55)"/>
    <text x="35" y="${cardH - 12}" fill="#7dd3fc" font-size="8" text-anchor="middle" font-family="JetBrains Mono, monospace">${role}</text>
  </svg>`;
}

export function edgePreviewSvg(rule, { width = 220, height = 56 } = {}) {
  const stroke = rule?.stroke || "#64748b";
  const dash = Array.isArray(rule?.lineDash) ? rule.lineDash.join(",") : "";
  const dashAttr = dash ? `stroke-dasharray="${dash}"` : "";
  const kinds = (rule?.matchKinds || []).slice(0, 2).join(", ") || "edge";
  return `<svg viewBox="0 0 ${width} ${height}" class="edge-preview-svg" aria-hidden="true">
    <line x1="16" y1="${height / 2}" x2="${width - 28}" y2="${height / 2}" stroke="${stroke}" stroke-width="${rule?.lineWidth || 2}" ${dashAttr} stroke-linecap="round"/>
    <polygon points="${width - 28},${height / 2 - 5} ${width - 16},${height / 2} ${width - 28},${height / 2 + 5}" fill="${stroke}"/>
    <text x="16" y="14" fill="#94a3b8" font-size="9" font-family="JetBrains Mono, monospace">${escapeHtml(kinds)}</text>
  </svg>`;
}

export function roleSizePreview(role, size) {
  const w = Number(size?.width) || 120;
  const h = Number(size?.height) || 80;
  const scale = Math.min(140 / w, 90 / h, 1);
  const sw = Math.round(w * scale);
  const sh = Math.round(h * scale);
  return `<div class="role-size-box" style="width:${sw}px;height:${sh}px" title="${w}×${h}">
    <span>${escapeHtml(role)}</span>
    <small>${w}×${h}</small>
  </div>`;
}

export function chipList(items, { removable = false, dataAttr = "" } = {}) {
  return (items || [])
    .map(
      (item, index) =>
        `<span class="chip" ${dataAttr} data-index="${index}">${escapeHtml(item)}${
          removable ? `<button type="button" class="chip-remove" data-remove="${index}" aria-label="Remove">×</button>` : ""
        }</span>`,
    )
    .join("");
}
