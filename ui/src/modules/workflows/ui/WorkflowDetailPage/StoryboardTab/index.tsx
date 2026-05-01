/**
 * Storyboard tab for the workflow detail page.
 *
 * Composition: validation strip + storyboard viewer + floating scene
 * inspector popover. The storyboard is the centerpiece — no PageCard
 * wrapper, just a subtle surface container with a soft shadow. If the graph
 * fails the runtime schema check this falls back to an ErrorState surfacing
 * the schema issues.
 *
 * The popover anchors to the selected scene using the same layout Dagre
 * produced for the viewer (reused via `useStoryboardModel`), and picks a
 * side so the card never clips off the canvas edges.
 */
import { useMemo } from 'react'
import { uiText } from '../../../../../shared/constants/uiText'
import { ErrorState } from '../../../../../shared/ui/ErrorState'
import { StoryboardViewer } from '../../../../workflows-builder/surfaces/storyboard/StoryboardViewer'
import { useStoryboardModel } from '../../../../workflows-builder/surfaces/storyboard/useStoryboardModel'
import type { Graph } from '../../../../workflows-builder/state/runtimeContract'
import type { WorkflowResponse } from '../../../lib/workflowSchemas'
import { parseWorkflowGraph } from '../lib/parseWorkflowGraph'
import type { ValidationViewState } from '../lib/useWorkflowDetailActions'
import { SceneInspectorPopover } from './SceneInspectorPopover'
import { useSceneSelection } from './useSceneSelection'
import { ValidationStrip } from './ValidationStrip'

export interface StoryboardTabProps {
  workflow: WorkflowResponse
  validationState: ValidationViewState
  onValidate: () => void
  onDismissValidation: () => void
  isValidationPending: boolean
}

export function StoryboardTab({
  workflow,
  validationState,
  onValidate,
  onDismissValidation,
  isValidationPending,
}: StoryboardTabProps) {
  const parseResult = useMemo(() => parseWorkflowGraph(workflow.graph), [workflow.graph])
  const { selectedSceneId, select, clear } = useSceneSelection()

  if (!parseResult.ok) {
    return (
      <div className="space-y-3">
        <ValidationStrip
          state={validationState}
          onValidate={onValidate}
          onDismiss={onDismissValidation}
          disabled={isValidationPending}
        />
        <ErrorState
          title={uiText.workflows.detailGraphInvalidTitle}
          message={[uiText.workflows.detailGraphInvalidMessage, ...parseResult.issues].join(' · ')}
        />
      </div>
    )
  }

  const { graph } = parseResult
  return (
    <StoryboardTabBody
      graph={graph}
      trigger={workflow.trigger}
      selectedSceneId={selectedSceneId}
      onSelect={select}
      onClear={clear}
      validationState={validationState}
      onValidate={onValidate}
      onDismissValidation={onDismissValidation}
      isValidationPending={isValidationPending}
    />
  )
}

interface StoryboardTabBodyProps {
  graph: Graph
  trigger: Record<string, unknown> | null
  selectedSceneId: string | null
  onSelect: (id: string) => void
  onClear: () => void
  validationState: ValidationViewState
  onValidate: () => void
  onDismissValidation: () => void
  isValidationPending: boolean
}

function StoryboardTabBody({
  graph,
  trigger,
  selectedSceneId,
  onSelect,
  onClear,
  validationState,
  onValidate,
  onDismissValidation,
  isValidationPending,
}: StoryboardTabBodyProps) {
  const { layout, viewport, canvasSize } = useStoryboardModel(graph, trigger)
  const selectedSceneLayout = selectedSceneId ? layout.scenes.get(selectedSceneId) : undefined
  const canvasWidth = canvasSize.width
  const canvasHeight = canvasSize.height

  return (
    <div className="flex flex-col gap-3">
      <ValidationStrip
        state={validationState}
        onValidate={onValidate}
        onDismiss={onDismissValidation}
        disabled={isValidationPending}
      />
      <div
        data-testid="workflow-storyboard-surface"
        className="storyboard-canvas flex justify-center overflow-x-auto rounded-xl border border-[var(--color-border)] bg-[var(--color-surface)] p-4 shadow-[var(--shadow-subtle)]"
        style={{ position: 'relative' }}
      >
        <div style={{ position: 'relative', width: canvasWidth }}>
          <StoryboardViewer
            graph={graph}
            trigger={trigger}
            selectedSceneId={selectedSceneId}
            onSelectScene={onSelect}
          />
          {selectedSceneId && selectedSceneLayout ? (
            <SceneInspectorPopover
              graph={graph}
              sceneId={selectedSceneId}
              sceneLayout={selectedSceneLayout}
              canvasWidth={canvasWidth}
              canvasHeight={canvasHeight}
              viewBoxOriginX={viewport.x}
              onClose={onClear}
            />
          ) : null}
        </div>
      </div>
    </div>
  )
}
