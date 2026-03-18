import type { PropsWithChildren } from 'react'

type PageCardProps = PropsWithChildren<{
  title: string
  subtitle?: string
}>

export function PageCard({ title, subtitle, children }: PageCardProps) {
  return (
    <section className="rounded-lg border border-slate-200 bg-white p-5 shadow-sm">
      <h2 className="text-lg font-semibold text-slate-900">{title}</h2>
      {subtitle ? <p className="mt-1 text-sm text-slate-600">{subtitle}</p> : null}
      <div className="mt-4">{children}</div>
    </section>
  )
}
