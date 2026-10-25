import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { Outlet } from 'react-router-dom'
import { fetchMeta } from '../api/client'
import { useLang } from '../i18n/LangProvider'
import { ROLES, type Role } from '../nav/nav'
import { gravarPreferencia, lerPreferencia } from '../storage'
import { DevBanner } from './DevBanner'
import { Menu } from './Menu'
import { RoleContext } from './RoleContext'

const CHAVE_PAPEL = 'painel.papelSimulado'

function papelSimuladoInicial(): Role {
  const salvo = lerPreferencia(CHAVE_PAPEL)
  return ROLES.find((r) => r === salvo) ?? 'GERENTE' // padrão em desenvolvimento
}

export function Shell() {
  const { t } = useLang()
  const meta = useQuery({ queryKey: ['meta'], queryFn: fetchMeta, staleTime: 60_000 })
  const [simulado, setSimulado] = useState<Role>(papelSimuladoInicial)

  const dev = meta.data?.devMode === true

  // Em desenvolvimento (devMode) o papel exibido vem do seletor de simulação.
  // Em produção o papel virá do backend (SSO/OIDC, endpoint de sessão), ainda não
  // implementado nesta tarefa: até lá vale o menor privilégio (USUARIO), inclusive
  // enquanto /api/meta carrega ou se falhar.
  const papel: Role = dev ? simulado : 'USUARIO'

  function trocarPapel(r: Role) {
    setSimulado(r)
    gravarPreferencia(CHAVE_PAPEL, r)
  }

  return (
    <RoleContext.Provider value={papel}>
      <div className={`shell${meta.data?.banner ? ' shell--dev' : ''}`}>
        <a className="pular" href="#conteudo">
          {t('app.pular')}
        </a>
        {meta.data?.banner && <DevBanner texto={meta.data.banner} />}
        <div className="frame" aria-hidden="true" />
        <div className="app">
          <Menu role={papel} aoTrocarPapel={dev ? trocarPapel : undefined} />
          <main id="conteudo" tabIndex={-1}>
            {meta.isError && <p className="notice">{t('shell.metaErro')}</p>}
            <Outlet />
          </main>
        </div>
      </div>
    </RoleContext.Provider>
  )
}
