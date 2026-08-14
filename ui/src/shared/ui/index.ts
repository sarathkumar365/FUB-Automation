/**
 * Barrel for `shared/ui`.
 *
 * Two tiers are re-exported from here:
 *   - **Primitives** (shadcn-style wrappers over Radix + hand-rolled atoms)
 *   - **Recipes** (opinionated compositions built on the primitives — added
 *     under `./recipes/` as Slice 3 of the workflow-builder UI audit ships)
 *
 * The barrel is purely additive. Callers that already import a component by
 * its explicit path (e.g. `shared/ui/Badge`) keep working; new call sites
 * can choose whichever form reads better.
 *
 * See `./README.md` for the tier model and when to add a new file here vs.
 * keeping it inside a feature module.
 */

// --- Primitives: layout shells ---
export { AppContentFrame } from './AppContentFrame'
export { BrandLockup } from './BrandLockup'
export { AppPanel } from './AppPanel'
export { AppRail } from './AppRail'
export { InspectorPanel } from './InspectorPanel'
export { PageCard } from './PageCard'
export { PageHeader } from './PageHeader'
export { PanelNav } from './PanelNav'

// --- Primitives: shadcn-style atoms ---
export { Badge, type BadgeProps } from './badge'
export { Button, type ButtonProps } from './button'
export { Input } from './input'
export { Select } from './select'
export { DateInput } from './DateInput'

// --- Primitives: Radix wrappers ---
export { Popover, PopoverAnchor, PopoverContent } from './Popover'
export { Tabs, TabsContent, TabsList, TabsTrigger } from './Tabs'

// --- Primitives: specialized ---
export { ConfirmDialog, type ConfirmDialogProps } from './ConfirmDialog'
export { DataTable, type ColumnDef, type DataTableProps } from './DataTable'
export { EmptyState } from './EmptyState'
export { ErrorState } from './ErrorState'
export { FilterBar } from './FilterBar'
export { JsonViewer, type JsonViewerProps } from './JsonViewer'
export { LoadingState } from './LoadingState'
export { PagePagination, type PagePaginationProps } from './PagePagination'
export { SegmentedControl, type SegmentedOption } from './SegmentedControl'
export { StatusBadge, type StatusTone } from './StatusBadge'
export { Toggle } from './Toggle'

// --- Icons ---
export {
  ActivityIcon,
  AlertTriangleIcon,
  ApplyIcon,
  BarChartIcon,
  ChevronDownIcon,
  ChevronLeftIcon,
  CloseIcon,
  CompassIcon,
  EyeIcon,
  FilterIcon,
  LockIcon,
  LogoMarkIcon,
  LogoutIcon,
  MoonIcon,
  NextIcon,
  PauseIcon,
  PhoneIcon,
  RefreshIcon,
  ReplayIcon,
  ResetIcon,
  ResumeIcon,
  SettingsIcon,
  SunIcon,
  UsersIcon,
  WorkflowIcon,
} from './icons'

// --- Theme ---
export { ThemeToggle } from './ThemeToggle'

// --- Recipes ---
export * from './recipes'
