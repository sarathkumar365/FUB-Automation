import type { ContactCounts } from '@platform/contracts/reportingSchemas'
import { cn } from '@shared/lib/cn'
import { outcomeVars } from '../lib/outcomeStyles'
import { mixSegments, type OutcomeKey, outcomeKeys } from '../lib/reportMath'

/** Narration story chip — colored count phrase embedded in the headline. */
export function OutcomeChip({ outcome, children }: { outcome: OutcomeKey; children: string }) {
  return (
    <span
      className="whitespace-nowrap rounded-lg px-2 py-px"
      style={{ background: outcomeVars[outcome].bg, color: outcomeVars[outcome].fg }}
    >
      {children}
    </span>
  )
}

/** Proportional three-segment outcome bar (ledger rows, agent table engagement). */
export function MixBar({ counts, className }: { counts: ContactCounts; className?: string }) {
  const widths = mixSegments(counts)
  return (
    <span
      aria-hidden="true"
      className={cn(
        'flex h-2 overflow-hidden rounded-full bg-[var(--color-surface-alt)]',
        className,
      )}
    >
      {outcomeKeys.map((key) => (
        <span key={key} style={{ width: widths[key], background: outcomeVars[key].dot }} />
      ))}
    </span>
  )
}

export function OutcomeDot({ outcome, className }: { outcome: OutcomeKey; className?: string }) {
  return (
    <span
      aria-hidden="true"
      className={cn('inline-block h-[9px] w-[9px] shrink-0 rounded-full', className)}
      style={{ background: outcomeVars[outcome].dot }}
    />
  )
}
