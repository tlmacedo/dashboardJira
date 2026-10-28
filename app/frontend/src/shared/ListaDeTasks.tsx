import { useLang } from '../i18n/LangProvider'
import { baixarCsv } from './csv'
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

/** Tabela das tasks do drill, igual na gaveta e na página de detalhe. Todo valor entra como texto. */
export function ListaDeTasks(props: ListaDeTasksProps) {
  const { t } = useLang()
  const colunas = colunasDe(props.dados)
  return (
    <div className="tabela-rolagem" tabIndex={0} role="region" aria-label={props.titulo}>
      <table className="tabela">
        <thead>
          <tr>
            {colunas.map((c) => (
              <th scope="col" key={c}>
                {t(`gaveta.col.${c}`)}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {/* Uma task pode aparecer mais de uma vez (ex.: dois sinais da auditoria na mesma issue): a posição entra na chave. */}
          {props.dados.linhas.map((r, i) => (
            <tr key={`${r.key}:${i}`}>
              {colunas.map((c) =>
                c === 'chave' ? (
                  <th scope="row" className="mono" key={c}>
                    {props.aoAbrirTask ? (
                      <button
                        type="button"
                        className="contagem-botao mono"
                        title={t('gaveta.abrirTask').replace('{key}', r.key)}
                        onClick={() => props.aoAbrirTask?.(r.key)}
                      >
                        {r.key}
                      </button>
                    ) : (
                      r.key
                    )}
                  </th>
                ) : (
                  <td key={c}>{c === 'responsavel' ? (r.responsavel ?? '—') : VALOR_DA_COLUNA[c](r)}</td>
                ),
              )}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
