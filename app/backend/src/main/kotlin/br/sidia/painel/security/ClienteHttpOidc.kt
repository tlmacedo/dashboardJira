package br.sidia.painel.security

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout

/**
 * Cliente HTTP do login OIDC: fala só com o provedor de identidade (descoberta, JWKS e troca do código). Sem seguir
 * redirecionamento (um provedor legítimo não redireciona esses endereços) e com tempo limite curto, para que um provedor
 * lento não pendure o login. É o ÚNICO ponto do código de produção, fora do pacote de sincronização com o rastreador, que
 * constrói um cliente HTTP; a varredura de fontes do projeto o permite por nome e confere que este arquivo não cita o
 * rastreador nem recebe configuração dele.
 *
 * Proxy da empresa para sair até o provedor: configuração da implantação (Plano 13), não deste arquivo.
 */
fun criarClienteHttpOidc(): HttpClient = HttpClient(CIO) {
    followRedirects = false
    expectSuccess = false
    install(HttpTimeout) {
        connectTimeoutMillis = 5_000
        requestTimeoutMillis = 15_000
        socketTimeoutMillis = 15_000
    }
}
