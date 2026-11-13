import react from '@vitejs/plugin-react'
import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'

const BACKEND_URL = 'http://localhost:8080'

// The dev server forwards API and WebSocket calls to the backend, so the
// browser only ever talks to one origin (no CORS setup needed).
export default defineConfig({
  plugins: [react()],
  resolve: {
    // '@/shared/...' and '@/features/...' instead of long ../../../ paths
    alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) },
  },
  server: {
    proxy: {
      '/api': BACKEND_URL,
      '/ws': { target: BACKEND_URL, ws: true },
    },
  },
})
