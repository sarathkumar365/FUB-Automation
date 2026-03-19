import { createContext } from 'react'
import type { NotifyApi } from './types'

const noop = () => undefined

export const notifyContext = createContext<NotifyApi>({
  success: noop,
  error: noop,
  warning: noop,
  info: noop,
})
