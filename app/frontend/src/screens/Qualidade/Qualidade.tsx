import { useState } from 'react'
import { useLang } from '../../i18n/LangProvider'
import { Cartao } from '../../shared/Cartao'
import { CardPage } from '../../shared/CardPage'
import { baixarCsv } from '../../shared/csv'
import { Filtros, resumoDosFiltros, useFiltrosDaUrl } from '../../shared/Filtros'
import { dataCurta, inteiro, percentual } from '../../shared/format'
import { GavetaDeTasks } from '../../shared/GavetaDeTasks'
import { temBloqueio, useCard } from '../../shared/useCard'
import '../../shared/tabela.css'
import { GRUPOS_DE_VERIFICACAO, type LinhaQualidade, type QualidadeDto } from './api'

type Gaveta = { cartaoId: string; segmento: string; titulo: string }

/** Símbolo e texto de cada estado: nunca só cor (`✓` dentro do esperado, `●` atenção, `▲` problema, `■` informativo). */
const SIMBOLO: Record<string, string> = { ok: '✓', atencao: '●', problema: '▲', neutro: '■', indisponivel: '—' }

/**
 * Qualidade dos dados (QD-01 a QD-05): o que está faltando ou desencontrado nos dados que chegam do Jira. Cada linha é "n de um
 * universo" com o estado em símbolo e texto (verde até 10%, laranja até 30%, vermelho acima) e abre as tasks que a formam. Só
 * leitura: status, campos e labels desconhecidos são nomes para o gestor corrigir nos YAML. Coordenação e gerente.
 */
export function Qualidade() {
  const { t, lang } = useLang()
  const [sp, aplicar] = useFiltrosDaUrl()
  const consulta = useCard<QualidadeDto>('qualidade', sp)
  const dto = consulta.data
  const [gaveta, setGaveta] = useState<Gaveta | null>(null)
  const bloqueado = temBloqueio(dto?.avisos)
  const resumo = resumoDosFiltros(sp, t, (iso) => dataCurta(iso, lang))
  const porVerificacao = new Map((dto?.linhas ?? []).map((l) => [l.verificacao, l]))

  function exportar() {
    if (!dto) return
    baixarCsv('qualidade.csv', [
      [`# ${t('nav.qualidade')} (${dto.versaoRegras})`],
      [`# ${t('gaveta.csv.filtros')}: ${resumo}`],
      [],
      [t('qualidade.col.verificacao'), t('qualidade.col.n'), t('qualidade.col.universo'), t('qualidade.col.percentual'), t('qualidade.col.estado'), t('qualidade.col.nomes')],
      ...dto.linhas.map((l) => [
        t(`qualidade.verif.${l.verificacao}`), String(l.n), String(l.universo), l.fracao === null ? '' : percentual(l.fracao, lang),
        t(`qualidade.estado.${l.estado}`), l.nomes.join(', '),
      ]),
    ])
  }

  function numero(l: LinhaQualidade, cartao: string, temGaveta: boolean) {
    if (bloqueado || l.estado === 'indisponivel') return '—'
    const texto = l.universo > 0 ? `${inteiro(l.n, lang)} / ${inteiro(l.universo, lang)}` : inteiro(l.n, lang)
    if (!temGaveta || l.n === 0) return texto
    const nome = t(`qualidade.verif.${l.verificacao}`)
    return (
      <button type="button" className="contagem-botao" aria-label={`${nome}: ${l.n}`} onClick={() => setGaveta({ cartaoId: cartao, segmento: l.verificacao, titulo: nome })}>
        {texto}
      </button>
    )
  }

  return (
    <>
      <CardPage
        titulo={t('nav.qualidade')}
        pergunta={t('qualidade.pergunta')}
        filtros={<Filtros key={sp.toString()} campos={['squad']} valor={sp} aoAplicar={aplicar} />}
        consulta={consulta}
        avisos={dto?.avisos}
        aoExportar={exportar}
      >
        {dto && (
          <>
            <p className="legenda-meta">{t('qualidade.regra').replace('{dias}', String(dto.janelaDias))}</p>
            {GRUPOS_DE_VERIFICACAO.map((g) => {
              const linhas = g.verificacoes.map((v) => porVerificacao.get(v)).filter((l): l is LinhaQualidade => l !== undefined)
              const idTitulo = `h-${g.cartao}`
              const temGaveta = g.cartao !== 'QD-05'
              return (
                <Cartao key={g.cartao} id={g.cartao} variante="tabela" nivelDoTitulo={2} idDoTitulo={idTitulo} titulo={t(g.titulo)}>
                  <div className="tabela-rolagem" tabIndex={0} role="region" aria-labelledby={idTitulo}>
                    <table className="tabela" aria-labelledby={idTitulo}>
                      <thead>
                        <tr>
                          <th scope="col">{t('qualidade.col.verificacao')}</th>
                          <th scope="col" className="num">{t('qualidade.col.n')}</th>
                          <th scope="col" className="num">{t('qualidade.col.percentual')}</th>
                          <th scope="col">{t('qualidade.col.estado')}</th>
                        </tr>
                      </thead>
                      <tbody>
                        {linhas.map((l) => (
                          <tr key={l.verificacao}>
                            <th scope="row">
                              {t(`qualidade.verif.${l.verificacao}`)}
                              {l.nomes.length > 0 && l.verificacao !== 'LABEL_SEM_CLASSIFICACAO' && (
                                <span className="legenda-meta"> {l.nomes.join(', ')}</span>
                              )}
                            </th>
                            <td className="num">{numero(l, g.cartao, temGaveta)}</td>
                            <td className="num">{l.fracao === null || bloqueado ? '—' : percentual(l.fracao, lang)}</td>
                            <td>{SIMBOLO[l.estado] ?? '—'} {t(`qualidade.estado.${l.estado}`)}</td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                  {g.cartao === 'QD-03' && dto.labels.length > 0 && (
                    <>
                      <h3 className="legenda-meta">{t('qualidade.labels.titulo')}</h3>
                      <p className="legenda-meta">{t('qualidade.labels.dica')}</p>
                      <ul>
                        {dto.labels.map((lb) => (
                          <li key={lb.label}>
                            <button
                              type="button" className="contagem-botao" aria-label={`${lb.label}: ${lb.n}`}
                              onClick={() => setGaveta({ cartaoId: 'QD-03', segmento: `label:${lb.label}`, titulo: lb.label })}
                            >
                              {lb.label}
                            </button>{' '}
                            <span className="legenda-meta">({inteiro(lb.n, lang)})</span>
                          </li>
                        ))}
                      </ul>
                    </>
                  )}
                </Cartao>
              )
            })}
            <p className="legenda-meta">{t('qualidade.rodape').replace('{versao}', dto.versaoRegras)}</p>
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

