/**
 * Runtime contract for the workflow graph.
 *
 * This mirrors the invariants enforced by the Java `WorkflowGraphValidator`:
 *   - schemaVersion must be 1
 *   - entryNode must reference an existing node
 *   - node ids must be unique
 *   - transitions are either a list of target node ids (fan-out) or a `{ terminal }` map
 *
 * Keep this file in sync with:
 *   src/main/java/com/fuba/automation_engine/service/workflow/WorkflowGraphValidator.java
 *
 * All builder code MUST import node/edge/graph types from here. Any divergence
 * between this schema and the engine's runtime is a bug in the builder.
 */
import { z } from 'zod'

export const terminalTransitionSchema = z.object({
  terminal: z.string().min(1),
})

export const fanoutTransitionSchema = z.array(z.string().min(1))

export const transitionValueSchema = z.union([terminalTransitionSchema, fanoutTransitionSchema])

export const graphNodeSchema = z.object({
  id: z.string().min(1),
  type: z.string().min(1),
  config: z.record(z.string(), z.unknown()).optional().default({}),
  transitions: z.record(z.string(), transitionValueSchema).optional().default({}),
})

export const graphSchema = z.object({
  schemaVersion: z.literal(1),
  entryNode: z.string().min(1),
  nodes: z.array(graphNodeSchema).min(1),
})

export type GraphNode = z.infer<typeof graphNodeSchema>
export type TerminalTransition = z.infer<typeof terminalTransitionSchema>
export type FanoutTransition = z.infer<typeof fanoutTransitionSchema>
export type TransitionValue = z.infer<typeof transitionValueSchema>
export type Graph = z.infer<typeof graphSchema>

export function isTerminalTransition(value: TransitionValue): value is TerminalTransition {
  return !Array.isArray(value) && typeof value === 'object' && value !== null && 'terminal' in value
}

export function isFanoutTransition(value: TransitionValue): value is FanoutTransition {
  return Array.isArray(value)
}

/** Create an empty, valid graph seeded with a single entry placeholder. */
export function emptyGraph(entryNodeId = 'start'): Graph {
  return {
    schemaVersion: 1,
    entryNode: entryNodeId,
    nodes: [
      {
        id: entryNodeId,
        type: 'set_variable',
        config: {},
        transitions: {},
      },
    ],
  }
}

/** True when a parsed value conforms to the graph shape. */
export function isGraph(value: unknown): value is Graph {
  return graphSchema.safeParse(value).success
}
