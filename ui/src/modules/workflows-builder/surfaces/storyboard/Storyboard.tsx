/**
 * Top-level storyboard surface for the authoring builder.
 *
 * Thin wrapper around `StoryboardViewer`: reads graph + trigger + selection
 * from the builder store and dispatches the select action. Read-only
 * rendering logic lives in `StoryboardViewer` so other surfaces (e.g. the
 * workflow detail page) can embed it without mounting the store.
 */
import { useBuilderStore } from '../../state/builderStore'
import { StoryboardViewer } from './StoryboardViewer'

export function Storyboard() {
  const graph = useBuilderStore((state) => state.graph)
  const trigger = useBuilderStore((state) => state.trigger)
  const selectedNodeId = useBuilderStore((state) => state.layout.selectedNodeId)
  const dispatch = useBuilderStore((state) => state.dispatch)

  return (
    <StoryboardViewer
      graph={graph}
      trigger={trigger}
      selectedSceneId={selectedNodeId}
      onSelectScene={(id) => dispatch({ type: 'node/select', nodeId: id })}
    />
  )
}
