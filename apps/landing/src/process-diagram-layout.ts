import { processEdges, processNodes, processZones } from './process-diagram-model'
import type { Bounds, Point, ProcessEdge, ProcessNode, ProcessZone } from './process-diagram-model'

export type ProcessLayout = {
  kind: 'wide' | 'medium' | 'vertical' | 'compact'
  width: number
  height: number
  nodes: ProcessNode[]
  zones: ProcessZone[]
  edges: ProcessEdge[]
}

const unitNodes: Record<string, string[]> = {
  inception: ['goals', 'feasibility', 'cost', 'g0', 'method', 'teams', 'g1'],
  analysis: ['plan', 'discover', 'explore', 'compose', 'domain-review', 'g2'],
  architecture: ['boundaries', 'contracts', 'compute', 'flows', 'architecture-review', 'g3'],
  cloud: ['deployment', 'storage', 'workflows', 'cloud-review', 'g4'],
  implementation: ['generate', 'code', 'local-test', 'g5', 'iteration-review'],
  release: ['assemble', 'large-test', 'g6', 'deploy', 'g7', 'release-review'],
  retirement: ['retirement-plan', 'migrate', 'decommission', 'postmortem', 'g8'],
  maintenance: ['observe', 'classify', 'kanban'],
}
const owner = new Map(Object.entries(unitNodes).flatMap(([unit, ids]) => ids.map(id => [id, unit] as const)))
const sourceZones = new Map(processZones.map(zone => [zone.id, zone]))
const round = (value: number) => Math.round(value / 4) * 4
const centerX = (box: Bounds) => box.x + box.width / 2
const centerY = (box: Bounds) => box.y + box.height / 2
const right = (box: Bounds) => box.x + box.width
const bottom = (box: Bounds) => box.y + box.height

function wideLayout(): ProcessLayout {
  // Scale the complete horizontal lifecycle to desktop width. Only maintenance
  // sits below the three phases; smaller screens use the stacked layouts.
  return {
    kind: 'wide',
    width: 2760,
    height: 1032,
    nodes: processNodes,
    zones: processZones,
    edges: processEdges,
  }
}

function verticalLayout(width: 800 | 384): ProcessLayout {
  const medium = width === 800
  const placements = new Map<string, Bounds>()
  const nodes: ProcessNode[] = []
  const zones: ProcessZone[] = []
  const internalEdges: ProcessEdge[] = []
  const columnWidth = 336
  const columnX = (width - columnWidth) / 2

  function zone(id: string, bounds: Bounds) {
    const result = { ...sourceZones.get(id)!, ...bounds }
    zones.push(result)
    return result
  }

  function unit(id: string, x: number, y: number, unitWidth = columnWidth): Bounds {
    const source = sourceZones.get(id)!
    if (id === 'maintenance') {
      const unitIds = unitNodes[id]
      let cursor = y + 80
      const made: ProcessNode[] = []
      for (const nodeId of unitIds) {
        const original = processNodes.find(node => node.id === nodeId)!
        const nodeWidth = 208
        const result = { ...original, x: x + (unitWidth - nodeWidth) / 2, y: cursor, width: nodeWidth }
        nodes.push(result)
        made.push(result)
        cursor += result.height + 32
      }
      const bounds = { x, y, width: unitWidth, height: cursor - y - 8 }
      placements.set(id, bounds)
      zone(id, bounds)
      for (const edge of processEdges.filter(edge => unitIds.includes(edge.from) && unitIds.includes(edge.to))) {
        const a = made.find(node => node.id === edge.from)!, b = made.find(node => node.id === edge.to)!
        const points: Point[] = [[centerX(a), bottom(a)], [centerX(b), b.y]]
        internalEdges.push({ ...edge, points })
      }
      return bounds
    }

    const mapX = (value: number) => x + round((value - source.x) * unitWidth / source.width)
    const mapY = (value: number) => y + value - source.y
    const bounds = { x, y, width: unitWidth, height: source.height }
    placements.set(id, bounds)
    zone(id, bounds)
    if (id === 'analysis') {
      const domain = sourceZones.get('domain')!
      zone('domain', { x: mapX(domain.x), y: mapY(domain.y), width: mapX(right(domain)) - mapX(domain.x), height: domain.height })
    }
    for (const node of processNodes.filter(node => owner.get(node.id) === id)) {
      nodes.push({ ...node, x: mapX(node.x), y: mapY(node.y), width: mapX(right(node)) - mapX(node.x) })
    }
    for (const edge of processEdges.filter(edge => owner.get(edge.from) === id && owner.get(edge.to) === id)) {
      internalEdges.push({ ...edge, points: edge.points.map(([x, y]): Point => [mapX(x), mapY(y)]) })
    }
    return bounds
  }

  const inception = unit('inception', columnX, 16)
  const activeY = bottom(inception) + 64
  const iterationY = activeY + 80
  const analysis = unit('analysis', columnX, iterationY + 64)
  const designY = bottom(analysis) + 64
  const designX = 16
  const designWidth = width - designX * 2
  const architecture = unit('architecture', medium ? 32 : columnX, designY + 64, medium ? 352 : columnWidth)
  const cloud = unit('cloud', medium ? 416 : columnX, medium ? architecture.y : bottom(architecture) + 64, medium ? 352 : columnWidth)
  const design = zone('design', { x: designX, y: designY, width: designWidth, height: bottom(cloud) - designY + 24 })
  const implementation = unit('implementation', columnX, bottom(design) + 64)
  const iteration = zone('iteration', { x: 12, y: iterationY, width: width - 24, height: bottom(implementation) - iterationY + 24 })
  const release = unit('release', columnX, bottom(iteration) + 64)
  const active = zone('active', { x: 4, y: activeY, width: width - 8, height: bottom(release) - activeY + 24 })
  const maintenance = unit('maintenance', columnX, bottom(active) + 64)
  const retirement = unit('retirement', columnX, bottom(maintenance) + 64)

  const nodeMap = new Map(nodes.map(node => [node.id, node]))
  const zoneMap = new Map(zones.map(zone => [zone.id, zone]))
  const laneRight = width - 20
  const node = (id: string) => nodeMap.get(id)!
  function forward(from: string, to: string, rail = laneRight): Point[] {
    const a = node(from), b = node(to), row = b.y - 16
    return [[right(a), centerY(a)], [rail, centerY(a)], [rail, row], [centerX(b), row], [centerX(b), b.y]]
  }
  function back(from: string, target: string, rail: number, leaveBottom = false, extra = 16): Point[] {
    const a = node(from), b = zoneMap.get(target)!, targetY = bottom(b) - 16
    if (leaveBottom) {
      const sourceGroup = placements.get(owner.get(from)!)!
      const row = bottom(sourceGroup) + extra
      return [[centerX(a), bottom(a)], [centerX(a), row], [rail, row], [rail, targetY], [b.x, targetY]]
    }
    return [[a.x, centerY(a)], [rail, centerY(a)], [rail, targetY], [b.x, targetY]]
  }
  const g7 = node('g7'), observe = node('observe'), classify = node('classify')
  const external: Record<string, Point[]> = {
    'g1:plan': forward('g1', 'plan'),
    'g2:boundaries': forward('g2', 'boundaries'),
    'g3:deployment': medium
      ? [[right(node('g3')), centerY(node('g3'))], [400, centerY(node('g3'))], [400, centerY(node('deployment'))], [node('deployment').x, centerY(node('deployment'))]]
      : forward('g3', 'deployment'),
    'g3:domain': back('g3', 'domain', 8, true),
    'g4:architecture': back('g4', 'architecture', 20, true, 32),
    'g4:generate': forward('g4', 'generate'),
    'g5:cloud': back('g5', 'cloud', 32),
    'iteration-review:assemble': forward('iteration-review', 'assemble'),
    'g6:implementation': back('g6', 'implementation', 8),
    'g7:retirement-plan': forward('g7', 'retirement-plan', width - 8),
    'g7:observe': [[centerX(g7), bottom(g7)], [centerX(g7), bottom(g7) + 16], [laneRight, bottom(g7) + 16], [laneRight, centerY(observe)], [right(observe), centerY(observe)]],
    'classify:active': [[classify.x, centerY(classify)], [4, centerY(classify)], [4, bottom(active) + 16], [centerX(active), bottom(active) + 16], [centerX(active), bottom(active)]],
  }
  const localMap = new Map(internalEdges.map(edge => [`${edge.from}:${edge.to}`, edge]))
  const edges = processEdges.map(edge => localMap.get(`${edge.from}:${edge.to}`) ?? { ...edge, points: external[`${edge.from}:${edge.to}`] })
  // Paint outer groups first; nested groups and activities remain in front.
  const order = processZones.map(zone => zone.id)
  zones.sort((a, b) => order.indexOf(a.id) - order.indexOf(b.id))
  return { kind: medium ? 'medium' : 'vertical', width, height: bottom(retirement) + 24, nodes, zones, edges }
}

// Compress vertical geometry without distorting text. Keep the local activity
// pairs even on small phones; the renderer uses smaller labels for this layout.
export const compactProcessDensity = 0.5

function compactLayout(): ProcessLayout {
  const source = verticalLayout(384)
  const compact = <T extends Bounds>(item: T): T => ({ ...item, y: item.y * compactProcessDensity, height: item.height * compactProcessDensity })
  return {
    ...source,
    kind: 'compact',
    height: source.height * compactProcessDensity,
    nodes: source.nodes.map(compact),
    zones: source.zones.map(compact),
    edges: source.edges.map(edge => ({ ...edge, points: edge.points.map(([x, y]): Point => [x, y * compactProcessDensity]) })),
  }
}

const wide = wideLayout()
const medium = verticalLayout(800)
const compact = compactLayout()

export function processLayoutForWidth(width: number): ProcessLayout {
  if (width >= 1100) return wide
  if (width >= 640) return medium
  return compact
}
