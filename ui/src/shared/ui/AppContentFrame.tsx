import type { PropsWithChildren } from 'react'
import { uiText } from '../constants/uiText'

type AppContentFrameProps = PropsWithChildren<{
  className?: string
}>

export function AppContentFrame({ className, children }: AppContentFrameProps) {
  return (
    <main className={`min-w-0 bg-[var(--color-bg)] p-4 md:p-6 ${className ?? ''}`} aria-label={uiText.app.shell.contentAriaLabel}>
      {children}
    </main>
  )
}
