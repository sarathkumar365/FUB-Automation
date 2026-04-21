/**
 * A single `<dt>`/`<dd>` pair inside the inspector's config grid.
 *
 * Scalar values (string / number / boolean / null / templating strings) render
 * inline as a compact chip. Structured values (object / array) render via the
 * shared `JsonViewer` primitive so users can inspect and copy nested config.
 */
import { JsonViewer } from '../../../../../../shared/ui/JsonViewer'
import { CONFIG_JSON_MAX_HEIGHT_CLASS } from '../constants'
import { formatConfigValue } from './formatConfigValue'

interface ConfigRowProps {
  label: string
  value: unknown
}

export function ConfigRow({ label, value }: ConfigRowProps) {
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
          fontFamily: display.kind === 'scalar' && display.mono ? 'var(--font-mono)' : 'inherit',
        }}
      >
        {display.kind === 'structured' ? (
          <JsonViewer value={display.value} maxHeightClassName={CONFIG_JSON_MAX_HEIGHT_CLASS} />
        ) : display.kind === 'empty' ? (
          <span style={{ fontSize: 13 }}>—</span>
        ) : (
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
        )}
      </dd>
    </>
  )
}
