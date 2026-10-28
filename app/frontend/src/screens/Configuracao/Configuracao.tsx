import { useQuery } from '@tanstack/react-query'
import { useLang } from '../../i18n/LangProvider'
import { CardPage } from '../../shared/CardPage'
import { Cartao } from '../../shared/Cartao'
import { inteiro } from '../../shared/format'
import '../../shared/tabela.css'
import { fetchConfiguracao } from './api'

/**
 * Configuração em leitura (cartão CF-01): o ambiente, o modo da sincronização e, de cada arquivo de regras, versão, hash e se vale
 * o arquivo real ou o modelo. Só o gerente. O painel não edita nada e nunca exibe segredo, URL, caminho nem dado de pessoas.
 */
export function Configuracao() {
  const { t, lang } = useLang()
  const consulta = useQuery({ queryKey: ['configuracao'], queryFn: fetchConfiguracao, retry: false })
  const dto = consulta.data
  const sim = (v: boolean) => (v ? `● ${t('configuracao.sim')}` : `✓ ${t('configuracao.nao')}`)

  return (
    <CardPage titulo={t('nav.configuracao')} pergunta={t('configuracao.pergunta')} filtros={null} consulta={consulta}>
      {dto && (
        <Cartao id="CF-01" variante="tabela" nivelDoTitulo={2} idDoTitulo="h-cf" titulo={t('configuracao.arquivos')}>
          <dl className="ficha">
            <dt>{t('configuracao.devMode')}</dt>
            <dd>{sim(dto.devMode)}</dd>
            <dt>{t('configuracao.liveEnabled')}</dt>
            <dd>{sim(dto.liveEnabled)}</dd>
            <dt>{t('configuracao.modoSync')}</dt>
            <dd>{t(`configuracao.modo.${dto.modoSync}`)}</dd>
            <dt>{t('configuracao.pessoas')}</dt>
            <dd>{inteiro(dto.pessoas, lang)}</dd>
            <dt>{t('configuracao.squads')}</dt>
            <dd>{inteiro(dto.squads, lang)}</dd>
          </dl>
          <p className="legenda-meta">{t('configuracao.nota')}</p>
          <div className="tabela-rolagem" tabIndex={0} role="region" aria-labelledby="h-cf">
            <table className="tabela" aria-labelledby="h-cf">
              <thead>
                <tr>
                  <th scope="col">{t('configuracao.col.arquivo')}</th>
                  <th scope="col" className="num">{t('configuracao.col.versao')}</th>
                  <th scope="col">{t('configuracao.col.origem')}</th>
                  <th scope="col">{t('configuracao.col.hash')}</th>
                </tr>
              </thead>
              <tbody>
                {dto.arquivos.map((a) => (
                  <tr key={a.nome}>
                    <th scope="row">{a.nome}</th>
                    <td className="num">{a.versao === null ? '—' : a.versao}</td>
                    <td>{a.origem === 'real' ? `✓ ${t('configuracao.origem.real')}` : `■ ${t('configuracao.origem.exemplo')}`}</td>
                    <td><code title={a.sha256}>{a.sha256.slice(0, 12)}</code></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </Cartao>
      )}
    </CardPage>
  )
}
