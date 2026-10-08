function overlaps(a, b) {
  return a.left < b.right && a.right > b.left && a.top < b.bottom && a.bottom > b.top;
}

function midpoint(points) {
  const lengths = points
    .slice(1)
    .map((point, index) => Math.hypot(point.x - points[index].x, point.y - points[index].y));
  let remaining = lengths.reduce((sum, length) => sum + length, 0) / 2;
  for (let index = 0; index < lengths.length; index++) {
    const length = lengths[index];
    if (length > 0 && remaining <= length) {
      const ratio = remaining / length;
      return {
        x: points[index].x + (points[index + 1].x - points[index].x) * ratio,
        y: points[index].y + (points[index + 1].y - points[index].y) * ratio,
      };
    }
    remaining -= length;
  }
  return points[0];
}

// Labels use the same path midpoint and font metrics as the edge renderer. Keep
// emphasized relationships readable and suppress nearby labels that would collide.
export function declutterEdgeLabels(nodes, edges) {
  const occupied = nodes.map(({ style }) => ({
    left: style.x - style.width / 2 - 4,
    right: style.x + style.width / 2 + 4,
    top: style.y - style.height / 2 - 4,
    bottom: style.y + style.height / 2 + 4,
  }));
  const ordered = [...edges].sort((a, b) => {
    const priority = (edge) => Number(Boolean(edge.style.selected || edge.style.hovered));
    return priority(b) - priority(a) || a.id.localeCompare(b.id);
  });
  for (const edge of ordered) {
    const { style, data } = edge;
    if (!style.labelText || !data.sourceAnchor || !data.targetAnchor) continue;
    const points = [data.routeStart, ...(data.pinPoints || []), data.routeEnd];
    if (points.some((point) => !Number.isFinite(point?.x) || !Number.isFinite(point?.y))) continue;
    const center = midpoint(points);
    // The renderer truncates at 32 characters and draws at 10px (11px selected).
    const halfWidth =
      Math.min(32, String(style.labelText).length) * (style.selected ? 3.8 : 3.5) + 6;
    const box = {
      left: center.x - halfWidth,
      right: center.x + halfWidth,
      top: center.y - 18,
      bottom: center.y + 2,
    };
    if (!style.selected && !style.hovered && occupied.some((area) => overlaps(box, area))) {
      style.labelText = "";
      style.labelBackground = false;
    } else {
      occupied.push(box);
    }
  }
}
