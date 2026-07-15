import {
  BadgeCheck,
  Boxes,
  Braces,
  Download,
  Eye,
  FileJson,
  GitBranch,
  Grid3X3,
  Layers3,
  Network,
  Palette,
  Plus,
  Save,
  Shapes,
  SlidersHorizontal,
  Trash2,
  Upload,
} from "lucide-react";
import { ChangeEvent, ReactNode, useEffect, useMemo, useRef, useState } from "react";
import { api } from "./api";

type CvsDocument = Record<string, any>;
type EditorSection =
  | "overview"
  | "elements"
  | "primitives"
  | "packages"
  | "viewpoints"
  | "canvas"
  | "relationships"
  | "badges"
  | "advanced";

const levels = ["cim", "pim", "psm"];
const metamodel: Record<string, any> = {
  cim: {
    displayName: "CIM",
    ecore: "mde/metamodels/cim/cim-combined.ecore",
    nsUri: "https://varka.org/cim/1.0",
    rootEClass: "CIMModel",
    modelLevel: "CIM",
  },
  pim: {
    displayName: "PIM",
    ecore: "mde/metamodels/pim/pim-combined.ecore",
    nsUri: "https://varka.org/pim/1.0",
    rootEClass: "PIMModel",
    modelLevel: "PIM",
  },
  psm: {
    displayName: "PSM",
    ecore: "mde/metamodels/psm/psm-combined.ecore",
    nsUri: "https://varka.org/psm/aws/1.0",
    rootEClass: "PSMModel",
    modelLevel: "PSM",
  },
};
const visualRoles = ["node", "container", "detail", "support", "relationship"];
const geometries = ["rectangle", "rounded-rectangle", "diamond", "hexagon", "octagon", "trapezoid", "ellipse"];
const shapePresets = [
  "concept-card",
  "workspace-container",
  "participant-card",
  "class-card",
  "behavior-node",
  "process-node",
  "constraint-badge-card",
  "dashboard-row",
  "goal-card",
  "metric-card",
  "api-card",
  "function-card",
  "container-card",
];
const commonIcons = [
  "dashboard",
  "category",
  "flag",
  "analytics",
  "database",
  "bolt",
  "schema",
  "shield",
  "groups",
  "person",
  "api",
  "functions",
  "event",
  "route",
  "memory",
  "cloud",
  "settings",
  "security",
  "link",
  "track_changes",
  "fact_check",
  "tag",
  "article",
  "folder",
  "map",
  "timeline",
  "gavel",
  "warning",
  "check_circle",
  "deploy",
  "lan",
  "rss_feed",
  "inventory",
  "monitoring",
  "key",
  "public",
  "extension",
  "placeholder",
];
const layoutHints = ["DEFAULT_LAYERED", "CONTAINER", "PROCESS", "GOVERNANCE", "SPACIOUS_LAYERED", "TREE", "RADIAL"];
const markers = ["", "arrow", "triangle-hollow", "triangle-filled", "diamond-filled", "diamond-hollow"];

const sections: Array<{ id: EditorSection; label: string; icon: ReactNode }> = [
  { id: "overview", label: "Overview", icon: <Eye size={16} /> },
  { id: "elements", label: "Elements", icon: <Boxes size={16} /> },
  { id: "primitives", label: "Primitives", icon: <Shapes size={16} /> },
  { id: "packages", label: "Package rules", icon: <Layers3 size={16} /> },
  { id: "viewpoints", label: "Viewpoints", icon: <Grid3X3 size={16} /> },
  { id: "canvas", label: "Canvas", icon: <SlidersHorizontal size={16} /> },
  { id: "relationships", label: "Relationships", icon: <GitBranch size={16} /> },
  { id: "badges", label: "Badges", icon: <BadgeCheck size={16} /> },
  { id: "advanced", label: "Advanced", icon: <Braces size={16} /> },
];

export function CvsEditorView(props: {
  token: string;
  canAdmin: boolean;
  query: string;
  onError: (err: unknown) => void;
}) {
  const [doc, setDoc] = useState<CvsDocument | null>(null);
  const [level, setLevel] = useState("cim");
  const [section, setSection] = useState<EditorSection>("overview");
  const [dirty, setDirty] = useState(false);
  const [selected, setSelected] = useState<Record<string, any>>({});
  const [filter, setFilter] = useState({ element: "", category: "all" });
  const [saving, setSaving] = useState(false);
  const [loading, setLoading] = useState(false);
  const fileInput = useRef<HTMLInputElement>(null);

  useEffect(() => {
    void loadLevel(level);
  }, []);

  useEffect(() => {
    const onBeforeUnload = (event: BeforeUnloadEvent) => {
      if (!dirty) return;
      event.preventDefault();
      event.returnValue = "";
    };
    window.addEventListener("beforeunload", onBeforeUnload);
    return () => window.removeEventListener("beforeunload", onBeforeUnload);
  }, [dirty]);

  const visibleSections = sections.filter((item) =>
    `${item.label} ${item.id}`.toLowerCase().includes(props.query.trim().toLowerCase()),
  );

  async function loadLevel(nextLevel: string) {
    if (dirty && !window.confirm("Discard unsaved CVS changes?")) return;
    setLoading(true);
    try {
      const loaded = await api<CvsDocument>(`/api/admin/notation/${nextLevel}`, props.token);
      setDoc(normalizeLoadedDoc(loaded));
      setLevel(nextLevel);
      setDirty(false);
      setSection("overview");
      setSelected({});
    } catch (err) {
      props.onError(err);
    } finally {
      setLoading(false);
    }
  }

  function newDocument(nextLevel: string) {
    if (dirty && !window.confirm("Discard unsaved CVS changes?")) return;
    setDoc(createEmptyDoc(nextLevel));
    setLevel(nextLevel);
    setDirty(true);
    setSection("overview");
    setSelected({});
  }

  function mutate(mutator: (draft: CvsDocument) => void) {
    setDoc((current) => {
      if (!current) return current;
      const next = structuredClone(current);
      mutator(next);
      next.notationPrimitives = { ...(next.primitives || {}) };
      return next;
    });
    setDirty(true);
  }

  async function save() {
    if (!doc) return;
    setSaving(true);
    try {
      await api<void>(`/api/admin/notation/${levelFromDoc(doc)}`, props.token, {
        method: "POST",
        body: JSON.stringify({
          document: doc,
          reason: "admin CVS editor update",
        }),
      });
      setDirty(false);
    } catch (err) {
      props.onError(err);
    } finally {
      setSaving(false);
    }
  }

  function exportDoc() {
    if (!doc) return;
    const blob = new Blob([JSON.stringify(doc, null, 2)], { type: "application/json" });
    const url = URL.createObjectURL(blob);
    const anchor = document.createElement("a");
    anchor.href = url;
    anchor.download = `${levelFromDoc(doc)}.cvs.json`;
    anchor.click();
    URL.revokeObjectURL(url);
  }

  function importDoc(event: ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0];
    event.target.value = "";
    if (!file) return;
    const reader = new FileReader();
    reader.onload = () => {
      try {
        const parsed = normalizeLoadedDoc(JSON.parse(String(reader.result || "{}")));
        setDoc(parsed);
        setLevel(levelFromDoc(parsed));
        setDirty(true);
        setSection("overview");
        setSelected({});
      } catch (err) {
        props.onError(err);
      }
    };
    reader.readAsText(file);
  }

  return (
    <div className="cvs-admin">
      <section className="panel cvs-toolbar">
        <div className="cvs-document">
          <FileJson size={20} />
          <div>
            <strong>{doc ? `${doc.displayName || level.toUpperCase()} · CVS v${doc.cvsVersion || 2}` : "No CVS document"}</strong>
            <span>{doc ? doc.metamodelRef?.ecore : "Load a concrete visual syntax document."}</span>
          </div>
        </div>
        <div className="cvs-actions">
          <Pills values={[level.toUpperCase(), dirty ? "UNSAVED" : "SAVED"]} empty="" />
          <div className="segmented-control">
            {levels.map((item) => (
              <button className={level === item ? "active" : ""} disabled={loading} key={item} onClick={() => loadLevel(item)}>
                {item.toUpperCase()}
              </button>
            ))}
          </div>
          <button className="ghost-action" onClick={() => fileInput.current?.click()} title="Import CVS JSON">
            <Upload size={16} />
            <span>Import</span>
          </button>
          <button className="ghost-action" onClick={exportDoc} disabled={!doc} title="Export CVS JSON">
            <Download size={16} />
            <span>Export</span>
          </button>
          {props.canAdmin && (
            <button className="primary-action" onClick={save} disabled={!doc || !dirty || saving}>
              <Save size={16} />
              <span>{saving ? "Saving" : "Save CVS"}</span>
            </button>
          )}
          <input ref={fileInput} hidden type="file" accept=".json,application/json" onChange={importDoc} />
        </div>
      </section>

      <div className="cvs-layout">
        <aside className="panel cvs-section-nav">
          <div className="cvs-section-new">
            {levels.map((item) => (
              <button key={item} onClick={() => newDocument(item)} title={`New ${item.toUpperCase()} document`}>
                <Plus size={15} />
                <span>{item.toUpperCase()}</span>
              </button>
            ))}
          </div>
          {visibleSections.map((item) => (
            <button
              className={section === item.id ? "cvs-section-button active" : "cvs-section-button"}
              key={item.id}
              onClick={() => setSection(item.id)}
            >
              {item.icon}
              <span>{item.label}</span>
            </button>
          ))}
        </aside>
        <section className="cvs-editor-surface">
          {!doc ? (
            <section className="panel empty-cvs">
              <Network size={28} />
              <h2>Concrete Visual Syntax</h2>
              <p>Select a level or import a CVS JSON document to edit visual notation inside admin.</p>
            </section>
          ) : (
            <EditorSectionView
              doc={doc}
              section={section}
              selected={selected}
              filter={filter}
              setSection={setSection}
              setSelected={setSelected}
              setFilter={setFilter}
              mutate={mutate}
              onError={props.onError}
            />
          )}
        </section>
      </div>
    </div>
  );
}

function EditorSectionView(props: {
  doc: CvsDocument;
  section: EditorSection;
  selected: Record<string, any>;
  filter: { element: string; category: string };
  setSection: (section: EditorSection) => void;
  setSelected: (selected: Record<string, any>) => void;
  setFilter: (filter: { element: string; category: string }) => void;
  mutate: (mutator: (draft: CvsDocument) => void) => void;
  onError: (err: unknown) => void;
}) {
  switch (props.section) {
    case "elements":
      return <ElementsEditor {...props} />;
    case "primitives":
      return <PrimitivesEditor {...props} />;
    case "packages":
      return <PackageRulesEditor {...props} />;
    case "viewpoints":
      return <ViewpointsEditor {...props} />;
    case "canvas":
      return <CanvasEditor {...props} />;
    case "relationships":
      return <RelationshipsEditor {...props} />;
    case "badges":
      return <BadgesEditor {...props} />;
    case "advanced":
      return <AdvancedEditor {...props} />;
    default:
      return <OverviewEditor {...props} />;
  }
}

function OverviewEditor({ doc, mutate, setSection }: any) {
  const stats = [
    ["Elements", doc.elementOverrides?.length || 0],
    ["Primitives", Object.keys(doc.primitives || {}).length],
    ["Viewpoints", doc.viewpoints?.length || 0],
    ["Package rules", doc.elementVisualRules?.length || 0],
    ["Edge styles", doc.relationshipVisualRules?.length || 0],
    ["Badge rules", doc.badgeRules?.length || 0],
  ];
  return (
    <>
      <SectionHead title="Overview" description={`Document metadata and coverage summary for ${doc.displayName || levelFromDoc(doc).toUpperCase()}.`} />
      <div className="cvs-overview-grid">
        <section className="panel">
          <h2>Metamodel</h2>
          <Field label="Display name" value={doc.displayName} onChange={(value) => mutate((draft: any) => (draft.displayName = value))} />
          <Field
            label="CVS version"
            type="number"
            value={doc.cvsVersion}
            onChange={(value) => mutate((draft: any) => (draft.cvsVersion = Number(value) || 2))}
          />
          <SelectField
            label="Level"
            value={levelFromDoc(doc)}
            options={levels}
            onChange={(value) =>
              mutate((draft: any) => {
                draft.metamodelRef ??= {};
                draft.metamodelRef.level = value;
                draft.metamodelRef.ecore = metamodel[value].ecore;
                draft.metamodelRef.nsUri = metamodel[value].nsUri;
              })
            }
          />
          <Field label="Ecore path" value={doc.metamodelRef?.ecore} onChange={(value) => mutate((draft: any) => (draft.metamodelRef.ecore = value))} />
          <Field label="Namespace URI" value={doc.metamodelRef?.nsUri} onChange={(value) => mutate((draft: any) => (draft.metamodelRef.nsUri = value))} />
        </section>
        <section className="panel">
          <h2>Coverage</h2>
          <div className="cvs-stat-grid">
            {stats.map(([label, value]) => (
              <div className="cvs-stat" key={String(label)}>
                <strong>{String(value)}</strong>
                <span>{String(label)}</span>
              </div>
            ))}
          </div>
        </section>
        <section className="panel cvs-wide">
          <h2>Defaults Preview</h2>
          <div className="cvs-preview-row">
            <NodePreview visual={resolveElementVisual(doc, { type: "Sample element" })} />
            <button className="ghost-action" onClick={() => setSection("advanced")}>
              <Palette size={16} />
              <span>Edit defaults</span>
            </button>
          </div>
        </section>
      </div>
    </>
  );
}

function ElementsEditor({ doc, selected, setSelected, filter, setFilter, mutate }: any) {
  const categories = useMemo(() => ["all", ...elementCategories(doc)], [doc]);
  const selectedIndex = selected.elements ?? -1;
  const items = (doc.elementOverrides || [])
    .map((element: any, index: number) => ({ element, index, visual: resolveElementVisual(doc, element) }))
    .filter(({ element, visual }: any) => {
      if (filter.category !== "all" && visual.category !== filter.category) return false;
      const query = filter.element.trim().toLowerCase();
      return !query || `${element.type} ${visual.label} ${visual.category} ${visual.tag}`.toLowerCase().includes(query);
    });
  const element = doc.elementOverrides?.[selectedIndex];
  const visual = element ? resolveElementVisual(doc, element) : null;

  function updateElement(mutator: (element: any) => void) {
    mutate((draft: any) => mutator(draft.elementOverrides[selectedIndex]));
  }

  return (
    <>
      <SectionHead
        title="Elements"
        description="Per-type visual overrides, icons, colors, shapes, tags, roles, and card fields."
        action={
          <button
            className="primary-action"
            onClick={() => {
              const type = window.prompt("EClass type name");
              if (!type?.trim()) return;
              mutate((draft: any) => {
                draft.elementOverrides.push({
                  type: type.trim(),
                  label: type.trim(),
                  icon: "category",
                  color: themeColorPairFromHex("#2563eb"),
                  category: "New",
                  primitive: "concept-card",
                  visualRole: "node",
                  card: { tag: type.slice(0, 4).toUpperCase(), lineFields: ["name"], detailFields: [] },
                });
                setSelected({ elements: draft.elementOverrides.length - 1 });
              });
            }}
          >
            <Plus size={16} />
            <span>Add element</span>
          </button>
        }
      />
      <div className="cvs-split">
        <div>
          <div className="cvs-filterbar">
            <input value={filter.element} onChange={(event) => setFilter({ ...filter, element: event.target.value })} placeholder="Search types" />
            <div className="chip-filters">
              {categories.map((category) => (
                <button
                  className={filter.category === category ? "active" : ""}
                  key={category}
                  onClick={() => setFilter({ ...filter, category })}
                >
                  {category === "all" ? "All" : category}
                </button>
              ))}
            </div>
          </div>
          <div className="element-gallery">
            {items.map(({ element, index, visual }: any) => (
              <button className={index === selectedIndex ? "element-card active" : "element-card"} key={index} onClick={() => setSelected({ elements: index })}>
                <NodePreview visual={visual} selected={index === selectedIndex} />
                <strong>{element.type}</strong>
                <span>{visual.category || "-"}</span>
              </button>
            ))}
            {items.length === 0 && <div className="empty-inline">No elements match.</div>}
          </div>
        </div>
        <aside className="panel cvs-inspector">
          {!element || !visual ? (
            <div className="empty-inline">Select an element to edit its visual syntax.</div>
          ) : (
            <>
              <NodePreview visual={visual} selected />
              <Field label="Type name" value={element.type} onChange={(value) => updateElement((item) => (item.type = value))} />
              <Field label="Label" value={element.label || element.displayName} onChange={(value) => updateElement((item) => ((item.label = value), (item.displayName = value)))} />
              <Field label="Category" value={element.category} onChange={(value) => updateElement((item) => (item.category = value))} />
              <SelectField label="Visual role" value={element.visualRole || "node"} options={visualRoles} onChange={(value) => updateElement((item) => (item.visualRole = value))} />
              <ThemeColorFields value={element.color || visual.color} onChange={(value) => updateElement((item) => (item.color = value))} />
              <SelectField label="Primitive / shape" value={element.primitive || ""} options={shapePresets} onChange={(value) => updateElement((item) => (item.primitive = value))} />
              <Field label="Card tag" value={element.card?.tag} onChange={(value) => updateElement((item) => ((item.card ??= {}), (item.card.tag = value)))} />
              <ChipEditor label="Line fields" values={element.card?.lineFields || []} onChange={(values) => updateElement((item) => ((item.card ??= {}), (item.card.lineFields = values)))} />
              <ChipEditor label="Detail fields" values={element.card?.detailFields || []} onChange={(values) => updateElement((item) => ((item.card ??= {}), (item.card.detailFields = values)))} />
              <CheckField label="Contained only" checked={element.containedOnly} onChange={(value) => updateElement((item) => (item.containedOnly = value))} />
              <CheckField label="Support only" checked={element.supportOnly} onChange={(value) => updateElement((item) => (item.supportOnly = value))} />
              <CheckField label="Creatable" checked={element.creatable !== false} onChange={(value) => updateElement((item) => (item.creatable = value))} />
              <details className="icon-picker">
                <summary>Icon library</summary>
                <div className="icon-grid">
                  {commonIcons.map((icon) => (
                    <button className={element.icon === icon ? "active" : ""} key={icon} onClick={() => updateElement((item) => (item.icon = icon))} title={icon}>
                      <img src={iconUrl(icon)} alt="" />
                    </button>
                  ))}
                </div>
              </details>
              <button
                className="danger-action"
                onClick={() => {
                  if (!window.confirm("Delete this element override?")) return;
                  mutate((draft: any) => draft.elementOverrides.splice(selectedIndex, 1));
                  setSelected({ elements: -1 });
                }}
              >
                <Trash2 size={16} />
                <span>Delete element</span>
              </button>
            </>
          )}
        </aside>
      </div>
    </>
  );
}

function PrimitivesEditor({ doc, selected, setSelected, mutate }: any) {
  const key = selected.primitives || "";
  const primitive = key ? doc.primitives?.[key] : null;
  return (
    <>
      <SectionHead
        title="Primitives"
        description="Reusable geometry and shape building blocks."
        action={
          <button
            className="primary-action"
            onClick={() => {
              const nextKey = window.prompt("Primitive key");
              if (!nextKey?.trim()) return;
              mutate((draft: any) => (draft.primitives[nextKey.trim()] = { geometry: "rounded-rectangle", cornerRadius: 8 }));
              setSelected({ primitives: nextKey.trim() });
            }}
          >
            <Plus size={16} />
            <span>Add primitive</span>
          </button>
        }
      />
      <div className="cvs-split">
        <div className="primitive-gallery">
          {Object.entries(doc.primitives || {}).map(([itemKey, value]: any) => (
            <button className={key === itemKey ? "primitive-card active" : "primitive-card"} key={itemKey} onClick={() => setSelected({ primitives: itemKey })}>
              <PrimitivePreview primitive={value} />
              <strong>{itemKey}</strong>
              <span>{value?.geometry || "rectangle"}</span>
            </button>
          ))}
        </div>
        <aside className="panel cvs-inspector">
          {!primitive ? (
            <div className="empty-inline">Select a primitive shape.</div>
          ) : (
            <>
              <Field
                label="Key"
                value={key}
                onChange={(value) => {
                  if (!value.trim() || value === key) return;
                  mutate((draft: any) => {
                    draft.primitives[value.trim()] = draft.primitives[key];
                    delete draft.primitives[key];
                  });
                  setSelected({ primitives: value.trim() });
                }}
              />
              <SelectField label="Geometry" value={primitive.geometry} options={geometries} onChange={(value) => mutate((draft: any) => (draft.primitives[key].geometry = value))} />
              <Field label="Corner radius" type="number" value={primitive.cornerRadius ?? 0} onChange={(value) => mutate((draft: any) => (draft.primitives[key].cornerRadius = Number(value)))} />
              <Field label="Description" multiline value={primitive.description || ""} onChange={(value) => mutate((draft: any) => (draft.primitives[key].description = value))} />
              <button
                className="danger-action"
                onClick={() => {
                  if (!window.confirm("Delete primitive?")) return;
                  mutate((draft: any) => delete draft.primitives[key]);
                  setSelected({ primitives: "" });
                }}
              >
                <Trash2 size={16} />
                <span>Delete primitive</span>
              </button>
            </>
          )}
        </aside>
      </div>
    </>
  );
}

function PackageRulesEditor({ doc, selected, setSelected, mutate }: any) {
  const index = selected.packages ?? -1;
  const rule = doc.elementVisualRules?.[index];
  return (
    <>
      <SectionHead
        title="Package Rules"
        description="Visual defaults by metamodel package."
        action={
          <button
            className="primary-action"
            onClick={() => {
              mutate((draft: any) => {
                draft.elementVisualRules.push({
                  match: { packages: ["newpackage"] },
                  metadata: { icon: "category", color: themeColorPairFromHex("#475569"), category: "New", notation: { tag: "NEW", shape: "concept-card", lineFields: [] } },
                });
                setSelected({ packages: draft.elementVisualRules.length - 1 });
              });
            }}
          >
            <Plus size={16} />
            <span>Add rule</span>
          </button>
        }
      />
      <div className="cvs-split">
        <div className="package-list">
          {(doc.elementVisualRules || []).map((item: any, itemIndex: number) => (
            <button className={index === itemIndex ? "package-rule-card active" : "package-rule-card"} key={itemIndex} onClick={() => setSelected({ packages: itemIndex })}>
              <span className="swatch" style={{ background: normalizeThemeColor(item.metadata?.color).light }} />
              <strong>{(item.match?.packages || []).join(", ") || "-"}</strong>
              <span>{item.metadata?.category || ""}</span>
            </button>
          ))}
        </div>
        <aside className="panel cvs-inspector">
          {!rule ? (
            <div className="empty-inline">Select a package rule.</div>
          ) : (
            <>
              <Field label="Packages" value={(rule.match?.packages || []).join(", ")} onChange={(value) => mutate((draft: any) => ((draft.elementVisualRules[index].match ??= {}), (draft.elementVisualRules[index].match.packages = splitList(value))))} />
              <Field label="Category" value={rule.metadata?.category} onChange={(value) => mutate((draft: any) => ((draft.elementVisualRules[index].metadata ??= {}), (draft.elementVisualRules[index].metadata.category = value)))} />
              <Field label="Icon" value={rule.metadata?.icon} onChange={(value) => mutate((draft: any) => ((draft.elementVisualRules[index].metadata ??= {}), (draft.elementVisualRules[index].metadata.icon = value)))} />
              <ThemeColorFields value={rule.metadata?.color} onChange={(value) => mutate((draft: any) => ((draft.elementVisualRules[index].metadata ??= {}), (draft.elementVisualRules[index].metadata.color = value)))} />
              <Field label="Shape" value={rule.metadata?.notation?.shape} onChange={(value) => mutate((draft: any) => ((draft.elementVisualRules[index].metadata ??= {}), (draft.elementVisualRules[index].metadata.notation ??= {}), (draft.elementVisualRules[index].metadata.notation.shape = value)))} />
              <Field label="Tag" value={rule.metadata?.notation?.tag} onChange={(value) => mutate((draft: any) => ((draft.elementVisualRules[index].metadata ??= {}), (draft.elementVisualRules[index].metadata.notation ??= {}), (draft.elementVisualRules[index].metadata.notation.tag = value)))} />
              <Field label="Line fields" value={(rule.metadata?.notation?.lineFields || []).join(", ")} onChange={(value) => mutate((draft: any) => ((draft.elementVisualRules[index].metadata ??= {}), (draft.elementVisualRules[index].metadata.notation ??= {}), (draft.elementVisualRules[index].metadata.notation.lineFields = splitList(value))))} />
              <button className="danger-action" onClick={() => (window.confirm("Delete package rule?") ? (mutate((draft: any) => draft.elementVisualRules.splice(index, 1)), setSelected({ packages: -1 })) : undefined)}>
                <Trash2 size={16} />
                <span>Delete rule</span>
              </button>
            </>
          )}
        </aside>
      </div>
    </>
  );
}

function ViewpointsEditor({ doc, selected, setSelected, mutate }: any) {
  const index = selected.viewpoints ?? -1;
  const view = doc.viewpoints?.[index];
  const allTypes = (doc.elementOverrides || []).map((item: any) => item.type).filter(Boolean);
  const toggle = (field: "palette" | "elementTypes", type: string) =>
    mutate((draft: any) => {
      const list = new Set(draft.viewpoints[index][field] || []);
      list.has(type) ? list.delete(type) : list.add(type);
      draft.viewpoints[index][field] = [...list];
    });
  return (
    <>
      <SectionHead
        title="Viewpoints"
        description="Named views, palettes, scoped element types, relationship kinds, and layout hints."
        action={
          <button
            className="primary-action"
            onClick={() => {
              const id = window.prompt("View id");
              if (!id?.trim()) return;
              mutate((draft: any) => {
                draft.viewpoints.push({ id: id.trim(), displayName: id.trim(), viewType: id.trim().toUpperCase().replaceAll("-", "_"), viewpoint: id.trim(), elementTypes: [], palette: [], relationshipKinds: [], layoutHint: "DEFAULT_LAYERED" });
                setSelected({ viewpoints: draft.viewpoints.length - 1 });
              });
            }}
          >
            <Plus size={16} />
            <span>Add viewpoint</span>
          </button>
        }
      />
      <div className="view-strip">
        {(doc.viewpoints || []).map((item: any, itemIndex: number) => (
          <button className={index === itemIndex ? "view-card active" : "view-card"} key={itemIndex} onClick={() => setSelected({ viewpoints: itemIndex })}>
            <span>{item.id}</span>
            <strong>{item.displayName}</strong>
            <small>{(item.palette || []).length} palette · {(item.elementTypes || []).length} scoped</small>
          </button>
        ))}
      </div>
      {!view ? (
        <section className="panel empty-inline">Select a viewpoint.</section>
      ) : (
        <section className="panel view-editor">
          <div className="view-fields">
            {["id", "displayName", "viewType", "viewpoint"].map((field) => (
              <Field key={field} label={field} value={view[field]} onChange={(value) => mutate((draft: any) => (draft.viewpoints[index][field] = value))} />
            ))}
            <SelectField label="Layout hint" value={view.layoutHint || "DEFAULT_LAYERED"} options={layoutHints} onChange={(value) => mutate((draft: any) => (draft.viewpoints[index].layoutHint = value))} />
          </div>
          <div className="dual-list">
            <TypePool title="Palette" active={view.palette || []} allTypes={allTypes} onToggle={(type) => toggle("palette", type)} />
            <TypePool title="Scoped element types" active={view.elementTypes || []} allTypes={allTypes} onToggle={(type) => toggle("elementTypes", type)} />
          </div>
          <ChipEditor label="Relationship kinds" values={view.relationshipKinds || []} onChange={(values) => mutate((draft: any) => (draft.viewpoints[index].relationshipKinds = values))} />
          <button className="danger-action" onClick={() => (window.confirm("Delete viewpoint?") ? (mutate((draft: any) => draft.viewpoints.splice(index, 1)), setSelected({ viewpoints: -1 })) : undefined)}>
            <Trash2 size={16} />
            <span>Delete viewpoint</span>
          </button>
        </section>
      )}
    </>
  );
}

function CanvasEditor({ doc, mutate }: any) {
  const policy = doc.canvasPolicy || {};
  const roles = policy.roleSizes || {};
  const fields = ["lowDetailBelow", "highDetailAtOrAbove", "edgeLabelsAtOrAbove", "denseEdgeThreshold", "denseEdgeLabelsAtOrAbove", "veryDenseEdgeThreshold", "veryDenseEdgeLabelsAtOrAbove"];
  return (
    <>
      <SectionHead title="Canvas Policy" description="Role sizes and semantic zoom thresholds." />
      <div className="canvas-grid">
        <section className="panel">
          <h2>Role sizes</h2>
          <div className="role-size-row">
            {Object.entries(roles).map(([role, size]: any) => (
              <div className="role-size-box" key={role} style={{ width: Math.min(Number(size.width) || 120, 180) / 1.4, height: Math.min(Number(size.height) || 80, 140) / 1.4 }}>
                <strong>{role}</strong>
                <span>{size.width}x{size.height}</span>
              </div>
            ))}
          </div>
          {Object.entries(roles).map(([role, size]: any) => (
            <div className="role-fields" key={role}>
              <h3>{role}</h3>
              <Field label="Width" type="number" value={size.width} onChange={(value) => mutate((draft: any) => (draft.canvasPolicy.roleSizes[role].width = Number(value)))} />
              <Field label="Height" type="number" value={size.height} onChange={(value) => mutate((draft: any) => (draft.canvasPolicy.roleSizes[role].height = Number(value)))} />
            </div>
          ))}
        </section>
        <section className="panel">
          <h2>Semantic zoom</h2>
          {fields.map((field) => (
            <Field key={field} label={field} type="number" value={policy[field]} onChange={(value) => mutate((draft: any) => (draft.canvasPolicy[field] = Number(value)))} />
          ))}
        </section>
      </div>
    </>
  );
}

function RelationshipsEditor({ doc, selected, setSelected, mutate }: any) {
  const index = selected.relationships ?? -1;
  const rule = doc.relationshipVisualRules?.[index];
  return (
    <>
      <SectionHead
        title="Relationships"
        description="Kinds, labels, and edge presentation rules."
        action={
          <button
            className="primary-action"
            onClick={() => {
              mutate((draft: any) => {
                draft.relationshipVisualRules.push({ matchKinds: ["NEW_KIND"], className: "edge-custom", stroke: themeColorPairFromHex("#64748b"), lineWidth: 2, markerEnd: "arrow" });
                setSelected({ relationships: draft.relationshipVisualRules.length - 1 });
              });
            }}
          >
            <Plus size={16} />
            <span>Add visual rule</span>
          </button>
        }
      />
      <section className="panel relationships-header">
        <Field label="Kinds" value={(doc.relationshipKinds || []).join(", ")} onChange={(value) => mutate((draft: any) => (draft.relationshipKinds = splitList(value)))} />
        <Field
          label="Kind labels"
          multiline
          value={Object.entries(doc.relationshipKindLabels || {}).map(([key, value]) => `${key} -> ${value}`).join("\n")}
          onChange={(value) =>
            mutate((draft: any) => {
              const labels: Record<string, string> = {};
              value.split("\n").forEach((line) => {
                const [key, ...rest] = line.split("->");
                if (key.trim()) labels[key.trim()] = rest.join("->").trim();
              });
              draft.relationshipKindLabels = labels;
            })
          }
        />
      </section>
      <div className="cvs-split">
        <div className="edge-rule-gallery">
          {(doc.relationshipVisualRules || []).map((item: any, itemIndex: number) => (
            <button className={index === itemIndex ? "edge-card active" : "edge-card"} key={itemIndex} onClick={() => setSelected({ relationships: itemIndex })}>
              <EdgePreview rule={item} />
              <span>{(item.matchKinds || []).join(", ") || "edge"}</span>
            </button>
          ))}
        </div>
        <aside className="panel cvs-inspector">
          {!rule ? (
            <div className="empty-inline">Select an edge visual rule.</div>
          ) : (
            <>
              <Field label="Match kinds" value={(rule.matchKinds || []).join(", ")} onChange={(value) => mutate((draft: any) => (draft.relationshipVisualRules[index].matchKinds = splitList(value)))} />
              <Field label="Match EClasses" value={(rule.matchEClasses || []).join(", ")} onChange={(value) => mutate((draft: any) => (draft.relationshipVisualRules[index].matchEClasses = splitList(value)))} />
              <Field label="CSS class" value={rule.className} onChange={(value) => mutate((draft: any) => (draft.relationshipVisualRules[index].className = value))} />
              <ThemeColorFields value={rule.stroke} onChange={(value) => mutate((draft: any) => (draft.relationshipVisualRules[index].stroke = value))} />
              <Field label="Line width" type="number" value={rule.lineWidth ?? 2} onChange={(value) => mutate((draft: any) => (draft.relationshipVisualRules[index].lineWidth = Number(value)))} />
              <Field label="Line dash" value={(rule.lineDash || []).join(", ")} onChange={(value) => mutate((draft: any) => (draft.relationshipVisualRules[index].lineDash = splitList(value).map(Number).filter((item) => !Number.isNaN(item))))} />
              <SelectField label="Marker end" value={rule.markerEnd || "arrow"} options={markers} onChange={(value) => mutate((draft: any) => (draft.relationshipVisualRules[index].markerEnd = value))} />
              <SelectField label="Marker start" value={rule.markerStart || ""} options={markers} onChange={(value) => mutate((draft: any) => (draft.relationshipVisualRules[index].markerStart = value))} />
              <button className="danger-action" onClick={() => (window.confirm("Delete visual rule?") ? (mutate((draft: any) => draft.relationshipVisualRules.splice(index, 1)), setSelected({ relationships: -1 })) : undefined)}>
                <Trash2 size={16} />
                <span>Delete rule</span>
              </button>
            </>
          )}
        </aside>
      </div>
    </>
  );
}

function BadgesEditor({ doc, selected, setSelected, mutate }: any) {
  const index = selected.badges ?? -1;
  const rule = doc.badgeRules?.[index];
  return (
    <>
      <SectionHead
        title="Badges"
        description="Attribute-driven node badge rules."
        action={
          <button className="primary-action" onClick={() => mutate((draft: any) => (draft.badgeRules.push({ field: "status", useValue: true }), setSelected({ badges: draft.badgeRules.length - 1 })))}>
            <Plus size={16} />
            <span>Add badge rule</span>
          </button>
        }
      />
      <div className="cvs-split">
        <div className="badge-list">
          {(doc.badgeRules || []).map((item: any, itemIndex: number) => (
            <button className={index === itemIndex ? "badge-card active" : "badge-card"} key={itemIndex} onClick={() => setSelected({ badges: itemIndex })}>
              <span className="badge-preview">{item.label || item.field}</span>
              <strong>{item.field}</strong>
              <small>{item.useValue ? "uses value" : item.when === true ? "when true" : ""}</small>
            </button>
          ))}
        </div>
        <aside className="panel cvs-inspector">
          {!rule ? (
            <div className="empty-inline">Select a badge rule.</div>
          ) : (
            <>
              <Field label="Field" value={rule.field} onChange={(value) => mutate((draft: any) => (draft.badgeRules[index].field = value))} />
              <Field label="Label" value={rule.label || ""} onChange={(value) => mutate((draft: any) => (draft.badgeRules[index].label = value))} />
              <CheckField label="Use field value as label" checked={rule.useValue} onChange={(value) => mutate((draft: any) => (draft.badgeRules[index].useValue = value))} />
              <CheckField label="Show when true" checked={rule.when === true} onChange={(value) => mutate((draft: any) => (draft.badgeRules[index].when = value ? true : undefined))} />
              <button className="danger-action" onClick={() => (window.confirm("Delete badge rule?") ? (mutate((draft: any) => draft.badgeRules.splice(index, 1)), setSelected({ badges: -1 })) : undefined)}>
                <Trash2 size={16} />
                <span>Delete rule</span>
              </button>
            </>
          )}
        </aside>
      </div>
    </>
  );
}

function AdvancedEditor({ doc, mutate, onError }: any) {
  const [raw, setRaw] = useState(JSON.stringify(doc, null, 2));
  useEffect(() => setRaw(JSON.stringify(doc, null, 2)), [doc]);
  const defaults = doc.elementVisualDefaults || {};
  const notation = defaults.notation || {};
  return (
    <>
      <SectionHead title="Advanced" description="Defaults, templates, and raw JSON." />
      <div className="advanced-grid">
        <section className="panel">
          <h2>Element visual defaults</h2>
          <Field label="Icon" value={defaults.icon} onChange={(value) => mutate((draft: any) => ((draft.elementVisualDefaults ??= {}), (draft.elementVisualDefaults.icon = value)))} />
          <ThemeColorFields value={defaults.color} onChange={(value) => mutate((draft: any) => ((draft.elementVisualDefaults ??= {}), (draft.elementVisualDefaults.color = value)))} />
          <Field label="Category" value={defaults.category} onChange={(value) => mutate((draft: any) => ((draft.elementVisualDefaults ??= {}), (draft.elementVisualDefaults.category = value)))} />
          <Field label="Shape" value={notation.shape} onChange={(value) => mutate((draft: any) => ((draft.elementVisualDefaults ??= {}), (draft.elementVisualDefaults.notation ??= {}), (draft.elementVisualDefaults.notation.shape = value)))} />
          <Field label="Tag" value={notation.tag} onChange={(value) => mutate((draft: any) => ((draft.elementVisualDefaults ??= {}), (draft.elementVisualDefaults.notation ??= {}), (draft.elementVisualDefaults.notation.tag = value)))} />
          <Field label="Line fields" value={(notation.lineFields || []).join(", ")} onChange={(value) => mutate((draft: any) => ((draft.elementVisualDefaults ??= {}), (draft.elementVisualDefaults.notation ??= {}), (draft.elementVisualDefaults.notation.lineFields = splitList(value))))} />
        </section>
        <section className="panel cvs-wide">
          <h2>Raw document JSON</h2>
          <textarea className="raw-json" spellCheck={false} value={raw} onChange={(event) => setRaw(event.target.value)} />
          <button
            className="primary-action"
            onClick={() => {
              try {
                const parsed = JSON.parse(raw);
                mutate((draft: any) => {
                  Object.keys(draft).forEach((key) => delete draft[key]);
                  Object.assign(draft, parsed);
                });
              } catch (err) {
                onError(err);
              }
            }}
          >
            <Save size={16} />
            <span>Apply JSON</span>
          </button>
        </section>
      </div>
    </>
  );
}

function SectionHead({ title, description, action }: { title: string; description: string; action?: ReactNode }) {
  return (
    <header className="cvs-section-head">
      <div>
        <h2>{title}</h2>
        <p>{description}</p>
      </div>
      {action}
    </header>
  );
}

function Field({
  label,
  value,
  onChange,
  type = "text",
  multiline = false,
}: {
  label: string;
  value: unknown;
  onChange: (value: string) => void;
  type?: string;
  multiline?: boolean;
}) {
  const textValue = value == null ? "" : String(value);
  return (
    <label className="admin-field">
      <span>{label}</span>
      {multiline ? (
        <textarea value={textValue} onChange={(event) => onChange(event.target.value)} />
      ) : (
        <input type={type} value={textValue} onChange={(event) => onChange(event.target.value)} />
      )}
    </label>
  );
}

function SelectField({
  label,
  value,
  options,
  onChange,
}: {
  label: string;
  value: unknown;
  options: string[];
  onChange: (value: string) => void;
}) {
  const textValue = value == null ? "" : String(value);
  return (
    <label className="admin-field">
      <span>{label}</span>
      <select value={textValue} onChange={(event) => onChange(event.target.value)}>
        {options.map((option: string) => (
          <option key={option} value={option}>
            {option || "-"}
          </option>
        ))}
      </select>
    </label>
  );
}

function CheckField({
  label,
  checked,
  onChange,
}: {
  label: string;
  checked: unknown;
  onChange: (value: boolean) => void;
}) {
  return (
    <label className="check-field">
      <input type="checkbox" checked={Boolean(checked)} onChange={(event) => onChange(event.target.checked)} />
      <span>{label}</span>
    </label>
  );
}

function ThemeColorFields({
  value,
  onChange,
}: {
  value: unknown;
  onChange: (value: { light: string; dark: string }) => void;
}) {
  const pair = normalizeThemeColor(value);
  return (
    <div className="theme-color-fields">
      <label className="admin-field">
        <span>Color light</span>
        <input type="color" value={pair.light} onChange={(event) => onChange({ ...pair, light: event.target.value })} />
      </label>
      <label className="admin-field">
        <span>Color dark</span>
        <input type="color" value={pair.dark} onChange={(event) => onChange({ ...pair, dark: event.target.value })} />
      </label>
    </div>
  );
}

function ChipEditor({
  label,
  values,
  onChange,
}: {
  label: string;
  values: string[];
  onChange: (values: string[]) => void;
}) {
  const [next, setNext] = useState("");
  return (
    <div className="chip-editor">
      <span>{label}</span>
      <div>
        {(values || []).map((value: string, index: number) => (
          <button key={`${value}-${index}`} onClick={() => onChange(values.filter((_: string, itemIndex: number) => itemIndex !== index))}>
            {value}
            <Trash2 size={12} />
          </button>
        ))}
      </div>
      <input
        value={next}
        placeholder="Add value + Enter"
        onChange={(event) => setNext(event.target.value)}
        onKeyDown={(event) => {
          if (event.key !== "Enter" || !next.trim()) return;
          event.preventDefault();
          if (!values.includes(next.trim())) onChange([...values, next.trim()]);
          setNext("");
        }}
      />
    </div>
  );
}

function TypePool({
  title,
  active,
  allTypes,
  onToggle,
}: {
  title: string;
  active: string[];
  allTypes: string[];
  onToggle: (type: string) => void;
}) {
  const activeSet = new Set(active || []);
  return (
    <div className="type-pool">
      <h3>{title}</h3>
      <div>
        {allTypes.map((type: string) => (
          <button className={activeSet.has(type) ? "active" : ""} key={type} onClick={() => onToggle(type)}>
            {type}
          </button>
        ))}
      </div>
    </div>
  );
}

function NodePreview({ visual, selected = false }: { visual: any; selected?: boolean }) {
  const color = normalizeThemeColor(visual.color).light;
  return (
    <div className={selected ? "node-preview selected" : "node-preview"} style={{ borderColor: color }}>
      <div className="node-preview-head" style={{ background: color }}>
        <img src={iconUrl(visual.icon)} alt="" />
        <strong>{visual.label || "Element"}</strong>
        <span>{visual.tag || String(visual.primitive || "TYPE").slice(0, 4).toUpperCase()}</span>
      </div>
      <small>{(visual.lineFields || []).slice(0, 2).join(" · ") || "line fields"}</small>
      <em>{visual.visualRole || "node"}</em>
    </div>
  );
}

function PrimitivePreview({ primitive }: { primitive: any }) {
  return <div className={`primitive-preview primitive-${primitive?.geometry || "rectangle"}`} />;
}

function EdgePreview({ rule }: { rule: any }) {
  const color = normalizeThemeColor(rule?.stroke, "#64748b").light;
  return (
    <div className="edge-preview">
      <span style={{ borderTopColor: color, borderTopWidth: `${rule?.lineWidth || 2}px`, borderTopStyle: rule?.lineDash?.length ? "dashed" : "solid" }} />
      <i style={{ borderLeftColor: color }} />
    </div>
  );
}

function Pills({ values, empty }: { values: string[]; empty: string }) {
  if (values.length === 0) return <span className="muted">{empty}</span>;
  return (
    <div className="pills">
      {values.map((value) => (
        <span className="pill" key={value}>
          {value}
        </span>
      ))}
    </div>
  );
}

function createEmptyDoc(level = "cim") {
  const meta = metamodel[level] || metamodel.cim;
  return {
    cvsVersion: 2,
    displayName: meta.displayName,
    metamodelRef: { level, ecore: meta.ecore, nsUri: meta.nsUri },
    primitives: { "concept-card": { geometry: "rounded-rectangle", cornerRadius: 8, description: "Default concept card" } },
    notationPrimitives: {},
    elementVisualDefaults: { icon: "category", color: { light: "#475569", dark: "#94a3b8" }, category: meta.displayName, notation: { tag: meta.displayName, shape: "concept-card", lineFields: ["name", "summary"] } },
    elementVisualRules: [],
    elementOverrides: [],
    referenceMappings: [],
    relationshipMappings: [],
    relationshipRules: [],
    relationshipKinds: ["CONTAINS", "DEPENDS_ON", "TRACE"],
    relationshipKindLabels: {},
    relationshipVisualRules: [],
    semanticReferenceRules: [],
    semanticEdgeObjectRules: [],
    badgeRules: [],
    viewpoints: [{ id: "main", displayName: "Main Canvas", viewType: "MAIN", viewpoint: "main", elementTypes: [], palette: [], relationshipKinds: [], layoutHint: "DEFAULT_LAYERED" }],
    canvasPolicy: { roleSizes: { node: { width: 120, height: 118 }, container: { width: 316, height: 168 }, detail: { width: 228, height: 84 } }, lowDetailBelow: 0.42, highDetailAtOrAbove: 1.35, edgeLabelsAtOrAbove: 0.8, denseEdgeThreshold: 700, denseEdgeLabelsAtOrAbove: 1.3, veryDenseEdgeThreshold: 1600, veryDenseEdgeLabelsAtOrAbove: 1.7 },
    boundedContext: { enabled: false },
    complexityManagement: [],
    workbench: {},
    scaffoldRecipes: [],
    constraints: [],
    strictnessModes: ["exploration", "methodology", "production"],
    rootTemplate: { eClass: meta.rootEClass, modelLevel: meta.modelLevel, name: "", diagram: { elements: [], relationships: [] }, graph: { elements: [], relationships: [], traceLinks: [], assumptions: [], validationIssues: [], manualBacklog: [] }, views: [], fragments: [] },
  };
}

function normalizeLoadedDoc(raw: CvsDocument) {
  const doc = structuredClone(raw);
  doc.cvsVersion ??= 2;
  doc.primitives ??= {};
  doc.notationPrimitives = Object.keys(doc.notationPrimitives || {}).length ? doc.notationPrimitives : { ...doc.primitives };
  doc.elementOverrides ??= [];
  doc.elementVisualRules ??= [];
  doc.relationshipVisualRules ??= [];
  doc.relationshipKinds ??= [];
  doc.relationshipKindLabels ??= {};
  doc.badgeRules ??= [];
  doc.viewpoints ??= [];
  doc.canvasPolicy ??= createEmptyDoc(levelFromDoc(doc)).canvasPolicy;
  return doc;
}

function levelFromDoc(doc: CvsDocument) {
  return String(doc?.metamodelRef?.level || "cim").toLowerCase();
}

function elementCategories(doc: CvsDocument): string[] {
  return [
    ...new Set<string>((doc.elementOverrides || []).map((item: any) => String(item.category || "")).filter(Boolean)),
  ].sort();
}

function resolveElementVisual(doc: CvsDocument, element: any) {
  const defaults = doc.elementVisualDefaults || {};
  return {
    icon: element?.icon || defaults.icon || "category",
    color: element?.color || defaults.color || "#475569",
    category: element?.category || defaults.category || "",
    visualRole: element?.visualRole || "node",
    primitive: element?.primitive || defaults.notation?.shape || "concept-card",
    tag: element?.card?.tag || defaults.notation?.tag || "",
    lineFields: element?.card?.lineFields || defaults.notation?.lineFields || [],
    label: element?.displayName || element?.label || element?.type || "Element",
  };
}

function normalizeThemeColor(value: any, fallback = "#475569") {
  if (value && typeof value === "object") {
    const light = String(value.light || value.dark || fallback).trim();
    const dark = String(value.dark || value.light || fallback).trim();
    return { light, dark };
  }
  const text = String(value || fallback).trim();
  return { light: text, dark: text };
}

function themeColorPairFromHex(hex: string) {
  const normalized = String(hex || "").trim();
  return { light: normalized || "#475569", dark: normalized || "#94a3b8" };
}

function splitList(value: string) {
  return String(value || "")
    .split(",")
    .map((item) => item.trim())
    .filter(Boolean);
}

function iconUrl(name: string) {
  return `/icons/${String(name || "placeholder").trim() || "placeholder"}.svg`;
}
