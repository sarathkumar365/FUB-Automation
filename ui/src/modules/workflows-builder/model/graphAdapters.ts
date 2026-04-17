/**
 * Graph ↔ Storyboard transforms.
 *
 * The runtime graph (see state/runtimeContract.ts) is optimized for the
 * engine: flat nodes list + per-node `transitions[resultCode] = target`.
 * The storyboard surface needs a rendering-friendly projection: explicit
 * nodes + typed edges + terminal markers.
 *
 * The projection is DERIVED — the runtime graph is canonical. Never mutate
 * the projection; dispatch an action instead.
 */
import {
  isFanoutTransition,
  isTerminalTransition,
  type Graph,
  type GraphNode,
} from '../state/runtimeContract'

export type SceneKind = 'trigger' | 'step'

export interface StoryboardScene {
  id: string
  kind: SceneKind
  /** Raw step type id (e.g. `branch_on_field`, `slack_notify`). Trigger scene uses `__trigger__`. */
  stepType: string
  /** Node config as-is from the graph — card formatters read this. */
  config: Record<string, unknown>
  /** True when this is the graph's declared entry node. */
  isEntry: boolean
}

export interface StoryboardExitEdge {
  id: string
  fromSceneId: string
  toSceneId: string
  resultCode: string
}

export interface StoryboardTerminal {
  id: string
  fromSceneId: string
  resultCode: string
  reason: string
}

export interface StoryboardModel {
  scenes: StoryboardScene[]
  exits: StoryboardExitEdge[]
  terminals: StoryboardTerminal[]
  /** Ids in topological-ish order (entry first, then BFS). Storyboard uses this for spine order. */
  sceneOrder: string[]
}

const TRIGGER_SCENE_ID = '__trigger__'

export function graphToStoryboard(
  graph: Graph,
  trigger: Record<string, unknown> | null,
): StoryboardModel {
  const scenes: StoryboardScene[] = []
  const exits: StoryboardExitEdge[] = []
  const terminals: StoryboardTerminal[] = []

  if (trigger) {
    scenes.push({
      id: TRIGGER_SCENE_ID,
      kind: 'trigger',
      stepType: typeof trigger.type === 'string' ? trigger.type : '__trigger__',
      config: (trigger.config as Record<string, unknown> | undefined) ?? {},
      isEntry: false,
    })
    exits.push({
      id: `${TRIGGER_SCENE_ID}->${graph.entryNode}`,
      fromSceneId: TRIGGER_SCENE_ID,
      toSceneId: graph.entryNode,
      resultCode: 'fires',
    })
  }

  for (const node of graph.nodes) {
    scenes.push(nodeToScene(node, graph.entryNode))
    const transitions = node.transitions ?? {}
    for (const [resultCode, value] of Object.entries(transitions)) {
      if (isFanoutTransition(value)) {
        for (const target of value) {
          exits.push({
            id: `${node.id}--${resultCode}-->${target}`,
            fromSceneId: node.id,
            toSceneId: target,
            resultCode,
          })
        }
      } else if (isTerminalTransition(value)) {
        terminals.push({
          id: `${node.id}--${resultCode}--terminal`,
          fromSceneId: node.id,
          resultCode,
          reason: value.terminal,
        })
      }
    }
  }

  return {
    scenes,
    exits,
    terminals,
    sceneOrder: computeSceneOrder(scenes, exits, trigger ? TRIGGER_SCENE_ID : graph.entryNode),
  }
}

function nodeToScene(node: GraphNode, entryNodeId: string): StoryboardScene {
  return {
    id: node.id,
    kind: 'step',
    stepType: node.type,
    config: (node.config ?? {}) as Record<string, unknown>,
    isEntry: node.id === entryNodeId,
  }
}

/**
 * BFS from the root scene so the storyboard draws strictly left-to-right in
 * execution order. Any orphan scenes (shouldn't happen in a valid graph) are
 * appended at the end so we never silently drop them.
 */
function computeSceneOrder(
  scenes: StoryboardScene[],
  exits: StoryboardExitEdge[],
  rootId: string,
): string[] {
  const order: string[] = []
  const visited = new Set<string>()
  const queue: string[] = [rootId]
  const adjacency = new Map<string, string[]>()
  for (const exit of exits) {
    const list = adjacency.get(exit.fromSceneId) ?? []
    list.push(exit.toSceneId)
    adjacency.set(exit.fromSceneId, list)
  }
  while (queue.length > 0) {
    const current = queue.shift() as string
    if (visited.has(current)) continue
    visited.add(current)
    order.push(current)
    for (const next of adjacency.get(current) ?? []) {
      if (!visited.has(next)) queue.push(next)
    }
  }
  for (const scene of scenes) {
    if (!visited.has(scene.id)) order.push(scene.id)
  }
  return order
}
