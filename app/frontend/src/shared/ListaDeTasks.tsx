import { useLang } from '../i18n/LangProvider'
import { baixarCsv } from './csv'
import { TabelaVirtual, type Coluna } from './TabelaVirtual'
import type { LinhaDeTask, TasksDoCartao } from './tasksApi'
import './tabela.css'

/** Valor de cada coluna do drill: o id vem do backend (`colunas`); o texto do cabeçalho é a chave `gaveta.col.<id>`. */
const VALOR_DA_COLUNA: Record<string, (r: LinhaDeTask) => string> = {
  chave: (r) => r.key,
  resumo: (r) => r.resumo,
  responsavel: (r) => r.responsavel ?? '',
  status: (r) => r.status,
  detalhe: (r) => r.detalhe,
}

/** Só as colunas que a tela sabe desenhar, na ordem que o backend mandou. */
export function colunasDe(dados: TasksDoCartao | undefined): string[] {
  return (dados?.colunas ?? []).filter((c) => c in VALOR_DA_COLUNA)
}

/**
 * Exporta a lista INTEIRA do drill (nunca só o que está na janela da tela) com a definição do número e os filtros
 * ativos no cabeçalho. A neutralização de fórmula é do `baixarCsv`.
 */
export function exportarTasksCsv(args: {
  arquivo: string
  titulo: string
  dados: TasksDoCartao
  resumoDosFiltros?: string
  t: (k: string) => string
}) {
  const { t, dados } = args
  const colunas = colunasDe(dados)
  baixarCsv(args.arquivo, [
    [`# ${args.titulo}`],
    [`# ${t('gaveta.csv.filtros')}: ${args.resumoDosFiltros || t('gaveta.semFiltros')}`],
    [`# ${t('gaveta.definicao')}: ${t(dados.definicaoChave)}`],
    colunas.map((c) => t(`gaveta.col.${c}`)),
    ...dados.linhas.map((r) => colunas.map((c) => VALOR_DA_COLUNA[c](r))),
  ])
}

export type ListaDeTasksProps = {
  dados: TasksDoCartao
  titulo: string
  /** Presente = a chave de cada linha vira botão que abre a gaveta da task. */
  aoAbrirTask?: (chave: string) => void
}

/**
 * Tabela das tasks do drill, igual na gaveta e na página de detalhe. Ordenável por coluna e VIRTUAL acima de 200 linhas (um
 * drill pode ter dezenas de milhares): só a janela visível é desenhada, o CSV usa a lista inteira. Todo valor entra como texto.
 */
export function ListaDeTasks(props: ListaDeTasksProps) {
  const { t } = useLang()
  const colunas: Coluna<LinhaDeTask>[] = colunasDe(props.dados).map((c) => ({
    id: c,
    cabecalho: t(`gaveta.col.${c}`),
    valor: (r) => (c === 'responsavel' ? (r.responsavel ?? null) : VALOR_DA_COLUNA[c](r)),
    render:
      c === 'chave'
        ? (r) =>
            props.aoAbrirTask ? (
              <button type="button" className="contagem-botao mono" title={t('gaveta.abrirTask').replace('{key}', r.key)} onClick={() => props.aoAbrirTask?.(r.key)}>
                {r.key}
              </button>
            ) : (
              r.key
            )
        : c === 'responsavel'
          ? (r) => r.responsavel ?? '—'
          : undefined,
  }))
  return (
    <TabelaVirtual
      colunas={colunas}
      linhas={props.dados.linhas}
      // Uma task pode aparecer mais de uma vez (ex.: dois sinais da auditoria na mesma issue): a posição entra na chave.
      chave={(r, i) => `${r.key}:${i}`}
      rotulo={props.titulo}
      primeiraEhCabecalhoDeLinha={colunas[0]?.id === 'chave'}
      aoAbrir={props.aoAbrirTask ? (r) => props.aoAbrirTask?.(r.key) : undefined}
    />
  )
}
