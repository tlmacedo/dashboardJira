import '@testing-library/jest-dom/vitest'
import { configure } from '@testing-library/react'

// Sob carga (vários workers do vitest, ou outra suíte rodando junto) o jsdom demora mais que o 1 s padrão do
// findBy/waitFor e testes corretos falhavam por tempo (Negativas, Riscos). O limite sobe só a espera máxima: um teste que
// passa continua passando na mesma velocidade; um que falha de verdade só demora mais para falhar.
configure({ asyncUtilTimeout: 5000 })
