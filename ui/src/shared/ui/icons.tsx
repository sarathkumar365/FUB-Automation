import type { SVGProps } from 'react'
import { cn } from '../lib/cn'

type IconProps = SVGProps<SVGSVGElement>

function SvgIcon({ className, children, ...props }: IconProps) {
  return (
    <svg
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.8"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
      className={cn('h-4 w-4', className)}
      {...props}
    >
      {children}
    </svg>
  )
}

export function FilterIcon(props: IconProps) {
  return (
    <SvgIcon {...props}>
      <path d="M3 5h18" />
      <path d="M6 12h12" />
      <path d="M10 19h4" />
    </SvgIcon>
  )
}

export function CalendarIcon(props: IconProps) {
  return (
    <SvgIcon {...props}>
      <rect x="3" y="5" width="18" height="16" rx="2" />
      <path d="M16 3v4" />
      <path d="M8 3v4" />
      <path d="M3 10h18" />
    </SvgIcon>
  )
}

export function ApplyIcon(props: IconProps) {
  return (
    <SvgIcon {...props}>
      <path d="M20 6 9 17l-5-5" />
    </SvgIcon>
  )
}

export function ResetIcon(props: IconProps) {
  return (
    <SvgIcon {...props}>
      <path d="M3 12a9 9 0 1 0 2.64-6.36" />
      <path d="M3 4v5h5" />
    </SvgIcon>
  )
}

export function NextIcon(props: IconProps) {
  return (
    <SvgIcon {...props}>
      <path d="m9 18 6-6-6-6" />
    </SvgIcon>
  )
}
