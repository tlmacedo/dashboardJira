// Contrato da Configuração em leitura (espelha `ConfiguracaoDto` do backend). Só o gerente. Nunca traz segredo, URL, caminho,
// nome nem o conteúdo de papeis/equipes/ausencias: só nomes lógicos, versão, hash e origem de cada arquivo de regras.

import { getJson } from '../../api/client'

export type ArquivoConfig = {
  /** Nome lógico (`metas`, `cards/qualidade`); nunca o caminho. */
  nome: string
  versao: number | null
  sha256: string
  /** `real` (o arquivo do gestor) ou `exemplo` (o modelo versionado). */
  origem: 'real' | 'exemplo' | string
}

export type ConfiguracaoDto = {
  devMode: boolean
  liveEnabled: boolean
  /** `AO_VIVO`, `DEMONSTRACAO` ou `DESLIGADO`. */
  modoSync: string
  arquivos: ArquivoConfig[]
  pessoas: number
  squads: number
}

export const fetchConfiguracao = () => getJson<ConfiguracaoDto>('/api/governanca/configuracao')
