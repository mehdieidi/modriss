import { useState } from 'react'
import { createRoot } from 'react-dom/client'
import workspaceScreenshot from './assets/varka-workspace.png'
import wolfLogo from './assets/wolf.png'
import './styles.css'
import './overrides.css'

const appUrl = import.meta.env.VITE_APP_URL ?? 'http://127.0.0.1:8082'
const docsUrl = import.meta.env.VITE_DOCS_URL ?? 'https://github.com/mehdieidi/varka/tree/main/docs/public-docs'
const githubUrl = import.meta.env.VITE_GITHUB_URL ?? 'https://github.com/mehdieidi/varka'

type IconName = 'arrow' | 'book' | 'check' | 'code' | 'github' | 'layers' | 'shield' | 'spark' | 'terminal' | 'workflow'

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
        <a href="#trust" onClick={closeMenu}>Built with intent</a>
        <a className="nav-resource" href={docsUrl}><Icon name="book" size={16} /> Documentation</a>
        <a className="nav-github" href={githubUrl} aria-label="Varka on GitHub"><Icon name="github" size={18} /></a>
        <a className="nav-cta" href={appUrl}>Open workspace <Icon name="arrow" size={16} /></a>
      </div>
    </nav>

    <section className="hero" id="top">
      <div className="hero-copy">
        <div className="eyebrow"><span className="pulse" /> Model-driven engineering for AWS serverless</div>
        <h1>Make the system<br /><em>make sense</em> first.</h1>
        <p className="hero-lede">Varka turns business intent into a reviewable AWS serverless project—through formal models, validation, and an AI assistant that works inside clear guardrails.</p>
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

    <section className="proof-strip" aria-label="Key product facts"><span><b>CIM</b> business intent</span><i /><span><b>PIM</b> architecture</span><i /><span><b>AWS PSM</b> deployable design</span><i /><span><b>Artifacts</b> code &amp; infrastructure</span></section>

    <section className="section story" id="how-it-works">
      <div className="section-intro"><div className="eyebrow">A connected path, not a handoff</div><h2>Keep the reason<br />connected to the result.</h2></div>
      <p className="section-copy">Varka lets teams move deliberately from the problem they are solving to an implementation they can inspect, refine, and take ownership of.</p>
      <div className="journey">
        <article className="journey-card intent"><div className="card-index">01</div><div className="journey-icon"><Icon name="layers" /></div><h3>Model intent</h3><p>Capture goals, actors, policies, domain concepts, events, and processes without committing to technology.</p><span className="model-label">CIM · Computation-independent</span></article>
        <article className="journey-card architecture"><div className="card-index">02</div><div className="journey-icon"><Icon name="workflow" /></div><h3>Refine architecture</h3><p>Shape services, APIs, workflows, events, data stores, identities, and integrations in a provider-independent model.</p><span className="model-label">PIM · Platform-independent</span></article>
        <article className="journey-card build"><div className="card-index">03</div><div className="journey-icon"><Icon name="code" /></div><h3>Generate, then own</h3><p>Refine to AWS resources and generate a project with infrastructure, Go handlers, contracts, tests, and docs for review.</p><span className="model-label">AWS PSM · Artifacts</span></article>
      </div>
    </section>

    <section className="section capabilities" id="capabilities">
      <div className="capability-feature"><div className="feature-art"><div className="validation-panel"><div className="panel-head"><span>Validation</span><b>0 errors</b></div><div className="validation-row"><span className="success-dot">✓</span><div><strong>Architecture is consistent</strong><small>42 constraints evaluated</small></div></div><div className="validation-row"><span className="success-dot">✓</span><div><strong>Relationships are valid</strong><small>Metamodel rules satisfied</small></div></div><div className="validation-row"><span className="success-dot">✓</span><div><strong>Ready for transformation</strong><small>CIM → PIM</small></div></div></div></div><div className="feature-copy"><div className="eyebrow">Built for decisions you can defend</div><h2>Visual clarity.<br /><em>Formal confidence.</em></h2><p>Drag-and-drop modeling does not have to mean loose thinking. Varka’s visual workbenches are driven by formal Ecore metamodels; semantic rules are checked with Eclipse Epsilon validation before each transformation.</p><a className="inline-link" href={docsUrl}>Explore the modeling method <Icon name="arrow" size={17} /></a></div></div>
      <div className="capability-grid"><article><Icon name="layers" /><h3>Three focused workbenches</h3><p>Purpose-built visual editors for business, architecture, and AWS-specific modeling levels.</p></article><article><Icon name="terminal" /><h3>Reviewable output</h3><p>Generated projects include infrastructure, source code, contracts, tests, and documentation—not a black box.</p></article><article><Icon name="shield" /><h3>Guardrails for AI work</h3><p>The optional assistant acts through validated tools, audits changes, supports checkpoints, and requires confirmation for destructive batches.</p></article></div>
    </section>

    <section className="section trust" id="trust"><div className="trust-heading"><div className="eyebrow">AI, with a boundary</div><h2>Assistance that<br />doesn’t outrun you.</h2><p>Use AI to understand the metamodel and work with the model—not to bypass engineering judgment.</p></div><div className="trust-list"><article><span>01</span><div><h3>Grounded in your live model</h3><p>The assistant works through the platform’s model contract and selected model state.</p></div></article><article><span>02</span><div><h3>Changes are checked before commit</h3><p>Invalid structures and values are rejected before a model mutation is persisted.</p></div></article><article><span>03</span><div><h3>Work stays inspectable</h3><p>Durable turns, checkpoints, confirmations, and undo make the change process visible.</p></div></article></div></section>

    <section className="final-cta"><div className="final-orbit orbit-one" /><div className="final-orbit orbit-two" /><div className="final-content"><div className="eyebrow light">The first system design is a conversation</div><h2>Start with what<br />matters.</h2><p>Turn your domain knowledge into an architecture your team can reason about—and a project you can build on.</p><a className="button light-button" href={appUrl}>Open the Varka workspace <Icon name="arrow" size={18} /></a></div></section>

    <footer><a className="brand" href="#top"><img className="brand-wolf" src={wolfLogo} alt="" />varka</a><p>Model business intent. Refine architecture. Generate with confidence.</p><div><a href={docsUrl}>Documentation</a><a href={githubUrl}>GitHub</a></div></footer>
  </main>
}

createRoot(document.getElementById('root')!).render(<App />)
