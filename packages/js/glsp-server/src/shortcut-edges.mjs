/** Expands PSM shortcut connector rules into summary edges for the active view. */
export function expandShortcutEdges(diagram, levelConfig = {}) {
  const rules = levelConfig.shortcutConnectorRules || [];
  if (!rules.length) {
    return diagram.connections;
  }
  const nodeByType = indexNodesByType(diagram.nodes);
  const existing = new Set(
    diagram.connections.map((edge) => `${edge.sourceId}|${edge.targetId}|${edge.kind}`),
  );
  const extras = [];
  for (const rule of rules) {
    const sources = nodeByType.get(rule.sourceType) || [];
    const targets = nodeByType.get(rule.targetType) || [];
    const kind = rule.summaryKind || rule.edgeKinds?.[0] || "INTEGRATION";
    for (const source of sources) {
      for (const target of targets) {
        const key = `${source.id}|${target.id}|${kind}`;
        if (existing.has(key)) {
          continue;
        }
        extras.push({
          id: `shortcut-${source.id}-${target.id}-${kind}`,
          sourceId: source.id,
          targetId: target.id,
          kind,
          data: { shortcut: true, label: rule.label || kind, viewType: rule.viewType },
          shortcut: true,
        });
        existing.add(key);
      }
    }
  }
  return [...diagram.connections, ...extras];
}

function indexNodesByType(nodes) {
  const map = new Map();
  for (const node of nodes) {
    const list = map.get(node.type) || [];
    list.push(node);
    map.set(node.type, list);
  }
  return map;
}
