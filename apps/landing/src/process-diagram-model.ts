export type Point = readonly [number, number]
export type Bounds = { x: number; y: number; width: number; height: number }
export type ProcessNode = Bounds & { id: string; label: string[]; gate?: boolean }
export type ProcessZone = Bounds & { id: string; label: string; tag?: string; kind?: string }
export type ProcessEdge = { from: string; to: string; points: Point[]; feedback?: boolean }

// Independently laid out on a 4px grid. The reference defines the content and
// graph; no exported SVG markup, coordinates or path strings are used here.
export const processZones: ProcessZone[] = [
  { id: 'inception', x: 24, y: 200, width: 336, height: 512, label: 'Inception', tag: 'Phase 1', kind: 'phase' },
  { id: 'active', x: 432, y: 24, width: 1920, height: 736, label: 'Active Product Development', tag: 'Phase 2', kind: 'active' },
  { id: 'iteration', x: 448, y: 100, width: 1616, height: 636, label: 'Iterative–incremental development', kind: 'iteration' },
  { id: 'analysis', x: 480, y: 164, width: 336, height: 524, label: 'Analysis' },
  { id: 'domain', x: 492, y: 324, width: 312, height: 344, label: 'Domain Modeling', tag: 'CIM' },
  { id: 'design', x: 848, y: 120, width: 784, height: 584, label: 'Design', kind: 'design' },
  { id: 'architecture', x: 864, y: 204, width: 352, height: 480, label: 'Architecture Design', tag: 'PIM' },
  { id: 'cloud', x: 1256, y: 204, width: 352, height: 480, label: 'Cloud Design', tag: 'PSM' },
  { id: 'implementation', x: 1680, y: 180, width: 336, height: 488, label: 'Implementation', tag: 'Artifacts' },
  { id: 'release', x: 2096, y: 164, width: 224, height: 560, label: 'Release', tag: 'Delivery' },
  { id: 'retirement', x: 2400, y: 236, width: 336, height: 436, label: 'Retirement', tag: 'Phase 3', kind: 'phase' },
  { id: 'maintenance', x: 1680, y: 832, width: 648, height: 152, label: 'Maintenance Process', tag: 'Ongoing', kind: 'maintenance' },
]

function activity(id: string, x: number, y: number, label: string[], width = 128): ProcessNode {
  return { id, x, y, width, height: 64, label }
}
function gate(id: string, x: number, y: number): ProcessNode {
  return { id, x, y, width: 32, height: 32, label: [id.toUpperCase()], gate: true }
}

export const processNodes: ProcessNode[] = [
  activity('goals', 44, 280, ['Set Goals']),
  activity('feasibility', 212, 280, ['Analyze', 'Feasibility']),
  activity('cost', 128, 396, ['Estimate Cost']),
  gate('g0', 176, 492),
  activity('method', 44, 556, ['Prepare', 'Method']),
  activity('teams', 212, 556, ['Form Teams &', 'Environment']),
  gate('g1', 280, 660),
  activity('plan', 584, 244, ['Plan', 'Increment']),
  activity('discover', 512, 404, ['Discover', 'Context']),
  activity('explore', 656, 404, ['Explore', 'Domain']),
  activity('compose', 656, 508, ['Compose', 'Domain']),
  activity('domain-review', 512, 508, ['Validate &', 'Complete']),
  gate('g2', 736, 620),
  activity('boundaries', 884, 300, ['Set Service', 'Boundaries']),
  activity('contracts', 1068, 300, ['Design Data &', 'Contracts']),
  activity('compute', 1068, 408, ['Design', 'Compute']),
  activity('flows', 884, 408, ['Design Flows', '& Integration']),
  activity('architecture-review', 976, 516, ['Validate &', 'Complete']),
  gate('g3', 1024, 624),
  activity('deployment', 1276, 300, ['Structure', 'Deployment']),
  activity('storage', 1444, 300, ['Design Storage', '& Messaging'], 152),
  activity('workflows', 1444, 408, ['Design Compute', '& Workflows'], 152),
  activity('cloud-review', 1276, 408, ['Validate &', 'Complete']),
  gate('g4', 1400, 540),
  activity('generate', 1700, 296, ['Generate', 'Code']),
  activity('code', 1868, 296, ['Code/Test']),
  activity('local-test', 1764, 408, ['Test on Local', 'Environment'], 168),
  gate('g5', 1832, 516),
  activity('iteration-review', 1764, 592, ['Review Iteration'], 168),
  activity('assemble', 2116, 252, ['Assemble Release', 'Candidate'], 184),
  activity('large-test', 2116, 376, ['Test in the Large'], 184),
  gate('g6', 2192, 480),
  activity('deploy', 2116, 548, ['Deploy'], 184),
  gate('g7', 2288, 656),
  activity('release-review', 2116, 640, ['Review', 'Release'], 144),
  activity('retirement-plan', 2420, 320, ['Plan', 'Retirement']),
  activity('migrate', 2588, 320, ['Migrate &', 'Archive Data']),
  activity('decommission', 2588, 432, ['Decommission', 'Service']),
  activity('postmortem', 2420, 432, ['Postmortem']),
  gate('g8', 2468, 568),
  activity('observe', 1700, 896, ['Operate &', 'Observe'], 160),
  activity('classify', 1924, 896, ['Register &', 'Classify Work'], 160),
  activity('kanban', 2148, 896, ['Manage Flow', '(Kanban)'], 160),
]

function flow(from: string, to: string, points: Point[], feedback = false): ProcessEdge {
  return { from, to, points, feedback }
}

// All 54 directed relationships in process.svg, including returns to groups.
export const processEdges: ProcessEdge[] = [
  flow('goals', 'feasibility', [[172, 312], [212, 312]]),
  flow('feasibility', 'cost', [[276, 344], [276, 368], [192, 368], [192, 396]]),
  flow('cost', 'g0', [[192, 460], [192, 492]]),
  flow('g0', 'feasibility', [[208, 508], [344, 508], [344, 380], [300, 380], [300, 344]], true),
  flow('g0', 'method', [[176, 508], [108, 508], [108, 556]]),
  flow('method', 'teams', [[172, 588], [212, 588]]),
  flow('teams', 'g1', [[276, 620], [276, 640], [296, 640], [296, 660]]),
  flow('g1', 'method', [[280, 676], [108, 676], [108, 620]], true),
  flow('g1', 'plan', [[312, 676], [416, 676], [416, 276], [584, 276]]),
  flow('plan', 'discover', [[584, 288], [496, 288], [496, 436], [512, 436]]),
  flow('discover', 'explore', [[640, 436], [656, 436]]),
  flow('explore', 'compose', [[720, 468], [720, 508]]),
  flow('compose', 'domain-review', [[656, 540], [640, 540]]),
  flow('domain-review', 'g2', [[576, 572], [576, 596], [752, 596], [752, 620]]),
  flow('g2', 'domain-review', [[736, 636], [536, 636], [536, 572]], true),
  flow('g2', 'boundaries', [[768, 636], [832, 636], [832, 332], [884, 332]]),
  flow('boundaries', 'contracts', [[1012, 332], [1068, 332]]),
  flow('contracts', 'compute', [[1132, 364], [1132, 408]]),
  flow('compute', 'flows', [[1068, 440], [1012, 440]]),
  flow('flows', 'architecture-review', [[948, 472], [948, 492], [1040, 492], [1040, 516]]),
  flow('architecture-review', 'g3', [[1040, 580], [1040, 624]]),
  flow('g3', 'architecture-review', [[1024, 640], [1000, 640], [1000, 580]], true),
  flow('g3', 'domain', [[1040, 656], [1040, 712], [824, 712], [824, 656], [804, 656]], true),
  flow('g3', 'deployment', [[1056, 640], [1236, 640], [1236, 332], [1276, 332]]),
  flow('deployment', 'storage', [[1404, 332], [1444, 332]]),
  flow('storage', 'workflows', [[1520, 364], [1520, 408]]),
  flow('workflows', 'cloud-review', [[1444, 440], [1404, 440]]),
  flow('cloud-review', 'g4', [[1340, 472], [1340, 500], [1416, 500], [1416, 540]]),
  flow('g4', 'cloud-review', [[1400, 556], [1308, 556], [1308, 472]], true),
  flow('g4', 'architecture', [[1416, 572], [1416, 720], [1236, 720], [1236, 664], [1216, 664]], true),
  flow('g4', 'generate', [[1432, 556], [1656, 556], [1656, 328], [1700, 328]]),
  flow('generate', 'code', [[1828, 328], [1868, 328]]),
  flow('code', 'local-test', [[1936, 360], [1936, 384], [1848, 384], [1848, 408]]),
  flow('local-test', 'g5', [[1848, 472], [1848, 516]]),
  flow('g5', 'code', [[1864, 532], [2000, 532], [2000, 392], [1960, 392], [1960, 360]], true),
  flow('g5', 'cloud', [[1832, 532], [1728, 532], [1728, 612], [1608, 612]], true),
  flow('g5', 'iteration-review', [[1848, 548], [1848, 592]]),
  flow('iteration-review', 'assemble', [[1932, 624], [2056, 624], [2056, 284], [2116, 284]]),
  flow('assemble', 'large-test', [[2208, 316], [2208, 376]]),
  flow('large-test', 'g6', [[2208, 440], [2208, 480]]),
  flow('g6', 'deploy', [[2208, 512], [2208, 548]]),
  flow('g6', 'implementation', [[2192, 496], [2072, 496], [2072, 648], [2016, 648]], true),
  flow('deploy', 'g7', [[2248, 612], [2248, 628], [2304, 628], [2304, 656]]),
  flow('g7', 'release-review', [[2288, 672], [2260, 672]]),
  flow('g7', 'retirement-plan', [[2320, 672], [2368, 672], [2368, 352], [2420, 352]]),
  flow('retirement-plan', 'migrate', [[2548, 352], [2588, 352]]),
  flow('migrate', 'decommission', [[2652, 384], [2652, 432]]),
  flow('decommission', 'postmortem', [[2588, 464], [2548, 464]]),
  flow('postmortem', 'g8', [[2484, 496], [2484, 568]]),
  flow('g8', 'decommission', [[2500, 584], [2692, 584], [2692, 496]], true),
  flow('g7', 'observe', [[2304, 688], [2304, 792], [1664, 792], [1664, 928], [1700, 928]]),
  flow('observe', 'classify', [[1860, 928], [1924, 928]]),
  flow('classify', 'kanban', [[2084, 928], [2148, 928]]),
  flow('classify', 'active', [[2004, 960], [2004, 1008], [1648, 1008], [1648, 760]]),
]

// Round each right-angle bend and bridge crossings with earlier connectors.
// The graph remains data; routing is rendered independently of the reference.
export function connectorPath(edge: ProcessEdge, earlier: ProcessEdge[] = []): string {
  const points = edge.points
  const commands = [`M ${points[0][0]} ${points[0][1]}`]
  let current = points[0]
  const distance = (a: Point, b: Point) => Math.abs(a[0] - b[0]) + Math.abs(a[1] - b[1])
  const approach = (from: Point, to: Point, length: number): Point => [
    from[0] + Math.sign(to[0] - from[0]) * length,
    from[1] + Math.sign(to[1] - from[1]) * length,
  ]
  function segment(to: Point) {
    const horizontal = current[1] === to[1]
    const start = current
    const hops: Point[] = []
    for (const previous of earlier) {
      for (let i = 1; i < previous.points.length; i++) {
        const a = previous.points[i - 1], b = previous.points[i]
        if (horizontal === (a[1] === b[1])) continue
        const cross: Point = horizontal ? [a[0], start[1]] : [start[0], a[1]]
        const inside = (p: Point, a: Point, b: Point) => distance(a, p) + distance(p, b) === distance(a, b)
        if (inside(cross, start, to) && inside(cross, a, b) && distance(cross, start) > 12 && distance(cross, to) > 12 && distance(cross, a) > 12 && distance(cross, b) > 12) hops.push(cross)
      }
    }
    hops.sort((a, b) => distance(start, a) - distance(start, b))
    let previousDistance = -20
    for (const cross of hops) {
      const offset = distance(start, cross)
      if (offset - previousDistance < 16) continue
      const before = approach(cross, start, 6), after = approach(cross, to, 6)
      commands.push(`L ${before[0]} ${before[1]} A 6 6 0 0 1 ${after[0]} ${after[1]}`)
      previousDistance = offset
    }
    commands.push(`L ${to[0]} ${to[1]}`)
    current = to
  }
  for (let i = 1; i < points.length; i++) {
    const corner = points[i], next = points[i + 1]
    if (!next) { segment(corner); break }
    const radius = Math.min(8, distance(points[i - 1], corner) / 2, distance(corner, next) / 2)
    const before = approach(corner, points[i - 1], radius), after = approach(corner, next, radius)
    segment(before)
    commands.push(`Q ${corner[0]} ${corner[1]} ${after[0]} ${after[1]}`)
    current = after
  }
  return commands.join(' ')
}
