// Contrato do card Carga por tipo e OS (espelha `CargaTipoDto` do backend). Cartões CG-01 e CG-02.

import type { Aviso } from '../../shared/useCard'

/** As matrizes são `[tipo][versão]`. A versão vazia (`""`) é "sem OS" (a tela traduz). */
export type CargaTipoDto = {
  tipos: string[]
  versoes: string[]
  abertasH: number[][]
  abertasN: number[][]
  fechadasN: number[][]
  esforco: number[][]
  avisos: Aviso[]
}
