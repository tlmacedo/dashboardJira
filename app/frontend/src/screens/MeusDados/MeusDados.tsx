import { useQuery } from '@tanstack/react-query'
import { useLang } from '../../i18n/LangProvider'
import { CardPage } from '../../shared/CardPage'
import { dataCurta, inteiro, instanteCurto, numero } from '../../shared/format'
import '../../shared/tabela.css'
import { fetchMeusDados, type MeusDadosDto } from './api'

/** Qual retenção vale para a variável de uma categoria (o nome da variável vem do backend). */
const CHAVE_DA_RETENCAO: Record<string, string> = {
  RETENTION_HISTORICO_MONTHS: 'historico',
  RETENTION_ACCESS_LOG_MONTHS: 'accessLog',
  RETENTION_DECISOES_MONTHS: 'decisoes',
  RETENTION_EXECUCOES_MONTHS: 'execucoes',
}

/** Baixa o MESMO corpo da API, gerado no navegador (nada vai a outro servidor). Nome `meus-dados-<data>.json`. */
function baixar(dto: MeusDadosDto) {
  const blob = new Blob([JSON.stringify(dto, null, 2)], { type: 'application/json' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `meus-dados-${dto.geradoEm.slice(0, 10)}.json`
  document.body.appendChild(a)
  a.click()
  a.remove()
  URL.revokeObjectURL(url)
}

/**
 * Meus dados (direito de acesso do titular, LGPD): o que o painel guarda sobre a PESSOA LOGADA (contagens e papéis), as categorias
 * tratadas com finalidade e retenção, os acessos recentes dela e o botão para baixar tudo em JSON. Tela de leitura, para todos os
 * papéis. Não mostra o conteúdo de sinais de Auditoria nem de Integridade. Correção e eliminação se pedem ao DPO.
 */
export function MeusDados() {
  const { t, lang } = useLang()
  const consulta = useQuery({ queryKey: ['meus-dados'], queryFn: fetchMeusDados, retry: false })
  const dto = consulta.data

  return (
    <CardPage titulo={t('nav.meusDados')} pergunta={t('meusDados.pergunta')} filtros={null} consulta={consulta}>
      {dto && (
        <>
          <div className="top-acoes">
            <button type="button" className="btn" onClick={() => baixar(dto)}>
              {t('meusDados.baixar')}
            </button>
          </div>

          <section aria-labelledby="h-md-resumo">
            <h2 id="h-md-resumo">{t('meusDados.resumo')}</h2>
            <dl className="ficha">
              <dt>{t('meusDados.issuesComoResponsavel')}</dt>
              <dd>{inteiro(dto.issuesComoResponsavel, lang)}</dd>
              <dt>{t('meusDados.issuesComoReporter')}</dt>
              <dd>{inteiro(dto.issuesComoReporter, lang)}</dd>
              <dt>{t('meusDados.alteracoesComoAutor')}</dt>
              <dd>{inteiro(dto.alteracoesComoAutor, lang)}</dd>
              <dt>{t('meusDados.worklogs')}</dt>
              <dd>{inteiro(dto.worklogs, lang)}</dd>
              <dt>{t('meusDados.horas')}</dt>
              <dd>{numero(dto.horasRegistradas, lang)}</dd>
              <dt>{t('meusDados.ausencias')}</dt>
              <dd>{inteiro(dto.ausenciasPlanejadas, lang)}</dd>
              <dt>{t('meusDados.decisoes')}</dt>
              <dd>{inteiro(dto.decisoesEnvolvendo, lang)}</dd>
            </dl>
            <p className="legenda-meta">
              {t('meusDados.geradoEm')} {instanteCurto(dto.geradoEm, lang)}
            </p>
          </section>

          <section aria-labelledby="h-md-papeis">
            <h2 id="h-md-papeis">{t('meusDados.papeis')}</h2>
            {dto.papeis.length === 0 ? (
              <p className="vazio">{t('meusDados.semPapeis')}</p>
            ) : (
              <div className="tabela-rolagem" tabIndex={0} role="region" aria-labelledby="h-md-papeis">
                <table className="tabela" aria-labelledby="h-md-papeis">
                  <thead>
                    <tr>
                      <th scope="col">{t('meusDados.col.papel')}</th>
                      <th scope="col">{t('meusDados.col.squad')}</th>
                      <th scope="col">{t('meusDados.col.de')}</th>
                      <th scope="col">{t('meusDados.col.ate')}</th>
                    </tr>
                  </thead>
                  <tbody>
                    {dto.papeis.map((p, i) => (
                      <tr key={`${p.papel}-${i}`}>
                        <td>{t(`role.${p.papel}`)}</td>
                        <td>{p.squad ?? '—'}</td>
                        <td>{p.de ? dataCurta(p.de, lang) : t('meusDados.semLimite')}</td>
                        <td>{p.ate ? dataCurta(p.ate, lang) : t('meusDados.semLimite')}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </section>

          <section aria-labelledby="h-md-categorias">
            <h2 id="h-md-categorias">{t('meusDados.categorias')}</h2>
            <div className="tabela-rolagem" tabIndex={0} role="region" aria-labelledby="h-md-categorias">
              <table className="tabela" aria-labelledby="h-md-categorias">
                <thead>
                  <tr>
                    <th scope="col">{t('meusDados.col.categoria')}</th>
                    <th scope="col">{t('meusDados.col.finalidade')}</th>
                    <th scope="col">{t('meusDados.col.quemVe')}</th>
                    <th scope="col">{t('meusDados.col.retencao')}</th>
                  </tr>
                </thead>
                <tbody>
                  {dto.categorias.map((c) => {
                    const meses = dto.retencao[CHAVE_DA_RETENCAO[c.retencaoVariavel] ?? '']
                    return (
                      <tr key={c.categoria}>
                        <th scope="row">{t(`meusDados.categoria.${c.categoria}`)}</th>
                        <td>{t(`meusDados.finalidade.${c.categoria}`)}</td>
                        <td>{c.quemVe.map((r) => t(`role.${r}`)).join(', ')}</td>
                        <td>{meses === undefined ? '—' : t('meusDados.meses').replace('{n}', String(meses))}</td>
                      </tr>
                    )
                  })}
                </tbody>
              </table>
            </div>
          </section>

          <section aria-labelledby="h-md-acessos">
            <h2 id="h-md-acessos">{t('meusDados.acessos')}</h2>
            {dto.acessos.length === 0 ? (
              <p className="vazio">{t('meusDados.semAcessos')}</p>
            ) : (
              <div className="tabela-rolagem" tabIndex={0} role="region" aria-labelledby="h-md-acessos">
                <table className="tabela" aria-labelledby="h-md-acessos">
                  <thead>
                    <tr>
                      <th scope="col">{t('meusDados.col.quando')}</th>
                      <th scope="col">{t('meusDados.col.rota')}</th>
                    </tr>
                  </thead>
                  <tbody>
                    {dto.acessos.map((a, i) => (
                      <tr key={`${a.at}-${i}`}>
                        <td>{instanteCurto(a.at, lang)}</td>
                        <td><code>{a.rota}</code></td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </section>

          <p className="legenda-meta">{t('meusDados.canal')}</p>
        </>
      )}
    </CardPage>
  )
}
