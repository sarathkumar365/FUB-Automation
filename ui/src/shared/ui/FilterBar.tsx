import type { PropsWithChildren, ReactNode } from 'react'

type FilterBarProps = PropsWithChildren<{
  actions?: ReactNode
}>

export function FilterBar({ actions, children }: FilterBarProps) {
  return (
    <div className="rounded-lg border border-[var(--color-border)] bg-[var(--color-surface)] p-3">
      <div className="flex flex-wrap items-center gap-3">
        <div className="flex flex-wrap items-center gap-2">{children}</div>
        {actions ? <div className="ml-auto flex items-center gap-2">{actions}</div> : null}
      </div>
    </div>
  )
}
