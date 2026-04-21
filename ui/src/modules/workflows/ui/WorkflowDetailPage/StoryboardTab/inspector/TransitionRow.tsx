/**
 * One row of the inspector's transitions list.
 *
 * The list is flattened upstream via `./transitions.flattenTransitions` so
 * this component just renders a single outcome: a result code, an arrow,
 * and a target label (node id or terminal name).
 */
import { uiText } from '../../../../../../shared/constants/uiText'
import type { TransitionRowData } from './transitions'

interface TransitionRowProps {
  row: TransitionRowData
  isFirst: boolean
}

export function TransitionRow({ row, isFirst }: TransitionRowProps) {
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
