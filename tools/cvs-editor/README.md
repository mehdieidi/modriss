# CVS Editor — Concrete Visual Syntax Studio

A standalone graphical editor for Modless CVS v2 notation files (`mde/notation/*.cvs.json`).

## Quick start

From the repository root, serve the tool (recommended — enables icon previews):

```bash
npx --yes serve tools/cvs-editor -p 5190
```

Open [http://localhost:5190](http://localhost:5190).

You can also open `tools/cvs-editor/index.html` directly in a browser; **Load** / **Export** still work, but icon previews may be limited.

## Workflow

1. Click **Load CVS** and choose an existing file (e.g. `mde/notation/cim.cvs.json`).
2. Edit elements, primitives, viewpoints, canvas policy, relationships, and badges in the visual panels.
3. Click **Export CVS** to download the updated JSON.
4. Replace the file under `mde/notation/` manually and restart the backend to pick up changes.

## Sections

| Panel             | What you edit                                        |
| ----------------- | ---------------------------------------------------- |
| **Overview**      | Level metadata, metamodel reference, document stats  |
| **Elements**      | Per-type icons, colors, shapes, tags, visual roles   |
| **Primitives**    | Geometry library (hexagon, diamond, cards, …)        |
| **Package rules** | Package-level visual defaults (`elementVisualRules`) |
| **Viewpoints**    | Views, palettes, scoped element types                |
| **Canvas**        | Role sizes and semantic-zoom thresholds              |
| **Relationships** | Kinds, labels, edge styling rules                    |
| **Badges**        | Node badge rules                                     |
| **Advanced**      | Defaults, templates, raw JSON                        |
