package br.sidia.painel.checklist

import br.sidia.painel.pessoas.lerEstrito
import com.charleskorn.kaml.Yaml
import kotlinx.serialization.Serializable
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale

/**
 * Regras do card Checklist × Review, lidas de `checklist_review.yaml` (versionado, sem dado pessoal; em produção o arquivo
 * real é obrigatório). Os tipos de Review NÃO ficam aqui: vêm da auditoria (`vinculos.yaml`, fonte única). [amostraMinima]
 * é o `n` abaixo do qual o painel não afirma aprendizado nem lift (mostra "n baixo"). A [versao] sai em toda resposta.
 */
data class RegrasChecklistReview(
    val versao: Int,
    val tiposPai: Set<String>,
    val amostraMinima: Int,
    val limiteVenceDepois: Double,
    val limiteMesmaPessoa: Double,
    val limiteParesComAlerta: Double,
    val reviewCurtaHoras: Double,
    val faixasDuracaoHoras: List<Double>,
) {
    private val pais: Set<String> = tiposPai.map { it.trim().lowercase(Locale.ROOT) }.toSet()

    init {
        require(versao == 1) { "versao não suportada (esperado: 1)" }
        require(tiposPai.isNotEmpty() && tiposPai.none { it.isBlank() }) { "tiposPai deve ter ao menos um tipo, sem itens vazios" }
        require(amostraMinima in 1..LIMITE_DA_AMOSTRA) { "amostraMinima fora de 1..$LIMITE_DA_AMOSTRA" }
        for ((nome, v) in listOf("limiteVenceDepois" to limiteVenceDepois, "limiteMesmaPessoa" to limiteMesmaPessoa, "limiteParesComAlerta" to limiteParesComAlerta)) {
            require(v > 0.0 && v <= 1.0) { "$nome fora de (0, 1]" }
        }
        require(reviewCurtaHoras > 0.0 && reviewCurtaHoras <= LIMITE_DE_HORAS) { "reviewCurtaHoras fora de (0, $LIMITE_DE_HORAS]" }
        require(faixasDuracaoHoras.size in 1..MAXIMO_DE_CORTES) { "faixasDuracaoHoras deve ter de 1 a $MAXIMO_DE_CORTES cortes" }
        require(faixasDuracaoHoras.all { it > 0.0 && it <= LIMITE_DE_HORAS }) { "faixasDuracaoHoras com corte fora de (0, $LIMITE_DE_HORAS]" }
        require(faixasDuracaoHoras.zipWithNext().all { (a, b) -> a < b }) { "faixasDuracaoHoras deve ser estritamente crescente" }
    }

    /** O texto da versão que sai em toda resposta (`checklist-review v1`). */
    val rotulo: String get() = "checklist-review v$versao"

    /** O tipo conta como o pai do par (o lado Checklist)? Sem diferenciar maiúsculas nem espaços nas pontas. */
    fun ehPai(tipo: String): Boolean = tipo.trim().lowercase(Locale.ROOT) in pais

    companion object {
        private const val LIMITE_DA_AMOSTRA = 1000
        private const val LIMITE_DE_HORAS = 1000.0
        private const val MAXIMO_DE_CORTES = 6

        /** Valores do modelo `checklist_review.exemplo.yaml`, para os testes que não leem o arquivo. */
        val PADRAO = RegrasChecklistReview(
            versao = 1, tiposPai = setOf("Checklist", "Checklist TG"), amostraMinima = 5, limiteVenceDepois = 0.10,
            limiteMesmaPessoa = 0.10, limiteParesComAlerta = 0.30, reviewCurtaHoras = 0.5, faixasDuracaoHoras = listOf(1.0, 4.0, 12.0),
        )

        /** Estrito: chave desconhecida ou ausente e limite violado falham citando o arquivo (nunca o valor lido). */
        fun fromYaml(path: Path): RegrasChecklistReview = lerEstrito(path) {
            val b = Yaml.default.decodeFromString(RegrasChecklistReviewYaml.serializer(), Files.readString(path))
            RegrasChecklistReview(
                b.versao, b.tiposPai.toSet(), b.amostraMinima, b.limiteVenceDepois, b.limiteMesmaPessoa, b.limiteParesComAlerta,
                b.reviewCurtaHoras, b.faixasDuracaoHoras,
            )
        }
    }
}

@Serializable
private data class RegrasChecklistReviewYaml(
    val versao: Int,
    val tiposPai: List<String>,
    val amostraMinima: Int,
    val limiteVenceDepois: Double,
    val limiteMesmaPessoa: Double,
    val limiteParesComAlerta: Double,
    val reviewCurtaHoras: Double,
    val faixasDuracaoHoras: List<Double>,
)
