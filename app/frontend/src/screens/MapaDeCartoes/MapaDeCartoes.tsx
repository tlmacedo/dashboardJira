import { useMemo } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { ErroDaApi } from '../../api/client'
import { useLang } from '../../i18n/LangProvider'
import { GROUPS, itemPorRota, NAV_ITEMS, podeVer, type NavItem } from '../../nav/nav'
import { useRole } from '../../shell/RoleContext'
import { baixarCsv } from '../../shared/csv'
import { inteiro } from '../../shared/format'
import { useCatalogo, type CartaoCatalogo } from '../../shared/useCatalogo'
import './mapa.css'

const NATUREZAS: CartaoCatalogo['natureza'][] = ['ESTOQUE', 'FLUXO', 'PRAZO', 'PESSOAS', 'QUALIDADE']
const ROTA_DA_TELA = '/mapaCartoes'

/** Id de tela do backend (`/api/cards/<tela>`) que não é igual ao id do item do menu. */
const TELA_PARA_MENU: Record<string, string> = { 'tempo-em-status': 'tempo' }

/** Item do menu da tela onde o cartão vive; nulo se o id da tela não corresponde a nenhum item. */
function menuDaTela(tela: string): NavItem | undefined {
  const id = TELA_PARA_MENU[tela] ?? tela
  return NAV_ITEMS.find((i) => i.id === id)
}

/** Sem acento e sem diferenciar maiúsculas, para a busca por título. */
function normalizar(texto: string): string {
  return texto.normalize('NFD').replace(/\p{M}/gu, '').toLowerCase()
}

/**
 * Mapa de cartões (Governança): gerado do catálogo do backend, então nunca fica desatualizado. Mostra só o que o papel
 * vê (o filtro é do servidor) e não faz nenhuma outra chamada além de `/api/cartoes/catalogo`.
 */
export function MapaDeCartoes() {
  const { t, lang } = useLang()
  const role = useRole()
  const [sp, setSp] = useSearchParams()
  const item = itemPorRota(ROTA_DA_TELA)
  const permitido = item === undefined || podeVer(item, role)
  const { consulta, cartoes } = useCatalogo(permitido)

  const grupo = sp.get('g') ?? ''
  const natureza = sp.get('n') ?? ''
  const busca = sp.get('q') ?? ''
  const status = consulta.error instanceof ErroDaApi ? consulta.error.status : null

  function mudar(chave: 'g' | 'n' | 'q', valor: string) {
    const novo = new URLSearchParams(sp)
    if (valor) novo.set(chave, valor)
    else novo.delete(chave)
    setSp(novo, { replace: true })
  }

  const visiveis = useMemo(() => {
    const termo = normalizar(busca.trim())
    return (cartoes ?? []).filter(
      (c) =>
        (!grupo || c.grupo === grupo) &&
        (!natureza || c.natureza === natureza) &&
        (!termo || normalizar(t(c.tituloChave)).includes(termo)),
    )
  }, [cartoes, grupo, natureza, busca, t])

  // Grupos na ordem do menu; dentro de cada um, na ordem do registro (por id).
  const porGrupo = GROUPS.map((g) => ({
    id: g,
    cartoes: visiveis.filter((c) => c.grupo === g).sort((a, b) => a.id.localeCompare(b.id)),
  })).filter((g) => g.cartoes.length > 0)

  function textoDaTela(c: CartaoCatalogo): string {
    const menu = menuDaTela(c.tela)
    return menu ? t(menu.labelKey) : c.tela
  }

  function resumoDosFiltros(): string {
    const partes: string[] = []
    if (grupo) partes.push(`${t('mapa.grupo')}: ${t(`grupo.${grupo}`)}`)
    if (natureza) partes.push(`${t('mapa.natureza')}: ${t(`catalogo.natureza.${natureza}`)}`)
    if (busca.trim()) partes.push(`${t('mapa.busca')}: ${busca.trim()}`)
    return partes.join(' | ') || t('mapa.nenhum')
  }

  function exportar() {
    baixarCsv('mapa-de-cartoes.csv', [
      [`# ${t('nav.mapaCartoes')}`],
      [`# ${t('mapa.csv.filtros')}: ${resumoDosFiltros()}`],
      [
        t('mapa.col.id'),
        t('mapa.col.titulo'),
        t('mapa.grupo'),
        t('mapa.natureza'),
        t('cartao.ajuda.oQueMostra'),
        t('cartao.ajuda.comoCalcula'),
        t('cartao.ajuda.fonte'),
        t('mapa.tela'),
        t('cartao.ajuda.visivelPara'),
      ],
      ...porGrupo.flatMap((g) =>
        g.cartoes.map((c) => [
          c.id,
          t(c.tituloChave),
          t(`grupo.${c.grupo}`),
          t(`catalogo.natureza.${c.natureza}`),
          t(c.oQueMostraChave),
          t(c.comoCalculaChave),
          c.fontes.map((f) => t(`catalogo.fonte.${f}`)).join(', '),
          textoDaTela(c),
          c.papeis.map((p) => t(`role.${p}`)).join(', '),
        ]),
      ),
    ])
  }

  return (
    <div className="mapa">
      <div className="top">
        <div>
          <h1>{t('nav.mapaCartoes')}</h1>
          <p className="sub">{t('mapa.sub')}</p>
        </div>
        <div className="top-acoes">
          <button type="button" className="btn" onClick={exportar} disabled={visiveis.length === 0}>
            {t('mapa.csv')}
          </button>
        </div>
      </div>

      {!permitido && <p className="vazio">🔒 {t('shell.semPermissao')}</p>}

      {permitido && (
        <>
          <form className="mapa-filtros" onSubmit={(e) => e.preventDefault()} aria-label={t('mapa.filtros')}>
            <label className="campo">
              <span>{t('mapa.grupo')}</span>
              <select value={grupo} onChange={(e) => mudar('g', e.target.value)}>
                <option value="">{t('mapa.todos')}</option>
                {GROUPS.filter((g) => (cartoes ?? []).some((c) => c.grupo === g)).map((g) => (
                  <option key={g} value={g}>
                    {t(`grupo.${g}`)}
                  </option>
                ))}
              </select>
            </label>
            <label className="campo">
              <span>{t('mapa.natureza')}</span>
              <select value={natureza} onChange={(e) => mudar('n', e.target.value)}>
                <option value="">{t('mapa.todos')}</option>
                {NATUREZAS.map((n) => (
                  <option key={n} value={n}>
                    {t(`catalogo.natureza.${n}`)}
                  </option>
                ))}
              </select>
            </label>
            <label className="campo">
              <span>{t('mapa.busca')}</span>
              <input type="search" maxLength={100} value={busca} onChange={(e) => mudar('q', e.target.value)} />
            </label>
          </form>

          {consulta.isPending && <p className="vazio" aria-busy="true">{t('app.carregando')}</p>}

          {consulta.isError && (
            <div className="estado estado--erro" role="alert">
              {status === 403 ? (
                <p>{t('mapa.semPermissao')}</p>
              ) : (
                <>
                  <p>
                    {t('mapa.erro')}
                    {status ? ` (HTTP ${status})` : ''}
                  </p>
                  <button type="button" className="btn" onClick={() => consulta.refetch()}>
                    {t('mapa.tentarDeNovo')}
                  </button>
                </>
              )}
            </div>
          )}

          {consulta.isSuccess && (
            <>
              <p className="legenda" aria-live="polite">
                {t('mapa.total').replace('{n}', inteiro(visiveis.length, lang))}
              </p>
              {visiveis.length === 0 && <p className="vazio">{t('mapa.vazio')}</p>}
              {porGrupo.map((g) => (
                <section key={g.id} className="mapa-grupo" aria-labelledby={`mapa-grupo-${g.id}`}>
                  <h2 id={`mapa-grupo-${g.id}`}>{t(`grupo.${g.id}`)}</h2>
                  <ul className="mapa-lista">
                    {g.cartoes.map((c) => {
                      const menu = menuDaTela(c.tela)
                      return (
                        <li key={c.id} className="mapa-cartao">
                          <div className="mapa-topo">
                            <span className="mapa-id mono">{c.id}</span>
                            <h3>{t(c.tituloChave)}</h3>
                            <span className="mapa-natureza">{t(`catalogo.natureza.${c.natureza}`)}</span>
                          </div>
                          <dl className="mapa-dl">
                            <dt>{t('cartao.ajuda.oQueMostra')}</dt>
                            <dd>{t(c.oQueMostraChave)}</dd>
                            <dt>{t('cartao.ajuda.comoCalcula')}</dt>
                            <dd>{t(c.comoCalculaChave)}</dd>
                            <dt>{t('cartao.ajuda.fonte')}</dt>
                            <dd>{c.fontes.map((f) => t(`catalogo.fonte.${f}`)).join(', ')}</dd>
                            <dt>{t('mapa.tela')}</dt>
                            <dd>{menu ? <Link to={menu.route}>{t(menu.labelKey)}</Link> : c.tela}</dd>
                            <dt>{t('cartao.ajuda.visivelPara')}</dt>
                            <dd>{c.papeis.map((p) => t(`role.${p}`)).join(', ')}</dd>
                          </dl>
                        </li>
                      )
                    })}
                  </ul>
                </section>
              ))}
            </>
          )}
        </>
      )}
    </div>
  )
}
