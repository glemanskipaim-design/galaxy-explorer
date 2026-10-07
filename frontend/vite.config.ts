import react from '@vitejs/plugin-react'
import { loadEnv } from 'vite'
import { defineConfig } from 'vitest/config'

// Alvo do proxy /api. Sem prefixo VITE_ de proposito: so variaveis VITE_* vao para o
// bundle do navegador, e o endereco do backend e configuracao de desenvolvimento.
// 127.0.0.1 (e nao "localhost"): o backend escuta so em IPv4 loopback e o Node pode
// resolver "localhost" para ::1 primeiro ("connection refused" intermitente).
const DEFAULT_API_TARGET = 'http://127.0.0.1:8081'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  const target = env.GALAXY_API_TARGET || DEFAULT_API_TARGET
  // changeOrigin: o Host chega ao backend como 127.0.0.1:8081 (compativel com uma
  // allowlist de Host no backend, defesa futura contra DNS rebinding).
  const proxy = { '/api': { target, changeOrigin: true } }

  return {
    plugins: [react()],
    // So loopback. Nao usar --host nem allowedHosts: true — a checagem de Host do
    // Vite e a defesa dele contra DNS rebinding, e o proxy da acesso ao POST /imports.
    server: { host: '127.0.0.1', port: 5173, strictPort: true, proxy },
    preview: { host: '127.0.0.1', port: 4173, strictPort: true, proxy },
    test: {
      environment: 'jsdom',
      setupFiles: ['./src/test/setup.ts'],
      include: ['src/**/*.test.{ts,tsx}'],
      restoreMocks: true,
    },
  }
})
