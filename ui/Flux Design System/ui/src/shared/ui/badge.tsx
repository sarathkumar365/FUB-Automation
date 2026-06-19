import type { HTMLAttributes } from 'react'
import { cva, type VariantProps } from 'class-variance-authority'
import { cn } from '../lib/cn'

const badgeVariants = cva('inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-semibold', {
  variants: {
    variant: {
      default: 'bg-[var(--color-brand-soft)] text-[var(--color-brand)]',
      success: 'bg-[var(--color-status-ok-bg)] text-[var(--color-status-ok)]',
      warning: 'bg-[var(--color-status-warn-bg)] text-[var(--color-status-warn)]',
      error: 'bg-[var(--color-status-bad-bg)] text-[var(--color-status-bad)]',
      muted: 'bg-[var(--color-surface-alt)] text-[var(--color-text-muted)]',
    },
  },
  defaultVariants: {
    variant: 'default',
  },
})

export interface BadgeProps extends HTMLAttributes<HTMLDivElement>, VariantProps<typeof badgeVariants> {}

export function Badge({ className, variant, ...props }: BadgeProps) {
  return <div className={cn(badgeVariants({ variant }), className)} {...props} />
}
