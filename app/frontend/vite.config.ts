import react from '@vitejs/plugin-react'
import { defineConfig } from 'vitest/config'
import { mockApi } from './dev-mock/mockApi.ts'

// https://vite.dev/config/
export default defineConfig({
  // PAINEL_MOCK=1 pnpm dev: API simulada com dados fictícios (só em desenvolvimento visual).
  plugins: [react(), ...(process.env.PAINEL_MOCK === '1' ? [mockApi()] : [])],
  server: {
    // Em desenvolvimento, a API (Ktor) roda na porta 8080 e o Vite encaminha /api para ela.
    proxy: process.env.PAINEL_MOCK === '1' ? undefined : { '/api': 'http://localhost:8080' },
  },
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
    css: false,
    // Folga para máquina carregada (jsdom + muitos workers); nenhum teste é pulado nem tem a asserção afrouxada.
    testTimeout: 20_000,
    hookTimeout: 20_000,
  },
})
