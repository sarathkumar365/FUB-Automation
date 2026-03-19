import { createContext } from 'react'
import type { ReactNode } from 'react'

export type ShellRegionContent = {
  title?: string
  body: ReactNode
}

export type ShellRegionsContextValue = {
  panelContent: ShellRegionContent | null
  inspectorContent: ShellRegionContent | null
  setPanelContent: (content: ShellRegionContent | null) => void
  setInspectorContent: (content: ShellRegionContent | null) => void
}

export const shellRegionsContext = createContext<ShellRegionsContextValue | null>(null)
