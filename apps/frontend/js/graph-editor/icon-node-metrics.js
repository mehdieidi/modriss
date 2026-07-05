export const ICON_GAP = 2;
export const ICON_SIZE_NORMAL = 72;
export const ICON_SIZE_LOW = 44;
export const ICON_NODE_WIDTH = 120;
export const ICON_NODE_SIZE = { width: ICON_NODE_WIDTH, height: 118 };
export const ICON_NODE_LOW_SIZE = { width: 88, height: 86 };
export const PLACEHOLDER_ICON = "/assets/icons/placeholder.svg";

export function resolveIconSource(icon) {
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

export function wrapLabelLines(text, maxCharsPerLine = 17) {
  const value = String(text || "").trim();
  if (!value) {
    return [];
  }
  const lines = [];
  let current = "";
  const flush = () => {
    if (current) {
      lines.push(current);
      current = "";
    }
  };
  const pushLongToken = (token) => {
    for (let index = 0; index < token.length; index += maxCharsPerLine) {
      lines.push(token.slice(index, index + maxCharsPerLine));
    }
  };
  value.split(/\s+/).forEach((word) => {
    if (!word) {
      return;
    }
    if (word.length > maxCharsPerLine) {
      flush();
      pushLongToken(word);
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

export function measureIconNodeSize(labelText, options = {}) {
  const low = Boolean(options.low);
  const width = Number(options.width) || ICON_NODE_WIDTH;
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
    nameLines,
    iconSize,
    nameLineHeight,
    kindLineHeight,
    padTop,
    gapAfterIcon,
    gapKindName,
    padBottom,
  };
}

export function computeIconNodeLayout(width, height, low = false, labelText = "") {
  const measured = measureIconNodeSize(labelText, { width, low });
  const left = -width / 2;
  const top = -height / 2;
  const iconSize = measured.iconSize;
  const iconX = left + (width - iconSize) / 2;
  const iconY = top + measured.padTop;
  const kindY = iconY + iconSize + measured.gapAfterIcon;
  const nameY = kindY + measured.kindLineHeight + measured.gapKindName;
  const centerX = left + width / 2;
  return {
    left,
    top,
    width,
    height,
    iconSize,
    iconX,
    iconY,
    kindY,
    nameY,
    centerX,
    kindLineHeight: measured.kindLineHeight,
    nameLineHeight: measured.nameLineHeight,
    nameLines: measured.nameLines,
  };
}

export function iconAnchorBoundsFromNodeRect(
  nodeX,
  nodeY,
  width,
  height,
  low = false,
  labelText = "",
) {
  const measured = measureIconNodeSize(labelText, { width, low });
  const iconSize = measured.iconSize;
  const iconLeft = nodeX + (width - iconSize) / 2;
  const iconTop = nodeY + measured.padTop;
  const left = iconLeft - ICON_GAP;
  const top = iconTop - ICON_GAP;
  const right = iconLeft + iconSize + ICON_GAP;
  const bottom = iconTop + iconSize + ICON_GAP;
  return {
    left,
    top,
    right,
    bottom,
    width: right - left,
    height: bottom - top,
    centerX: iconLeft + iconSize / 2,
    centerY: iconTop + iconSize / 2,
    iconLeft,
    iconTop,
    iconSize,
  };
}

export function iconAnchorBoundsLocal(width, height, low = false, labelText = "") {
  const measured = measureIconNodeSize(labelText, { width, low });
  const left = -width / 2 + (width - measured.iconSize) / 2 - ICON_GAP;
  const top = -height / 2 + measured.padTop - ICON_GAP;
  const right = left + measured.iconSize + ICON_GAP * 2;
  const bottom = top + measured.iconSize + ICON_GAP * 2;
  return {
    left,
    top,
    right,
    bottom,
    width: right - left,
    height: bottom - top,
    centerX: (left + right) / 2,
    centerY: (top + bottom) / 2,
  };
}

export function labelBlockBoundsLocal(width, height, low = false, labelText = "", kindText = "") {
  const measured = measureIconNodeSize(labelText, { width, low });
  const layout = computeIconNodeLayout(width, height, low, labelText);
  const kind = String(kindText || "Element")
    .trim()
    .toUpperCase();
  const kindCharW = low ? 4.4 : 4.85;
  const nameCharW = low ? 5.8 : 6.25;
  const kindWidth = kind.length * kindCharW + 6;
  const nameWidth = Math.max(0, ...measured.nameLines.map((line) => line.length * nameCharW + 6));
  const blockWidth = Math.min(width - 4, Math.max(28, kindWidth, nameWidth));
  const left = layout.centerX - blockWidth / 2;
  return {
    left,
    right: left + blockWidth,
    top: layout.iconY + layout.iconSize,
    bottom: layout.top + layout.height - measured.padBottom,
    width: blockWidth,
    centerX: layout.centerX,
  };
}

export function labelBlockBoundsFromNodeRect(
  nodeX,
  nodeY,
  width,
  height,
  low = false,
  labelText = "",
  kindText = "",
) {
  const local = labelBlockBoundsLocal(width, height, low, labelText, kindText);
  const centerOffsetX = nodeX + width / 2;
  const centerOffsetY = nodeY + height / 2;
  return {
    left: local.left + centerOffsetX,
    right: local.right + centerOffsetX,
    top: local.top + centerOffsetY,
    bottom: local.bottom + centerOffsetY,
    width: local.width,
    centerX: local.centerX + centerOffsetX,
  };
}

export function openControlBoundsForNode(nodeX, nodeY, width, low = false, labelText = "") {
  const measured = measureIconNodeSize(labelText, { width, low });
  const iconLeft = nodeX + (width - measured.iconSize) / 2;
  const iconTop = nodeY + measured.padTop;
  const iconCenterX = iconLeft + measured.iconSize / 2;
  const controlWidth = low ? 30 : 36;
  const controlHeight = low ? 14 : 15;
  const gap = 4;
  return {
    x: iconCenterX - controlWidth / 2,
    y: iconTop - controlHeight - gap,
    width: controlWidth,
    height: controlHeight,
  };
}

export function isPointInOpenControlBounds(
  pointX,
  pointY,
  nodeX,
  nodeY,
  width,
  low = false,
  labelText = "",
  padding = 6,
) {
  const open = openControlBoundsForNode(nodeX, nodeY, width, low, labelText);
  return (
    pointX >= open.x - padding &&
    pointX <= open.x + open.width + padding &&
    pointY >= open.y - padding &&
    pointY <= open.y + open.height + padding
  );
}

export function isPointInOpenApproachBounds(
  pointX,
  pointY,
  nodeX,
  nodeY,
  width,
  height,
  low = false,
  labelText = "",
) {
  const anchor = iconAnchorBoundsFromNodeRect(nodeX, nodeY, width, height, low, labelText);
  const open = openControlBoundsForNode(nodeX, nodeY, width, low, labelText);
  const approachPad = 16;
  const left = Math.min(anchor.left, open.x) - approachPad;
  const right = Math.max(anchor.right, open.x + open.width) + approachPad;
  return (
    pointX >= left &&
    pointX <= right &&
    pointY >= open.y - approachPad &&
    pointY <= anchor.top + approachPad
  );
}

export function isPointInOpenInteractionZone(
  pointX,
  pointY,
  nodeX,
  nodeY,
  width,
  height,
  low = false,
  labelText = "",
  padding = 10,
) {
  if (isPointInOpenControlBounds(pointX, pointY, nodeX, nodeY, width, low, labelText, padding)) {
    return true;
  }
  return isPointInOpenApproachBounds(pointX, pointY, nodeX, nodeY, width, height, low, labelText);
}

export function nodeInteractionBoundsFromNodeRect(
  nodeX,
  nodeY,
  width,
  height,
  low = false,
  labelText = "",
  { isContainer = false } = {},
) {
  const anchor = iconAnchorBoundsFromNodeRect(nodeX, nodeY, width, height, low, labelText);
  const measured = measureIconNodeSize(labelText, { width, low });
  const bottom = nodeY + height - measured.padBottom;
  const contentTop = nodeY + measured.padTop + measured.iconSize;
  const open = isContainer ? openControlBoundsForNode(nodeX, nodeY, width, low, labelText) : null;
  const top = open ? Math.min(anchor.top, open.y) : anchor.top;
  return {
    x: Math.min(anchor.left, nodeX),
    y: top,
    width: Math.max(anchor.right, nodeX + width) - Math.min(anchor.left, nodeX),
    height: bottom - top,
    maxX: nodeX + width,
    maxY: bottom,
    anchor,
    contentTop,
    bottom,
  };
}

export function isPointInNodeInteractionBounds(
  pointX,
  pointY,
  nodeX,
  nodeY,
  width,
  height,
  low = false,
  labelText = "",
  options = {},
) {
  const { isContainer = false, kindText = "" } = options;
  const anchor = iconAnchorBoundsFromNodeRect(nodeX, nodeY, width, height, low, labelText);
  const labels = labelBlockBoundsFromNodeRect(
    nodeX,
    nodeY,
    width,
    height,
    low,
    labelText,
    kindText,
  );

  if (isContainer) {
    if (isPointInOpenInteractionZone(pointX, pointY, nodeX, nodeY, width, height, low, labelText)) {
      return true;
    }
  }

  const inIcon =
    pointX >= anchor.left &&
    pointX <= anchor.right &&
    pointY >= anchor.top &&
    pointY <= anchor.bottom;

  const inLabels =
    pointX >= labels.left &&
    pointX <= labels.right &&
    pointY >= labels.top &&
    pointY <= labels.bottom;

  return inIcon || inLabels;
}

export function nodeHitPathLocal(
  width,
  height,
  low = false,
  labelText = "",
  isContainer = false,
  kindText = "",
) {
  const layout = computeIconNodeLayout(width, height, low, labelText);
  const anchor = iconAnchorBoundsLocal(width, height, low, labelText);
  const measured = measureIconNodeSize(labelText, { width, low });
  const labels = labelBlockBoundsLocal(width, height, low, labelText, kindText);
  const interactionBottom = layout.top + layout.height - measured.padBottom;
  const controlHeight = low ? 14 : 15;
  const hitTop = isContainer ? layout.iconY - controlHeight - 20 : anchor.top;

  return [
    ["M", anchor.left, hitTop],
    ["L", anchor.right, hitTop],
    ["L", anchor.right, anchor.bottom],
    ["L", labels.right, anchor.bottom],
    ["L", labels.right, interactionBottom],
    ["L", labels.left, interactionBottom],
    ["L", labels.left, anchor.bottom],
    ["L", anchor.left, anchor.bottom],
    ["Z"],
  ];
}

export function linkHandlePointsForNode(nodeX, nodeY, width, height, low = false, labelText = "") {
  const anchor = iconAnchorBoundsFromNodeRect(nodeX, nodeY, width, height, low, labelText);
  return {
    left: { x: anchor.left, y: anchor.centerY },
    right: { x: anchor.right, y: anchor.centerY },
  };
}

export function routePointOnIconAnchor(
  nodeX,
  nodeY,
  width,
  height,
  side,
  offsetY,
  low = false,
  labelText = "",
) {
  const bounds = iconAnchorBoundsFromNodeRect(nodeX, nodeY, width, height, low, labelText);
  const y = Number.isFinite(offsetY) ? nodeY + offsetY : bounds.centerY;
  if (side === "right") {
    return { x: Math.round(bounds.right), y: Math.round(y) };
  }
  if (side === "left") {
    return { x: Math.round(bounds.left), y: Math.round(y) };
  }
  if (side === "top" || side === "north") {
    return { x: Math.round(bounds.centerX), y: Math.round(bounds.top) };
  }
  if (side === "bottom" || side === "south") {
    return { x: Math.round(bounds.centerX), y: Math.round(bounds.bottom) };
  }
  return { x: Math.round(bounds.centerX), y: Math.round(bounds.centerY) };
}
