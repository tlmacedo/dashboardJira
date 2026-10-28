import { describe, expect, it } from 'vitest'

// Todo código-fonte das telas e do kit comum, como texto (sem rodar nada).
const fontes = import.meta.glob(['../**/*.ts', '../**/*.tsx', '!../**/*.test.ts', '!../**/*.test.tsx'], {
  query: '?raw',
  import: 'default',
  eager: true,
}) as Record<string, string>

describe('uma gaveta só', () => {
  it('auditoriaUsaDrillDoShared', () => {
    const arquivos = Object.keys(fontes)
    expect(arquivos.length).toBeGreaterThan(50)
    // Nenhuma tela importa um DrillDrawer local (E12): todas usam a GavetaDeTasks de `shared/`.
    const comDrillLocal = arquivos.filter((a) => /from ['"][^'"]*DrillDrawer['"]/.test(fontes[a]))
    expect(comDrillLocal).toEqual([])
    expect(arquivos.some((a) => a.endsWith('screens/TempoEmStatus/DrillDrawer.tsx'))).toBe(false)
  })

  it('semCopiaDoComportamentoDeGavetaModal', () => {
    // O foco/Esc/Tab preso e o resto inerte vivem num hook só; nenhuma gaveta repete o laço de `inert`.
    const comCopia = Object.keys(fontes).filter((a) => fontes[a].includes("setAttribute('inert'") && !a.endsWith('useModalAcessivel.ts') && !a.includes('.test.'))
    expect(comCopia).toEqual([])
  })
})
