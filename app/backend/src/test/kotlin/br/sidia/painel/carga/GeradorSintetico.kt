package br.sidia.painel.carga

import br.sidia.painel.sync.ChangeEventRecord
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.nio.file.Files
import java.nio.file.Path
import java.sql.Timestamp
import java.sql.Types
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Random
import javax.sql.DataSource

/** Tamanho de um cenário de carga. A escala da spec (4.6) é [COMPLETA]; o CI usa [MODERADA]. */
class Escala(val issues: Int, val eventosPorIssue: Int, val pessoas: Int, val worklogs: Int) {
    companion object {
        /** A escala da spec mestra 4.6: dezenas de pessoas e dezenas de milhares de issues. */
        val COMPLETA = Escala(issues = 30_000, eventosPorIssue = 10, pessoas = 60, worklogs = 50_000)

        /** Cenário menor, para rodar rápido onde a máquina é mais lenta (CI). */
        val MODERADA = Escala(issues = 4_000, eventosPorIssue = 8, pessoas = 24, worklogs = 6_000)

        /** Cenário mínimo, para os testes da suíte padrão. */
        val MINIMA = Escala(issues = 400, eventosPorIssue = 6, pessoas = 12, worklogs = 600)

        /** `painel.carga.escala` = `completa` (padrão), `moderada` ou `minima`. */
        fun daPropriedade(): Escala = when (System.getProperty("painel.carga.escala")?.lowercase()) {
            "moderada" -> MODERADA
            "minima" -> MINIMA
            else -> COMPLETA
        }
    }
}

/** Uma issue pronta para o `SqliteIssueStore.upsert` (o caminho real da sincronização), com os eventos do changelog. */
class IssueSincronizada(val json: JsonObject, val eventos: List<ChangeEventRecord>)

/** O que o gerador gravou. */
class ResumoSintetico(val issues: Int, val eventos: Int, val worklogs: Int, val pessoas: Int)

/**
 * Dados SINTÉTICOS e determinísticos (mesma semente, mesmo banco) na escala da spec, para medir o desempenho sobre o
 * SQLite em arquivo. Nada vem do Jira nem de pessoas reais: usernames `dev.sint001` a `dev.sint060`, chaves `SINT-n` e
 * instância `samsung`. Grava direto nas tabelas do `V1__base.sql` (mais as colunas da `V2`) com `PreparedStatement` em
 * lote, UMA transação por lote, como a sincronização grava (transação curta, nunca aberta durante outra coisa).
 */
object GeradorSintetico {
    const val INSTANCIA = "samsung"
    const val SQUADS = 6

    /** O "agora" dos cenários (o relógio dos testes de carga). */
    val AGORA: Instant = Instant.parse("2026-10-28T15:00:00Z")

    private const val LOTE = 2_000
    private val TIPOS = listOf("Checklist", "Checklist TG", "Checklist Review")
    private val PRIORIDADES = listOf("High", "Medium", "Low")
    private val SEM_RESOLUCAO_NEGATIVA = listOf("Done", "Done", "Done", "Done", "Done", "Done", "Done", "Won't Do", "Duplicate")
    private val FORMATO_JIRA: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSxx").withZone(ZoneOffset.UTC)

    fun usuario(n: Int): String = "dev.sint%03d".format(n)

    /** Status final de uma issue conforme o sorteio [x] (0..99): 55% concluídas. */
    private fun statusFinal(x: Int): String = when {
        x < 55 -> "Done"
        x < 65 -> "To Do"
        x < 85 -> "In Progress"
        x < 93 -> "In Review"
        else -> "Blocked"
    }

    /** Caminho de status até o final (pares de -> para), com 1 em 5 passando por Blocked antes de seguir. */
    private fun caminho(final: String, desvio: Boolean): List<Pair<String, String>> = when (final) {
        "To Do" -> emptyList()
        "In Progress" -> listOf("To Do" to "In Progress")
        "Blocked" -> listOf("To Do" to "In Progress", "In Progress" to "Blocked")
        "In Review" -> listOf("To Do" to "In Progress", "In Progress" to "In Review")
        else -> if (desvio) {
            listOf("To Do" to "In Progress", "In Progress" to "Blocked", "Blocked" to "In Progress", "In Progress" to "In Review", "In Review" to "Done")
        } else {
            listOf("To Do" to "In Progress", "In Progress" to "In Review", "In Review" to "Done")
        }
    }

    /** Um evento planejado, antes de ter id. */
    private class Evento(val campo: String, val de: String?, val para: String?, val em: Instant, val autor: String)

    /** Sorteia os dados de uma issue; o mesmo [rnd] na mesma ordem produz sempre a mesma issue. */
    private class Sorteio(rnd: Random, pessoas: Int, eventos: Int, agora: Instant, idadeMaxDias: Int) {
        val status: String = statusFinal(rnd.nextInt(100))
        val criada: Instant = agora.minusSeconds(rnd.nextInt(idadeMaxDias * 24) * 3600L + 3600L)
        val responsavel: String? = if (rnd.nextInt(100) < 2) null else usuario(1 + rnd.nextInt(pessoas))
        val tipo: String = TIPOS[rnd.nextInt(TIPOS.size)]
        val prioridade: String = PRIORIDADES[rnd.nextInt(PRIORIDADES.size)]
        val resolucao: String? = if (status == "Done") SEM_RESOLUCAO_NEGATIVA[rnd.nextInt(SEM_RESOLUCAO_NEGATIVA.size)] else null
        val resolvida: Instant? = if (status == "Done") minOf(agora, criada.plusSeconds((1 + rnd.nextInt(60)) * 86_400L)) else null
        val vence: LocalDate? = if (rnd.nextInt(100) < 75) {
            LocalDate.ofInstant(criada.plusSeconds((5 + rnd.nextInt(40)) * 86_400L), ZoneOffset.UTC)
        } else {
            null
        }
        val atrasada: Boolean = rnd.nextInt(100) < 5
        val estimativaS: Long = (1 + rnd.nextInt(16)) * 3600L
        val gastoS: Long = (rnd.nextInt(20)) * 1800L
        val pontos: Double = (1 + rnd.nextInt(8)).toDouble()
        val epico: String = "SINT-${90_000 + rnd.nextInt(40)}"
        val osVersion: String = "OS-${1 + rnd.nextInt(4)}"
        val sinalizada: Boolean = rnd.nextInt(100) < 3
        val atualizada: Instant = resolvida ?: minOf(agora, criada.plusSeconds((rnd.nextInt(30)) * 86_400L))
        val trilha: List<Evento> = montarEventos(rnd, pessoas, eventos)

        private fun montarEventos(rnd: Random, pessoas: Int, quantidade: Int): List<Evento> {
            val passos = caminho(status, rnd.nextInt(5) == 0)
            val fim = resolvida ?: atualizada
            val total = maxOf(quantidade, passos.size)
            val duracao = maxOf(3_600L, fim.epochSecond - criada.epochSecond)
            // Instantes crescentes dentro da vida da issue; os passos de status vêm primeiro, o resto são outros campos.
            val instantes = (1..total).map { criada.plusSeconds(duracao * it / (total + 1)) }
            val saida = ArrayList<Evento>(total)
            passos.forEachIndexed { i, (de, para) ->
                val dono = responsavel ?: usuario(1 + rnd.nextInt(pessoas))
                saida += Evento("status", de, para, instantes[i], dono)
            }
            var anterior = responsavel
            for (i in passos.size until total) {
                val dono = usuario(1 + rnd.nextInt(pessoas))
                when (i % 3) {
                    0 -> {
                        val novo = usuario(1 + rnd.nextInt(pessoas))
                        saida += Evento("assignee", anterior, novo, instantes[i], dono)
                        anterior = novo
                    }
                    1 -> saida += Evento(
                        "duedate", vence?.minusDays(3)?.toString(), vence?.toString(), instantes[i], dono,
                    )
                    else -> saida += Evento("labels", null, if (atrasada) "LATE_SENT" else "REVISADA", instantes[i], dono)
                }
            }
            return saida.sortedBy { it.em }
        }
    }

    /**
     * Grava [pessoas], [issues], eventos e worklogs. Devolve o que foi gravado. Pode ser chamado uma vez por banco novo
     * (as chaves `SINT-n` são únicas por instância).
     */
    fun popular(
        ds: DataSource,
        semente: Long,
        issues: Int = Escala.COMPLETA.issues,
        eventosPorIssue: Int = Escala.COMPLETA.eventosPorIssue,
        pessoas: Int = Escala.COMPLETA.pessoas,
        worklogs: Int = Escala.COMPLETA.worklogs,
    ): ResumoSintetico {
        require(issues > 0 && pessoas > 0 && eventosPorIssue >= 1) { "escala inválida" }
        val rnd = Random(semente)
        var totalEventos = 0
        ds.connection.use { c ->
            c.autoCommit = false
            c.prepareStatement("INSERT INTO person(jira_ref, name, active) VALUES (?, ?, 1)").use { ps ->
                for (p in 1..pessoas) {
                    ps.setString(1, usuario(p))
                    ps.setString(2, "Dev Sintetico %03d".format(p))
                    ps.addBatch()
                }
                ps.executeBatch()
            }
            c.commit()

            val baseId = c.createStatement().use { st -> st.executeQuery("SELECT COALESCE(MAX(id), 0) FROM issue").use { it.next(); it.getLong(1) } }
            val sorteios = ArrayList<Sorteio>(LOTE)
            val sorteiosPorIssue = HashMap<Int, Pair<Instant, Instant?>>() // só criada/resolvida, para os worklogs
            for (inicio in 1..issues step LOTE) {
                val fim = minOf(issues, inicio + LOTE - 1)
                sorteios.clear()
                for (n in inicio..fim) sorteios += Sorteio(rnd, pessoas, eventosPorIssue, AGORA, idadeMaxDias = 540)
                c.prepareStatement(
                    "INSERT INTO issue(key, instance, project, issue_type, status, resolution, assignee_ref, reporter_ref, created, updated, " +
                        "resolved, duedate, labels, estimate_s, spent_s, os_version, jira_id, priority, remaining_s, story_points, epic_key, " +
                        "flagged, components) VALUES (?, ?, 'TG', ?, ?, ?, ?, 'pl.sint', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, '[]')",
                ).use { ps ->
                    for ((i, s) in sorteios.withIndex()) {
                        val n = inicio + i
                        ps.setString(1, "SINT-$n")
                        ps.setString(2, INSTANCIA)
                        ps.setString(3, s.tipo)
                        ps.setString(4, s.status)
                        ps.setString(5, s.resolucao)
                        ps.setString(6, s.responsavel)
                        ps.setTimestamp(7, Timestamp.from(s.criada))
                        ps.setTimestamp(8, Timestamp.from(s.atualizada))
                        if (s.resolvida != null) ps.setTimestamp(9, Timestamp.from(s.resolvida)) else ps.setNull(9, Types.INTEGER)
                        ps.setString(10, s.vence?.toString())
                        ps.setString(11, if (s.atrasada) """["LATE_SENT"]""" else "[]")
                        ps.setLong(12, s.estimativaS)
                        ps.setLong(13, s.gastoS)
                        ps.setString(14, s.osVersion)
                        ps.setLong(15, baseId + n)
                        ps.setString(16, s.prioridade)
                        ps.setLong(17, maxOf(0L, s.estimativaS - s.gastoS))
                        ps.setDouble(18, s.pontos)
                        ps.setString(19, s.epico)
                        ps.setInt(20, if (s.sinalizada) 1 else 0)
                        ps.addBatch()
                        sorteiosPorIssue[n] = s.criada to s.resolvida
                    }
                    ps.executeBatch()
                }
                c.prepareStatement(
                    "INSERT INTO change_event(changelog_id, issue_id, field, from_value, to_value, at, author_ref) VALUES (?, ?, ?, ?, ?, ?, ?)",
                ).use { ps ->
                    for ((i, s) in sorteios.withIndex()) {
                        val n = inicio + i
                        s.trilha.forEachIndexed { e, ev ->
                            ps.setString(1, "h${e + 1}")
                            ps.setLong(2, baseId + n)
                            ps.setString(3, ev.campo)
                            ps.setString(4, ev.de)
                            ps.setString(5, ev.para)
                            ps.setTimestamp(6, Timestamp.from(ev.em))
                            ps.setString(7, ev.autor)
                            ps.addBatch()
                            totalEventos++
                        }
                    }
                    ps.executeBatch()
                }
                c.commit()
            }

            // Worklogs: cada um numa issue sorteada, dentro da vida dela e de quem era o responsável (ou qualquer pessoa).
            for (inicio in 1..worklogs step LOTE) {
                val fim = minOf(worklogs, inicio + LOTE - 1)
                c.prepareStatement("INSERT INTO worklog(issue_id, author_ref, started, seconds) VALUES (?, ?, ?, ?)").use { ps ->
                    for (w in inicio..fim) {
                        val n = 1 + rnd.nextInt(issues)
                        val (criada, resolvida) = sorteiosPorIssue[n] ?: (AGORA.minusSeconds(86_400L) to null)
                        val limite = (resolvida ?: AGORA).epochSecond - criada.epochSecond
                        val inicioWl = criada.plusSeconds(if (limite <= 1) 0 else rnd.nextLong(limite))
                        ps.setLong(1, baseId + n)
                        ps.setString(2, usuario(1 + rnd.nextInt(pessoas)))
                        ps.setTimestamp(3, Timestamp.from(inicioWl))
                        ps.setLong(4, 1_800L + rnd.nextInt(8) * 1_800L)
                        ps.addBatch()
                    }
                    ps.executeBatch()
                }
                c.commit()
            }
        }
        return ResumoSintetico(issues, totalEventos, worklogs, pessoas)
    }

    /**
     * [quantidade] issues no formato que o `SqliteIssueStore.upsert` aceita (o JSON do Jira e os eventos do changelog),
     * com chaves `<prefixo><n>` (por padrão `SINT-S<n>`, que nunca colidem com as de [popular]). É o gravador da
     * sincronização simulada; prefixos diferentes dão lotes que não se sobrepõem.
     */
    fun lotesDeSincronizacao(
        semente: Long, quantidade: Int, eventosPorIssue: Int = 10, pessoas: Int = 60, prefixo: String = "SINT-S",
    ): List<IssueSincronizada> {
        val rnd = Random(semente)
        return (1..quantidade).map { n ->
            val s = Sorteio(rnd, pessoas, eventosPorIssue, AGORA, idadeMaxDias = 30)
            val chave = "$prefixo$n"
            val json = buildJsonObject {
                put("key", chave)
                putJsonObject("fields") {
                    put("summary", "Task sintetica $chave")
                    putJsonObject("project") { put("key", "TG") }
                    putJsonObject("issuetype") { put("name", s.tipo) }
                    putJsonObject("status") { put("name", s.status) }
                    s.resolucao?.let { r -> putJsonObject("resolution") { put("name", r) } }
                    s.responsavel?.let { r -> putJsonObject("assignee") { put("name", r) } }
                    putJsonObject("reporter") { put("name", "pl.sint") }
                    put("created", FORMATO_JIRA.format(s.criada))
                    put("updated", FORMATO_JIRA.format(s.atualizada))
                    s.resolvida?.let { put("resolutiondate", FORMATO_JIRA.format(it)) }
                    s.vence?.let { put("duedate", it.toString()) }
                    putJsonArray("labels") { if (s.atrasada) add("LATE_SENT") }
                    putJsonObject("priority") { put("name", s.prioridade) }
                    put("timeoriginalestimate", s.estimativaS)
                    put("timespent", s.gastoS)
                    put("customfield_10002", s.pontos)
                    put("customfield_10003", s.epico)
                    putJsonObject("customfield_12416") { put("value", s.osVersion) }
                }
            }
            val eventos = s.trilha.mapIndexed { e, ev ->
                ChangeEventRecord("$chave:h${e + 1}:0", chave, ev.campo, ev.de, ev.para, ev.em, ev.autor)
            }
            IssueSincronizada(json, eventos)
        }
    }

    /** Os `customId` do dicionário usados em [lotesDeSincronizacao] (Story Points, Epic Link e Flagged). */
    val IDS_DE_TRABALHO: Map<String, String?> = mapOf(
        "Story Points" to "customfield_10002", "Epic Link" to "customfield_10003", "Flagged" to "customfield_10004",
    )

    /** O `customId` do OS Version usado em [lotesDeSincronizacao]. */
    const val CUSTOM_OS_VERSION = "customfield_12416"

    /**
     * Escreve em [pasta] os YAML de papéis, equipes e ausências do cenário (formato dos modelos `.exemplo`) e devolve os
     * caminhos. 6 squads (`Sint1` a `Sint6`) com as [pessoas] distribuídas por igual, um líder por squad (que também é
     * USUARIO), 2 coordenadores e 1 gerente. Todos fictícios.
     */
    fun arquivosDeEquipe(pasta: Path, pessoas: Int): Triple<Path, Path, Path> {
        Files.createDirectories(pasta)
        val papeis = StringBuilder("pessoas:\n")
        papeis.append("  - id: gerente.sint\n    nome: Gerente Sintetico\n    papeis:\n      - papel: GERENTE\n")
        for (i in 1..2) papeis.append("  - id: coord.sint$i\n    nome: Coordenador Sintetico $i\n    papeis:\n      - papel: COORDENADOR\n")
        for (s in 1..SQUADS) {
            papeis.append("  - id: lider.sint$s\n    nome: Lider Sintetico $s\n    papeis:\n")
            papeis.append("      - papel: USUARIO\n        squad: Sint$s\n      - papel: SQUAD_LEADER\n        squad: Sint$s\n")
        }
        for (p in 1..pessoas) {
            papeis.append("  - id: ${usuario(p)}\n    nome: Dev Sintetico %03d\n    papeis:\n".format(p))
            papeis.append("      - papel: USUARIO\n        squad: Sint${1 + (p - 1) % SQUADS}\n")
        }
        val equipes = StringBuilder("membros:\n")
        for (s in 1..SQUADS) equipes.append("  - id: lider.sint$s\n    horasSemanais: 40\n    foco: 0.5\n    alocacao: 1.0\n    competencias: [android, rede]\n\n")
        for (p in 1..pessoas) {
            equipes.append("  - id: ${usuario(p)}\n    horasSemanais: 40\n    foco: 0.7\n    alocacao: 1.0\n    competencias: [android, rede, ui]\n\n")
        }
        val ausencias = StringBuilder("ausencias:\n")
        for (p in 1..minOf(pessoas, 10)) {
            ausencias.append("  - pessoa: ${usuario(p)}\n    de: 2026-11-${"%02d".format(2 + p)}\n    ate: 2026-11-${"%02d".format(3 + p)}\n    fracao: 1.0\n\n")
        }
        val a = Files.writeString(pasta.resolve("papeis-sint.yaml"), papeis.toString())
        val b = Files.writeString(pasta.resolve("equipes-sint.yaml"), equipes.toString())
        val c = Files.writeString(pasta.resolve("ausencias-sint.yaml"), ausencias.toString())
        return Triple(a, b, c)
    }
}
