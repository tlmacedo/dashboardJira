import { ErroDaApi } from '../api/client'

/**
 * Texto de um erro da API pelo código do `ErroDto` (`erros.<CODIGO>`, com `{param}` interpolado). Código sem tradução mostra
 * "Erro do servidor (código X)"; erro sem código (ou que nem é da API) cai na mensagem genérica. Nunca devolve texto vindo
 * do servidor nem status HTTP.
 */
export function textoDoErro(erro: unknown, t: (k: string) => string): string {
  if (!(erro instanceof ErroDaApi) || !erro.codigo) return t('estado.erro')
  const chave = `erros.${erro.codigo}`
  const base = t(chave)
  if (base === chave) return t('erros.DESCONHECIDO').replace('{codigo}', erro.codigo)
  const preenchido = Object.entries(erro.params).reduce((texto, [nome, valor]) => texto.replaceAll(`{${nome}}`, () => valor), base)
  return preenchido.replace(/\{\w+\}/g, '…')
}
