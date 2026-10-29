import { useLang } from '../../i18n/LangProvider'
import '../tabela.css'

/**
 * Os mesmos dados do gráfico, em tabela, para quem não enxerga o desenho (leitor de tela, teclado). Fica recolhida em
 * "Ver dados em tabela", mas sempre presente no HTML.
 */
export function TabelaEquivalente(props: { rotulo: string; cabecalhos: string[]; linhas: string[][] }) {
  const { t } = useLang()
  return (
    <details className="grafico-tabela">
      <summary>{t('grafico.verTabela')}</summary>
      <div className="tabela-rolagem">
        <table className="tabela" aria-label={props.rotulo}>
          <thead>
            <tr>
              {props.cabecalhos.map((c, i) => (
                <th key={i} scope="col" className={i > 0 ? 'num' : undefined}>
                  {c}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {props.linhas.map((l, i) => (
              <tr key={i}>
                {l.map((c, k) =>
                  k === 0 ? (
                    <th key={k} scope="row">
                      {c}
                    </th>
                  ) : (
                    <td key={k} className="num">
                      {c}
                    </td>
                  ),
                )}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </details>
  )
}
