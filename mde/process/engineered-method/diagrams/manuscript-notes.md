# Manuscript Notes and Suggested Captions

## Primary lifecycle figure

Use `modriss-lifecycle-manuscript.svg` as the main development-process figure.

Suggested caption:

> **The MODRISS integrated product lifecycle.** Development and Delivery uses
> three one-time sequential phases for inception, the active product life, and
> retirement. Model-driven increments and release activities repeat within the
> active-product phase. The distinct ongoing, event-driven Operations and Maintenance Process
> sustains live releases through Kanban. G7 handover, telemetry/change feedback,
> retirement, and G8 synchronize the lanes; the DevOps rail names their shared
> integration responsibilities.

Suggested in-text interpretation:

> Each phase occurs once and in sequence; the release iteration repeats
> activities inside Phase 1. After G7, Operations and Maintenance continues for the accepted
> baseline while another release may be engineered. Product-changing service
> work returns to the earliest authoritative source; operations-only work stays
> on the Kanban board. Phase 2 begins only after retirement is authorized, and
> operations ends only when G8 confirms that no live release remains.

## Operational pull-flow detail

Use `modriss-operational-flow.svg` to explain how unpredictable production
demand coexists with planned releases.

Suggested caption:

> **The MODRISS Kanban service-delivery and maintenance system.** Service
> demand is captured as WP-30, classified along three independent axes, and
> replenished and pulled under the WP-31 Definition of Workflow. Verification
> precedes one accepted disposition: operations-only closure, the shortest safe
> model-driven/release path, or explicit commitment to a planned release.
> Temporary emergency changes remain open until removed or reconciled with the
> authoritative source.

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

## Alternative lifecycle figure

Use `modriss-lifecycle-2.svg` when a conventional process
overview, with a large nested construction phase, is preferable to the
semantic-slot presentation of the primary figure.

Suggested caption:

> **Coordinated-process view of MODRISS.** Development and Delivery creates and
> retires releases through sequential phases; Operations and Maintenance
> sustains each live baseline using event-driven Kanban flow. DevOps practices
> provide the cross-process interface.

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
