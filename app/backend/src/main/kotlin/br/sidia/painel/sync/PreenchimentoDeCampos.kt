package br.sidia.painel.sync

import br.sidia.painel.jql.FieldDictionary
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import javax.sql.DataSource

/**
 * Preenchimento retroativo de `issue.campos_dicionario` a partir do `issue.raw` já gravado (Plano 10, T1). Só lê e
 * escreve no banco local: não recebe cliente do Jira e nunca faz requisição.
 *
 * Preencher do `raw` NÃO marca a linha como completa (`campos_dicionario_em` fica nulo): o `raw` antigo pode não ter os
 * campos que a ficha passou a pedir depois. Só uma sincronização (ou a reconciliação) que já pede os campos grava o
 * marcador, e [linhasIncompletas] conta as que ainda esperam por isso.
 */
class PreenchimentoDeCampos(private val ds: DataSource) {

    /**
     * Preenche as linhas da [instancia] com `campos_dicionario IS NULL`, em lotes de [lote]; devolve quantas tocou.
     * Sem nenhum campo com customId conhecido não há o que extrair e nada é gravado (o objeto vazio gravado agora
     * esconderia a linha de um preenchimento futuro). Rodar de novo devolve 0.
     */
    fun preencher(instancia: String, campos: List<CampoDeFiltro>, lote: Int = 500): Int {
        if (campos.none { it.customId != null }) return 0
        var total = 0
        while (true) {
            val feitas = preencherUmLote(instancia, campos, lote)
            total += feitas
            if (feitas < lote) return total
        }
    }

    /** O mesmo para todas as instâncias do dicionário, cada uma com os seus campos. */
    fun preencherTudo(dic: FieldDictionary): Int =
        dic.instancias().sumOf { preencher(it, CamposDoDicionario.deFiltro(dic, it)) }

    /** Linhas ativas da [instancia] que ainda não foram gravadas por uma sincronização que pedia os campos. */
    fun linhasIncompletas(instancia: String): Int =
        ds.connection.use { con ->
            con.prepareStatement(
                "SELECT COUNT(*) FROM issue WHERE instance = ? AND removida_em IS NULL AND campos_dicionario_em IS NULL",
            ).use { ps ->
                ps.setString(1, instancia)
                ps.executeQuery().use { rs -> if (rs.next()) rs.getInt(1) else 0 }
            }
        }

    private fun preencherUmLote(instancia: String, campos: List<CampoDeFiltro>, lote: Int): Int =
        ds.connection.use { con ->
            val autoCommit = con.autoCommit
            con.autoCommit = false
            try {
                val linhas = con.prepareStatement(
                    "SELECT id, raw FROM issue WHERE instance = ? AND campos_dicionario IS NULL ORDER BY id LIMIT ?",
                ).use { ps ->
                    ps.setString(1, instancia)
                    ps.setInt(2, lote)
                    ps.executeQuery().use { rs -> buildList { while (rs.next()) add(rs.getLong(1) to rs.getString(2)) } }
                }
                con.prepareStatement("UPDATE issue SET campos_dicionario = ? WHERE id = ?").use { ps ->
                    for ((id, raw) in linhas) {
                        val issue = try {
                            Json.parseToJsonElement(raw.orEmpty()) as? JsonObject
                        } catch (e: SerializationException) {
                            null
                        }
                        val valores = issue?.let { CamposDoDicionario.extrair(it, campos) }.orEmpty()
                        ps.setString(1, CamposDoDicionario.paraJson(valores))
                        ps.setLong(2, id)
                        ps.addBatch()
                    }
                    ps.executeBatch()
                }
                con.commit()
                linhas.size
            } catch (e: Throwable) {
                con.rollback()
                throw e
            } finally {
                con.autoCommit = autoCommit
            }
        }
}
