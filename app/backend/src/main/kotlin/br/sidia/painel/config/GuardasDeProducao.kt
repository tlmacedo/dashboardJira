package br.sidia.painel.config

import br.sidia.painel.security.EstrategiaDeUsername
import br.sidia.painel.security.SegredosDaSessao
import java.net.URI
import java.net.URISyntaxException
import java.util.Base64

/** Produção recusada na subida. Os [codigos] são estáveis e a mensagem nunca traz valor (segredo, URL ou caminho). */
class ViolacaoDeProducao(val codigos: List<String>) : IllegalStateException("Subida recusada em produção: " + codigos.joinToString(", "))

/**
 * Guardas de produção (Plano 13, T4): rodam em `main()` logo depois de `Settings.fromEnv` e ANTES de abrir banco e trava, e
 * recusam a subida com TODOS os problemas de uma vez, em ordem estável. Em modo de desenvolvimento não fazem nada.
 *
 * Lêem as variáveis de OIDC, sessão e origem pública direto do mapa [env] (não dependem do formato interno de `OidcSettings`)
 * e o resto da configuração já interpretada ([Settings]). Códigos, nesta ordem: `DEMO_EM_PRODUCAO`, `OIDC_ISSUER_SEM_HTTPS`,
 * `REDIRECT_URI_SEM_HTTPS`, `ORIGEM_PUBLICA_AUSENTE`, `REDIRECT_URI_FORA_DA_ORIGEM_PUBLICA`, `SESSION_SECRET_CURTO`,
 * `DOMINIO_DO_USERNAME_AUSENTE`, `PROXY_NAO_CONFIGURADO`, `ESCUTA_ABERTA_A_TODAS`, `CAMINHO_RELATIVO`.
 *
 * Mais `TRAVA_DPO_NAO_DECLARADA` (por último): `DASHBOARD_DPO_ACEITE=true` sem `DASHBOARD_DPO_ACEITE_REF`. Aceite ausente ou
 * false NÃO recusa nada: a trava do DPO fica fechada e o painel sobe. Observação: pelo `main()` este código raramente aparece,
 * porque `LgpdSettings.fromEnv` (dentro de `Settings.fromEnv`) já recusa esse caso antes, com outra mensagem; a guarda existe
 * como defesa em profundidade para quem monta `Settings` sem passar por `fromEnv`, e é provada diretamente nos testes.
 */
object GuardasDeProducao {
    fun verificar(s: Settings, env: Map<String, String>) {
        if (s.dashboardDevMode) return
        fun valor(nome: String): String? = env[nome]?.trim()?.takeIf { it.isNotEmpty() }
        val achados = mutableListOf<String>()

        if (s.dashboardDemoData) achados += "DEMO_EM_PRODUCAO"

        val issuer = valor("OIDC_ISSUER")
        if (issuer == null || !ehHttps(issuer)) achados += "OIDC_ISSUER_SEM_HTTPS"

        val redirect = valor("OIDC_REDIRECT_URI")
        if (redirect != null && !ehHttps(redirect)) achados += "REDIRECT_URI_SEM_HTTPS"

        val origem = valor("DASHBOARD_ORIGEM_PUBLICA")
        if (origem == null || !ehHttps(origem)) {
            achados += "ORIGEM_PUBLICA_AUSENTE"
        } else if (redirect != null && origemDe(redirect) != origemDe(origem)) {
            achados += "REDIRECT_URI_FORA_DA_ORIGEM_PUBLICA"
        }

        if (!segredoDeSessaoValido(valor("SESSION_SECRET"))) achados += "SESSION_SECRET_CURTO"

        // parte_local_do_email e conta_windows descartam o domínio: sem lista de domínios permitidos, um convidado de outro domínio
        // com a mesma parte local viraria a pessoa do papeis.yaml.
        val estrategia = try {
            EstrategiaDeUsername.daVariavel(valor("OIDC_USERNAME_ESTRATEGIA"))
        } catch (e: IllegalArgumentException) {
            EstrategiaDeUsername.CLAIM_DIRETO // valor inválido já derruba a subida em OidcSettings
        }
        val dominios = valor("OIDC_DOMINIOS_PERMITIDOS")?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()
        if (estrategia.exigeDominio && dominios.isEmpty()) achados += "DOMINIO_DO_USERNAME_AUSENTE"

        if (s.proxyConfiavel.isEmpty()) achados += "PROXY_NAO_CONFIGURADO"

        when (classificarEscuta(s.host)) {
            Escuta.TODAS -> achados += "ESCUTA_ABERTA_A_TODAS"
            Escuta.NAO_LITERAL -> achados += "ESCUTA_NAO_LITERAL"
            Escuta.ESPECIFICA -> {}
        }

        if (!s.databasePath.isAbsolute || !s.configDir.isAbsolute || !s.staticDir.isAbsolute) achados += "CAMINHO_RELATIVO"

        // Trava do DPO: aceite ausente/false = trava FECHADA, e a subida segue (fail-closed). Aceite verdadeiro sem a referência
        // do parecer é declaração incompleta e recusa a subida (o LgpdSettings também recusa; aqui o código sai junto dos outros).
        if (valor("DASHBOARD_DPO_ACEITE")?.lowercase() == "true" && valor("DASHBOARD_DPO_ACEITE_REF") == null) {
            achados += "TRAVA_DPO_NAO_DECLARADA"
        }

        if (achados.isNotEmpty()) throw ViolacaoDeProducao(achados)
    }

    private enum class Escuta { TODAS, NAO_LITERAL, ESPECIFICA }

    private val IPV4_NUMERICO = Regex("^[0-9]+(\\.[0-9]+){0,3}$")
    private val IPV6_LITERAL = Regex("^[0-9A-Fa-f:][0-9A-Fa-f:.]*$")

    /**
     * Compara o ENDEREÇO, não o texto: `0`, `0.0`, `000.000.000.000`, `::0`, `0::0`, `0:0:0:0:0:0:0:0` e `::ffff:0.0.0.0` valem
     * "todas as interfaces". Só literais IP numéricos são interpretados (nunca há consulta de nome); fora `localhost`, qualquer
     * nome é recusado porque poderia apontar para qualquer endereço.
     */
    /**
     * Valor numérico de um IPv4 em 1 a 4 partes decimais, como o JDK o interpreta (`a.b.c.d`; com menos partes a última ocupa os
     * bytes restantes: `127.1` = 127.0.0.1). Nulo se alguma parte estourar a faixa. Nunca chama o resolvedor.
     */
    private fun valorIpv4(texto: String): Long? {
        val partes = texto.split('.').map { it.toBigIntegerOrNull() ?: return null }
        val n = partes.size
        var total = java.math.BigInteger.ZERO
        for ((i, p) in partes.withIndex()) {
            val ultima = i == n - 1
            val limite = if (ultima) java.math.BigInteger.ONE.shiftLeft(8 * (5 - n)) else java.math.BigInteger.valueOf(256)
            if (p >= limite) return null
            total = if (ultima) total.add(p) else total.add(p.shiftLeft(8 * (3 - i)))
        }
        return total.toLong()
    }

    private fun classificarEscuta(host: String): Escuta {
        val h = host.trim().removeSurrounding("[", "]")
        if (h.isEmpty()) return Escuta.TODAS
        if (IPV4_NUMERICO.matches(h)) {
            // O formato é validado AQUI, antes do JDK: um valor fora de faixa (ex.: 999.0.0.0) viraria nome e consultaria o DNS.
            val valor = valorIpv4(h) ?: return Escuta.NAO_LITERAL
            return if (valor == 0L) Escuta.TODAS else Escuta.ESPECIFICA
        }
        val literal = ':' in h && IPV6_LITERAL.matches(h)
        if (!literal) return if (h.equals("localhost", ignoreCase = true)) Escuta.ESPECIFICA else Escuta.NAO_LITERAL
        val endereco = try {
            java.net.InetAddress.getByName(h)
        } catch (e: java.net.UnknownHostException) {
            return Escuta.NAO_LITERAL
        }
        return if (endereco.isAnyLocalAddress) Escuta.TODAS else Escuta.ESPECIFICA
    }

    private fun uriOuNula(texto: String): URI? = try {
        URI(texto)
    } catch (e: URISyntaxException) {
        null
    }

    private fun ehHttps(texto: String): Boolean {
        val u = uriOuNula(texto) ?: return false
        return u.scheme.equals("https", ignoreCase = true) && !u.host.isNullOrEmpty()
    }

    /** `esquema://host[:porta]` em minúsculas, com a porta padrão (443) implícita; nulo se ilegível. */
    private fun origemDe(texto: String): String? {
        val u = uriOuNula(texto) ?: return null
        val host = u.host?.lowercase() ?: return null
        val porta = if (u.port == -1 || u.port == 443) "" else ":${u.port}"
        return "${u.scheme?.lowercase()}://$host$porta"
    }

    private fun segredoDeSessaoValido(texto: String?): Boolean {
        if (texto == null) return false
        return try {
            Base64.getDecoder().decode(texto).size >= SegredosDaSessao.MINIMO_DE_BYTES
        } catch (e: IllegalArgumentException) {
            false
        }
    }
}
