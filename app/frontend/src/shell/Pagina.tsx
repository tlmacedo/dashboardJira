import { useLocation } from 'react-router-dom'
import { useLang } from '../i18n/LangProvider'
import { itemPorRota, podeVer } from '../nav/nav'
import { useRole } from './RoleContext'

/** Tela provisória: as telas reais entram nos planos seguintes. */
export function Pagina() {
  const { t } = useLang()
  const role = useRole()
  const { pathname } = useLocation()
  const item = itemPorRota(pathname)
  const titulo = item ? t(item.labelKey) : pathname

  return (
    <div className="top">
      <div>
        <h1>{titulo}</h1>
        <p className="sub">
          {item && !podeVer(item, role) ? `🔒 ${t('shell.semPermissao')}` : t('shell.telaPrevista')}
        </p>
      </div>
    </div>
  )
}
