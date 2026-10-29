import { useLang } from '../i18n/LangProvider'
import type { LinhaHistorico } from '../screens/Auditoria/api'
import { dataHora } from './format'
import './tabela.css'

/** Texto da coluna Autorização: símbolo e palavra (nunca só cor); sem regra para o campo, um traço. */
export function textoDaAutorizacao(l: LinhaHistorico, t: (k: string) => string): string {
  if (l.autorizado == null) return '—'
  return `${l.autorizado ? '✓' : '▲'} ${l.autorizado ? t('auditoria.autorizado') : t('auditoria.naoAutorizado')}${l.motivo ? ` · ${t(`auditoria.motivo.${l.motivo}`)}` : ''}`
}

/**
 * Histórico de uma issue campo a campo, igual na gaveta da Auditoria e na gaveta da task. Campo sem valor guardado mostra
 * "valor não guardado (privacidade)": o painel nunca guarda texto livre do Jira. Todo valor entra como texto.
 */
export function TabelaDeHistorico(props: { linhas: LinhaHistorico[]; rotulo: string }) {
  const { t, lang } = useLang()
  return (
    <div className="tabela-rolagem" tabIndex={0} role="region" aria-label={props.rotulo}>
      <table className="tabela" aria-label={props.rotulo}>
        <thead>
          <tr>
            <th scope="col">{t('auditoria.historico.col.data')}</th>
            <th scope="col">{t('auditoria.historico.col.campo')}</th>
            <th scope="col">{t('auditoria.historico.col.de')}</th>
            <th scope="col">{t('auditoria.historico.col.para')}</th>
            <th scope="col">{t('auditoria.historico.col.autor')}</th>
            <th scope="col">{t('auditoria.historico.col.autorizacao')}</th>
          </tr>
        </thead>
        <tbody>
          {props.linhas.map((l, i) => (
            <tr key={`${l.at}:${l.campo}:${i}`}>
              <td className="num">{dataHora(l.at, lang)}</td>
              <th scope="row" className="mono">
                {l.campo}
              </th>
              {l.valorGuardado ? (
                <>
                  <td>{l.de ?? '—'}</td>
                  <td>{l.para ?? '—'}</td>
                </>
              ) : (
                <td colSpan={2} className="aud-privacidade">
                  {t('auditoria.historico.naoGuardado')}
                </td>
              )}
              <td>{l.autor ?? t('auditoria.autorDesconhecido')}</td>
              <td>{textoDaAutorizacao(l, t)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
