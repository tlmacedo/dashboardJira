import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'
import { LangProvider } from './i18n/LangProvider'
import { inicioDoPapel } from './nav/nav'
import { Pagina } from './shell/Pagina'
import { useRole } from './shell/RoleContext'
import { Shell } from './shell/Shell'
import { ThemeProvider } from './theme/ThemeProvider'

const queryClient = new QueryClient()

function Inicio() {
  return <Navigate to={inicioDoPapel(useRole())} replace />
}

export default function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <ThemeProvider>
        <LangProvider>
          <BrowserRouter>
            <Routes>
              <Route element={<Shell />}>
                <Route index element={<Inicio />} />
                <Route path="*" element={<Pagina />} />
              </Route>
            </Routes>
          </BrowserRouter>
        </LangProvider>
      </ThemeProvider>
    </QueryClientProvider>
  )
}
