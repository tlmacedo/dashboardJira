import { useState } from 'react'
import { useLang } from '../../i18n/LangProvider'
import { Cartao } from '../../shared/Cartao'
import { CardPage } from '../../shared/CardPage'
import { baixarCsv } from '../../shared/csv'
import { Filtros, resumoDosFiltros, useFiltrosDaUrl } from '../../shared/Filtros'
import { dataCurta, inteiro, numero } from '../../shared/format'
import { GavetaDeTasks } from '../../shared/GavetaDeTasks'
import { temBloqueio, useCard } from '../../shared/useCard'
import type { CargaTipoDto } from './api'
import '../../shared/tabela.css'
import '../../shared/pessoas.css'

type Gaveta = { cartaoId: string; segmento: string; titulo: string }

/**
 * Carga por tipo e OS (CG-01 e CG-02): as tasks abertas (contagem e horas de fila) e as fechadas do período (contagem e índice
 * de esforço) por tipo de task × versão do SO. "Sem OS" é uma coluna própria, escrita em texto (a versão vazia do backend).
 * Cada célula com tasks abre as mesmas tasks que a formam.
 */
export function CargaTipo() {
  const { t, lang } = useLang()
  const [sp, aplicar] = useFiltrosDaUrl()
  const consulta = useCard<CargaTipoDto>('carga-tipo', sp)
  const dto = consulta.data
  const [gaveta, setGaveta] = useState<Gaveta | null>(null)
  const bloqueado = temBloqueio(dto?.avisos)
  const traco = '—'
  const resumo = resumoDosFiltros(sp, t, (iso) => dataCurta(iso, lang))
  const nomeDaOs = (v: string) => (v === '' ? t('cargaTipo.semOs') : v)

  function exportar() {
    if (!dto) return
    baixarCsv('carga-tipo.csv', [
      [`# ${t('nav.cargaTipo')}`],
      [`# ${t('gaveta.csv.filtros')}: ${resumo}`],
      [],
      [t('cargaTipo.col.tipo'), t('cargaTipo.col.os'), t('cargaTipo.col.abertas'), t('cargaTipo.col.horas'), t('cargaTipo.col.fechadas'), t('cargaTipo.col.esforco')],
      ...dto.tipos.flatMap((tipo, i) =>
        dto.versoes.map((v, j) => [tipo, nomeDaOs(v), String(dto.abertasN[i][j]), String(dto.abertasH[i][j]), String(dto.fechadasN[i][j]), String(dto.esforco[i][j])]),
      ),
    ])
  }

  function celula(cartaoId: 'CG-01' | 'CG-02', prefixo: 'abertas' | 'fechadas', tipo: string, versao: string, n: number, texto: string) {
    if (bloqueado) return traco
    if (n === 0) return inteiro(0, lang)
    return (
      <button
        type="button" className="contagem-botao" aria-label={`${tipo}, ${nomeDaOs(versao)}: ${n}`}
        onClick={() => setGaveta({ cartaoId, segmento: `${prefixo}:tipo:${tipo}:os:${versao}`, titulo: `${tipo}, ${nomeDaOs(versao)}` })}
      >
        {texto}
      </button>
    )
  }

  const matriz = (
    dados: CargaTipoDto,
    cartaoId: 'CG-01' | 'CG-02',
    idTitulo: string,
    titulo: string,
    prefixo: 'abertas' | 'fechadas',
    contagens: number[][],
    texto: (i: number, j: number) => string,
  ) => (
    <Cartao id={cartaoId} variante="tabela" nivelDoTitulo={2} idDoTitulo={idTitulo} titulo={titulo}>
      <div className="tabela-rolagem" tabIndex={0} role="region" aria-labelledby={idTitulo}>
        <table className="tabela grade-pessoas" aria-labelledby={idTitulo}>
          <thead>
            <tr>
              <th scope="col">{t('cargaTipo.col.tipo')}</th>
              {dados.versoes.map((v) => (
                <th key={v === '' ? 'sem-os' : v} scope="col" className="num">{nomeDaOs(v)}</th>
              ))}
            </tr>
          </thead>
          <tbody>
            {dados.tipos.map((tipo, i) => (
              <tr key={tipo}>
                <th scope="row">{tipo}</th>
                {dados.versoes.map((v, j) => (
                  <td key={v === '' ? 'sem-os' : v} className="num">
                    {celula(cartaoId, prefixo, tipo, v, contagens[i][j], texto(i, j))}
                  </td>
                ))}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </Cartao>
  )

  return (
    <>
      <CardPage
        titulo={t('nav.cargaTipo')}
        pergunta={t('cargaTipo.pergunta')}
        filtros={
          <Filtros key={sp.toString()} campos={['periodo', 'squad', 'pessoa', 'tipo', 'prioridade', 'os', 'campos']} valor={sp} aoAplicar={aplicar} />
        }
        consulta={consulta}
        avisos={dto?.avisos}
        aoExportar={exportar}
      >
        {dto &&
          (dto.tipos.length === 0 ? (
            <p className="vazio">{t('cargaTipo.vazia')}</p>
          ) : (
            <>
              {matriz(
                dto, 'CG-01', 'h-cg-abertas', t('cargaTipo.abertas'), 'abertas', dto.abertasN,
                (i, j) => t('cargaTipo.celulaAbertas').replace('{n}', inteiro(dto.abertasN[i][j], lang)).replace('{h}', numero(dto.abertasH[i][j], lang)),
              )}
              {matriz(
                dto, 'CG-02', 'h-cg-fechadas', t('cargaTipo.fechadas'), 'fechadas', dto.fechadasN,
                (i, j) => t('cargaTipo.celulaFechadas').replace('{n}', inteiro(dto.fechadasN[i][j], lang)).replace('{e}', numero(dto.esforco[i][j], lang)),
              )}
              <p className="legenda-meta">{t('cargaTipo.nota')}</p>
            </>
          ))}
      </CardPage>
      {gaveta && (
        <GavetaDeTasks
          cartaoId={gaveta.cartaoId} segmento={gaveta.segmento} titulo={gaveta.titulo} filtros={sp}
          resumoDosFiltros={resumo} aoFechar={() => setGaveta(null)}
        />
      )}
    </>
  )
}
