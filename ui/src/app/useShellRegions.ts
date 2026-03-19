import { useContext } from 'react'
import { shellRegionsContext } from './shellRegionsContext'

export function useShellRegions() {
  const context = useContext(shellRegionsContext)

  if (!context) {
    throw new Error('useShellRegions must be used within ShellRegionsProvider')
  }

  return context
}
