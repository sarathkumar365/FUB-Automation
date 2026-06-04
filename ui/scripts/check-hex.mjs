#!/usr/bin/env node
/**
 * Token-discipline guard. Fails if a hard-coded color literal (#hex or
 * rgb/rgba()) appears in product `*.tsx` / `*.css` outside `tokens.css`.
 *
 * Colors must come from `var(--…)` tokens (see src/styles/tokens.README.md).
 * Intentional exceptions:
 *   - add a `hex-allow` comment on the line, or
 *   - add a `hex-allow-file` comment anywhere in the file (dev-only surfaces).
 */
import { readdirSync, readFileSync, statSync } from 'node:fs'
import { join, relative } from 'node:path'
import { fileURLToPath } from 'node:url'

const SRC = fileURLToPath(new URL('../src', import.meta.url))
const EXCLUDE_REL = new Set(['styles/tokens.css'])
const EXTS = ['.tsx', '.css']
const HEX = /#[0-9a-fA-F]{3,8}\b/
const FUNC = /\brgba?\(|\bhsla?\(/

function walk(dir) {
  const out = []
  for (const name of readdirSync(dir)) {
    const full = join(dir, name)
    if (statSync(full).isDirectory()) out.push(...walk(full))
    else out.push(full)
  }
  return out
}

const violations = []
for (const file of walk(SRC)) {
  if (!EXTS.some((e) => file.endsWith(e))) continue
  const rel = relative(SRC, file)
  if (EXCLUDE_REL.has(rel)) continue
  if (rel.includes('/test/') || rel.endsWith('.test.tsx')) continue
  const text = readFileSync(file, 'utf8')
  if (text.includes('hex-allow-file')) continue
  text.split('\n').forEach((line, i) => {
    if (line.includes('hex-allow')) return
    if (HEX.test(line) || FUNC.test(line)) violations.push(`${rel}:${i + 1}: ${line.trim()}`)
  })
}

if (violations.length > 0) {
  console.error(
    '\n✖ Hard-coded color literals found. Use var(--…) tokens from tokens.css,\n' +
      '  or mark an intentional exception with a "hex-allow" / "hex-allow-file" comment:\n',
  )
  for (const v of violations) console.error('  ' + v)
  console.error(`\n${violations.length} violation(s).`)
  process.exit(1)
}
console.log('✓ check-hex: no hard-coded color literals outside tokens.css')
