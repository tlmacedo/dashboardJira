import { useId, useRef, useState, type KeyboardEvent } from 'react'
import { useLang } from '../i18n/LangProvider'
import type { CartaoCatalogo } from './useCatalogo'

/**
 * Botão ⓘ do cartão: abre um balão NÃO modal com "O que mostra", "Como é calculado", "Fonte" e "Visível para".
 * O botão leva `aria-expanded`; `Esc` fecha e devolve o foco a ele. Todo texto vem das chaves do catálogo (pt/en).
 */
export function AjudaDoCartao({ meta }: { meta: CartaoCatalogo }) {
  const { t } = useLang()
  const [aberto, setAberto] = useState(false)
  const botao = useRef<HTMLButtonElement>(null)
  const idDoBalao = useId()
  const rotulo = t('cartao.ajuda.abrir')

  function aoTeclar(e: KeyboardEvent) {
    if (e.key === 'Escape' && aberto) {
      e.stopPropagation()
      setAberto(false)
      botao.current?.focus()
    }
  }

  return (
    <span className="cartao-ajuda" onKeyDown={aoTeclar}>
      <button
        ref={botao}
        type="button"
        className="cartao-ajuda-botao"
        aria-label={rotulo}
        aria-expanded={aberto}
        aria-controls={aberto ? idDoBalao : undefined}
        onClick={() => setAberto(!aberto)}
      >
        <span aria-hidden="true">ⓘ</span>
      </button>
      {aberto && (
        <div id={idDoBalao} role="group" aria-label={rotulo} className="cartao-balao">
          <dl>
            <dt>{t('cartao.ajuda.oQueMostra')}</dt>
            <dd>{t(meta.oQueMostraChave)}</dd>
            <dt>{t('cartao.ajuda.comoCalcula')}</dt>
            <dd>{t(meta.comoCalculaChave)}</dd>
            <dt>{t('cartao.ajuda.fonte')}</dt>
            <dd>{meta.fontes.map((f) => t(`catalogo.fonte.${f}`)).join(', ')}</dd>
            <dt>{t('cartao.ajuda.visivelPara')}</dt>
            <dd>{meta.papeis.map((p) => t(`role.${p}`)).join(', ')}</dd>
          </dl>
        </div>
      )}
    </span>
  )
}
