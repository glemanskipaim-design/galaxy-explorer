import { Route, Routes } from 'react-router'
import { AppShell } from './components/layout/AppShell'
import { CategoryPage } from './pages/CategoryPage'
import { HomePage } from './pages/HomePage'
import { NotFoundPage } from './pages/NotFoundPage'
import { ObjectPage } from './pages/ObjectPage'

/**
 * Roteamento declarativo (nao o "data mode" com loaders): quem busca dados e o TanStack Query.
 * O Router (Browser ou Memory, nos testes) e o QueryClient sao providos por fora.
 */
export function App() {
  return (
    <AppShell>
      <Routes>
        <Route path="/" element={<HomePage />} />
        <Route path="/objects/:slug" element={<ObjectPage />} />
        <Route path="/categories/:code" element={<CategoryPage />} />
        <Route path="*" element={<NotFoundPage />} />
      </Routes>
    </AppShell>
  )
}
