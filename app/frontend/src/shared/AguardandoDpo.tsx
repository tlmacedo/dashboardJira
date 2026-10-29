import { CODIGO_AGUARDANDO_DPO, ErroDaApi } from '../api/client'
import { useLang } from '../i18n/LangProvider'

/** O erro é o 403 da trava do DPO (`AGUARDANDO_ACEITE_DPO`)? Vale antes do caso genérico de "sem permissão". */
export function ehAguardandoDpo(erro: unknown): boolean {
  return erro instanceof ErroDaApi && erro.status === 403 && erro.codigo === CODIGO_AGUARDANDO_DPO
}

/**
 * Estado da tela de um módulo sensível (Auditoria, Integridade) fechado até o aceite do encarregado de dados (DPO).
 * Só texto: nenhum número, tabela, botão de exportar nem "tentar de novo" (não há dado consultado nem o que repetir).
 */
export function AguardandoDpo() {
  const { t } = useLang()
  return (
    <div className="estado estado--erro" role="alert">
      <p>
        <span aria-hidden="true">⏸ </span>
        {t('dpo.telaAguardando')}
      </p>
    </div>
  )
}
