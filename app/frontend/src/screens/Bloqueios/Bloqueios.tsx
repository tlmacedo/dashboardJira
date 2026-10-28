import { useState } from 'react'
import { useLang } from '../../i18n/LangProvider'
import { Cartao } from '../../shared/Cartao'
import { CardPage } from '../../shared/CardPage'
import { baixarCsv } from '../../shared/csv'
import { Filtros, resumoDosFiltros, useFiltrosDaUrl } from '../../shared/Filtros'
import { dataCurta, inteiro } from '../../shared/format'
import { GavetaDeTasks } from '../../shared/GavetaDeTasks'
import { temBloqueio, useCard } from '../../shared/useCard'
import type { Bloqueadora, BloqueiosDto, LinhaDeBloqueio } from './api'
import '../../shared/tabela.css'

type Gaveta = { cartaoId: string; segmento: string; titulo: string }

/**
 * Bloqueios entre tasks (BQ-01 a BQ-05): as tasks abertas que dependem de outra ainda aberta, com a bloqueadora, o vencimento
 * dela e o sinal quando ela vence DEPOIS da bloqueada. Bloqueadora de pessoa fora da visão aparece como "task de outro
 * squad", sem chave nem nome; ponta fora da base vira "verificar no Jira". A lista segue a ordem da API (vencimento da
 * bloqueada): sem ordenação por bloqueadora e sem ranking.
 */
export function Bloqueios() {
  const { t, lang } = useLang()
  const [sp, aplicar] = useFiltrosDaUrl()
  const consulta = useCard<BloqueiosDto>('bloqueios', sp)
  const dto = consulta.data
  const [gaveta, setGaveta] = useState<Gaveta | null>(null)
  const bloqueado = temBloqueio(dto?.avisos)
  const traco = '—'
  const resumo = resumoDosFiltros(sp, t, (iso) => dataCurta(iso, lang))
  const num = (n: number) => (bloqueado ? traco : inteiro(n, lang))
  const data = (iso: string | null) => (iso ? dataCurta(iso, lang) : traco)
  const abrirCom = (cartaoId: string, segmento: string, titulo: string, n: number) =>
    !bloqueado && n > 0 ? () => setGaveta({ cartaoId, segmento, titulo }) : undefined

  function quem(b: Bloqueadora) {
    if (b.estado === 'OUTRO_SQUAD') return t('bloqueios.outroSquad')
    if (b.estado === 'FORA_DA_BASE') return b.key ? t('bloqueios.foraDaBase').replace('{key}', b.key) : t('bloqueios.foraDaBaseSemChave')
    return <span className="mono">{b.key}</span>
  }

  /** Símbolo + texto do sinal da linha (nunca só cor). */
  function sinal(b: Bloqueadora): { simbolo: string; texto: string } | null {
    if (b.estado === 'FORA_DA_BASE') return { simbolo: '■', texto: t('bloqueios.sinal.verificar') }
    if (b.conflito) return { simbolo: '▲', texto: t('bloqueios.sinal.conflito') }
    if (b.semVencimento) return { simbolo: '●', texto: t('bloqueios.sinal.semVencimento') }
    return null
  }

  function exportar() {
    if (!dto) return
    baixarCsv('bloqueios.csv', [
      [`# ${t('nav.bloqueios')}`],
      [`# ${t('gaveta.csv.filtros')}: ${resumo}`],
      [],
      [t('bloqueios.col.bloqueada'), t('abertas.col.resumo'), t('abertas.col.status'), t('bloqueios.col.venceBloqueada'), t('bloqueios.col.bloqueadora'), t('bloqueios.col.venceBloqueadora'), t('bloqueios.col.sinal')],
      ...dto.linhas.map((l: LinhaDeBloqueio) => [
        l.bloqueada, l.resumo, l.status, l.vence ?? '',
        l.bloqueadora.estado === 'OUTRO_SQUAD' ? t('bloqueios.outroSquad') : (l.bloqueadora.key ?? ''),
        l.bloqueadora.vence ?? '', sinal(l.bloqueadora)?.texto ?? '',
      ]),
    ])
  }

  const colunas = ['bloqueada', 'bloqueadora', 'venceBloqueada', 'venceBloqueadora', 'sinal']

  return (
    <>
      <CardPage
        titulo={t('nav.bloqueios')}
        pergunta={t('bloqueios.pergunta')}
        filtros={<Filtros key={sp.toString()} campos={['squad', 'pessoa', 'tipo', 'prioridade', 'os']} valor={sp} aoAplicar={aplicar} />}
        consulta={consulta}
        avisos={dto?.avisos}
        aoExportar={exportar}
      >
        {dto && (
          <>
            <div role="group" aria-label={t('bloqueios.indicadores')} className="cardpage-kpis">
              <Cartao
                id="BQ-01" titulo={t('bloqueios.kpi.bloqueadas')} valor={num(dto.bloqueadas)}
                legenda={t('bloqueios.tipoDeLink').replace('{tipo}', dto.tipoDeLink)}
                aoAbrir={abrirCom('BQ-01', 'bloqueadas', t('bloqueios.kpi.bloqueadas'), dto.bloqueadas)}
              />
              <Cartao
                id="BQ-02" titulo={t('bloqueios.kpi.bloqueadoras')} valor={num(dto.bloqueadoras)}
                legenda={dto.bloqueadorasForaDaVisao > 0 ? t('bloqueios.foraDaVisao').replace('{n}', String(dto.bloqueadorasForaDaVisao)) : undefined}
                aoAbrir={abrirCom('BQ-02', 'bloqueadoras', t('bloqueios.kpi.bloqueadoras'), dto.bloqueadoras)}
              />
              <Cartao
                id="BQ-03" titulo={t('bloqueios.kpi.conflito')} valor={num(dto.prazoEmConflito)}
                estado={bloqueado ? undefined : dto.prazoEmConflito > 0
                  ? { tipo: 'problema', texto: t('bloqueios.conflitoTexto').replace('{n}', String(dto.prazoEmConflito)) }
                  : { tipo: 'ok', texto: t('bloqueios.semConflito') }}
                aoAbrir={abrirCom('BQ-03', 'prazoEmConflito', t('bloqueios.kpi.conflito'), dto.prazoEmConflito)}
              />
              <Cartao
                id="BQ-04" titulo={t('bloqueios.kpi.semCausa')} valor={num(dto.semCausa)}
                estado={bloqueado ? undefined : dto.semCausa > 0
                  ? { tipo: 'atencao', texto: t('bloqueios.semCausaTexto').replace('{n}', String(dto.semCausa)) }
                  : { tipo: 'ok', texto: t('bloqueios.todasComCausa') }}
                aoAbrir={abrirCom('BQ-04', 'semCausa', t('bloqueios.kpi.semCausa'), dto.semCausa)}
              />
              <Cartao
                id="BQ-05" titulo={t('bloqueios.kpi.verificar')} valor={num(dto.verificarNoJira)}
                estado={bloqueado ? undefined : dto.verificarNoJira > 0
                  ? { tipo: 'atencao', texto: t('bloqueios.verificarTexto').replace('{n}', String(dto.verificarNoJira)) }
                  : { tipo: 'ok', texto: t('bloqueios.nenhumaForaDaBase') }}
                aoAbrir={abrirCom('BQ-05', 'verificarNoJira', t('bloqueios.kpi.verificar'), dto.verificarNoJira)}
              />
            </div>

            <Cartao id="BQ-lista" variante="lista" nivelDoTitulo={2} idDoTitulo="h-bq-lista" titulo={t('bloqueios.lista')}>
              <p className="legenda-meta">{t('bloqueios.nota')}</p>
              {dto.linhas.length === 0 ? (
                <p className="vazio">{t('bloqueios.vazio')}</p>
              ) : (
                <div className="tabela-rolagem" tabIndex={0} role="region" aria-labelledby="h-bq-lista">
                  <table className="tabela" aria-label={t('bloqueios.tabela')}>
                    <thead>
                      <tr>
                        {colunas.map((c) => (
                          <th key={c} scope="col">{t(`bloqueios.col.${c}`)}</th>
                        ))}
                      </tr>
                    </thead>
                    <tbody>
                      {dto.linhas.map((l, i) => {
                        const s = sinal(l.bloqueadora)
                        return (
                          <tr key={`${i}:${l.bloqueada}:${l.bloqueadora.key ?? l.bloqueadora.estado}`}>
                            <td>
                              <span className="mono">{l.bloqueada}</span>
                              <span className="resumo-task"> {l.resumo}</span>
                            </td>
                            <td>{quem(l.bloqueadora)}</td>
                            <td>{bloqueado ? traco : data(l.vence)}</td>
                            <td>{bloqueado ? traco : data(l.bloqueadora.vence)}</td>
                            <td>{s ? <span><span aria-hidden="true">{s.simbolo}</span> {s.texto}</span> : traco}</td>
                          </tr>
                        )
                      })}
                    </tbody>
                  </table>
                </div>
              )}
            </Cartao>
          </>
        )}
      </CardPage>
      {gaveta && (
        <GavetaDeTasks
          cartaoId={gaveta.cartaoId} segmento={gaveta.segmento} titulo={gaveta.titulo} filtros={sp}
          resumoDosFiltros={resumo} aoFechar={() => setGaveta(null)}
        />
      )}
    </>
  )
}
