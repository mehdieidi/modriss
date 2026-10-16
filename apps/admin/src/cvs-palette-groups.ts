type CvsDocument = Record<string, any>;

export type PaletteGroup = {
  name: string;
  elementTypes: string[];
  sharedRules: string[];
  isDefault: boolean;
};

function groupName(value: unknown): string {
  return String(value || "").trim() || "Model Elements";
}

export function paletteGroups(doc: CvsDocument): PaletteGroup[] {
  const groups = new Map<string, PaletteGroup>();
  function group(value: unknown) {
    const name = groupName(value);
    if (!groups.has(name)) {
      groups.set(name, { name, elementTypes: [], sharedRules: [], isDefault: false });
    }
    return groups.get(name)!;
  }
  group(doc.elementVisualDefaults?.category).isDefault = true;
  for (const element of doc.elements || []) {
    if (Object.hasOwn(element, "category")) {
      group(element.category).elementTypes.push(element.type);
    }
  }
  for (const rule of doc.elementVisualRules || []) {
    if (!Object.hasOwn(rule.metadata || {}, "category")) continue;
    const match = rule.match || {};
    const selectors = [
      ["Types", match.types],
      ["Packages", match.packages],
      ["Supertypes", match.supertypes],
      ["Type prefixes", match.typePrefixes],
      ["Type suffixes", match.typeSuffixes],
      ["Type contains", match.typeContains],
    ].filter(([, values]) => Array.isArray(values) && values.length)
      .map(([label, values]) => `${label}: ${(values as string[]).join(", ")}`);
    if (typeof match.abstract === "boolean") selectors.push(`Abstract: ${match.abstract ? "yes" : "no"}`);
    group(rule.metadata.category).sharedRules.push(selectors.join(" · ") || "Shared element rule");
  }
  return [...groups.values()].sort((left, right) => left.name.localeCompare(right.name));
}

/** Renames the category at every source, including rules inherited by contained elements. */
export function renamePaletteGroup(doc: CvsDocument, currentName: string, nextName: string): void {
  const name = nextName.trim();
  if (!name) throw new Error("A palette group name is required.");
  if (groupName(doc.elementVisualDefaults?.category) === currentName) {
    (doc.elementVisualDefaults ??= {}).category = name;
  }
  for (const element of doc.elements || []) {
    if (Object.hasOwn(element, "category") && groupName(element.category) === currentName) {
      element.category = name;
    }
  }
  for (const rule of doc.elementVisualRules || []) {
    if (Object.hasOwn(rule.metadata || {}, "category") && groupName(rule.metadata.category) === currentName) {
      rule.metadata.category = name;
    }
  }
}
