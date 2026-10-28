import { useId, useState, type ReactNode } from 'react'
import { Link, useInRouterContext } from 'react-router-dom'
import { useLang } from '../i18n/LangProvider'
import { inteiro } from './format'
import { AjudaDoCartao } from './AjudaDoCartao'
import { useCatalogo, type CartaoCatalogo } from './useCatalogo'
import './cartao.css'

/** `✓` ok, `●` atenção, `▲` problema, `■` neutro: o estado nunca é só cor (símbolo + texto). */
export type EstadoDoCartao = 'ok' | 'atencao' | 'problema' | 'neutro'

const SIMBOLO_DO_ESTADO: Record<EstadoDoCartao, string> = { ok: '✓', atencao: '●', problema: '▲', neutro: '■' }

export type VarianteDoCartao = 'kpi' | 'serie' | 'tabela' | 'lista'

/** Em que situação o cartão está (além do normal). `incompleto` é uma prop à parte: mostra o número e o aviso. */
export type SituacaoDoCartao = 'normal' | 'carregando' | 'vazio' | 'erro' | 'semPermissao'

/** Altura mínima fixa por variante: o esqueleto ocupa o que o cartão carregado ocupa (sem salto de layout). */
const ALTURA_MINIMA: Record<VarianteDoCartao, string> = { kpi: '132px', serie: '240px', tabela: '220px', lista: '200px' }

/** Forma da natureza (junto do texto), para o chip não depender da cor. */
const GLIFO_DA_NATUREZA: Record<CartaoCatalogo['natureza'], string> = {
  ESTOQUE: '▤',
  FLUXO: '⇢',
  PRAZO: '◷',
  PESSOAS: '◉',
  QUALIDADE: '◈',
}

export type CartaoProps = {
  /** Id do cartão no catálogo (ex.: `TS-03`). A ajuda e a natureza vêm de lá. */
  id: string
  variante?: VarianteDoCartao
  /** Título já traduzido (o mesmo texto da chave `tituloChave` do catálogo). */
  titulo: string
  /** Nível do título na página (2 ou 3). */
  nivelDoTitulo?: 2 | 3
  /** Id do título, quando o corpo precisa nomear tabelas e regiões por ele (`aria-labelledby`). */
  idDoTitulo?: string
  /** Número já formatado pelo idioma, com unidade. Sempre renderizado como TEXTO. */
  valor?: string
  /** Símbolo decorativo antes do valor (ex.: ⏸); não substitui o texto do estado. */
  simboloDoValor?: string
  legenda?: string
  estado?: { tipo: EstadoDoCartao; texto: string }
  /** Variação contra o período anterior, já com símbolo e texto ("+12 vs semana passada"). */
  variacao?: string
  /** Tamanho da amostra. */
  n?: number
  /** Momento da última sincronização (ISO). */
  atualizadoEm?: string
  /** Relógio injetável (testes). */
  agora?: Date
  /** Dados incompletos: mostra o número e um aviso com o caminho para a Qualidade dos dados. */
  incompleto?: boolean
  situacao?: SituacaoDoCartao
  /** Presente = o cartão tem gaveta de tasks. O cartão inteiro vira a área clicável da ação. */
  aoAbrir?: () => void
  aoTentarDeNovo?: () => void
  /** Corpo das variantes `serie`, `tabela` e `lista`. */
  children?: ReactNode
}

/** "8 min", "3 h", "2 d" a partir do instante da última sincronização. */
function haQuantoTempo(iso: string, agora: Date, t: (k: string) => string): string | null {
  const ms = agora.getTime() - new Date(iso).getTime()
  if (Number.isNaN(ms) || ms < 0) return null
  const minutos = Math.floor(ms / 60_000)
  if (minutos < 1) return t('cartao.tempo.agora')
  if (minutos < 60) return t('cartao.tempo.min').replace('{n}', String(minutos))
  const horas = Math.floor(minutos / 60)
  if (horas < 24) return t('cartao.tempo.h').replace('{n}', String(horas))
  return t('cartao.tempo.d').replace('{n}', String(Math.floor(horas / 24)))
}

/**
 * O cartão do painel (anatomia: `docs/design/cartoes-gramatica-e-wireframes.md`, seção 2). Todo cartão de todas as
 * telas passa por aqui. O cartão é um `<article>`; a ação principal é UM botão cuja área cobre o cartão por `::after`
 * e o ⓘ fica acima dessa camada, então não há botão dentro de botão. Todo valor é renderizado como texto.
 */
export function Cartao(props: CartaoProps) {
  const { t, lang } = useLang()
  const { meta: metaDe } = useCatalogo()
  const meta = metaDe(props.id)
  const idGerado = useId()
  const idDoTitulo = props.idDoTitulo ?? idGerado
  const idDoConteudo = useId()
  const idDaAcao = useId()
  const emRouter = useInRouterContext()
  // O relógio é lido uma vez, na montagem: "atualizado há X" não precisa piscar a cada renderização.
  const [instanteDaMontagem] = useState(() => new Date())

  const variante = props.variante ?? 'kpi'
  const situacao = props.situacao ?? 'normal'
  // Cartão que o catálogo diz não ter tasks por trás não promete uma gaveta.
  const abre = props.aoAbrir && meta?.temDrill !== false && situacao === 'normal' ? props.aoAbrir : undefined
  const Titulo = props.nivelDoTitulo === 2 ? 'h2' : 'h3'
  const atualizado =
    props.atualizadoEm && situacao === 'normal' ? haQuantoTempo(props.atualizadoEm, props.agora ?? instanteDaMontagem, t) : null
  const temProcedencia = situacao === 'normal' && (props.n !== undefined || atualizado !== null || props.incompleto)

  const estado = props.estado && (
    <span className={`cartao-estado cartao-estado--${props.estado.tipo}`}>
      <span aria-hidden="true">{SIMBOLO_DO_ESTADO[props.estado.tipo]}</span> {props.estado.texto}
    </span>
  )
  const verTasks = abre && (
    <span className="cartao-ver" id={variante === 'kpi' ? undefined : idDaAcao}>
      {t('cartao.verTasks')} →
    </span>
  )

  let corpo: ReactNode = null
  if (situacao === 'carregando') {
    corpo = (
      <div className="cartao-esqueleto" aria-hidden="true">
        <div className="cartao-esq" />
        <div className="cartao-esq cartao-esq--curto" />
      </div>
    )
  } else if (situacao === 'vazio') {
    corpo = (
      <div className="cartao-mensagem">
        <p>{t('cartao.vazio')}</p>
        <p className="cartao-dica">{t('cartao.vazio.dica')}</p>
      </div>
    )
  } else if (situacao === 'erro') {
    corpo = (
      <div className="cartao-mensagem cartao-mensagem--erro" role="alert">
        <p>
          <span aria-hidden="true">▲</span> {t('cartao.erro')}
        </p>
        {props.aoTentarDeNovo && (
          <button type="button" className="btn cartao-tentar" onClick={props.aoTentarDeNovo}>
            {t('cartao.tentarDeNovo')}
          </button>
        )}
      </div>
    )
  } else if (situacao === 'semPermissao') {
    corpo = (
      <div className="cartao-mensagem">
        <p>
          <span aria-hidden="true">■</span> {t('cartao.semPermissao')}
        </p>
      </div>
    )
  } else if (variante === 'kpi') {
    const conteudo = (
      <span className="cartao-kpi" id={idDoConteudo}>
        <span className="cartao-valor">
          {props.simboloDoValor && <span aria-hidden="true" className="cartao-simbolo">{props.simboloDoValor} </span>}
          {props.valor}
        </span>
        {props.legenda && <span className="cartao-legenda">{props.legenda}</span>}
        {estado}
        {props.variacao && <span className="cartao-variacao">{props.variacao}</span>}
        {verTasks}
      </span>
    )
    corpo = abre ? (
      <button type="button" className="cartao-acao" aria-labelledby={`${idDoTitulo} ${idDoConteudo}`} onClick={abre}>
        {conteudo}
      </button>
    ) : (
      conteudo
    )
  } else {
    corpo = (
      <>
        <div className="cartao-corpo">{props.children}</div>
        {abre && (
          <button type="button" className="cartao-acao cartao-acao--rodape" aria-labelledby={`${idDoTitulo} ${idDaAcao}`} onClick={abre}>
            {verTasks}
          </button>
        )}
      </>
    )
  }

  const aviso = props.incompleto && situacao === 'normal' && (
    <span className="cartao-aviso">
      <span aria-hidden="true">●</span> {t('cartao.incompleto')}
      {' · '}
      {emRouter ? (
        <Link to="/qualidade">{t('cartao.incompleto.ver')}</Link>
      ) : (
        <a href="/qualidade">{t('cartao.incompleto.ver')}</a>
      )}
    </span>
  )

  return (
    <article
      className={`cartao cartao--${variante}${abre ? ' cartao--clicavel' : ''}`}
      aria-labelledby={idDoTitulo}
      aria-busy={situacao === 'carregando' ? true : undefined}
      style={{ minHeight: ALTURA_MINIMA[variante] }}
    >
      <header className="cartao-topo">
        <Titulo id={idDoTitulo} className="cartao-titulo">
          {props.titulo}
        </Titulo>
        {meta && (
          <span className="cartao-natureza">
            <span aria-hidden="true">{GLIFO_DA_NATUREZA[meta.natureza]}</span> {t(`catalogo.natureza.${meta.natureza}`)}
          </span>
        )}
        {meta && <AjudaDoCartao meta={meta} />}
      </header>
      {situacao === 'carregando' && <p className="so-leitor">{t('cartao.carregando')}</p>}
      {corpo}
      {temProcedencia && (
        <footer className="cartao-rodape">
          {props.n !== undefined && <span>{t('cartao.n').replace('{n}', inteiro(props.n, lang))}</span>}
          {atualizado && <span>{t('cartao.atualizadoHa').replace('{tempo}', atualizado)}</span>}
          {aviso}
        </footer>
      )}
    </article>
  )
}
