import { configDefaults, defineConfig } from 'vitest/config'
import vue from '@vitejs/plugin-vue'

// In quarkusDev the browser talks to Quarkus (:8080), which forwards everything except
// /api, /ws and /q to this dev server. The proxy below is only used when running `npm run dev`
// standalone and opening :5173 directly.
const backend = process.env.HMC_BACKEND ?? 'http://localhost:8080'

export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    strictPort: true,
    proxy: {
      '/api': backend,
      '/ws': { target: backend.replace(/^http/, 'ws'), ws: true },
    },
  },
  test: {
    environment: 'jsdom',
    // need a running backend, see vitest.integration.config.ts
    exclude: [...configDefaults.exclude, 'src/integration/**'],
  },
})
