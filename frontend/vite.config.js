import react from '@vitejs/plugin-react'
import process from 'node:process'
import { fileURLToPath, URL } from 'node:url'
import { defineConfig, loadEnv } from 'vite'

// Forward API and WebSocket requests to the backend.
// VITE_DEV_PROXY_TARGET can override the default backend address.
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd())
  const backendUrl = env.VITE_DEV_PROXY_TARGET || 'http://localhost:8080'

  return {
    plugins: [react()],
    resolve: {
      alias: {
        '@': fileURLToPath(new URL('./src', import.meta.url)),
      },
    },
    server: {
      proxy: {
        '/api': {
          target: backendUrl,
          changeOrigin: true,
        },
        '/ws': {
          target: backendUrl,
          changeOrigin: true,
          ws: true,
        },
      },
    },
  }
})