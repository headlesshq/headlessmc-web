import { defineConfig } from 'vitest/config'

// Integration tests against a running backend: HMC_BACKEND=http://localhost:8080 npm run test:integration
export default defineConfig({
  test: {
    include: ['src/integration/**/*.test.ts'],
    environment: 'node',
  },
})
