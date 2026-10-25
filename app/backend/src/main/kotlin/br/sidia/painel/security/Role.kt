package br.sidia.painel.security

import java.time.LocalDate

/** Papéis de acesso, do menos ao mais abrangente. */
enum class Role { USUARIO, SQUAD_LEADER, COORDENADOR, GERENTE }

/** Período de vigência com pontas inclusivas; `null` significa sem limite. */
data class Vigencia(val de: LocalDate?, val ate: LocalDate?) {
    init {
        require(de == null || ate == null || !de.isAfter(ate)) { "Vigência invertida: de=$de > ate=$ate" }
    }

    fun contem(d: LocalDate): Boolean =
        (de == null || !d.isBefore(de)) && (ate == null || !d.isAfter(ate))
}

/** Papel atribuído a uma pessoa durante uma vigência, opcionalmente ligado a um squad. */
data class AtribuicaoPapel(val papel: Role, val vigencia: Vigencia, val squad: String? = null)

/** Pessoa do diretório com todo o histórico de papéis. */
data class Pessoa(val id: String, val nome: String, val papeis: List<AtribuicaoPapel>)

/** Quem está logado e com quais papéis. */
data class Principal(val userId: String, val roles: Set<Role>)
