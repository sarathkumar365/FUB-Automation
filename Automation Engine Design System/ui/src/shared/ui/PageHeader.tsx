import type { PropsWithChildren } from 'react'

type PageHeaderProps = PropsWithChildren<{
  title: string
  subtitle?: string
}>

export function PageHeader({ title, subtitle, children }: PageHeaderProps) {
  return (
    <header className="flex items-start justify-between gap-4">
      <div>
        <h2 className="text-xl font-semibold text-[var(--color-text)]">{title}</h2>
        {subtitle ? <p className="mt-1 text-sm text-[var(--color-text-muted)]">{subtitle}</p> : null}
      </div>
      {children ? <div className="shrink-0">{children}</div> : null}
    </header>
  )
}
