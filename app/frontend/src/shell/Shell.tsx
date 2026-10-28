import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { Outlet } from 'react-router-dom'
import { fetchMe, fetchMeta, type Me } from '../api/client'
import { useLang } from '../i18n/LangProvider'
import { ROLES, type Role } from '../nav/nav'
import { gravarPreferencia, lerPreferencia } from '../storage'
import { AvisoDeAlertas } from './AvisoDeAlertas'
import { DevBanner } from './DevBanner'
import { Menu } from './Menu'
import { RoleContext } from './RoleContext'
import { SessaoExpirada } from './SessaoExpirada'

const CHAVE_PAPEL = 'painel.papelSimulado'

function papelSimuladoInicial(): Role {
  const salvo = lerPreferencia(CHAVE_PAPEL)
  return ROLES.find((r) => r === salvo) ?? 'GERENTE' // padrão em desenvolvimento
}

/** Papel mais alto entre os informados por /api/me; qualquer coisa inesperada vira USUARIO. */
function papelDeMe(me: Me | undefined): Role {
  const roles: unknown = me?.roles
  if (!Array.isArray(roles)) return 'USUARIO'
  const validos = ROLES.filter((r) => roles.includes(r))
  return validos.length > 0 ? validos[validos.length - 1] : 'USUARIO'
}

export function Shell() {
  const { t } = useLang()
  const meta = useQuery({ queryKey: ['meta'], queryFn: fetchMeta, staleTime: 60_000 })
  const [simulado, setSimulado] = useState<Role>(papelSimuladoInicial)

  const dev = meta.data?.devMode === true

  // Só fora do modo desenvolvimento o papel vem do backend (/api/me).
  const me = useQuery({
    queryKey: ['me'],
    queryFn: fetchMe,
    enabled: meta.data !== undefined && !dev,
    retry: false,
    staleTime: 60_000,
  })

  // ATENÇÃO: filtrar o menu por papel é só conveniência de interface. A autorização
  // de verdade é imposta pelo backend; nunca confiar neste filtro para proteger dados.
  // Em desenvolvimento (devMode) o papel exibido vem do seletor de simulação.
  // Em produção o papel efetivo vem só de /api/me; o localStorage não conta. Enquanto
  // /api/me carrega ou se falhar (403, 500, resposta estranha) vale o menor
  // privilégio (USUARIO), inclusive se /api/meta falhar.
  const papel: Role = dev ? simulado : papelDeMe(me.data)

  function trocarPapel(r: Role) {
    setSimulado(r)
    gravarPreferencia(CHAVE_PAPEL, r)
  }

  return (
    <RoleContext.Provider value={papel}>
      <div
        className={`shell${meta.data?.banner ? ' shell--dev' : ''}`}
        data-meta={meta.status}
      >
        <a className="pular" href="#conteudo">
          {t('app.pular')}
        </a>
        {meta.data?.banner && <DevBanner texto={meta.data.banner} />}
        <div className="frame" aria-hidden="true" />
        <div className="app">
          <Menu role={papel} aoTrocarPapel={dev ? trocarPapel : undefined} />
          <main id="conteudo" tabIndex={-1}>
            {meta.isError && <p className="notice">{t('shell.metaErro')}</p>}
            <AvisoDeAlertas />
            <Outlet />
          </main>
        </div>
        <SessaoExpirada devMode={dev} />
      </div>
    </RoleContext.Provider>
  )
}
