package br.sidia.painel.jira

import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/**
 * Teto de requisições ao Jira para um trecho de trabalho (ex.: uma execução de sincronização).
 * Entra no contexto da corrotina (`withContext(OrcamentoDeRequisicoes(n))`) e o [JiraClient] consome
 * uma unidade em TODA tentativa de requisição, inclusive as repetições do retry. Esgotado, a tentativa
 * seguinte é barrada com [OrcamentoEsgotadoException] antes de sair para a rede. Sem o elemento no
 * contexto, o cliente não tem limite. A contagem é segura entre corrotinas.
 */
class OrcamentoDeRequisicoes(val max: Int) : AbstractCoroutineContextElement(Key) {
    init {
        require(max >= 0) { "max do orçamento não pode ser negativo" }
    }

    private val usadasAtomico = AtomicInteger(0)

    /** Requisições já autorizadas (nunca passa de [max]). */
    val usadas: Int get() = usadasAtomico.get()

    /** Gasta uma requisição; lança [OrcamentoEsgotadoException] se o teto já foi atingido. */
    fun consumir() {
        while (true) {
            val atual = usadasAtomico.get()
            if (atual >= max) throw OrcamentoEsgotadoException()
            if (usadasAtomico.compareAndSet(atual, atual + 1)) return
        }
    }

    companion object Key : CoroutineContext.Key<OrcamentoDeRequisicoes>
}

/** O orçamento de requisições do trecho acabou; não é falha do Jira, é um limite nosso. */
class OrcamentoEsgotadoException : RuntimeException("orçamento de requisições esgotado")
