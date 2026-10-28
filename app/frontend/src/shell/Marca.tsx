import { useState } from 'react'
import { useLang } from '../i18n/LangProvider'
import { useTheme } from '../theme/ThemeProvider'
import { ATIVOS_MARCA } from './marca-ativos'

/**
 * Marca do painel no menu: logo (arquivo <img>, nunca SVG inline) seguido do nome em texto.
 * Sem logo, ou se a imagem não carregar, mostra só o nome. Os ativos vêm de marca-ativos.ts.
 */
export function Marca({ compacta = false }: { compacta?: boolean }) {
  const { theme } = useTheme()
  const { t } = useLang()
  const [imagemFalhou, setImagemFalhou] = useState(false)

  const logo = ATIVOS_MARCA && !imagemFalhou
    ? theme === 'light'
      ? ATIVOS_MARCA.logoParaFundoClaro
      : ATIVOS_MARCA.logoParaFundoEscuro
    : null

  return (
    <div className="marca">
      {logo && <img className="marca__logo" alt="Sidia" src={logo} onError={() => setImagemFalhou(true)} />}
      {(!compacta || !logo) && <span className="marca__nome">{t('app.nome')}</span>}
    </div>
  )
}
