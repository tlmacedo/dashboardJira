import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'
import { LangProvider } from './i18n/LangProvider'
import { inicioDoPapel } from './nav/nav'
import { Abertas } from './screens/Abertas/Abertas'
import { AgingWip } from './screens/AgingWip/AgingWip'
import { Auditoria } from './screens/Auditoria/Auditoria'
import { Bloqueios } from './screens/Bloqueios/Bloqueios'
import { ChecklistReview } from './screens/ChecklistReview/ChecklistReview'
import { Cruzamentos } from './screens/Cruzamentos/Cruzamentos'
import { Epicos } from './screens/Epicos/Epicos'
import { Integridade } from './screens/Integridade/Integridade'
import { Fechadas } from './screens/Fechadas/Fechadas'
import { FluxoDaSemana } from './screens/FluxoDaSemana/FluxoDaSemana'
import { Negativas } from './screens/Negativas/Negativas'
import { Previsao } from './screens/Previsao/Previsao'
import { Calendario } from './screens/Calendario/Calendario'
import { Capacidade } from './screens/Capacidade/Capacidade'
import { Carga } from './screens/Carga/Carga'
import { Gargalos } from './screens/Gargalos/Gargalos'
import { Plano } from './screens/Plano/Plano'
import { Tendencia } from './screens/Tendencia/Tendencia'
import { CargaTipo } from './screens/CargaTipo/CargaTipo'
import { Cobertura } from './screens/Cobertura/Cobertura'
import { Competencias } from './screens/Competencias/Competencias'
import { Esforco } from './screens/Esforco/Esforco'
import { Posse } from './screens/Posse/Posse'
import { Revisoes } from './screens/Revisoes/Revisoes'
import { Simulador } from './screens/Simulador/Simulador'
import { Vencimentos } from './screens/Vencimentos/Vencimentos'
import { Riscos } from './screens/Riscos/Riscos'
import { Metas } from './screens/Metas/Metas'
import { Negligenciadas } from './screens/Negligenciadas/Negligenciadas'
import { Decisoes } from './screens/Decisoes/Decisoes'
import { Distribuicao } from './screens/Distribuicao/Distribuicao'
import { Rebalanceamento } from './screens/Rebalanceamento/Rebalanceamento'
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
                <Route path="negativas" element={<Negativas />} />
                <Route path="riscos" element={<Riscos />} />
                <Route path="previsao" element={<Previsao />} />
                <Route path="carga" element={<Carga />} />
                <Route path="capacidade" element={<Capacidade />} />
                <Route path="calendario" element={<Calendario />} />
                <Route path="plano" element={<Plano />} />
                <Route path="vencimentos" element={<Vencimentos />} />
                <Route path="gargalos" element={<Gargalos />} />
                <Route path="tendencia" element={<Tendencia />} />
                <Route path="posse" element={<Posse />} />
                <Route path="esforco" element={<Esforco />} />
                <Route path="cobertura" element={<Cobertura />} />
                <Route path="simulador" element={<Simulador />} />
                <Route path="competencias" element={<Competencias />} />
                <Route path="revisoes" element={<Revisoes />} />
                <Route path="cargaTipo" element={<CargaTipo />} />
                <Route path="epicos" element={<Epicos />} />
                <Route path="fluxoDaSemana" element={<FluxoDaSemana />} />
                <Route path="agingWip" element={<AgingWip />} />
                <Route path="bloqueios" element={<Bloqueios />} />
                <Route path="cruzamentos" element={<Cruzamentos />} />
                <Route path="checklistReview" element={<ChecklistReview />} />
                <Route path="integridade" element={<Integridade />} />
                <Route path="distribuicao" element={<Distribuicao />} />
                <Route path="rebalanceamento" element={<Rebalanceamento />} />
                <Route path="decisoes" element={<Decisoes />} />
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
