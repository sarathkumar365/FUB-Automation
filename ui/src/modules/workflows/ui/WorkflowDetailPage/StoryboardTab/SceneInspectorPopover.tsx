/**
 * Floating inspector popover for the storyboard tab.
 *
 * Replaces the prior bottom-anchored drawer. The popover is absolutely
 * positioned inside the canvas wrapper and anchors itself to the currently
 * selected scene. Side choice is purely geometric — if the scene sits on
 * the left half of the canvas the popover flies out to the right, otherwise
 * it flies out to the left — so the card never clips off the edge.
 *
 * Content is the same structured breakdown the drawer used (accent pill,
 * node id, config rows, transition chips); only the presentation shell
 * changed.
 */
import { useEffect, useRef } from 'react'
import { uiText } from '../../../../../shared/constants/uiText'
import { Button } from '../../../../../shared/ui/button'
import {
  formatScene,
  type FormatterAccent,
} from '../../../../workflows-builder/model/cardFormatters'
import type { SceneLayout } from '../../../../workflows-builder/model/layoutEngine'
import {
  isFanoutTransition,
  isTerminalTransition,
  type Graph,
  type GraphNode,
} from '../../../../workflows-builder/state/runtimeContract'

const ACCENT_TONES: Record<FormatterAccent, { chip: string; text: string }> = {
  trigger: { chip: 'rgba(15, 159, 184, 0.18)', text: '#0f9fb8' },
  'side-effect': { chip: 'rgba(217, 119, 6, 0.14)', text: '#b45309' },
  wait: { chip: 'rgba(99, 102, 241, 0.14)', text: '#4338ca' },
  branch: { chip: 'rgba(219, 39, 119, 0.14)', text: '#9d174d' },
  compute: { chip: 'rgba(5, 150, 105, 0.14)', text: '#047857' },
  neutral: { chip: 'rgba(100, 116, 139, 0.14)', text: '#334155' },
}

const POPOVER_WIDTH = 340
const POPOVER_OFFSET = 16
const POPOVER_MAX_HEIGHT = 480

export interface SceneInspectorPopoverProps {
  graph: Graph
  sceneId: string
  sceneLayout: SceneLayout
  canvasWidth: number
  canvasHeight: number
  onClose: () => void
}

export function SceneInspectorPopover({
  graph,
  sceneId,
  sceneLayout,
  canvasWidth,
  canvasHeight,
  onClose,
}: SceneInspectorPopoverProps) {
  const popoverRef = useRef<HTMLDivElement | null>(null)

  useEffect(() => {
    function handleKey(event: KeyboardEvent) {
      if (event.key === 'Escape') onClose()
    }
    function handleClick(event: MouseEvent) {
      const target = event.target as Node | null
      if (!popoverRef.current || !target) return
      if (popoverRef.current.contains(target)) return
      // A click on a scene re-targets selection (handled by the tab); don't
      // close the popover when the user picks a different scene.
      const element = target instanceof Element ? target : null
      if (element?.closest('[data-builder-region="scene"]')) return
      onClose()
    }
    document.addEventListener('keydown', handleKey)
    document.addEventListener('mousedown', handleClick)
    return () => {
      document.removeEventListener('keydown', handleKey)
      document.removeEventListener('mousedown', handleClick)
    }
  }, [onClose])

  const node: GraphNode | undefined = graph.nodes.find((candidate) => candidate.id === sceneId)

  const side: 'left' | 'right' = sceneLayout.x <= canvasWidth / 2 ? 'right' : 'left'
  const sceneTop = sceneLayout.y - sceneLayout.height / 2
  const maxHeight = Math.max(120, Math.min(canvasHeight - 16, POPOVER_MAX_HEIGHT))
  const top = Math.max(8, sceneTop)

  const style: React.CSSProperties = {
    position: 'absolute',
    top,
    width: POPOVER_WIDTH,
    maxHeight,
    overflowY: 'auto',
    background: 'var(--color-surface)',
    border: '1px solid var(--color-border)',
    borderRadius: 14,
    boxShadow: '0 16px 40px rgba(15, 23, 42, 0.14)',
    zIndex: 5,
  }
  if (side === 'right') {
    style.left = sceneLayout.x + sceneLayout.width / 2 + POPOVER_OFFSET
  } else {
    style.left = sceneLayout.x - sceneLayout.width / 2 - POPOVER_OFFSET - POPOVER_WIDTH
  }

  return (
    <div
      ref={popoverRef}
      data-testid="workflow-scene-inspector"
      data-popover-side={side}
      role="dialog"
      aria-label={uiText.workflows.sceneInspectorTitle}
      style={style}
    >
      {node ? (
        <InspectorBody node={node} onClose={onClose} />
      ) : (
        <div className="flex items-center justify-between gap-3 p-4">
          <span className="text-sm text-[var(--color-text-muted)]">
            {uiText.workflows.sceneInspectorNotFound}
          </span>
          <Button type="button" size="sm" variant="ghost" onClick={onClose}>
            {uiText.workflows.sceneInspectorCloseLabel}
          </Button>
        </div>
      )}
    </div>
  )
}

function InspectorBody({ node, onClose }: { node: GraphNode; onClose: () => void }) {
  const config = node.config ?? {}
  const formatted = formatScene(node.type, config)
  const accent = ACCENT_TONES[formatted.accent]
  const configEntries = Object.entries(config)
  const transitionEntries = Object.entries(node.transitions ?? {})

  return (
    <div className="flex flex-col gap-4 p-4">
      <header className="flex items-start justify-between gap-3">
        <div className="flex flex-col gap-1">
          <span
            className="inline-flex w-fit items-center gap-1 rounded-full px-2 py-0.5 text-[11px] font-semibold uppercase tracking-wide"
            style={{ background: accent.chip, color: accent.text }}
          >
            {node.type}
          </span>
          <h3 className="text-base font-semibold text-[var(--color-text)]">{formatted.title}</h3>
          <p className="font-mono text-xs text-[var(--color-text-muted)]">
            <span className="text-[var(--color-text-muted)]">
              {uiText.workflows.sceneInspectorIdLabel}:{' '}
            </span>
            {node.id}
          </p>
        </div>
        <Button
          type="button"
          size="sm"
          variant="ghost"
          onClick={onClose}
          aria-label={uiText.workflows.sceneInspectorCloseLabel}
        >
          ×
        </Button>
      </header>

      <section className="flex flex-col gap-2">
        <h4 className="text-xs font-semibold uppercase tracking-wide text-[var(--color-text-muted)]">
          {uiText.workflows.sceneInspectorConfigLabel}
        </h4>
        {configEntries.length === 0 ? (
          <p className="text-sm text-[var(--color-text-muted)]">
            {uiText.workflows.sceneInspectorEmptyConfig}
          </p>
        ) : (
          <dl className="grid grid-cols-[120px_1fr] gap-x-3 gap-y-2 text-sm">
            {configEntries.map(([key, value]) => (
              <ConfigRow key={key} label={key} value={value} />
            ))}
          </dl>
        )}
      </section>

      <section className="flex flex-col gap-2">
        <h4 className="text-xs font-semibold uppercase tracking-wide text-[var(--color-text-muted)]">
          {uiText.workflows.sceneInspectorTransitionsLabel}
        </h4>
        {transitionEntries.length === 0 ? (
          <p className="text-sm text-[var(--color-text-muted)]">
            {uiText.workflows.sceneInspectorEmptyTransitions}
          </p>
        ) : (
          <ul className="flex flex-wrap gap-2">
            {transitionEntries.map(([resultCode, value]) => (
              <TransitionChip key={resultCode} resultCode={resultCode} value={value} />
            ))}
          </ul>
        )}
      </section>
    </div>
  )
}

function ConfigRow({ label, value }: { label: string; value: unknown }) {
  const display = formatConfigValue(value)
  return (
    <>
      <dt className="truncate font-medium text-[var(--color-text)]">{label}</dt>
      <dd
        className={display.mono ? 'font-mono text-[var(--color-text)]' : 'text-[var(--color-text)]'}
      >
        <span
          className={
            display.mono
              ? 'rounded bg-[var(--color-surface-alt)] px-1.5 py-0.5 text-xs'
              : 'text-sm'
          }
        >
          {display.text}
        </span>
      </dd>
    </>
  )
}

function formatConfigValue(value: unknown): { text: string; mono: boolean } {
  if (value === null || value === undefined) return { text: '—', mono: false }
  if (typeof value === 'string') {
    const mono = value.startsWith('$') || /[${}]/.test(value)
    return { text: value, mono }
  }
  if (typeof value === 'number' || typeof value === 'boolean') {
    return { text: String(value), mono: true }
  }
  try {
    return { text: JSON.stringify(value), mono: true }
  } catch {
    return { text: '…', mono: false }
  }
}

function TransitionChip({ resultCode, value }: { resultCode: string; value: unknown }) {
  let arrow: string
  if (isFanoutTransition(value as never)) {
    const targets = value as string[]
    arrow = targets.join(', ')
  } else if (isTerminalTransition(value as never)) {
    const terminal = (value as { terminal: string }).terminal
    arrow = `${uiText.workflows.sceneInspectorTransitionTerminal}: ${terminal}`
  } else {
    arrow = '?'
  }

  return (
    <li className="inline-flex items-center gap-1 rounded-full border border-[var(--color-border)] bg-[var(--color-surface-alt)] px-2.5 py-1 text-xs">
      <span className="font-medium text-[var(--color-text)]">on {resultCode}</span>
      <span className="text-[var(--color-text-muted)]">→</span>
      <span className="font-mono text-[var(--color-text)]">{arrow}</span>
    </li>
  )
}
