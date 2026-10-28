import { useLang } from '../../i18n/LangProvider'
import { Cartao } from '../../shared/Cartao'
import { CardPage } from '../../shared/CardPage'
import { baixarCsv } from '../../shared/csv'
import { Filtros, resumoDosFiltros, useFiltrosDaUrl } from '../../shared/Filtros'
import { dataCurta } from '../../shared/format'
import { Ocupacao } from '../../shared/Ocupacao'
import { temBloqueio, useCard } from '../../shared/useCard'
import type { TendenciaDto } from './api'
import '../../shared/tabela.css'
import '../../shared/pessoas.css'

const SIMBOLO_DO_PADRAO: Record<string, string> = { RECORRENTE: '▲', PONTUAL: '●', NORMAL: '✓' }
const CLASSE_DO_PADRAO: Record<string, string> = { RECORRENTE: 'problema', PONTUAL: 'atencao', NORMAL: 'ok' }

/**
 * Tendência de ocupação (TD-01), só liderança: 12 semanas por pessoa, reconstruídas do changelog com as horas de hoje (o aviso
 * fica sempre visível). O padrão aparece em texto com símbolo: ▲ recorrente, ● pontual, ✓ normal. Sem ranking.
 */
export function Tendencia() {
  const { t, lang } = useLang()
  const [sp, aplicar] = useFiltrosDaUrl()
  const consulta = useCard<TendenciaDto>('tendencia', sp)
  const dto = consulta.data
  const bloqueado = temBloqueio(dto?.avisos)
  const traco = '—'
  const resumo = resumoDosFiltros(sp, t, (iso) => dataCurta(iso, lang))

  function exportar() {
    if (!dto) return
    baixarCsv('tendencia.csv', [
      [`# ${t('nav.tendencia')}`],
      [`# ${t('gaveta.csv.filtros')}: ${resumo}`],
      [`# ${t('tendencia.nota')}`],
      [],
      [t('carga.col.pessoa'), ...dto.semanas, t('tendencia.col.padrao')],
      ...dto.linhas.map((l) => [
        l.pessoa,
        ...l.ocupacoes.map((o) => (o.semCapacidade ? t('pessoas.semCapacidade') : `${Math.round((o.fracao ?? 0) * 100)}%`)),
        t(`tendencia.padrao.${l.padrao}`),
      ]),
    ])
  }

  return (
    <CardPage
      titulo={t('nav.tendencia')}
      pergunta={t('tendencia.pergunta')}
      filtros={<Filtros key={sp.toString()} campos={['squad', 'pessoa']} valor={sp} aoAplicar={aplicar} />}
      consulta={consulta}
      avisos={dto?.avisos}
      aoExportar={exportar}
    >
      {dto && (
        <Cartao id="TD-01" variante="tabela" nivelDoTitulo={2} idDoTitulo="h-td-grade" titulo={t('tendencia.titulo')}>
          <p className="legenda-meta" role="note">{t('tendencia.nota')}</p>
          {dto.linhas.length === 0 ? (
            <p className="vazio">{t('carga.vazia')}</p>
          ) : (
            <div className="tabela-rolagem" tabIndex={0} role="region" aria-labelledby="h-td-grade">
              <table className="tabela grade-pessoas" aria-labelledby="h-td-grade">
                <thead>
                  <tr>
                    <th scope="col">{t('carga.col.pessoa')}</th>
                    {dto.semanas.map((s) => (
                      <th scope="col" key={s}>{dataCurta(s, lang).slice(0, 5)}</th>
                    ))}
                    <th scope="col">{t('tendencia.col.padrao')}</th>
                  </tr>
                </thead>
                <tbody>
                  {dto.linhas.map((l) => (
                    <tr key={l.pessoa}>
                      <th scope="row">{l.pessoa}</th>
                      {l.ocupacoes.map((o, i) => (
                        <td key={dto.semanas[i]}>{bloqueado ? traco : <Ocupacao o={o} compacta />}</td>
                      ))}
                      <td>
                        <span className={`ocupacao ocupacao--${CLASSE_DO_PADRAO[l.padrao] ?? 'ok'}`}>
                          <span aria-hidden="true">{SIMBOLO_DO_PADRAO[l.padrao] ?? '■'}</span> {t(`tendencia.padrao.${l.padrao}`)}
                        </span>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </Cartao>
      )}
    </CardPage>
  )
}
