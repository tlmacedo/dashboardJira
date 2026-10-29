import { useQuery } from '@tanstack/react-query'
import { createPortal } from 'react-dom'
import { ErroDaApi, fetchTarefa, type Tarefa } from '../api/client'
import { useLang } from '../i18n/LangProvider'
import { dataCurta, dataHora, numero, percentual } from './format'
import { linkSeguro } from './linkSeguro'
import { TabelaDeHistorico } from './TabelaDeHistorico'
import { textoDoAviso } from './useCard'
import { useModalAcessivel } from './useModalAcessivel'
import './gaveta.css'
import './tabela.css'

export type TarefaDrawerProps = {
  chave: string
  instancia?: string
  aoFechar: () => void
}

/** Símbolo do estado da categoria do status: o estado nunca depende só da cor. */
const SIMBOLO_DA_CATEGORIA: Record<string, string> = { DONE: '✓', ACTIVE: '●', WAIT: '●', BLOCKED: '▲', TODO: '■' }

/**
 * Gaveta universal da task (`GET /api/tarefa/{key}`): situação, tempo por status, posse, fatores de risco, campos do
 * dicionário, histórico (só quando o backend o manda: coordenação e gerente) e o botão "Abrir no Jira" (só com o link pronto
 * do backend). Todo valor vindo da API entra como texto. Foco no Fechar ao abrir, Tab preso, Esc fecha e o foco volta à
 * linha de origem; o resto da página fica inerte.
 */
export function TarefaDrawer(props: TarefaDrawerProps) {
  const { t } = useLang()
  const { chave, aoFechar } = props
  const { portal, raiz, fechar, aoTeclar } = useModalAcessivel(aoFechar)
  const consulta = useQuery({
    queryKey: ['tarefa', chave, props.instancia ?? ''],
    queryFn: () => fetchTarefa(chave, props.instancia),
    retry: false,
  })
  const tarefa = consulta.data
  const status = consulta.error instanceof ErroDaApi ? consulta.error.status : null
  const titulo = t('tarefa.titulo').replace('{key}', chave)
  const link = linkSeguro(tarefa?.linkJira ?? null)

  return createPortal(
    <div className="gaveta-fundo gaveta-fundo--tarefa" onClick={aoFechar}>
      <div
        ref={raiz}
        className="gaveta"
        role="dialog"
        aria-modal="true"
        aria-labelledby="tarefa-titulo"
        onClick={(e) => e.stopPropagation()}
        onKeyDown={aoTeclar}
      >
        <header className="gaveta-topo">
          <div>
            <h2 id="tarefa-titulo">{titulo}</h2>
            {tarefa && <p className="sub">{tarefa.resumo}</p>}
          </div>
          <div className="gaveta-acoes">
            {link && (
              <a className="gaveta-link" href={link} target="_blank" rel="noopener noreferrer">
                {t('gaveta.abrirNoJira')} <span aria-hidden="true">↗</span>
              </a>
            )}
            <button type="button" className="btn" ref={fechar} onClick={aoFechar}>
              {t('gaveta.fechar')}
            </button>
          </div>
        </header>

        <div className="gaveta-corpo">
          {consulta.isPending && (
            <p className="vazio" aria-busy="true">
              {t('app.carregando')}
            </p>
          )}
          {status === 404 && <p className="estado estado--erro">{t('tarefa.naoEncontrada')}</p>}
          {status === 403 && <p className="estado estado--erro">{t('gaveta.semPermissao')}</p>}
          {status === 400 && <p className="estado estado--erro">{t('tarefa.chaveInvalida')}</p>}
          {consulta.isError && status !== 404 && status !== 403 && status !== 400 && (
            <p className="estado estado--erro">
              {t('tarefa.erro')}{' '}
              <button type="button" className="btn" onClick={() => consulta.refetch()}>
                {t('gaveta.tentarDeNovo')}
              </button>
            </p>
          )}
          {tarefa && <Conteudo tarefa={tarefa} titulo={titulo} />}
        </div>
      </div>
    </div>,
    portal,
  )
}

function Conteudo({ tarefa, titulo }: { tarefa: Tarefa; titulo: string }) {
  const { t, lang } = useLang()
  const data = (iso: string | null) => (iso ? dataCurta(iso, lang) : '—')
  const campos = Object.entries(tarefa.campos)
  return (
    <>
      {tarefa.avisos.length > 0 && (
        <ul className="gaveta-avisos">
          {tarefa.avisos.map((a) => (
            <li key={`${a.codigo}:${JSON.stringify(a.params ?? {})}`}>{textoDoAviso(a, t)}</li>
          ))}
        </ul>
      )}

      <section aria-labelledby="tarefa-situacao" className="tarefa-secao">
        <h3 id="tarefa-situacao">{t('tarefa.situacao')}</h3>
        <dl className="tarefa-dl">
          <dt>{t('tarefa.status')}</dt>
          <dd>
            <span aria-hidden="true">{SIMBOLO_DA_CATEGORIA[tarefa.categoria] ?? '■'}</span> {tarefa.status}{' '}
            <span className="tarefa-cat">({t(`tarefa.cat.${tarefa.categoria}`)})</span>
          </dd>
          <dt>{t('tarefa.tipo')}</dt>
          <dd>{tarefa.tipo}</dd>
          <dt>{t('tarefa.responsavel')}</dt>
          <dd>{tarefa.responsavel ?? t('posse.outraPessoa')}</dd>
          <dt>{t('tarefa.prioridade')}</dt>
          <dd>{tarefa.prioridade ?? '—'}</dd>
          <dt>{t('tarefa.resolucao')}</dt>
          <dd>{tarefa.resolucao ?? '—'}</dd>
          <dt>{t('tarefa.criada')}</dt>
          <dd>{dataHora(tarefa.criada, lang)}</dd>
          <dt>{t('tarefa.atualizada')}</dt>
          <dd>{tarefa.atualizada ? dataHora(tarefa.atualizada, lang) : '—'}</dd>
          <dt>{t('tarefa.resolvida')}</dt>
          <dd>{tarefa.resolvida ? dataHora(tarefa.resolvida, lang) : '—'}</dd>
          <dt>{t('tarefa.vencimento')}</dt>
          <dd>{data(tarefa.vencimento)}</dd>
          <dt>{t('tarefa.vencimentoOriginal')}</dt>
          <dd>{data(tarefa.vencimentoOriginal)}</dd>
          {tarefa.labels.length > 0 && (
            <>
              <dt>{t('tarefa.labels')}</dt>
              <dd>
                <ul className="tarefa-labels">
                  {tarefa.labels.map((l) => (
                    <li key={l} className="mono">
                      {l}
                    </li>
                  ))}
                </ul>
              </dd>
            </>
          )}
        </dl>
      </section>

      <section aria-labelledby="tarefa-tempo" className="tarefa-secao">
        <h3 id="tarefa-tempo">{t('tarefa.tempoPorStatus')}</h3>
        {tarefa.tempoPorStatus.length === 0 ? (
          <p className="vazio">{t('tarefa.semDados')}</p>
        ) : (
          <table className="tabela" aria-labelledby="tarefa-tempo">
            <thead>
              <tr>
                <th scope="col">{t('tarefa.col.status')}</th>
                <th scope="col" className="num">
                  {t('tarefa.col.dias')}
                </th>
              </tr>
            </thead>
            <tbody>
              {tarefa.tempoPorStatus.map((s) => (
                <tr key={s.status}>
                  <th scope="row">{s.status}</th>
                  <td className="num">{numero(s.dias, lang)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </section>

      <section aria-labelledby="tarefa-posse" className="tarefa-secao">
        <h3 id="tarefa-posse">{t('tarefa.posse')}</h3>
        {tarefa.posse.length === 0 ? (
          <p className="vazio">{t('tarefa.semDados')}</p>
        ) : (
          <table className="tabela" aria-labelledby="tarefa-posse">
            <thead>
              <tr>
                <th scope="col">{t('tarefa.col.pessoa')}</th>
                <th scope="col" className="num">
                  {t('tarefa.col.dias')}
                </th>
                <th scope="col" className="num">
                  {t('tarefa.col.fracao')}
                </th>
              </tr>
            </thead>
            <tbody>
              {tarefa.posse.map((p, i) => (
                <tr key={`${p.pessoa ?? (p.semResponsavel ? 'sem' : 'outra')}:${i}`}>
                  <th scope="row">{p.pessoa ?? (p.semResponsavel ? t('tarefa.semResponsavel') : t('posse.outraPessoa'))}</th>
                  <td className="num">{numero(p.dias, lang)}</td>
                  <td className="num">{percentual(p.fracao, lang)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </section>

      <section aria-labelledby="tarefa-fatores" className="tarefa-secao">
        <h3 id="tarefa-fatores">{t('tarefa.fatores')}</h3>
        {tarefa.fatoresDeRisco.length === 0 ? (
          <p className="vazio">{t('tarefa.fatores.nenhum')}</p>
        ) : (
          <ul>
            {tarefa.fatoresDeRisco.map((f) => (
              <li key={f}>{t(`riscos.fator.${f}`)}</li>
            ))}
          </ul>
        )}
      </section>

      {campos.length > 0 && (
        <section aria-labelledby="tarefa-campos" className="tarefa-secao">
          <h3 id="tarefa-campos">{t('tarefa.campos')}</h3>
          <dl className="tarefa-dl">
            {campos.map(([nome, valor]) => (
              <div key={nome} className="tarefa-par">
                <dt>{nome}</dt>
                <dd>{valor}</dd>
              </div>
            ))}
          </dl>
        </section>
      )}

      {tarefa.historico && (
        <section aria-labelledby="tarefa-historico" className="tarefa-secao">
          <h3 id="tarefa-historico">{t('tarefa.historico')}</h3>
          {tarefa.historico.length === 0 ? (
            <p className="vazio">{t('auditoria.historico.vazio')}</p>
          ) : (
            <TabelaDeHistorico linhas={tarefa.historico} rotulo={`${t('tarefa.historico')} ${titulo}`} />
          )}
        </section>
      )}
    </>
  )
}
