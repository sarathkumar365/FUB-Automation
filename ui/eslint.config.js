import js from '@eslint/js'
import globals from 'globals'
import reactHooks from 'eslint-plugin-react-hooks'
import reactRefresh from 'eslint-plugin-react-refresh'
import tseslint from 'typescript-eslint'
import { defineConfig, globalIgnores } from 'eslint/config'

const NO_DEEP_RELATIVE = {
  group: ['../../../../**'],
  message: 'Avoid deep relative imports (4+ levels). Use a path alias: @app, @platform, @modules, @shared.',
}
const NO_MODULES = {
  group: ['@modules/**', '**/modules/**'],
  message:
    'platform must not import from modules (RD-011). The only exception is the auth token store, marked with an inline eslint-disable at its import.',
}
const NO_ADAPTERS = {
  group: ['@platform/adapters/**', '**/adapters/**'],
  message: 'ports must not import from adapters (RD-011); import contract types from @platform/contracts.',
}
const CONTRACTS_LEAF = {
  group: ['@app/**', '**/app/**', '@modules/**', '**/modules/**', '@shared/**', '**/shared/**', '@platform/**'],
  message: 'platform/contracts is a leaf — import only zod.',
}
const SHARED_LEAF = {
  group: ['@app/**', '**/app/**', '@modules/**', '**/modules/**', '@platform/**', '**/platform/**'],
  message: 'shared is a leaf — must not import from app/modules/platform.',
}

export default defineConfig([
  globalIgnores(['dist']),
  {
    files: ['**/*.{ts,tsx}'],
    extends: [
      js.configs.recommended,
      tseslint.configs.recommended,
      reactHooks.configs.flat.recommended,
      reactRefresh.configs.vite,
    ],
    languageOptions: {
      ecmaVersion: 2020,
      globals: globals.browser,
    },
    rules: {
      'no-restricted-imports': ['error', { patterns: [NO_DEEP_RELATIVE] }],
    },
  },
  {
    // platform must not import from modules (the auth token store is excepted per-line at its import)
    files: ['src/platform/**/*.{ts,tsx}'],
    rules: {
      'no-restricted-imports': ['error', { patterns: [NO_DEEP_RELATIVE, NO_MODULES] }],
    },
  },
  {
    // ports must not import from adapters (and still must not import from modules)
    files: ['src/platform/ports/**/*.{ts,tsx}'],
    rules: {
      'no-restricted-imports': ['error', { patterns: [NO_DEEP_RELATIVE, NO_MODULES, NO_ADAPTERS] }],
    },
  },
  {
    // platform/contracts is a zod-only leaf
    files: ['src/platform/contracts/**/*.{ts,tsx}'],
    rules: {
      'no-restricted-imports': ['error', { patterns: [NO_DEEP_RELATIVE, CONTRACTS_LEAF] }],
    },
  },
  {
    // shared is a leaf — must not reach into app/modules/platform
    files: ['src/shared/**/*.{ts,tsx}'],
    rules: {
      'no-restricted-imports': ['error', { patterns: [NO_DEEP_RELATIVE, SHARED_LEAF] }],
    },
  },
])
