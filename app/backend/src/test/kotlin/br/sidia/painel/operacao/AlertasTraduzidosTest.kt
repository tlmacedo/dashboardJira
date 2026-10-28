package br.sidia.painel.operacao

import br.sidia.painel.TestPaths
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Trava a ponta do backend com a da tela: todo código de alerta tem texto em português e em inglês no frontend. */
class AlertasTraduzidosTest {
    private fun dicionario(idioma: String): Map<String, String> =
        Json.parseToJsonElement(Files.readString(TestPaths.repoDir.resolve("app/frontend/src/i18n/$idioma.json")))
            .jsonObject.mapValues { it.value.jsonPrimitive.content }

    @Test
    fun todoCodigoDeAlertaTemTraducaoPtEEn() {
        assertEquals(14, CodigoAlerta.entries.size)
        for (idioma in listOf("pt", "en")) {
            val d = dicionario(idioma)
            for (codigo in CodigoAlerta.entries) {
                assertTrue(d["alertas.${codigo.name}"].orEmpty().isNotBlank(), "faltou alertas.${codigo.name} em $idioma.json")
            }
        }
    }
}
