import { useState } from 'react'
import { useLang } from '../../i18n/LangProvider'
import { Cartao } from '../../shared/Cartao'
import { CardPage } from '../../shared/CardPage'
import { baixarCsv } from '../../shared/csv'
import { Filtros, resumoDosFiltros, useFiltrosDaUrl } from '../../shared/Filtros'
import { dataCurta, inteiro, numero } from '../../shared/format'
import { GavetaDeTasks } from '../../shared/GavetaDeTasks'
import { TabelaOrdenavel, type Coluna } from '../../shared/TabelaOrdenavel'
import { temBloqueio, useCard } from '../../shared/useCard'
import type { LinhaVencimento, VencimentosDto } from './api'
import '../../shared/tabela.css'
import '../../shared/pessoas.css'

type Gaveta = { cartaoId: string; segmento: string; titulo: string }

const SIMBOLO: Record<string, string> = { ok: '✓', atencao: '●', problema: '▲' }

/**
 * Vencimentos da semana (VE-01 e VE-02): o que vence em 7 ou 14 dias, com o término previsto pela capacidade do responsável
 * e a folga real em dias úteis. Folga negativa (ou sem previsão) é "▲ risco de atrasar", folga zero é "● sem folga" e o
 * resto "✓". Não calcula "no prazo": isso é da tela Metas e prazos.
 */
export function Vencimentos() {
  const { t, lang } = useLang()
  const [sp, aplicar] = useFiltrosDaUrl()
  const consulta = useCard<VencimentosDto>('vencimentos', sp)
  const dto = consulta.data
  const [gaveta, setGaveta] = useState<Gaveta | null>(null)
  const bloqueado = temBloqueio(dto?.avisos)
  const traco = '—'
  const resumo = resumoDosFiltros(sp, t, (iso) => dataCurta(iso, lang))
  const dias = sp.get('dias') === '14' ? 14 : 7
  const abrir = (cartaoId: string, segmento: string, titulo: string) => setGaveta({ cartaoId, segmento, titulo })
  const comAbrir = (n: number, cartaoId: string, segmento: string, titulo: string) =>
    !bloqueado && n > 0 ? () => abrir(cartaoId, segmento, titulo) : undefined

  function escolher(n: 7 | 14) {
    const novo = new URLSearchParams(sp)
    if (n === 7) novo.delete('dias')
    else novo.set('dias', '14')
    aplicar(novo)
  }

  function exportar() {
    if (!dto) return
    baixarCsv('vencimentos.csv', [
      [`# ${t('nav.vencimentos')} (${dto.dias})`],
      [`# ${t('gaveta.csv.filtros')}: ${resumo}`],
      [],
      [t('abertas.col.task'), t('abertas.col.resumo'), t('abertas.col.responsavel'), t('vencimentos.col.vence'), t('vencimentos.col.termino'), t('vencimentos.col.folga'), t('vencimentos.col.estado')],
      ...dto.linhas.map((l) => [l.key, l.resumo, l.pessoa ?? '', l.vence, l.termino ?? '', l.folgaDiasUteis === null ? '' : String(l.folgaDiasUteis), t(`vencimentos.estado.${l.estado}`)]),
    ])
  }

  const colunas: Coluna<LinhaVencimento>[] = [
    { id: 'key', cabecalho: t('abertas.col.task'), valor: (l) => l.key, render: (l) => (
      <>
        <span className="mono">{l.key}</span>
        <span className="resumo-task"> {l.resumo}</span>
      </>
    ) },
    { id: 'pessoa', cabecalho: t('abertas.col.responsavel'), valor: (l) => l.pessoa },
    { id: 'vence', cabecalho: t('vencimentos.col.vence'), valor: (l) => l.vence, render: (l) => dataCurta(l.vence, lang) },
    {
      id: 'termino', cabecalho: t('vencimentos.col.termino'), valor: (l) => l.termino,
      render: (l) => (l.termino ? dataCurta(l.termino, lang) : t('vencimentos.semTermino')),
    },
    {
      id: 'folga', cabecalho: t('vencimentos.col.folga'), valor: (l) => l.folgaDiasUteis, numerica: true,
      render: (l) => (l.folgaDiasUteis === null ? traco : numero(l.folgaDiasUteis, lang, 0)),
    },
    {
      id: 'estado', cabecalho: t('vencimentos.col.estado'), valor: (l) => l.estado,
      render: (l) => (
        <span className={`ocupacao ocupacao--${l.estado}`}>
          <span aria-hidden="true">{SIMBOLO[l.estado] ?? '■'}</span> {t(`vencimentos.estado.${l.estado}`)}
        </span>
      ),
    },
  ]

  return (
    <>
      <CardPage
        titulo={t('nav.vencimentos')}
        pergunta={t('vencimentos.pergunta')}
        filtros={
          <Filtros
            key={sp.toString()} campos={['squad', 'pessoa']} valor={sp} aoAplicar={aplicar}
            extras={
              <div role="group" aria-label={t('vencimentos.periodo')} className="seg seg--claro">
                {([7, 14] as const).map((n) => (
                  <button key={n} type="button" aria-pressed={dias === n} onClick={() => escolher(n)}>
                    {t('vencimentos.nDias').replace('{n}', String(n))}
                  </button>
                ))}
              </div>
            }
          />
        }
        consulta={consulta}
        avisos={dto?.avisos}
        aoExportar={exportar}
      >
        {dto && (
          <>
            <div role="group" aria-label={t('vencimentos.indicadores')} className="cardpage-kpis">
              <Cartao
                id="VE-01" titulo={t('vencimentos.kpi.vencem').replace('{n}', String(dto.dias))} valor={bloqueado ? traco : inteiro(dto.vencem, lang)}
                aoAbrir={comAbrir(dto.vencem, 'VE-01', 'vencem', t('vencimentos.kpi.vencem').replace('{n}', String(dto.dias)))}
              />
              <Cartao
                id="VE-02" titulo={t('vencimentos.kpi.risco')} valor={bloqueado ? traco : inteiro(dto.riscoDeAtrasar, lang)}
                simboloDoValor={!bloqueado && dto.riscoDeAtrasar > 0 ? '▲' : undefined}
                legenda={t('vencimentos.kpi.risco.dica')}
                aoAbrir={comAbrir(dto.riscoDeAtrasar, 'VE-02', 'riscoDeAtrasar', t('vencimentos.kpi.risco'))}
              />
            </div>
            <Cartao id="VE-01" variante="lista" nivelDoTitulo={2} idDoTitulo="h-ve-lista" titulo={t('vencimentos.lista')}>
              {dto.linhas.length === 0 ? (
                <p className="vazio">{t('vencimentos.vazia')}</p>
              ) : (
                <TabelaOrdenavel
                  rotulo={t('vencimentos.lista')} colunas={colunas} linhas={bloqueado ? [] : dto.linhas} chave={(l) => l.key}
                  inicial={{ id: 'vence' }}
                />
              )}
              <p className="legenda-meta">{t('vencimentos.nota')}</p>
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
