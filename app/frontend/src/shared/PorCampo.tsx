import { useLang } from '../i18n/LangProvider'
import { Cartao } from './Cartao'
import { ContagensEmBarras } from './ContagensEmBarras'

/** Um grupo de um campo do dicionário: `valor` nulo = "sem valor" (espelha `AgrupamentoDto` do backend). */
export type Agrupamento = { campo: string; slug: string; grupos: { valor: string | null; n: number }[] }

/** Segmento do drill de um grupo: `campo:<slug>:<valor>`, e `-` para "sem valor". */
const SEM_VALOR_NO_SEGMENTO = '-'

export type PorCampoProps = {
  /** Cartão do contrato que abre as tasks dos grupos (AB-10 em Abertas, FE-13 em Fechadas). */
  cartaoId: string
  agrupamentos: Agrupamento[]
  aoAbrir: (segmento: string, titulo: string) => void
  /** Aviso bloqueante: os números ficam "—". */
  indisponivel?: boolean
}

/**
 * Contagem por campo do dicionário (Checklist Type, SKU Type, ...): um cartão por campo com ao menos um grupo, em ordem
 * alfabética e com "sem valor" por último. Cada número abre as tasks do grupo (mesmo tamanho do número).
 */
export function PorCampo(props: PorCampoProps) {
  const { t } = useLang()
  const comGrupos = props.agrupamentos.filter((a) => a.grupos.length > 0)
  if (comGrupos.length === 0) return null
  return (
    <div className="cardpage-duas-colunas">
      {comGrupos.map((a) => {
        const titulo = `${t('porCampo.titulo')}: ${a.campo}`
        return (
          <Cartao key={a.slug} id={props.cartaoId} variante="tabela" nivelDoTitulo={2} idDoTitulo={`h-campo-${a.slug}`} titulo={titulo}>
            <ContagensEmBarras
              barras
              rotulo={titulo}
              cabecalho={a.campo}
              indisponivel={props.indisponivel}
              itens={a.grupos.map((g) => ({
                chave: g.valor ?? SEM_VALOR_NO_SEGMENTO,
                rotulo: g.valor ?? t('porCampo.semValor'),
                n: g.n,
              }))}
              aoAbrir={(chave) =>
                props.aoAbrir(
                  `campo:${a.slug}:${chave}`,
                  `${titulo}: ${chave === SEM_VALOR_NO_SEGMENTO ? t('porCampo.semValor') : chave}`,
                )
              }
            />
          </Cartao>
        )
      })}
    </div>
  )
}
