import { useState, type FormEvent } from 'react'
import { useLang } from '../../i18n/LangProvider'
import { Cartao } from '../../shared/Cartao'
import { CardPage } from '../../shared/CardPage'
import { dataCurta, inteiro, numero } from '../../shared/format'
import { GavetaDeTasks } from '../../shared/GavetaDeTasks'
import { Ocupacao } from '../../shared/Ocupacao'
import { resumoDosFiltros, useFiltrosDaUrl } from '../../shared/Filtros'
import { temBloqueio, useCard } from '../../shared/useCard'
import type { SimuladorDto } from './api'
import '../../shared/tabela.css'
import '../../shared/pessoas.css'

/**
 * Simulador de ausência (SI-01), só liderança. É um formulário de LEITURA: pessoa, de, até e fração só mudam a URL da tela, e a
 * consulta é sempre um GET (a tela nunca envia POST, PUT, PATCH nem DELETE; nada é gravado e nenhuma ausência é cadastrada).
 * Mostra as tasks que passariam a atrasar, as horas a replanejar e a ocupação de cada semana antes e depois. Capacidade zero é
 * o texto "sem capacidade", nunca uma porcentagem.
 */
export function Simulador() {
  const { t, lang } = useLang()
  const [sp, aplicar] = useFiltrosDaUrl()
  const consulta = useCard<SimuladorDto>('simulador', sp)
  const dto = consulta.data
  const [gaveta, setGaveta] = useState(false)
  const bloqueado = temBloqueio(dto?.avisos)
  const traco = '—'
  const resumo = resumoDosFiltros(sp, t, (iso) => dataCurta(iso, lang))
  const [rascunho, setRascunho] = useState({
    pessoa: sp.get('pessoa') ?? '',
    de: sp.get('de') ?? '',
    ate: sp.get('ate') ?? '',
    fracao: sp.get('fracao') === '0.5' ? '0.5' : '1.0',
  })

  function simular(e: FormEvent) {
    e.preventDefault()
    const novo = new URLSearchParams()
    if (rascunho.pessoa.trim()) novo.set('pessoa', rascunho.pessoa.trim())
    if (rascunho.de.trim()) novo.set('de', rascunho.de.trim())
    if (rascunho.ate.trim()) novo.set('ate', rascunho.ate.trim())
    novo.set('fracao', rascunho.fracao)
    aplicar(novo)
  }

  const formulario = (
    <form className="filtros" onSubmit={simular} aria-label={t('simulador.form')}>
      <label className="campo">
        <span>{t('simulador.pessoa')}</span>
        <input type="text" maxLength={100} value={rascunho.pessoa} onChange={(e) => setRascunho({ ...rascunho, pessoa: e.target.value })} />
      </label>
      <label className="campo campo--data">
        <span>{t('filtros.de')}</span>
        <input type="date" value={rascunho.de} onChange={(e) => setRascunho({ ...rascunho, de: e.target.value })} />
      </label>
      <label className="campo campo--data">
        <span>{t('filtros.ate')}</span>
        <input type="date" value={rascunho.ate} onChange={(e) => setRascunho({ ...rascunho, ate: e.target.value })} />
      </label>
      <label className="campo">
        <span>{t('simulador.fracao')}</span>
        <select value={rascunho.fracao} onChange={(e) => setRascunho({ ...rascunho, fracao: e.target.value })}>
          <option value="1.0">{t('simulador.fracao.inteira')}</option>
          <option value="0.5">{t('simulador.fracao.meia')}</option>
        </select>
      </label>
      <button type="submit" className="btn btn--primario">
        {t('simulador.simular')}
      </button>
    </form>
  )

  const simulou = dto !== undefined && dto.pessoa !== ''

  return (
    <>
      <CardPage
        titulo={t('nav.simulador')}
        pergunta={t('simulador.pergunta')}
        filtros={formulario}
        consulta={consulta}
        avisos={dto?.avisos}
      >
        {dto && simulou && (
          <>
            <div role="group" aria-label={t('simulador.indicadores')} className="cardpage-kpis">
              <Cartao
                id="SI-01" titulo={t('simulador.kpi.atrasos')} valor={bloqueado ? traco : inteiro(dto.atrasosCausados.length, lang)}
                simboloDoValor={!bloqueado && dto.atrasosCausados.length > 0 ? '▲' : undefined}
                legenda={`${dto.pessoa} · ${dataCurta(dto.de, lang)} – ${dataCurta(dto.ate, lang)}`}
                aoAbrir={!bloqueado && dto.atrasosCausados.length > 0 ? () => setGaveta(true) : undefined}
              />
              <Cartao id="SI-01" titulo={t('simulador.kpi.horas')} valor={bloqueado ? traco : `${numero(dto.horasAReplanejar, lang)} h`} />
            </div>

            <Cartao id="SI-01" variante="tabela" nivelDoTitulo={2} idDoTitulo="h-si-tabela" titulo={t('simulador.tabela')}>
              <div className="tabela-rolagem" tabIndex={0} role="region" aria-labelledby="h-si-tabela">
                <table className="tabela grade-pessoas" aria-labelledby="h-si-tabela">
                  <thead>
                    <tr>
                      <th scope="col">{t('simulador.col.semana')}</th>
                      <th scope="col">{t('simulador.col.antes')}</th>
                      <th scope="col">{t('simulador.col.depois')}</th>
                    </tr>
                  </thead>
                  <tbody>
                    {dto.semanas.map((s) => (
                      <tr key={s.semana}>
                        <th scope="row">{dataCurta(s.semana, lang)}</th>
                        <td>{bloqueado ? traco : <Ocupacao o={s.antes} compacta />}</td>
                        <td>{bloqueado ? traco : <Ocupacao o={s.depois} compacta />}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
              <p className="legenda-meta">{t('simulador.nota')}</p>
            </Cartao>
          </>
        )}
        {dto && !simulou && <p className="vazio">{t('simulador.vazio')}</p>}
      </CardPage>
      {gaveta && (
        <GavetaDeTasks
          cartaoId="SI-01" segmento="atrasosCausados" titulo={t('simulador.kpi.atrasos')} filtros={sp} resumoDosFiltros={resumo}
          aoFechar={() => setGaveta(false)}
        />
      )}
    </>
  )
}
