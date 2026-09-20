# Process Diagram Artifacts

This directory contains three publication-oriented diagrams and their
self-contained HTML sources:

- `modriss-lifecycle-manuscript.svg` is the primary A4-landscape manuscript
  figure. It presents the five lifecycle phases, repeated semantic slots,
  evidence gates, feedback paths, and continuous disciplines.
- `modriss-model-driven-engine.svg` is the companion detail figure for the
  vertical CIM→PIM→PSM→artifact delivery engine.
- `modriss-lifecycle-2.svg` is an alternative phase-oriented
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

Both lifecycle figures use the Phase 3 → Phase 1 feedback path for a later
release or operationally discovered change. The accepted release continues to
operate during this work. Phase 3 → Phase 4 is conditional on explicit
retirement authorization; it is not the default result of completing a
release.
