import { useState } from 'react'
import { createRoot } from 'react-dom/client'
import workspaceScreenshot from './assets/modriss-workspace.png'
import modrissFavicon from './assets/modriss-favicon.svg'
import modrissLogoBlack from './assets/modriss-logo.svg'
import modrissLogoWhite from './assets/modriss-logo-w.svg'
import './styles.css'

const appUrl = import.meta.env.VITE_APP_URL ?? window.location.origin
const backendUrl = (import.meta.env.VITE_BACKEND_BASE_URL ?? window.location.origin).replace(/\/$/, '')
const docsUrl = import.meta.env.VITE_DOCS_URL ?? 'https://docs.modriss.site'
const processDocsUrl = `${docsUrl.replace(/\/$/, '')}/guides/full-lifecycle-method/`
const githubUrl = import.meta.env.VITE_GITHUB_URL ?? 'https://github.com/mehdieidi/modriss'
const labUrl = 'https://www.sharif.ir/en/web/me_ce/home'
const universityProfileUrl = 'https://www.sharif.ir/en/web/me_ce/w/mehdi-eidi'
const researcherUrl = 'https://mehdieidi.github.io/'
const paperUrl = 'https://www.scitepress.org/Link.aspx?doi=10.5220/0014634200004058'

type IconName = 'arrow' | 'book' | 'code' | 'downArrow' | 'github' | 'layers' | 'model' | 'process' | 'spark' | 'transform'

async function sendLandingVisit() {
  const body = JSON.stringify({
    app: 'landing',
    kind: 'page_view',
    url: window.location.pathname + window.location.search,
    attributes: { referrer: document.referrer },
    occurredAt: new Date().toISOString(),
  })
  const endpoint = `${backendUrl}/api/telemetry/frontend`
  if (navigator.sendBeacon) {
    const sent = navigator.sendBeacon(endpoint, new Blob([body], { type: 'application/json' }))
    if (sent) return
  }
  void fetch(endpoint, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'X-Request-Id': crypto.randomUUID() },
    body,
    keepalive: true,
  }).catch(() => undefined)
}

void sendLandingVisit()

document.querySelector('link[rel="icon"]')?.setAttribute('href', modrissFavicon)

function Icon({ name, size = 20 }: { name: IconName; size?: number }) {
  const common = { width: size, height: size, viewBox: '0 0 24 24', fill: 'none', stroke: 'currentColor', strokeWidth: 1.7, strokeLinecap: 'round' as const, strokeLinejoin: 'round' as const, 'aria-hidden': true }
  const paths: Record<IconName, React.ReactNode> = {
    arrow: <><path d="M5 12h14" /><path d="m13 6 6 6-6 6" /></>,
    book: <><path d="M4 5.5A2.5 2.5 0 0 1 6.5 3H20v16H6.5A2.5 2.5 0 0 0 4 21.5v-16Z" /><path d="M4 19a2.5 2.5 0 0 1 2.5-2.5H20" /></>,
    code: <><path d="m8 9-3 3 3 3M16 9l3 3-3 3M14 5l-4 14" /></>,
    downArrow: <><path d="M12 5v14" /><path d="m6 13 6 6 6-6" /></>,
    github: <><path d="M15 22v-3.9c.04-1.01-.3-2-.95-2.78 3.16-.35 6.48-1.55 6.48-7A5.5 5.5 0 0 0 19.1 4.5 5.1 5.1 0 0 0 19 1s-1.2-.38-3.94 1.5a13.6 13.6 0 0 0-7.17 0C5.15.62 3.95 1 3.95 1a5.1 5.1 0 0 0-.1 3.5 5.5 5.5 0 0 0-1.43 3.81c0 5.44 3.31 6.64 6.47 7-.64.78-.98 1.77-.95 2.78V22" /><path d="M9 19c-3 .92-3-1.5-4.2-1.84" /></>,
    layers: <><path d="m12 3 9 5-9 5-9-5 9-5Z" /><path d="m3 12 9 5 9-5M3 17l9 5 9-5" /></>,
    model: <><rect x="3" y="4" width="7" height="6" rx="1" /><rect x="14" y="14" width="7" height="6" rx="1" /><path d="M10 7h4a3 3 0 0 1 3 3v4" /></>,
    process: <><circle cx="5" cy="5" r="2" /><circle cx="19" cy="19" r="2" /><path d="M7 5h8a4 4 0 0 1 4 4v8M5 7v5a4 4 0 0 0 4 4h8" /></>,
    spark: <><path d="m12 3-1.4 5.6L5 10l5.6 1.4L12 17l1.4-5.6L19 10l-5.6-1.4L12 3Z" /><path d="M19 16v6M16 19h6" /></>,
    transform: <><path d="M4 7h12l-3-3M16 7l-3 3M20 17H8l3 3M8 17l3-3" /></>,
  }
  return <svg {...common}>{paths[name]}</svg>
}

const contributions = [
  ['01', 'Development process', 'An iterative and incremental process guides teams from project initiation through coordinated CIM–PIM–PSM modeling and on to release, operations, evolution, and retirement.'],
  ['02', 'Modeling framework', 'A set of domain-specific modeling languages represents the problem domain, platform-independent serverless architecture, and AWS deployment design. Their abstract syntax is specified through Ecore metamodels.'],
  ['03', 'Automated refinement', 'Explicit model-to-model transformations refine CIM models into PIM and PSM models. Model-to-text transformations generate an inspectable serverless project from the provider-specific model.'],
  ['04', 'AI-assisted modeling', 'A conversational LLM-based assistant supports model creation, inspection, explanation, and controlled modification. It works with the live metamodel and keeps generated changes subject to structural conformance and human control.'],
]

const frameworkItems = [
  ['Abstract syntax', 'Ecore metamodels define the concepts, attributes, containments, references, and multiplicities of each DSML.'],
  ['Semantics and rules', 'EVL constraints, critiques, and well-formedness rules support explicit semantic validation workflows at the CIM, PIM, and PSM levels.'],
  ['Concrete syntax', 'Browser-based editors provide palettes, diagram notation, relationships, layout, and model views for the languages.'],
  ['Model transformation', 'ETL rules support semi-automated CIM-to-PIM and PIM-to-PSM refinement while leaving ambiguous decisions for human review.'],
  ['Code generation', 'EGX and EGL templates produce infrastructure definitions, application baselines, contracts, tests, automation, documentation, and trace reports.'],
  ['Traceability', 'Links and reports preserve correspondences between source concepts, refined architecture elements, and generated artifacts.'],
]

function App() {
  const [menuOpen, setMenuOpen] = useState(false)
  const closeMenu = () => setMenuOpen(false)

  return <main id="top">
    <nav className="nav" aria-label="Main navigation">
      <a className="brand" href="#top" onClick={closeMenu} aria-label="MODRISS model-driven methodology research project home">
        <img src={modrissLogoBlack} alt="" />
        <span>
          MODRISS
          <small>Research Project</small>
        </span>
      </a>
      <button className="menu" onClick={() => setMenuOpen(!menuOpen)} aria-expanded={menuOpen} aria-label="Toggle navigation"><span /><span /></button>
      <div className={`nav-links ${menuOpen ? 'open' : ''}`}>
        <a href="#research" onClick={closeMenu}>Research</a>
        <a href="#methodology" onClick={closeMenu}>Methodology</a>
        <a href="#framework" onClick={closeMenu}>Framework</a>
        <a href="#process" onClick={closeMenu}>Process</a>
        <a href="#publication" onClick={closeMenu}>Publications</a>
        <a href={docsUrl} onClick={closeMenu}>Documentation</a>
        <a className="nav-icon" href={githubUrl} aria-label="MODRISS source code on GitHub"><Icon name="github" size={18} /></a>
      </div>
    </nav>

    <header className="hero">
      <div className="hero-copy">
        <h1>A model-driven methodology for <i>serverless software development</i></h1>
        <p className="lede">MODRISS is an ongoing academic research project on how serverless software development can be supported through an explicit process, domain-specific modeling languages, model transformations, and code generation. It also explores LLM-assisted modeling for creating, explaining, and modifying models. The work is conducted at the Methodology Engineering Laboratory, Sharif University of Technology.</p>
        <div className="hero-actions">
          <a className="button primary" href={appUrl}>Open the Modeling Workbench <Icon name="arrow" size={17} /></a>
          <a className="button secondary" href="#research">Read about the research <Icon name="downArrow" size={17} /></a>
        </div>
      </div>
      <aside className="research-card" aria-label="Research context">
        <div className="card-rule"><span>Research record</span><b>01</b></div>
        <dl>
          <div><dt>Researcher</dt><dd><a href={researcherUrl}>Mehdi Eidi ↗</a></dd></div>
          {/* <div><dt>Context</dt><dd>MSc thesis research</dd></div> */}
          <div><dt>Laboratory</dt><dd><a href={labUrl}>Methodology Engineering Laboratory ↗</a></dd></div>
          <div><dt>Institution</dt><dd>Department of Computer Engineering<br />Sharif University of Technology</dd></div>
          <div><dt>Status</dt><dd><span className="status-dot" /> Ongoing research and development</dd></div>
        </dl>
        <a className="text-link" href={universityProfileUrl}>Researcher profile <Icon name="arrow" size={15} /></a>
      </aside>
    </header>

    <section className="research-question" id="research">
      <div className="section-label"><span>01</span> Research premise</div>
      <div className="question-layout">
        <h2>Serverless development needs a methodology that addresses its full lifecycle.</h2>
        <div className="prose">
          <p>Serverless computing removes much of the direct work of server management, but it creates architectural and development concerns that extend beyond function implementation. Existing model-driven approaches for serverless development remain limited in their process coverage, modeling support, and treatment of serverless-specific concerns.</p>
          <p>This research studies the design of a model-driven software development methodology for the serverless paradigm. The methodology is intended to connect domain understanding, architectural design, cloud-specific configuration, and generated implementation artefacts within one traceable development path.</p>
          <a className="text-link" href={paperUrl}>Read the study that frames the research gap <Icon name="arrow" size={15} /></a>
        </div>
      </div>
    </section>

    <section className="methodology" id="methodology">
      <div className="section-heading">
        <div className="section-label light"><span>02</span> Proposed contribution</div>
        <h2>The methodology joins process guidance with a structured modeling framework.</h2>
        <p>In this research, a methodology contains two related parts. The process explains how the development work is carried out. The modeling framework provides the languages, rules, transformations, and tools used to perform that work.</p>
      </div>

      <div className="method-equation" aria-label="MODRISS methodology consists of a development process and a modeling framework">
        <article>
          <div className="equation-icon"><Icon name="process" size={25} /></div>
          <p>Part A</p><h3>Development process</h3>
          <span>Lifecycle, phases, roles, tasks, work products, decision points, iterations, and umbrella activities</span>
        </article>
        <b>+</b>
        <article>
          <div className="equation-icon"><Icon name="model" size={25} /></div>
          <p>Part B</p><h3>Modeling framework</h3>
          <span>DSMLs and metamodels, semantics and constraints, transformations, code generation, notation, and tool support</span>
        </article>
      </div>

      <div className="contribution-list">
        {contributions.map(([number, title, body]) => <article key={number}>
          <span>{number}</span><div><h3>{title}</h3><p>{body}</p></div>
        </article>)}
      </div>
    </section>

    <section className="pipeline-section" aria-labelledby="pipeline-title">
      <div className="section-label"><span>03</span> Model-driven path</div>
      <div className="pipeline-intro">
        <h2 id="pipeline-title">From domain knowledge to serverless artifacts</h2>
        <p>The current framework organizes models at three abstraction levels. Transformations provide a systematic refinement path, while modelers review and complete decisions at every level.</p>
      </div>
      <div className="pipeline">
        <article><span>01 · CIM</span><h3>Problem domain</h3><p>Capabilities, actors, concepts, events, processes, policies, governance, and requirements without a software or cloud commitment.</p><small>Computation-Independent Model</small></article>
        <i><Icon name="arrow" /></i>
        <article><span>02 · PIM</span><h3>Serverless architecture</h3><p>Services, functions, contracts, data, events, workflows, integrations, security, policies, and deployment concerns.</p><small>Platform-Independent Model</small></article>
        <i><Icon name="arrow" /></i>
        <article><span>03 · PSM</span><h3>Cloud provider ecosystem</h3><p>AWS resources and their configuration for compute, APIs, storage, messaging, identity, networking, and observability.</p><small>Platform-Specific Model</small></article>
        <i><Icon name="arrow" /></i>
        <article className="artefact"><span>04 · M2T</span><h3>Generated artifacts</h3><p>Infrastructure, Go handlers, contracts, tests, workflows, scripts, documentation, and trace information for review.</p><small>Engineering project baseline</small></article>
      </div>
      <p className="pipeline-note"><Icon name="transform" size={18} /> ETL supports model-to-model refinement. EGX and EGL coordinate model-to-text generation. Semantic EVL checks remain explicit user-initiated validation activities.</p>
    </section>

    <section className="framework" id="framework">
      <div className="framework-copy">
        <div className="section-label"><span>04</span> Modeling framework</div>
        <h2>Domain-specific languages made usable in a web-based workbench</h2>
        <p>The browser platform is the concrete research artefact through which the methodology can be enacted and examined. It makes the DSMLs available as visual editors and connects them with validation, transformation, generation, traceability, and project management functions.</p>
        <a className="text-link" href={docsUrl}>Examine the technical documentation <Icon name="arrow" size={15} /></a>
      </div>
      <div className="framework-list">
        {frameworkItems.map(([title, body], index) => <article key={title}>
          <span>{String(index + 1).padStart(2, '0')}</span><div><h3>{title}</h3><p>{body}</p></div>
        </article>)}
      </div>
    </section>

    <section className="process-section" id="process" aria-labelledby="process-title">
      <div className="process-intro">
        <div>
          <div className="section-label"><span>05</span> Engineered development process</div>
          <h2 id="process-title">A development process for engineering and evolving software in the serverless paradigm</h2>
        </div>
        <div className="process-summary">
          <p>MODRISS is not a single pass. Its three lifecycle phases occur once and in sequence. Inside the active-product phase, repeated release iterations assemble one or more small vertical increments while the accepted baseline remains live.</p>
          <p>The integrated lifecycle coordinates two processes: sequential, phase-based Development and Delivery, and ongoing, event-driven Operations and Maintenance. DevOps practices connect them through CI/CD, operational readiness, telemetry, change routing, and shared learning.</p>
          <a className="text-link" href={processDocsUrl}>Study the full lifecycle process <Icon name="arrow" size={15} /></a>
        </div>
      </div>

      <figure className="process-figure" aria-labelledby="process-map-title process-map-caption">
        <div className="process-map-heading">
          <div><span>Integrated lifecycle</span><h3 id="process-map-title">Two processes, one product lifecycle</h3></div>
          <p>Development delivers releases while operations sustains the live baseline.</p>
        </div>

        <div className="process-lane-heading development-lane-heading">
          <span>Process A</span>
          <b>Development &amp; Delivery</b>
          <small>Phase 0 <i aria-hidden="true">→</i> Phase 1 <i aria-hidden="true">→</i> Phase 2</small>
        </div>

        <div className="lifecycle-cycle" aria-label="Development and Delivery proceeds through three sequential, one-time phases">
          <article className="lifecycle-boundary lifecycle-start">
            <span>Phase 0 · once</span>
            <h4>Inception &amp; tailor</h4>
            <small>G0 · G1</small>
          </article>

          <i className="lifecycle-flow" aria-hidden="true">→</i>

          <div className="active-lifecycle-stack">
            <section className="active-lifecycle" aria-label="One-time active product phase containing repeatable delivery activities">
              <header className="active-lifecycle-heading">
                <div><span>Phase 1 · once</span><b>Active product life</b></div>
                <small>Activities and iterations repeat · phases do not</small>
              </header>

              <div className="release-cycle">
                <section className="increment-zone">
                  <header><span>Iteration</span><b>1+ increments</b></header>
                  <div className="increment-stack">
                    <div className="increment-card-back increment-card-back-far" aria-hidden="true" />
                    <div className="increment-card-back increment-card-back-near" aria-hidden="true" />
                    <article className="increment-card">
                      <div><b>Increment k</b><span>one valuable slice</span></div>
                      <ol aria-label="Model-driven path through one increment"><li>CIM</li><li>PIM</li><li>PSM</li><li>Artifacts</li></ol>
                      <small><span aria-hidden="true">↻</span> review · rework · accept</small>
                    </article>
                  </div>
                  <div className="increment-gates">G2 → G5</div>
                </section>

                <i className="cycle-flow" aria-hidden="true">→</i>

                <article className="release-node">
                  <span>Release activities</span>
                  <h4>Qualify, promote &amp; hand over</h4>
                  <div><b>Candidate</b><i aria-hidden="true">→</i><b>Promote</b></div>
                  <small>G6 · G7</small>
                </article>

              </div>

              <div className="release-learning-loop" aria-label="Release learning loop: review release evidence, adapt the backlog, and begin the next release iteration">
                <span>Release learning loop</span>
                <ol>
                  <li><b>G7 evidence</b></li>
                  <li><b>Review outcomes</b></li>
                  <li><b>Adapt backlog</b></li>
                  <li><strong><i aria-hidden="true">↺</i> Next iteration</strong></li>
                </ol>
              </div>
            </section>
          </div>

          <i className="lifecycle-flow lifecycle-exit-flow" aria-hidden="true">→</i>

          <article className="lifecycle-boundary lifecycle-end">
            <span>Phase 2 · once authorized</span>
            <h4>Retire &amp; close</h4>
            <small>G8</small>
          </article>
        </div>

        <div className="process-coupling" aria-label="DevOps interface between the processes: operational feedback returns to active product development and the G7 release enters operations">
          <div className="coupling-heading"><b>DevOps interface</b><span>shared automation + evidence</span></div>
          <svg className="coupling-lines" viewBox="0 0 1000 76" preserveAspectRatio="none" aria-hidden="true">
            <defs>
              <marker id="coupling-coral-arrow" markerWidth="8" markerHeight="6" refX="7" refY="3" orient="auto"><path d="M0 0 8 3 0 6Z" /></marker>
              <marker id="coupling-teal-arrow" markerWidth="8" markerHeight="6" refX="7" refY="3" orient="auto"><path d="M0 0 8 3 0 6Z" /></marker>
            </defs>
            <path className="coupling-path coupling-path-feedback" d="M280 76 V0" markerEnd="url(#coupling-teal-arrow)" />
            <path className="coupling-path coupling-path-handover" d="M680 0 V76" markerEnd="url(#coupling-coral-arrow)" />
          </svg>
          <div className="coupling-channel coupling-feedback">
            <span data-short="Feedback"><i aria-hidden="true">↑</i> Feedback + change</span>
          </div>
          <div className="coupling-channel coupling-handover">
            <span data-short="G7 release"><i aria-hidden="true">↓</i> G7 release</span>
          </div>
        </div>

        <article className="operations-process" aria-label="Distinct concurrent Operations and Maintenance process with Kanban pull flow">
          <header>
            <span>Process B · concurrent · not a phase</span>
            <h4>Operations &amp; Maintenance</h4>
            <div className="live-baseline"><span><i aria-hidden="true" />Live baseline</span><small>First G7 <i aria-hidden="true">→</i> shared G8 closure</small></div>
          </header>
          <div className="operations-mechanism">
            <div className="kanban-label"><b>Kanban pull</b><span>WIP + SLE</span></div>
            <ol className="kanban-mini" aria-label="Requested, Ready, Doing, Verify, Done">
              <li><span>Requested</span></li>
              <li><span>Ready</span></li>
              <li><span>Doing</span><small>2/2</small></li>
              <li><span>Verify</span><small>1/2</small></li>
              <li><span>Done</span></li>
            </ol>
            <div className="operations-routes" aria-label="At replenishment, classify each service item for one of three destinations">
              <div className="route-decision">
                <span>At replenishment</span>
                <b>Classify service item</b>
              </div>
              <svg className="route-fork" viewBox="0 0 52 128" preserveAspectRatio="none" aria-hidden="true">
                <defs>
                  <marker id="route-fork-arrow" markerWidth="7" markerHeight="6" refX="6" refY="3" orient="auto"><path d="M0 0 7 3 0 6Z" /></marker>
                </defs>
                <path d="M0 64 H14" />
                <path d="M14 64 V22 Q14 16 20 16 H50" markerEnd="url(#route-fork-arrow)" />
                <path d="M14 64 H50" markerEnd="url(#route-fork-arrow)" />
                <path d="M14 64 V106 Q14 112 20 112 H50" markerEnd="url(#route-fork-arrow)" />
              </svg>
              <div className="route-outcomes">
                <article><i aria-hidden="true">↺</i><div><b>Resolve in operations</b><small>Runbook + evidence</small></div></article>
                <article><i aria-hidden="true">↑</i><div><b>Return to MDE flow</b><small>Earliest owning source</small></div></article>
                <article><i aria-hidden="true">↗</i><div><b>Commit to planned release</b><small>Release backlog</small></div></article>
              </div>
            </div>
          </div>
        </article>

        <div className="discipline-rail"><b>Continuous disciplines</b><span>Management · risk · quality · security · change · traceability · FinOps · learning</span></div>
        <figcaption id="process-map-caption">Three one-time sequential Development and Delivery phases coexist with a distinct ongoing, event-driven Operations and Maintenance process. Release iterations repeat only inside Phase 1. DevOps interfaces carry G7 handover, CI/CD evidence, telemetry, maintenance change, and learning. Phase 2 and Operations have separate work flows; both finish only when the shared G8 closure criteria are satisfied.</figcaption>
      </figure>
    </section>

    <section className="prototype" id="prototype">
      <div className="prototype-heading">
        <div>
          <div className="section-label light"><span>06</span> Research prototype</div>
          <h2>The methodology is realized as an integrated low-code platform.</h2>
        </div>
        <p>The prototype provides project workspaces, graphical modeling editors, process guidance, model import and export, impact analysis, transformations, generated-artifacts inspection, and a conversational modeling assistant.</p>
      </div>
      <figure className="workspace-shot">
        <img src={workspaceScreenshot} alt="MODRISS browser-based CIM modeling workbench with a visual process model, modeling palette, lifecycle actions, and conversational assistant." />
        <figcaption><span>Figure 1.</span> The MODRISS modeling workbench, showing the CIM editor and the LLM-based modeling assistant.</figcaption>
      </figure>
    </section>

    <section className="assistant-section">
      <div className="assistant-title">
        <div className="assistant-mark"><Icon name="spark" size={28} /></div>
        <div><div className="section-label"><span>07</span> LLM-supported modeling</div><h2>A conversational LLM-based assistant grounded in the modeling languages</h2></div>
      </div>
      <div className="assistant-grid">
        <div className="prose"><p>The assistant supports modeling activity through natural-language interaction. It can use the current model and uploaded source material to create, inspect, explain, or modify CIM and PIM models. The live Ecore metamodel provides its modeling vocabulary and structural contract.</p><p>The assistant is part of the research framework, rather than an independent code generator. Its operations are recorded as durable turns, and changes can use checkpoints, confirmation, cancellation, and undo.</p></div>
        {/* <aside>
          <h3>Validation boundary</h3>
          <p>Assistant-generated output is gated by structural Ecore/EMF conformance. EVL semantic validation is kept outside assistant apply, repair, and commit paths, and is performed only through explicit model-validation workflows. PSM chatbot sessions are currently outside the implemented scope.</p>
        </aside> */}
      </div>
    </section>

    <section className="publication" id="publication">
      <div className="section-label"><span>08</span> Research output</div>
      <article className="paper-card">
        <div className="paper-meta"><span>Conference paper</span><b>2026</b></div>
        <div className="paper-main">
          <div><p>MODELSWARD 2026 · 14th International Conference on Model-Based Software and Systems Engineering</p><h2>Model-Driven Approaches for Serverless Software Development: Evaluation and Future Directions</h2><p className="authors">Mehdi Eidi and Raman Ramsin</p></div>
          <div className="abstract"><h3>Relation to MODRISS</h3><p>The paper reviews selected model-driven approaches for serverless and microservices development through a process-centered template. Its evaluation framework identifies gaps in existing serverless approaches and provides part of the research basis for developing the MODRISS methodology.</p><dl><div><dt>Pages</dt><dd>560–567</dd></div><div><dt>DOI</dt><dd>10.5220/0014634200004058</dd></div></dl></div>
        </div>
        <a className="paper-link" href={paperUrl}>View publication <Icon name="arrow" size={17} /></a>
      </article>
    </section>

    {/* <section className="status-section">
      <div>
        <div className="section-label light"><span>08</span> Current status</div>
        <h2>An evolving research methodology and its executable artefact</h2>
      </div>
      <div>
        <p>MODRISS is under active research and development. The repository includes the three modeling languages, formal validation rules, transformation profiles, code generation, modeling subprocesses, lifecycle material, and the web platform. Evaluation and refinement of the methodology and assistant continue as part of the thesis work.</p>
        <p>Generated projects are research outputs and engineering baselines. They still require human review, completion of recorded manual actions, testing, and deployment-specific decisions.</p>
        <div className="status-links"><a href={githubUrl}><Icon name="github" size={17} /> Source repository</a><a href={docsUrl}><Icon name="book" size={17} /> Research documentation</a><a href={appUrl}><Icon name="model" size={17} /> Modeling prototype</a></div>
      </div>
    </section> */}

    <footer>
      <div className="footer-brand"><img src={modrissLogoWhite} alt="" /><div><b>MODRISS</b><span>Model-Driven Methodology for Serverless Software Development</span></div></div>
      <p>A research project at the Methodology Engineering Laboratory, Department of Computer Engineering, Sharif University of Technology.</p>
      <div className="footer-links"><a href={labUrl}>Laboratory</a><a href={universityProfileUrl}>Lab profile</a><a href={researcherUrl}>Researcher</a><a href={githubUrl}>Repository</a></div>
    </footer>
  </main>
}

createRoot(document.getElementById('root')!).render(<App />)
