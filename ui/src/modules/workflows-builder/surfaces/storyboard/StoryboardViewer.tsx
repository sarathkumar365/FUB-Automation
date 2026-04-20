/**
 * Pure, props-driven storyboard renderer.
 *
 * Extracted from `Storyboard.tsx` so it can be embedded in surfaces that do
 * NOT mount the builder store (e.g. the workflow detail page). The builder
 * authoring surface wraps this component in `Storyboard.tsx` and wires it to
 * the zustand store.
 *
 * Layout is vertical (top → bottom). Edge labels are spread along their
 * bezier so multiple exits leaving the same scene (branching) don't stack
 * chips on top of each other.
 */
import { useMemo } from 'react'
import type { Graph } from '../../state/runtimeContract'
import { ExitEdge } from './ExitEdge'
import { Scene } from './Scene'
import { TerminalPill } from './TerminalPill'
import { useStoryboardModel } from './useStoryboardModel'

export interface StoryboardViewerProps {
  graph: Graph
  trigger: Record<string, unknown> | null
  selectedSceneId?: string | null
  onSelectScene?: (sceneId: string) => void
}

export function StoryboardViewer({
  graph,
  trigger,
  selectedSceneId = null,
  onSelectScene,
}: StoryboardViewerProps) {
  const { model, layout } = useStoryboardModel(graph, trigger)

  const exitGroupings = useMemo(() => {
    const counts = new Map<string, number>()
    for (const exit of model.exits) {
      counts.set(exit.fromSceneId, (counts.get(exit.fromSceneId) ?? 0) + 1)
    }
    const indices = new Map<string, number>()
    return model.exits.map((exit) => {
      const edgeIndex = indices.get(exit.fromSceneId) ?? 0
      indices.set(exit.fromSceneId, edgeIndex + 1)
      return {
        exit,
        edgeIndex,
        edgeCount: counts.get(exit.fromSceneId) ?? 1,
      }
    })
  }, [model.exits])

  const terminalGroupings = useMemo(() => {
    const totals = new Map<string, number>()
    for (const terminal of model.terminals) {
      totals.set(terminal.fromSceneId, (totals.get(terminal.fromSceneId) ?? 0) + 1)
    }
    const indices = new Map<string, number>()
    return model.terminals.map((terminal) => {
      const index = indices.get(terminal.fromSceneId) ?? 0
      indices.set(terminal.fromSceneId, index + 1)
      return {
        terminal,
        index,
        totalTerminals: totals.get(terminal.fromSceneId) ?? 1,
      }
    })
  }, [model.terminals])

  const handleSelect = onSelectScene ?? (() => {})
  const svgWidth = Math.max(layout.width, 320)
  const svgHeight = Math.max(layout.height + 24, 240)

  return (
    <div data-builder-region="storyboard" style={{ width: '100%', overflow: 'auto' }}>
      <svg
        role="img"
        aria-label="Workflow storyboard"
        width={svgWidth}
        height={svgHeight}
        viewBox={`0 0 ${svgWidth} ${svgHeight}`}
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
        {exitGroupings.map(({ exit, edgeIndex, edgeCount }) => {
          const from = layout.scenes.get(exit.fromSceneId)
          const to = layout.scenes.get(exit.toSceneId)
          if (!from || !to) return null
          return (
            <ExitEdge
              key={exit.id}
              id={exit.id}
              from={from}
              to={to}
              resultCode={exit.resultCode}
              edgeIndex={edgeIndex}
              edgeCount={edgeCount}
            />
          )
        })}
        {terminalGroupings.map(({ terminal, index, totalTerminals }) => {
          const from = layout.scenes.get(terminal.fromSceneId)
          if (!from) return null
          return (
            <TerminalPill
              key={terminal.id}
              id={terminal.id}
              from={from}
              resultCode={terminal.resultCode}
              reason={terminal.reason}
              index={index}
              totalTerminals={totalTerminals}
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
              selected={selectedSceneId === scene.id}
              onSelect={handleSelect}
            />
          )
        })}
      </svg>
    </div>
  )
}
