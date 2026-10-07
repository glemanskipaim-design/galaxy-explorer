import '@testing-library/jest-dom/vitest'
import { cleanup } from '@testing-library/react'
import { afterAll, afterEach, beforeAll } from 'vitest'
import { server } from './server'

// Qualquer requisicao sem handler FALHA o teste: impede dado inventado entrar pela porta dos fundos.
// (MSW 3 renomeou onUnhandledRequest -> onUnhandledFrame.)
beforeAll(() => server.listen({ onUnhandledFrame: 'error' }))
afterEach(() => {
  cleanup()
  server.resetHandlers()
})
afterAll(() => server.close())
