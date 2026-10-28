import { useQuery, keepPreviousData } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { useSearchParams } from 'react-router-dom'
import { ErroDaApi } from '../../api/client'
import { useLang } from '../../i18n/LangProvider'
import { Cartao } from '../../shared/Cartao'
import { baixarCsv } from '../../shared/csv'
import { dataCurta, inteiro, numero } from '../../shared/format'
import { GavetaDeTasks } from '../../shared/GavetaDeTasks'
import { temBloqueio, textoDoAviso } from '../../shared/useCard'
import '../../shared/tabela.css'
import '../../shared/cardpage.css'
import '../TempoEmStatus/tempo.css'
import {
  fetchCatalogoIntegridade, fetchIntegridade, filtrosDaUrl, paramsDe, SINAIS,
  type Evidencia, type FiltrosIntegridade, type IntegridadeDto,
} from './api'

type Gaveta = { cartaoId: string; segmento: string; titulo: string }

/** Detalhe da evidência em texto simples: `chave=valor` por par, na ordem de chegada. */
function detalheEmTexto(e: Evidencia): string {
  return Object.entries(e.detalhe).map(([k, v]) => `${k}=${v}`).join('; ')
}

/**
 * Integridade (IN-01 a IN-08), SÓ do gerente e sempre auditada. Layout próprio: as duas faixas fixas ("Indício, não prova" e
 * "Validar a base legal com o DPO antes do uso real") aparecem em TODOS os estados, inclusive erro e sem permissão.
 * Sem ranking de pessoas: a lista de pessoas é alfabética, sem colunas ordenáveis, e o nível vai em símbolo + texto.
 */
export function Integridade() {
  const { t, lang } = useLang()
  const [sp, setSp] = useSearchParams()
  const [gaveta, setGaveta] = useState<Gaveta | null>(null)
  const [catalogoAberto, setCatalogoAberto] = useState(false)
  const [form, setForm] = useState<FiltrosIntegridade>(() => filtrosDaUrl(sp))

  const consulta = useQuery({
    queryKey: ['integridade', sp.toString()],
    queryFn: () => fetchIntegridade(sp),
    placeholderData: keepPreviousData,
    retry: false,
  })
  const catalogo = useQuery({
    queryKey: ['integridade-catalogo'],
    queryFn: fetchCatalogoIntegridade,
    enabled: catalogoAberto,
    retry: false,
  })

  const dto: IntegridadeDto | undefined = consulta.data
  const erro = consulta.error instanceof ErroDaApi ? consulta.error : null
  const bloqueado = temBloqueio(dto?.avisos)
  const traco = '—'
  const abrir = (cartaoId: string, segmento: string, titulo: string) => setGaveta({ cartaoId, segmento, titulo })
  const comAbrir = (n: number, cartaoId: string, segmento: string, titulo: string) =>
    !bloqueado && n > 0 ? () => abrir(cartaoId, segmento, titulo) : undefined

  const resumoFiltros = [...paramsDe(filtrosDaUrl(sp)).entries()].map(([k, v]) => `${k}=${v}`).join(', ') || '—'

  function aplicar(ev: FormEvent) {
    ev.preventDefault()
    setSp(paramsDe(form))
  }

  function exportar() {
    if (!dto) return
    baixarCsv('integridade.csv', [
      [`# ${t('integridade.csv.indicio')}`],
      [`# ${t('integridade.csv.dpo')}`],
      [`# ${t('integridade.csv.periodo')}: ${dto.de} - ${dto.ate} (${dto.versaoRegras})`],
      [`# ${t('gaveta.csv.filtros')}: ${resumoFiltros}`],
      [],
      [t('integridade.col.sinal'), t('integridade.col.gravidade'), t('integridade.col.task'), t('integridade.col.pessoa'), t('integridade.col.dia'), t('integridade.col.detalhe')],
      ...dto.evidencias.map((e) => [t(`integridade.sinal.${e.sinal}`), String(e.gravidade), e.key, e.pessoa ?? t('integridade.semAutor'), e.dia, detalheEmTexto(e)]),
    ])
  }

  function contagem(n: number, rotulo: string, cartaoId: string, segmento: string, titulo: string) {
    if (bloqueado) return traco
    if (n === 0) return inteiro(n, lang)
    return (
      <button type="button" className="contagem-botao" aria-label={`${rotulo}: ${n}`} onClick={() => abrir(cartaoId, segmento, titulo)}>
        {inteiro(n, lang)}
      </button>
    )
  }

  const r = dto?.resumo

  return (
    <div className="tempo cardpage">
      <div className="top">
        <div>
          <h1>{t('nav.integridade')}</h1>
          <p className="sub">{t('integridade.pergunta')}</p>
        </div>
        <div className="top-acoes">
          <button type="button" className="btn" onClick={exportar} disabled={!dto}>
            {t('cardpage.csv')}
          </button>
        </div>
      </div>

      {/* Faixas fixas: sempre na tela, em qualquer estado. */}
      <div className="aviso" role="note">
        <strong>{t('integridade.aviso.indicio')}</strong>
      </div>
      <div className="aviso" role="note">
        <strong>{t('integridade.aviso.dpo')}</strong>
      </div>

      <form className="barra" role="search" aria-label={t('integridade.filtros')} onSubmit={aplicar}>
        {(['de', 'ate'] as const).map((k) => (
          <label key={k}>
            {t(`integridade.filtro.${k}`)}
            <input type="date" value={form[k]} onChange={(e) => setForm({ ...form, [k]: e.target.value })} />
          </label>
        ))}
        {(['squad', 'pessoa'] as const).map((k) => (
          <label key={k}>
            {t(`integridade.filtro.${k}`)}
            <input type="text" value={form[k]} onChange={(e) => setForm({ ...form, [k]: e.target.value })} />
          </label>
        ))}
        <label>
          {t('integridade.filtro.sinal')}
          <select value={form.sinal} onChange={(e) => setForm({ ...form, sinal: e.target.value })}>
            <option value="">{t('integridade.filtro.todos')}</option>
            {SINAIS.map((s) => (
              <option key={s} value={s}>
                {t(`integridade.sinal.${s}`)}
              </option>
            ))}
          </select>
        </label>
        <button type="submit" className="btn">
          {t('integridade.aplicar')}
        </button>
      </form>

      {consulta.isPending && (
        <p className="vazio" aria-busy="true">
          {t('app.carregando')}
        </p>
      )}

      {consulta.isError && !dto && (
        <div className="estado estado--erro" role="alert">
          {erro?.status === 403 && erro.codigo === 'AGUARDANDO_ACEITE_DPO' && <p>{t('integridade.aguardandoDpo')}</p>}
          {erro?.status === 403 && erro.codigo !== 'AGUARDANDO_ACEITE_DPO' && <p>{t('integridade.semPermissao')}</p>}
          {erro?.status === 400 && <p>{t('integridade.filtroInvalido')}</p>}
          {erro?.status !== 403 && erro?.status !== 400 && (
            <p>
              {t('integridade.erro')}
              {erro?.status ? ` (HTTP ${erro.status})` : ''}
            </p>
          )}
          {erro?.status !== 403 && (
            <button type="button" className="btn" onClick={() => void consulta.refetch()}>
              {t('integridade.tentarDeNovo')}
            </button>
          )}
        </div>
      )}

      {dto && r && (
        <div className={consulta.isFetching ? 'conteudo conteudo--atualizando' : 'conteudo'} aria-busy={consulta.isFetching}>
          {dto.avisos.length > 0 && (
            <div className={bloqueado ? 'aviso aviso--bloqueante' : 'aviso'} role={bloqueado ? 'alert' : 'status'}>
              <ul>
                {dto.avisos.map((a) => (
                  <li key={`${a.codigo}:${JSON.stringify(a.params ?? {})}`}>{textoDoAviso(a, t)}</li>
                ))}
              </ul>
            </div>
          )}

          <p className="legenda-meta">{t('integridade.regras').replace('{versao}', dto.versaoRegras)}</p>

          <div role="group" aria-label={t('integridade.indicadores')} className="cardpage-kpis">
            <Cartao
              id="IN-01" ajudaComTitulo titulo={t('integridade.kpi.evidencias')} valor={bloqueado ? traco : inteiro(r.evidencias, lang)}
              aoAbrir={comAbrir(r.evidencias, 'IN-01', 'todas', t('integridade.kpi.evidencias'))}
            />
            <Cartao
              id="IN-02" titulo={t('integridade.kpi.alto')} valor={bloqueado ? traco : inteiro(r.pessoasAlto, lang)}
              estado={!bloqueado && r.pessoasAlto > 0 ? { tipo: 'problema', texto: t('integridade.nivel.ALTO') } : undefined}
              legenda={t('integridade.kpi.pessoas.dica').replace('{n}', inteiro(r.evidenciasNivelAlto, lang))}
              aoAbrir={comAbrir(r.evidenciasNivelAlto, 'IN-02', 'nivel:ALTO', t('integridade.kpi.alto'))}
            />
            <Cartao
              id="IN-02" titulo={t('integridade.kpi.medio')} valor={bloqueado ? traco : inteiro(r.pessoasMedio, lang)}
              estado={!bloqueado && r.pessoasMedio > 0 ? { tipo: 'atencao', texto: t('integridade.nivel.MEDIO') } : undefined}
              legenda={t('integridade.kpi.pessoas.dica').replace('{n}', inteiro(r.evidenciasNivelMedio, lang))}
              aoAbrir={comAbrir(r.evidenciasNivelMedio, 'IN-02', 'nivel:MEDIO', t('integridade.kpi.medio'))}
            />
            <Cartao
              id="IN-03" titulo={t('integridade.kpi.afetadas')} valor={bloqueado ? traco : inteiro(r.issuesAfetadas, lang)}
              aoAbrir={comAbrir(r.issuesAfetadas, 'IN-03', 'afetadas', t('integridade.kpi.afetadas'))}
            />
            <Cartao
              id="IN-04" titulo={t('integridade.kpi.mediana')} valor={bloqueado ? traco : numero(r.medianaTaxa, lang, 2)}
              legenda={t('integridade.kpi.mediana.dica')}
            />
            <Cartao
              id="IN-06" titulo={t('integridade.kpi.impacto')}
              valor={bloqueado || r.impactoMetasPp == null ? traco : `${numero(r.impactoMetasPp, lang)} pp`}
              legenda={r.impactoMetasPp == null ? t('integridade.kpi.impacto.sem') : t('integridade.kpi.impacto.dica')}
            />
          </div>

          <Cartao id="IN-05" variante="tabela" nivelDoTitulo={2} idDoTitulo="h-in-sinais" titulo={t('integridade.tabela.sinais')}>
            <div className="tabela-rolagem" tabIndex={0} role="region" aria-labelledby="h-in-sinais">
              <table className="tabela" aria-labelledby="h-in-sinais">
                <thead>
                  <tr>
                    <th scope="col">{t('integridade.col.sinal')}</th>
                    <th scope="col">{t('integridade.col.gravidade')}</th>
                    <th scope="col" className="num">{t('integridade.col.evidencias')}</th>
                  </tr>
                </thead>
                <tbody>
                  {SINAIS.map((s) => {
                    const c = r.porSinal.find((x) => x.sinal === s)
                    const n = c?.n ?? 0
                    return (
                      <tr key={s}>
                        <th scope="row">{t(`integridade.sinal.${s}`)}</th>
                        <td>{c ? (c.ativo ? t(`integridade.gravidade.${c.gravidade}`) : t('integridade.inativo')) : traco}</td>
                        <td className="num">{contagem(n, t(`integridade.sinal.${s}`), 'IN-05', `sinal:${s}`, t(`integridade.sinal.${s}`))}</td>
                      </tr>
                    )
                  })}
                </tbody>
              </table>
            </div>
          </Cartao>

          <Cartao id="IN-02" variante="tabela" nivelDoTitulo={2} idDoTitulo="h-in-pessoas" titulo={t('integridade.tabela.pessoas')}>
            <p className="legenda-meta">{t('integridade.alfabetica')}</p>
            <p className="legenda-meta">
              {t('integridade.nivel.regra')
                .replace('{pontosAlto}', String(r.pontosAlto))
                .replace('{multiploAlto}', String(r.multiploAlto))
                .replace('{gravesAlto}', String(r.gravesAlto))
                .replace('{pontosMedio}', String(r.pontosMedio))
                .replace('{multiploMedio}', String(r.multiploMedio))
                .replace('{concluidasMinimas}', String(r.concluidasMinimas))}
            </p>
            {r.pessoas.length === 0 ? (
              <p className="vazio">{t('integridade.semPessoas')}</p>
            ) : (
              <div className="tabela-rolagem" tabIndex={0} role="region" aria-labelledby="h-in-pessoas">
                <table className="tabela" aria-labelledby="h-in-pessoas">
                  <thead>
                    <tr>
                      <th scope="col">{t('integridade.col.pessoa')}</th>
                      <th scope="col">{t('integridade.col.nivel')}</th>
                      <th scope="col" className="num">{t('integridade.col.pontos')}</th>
                      <th scope="col" className="num">{t('integridade.col.concluidas')}</th>
                      <th scope="col" className="num">{t('integridade.col.multiplo')}</th>
                      <th scope="col" className="num">{t('integridade.col.evidencias')}</th>
                    </tr>
                  </thead>
                  <tbody>
                    {[...r.pessoas].sort((a, b) => a.pessoa.localeCompare(b.pessoa)).map((p) => (
                      <tr key={p.pessoa}>
                        <th scope="row">{p.pessoa}</th>
                        <td>{t(`integridade.nivel.${p.nivel}`)}</td>
                        <td className="num">{bloqueado ? traco : numero(p.pontos, lang)}</td>
                        <td className="num">{inteiro(p.concluidas, lang)}</td>
                        <td className="num">{`${numero(p.multiplo, lang)}×`}</td>
                        <td className="num">{contagem(p.evidencias, `${p.pessoa}`, 'IN-02', `pessoa:${p.pessoa}`, `${t('integridade.tabela.pessoas')}: ${p.pessoa}`)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </Cartao>

          <Cartao id="IN-07" variante="lista" nivelDoTitulo={2} idDoTitulo="h-in-evid" titulo={t('integridade.tabela.evidencias')}>
            {dto.evidencias.length === 0 ? (
              <p className="vazio">{t('integridade.vazia')}</p>
            ) : (
              <div className="tabela-rolagem" tabIndex={0} role="region" aria-labelledby="h-in-evid">
                <table className="tabela" aria-labelledby="h-in-evid">
                  <thead>
                    <tr>
                      <th scope="col">{t('integridade.col.sinal')}</th>
                      <th scope="col">{t('integridade.col.gravidade')}</th>
                      <th scope="col">{t('integridade.col.task')}</th>
                      <th scope="col">{t('integridade.col.responsavel')}</th>
                      <th scope="col">{t('integridade.col.dia')}</th>
                      <th scope="col">{t('integridade.col.detalhe')}</th>
                    </tr>
                  </thead>
                  <tbody>
                    {dto.evidencias.map((e, i) => (
                      <tr key={`${e.sinal}:${e.key}:${e.quando}:${i}`}>
                        <th scope="row">{t(`integridade.sinal.${e.sinal}`)}</th>
                        <td>{t(`integridade.gravidade.${e.gravidade}`)}</td>
                        <td className="mono">{e.key}</td>
                        <td>{e.pessoa ?? t('integridade.semAutor')}</td>
                        <td className="num">{dataCurta(e.dia, lang)}</td>
                        <td>{detalheEmTexto(e)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </Cartao>

          <details onToggle={(e) => setCatalogoAberto((e.currentTarget as HTMLDetailsElement).open || catalogoAberto)}>
            <summary>{t('integridade.catalogo.abrir')}</summary>
            <Cartao id="IN-08" variante="tabela" nivelDoTitulo={2} idDoTitulo="h-in-catalogo" titulo={t('integridade.tabela.catalogo')}>
              {!catalogo.data ? (
                <p className="vazio" aria-busy="true">{t('integridade.catalogo.carregando')}</p>
              ) : (
                <div className="tabela-rolagem" tabIndex={0} role="region" aria-labelledby="h-in-catalogo">
                  <table className="tabela" aria-labelledby="h-in-catalogo">
                    <thead>
                      <tr>
                        <th scope="col">{t('integridade.col.sinal')}</th>
                        <th scope="col">{t('integridade.col.regra')}</th>
                        <th scope="col">{t('integridade.col.gravidade')}</th>
                        <th scope="col">{t('integridade.col.ativo')}</th>
                      </tr>
                    </thead>
                    <tbody>
                      {catalogo.data.sinais.map((s) => (
                        <tr key={s.sinal}>
                          <th scope="row">{t(`integridade.sinal.${s.sinal}`)}</th>
                          <td>
                            {Object.entries(s.params).reduce((txt, [k, v]) => txt.replaceAll(`{${k}}`, () => v), t(`integridade.regra.${s.sinal}`)).replace(/\{\w+\}/g, '…')}
                          </td>
                          <td>{t(`integridade.gravidade.${s.gravidade}`)}</td>
                          <td>{s.ativo ? t('integridade.ativo') : t('integridade.inativo')}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </Cartao>
          </details>
        </div>
      )}

      {gaveta && (
        <GavetaDeTasks
          cartaoId={gaveta.cartaoId} segmento={gaveta.segmento} titulo={gaveta.titulo} filtros={sp}
          resumoDosFiltros={resumoFiltros} aoFechar={() => setGaveta(null)}
        />
      )}
    </div>
  )
}
