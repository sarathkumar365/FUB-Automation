import type { SelectHTMLAttributes } from 'react'
import { cn } from '../lib/cn'

export function Select({ className, children, ...props }: SelectHTMLAttributes<HTMLSelectElement>) {
  return (
    <select
      className={cn(
        'h-9 w-full appearance-none rounded-md border border-[var(--color-border)] bg-[var(--color-surface)] px-3 py-2 pr-8 text-sm text-[var(--color-text)] outline-none transition-colors',
        'bg-[image:var(--select-chevron)] bg-no-repeat bg-[length:14px_14px] bg-[position:right_0.625rem_center]',
        'hover:border-[var(--color-text-muted)]',
        'focus-visible:ring-2 focus-visible:ring-[var(--color-brand)] focus-visible:ring-offset-2 ring-offset-[var(--color-surface)]',
        'disabled:cursor-not-allowed disabled:opacity-50',
        className,
      )}
      {...props}
    >
      {children}
    </select>
  )
}
