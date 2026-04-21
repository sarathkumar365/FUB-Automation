/**
 * Floating inspector popover for the storyboard tab.
 *
 * Replaces the prior bottom-anchored drawer. The popover is absolutely
 * positioned inside the canvas wrapper and anchors itself to the currently
 * selected scene. Side choice is purely geometric — if the scene sits on
 * the left half of the canvas the popover flies out to the right, otherwise
 * it flies out to the left — so the card never clips off the edge.
 *
 * After scene cards were reduced to just the node id, the popover became the
 * single source of all step detail: accent dot + title, node id subtitle,
 * config list, and transition chips. The outer card stays `overflow: visible`
 * so chip shadows don't get cropped; tall content scrolls inside a dedicated
 * inner wrapper.
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

const ACCENT_TONES: Record<FormatterAccent, { chip: string; text: string; dot: string }> = {
  trigger: { chip: 'rgba(15, 159, 184, 0.18)', text: '#0f9fb8', dot: '#0f9fb8' },
  'side-effect': { chip: 'rgba(217, 119, 6, 0.14)', text: '#b45309', dot: '#d97706' },
  wait: { chip: 'rgba(99, 102, 241, 0.14)', text: '#4338ca', dot: '#6366f1' },
  branch: { chip: 'rgba(219, 39, 119, 0.14)', text: '#9d174d', dot: '#db2777' },
  compute: { chip: 'rgba(5, 150, 105, 0.14)', text: '#047857', dot: '#059669' },
  neutral: { chip: 'rgba(100, 116, 139, 0.14)', text: '#334155', dot: '#64748b' },
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
  /** Left edge of the SVG viewBox in user-space units. Scene coordinates are
   *  in user-space, so this offset converts them to pixel coordinates within
   *  the HTML container that layers the popover above the SVG. Defaults to 0
   *  for callers that still render with a zero-origin viewBox. */
  viewBoxOriginX?: number
}

export function SceneInspectorPopover({
  graph,
  sceneId,
  sceneLayout,
  canvasWidth,
  canvasHeight,
  onClose,
  viewBoxOriginX = 0,
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

  const scenePixelX = sceneLayout.x - viewBoxOriginX
  const side: 'left' | 'right' = scenePixelX <= canvasWidth / 2 ? 'right' : 'left'
  const sceneTop = sceneLayout.y - sceneLayout.height / 2
  const maxHeight = Math.max(120, Math.min(canvasHeight - 16, POPOVER_MAX_HEIGHT))
  const top = Math.max(8, sceneTop)

  const style: React.CSSProperties = {
    position: 'absolute',
    top,
    width: POPOVER_WIDTH,
    maxHeight,
    // The outer card keeps shadows/chips visible; an inner wrapper handles
    // scroll when content overflows. Clipping the outer card caused chip
    // rows to be cut off by the border.
    overflow: 'visible',
    background: 'var(--color-surface)',
    border: '1px solid var(--color-border)',
    borderRadius: 14,
    boxShadow: '0 16px 40px rgba(15, 23, 42, 0.14)',
    zIndex: 5,
  }
  if (side === 'right') {
    style.left = scenePixelX + sceneLayout.width / 2 + POPOVER_OFFSET
  } else {
    style.left = scenePixelX - sceneLayout.width / 2 - POPOVER_OFFSET - POPOVER_WIDTH
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
        <InspectorBody node={node} maxHeight={maxHeight} onClose={onClose} />
      ) : (
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            gap: 12,
            padding: '18px 20px',
          }}
        >
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

function InspectorBody({
  node,
  maxHeight,
  onClose,
}: {
  node: GraphNode
  maxHeight: number
  onClose: () => void
}) {
  const config = node.config ?? {}
  const formatted = formatScene(node.type, config)
  const accent = ACCENT_TONES[formatted.accent]
  const configEntries = Object.entries(config)
  const transitionEntries = Object.entries(node.transitions ?? {})

  return (
    <div
      data-testid="workflow-scene-inspector-body"
      style={{
        position: 'relative',
        display: 'flex',
        flexDirection: 'column',
        gap: 14,
        padding: '18px 20px',
        maxHeight,
        overflowY: 'auto',
        minWidth: 0,
      }}
    >
      <button
        type="button"
        onClick={onClose}
        aria-label={uiText.workflows.sceneInspectorCloseLabel}
        style={{
          position: 'absolute',
          top: 10,
          right: 10,
          width: 28,
          height: 28,
          minWidth: 28,
          minHeight: 28,
          display: 'inline-flex',
          alignItems: 'center',
          justifyContent: 'center',
          borderRadius: 8,
          border: 'none',
          background: 'transparent',
          color: 'var(--color-text-muted)',
          cursor: 'pointer',
          fontSize: 18,
          lineHeight: 1,
        }}
      >
        ×
      </button>

      <header style={{ display: 'flex', flexDirection: 'column', gap: 6, paddingRight: 32 }}>
        <div style={{ display: 'inline-flex', alignItems: 'center', gap: 8, minWidth: 0 }}>
          <span
            aria-hidden="true"
            style={{
              display: 'inline-block',
              width: 10,
              height: 10,
              borderRadius: 999,
              background: accent.dot,
              flexShrink: 0,
            }}
          />
          <h3
            style={{
              margin: 0,
              fontSize: 15,
              fontWeight: 600,
              color: 'var(--color-text)',
              overflow: 'hidden',
              textOverflow: 'ellipsis',
              whiteSpace: 'nowrap',
              minWidth: 0,
            }}
          >
            {formatted.title}
          </h3>
        </div>
        <p
          style={{
            margin: 0,
            fontFamily: 'JetBrains Mono, monospace',
            fontSize: 12,
            color: 'var(--color-text-muted)',
            overflowWrap: 'anywhere',
            wordBreak: 'break-word',
          }}
        >
          <span>{uiText.workflows.sceneInspectorIdLabel}: </span>
          <span style={{ color: 'var(--color-text)' }}>{node.id}</span>
        </p>
      </header>

      <section style={{ display: 'flex', flexDirection: 'column', gap: 8, minWidth: 0 }}>
        <h4
          style={{
            margin: 0,
            fontSize: 11,
            fontWeight: 600,
            textTransform: 'uppercase',
            letterSpacing: 0.5,
            color: 'var(--color-text-muted)',
          }}
        >
          {uiText.workflows.sceneInspectorConfigLabel}
        </h4>
        {configEntries.length === 0 ? (
          <p style={{ margin: 0, fontSize: 13, color: 'var(--color-text-muted)' }}>
            {uiText.workflows.sceneInspectorEmptyConfig}
          </p>
        ) : (
          <dl
            style={{
              display: 'grid',
              gridTemplateColumns: '110px minmax(0, 1fr)',
              columnGap: 12,
              rowGap: 8,
              margin: 0,
              fontSize: 13,
              minWidth: 0,
            }}
          >
            {configEntries.map(([key, value]) => (
              <ConfigRow key={key} label={key} value={value} />
            ))}
          </dl>
        )}
      </section>

      <section style={{ display: 'flex', flexDirection: 'column', gap: 8, minWidth: 0 }}>
        <h4
          style={{
            margin: 0,
            fontSize: 11,
            fontWeight: 600,
            textTransform: 'uppercase',
            letterSpacing: 0.5,
            color: 'var(--color-text-muted)',
          }}
        >
          {uiText.workflows.sceneInspectorTransitionsLabel}
        </h4>
        {transitionEntries.length === 0 ? (
          <p style={{ margin: 0, fontSize: 13, color: 'var(--color-text-muted)' }}>
            {uiText.workflows.sceneInspectorEmptyTransitions}
          </p>
        ) : (
          <ul
            data-testid="workflow-scene-inspector-transitions"
            data-layout="stack"
            style={{
              display: 'flex',
              flexDirection: 'column',
              margin: 0,
              padding: 0,
              listStyle: 'none',
              minWidth: 0,
              width: '100%',
            }}
          >
            {flattenTransitions(transitionEntries).map((row, index) => (
              <TransitionRow
                key={`${row.resultCode}-${row.kind}-${row.target}-${index}`}
                row={row}
                isFirst={index === 0}
              />
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
      <dt
        style={{
          fontWeight: 500,
          color: 'var(--color-text)',
          overflow: 'hidden',
          textOverflow: 'ellipsis',
          whiteSpace: 'nowrap',
          minWidth: 0,
        }}
      >
        {label}
      </dt>
      <dd
        style={{
          margin: 0,
          color: 'var(--color-text)',
          minWidth: 0,
          overflowWrap: 'anywhere',
          wordBreak: 'break-word',
          fontFamily: display.mono ? 'JetBrains Mono, monospace' : 'inherit',
        }}
      >
        <span
          style={
            display.mono
              ? {
                  display: 'inline-block',
                  maxWidth: '100%',
                  padding: '1px 6px',
                  borderRadius: 4,
                  background: 'var(--color-surface-alt)',
                  fontSize: 12,
                  overflowWrap: 'anywhere',
                  wordBreak: 'break-word',
                }
              : { fontSize: 13 }
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

interface TransitionRowData {
  resultCode: string
  kind: 'node' | 'terminal' | 'unknown'
  target: string
}

function flattenTransitions(entries: Array<[string, unknown]>): TransitionRowData[] {
  const rows: TransitionRowData[] = []
  for (const [resultCode, value] of entries) {
    if (isFanoutTransition(value as never)) {
      const targets = value as string[]
      if (targets.length === 0) {
        rows.push({ resultCode, kind: 'unknown', target: '?' })
      } else {
        for (const target of targets) {
          rows.push({ resultCode, kind: 'node', target })
        }
      }
    } else if (isTerminalTransition(value as never)) {
      const terminal = (value as { terminal: string }).terminal
      rows.push({ resultCode, kind: 'terminal', target: terminal })
    } else {
      rows.push({ resultCode, kind: 'unknown', target: '?' })
    }
  }
  return rows
}

function TransitionRow({ row, isFirst }: { row: TransitionRowData; isFirst: boolean }) {
  const targetLabel =
    row.kind === 'terminal'
      ? `${uiText.workflows.sceneInspectorTransitionTerminal}: ${row.target}`
      : row.kind === 'node'
        ? `${uiText.workflows.sceneInspectorTransitionNode} ${row.target}`
        : row.target

  return (
    <li
      data-testid="workflow-scene-inspector-transition-row"
      style={{
        display: 'flex',
        alignItems: 'flex-start',
        gap: 6,
        padding: '8px 0',
        borderTop: isFirst ? 'none' : '1px solid var(--color-border)',
        fontSize: 12,
        lineHeight: 1.5,
        minWidth: 0,
        width: '100%',
      }}
    >
      <span
        style={{
          fontFamily: 'JetBrains Mono, monospace',
          color: 'var(--color-text-muted)',
          flexShrink: 0,
          overflowWrap: 'anywhere',
          wordBreak: 'break-word',
          minWidth: 0,
        }}
      >
        on {row.resultCode}
      </span>
      <span style={{ color: 'var(--color-text-muted)', flexShrink: 0 }}>→</span>
      <span
        data-testid="workflow-scene-inspector-transition-target"
        style={{
          fontFamily: 'JetBrains Mono, monospace',
          color: 'var(--color-text)',
          overflowWrap: 'anywhere',
          wordBreak: 'break-word',
          minWidth: 0,
          flex: 1,
        }}
      >
        {targetLabel}
      </span>
    </li>
  )
}
