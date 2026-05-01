/**
 * Scrollable inner body of the scene inspector popover.
 *
 * Kept separate from the popover shell so the outer Radix content can remain
 * `overflow: visible` (for chip shadows) while this body handles its own
 * vertical scroll when transitions/config lists grow tall.
 */
import { uiText } from '../../../../../../shared/constants/uiText'
import { Button } from '../../../../../../shared/ui/button'
import { CloseIcon } from '../../../../../../shared/ui/icons'
import { formatScene } from '../../../../../workflows-builder/model/cardFormatters'
import { getAccentTone } from '../../../../../workflows-builder/surfaces/storyboard/accentTokens'
import type { GraphNode } from '../../../../../workflows-builder/state/runtimeContract'
import { INSPECTOR_PADDING_X, INSPECTOR_PADDING_Y } from '../constants'
import { ConfigRow } from './ConfigRow'
import { TransitionRow } from './TransitionRow'
import { flattenTransitions } from './transitions'

interface InspectorBodyProps {
  node: GraphNode
  maxHeight: number
  onClose: () => void
}

export function InspectorBody({ node, maxHeight, onClose }: InspectorBodyProps) {
  const config = node.config ?? {}
  const formatted = formatScene(node.type, config)
  const accent = getAccentTone(formatted.accent)
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
        padding: `${INSPECTOR_PADDING_Y}px ${INSPECTOR_PADDING_X}px`,
        maxHeight,
        overflowY: 'auto',
        minWidth: 0,
      }}
    >
      <Button
        type="button"
        variant="ghost"
        size="icon"
        onClick={onClose}
        aria-label={uiText.workflows.sceneInspectorCloseLabel}
        className="absolute right-2 top-2 h-7 w-7"
      >
        <CloseIcon />
      </Button>

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
            fontFamily: 'var(--font-mono)',
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
          <div
            data-testid="workflow-scene-inspector-config"
            style={{
              display: 'flex',
              flexDirection: 'column',
              gap: 10,
              minWidth: 0,
            }}
          >
            {configEntries.map(([key, value]) => (
              <ConfigRow key={key} label={key} value={value} />
            ))}
          </div>
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
              />
            ))}
          </ul>
        )}
      </section>
    </div>
  )
}
