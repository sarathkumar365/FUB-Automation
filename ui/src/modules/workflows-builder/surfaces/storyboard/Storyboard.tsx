/**
 * Top-level storyboard surface.
 *
 * Pure-ish view component: given a graph + trigger from the builder store,
 * it computes the projection + layout with `useMemo` and renders the SVG.
 * Selection writes back to the store via `dispatch({ type: 'node/select' })`
 * so the debug overlay records it.
 *
 * Phase 1 constraints:
 *   - read-only; no drag, no inline edit.
 *   - no panning/zooming; the SVG just sizes to fit its content.
 */
import { useMemo } from 'react'
import { graphToStoryboard } from '../../model/graphAdapters'
import { layoutStoryboard } from '../../model/layoutEngine'
import { useBuilderStore } from '../../state/builderStore'
import { ExitEdge } from './ExitEdge'
import { Scene } from './Scene'
import { Spine } from './Spine'
import { TerminalPill } from './TerminalPill'

export function Storyboard() {
  const graph = useBuilderStore((state) => state.graph)
  const trigger = useBuilderStore((state) => state.trigger)
  const selectedNodeId = useBuilderStore((state) => state.layout.selectedNodeId)
  const dispatch = useBuilderStore((state) => state.dispatch)

  const model = useMemo(() => graphToStoryboard(graph, trigger), [graph, trigger])
  const layout = useMemo(() => layoutStoryboard(model), [model])

  const terminalCountByScene = new Map<string, number>()

  return (
    <div data-builder-region="storyboard" style={{ width: '100%', overflow: 'auto' }}>
      <svg
        role="img"
        aria-label="Workflow storyboard"
        width={Math.max(layout.width, 320)}
        height={Math.max(layout.height + 24, 240)}
        viewBox={`0 0 ${Math.max(layout.width, 320)} ${Math.max(layout.height + 24, 240)}`}
        style={{ fontFamily: 'Manrope, system-ui, sans-serif' }}
      >
        <defs>
          <marker
            id="storyboard-arrow"
            viewBox="0 0 10 10"
            refX="10"
            refY="5"
            markerWidth="6"
            markerHeight="6"
            orient="auto-start-reverse"
          >
            <path d="M 0 0 L 10 5 L 0 10 z" fill="rgba(15, 23, 42, 0.55)" />
          </marker>
        </defs>
        <Spine layout={layout} />
        {model.exits.map((exit) => {
          const from = layout.scenes.get(exit.fromSceneId)
          const to = layout.scenes.get(exit.toSceneId)
          if (!from || !to) return null
          return <ExitEdge key={exit.id} id={exit.id} from={from} to={to} resultCode={exit.resultCode} />
        })}
        {model.terminals.map((terminal) => {
          const from = layout.scenes.get(terminal.fromSceneId)
          if (!from) return null
          const index = terminalCountByScene.get(terminal.fromSceneId) ?? 0
          terminalCountByScene.set(terminal.fromSceneId, index + 1)
          return (
            <TerminalPill
              key={terminal.id}
              id={terminal.id}
              from={from}
              resultCode={terminal.resultCode}
              reason={terminal.reason}
              index={index}
            />
          )
        })}
        {model.scenes.map((scene) => {
          const sceneLayout = layout.scenes.get(scene.id)
          if (!sceneLayout) return null
          return (
            <Scene
              key={scene.id}
              scene={scene}
              layout={sceneLayout}
              selected={selectedNodeId === scene.id}
              onSelect={(sceneId) => dispatch({ type: 'node/select', nodeId: sceneId })}
            />
          )
        })}
      </svg>
    </div>
  )
}
