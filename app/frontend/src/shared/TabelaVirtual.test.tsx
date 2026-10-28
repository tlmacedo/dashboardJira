import { cleanup, fireEvent, render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { LangProvider } from '../i18n/LangProvider'
import { paraCsv } from './csv'
import { TabelaVirtual, type Coluna } from './TabelaVirtual'

type Linha = { key: string; nome: string; n: number }

const linhas = (n: number): Linha[] => Array.from({ length: n }, (_, i) => ({ key: `TG-${i + 1}`, nome: `Task ${i + 1}`, n: (i * 7) % 13 }))

const COLUNAS: Coluna<Linha>[] = [
  { id: 'key', cabecalho: 'Task', valor: (l) => l.key },
  { id: 'nome', cabecalho: 'Nome', valor: (l) => l.nome },
  { id: 'n', cabecalho: 'N', valor: (l) => l.n, numerica: true },
]

function renderizar(props: Partial<Parameters<typeof TabelaVirtual<Linha>>[0]> & { total: number }) {
  const { total, ...resto } = props
  return render(
    <LangProvider>
      <TabelaVirtual<Linha> colunas={COLUNAS} linhas={linhas(total)} chave={(l) => l.key} rotulo="Tasks" altura={480} alturaLinha={36} {...resto} />
    </LangProvider>,
  )
}

/** Linhas de dados no DOM (sem o cabeçalho e sem os espaçadores). */
const dados = () => screen.getAllByRole('row').filter((r) => r.querySelector('td, th[scope="row"]'))

beforeEach(() => localStorage.clear())
afterEach(() => cleanup())

describe('TabelaVirtual', () => {
  it('abaixoDoLimiarRenderizaTudo', () => {
    renderizar({ total: 150 })
    expect(dados()).toHaveLength(150)
    // Abaixo do limiar é a tabela comum, sem grade virtual.
    expect(screen.queryByRole('grid')).toBeNull()
  })

  it('renderizaSoAsLinhasVisiveis', () => {
    renderizar({ total: 30_000 })
    const grade = screen.getByRole('grid', { name: 'Tasks' })
    // 14 linhas cabem na janela de 480 px (36 px cada) mais 10 de folga abaixo (nada acima no início): 14 + 20 no máximo.
    expect(dados().length).toBeLessThanOrEqual(14 + 20)
    expect(dados().length).toBeGreaterThanOrEqual(14)
    expect(grade).toHaveAttribute('aria-rowcount', '30001')
    // Cada linha informa a posição na lista inteira (o cabeçalho é a 1).
    expect(dados()[0]).toHaveAttribute('aria-rowindex', '2')
  })

  it('rolarMostraAJanelaCerta', () => {
    renderizar({ total: 30_000 })
    const rolagem = screen.getByRole('grid', { name: 'Tasks' }).parentElement as HTMLElement
    rolagem.scrollTop = 36 * 1000
    fireEvent.scroll(rolagem)
    expect(screen.getByText('TG-1001')).toBeInTheDocument()
    expect(screen.queryByText('TG-1')).toBeNull()
    expect(dados().length).toBeLessThanOrEqual(14 + 20)
  })

  it('tecladoRolaATabela', async () => {
    renderizar({ total: 30_000 })
    const u = userEvent.setup()
    const grade = screen.getByRole('grid', { name: 'Tasks' })
    grade.focus()
    await u.keyboard('{End}')
    const ultima = screen.getByText('TG-30000').closest('tr') as HTMLElement
    expect(ultima).toBeInTheDocument()
    expect(ultima).toHaveAttribute('aria-selected', 'true')
    expect(grade).toHaveAttribute('aria-activedescendant', ultima.id)
    await u.keyboard('{Home}')
    const primeira = screen.getByText('TG-1').closest('tr') as HTMLElement
    expect(primeira).toHaveAttribute('aria-selected', 'true')
    await u.keyboard('{ArrowDown}{ArrowDown}')
    expect(screen.getByText('TG-3').closest('tr')).toHaveAttribute('aria-selected', 'true')
    await u.keyboard('{ArrowUp}')
    expect(screen.getByText('TG-2').closest('tr')).toHaveAttribute('aria-selected', 'true')
    await u.keyboard('{PageDown}')
    // Uma janela inteira (13 linhas de 36 px em 480 px) para baixo.
    expect(screen.getByText('TG-15').closest('tr')).toHaveAttribute('aria-selected', 'true')
    await u.keyboard('{PageUp}')
    expect(screen.getByText('TG-2').closest('tr')).toHaveAttribute('aria-selected', 'true')
  })

  it('enterNaLinhaAtivaAbreATask', async () => {
    const abrir = vi.fn()
    renderizar({ total: 500, aoAbrir: abrir })
    const u = userEvent.setup()
    screen.getByRole('grid', { name: 'Tasks' }).focus()
    await u.keyboard('{ArrowDown}{ArrowDown}{Enter}')
    expect(abrir).toHaveBeenCalledWith(expect.objectContaining({ key: 'TG-2' }))
  })

  it('ordenacaoNaoMudaContagem', async () => {
    renderizar({ total: 5_000 })
    const grade = screen.getByRole('grid', { name: 'Tasks' })
    expect(grade).toHaveAttribute('aria-rowcount', '5001')
    await userEvent.click(within(grade).getByRole('button', { name: /^N(?!ome)/ }))
    expect(grade).toHaveAttribute('aria-rowcount', '5001')
    expect(within(grade).getAllByRole('columnheader').find((c) => c.getAttribute('aria-sort') === 'ascending')).toBeTruthy()
    // A ordenação vale sobre a lista INTEIRA: as linhas com n = 0 vêm primeiro (ficam em ordem natural, TG-1 entre elas).
    expect(dados()[0].querySelectorAll('td')[2].textContent).toBe('0')
    await userEvent.click(within(grade).getByRole('button', { name: /^N(?!ome)/ }))
    expect(dados()[0].querySelectorAll('td')[2].textContent).toBe('12')
  })

  it('csvExportaTudo', () => {
    // A tabela só desenha a janela, mas quem exporta usa a lista inteira: o CSV nunca é da janela.
    const todas = linhas(30_000)
    const texto = paraCsv(todas.map((l) => [l.key, l.nome]))
    expect(texto.split('\r\n')).toHaveLength(30_000)
    renderizar({ total: 30_000 })
    expect(dados().length).toBeLessThan(40)
  })

  it('limiarEAlturaVemDasPropriedades', () => {
    renderizar({ total: 60, limiarVirtual: 50, altura: 144, alturaLinha: 36 })
    expect(screen.getByRole('grid', { name: 'Tasks' })).toBeInTheDocument()
    expect(dados().length).toBeLessThanOrEqual(4 + 20)
  })
})
