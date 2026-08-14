import { cn } from '../lib/cn'

export type SegmentedOption<V extends string> = {
  value: V
  label: string
}

type SegmentedControlProps<V extends string> = {
  options: readonly SegmentedOption<V>[]
  value: V
  onChange: (value: V) => void
  /** Required: the group is meaningless to a screen reader without it. */
  ariaLabel: string
  className?: string
}

/**
 * Pill-style segmented switcher (reporting handoff): bordered container,
 * active segment = brand-soft fill + brand text. Real buttons with
 * `aria-pressed`, per the locked keyboard-reachability rule.
 */
export function SegmentedControl<V extends string>({
  options,
  value,
  onChange,
  ariaLabel,
  className,
}: SegmentedControlProps<V>) {
  return (
    <div
      role="group"
      aria-label={ariaLabel}
      className={cn(
        'inline-flex shrink-0 gap-0.5 rounded-[10px] border border-[var(--color-border)] bg-[var(--color-surface)] p-[3px]',
        className,
      )}
    >
      {options.map((option) => {
        const active = option.value === value
        return (
          <button
            key={option.value}
            type="button"
            aria-pressed={active}
            onClick={() => onChange(option.value)}
            className={cn(
              'rounded-lg px-3.5 py-1.5 text-[13px] font-bold transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--color-brand)]',
              active
                ? 'bg-[var(--color-brand-soft)] text-[var(--color-brand)]'
                : 'bg-transparent text-[var(--color-text-muted)] hover:text-[var(--color-text)]',
            )}
          >
            {option.label}
          </button>
        )
      })}
    </div>
  )
}
