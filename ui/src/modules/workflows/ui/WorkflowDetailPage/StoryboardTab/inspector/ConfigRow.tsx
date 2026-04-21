/**
 * A single labeled config entry inside the scene inspector popover.
 *
 * Renders on top of the shared `FieldRow` recipe and dispatches the value
 * kind (from `formatConfigValue`) to the appropriate shared primitive:
 *
 *   - `empty`      → FieldRow inline, em-dash placeholder.
 *   - `scalar`     → FieldRow inline, plain text (D4.1-a).
 *   - `plain`      → FieldRow stacked, `ClampedText` with 4-line clamp
 *                    and Show more / Show less toggle (D4.4-a + D4.10-b).
 *   - `templating` → FieldRow stacked, `CopyableValue` in monospace
 *                    (D4.2-b + D4.9-b).
 *   - `url`        → FieldRow stacked, `CopyableValue` in monospace —
 *                    copy-only, NO anchor (D4.5-c).
 *   - `structured` → FieldRow stacked, shared `JsonViewer`.
 *
 * The row never wraps the value in an anchor; navigation affordances are
 * deliberately out of scope for the inspector (per D4.5-c).
 */
import { ClampedText, CopyableValue, FieldRow } from '../../../../../../shared/ui'
import { JsonViewer } from '../../../../../../shared/ui/JsonViewer'
import { CONFIG_JSON_MAX_HEIGHT_CLASS } from '../constants'
import { formatConfigValue } from './formatConfigValue'

interface ConfigRowProps {
  label: string
  value: unknown
}

export function ConfigRow({ label, value }: ConfigRowProps) {
  const display = formatConfigValue(value)

  switch (display.kind) {
    case 'empty':
      return <FieldRow label={label} value={null} layout="inline" />
    case 'scalar':
      return <FieldRow label={label} value={display.text} layout="inline" />
    case 'plain':
      return (
        <FieldRow
          label={label}
          value={<ClampedText text={display.text} maxLines={4} />}
          layout="stacked"
        />
      )
    case 'templating':
      return (
        <FieldRow label={label} value={<CopyableValue value={display.text} />} layout="stacked" />
      )
    case 'url':
      return (
        <FieldRow label={label} value={<CopyableValue value={display.text} />} layout="stacked" />
      )
    case 'structured':
      return (
        <FieldRow
          label={label}
          value={
            <JsonViewer value={display.value} maxHeightClassName={CONFIG_JSON_MAX_HEIGHT_CLASS} />
          }
          layout="stacked"
        />
      )
  }
}
