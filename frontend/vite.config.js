import react from '@vitejs/plugin-react'
import process from 'node:process'
import { fileURLToPath, URL } from 'node:url'
import { defineConfig, loadEnv } from 'vite'

// The dev server forwards API and WebSocket calls to the backend, so the
// browser only ever talks to one origin (no CORS setup needed).
// VITE_DEV_PROXY_TARGET points it at a backend other than the local default.
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd())
  const backendUrl = env.VITE_DEV_PROXY_TARGET || 'http://localhost:8080'

  return {
    plugins: [react()],
    resolve: {
      // '@/shared/...' and '@/features/...' instead of long ../../../ paths
      alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) },
    },
    server: {
      proxy: {
        '/api': backendUrl,
        '/ws': { target: backendUrl, ws: true },
      },
    },
  }
})
