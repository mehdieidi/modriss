import {
  BadgeCheck,
  Boxes,
  Braces,
  Download,
  Eye,
  FileJson,
  GitBranch,
  Grid3X3,
  Network,
  Palette,
  Plus,
  Save,
  SquareStack,
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
  | "views"
  | "containers"
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
  { id: "views", label: "Views", icon: <Grid3X3 size={16} /> },
  { id: "containers", label: "Containers", icon: <SquareStack size={16} /> },
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
  const [activationInfo, setActivationInfo] = useState("");
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
      setActivationInfo("");
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
    setActivationInfo("");
    setSection("overview");
    setSelected({});
  }

  function mutate(mutator: (draft: CvsDocument) => void) {
    setDoc((current) => {
      if (!current) return current;
      const next = structuredClone(current);
      mutator(next);
      return next;
    });
    setDirty(true);
  }

  async function save() {
    if (!doc) return;
    setSaving(true);
    try {
      const activation = await api<{ activeFile: string; backupFile: string | null }>(`/api/admin/notation/${levelFromDoc(doc)}`, props.token, {
        method: "POST",
        body: JSON.stringify({
          document: doc,
          reason: "admin CVS editor update",
        }),
      });
      setDirty(false);
      setActivationInfo(
        activation?.backupFile
          ? `Active: ${activation.activeFile} · backup: ${activation.backupFile}`
          : `Active: ${activation?.activeFile || `${levelFromDoc(doc)}.cvs.json`}`,
      );
    } catch (err) {
      props.onError(err);
    } finally {
      setSaving(false);
    }
  }

  function exportDoc() {
    if (!doc) return;
    const blob = new Blob([JSON.stringify(stripEditorCatalog(doc), null, 2)], { type: "application/json" });
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
          {activationInfo && <span className="muted">{activationInfo}</span>}
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
              <span>{saving ? "Activating" : "Activate & backup"}</span>
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
    case "views":
      return <ViewsEditor {...props} />;
    case "containers":
      return <ContainersEditor {...props} />;
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
    ["Elements", doc.elements?.length || 0],
    ["Views", doc.views?.length || 0],
    ["Containers", doc.containers?.length || 0],
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
  const items = (doc.elements || [])
    .map((element: any, index: number) => ({ element, index, visual: resolveElementVisual(doc, element) }))
    .filter(({ element, visual }: any) => {
      if (filter.category !== "all" && visual.category !== filter.category) return false;
      const query = filter.element.trim().toLowerCase();
      return !query || `${element.type} ${visual.label} ${visual.category} ${visual.tag}`.toLowerCase().includes(query);
    });
  const element = doc.elements?.[selectedIndex];
  const visual = element ? resolveElementVisual(doc, element) : null;

  function updateElement(mutator: (element: any) => void) {
    mutate((draft: any) => mutator(draft.elements[selectedIndex]));
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
                draft.elements.push({
                  type: type.trim(),
                  label: type.trim(),
                  icon: "category",
                  color: themeColorPairFromHex("#2563eb"),
                  category: "New",
                  visualRole: "node",
                  visibleFields: ["name"],
                });
                setSelected({ elements: draft.elements.length - 1 });
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
                  if (!window.confirm("Delete this element visual definition?")) return;
                  mutate((draft: any) => draft.elements.splice(selectedIndex, 1));
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

function ViewsEditor({ doc, selected, setSelected, mutate }: any) {
  const index = selected.views ?? -1;
  const view = doc.views?.[index];
  const allTypes = (doc.elements || [])
    .filter(
      (item: any) =>
        item?.type &&
        item.creatable !== false &&
        item.containedOnly !== true &&
        item.supportOnly !== true &&
        item.relationshipElement !== true &&
        item.visualRole !== "relationship",
    )
    .map((item: any) => item.type);
  const toggle = (field: "palette" | "canvas", type: string) =>
    mutate((draft: any) => {
      const list = new Set(draft.views[index][field] || []);
      list.has(type) ? list.delete(type) : list.add(type);
      draft.views[index][field] = [...list];
    });
  return (
    <>
      <SectionHead
        title="Views"
        description="Choose the draggable palette and the element types rendered on each view canvas."
        action={
          <button
            className="primary-action"
            onClick={() => {
              const id = window.prompt("View id");
              if (!id?.trim()) return;
              mutate((draft: any) => {
                draft.views.push({ id: id.trim(), displayName: id.trim(), viewType: id.trim().toUpperCase().replaceAll("-", "_"), palette: [], canvas: [], relationshipKinds: [], layoutHint: "DEFAULT_LAYERED" });
                setSelected({ views: draft.views.length - 1 });
              });
            }}
          >
            <Plus size={16} />
            <span>Add view</span>
          </button>
        }
      />
      <div className="view-strip">
        {(doc.views || []).map((item: any, itemIndex: number) => (
          <button className={index === itemIndex ? "view-card active" : "view-card"} key={itemIndex} onClick={() => setSelected({ views: itemIndex })}>
            <span>{item.id}</span>
            <strong>{item.displayName}</strong>
            <small>{(item.palette || []).length} palette · {(item.canvas || []).length} canvas</small>
          </button>
        ))}
      </div>
      {!view ? (
        <section className="panel empty-inline">Select a view.</section>
      ) : (
        <section className="panel view-editor">
          <div className="view-fields">
            {["id", "displayName", "viewType"].map((field) => (
              <Field key={field} label={field} value={view[field]} onChange={(value) => mutate((draft: any) => (draft.views[index][field] = value))} />
            ))}
            <SelectField label="Layout hint" value={view.layoutHint || "DEFAULT_LAYERED"} options={layoutHints} onChange={(value) => mutate((draft: any) => (draft.views[index].layoutHint = value))} />
          </div>
          <div className="dual-list">
            <TypePool title="Palette" active={view.palette || []} allTypes={allTypes} onToggle={(type) => toggle("palette", type)} />
            <TypePool title="Canvas" active={view.canvas || []} allTypes={allTypes} onToggle={(type) => toggle("canvas", type)} />
          </div>
          <ChipEditor label="Relationship kinds" values={view.relationshipKinds || []} onChange={(values) => mutate((draft: any) => (draft.views[index].relationshipKinds = values))} />
          <button className="danger-action" onClick={() => (window.confirm("Delete view?") ? (mutate((draft: any) => draft.views.splice(index, 1)), setSelected({ views: -1 })) : undefined)}>
            <Trash2 size={16} />
            <span>Delete view</span>
          </button>
        </section>
      )}
    </>
  );
}

function ContainersEditor({ doc, selected, setSelected, mutate }: any) {
  const index = selected.containers ?? -1;
  const container = doc.containers?.[index];
  const allTypes = (doc.elements || []).map((item: any) => item.type).filter(Boolean);
  const ownerOptions = [...new Set<string>([container?.elementType, ...allTypes].filter(Boolean))];
  const toggle = (field: "palette" | "canvas", type: string) =>
    mutate((draft: any) => {
      const values = new Set(draft.containers[index][field] || []);
      values.has(type) ? values.delete(type) : values.add(type);
      draft.containers[index][field] = [...values];
    });
  return (
    <>
      <SectionHead
        title="Containers"
        description="Choose which element types appear in the palette and canvas after opening a container."
        action={
          <button
            className="primary-action"
            onClick={() => {
              const availableOwners = allTypes.filter((type: string) => !(doc.containers || []).some((item: any) => item.elementType === type));
              const type = window.prompt(`Container EClass (choose from: ${availableOwners.join(", ")})`, availableOwners[0] || "");
              if (!type?.trim()) return;
              if (!allTypes.includes(type.trim())) {
                window.alert("Choose an EClass from the metamodel-derived list.");
                return;
              }
              mutate((draft: any) => {
                if (draft.containers.some((item: any) => item.elementType === type.trim())) return;
                draft.containers.push({ elementType: type.trim(), palette: [], canvas: [], relationshipKinds: [], layoutHint: "CONTAINER" });
                setSelected({ containers: draft.containers.length - 1 });
              });
            }}
          >
            <Plus size={16} />
            <span>Add container</span>
          </button>
        }
      />
      <div className="view-strip">
        {(doc.containers || []).map((item: any, itemIndex: number) => (
          <button className={index === itemIndex ? "view-card active" : "view-card"} key={itemIndex} onClick={() => setSelected({ containers: itemIndex })}>
            <span>{item.elementType}</span>
            <strong>{doc.elements?.find((element: any) => element.type === item.elementType)?.displayName || item.elementType}</strong>
            <small>{(item.palette || []).length} palette · {(item.canvas || []).length} canvas</small>
          </button>
        ))}
      </div>
      {!container ? (
        <section className="panel empty-inline">Select a container profile.</section>
      ) : (
        <section className="panel view-editor">
          <SelectField label="Container element type" value={container.elementType} options={ownerOptions} onChange={(value) => mutate((draft: any) => (draft.containers[index].elementType = value))} />
          <SelectField label="Layout hint" value={container.layoutHint || "CONTAINER"} options={layoutHints} onChange={(value) => mutate((draft: any) => (draft.containers[index].layoutHint = value))} />
          <div className="dual-list">
            <TypePool title="Palette" active={container.palette || []} allTypes={allTypes} onToggle={(type) => toggle("palette", type)} />
            <TypePool title="Canvas" active={container.canvas || []} allTypes={allTypes} onToggle={(type) => toggle("canvas", type)} />
          </div>
          <div className="containment-facts">
            <strong>Ecore containment references</strong>
            {(doc.metamodelContainmentFeatures?.[container.elementType] || []).map((feature: any) => (
              <div key={feature.feature}><span>{feature.feature}</span><small>{feature.targetType} · {(feature.types || []).join(", ") || "no concrete types"}</small></div>
            ))}
            {!doc.metamodelContainmentFeatures?.[container.elementType]?.length && <small>No writable containment reference was derived for this owner.</small>}
          </div>
          <ChipEditor label="Relationship kinds" values={container.relationshipKinds || []} onChange={(values) => mutate((draft: any) => (draft.containers[index].relationshipKinds = values))} />
          <button className="ghost-action" onClick={() => mutate((draft: any) => (draft.containers[index].canvas = [...(draft.containers[index].palette || [])]))}>Use palette as canvas</button>
          <button className="danger-action" onClick={() => (window.confirm("Delete container profile?") ? (mutate((draft: any) => draft.containers.splice(index, 1)), setSelected({ containers: -1 })) : undefined)}>
            <Trash2 size={16} />
            <span>Delete container profile</span>
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
  const [mode, setMode] = useState<"catalog" | "styles" | "mappings">("catalog");
  const catalog = useMemo(() => buildRelationshipCatalog(doc), [doc]);
  const selectedCatalogKey = selected.relationshipCatalog || catalog[0]?.key;
  const relation = catalog.find((item: any) => item.key === selectedCatalogKey);
  const index = selected.relationships ?? -1;
  const rule = doc.relationshipVisualRules?.[index];
  const matchingRuleIndex = relation
    ? (doc.relationshipVisualRules || []).findIndex((item: any) => relationshipVisualRuleMatches(item, relation))
    : -1;
  const configuredRuleIndex = relation
    ? (doc.relationshipRules || []).findIndex((item: any) => relationshipRuleIsExact(item, relation))
    : -1;
  return (
    <>
      <SectionHead
        title="Relationships"
        description="Inspect every relationship derived from Ecore, then edit the CVS rules that map and render those real relationships."
        action={
          <button
            className="primary-action"
            onClick={() => {
              if (!relation) return;
              mutate((draft: any) => {
                draft.relationshipVisualRules.push({
                  matchKinds: relation.kinds,
                  ...(relation.eClass ? { matchEClasses: [relation.eClass] } : {}),
                  className: "edge-custom",
                  stroke: themeColorPairFromHex("#64748b"),
                  lineWidth: 2,
                  markerEnd: "arrow",
                });
                setSelected({ relationships: draft.relationshipVisualRules.length - 1 });
                setMode("styles");
              });
            }}
            disabled={!relation}
          >
            <Plus size={16} />
            <span>Add style for selected relation</span>
          </button>
        }
      />
      <section className="panel relationships-header">
        <div className="relationship-summary">
          <div>
            <strong>{catalog.length}</strong>
            <span>metamodel relationships</span>
          </div>
          <div>
            <strong>{(doc.relationshipVisualRules || []).length}</strong>
            <span>CVS edge styles</span>
          </div>
          <div>
            <strong>{(doc.semanticEdgeObjectRules || []).length}</strong>
            <span>configured edge mappings</span>
          </div>
        </div>
        <ChoiceChips
          label="Relationship kinds (metamodel/configuration values)"
          values={doc.relationshipKinds || []}
          options={buildRelationshipKindOptions(doc)}
          onChange={(values) => mutate((draft: any) => (draft.relationshipKinds = values))}
        />
        <div className="kind-label-grid">
          <strong>Kind labels</strong>
          {(doc.relationshipKinds || []).map((kind: string) => (
            <Field key={kind} label={kind} value={doc.relationshipKindLabels?.[kind] || ""} onChange={(value) => mutate((draft: any) => ((draft.relationshipKindLabels ??= {}), (draft.relationshipKindLabels[kind] = value)))} />
          ))}
        </div>
      </section>
      <div className="segmented-control relationship-tabs">
        <button className={mode === "catalog" ? "active" : ""} onClick={() => setMode("catalog")}>Metamodel catalog</button>
        <button className={mode === "styles" ? "active" : ""} onClick={() => setMode("styles")}>Edge styles</button>
        <button className={mode === "mappings" ? "active" : ""} onClick={() => setMode("mappings")}>Edge mappings</button>
      </div>
      {mode === "catalog" && (
        <div className="cvs-split relationship-catalog-layout">
          <div className="relationship-catalog">
            {catalog.map((item: any) => {
              const styleIndex = (doc.relationshipVisualRules || []).findIndex((visual: any) => relationshipVisualRuleMatches(visual, item));
              return (
                <button
                  className={selectedCatalogKey === item.key ? "relationship-card active" : "relationship-card"}
                  key={item.key}
                  onClick={() => setSelected({ relationshipCatalog: item.key, relationships: styleIndex })}
                >
                  <div className="relationship-card-heading">
                    <strong>{item.eClass || item.feature || "Reference relationship"}</strong>
                    <span>{item.sourceType} → {item.targetType}</span>
                  </div>
                  <small>{item.feature ? `feature: ${item.feature}` : "semantic edge object"}</small>
                  <Pills values={item.kinds} empty="kind inferred by metamodel" />
                  <em>{item.origin}</em>
                </button>
              );
            })}
          </div>
          <aside className="panel cvs-inspector">
            {!relation ? (
              <div className="empty-inline">No Ecore-derived relationship catalog is available for this document.</div>
            ) : (
              <>
                <div className="inspector-title">
                  <strong>{relation.eClass || relation.feature || "Relationship"}</strong>
                  <span>{relation.origin}</span>
                </div>
                <div className="relationship-facts">
                  <div><span>Source</span><strong>{relation.sourceType}</strong></div>
                  <div><span>Target</span><strong>{relation.targetType}</strong></div>
                  <div><span>Feature</span><strong>{relation.feature || "—"}</strong></div>
                  <div><span>Legal kinds</span><strong>{relation.kinds.join(", ") || "—"}</strong></div>
                </div>
                <p className="editor-note">These endpoints and legal kinds come from the Ecore references and edge EClasses. Edit their CVS presentation or configured edge mapping; do not create synthetic relationship types.</p>
                {configuredRuleIndex >= 0 ? (
                  <ChoiceChips
                    label="Configured allowed kinds"
                    values={doc.relationshipRules[configuredRuleIndex].allowedKinds || []}
                    options={doc.relationshipKinds || []}
                    onChange={(values) => mutate((draft: any) => (draft.relationshipRules[configuredRuleIndex].allowedKinds = values))}
                  />
                ) : (
                  <button className="ghost-action" onClick={() => mutate((draft: any) => {
                    draft.relationshipRules.push({ sourceType: relation.sourceType, targetType: relation.targetType, ...(relation.feature ? { feature: relation.feature } : {}), ...(relation.eClass ? { edgeObjectType: relation.eClass } : {}), allowedKinds: relation.kinds });
                  })}>Create CVS legality rule from Ecore relation</button>
                )}
                <button className="ghost-action" onClick={() => {
                  if (matchingRuleIndex >= 0) {
                    setSelected({ relationshipCatalog: relation.key, relationships: matchingRuleIndex });
                  } else {
                    mutate((draft: any) => {
                      draft.relationshipVisualRules.push({ matchKinds: relation.kinds, ...(relation.eClass ? { matchEClasses: [relation.eClass] } : {}), className: "edge-custom", stroke: themeColorPairFromHex("#64748b"), lineWidth: 2, markerEnd: "arrow" });
                      setSelected({ relationshipCatalog: relation.key, relationships: draft.relationshipVisualRules.length - 1 });
                    });
                    setMode("styles");
                  }
                }}>{matchingRuleIndex >= 0 ? "Edit matching edge style" : "Create edge style for this relation"}</button>
              </>
            )}
          </aside>
        </div>
      )}
      {mode === "styles" && (
        <div className="cvs-split">
          <div className="edge-rule-gallery">
            {(doc.relationshipVisualRules || []).map((item: any, itemIndex: number) => (
              <button className={index === itemIndex ? "edge-card active" : "edge-card"} key={itemIndex} onClick={() => setSelected({ relationships: itemIndex })}>
                <EdgePreview rule={item} />
                <span>{(item.matchKinds || []).join(", ") || "edge"}</span>
                <small>{(item.matchEClasses || []).join(", ") || "all matching EClasses"}</small>
              </button>
            ))}
          </div>
          <aside className="panel cvs-inspector">
            {!rule ? (
              <div className="empty-inline">Select an edge visual rule or select a metamodel relation and create its style.</div>
            ) : (
              <>
                <ChoiceChips label="Match kinds" values={rule.matchKinds || []} options={doc.relationshipKinds || []} onChange={(values) => mutate((draft: any) => (draft.relationshipVisualRules[index].matchKinds = values))} />
                <ChoiceChips label="Match EClasses" values={rule.matchEClasses || []} options={buildEdgeEClassOptions(doc)} onChange={(values) => mutate((draft: any) => (draft.relationshipVisualRules[index].matchEClasses = values))} />
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
      )}
      {mode === "mappings" && <EdgeMappingsEditor doc={doc} selected={selected} setSelected={setSelected} mutate={mutate} />}
    </>
  );
}

function EdgeMappingsEditor({ doc, selected, setSelected, mutate }: any) {
  const rules = doc.semanticEdgeObjectRules || [];
  const index = selected.edgeObjects ?? -1;
  const rule = rules[index];
  const typeOptions = ["*", ...(doc.elements || []).map((item: any) => item.type).filter(Boolean)];
  const referenceCatalog: any[] = [];
  (doc.metamodelSemanticReferenceRules || doc.semanticReferenceRules || []).forEach((item: any) => {
    if (item?.feature && !referenceCatalog.some((candidate) => candidate.feature === item.feature)) {
      referenceCatalog.push(item);
    }
  });
  const referenceEntries = Object.entries(doc.semanticReferenceKindMappings || {});
  const referenceFeature = selected.referenceFeature || String(referenceEntries[0]?.[0] || referenceCatalog[0]?.feature || "");
  const referenceKind = String((doc.semanticReferenceKindMappings || {})[referenceFeature] || referenceCatalog.find((item: any) => item.feature === referenceFeature)?.kind || "");
  const referenceFeatures = [...new Set<string>([
    ...referenceCatalog.map((item: any) => String(item.feature)),
    ...referenceEntries.map(([feature]) => feature),
  ])].filter(Boolean).sort();
  const addReferenceMapping = () => {
    const feature = referenceFeatures.find((item) => !(doc.semanticReferenceKindMappings || {})[item]);
    if (!feature) return;
    const derivedKind = referenceCatalog.find((item: any) => item.feature === feature)?.kind || doc.relationshipKinds?.[0] || "";
    mutate((draft: any) => ((draft.semanticReferenceKindMappings ??= {}), (draft.semanticReferenceKindMappings[feature] = derivedKind)));
    setSelected({ referenceFeature: feature });
  };
  return (
    <div className="mapping-stack">
      <section className="panel mapping-panel">
        <div className="mapping-panel-heading">
          <div><h2>Metamodel reference mappings</h2><p>Map real Ecore reference features to the relationship kind used by the frontend.</p></div>
          <button className="primary-action" onClick={addReferenceMapping} disabled={!referenceFeatures.some((item) => !(doc.semanticReferenceKindMappings || {})[item])}><Plus size={16} /><span>Add Ecore feature mapping</span></button>
        </div>
        <div className="mapping-grid">
          <div className="relationship-catalog">
            {referenceEntries.map(([feature, kind]) => {
              const item = referenceCatalog.find((candidate: any) => candidate.feature === feature);
              return <button className={referenceFeature === feature ? "relationship-card active" : "relationship-card"} key={feature} onClick={() => setSelected({ referenceFeature: feature })}><div className="relationship-card-heading"><strong>{feature}</strong><span>{item?.sourceType || "*"} → {item?.targetType || "*"}</span></div><Pills values={[String(kind)]} empty="no kind" /></button>;
            })}
            {!referenceEntries.length && <div className="empty-inline">No configured reference mappings. Ecore features remain visible in the catalog tab.</div>}
          </div>
          <div className="mapping-inspector">
            {referenceFeature ? <>
              <SelectField label="Ecore reference feature" value={referenceFeature} options={referenceFeatures} onChange={(value) => setSelected({ referenceFeature: value })} />
              <SelectField label="Rendered relationship kind" value={referenceKind} options={doc.relationshipKinds || []} onChange={(value) => mutate((draft: any) => ((draft.semanticReferenceKindMappings ??= {}), (draft.semanticReferenceKindMappings[referenceFeature] = value)))} />
              <ChoiceChips label="Excluded Ecore reference features" values={doc.semanticReferenceExclusions || []} options={referenceFeatures} onChange={(values) => mutate((draft: any) => (draft.semanticReferenceExclusions = values))} />
              <button className="danger-action" onClick={() => mutate((draft: any) => { draft.semanticReferenceKindMappings ??= {}; delete draft.semanticReferenceKindMappings[referenceFeature]; setSelected({ referenceFeature: "" }); })}><Trash2 size={16} /><span>Remove configured mapping</span></button>
            </> : <div className="empty-inline">Select an Ecore reference feature.</div>}
          </div>
        </div>
      </section>
      <section className="panel mapping-panel">
        <div className="mapping-panel-heading"><div><h2>Semantic edge EClass mappings</h2><p>Configure how Ecore relationship objects become frontend edges.</p></div></div>
        <div className="mapping-grid">
          <div className="relationship-catalog">
            {rules.map((item: any, itemIndex: number) => (
              <button className={index === itemIndex ? "relationship-card active" : "relationship-card"} key={itemIndex} onClick={() => setSelected({ edgeObjects: itemIndex })}>
                <div className="relationship-card-heading"><strong>{item.eClass}</strong><span>{item.sourceType} → {item.targetType}</span></div>
                <small>{item.rootFeature || "root edge collection"}</small>
                <Pills values={item.matchKinds || []} empty="no configured kind" />
              </button>
            ))}
            {!rules.length && <div className="empty-inline">No configured semantic edge mappings.</div>}
          </div>
          <div className="mapping-inspector">
            {!rule ? <div className="empty-inline">Select a configured edge mapping. The catalog tab lists all Ecore-derived edge objects.</div> : <>
              <div className="inspector-title"><strong>{rule.eClass}</strong><span>Configured CVS edge mapping</span></div>
              <SelectField label="EClass" value={rule.eClass} options={buildEdgeEClassOptions(doc)} onChange={(value) => mutate((draft: any) => (draft.semanticEdgeObjectRules[index].eClass = value))} />
              <SelectField label="Source type" value={rule.sourceType || "*"} options={typeOptions} onChange={(value) => mutate((draft: any) => (draft.semanticEdgeObjectRules[index].sourceType = value))} />
              <SelectField label="Target type" value={rule.targetType || "*"} options={typeOptions} onChange={(value) => mutate((draft: any) => (draft.semanticEdgeObjectRules[index].targetType = value))} />
              <ChoiceChips label="Allowed edge kinds" values={rule.matchKinds || []} options={doc.relationshipKinds || []} onChange={(values) => mutate((draft: any) => (draft.semanticEdgeObjectRules[index].matchKinds = values))} />
              <SelectField label="Root feature" value={rule.rootFeature} options={buildEdgeFeatureOptions(doc, "rootFeature")} onChange={(value) => mutate((draft: any) => (draft.semanticEdgeObjectRules[index].rootFeature = value))} />
              <SelectField label="Source feature" value={rule.sourceFeature} options={buildEdgeFeatureOptions(doc, "sourceFeature")} onChange={(value) => mutate((draft: any) => (draft.semanticEdgeObjectRules[index].sourceFeature = value))} />
              <SelectField label="Target feature" value={rule.targetFeature} options={buildEdgeFeatureOptions(doc, "targetFeature")} onChange={(value) => mutate((draft: any) => (draft.semanticEdgeObjectRules[index].targetFeature = value))} />
            </>}
          </div>
        </div>
      </section>
    </div>
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
  const [raw, setRaw] = useState(JSON.stringify(stripEditorCatalog(doc), null, 2));
  useEffect(() => setRaw(JSON.stringify(stripEditorCatalog(doc), null, 2)), [doc]);
  const defaults = doc.elementVisualDefaults || {};
  return (
    <>
      <SectionHead title="Advanced" description="Defaults, templates, and raw JSON." />
      <div className="advanced-grid">
        <section className="panel">
          <h2>Element visual defaults</h2>
          <Field label="Icon" value={defaults.icon} onChange={(value) => mutate((draft: any) => ((draft.elementVisualDefaults ??= {}), (draft.elementVisualDefaults.icon = value)))} />
          <ThemeColorFields value={defaults.color} onChange={(value) => mutate((draft: any) => ((draft.elementVisualDefaults ??= {}), (draft.elementVisualDefaults.color = value)))} />
          <Field label="Category" value={defaults.category} onChange={(value) => mutate((draft: any) => ((draft.elementVisualDefaults ??= {}), (draft.elementVisualDefaults.category = value)))} />
        </section>
        <section className="panel cvs-wide">
          <h2>Raw document JSON</h2>
          <textarea className="raw-json" spellCheck={false} value={raw} onChange={(event) => setRaw(event.target.value)} />
          <button
            className="primary-action"
            onClick={() => {
              try {
                const parsed = normalizeLoadedDoc(JSON.parse(raw));
                mutate((draft: any) => {
                  const catalog = {
                    metamodelRelationshipRules: draft.metamodelRelationshipRules,
                    metamodelContainmentFeatures: draft.metamodelContainmentFeatures,
                    metamodelSemanticReferenceRules: draft.metamodelSemanticReferenceRules,
                    metamodelSemanticEdgeObjectRules: draft.metamodelSemanticEdgeObjectRules,
                  };
                  Object.keys(draft).forEach((key) => delete draft[key]);
                  Object.assign(draft, parsed);
                  Object.assign(draft, catalog);
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

function ChoiceChips({
  label,
  values,
  options,
  onChange,
}: {
  label: string;
  values: string[];
  options: string[];
  onChange: (values: string[]) => void;
}) {
  const available = options.filter((option) => !values.includes(option));
  return (
    <div className="choice-chips">
      <span>{label}</span>
      <div className="pills">
        {(values || []).map((value) => (
          <button className="pill removable" key={value} onClick={() => onChange(values.filter((item) => item !== value))}>
            {value}
            <Trash2 size={12} />
          </button>
        ))}
      </div>
      {available.length > 0 && (
        <select value="" onChange={(event) => event.target.value && onChange([...values, event.target.value])}>
          <option value="">Add a metamodel value…</option>
          {available.map((option) => <option key={option} value={option}>{option}</option>)}
        </select>
      )}
    </div>
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
      <img src={iconUrl(visual.icon)} alt="" />
      <strong>{visual.label || "Element"}</strong>
    </div>
  );
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

function buildRelationshipCatalog(doc: CvsDocument) {
  const catalog: any[] = [];
  const seen = new Set<string>();
  const add = (item: any, origin: string, kind: string) => {
    const sourceType = String(item?.sourceType || "*");
    const targetType = String(item?.targetType || "*");
    const feature = String(item?.feature || "");
    const eClass = String(item?.eClass || item?.edgeObjectType || "");
    const kinds = [...new Set<string>([
      ...(Array.isArray(item?.allowedKinds) ? item.allowedKinds : []),
      ...(Array.isArray(item?.matchKinds) ? item.matchKinds : []),
      ...(item?.kind ? [String(item.kind)] : []),
    ])];
    const key = [sourceType, targetType, feature, eClass, kinds.join("|")].join("::");
    if (seen.has(key)) return;
    seen.add(key);
    catalog.push({ key, origin, sourceType, targetType, feature, eClass, kinds, kind });
  };
  (doc.metamodelRelationshipRules || doc.relationshipRules || []).forEach((item: any) => add(item, "Ecore reference rule", "reference"));
  (doc.metamodelSemanticReferenceRules || doc.semanticReferenceRules || []).forEach((item: any) => add(item, "Ecore semantic reference", "semantic-reference"));
  (doc.metamodelSemanticEdgeObjectRules || doc.semanticEdgeObjectRules || []).forEach((item: any) => add(item, "Ecore edge EClass", "edge-object"));
  return catalog.sort((left, right) => `${left.sourceType}:${left.targetType}:${left.feature}:${left.eClass}`.localeCompare(`${right.sourceType}:${right.targetType}:${right.feature}:${right.eClass}`));
}

function relationshipVisualRuleMatches(rule: any, relation: any) {
  const kinds = Array.isArray(rule?.matchKinds) ? rule.matchKinds : [];
  const eClasses = Array.isArray(rule?.matchEClasses) ? rule.matchEClasses : [];
  const kindMatches = !kinds.length || relation.kinds.some((kind: string) => kinds.includes(kind));
  const eClassMatches = !eClasses.length || (relation.eClass && eClasses.includes(relation.eClass));
  return kindMatches && eClassMatches;
}

function relationshipRuleIsExact(rule: any, relation: any) {
  if (relation.eClass) return rule?.edgeObjectType === relation.eClass;
  return rule?.sourceType === relation.sourceType
    && rule?.targetType === relation.targetType
    && (!relation.feature || rule?.feature === relation.feature);
}

function buildEdgeEClassOptions(doc: CvsDocument) {
  return [...new Set<string>([
    ...buildRelationshipCatalog(doc).map((item: any) => item.eClass).filter(Boolean),
    ...(doc.semanticEdgeObjectRules || []).map((item: any) => item.eClass).filter(Boolean),
  ])].sort();
}

function buildRelationshipKindOptions(doc: CvsDocument) {
  return [...new Set<string>([
    ...(doc.relationshipKinds || []),
    ...buildRelationshipCatalog(doc).flatMap((item: any) => item.kinds || []),
  ])].filter(Boolean).sort();
}

function buildEdgeFeatureOptions(doc: CvsDocument, field: string) {
  return [...new Set<string>([
    ...(doc.semanticEdgeObjectRules || []).map((item: any) => item?.[field]).filter(Boolean),
    ...(doc.metamodelSemanticEdgeObjectRules || []).map((item: any) => item?.[field]).filter(Boolean),
  ])].sort();
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
    elements: [],
    views: [{ id: "main", displayName: "Main Canvas", viewType: "MAIN", palette: [], canvas: [], relationshipKinds: [], layoutHint: "DEFAULT_LAYERED" }],
    containers: [],
    elementVisualDefaults: { icon: "category", color: { light: "#475569", dark: "#94a3b8" }, category: meta.displayName },
    referenceMappings: [],
    relationshipMappings: [],
    relationshipRules: [],
    relationshipKinds: ["CONTAINS", "DEPENDS_ON", "TRACE"],
    relationshipKindLabels: {},
    relationshipVisualRules: [],
    semanticReferenceRules: [],
    semanticReferenceKindMappings: {},
    semanticReferenceExclusions: [],
    semanticEdgeObjectRules: [],
    badgeRules: [],
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
  const rawElements = Array.isArray(doc.elements) ? doc.elements : doc.elementOverrides || [];
  const legacyContainers = rawElements
    .filter((element: any) => element?.type && (element.visualRole === "container" || element.containmentPaletteExtras))
    .map((element: any) => ({
      elementType: element.type,
      palette: element.containmentPaletteExtras || [],
      canvas: element.containmentPaletteExtras || [],
      relationshipKinds: [],
      layoutHint: "CONTAINER",
    }));
  doc.elements = rawElements.map((element: any) => {
    const { primitive, card, notation, containmentPaletteExtras, ...clean } = element;
    const visibleFields = clean.visibleFields || card?.lineFields || notation?.lineFields;
    return visibleFields ? { ...clean, visibleFields } : clean;
  });
  const rawViews = Array.isArray(doc.views) ? doc.views : doc.viewpoints || [];
  doc.views = rawViews.map((view: any) => {
    const { viewpoint, elementTypes, ...clean } = view;
    const palette = Array.isArray(clean.palette) ? clean.palette : Array.isArray(elementTypes) ? elementTypes : [];
    return {
      ...clean,
      palette,
      canvas: Array.isArray(clean.canvas) ? clean.canvas : [...palette],
    };
  });
  const rawContainers = Array.isArray(doc.containers) ? doc.containers : legacyContainers;
  doc.containers = rawContainers
    .map((container: any) => {
      const elementType = container.elementType || container.type;
      if (!elementType) return null;
      const palette = Array.isArray(container.palette) ? container.palette : container.types || [];
      return {
        ...container,
        elementType,
        palette,
        canvas: Array.isArray(container.canvas) ? container.canvas : [...palette],
      };
    })
    .filter(Boolean);
  doc.elementVisualRules = (doc.elementVisualRules || []).map((rule: any) => {
    if (!rule?.metadata || typeof rule.metadata !== "object") return rule;
    const { notation, ...metadata } = rule.metadata;
    return { ...rule, metadata };
  });
  doc.relationshipVisualRules ??= [];
  doc.relationshipKinds ??= [];
  doc.relationshipKindLabels ??= {};
  doc.badgeRules ??= [];
  doc.canvasPolicy ??= createEmptyDoc(levelFromDoc(doc)).canvasPolicy;
  if (doc.elementVisualDefaults && typeof doc.elementVisualDefaults === "object") {
    const { notation, ...defaults } = doc.elementVisualDefaults;
    doc.elementVisualDefaults = defaults;
  }
  delete doc.primitives;
  delete doc.notationPrimitives;
  delete doc.elementOverrides;
  delete doc.viewpoints;
  delete doc.universalSyntax;
  delete doc.kernelSyntax;
  delete doc.kernelNotation;
  return doc;
}

function stripEditorCatalog(raw: CvsDocument) {
  const doc = structuredClone(raw || {});
  delete doc.metamodelRelationshipRules;
  delete doc.metamodelContainmentFeatures;
  delete doc.metamodelSemanticReferenceRules;
  delete doc.metamodelSemanticEdgeObjectRules;
  return doc;
}

function levelFromDoc(doc: CvsDocument) {
  return String(doc?.metamodelRef?.level || "cim").toLowerCase();
}

function elementCategories(doc: CvsDocument): string[] {
  return [
    ...new Set<string>((doc.elements || []).map((item: any) => String(item.category || "")).filter(Boolean)),
  ].sort();
}

function resolveElementVisual(doc: CvsDocument, element: any) {
  const defaults = doc.elementVisualDefaults || {};
  return {
    icon: element?.icon || defaults.icon || "category",
    color: element?.color || defaults.color || "#475569",
    category: element?.category || defaults.category || "",
    visualRole: element?.visualRole || "node",
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
