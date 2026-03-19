import { createContext } from 'react'
import { appPorts, type AppPorts } from '../platform/container'

export const PortsContext = createContext<AppPorts>(appPorts)
