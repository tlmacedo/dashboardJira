import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, render as renderizarBase, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { LangProvider } from '../i18n/LangProvider'
import { PorCampo, type Agrupamento } from './PorCampo'

const AGRUPAMENTOS: Agrupamento[] = [
  { campo: 'SKU Type', slug: 'sku-type', grupos: [{ valor: '1st Carrier (Global)', n: 1 }, { valor: 'MR', n: 2 }, { valor: null, n: 1 }] },
  { campo: 'Model Name', slug: 'model-name', grupos: [] },
]

afterEach(() => cleanup())

function render(ui: React.ReactElement) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return renderizarBase(<QueryClientProvider client={client}>{ui}</QueryClientProvider>)
}

describe('PorCampo', () => {
  it('semValorPorUltimoEClicaAbreOSegmento', async () => {
    const abrir = vi.fn()
    render(
      <LangProvider>
        <PorCampo cartaoId="AB-10" agrupamentos={AGRUPAMENTOS} aoAbrir={abrir} />
      </LangProvider>,
    )
    // Campo sem nenhum grupo não ganha tabela.
    expect(screen.queryByRole('table', { name: /Model Name/ })).toBeNull()
    const tabela = screen.getByRole('table', { name: /SKU Type/ })
    expect(within(tabela).getAllByRole('rowheader').map((c) => c.textContent)).toEqual(['1st Carrier (Global)', 'MR', 'Sem valor'])
    await userEvent.click(within(tabela).getByRole('button', { name: 'MR: 2' }))
    expect(abrir).toHaveBeenCalledWith('campo:sku-type:MR', expect.stringContaining('MR'))
    await userEvent.click(within(tabela).getByRole('button', { name: 'Sem valor: 1' }))
    expect(abrir).toHaveBeenLastCalledWith('campo:sku-type:-', expect.stringContaining('Sem valor'))
  })

  it('indisponivelMostraTraco', () => {
    render(
      <LangProvider>
        <PorCampo cartaoId="AB-10" agrupamentos={AGRUPAMENTOS} aoAbrir={vi.fn()} indisponivel />
      </LangProvider>,
    )
    expect(screen.queryAllByRole('button')).toHaveLength(0)
  })
})
