// ── Pure utility functions (no imports) ───────────────────────────────────────

const ULID_ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";

function encodeUlidTime(time) {
  let value = time;
  let encoded = "";
  for (let index = 0; index < 10; index++) {
    encoded = ULID_ALPHABET[value % 32] + encoded;
    value = Math.floor(value / 32);
  }
  return encoded;
}

export function genId() {
  if (globalThis.crypto?.getRandomValues) {
    const random = new Uint8Array(16);
    globalThis.crypto.getRandomValues(random);
    return (
      encodeUlidTime(Date.now()) +
      Array.from(random, (value) => ULID_ALPHABET[value & 31])
        .join("")
        .slice(0, 16)
    );
  }
  if (globalThis.crypto?.randomUUID) {
    return globalThis.crypto.randomUUID();
  }
  return "xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx".replace(/[xy]/g, (token) => {
    const random = Math.floor(Math.random() * 16);
    return (token === "x" ? random : (random & 0x3) | 0x8).toString(16);
  });
}

export function escapeHtml(value) {
  return String(value)
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#39;");
}

export function emptyDiagram(type) {
  return { type, name: `${type}-model`, nodes: [], connections: [] };
}

export function autoLayoutIfStacked(nodes) {
  const LAYOUT_MARGIN = 40;
  const LAYOUT_SPACING_X = 200;
  const LAYOUT_SPACING_Y = 120;
  const DUPLICATE_POSITION_THRESHOLD = 0.5;
  if (nodes.length < 2) {
    return nodes;
  }
  const positions = new Set(nodes.map((n) => `${n.x},${n.y}`));
  const allZero = nodes.every((n) => n.x === 0 && n.y === 0);
  if (!allZero && positions.size > nodes.length * DUPLICATE_POSITION_THRESHOLD) {
    return nodes;
  }
  const cols = Math.ceil(Math.sqrt(nodes.length));
  nodes.forEach((node, i) => {
    node.x = (i % cols) * LAYOUT_SPACING_X + LAYOUT_MARGIN;
    node.y = Math.floor(i / cols) * LAYOUT_SPACING_Y + LAYOUT_MARGIN;
  });
  return nodes;
}

export function autoLayout(nodes, connections = []) {
  const COL_SPACING = 240;
  const ROW_SPACING = 110;
  const MARGIN = 60;

  if (nodes.length === 0) {
    return nodes;
  }

  const nodeSet = new Set(nodes.map((n) => n.id));
  const validConns = connections.filter(
    (c) => nodeSet.has(c.sourceId) && nodeSet.has(c.targetId) && c.sourceId !== c.targetId,
  );

  // Assign column depth via longest-path edge relaxation (handles DAGs and cycles)
  const depth = new Map(nodes.map((n) => [n.id, 0]));
  for (let pass = 0; pass < nodes.length; pass++) {
    let changed = false;
    for (const c of validConns) {
      const d = depth.get(c.sourceId) + 1;
      if (d > depth.get(c.targetId)) {
        depth.set(c.targetId, d);
        changed = true;
      }
    }
    if (!changed) {
      break;
    }
  }

  const maxDepth = Math.max(...depth.values());

  if (maxDepth === 0) {
    // No edges or all nodes at same level — use a square grid
    const cols = Math.max(1, Math.ceil(Math.sqrt(nodes.length)));
    nodes.forEach((node, i) => {
      node.x = MARGIN + (i % cols) * COL_SPACING;
      node.y = MARGIN + Math.floor(i / cols) * ROW_SPACING;
      if (node.meta) {
        node.meta.x = node.x;
        node.meta.y = node.y;
      }
    });
    return nodes;
  }

  // Group nodes by column (depth)
  const columns = new Map();
  for (const n of nodes) {
    const col = depth.get(n.id);
    if (!columns.has(col)) {
      columns.set(col, []);
    }
    columns.get(col).push(n);
  }

  // Place columns left-to-right; center each column vertically relative to tallest
  const sortedCols = [...columns.entries()].sort(([a], [b]) => a - b);
  const maxRows = Math.max(...sortedCols.map(([, ns]) => ns.length));

  sortedCols.forEach(([, colNodes], colIdx) => {
    const rowOffset = Math.round((maxRows - colNodes.length) / 2);
    colNodes.forEach((node, rowIdx) => {
      node.x = MARGIN + colIdx * COL_SPACING;
      node.y = MARGIN + (rowOffset + rowIdx) * ROW_SPACING;
      // Sync meta so positions survive serialization
      if (node.meta) {
        node.meta.x = node.x;
        node.meta.y = node.y;
      }
    });
  });

  return nodes;
}

/** Yields to the browser so long synchronous work does not freeze the UI. */
export function yieldToMain() {
  return new Promise((resolve) => {
    if (typeof globalThis.requestAnimationFrame === "function") {
      globalThis.requestAnimationFrame(() => globalThis.setTimeout(resolve, 0));
      return;
    }
    globalThis.setTimeout(resolve, 0);
  });
}

/** Runs work when the browser is idle so post-load UI updates do not block interaction. */
export function scheduleIdleTask(task) {
  return new Promise((resolve) => {
    const run = async () => {
      try {
        await task();
      } finally {
        resolve();
      }
    };
    if (typeof globalThis.requestIdleCallback === "function") {
      globalThis.requestIdleCallback(
        () => {
          void run();
        },
        { timeout: 2500 },
      );
      return;
    }
    globalThis.setTimeout(() => {
      void run();
    }, 0);
  });
}
