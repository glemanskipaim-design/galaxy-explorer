import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import type { ReactElement } from 'react'
import { MemoryRouter, useLocation } from 'react-router'
import { App } from '../App'

/** Mostra a URL atual (pathname + search) para os testes conferirem navegacao. */
function LocationProbe() {
  const location = useLocation()
  return <output data-testid="location">{location.pathname + location.search}</output>
}

function newClient(): QueryClient {
  return new QueryClient({
    defaultOptions: { queries: { retry: false, gcTime: Infinity }, mutations: { retry: false } },
  })
}

/** App inteiro numa rota, com QueryClient novo por teste (retry desligado). */
export function renderApp(path: string) {
  const user = userEvent.setup()
  const result = render(
    <QueryClientProvider client={newClient()}>
      <MemoryRouter initialEntries={[path]}>
        <App />
        <LocationProbe />
      </MemoryRouter>
    </QueryClientProvider>,
  )
  return { user, ...result }
}

/** Um componente isolado com os mesmos providers. */
export function renderWithProviders(ui: ReactElement, path = '/') {
  return render(
    <QueryClientProvider client={newClient()}>
      <MemoryRouter initialEntries={[path]}>{ui}</MemoryRouter>
    </QueryClientProvider>,
  )
}
