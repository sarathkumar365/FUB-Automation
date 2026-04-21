/**
 * Normalisation helpers for the scene-inspector transitions list.
 *
 * Transitions in the runtime contract are either a `{ terminal }` object or a
 * fan-out array of target node ids. `flattenTransitions` turns the `Object.entries`
 * output of `node.transitions` into a flat list of row records so the UI can
 * render one `<li>` per outcome — a fan-out to two nodes yields two rows that
 * share the same resultCode.
 */
import {
  isFanoutTransition,
  isTerminalTransition,
} from '../../../../../workflows-builder/state/runtimeContract'

export interface TransitionRowData {
  resultCode: string
  kind: 'node' | 'terminal' | 'unknown'
  target: string
}

export function flattenTransitions(entries: Array<[string, unknown]>): TransitionRowData[] {
  const rows: TransitionRowData[] = []
  for (const [resultCode, value] of entries) {
    if (isFanoutTransition(value)) {
      if (value.length === 0) {
        rows.push({ resultCode, kind: 'unknown', target: '?' })
      } else {
        for (const target of value) {
          rows.push({ resultCode, kind: 'node', target })
        }
      }
    } else if (isTerminalTransition(value)) {
      rows.push({ resultCode, kind: 'terminal', target: value.terminal })
    } else {
      rows.push({ resultCode, kind: 'unknown', target: '?' })
    }
  }
  return rows
}
