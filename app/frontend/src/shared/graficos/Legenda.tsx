import { Marcador, preenchimentoDaSerie } from './padroes'
import { estiloDaSerie } from './paleta'

export type ItemDeLegenda = { id: string; rotulo: string }

/**
 * Legenda SEMPRE visível dos gráficos: cada série com a amostra do padrão, o marcador da forma e o nome. A mesma série tem
 * `data-padrao` e `data-forma` aqui e no desenho, então a cor nunca é o único sinal.
 */
export function Legenda(props: { itens: ItemDeLegenda[]; base: string; rotulo: string }) {
  return (
    <ul className="grafico-legenda" aria-label={props.rotulo}>
      {props.itens.map((item, i) => {
        const e = estiloDaSerie(i)
        return (
          <li key={item.id} data-serie={item.id} data-padrao={e.padrao} data-forma={e.forma}>
            <svg width="34" height="14" aria-hidden="true">
              <rect x="1" y="2" width="16" height="10" fill={preenchimentoDaSerie(props.base, i)} stroke={e.cor} strokeWidth="1.5" />
              <Marcador forma={e.forma} cx={26} cy={7} r={4} cor={e.cor} />
            </svg>
            {item.rotulo}
          </li>
        )
      })}
    </ul>
  )
}
