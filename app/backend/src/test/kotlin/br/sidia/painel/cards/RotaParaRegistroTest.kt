package br.sidia.painel.cards

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A rota gravada no `access_log` é o MODELO da rota (`/api/tarefa/{key}`), nunca o caminho decodificado: uma chave codificada
 * em `%2F` não pode virar rota falsa nem levar a chave para o registro.
 */
class RotaParaRegistroTest {
    private val variantesTarefa = listOf(
        "/api/tarefa/TG-1",
        "/api/tarefa/..%2Fx%2FTG-1",
        "/api/tarefa/..%2fx%2fTG-1",
        "/api/tarefa/%2E%2E%2Fx%2FTG-1",
        "/api/tarefa/..%252Fx%252FTG-1",
        "/api/tarefa/..%2F..%2Fintegridade%2FTG-1",
        "/api/tarefa/TG-1;param=x",
        "/api/tarefa//TG-1",
        "/api/%74arefa/..%2Fx%2FTG-1",
        "/API/TAREFA/..%2Fx%2FTG-1",
    )

    @Test
    fun tarefaGravaSempreOModelo() {
        for (v in variantesTarefa) {
            val rota = rotaParaRegistro(v)
            assertEquals("/api/tarefa/{key}", rota, "variante $v")
            assertFalse(rota.contains("TG-1"), "variante $v")
        }
    }

    @Test
    fun historicoDaAuditoriaGravaSempreOModelo() {
        for (v in variantesTarefa.map { it.replace("tarefa", "auditoria/historico").replace("TAREFA", "AUDITORIA/HISTORICO").replace("%74arefa", "auditoria/historico") }) {
            val rota = rotaParaRegistro(v)
            assertEquals("/api/auditoria/historico/{key}", rota, "variante $v")
        }
    }

    @Test
    fun barraInvertidaCruaEParteDoSegmento() {
        // O Ktor trata `\` como caractere do segmento: a chave inteira continua sendo UM segmento do modelo.
        val variantes = listOf("..\\x\\TG-1", "..\\..\\integridade\\TG-1", "..%5Cx%5CTG-1", "..\\x%2FTG-1", "%2E%2E\\x\\TG-1")
        for (v in variantes) {
            assertEquals("/api/tarefa/{key}", rotaParaRegistro("/api/tarefa/$v"), "tarefa $v")
            assertEquals("/api/auditoria/historico/{key}", rotaParaRegistro("/api/auditoria/historico/$v"), "historico $v")
        }
    }

    @Test
    fun rotaSensivelComBarraInvertidaContinuaSensivel() {
        for (v in listOf("/api/cartoes/IN-01\\..\\..\\x/tasks", "/api/cartoes/IN-01%5C..%5Cx/tasks", "/api/integridade\\x")) {
            assertTrue(rotaSensivel(v), v)
        }
        assertFalse(rotaSensivel("/api/cartoes/AB-01/tasks"))
    }

    @Test
    fun rotaComunSegueNormalizada() {
        assertEquals("/api/integridade", rotaParaRegistro("/api/%69ntegridade?x=1"))
    }
}
