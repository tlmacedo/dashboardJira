import { useEffect, useRef, useState } from 'react'
import { caminhoDeVolta, EVENTO_SESSAO_EXPIRADA, urlDeLogin } from '../api/client'
import { useLang } from '../i18n/LangProvider'

/**
 * Aviso de sessão expirada: aparece quando qualquer chamada da API recebe 401 (evento global do cliente HTTP).
 * Não fecha sozinho nem por Esc: a sessão realmente acabou e só o login resolve. O link leva ao login com a volta
 * para o caminho atual (só caminho interno; os filtros da tela ficam na URL). Não aparece em modo desenvolvimento,
 * onde não há login.
 */
export function SessaoExpirada({ devMode }: { devMode: boolean }) {
  const { t } = useLang()
  const [hrefLogin, setHrefLogin] = useState<string | null>(null)
  const link = useRef<HTMLAnchorElement>(null)

  useEffect(() => {
    if (devMode) return
    function aoExpirar() {
      setHrefLogin(urlDeLogin(caminhoDeVolta(window.location.pathname + window.location.search)))
    }
    window.addEventListener(EVENTO_SESSAO_EXPIRADA, aoExpirar)
    return () => window.removeEventListener(EVENTO_SESSAO_EXPIRADA, aoExpirar)
  }, [devMode])

  useEffect(() => {
    if (hrefLogin) link.current?.focus()
  }, [hrefLogin])

  if (devMode || !hrefLogin) return null
  return (
    <div className="sessao-expirada" role="presentation">
      <div
        className="sessao-expirada__caixa"
        role="alertdialog"
        aria-modal="true"
        aria-labelledby="sessao-expirada-texto"
        onKeyDown={(e) => {
          // Esc não fecha: manter o foco no botão. Tab fica preso no único elemento focável.
          if (e.key === 'Escape' || e.key === 'Tab') {
            e.preventDefault()
            link.current?.focus()
          }
        }}
      >
        <p id="sessao-expirada-texto">{t('sessao.expirada')}</p>
        <a ref={link} className="btn" href={hrefLogin}>
          {t('sessao.entrar')}
        </a>
      </div>
    </div>
  )
}
