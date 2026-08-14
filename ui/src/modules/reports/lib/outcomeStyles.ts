import type { OutcomeKey } from './reportMath'

/** Outcome → token triple. The only place outcome CSS vars are named in the UI. */
export const outcomeVars: Record<OutcomeKey, { fg: string; bg: string; dot: string }> = {
  spoke: {
    fg: 'var(--color-outcome-spoke-fg)',
    bg: 'var(--color-outcome-spoke-bg)',
    dot: 'var(--color-outcome-spoke-dot)',
  },
  attempted: {
    fg: 'var(--color-outcome-attempted-fg)',
    bg: 'var(--color-outcome-attempted-bg)',
    dot: 'var(--color-outcome-attempted-dot)',
  },
  nothing: {
    fg: 'var(--color-outcome-nothing-fg)',
    bg: 'var(--color-outcome-nothing-bg)',
    dot: 'var(--color-outcome-nothing-dot)',
  },
}
