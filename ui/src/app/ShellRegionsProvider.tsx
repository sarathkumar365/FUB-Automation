import type { PropsWithChildren } from 'react'
import { useMemo, useState } from 'react'
import type { ShellRegionContent, ShellRegionsContextValue } from './shellRegionsContext'
import { shellRegionsContext } from './shellRegionsContext'

export function ShellRegionsProvider({ children }: PropsWithChildren) {
  const [panelContent, setPanelContent] = useState<ShellRegionContent | null>(null)
  const [inspectorContent, setInspectorContent] = useState<ShellRegionContent | null>(null)

  const value = useMemo<ShellRegionsContextValue>(
    () => ({
      panelContent,
      inspectorContent,
      setPanelContent,
      setInspectorContent,
    }),
    [panelContent, inspectorContent],
  )

  return <shellRegionsContext.Provider value={value}>{children}</shellRegionsContext.Provider>
}
