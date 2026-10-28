package br.sidia.painel.pessoas

import br.sidia.painel.security.RoleDirectory
import com.charleskorn.kaml.Yaml
import kotlinx.serialization.Serializable
import java.nio.file.Files
import java.nio.file.Path

/**
 * Quanto uma pessoa produz: horas por semana, [foco] (fração do dia dedicada a tasks; nulo = `focoPadrao` do planejamento),
 * [alocacao] ao time (0 a 1) e [competencias] (rótulos em minúsculas). Dado pessoal: vem de `equipes.yaml`, fora do git.
 */
data class MembroEquipe(val id: String, val horasSemanais: Double, val foco: Double?, val alocacao: Double, val competencias: Set<String>) {
    init {
        require(horasSemanais in 0.0..60.0) { "horasSemanais fora de 0 a 60" }
        require(foco == null || (foco > 0.0 && foco <= 1.0)) { "foco fora de (0, 1]" }
        require(alocacao in 0.0..1.0) { "alocacao fora de 0 a 1" }
        require(competencias.all { COMPETENCIA.matches(it) }) { "competência fora do formato [a-z0-9-]" }
    }

    companion object {
        private val COMPETENCIA = Regex("[a-z0-9-]+")
    }
}

/** As pessoas da equipe, na ordem do arquivo. Somente leitura. */
class Equipes(membros: List<MembroEquipe>) {
    private val porId: Map<String, MembroEquipe>
    val todos: List<MembroEquipe> = membros.toList()

    init {
        val repetidos = todos.groupingBy { it.id }.eachCount().filterValues { it > 1 }.keys
        require(repetidos.isEmpty()) { "id repetido em equipes" }
        porId = todos.associateBy { it.id }
    }

    fun de(id: String): MembroEquipe? = porId[id]

    companion object {
        /** Estrito: chave desconhecida, limite violado, id repetido ou id que não existe no `papeis.yaml` falham citando o arquivo. */
        fun fromYaml(path: Path, roles: RoleDirectory): Equipes = lerEstrito(path) {
            val bruto = Yaml.default.decodeFromString(EquipesYaml.serializer(), Files.readString(path))
            val membros = bruto.membros.mapIndexed { i, m ->
                require(roles.conhece(m.id)) { "membro #${i + 1} não existe no papeis.yaml" }
                try {
                    MembroEquipe(m.id, m.horasSemanais, m.foco, m.alocacao, m.competencias.toSet())
                } catch (e: IllegalArgumentException) {
                    throw IllegalArgumentException("membro #${i + 1}: ${e.message}")
                }
            }
            Equipes(membros)
        }
    }
}

@Serializable
private data class EquipesYaml(val membros: List<MembroYaml> = emptyList())

@Serializable
private data class MembroYaml(
    val id: String,
    val horasSemanais: Double,
    val foco: Double? = null,
    val alocacao: Double,
    val competencias: List<String> = emptyList(),
)
