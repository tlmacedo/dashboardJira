import type { Aviso } from './api'

/**
 * Texto de um aviso informativo pelo código (`<prefixo>.<CODIGO>` nos arquivos de tradução). Os dados do aviso vêm em
 * `params` e entram nos marcadores `{nome}`; marcador sem dado nunca aparece cru e a mensagem do backend só serve de
 * último recurso para um código desconhecido.
 */
export function textoDoAviso(a: Aviso, t: (k: string) => string, prefixo = 'auditoria.aviso'): string {
  const chave = `${prefixo}.${a.codigo}`
  const base = t(chave)
  if (base === chave) return a.mensagem
  const preenchido = Object.entries(a.params ?? {}).reduce((texto, [nome, valor]) => texto.replaceAll(`{${nome}}`, () => valor), base)
  return preenchido.replace(/\{\w+\}/g, '…')
}
