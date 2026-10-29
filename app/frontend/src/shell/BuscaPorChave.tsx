import { useQueryClient } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { fetchOpcoesDeFiltro } from '../api/client'
import { useLang } from '../i18n/LangProvider'
import { TarefaDrawer } from '../shared/TarefaDrawer'

/** Chave de issue completa (`TG-107`). */
const CHAVE = /^([A-Za-z][A-Za-z0-9_]{0,19})-(\d{1,9})$/
/** Só o número (`107`). */
const NUMERO = /^\d{1,9}$/

/**
 * Busca rápida por chave (I-26), em qualquer tela: digitar `TG-107` (ou só `107`) abre a gaveta da task. Não há busca por
 * texto nem autocompletar: o painel não oferece chaves que a pessoa não vê. Só o número usa o prefixo da visão (o das
 * opções de filtro), e só quando há um único. Chave inexistente ou fora da visão cai no 404 da gaveta, que não diz qual.
 */
export function BuscaPorChave() {
  const { t } = useLang()
  const cliente = useQueryClient()
  const [texto, setTexto] = useState('')
  const [chave, setChave] = useState<string | null>(null)
  const [invalida, setInvalida] = useState(false)

  async function buscar(e: FormEvent) {
    e.preventDefault()
    setInvalida(false)
    const digitado = texto.trim()
    const completa = CHAVE.exec(digitado)
    if (completa) {
      setChave(`${completa[1].toUpperCase()}-${Number(completa[2])}`)
      return
    }
    if (NUMERO.test(digitado)) {
      // As opções já guardam os prefixos de chave da visão; só são pedidas quando a pessoa digita o número sozinho.
      const opcoes = await cliente
        .fetchQuery({ queryKey: ['filtros-opcoes'], queryFn: fetchOpcoesDeFiltro, staleTime: 60_000 })
        .catch(() => null)
      const prefixos = opcoes?.prefixosDeChave ?? []
      if (prefixos.length === 1) {
        setChave(`${prefixos[0]}-${Number(digitado)}`)
        return
      }
    }
    setInvalida(true)
  }

  return (
    <>
      <form className="busca-chave" role="search" onSubmit={buscar}>
        <input
          type="text"
          inputMode="text"
          autoComplete="off"
          maxLength={40}
          value={texto}
          placeholder={t('busca.placeholder')}
          aria-label={t('busca.rotulo')}
          onChange={(e) => setTexto(e.target.value)}
        />
        <button type="submit" className="btn">
          {t('busca.botao')}
        </button>
        {invalida && (
          <p className="busca-chave-erro" role="alert">
            {t('busca.invalida')}
          </p>
        )}
      </form>
      {chave && <TarefaDrawer key={chave} chave={chave} aoFechar={() => setChave(null)} />}
    </>
  )
}
