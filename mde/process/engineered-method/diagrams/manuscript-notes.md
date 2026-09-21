# Manuscript Notes and Suggested Captions

## Primary lifecycle figure

Use `modriss-lifecycle-manuscript.svg` as the main development-process figure.

Suggested caption:

> **The MODRISS full-lifecycle software development process.** The five phases
> organize opportunity and situational tailoring, iterative model-driven
> delivery, release and transition, operation and evolution, and retirement.
> Each phase answers a governing question, performs defined work, produces
> reviewable evidence, and yields a primary lifecycle output. Solid arrows show
> progression; dashed arrows show evidence-driven re-entry and organizational
> learning. Management, risk, quality, security/privacy, configuration/change,
> traceability, FinOps, knowledge, measurement, and team dependencies continue
> across the lifecycle.

Suggested in-text interpretation:

> The phases are not a waterfall. Phase 1 repeats for thin vertical increments;
> releases can contain several accepted increments; operational findings return
> to the earliest authoritative work product; and retirement is governed as a
> release with explicit closure evidence. After G7, the accepted release stays
> in operation while a later release may be engineered through Phases 1–3. The
> dashed Phase 3-to-Phase 1 path denotes this next-release/change cycle; Phase 4
> is entered only after an explicit retirement decision.

## Model-driven engine detail

Use `modriss-model-driven-engine.svg` when the manuscript needs to explain the
focal Phase 1 loop.

Suggested caption:

> **The MODRISS iterative model-driven delivery engine.** A bounded outcome
> slice progresses through CIM discovery, transformation and three-way
> reconciliation, PIM architecture, AWS PSM realization, reproducible EGL/EGX
> generation, implementation completion, integration, testing, and acceptance.
> Gates G2–G5 accept exact revisions; generated output remains a draft until
> reviewed, and findings are routed to the earliest authoritative source.

## Alternative phase-oriented lifecycle figure

Use `modriss-lifecycle-2.svg` when a conventional process
overview, with a large nested construction phase, is preferable to the
semantic-slot presentation of the primary figure.

Suggested caption:

> **Phase-oriented view of the MODRISS full-lifecycle process.** Initiation and
> situational tailoring establish the development setting. Iterative model-
> driven delivery then refines a bounded increment through CIM, PIM, and AWS
> PSM models, generates implementation and deployment artifacts, and verifies
> the resulting software. Accepted increments enter release and transition,
> operation and evolution, and eventual retirement. Dashed paths denote
> iteration, next-release work, and evidence-driven re-entry; the lower rail identifies continuous
> management and engineering disciplines that apply throughout the lifecycle.

## Typesetting

For an A4 or two-column paper, place the primary figure across the full text
width and keep its aspect ratio. Avoid converting it to a low-resolution raster
image. If a publication system cannot preserve the embedded web fonts, convert
text to outlines in a vector editor or use a print-quality PDF generated from
the SVG; keep the supplied SVG as the archival source.

Example LaTeX placement when SVG support is available:

```latex
\begin{figure*}[t]
  \centering
  \includesvg[width=\textwidth]{modriss-lifecycle-manuscript}
  \caption{The MODRISS full-lifecycle software development process.}
  \label{fig:modriss-lifecycle}
\end{figure*}
```
