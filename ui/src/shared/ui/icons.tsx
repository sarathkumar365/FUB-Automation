import { AlertTriangle, ChevronDown, ChevronLeft, Compass, Lock, RefreshCw, Settings } from 'lucide-react'
import type { SVGProps } from 'react'
import { cn } from '../lib/cn'

type IconProps = SVGProps<SVGSVGElement>

// lucide glyphs (Decision G) normalised to the hand-rolled set: stroke 1.8,
// decorative (aria-hidden), 16px default. Callers override strokeWidth/size as needed.
// Kept as explicit components (not a factory) because react-refresh requires
// each exported component to be a literal function.
export function SettingsIcon({ className, ...props }: IconProps) {
  return <Settings aria-hidden="true" strokeWidth={1.8} className={cn('h-4 w-4', className)} {...props} />
}

export function RefreshIcon({ className, ...props }: IconProps) {
  return <RefreshCw aria-hidden="true" strokeWidth={1.8} className={cn('h-4 w-4', className)} {...props} />
}

export function AlertTriangleIcon({ className, ...props }: IconProps) {
  return <AlertTriangle aria-hidden="true" strokeWidth={1.8} className={cn('h-4 w-4', className)} {...props} />
}

export function CompassIcon({ className, ...props }: IconProps) {
  return <Compass aria-hidden="true" strokeWidth={1.8} className={cn('h-4 w-4', className)} {...props} />
}

export function LockIcon({ className, ...props }: IconProps) {
  return <Lock aria-hidden="true" strokeWidth={1.8} className={cn('h-4 w-4', className)} {...props} />
}

export function ChevronLeftIcon({ className, ...props }: IconProps) {
  return <ChevronLeft aria-hidden="true" strokeWidth={1.8} className={cn('h-4 w-4', className)} {...props} />
}

export function ChevronDownIcon({ className, ...props }: IconProps) {
  return <ChevronDown aria-hidden="true" strokeWidth={1.8} className={cn('h-4 w-4', className)} {...props} />
}

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

export function PauseIcon(props: IconProps) {
  return (
    <SvgIcon {...props}>
      <path d="M9 5v14" />
      <path d="M15 5v14" />
    </SvgIcon>
  )
}

export function ResumeIcon(props: IconProps) {
  return (
    <SvgIcon {...props}>
      <path d="m8 5 11 7-11 7z" />
    </SvgIcon>
  )
}

export function CloseIcon(props: IconProps) {
  return (
    <SvgIcon {...props}>
      <path d="M18 6 6 18" />
      <path d="m6 6 12 12" />
    </SvgIcon>
  )
}

export function ReplayIcon(props: IconProps) {
  return (
    <SvgIcon {...props}>
      <path d="M3 12a9 9 0 1 0 2.64-6.36" />
      <path d="M3 4v5h5" />
    </SvgIcon>
  )
}

// --- Navigation icons (Lucide-style, stroke-width 1.8) ---

export function ActivityIcon(props: IconProps) {
  return (
    <SvgIcon {...props}>
      <path d="M22 12h-4l-3 9L9 3l-3 9H2" />
    </SvgIcon>
  )
}

export function PhoneIcon(props: IconProps) {
  return (
    <SvgIcon {...props}>
      <path d="M22 16.92v3a2 2 0 0 1-2.18 2 19.79 19.79 0 0 1-8.63-3.07 19.5 19.5 0 0 1-6-6 19.79 19.79 0 0 1-3.07-8.67A2 2 0 0 1 4.11 2h3a2 2 0 0 1 2 1.72c.127.96.361 1.903.7 2.81a2 2 0 0 1-.45 2.11L8.09 9.91a16 16 0 0 0 6 6l1.27-1.27a2 2 0 0 1 2.11-.45c.907.339 1.85.573 2.81.7A2 2 0 0 1 22 16.92z" />
    </SvgIcon>
  )
}

export function UsersIcon(props: IconProps) {
  return (
    <SvgIcon {...props}>
      <path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2" />
      <circle cx="9" cy="7" r="4" />
      <path d="M22 21v-2a4 4 0 0 0-3-3.87" />
      <path d="M16 3.13a4 4 0 0 1 0 7.75" />
    </SvgIcon>
  )
}

export function WorkflowIcon(props: IconProps) {
  return (
    <SvgIcon {...props}>
      <line x1="6" y1="3" x2="6" y2="15" />
      <circle cx="18" cy="6" r="3" />
      <circle cx="6" cy="18" r="3" />
      <path d="M18 9a9 9 0 0 1-9 9" />
    </SvgIcon>
  )
}

export function LogoutIcon(props: IconProps) {
  return (
    <SvgIcon {...props}>
      <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4" />
      <path d="m16 17 5-5-5-5" />
      <path d="M21 12H9" />
    </SvgIcon>
  )
}

// Brand mark — flow-nodes monogram (one node branching to two), stroke-width 2
// per the design system. Intended to sit inside a brand-cyan square (rail logo).
export function LogoMarkIcon(props: IconProps) {
  return (
    <SvgIcon strokeWidth={2} {...props}>
      <path d="M8 12h3" />
      <path d="M11 12c2.2 0 2.2-4 4.4-4" />
      <path d="M11 12c2.2 0 2.2 4 4.4 4" />
      <circle cx="6.5" cy="12" r="1.6" />
      <circle cx="16.5" cy="8" r="1.6" />
      <circle cx="16.5" cy="16" r="1.6" />
    </SvgIcon>
  )
}

export function MoonIcon(props: IconProps) {
  return (
    <SvgIcon {...props}>
      <path d="M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79z" />
    </SvgIcon>
  )
}

export function SunIcon(props: IconProps) {
  return (
    <SvgIcon {...props}>
      <circle cx="12" cy="12" r="4" />
      <path d="M12 2v2" />
      <path d="M12 20v2" />
      <path d="m4.93 4.93 1.41 1.41" />
      <path d="m17.66 17.66 1.41 1.41" />
      <path d="M2 12h2" />
      <path d="M20 12h2" />
      <path d="m6.34 17.66-1.41 1.41" />
      <path d="m19.07 4.93-1.41 1.41" />
    </SvgIcon>
  )
}
