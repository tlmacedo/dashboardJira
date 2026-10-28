import { useLang } from '../i18n/LangProvider'
import { inteiro } from './format'
import './cardpage.css'

export type ItemDeBarra = { chave: string; rotulo: string; n: number }

export type ContagensEmBarrasProps = {
  itens: ItemDeBarra[]
  /** Nome da tabela (leitor de tela). */
  rotulo: string
  /** Texto do cabeçalho da coluna do rótulo. */
  cabecalho: string
  /** Presente = o número vira botão que abre as tasks daquele recorte. */
  aoAbrir?: (chave: string) => void
  /** Barra proporcional ao maior valor (um gráfico); sem ela é só a tabela de contagens. */
  barras?: boolean
  /** Mostrar "—" no lugar dos números (aviso bloqueante). */
  indisponivel?: boolean
}

/**
 * Lista de contagens por recorte: rótulo, barra opcional e o número. O número é SEMPRE texto; a barra é decorativa
 * (`aria-hidden`), então a informação nunca depende de cor ou de largura. A ordem é a recebida (ordem do fluxo, de faixa
 * ou alfabética); nunca reordena por valor.
 */
export function ContagensEmBarras(props: ContagensEmBarrasProps) {
  const { lang } = useLang()
  const maior = Math.max(1, ...props.itens.map((i) => i.n))
  return (
    <div className="tabela-rolagem" tabIndex={0} role="region" aria-label={props.rotulo}>
      <table className="tabela" aria-label={props.rotulo}>
        <thead>
          <tr>
            <th scope="col">{props.cabecalho}</th>
            {props.barras && <th scope="col" aria-hidden="true" />}
            <th scope="col" className="num">
              n
            </th>
          </tr>
        </thead>
        <tbody>
          {props.itens.map((i) => (
            <tr key={i.chave}>
              <th scope="row">{i.rotulo}</th>
              {props.barras && (
                <td className="barra-celula" aria-hidden="true">
                  <span className="barra-cheia" style={{ width: `${props.indisponivel ? 0 : (i.n / maior) * 100}%` }} />
                </td>
              )}
              <td className="num">
                {props.indisponivel ? (
                  '—'
                ) : i.n > 0 && props.aoAbrir ? (
                  <button type="button" className="contagem-botao" aria-label={`${i.rotulo}: ${i.n}`} onClick={() => props.aoAbrir?.(i.chave)}>
                    {inteiro(i.n, lang)}
                  </button>
                ) : (
                  inteiro(i.n, lang)
                )}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
