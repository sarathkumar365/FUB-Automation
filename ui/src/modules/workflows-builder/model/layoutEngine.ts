/**
 * Deterministic storyboard layout using Dagre (TB).
 *
 * Why deterministic: when two engineers compare their screens, the scenes
 * MUST be in the same place. Freeform drag-and-drop layouts cause real debug
 * fires ("looks fine on my screen" vs "why is that card way over there?").
 *
 * We compute positions from the graph structure alone. The user does not
 * rearrange scenes. If they want a different layout, they change the graph.
 *
 * Coordinates are in SVG user-space pixels. The caller sizes the SVG
 * viewBox from `width` / `height` on the returned layout.
 *
 * Orientation: top → bottom. Siblings spread horizontally; ranks stack down.
 */
import dagre from '@dagrejs/dagre'
import type { StoryboardModel } from './graphAdapters'

export const SCENE_WIDTH = 260
export const SCENE_HEIGHT = 110
const NODE_SEP = 64
const RANK_SEP = 72
const MARGIN = 40

export interface SceneLayout {
  id: string
  /** Center x in SVG user-space pixels. */
  x: number
  /** Center y in SVG user-space pixels. */
  y: number
  width: number
  height: number
}

export interface StoryboardLayout {
  scenes: Map<string, SceneLayout>
  width: number
  height: number
}

export function layoutStoryboard(model: StoryboardModel): StoryboardLayout {
  const g = new dagre.graphlib.Graph()
  g.setGraph({
    rankdir: 'TB',
    nodesep: NODE_SEP,
    ranksep: RANK_SEP,
    marginx: MARGIN,
    marginy: MARGIN,
  })
  g.setDefaultEdgeLabel(() => ({}))

  for (const scene of model.scenes) {
    g.setNode(scene.id, { width: SCENE_WIDTH, height: SCENE_HEIGHT })
  }
  for (const exit of model.exits) {
    g.setEdge(exit.fromSceneId, exit.toSceneId)
  }

  dagre.layout(g)

  const scenes = new Map<string, SceneLayout>()
  for (const id of g.nodes()) {
    const node = g.node(id)
    if (!node) continue
    scenes.set(id, {
      id,
      x: node.x,
      y: node.y,
      width: node.width ?? SCENE_WIDTH,
      height: node.height ?? SCENE_HEIGHT,
    })
  }
  const graphLabel = g.graph()
  return {
    scenes,
    width: graphLabel?.width ?? SCENE_WIDTH + MARGIN * 2,
    height: graphLabel?.height ?? SCENE_HEIGHT + MARGIN * 2,
  }
}
