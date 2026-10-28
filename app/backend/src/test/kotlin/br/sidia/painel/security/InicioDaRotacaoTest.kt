package br.sidia.painel.security

import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** O início da rotação do segredo da sessão sobrevive ao reinício do painel (arquivo ao lado do banco). */
class InicioDaRotacaoTest {
    private val pasta: Path = Files.createTempDirectory("painel-rotacao-")
    private val arquivo = InicioDaRotacao.arquivoAoLadoDe(pasta.resolve("painel.db"))
    private val anterior = ByteArray(32) { (it + 1).toByte() }
    private val t0 = Instant.parse("2026-10-28T15:00:00Z")

    @AfterTest
    fun limpar() {
        pasta.toFile().deleteRecursively()
    }

    @Test
    fun arquivoFicaAoLadoDoBanco() {
        assertEquals(pasta.resolve("painel.db.rotacao"), arquivo)
    }

    @Test
    fun primeiraSubidaGravaOInstanteEAsSeguintesOReaproveitam() {
        assertEquals(t0, InicioDaRotacao.resolver(arquivo, anterior, t0))
        // reinícios horas depois: a janela NÃO recomeça
        assertEquals(t0, InicioDaRotacao.resolver(arquivo, anterior, t0.plusSeconds(3 * 3600)))
        assertEquals(t0, InicioDaRotacao.resolver(arquivo, anterior, t0.plusSeconds(30 * 3600)))
    }

    @Test
    fun arquivoComDataNoFuturoNaoEstendeAJanela() {
        InicioDaRotacao.resolver(arquivo, anterior, t0)
        // alguém (ou um relógio errado) deixou o início no futuro: valeria para sempre; vale no máximo "agora"
        val impressao = Files.readAllLines(arquivo)[0]
        Files.writeString(arquivo, impressao + "\n" + t0.plusSeconds(365L * 86_400).epochSecond + "\n")
        val agora = t0.plusSeconds(3600)
        assertEquals(agora, InicioDaRotacao.resolver(arquivo, anterior, agora))
        // e uma data legítima do passado segue valendo
        Files.writeString(arquivo, impressao + "\n" + t0.epochSecond + "\n")
        assertEquals(t0, InicioDaRotacao.resolver(arquivo, anterior, agora))
    }

    @Test
    fun oArquivoNaoGuardaOSegredoAnterior() {
        InicioDaRotacao.resolver(arquivo, anterior, t0)
        val texto = Files.readString(arquivo)
        assertFalse(texto.contains(java.util.Base64.getEncoder().encodeToString(anterior)))
        assertFalse(texto.contains(anterior.joinToString("") { "%02x".format(it) }))
        assertEquals(2, texto.trim().lines().size)
    }

    @Test
    fun outraRotacaoComoutroSegredoAnteriorRecomeca() {
        InicioDaRotacao.resolver(arquivo, anterior, t0)
        val outro = ByteArray(32) { (it + 50).toByte() }
        val depois = t0.plusSeconds(86_400)
        assertEquals(depois, InicioDaRotacao.resolver(arquivo, outro, depois))
        // e agora é essa rotação que persiste
        assertEquals(depois, InicioDaRotacao.resolver(arquivo, outro, depois.plusSeconds(600)))
    }

    @Test
    fun semSegredoAnteriorOArquivoSai() {
        InicioDaRotacao.resolver(arquivo, anterior, t0)
        assertTrue(Files.exists(arquivo))
        assertNull(InicioDaRotacao.resolver(arquivo, null, t0.plusSeconds(10)))
        assertFalse(Files.exists(arquivo))
        // uma rotação futura com o MESMO segredo antigo é uma rotação nova: começa de novo
        assertEquals(t0.plusSeconds(99), InicioDaRotacao.resolver(arquivo, anterior, t0.plusSeconds(99)))
    }

    @Test
    fun arquivoCorrompidoOuIlegivelNaoDerrubaAsubida() {
        Files.writeString(arquivo, "lixo\nnao-e-numero\n")
        assertEquals(t0, InicioDaRotacao.resolver(arquivo, anterior, t0))
        Files.writeString(arquivo, "")
        assertEquals(t0.plusSeconds(1), InicioDaRotacao.resolver(arquivo, anterior, t0.plusSeconds(1)))
    }

    @Test
    fun pastaInexistenteEhCriadaEFalhaDeEscritaCaiNoComportamentoAntigo() {
        val aninhado = pasta.resolve("a/b/painel.db.rotacao")
        assertEquals(t0, InicioDaRotacao.resolver(aninhado, anterior, t0))
        assertTrue(Files.exists(aninhado))
        // o destino é um diretório: não dá para gravar, mas a subida segue com "agora"
        val impossivel = pasta.resolve("dir.rotacao").also { Files.createDirectories(it.resolve("x")) }
        assertEquals(t0, InicioDaRotacao.resolver(impossivel, anterior, t0))
    }

    @Test
    fun rotacaoPersistidaFechaAJanelaDoSegredoAnteriorMesmoAposReiniciar() {
        // Sessão de 24 h com janela de rotação de 8 h: o cookie velho ainda não venceu às 9 h, só a janela é que fechou.
        val duracao = java.time.Duration.ofHours(24)
        val janela = java.time.Duration.ofHours(8)
        val antigo = java.util.Base64.getEncoder().encodeToString(ByteArray(48) { (it * 3).toByte() })
        val novo = java.util.Base64.getEncoder().encodeToString(ByteArray(48) { (it * 3 + 1).toByte() })
        val segredos = SegredosDaSessao.de(novo, antigo)
        val velho = SessaoAssinada(SegredosDaSessao.de(antigo, null), duracao) { t0 }.emitir("ana.silva")

        fun subir(agora: Instant): SessaoAssinada =
            SessaoAssinada(
                segredos, duracao, janela, inicioDaRotacao = InicioDaRotacao.resolver(arquivo, segredos.anterior, agora),
            ) { agora }

        // 1ª subida da rotação às 15:00; o cookie velho vale
        assertEquals("ana.silva", subir(t0).ler(velho)?.userId)
        // reinício 7 h depois: ainda dentro da janela
        assertEquals("ana.silva", subir(t0.plusSeconds(7 * 3600)).ler(velho)?.userId)
        // reinício 9 h depois: a janela (8 h desde as 15:00) JÁ fechou; sem persistir, o reinício a reabriria
        assertNull(subir(t0.plusSeconds(9 * 3600)).ler(velho))
    }
}
