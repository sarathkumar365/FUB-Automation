import type { ReactNode } from 'react'
import { uiText } from '../constants/uiText'

type AppPanelProps = {
  title?: string
  children?: ReactNode
  className?: string
}

export function AppPanel({ title, children, className }: AppPanelProps) {
  return (
    <section
      className={`w-[260px] border-r border-[var(--color-border)] bg-[var(--color-surface)] p-4 ${className ?? ''}`}
      aria-label={uiText.app.shell.panelAriaLabel}
    >
      {title ? <h2 className="text-sm font-semibold text-[var(--color-text)]">{title}</h2> : null}
      {children ? <div className={`space-y-3 text-sm text-[var(--color-text-muted)] ${title ? 'mt-3' : ''}`}>{children}</div> : null}
    </section>
  )
}
