import { cva, type VariantProps } from 'class-variance-authority'
import type { ButtonHTMLAttributes } from 'react'
import { cn } from '../lib/cn'

const buttonVariants = cva(
  'inline-flex items-center justify-center rounded-md text-sm font-medium transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-offset-2 disabled:pointer-events-none disabled:opacity-50 ring-offset-[var(--color-surface)]',
  {
    variants: {
      variant: {
        default: 'bg-[var(--color-brand)] text-white hover:bg-[color-mix(in_srgb,var(--color-brand),black_12%)] focus-visible:ring-[var(--color-brand)]',
        secondary: 'bg-[var(--color-brand-soft)] text-[var(--color-brand)] hover:bg-[color-mix(in_srgb,var(--color-brand-soft),black_6%)] focus-visible:ring-[var(--color-brand)]',
        outline: 'border border-[var(--color-border)] bg-[var(--color-surface)] text-[var(--color-text)] hover:bg-[var(--color-surface-alt)] focus-visible:ring-[var(--color-brand)]',
        ghost: 'text-[var(--color-text)] hover:bg-[var(--color-surface-alt)] focus-visible:ring-[var(--color-brand)]',
        destructive: 'bg-[var(--color-status-bad)] text-white hover:bg-[color-mix(in_srgb,var(--color-status-bad),black_8%)] focus-visible:ring-[var(--color-status-bad)]',
      },
      size: {
        default: 'h-9 px-4 py-2',
        sm: 'h-8 rounded-md px-3 text-xs',
        lg: 'h-10 rounded-md px-8',
        icon: 'h-9 w-9',
      },
    },
    defaultVariants: {
      variant: 'default',
      size: 'default',
    },
  },
)

export interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement>, VariantProps<typeof buttonVariants> {}

export function Button({ className, variant, size, ...props }: ButtonProps) {
  return <button className={cn(buttonVariants({ variant, size }), className)} {...props} />
}
