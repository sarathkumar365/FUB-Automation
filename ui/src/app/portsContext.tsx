import type { ReactNode } from 'react'
import { appPorts } from '../platform/container'
import { PortsContext } from './portsContextValue'

export function AppPortsProvider({ children }: { children: ReactNode }) {
  return <PortsContext.Provider value={appPorts}>{children}</PortsContext.Provider>
}
