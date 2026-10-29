import { useId } from 'react'
import { PADROES, estiloDaSerie, type Forma, type Padrao } from './paleta'

/** Identificador único de um conjunto de padrões numa página (os `id` de `<pattern>` não podem repetir). */
export function useIdDePadroes(): string {
  return `gp-${useId().replace(/[^a-zA-Z0-9]/g, '')}`
}

/** Preenchimento SVG da série [i]: o padrão do conjunto [base] (ou a cor lisa, no sólido). */
export function preenchimentoDaSerie(base: string, i: number): string {
  const e = estiloDaSerie(i)
  return e.padrao === 'solido' ? e.cor : `url(#${base}-${e.padrao}-${i % PADROES.length})`
}

/** Marcas de cada padrão, desenhadas em [cor] sobre o fundo da superfície, numa célula de 8×8. */
function marcas(padrao: Padrao, cor: string) {
  switch (padrao) {
    case 'solido':
      return <rect width="8" height="8" fill={cor} />
    case 'listras':
      return <path d="M0 2H8M0 6H8" stroke={cor} strokeWidth="2" />
    case 'pontos':
      return <circle cx="4" cy="4" r="1.8" fill={cor} />
    case 'xadrez':
      return (
        <>
          <rect width="4" height="4" fill={cor} />
          <rect x="4" y="4" width="4" height="4" fill={cor} />
        </>
      )
    case 'diagonal':
      return <path d="M-1 1L1 -1M0 8L8 0M7 9L9 7" stroke={cor} strokeWidth="2" />
    case 'ondas':
      return <path d="M0 4Q2 1 4 4T8 4" fill="none" stroke={cor} strokeWidth="1.6" />
  }
}

/** `<defs>` com um padrão por série (até 6), com os ids derivados de [base]. Vai dentro do `<svg>` do gráfico. */
export function DefinicoesDePadroes(props: { base: string; series: number }) {
  return (
    <defs>
      {Array.from({ length: Math.min(props.series, PADROES.length) }, (_, i) => {
        const e = estiloDaSerie(i)
        if (e.padrao === 'solido') return null
        return (
          <pattern key={i} id={`${props.base}-${e.padrao}-${i}`} width="8" height="8" patternUnits="userSpaceOnUse">
            <rect width="8" height="8" fill="var(--surface)" />
            {marcas(e.padrao, e.cor)}
          </pattern>
        )
      })}
    </defs>
  )
}

/** Marcador da série em (cx, cy): círculo, quadrado, triângulo, losango, cruz ou estrela, de raio [r]. */
export function Marcador(props: { forma: Forma; cx: number; cy: number; r?: number; cor: string; vazado?: boolean; className?: string }) {
  const { forma, cx, cy, r = 5, cor } = props
  const comum = {
    className: props.className,
    'data-forma': forma,
    fill: props.vazado ? 'var(--surface)' : cor,
    stroke: cor,
    strokeWidth: 1.5,
  }
  switch (forma) {
    case 'circulo':
      return <circle cx={cx} cy={cy} r={r} {...comum} />
    case 'quadrado':
      return <rect x={cx - r} y={cy - r} width={2 * r} height={2 * r} {...comum} />
    case 'triangulo':
      return <polygon points={`${cx},${cy - r} ${cx + r},${cy + r} ${cx - r},${cy + r}`} {...comum} />
    case 'losango':
      return <polygon points={`${cx},${cy - r - 1} ${cx + r + 1},${cy} ${cx},${cy + r + 1} ${cx - r - 1},${cy}`} {...comum} />
    case 'cruz':
      return <path d={`M${cx - r} ${cy}H${cx + r}M${cx} ${cy - r}V${cy + r}`} {...comum} fill="none" strokeWidth={3} />
    case 'estrela': {
      const pontos = Array.from({ length: 10 }, (_, k) => {
        const raio = k % 2 === 0 ? r + 1 : (r + 1) / 2.2
        const a = (Math.PI / 5) * k - Math.PI / 2
        return `${cx + raio * Math.cos(a)},${cy + raio * Math.sin(a)}`
      }).join(' ')
      return <polygon points={pontos} {...comum} />
    }
  }
}
