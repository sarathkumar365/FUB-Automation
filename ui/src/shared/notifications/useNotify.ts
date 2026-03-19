import { useContext } from 'react'
import { notifyContext } from './notifyContext'

export function useNotify() {
  return useContext(notifyContext)
}
