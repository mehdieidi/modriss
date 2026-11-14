import { useId, useLayoutEffect, useRef, useState } from 'react'
import { incrementConnectorPath, incrementLayoutForWidth } from './increment-diagram-layout'
import './IncrementDiagram.css'

export function IncrementDiagram() {
  const container = useRef<HTMLDivElement>(null)
  const [width, setWidth] = useState(0)
  const prefix = `increment-${useId().replaceAll(':', '')}`
  const layout = incrementLayoutForWidth(width)
  const { gate } = layout
  const compact = layout.kind === 'phone'
  const inset = compact ? 14 : 18

  useLayoutEffect(() => {
    const element = container.current
    if (!element) return
    setWidth(element.clientWidth)
    const observer = new ResizeObserver(entries => setWidth(entries[0].contentRect.width))
    observer.observe(element)
    return () => observer.disconnect()
  }, [])

  return <figure className="increment-figure">
    <div className="increment-graph" ref={container} data-layout={layout.kind}>
      <svg className="increment-drawing" viewBox={`0 0 ${layout.width} ${layout.height}`} width={layout.width} height={layout.height} role="img" aria-labelledby={`${prefix}-title ${prefix}-desc`}>
        <title id={`${prefix}-title`}>The model-driven increment</title>
        <desc id={`${prefix}-desc`}>Plan an increment, model the domain at CIM, design serverless architecture at PIM, realize the cloud at PSM, generate and complete artifacts, then accept at G5 and assemble a release; domain, architecture and model gaps return to the preceding model, rework returns to testing, and the next slice returns to planning.</desc>
        <defs>
          <marker id={`${prefix}-arrow`} markerWidth="8" markerHeight="8" refX="7" refY="4" orient="auto" markerUnits="userSpaceOnUse"><path d="M1 1 7 4 1 7" className="increment-arrowhead" /></marker>
          <marker id={`${prefix}-feedback`} markerWidth="8" markerHeight="8" refX="7" refY="4" orient="auto" markerUnits="userSpaceOnUse"><path d="M1 1 7 4 1 7" className="increment-feedback-head" /></marker>
        </defs>
        <g className="increment-connections">
          {layout.edges.map(edge => <path key={`${edge.from}-${edge.to}`} data-edge={`${edge.from}:${edge.to}`} className={`increment-edge${edge.feedback ? ' increment-feedback' : ''}`} d={incrementConnectorPath(edge.points)} markerEnd={`url(#${prefix}-${edge.feedback ? 'feedback' : 'arrow'})`} />)}
        </g>
        {layout.edges.map(edge => edge.label && <g key={`${edge.from}-${edge.to}`} className="increment-edge-label" data-label={`${edge.from}:${edge.to}`}>
          <rect x={edge.label.x - edge.label.width / 2} y={edge.label.y - 14} width={edge.label.width} height="20" />
          <text x={edge.label.x} y={edge.label.y} textAnchor="middle">{edge.label.text}</text>
        </g>)}
        {layout.nodes.map(node => <g key={node.id} data-node={node.id} className={`increment-node increment-node-${node.id}`}>
          <rect x={node.x} y={node.y} width={node.width} height={node.height} rx="8" />
          {node.tag && <text className="increment-tag" x={node.x + inset} y={node.y + (compact ? 18 : 28)}>{node.tag}</text>}
          {node.gate && <g className="increment-gate-badge">
            <rect x={node.x + node.width - (compact ? 38 : 46)} y={node.y + (compact ? 8 : 14)} width={compact ? 24 : 28} height={compact ? 22 : 26} rx="2" />
            <text x={node.x + node.width - (compact ? 26 : 32)} y={node.y + (compact ? 23 : 31)} textAnchor="middle">{node.gate}</text>
          </g>}
          <text className="increment-node-name" x={node.x + inset} y={node.y + (compact ? (node.id === 'plan' ? 36 : 44) : (node.id === 'plan' ? 50 : 66))}>
            {node.name.map((line, i) => <tspan key={line} x={node.x + inset} dy={i === 0 ? 0 : 27}>{line}</tspan>)}
          </text>
          <text className="increment-owner" x={node.x + inset} y={node.y + node.height - (compact ? 16 : 24)}>{node.owner}</text>
        </g>)}
        <g className="increment-decision" data-node="g5">
          <path d={`M${gate.x} ${gate.y - gate.radius} L${gate.x + gate.radius} ${gate.y} L${gate.x} ${gate.y + gate.radius} L${gate.x - gate.radius} ${gate.y}Z`} />
          <text x={gate.x} y={gate.y} textAnchor="middle" dominantBaseline="central">G5</text>
        </g>
      </svg>
    </div>
    <figcaption className="increment-caption">
      <span>A model-driven increment</span>
      <span className="increment-legend"><span><i aria-hidden="true" />Delivery flow</span><span><i className="increment-key-feedback" aria-hidden="true" />Feedback / iteration</span><span><b>G2–G5</b> Decision gates</span></span>
    </figcaption>
  </figure>
}
