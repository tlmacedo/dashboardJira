import { useQuery } from '@tanstack/react-query'
import { useLang } from '../../i18n/LangProvider'
import { ErroDaApi } from '../../api/client'
import { Cartao } from '../../shared/Cartao'
import { fetchSyncStatus, type ExecucaoDto, type InstanciaStatusDto, type SyncStatusDto } from './api'
import './sincronizacao.css'

/** Fuso fixo do painel (CLAUDE.md): as datas da tela são sempre mostradas em Manaus. */
const FUSO = 'America/Manaus'

/** A cor nunca é o único sinal: todo estado leva símbolo e texto. */
const SIMBOLO_STATUS: Record<string, string> = { OK: '✓', FALHA: '▲', INCOMPLETA: '●', CANCELADA: '●', EM_ANDAMENTO: '●' }

/** Códigos de limite próprio: a execução parou no teto com o cursor avançando (não conta como falha seguida). */
const CODIGOS_DE_LIMITE = new Set(['TETO_ITENS', 'ORCAMENTO_REQUISICOES', 'TEMPO_ESGOTADO'])
const SIMBOLO_GATE: Record<string, string> = {
  LIBERADA: '✓',
  DESLIGADO_PELA_FLAG: '●',
  INSTANCIA_NAO_CONFIGURADA: '●',
  SUSPENSA_POR_ERRO: '▲',
}
const CLASSE_STATUS: Record<string, string> = { OK: 'ok', FALHA: 'ruim', INCOMPLETA: 'aviso', CANCELADA: 'aviso', EM_ANDAMENTO: 'aviso' }
const CLASSE_GATE: Record<string, string> = {
  LIBERADA: 'ok',
  DESLIGADO_PELA_FLAG: 'aviso',
  INSTANCIA_NAO_CONFIGURADA: 'aviso',
  SUSPENSA_POR_ERRO: 'ruim',
}

type T = (chave: string) => string

export function Sincronizacao() {
  const { t, lang } = useLang()
  const consulta = useQuery({ queryKey: ['sync-status'], queryFn: fetchSyncStatus, retry: false })
  const dto = consulta.data
  const status = consulta.error instanceof ErroDaApi ? consulta.error.status : null

  return (
    <div className="sinc">
      <div className="top">
        <div>
          <h1>{t('nav.sincronizacao')}</h1>
          <p className="sub">{t('sinc.sub')}</p>
        </div>
        <div className="top-acoes">
          <button type="button" className="btn" onClick={() => consulta.refetch()} disabled={consulta.isFetching}>
            {t('sinc.atualizar')}
          </button>
        </div>
      </div>

      {consulta.isPending && <p className="sinc-carregando">{t('app.carregando')}</p>}

      {consulta.isError && !dto && (
        <div className="sinc-erro" role="alert">
          {status === 403 ? (
            <p>{t('sinc.semPermissao')}</p>
          ) : (
            <>
              <p>
                {t('sinc.falhaCarregar')}
                {status ? ` (HTTP ${status})` : ''}
              </p>
              <button type="button" className="btn" onClick={() => consulta.refetch()}>
                {t('sinc.tentarDeNovo')}
              </button>
            </>
          )}
        </div>
      )}

      {dto && <Conteudo dto={dto} t={t} lang={lang} />}
    </div>
  )
}

function Conteudo({ dto, t, lang }: { dto: SyncStatusDto; t: T; lang: string }) {
  const desligado = dto.modo === 'DESLIGADO_PELA_FLAG'
  return (
    <>
      {/* SY-01: o modo do sincronizador. Sem tasks por trás, então o cartão não tem gaveta. */}
      <Cartao id="SY-01" variante="lista" nivelDoTitulo={2} titulo={t('sinc.modo')}>
        <div className={`sinc-faixa sinc-faixa--${desligado ? 'aviso' : 'ok'}`} role="status">
          <strong>{`${desligado ? '●' : '✓'} ${rotulo(t, `sinc.modo.${dto.modo}`, dto.modo)}`}</strong>
          <span>{rotulo(t, `sinc.modo.nota.${dto.modo}`, '')}</span>
        </div>
      </Cartao>

      {dto.instancias.length === 0 && <p className="sinc-vazio">{t('sinc.semInstancias')}</p>}
      {dto.instancias.map((i) => (
        <Instancia key={i.instancia} inst={i} t={t} lang={lang} />
      ))}

      {/* SY-03: a última limpeza local. */}
      <Cartao id="SY-03" variante="tabela" nivelDoTitulo={2} idDoTitulo="sinc-retencao" titulo={t('sinc.retencao')}>
        <p className="sinc-nota">{t('sinc.retencao.nota')}</p>
        {dto.retencao ? (
          <TabelaExecucoes nome={t('sinc.retencao')} execucoes={[dto.retencao]} t={t} lang={lang} />
        ) : (
          <p className="sinc-vazio">{t('sinc.retencao.vazia')}</p>
        )}
      </Cartao>
    </>
  )
}

function Instancia({ inst, t, lang }: { inst: InstanciaStatusDto; t: T; lang: string }) {
  const id = `sinc-inst-${inst.instancia}`
  const classe = CLASSE_GATE[inst.gate] ?? 'aviso'
  return (
    // SY-02: as últimas execuções de uma instância; o título leva o nome da instância.
    <Cartao id="SY-02" variante="tabela" nivelDoTitulo={2} idDoTitulo={id} titulo={`${t('sinc.instancia')} ${inst.instancia}`}>
      <dl className="sinc-dl">
        <div>
          <dt>{t('sinc.gate')}</dt>
          <dd className={`sinc-gate sinc-${classe}`}>
            {`${SIMBOLO_GATE[inst.gate] ?? '●'} ${rotulo(t, `sinc.gate.${inst.gate}`, inst.gate)}`}
          </dd>
        </div>
        <div>
          <dt>{t('sinc.cursor')}</dt>
          <dd>{data(inst.cursor, lang)}</dd>
        </div>
        <div>
          <dt>{t('sinc.reconciliacao')}</dt>
          <dd>{data(inst.ultimaReconciliacao, lang)}</dd>
        </div>
        {inst.suspensa && (
          <div>
            <dt>{t('sinc.suspensao')}</dt>
            <dd className="sinc-ruim">
              {`▲ ${textoDoCodigo(inst.suspensa, t) ?? ''}`} <code>{inst.suspensa}</code>
              <span className="sinc-nota">{t('sinc.suspensa.nota')}</span>
            </dd>
          </div>
        )}
      </dl>
      {inst.execucoes.length === 0 ? (
        <p className="sinc-vazio">{t('sinc.execucoes.vazia')}</p>
      ) : (
        <TabelaExecucoes nome={`${t('sinc.execucoes')} — ${inst.instancia}`} execucoes={inst.execucoes} t={t} lang={lang} />
      )}
    </Cartao>
  )
}

function TabelaExecucoes({ nome, execucoes, t, lang }: { nome: string; execucoes: ExecucaoDto[]; t: T; lang: string }) {
  return (
    <div className="sinc-rolagem">
      <table className="sinc-tab" aria-label={nome}>
        <thead>
          <tr>
            <th scope="col">{t('sinc.col.tipo')}</th>
            <th scope="col">{t('sinc.col.status')}</th>
            <th scope="col">{t('sinc.col.inicio')}</th>
            <th scope="col">{t('sinc.col.fim')}</th>
            <th scope="col" className="num">{t('sinc.col.issues')}</th>
            <th scope="col" className="num">{t('sinc.col.eventos')}</th>
            <th scope="col" className="num">{t('sinc.col.requisicoes')}</th>
            <th scope="col" className="num">{t('sinc.col.incompletas')}</th>
            <th scope="col" className="num">{t('sinc.col.usuarios')}</th>
            <th scope="col" className="num">{t('sinc.col.removidas')}</th>
            <th scope="col">{t('sinc.col.resultado')}</th>
          </tr>
        </thead>
        <tbody>
          {execucoes.map((e, i) => {
            // Bateu num limite nosso (itens, requisições ou tempo): progresso parcial, não falha do Jira.
            const status = e.status === 'FALHA' && e.codigoErro && CODIGOS_DE_LIMITE.has(e.codigoErro) ? 'INCOMPLETA' : e.status
            return (
            <tr key={`${e.inicio}:${e.tipo}:${i}`}>
              <td>{rotulo(t, `sinc.tipo.${e.tipo}`, e.tipo)}</td>
              <td className={`sinc-${CLASSE_STATUS[status] ?? 'aviso'}`}>
                {`${SIMBOLO_STATUS[status] ?? '●'} ${rotulo(t, `sinc.status.${status}`, status)}`}
              </td>
              <td>{data(e.inicio, lang)}</td>
              <td>{data(e.fim, lang)}</td>
              <td className="num">{e.issues}</td>
              <td className="num">{e.eventos}</td>
              <td className="num">{e.requisicoes}</td>
              <td className="num">{e.incompletas}</td>
              <td className="num">{e.usuariosNaoResolvidos}</td>
              <td className="num">{e.removidas}</td>
              <td>
                {e.codigoErro ? (
                  <>
                    {textoDoCodigo(e.codigoErro, t) ?? ''} <code>{e.codigoErro}</code>
                  </>
                ) : (
                  '—'
                )}
              </td>
            </tr>
            )
          })}
        </tbody>
      </table>
    </div>
  )
}

/** Tradução por chave; sem tradução usa [padrao] (nunca mostra a chave crua). */
function rotulo(t: T, chave: string, padrao: string): string {
  const texto = t(chave)
  return texto === chave ? padrao : texto
}

/**
 * Texto do código de erro (o backend só manda códigos, nunca mensagem do Jira). `HTTP_nnn` sem tradução
 * própria usa o texto genérico; código desconhecido devolve nulo (a tela mostra só o código técnico).
 */
function textoDoCodigo(codigo: string, t: T): string | null {
  const direto = t(`sinc.erro.${codigo}`)
  if (direto !== `sinc.erro.${codigo}`) return direto
  const http = /^HTTP_(\d+)$/.exec(codigo)
  if (http) return t('sinc.erro.HTTP_GENERICO').replace('{n}', http[1])
  return null
}

function data(iso: string | null, lang: string): string {
  if (!iso) return '—'
  const d = new Date(iso)
  if (Number.isNaN(d.getTime())) return '—'
  return d.toLocaleString(lang === 'en' ? 'en-US' : 'pt-BR', { timeZone: FUSO, dateStyle: 'short', timeStyle: 'short' })
}
