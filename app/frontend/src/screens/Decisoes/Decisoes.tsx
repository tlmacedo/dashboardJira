import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { useLang } from '../../i18n/LangProvider'
import { CardPage } from '../../shared/CardPage'
import { Cartao } from '../../shared/Cartao'
import { baixarCsv } from '../../shared/csv'
import { dataCurta, instanteCurto } from '../../shared/format'
import { Filtros, useFiltrosDaUrl } from '../../shared/Filtros'
import '../../shared/tabela.css'
import { fetchDecisoes, type DecisoesDto } from '../Distribuicao/api'
import { rotuloDaDecisao } from '../Distribuicao/rotulos'

/**
 * Decisões de distribuição (AU-01 do inventário; cartão DC-01): o que foi aprovado, trocado ou rejeitado, do mais recente ao
 * mais antigo, com quem decidiu e o motivo. Só coordenação e gerente. Sem contagem nem ordem por pessoa.
 */
export function Decisoes() {
  const { t, lang } = useLang()
  const [sp, aplicar] = useFiltrosDaUrl()
  const consulta = useQuery({
    queryKey: ['decisoes', sp.toString()], queryFn: () => fetchDecisoes(sp), placeholderData: keepPreviousData, retry: false,
  })
  const dto: DecisoesDto | undefined = consulta.data

  function exportar() {
    if (!dto) return
    baixarCsv('decisoes.csv', [
      [`# ${t('nav.decisoes')}`],
      [`# ${t('decisoes.periodo')}: ${dto.de} - ${dto.ate}`],
      [],
      [t('decisoes.col.quando'), t('decisoes.col.tipo'), t('decisoes.col.task'), t('decisoes.col.sugerido'), t('decisoes.col.escolhido'), t('decisoes.col.decisao'), t('decisoes.col.motivo'), t('decisoes.col.por')],
      ...dto.decisoes.map((d) => [d.decididoEm, t(`decisoes.tipo.${d.tipo}`), d.issueKey, d.sugerido ?? '', d.escolhido ?? '', t(`decisao.${d.decisao}`), d.motivo ?? '', d.decididoPor]),
    ])
  }

  return (
    <CardPage
      titulo={t('nav.decisoes')}
      pergunta={t('decisoes.pergunta')}
      filtros={<Filtros key={sp.toString()} campos={['periodo']} valor={sp} aoAplicar={aplicar} />}
      consulta={consulta}
      aoExportar={exportar}
    >
      {dto && (
        <Cartao id="DC-01" variante="tabela" nivelDoTitulo={2} idDoTitulo="h-dc-tabela" titulo={t('decisoes.tabela')}>
          <p className="legenda-meta">
            {t('decisoes.nota').replace('{de}', dataCurta(dto.de, lang)).replace('{ate}', dataCurta(dto.ate, lang))}
          </p>
          {dto.truncado && <p className="aviso" role="status">{t('decisoes.truncado')}</p>}
          {dto.decisoes.length === 0 ? (
            <p className="vazio">{t('decisoes.vazia')}</p>
          ) : (
            <div className="tabela-rolagem" tabIndex={0} role="region" aria-labelledby="h-dc-tabela">
              <table className="tabela" aria-labelledby="h-dc-tabela">
                <thead>
                  <tr>
                    <th scope="col">{t('decisoes.col.quando')}</th>
                    <th scope="col">{t('decisoes.col.tipo')}</th>
                    <th scope="col">{t('decisoes.col.task')}</th>
                    <th scope="col">{t('decisoes.col.sugerido')}</th>
                    <th scope="col">{t('decisoes.col.escolhido')}</th>
                    <th scope="col">{t('decisoes.col.decisao')}</th>
                    <th scope="col">{t('decisoes.col.motivo')}</th>
                    <th scope="col">{t('decisoes.col.por')}</th>
                  </tr>
                </thead>
                <tbody>
                  {dto.decisoes.map((d, i) => (
                    <tr key={`${d.issueKey}:${d.decididoEm}:${i}`}>
                      <td>{instanteCurto(d.decididoEm, lang)}</td>
                      <td>{t(`decisoes.tipo.${d.tipo}`)}</td>
                      <th scope="row">{d.issueKey}</th>
                      <td>{d.sugerido ?? '—'}</td>
                      <td>{d.escolhido ?? '—'}</td>
                      <td>{rotuloDaDecisao(d.decisao, t)}</td>
                      <td>{d.motivo ?? '—'}</td>
                      <td>{d.decididoPor}</td>
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
