import { useId, useLayoutEffect, useMemo, useRef, useState } from 'react'
import { connectorPath } from './process-diagram-model'
import { compactProcessDensity, processLayoutForWidth } from './process-diagram-layout'
import './ProcessDiagram.css'

export function ProcessDiagram() {
  const container = useRef<HTMLDivElement>(null)
  const [width, setWidth] = useState(window.innerWidth)
  const prefix = `process-${useId().replaceAll(':', '')}`
  const layout = processLayoutForWidth(width)
  const labelOffset = layout.kind === 'compact' ? compactProcessDensity : 1
  const lineHeight = layout.kind === 'compact' ? 12 : 20
  const edges = useMemo(() => layout.edges.map((edge, i) => ({ ...edge, path: connectorPath(edge, layout.edges.slice(0, i)) })), [layout])

  useLayoutEffect(() => {
    const element = container.current
    if (!element) return
    setWidth(element.clientWidth)
    const observer = new ResizeObserver(entries => setWidth(entries[0].contentRect.width))
    observer.observe(element)
    return () => observer.disconnect()
  }, [])

  return <figure className="process-figure pd-figure" aria-label="MODRISS development process">
    <div className="pd-graph" ref={container} data-layout={layout.kind}>
      <svg className={`pd-drawing pd-layout-${layout.kind}`} viewBox={`0 0 ${layout.width} ${layout.height}`} width={layout.width} height={layout.height} role="img" aria-labelledby={`${prefix}-title ${prefix}-desc`}>
        <title id={`${prefix}-title`}>MODRISS development process</title>
        <desc id={`${prefix}-desc`}>Inception leads to nested iterative analysis, architecture and cloud design, implementation and release, with explicit gate rework, ongoing maintenance and retirement.</desc>
        <defs>
          <marker id={`${prefix}-arrow`} markerWidth="8" markerHeight="6" refX="7" refY="3" orient="auto" markerUnits="userSpaceOnUse"><path d="M0 0 8 3 0 6Z" className="pd-arrow-head" /></marker>
          <marker id={`${prefix}-feedback`} markerWidth="8" markerHeight="6" refX="7" refY="3" orient="auto" markerUnits="userSpaceOnUse"><path d="M0 0 8 3 0 6Z" className="pd-feedback-head" /></marker>
        </defs>
        <rect width={layout.width} height={layout.height} className="pd-paper" />
        {layout.zones.map(zone => <g key={zone.id} data-zone={zone.id} className={`pd-zone pd-zone-${zone.kind ?? 'stage'}`}>
          {zone.kind === 'iteration' && <><rect x={zone.x + 8} y={zone.y - 8} width={zone.width - 16} height={zone.height} rx="8" className="pd-repeat-frame" /><rect x={zone.x + 4} y={zone.y - 4} width={zone.width - 8} height={zone.height} rx="8" className="pd-repeat-frame" /></>}
          <rect x={zone.x} y={zone.y} width={zone.width} height={zone.height} rx="8" className="pd-zone-frame" />
        </g>)}
        <g className="pd-connections">
          {edges.map(edge => <g key={`${edge.from}-${edge.to}`} data-edge={`${edge.from}:${edge.to}`} data-feedback={edge.feedback ? 'true' : 'false'}>
            <path d={edge.path} className="pd-edge-halo" />
            <path d={edge.path} className={edge.feedback ? 'pd-edge pd-edge-feedback' : 'pd-edge'} markerEnd={`url(#${prefix}-${edge.feedback ? 'feedback' : 'arrow'})`} />
          </g>)}
        </g>
        {layout.zones.map(zone => {
          const titleY = zone.y + (zone.tag ? 52 * labelOffset + (layout.kind === 'compact' ? 3 : 0) : 32 * labelOffset)
          return <g key={zone.id} className={`pd-zone-label pd-zone-${zone.kind ?? 'stage'}`}>
            {zone.tag && <text x={zone.x + 20} y={zone.y + 24 * labelOffset} className="pd-tag">{zone.tag}</text>}
            <text x={zone.x + 20} y={titleY} className="pd-zone-title">{zone.label}</text>
          </g>
        })}
        {layout.nodes.map(node => <g key={node.id} data-node={node.id} className={`pd-node${node.gate ? ' pd-gate' : ''}${node.id === 'g7' ? ' pd-node-focal' : ''}`}>
          <rect x={node.x} y={node.y} width={node.width} height={node.height} rx={node.gate ? 2 : 6} />
          <text x={node.x + node.width / 2} y={node.y + node.height / 2} className="pd-node-name" textAnchor="middle" dominantBaseline="central">
            {node.label.map((line, i) => <tspan key={i} x={node.x + node.width / 2} dy={i === 0 ? -((node.label.length - 1) * lineHeight / 2) : lineHeight}>{line}</tspan>)}
          </text>
        </g>)}
      </svg>
    </div>
    <div className="pd-disciplines"><b>Throughout the lifecycle</b><span>Project Management · People Management · Risk Management · Quality Assurance · Cost Management (FinOps)</span></div>
    <div className="pd-legend" aria-label="Diagram legend">
      <span><i className="pd-key-flow" aria-hidden="true" />Control flow</span><span><i className="pd-key-feedback" aria-hidden="true" />Rework / feedback</span><span><i className="pd-key-activity" aria-hidden="true" />Activity</span><span><i className="pd-key-phase" aria-hidden="true" />Phase</span><span><i className="pd-key-gate" aria-hidden="true">G</i>Decision gate</span>
    </div>
  </figure>
}
