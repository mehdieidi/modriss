type RouteKind = 'generate' | 'edit' | 'explain'

function ModelGlyph({ added = false }: { added?: boolean }) {
  return <g className="ad-glyph">
    <path d="M-24-12 H0 V16 H24 M0-12 H24" />
    <rect x="-34" y="-22" width="20" height="20" rx="3" />
    <rect x="14" y="-22" width="20" height="20" rx="3" />
    <rect className={added ? 'ad-new-element' : ''} x="14" y="6" width="20" height="20" rx="3" />
    {added && <path className="ad-plus" d="M20 16 H28 M24 12 V20" />}
  </g>
}

function Context({ x, y }: { x: number; y: number }) {
  return <g transform={`translate(${x} ${y})`}>
    <g transform="translate(-92 0)"><ModelGlyph /></g>
    <g className="ad-glyph">
      <rect x="-25" y="-25" width="50" height="48" rx="3" />
      <path d="M-25-9 H25 M-15 1 H1 M-15 11 H12" />
      <circle cx="15" cy="-17" r="2" />
    </g>
    <g className="ad-glyph" transform="translate(92 0)">
      <path d="M-14-24 H15 L25-14 V24 H-14Z M15-24 V-14 H25 M-22-16 H-24 V16 M-5-2 H15 M-5 7 H15 M-5 16 H8" />
    </g>
    <text className="ad-small" x="-92" y="49">Model</text>
    <text className="ad-small" y="49">Ecore</text>
    <text className="ad-small" x="92" y="49">Sources</text>
    <path className="ad-context-bracket" d="M-124 60 V64 Q-124 72-116 72 H116 Q124 72 124 64 V60" />
  </g>
}

function Workbench({ x, y, compact }: { x: number; y: number; compact: boolean }) {
  return <g transform={`translate(${x} ${y})`}>
    <text className="ad-label" x={compact ? 72 : 92} y="-20">Modeler + chatbot</text>
    <g transform={compact ? 'scale(.7826 .8125)' : undefined}>
      <rect className="ad-window" width="184" height="128" rx="8" />
      <path className="ad-window-rule" d="M0 24 H184 M108 24 V128" />
      <g className="ad-window-dots"><circle cx="14" cy="12" r="2" /><circle cx="23" cy="12" r="2" /><circle cx="32" cy="12" r="2" /></g>
      <g transform="translate(51 76) scale(.9)"><ModelGlyph /></g>
      <g className="ad-chat-glyph">
        <path d="M120 43 H171 V66 H137 L129 74 V66 H120Z" />
        <path d="M120 88 H171 V109 H163 V116 L155 109 H120Z" />
        <path className="ad-chat-lines" d="M129 52 H160 M129 58 H150 M129 96 H160 M129 102 H150" />
      </g>
    </g>
  </g>
}

function Router({ x, y }: { x: number; y: number }) {
  return <g transform={`translate(${x} ${y})`}>
    <circle className="ad-router-ring" r="60" />
    <circle className="ad-router" r="52" />
    <g className="ad-router-symbol"><path d="M-16-17 H16 M0-17 V-29" /><circle cx="-16" cy="-17" r="3" /><circle cx="16" cy="-17" r="3" /><circle cy="-29" r="3" /></g>
    <text className="ad-router-text" y="18">LLM</text>
  </g>
}

function Route({ kind, x, y }: { kind: RouteKind; x: number; y: number }) {
  const label = { generate: 'Generate', edit: 'Edit', explain: 'Explain' }[kind]
  return <g transform={`translate(${x} ${y})`}>
    <circle className="ad-route-disc" r="34" />
    {kind === 'generate' && <g transform="scale(.66)"><ModelGlyph added /></g>}
    {kind === 'edit' && <g className="ad-glyph">
      <rect x="-18" y="-16" width="25" height="28" rx="3" />
      <path className="ad-selection" d="M-24-22 H13 V18 H-24Z" />
      <path className="ad-pencil" d="m-1 10 4-12 17-17 7 7L10 5Z M3-2 10 5" />
    </g>}
    {kind === 'explain' && <g className="ad-glyph"><path d="M-21-17 H21 V12 H-3 L-14 22 V12 H-21Z M-12-6 H12 M-12 2 H5" /></g>}
    <text className="ad-label" y="57">{label}</text>
    {kind === 'explain' && <text className="ad-small" y="80">Read-only</text>}
  </g>
}

function Draft({ x, y, compact }: { x: number; y: number; compact: boolean }) {
  const width = compact ? 240 : 192
  const height = compact ? 184 : 224
  return <g transform={`translate(${x} ${y})`}>
    <text className="ad-label" x={width / 2} y="-20">Private draft</text>
    <rect className="ad-draft" width={width} height={height} rx="8" />
    <g transform={`translate(${width / 2} ${compact ? 60 : 88}) scale(${compact ? 1.1 : 1.35})`}><ModelGlyph added /></g>
    <g className="ad-glyph ad-draft-extension" transform={`translate(${width / 2} ${compact ? 60 : 88}) scale(${compact ? 1.1 : 1.35})`}>
      <path d="M-24-2 V48 H24 V26" /><rect x="-34" y="38" width="20" height="20" rx="3" />
    </g>
    <path className="ad-draft-rule" d={`M16 ${height - 48} H${width - 16}`} />
    <text className="ad-small" x={width / 2} y={height - 20}>Compile · stage</text>
  </g>
}

function Gate({ x, y, compact }: { x: number; y: number; compact: boolean }) {
  return <g transform={`translate(${x} ${y})`}>
    <path className="ad-gate" d="M0-48 36 0 0 48-36 0Z" />
    <path className="ad-gate-check" d="m-12 0 8 8 16-18" />
    <text className="ad-label" x={compact ? 100 : 0} y={compact ? -8 : -80}>Ecore / EMF</text>
    <text className="ad-small" x={compact ? 100 : 0} y={compact ? 16 : -58}>Structural gate</text>
  </g>
}

function Checkpoint({ x, y }: { x: number; y: number }) {
  return <g transform={`translate(${x} ${y})`}>
    <rect className="ad-saved-back" x="-47" y="-40" width="88" height="72" rx="5" />
    <rect className="ad-saved" x="-40" y="-32" width="88" height="72" rx="5" />
    <g transform="translate(4 3) scale(.75)"><ModelGlyph added /></g>
    <circle className="ad-saved-badge" cx="44" cy="-28" r="13" />
    <path className="ad-saved-check" d="m38-28 4 4 8-9" />
    <text className="ad-label" x="4" y="70">Checkpoint</text>
    <text className="ad-small" x="4" y="94">Revision checked</text>
  </g>
}

function Architecture({ compact = false }: { compact?: boolean }) {
  const id = compact ? 'assistant-mobile' : 'assistant-wide'
  return <svg className={`assistant-diagram ${compact ? 'assistant-diagram-mobile' : 'assistant-diagram-wide'}`} viewBox={compact ? '0 0 400 1120' : '0 0 1280 712'} role="img" aria-labelledby={`${id}-title ${id}-desc`}>
    <title id={`${id}-title`}>The MODRISS conversational modeling loop</title>
    <desc id={`${id}-desc`}>Model and conversation context, live Ecore contracts, and optional sources inform LLM strategy selection within a durable backend turn. Generate and edit routes compile changes into a private draft. Structural Ecore/EMF conformance and a revision check precede an atomic checkpoint and model update. Rejected drafts can return for bounded repair. Explanation is read-only and returns a reply. EVL semantic validation is outside this loop.</desc>
    <defs>
      <marker id={`${id}-arrow`} markerWidth="8" markerHeight="6" refX="7" refY="3" orient="auto"><path d="M0 0 8 3 0 6Z" fill="var(--muted)" /></marker>
      <marker id={`${id}-accent`} markerWidth="8" markerHeight="6" refX="7" refY="3" orient="auto"><path d="M0 0 8 3 0 6Z" fill="var(--teal-dark)" /></marker>
      <marker id={`${id}-link`} markerWidth="8" markerHeight="6" refX="7" refY="3" orient="auto"><path d="M0 0 8 3 0 6Z" fill="var(--ink-soft)" /></marker>
    </defs>
    {!compact && <><rect className="ad-turn-boundary" x="264" y="168" width="984" height="480" rx="8" /><text className="ad-eyebrow" x="284" y="194">DURABLE TURN</text></>}
    {compact && <text className="ad-eyebrow" x="24" y="18">MODEL · METAMODEL · SOURCES</text>}
    <g className="ad-flows" markerEnd={`url(#${id}-arrow)`}>
      {compact ? <>
        <path d="M200 124 V144 Q200 152 208 152 H280 Q288 152 288 160 V184" />
        <path d="M168 244 H228" />
        <path d="M264 298 V308 Q264 316 256 316 H88 Q80 316 80 324 V390" />
        <path d="M288 304 V332 Q288 340 280 340 H208 Q200 340 200 348 V390" />
        <path d="M312 298 V308 Q312 316 316 316 Q320 316 320 324 V390" />
        <path d="M46 424 H40 Q32 424 32 432 V504 Q32 512 40 512 H92 Q100 512 100 520 V560" />
        <path d="M234 424 H252 Q260 424 260 432 V560" />
        <path d="M180 744 V784" />
        <path className="ad-approved-flow" markerEnd={`url(#${id}-accent)`} d="M180 880 V936" />
        <path className="ad-repair-flow" d="M144 832 H32 Q24 832 24 824 V656 Q24 648 32 648 H60" />
        <path className="ad-return-flow" d="M224 984 H352 Q360 984 360 992 V1072 Q360 1080 352 1080 H16 Q8 1080 8 1072 V276 Q8 268 16 268 H24" />
      </> : <>
        <path d="M392 152 V276" />
        <path d="M224 336 H332" />
        <path d="M448 316 H484 Q492 316 492 308 V248 Q492 240 500 240 H586" />
        <path d="M448 356 H492 Q500 356 500 364 V368 Q500 376 508 376 H586" />
        <path d="M392 396 V496 Q392 504 400 504 H612 Q620 504 620 512 V518" />
        <path d="M654 240 H776" />
        <path d="M654 376 H776" />
        <path d="M968 328 H996" />
        <path className="ad-approved-flow" markerEnd={`url(#${id}-accent)`} d="M1068 328 H1129" />
        <path className="ad-repair-flow" d="M1032 376 V464 Q1032 472 1024 472 H880 Q872 472 872 464 V432" />
        <path className="ad-return-flow" d="M586 552 H184 Q176 552 176 544 V400" />
        <path className="ad-return-flow" d="M1224 344 H1252 Q1260 344 1260 352 V656 Q1260 664 1252 664 H104 Q96 664 96 656 V400" />
      </>}
    </g>
    {compact ? <>
      <Context x={200} y={52} />
      <Workbench x={24} y={188} compact />
      <Router x={288} y={244} />
      <Route kind="generate" x={80} y={424} />
      <Route kind="edit" x={200} y={424} />
      <Route kind="explain" x={320} y={424} />
      <Draft x={60} y={560} compact />
      <Gate x={180} y={832} compact />
      <Checkpoint x={176} y={976} />
      <text className="ad-edge-label" x="79" y="820">Repair</text>
      <text className="ad-edge-label" x="205" y="916">pass</text>
      <text className="ad-edge-label" x="280" y="1104">Model update</text>
    </> : <>
      <Context x={392} y={80} />
      <Workbench x={40} y={272} compact={false} />
      <Router x={392} y={336} />
      <Route kind="generate" x={620} y={240} />
      <Route kind="edit" x={620} y={376} />
      <Route kind="explain" x={620} y={552} />
      <Draft x={776} y={208} compact={false} />
      <Gate x={1032} y={328} compact={false} />
      <Checkpoint x={1176} y={328} />
      <text className="ad-edge-label" x="280" y="324">request</text>
      <text className="ad-edge-label" x="1098" y="316">pass</text>
      <text className="ad-edge-label" x="948" y="460">Repair</text>
      <text className="ad-edge-label" x="288" y="540">Reply</text>
      <text className="ad-edge-label" x="1016" y="652">Model update</text>
    </>}
  </svg>
}

/** Source-to-figure mapping: docs/internal/ai/landing-assistant-diagram.md. */
export function AssistantDiagram() {
  return <figure className="assistant-figure" aria-labelledby="assistant-map-title">
    <div className="assistant-map-heading">
      <div><span>CIM &amp; PIM · implemented architecture</span><h3 id="assistant-map-title">The conversational modeling loop</h3></div>
      <div className="assistant-scope-tag">Human intent → model change</div>
    </div>
    <Architecture />
    <Architecture compact />
    <figcaption><span>Figure 3.</span> Generation and editing share a private draft and structural gate; explanation is read-only. Repair is bounded; checkpoints are atomic and reversible. EVL semantic validation remains a separate, explicit user workflow.</figcaption>
  </figure>
}
