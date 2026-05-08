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
 * chips on top of each other. Terminal pills fly outward relative to the
 * graph's horizontal midline so pills on the left branch never collide with
 * subtrees on the right.
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
  const { model, layout, terminalPlacements, viewport, canvasSize } = useStoryboardModel(
    graph,
    trigger,
  )

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

  const terminalsByIdLookup = useMemo(() => {
    const byId = new Map<string, (typeof terminalPlacements)[number]>()
    for (const placement of terminalPlacements) byId.set(placement.terminalId, placement)
    return byId
  }, [terminalPlacements])

  const svgWidth = canvasSize.width
  const svgHeight = canvasSize.height

  return (
    <div data-builder-region="storyboard" style={{ width: '100%', overflowX: 'auto' }}>
      <svg
        role="img"
        aria-label="Workflow storyboard"
        width={svgWidth}
        height={svgHeight}
        viewBox={`${viewport.x} ${viewport.y} ${svgWidth} ${svgHeight}`}
        style={{ fontFamily: 'var(--font-ui)', display: 'block', margin: '0 auto' }}
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
            <path d="M 0 0 L 10 5 L 0 10 z" fill="var(--color-storyboard-arrow)" />
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
        {model.terminals.map((terminal) => {
          const from = layout.scenes.get(terminal.fromSceneId)
          const placement = terminalsByIdLookup.get(terminal.id)
          if (!from || !placement) return null
          return (
            <TerminalPill
              key={terminal.id}
              id={terminal.id}
              from={from}
              resultCode={terminal.resultCode}
              reason={terminal.reason}
              index={placement.index}
              totalTerminals={placement.totalTerminals}
              side={placement.side}
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
              onSelect={onSelectScene}
            />
          )
        })}
      </svg>
    </div>
  )
}
