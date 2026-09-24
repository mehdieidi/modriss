# Process Diagram Artifacts

This directory contains four publication-oriented diagrams and their
self-contained HTML sources:

- `modriss-lifecycle-manuscript.svg` is the primary A4-landscape manuscript
  figure. It presents sequential Development and Delivery phases above the
  concurrent Operations and Maintenance process and their DevOps interfaces.
- `modriss-model-driven-engine.svg` is the companion detail figure for the
  vertical CIM→PIM→PSM→artifact delivery engine.
- `modriss-operational-flow.svg` is the companion Operations and Maintenance view. It separates
  maintenance purpose, emergency status, and class of service; shows pull
  control through WP-31; and makes the three accepted dispositions explicit.
- `modriss-lifecycle-2.svg` is an alternative lifecycle
  lifecycle map inspired by the visual organization of some process
  figure. It enlarges the nested construction phase and places lifecycle
  governance on a full-width cross-cutting rail.
- `manuscript-notes.md` provides suggested captions, interpretation text, and
  typesetting guidance.

The matching `.html` files are the editable sources of truth. Re-export them
with `node ../tools/export-diagram-svg.mjs <source.html>` after changes.

Visual generation is intentionally separated from the normative SPEM model in
`../spem/`. The SVG is an explanatory view; the SPEM XML and process narrative
remain authoritative if a visual simplification omits detail.

Both lifecycle figures use the Operations and Maintenance → Phase 1 feedback
path only for a selected product change. Operations-only work stays in its
Kanban system while the accepted release continues to operate. Development
Phase 2 starts only after explicit retirement authorization, and Operations
and Maintenance continues until G8 confirms that no live release remains.
