/**
 * Named action creators for the workflow builder store.
 *
 * Every mutation to the builder graph flows through one of these actions, which
 * gives us two big wins:
 *   1. Time-travel replay: the action log is a deterministic script of what the
 *      user did. Re-applying the script to the original graph should always
 *      produce the same hash (see contentHash.ts).
 *   2. Consistent telemetry: the dispatch wrapper in builderStore.ts owns the
 *      hash-before / hash-after / correlation-id plumbing, so every action
 *      gets the same observability treatment for free.
 *
 * Actions are plain data; the reducer lives in builderStore.ts.
 */
import type { Graph } from './runtimeContract'

export type BuilderAction =
  | { type: 'graph/load'; graph: Graph }
  | { type: 'graph/reset' }
  | { type: 'node/select'; nodeId: string | null }
  | { type: 'node/updateConfig'; nodeId: string; config: Record<string, unknown> }
  | { type: 'node/add'; node: Graph['nodes'][number]; afterNodeId?: string; resultCode?: string }
  | { type: 'node/remove'; nodeId: string }
  | { type: 'edge/add'; fromNodeId: string; resultCode: string; toNodeId: string }
  | { type: 'edge/removeByResultCode'; fromNodeId: string; resultCode: string }
  | { type: 'edge/setTerminal'; fromNodeId: string; resultCode: string; terminal: string }
  | { type: 'entry/set'; nodeId: string }
  | { type: 'trigger/set'; trigger: Record<string, unknown> | null }
  | { type: 'surface/switch'; surface: 'storyboard' | 'outline' | 'json' }

export type BuilderActionType = BuilderAction['type']

/** Name of the action for logs / debug overlay. */
export function actionName(action: BuilderAction): string {
  return action.type
}
