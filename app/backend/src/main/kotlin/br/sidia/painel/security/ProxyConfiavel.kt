package br.sidia.painel.security

import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.application.install
import io.ktor.server.plugins.mutableOriginConnectionPoint
import java.net.InetAddress

/**
 * Faixa de endereços IP em notação CIDR (IPv4 ou IPv6), usada para saber se a CONEXÃO vem do proxy da empresa
 * (`DASHBOARD_PROXY_CONFIAVEL`). Só aceita endereços literais: nunca resolve nome (nada de DNS).
 */
class FaixaCidr private constructor(private val rede: ByteArray, private val prefixo: Int) {
    /** O [ip] (literal) está na faixa? Texto que não é um IP literal, ou de outra família, nunca casa. */
    fun contem(ip: String): Boolean {
        val bytes = enderecoLiteral(ip) ?: return false
        if (bytes.size != rede.size) return false
        var bits = prefixo
        var i = 0
        while (bits > 0) {
            val mascara = if (bits >= 8) 0xFF else (0xFF shl (8 - bits)) and 0xFF
            if ((bytes[i].toInt() and mascara) != (rede[i].toInt() and mascara)) return false
            bits -= 8
            i++
        }
        return true
    }

    override fun toString(): String = InetAddress.getByAddress(rede).hostAddress + "/" + prefixo

    companion object {
        private val IPV4 = Regex("^(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})$")
        // Começa por hexadecimal ou ':' (nunca por '.', que faria o JDK tratar o texto como nome e consultar o DNS).
        private val IPV6 = Regex("^[0-9A-Fa-f:][0-9A-Fa-f:.]{1,44}$")

        /** Bytes do endereço literal (4 ou 16), ou nulo se não for um IP literal. Nunca consulta DNS. */
        internal fun enderecoLiteral(texto: String): ByteArray? {
            val t = texto.trim()
            IPV4.matchEntire(t)?.let { m ->
                val partes = m.groupValues.drop(1).map { it.toInt() }
                return if (partes.all { it in 0..255 }) ByteArray(4) { partes[it].toByte() } else null
            }
            // IPv6 literal: só hexadecimais, ':' e '.'; com ':' o InetAddress não faz consulta de nome.
            if (':' !in t || !IPV6.matches(t)) return null
            return try {
                InetAddress.getByName(t).address
            } catch (e: java.net.UnknownHostException) {
                null
            }
        }

        /** `10.0.0.0/24`, `::1/128`, ou um endereço sem prefixo (= um só). Inválido: [IllegalArgumentException] sem o valor. */
        fun parse(texto: String): FaixaCidr {
            val t = texto.trim()
            val partes = t.split('/')
            require(partes.size in 1..2) { "faixa CIDR inválida" }
            val bytes = enderecoLiteral(partes[0]) ?: throw IllegalArgumentException("faixa CIDR inválida")
            val maximo = bytes.size * 8
            val prefixo = if (partes.size == 1) {
                maximo
            } else {
                partes[1].takeIf { it.isNotEmpty() && it.all(Char::isDigit) && it.length <= 3 }?.toInt()
                    ?: throw IllegalArgumentException("faixa CIDR inválida")
            }
            require(prefixo in 0..maximo) { "faixa CIDR inválida" }
            return FaixaCidr(bytes, prefixo)
        }
    }
}

/** De onde a chamada realmente veio, depois de considerar (ou ignorar) os cabeçalhos do proxy. */
data class OrigemDaChamada(val ip: String, val https: Boolean, val host: String)

// `nome[:porta]` ou `[ipv6][:porta]`.
private val HOST_ENCAMINHADO = Regex("^([A-Za-z0-9.\\-]{1,253}|\\[[0-9A-Fa-f:.]{2,45}\\])(:\\d{1,5})?$")

/** Separa `nome[:porta]` ou `[ipv6][:porta]` em host (IPv6 sem colchetes) e porta; só para texto que passou em [HOST_ENCAMINHADO]. */
internal fun separarHostEPorta(host: String): Pair<String, Int?> =
    if (host.startsWith("[")) {
        val fim = host.indexOf(']')
        host.substring(1, fim) to host.substring(fim + 1).removePrefix(":").toIntOrNull()
    } else {
        host.substringBefore(':') to host.substringAfter(':', "").toIntOrNull()
    }

/**
 * Decide o IP do cliente, o esquema e o host da chamada. `X-Forwarded-Proto`, `-For` e `-Host` SÓ valem quando o IP da
 * CONEXÃO ([ipDaConexao]) está numa faixa de [confiaveis]; caso contrário são ignorados (um cliente que não passou pelo proxy
 * poderia forjá-los). `X-Forwarded-For` é lido da DIREITA para a ESQUERDA, pulando endereços confiáveis: o primeiro não
 * confiável é o cliente (entradas à esquerda podem ter sido forjadas pelo cliente; o proxy acrescenta o real à direita).
 * Entrada ilegível no caminho interrompe a leitura: nunca vira IP.
 */
fun resolverOrigem(
    ipDaConexao: String,
    cabecalho: (String) -> String?,
    confiaveis: List<FaixaCidr>,
    hostDaRequisicao: String,
): OrigemDaChamada {
    fun confiavel(ip: String) = confiaveis.any { it.contem(ip) }
    if (!confiavel(ipDaConexao)) return OrigemDaChamada(ipDaConexao, https = false, host = hostDaRequisicao)

    // Proto e Host podem vir em várias linhas ou valores (o cliente forja os da esquerda; o proxy acrescenta à direita). Se todos
    // concordam, vale esse valor; se DIVERGEM, ninguém é crido (um proxy que não define o cabeçalho deixaria o do cliente passar).
    fun unico(nome: String): String? {
        val valores = cabecalho(nome)?.split(',')?.map { it.trim().lowercase() }.orEmpty().filter { it.isNotEmpty() }.distinct()
        return valores.singleOrNull()
    }

    val https = unico("X-Forwarded-Proto") == "https"
    val host = unico("X-Forwarded-Host")?.takeIf { HOST_ENCAMINHADO.matches(it) } ?: hostDaRequisicao
    val entradas = cabecalho("X-Forwarded-For")?.split(',')?.map { it.trim() }.orEmpty().filter { it.isNotEmpty() }
    var ip = ipDaConexao
    for (entrada in entradas.asReversed()) {
        if (FaixaCidr.enderecoLiteral(entrada) == null) break
        ip = entrada
        if (!confiavel(entrada)) break
    }
    return OrigemDaChamada(ip, https, host)
}

/**
 * Instala a leitura dos cabeçalhos do proxy: depois dela, `call.request.origin` (esquema, host e IP remoto) reflete o que o
 * proxy informou, mas SÓ para conexões das faixas [confiaveis]. [origemDaConexao] é o ponto de injeção dos testes (o IP da
 * conexão real). O `redirect_uri` do login não passa por aqui: vem só de `OIDC_REDIRECT_URI`.
 */
fun Application.instalarProxyConfiavel(
    confiaveis: List<FaixaCidr>,
    origemDaConexao: (ApplicationCall) -> String = { it.request.local.remoteAddress },
) {
    install(
        createApplicationPlugin("ProxyConfiavel") {
            onCall { call ->
                val conexao = origemDaConexao(call)
                // Conexão fora das faixas: não se toca em nada (os cabeçalhos do proxy nem são lidos).
                if (confiaveis.none { it.contem(conexao) }) return@onCall
                val o = resolverOrigem(
                    ipDaConexao = conexao,
                    // `headers[...]` devolve só a PRIMEIRA linha; todas as linhas entram, na ordem em que chegaram.
                    cabecalho = { call.request.headers.getAll(it)?.joinToString(",") },
                    confiaveis = confiaveis,
                    hostDaRequisicao = call.request.local.serverHost,
                )
                val ponto = call.mutableOriginConnectionPoint
                if (o.https) ponto.scheme = "https"
                val (nome, porta) = separarHostEPorta(o.host)
                ponto.serverHost = nome
                porta?.let { ponto.serverPort = it }
                ponto.remoteAddress = o.ip
            }
        },
    )
}
