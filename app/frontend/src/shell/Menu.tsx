import { useState } from 'react'
import { NavLink } from 'react-router-dom'
import { useLang } from '../i18n/LangProvider'
import { ROLES, visibleNav, type Role } from '../nav/nav'
import { TEMAS, useTheme } from '../theme/ThemeProvider'
import { Marca } from './Marca'

type Opcao<T extends string> = { valor: T; rotulo: string }

/** Botões segmentados com aria-pressed: o estado ativo tem negrito e fundo, não só cor. */
function Segmentado<T extends string>(props: {
  rotulo: string
  opcoes: Opcao<T>[]
  atual: T
  aoEscolher: (v: T) => void
}) {
  return (
    <div role="group" aria-label={props.rotulo} className="seg-bloco">
      <span className="seg-rotulo">{props.rotulo}</span>
      <div className="seg">
        {props.opcoes.map((o) => (
          <button
            key={o.valor}
            type="button"
            aria-pressed={o.valor === props.atual}
            onClick={() => props.aoEscolher(o.valor)}
          >
            {o.rotulo}
          </button>
        ))}
      </div>
    </div>
  )
}

export function Menu(props: {
  role: Role
  /** Módulos fechados pela trava do DPO (`/api/me`): o item continua no menu, marcado com ⏸ e o texto. */
  modulosBloqueados?: string[]
  /** Só em desenvolvimento: permite simular outro papel. */
  aoTrocarPapel?: (r: Role) => void
  /** Só em produção (com login): encerra a sessão. */
  aoSair?: () => void
}) {
  const { lang, setLang, t } = useLang()
  const { theme, setTheme } = useTheme()
  const [aberto, setAberto] = useState(false)
  const grupos = visibleNav([props.role])

  return (
    <aside className={`menu${aberto ? ' menu--aberto' : ''}`}>
      <Marca />
      <button
        type="button"
        className="btn burger"
        aria-label={t('shell.menu')}
        aria-expanded={aberto}
        aria-controls="menu-principal"
        onClick={() => setAberto((a) => !a)}
      >
        ☰
      </button>

      <nav id="menu-principal" aria-label={t('shell.menuPrincipal')} className="menu-nav">
        {grupos.map((g) => (
          <div key={g.id} role="group" aria-labelledby={`grp-${g.id}`}>
            <div className="grp" id={`grp-${g.id}`}>
              {t(g.labelKey)}
            </div>
            {g.items.map((i) => {
              const bloqueado = i.modulo !== undefined && (props.modulosBloqueados ?? []).includes(i.modulo)
              return (
                <NavLink key={i.id} to={i.route} onClick={() => setAberto(false)}>
                  {t(i.labelKey)}
                  {bloqueado && (
                    // O símbolo não é o único sinal: o texto diz que o módulo aguarda o aceite do DPO.
                    <span className="menu-dpo">
                      {' '}
                      <span aria-hidden="true">⏸</span> {t('dpo.menu')}
                    </span>
                  )}
                </NavLink>
              )
            })}
          </div>
        ))}
      </nav>

      <div className="foot">
        {props.aoTrocarPapel && (
          <Segmentado
            rotulo={t('shell.papel')}
            opcoes={ROLES.slice()
              .reverse()
              .map((r) => ({ valor: r, rotulo: t(`role.${r}`) }))}
            atual={props.role}
            aoEscolher={props.aoTrocarPapel}
          />
        )}
        <Segmentado
          rotulo={t('shell.tema')}
          opcoes={TEMAS.map((v) => ({ valor: v, rotulo: t(`theme.${v}`) }))}
          atual={theme}
          aoEscolher={setTheme}
        />
        <Segmentado
          rotulo={t('shell.idioma')}
          opcoes={[
            { valor: 'pt', rotulo: 'PT' },
            { valor: 'en', rotulo: 'EN' },
          ]}
          atual={lang}
          aoEscolher={setLang}
        />
        <div>{t('shell.fuso')}</div>
        {props.aoSair && (
          <button type="button" className="btn" onClick={props.aoSair}>
            {t('shell.sair')}
          </button>
        )}
      </div>
    </aside>
  )
}
