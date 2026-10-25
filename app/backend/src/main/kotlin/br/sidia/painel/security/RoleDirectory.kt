package br.sidia.painel.security

import com.charleskorn.kaml.Yaml
import kotlinx.serialization.Serializable
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate

/**
 * Diretório de pessoas, papéis e squads com vigência por data: toda consulta recebe a data,
 * então a autorização retroativa usa o papel que valia naquele dia. Domínio puro, sem rede.
 */
class RoleDirectory(pessoas: List<Pessoa>) {
    private val pessoas: Map<String, Pessoa> = pessoas.associateBy { it.id }

    init {
        require(this.pessoas.size == pessoas.size) { "Ids de pessoa duplicados" }
        pessoas.forEach { p ->
            p.papeis.forEach {
                require(it.papel != Role.SQUAD_LEADER || !it.squad.isNullOrBlank()) {
                    "Squad Leader sem squad: ${p.id}"
                }
            }
        }
    }

    private fun ativos(userId: String, date: LocalDate): List<AtribuicaoPapel> =
        pessoas[userId]?.papeis.orEmpty().filter { it.vigencia.contem(date) }

    /** Papéis da pessoa na data (vazio se desconhecida). */
    fun rolesOf(userId: String, date: LocalDate): Set<Role> = ativos(userId, date).map { it.papel }.toSet()

    /** Squad de que a pessoa é membro na data; o papel USUARIO tem precedência sobre o de líder. */
    fun squadOf(userId: String, date: LocalDate): String? {
        val ativos = ativos(userId, date).filter { it.squad != null }
        return (ativos.firstOrNull { it.papel == Role.USUARIO } ?: ativos.firstOrNull { it.papel == Role.SQUAD_LEADER })?.squad
    }

    /** Ids das pessoas cujo squad na data é [squad] (conjunto; a ordem de exibição é de quem exibe). */
    fun membersOf(squad: String, date: LocalDate): Set<String> =
        pessoas.keys.filter { squadOf(it, date) == squad }.toSet()

    /** Squads que a pessoa lidera na data. */
    fun squadsLedBy(userId: String, date: LocalDate): Set<String> =
        ativos(userId, date).filter { it.papel == Role.SQUAD_LEADER }.mapNotNull { it.squad }.toSet()

    /**
     * Quem pode trocar responsável, vencimento e labels de atraso: o próprio Reporter,
     * coordenador/gerente na data, ou Squad Leader do squad do responsável na data.
     */
    fun isReporterAuthorized(reporterId: String, actorId: String, assigneeId: String?, date: LocalDate): Boolean {
        if (actorId == reporterId) return true
        val papeis = rolesOf(actorId, date)
        if (Role.COORDENADOR in papeis || Role.GERENTE in papeis) return true
        val squadDoResponsavel = assigneeId?.let { squadOf(it, date) } ?: return false
        return squadDoResponsavel in squadsLedBy(actorId, date)
    }

    companion object {
        /** Lê `pessoas: [{id, nome, papeis: [{papel, de?, ate?, squad?}]}]` de um YAML. */
        fun fromYaml(path: Path): RoleDirectory {
            val arquivo = Yaml.default.decodeFromString(ArquivoPapeis.serializer(), Files.readString(path))
            return RoleDirectory(
                arquivo.pessoas.map { p ->
                    Pessoa(
                        p.id,
                        p.nome,
                        p.papeis.map {
                            val papel = Role.entries.firstOrNull { r -> r.name == it.papel.uppercase() }
                                ?: throw IllegalArgumentException("Papel desconhecido '${it.papel}' em ${p.id}")
                            AtribuicaoPapel(papel, Vigencia(it.de?.let(LocalDate::parse), it.ate?.let(LocalDate::parse)), it.squad)
                        },
                    )
                },
            )
        }
    }
}

@Serializable
private data class ArquivoPapeis(val pessoas: List<PessoaYaml> = emptyList())

@Serializable
private data class PessoaYaml(val id: String, val nome: String = "", val papeis: List<PapelYaml> = emptyList())

@Serializable
private data class PapelYaml(val papel: String, val de: String? = null, val ate: String? = null, val squad: String? = null)
