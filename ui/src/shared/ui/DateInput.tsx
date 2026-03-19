import type { InputHTMLAttributes } from 'react'
import { Input } from './input'

type DateInputProps = Omit<InputHTMLAttributes<HTMLInputElement>, 'type'>

export function DateInput(props: DateInputProps) {
  return <Input type="date" {...props} />
}
