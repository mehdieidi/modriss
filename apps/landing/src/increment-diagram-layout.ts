export type IncrementNode = {
  id: string
  x: number
  y: number
  width: number
  height: number
  tag?: string
  name: string[]
  owner: string
  gate?: string
}

type Point = [number, number]
type Connection = {
  from: string
  to: string
  points: Point[]
  feedback?: boolean
  label?: { text: string; x: number; y: number; width: number }
}

export type IncrementLayout = {
  kind: 'wide' | 'tablet' | 'phone'
  width: number
  height: number
  nodes: IncrementNode[]
  gate: { x: number; y: number; radius: number }
  edges: Connection[]
}

const content = {
  plan: { name: ['Plan', 'Increment'], owner: 'Product Owner' },
  cim: { tag: 'CIM', name: ['Domain Modeling'], owner: 'Business Modeler', gate: 'G2' },
  pim: { tag: 'PIM', name: ['Serverless Design'], owner: 'Solutions Architect', gate: 'G3' },
  psm: { tag: 'PSM', name: ['Cloud Realization'], owner: 'Cloud Engineer', gate: 'G4' },
  artifacts: { tag: 'Artifacts', name: ['Complete & Test'], owner: 'Software Engineer' },
  release: { tag: 'Increment', name: ['Release Assembly'], owner: 'Software Engineer' },
}

function node(id: keyof typeof content, x: number, y: number, width: number, height = 148): IncrementNode {
  return { id, x, y, width, height, ...content[id] }
}

function label(text: string, x: number, y: number, width: number) {
  return { text, x, y, width }
}

// New coordinates and routes for each presentation; the same directed graph in all three.
export function incrementLayoutForWidth(availableWidth: number): IncrementLayout {
  if (availableWidth >= 1000) return {
    kind: 'wide', width: 1120, height: 500,
    nodes: [node('plan', 16, 80, 224), node('cim', 304, 80, 224), node('pim', 592, 80, 224), node('psm', 880, 80, 224), node('artifacts', 880, 320, 224), node('release', 392, 320, 224)],
    gate: { x: 752, y: 394, radius: 32 },
    edges: [
      { from: 'plan', to: 'cim', points: [[240, 154], [304, 154]] },
      { from: 'cim', to: 'pim', points: [[528, 154], [592, 154]], label: label('ETL', 560, 138, 36) },
      { from: 'pim', to: 'psm', points: [[816, 154], [880, 154]], label: label('ETL', 848, 138, 36) },
      { from: 'psm', to: 'artifacts', points: [[1008, 228], [1008, 320]], label: label('EGX/EGL', 1058, 278, 80) },
      { from: 'artifacts', to: 'g5', points: [[880, 394], [784, 394]] },
      { from: 'g5', to: 'release', points: [[720, 394], [616, 394]], label: label('Accept', 668, 378, 56) },
      { from: 'pim', to: 'cim', feedback: true, points: [[700, 80], [700, 40], [416, 40], [416, 80]], label: label('Domain Gap', 558, 24, 100) },
      { from: 'psm', to: 'pim', feedback: true, points: [[992, 80], [992, 40], [736, 40], [736, 80]], label: label('Architecture Gap', 864, 24, 140) },
      { from: 'artifacts', to: 'psm', feedback: true, points: [[936, 320], [936, 228]], label: label('Model Gap', 876, 278, 100) },
      { from: 'g5', to: 'artifacts', feedback: true, points: [[752, 426], [752, 444], [880, 444]], label: label('Rework', 816, 468, 64) },
      { from: 'release', to: 'plan', feedback: true, points: [[392, 394], [128, 394], [128, 228]], label: label('Next Slice', 260, 378, 96) },
    ],
  }

  if (availableWidth >= 600) return {
    kind: 'tablet', width: 720, height: 784,
    nodes: [node('plan', 40, 56, 244), node('cim', 436, 56, 244), node('pim', 436, 300, 244), node('psm', 40, 300, 244), node('artifacts', 40, 544, 244), node('release', 436, 544, 244)],
    gate: { x: 360, y: 618, radius: 28 },
    edges: [
      { from: 'plan', to: 'cim', points: [[284, 130], [436, 130]] },
      { from: 'cim', to: 'pim', points: [[550, 204], [550, 300]], label: label('ETL', 520, 256, 36) },
      { from: 'pim', to: 'psm', points: [[436, 374], [284, 374]], label: label('ETL', 360, 358, 36) },
      { from: 'psm', to: 'artifacts', points: [[232, 448], [232, 544]], label: label('EGX/EGL', 282, 500, 80) },
      { from: 'artifacts', to: 'g5', points: [[284, 618], [332, 618]] },
      { from: 'g5', to: 'release', points: [[388, 618], [436, 618]], label: label('Accept', 412, 602, 44) },
      { from: 'pim', to: 'cim', feedback: true, points: [[680, 340], [704, 340], [704, 168], [680, 168]], label: label('Domain Gap', 638, 256, 100) },
      { from: 'psm', to: 'pim', feedback: true, points: [[284, 418], [436, 418]], label: label('Architecture Gap', 360, 444, 112) },
      { from: 'artifacts', to: 'psm', feedback: true, points: [[152, 544], [152, 448]], label: label('Model Gap', 98, 500, 88) },
      { from: 'g5', to: 'artifacts', feedback: true, points: [[360, 646], [360, 668], [284, 668]], label: label('Rework', 322, 692, 64) },
      { from: 'release', to: 'plan', feedback: true, points: [[550, 692], [550, 748], [16, 748], [16, 130], [40, 130]], label: label('Next Slice', 284, 732, 96) },
    ],
  }

  const width = Math.min(340, Math.max(240, Math.floor(availableWidth)))
  const center = width / 2
  const right = width - 32
  const rail = width - 8
  const height = 88
  const feedbackLabel = (text: string, y: number, size: number) => label(text, rail - 8 - size / 2, y, size)
  const forwardLabel = (text: string, y: number, size: number) => label(text, center - 10 - size / 2, y, size)
  return {
    kind: 'phone', width, height: 824,
    nodes: [{ ...node('plan', 32, 16, width - 64, height), name: ['Plan Increment'] }, node('cim', 32, 140, width - 64, height), node('pim', 32, 264, width - 64, height), node('psm', 32, 388, width - 64, height), node('artifacts', 32, 512, width - 64, height), node('release', 32, 720, width - 64, height)],
    gate: { x: center, y: 656, radius: 22 },
    edges: [
      { from: 'plan', to: 'cim', points: [[center, 104], [center, 140]] },
      { from: 'cim', to: 'pim', points: [[center, 228], [center, 264]], label: forwardLabel('ETL', 250, 32) },
      { from: 'pim', to: 'psm', points: [[center, 352], [center, 388]], label: forwardLabel('ETL', 374, 32) },
      { from: 'psm', to: 'artifacts', points: [[center, 476], [center, 512]], label: forwardLabel('EGX/EGL', 498, 68) },
      { from: 'artifacts', to: 'g5', points: [[center, 600], [center, 634]] },
      { from: 'g5', to: 'release', points: [[center, 678], [center, 720]], label: label('Accept', center + 40, 702, 56) },
      { from: 'pim', to: 'cim', feedback: true, points: [[right, 288], [rail, 288], [rail, 204], [right, 204]], label: feedbackLabel('Domain Gap', 250, 84) },
      { from: 'psm', to: 'pim', feedback: true, points: [[right, 412], [rail, 412], [rail, 328], [right, 328]], label: feedbackLabel('Architecture Gap', 374, 112) },
      { from: 'artifacts', to: 'psm', feedback: true, points: [[right, 536], [rail, 536], [rail, 452], [right, 452]], label: feedbackLabel('Model Gap', 498, 80) },
      { from: 'g5', to: 'artifacts', feedback: true, points: [[center + 22, 656], [rail, 656], [rail, 576], [right, 576]], label: feedbackLabel('Rework', 624, 64) },
      { from: 'release', to: 'plan', feedback: true, points: [[32, 780], [8, 780], [8, 76], [32, 76]], label: label('Next Slice', 62, 702, 88) },
    ],
  }
}

// Round each orthogonal elbow without changing endpoints or sharing connector strokes.
export function incrementConnectorPath(points: Point[]): string {
  let path = `M${points[0].join(' ')}`
  for (let i = 1; i < points.length - 1; i++) {
    const [before, corner, after] = [points[i - 1], points[i], points[i + 1]]
    const incoming = Math.hypot(corner[0] - before[0], corner[1] - before[1])
    const outgoing = Math.hypot(after[0] - corner[0], after[1] - corner[1])
    const radius = Math.min(8, incoming / 2, outgoing / 2)
    const start = corner.map((v, axis) => v + (before[axis] - v) * radius / incoming)
    const end = corner.map((v, axis) => v + (after[axis] - v) * radius / outgoing)
    path += ` L${start.join(' ')} Q${corner.join(' ')} ${end.join(' ')}`
  }
  return `${path} L${points[points.length - 1].join(' ')}`
}
