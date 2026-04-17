/**
 * Canonical Zustand store for the workflow builder.
 *
 * One source of truth for all three surfaces (Storyboard / Outline / JSON):
 *   - `graph` is the workflow graph in runtime-contract shape.
 *   - `trigger` is the workflow trigger (top-level on save, rendered as the
 *     pinned opening scene in the storyboard).
 *   - `layout` holds non-persisted positions and UI-only state (selection,
 *     current surface). The server never sees `layout`.
 *
 * Every mutation goes through `dispatch(action)`, which:
 *   1. Computes the content hash of the graph before applying the reducer.
 *   2. Applies the reducer (pure).
 *   3. Computes the hash after.
 *   4. Pushes a record into the action log for the debug overlay.
 *
 * Keeping the reducer pure is what makes time-travel replay possible — see
 * observability/debugOverlay.tsx.
 */
import { create } from 'zustand'
import { contentHash } from './contentHash'
import { pushActionLogEntry } from './actionLog'
import { emptyGraph, isFanoutTransition, type Graph, type TransitionValue } from './runtimeContract'
import type { BuilderAction } from './actions'

export type BuilderSurface = 'storyboard' | 'outline' | 'json'

export interface BuilderLayoutState {
  selectedNodeId: string | null
  surface: BuilderSurface
}

export interface BuilderState {
  graph: Graph
  trigger: Record<string, unknown> | null
  layout: BuilderLayoutState
  /** Cached hash of the current graph; kept in sync by the dispatch wrapper. */
  graphHash: string
  /** Monotonically increasing dispatch counter. Handy for React keys / tests. */
  revision: number
  dispatch: (action: BuilderAction, meta?: { correlationId?: string }) => void
}

function reduce(state: BuilderState, action: BuilderAction): Partial<BuilderState> {
  switch (action.type) {
    case 'graph/load':
      return { graph: action.graph }
    case 'graph/reset':
      return { graph: emptyGraph() }
    case 'node/select':
      return { layout: { ...state.layout, selectedNodeId: action.nodeId } }
    case 'node/updateConfig': {
      const nodes = state.graph.nodes.map((node) =>
        node.id === action.nodeId ? { ...node, config: action.config } : node,
      )
      return { graph: { ...state.graph, nodes } }
    }
    case 'node/add': {
      const nodes = [...state.graph.nodes, action.node]
      let next: Graph = { ...state.graph, nodes }
      if (action.afterNodeId && action.resultCode) {
        next = applyEdge(next, action.afterNodeId, action.resultCode, action.node.id)
      }
      return { graph: next }
    }
    case 'node/remove': {
      const nodes = state.graph.nodes
        .filter((node) => node.id !== action.nodeId)
        .map((node) => ({
          ...node,
          transitions: pruneTransitionsTargeting(node.transitions ?? {}, action.nodeId),
        }))
      return { graph: { ...state.graph, nodes } }
    }
    case 'edge/add':
      return {
        graph: applyEdge(state.graph, action.fromNodeId, action.resultCode, action.toNodeId),
      }
    case 'edge/removeByResultCode': {
      const nodes = state.graph.nodes.map((node) => {
        if (node.id !== action.fromNodeId) return node
        const transitions = { ...(node.transitions ?? {}) }
        delete transitions[action.resultCode]
        return { ...node, transitions }
      })
      return { graph: { ...state.graph, nodes } }
    }
    case 'edge/setTerminal': {
      const nodes = state.graph.nodes.map((node) => {
        if (node.id !== action.fromNodeId) return node
        const transitions = { ...(node.transitions ?? {}) }
        transitions[action.resultCode] = { terminal: action.terminal }
        return { ...node, transitions }
      })
      return { graph: { ...state.graph, nodes } }
    }
    case 'entry/set':
      return { graph: { ...state.graph, entryNode: action.nodeId } }
    case 'trigger/set':
      return { trigger: action.trigger }
    case 'surface/switch':
      return { layout: { ...state.layout, surface: action.surface } }
    default: {
      const _exhaustive: never = action
      return _exhaustive
    }
  }
}

function applyEdge(graph: Graph, fromNodeId: string, resultCode: string, toNodeId: string): Graph {
  const nodes = graph.nodes.map((node) => {
    if (node.id !== fromNodeId) return node
    const transitions = { ...(node.transitions ?? {}) }
    const existing: TransitionValue | undefined = transitions[resultCode]
    if (existing && isFanoutTransition(existing)) {
      transitions[resultCode] = existing.includes(toNodeId) ? existing : [...existing, toNodeId]
    } else {
      transitions[resultCode] = [toNodeId]
    }
    return { ...node, transitions }
  })
  return { ...graph, nodes }
}

function pruneTransitionsTargeting(
  transitions: Record<string, TransitionValue>,
  removedNodeId: string,
): Record<string, TransitionValue> {
  const out: Record<string, TransitionValue> = {}
  for (const [key, value] of Object.entries(transitions)) {
    if (isFanoutTransition(value)) {
      const filtered = value.filter((id) => id !== removedNodeId)
      if (filtered.length > 0) out[key] = filtered
      // else drop the edge entirely
    } else {
      out[key] = value
    }
  }
  return out
}

const initialGraph: Graph = emptyGraph()

export const useBuilderStore = create<BuilderState>((set, get) => ({
  graph: initialGraph,
  trigger: null,
  layout: { selectedNodeId: null, surface: 'storyboard' },
  graphHash: contentHash(initialGraph),
  revision: 0,
  dispatch(action, meta) {
    const started = performance.now()
    const state = get()
    const hashBefore = state.graphHash
    const patch = reduce(state, action)
    const nextGraph = patch.graph ?? state.graph
    const hashAfter = patch.graph ? contentHash(nextGraph) : hashBefore
    set({
      ...state,
      ...patch,
      graphHash: hashAfter,
      revision: state.revision + 1,
    })
    pushActionLogEntry({
      action: action.type,
      payload: action,
      hashBefore,
      hashAfter,
      correlationId: meta?.correlationId,
      durationMs: performance.now() - started,
    })
  },
}))

/** Test helper: reset the store to pristine state. */
export function resetBuilderStore(): void {
  const fresh = emptyGraph()
  useBuilderStore.setState({
    graph: fresh,
    trigger: null,
    layout: { selectedNodeId: null, surface: 'storyboard' },
    graphHash: contentHash(fresh),
    revision: 0,
  })
}
