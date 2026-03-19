import { useEffect } from 'react'
import type { ShellRegionContent } from './shellRegionsContext'
import { useShellRegions } from './useShellRegions'

type RegionRegistrationInput = {
  panel?: ShellRegionContent | null
  inspector?: ShellRegionContent | null
}

export function useShellRegionRegistration({ panel, inspector }: RegionRegistrationInput) {
  const { setPanelContent, setInspectorContent } = useShellRegions()

  useEffect(() => {
    setPanelContent(panel ?? null)
    setInspectorContent(inspector ?? null)

    return () => {
      setPanelContent(null)
      setInspectorContent(null)
    }
  }, [inspector, panel, setInspectorContent, setPanelContent])
}
