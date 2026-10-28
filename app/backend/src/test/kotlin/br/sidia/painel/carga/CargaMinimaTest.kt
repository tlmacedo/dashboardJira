package br.sidia.painel.carga

import java.time.Duration

/**
 * O MESMO cenário de [CargaTest] em escala mínima (400 issues), dentro da suíte padrão: prova a cada execução que a
 * leitura concorrente, a escrita durante a leitura, os dois gravadores e a transação de escrita aberta funcionam
 * sem 5xx nem `SQLITE_BUSY`. Os números de tempo que valem são os do `testCarga` na escala da spec.
 */
class CargaMinimaTest : CenarioDeCarga(
    escala = Escala.MINIMA,
    duracaoDaLeitura = Duration.ofSeconds(3),
    issuesDaSincronizacao = 200,
)
