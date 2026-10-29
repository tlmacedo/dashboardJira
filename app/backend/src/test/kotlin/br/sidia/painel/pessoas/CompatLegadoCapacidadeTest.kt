package br.sidia.painel.pessoas

import br.sidia.painel.pessoas.Ocupacao.Valor
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Compatibilidade com o app Python anterior: as três regras que NÃO mudam. As fórmulas foram conferidas no código do
 * legado (`legacy/backend/jiradash/metrics/capacity.py` e `core.py`, preservados na tag `legacy-final`) e ficam
 * congeladas aqui nos comentários e nos valores esperados; o teste roda sem a pasta `legacy/`. As regras também são
 * provadas por `CapacidadeTest`, `HorasDaIssueTest` e `DemandaTest`.
 */
class CompatLegadoCapacidadeTest {
    @Test
    fun capacidadeDoDiaComoOLegado() {
        // `day_capacity_h` (capacity.py): `p.weekly_hours / 5.0 * p.focus * p.allocation` e
        // `base * (1.0 - absence_fraction(p, d))`; fim de semana e feriado = 0.
        // 40 h, foco 0,7, alocação 1,0: 5,6 h por dia útil, igual ao legado.
        val equipes = Equipes(listOf(MembroEquipe("ana", 40.0, 0.7, 1.0, emptySet())))
        val cap = Capacidade(equipes, Ausencias(emptyList()), Cap1.calendario, RegrasDePlanejamento.PADRAO)
        assertEquals(5.6, cap.dia("ana", LocalDate.of(2026, 11, 3)), 1e-9)
        assertEquals(0.0, cap.dia("ana", LocalDate.of(2026, 11, 1)), "domingo")
        // Com meio dia de ausência o legado desconta metade; o painel também.
        val meio = Capacidade(equipes, Ausencias(listOf(Ausencia("ana", LocalDate.of(2026, 11, 3), LocalDate.of(2026, 11, 3), 0.5))), Cap1.calendario, RegrasDePlanejamento.PADRAO)
        assertEquals(2.8, meio.dia("ana", LocalDate.of(2026, 11, 3)), 1e-9)
    }

    @Test
    fun horasMinimasComoOLegado() {
        // `issue_hours` (capacity.py): restante primeiro (`return remaining_s / 3600.0`); senão
        // `return max(0.5, (original_s - spent_s) / 3600.0)`.
        val horas = HorasDaIssue(RegrasDePlanejamento.PADRAO)
        val quase = Cap1.snapshot("TG-1", "Checklist", "ana", "Open", Cap1.agora, null, null, 36000L, 35280L)
        assertEquals(0.5, horas.de(quase).valor, 1e-9)
    }

    @Test
    fun jainComoOLegado() {
        // `jain_index` (core.py): `sum(xs) ** 2 / (len(xs) * sum(x * x for x in xs))`, isto é, (Σx)² / (n·Σx²);
        // aqui acrescenta-se o teto por valor (3,0) e o null com menos de 2 valores.
        assertEquals(0.9, Jain.indice(listOf(Valor(0.5), Valor(1.0)), 3.0)!!, 1e-3)
        // Todos iguais → 1,0, como no legado.
        assertEquals(1.0, Jain.indice(listOf(Valor(0.8), Valor(0.8), Valor(0.8)), 3.0)!!, 1e-9)
        // Diferença deliberada: um valor sozinho não tem índice (o legado devolveria 1,0).
        assertEquals(null, Jain.indice(listOf(Valor(0.8)), 3.0))
    }
}
