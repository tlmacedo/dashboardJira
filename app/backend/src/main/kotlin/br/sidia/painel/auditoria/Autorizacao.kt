package br.sidia.painel.auditoria

import br.sidia.painel.security.Role
import br.sidia.painel.security.RoleDirectory
import java.time.Instant
import java.time.ZoneId

enum class MotivoAutorizacao { REPORTER, COORDENACAO, LIDER_DO_SQUAD, PROPRIO_RESPONSAVEL, NAO_AUTORIZADO, AUTOR_DESCONHECIDO }

data class Avaliacao(val autorizado: Boolean, val motivo: MotivoAutorizacao)

/**
 * Decide se o autor de uma alteração podia fazê-la, com os papéis vigentes na data civil do fuso ([zone],
 * America/Manaus) do instante da alteração. Para autor diferente do responsável, o resultado coincide com
 * [RoleDirectory.isReporterAuthorized]; o próprio responsável nunca é autorizado por ser líder do próprio squad.
 */
class Autorizacao(private val roles: RoleDirectory, private val zone: ZoneId) {

    fun avaliar(autor: String?, reporter: String?, responsavelDeReferencia: String?, at: Instant): Avaliacao {
        if (autor.isNullOrBlank()) return Avaliacao(false, MotivoAutorizacao.AUTOR_DESCONHECIDO)
        if (autor == reporter) return Avaliacao(true, MotivoAutorizacao.REPORTER)
        val data = at.atZone(zone).toLocalDate()
        val papeis = roles.rolesOf(autor, data)
        if (Role.COORDENADOR in papeis || Role.GERENTE in papeis) return Avaliacao(true, MotivoAutorizacao.COORDENACAO)
        if (autor == responsavelDeReferencia) return Avaliacao(false, MotivoAutorizacao.PROPRIO_RESPONSAVEL)
        val squadDoResponsavel = responsavelDeReferencia?.let { roles.squadOf(it, data) }
        if (squadDoResponsavel != null && squadDoResponsavel in roles.squadsLedBy(autor, data)) {
            return Avaliacao(true, MotivoAutorizacao.LIDER_DO_SQUAD)
        }
        return Avaliacao(false, MotivoAutorizacao.NAO_AUTORIZADO)
    }
}
