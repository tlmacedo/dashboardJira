import { useState } from 'react'
import { useLang } from '../../i18n/LangProvider'
import { Cartao } from '../../shared/Cartao'
import { CardPage } from '../../shared/CardPage'
import { baixarCsv } from '../../shared/csv'
import { Filtros, resumoDosFiltros, useFiltrosDaUrl } from '../../shared/Filtros'
import { dataCurta, inteiro, numero } from '../../shared/format'
import { GavetaDeTasks } from '../../shared/GavetaDeTasks'
import { temBloqueio, useCard } from '../../shared/useCard'
import type { Celula, CruzamentosDto } from './api'
import './cruzamentos.css'
import '../../shared/tabela.css'

type Gaveta = { segmento: string; titulo: string }

const EIXOS_DE_LINHA = ['squad', 'categoria', 'status', 'tipo', 'prioridade', 'os', 'idade', 'vencimento', 'atraso', 'pessoa']
const SEM = '(sem)'
const CHAVE_DA_FAIXA_DE_IDADE: Record<string, string> = { '0-7': 'cruzamentos.idade.ate7', '8-14': 'cruzamentos.idade.ate14', '15-30': 'cruzamentos.idade.ate30', '>30': 'cruzamentos.idade.mais30' }

/**
 * Cruzamentos (CZ-01): tabela cruzada das tasks abertas sobre dois eixos de uma lista FECHADA, com a contagem ou a idade
 * P50. O estado dos eixos e da medida vai na URL (link compartilhável). Pessoa só como linha, em ordem alfabética e sem
 * total por pessoa; a ordem de cada eixo vem da API, nunca por contagem. A célula mais alta leva padrão hachurado além do
 * número. Cada célula e cada total abrem as tasks (segmento posicional).
 */
export function Cruzamentos() {
  const { t, lang } = useLang()
  const [sp, aplicar] = useFiltrosDaUrl()
  const consulta = useCard<CruzamentosDto>('cruzamentos', sp)
  const dto = consulta.data
  const [gaveta, setGaveta] = useState<Gaveta | null>(null)
  const bloqueado = temBloqueio(dto?.avisos)
  const traco = '—'
  const resumo = resumoDosFiltros(sp, t, (iso) => dataCurta(iso, lang))
  const linhasEixo = sp.get('linhas') ?? dto?.linhasEixo ?? 'tipo'
  const colunasEixo = sp.get('colunas') ?? dto?.colunasEixo ?? 'categoria'
  const medida = sp.get('medida') ?? dto?.medida ?? 'contagem'
  const idade = (dto?.medida ?? medida) === 'idadeP50'
  const unidade = t(dto?.unit === 'corrido' ? 'abertas.diasCorridos' : 'abertas.diasUteis')

  const nomeDoEixo = (id: string) => t(`cruzamentos.eixo.${id}`)
  const nomeCurto = (id: string) => (id === 'categoria' ? t('cruzamentos.eixoCurto.categoria') : nomeDoEixo(id))

  /** O valor de um eixo em texto do painel (categoria, faixa de idade, atraso, semana e o "sem dado" são traduzidos). */
  function rotulo(eixo: string, valor: string): string {
    if (valor === SEM) return t(`cruzamentos.sem.${eixo}`)
    if (eixo === 'categoria') return t(`cruzamentos.categoria.${valor}`)
    if (eixo === 'idade') return t(CHAVE_DA_FAIXA_DE_IDADE[valor] ?? valor)
    if (eixo === 'atraso') return t(`cruzamentos.atraso.${valor}`)
    if (eixo === 'vencimento') return t('cruzamentos.semanaDe').replace('{data}', dataCurta(valor, lang))
    return valor
  }

  function trocar(parametro: 'linhas' | 'colunas' | 'medida', valor: string) {
    const novo = new URLSearchParams(sp)
    novo.set(parametro, valor)
    aplicar(novo)
  }

  const abrir = (segmento: string, titulo: string) => setGaveta({ segmento, titulo })
  const mostrar = (c: Celula) => (c.n === 0 ? (idade ? traco : inteiro(0, lang)) : idade && c.valor != null ? numero(c.valor, lang) : inteiro(c.n, lang))

  // Faixa mais alta: 75% ou mais do maior valor da tabela (números e padrão, nunca só cor).
  const valores = (dto?.celulas ?? []).flat().map((c) => (idade ? (c.valor ?? 0) : c.n))
  const maior = Math.max(0, ...valores)
  const limiteAlto = maior * 0.75
  // Com pessoa em qualquer eixo não há destaque: seria um ranking visual de pessoas.
  const comPessoa = (dto?.linhasEixo ?? linhasEixo) === 'pessoa' || (dto?.colunasEixo ?? colunasEixo) === 'pessoa'
  const ehAlta = (c: Celula) => !comPessoa && maior > 0 && (idade ? (c.valor ?? 0) : c.n) >= limiteAlto && c.n > 0

  function exportar() {
    if (!dto) return
    baixarCsv('cruzamentos.csv', [
      [`# ${t('nav.cruzamentos')}: ${nomeCurto(dto.linhasEixo)} × ${nomeCurto(dto.colunasEixo)} (${t(idade ? 'cruzamentos.medida.idadeP50' : 'cruzamentos.medida.contagem')})`],
      [`# ${t('gaveta.csv.filtros')}: ${resumo}`],
      [],
      ['', ...dto.colunas.map((c) => rotulo(dto.colunasEixo, c))],
      ...dto.linhas.map((l, i) => [rotulo(dto.linhasEixo, l), ...dto.celulas[i].map((c) => (c.n === 0 ? '' : idade && c.valor != null ? numero(c.valor, lang) : String(c.n)))]),
    ])
  }

  return (
    <>
      <CardPage
        titulo={t('nav.cruzamentos')}
        pergunta={t('cruzamentos.pergunta')}
        filtros={<Filtros key={sp.toString()} campos={['squad', 'pessoa', 'tipo', 'prioridade', 'os', 'unit']} valor={sp} aoAplicar={aplicar} />}
        consulta={consulta}
        avisos={dto?.avisos}
        aoExportar={exportar}
      >
        {dto && (
          <Cartao id="CZ-01" variante="tabela" nivelDoTitulo={2} idDoTitulo="h-cz-tabela" titulo={t('cruzamentos.titulo')} n={dto.total}>
            <div className="cruz-eixos">
              <label className="campo">
                <span>{t('cruzamentos.linhas')}</span>
                <select value={linhasEixo} onChange={(e) => trocar('linhas', e.target.value)}>
                  {(dto.eixosDeLinha.length > 0 ? dto.eixosDeLinha : EIXOS_DE_LINHA).map((id) => (
                    <option key={id} value={id} disabled={id === colunasEixo}>{nomeDoEixo(id)}</option>
                  ))}
                </select>
              </label>
              <label className="campo">
                <span>{t('cruzamentos.colunas')}</span>
                <select value={colunasEixo} onChange={(e) => trocar('colunas', e.target.value)}>
                  {dto.eixosDeColuna.map((id) => (
                    <option key={id} value={id} disabled={id === linhasEixo}>{nomeDoEixo(id)}</option>
                  ))}
                </select>
              </label>
              <div role="radiogroup" aria-label={t('cruzamentos.medida')} className="cruz-medida">
                {(['contagem', 'idadeP50'] as const).map((m) => (
                  <label key={m} className="campo--check">
                    <input type="radio" name="medida" checked={medida === m} onChange={() => trocar('medida', m)} /> {t(`cruzamentos.medida.${m}`)}
                  </label>
                ))}
              </div>
            </div>
            <p className="legenda-meta">{t('cruzamentos.nota')}</p>
            {idade && <p className="legenda-meta">{t('cruzamentos.unidadeNota').replace('{unidade}', unidade)}</p>}

            {dto.linhas.length === 0 ? (
              <p className="vazio">{t('cruzamentos.vazio')}</p>
            ) : (
              <div className="tabela-rolagem" tabIndex={0} role="region" aria-labelledby="h-cz-tabela">
                <table className="tabela cruz-tabela" aria-label={t('cruzamentos.tabela')}>
                  <thead>
                    <tr>
                      <th scope="col">{nomeCurto(dto.linhasEixo)} × {nomeCurto(dto.colunasEixo)}</th>
                      {dto.colunas.map((c) => (
                        <th key={c} scope="col" className="num">{rotulo(dto.colunasEixo, c)}</th>
                      ))}
                      {dto.totaisDasLinhas && <th scope="col" className="num">{t('cruzamentos.total')}</th>}
                    </tr>
                  </thead>
                  <tbody>
                    {dto.linhas.map((l, i) => (
                      <tr key={l}>
                        <th scope="row">{rotulo(dto.linhasEixo, l)}</th>
                        {dto.celulas[i].map((c, j) => {
                          const alta = !bloqueado && ehAlta(c)
                          const nome = `${rotulo(dto.linhasEixo, l)} × ${rotulo(dto.colunasEixo, dto.colunas[j])}: ${mostrar(c)}${c.amostraPequena ? `, ${t('cruzamentos.amostraPequena')}` : ''}${alta ? `, ${t('cruzamentos.faixaMaisAlta')}` : ''}`
                          return (
                            <td key={dto.colunas[j]} className={`num${alta ? ' cruz-alta' : ''}`} data-faixa={alta ? 'alta' : undefined}>
                              {bloqueado ? (
                                traco
                              ) : c.n > 0 ? (
                                <>
                                  <button
                                    type="button" className="contagem-botao" aria-label={nome}
                                    onClick={() => abrir(`c:${i + 1}:${j + 1}`, `${rotulo(dto.linhasEixo, l)} × ${rotulo(dto.colunasEixo, dto.colunas[j])}`)}
                                  >
                                    {mostrar(c)}
                                  </button>
                                  {idade && (
                                    <span className="cruz-n"> n = {c.n}{c.amostraPequena && <> <span aria-hidden="true">●</span></>}</span>
                                  )}
                                </>
                              ) : (
                                mostrar(c)
                              )}
                            </td>
                          )
                        })}
                        {dto.totaisDasLinhas && (
                          <td className="num cruz-total">
                            {bloqueado ? traco : dto.totaisDasLinhas[i] > 0 ? (
                              <button
                                type="button" className="contagem-botao" aria-label={`${t('cruzamentos.total')} ${rotulo(dto.linhasEixo, l)}: ${dto.totaisDasLinhas[i]}`}
                                onClick={() => abrir(`l:${i + 1}`, `${t('cruzamentos.total')}: ${rotulo(dto.linhasEixo, l)}`)}
                              >
                                {inteiro(dto.totaisDasLinhas[i], lang)}
                              </button>
                            ) : inteiro(0, lang)}
                          </td>
                        )}
                      </tr>
                    ))}
                  </tbody>
                  <tfoot>
                    <tr>
                      <th scope="row">{t('cruzamentos.total')}</th>
                      {dto.totaisDasColunas.map((n, j) => (
                        <td key={dto.colunas[j]} className="num cruz-total">
                          {bloqueado ? traco : n > 0 ? (
                            <button
                              type="button" className="contagem-botao" aria-label={`${t('cruzamentos.total')} ${rotulo(dto.colunasEixo, dto.colunas[j])}: ${n}`}
                              onClick={() => abrir(`k:${j + 1}`, `${t('cruzamentos.total')}: ${rotulo(dto.colunasEixo, dto.colunas[j])}`)}
                            >
                              {inteiro(n, lang)}
                            </button>
                          ) : inteiro(0, lang)}
                        </td>
                      ))}
                      {dto.totaisDasLinhas && (
                        <td className="num cruz-total">
                          {bloqueado ? traco : dto.total > 0 ? (
                            <button
                              type="button" className="contagem-botao" aria-label={`${t('cruzamentos.totalGeral')}: ${dto.total}`}
                              onClick={() => abrir('total', t('cruzamentos.totalGeral'))}
                            >
                              {inteiro(dto.total, lang)}
                            </button>
                          ) : inteiro(0, lang)}
                        </td>
                      )}
                    </tr>
                  </tfoot>
                </table>
              </div>
            )}
            {dto.linhas.length > 0 && !dto.totaisDasLinhas && (
              // Sem total por pessoa, o total geral (não é por pessoa) continua alcançável fora da tabela.
              <p className="legenda-meta">
                {t('cruzamentos.totalGeral')}:{' '}
                {bloqueado ? traco : dto.total > 0 ? (
                  <button
                    type="button" className="contagem-botao" aria-label={`${t('cruzamentos.totalGeral')}: ${dto.total}`}
                    onClick={() => abrir('total', t('cruzamentos.totalGeral'))}
                  >
                    {inteiro(dto.total, lang)}
                  </button>
                ) : inteiro(0, lang)}
              </p>
            )}
            {!comPessoa && <p className="legenda-meta">{t('cruzamentos.legendaFaixa')}</p>}
          </Cartao>
        )}
      </CardPage>
      {gaveta && (
        <GavetaDeTasks cartaoId="CZ-01" segmento={gaveta.segmento} titulo={gaveta.titulo} filtros={sp} resumoDosFiltros={resumo} aoFechar={() => setGaveta(null)} />
      )}
    </>
  )
}
