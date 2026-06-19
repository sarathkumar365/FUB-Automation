import type { PropsWithChildren } from 'react'
import { uiText } from '../constants/uiText'

type InspectorPanelProps = PropsWithChildren<{
  title?: string
  isOpen?: boolean
  className?: string
}>

export function InspectorPanel({
  title = uiText.app.shell.inspectorTitle,
  isOpen = true,
  className,
  children,
}: InspectorPanelProps) {
  if (!isOpen) {
    return null
  }

  return (
    <aside
      className={`border-l border-[var(--color-border)] bg-[var(--color-surface)] p-4 ${className ?? ''}`}
      aria-label={uiText.app.shell.inspectorAriaLabel}
    >
      <h3 className="text-sm font-semibold text-[var(--color-text)]">{title}</h3>
      <div className="mt-3">
        {children ?? <p className="text-sm text-[var(--color-text-muted)]">{uiText.app.shell.inspectorFallback}</p>}
      </div>
    </aside>
  )
}
