package br.sidia.painel.operacao

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/** Relógio dos testes de operação: parte de um instante fixo e só anda quando o teste manda. */
class RelogioDeTeste(var agora: Instant, private val zona: ZoneId = ZoneId.of("America/Manaus")) : Clock() {
    override fun getZone(): ZoneId = zona
    override fun withZone(zone: ZoneId?): Clock = RelogioDeTeste(agora, zone ?: zona)
    override fun instant(): Instant = agora

    fun avancar(d: Duration) {
        agora = agora.plus(d)
    }
}
