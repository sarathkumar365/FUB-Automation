/**
 * WorkflowBuilderPage — Phase 1 read-only storyboard.
 *
 * Phase 1 scope:
 *   - Load an existing workflow by `:key` (if present) via the existing query.
 *   - Parse its graph + trigger into the runtime contract (zod-validated).
 *   - Push into the builder store once, then render the Storyboard surface.
 *   - Mount the debug overlay (Cmd+Shift+D).
 *
 * Authoring (drag, inspector, save) arrives in Phase 2+.
 */
import { useEffect, useMemo } from 'react'
import { Link, useParams } from 'react-router-dom'
import { useShellRegionRegistration } from '../../../app/useShellRegionRegistration'
import { routes } from '../../../shared/constants/routes'
import { uiText } from '../../../shared/constants/uiText'
import { ErrorState } from '../../../shared/ui/ErrorState'
import { LoadingState } from '../../../shared/ui/LoadingState'
import { PageCard } from '../../../shared/ui/PageCard'
import { PageHeader } from '../../../shared/ui/PageHeader'
import { useWorkflowDetailQuery } from '../../workflows/data/useWorkflowDetailQuery'
import { BuilderDebugOverlay } from '../observability/debugOverlay'
import { useBuilderStore } from '../state/builderStore'
import { emptyGraph, graphSchema, type Graph } from '../state/runtimeContract'
import { Storyboard } from '../surfaces/storyboard/Storyboard'

export function WorkflowBuilderPage() {
  const { key } = useParams<{ key: string }>()
  const detailQuery = useWorkflowDetailQuery(key)
  const dispatch = useBuilderStore((state) => state.dispatch)
  const graphHash = useBuilderStore((state) => state.graphHash)
  const surface = useBuilderStore((state) => state.layout.surface)

  const rawGraph = detailQuery.data?.graph
  const parsedGraph = useMemo<Graph | null>(() => {
    if (!rawGraph) return null
    const parsed = graphSchema.safeParse(rawGraph)
    return parsed.success ? parsed.data : null
  }, [rawGraph])

  const trigger = detailQuery.data?.trigger ?? null

  useEffect(() => {
    if (key && parsedGraph) {
      dispatch({ type: 'graph/load', graph: parsedGraph })
      dispatch({ type: 'trigger/set', trigger })
    }
    if (!key) {
      // New workflow route — seed with an empty graph.
      dispatch({ type: 'graph/load', graph: emptyGraph() })
      dispatch({ type: 'trigger/set', trigger: null })
    }
  }, [dispatch, key, parsedGraph, trigger])

  useShellRegionRegistration({
    panel: null,
    inspector: {
      title: uiText.workflows.builderInspectorTitle,
      body: (
        <div className="space-y-2 text-xs text-[var(--color-text-muted)]">
          <p>
            <span className="font-medium text-[var(--color-text)]">
              {uiText.workflows.builderInfoHashLabel}
            </span>{' '}
            <span className="font-mono">{graphHash}</span>
          </p>
          <p>
            <span className="font-medium text-[var(--color-text)]">
              {uiText.workflows.builderInfoSurfaceLabel}
            </span>{' '}
            {surface}
          </p>
          <p className="mt-3">
            Press <kbd>⌘⇧D</kbd> (or <kbd>Ctrl+Shift+D</kbd>) for the action log.
          </p>
        </div>
      ),
    },
  })

  const title = key
    ? `${uiText.workflows.builderEditTitlePrefix}: ${key}`
    : uiText.workflows.builderNewTitle
  const subtitle = key
    ? uiText.workflows.builderEditSubtitle
    : uiText.workflows.builderNewSubtitle

  if (key && detailQuery.isPending) {
    return <LoadingState />
  }
  if (key && (detailQuery.isError || !detailQuery.data)) {
    return (
      <div className="space-y-4">
        <PageHeader title={title} subtitle={subtitle} />
        <PageCard title={uiText.states.errorTitle}>
          <p className="text-sm text-[var(--color-text-muted)]">{uiText.workflows.detailNotFound}</p>
        </PageCard>
      </div>
    )
  }
  if (key && !parsedGraph) {
    return (
      <div className="space-y-4">
        <PageHeader title={title} subtitle={subtitle} />
        <PageCard title={uiText.workflows.builderGraphInvalidTitle}>
          <ErrorState message={uiText.workflows.builderGraphInvalidMessage} />
        </PageCard>
      </div>
    )
  }

  return (
    <div className="space-y-4">
      <PageHeader title={title} subtitle={subtitle}>
        <Link className="text-sm text-[var(--color-brand)] underline" to={routes.workflows}>
          {uiText.workflows.title}
        </Link>
      </PageHeader>

      <PageCard title={uiText.workflows.builderStoryboardCardTitle}>
        <Storyboard />
      </PageCard>

      <BuilderDebugOverlay />
    </div>
  )
}
