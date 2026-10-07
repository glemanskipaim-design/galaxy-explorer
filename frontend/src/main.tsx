import '@fontsource-variable/inter'
import '@fontsource-variable/space-grotesk'
import './styles/tokens.css'
import './styles/global.css'

import { QueryClientProvider } from '@tanstack/react-query'
import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router'
import { createQueryClient } from './api/queries'
import { App } from './App'

const root = document.getElementById('root')
if (!root) throw new Error('elemento #root ausente no index.html')

const queryClient = createQueryClient()

createRoot(root).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <App />
      </BrowserRouter>
    </QueryClientProvider>
  </StrictMode>,
)
