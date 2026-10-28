package br.sidia.painel.cards.fluxo

import br.sidia.painel.auditoria.Vencimentos
import br.sidia.painel.db.emJson
import java.time.LocalDate
import javax.sql.DataSource

/**
 * A ponta de um vínculo, lida direto de `issue` (sem a ficha): é a única forma de saber se a outra ponta está na base
 * (e aberta) mesmo quando ela é de um tipo que nenhum card lê. Só o que o card precisa; [resolvida] = tem data de resolução.
 */
data class PontaDoLink(
    val key: String,
    val resumo: String,
    val tipo: String,
    val status: String,
    val resolvida: Boolean,
    val vence: LocalDate?,
    val responsavel: String?,
)

/** Leitura das pontas dos vínculos. Sempre por instância, sem as issues removidas logicamente; só consultas parametrizadas. */
class LeitorDePontas(private val ds: DataSource) {
    /** As pontas de [chaves] que existem na base da [instancia] (a que não existe fica fora do mapa). */
    fun ler(instancia: String, chaves: Set<String>): Map<String, PontaDoLink> {
        if (chaves.isEmpty()) return emptyMap()
        val sql = "SELECT key, json_extract(raw, '$.fields.summary'), issue_type, status, resolved IS NOT NULL, duedate, assignee_ref " +
            "FROM issue WHERE instance = ? AND removida_em IS NULL AND key IN (SELECT value FROM json_each(?))"
        return ds.connection.use { con ->
            con.prepareStatement(sql).use { ps ->
                ps.setString(1, instancia)
                ps.setString(2, chaves.emJson())
                ps.executeQuery().use { rs ->
                    buildMap {
                        while (rs.next()) {
                            put(
                                rs.getString(1),
                                PontaDoLink(
                                    key = rs.getString(1), resumo = rs.getString(2).orEmpty(), tipo = rs.getString(3).orEmpty(),
                                    status = rs.getString(4).orEmpty(), resolvida = rs.getInt(5) == 1, vence = Vencimentos.ler(rs.getString(6)),
                                    responsavel = rs.getString(7),
                                ),
                            )
                        }
                    }
                }
            }
        }
    }
}
