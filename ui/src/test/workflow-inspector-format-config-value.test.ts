import { describe, expect, it } from 'vitest'
import {
  formatConfigValue,
  isTemplating,
  isUrlShaped,
} from '../modules/workflows/ui/WorkflowDetailPage/StoryboardTab/inspector/formatConfigValue'

describe('formatConfigValue kind dispatch (D4.1-a / D4.2-b / D4.4-a / D4.5-c)', () => {
  it('classifies null/undefined as empty', () => {
    expect(formatConfigValue(null)).toEqual({ kind: 'empty' })
    expect(formatConfigValue(undefined)).toEqual({ kind: 'empty' })
  })

  it('classifies booleans and numbers as scalar with stringified text', () => {
    expect(formatConfigValue(true)).toEqual({ kind: 'scalar', text: 'true' })
    expect(formatConfigValue(false)).toEqual({ kind: 'scalar', text: 'false' })
    expect(formatConfigValue(42)).toEqual({ kind: 'scalar', text: '42' })
  })

  it('classifies short plain strings as scalar', () => {
    const out = formatConfigValue('webhook')
    expect(out).toEqual({ kind: 'scalar', text: 'webhook' })
  })

  it('classifies long plain strings as plain', () => {
    const long = 'a'.repeat(80)
    const out = formatConfigValue(long)
    expect(out).toEqual({ kind: 'plain', text: long })
  })

  it('classifies multi-line strings as plain regardless of length', () => {
    const out = formatConfigValue('line1\nline2')
    expect(out.kind).toBe('plain')
  })

  it('classifies https URLs as url', () => {
    const url = 'https://api.followupboss.com/v1/people/123'
    const out = formatConfigValue(url)
    expect(out).toEqual({ kind: 'url', text: url })
  })

  it('classifies http URLs as url', () => {
    const out = formatConfigValue('http://example.com')
    expect(out.kind).toBe('url')
  })

  it('does NOT classify mailto/tel strings as url (per D4.5-c scope)', () => {
    const mailto = formatConfigValue('mailto:owner@example.com')
    const tel = formatConfigValue('tel:+15555551212')
    expect(mailto.kind).not.toBe('url')
    expect(tel.kind).not.toBe('url')
  })

  it('classifies JSONata-style strings (leading $) as templating', () => {
    const out = formatConfigValue('$now()')
    expect(out).toEqual({ kind: 'templating', text: '$now()' })
  })

  it('classifies handlebars-style strings ({{ }}) as templating', () => {
    const out = formatConfigValue('Welcome {{ lead.name }}')
    expect(out).toEqual({ kind: 'templating', text: 'Welcome {{ lead.name }}' })
  })

  it('prefers url over templating when both patterns could match', () => {
    // URL-shape check comes first; a URL containing {{ still wins as url
    // because copy-only is the useful affordance there.
    const out = formatConfigValue('https://api.example.com/{{ lead.id }}/tasks')
    expect(out.kind).toBe('url')
  })

  it('classifies objects as structured', () => {
    const out = formatConfigValue({ a: 1 })
    expect(out).toEqual({ kind: 'structured', value: { a: 1 } })
  })

  it('classifies arrays as structured', () => {
    const out = formatConfigValue([1, 2, 3])
    expect(out.kind).toBe('structured')
  })
})

describe('isUrlShaped helper', () => {
  it('matches http and https (case-insensitive)', () => {
    expect(isUrlShaped('http://x')).toBe(true)
    expect(isUrlShaped('HTTPS://x')).toBe(true)
  })

  it('rejects non-http schemes', () => {
    expect(isUrlShaped('ftp://x')).toBe(false)
    expect(isUrlShaped('mailto:x@y.com')).toBe(false)
  })
})

describe('isTemplating helper', () => {
  it('matches leading $', () => {
    expect(isTemplating('$now()')).toBe(true)
  })

  it('matches {{ or }} anywhere', () => {
    expect(isTemplating('Hi {{ name }}')).toBe(true)
    expect(isTemplating('x }} y')).toBe(true)
  })

  it('rejects plain strings', () => {
    expect(isTemplating('Hello World')).toBe(false)
    expect(isTemplating('user_id')).toBe(false)
  })
})
