import { el } from "./dom.js";

export const CANVAS_FIT_PADDING = 48;

function isElementVisible(element) {
  if (!element) {
    return false;
  }
  if (element.classList?.contains("hidden")) {
    return false;
  }
  const style = window.getComputedStyle(element);
  return style.display !== "none" && style.visibility !== "hidden" && Number(style.opacity) !== 0;
}

function rectsOverlap(a, b) {
  return (
    Math.min(a.right, b.right) > Math.max(a.left, b.left) &&
    Math.min(a.bottom, b.bottom) > Math.max(a.top, b.top)
  );
}

function shrinkFitRectForObstacle(fitRect, obstacleRect) {
  if (!rectsOverlap(fitRect, obstacleRect)) {
    return fitRect;
  }

  let { left, right, top, bottom } = fitRect;
  const canvasWidth = right - left;
  const canvasHeight = bottom - top;
  if (canvasWidth <= 0 || canvasHeight <= 0) {
    return fitRect;
  }

  const overlapLeft = Math.max(left, obstacleRect.left);
  const overlapRight = Math.min(right, obstacleRect.right);
  const overlapTop = Math.max(top, obstacleRect.top);
  const overlapBottom = Math.min(bottom, obstacleRect.bottom);
  const overlapWidth = overlapRight - overlapLeft;
  const overlapHeight = overlapBottom - overlapTop;

  if (overlapHeight >= canvasHeight * 0.12) {
    const obstacleOnRight = obstacleRect.left > left + canvasWidth * 0.42;
    const obstacleOnLeft = obstacleRect.right < left + canvasWidth * 0.58;
    if (obstacleOnRight) {
      right = Math.min(right, obstacleRect.left);
    } else if (obstacleOnLeft) {
      left = Math.max(left, obstacleRect.right);
    }
  }

  if (right <= left) {
    return fitRect;
  }

  const nextWidth = right - left;
  if (overlapWidth >= nextWidth * 0.12) {
    const nextHeight = bottom - top;
    const obstacleOnBottom = obstacleRect.top > top + nextHeight * 0.42;
    const obstacleOnTop = obstacleRect.bottom < top + nextHeight * 0.58;
    if (obstacleOnTop) {
      top = Math.max(top, obstacleRect.bottom);
    } else if (obstacleOnBottom) {
      bottom = Math.min(bottom, obstacleRect.top);
    }
  }

  if (bottom <= top) {
    return fitRect;
  }

  return { left, right, top, bottom };
}

function collectCanvasObstacles() {
  return [
    el.validationDrawer,
    el.attributePanel,
    el.modelTreePanel,
    el.impactPanel,
    el.palettePane,
    el.methodologyPane,
    el.chatWindow,
    document.getElementById("topbarModelingRow"),
  ].filter(Boolean);
}

function shrinkCanvasFitRect(baseRect) {
  let left = baseRect.left;
  let right = baseRect.right;
  let top = baseRect.top;
  let bottom = baseRect.bottom;

  for (const obstacle of collectCanvasObstacles()) {
    if (!isElementVisible(obstacle)) {
      continue;
    }
    const obstacleRect = obstacle.getBoundingClientRect();
    if (!obstacleRect.width || !obstacleRect.height) {
      continue;
    }
    ({ left, right, top, bottom } = shrinkFitRectForObstacle(
      { left, right, top, bottom },
      obstacleRect,
    ));
  }

  return { left, right, top, bottom };
}

export function getCanvasFitPadding(canvasRect = null, { margin = CANVAS_FIT_PADDING } = {}) {
  const baseRect = canvasRect || el.canvasViewport?.getBoundingClientRect?.();
  if (!baseRect?.width || !baseRect?.height) {
    return [margin, margin, margin, margin];
  }

  const { left, right, top, bottom } = shrinkCanvasFitRect(baseRect);
  return [
    Math.max(margin, top - baseRect.top + margin),
    Math.max(margin, baseRect.right - right + margin),
    Math.max(margin, baseRect.bottom - bottom + margin),
    Math.max(margin, left - baseRect.left + margin),
  ];
}

export function getCanvasFitArea(canvasRect = null, { padding = CANVAS_FIT_PADDING } = {}) {
  const baseRect = canvasRect || el.canvasViewport?.getBoundingClientRect?.();
  if (!baseRect?.width || !baseRect?.height) {
    return null;
  }

  const { left, right, top, bottom } = shrinkCanvasFitRect(baseRect);
  const unobstructedWidth = Math.max(1, right - left);
  const unobstructedHeight = Math.max(1, bottom - top);
  const width = Math.max(1, unobstructedWidth - padding * 2);
  const height = Math.max(1, unobstructedHeight - padding * 2);
  const localLeft = left - baseRect.left;
  const localTop = top - baseRect.top;

  return {
    width,
    height,
    centerX: localLeft + padding + width / 2,
    centerY: localTop + padding + height / 2,
    padding,
    viewportWidth: baseRect.width,
    viewportHeight: baseRect.height,
    paddingInsets: getCanvasFitPadding(baseRect, { margin: padding }),
  };
}
