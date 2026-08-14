import type { PropsWithChildren } from 'react'

type PageCardProps = PropsWithChildren<{
  title: string
  subtitle?: string
}>

export function PageCard({ title, subtitle, children }: PageCardProps) {
  return (
    <section className="rounded-lg border border-[var(--color-border)] bg-[var(--color-surface)] p-5 shadow-[var(--shadow-subtle)]">
      <h2 className="text-lg font-semibold text-[var(--color-text)]">{title}</h2>
      {subtitle ? <p className="mt-1 text-sm text-[var(--color-text-muted)]">{subtitle}</p> : null}
      <div className="mt-4">{children}</div>
    </section>
  )
}
