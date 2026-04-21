/**
 * A single `<dt>`/`<dd>` pair inside the inspector's config grid.
 *
 * NOTE: this file is a *temporary adapter* during Slice 4 Phase C rollout.
 * Once Commit 3 of Phase C rebuilds `ConfigRow` on top of the `FieldRow`
 * recipe with kind-aware rendering (D4.1-a / D4.2-b / D4.4-a / D4.5-c /
 * D4.9-b), this dt/dd pair goes away. Until then the file treats the new
 * discriminator kinds (templating, url, plain) the same as the old
 * scalar kind so the inspector keeps rendering without a visual
 * regression between commits.
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

  const renderValue = () => {
    if (display.kind === 'structured') {
      return <JsonViewer value={display.value} maxHeightClassName={CONFIG_JSON_MAX_HEIGHT_CLASS} />
    }
    if (display.kind === 'empty') {
      return <span style={{ fontSize: 13 }}>—</span>
    }
    const mono =
      display.kind === 'templating' || display.kind === 'url' || display.kind === 'scalar'
    return (
      <span
        style={
          mono
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
    )
  }

  const valueFontFamily =
    display.kind === 'scalar' || display.kind === 'templating' || display.kind === 'url'
      ? 'var(--font-mono)'
      : 'inherit'

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
          fontFamily: valueFontFamily,
        }}
      >
        {renderValue()}
      </dd>
    </>
  )
}
