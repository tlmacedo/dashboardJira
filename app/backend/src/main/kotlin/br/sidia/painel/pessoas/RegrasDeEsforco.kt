package br.sidia.painel.pessoas

import com.charleskorn.kaml.Yaml
import kotlinx.serialization.Serializable
import java.nio.file.Files
import java.nio.file.Path

/**
 * Pesos do esforço por task fechada, lidos de `esforco.yaml` (versionado, sem dado pessoal; em produção o arquivo real é
 * obrigatório). Índice = peso do tipo + testes × [pesoTeste] + horas × [fatorHora]: proposta inicial a calibrar com o
 * histórico real. A [versao] sai em toda resposta dos cards como `esforço v<versao>`.
 */
data class RegrasDeEsforco(
    val versao: Int,
    val pesoPorTipo: Map<String, Double>,
    val pesoPadrao: Double,
    val prefixoTeste: String,
    val pesoTeste: Double,
    val fatorHora: Double,
) {
    init {
        require(versao == 1) { "versao não suportada (esperado: 1)" }
        require(pesoPorTipo.values.all { it > 0.0 && it <= LIMITE_DO_PESO }) { "pesoPorTipo com valor fora de (0, $LIMITE_DO_PESO]" }
        require(pesoPadrao > 0.0 && pesoPadrao <= LIMITE_DO_PESO) { "pesoPadrao fora de (0, $LIMITE_DO_PESO]" }
        require(prefixoTeste.isNotBlank() && prefixoTeste.length <= TAMANHO_MAXIMO_DO_PREFIXO && prefixoTeste.none { it.isISOControl() }) {
            "prefixoTeste vazio, longo demais ou com caractere de controle"
        }
        require(pesoTeste >= 0.0 && pesoTeste <= LIMITE_DO_PESO) { "pesoTeste fora de [0, $LIMITE_DO_PESO]" }
        require(fatorHora >= 0.0 && fatorHora <= LIMITE_DO_PESO) { "fatorHora fora de [0, $LIMITE_DO_PESO]" }
    }

    /** O texto da versão que sai em toda resposta (`esforço v1`). */
    val rotulo: String get() = "esforço v$versao"

    companion object {
        private const val LIMITE_DO_PESO = 1000.0
        private const val TAMANHO_MAXIMO_DO_PREFIXO = 32

        /** Valores do modelo `esforco.exemplo.yaml`, para os testes que não leem o arquivo. */
        val PADRAO = RegrasDeEsforco(
            versao = 1,
            pesoPorTipo = mapOf("Checklist" to 3.0, "Checklist TG" to 3.0, "Checklist Review" to 1.0, "Feature check" to 2.0),
            pesoPadrao = 1.0, prefixoTeste = "TEST-", pesoTeste = 0.5, fatorHora = 1.0,
        )

        /** Estrito: chave desconhecida ou ausente e limite violado falham citando o arquivo (nunca o valor lido). */
        fun fromYaml(path: Path): RegrasDeEsforco = lerEstrito(path) {
            val b = Yaml.default.decodeFromString(RegrasDeEsforcoYaml.serializer(), Files.readString(path))
            RegrasDeEsforco(b.versao, b.pesoPorTipo, b.pesoPadrao, b.prefixoTeste, b.pesoTeste, b.fatorHora)
        }
    }
}

@Serializable
private data class RegrasDeEsforcoYaml(
    val versao: Int,
    val pesoPorTipo: Map<String, Double>,
    val pesoPadrao: Double,
    val prefixoTeste: String,
    val pesoTeste: Double,
    val fatorHora: Double,
)
