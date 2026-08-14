import type { PropsWithChildren, ReactNode } from 'react'
import { uiText } from '../constants/uiText'

type EmptyStateProps = PropsWithChildren<{
  title?: string
  message?: string
  /** Hero layout (reporting handoff): centered, icon tile + uppercase kicker. */
  hero?: boolean
  icon?: ReactNode
  kicker?: string
}>

export function EmptyState({
  title = uiText.states.emptyTitle,
  message = uiText.states.emptyMessage,
  hero = false,
  icon,
  kicker,
  children,
}: EmptyStateProps) {
  if (hero) {
    return (
      <div className="flex flex-col items-center gap-2 text-center">
        {icon ? (
          <div className="mb-1.5 flex h-[52px] w-[52px] items-center justify-center rounded-[14px] bg-[var(--color-brand-soft)] text-[var(--color-brand)]">
            {icon}
          </div>
        ) : null}
        {kicker ? (
          <p className="text-[11px] font-bold uppercase tracking-[0.08em] text-[var(--color-text-muted)]">
            {kicker}
          </p>
        ) : null}
        <h3 className="text-lg font-bold text-[var(--color-text)]">{title}</h3>
        <p className="max-w-[420px] text-sm text-[var(--color-text-muted)]">{message}</p>
        {children ? <div className="mt-2.5">{children}</div> : null}
      </div>
    )
  }

  return (
    <div className="rounded-lg border border-[var(--color-border)] bg-[var(--color-surface)] p-4">
      <p className="text-sm font-semibold text-[var(--color-text)]">{title}</p>
      <p className="mt-1 text-sm text-[var(--color-text-muted)]">{message}</p>
      {children ? <div className="mt-3">{children}</div> : null}
    </div>
  )
}
