import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vitest/config'
import react from '@vitejs/plugin-react'

const r = (p: string) => fileURLToPath(new URL(p, import.meta.url))

export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: [
      { find: /^@app\//, replacement: r('./src/app/') },
      { find: /^@platform\//, replacement: r('./src/platform/') },
      { find: /^@modules\//, replacement: r('./src/modules/') },
      { find: /^@shared\//, replacement: r('./src/shared/') },
    ],
  },
  server: {
    proxy: {
      '/admin/': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      '/webhooks': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
    globals: true,
    include: ['src/**/*.test.{ts,tsx}'],
    exclude: ['e2e/**'],
  },
})
