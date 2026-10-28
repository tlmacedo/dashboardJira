package br.sidia.painel.db

import java.lang.ref.SoftReference
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/** Chave de uma leitura em cache: a fonte (o banco, por identidade), o tipo da leitura e os parâmetros dela. */
data class ChaveDeLeitura(val fonte: Any, val tipo: String, val parametros: Any)

/**
 * Cache curto das leituras BRUTAS do banco que todo card refaz a cada requisição (as issues e o histórico delas). Em
 * 30.000 issues e 300.000 eventos cada leitura custa ~1,3 s e dezenas de MB, e 8 pessoas abrindo telas ao mesmo tempo
 * as repetem 8 vezes (medido no `testCarga`: sem o cache, 2 respostas 500 por falta de memória com 512 MB).
 *
 * O que se guarda é o dado ANTES de qualquer recorte por pessoa: o papel de quem pergunta, as pessoas visíveis e os
 * filtros da tela são aplicados DEPOIS, em cada requisição, pelo código do card. Por isso um papel nunca recebe o número
 * calculado para outro. A chave nunca leva o principal.
 *
 * Proteções contra dado velho e contra memória:
 * 1. validade curta ([configurar]; 60 s em produção, 0 = desligado, que é o padrão do código e dos testes). Vencida há
 *    menos de uma validade, a leitura antiga ainda é entregue enquanto UMA renovação roda em segundo plano (ninguém espera
 *    a recarga a cada vencimento); mais velha que isso, quem pede espera a recarga;
 * 2. [invalidar] a cada fim de sincronização, de retenção e de carga de demonstração: o dado novo aparece na hora, sem
 *    período de graça (uma leitura de antes da invalidação nunca é entregue);
 * 3. uma carga por chave por vez, e no máximo [MAXIMO_DE_CARGAS_SIMULTANEAS] cargas ao mesmo tempo: leitores simultâneos
 *    esperam a primeira em vez de repetirem (e de somarem memória);
 * 4. valores em `SoftReference` (a JVM os solta antes de faltar memória) e no máximo [MAXIMO_DE_ENTRADAS] entradas.
 * Falha na carga não é guardada. Os valores guardados são só de leitura para quem os recebe.
 */
object CacheDeLeituras {
    const val MAXIMO_DE_ENTRADAS = 40

    /** Quantas leituras brutas podem estar sendo CARREGADAS ao mesmo tempo (cada uma ocupa dezenas de MB na escala da spec). */
    const val MAXIMO_DE_CARGAS_SIMULTANEAS = 2

    private class Entrada(val versao: Long, val validaAteNanos: Long, val valor: SoftReference<Any>) {
        /** Há uma renovação em segundo plano desta entrada. */
        val renovando = AtomicBoolean(false)
    }

    @Volatile private var validade: Duration = Duration.ZERO
    @Volatile private var relogio: () -> Long = System::nanoTime
    @Volatile private var emSegundoPlano: Executor = Executor { tarefa -> executorPadrao.execute(tarefa) }
    private val versao = AtomicLong()
    private val cargas = Semaphore(MAXIMO_DE_CARGAS_SIMULTANEAS)
    private val entradas = object : LinkedHashMap<Any, Entrada>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Any, Entrada>?): Boolean = size > MAXIMO_DE_ENTRADAS
    }
    private val travas = ConcurrentHashMap<Any, Any>()

    /** Uma thread só, de fundo (não impede a JVM de encerrar): renovar nunca compete com as requisições por CPU. */
    private val executorPadrao by lazy {
        Executors.newSingleThreadExecutor { r -> Thread(r, "cache-de-leituras").also { it.isDaemon = true } }
    }

    /** Está guardando? */
    val ligado: Boolean get() = !validade.isZero

    /**
     * Liga o cache com a [validade] (zero desliga) e esvazia tudo. [relogio] devolve nanossegundos monotônicos;
     * [renovador] roda as renovações em segundo plano (os testes passam um executor direto).
     */
    fun configurar(validade: Duration, relogio: () -> Long = System::nanoTime, renovador: Executor? = null) {
        require(!validade.isNegative) { "validade do cache não pode ser negativa" }
        synchronized(entradas) { entradas.clear() }
        travas.clear()
        this.relogio = relogio
        this.emSegundoPlano = renovador ?: Executor { tarefa -> executorPadrao.execute(tarefa) }
        this.validade = validade
        versao.incrementAndGet()
    }

    fun desligar() = configurar(Duration.ZERO)

    /** O banco mudou: nada do que está guardado vale mais. Barato (um contador). */
    fun invalidar() {
        versao.incrementAndGet()
    }

    internal fun tamanho(): Int = synchronized(entradas) { entradas.size }

    private val cargasFeitas = AtomicLong()

    /** Quantas leituras o banco já fez por causa do cache (medição: quantas telas pagaram a carga inteira). */
    fun totalDeCargas(): Long = cargasFeitas.get()

    /** O valor guardado para [chave], ou o resultado de [carregar] (guardado, se o cache estiver ligado). */
    @Suppress("UNCHECKED_CAST")
    fun <T : Any> obter(chave: Any, carregar: () -> T): T {
        val validadeAtual = validade
        if (validadeAtual.isZero) return carregar()
        val v = versao.get()
        // Caminho rápido: sem trava de carga. Dentro da validade entrega; vencida há pouco, entrega e renova ao fundo.
        val entrada = synchronized(entradas) { entradas[chave] }
        if (entrada != null && entrada.versao == v) {
            val valor = entrada.valor.get()
            if (valor != null) {
                val agora = relogio()
                if (agora - entrada.validaAteNanos < 0) return valor as T
                if (agora - entrada.validaAteNanos < validadeAtual.toNanos()) {
                    renovarAoFundo(chave, entrada, carregar)
                    return valor as T
                }
            }
        }
        return carregarEGuardar(chave, carregar)
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T : Any> carregarEGuardar(chave: Any, carregar: () -> T): T {
        val trava = travas.computeIfAbsent(chave) { Any() }
        synchronized(trava) {
            val v = versao.get()
            val agora = relogio()
            // Outro leitor pode ter acabado de carregar enquanto este esperava a trava da chave.
            synchronized(entradas) { entradas[chave] }?.let { e ->
                val valor = e.valor.get()
                if (valor != null && e.versao == v && agora - e.validaAteNanos < 0) return valor as T
            }
            // Teto de cargas ao mesmo tempo (cada uma ocupa dezenas de MB): as demais esperam a vez, em vez de somar memória.
            cargas.acquire()
            val novo = try {
                cargasFeitas.incrementAndGet()
                carregar()
            } finally {
                cargas.release()
            }
            // Guarda com a versão de ANTES da carga: uma escrita que terminou no meio dela torna o valor velho de saída.
            synchronized(entradas) {
                entradas[chave] = Entrada(v, agora + validade.toNanos(), SoftReference(novo))
            }
            return novo
        }
    }

    private fun <T : Any> renovarAoFundo(chave: Any, entrada: Entrada, carregar: () -> T) {
        if (!entrada.renovando.compareAndSet(false, true)) return
        try {
            emSegundoPlano.execute {
                try {
                    // Renovação já é um vencimento: a carga ignora o que está guardado e grava o novo.
                    recarregar(chave, carregar)
                } catch (e: Exception) {
                    // Falha ao renovar: a entrada antiga segue valendo até o fim da graça; a próxima requisição tenta de novo.
                } finally {
                    entrada.renovando.set(false)
                }
            }
        } catch (e: Exception) {
            entrada.renovando.set(false)
        }
    }

    private fun <T : Any> recarregar(chave: Any, carregar: () -> T) {
        val trava = travas.computeIfAbsent(chave) { Any() }
        synchronized(trava) {
            val v = versao.get()
            val agora = relogio()
            cargas.acquire()
            val novo = try {
                cargasFeitas.incrementAndGet()
                carregar()
            } finally {
                cargas.release()
            }
            synchronized(entradas) {
                entradas[chave] = Entrada(v, agora + validade.toNanos(), SoftReference(novo))
            }
        }
    }
}
