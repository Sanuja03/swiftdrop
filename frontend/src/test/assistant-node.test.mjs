import { after } from 'node:test'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { createServer } from 'vite'

// The assistant API uses the shared Vite HTTP client (aliases/import.meta.env).
// Load its existing Node tests through Vite without changing application imports.
const root = fileURLToPath(new URL('../../', import.meta.url))
const server = await createServer({
  configFile: false,
  root,
  cacheDir: path.join(root, 'node_modules/.vite-assistant-tests'),
  optimizeDeps: { noDiscovery: true },
  resolve: { alias: { '@': path.join(root, 'src') } },
  server: { middlewareMode: true, hmr: false },
})
after(() => server.close())
await server.ssrLoadModule('/src/features/assistant/__tests__/assistant.test.js')
