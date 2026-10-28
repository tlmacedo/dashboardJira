import { useMemo, useState, type ReactNode } from 'react'
import { useLang } from '../i18n/LangProvider'
import './cardpage.css'

export type Coluna<T> = {
  id: string
  cabecalho: string
  /** Valor usado na ordenação (nulo vai para o fim, nos dois sentidos). */
  valor: (linha: T) => string | number | null
  /** Conteúdo da célula; sem ele vale o próprio valor (vazio = "—"). */
  render?: (linha: T) => ReactNode
  numerica?: boolean
}

export type TabelaOrdenavelProps<T> = {
  colunas: Coluna<T>[]
  linhas: T[]
  chave: (linha: T, indice: number) => string
  /** Nome da tabela (leitor de tela). */
  rotulo: string
  /** Coluna da ordenação inicial; sem ela a ordem recebida. */
  inicial?: { id: string; descendente?: boolean }
  /** A primeira coluna é o cabeçalho de linha (chave da task). */
  primeiraEhCabecalhoDeLinha?: boolean
}

/**
 * Tabela ordenável por qualquer coluna (clique no cabeçalho; `aria-sort` informa o sentido). Não tem coluna de posição:
 * a ordem é só a do usuário. Todo valor entra como texto.
 */
export function TabelaOrdenavel<T>(props: TabelaOrdenavelProps<T>) {
  const { t } = useLang()
  const [ordem, setOrdem] = useState<{ id: string; descendente: boolean } | null>(
    props.inicial ? { id: props.inicial.id, descendente: props.inicial.descendente ?? false } : null,
  )

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

  function alternar(id: string) {
    setOrdem((o) => (o && o.id === id ? { id, descendente: !o.descendente } : { id, descendente: false }))
  }

  return (
    <div className="tabela-rolagem" tabIndex={0} role="region" aria-label={props.rotulo}>
      <table className="tabela" aria-label={props.rotulo}>
        <thead>
          <tr>
            {props.colunas.map((c) => {
              const ativa = ordem?.id === c.id
              return (
                <th
                  key={c.id}
                  scope="col"
                  className={c.numerica ? 'num' : undefined}
                  aria-sort={ativa ? (ordem.descendente ? 'descending' : 'ascending') : 'none'}
                >
                  <button type="button" className="ordenar-botao" onClick={() => alternar(c.id)}>
                    {c.cabecalho}
                    <span aria-hidden="true">{ativa ? (ordem.descendente ? ' ▼' : ' ▲') : ''}</span>
                    {ativa && <span className="so-leitor"> {t(ordem.descendente ? 'tabela.decrescente' : 'tabela.crescente')}</span>}
                  </button>
                </th>
              )
            })}
          </tr>
        </thead>
        <tbody>
          {linhas.map((l, i) => (
            <tr key={props.chave(l, i)}>
              {props.colunas.map((c, k) => {
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
              })}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
