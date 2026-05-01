import { describe, expect, it, beforeEach } from 'vitest'
import { contentHash } from '../modules/workflows-builder/state/contentHash'
import {
  emptyGraph,
  graphSchema,
  isFanoutTransition,
  isTerminalTransition,
  type Graph,
} from '../modules/workflows-builder/state/runtimeContract'
import { graphToStoryboard } from '../modules/workflows-builder/model/graphAdapters'
import { layoutStoryboard } from '../modules/workflows-builder/model/layoutEngine'
import { formatScene, registeredStepTypes } from '../modules/workflows-builder/model/cardFormatters'
import { resetBuilderStore, useBuilderStore } from '../modules/workflows-builder/state/builderStore'
import { getActionLog, resetActionLog } from '../modules/workflows-builder/state/actionLog'

describe('contentHash', () => {
  it('is stable across key insertion order', () => {
    const a = { x: 1, y: { a: 1, b: 2 } }
    const b = { y: { b: 2, a: 1 }, x: 1 }
    expect(contentHash(a)).toBe(contentHash(b))
  })

  it('changes when values change', () => {
    expect(contentHash({ x: 1 })).not.toBe(contentHash({ x: 2 }))
  })

  it('produces an 8-char hex digest', () => {
    expect(contentHash({ x: 1 })).toMatch(/^[0-9a-f]{8}$/)
  })
})

describe('runtimeContract', () => {
  it('accepts an empty graph', () => {
    expect(graphSchema.safeParse(emptyGraph()).success).toBe(true)
  })

  it('rejects non-v1 schemaVersion', () => {
    const bad = { ...emptyGraph(), schemaVersion: 2 }
    expect(graphSchema.safeParse(bad).success).toBe(false)
  })

  it('distinguishes terminal and fanout transitions', () => {
    expect(isTerminalTransition({ terminal: 'done' })).toBe(true)
    expect(isTerminalTransition(['a', 'b'])).toBe(false)
    expect(isFanoutTransition(['a'])).toBe(true)
    expect(isFanoutTransition({ terminal: 'x' })).toBe(false)
  })
})

describe('graphToStoryboard', () => {
  const graph: Graph = {
    schemaVersion: 1,
    entryNode: 'root',
    nodes: [
      {
        id: 'root',
        type: 'branch_on_field',
        config: { field: 'status', cases: [{ code: 'won' }, { code: 'lost' }] },
        transitions: { won: ['celebrate'], lost: { terminal: 'dropped' } },
      },
      { id: 'celebrate', type: 'slack_notify', config: { channel: '#sales' }, transitions: {} },
    ],
  }

  it('emits a scene per node', () => {
    const model = graphToStoryboard(graph, null)
    expect(model.scenes.map((s) => s.id).sort()).toEqual(['celebrate', 'root'])
  })

  it('flags entry node', () => {
    const model = graphToStoryboard(graph, null)
    expect(model.scenes.find((s) => s.id === 'root')?.isEntry).toBe(true)
  })

  it('emits fanout exits and terminal pills separately', () => {
    const model = graphToStoryboard(graph, null)
    expect(model.exits).toHaveLength(1)
    expect(model.exits[0]).toMatchObject({ fromSceneId: 'root', toSceneId: 'celebrate', resultCode: 'won' })
    expect(model.terminals).toHaveLength(1)
    expect(model.terminals[0]).toMatchObject({ fromSceneId: 'root', resultCode: 'lost', reason: 'dropped' })
  })

  it('prepends trigger scene with `fires` exit to entry', () => {
    const model = graphToStoryboard(graph, { type: 'fub_event', config: {} })
    expect(model.scenes[0].id).toBe('__trigger__')
    const triggerExit = model.exits.find((e) => e.fromSceneId === '__trigger__')
    expect(triggerExit?.toSceneId).toBe('root')
  })

  it('orders scenes BFS from root', () => {
    const model = graphToStoryboard(graph, null)
    expect(model.sceneOrder).toEqual(['root', 'celebrate'])
  })
})

describe('layoutStoryboard', () => {
  it('returns a layout entry for every scene', () => {
    const graph: Graph = emptyGraph()
    const model = graphToStoryboard(graph, null)
    const layout = layoutStoryboard(model)
    expect(layout.scenes.size).toBe(model.scenes.length)
    expect(layout.width).toBeGreaterThan(0)
    expect(layout.height).toBeGreaterThan(0)
  })

  it('is deterministic for the same input', () => {
    const graph: Graph = emptyGraph()
    const model = graphToStoryboard(graph, null)
    const a = layoutStoryboard(model)
    const b = layoutStoryboard(model)
    for (const [id, pos] of a.scenes) {
      expect(b.scenes.get(id)).toEqual(pos)
    }
  })
})

describe('cardFormatters', () => {
  it('formats branch_on_field with field + case count', () => {
    expect(formatScene('branch_on_field', { field: 'status', cases: [{}, {}, {}] })).toMatchObject({
      title: 'Branch on status',
      summary: '3 cases',
      accent: 'branch',
    })
  })

  it('formats slack_notify with channel', () => {
    expect(formatScene('slack_notify', { channel: '#sales' })).toMatchObject({
      accent: 'side-effect',
      summary: '→ #sales',
    })
  })

  it('falls back gracefully for unknown step types', () => {
    expect(formatScene('does_not_exist', { foo: 'bar' }).accent).toBe('neutral')
  })

  it('renders unregistered step types with a humane title-cased fallback', () => {
    const formatted = formatScene('my_custom_thing', {})
    expect(formatted.title).toBe('My Custom Thing')
    expect(formatted.accent).toBe('neutral')
  })

  it('formats the synthetic trigger scene with a meaningful title', () => {
    const withType = formatScene('__trigger__', { type: 'webhook' })
    expect(withType.title).toBe('Trigger: webhook')
    expect(withType.accent).toBe('trigger')

    const bare = formatScene('__trigger__', {})
    expect(bare.title).toBe('Trigger')
    expect(bare.accent).toBe('trigger')
  })

  it('registers all 10 engine step types', () => {
    expect(registeredStepTypes()).toEqual([
      'branch_on_field',
      'delay',
      'fub_add_tag',
      'fub_move_to_pond',
      'fub_reassign',
      'http_request',
      'set_variable',
      'slack_notify',
      'wait_and_check_claim',
      'wait_and_check_communication',
    ])
  })
})

describe('builderStore.dispatch', () => {
  beforeEach(() => {
    resetBuilderStore()
    resetActionLog()
  })

  it('updates hash and logs action', () => {
    const before = useBuilderStore.getState().graphHash
    useBuilderStore.getState().dispatch({
      type: 'node/updateConfig',
      nodeId: 'start',
      config: { name: 'x', value: 1 },
    })
    const after = useBuilderStore.getState().graphHash
    expect(after).not.toBe(before)
    const log = getActionLog()
    expect(log).toHaveLength(1)
    expect(log[0]).toMatchObject({ action: 'node/updateConfig', hashBefore: before, hashAfter: after })
  })

  it('node/select does not change graph hash', () => {
    const before = useBuilderStore.getState().graphHash
    useBuilderStore.getState().dispatch({ type: 'node/select', nodeId: 'start' })
    const after = useBuilderStore.getState().graphHash
    expect(after).toBe(before)
    expect(useBuilderStore.getState().layout.selectedNodeId).toBe('start')
  })

  it('edge/add wires result code into transitions', () => {
    const store = useBuilderStore.getState()
    store.dispatch({
      type: 'node/add',
      node: { id: 'n2', type: 'slack_notify', config: {}, transitions: {} },
    })
    store.dispatch({ type: 'edge/add', fromNodeId: 'start', resultCode: 'ok', toNodeId: 'n2' })
    const root = useBuilderStore.getState().graph.nodes.find((n) => n.id === 'start')
    expect(root?.transitions?.ok).toEqual(['n2'])
  })

  it('node/remove prunes inbound edges', () => {
    const store = useBuilderStore.getState()
    store.dispatch({
      type: 'node/add',
      node: { id: 'n2', type: 'slack_notify', config: {}, transitions: {} },
    })
    store.dispatch({ type: 'edge/add', fromNodeId: 'start', resultCode: 'ok', toNodeId: 'n2' })
    store.dispatch({ type: 'node/remove', nodeId: 'n2' })
    const root = useBuilderStore.getState().graph.nodes.find((n) => n.id === 'start')
    expect(root?.transitions?.ok).toBeUndefined()
  })
})
