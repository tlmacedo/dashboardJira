package br.sidia.painel.auditoria

import br.sidia.painel.sync.ChangeEventRecord
import java.time.Instant
import java.time.LocalDate

/**
 * Construtores de teste para cenários com várias issues (Task × Review, no prazo, calculador). Sobre o cenário
 * AUD-1 ([CenarioAud1]): mesmos papéis, regras, labels e mapa de status. Datas e chaves fictícias.
 */
class CenarioMulti(val c: CenarioAud1 = CenarioAud1()) {
    private var seq = 0

    fun i(s: String): Instant = Instant.parse(s)
    fun d(s: String): LocalDate = LocalDate.parse(s)

    fun ev(key: String, campo: String, de: String?, para: String?, at: String, autor: String? = "pl.alfa") =
        ChangeEventRecord("$key:${++seq}:0", key, campo, de, para, i(at), autor)

    @Suppress("LongParameterList")
    fun issue(
        key: String,
        eventos: List<ChangeEventRecord> = emptyList(),
        assignee: String? = "ana",
        duedate: String? = "2026-10-15",
        status: String = "Open",
        resolution: String? = null,
        resolved: String? = null,
        labels: List<String> = emptyList(),
        tipo: String = "Task",
        reporter: String? = "pl.alfa",
        criada: String = "2026-10-01T12:00:00Z",
    ) = AuditIssue(
        "dc", key, "Resumo $key", tipo, null, reporter, assignee, duedate?.let(::d), i(criada),
        resolved?.let(::i), resolution, status, labels, eventos,
    )

    /** Sinal escrito à mão (para testar quem só consome sinais). */
    fun sinal(
        codigo: CodigoSinal, key: String, para: String? = null, at: String = "2026-10-16T15:00:00Z",
        resultado: Resultado = Resultado.AUDITORIA,
    ) = Sinal(
        codigo, resultado, 3, key, "labels", null, para, i(at), "carla", false, MotivoAutorizacao.NAO_AUTORIZADO, "ana", "$key:s:0",
    )
}
