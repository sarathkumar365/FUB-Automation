import type { PropsWithChildren } from 'react'
import { uiText } from '../constants/uiText'

type EmptyStateProps = PropsWithChildren<{
  title?: string
  message?: string
}>

export function EmptyState({ title = uiText.states.emptyTitle, message = uiText.states.emptyMessage, children }: EmptyStateProps) {
  return (
    <div className="rounded-lg border border-[var(--color-border)] bg-[var(--color-surface)] p-4">
      <p className="text-sm font-semibold text-[var(--color-text)]">{title}</p>
      <p className="mt-1 text-sm text-[var(--color-text-muted)]">{message}</p>
      {children ? <div className="mt-3">{children}</div> : null}
    </div>
  )
}
