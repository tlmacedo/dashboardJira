package br.sidia.painel.integridade

import br.sidia.painel.cards.AvisoDto
import br.sidia.painel.integridade.Int1.fechada
import br.sidia.painel.trabalho.IssueSnapshot
import br.sidia.painel.trabalho.SnapDeTeste.ev
import br.sidia.painel.trabalho.SnapDeTeste.snap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Sinais de prazo, fluxo, estimativa e criticidade da Integridade no cenário INT-1. */
class DetectoresDePrazoEFluxoTest {
    private val comEstimativa = Int1.regras.copy(ativos = Int1.regras.ativos + SinalIntegridade.ESTIMATIVA_AUMENTADA)

    private fun detectar(issues: List<IssueSnapshot>, regras: RegrasDeIntegridade = Int1.regras): List<EvidenciaIntegridade> =
        DetectoresDePrazoEFluxo.todos.flatMap { it.detectar(issues, regras, Int1.ctx) }

    private fun de(sinal: SinalIntegridade, issues: List<IssueSnapshot>, regras: RegrasDeIntegridade = Int1.regras) =
        detectar(issues, regras).filter { it.sinal == sinal }

    private fun prorroga(key: String, em: String, de: String, para: String, autor: String? = "pl.alfa") = ev(key, "duedate", de, para, em, autor)

    // ---- Prazo ----

    @Test
    fun resgatePorProrrogacao() {
        // Vencida em 15/10, prorrogada em 17/10 para 20/10 e fechada em 19/10: resgate, e não uma prorrogação comum.
        val s = fechada(
            "TG-601", vence = "2026-10-20", fim = "2026-10-19T12:00:00Z", autorFim = "ana",
            extras = listOf(prorroga("TG-601", "2026-10-17T12:00:00Z", "2026-10-15", "2026-10-20")),
        )
        val todas = detectar(listOf(s))
        assertEquals(listOf(SinalIntegridade.RESGATE_POR_PRORROGACAO), todas.map { it.sinal })
        val e = todas.single()
        assertEquals("pl.alfa", e.pessoa, "a pessoa é o autor da prorrogação")
        assertEquals(3, e.gravidade)
        assertEquals(Int1.i("2026-10-17T12:00:00Z"), e.at)
        assertEquals("2026-10-15", e.detalhe["de"])
        assertEquals("2026-10-20", e.detalhe["para"])
    }

    @Test
    fun prorrogadoAntesDeVencer() {
        val s = fechada(
            "TG-602", vence = "2026-10-20", fim = "2026-10-19T12:00:00Z", autorFim = "ana",
            extras = listOf(prorroga("TG-602", "2026-10-10T12:00:00Z", "2026-10-15", "2026-10-20")),
        )
        val todas = detectar(listOf(s))
        assertEquals(listOf(SinalIntegridade.VENCIMENTO_PRORROGADO), todas.map { it.sinal })
        assertEquals(2, todas.single().gravidade)
    }

    @Test
    fun prorrogacaoDeTaskAbertaTambemEhIndicio() {
        val aberta = snap(
            "TG-603", "Checklist", "ana", "2026-10-01T12:00:00Z", "In Progress", vence = "2026-10-25",
            eventos = listOf(prorroga("TG-603", "2026-10-14T12:00:00Z", "2026-10-15", "2026-10-25")),
        )
        assertEquals(listOf(SinalIntegridade.VENCIMENTO_PRORROGADO), detectar(listOf(aberta)).map { it.sinal })
    }

    @Test
    fun resgateExigeFechamentoAteOVencimentoNovo() {
        // Fechada em 21/10, depois do novo vencimento (20/10): é prorrogação, não resgate.
        val s = fechada(
            "TG-604", vence = "2026-10-20", fim = "2026-10-21T12:00:00Z", autorFim = "ana",
            extras = listOf(prorroga("TG-604", "2026-10-17T12:00:00Z", "2026-10-15", "2026-10-20")),
        )
        assertEquals(listOf(SinalIntegridade.VENCIMENTO_PRORROGADO), detectar(listOf(s)).map { it.sinal })
    }

    @Test
    fun antecipacaoNaoConta() {
        val s = fechada(
            "TG-605", vence = "2026-10-12", fim = "2026-10-11T12:00:00Z", autorFim = "ana",
            extras = listOf(prorroga("TG-605", "2026-10-10T12:00:00Z", "2026-10-15", "2026-10-12")),
        )
        assertEquals(emptyList(), de(SinalIntegridade.VENCIMENTO_PRORROGADO, listOf(s)) + de(SinalIntegridade.RESGATE_POR_PRORROGACAO, listOf(s)))
    }

    @Test
    fun vencimentoIlegivelOuPrimeiraDefinicaoNaoViraProrrogacao() {
        val s = fechada(
            "TG-606", vence = "2026-10-12", fim = "2026-10-11T12:00:00Z", autorFim = "ana",
            extras = listOf(prorroga("TG-606", "2026-10-02T12:00:00Z", "lixo", "2026-10-12"), prorroga("TG-606", "2026-10-03T12:00:00Z", "", "2026-10-12")),
        )
        assertEquals(emptyList(), de(SinalIntegridade.VENCIMENTO_PRORROGADO, listOf(s)))
    }

    @Test
    fun vencimentoTardio() {
        // Vida de 10 dias (01/10 00:00Z a 11/10 00:00Z): vencimento definido no dia 8 (80%) dispara; no dia 6 (60%) não.
        fun tarefa(key: String, definidoEm: String) = fechada(
            key, criada = "2026-10-01T00:00:00Z", inicio = "2026-10-02T00:00:00Z", fim = "2026-10-11T00:00:00Z", autorFim = "ana", vence = "2026-10-12",
            extras = listOf(prorroga(key, definidoEm, "", "2026-10-12", "lider.alfa")),
        )
        val tarde = tarefa("TG-611", "2026-10-09T00:00:00Z")
        val cedo = tarefa("TG-612", "2026-10-07T00:00:00Z")
        val evidencias = de(SinalIntegridade.VENCIMENTO_TARDIO, listOf(tarde, cedo))
        assertEquals(listOf("TG-611"), evidencias.map { it.issueKey })
        assertEquals("lider.alfa", evidencias.single().pessoa)
        assertEquals("80", evidencias.single().detalhe["pctDaVida"])
        assertEquals(2, evidencias.single().gravidade)
    }

    @Test
    fun soAPrimeiraDefinicaoContaParaVencimentoTardio() {
        // Vencimento definido cedo e mudado tarde: a mudança tardia é prorrogação, não "definição tardia".
        val s = fechada(
            "TG-613", criada = "2026-10-01T00:00:00Z", inicio = "2026-10-02T00:00:00Z", fim = "2026-10-11T00:00:00Z", autorFim = "ana", vence = "2026-10-14",
            extras = listOf(prorroga("TG-613", "2026-10-02T00:00:00Z", "", "2026-10-12"), prorroga("TG-613", "2026-10-10T00:00:00Z", "2026-10-12", "2026-10-14")),
        )
        assertEquals(emptyList(), de(SinalIntegridade.VENCIMENTO_TARDIO, listOf(s)))
    }

    // ---- Fluxo ----

    @Test
    fun pulouOFluxo() {
        // Open -> Closed, sem passar por status ativo.
        val pulou = fechada("TG-621", inicio = null, criada = "2026-10-01T12:00:00Z", fim = "2026-10-14T12:00:00Z", autorFim = "bruno")
        val normal = fechada("TG-622", autorFim = "bruno")
        val evidencias = de(SinalIntegridade.PULOU_O_FLUXO, listOf(pulou, normal))
        assertEquals(listOf("TG-621"), evidencias.map { it.issueKey })
        assertEquals("bruno", evidencias.single().pessoa)
        assertEquals(2, evidencias.single().gravidade)
    }

    @Test
    fun esperaOuBloqueioNaoSubstituiAtividade() {
        // Só passou por espera (Submitted): segue sem nunca ter ficado em status ativo.
        val s = snap(
            "TG-623", "Checklist", "ana", "2026-10-01T12:00:00Z", "Closed", resolution = "Done", resolvida = "2026-10-14T12:00:00Z",
            eventos = listOf(
                ev("TG-623", "status", "Open", "Submitted", "2026-10-02T12:00:00Z", "ana"),
                ev("TG-623", "status", "Submitted", "Closed", "2026-10-14T12:00:00Z", "ana"),
            ),
        )
        assertEquals(listOf("TG-623"), de(SinalIntegridade.PULOU_O_FLUXO, listOf(s)).map { it.issueKey })
    }

    // ---- Estimativa ----

    private fun comEstimativaMudando(key: String, de: String?, para: String?) = fechada(
        key, autorFim = "ana", extras = listOf(ev(key, "timeoriginalestimate", de, para, "2026-10-05T12:00:00Z", "carla")),
    )

    @Test
    fun estimativaSemDadoViraAviso() {
        // O changelog só traz o NOME do campo da estimativa (sem valor): nenhuma evidência e o aviso SINAL_SEM_DADO.
        val s = comEstimativaMudando("TG-631", null, null)
        assertEquals(emptyList(), de(SinalIntegridade.ESTIMATIVA_AUMENTADA, listOf(s), comEstimativa))
        assertTrue(!DetectoresDePrazoEFluxo.estimativaTemDado(listOf(s)))
        val avisos = DetectoresDePrazoEFluxo.avisosDeDados(listOf(s), comEstimativa)
        assertEquals(listOf("SINAL_SEM_DADO"), avisos.map { it.codigo })
        assertEquals(AvisoDto.INFORMATIVO, avisos.single().gravidade)
        assertEquals("ESTIMATIVA_AUMENTADA", avisos.single().params["sinal"])
        // Com o sinal desligado (o padrão) não há o que avisar.
        assertEquals(emptyList(), DetectoresDePrazoEFluxo.avisosDeDados(listOf(s), Int1.regras))
    }

    @Test
    fun estimativaComDado() {
        // 36000 -> 50400 s (+40%): sinal quando o sinal está ativo no YAML; +25% não; exatamente +30% sim (limite inclusivo).
        val sobe = comEstimativaMudando("TG-632", "36000", "50400")
        val pouco = comEstimativaMudando("TG-633", "36000", "45000")
        val limite = comEstimativaMudando("TG-634", "36000", "46800")
        val reduz = comEstimativaMudando("TG-635", "36000", "18000")
        val primeira = comEstimativaMudando("TG-636", null, "36000")
        val evidencias = de(SinalIntegridade.ESTIMATIVA_AUMENTADA, listOf(sobe, pouco, limite, reduz, primeira), comEstimativa)
        assertEquals(listOf("TG-632", "TG-634"), evidencias.map { it.issueKey }.sorted())
        assertEquals("carla", evidencias.first().pessoa)
        assertEquals(2, evidencias.first().gravidade)
        assertTrue(DetectoresDePrazoEFluxo.estimativaTemDado(listOf(sobe)))
        assertEquals(emptyList(), DetectoresDePrazoEFluxo.avisosDeDados(listOf(sobe), comEstimativa))
        // Desligado (o padrão do modelo): nenhuma evidência, mesmo com dado.
        assertEquals(emptyList(), de(SinalIntegridade.ESTIMATIVA_AUMENTADA, listOf(sobe)))
    }

    // ---- Criticidade ----

    private fun removeLabel(key: String, fim: String, removidaEm: String, de: String = "CRITICAL ui", para: String = "ui") =
        fechada(key, fim = fim, autorFim = "ana", extras = listOf(ev(key, "labels", de, para, removidaEm, "bruno")))

    @Test
    fun criticidadeRemovidaPertoDoFim() {
        // Removida 1 dia antes do fechamento: sinal; 3 dias antes: não; exatamente 2 dias: limite inclusivo.
        val perto = removeLabel("TG-641", "2026-10-14T12:00:00Z", "2026-10-13T12:00:00Z")
        val longe = removeLabel("TG-642", "2026-10-14T12:00:00Z", "2026-10-11T12:00:00Z")
        val limite = removeLabel("TG-643", "2026-10-14T12:00:00Z", "2026-10-12T12:00:00Z")
        val evidencias = de(SinalIntegridade.LABEL_CRITICIDADE_REMOVIDA, listOf(perto, longe, limite))
        assertEquals(listOf("TG-641", "TG-643"), evidencias.map { it.issueKey }.sorted())
        val e = evidencias.first { it.issueKey == "TG-641" }
        assertEquals("bruno", e.pessoa, "a pessoa é o autor da remoção")
        assertEquals("CRITICAL", e.detalhe["label"])
        assertEquals(2, e.gravidade)
    }

    @Test
    fun labelsSemDiferenciarMaiusculas() {
        val s = removeLabel("TG-644", "2026-10-14T12:00:00Z", "2026-10-13T12:00:00Z", de = "critical ui", para = "ui")
        assertEquals(1, de(SinalIntegridade.LABEL_CRITICIDADE_REMOVIDA, listOf(s)).size)
        // Remover outra label (não de criticidade) ou ADICIONAR a de criticidade não é sinal.
        assertEquals(emptyList(), de(SinalIntegridade.LABEL_CRITICIDADE_REMOVIDA, listOf(removeLabel("TG-645", "2026-10-14T12:00:00Z", "2026-10-13T12:00:00Z", "ui rede", "ui"))))
        assertEquals(emptyList(), de(SinalIntegridade.LABEL_CRITICIDADE_REMOVIDA, listOf(removeLabel("TG-646", "2026-10-14T12:00:00Z", "2026-10-13T12:00:00Z", "ui", "CRITICAL ui"))))
    }

    @Test
    fun removidaDepoisDoFechamentoNaoConta() {
        val s = removeLabel("TG-647", "2026-10-14T12:00:00Z", "2026-10-15T12:00:00Z")
        assertEquals(emptyList(), de(SinalIntegridade.LABEL_CRITICIDADE_REMOVIDA, listOf(s)))
    }

    @Test
    fun sinalInativoNaoGera() {
        val s = fechada(
            "TG-651", vence = "2026-10-20", fim = "2026-10-19T12:00:00Z", autorFim = "ana",
            extras = listOf(prorroga("TG-651", "2026-10-17T12:00:00Z", "2026-10-15", "2026-10-20")),
        )
        assertEquals(emptyList(), detectar(listOf(s), Int1.semOsSinais(SinalIntegridade.RESGATE_POR_PRORROGACAO, SinalIntegridade.VENCIMENTO_PRORROGADO)))
    }
}
