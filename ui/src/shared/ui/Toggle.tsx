import * as SwitchPrimitive from '@radix-ui/react-switch'
import { cn } from '../lib/cn'

type ToggleProps = {
  checked: boolean
  onCheckedChange?: (checked: boolean) => void
  disabled?: boolean
  id?: string
  ariaLabel?: string
}

export function Toggle({ checked, onCheckedChange, disabled, id, ariaLabel }: ToggleProps) {
  return (
    <SwitchPrimitive.Root
      id={id}
      checked={checked}
      onCheckedChange={onCheckedChange}
      disabled={disabled}
      aria-label={ariaLabel}
      className={cn(
        'relative inline-flex h-6 w-11 shrink-0 cursor-pointer items-center rounded-full border transition-colors',
        'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--color-brand)] focus-visible:ring-offset-2 ring-offset-[var(--color-surface)]',
        'disabled:cursor-not-allowed disabled:opacity-50',
        'data-[state=checked]:border-[var(--color-brand)] data-[state=checked]:bg-[var(--color-brand)]',
        'data-[state=unchecked]:border-[var(--color-border)] data-[state=unchecked]:bg-[var(--color-surface-alt)]',
      )}
    >
      <SwitchPrimitive.Thumb
        className={cn(
          'pointer-events-none inline-block h-5 w-5 rounded-full bg-white shadow-[var(--shadow-subtle)] transition-transform',
          'data-[state=checked]:translate-x-5 data-[state=unchecked]:translate-x-0.5',
        )}
      />
    </SwitchPrimitive.Root>
  )
}
