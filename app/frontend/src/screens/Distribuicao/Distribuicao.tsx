import { keepPreviousData, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { useLang } from '../../i18n/LangProvider'
import { CardPage } from '../../shared/CardPage'
import { Cartao } from '../../shared/Cartao'
import { baixarCsv } from '../../shared/csv'
import { dataCurta, inteiro, numero } from '../../shared/format'
import { GavetaDeTasks } from '../../shared/GavetaDeTasks'
import { temBloqueio } from '../../shared/useCard'
import '../../shared/tabela.css'
import { fetchDistribuicao, type DistribuicaoDto, type Sugestao } from './api'
import { DecisaoDeSugestao } from './DecisaoDeSugestao'
import './distribuicao.css'

type Gaveta = { cartaoId: string; segmento: string; titulo: string }

/** A faixa fixa do modo sombra: aparece em todo estado da tela, inclusive erro e sem permissão. */
export function FaixaModoSombra() {
  const { t } = useLang()
  return (
    <div className="aviso" role="note">
      <strong>{t('distribuicao.sombra')}</strong> {t('distribuicao.sombra.detalhe')}
    </div>
  )
}

/** Os fatores do score de cada candidato para uma task: ordem alfabética (vinda do backend), sem coluna de posição. */
function TabelaDeFatores({ s }: { s: Sugestao }) {
  const { t, lang } = useLang()
  const idTitulo = `h-fatores-${s.issueKey}`
  return (
    <div className="tabela-rolagem" tabIndex={0} role="region" aria-labelledby={idTitulo}>
      <table className="tabela" aria-labelledby={idTitulo}>
        <thead>
          <tr>
            <th scope="col">{t('distribuicao.col.pessoa')}</th>
            <th scope="col" className="num">{t('distribuicao.col.carga')}</th>
            <th scope="col" className="num">{t('distribuicao.col.wip')}</th>
            <th scope="col" className="num">{t('distribuicao.col.wipCheio')}</th>
            <th scope="col" className="num">{t('distribuicao.col.competencias')}</th>
            <th scope="col" className="num">{t('distribuicao.col.bonus')}</th>
            <th scope="col" className="num">{t('distribuicao.col.score')}</th>
          </tr>
        </thead>
        <tbody>
          {s.fatores.map((f) => (
            <tr key={f.pessoa} className={f.pessoa === s.sugerido ? 'linha-sugerida' : undefined}>
              <th scope="row">
                {f.pessoa}
                {f.pessoa === s.sugerido && <span> ✓ {t('distribuicao.sugerida')}</span>}
              </th>
              <td className="num">{numero(f.carga, lang, 2)}</td>
              <td className="num">{numero(f.wip, lang, 2)}</td>
              <td className="num">{numero(f.wipCheio, lang, 2)}</td>
              <td className="num">{numero(f.competencias, lang, 2)}</td>
              <td className="num">{f.bonus > 0 ? `−${numero(f.bonus, lang, 2)}` : numero(0, lang, 2)}</td>
              <td className="num">{numero(f.score, lang, 2)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

/**
 * Distribuição (DI-01 a DI-05), em MODO SOMBRA: sugere quem deve pegar cada task que espera dono e mostra o porquê de cada
 * candidato, em ordem alfabética e sem posição (nunca um ranking de pessoas). A decisão (aprovar, trocar com motivo, rejeitar)
 * é gravada só no painel; nada vai ao Jira.
 */
export function Distribuicao() {
  const { t, lang } = useLang()
  const qc = useQueryClient()
  const [gaveta, setGaveta] = useState<Gaveta | null>(null)
  const consulta = useQuery({ queryKey: ['distribuicao'], queryFn: fetchDistribuicao, placeholderData: keepPreviousData, retry: false })
  const dto: DistribuicaoDto | undefined = consulta.data
  const bloqueado = temBloqueio(dto?.avisos)
  const traco = '—'
  const abrir = (cartaoId: string, segmento: string, titulo: string) => setGaveta({ cartaoId, segmento, titulo })
  const comAbrir = (n: number, cartaoId: string, segmento: string, titulo: string) =>
    !bloqueado && n > 0 ? () => abrir(cartaoId, segmento, titulo) : undefined
  const recarregar = () => void Promise.all([qc.invalidateQueries({ queryKey: ['distribuicao'] }), qc.invalidateQueries({ queryKey: ['decisoes'] })])

  function exportar() {
    if (!dto) return
    baixarCsv('distribuicao.csv', [
      [`# ${t('nav.distribuicao')} (${dto.versaoRegras})`],
      [`# ${t('distribuicao.sombra')}`],
      [],
      [t('distribuicao.col.task'), t('distribuicao.col.resumo'), t('distribuicao.col.horas'), t('distribuicao.col.sugerido'), t('distribuicao.col.decisao')],
      ...dto.sugestoes.map((s) => [s.issueKey, s.resumo, String(s.horas), s.sugerido ?? t('distribuicao.semSugestao.curta'), s.decisao ? t(`decisao.${s.decisao}`) : '']),
    ])
  }

  return (
    <>
      <CardPage
        titulo={t('nav.distribuicao')}
        pergunta={t('distribuicao.pergunta')}
        filtros={<FaixaModoSombra />}
        consulta={consulta}
        avisos={dto?.avisos}
        aoExportar={exportar}
      >
        {dto && (
          <>
            <div role="group" aria-label={t('distribuicao.indicadores')} className="cardpage-kpis">
              <Cartao
                id="DI-01" titulo={t('distribuicao.kpi.aguardando')} valor={bloqueado ? traco : inteiro(dto.aguardando, lang)}
                aoAbrir={comAbrir(dto.aguardando, 'DI-01', 'pendentes', t('distribuicao.kpi.aguardando'))}
              />
              <Cartao
                id="DI-02" titulo={t('distribuicao.kpi.semDecisao')} valor={bloqueado ? traco : inteiro(dto.sugestoesPendentes, lang)}
                aoAbrir={comAbrir(dto.sugestoesPendentes, 'DI-02', 'semDecisao', t('distribuicao.kpi.semDecisao'))}
              />
              <Cartao
                id="DI-03" titulo={t('distribuicao.kpi.jain')}
                valor={bloqueado || dto.jainAntes === null || dto.jainDepois === null ? traco : `${numero(dto.jainAntes, lang, 2)} → ${numero(dto.jainDepois, lang, 2)}`}
                legenda={dto.jainAntes === null ? t('distribuicao.kpi.jain.indisponivel') : t('distribuicao.kpi.jain.nota')}
              />
            </div>

            {dto.sugestoes.length === 0 ? (
              <p className="vazio">{t('distribuicao.vazia')}</p>
            ) : (
              <>
                <p className="legenda-meta">{t('distribuicao.nota')}</p>
                {dto.sugestoes.map((s) => (
                  <Cartao
                    key={s.issueKey} id="DI-04" variante="lista" nivelDoTitulo={2} idDoTitulo={`h-fatores-${s.issueKey}`}
                    titulo={`${s.issueKey} · ${s.resumo}`}
                  >
                    <div className="sugestao">
                      <p className="sugestao-cabeca">
                        <span>{s.comGerente ? t('distribuicao.comGerente') : t('distribuicao.semDono')}</span>
                        <span>
                          {numero(s.horas, lang)} h{s.horasAssumidas ? ` (${t('pessoas.horasAssumidas')})` : ''}
                        </span>
                        {s.vence && <span>{t('distribuicao.vence').replace('{data}', dataCurta(s.vence, lang))}</span>}
                      </p>
                      <p className="sugestao-sugerido">
                        {s.sugerido ? `✓ ${t('distribuicao.sugeridoPara').replace('{pessoa}', s.sugerido)}` : `■ ${t('distribuicao.semSugestao')}`}
                      </p>
                      {s.fatores.length > 0 && <TabelaDeFatores s={s} />}
                      <DecisaoDeSugestao
                        tipo="DISTRIBUICAO" issueKey={s.issueKey} sugerido={s.sugerido} candidatos={dto.candidatos}
                        decisao={s.decisao} aoDecidir={recarregar}
                      />
                    </div>
                  </Cartao>
                ))}
              </>
            )}
            <p className="legenda-meta">{t('distribuicao.rodape').replace('{versao}', dto.versaoRegras)}</p>
          </>
        )}
      </CardPage>
      {gaveta && (
        <GavetaDeTasks
          cartaoId={gaveta.cartaoId} segmento={gaveta.segmento} titulo={gaveta.titulo} filtros={new URLSearchParams()}
          resumoDosFiltros="" aoFechar={() => setGaveta(null)}
        />
      )}
    </>
  )
}
