# Process reference diagrams

These 20 explanatory diagrams accompany the MkDocs complete process reference.
Each `.html` is a self-contained source preview. The matching SVGs live in
`docs/public-docs/docs/assets/diagrams/process-reference/`.

Regenerate the previews, SVGs, and reference-page placements from the repository
root with:

```bash
node methodology/process/tools/generate-public-process-reference.mjs
```

Edit `methodology/process/tools/lib/public-process-diagrams.mjs` to change the
views. Do not edit the generated HTML or public SVG copies independently.
The four child-process maps read their activity names, ordering, and recursive
task counts from `methodology/process/definitions/`. The six task examples also
read their names, primary performers, and complete input/output lists from those
definitions; their procedure text is an explanatory summary.

| Views                                                                       | Purpose                                                                                    |
| --------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------ |
| `lifecycle`, `inception`, `increment`, `release`, `retirement`              | Distinguish one-time phases, repeated delivery activities, and shared closure              |
| `operations`, `change-routing`                                              | Explain item flow and the three operational dispositions                                   |
| `cim-activities`, `pim-activities`, `psm-activities`, `artifact-activities` | Connect each child process's activities with its results and feedback                      |
| Six `*-task` views                                                          | Show actual task accountability, procedure, inputs, and outputs                            |
| `spem-bindings`, `gate-review`, `evidence-lineage`                          | Explain reusable content, process occurrences, authority decisions, and traceable evidence |

The diagrams simplify presentation; canonical definitions and detailed item
entries remain authoritative. Phase 1 repeats activities rather than phases.
Operations starts at the first accepted G7 handover and continues through
retirement until G8 is accepted and no release remains live. G8 is a shared
criterion, not an activity-flow edge between the two processes.

## Style

The Diagram Design skill's minimal light treatment is adapted to the existing
MODRISS figures and `docs/public-docs/docs/stylesheets/extra.css`:

| Role                         | Value                                     |
| ---------------------------- | ----------------------------------------- |
| Paper                        | `#f5f3ed`                                 |
| Node surface                 | `#fcfbf7`                                 |
| Ink                          | `#102c3a`                                 |
| Muted text and arrows        | `#617178`                                 |
| Secondary text on teal tint  | `#2c4652`                                 |
| Rules                        | `#ccd2cf`                                 |
| Accent / tint                | `#087b78` / `#e1eeea`                     |
| Names and explanatory labels | Space Grotesk, 600 / 400                  |
| Technical identifiers        | Consolas with a system monospace fallback |

The content-fit canvases are 960px wide, with 18px names and 14px explanations.
There are no diagram headers, decorative title bands, or summary cards.
Connectors are orthogonal, feedback routes are dashed, and teal marks at most
two focal nodes per view. HTML previews scroll locally on narrow screens; the
public figures use the docs' existing responsive image and full-size viewer.

`SpaceGrotesk-Diagrams.woff2` is a 13KB Latin/punctuation subset of the existing
`SpaceGrotesk-VariableFont_wght.ttf`. It is embedded in each figure so the exact
brand face also renders when an SVG is loaded through an image element, without
depending on external font requests. If figure text needs other scripts, extend
the subset from the original font before regenerating.

Review generated figures as browser-rendered images after changing content or
geometry. Check text bounds, connector crossings, input/output completeness,
embedded fonts, and the surrounding MkDocs page. Build the docs with
`python -m mkdocs build --strict --config-file docs/public-docs/mkdocs.yml`.
