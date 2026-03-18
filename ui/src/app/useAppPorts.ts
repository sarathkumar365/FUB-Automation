import { useContext } from 'react'
import { PortsContext } from './portsContextValue'

export function useAppPorts() {
  return useContext(PortsContext)
}
