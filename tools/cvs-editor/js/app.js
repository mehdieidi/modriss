import { METAMODEL } from "./constants.js";
import {
  appState,
  clearDirty,
  createEmptyDoc,
  exportFileName,
  levelFromDoc,
  markDirty,
  mutate,
  setDoc,
} from "./state.js";
import { renderActiveSection } from "./render.js";

const $ = (sel, root = document) => root.querySelector(sel);
const $$ = (sel, root = document) => [...root.querySelectorAll(sel)];

function render() {
  const main = $("#mainContent");
  if (!main) return;
  main.innerHTML = renderActiveSection();
  bindSectionEvents();
  updateChrome();
}

function updateChrome() {
  const d = appState.doc;
  $("#docTitle").textContent = d
    ? `${d.displayName || levelFromDoc(d).toUpperCase()} · CVS v${d.cvsVersion || 2}`
    : "No document";
  $("#levelBadge").textContent = d ? levelFromDoc(d).toUpperCase() : "—";
  $$(".nav-item").forEach((btn) => {
    btn.classList.toggle("active", btn.dataset.section === appState.selection.section);
  });
}

function bindStaticEvents() {
  $("#loadBtn")?.addEventListener("click", () => $("#fileInput")?.click());
  $("#fileInput")?.addEventListener("change", onFileLoad);
  $("#exportBtn")?.addEventListener("click", exportDoc);
  $("#newCimBtn")?.addEventListener("click", () => newDoc("cim"));
  $("#newPimBtn")?.addEventListener("click", () => newDoc("pim"));
  $("#newPsmBtn")?.addEventListener("click", () => newDoc("psm"));

  $$(".nav-item").forEach((btn) => {
    btn.addEventListener("click", () => {
      appState.selection = { section: btn.dataset.section, index: -1, id: "", subIndex: -1 };
      render();
    });
  });

  const dropZone = document.body;
  dropZone.addEventListener("dragover", (e) => {
    e.preventDefault();
    dropZone.classList.add("drag-over");
  });
  dropZone.addEventListener("dragleave", () => dropZone.classList.remove("drag-over"));
  dropZone.addEventListener("drop", (e) => {
    e.preventDefault();
    dropZone.classList.remove("drag-over");
    const file = e.dataTransfer?.files?.[0];
    if (file) readFile(file);
  });

  window.addEventListener("beforeunload", (e) => {
    if (appState.dirty) {
      e.preventDefault();
      e.returnValue = "";
    }
  });
}

function bindSectionEvents() {
  const section = appState.selection.section;

  if (section === "overview") bindOverview();
  if (section === "elements") bindElements();
  if (section === "primitives") bindPrimitives();
  if (section === "packages") bindPackageRules();
  if (section === "viewpoints") bindViewpoints();
  if (section === "canvas") bindCanvas();
  if (section === "relationships") bindRelationships();
  if (section === "badges") bindBadges();
  if (section === "advanced") bindAdvanced();

  $$("[data-nav]").forEach((btn) => {
    btn.addEventListener("click", () => {
      appState.selection.section = btn.dataset.nav;
      render();
    });
  });
}

function onFileLoad(e) {
  const file = e.target.files?.[0];
  if (file) readFile(file);
  e.target.value = "";
}

function readFile(file) {
  const reader = new FileReader();
  reader.onload = () => {
    try {
      const json = JSON.parse(reader.result);
      setDoc(json, file.name);
      appState.selection = { section: "overview", index: -1, id: "", subIndex: -1 };
      render();
    } catch (err) {
      alert(`Invalid JSON: ${err.message}`);
    }
  };
  reader.readAsText(file);
}

function newDoc(level) {
  if (appState.dirty && !confirm("Discard unsaved changes?")) return;
  setDoc(createEmptyDoc(level), `${level}.cvs.json`);
  appState.selection = { section: "overview", index: -1, id: "", subIndex: -1 };
  render();
}

function exportDoc() {
  if (!appState.doc) {
    alert("Nothing to export.");
    return;
  }
  const blob = new Blob([JSON.stringify(appState.doc, null, 2)], { type: "application/json" });
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = exportFileName();
  a.click();
  URL.revokeObjectURL(url);
  clearDirty();
}

function bindOverview() {
  const bind = (id, apply) => {
    const el = $(`#${id}`);
    el?.addEventListener("change", () => {
      mutate((doc) => apply(doc, el));
    });
  };
  bind("ov-displayName", (doc, el) => {
    doc.displayName = el.value;
  });
  bind("ov-cvsVersion", (doc, el) => {
    doc.cvsVersion = Number(el.value) || 2;
  });
  bind("ov-level", (doc, el) => {
    const level = el.value;
    const meta = METAMODEL[level];
    doc.metamodelRef.level = level;
    doc.metamodelRef.ecore = meta.ecore;
    doc.metamodelRef.nsUri = meta.nsUri;
  });
  bind("ov-ecore", (doc, el) => {
    doc.metamodelRef.ecore = el.value;
  });
  bind("ov-nsUri", (doc, el) => {
    doc.metamodelRef.nsUri = el.value;
  });
}

function bindElements() {
  $("#elementSearch")?.addEventListener("input", (e) => {
    appState.filters.elementQuery = e.target.value;
    render();
  });
  $$(".filter-chip").forEach((btn) => {
    btn.addEventListener("click", () => {
      appState.filters.category = btn.dataset.category;
      render();
    });
  });
  $$(".element-card").forEach((btn) => {
    btn.addEventListener("click", () => {
      appState.selection.index = Number(btn.dataset.elementIndex);
      render();
    });
  });
  $("#addElementBtn")?.addEventListener("click", () => {
    const type = prompt("EClass type name (e.g. BusinessGoal):");
    if (!type?.trim()) return;
    mutate((doc) => {
      doc.elementOverrides.push({
        type: type.trim(),
        label: type.trim(),
        icon: "category",
        color: "#2563EB",
        category: "New",
        primitive: "concept-card",
        card: { tag: type.slice(0, 4).toUpperCase(), lineFields: ["name"], detailFields: [] },
      });
    });
    appState.selection.index = appState.doc.elementOverrides.length - 1;
    render();
  });

  const idx = appState.selection.index;
  if (idx < 0) return;

  const applyEl = (fn) => {
    mutate((doc) => fn(doc.elementOverrides[idx]));
    render();
  };

  const map = {
    "el-type": (el, v) => {
      el.type = v;
    },
    "el-label": (el, v) => {
      el.label = v;
      el.displayName = v;
    },
    "el-category": (el, v) => {
      el.category = v;
    },
    "el-visualRole": (el, v) => {
      el.visualRole = v;
    },
    "el-color": (el, v) => {
      el.color = v;
    },
    "el-primitive": (el, v) => {
      el.primitive = v;
    },
    "el-tag": (el, v) => {
      el.card ??= {};
      el.card.tag = v;
    },
    "el-containedOnly": (el, v) => {
      el.containedOnly = v;
    },
    "el-supportOnly": (el, v) => {
      el.supportOnly = v;
    },
    "el-creatable": (el, v) => {
      el.creatable = v;
    },
  };

  Object.entries(map).forEach(([id, fn]) => {
    const el = $(`#${id}`);
    if (!el) return;
    const event = el.type === "checkbox" ? "change" : "input";
    el.addEventListener(event, () => {
      const value = el.type === "checkbox" ? el.checked : el.value;
      applyEl((item) => fn(item, value));
    });
  });

  setupChipInput("el-lineFieldInput", "el-lineFields", idx, "lineFields");
  setupChipInput("el-detailFieldInput", "el-detailFields", idx, "detailFields");

  $$(".chip-remove").forEach((btn) => {
    btn.addEventListener("click", (e) => {
      e.stopPropagation();
      const field = btn.closest("#el-lineFields") ? "lineFields" : "detailFields";
      const i = Number(btn.dataset.remove);
      applyEl((el) => {
        el.card ??= {};
        el.card[field] = (el.card[field] || []).filter((_, j) => j !== i);
      });
    });
  });

  $$(".icon-pick").forEach((btn) => {
    btn.addEventListener("click", () => {
      applyEl((el) => {
        el.icon = btn.dataset.icon;
      });
    });
  });

  $("#deleteElementBtn")?.addEventListener("click", () => {
    if (!confirm("Delete this element override?")) return;
    mutate((doc) => {
      doc.elementOverrides.splice(idx, 1);
    });
    appState.selection.index = -1;
    render();
  });
}

function setupChipInput(inputId, containerId, elementIndex, field) {
  const input = $(`#${inputId}`);
  input?.addEventListener("keydown", (e) => {
    if (e.key !== "Enter") return;
    e.preventDefault();
    const value = input.value.trim();
    if (!value) return;
    mutate((doc) => {
      const el = doc.elementOverrides[elementIndex];
      el.card ??= {};
      el.card[field] ??= [];
      if (!el.card[field].includes(value)) el.card[field].push(value);
    });
    input.value = "";
    render();
  });
}

function bindPrimitives() {
  $$(".primitive-card").forEach((btn) => {
    btn.addEventListener("click", () => {
      appState.selection = { ...appState.selection, id: btn.dataset.primitiveKey };
      render();
    });
  });
  $("#addPrimitiveBtn")?.addEventListener("click", () => {
    const key = prompt("Primitive key (e.g. concept-card):");
    if (!key?.trim()) return;
    mutate((doc) => {
      doc.primitives[key.trim()] = { geometry: "rounded-rectangle", cornerRadius: 12 };
    });
    appState.selection.id = key.trim();
    render();
  });
  const key = appState.selection.id;
  if (!key || !appState.doc?.primitives?.[key]) return;

  const apply = (fn) => {
    mutate((doc) => fn(doc.primitives[key]));
    render();
  };

  $("#prim-key")?.addEventListener("change", (e) => {
    const newKey = e.target.value.trim();
    if (!newKey || newKey === key) return;
    mutate((doc) => {
      doc.primitives[newKey] = doc.primitives[key];
      delete doc.primitives[key];
    });
    appState.selection.id = newKey;
    render();
  });
  $("#prim-geometry")?.addEventListener("change", (e) => apply((p) => (p.geometry = e.target.value)));
  $("#prim-cornerRadius")?.addEventListener("input", (e) =>
    apply((p) => (p.cornerRadius = Number(e.target.value))),
  );
  $("#prim-description")?.addEventListener("input", (e) => apply((p) => (p.description = e.target.value)));
  $("#deletePrimitiveBtn")?.addEventListener("click", () => {
    if (!confirm("Delete primitive?")) return;
    mutate((doc) => delete doc.primitives[key]);
    appState.selection.id = "";
    render();
  });
}

function bindPackageRules() {
  $$("[data-package-index]").forEach((btn) => {
    btn.addEventListener("click", () => {
      appState.selection.index = Number(btn.dataset.packageIndex);
      render();
    });
  });
  $("#addPackageRuleBtn")?.addEventListener("click", () => {
    mutate((doc) => {
      doc.elementVisualRules.push({
        match: { packages: ["newpackage"] },
        metadata: { icon: "category", color: "#475569", category: "New", notation: { tag: "NEW", shape: "concept-card", lineFields: [] } },
      });
    });
    appState.selection.index = appState.doc.elementVisualRules.length - 1;
    render();
  });
  const idx = appState.selection.index;
  if (idx < 0) return;
  const apply = (fn) => {
    mutate((doc) => fn(doc.elementVisualRules[idx]));
    render();
  };
  $("#pkg-packages")?.addEventListener("change", (e) =>
    apply((r) => {
      r.match ??= {};
      r.match.packages = e.target.value.split(",").map((s) => s.trim()).filter(Boolean);
    }),
  );
  $("#pkg-category")?.addEventListener("input", (e) =>
    apply((r) => {
      r.metadata ??= {};
      r.metadata.category = e.target.value;
    }),
  );
  $("#pkg-icon")?.addEventListener("input", (e) =>
    apply((r) => {
      r.metadata ??= {};
      r.metadata.icon = e.target.value;
    }),
  );
  $("#pkg-color")?.addEventListener("input", (e) =>
    apply((r) => {
      r.metadata ??= {};
      r.metadata.color = e.target.value;
    }),
  );
  $("#pkg-shape")?.addEventListener("input", (e) =>
    apply((r) => {
      r.metadata ??= {};
      r.metadata.notation ??= {};
      r.metadata.notation.shape = e.target.value;
    }),
  );
  $("#pkg-tag")?.addEventListener("input", (e) =>
    apply((r) => {
      r.metadata ??= {};
      r.metadata.notation ??= {};
      r.metadata.notation.tag = e.target.value;
    }),
  );
  $("#pkg-lineFields")?.addEventListener("change", (e) =>
    apply((r) => {
      r.metadata ??= {};
      r.metadata.notation ??= {};
      r.metadata.notation.lineFields = e.target.value.split(",").map((s) => s.trim()).filter(Boolean);
    }),
  );
  $("#deletePackageRuleBtn")?.addEventListener("click", () => {
    if (!confirm("Delete package rule?")) return;
    mutate((doc) => doc.elementVisualRules.splice(idx, 1));
    appState.selection.index = -1;
    render();
  });
}

function bindViewpoints() {
  $$("[data-view-index]").forEach((btn) => {
    btn.addEventListener("click", () => {
      appState.selection.index = Number(btn.dataset.viewIndex);
      render();
    });
  });
  $("#addViewBtn")?.addEventListener("click", () => {
    const id = prompt("View id (e.g. domain-model):");
    if (!id?.trim()) return;
    mutate((doc) => {
      doc.viewpoints.push({
        id: id.trim(),
        displayName: id.trim(),
        viewType: id.trim().toUpperCase().replaceAll("-", "_"),
        viewpoint: id.trim(),
        elementTypes: [],
        palette: [],
        relationshipKinds: [],
        layoutHint: "DEFAULT_LAYERED",
      });
    });
    appState.selection.index = appState.doc.viewpoints.length - 1;
    render();
  });
  const idx = appState.selection.index;
  if (idx < 0) return;
  const apply = (fn) => {
    mutate((doc) => fn(doc.viewpoints[idx]));
    render();
  };
  ["id", "displayName", "viewType", "viewpoint", "layoutHint"].forEach((field) => {
    const el = $(`#view-${field}`);
    el?.addEventListener("input", () => apply((v) => (v[field === "id" ? "id" : field] = el.value)));
    el?.addEventListener("change", () => apply((v) => (v[field === "id" ? "id" : field] = el.value)));
  });
  $$(".type-toggle").forEach((btn) => {
    btn.addEventListener("click", () => {
      const pool = btn.dataset.pool;
      const type = btn.dataset.type;
      const listKey = pool === "palette" ? "palette" : "elementTypes";
      apply((view) => {
        const list = new Set(view[listKey] || []);
        if (list.has(type)) list.delete(type);
        else list.add(type);
        view[listKey] = [...list];
      });
    });
  });
  $("#view-relKindInput")?.addEventListener("keydown", (e) => {
    if (e.key !== "Enter") return;
    e.preventDefault();
    const value = e.target.value.trim();
    if (!value) return;
    apply((view) => {
      view.relationshipKinds ??= [];
      if (!view.relationshipKinds.includes(value)) view.relationshipKinds.push(value);
    });
    render();
  });
  $$("#view-relKinds .chip-remove").forEach((btn) => {
    btn.addEventListener("click", () => {
      const i = Number(btn.dataset.remove);
      apply((view) => {
        view.relationshipKinds = (view.relationshipKinds || []).filter((_, j) => j !== i);
      });
    });
  });
  $("#deleteViewBtn")?.addEventListener("click", () => {
    if (!confirm("Delete viewpoint?")) return;
    mutate((doc) => doc.viewpoints.splice(idx, 1));
    appState.selection.index = -1;
    render();
  });
}

function bindCanvas() {
  const policy = appState.doc?.canvasPolicy;
  if (!policy) return;
  Object.keys(policy.roleSizes || {}).forEach((role) => {
    $(`#canvas-${role}-w`)?.addEventListener("input", (e) => {
      mutate((doc) => {
        doc.canvasPolicy.roleSizes[role].width = Number(e.target.value);
      });
      render();
    });
    $(`#canvas-${role}-h`)?.addEventListener("input", (e) => {
      mutate((doc) => {
        doc.canvasPolicy.roleSizes[role].height = Number(e.target.value);
      });
      render();
    });
  });
  const fields = [
    "lowDetailBelow",
    "highDetailAtOrAbove",
    "edgeLabelsAtOrAbove",
    "denseEdgeThreshold",
    "denseEdgeLabelsAtOrAbove",
    "veryDenseEdgeThreshold",
    "veryDenseEdgeLabelsAtOrAbove",
  ];
  fields.forEach((f) => {
    $(`#canvas-${f}`)?.addEventListener("input", (e) => {
      mutate((doc) => {
        doc.canvasPolicy[f] = Number(e.target.value);
      });
    });
  });
}

function bindRelationships() {
  $("#rel-kinds")?.addEventListener("change", (e) => {
    mutate((doc) => {
      doc.relationshipKinds = e.target.value.split(",").map((s) => s.trim()).filter(Boolean);
    });
  });
  $("#rel-kindLabels")?.addEventListener("change", (e) => {
    mutate((doc) => {
      const labels = {};
      e.target.value.split("\n").forEach((line) => {
        const [k, ...rest] = line.split("→");
        if (k?.trim()) labels[k.trim()] = rest.join("→").trim();
      });
      doc.relationshipKindLabels = labels;
    });
  });
  $$("[data-edge-index]").forEach((btn) => {
    btn.addEventListener("click", () => {
      appState.selection.index = Number(btn.dataset.edgeIndex);
      render();
    });
  });
  $("#addEdgeRuleBtn")?.addEventListener("click", () => {
    mutate((doc) => {
      doc.relationshipVisualRules.push({
        matchKinds: ["NEW_KIND"],
        className: "edge-custom",
        stroke: "#64748b",
        lineWidth: 2,
      });
    });
    appState.selection.index = appState.doc.relationshipVisualRules.length - 1;
    render();
  });
  const idx = appState.selection.index;
  if (idx < 0) return;
  const apply = (fn) => {
    mutate((doc) => fn(doc.relationshipVisualRules[idx]));
    render();
  };
  $("#edge-kinds")?.addEventListener("change", (e) =>
    apply((r) => (r.matchKinds = e.target.value.split(",").map((s) => s.trim()).filter(Boolean))),
  );
  $("#edge-eclasses")?.addEventListener("change", (e) =>
    apply((r) => (r.matchEClasses = e.target.value.split(",").map((s) => s.trim()).filter(Boolean))),
  );
  $("#edge-className")?.addEventListener("input", (e) => apply((r) => (r.className = e.target.value)));
  $("#edge-stroke")?.addEventListener("input", (e) => apply((r) => (r.stroke = e.target.value)));
  $("#edge-lineWidth")?.addEventListener("input", (e) => apply((r) => (r.lineWidth = Number(e.target.value))));
  $("#edge-lineDash")?.addEventListener("change", (e) =>
    apply((r) => (r.lineDash = e.target.value.split(",").map((s) => Number(s.trim())).filter((n) => !Number.isNaN(n)))),
  );
  $("#edge-markerEnd")?.addEventListener("change", (e) => apply((r) => (r.markerEnd = e.target.value)));
  $("#edge-markerStart")?.addEventListener("change", (e) => apply((r) => (r.markerStart = e.target.value)));
  $("#deleteEdgeRuleBtn")?.addEventListener("click", () => {
    if (!confirm("Delete edge rule?")) return;
    mutate((doc) => doc.relationshipVisualRules.splice(idx, 1));
    appState.selection.index = -1;
    render();
  });
}

function bindBadges() {
  $$("[data-badge-index]").forEach((btn) => {
    btn.addEventListener("click", () => {
      appState.selection.index = Number(btn.dataset.badgeIndex);
      render();
    });
  });
  $("#addBadgeRuleBtn")?.addEventListener("click", () => {
    mutate((doc) => {
      doc.badgeRules.push({ field: "status", useValue: true });
    });
    appState.selection.index = appState.doc.badgeRules.length - 1;
    render();
  });
  const idx = appState.selection.index;
  if (idx < 0) return;
  const apply = (fn) => {
    mutate((doc) => fn(doc.badgeRules[idx]));
    render();
  };
  $("#badge-field")?.addEventListener("input", (e) => apply((r) => (r.field = e.target.value)));
  $("#badge-label")?.addEventListener("input", (e) => apply((r) => (r.label = e.target.value)));
  $("#badge-useValue")?.addEventListener("change", (e) => apply((r) => (r.useValue = e.target.checked)));
  $("#badge-when")?.addEventListener("change", (e) => apply((r) => (r.when = e.target.checked ? true : undefined)));
  $("#deleteBadgeRuleBtn")?.addEventListener("click", () => {
    if (!confirm("Delete badge rule?")) return;
    mutate((doc) => doc.badgeRules.splice(idx, 1));
    appState.selection.index = -1;
    render();
  });
}

function bindAdvanced() {
  const applyDefaults = (fn) => {
    mutate((doc) => {
      doc.elementVisualDefaults ??= {};
      fn(doc.elementVisualDefaults);
    });
    render();
  };
  $("#def-icon")?.addEventListener("input", (e) => applyDefaults((d) => (d.icon = e.target.value)));
  $("#def-color")?.addEventListener("input", (e) => applyDefaults((d) => (d.color = e.target.value)));
  $("#def-category")?.addEventListener("input", (e) => applyDefaults((d) => (d.category = e.target.value)));
  $("#def-shape")?.addEventListener("input", (e) =>
    applyDefaults((d) => {
      d.notation ??= {};
      d.notation.shape = e.target.value;
    }),
  );
  $("#def-tag")?.addEventListener("input", (e) =>
    applyDefaults((d) => {
      d.notation ??= {};
      d.notation.tag = e.target.value;
    }),
  );
  $("#def-lineFields")?.addEventListener("change", (e) =>
    applyDefaults((d) => {
      d.notation ??= {};
      d.notation.lineFields = e.target.value.split(",").map((s) => s.trim()).filter(Boolean);
    }),
  );
  $("#applyRawJsonBtn")?.addEventListener("click", () => {
    try {
      const parsed = JSON.parse($("#rawJsonEditor").value);
      setDoc(parsed, appState.fileName);
      markDirty();
      render();
    } catch (err) {
      alert(`JSON error: ${err.message}`);
    }
  });
}

bindStaticEvents();
render();
