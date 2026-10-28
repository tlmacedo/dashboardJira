package br.sidia.painel.operacao

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** O corte da linha em 8 KB nunca deixa parte de valor sensível, e a credencial em URL tem máscara além do teto. */
class MascaraDeLogCorteTest {
    @BeforeEach
    @AfterEach
    fun limpar() = MascaraDeLog.limparRegistrosParaTeste()

    @Test
    fun valorSensivelCruzandoOCorteNaoSobrevive() {
        val segredo = "alfa bravo charlie delta echo foxtrot golf hotel"
        val formas = listOf(
            "{\"password\":\"$segredo\"}", "{\"token\":\"$segredo\"}", "{'password':'$segredo'}", "password=$segredo",
            "Authorization: Bearer $segredo", "senha: $segredo", "x-api_key=\"$segredo\"",
        )
        for (forma in formas) {
            for (n in 8_000..8_200) {
                val saida = MascaraDeLog.mascarar("x".repeat(n) + " " + forma)
                for (palavra in segredo.split(' ')) assertFalse(palavra in saida, "'$palavra' sobreviveu em $forma com $n")
            }
        }
    }

    @Test
    fun credencialEmUrlMaiorQueOTetoEhMascaradaAteOFimDoTrecho() {
        val saida = MascaraDeLog.mascarar("conectou em https://usuario:" + "s".repeat(1_500) + "@host.exemplo.invalid/x ok")
        assertFalse("ssssssss" in saida, saida.take(80))
        assertTrue(saida.endsWith(" ok"), saida.takeLast(20))
    }
}
