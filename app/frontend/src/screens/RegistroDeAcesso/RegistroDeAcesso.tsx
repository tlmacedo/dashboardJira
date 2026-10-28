import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { useLang } from '../../i18n/LangProvider'
import { CardPage } from '../../shared/CardPage'
import { Cartao } from '../../shared/Cartao'
import { baixarCsv } from '../../shared/csv'
import { dataCurta, instanteCurto } from '../../shared/format'
import { Filtros, useFiltrosDaUrl } from '../../shared/Filtros'
import '../../shared/tabela.css'
import { fetchAcessos } from './api'

/** Resultado em símbolo e texto: nunca só cor. */
function resultado(codigo: number | null): string {
  if (codigo === null) return '—'
  if (codigo >= 200 && codigo < 300) return `✓ ${codigo}`
  if (codigo === 403) return `▲ ${codigo}`
  return `● ${codigo}`
}

/**
 * Registro de acesso (AU-02, cartão RA-01): quem consultou qual rota e quando, do mais recente ao mais antigo, em páginas. Só o
 * gerente. A própria consulta também fica registrada. Não mostra chave de task, segmento nem valor de filtro e não conta nem ordena
 * por pessoa.
 */
export function RegistroDeAcesso() {
  const { t, lang } = useLang()
  const [sp, aplicar] = useFiltrosDaUrl()
  // Pilha de cursores: a página atual é a do último; voltar tira o último.
  const [cursores, setCursores] = useState<number[]>([])
  const atual = cursores.length > 0 ? cursores[cursores.length - 1] : null
  const consulta = useQuery({
    queryKey: ['acessos', sp.toString(), atual], queryFn: () => fetchAcessos(sp, atual), placeholderData: keepPreviousData, retry: false,
  })
  const dto = consulta.data

  function trocar(novo: URLSearchParams) {
    setCursores([])
    aplicar(novo)
  }

  function filtrarTexto(e: FormEvent<HTMLFormElement>) {
    e.preventDefault()
    const f = new FormData(e.currentTarget)
    const novo = new URLSearchParams(sp)
    for (const k of ['usuario', 'rota']) {
      const v = String(f.get(k) ?? '').trim()
      if (v) novo.set(k, v)
      else novo.delete(k)
    }
    trocar(novo)
  }

  function exportar() {
    if (!dto) return
    baixarCsv('registro-de-acesso.csv', [
      [`# ${t('nav.registroAcesso')}`],
      [`# ${t('decisoes.periodo')}: ${dto.de} - ${dto.ate}`],
      [],
      [t('acessos.col.quando'), t('acessos.col.usuario'), t('acessos.col.rota'), t('acessos.col.resultado'), t('acessos.col.filtros')],
      ...dto.acessos.map((a) => [a.quando, a.usuario, a.rota, a.resultado === null ? '' : String(a.resultado), a.filtros.join(' ')]),
    ])
  }

  return (
    <CardPage
      titulo={t('nav.registroAcesso')}
      pergunta={t('acessos.pergunta')}
      filtros={
        <>
          <Filtros key={sp.toString()} campos={['periodo']} valor={sp} aoAplicar={trocar} />
          <form className="filtros" key={`txt-${sp.toString()}`} onSubmit={filtrarTexto}>
            <label>
              {t('acessos.filtro.usuario')} <input name="usuario" defaultValue={sp.get('usuario') ?? ''} maxLength={64} autoComplete="off" />
            </label>
            <label>
              {t('acessos.filtro.rota')} <input name="rota" defaultValue={sp.get('rota') ?? ''} maxLength={100} placeholder="/api/..." autoComplete="off" />
            </label>
            <button type="submit" className="btn">{t('acessos.filtro.aplicar')}</button>
          </form>
        </>
      }
      consulta={consulta}
      aoExportar={exportar}
    >
      {dto && (
        <Cartao id="RA-01" variante="tabela" nivelDoTitulo={2} idDoTitulo="h-ra-tabela" titulo={t('acessos.tabela')}>
          <p className="legenda-meta">
            {t('acessos.nota').replace('{de}', dataCurta(dto.de, lang)).replace('{ate}', dataCurta(dto.ate, lang))}
          </p>
          {dto.acessos.length === 0 ? (
            <p className="vazio">{t('acessos.vazia')}</p>
          ) : (
            <div className="tabela-rolagem" tabIndex={0} role="region" aria-labelledby="h-ra-tabela">
              <table className="tabela" aria-labelledby="h-ra-tabela">
                <thead>
                  <tr>
                    <th scope="col">{t('acessos.col.quando')}</th>
                    <th scope="col">{t('acessos.col.usuario')}</th>
                    <th scope="col">{t('acessos.col.rota')}</th>
                    <th scope="col">{t('acessos.col.resultado')}</th>
                    <th scope="col">{t('acessos.col.filtros')}</th>
                  </tr>
                </thead>
                <tbody>
                  {dto.acessos.map((a, i) => (
                    <tr key={`${a.quando}:${i}`}>
                      <td>{instanteCurto(a.quando, lang)}</td>
                      <td>{a.usuario}</td>
                      <td><code>{a.rota}</code></td>
                      <td>{resultado(a.resultado)}</td>
                      <td>{a.filtros.length > 0 ? a.filtros.join(', ') : '—'}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
          <div className="acoes">
            <button type="button" className="btn" disabled={cursores.length === 0} onClick={() => setCursores(cursores.slice(0, -1))}>
              {t('acessos.mais.recentes')}
            </button>
            <button type="button" className="btn" disabled={dto.proximo === null} onClick={() => dto.proximo !== null && setCursores([...cursores, dto.proximo])}>
              {t('acessos.mais.antigas')}
            </button>
          </div>
        </Cartao>
      )}
    </CardPage>
  )
}
