package br.sidia.painel.db

import org.junit.jupiter.api.AfterEach
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** O cache de leituras brutas: desligado por padrão, com validade curta, invalidação e uma carga por vez. */
class CacheDeLeiturasTest {
    private val agora = AtomicLong(0)
    private fun relogio() = agora.get()

    @AfterEach
    fun desligar() = CacheDeLeituras.desligar()

    /** Renovações em segundo plano rodam na hora, na própria thread (determinístico); [renovador] troca isso. */
    private fun ligar(segundos: Long = 30, renovador: Executor = Executor { it.run() }) =
        CacheDeLeituras.configurar(Duration.ofSeconds(segundos), ::relogio, renovador)

    @Test
    fun desligadoPorPadraoNuncaGuarda() {
        assertFalse(CacheDeLeituras.ligado)
        val chamadas = AtomicInteger()
        val a = CacheDeLeituras.obter("k") { chamadas.incrementAndGet(); listOf(1) }
        val b = CacheDeLeituras.obter("k") { chamadas.incrementAndGet(); listOf(1) }
        assertEquals(2, chamadas.get())
        assertNotSame(a, b)
    }

    @Test
    fun ligadoReaproveitaDentroDaValidadeEReleDepois() {
        ligar(30)
        val chamadas = AtomicInteger()
        val primeiro = CacheDeLeituras.obter("k") { chamadas.incrementAndGet(); listOf(chamadas.get()) }
        agora.set(Duration.ofSeconds(29).toNanos())
        assertSame(primeiro, CacheDeLeituras.obter("k") { chamadas.incrementAndGet(); listOf(chamadas.get()) })
        assertEquals(1, chamadas.get())
        // vencida há pouco (dentro de uma validade): entrega a antiga e renova; a seguinte já é a nova
        agora.set(Duration.ofSeconds(31).toNanos())
        val vencida = CacheDeLeituras.obter("k") { chamadas.incrementAndGet(); listOf(chamadas.get()) }
        assertSame(primeiro, vencida, "vencida há pouco, a leitura antiga ainda é entregue")
        assertEquals(2, chamadas.get(), "e a renovação rodou em segundo plano")
        val renovada = CacheDeLeituras.obter("k") { chamadas.incrementAndGet(); listOf(chamadas.get()) }
        assertEquals(listOf(2), renovada)
        assertEquals(2, chamadas.get())
    }

    @Test
    fun muitoVencidaQuemPedeEsperaARecarga() {
        ligar(30)
        val chamadas = AtomicInteger()
        CacheDeLeituras.obter("k") { chamadas.incrementAndGet(); listOf("velho") }
        agora.set(Duration.ofSeconds(61).toNanos()) // passou da validade E da graça (mais uma validade)
        val depois = CacheDeLeituras.obter("k") { chamadas.incrementAndGet(); listOf("novo") }
        assertEquals(listOf("novo"), depois)
        assertEquals(2, chamadas.get())
    }

    @Test
    fun invalidacaoNaoTemGracaMesmoComOCacheVencidoHaPouco() {
        ligar(30)
        CacheDeLeituras.obter("k") { listOf("antes da sincronização") }
        agora.set(Duration.ofSeconds(31).toNanos())
        CacheDeLeituras.invalidar()
        assertEquals(listOf("depois"), CacheDeLeituras.obter("k") { listOf("depois") })
    }

    @Test
    fun umaRenovacaoPorVezEFalhaDaRenovacaoNaoDerrubaAsRequisicoes() {
        val fila = ArrayDeque<Runnable>()
        ligar(30, renovador = Executor { fila.add(it) })
        val chamadas = AtomicInteger()
        val primeiro = CacheDeLeituras.obter("k") { chamadas.incrementAndGet(); listOf("v1") }
        agora.set(Duration.ofSeconds(31).toNanos())
        // a primeira requisição vencida pede a renovação (aqui com uma carga que vai falhar); as outras só recebem a antiga
        assertSame(primeiro, CacheDeLeituras.obter<List<String>>("k") { error("banco ocupado") })
        repeat(4) { assertSame(primeiro, CacheDeLeituras.obter("k") { chamadas.incrementAndGet(); listOf("v2") }) }
        assertEquals(1, fila.size, "cinco requisições vencidas pedem UMA renovação")
        assertEquals(1, chamadas.get(), "ninguém carregou na frente do usuário")
        // a renovação falha: a antiga segue valendo e uma nova renovação pode ser pedida
        fila.removeFirst().run()
        assertEquals(1, chamadas.get())
        assertSame(primeiro, CacheDeLeituras.obter("k") { chamadas.incrementAndGet(); listOf("v3") })
        assertEquals(1, fila.size, "depois da falha, a próxima requisição vencida volta a pedir renovação")
        fila.removeFirst().run()
        assertEquals(2, chamadas.get())
        assertEquals(listOf("v3"), CacheDeLeituras.obter("k") { listOf("nunca") })
    }

    @Test
    fun invalidarFazAProximaLeituraRecarregar() {
        ligar()
        val chamadas = AtomicInteger()
        val a = CacheDeLeituras.obter("k") { chamadas.incrementAndGet(); listOf("velho") }
        CacheDeLeituras.invalidar()
        val b = CacheDeLeituras.obter("k") { chamadas.incrementAndGet(); listOf("novo") }
        assertEquals(listOf("velho"), a)
        assertEquals(listOf("novo"), b)
        assertEquals(2, chamadas.get())
        // e depois da recarga volta a reaproveitar
        assertSame(b, CacheDeLeituras.obter("k") { chamadas.incrementAndGet(); listOf("outro") })
        assertEquals(2, chamadas.get())
    }

    @Test
    fun invalidacaoDuranteACargaNaoDeixaDadoVelhoValido() {
        ligar()
        val chamadas = AtomicInteger()
        // a escrita termina (invalidar) enquanto esta leitura ainda carrega: o que ela carregou já pode estar velho
        val lido = CacheDeLeituras.obter("k") { chamadas.incrementAndGet(); CacheDeLeituras.invalidar(); listOf("meio da escrita") }
        assertEquals(listOf("meio da escrita"), lido)
        val seguinte = CacheDeLeituras.obter("k") { chamadas.incrementAndGet(); listOf("depois da escrita") }
        assertEquals(listOf("depois da escrita"), seguinte)
        assertEquals(2, chamadas.get())
    }

    @Test
    fun chavesDiferentesNaoSeMisturam() {
        ligar()
        val a = CacheDeLeituras.obter(ChaveDeLeitura("banco-a", "snapshots", "samsung")) { listOf("A") }
        val b = CacheDeLeituras.obter(ChaveDeLeitura("banco-b", "snapshots", "samsung")) { listOf("B") }
        val c = CacheDeLeituras.obter(ChaveDeLeitura("banco-a", "snapshots", "sidia")) { listOf("C") }
        assertEquals(listOf("A"), a)
        assertEquals(listOf("B"), b)
        assertEquals(listOf("C"), c)
        assertEquals(listOf("A"), CacheDeLeituras.obter(ChaveDeLeitura("banco-a", "snapshots", "samsung")) { listOf("X") })
    }

    @Test
    fun falhaNaCargaNaoEhGuardada() {
        ligar()
        assertFailsWith<IllegalStateException> { CacheDeLeituras.obter("k") { error("banco ocupado") } }
        assertEquals(listOf("ok"), CacheDeLeituras.obter("k") { listOf("ok") })
    }

    @Test
    fun desligarLimpaTudo() {
        ligar()
        CacheDeLeituras.obter("k") { listOf("velho") }
        CacheDeLeituras.desligar()
        assertEquals(listOf("novo"), CacheDeLeituras.obter("k") { listOf("novo") })
        ligar()
        assertEquals(listOf("novo2"), CacheDeLeituras.obter("k") { listOf("novo2") }, "ligar de novo começa vazio")
    }

    @Test
    fun umaCargaPorChaveMesmoComVariosLeitoresAoMesmoTempo() {
        ligar()
        val chamadas = AtomicInteger()
        val pronto = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(8)
        try {
            val resultados = (1..8).map {
                pool.submit<List<Int>> {
                    pronto.await()
                    CacheDeLeituras.obter("k") { chamadas.incrementAndGet(); Thread.sleep(200); listOf(42) }
                }
            }
            pronto.countDown()
            val listas = resultados.map { it.get(10, TimeUnit.SECONDS) }
            assertEquals(1, chamadas.get(), "só uma carga, as outras esperam e reaproveitam")
            assertTrue(listas.all { it === listas.first() })
        } finally {
            pool.shutdownNow()
        }
    }

    @Test
    fun chavesDiferentesCarregamEmParalelo() {
        ligar()
        val pool = Executors.newFixedThreadPool(2)
        try {
            val dentro = CountDownLatch(2)
            val fs = (1..2).map { n ->
                pool.submit<Boolean> {
                    CacheDeLeituras.obter("k$n") {
                        dentro.countDown()
                        // as duas cargas só terminam se rodarem ao mesmo tempo: uma trava global as serializaria
                        dentro.await(5, TimeUnit.SECONDS)
                        listOf(n)
                    }
                    dentro.count == 0L
                }
            }
            assertTrue(fs.all { it.get(10, TimeUnit.SECONDS) })
        } finally {
            pool.shutdownNow()
        }
    }

    @Test
    fun cargasSimultaneasTemTetoParaNaoEstourarAMemoria() {
        ligar()
        val agoraCarregando = AtomicInteger()
        val maximo = AtomicInteger()
        val pool = Executors.newFixedThreadPool(8)
        try {
            val fs = (1..8).map { n ->
                pool.submit {
                    CacheDeLeituras.obter("chave-$n") {
                        val dentro = agoraCarregando.incrementAndGet()
                        maximo.accumulateAndGet(dentro, ::maxOf)
                        Thread.sleep(150)
                        agoraCarregando.decrementAndGet()
                        listOf(n)
                    }
                }
            }
            fs.forEach { it.get(20, TimeUnit.SECONDS) }
            assertTrue(maximo.get() <= CacheDeLeituras.MAXIMO_DE_CARGAS_SIMULTANEAS, "cargas ao mesmo tempo: ${maximo.get()}")
            assertTrue(maximo.get() >= 2, "o teto não pode serializar tudo: ${maximo.get()}")
        } finally {
            pool.shutdownNow()
        }
    }

    @Test
    fun cargaQueFalhaDevolveAVagaDoTeto() {
        ligar()
        repeat(CacheDeLeituras.MAXIMO_DE_CARGAS_SIMULTANEAS + 3) { n ->
            assertFailsWith<IllegalStateException> { CacheDeLeituras.obter("falha-$n") { error("x") } }
        }
        assertEquals(listOf("ok"), CacheDeLeituras.obter("depois") { listOf("ok") })
    }

    @Test
    fun numeroDeEntradasTemTeto() {
        ligar()
        val chamadas = AtomicInteger()
        for (i in 1..(CacheDeLeituras.MAXIMO_DE_ENTRADAS + 5)) CacheDeLeituras.obter("k$i") { chamadas.incrementAndGet(); listOf(i) }
        assertTrue(CacheDeLeituras.tamanho() <= CacheDeLeituras.MAXIMO_DE_ENTRADAS)
        // a mais antiga saiu e recarrega; a mais nova continua
        val antes = chamadas.get()
        CacheDeLeituras.obter("k${CacheDeLeituras.MAXIMO_DE_ENTRADAS + 5}") { chamadas.incrementAndGet(); listOf(0) }
        assertEquals(antes, chamadas.get())
        CacheDeLeituras.obter("k1") { chamadas.incrementAndGet(); listOf(0) }
        assertEquals(antes + 1, chamadas.get())
    }
}
