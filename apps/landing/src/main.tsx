import { useState } from 'react'
import { createRoot } from 'react-dom/client'
import workspaceScreenshot from './assets/varka-workspace.png'
import wolfLogo from './assets/wolf.png'
import './styles.css'
import './overrides.css'

const appUrl = import.meta.env.VITE_APP_URL ?? 'http://127.0.0.1:8082'
const backendUrl = (import.meta.env.VITE_BACKEND_BASE_URL ?? 'http://127.0.0.1:8080').replace(/\/$/, '')
const publicIpLookupUrl = import.meta.env.VITE_PUBLIC_IP_LOOKUP_URL ?? 'https://api.ipify.org?format=json'
const docsUrl = import.meta.env.VITE_DOCS_URL ?? 'https://github.com/mehdieidi/varka/tree/main/docs/public-docs'
const githubUrl = import.meta.env.VITE_GITHUB_URL ?? 'https://github.com/mehdieidi/varka'

type IconName = 'arrow' | 'book' | 'check' | 'code' | 'github' | 'layers' | 'shield' | 'spark' | 'terminal' | 'workflow'

async function lookupPublicIp() {
  const controller = new AbortController()
  const timeout = window.setTimeout(() => controller.abort(), 1500)
  try {
    const response = await fetch(publicIpLookupUrl, { signal: controller.signal, cache: 'no-store' })
    if (!response.ok) {
      return ''
    }
    const contentType = response.headers.get('Content-Type') || ''
    if (contentType.includes('application/json')) {
      const body = await response.json() as { ip?: unknown }
      return typeof body.ip === 'string' ? body.ip : ''
    }
    return (await response.text()).trim()
  } catch {
    return ''
  } finally {
    window.clearTimeout(timeout)
  }
}

async function sendLandingVisit() {
  const publicIp = await lookupPublicIp()
  const body = JSON.stringify({
    app: 'landing',
    kind: 'page_view',
    url: window.location.pathname + window.location.search,
    attributes: { referrer: document.referrer, publicIp },
    occurredAt: new Date().toISOString(),
  })
  const endpoint = `${backendUrl}/api/telemetry/frontend`
  if (navigator.sendBeacon) {
    const sent = navigator.sendBeacon(endpoint, new Blob([body], { type: 'application/json' }))
    if (sent) {
      return
    }
  }
  void fetch(endpoint, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'X-Request-Id': crypto.randomUUID(),
    },
    body,
    keepalive: true,
  }).catch(() => undefined)
}

void sendLandingVisit()

function Icon({ name, size = 20 }: { name: IconName; size?: number }) {
  const common = { width: size, height: size, viewBox: '0 0 24 24', fill: 'none', stroke: 'currentColor', strokeWidth: 1.8, strokeLinecap: 'round' as const, strokeLinejoin: 'round' as const, 'aria-hidden': true }
  const paths: Record<IconName, React.ReactNode> = {
    arrow: <><path d="M5 12h14" /><path d="m13 6 6 6-6 6" /></>,
    book: <><path d="M4 5.5A2.5 2.5 0 0 1 6.5 3H20v16H6.5A2.5 2.5 0 0 0 4 21.5v-16Z" /><path d="M4 19a2.5 2.5 0 0 1 2.5-2.5H20" /></>,
    check: <path d="m5 12 4.2 4.2L19 6.5" />,
    code: <><path d="m8 9-3 3 3 3M16 9l3 3-3 3M14 5l-4 14" /></>,
    github: <><path d="M15 22v-3.9c.04-1.01-.3-2-.95-2.78 3.16-.35 6.48-1.55 6.48-7A5.5 5.5 0 0 0 19.1 4.5 5.1 5.1 0 0 0 19 1s-1.2-.38-3.94 1.5a13.6 13.6 0 0 0-7.17 0C5.15.62 3.95 1 3.95 1a5.1 5.1 0 0 0-.1 3.5 5.5 5.5 0 0 0-1.43 3.81c0 5.44 3.31 6.64 6.47 7-.64.78-.98 1.77-.95 2.78V22" /><path d="M9 19c-3 .92-3-1.5-4.2-1.84" /></>,
    layers: <><path d="m12 3 9 5-9 5-9-5 9-5Z" /><path d="m3 12 9 5 9-5M3 17l9 5 9-5" /></>,
    shield: <><path d="M12 3 5 6v5c0 5 3 8.4 7 10 4-1.6 7-5 7-10V6l-7-3Z" /><path d="m9 12 2 2 4-4" /></>,
    spark: <><path d="m12 3-1.4 5.6L5 10l5.6 1.4L12 17l1.4-5.6L19 10l-5.6-1.4L12 3Z" /><path d="m19 16-.6 2.4L16 19l2.4.6L19 22l.6-2.4L22 19l-2.4-.6L19 16Z" /></>,
    terminal: <><path d="m5 7 4 5-4 5M12 17h7" /><rect x="3" y="3" width="18" height="18" rx="2" /></>,
    workflow: <><circle cx="6" cy="6" r="2.5" /><circle cx="18" cy="18" r="2.5" /><circle cx="18" cy="6" r="2.5" /><path d="M8.5 6H15.5M6 8.5V18h9.5" /></>,
  }
  return <svg {...common}>{paths[name]}</svg>
}

function App() {
  const [menuOpen, setMenuOpen] = useState(false)
  const closeMenu = () => setMenuOpen(false)

  return <main>
    <nav className="nav" aria-label="Main navigation">
      <a className="brand" href="#top" onClick={closeMenu} aria-label="Varka home"><img className="brand-wolf" src={wolfLogo} alt="" />VARKA</a>
      <button className="menu" onClick={() => setMenuOpen(!menuOpen)} aria-expanded={menuOpen} aria-label="Toggle navigation"><span /><span /></button>
      <div className={`nav-links ${menuOpen ? 'open' : ''}`}>
        <a href="#how-it-works" onClick={closeMenu}>How it works</a>
        <a href="#capabilities" onClick={closeMenu}>Capabilities</a>
        <a href="#outputs" onClick={closeMenu}>Outputs</a>
        <a href="#trust" onClick={closeMenu}>Trust &amp; control</a>
        <a className="nav-resource" href={docsUrl}><Icon name="book" size={16} /> Documentation</a>
        <a className="nav-github" href={githubUrl} aria-label="Varka on GitHub"><Icon name="github" size={18} /></a>
        <a className="nav-cta" href={appUrl}>Open workspace <Icon name="arrow" size={16} /></a>
      </div>
    </nav>

    <section className="hero" id="top">
      <div className="hero-copy">
        <div className="eyebrow"><span className="pulse" /> AI-assisted model-driven engineering for AWS serverless</div>
        <h1>From domain intent<br />to <em>defensible systems.</em></h1>
        <p className="hero-lede">Varka connects business intent, software architecture, and AWS deployment design in one traceable modeling pipeline—then generates a project your team can inspect, test, and own.</p>
        <div className="hero-actions">
          <a className="button primary" href={appUrl}>Start modeling <Icon name="arrow" size={18} /></a>
          <a className="button text" href="#how-it-works">See the workflow <span>↓</span></a>
        </div>
        <p className="microcopy">Open source under the MIT License. Bring your own AI provider when you need it.</p>
      </div>
      <div className="hero-visual">
        <div className="ambient a" /><div className="ambient b" />
        <figure className="workspace-shot"><img src={workspaceScreenshot} alt="The Varka browser workspace with its modeling canvas, palette, lifecycle actions, and AI assistant." /></figure>
      </div>
    </section>

    <section className="proof-strip" aria-label="Key product facts"><span><b>CIM</b> business intent</span><i /><span><b>PIM</b> architecture</span><i /><span><b>AWS PSM</b> deployment design</span><i /><span><b>EVL · ETL · EGX/EGL</b> checked pipeline</span></section>

    <section className="section story" id="how-it-works">
      <div className="section-intro"><div className="eyebrow">A connected path, not a handoff</div><h2>Keep intent<br /><em>connected to implementation.</em></h2></div>
      <p className="section-copy">Varka applies a model-driven engineering pipeline: validate each model, transform it with explicit rules, then generate artifacts from the AWS-specific design. Traceability remains available across the chain.</p>
      <div className="journey">
        <article className="journey-card intent"><div className="card-index">01</div><div className="journey-icon"><Icon name="layers" /></div><h3>Specify the domain</h3><p>Use a computation-independent model to express capabilities, actors, policies, concepts, events, and processes without premature technology choices.</p><span className="model-label">CIM · validate · ETL</span></article>
        <article className="journey-card architecture"><div className="card-index">02</div><div className="journey-icon"><Icon name="workflow" /></div><h3>Engineer the architecture</h3><p>Refine intent into services, contracts, workflows, data, security, identities, and integrations in a platform-independent model.</p><span className="model-label">PIM · validate · ETL</span></article>
        <article className="journey-card build"><div className="card-index">03</div><div className="journey-icon"><Icon name="code" /></div><h3>Generate a reviewable baseline</h3><p>Map the architecture to AWS and generate infrastructure, Go handlers, contracts, tests, automation, and documentation for human review.</p><span className="model-label">AWS PSM · validate · EGX/EGL</span></article>
      </div>
    </section>

    <section className="section capabilities" id="capabilities">
      <div className="capability-feature"><div className="feature-art"><div className="validation-panel"><div className="panel-head"><span>Model assurance</span><b>validation gate</b></div><div className="validation-row"><span className="success-dot">✓</span><div><strong>Structure is checked</strong><small>Ecore conformance, references &amp; multiplicities</small></div></div><div className="validation-row"><span className="success-dot">✓</span><div><strong>Semantics are checked</strong><small>Domain constraints and critiques in EVL</small></div></div><div className="validation-row"><span className="success-dot">✓</span><div><strong>Transformation is explicit</strong><small>Rule-based CIM → PIM → AWS PSM</small></div></div></div></div><div className="feature-copy"><div className="eyebrow">Formal models, practical workbenches</div><h2>Visual modeling.<br /><em>Explicit semantics.</em></h2><p>Varka’s editors implement domain-specific modeling languages (DSMLs) defined by Ecore metamodels. Structural conformance and domain-specific EVL constraints are evaluated before transformation—so diagramming is connected to a machine-checkable model.</p><a className="inline-link" href={docsUrl}>Explore the modeling method <Icon name="arrow" size={17} /></a></div></div>
      <div className="capability-grid"><article><Icon name="layers" /><h3>Three modeling levels</h3><p>Focused workbenches for business intent, platform-independent architecture, and AWS-specific deployment design.</p></article><article><Icon name="terminal" /><h3>Traceable, reviewable delivery</h3><p>Rule-based transformations and model-to-text generation produce inspectable artifacts rather than opaque output.</p></article><article><Icon name="shield" /><h3>Human-governed AI assistance</h3><p>A model-grounded agent uses metamodel-checked tools; destructive batches require confirmation, and checkpoints support undo.</p></article></div>
    </section>

    <section className="section outputs" id="outputs"><div className="outputs-intro"><div className="eyebrow">What Varka produces</div><h2>A project baseline,<br /><em>not a black box.</em></h2><p>Generation begins from the validated AWS PSM and emits an inspectable serverless project. The exact contents depend on the model; generated code remains a starting point for normal engineering review and completion.</p></div><div className="output-grid"><article><span className="output-index">01</span><Icon name="code" /><h3>Infrastructure</h3><p>SAM or CloudFormation resources, stages, configuration, IAM, APIs, storage, messaging, and workflows.</p></article><article><span className="output-index">02</span><Icon name="terminal" /><h3>Application runtime</h3><p>Go Lambda handlers and shared runtime packages shaped by the modeled services, flows, and contracts.</p></article><article><span className="output-index">03</span><Icon name="workflow" /><h3>Contracts &amp; verification</h3><p>OpenAPI, JSON Schema, ASL, sample events, and unit, integration, contract, workflow, event, and security tests.</p></article><article><span className="output-index">04</span><Icon name="book" /><h3>Operational evidence</h3><p>Build and deployment scripts, GitHub Actions workflows, documentation, trace reports, and manual-action reports.</p></article></div></section>

    <section className="section use-cases"><div className="section-intro"><div className="eyebrow">Where Varka fits</div><h2>For systems where<br /><em>reasoning must survive delivery.</em></h2></div><div className="use-case-grid"><article><h3>New serverless products</h3><p>Turn an early domain model into a documented AWS baseline without losing the decisions that shaped it.</p></article><article><h3>Complex event-driven systems</h3><p>Make service boundaries, events, workflows, data ownership, and operational concerns explicit before implementation.</p></article><article><h3>Architecture review &amp; research</h3><p>Use an inspectable chain of models, constraints, transformations, traces, and generated artifacts as evidence for design decisions.</p></article></div></section>

    <section className="section feature-matrix" aria-labelledby="feature-matrix-title"><div className="matrix-heading"><div className="eyebrow">Platform capabilities</div><h2 id="feature-matrix-title">What is carried<br /><em>through the pipeline.</em></h2><p>Each capability belongs to a defined stage of the engineering workflow, making both the result and its provenance available for review.</p></div><div className="matrix-wrap"><table><thead><tr><th scope="col">Capability</th><th scope="col">How Varka implements it</th><th scope="col">Engineering value</th></tr></thead><tbody><tr><th scope="row">Domain-specific models</th><td>Browser workbenches for CIM, PIM, and AWS PSM, defined by Ecore metamodels.</td><td>Captures intent, architecture, and deployment concerns at appropriate abstraction levels.</td></tr><tr><th scope="row">Validation gates</th><td>Structural Ecore checks plus EVL constraints and critiques before transformation.</td><td>Finds model conformance and domain-rule issues before they reach generated artifacts.</td></tr><tr><th scope="row">Explicit transformations</th><td>Rule-based ETL transformations from CIM to PIM and PIM to AWS PSM.</td><td>Makes refinement logic inspectable instead of hiding design decisions in a generator.</td></tr><tr><th scope="row">Reviewable generation</th><td>EGX/EGL generation of AWS infrastructure, Go code, contracts, tests, automation, and documentation.</td><td>Provides a maintainable project baseline with protected regions and manual-action reports.</td></tr><tr><th scope="row">Traceability</th><td>Trace links and reports connect source concepts, refined elements, and generated artifacts.</td><td>Supports impact analysis, architecture review, and defensible design rationale.</td></tr><tr><th scope="row">Bounded AI assistance</th><td>A durable agent loop uses metamodel-checked model tools, validation, checkpoints, confirmations, and undo.</td><td>Accelerates multi-step modeling while preserving human control and an observable change history.</td></tr></tbody></table></div></section>

    <section className="section trust" id="trust"><div className="trust-heading"><div className="eyebrow">Bounded AI agency</div><h2>An agent that<br /><em>works on the model.</em></h2><p>Varka uses a tool-using AI agent for multi-step modeling work. Its authority is intentionally bounded by the live metamodel, backend validation, and user controls; it does not replace architectural judgment.</p></div><div className="trust-list"><article><span>01</span><div><h3>Model-grounded tool use</h3><p>The agent reads and changes the working model through explicit tools, including inspection, planning, validation, and model-edit operations.</p></div></article><article><span>02</span><div><h3>Contract-checked mutations</h3><p>The backend rejects invalid types, attributes, references, containments, and enumeration values before a mutation is committed.</p></div></article><article><span>03</span><div><h3>Observable, reversible work</h3><p>Durable turns, tool events, checkpoints, confirmation for destructive batches, cancellation, and undo keep the process inspectable and controlled.</p></div></article></div></section>

    <section className="final-cta"><div className="final-orbit orbit-one" /><div className="final-orbit orbit-two" /><div className="final-content"><div className="eyebrow light">A model is a shared engineering argument</div><h2>Design systems<br />you can explain.</h2><p>Make the path from domain knowledge to deployable AWS software explicit, checked, traceable, and open to review.</p><a className="button light-button" href={appUrl}>Open the Varka workspace <Icon name="arrow" size={18} /></a></div></section>

    <footer><a className="brand" href="#top"><img className="brand-wolf" src={wolfLogo} alt="" />varka</a><p>Model intent. Validate semantics. Transform explicitly. Generate reviewable systems.</p><div><a href={docsUrl}>Documentation</a><a href={githubUrl}>GitHub</a></div></footer>
  </main>
}

createRoot(document.getElementById('root')!).render(<App />)
