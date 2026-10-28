import { useId, useMemo, useRef, useState, type KeyboardEvent, type ReactNode } from 'react'
import { useLang } from '../i18n/LangProvider'
import './cardpage.css'
import './tabela.css'

export type Coluna<T> = {
  id: string
  cabecalho: string
  /** Valor usado na ordenação (nulo vai para o fim, nos dois sentidos). */
  valor: (linha: T) => string | number | null
  /** Conteúdo da célula; sem ele vale o próprio valor (vazio = "—"). */
  render?: (linha: T) => ReactNode
  numerica?: boolean
}

export type Ordenacao = { id: string; descendente?: boolean }

export type TabelaVirtualProps<T> = {
  colunas: Coluna<T>[]
  linhas: T[]
  chave: (linha: T, indice: number) => string
  /** Nome da tabela (leitor de tela). */
  rotulo: string
  /** Coluna da ordenação inicial; sem ela a ordem recebida. */
  inicial?: Ordenacao
  /** A primeira coluna é o cabeçalho de linha (chave da task). */
  primeiraEhCabecalhoDeLinha?: boolean
  /** Altura de cada linha na tabela virtual, em px (padrão 36). */
  alturaLinha?: number
  /** Altura da janela da tabela virtual, em px (padrão 480). */
  altura?: number
  /** Acima desta quantidade de linhas só a janela visível é desenhada (padrão 200). */
  limiarVirtual?: number
  /** Enter na linha ativa (só na tabela virtual; a tabela comum abre pelo botão da própria célula). */
  aoAbrir?: (linha: T) => void
}

/** Linhas extras desenhadas acima e abaixo da janela, para a rolagem não mostrar vazio. */
const FOLGA = 10

/**
 * Tabela ordenável por qualquer coluna (clique no cabeçalho; `aria-sort` informa o sentido) que vira VIRTUAL acima de
 * [limiarVirtual] linhas: desenha só as da janela visível (mais [FOLGA] de cada lado), com espaçadores de altura no
 * lugar das demais, cabeçalho fixo, `role="grid"` com `aria-rowcount` da lista inteira e `aria-rowindex` em cada linha.
 *
 * Teclado na tabela virtual (a tabela tem o foco, a linha ativa é `aria-activedescendant`): ↑ e ↓ movem uma linha,
 * PageUp e PageDown uma janela, Home e End vão ao início e ao fim, Enter abre a linha ativa ([aoAbrir]). A ordenação é no
 * cliente, sobre a lista inteira, e não muda a contagem. Não há coluna de posição. Todo valor entra como texto.
 * Quem exporta CSV usa a lista inteira que já tem, nunca a janela.
 */
export function TabelaVirtual<T>(props: TabelaVirtualProps<T>) {
  const { t } = useLang()
  const { alturaLinha = 36, altura = 480, limiarVirtual = 200 } = props
  const id = useId()
  const rolagem = useRef<HTMLDivElement>(null)
  const [ordem, setOrdem] = useState<{ id: string; descendente: boolean } | null>(
    props.inicial ? { id: props.inicial.id, descendente: props.inicial.descendente ?? false } : null,
  )
  const [scrollTop, setScrollTop] = useState(0)
  const [ativa, setAtiva] = useState<number | null>(null)

  const linhas = useMemo(() => {
    if (!ordem) return props.linhas
    const coluna = props.colunas.find((c) => c.id === ordem.id)
    if (!coluna) return props.linhas
    const sinal = ordem.descendente ? -1 : 1
    return [...props.linhas].sort((a, b) => {
      const va = coluna.valor(a)
      const vb = coluna.valor(b)
      if (va === null && vb === null) return 0
      if (va === null) return 1
      if (vb === null) return -1
      if (typeof va === 'number' && typeof vb === 'number') return (va - vb) * sinal
      return String(va).localeCompare(String(vb), undefined, { numeric: true }) * sinal
    })
  }, [ordem, props.linhas, props.colunas])

  const virtual = linhas.length > limiarVirtual
  const total = linhas.length
  const visiveis = Math.max(1, Math.floor(altura / alturaLinha))
  const primeira = Math.min(total - 1, Math.max(0, Math.floor(scrollTop / alturaLinha)))
  const inicio = virtual ? Math.max(0, primeira - FOLGA) : 0
  const fim = virtual ? Math.min(total, primeira + Math.ceil(altura / alturaLinha) + FOLGA) : total

  function alternar(coluna: string) {
    setOrdem((o) => (o && o.id === coluna ? { id: coluna, descendente: !o.descendente } : { id: coluna, descendente: false }))
  }

  /** Move a linha ativa e rola o contêiner só o necessário para ela ficar na janela. */
  function irPara(indice: number) {
    const i = Math.min(total - 1, Math.max(0, indice))
    setAtiva(i)
    const topo = i * alturaLinha
    let novo = scrollTop
    if (topo < scrollTop) novo = topo
    else if (topo + alturaLinha > scrollTop + visiveis * alturaLinha) novo = topo + alturaLinha - visiveis * alturaLinha
    if (novo !== scrollTop) {
      setScrollTop(novo)
      if (rolagem.current) rolagem.current.scrollTop = novo
    }
  }

  function aoTeclar(e: KeyboardEvent) {
    if (!virtual || total === 0) return
    const atual = ativa ?? -1
    const alvo: Record<string, number> = {
      ArrowDown: atual + 1,
      ArrowUp: atual < 0 ? 0 : atual - 1,
      PageDown: atual + visiveis,
      PageUp: atual - visiveis,
      Home: 0,
      End: total - 1,
    }
    if (e.key in alvo) {
      e.preventDefault()
      irPara(alvo[e.key])
    } else if (e.key === 'Enter' && ativa !== null && props.aoAbrir) {
      e.preventDefault()
      props.aoAbrir(linhas[ativa])
    }
  }

  const cabecalho = (
    <thead>
      <tr aria-rowindex={virtual ? 1 : undefined}>
        {props.colunas.map((c) => {
          const ordenada = ordem?.id === c.id
          return (
            <th
              key={c.id}
              scope="col"
              className={c.numerica ? 'num' : undefined}
              aria-sort={ordenada ? (ordem.descendente ? 'descending' : 'ascending') : 'none'}
            >
              <button type="button" className="ordenar-botao" onClick={() => alternar(c.id)}>
                {c.cabecalho}
                <span aria-hidden="true">{ordenada ? (ordem.descendente ? ' ▼' : ' ▲') : ''}</span>
                {ordenada && <span className="so-leitor"> {t(ordem.descendente ? 'tabela.decrescente' : 'tabela.crescente')}</span>}
              </button>
            </th>
          )
        })}
      </tr>
    </thead>
  )

  const celulas = (l: T) =>
    props.colunas.map((c, k) => {
      const conteudo = c.render ? c.render(l) : (c.valor(l) ?? '—')
      return k === 0 && props.primeiraEhCabecalhoDeLinha ? (
        <th key={c.id} scope="row" className="mono">
          {conteudo}
        </th>
      ) : (
        <td key={c.id} className={c.numerica ? 'num' : undefined}>
          {conteudo}
        </td>
      )
    })

  if (!virtual) {
    return (
      <div className="tabela-rolagem" tabIndex={0} role="region" aria-label={props.rotulo}>
        <table className="tabela" aria-label={props.rotulo}>
          {cabecalho}
          <tbody>
            {linhas.map((l, i) => (
              <tr key={props.chave(l, i)}>{celulas(l)}</tr>
            ))}
          </tbody>
        </table>
      </div>
    )
  }

  const idDaLinha = (i: number) => `${id}-l${i}`
  const janela = linhas.slice(inicio, fim)
  return (
    <div
      ref={rolagem}
      className="tabela-rolagem tabela-virtual"
      style={{ height: altura }}
      onScroll={(e) => setScrollTop(e.currentTarget.scrollTop)}
    >
      <table
        className="tabela tabela--virtual"
        role="grid"
        aria-label={props.rotulo}
        aria-rowcount={total + 1}
        aria-activedescendant={ativa !== null && ativa >= inicio && ativa < fim ? idDaLinha(ativa) : undefined}
        tabIndex={0}
        onKeyDown={aoTeclar}
      >
        {cabecalho}
        <tbody>
          {inicio > 0 && (
            <tr aria-hidden="true" style={{ height: inicio * alturaLinha }}>
              <td colSpan={props.colunas.length} />
            </tr>
          )}
          {janela.map((l, k) => {
            const i = inicio + k
            return (
              <tr
                key={props.chave(l, i)}
                id={idDaLinha(i)}
                aria-rowindex={i + 2}
                aria-selected={ativa === i}
                style={{ height: alturaLinha }}
                onClick={() => setAtiva(i)}
              >
                {celulas(l)}
              </tr>
            )
          })}
          {fim < total && (
            <tr aria-hidden="true" style={{ height: (total - fim) * alturaLinha }}>
              <td colSpan={props.colunas.length} />
            </tr>
          )}
        </tbody>
      </table>
    </div>
  )
}
