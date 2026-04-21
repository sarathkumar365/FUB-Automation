/**
 * One card in the inspector's transitions list.
 *
 * Per D4.6-a: transitions render as stacked cards — a small monospace
 * `on <resultCode>` chip on top, and a full-width `→ <target>` line
 * below. This replaces the previous single-line row layout where the
 * resultCode competed with the target for horizontal space (and made
 * long codes or long targets ellipsize / wrap awkwardly).
 *
 * The list is flattened upstream via `./transitions.flattenTransitions`
 * so this component just renders a single outcome.
 */
import { uiText } from '../../../../../../shared/constants/uiText'
import type { TransitionRowData } from './transitions'

interface TransitionRowProps {
  row: TransitionRowData
}

export function TransitionRow({ row }: TransitionRowProps) {
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
        flexDirection: 'column',
        gap: 6,
        padding: '10px 12px',
        marginBottom: 6,
        borderRadius: 8,
        border: '1px solid var(--color-border)',
        background: 'var(--color-surface)',
        minWidth: 0,
        width: '100%',
      }}
    >
      <span
        style={{
          alignSelf: 'flex-start',
          display: 'inline-block',
          maxWidth: '100%',
          padding: '2px 8px',
          borderRadius: 999,
          background: 'var(--color-surface-alt)',
          fontFamily: 'var(--font-mono)',
          fontSize: 11,
          color: 'var(--color-text-muted)',
          overflowWrap: 'anywhere',
          wordBreak: 'break-word',
          minWidth: 0,
        }}
      >
        on {row.resultCode}
      </span>
      <span
        data-testid="workflow-scene-inspector-transition-target"
        style={{
          display: 'block',
          fontFamily: 'var(--font-mono)',
          fontSize: 12,
          lineHeight: 1.5,
          color: 'var(--color-text)',
          overflowWrap: 'anywhere',
          wordBreak: 'break-word',
          minWidth: 0,
          width: '100%',
        }}
      >
        <span aria-hidden="true" style={{ color: 'var(--color-text-muted)', marginRight: 6 }}>
          →
        </span>
        {targetLabel}
      </span>
    </li>
  )
}
