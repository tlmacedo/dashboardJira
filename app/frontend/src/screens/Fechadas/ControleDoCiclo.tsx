import { useState } from 'react'
import { useLang } from '../../i18n/LangProvider'
import { Cartao } from '../../shared/Cartao'
import { resumoDosFiltros, useFiltrosDaUrl } from '../../shared/Filtros'
import { dataCurta, inteiro, numero } from '../../shared/format'
import { GavetaDeTasks } from '../../shared/GavetaDeTasks'
import { temBloqueio, textoDoAviso, useCard } from '../../shared/useCard'
import type { ControleDoCicloDto } from './controleApi'
import { GraficoDeControle } from './GraficoDeControle'
import { GraficoDeTendencia } from './GraficoDeTendencia'
import { mesCurto } from './meses'
import '../../shared/tabela.css'

type Gaveta = { cartaoId: string; segmento: string; titulo: string }

/**
 * Seção "Controle do ciclo e tendência" da tela Fechadas (CT-01 e CT-02): o gráfico de controle (dia de conclusão x dias de
 * ciclo, com média móvel e faixas P50 e P85) POR TIPO, e a série mensal de P50 e P85. Lê os mesmos filtros da página (URL);
 * "sem outliers" também vai para a URL. Erro ou ausência de dados não derruba a tela de Fechadas: a seção só avisa.
 */
export function ControleDoCiclo() {
  const { t, lang } = useLang()
  const [sp, aplicar] = useFiltrosDaUrl()
  const consulta = useCard<ControleDoCicloDto>('controle-ciclo', sp)
  const dto = consulta.data
  const [tipoEscolhido, setTipo] = useState<string | null>(null)
  const [gaveta, setGaveta] = useState<Gaveta | null>(null)
  const [verTabela, setVerTabela] = useState(false)
  const bloqueado = temBloqueio(dto?.avisos)
  const traco = '—'
  const resumo = resumoDosFiltros(sp, t, (iso) => dataCurta(iso, lang))
  const unidade = t(dto?.unit === 'corrido' ? 'abertas.diasCorridos' : 'abertas.diasUteis')
  const serie = dto?.series.find((s) => s.tipo === tipoEscolhido) ?? dto?.series[0]
  const semOutliers = sp.get('semOutliers') === 'true'

  function alternarOutliers(ligado: boolean) {
    const novo = new URLSearchParams(sp)
    if (ligado) novo.set('semOutliers', 'true')
    else novo.delete('semOutliers')
    aplicar(novo)
  }

  const abrir = (cartaoId: string, segmento: string, titulo: string) => setGaveta({ cartaoId, segmento, titulo })

  let corpo
  if (consulta.isPending) {
    corpo = <p className="vazio" aria-busy="true">{t('app.carregando')}</p>
  } else if (consulta.isError && !dto) {
    corpo = <p className="vazio">{t('controle.erro')}</p>
  } else if (dto && bloqueado) {
    corpo = <p className="vazio">{t('controle.indisponivel')}</p>
  } else if (dto && !serie) {
    corpo = <p className="vazio">{t('controle.vazio')}</p>
  } else if (dto && serie) {
    const avisos = dto.avisos.filter((a) => a.params?.tipo === serie.tipo)
    corpo = (
      <>
        <p className="legenda-meta">{t('controle.nota')}</p>
        <p>
          {serie.p50 != null && serie.p85 != null
            ? t('controle.faixa').replace('{p50}', numero(serie.p50, lang)).replace('{p85}', numero(serie.p85, lang)).replace('{n}', inteiro(serie.n, lang)).replace('{unidade}', unidade)
            : null}
          {serie.amostraPequena && (
            <span className="pilula pilula--atencao"> <span aria-hidden="true">●</span> {t('controle.poucos')}</span>
          )}
        </p>
        <p className="legenda-meta">
          {serie.janela != null ? t('controle.janela').replace('{n}', String(serie.janela)) : t('controle.semMedia')}
        </p>
        {avisos.length > 0 && <p className="legenda-meta">{avisos.map((a) => textoDoAviso(a, t)).join(' ')}</p>}
        {dto.outliersExcluidos > 0 && (
          <p className="legenda-meta">{t('controle.excluidos').replace('{n}', String(dto.outliersExcluidos)).replace('{dias}', String(dto.limiteOutlierDias))}</p>
        )}
        <GraficoDeControle
          serie={serie} unidade={unidade} de={dto.de} ate={dto.ate}
          aoAbrirDia={(dia) => abrir('CT-01', `dia:${dia}:${serie.tipo}`, `${serie.tipo}: ${dataCurta(dia, lang)}`)}
        />
        <button
          type="button" className="btn controle-ver-tabela" aria-expanded={verTabela} aria-controls="controle-pontos"
          onClick={() => setVerTabela((v) => !v)}
        >
          {t(verTabela ? 'controle.ocultarTabela' : 'controle.verTabela')}
        </button>
        {verTabela && (
          <div id="controle-pontos" className="tabela-rolagem" tabIndex={0} role="region" aria-label={t('controle.tabela.pontos')}>
            <table className="tabela" aria-label={t('controle.tabela.pontos')}>
              <thead>
                <tr>
                  <th scope="col">{t('controle.col.dia')}</th>
                  <th scope="col" className="num">{t('controle.col.ciclo')} ({unidade})</th>
                  <th scope="col" className="num">{t('controle.col.media')}</th>
                  <th scope="col">{t('controle.col.sinal')}</th>
                </tr>
              </thead>
              <tbody>
                {serie.pontos.map((p, i) => (
                  <tr key={i}>
                    <th scope="row">{dataCurta(p.dia, lang)}</th>
                    <td className="num">
                      <button
                        type="button" className="contagem-botao" aria-label={`${dataCurta(p.dia, lang)}: ${numero(p.ciclo, lang)}`}
                        onClick={() => abrir('CT-01', `dia:${p.dia}:${serie.tipo}`, `${serie.tipo}: ${dataCurta(p.dia, lang)}`)}
                      >
                        {numero(p.ciclo, lang)}
                      </button>
                    </td>
                    <td className="num">{p.mediaMovel == null ? traco : numero(p.mediaMovel, lang)}</td>
                    <td>{p.outlier ? <span><span aria-hidden="true">▲</span> {t('controle.outlier')}</span> : traco}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </>
    )
  }

  const tendencia = dto && serie && !bloqueado && (
    <Cartao id="CT-02" variante="serie" nivelDoTitulo={2} idDoTitulo="h-ct-tendencia" titulo={t('controle.tendencia')} n={serie.n}>
      <GraficoDeTendencia meses={serie.meses} unidade={unidade} />
      <div className="tabela-rolagem" tabIndex={0} role="region" aria-labelledby="h-ct-tendencia">
        <table className="tabela" aria-label={t('controle.tabela.meses')}>
          <thead>
            <tr>
              <th scope="col">{t('controle.col.mes')}</th>
              <th scope="col" className="num">n</th>
              <th scope="col" className="num">P50 ({unidade})</th>
              <th scope="col" className="num">P85 ({unidade})</th>
              <th scope="col">{t('controle.col.aviso')}</th>
            </tr>
          </thead>
          <tbody>
            {serie.meses.map((m) => (
              <tr key={m.mes}>
                <th scope="row">{mesCurto(m.mes)}</th>
                <td className="num">
                  {m.n > 0 ? (
                    <button
                      type="button" className="contagem-botao" aria-label={`${mesCurto(m.mes)}: ${m.n}`}
                      onClick={() => abrir('CT-02', `mes:${m.mes}:${serie.tipo}`, `${serie.tipo}: ${mesCurto(m.mes)}`)}
                    >
                      {inteiro(m.n, lang)}
                    </button>
                  ) : (
                    inteiro(0, lang)
                  )}
                </td>
                <td className="num">{m.p50 == null ? traco : numero(m.p50, lang)}</td>
                <td className="num">{m.p85 == null ? traco : numero(m.p85, lang)}</td>
                <td>{m.amostraPequena ? <span><span aria-hidden="true">●</span> {t('controle.poucos')}</span> : traco}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </Cartao>
  )

  return (
    <>
      <Cartao id="CT-01" variante="serie" nivelDoTitulo={2} idDoTitulo="h-ct-controle" titulo={t('controle.titulo')} n={serie?.n}>
        <div className="controle-barra">
          <label className="campo">
            <span>{t('controle.tipo')}</span>
            <select value={serie?.tipo ?? ''} onChange={(e) => setTipo(e.target.value)} disabled={!dto || dto.series.length === 0}>
              {(dto?.series ?? []).map((s) => (
                <option key={s.tipo} value={s.tipo}>{s.tipo}</option>
              ))}
            </select>
          </label>
          <label className="campo--check">
            <input type="checkbox" checked={semOutliers} onChange={(e) => alternarOutliers(e.target.checked)} />{' '}
            {t('controle.semOutliers').replace('{dias}', String(dto?.limiteOutlierDias ?? 30))}
          </label>
        </div>
        {corpo}
      </Cartao>
      {tendencia}
      {gaveta && (
        <GavetaDeTasks
          cartaoId={gaveta.cartaoId} segmento={gaveta.segmento} titulo={gaveta.titulo} filtros={sp}
          resumoDosFiltros={resumo} aoFechar={() => setGaveta(null)}
        />
      )}
    </>
  )
}
