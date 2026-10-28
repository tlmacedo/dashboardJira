import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'
import { LangProvider } from './i18n/LangProvider'
import { inicioDoPapel } from './nav/nav'
import { Abertas } from './screens/Abertas/Abertas'
import { Auditoria } from './screens/Auditoria/Auditoria'
import { Fechadas } from './screens/Fechadas/Fechadas'
import { Metas } from './screens/Metas/Metas'
import { Negligenciadas } from './screens/Negligenciadas/Negligenciadas'
import { Divergencia } from './screens/Divergencia/Divergencia'
import { MapaDeCartoes } from './screens/MapaDeCartoes/MapaDeCartoes'
import { Sincronizacao } from './screens/Sincronizacao/Sincronizacao'
import { TempoEmStatus } from './screens/TempoEmStatus/TempoEmStatus'
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
                <Route path="abertas" element={<Abertas />} />
                <Route path="negligenciadas" element={<Negligenciadas />} />
                <Route path="fechadas" element={<Fechadas />} />
                <Route path="metas" element={<Metas />} />
                <Route path="tempo" element={<TempoEmStatus />} />
                <Route path="auditoria" element={<Auditoria />} />
                <Route path="divergencia" element={<Divergencia />} />
                <Route path="sincronizacao" element={<Sincronizacao />} />
                <Route path="mapaCartoes" element={<MapaDeCartoes />} />
                <Route path="*" element={<Pagina />} />
              </Route>
            </Routes>
          </BrowserRouter>
        </LangProvider>
      </ThemeProvider>
    </QueryClientProvider>
  )
}
