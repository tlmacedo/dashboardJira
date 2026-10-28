import { cleanup, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { visibleNav } from '../../nav/nav'
import { renderizarTela, simular } from '../../test/telas'
import type { MeusDadosDto } from './api'
import { MeusDados } from './MeusDados'

const API = '/api/titular/meus-dados'

const DTO: MeusDadosDto = {
  pessoa: 'ana',
  geradoEm: '2026-10-30T15:00:00Z',
  papeis: [{ papel: 'USUARIO', squad: 'Alfa', de: null, ate: null }],
  issuesComoResponsavel: 2,
  issuesComoReporter: 1,
  alteracoesComoAutor: 3,
  worklogs: 2,
  horasRegistradas: 2.5,
  ausenciasPlanejadas: 1,
  decisoesEnvolvendo: 2,
  acessos: [
    { rota: '/api/cartoes/AB-09/tasks', at: '2026-10-28T13:00:00Z' },
    { rota: '/api/central', at: '2026-10-20T13:00:00Z' },
  ],
  categorias: [
    { categoria: 'TEMPO_EM_STATUS', finalidade: 'x', quemVe: ['USUARIO', 'GERENTE'], retencaoVariavel: 'RETENTION_HISTORICO_MONTHS' },
    { categoria: 'AUDITORIA', finalidade: 'y', quemVe: ['COORDENADOR', 'GERENTE'], retencaoVariavel: 'RETENTION_HISTORICO_MONTHS' },
    { categoria: 'DECISOES', finalidade: 'z', quemVe: ['COORDENADOR', 'GERENTE'], retencaoVariavel: 'RETENTION_DECISOES_MONTHS' },
  ],
  retencao: { historico: 24, accessLog: 24, decisoes: 12, execucoes: 24 },
  aviso: 'SOLICITACOES_PELO_CANAL_DO_DPO',
}

beforeEach(() => localStorage.clear())
afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
  vi.unstubAllGlobals()
})

describe('tela Meus dados', () => {
  it('mostraContagensEPapeis', async () => {
    simular({ [API]: { status: 200, corpo: DTO } })
    renderizarTela(<MeusDados />, '/meus-dados')
    const resumo = await screen.findByRole('region', { name: 'O que o painel guarda sobre mim' })
    expect(within(resumo).getByText('Tasks sob minha responsabilidade').nextElementSibling).toHaveTextContent('2')
    expect(within(resumo).getByText('Horas registradas').nextElementSibling).toHaveTextContent('2,5')
    expect(within(resumo).getByText('Ausências planejadas').nextElementSibling).toHaveTextContent('1')
    const papeis = screen.getByRole('table', { name: 'Meus papéis' })
    expect(within(papeis).getByText('Usuário')).toBeInTheDocument()
    expect(within(papeis).getByText('Alfa')).toBeInTheDocument()
    // Categorias com a retenção da própria categoria, e os acessos recentes.
    const categorias = screen.getByRole('table', { name: 'O que é tratado' })
    expect(within(categorias).getAllByRole('row')).toHaveLength(4)
    expect(within(categorias).getByText(/12 meses/)).toBeInTheDocument()
    const acessos = screen.getByRole('table', { name: 'Meus acessos recentes' })
    expect(within(acessos).getAllByRole('row')).toHaveLength(3)
    // Sem conteúdo de sinais.
    expect(document.body.textContent ?? '').not.toMatch(/PASSA_BOLA|FECHAMENTO_EM_LOTE/)
  })

  it('baixaJsonComOMesmoConteudo', async () => {
    simular({ [API]: { status: 200, corpo: DTO } })
    let conteudo = ''
    const criar = vi.fn((blob: Blob) => {
      void blob.text().then((t) => (conteudo = t))
      return 'blob:meus-dados'
    })
    vi.stubGlobal('URL', Object.assign(URL, { createObjectURL: criar, revokeObjectURL: vi.fn() }))
    const nomes: string[] = []
    const clique = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function (this: HTMLAnchorElement) {
      nomes.push(this.download)
    })
    renderizarTela(<MeusDados />, '/meus-dados')
    await userEvent.click(await screen.findByRole('button', { name: 'Baixar meus dados (JSON)' }))
    expect(clique).toHaveBeenCalledTimes(1)
    expect(nomes).toEqual(['meus-dados-2026-10-30.json'])
    expect(criar.mock.calls[0][0].type).toContain('application/json')
    await vi.waitFor(() => expect(conteudo).not.toBe(''))
    expect(JSON.parse(conteudo)).toEqual(DTO)
  })

  it('textoDoCanalDoDpo', async () => {
    simular({ [API]: { status: 200, corpo: DTO } })
    renderizarTela(<MeusDados />, '/meus-dados')
    expect(
      await screen.findByText('Para pedir correção ou eliminação, fale com o encarregado de dados (DPO). Os dados vêm do Jira e são corrigidos lá.'),
    ).toBeInTheDocument()
    cleanup()
    localStorage.setItem('painel.idioma', 'en')
    simular({ [API]: { status: 200, corpo: DTO } })
    renderizarTela(<MeusDados />, '/meus-dados')
    expect(
      await screen.findByText('To request correction or deletion, talk to the data protection officer (DPO). The data comes from Jira and is corrected there.'),
    ).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Download my data (JSON)' })).toBeInTheDocument()
  })

  it('itemNoMenuParaTodosOsPapeis', () => {
    for (const papel of ['USUARIO', 'SQUAD_LEADER', 'COORDENADOR', 'GERENTE'] as const) {
      const ids = visibleNav([papel]).flatMap((g) => g.items.map((i) => i.id))
      expect(ids, papel).toContain('meusDados')
    }
  })

  it('estadosDaTela', async () => {
    // Carregando: nenhum dado e o texto de espera.
    vi.stubGlobal('fetch', vi.fn(() => new Promise(() => {})))
    renderizarTela(<MeusDados />, '/meus-dados')
    expect(screen.getByText('Carregando…')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Baixar meus dados (JSON)' })).toBeNull()
    cleanup()
    vi.unstubAllGlobals()
    // Erro: mensagem e "Tentar de novo", sem botão de download.
    simular({ [API]: { status: 500 } })
    renderizarTela(<MeusDados />, '/meus-dados')
    expect(await screen.findByRole('alert')).toHaveTextContent('500')
    expect(screen.getByRole('button', { name: 'Tentar de novo' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Baixar meus dados (JSON)' })).toBeNull()
    cleanup()
    // Vazio: tudo zerado continua mostrando a tela, com "nenhum acesso".
    simular({
      [API]: {
        status: 200,
        corpo: { ...DTO, issuesComoResponsavel: 0, issuesComoReporter: 0, alteracoesComoAutor: 0, worklogs: 0, horasRegistradas: 0, ausenciasPlanejadas: 0, decisoesEnvolvendo: 0, acessos: [], papeis: [] },
      },
    })
    renderizarTela(<MeusDados />, '/meus-dados')
    expect(await screen.findByText('Nenhum acesso nos últimos 90 dias.')).toBeInTheDocument()
    expect(screen.getByText('Nenhum papel no diretório.')).toBeInTheDocument()
  })
})
