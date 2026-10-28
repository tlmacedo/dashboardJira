package br.sidia.painel.cards.cargatipo

import br.sidia.painel.cards.AvisoDto
import br.sidia.painel.cards.CardDeLeitura
import br.sidia.painel.cards.CartaoDeLeitura
import br.sidia.painel.cards.EntradaDeCard
import br.sidia.painel.cards.INSTANCIA_TRABALHO
import br.sidia.painel.cards.ResultadoDeCard
import br.sidia.painel.cards.contrato.CartaoRegistrado
import br.sidia.painel.cards.contrato.FonteDoDado
import br.sidia.painel.cards.contrato.GrupoDeCartao
import br.sidia.painel.cards.contrato.Natureza
import br.sidia.painel.cards.esforco.esforcosDas
import br.sidia.painel.cards.linhaDe
import br.sidia.painel.cards.metaDeTrabalho
import br.sidia.painel.pessoas.MotorDePessoas
import br.sidia.painel.sync.FiltroDeFicha
import br.sidia.painel.trabalho.ConsultaDeTrabalho
import br.sidia.painel.trabalho.Fluxo
import br.sidia.painel.trabalho.Situacao
import java.util.Locale

/**
 * Card Carga por tipo e OS: as abertas (contagem e horas de fila) e as concluídas do período (contagem e índice de esforço) por
 * tipo × OS Version; "sem OS" é uma coluna própria. As abertas seguem o responsável ATUAL e as concluídas, a posse (as mesmas
 * tasks do card Esforço). Período padrão de 30 dias pela resolução. Todos os papéis veem o card; o USUARIO só as próprias tasks.
 */
object CardCargaTipo : CardDeLeitura<CargaTipoDto> {
    override val id = "carga-tipo"
    override val periodoPadraoDias: Int? = 30

    override fun calcular(e: EntradaDeCard): ResultadoDeCard<CargaTipoDto> {
        val ctx = e.ctx
        val motor = MotorDePessoas(ctx)
        val zona = ctx.settings.zone
        val inicio = requireNotNull(e.filtros.de).atStartOfDay(zona).toInstant()
        val fim = requireNotNull(e.filtros.ate).plusDays(1).atStartOfDay(zona).toInstant()
        val ficha = FiltroDeFicha.de(ctx.fichas.getValue(id))
        val abertas = e.store.snapshots(ConsultaDeTrabalho(INSTANCIA_TRABALHO, ficha, Situacao.ABERTAS, osVersion = e.filtros.os))
            .filter { e.escopo.permiteAtual(it) && e.filtros.aceita(it) }
        val resolvidas = e.store.snapshots(ConsultaDeTrabalho(INSTANCIA_TRABALHO, ficha, Situacao.RESOLVIDAS, inicio, fim, e.filtros.os))
        Fluxo.exigirStatusMapeados(ctx.statusMap, abertas, resolvidas)
        val fechadas = resolvidas.filter {
            Fluxo.concluidaPositiva(it, ctx.statusMap) && e.filtros.aceita(it) && e.escopo.permitePosse(it, ctx.statusMap, e.agora)
        }

        val analise = CargaTipoCalculator(motor).calcular(abertas, fechadas, esforcosDas(ctx, fechadas))
        fun numero(v: Double) = String.format(Locale.ROOT, "%.1f", v)
        return ResultadoDeCard(analise.dto) { segmento ->
            val lista = analise.segmentos[segmento] ?: return@ResultadoDeCard null
            lista.map { s ->
                val detalhe = if (segmento.startsWith("abertas:")) "Restam ${numero(motor.horas.de(s).valor)} h" else "Resolvida em ${s.resolved?.atZone(zona)?.toLocalDate()}"
                linhaDe(s, e.escopo, detalhe)
            }
        }
    }

    override fun vazio(avisos: List<AvisoDto>) = CargaTipoDto(
        tipos = emptyList(), versoes = emptyList(), abertasH = emptyList(), abertasN = emptyList(), fechadasN = emptyList(),
        esforco = emptyList(), avisos = avisos,
    )
}

/** Cartões da tela Carga por tipo e OS: `CG-01` (abertas, `abertas:tipo:<T>:os:<V>`) e `CG-02` (fechadas, `fechadas:tipo:<T>:os:<V>`). */
internal fun cartoesDeCargaTipo(): List<CartaoRegistrado> {
    fun celulas(dto: CargaTipoDto, prefixo: String, contagem: List<List<Int>>): Map<String, Int> = buildMap {
        for ((i, tipo) in dto.tipos.withIndex()) for ((j, versao) in dto.versoes.withIndex()) {
            if (contagem[i][j] > 0) put("$prefixo:tipo:$tipo:os:$versao", contagem[i][j])
        }
    }
    return listOf(
        CartaoDeLeitura(
            metaDeTrabalho(
                "CG-01", CardCargaTipo.id, GrupoDeCartao.PESSOAS_E_PLANEJAMENTO, Natureza.ESTOQUE,
                listOf(FonteDoDado.ISSUE, FonteDoDado.CONFIGURACAO),
            ),
            CardCargaTipo, { it.startsWith("abertas:tipo:") },
        ) { dto -> celulas(dto, "abertas", dto.abertasN) },
        CartaoDeLeitura(
            metaDeTrabalho(
                "CG-02", CardCargaTipo.id, GrupoDeCartao.PESSOAS_E_PLANEJAMENTO, Natureza.FLUXO,
                listOf(FonteDoDado.ISSUE, FonteDoDado.CHANGELOG, FonteDoDado.WORKLOG, FonteDoDado.CONFIGURACAO), exigeChangelog = true,
            ).copy(exigeWorklog = true),
            CardCargaTipo, { it.startsWith("fechadas:tipo:") },
        ) { dto -> celulas(dto, "fechadas", dto.fechadasN) },
    )
}
