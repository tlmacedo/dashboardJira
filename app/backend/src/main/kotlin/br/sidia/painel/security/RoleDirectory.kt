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
        // Validação sempre no construtor: nenhum diretório inválido chega a existir.
        pessoas.forEach(::validar)
    }

    private fun ativos(userId: String, date: LocalDate): List<AtribuicaoPapel> =
        pessoas[userId]?.papeis.orEmpty().filter { it.vigencia.contem(date) }

    /** Papéis da pessoa na data (vazio se desconhecida). */
    fun rolesOf(userId: String, date: LocalDate): Set<Role> = ativos(userId, date).map { it.papel }.toSet()

    /** Há ao menos uma pessoa com o [papel] vigente na data. */
    fun hasRoleOn(papel: Role, date: LocalDate): Boolean =
        pessoas.keys.any { papel in rolesOf(it, date) }

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
        private val PADRAO_ID = Regex("^(ge|co|sl|us)-\\d{3}$")
        private val PAPEIS_COM_SQUAD = setOf(Role.USUARIO, Role.SQUAD_LEADER)

        /** Lê `pessoas: [{id, nome, papeis: [{papel, de?, ate?, squad?}]}]` de um YAML. */
        fun fromYaml(path: Path): RoleDirectory {
            val arquivo = Yaml.default.decodeFromString(ArquivoPapeis.serializer(), Files.readString(path))
            val pessoas = arquivo.pessoas.map { p ->
                Pessoa(
                    p.id,
                    p.nome,
                    p.papeis.map {
                        val papel = Role.entries.firstOrNull { r -> r.name == it.papel.uppercase() }
                            ?: throw IllegalArgumentException("Papel desconhecido '${it.papel}' em ${p.id}")
                        AtribuicaoPapel(papel, Vigencia(it.de?.let(LocalDate::parse), it.ate?.let(LocalDate::parse)), it.squad)
                    },
                )
            }
            return RoleDirectory(pessoas)
        }

        /** Validações de cada pessoa: padrão do id, squad obrigatório e vigências de USUARIO sem sobreposição. */
        private fun validar(p: Pessoa) {
            require(PADRAO_ID.matches(p.id)) {
                "Id de pessoa fora do padrão (ge|co|sl|us)-NNN: '${p.id}'"
            }
            p.papeis.forEach {
                require(it.papel !in PAPEIS_COM_SQUAD || !it.squad.isNullOrBlank()) {
                    "${it.papel} sem squad: ${p.id}"
                }
            }
            // Um usuário não pode estar em dois squads no mesmo dia (pontas inclusivas).
            val usuario = p.papeis.filter { it.papel == Role.USUARIO }
            for (i in usuario.indices) {
                for (j in i + 1 until usuario.size) {
                    val a = usuario[i]
                    val b = usuario[j]
                    if (a.squad == b.squad) continue
                    val inicio = listOfNotNull(a.vigencia.de, b.vigencia.de).maxOrNull()
                    val fim = listOfNotNull(a.vigencia.ate, b.vigencia.ate).minOrNull()
                    if (inicio == null || fim == null || !inicio.isAfter(fim)) {
                        throw IllegalArgumentException(
                            "Vigências de USUARIO em squads diferentes (${a.squad} e ${b.squad}) se sobrepõem " +
                                "para ${p.id}: ${inicio ?: "início aberto"} a ${fim ?: "fim aberto"}",
                        )
                    }
                }
            }
        }
    }
}

@Serializable
private data class ArquivoPapeis(val pessoas: List<PessoaYaml> = emptyList())

@Serializable
private data class PessoaYaml(val id: String, val nome: String = "", val papeis: List<PapelYaml> = emptyList())

@Serializable
private data class PapelYaml(val papel: String, val de: String? = null, val ate: String? = null, val squad: String? = null)
